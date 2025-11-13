"""
Thermal AR Processing Server for ThinkPad P16 Gen 2
Receives thermal stream from Google Glass + Boson 320, processes with AI, sends AR annotations back
"""

import asyncio
import cv2
import numpy as np
import torch
import json
import base64
import os
import socket
import threading
from collections import deque
from pathlib import Path
from flask import Flask, render_template, jsonify
from flask_socketio import SocketIO, emit
import time
from datetime import datetime
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
socketio = SocketIO(app, cors_allowed_origins="*", async_mode='threading')

# ========== AUTOMATIC SERVER DISCOVERY ==========

DISCOVERY_PORT = 8081
SERVER_PORT = 8080  # Main Socket.IO port
SERVER_NAME = "ThermalAR-Server"

def start_discovery_service():
    """
    UDP broadcast responder for automatic Glass discovery
    Responds to 'THERMAL_AR_GLASS_DISCOVERY' broadcasts
    """
    def discovery_responder():
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

        try:
            sock.bind(('', DISCOVERY_PORT))
            logger.info(f"✓ Discovery service listening on port {DISCOVERY_PORT}")
            logger.info(f"  Glass devices will auto-discover this server")

            while True:
                try:
                    data, addr = sock.recvfrom(1024)
                    message = data.decode('utf-8')

                    if message == "THERMAL_AR_GLASS_DISCOVERY":
                        # Respond with server info
                        # Format: THERMAL_AR_SERVER:name:port:capabilities
                        response = f"THERMAL_AR_SERVER:{SERVER_NAME}:{SERVER_PORT}:object_detection,thermal_analysis"
                        sock.sendto(response.encode('utf-8'), addr)
                        logger.info(f"Responded to discovery from {addr[0]}")

                except Exception as e:
                    logger.error(f"Discovery response error: {e}")

        except Exception as e:
            logger.error(f"Discovery service failed to start: {e}")
        finally:
            sock.close()

    # Start discovery responder in background thread
    discovery_thread = threading.Thread(target=discovery_responder, daemon=True)
    discovery_thread.start()

# Start discovery service
start_discovery_service()

class Boson320Processor:
    """Main processor for Boson 320 60Hz thermal stream"""
    
    def __init__(self):
        self.resolution = (320, 256)
        self.fps = 60
        self.target_processing_fps = 30

        # Frame buffers
        self.frame_buffer = deque(maxlen=2)  # For temporal averaging
        self.frame_count = 0
        self.cached_annotations = None

        # Statistics
        self.stats = {
            'frames_received': 0,
            'frames_processed': 0,
            'avg_latency_ms': 0,
            'gpu_utilization': 0,
            'dropped_frames': 0
        }

        # Session recording
        self.recording = False
        self.recording_session = None
        self.recordings_dir = Path("recordings")
        self.recordings_dir.mkdir(exist_ok=True)

        # Thermal calibration (load from file or use defaults)
        self.load_calibration()

        # Load AI models
        logger.info("Loading AI models...")
        self.load_models()
        
    def load_calibration(self):
        """Load thermal calibration from file or use defaults"""
        calibration_file = Path("boson_calibration.json")

        if calibration_file.exists():
            try:
                with open(calibration_file, 'r') as f:
                    self.calibration = json.load(f)
                logger.info(f"Loaded calibration from {calibration_file}")
            except Exception as e:
                logger.warning(f"Failed to load calibration: {e}, using defaults")
                self.calibration = self._default_calibration()
        else:
            logger.info("No calibration file found, using defaults")
            self.calibration = self._default_calibration()

    def _default_calibration(self):
        """Default Boson 320 calibration parameters"""
        return {
            'offset': -8192,
            'scale': 0.01,
            'reference_temp': 20.0,
            'min_temp': -40,
            'max_temp': 330
        }

    def load_models(self):
        """Load YOLOv8 and other models optimized for RTX 4000 Ada"""
        self.object_detector = None
        self.device = 'cpu'

        try:
            # Check GPU availability first
            if torch.cuda.is_available():
                self.device = 'cuda'
                logger.info(f"GPU detected: {torch.cuda.get_device_name(0)}")
                logger.info(f"CUDA version: {torch.version.cuda}")
            else:
                logger.warning("GPU not available, using CPU (performance will be degraded)")

            # YOLOv8 for object detection - optimized for 320x256
            from ultralytics import YOLO

            model_path = 'yolov8l.pt'
            if not Path(model_path).exists():
                logger.warning(f"{model_path} not found, downloading...")

            self.object_detector = YOLO(model_path)
            logger.info(f"YOLOv8 model loaded successfully on {self.device}")

            # Warm up the model
            dummy_frame = np.zeros((256, 320, 3), dtype=np.uint8)
            _ = self.object_detector(dummy_frame, verbose=False)
            logger.info("Model warmup complete")

        except ImportError:
            logger.error("ultralytics package not installed. Run: pip install ultralytics")
            self.object_detector = None
        except Exception as e:
            logger.error(f"Error loading models: {e}")
            self.object_detector = None
    
    def calibrate_thermal(self, raw_frame):
        """Convert Boson raw values to temperature in Celsius using calibration data"""
        # Boson 320 outputs 14-bit values (0-16383)
        # Apply loaded calibration parameters
        temp_celsius = (raw_frame.astype(np.float32) + self.calibration['offset']) * self.calibration['scale']

        # Clamp to physical limits
        temp_celsius = np.clip(temp_celsius, self.calibration['min_temp'], self.calibration['max_temp'])

        return temp_celsius
    
    def temporal_denoise(self, frame):
        """Leverage 60Hz for noise reduction through temporal averaging"""
        self.frame_buffer.append(frame)
        
        if len(self.frame_buffer) < 2:
            return frame
        
        # Weighted average (more recent = higher weight)
        weights = np.array([0.4, 0.6])
        denoised = np.average(self.frame_buffer, axis=0, weights=weights)
        
        return denoised
    
    def detect_objects(self, frame):
        """Run object detection on thermal frame"""
        if self.object_detector is None:
            return []
        
        try:
            # Normalize frame for model input
            normalized = cv2.normalize(frame, None, 0, 255, cv2.NORM_MINMAX).astype(np.uint8)
            
            # Convert to 3-channel (YOLO expects RGB)
            frame_3ch = cv2.cvtColor(normalized, cv2.COLOR_GRAY2RGB)
            
            # Run detection
            results = self.object_detector(frame_3ch, verbose=False)
            
            # Parse results
            detections = []
            for result in results:
                boxes = result.boxes
                for box in boxes:
                    x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                    confidence = float(box.conf[0])
                    class_id = int(box.cls[0])
                    class_name = result.names[class_id]
                    
                    detections.append({
                        'bbox': [float(x1), float(y1), float(x2), float(y2)],
                        'confidence': confidence,
                        'class': class_name
                    })
            
            return detections
            
        except Exception as e:
            print(f"Detection error: {e}")
            return []
    
    def analyze_thermal_building(self, temp_frame):
        """Analyze thermal frame for building inspection anomalies"""
        # Calculate statistics
        median_temp = np.median(temp_frame)
        anomaly_threshold = 5.0  # degrees C difference
        
        # Find hot and cold spots
        hot_mask = temp_frame > (median_temp + anomaly_threshold)
        cold_mask = temp_frame < (median_temp - anomaly_threshold)
        
        # Find contours of anomalous regions
        hot_contours, _ = cv2.findContours(
            hot_mask.astype(np.uint8), 
            cv2.RETR_EXTERNAL, 
            cv2.CHAIN_APPROX_SIMPLE
        )
        
        cold_contours, _ = cv2.findContours(
            cold_mask.astype(np.uint8), 
            cv2.RETR_EXTERNAL, 
            cv2.CHAIN_APPROX_SIMPLE
        )
        
        # Process significant anomalies
        hot_spots = []
        for contour in hot_contours:
            area = cv2.contourArea(contour)
            if area > 50:  # Minimum area threshold
                x, y, w, h = cv2.boundingRect(contour)
                region = temp_frame[y:y+h, x:x+w]
                max_temp = np.max(region)
                hot_spots.append({
                    'bbox': [int(x), int(y), int(x+w), int(y+h)],
                    'max_temp': float(max_temp),
                    'area': float(area),
                    'type': 'hot_spot'
                })
        
        cold_spots = []
        for contour in cold_contours:
            area = cv2.contourArea(contour)
            if area > 50:
                x, y, w, h = cv2.boundingRect(contour)
                region = temp_frame[y:y+h, x:x+w]
                min_temp = np.min(region)
                cold_spots.append({
                    'bbox': [int(x), int(y), int(x+w), int(y+h)],
                    'min_temp': float(min_temp),
                    'area': float(area),
                    'type': 'cold_spot'
                })
        
        return {
            'hot_spots': hot_spots,
            'cold_spots': cold_spots,
            'baseline_temp': float(median_temp),
            'temp_range': [float(temp_frame.min()), float(temp_frame.max())]
        }
    
    def analyze_thermal_electronics(self, temp_frame, detections):
        """Specialized analysis for electronics inspection"""
        component_temps = []
        
        for detection in detections:
            bbox = detection['bbox']
            x1, y1, x2, y2 = [int(v) for v in bbox]
            
            # Extract temperature in bounding box
            region = temp_frame[y1:y2, x1:x2]
            if region.size > 0:
                max_temp = np.max(region)
                avg_temp = np.mean(region)
                
                # Temperature thresholds for different components
                thresholds = {
                    'IC': 70,
                    'resistor': 80,
                    'capacitor': 60,
                    'transistor': 70,
                    'default': 65
                }
                
                component_class = detection['class']
                threshold = thresholds.get(component_class, thresholds['default'])
                
                component_temps.append({
                    'component': component_class,
                    'bbox': bbox,
                    'max_temp': float(max_temp),
                    'avg_temp': float(avg_temp),
                    'is_hot': max_temp > threshold,
                    'severity': 'critical' if max_temp > threshold + 10 else 'warning' if max_temp > threshold else 'normal'
                })
        
        return component_temps
    
    def process_frame(self, frame_data, mode='building'):
        """Main processing pipeline"""
        start_time = time.time()

        try:
            # Smart format detection and decoding
            # Check if frame is MJPEG (JPEG magic bytes: 0xFF 0xD8)
            is_mjpeg = len(frame_data) >= 2 and frame_data[0] == 0xFF and frame_data[1] == 0xD8

            if is_mjpeg:
                # MJPEG format - decompress JPEG
                logger.debug(f"MJPEG frame detected ({len(frame_data)} bytes), decompressing...")
                frame_array = np.frombuffer(frame_data, dtype=np.uint8)
                frame_bgr = cv2.imdecode(frame_array, cv2.IMREAD_GRAYSCALE)

                if frame_bgr is None:
                    logger.error("Failed to decode MJPEG frame")
                    return None

                # MJPEG from Boson is 8-bit grayscale, convert to 16-bit for processing
                # Scale 0-255 → 0-65535 to match Y16 range
                frame = (frame_bgr.astype(np.uint16) * 257)  # 257 = 65535/255

                logger.debug(f"MJPEG decoded: {frame.shape}, dtype={frame.dtype}")

            else:
                # Raw Y16 format (16-bit radiometric)
                logger.debug(f"Y16 frame detected ({len(frame_data)} bytes), reshaping...")
                frame = np.frombuffer(frame_data, dtype=np.uint16).reshape(self.resolution[1], self.resolution[0])
            
            # Calibrate to temperature
            temp_frame = self.calibrate_thermal(frame)
            
            # Temporal denoising
            denoised = self.temporal_denoise(temp_frame)
            
            # Process based on mode
            annotations = {}
            
            if mode == 'building':
                # Building inspection mode
                detections = self.detect_objects(denoised)
                thermal_analysis = self.analyze_thermal_building(denoised)
                
                annotations = {
                    'detections': detections,
                    'thermal_anomalies': thermal_analysis,
                    'mode': 'building'
                }
                
            elif mode == 'electronics':
                # Electronics inspection mode
                detections = self.detect_objects(denoised)
                component_analysis = self.analyze_thermal_electronics(denoised, detections)
                
                annotations = {
                    'detections': detections,
                    'component_temps': component_analysis,
                    'mode': 'electronics'
                }
            
            # Add metadata
            processing_time = (time.time() - start_time) * 1000
            annotations['timestamp'] = time.time()
            annotations['processing_time_ms'] = processing_time
            annotations['frame_number'] = self.frame_count
            
            # Update stats
            self.stats['frames_processed'] += 1
            self.stats['avg_latency_ms'] = (self.stats['avg_latency_ms'] * 0.9 + processing_time * 0.1)
            
            return annotations
            
        except Exception as e:
            print(f"Processing error: {e}")
            return {'error': str(e)}
    
    def process_stream(self, frame_data, mode='building'):
        """Handle 60Hz stream with 30Hz processing"""
        self.frame_count += 1
        self.stats['frames_received'] += 1

        # Process every other frame (30fps processing from 60fps stream)
        if self.frame_count % 2 == 0:
            self.cached_annotations = self.process_frame(frame_data, mode)

            # Save to recording if active
            if self.recording and self.recording_session:
                self._save_frame_to_recording(frame_data, self.cached_annotations)
        else:
            self.stats['dropped_frames'] += 1

        # Return cached annotations (updated at 30fps, held for 2 frames)
        return self.cached_annotations

    def start_recording(self, session_name=None):
        """Start recording session with thermal frames and annotations"""
        if self.recording:
            logger.warning("Recording already in progress")
            return False

        if session_name is None:
            session_name = datetime.now().strftime("%Y%m%d_%H%M%S")

        session_dir = self.recordings_dir / session_name
        session_dir.mkdir(exist_ok=True)

        self.recording_session = {
            'name': session_name,
            'dir': session_dir,
            'start_time': time.time(),
            'frame_count': 0,
            'metadata_file': session_dir / "metadata.json",
            'metadata': {
                'session_name': session_name,
                'start_time': datetime.now().isoformat(),
                'resolution': self.resolution,
                'fps': self.target_processing_fps,
                'frames': []
            }
        }

        self.recording = True
        logger.info(f"Started recording session: {session_name}")
        return True

    def stop_recording(self):
        """Stop recording and save metadata"""
        if not self.recording or not self.recording_session:
            logger.warning("No recording in progress")
            return False

        self.recording = False

        # Save metadata
        self.recording_session['metadata']['end_time'] = datetime.now().isoformat()
        self.recording_session['metadata']['total_frames'] = self.recording_session['frame_count']
        self.recording_session['metadata']['duration_seconds'] = time.time() - self.recording_session['start_time']

        with open(self.recording_session['metadata_file'], 'w') as f:
            json.dump(self.recording_session['metadata'], f, indent=2)

        logger.info(f"Stopped recording: {self.recording_session['name']}, "
                   f"{self.recording_session['frame_count']} frames saved")

        self.recording_session = None
        return True

    def _save_frame_to_recording(self, frame_data, annotations):
        """Save thermal frame and annotations to recording"""
        if not self.recording_session:
            return

        frame_num = self.recording_session['frame_count']

        # Save thermal frame as NPY (preserves 16-bit data)
        frame_array = np.frombuffer(frame_data, dtype=np.uint16).reshape(self.resolution[1], self.resolution[0])
        frame_path = self.recording_session['dir'] / f"frame_{frame_num:06d}.npy"
        np.save(frame_path, frame_array)

        # Save annotations
        anno_path = self.recording_session['dir'] / f"annotations_{frame_num:06d}.json"
        with open(anno_path, 'w') as f:
            json.dump(annotations, f)

        # Update metadata
        self.recording_session['metadata']['frames'].append({
            'frame_number': frame_num,
            'timestamp': time.time(),
            'thermal_file': str(frame_path.name),
            'annotations_file': str(anno_path.name)
        })

        self.recording_session['frame_count'] += 1

# Global processor instance
processor = Boson320Processor()
current_mode = 'building'

@app.route('/')
def index():
    """Dashboard web interface"""
    return """
    <html>
    <head><title>Thermal AR Server</title></head>
    <body>
        <h1>Thermal AR Processing Server</h1>
        <p>Status: Running</p>
        <div id="stats">
            <h2>Statistics</h2>
            <p>Frames Received: <span id="frames_rx">0</span></p>
            <p>Frames Processed: <span id="frames_proc">0</span></p>
            <p>Avg Latency: <span id="latency">0</span> ms</p>
        </div>
        <script src="https://cdn.socket.io/4.5.4/socket.io.min.js"></script>
        <script>
            const socket = io();
            socket.on('stats', function(data) {
                document.getElementById('frames_rx').textContent = data.frames_received;
                document.getElementById('frames_proc').textContent = data.frames_processed;
                document.getElementById('latency').textContent = data.avg_latency_ms.toFixed(2);
            });
        </script>
    </body>
    </html>
    """

@socketio.on('connect')
def handle_connect():
    """Handle Glass client connection"""
    logger.info('Glass client connected')
    emit('server_ready', {
        'status': 'ready',
        'gpu_available': processor.device == 'cuda',
        'model_loaded': processor.object_detector is not None
    })

@socketio.on('disconnect')
def handle_disconnect():
    """Handle Glass client disconnection"""
    logger.info('Glass client disconnected')

    # Stop recording if active
    if processor.recording:
        processor.stop_recording()
        logger.info('Auto-stopped recording on disconnect')

@socketio.on('thermal_frame')
def handle_thermal_frame(data):
    """Receive thermal frame from Glass, process, and send annotations back"""
    try:
        # Extract frame data (base64 encoded)
        frame_base64 = data.get('frame')
        mode = data.get('mode', current_mode)

        # Extract Glass context metadata for syncing
        frame_number = data.get('frame_number', 0)
        glass_format = data.get('format', 'unknown')  # 'MJPEG', 'Y16', 'I420'
        has_temperature = data.get('has_temperature', False)
        client_timestamp = data.get('timestamp')

        # Decode base64 to bytes
        if isinstance(frame_base64, str):
            frame_data = base64.b64decode(frame_base64)
        else:
            # Fallback for raw bytes (backward compatibility)
            frame_data = frame_base64

        # Validate format sync (log first few frames)
        if frame_number <= 3:
            is_mjpeg = len(frame_data) >= 2 and frame_data[0] == 0xFF and frame_data[1] == 0xD8
            detected_format = "MJPEG" if is_mjpeg else "Y16/Raw"

            logger.info(f"Frame #{frame_number}: Glass='{glass_format}', Detected='{detected_format}', "
                       f"Size={len(frame_data)} bytes, HasTemp={has_temperature}")

            if glass_format != 'unknown' and glass_format not in detected_format:
                logger.warning(f"⚠ Format mismatch! Glass reports '{glass_format}' but server sees '{detected_format}'")

        # Process frame
        annotations = processor.process_stream(frame_data, mode)

        if annotations:
            # Add server metadata for latency tracking
            annotations['server_timestamp'] = int(time.time() * 1000)
            if client_timestamp:
                annotations['client_timestamp'] = client_timestamp

            # Echo format info
            annotations['format_confirmed'] = glass_format

        if annotations:
            # Send annotations back to Glass
            emit('annotations', annotations)

        # Periodically send stats
        if processor.frame_count % 60 == 0:
            emit('stats', processor.stats)

    except Exception as e:
        logger.error(f"Error handling frame: {e}", exc_info=True)
        emit('error', {'message': str(e)})

@socketio.on('set_mode')
def handle_set_mode(data):
    """Switch between building and electronics inspection modes"""
    global current_mode
    current_mode = data.get('mode', 'building')
    print(f"Mode switched to: {current_mode}")
    emit('mode_changed', {'mode': current_mode})

@socketio.on('request_stats')
def handle_stats_request():
    """Send current statistics"""
    emit('stats', processor.stats)

@socketio.on('start_recording')
def handle_start_recording(data):
    """Start recording session"""
    session_name = data.get('session_name', None)
    success = processor.start_recording(session_name)
    emit('recording_status', {
        'recording': success,
        'session_name': processor.recording_session['name'] if success else None,
        'message': 'Recording started' if success else 'Failed to start recording'
    })

@socketio.on('stop_recording')
def handle_stop_recording():
    """Stop recording session"""
    success = processor.stop_recording()
    emit('recording_status', {
        'recording': False,
        'message': 'Recording stopped' if success else 'No recording in progress'
    })

# Import and setup companion app extension
from server_companion_extension import setup_companion_events

if __name__ == '__main__':
    print("="*60)
    print("Thermal AR Processing Server")
    print("ThinkPad P16 Gen 2 with RTX 4000 Ada")
    print("="*60)
    print("\nStarting server on port 8080...")
    print("Dashboard available at: http://localhost:8080")
    print("\nWaiting for Google Glass connection...")

    # Setup companion app Socket.IO events
    setup_companion_events(socketio, processor)
    print("Companion app extension loaded")

    socketio.run(app, host='0.0.0.0', port=8080, debug=True)
