#!/usr/bin/env python3
"""
Glass AR Companion App for Windows (ThinkPad P16)

A desktop GUI application for remote viewing, controlling, and recording
thermal AR inspections from Google Glass EE2.

Features:
- Live thermal video stream with AI annotations
- Remote control of Glass (capture, record, mode switch)
- Session recording and playback
- Service management (start/stop server)
- Statistics and diagnostics

Requirements:
- Python 3.8+
- PyQt5
- OpenCV
- Pillow
- python-socketio[client]
"""

import sys
import os
import subprocess
import threading
import queue
import time
import json
from datetime import datetime
from pathlib import Path

from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QPushButton, QLabel, QTextEdit, QTabWidget, QGroupBox,
    QComboBox, QSpinBox, QCheckBox, QFileDialog, QMessageBox,
    QSlider, QProgressBar, QListWidget, QSplitter, QStatusBar
)
from PyQt5.QtCore import Qt, QTimer, pyqtSignal, QThread
from PyQt5.QtGui import QImage, QPixmap, QFont, QColor, QPalette

import socketio
import cv2
import numpy as np
from PIL import Image
import base64

# ==================== Configuration ====================

class Config:
    """Application configuration"""
    SERVER_HOST = "0.0.0.0"
    SERVER_PORT = 8080
    RECORDINGS_DIR = Path("./recordings")
    SNAPSHOTS_DIR = Path("./snapshots")
    LOGS_DIR = Path("./logs")

    # Ensure directories exist
    RECORDINGS_DIR.mkdir(exist_ok=True)
    SNAPSHOTS_DIR.mkdir(exist_ok=True)
    LOGS_DIR.mkdir(exist_ok=True)

# ==================== Socket.IO Client ====================

class SocketIOClient(QThread):
    """Socket.IO client for receiving thermal frames and events"""

    frame_received = pyqtSignal(np.ndarray, dict)  # frame, annotations
    status_update = pyqtSignal(str, str)  # event, message
    glass_connected = pyqtSignal(bool)  # connected status

    def __init__(self, host='localhost', port=8080):
        super().__init__()
        self.host = host
        self.port = port
        self.url = f'http://{host}:{port}'
        self.sio = socketio.Client()
        self.running = False
        self.latest_frame = None
        self.latest_annotations = {}

        # Setup event handlers
        self.setup_handlers()

    def setup_handlers(self):
        """Setup Socket.IO event handlers"""

        @self.sio.on('connect')
        def on_connect():
            self.status_update.emit('connected', 'Connected to server')
            print('[Companion] Connected to server')

        @self.sio.on('disconnect')
        def on_disconnect():
            self.status_update.emit('disconnected', 'Disconnected from server')
            self.glass_connected.emit(False)
            print('[Companion] Disconnected from server')

        @self.sio.on('glass_connected')
        def on_glass_connected(data):
            self.glass_connected.emit(True)
            self.status_update.emit('glass_connected', 'Glass connected')
            print('[Companion] Glass device connected')

        @self.sio.on('glass_disconnected')
        def on_glass_disconnected(data):
            self.glass_connected.emit(False)
            self.status_update.emit('glass_disconnected', 'Glass disconnected')
            print('[Companion] Glass device disconnected')

        @self.sio.on('thermal_frame_processed')
        def on_thermal_frame(data):
            """Receive processed thermal frame with annotations"""
            try:
                # Decode frame
                frame_data = base64.b64decode(data['frame'])
                nparr = np.frombuffer(frame_data, np.uint8)
                frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

                # Extract annotations
                annotations = {
                    'detections': data.get('detections', []),
                    'thermal_anomalies': data.get('thermal_anomalies', {}),
                    'mode': data.get('mode', 'thermal_only'),
                    'timestamp': data.get('timestamp', time.time())
                }

                self.latest_frame = frame
                self.latest_annotations = annotations
                self.frame_received.emit(frame, annotations)

            except Exception as e:
                print(f'[Companion] Error processing frame: {e}')

    def run(self):
        """Connect to server and run event loop"""
        self.running = True
        try:
            print(f'[Companion] Connecting to {self.url}...')
            self.sio.connect(self.url)

            while self.running:
                time.sleep(0.1)

        except Exception as e:
            print(f'[Companion] Connection error: {e}')
            self.status_update.emit('error', f'Connection error: {e}')
        finally:
            if self.sio.connected:
                self.sio.disconnect()

    def send_command(self, command, data=None):
        """Send command to Glass via server"""
        if not self.sio.connected:
            return False

        try:
            self.sio.emit(command, data or {})
            return True
        except Exception as e:
            print(f'[Companion] Error sending command: {e}')
            return False

    def stop(self):
        """Stop the client thread"""
        self.running = False
        if self.sio.connected:
            self.sio.disconnect()

# ==================== Main Window ====================

class GlassCompanionApp(QMainWindow):
    """Main application window"""

    def __init__(self):
        super().__init__()
        self.setWindowTitle('Glass AR Companion - ThinkPad P16')
        self.setGeometry(100, 100, 1400, 900)

        # State
        self.socket_client = None
        self.server_process = None
        self.is_recording = False
        self.recording_frames = []
        self.glass_connected = False

        # Setup UI
        self.setup_ui()
        self.setup_statusbar()

        # Apply dark theme
        self.apply_dark_theme()

        print('[Companion App] Initialized')

    def setup_ui(self):
        """Setup the user interface"""
        central_widget = QWidget()
        self.setCentralWidget(central_widget)

        main_layout = QHBoxLayout(central_widget)

        # Left panel: Video feed
        left_panel = self.create_video_panel()

        # Right panel: Controls
        right_panel = self.create_control_panel()

        # Splitter for resizable panels
        splitter = QSplitter(Qt.Horizontal)
        splitter.addWidget(left_panel)
        splitter.addWidget(right_panel)
        splitter.setStretchFactor(0, 3)  # Video gets more space
        splitter.setStretchFactor(1, 1)

        main_layout.addWidget(splitter)

    def create_video_panel(self):
        """Create the video display panel"""
        panel = QWidget()
        layout = QVBoxLayout(panel)

        # Video label
        self.video_label = QLabel()
        self.video_label.setAlignment(Qt.AlignCenter)
        self.video_label.setMinimumSize(640, 480)
        self.video_label.setStyleSheet('background-color: #1e1e1e; border: 2px solid #3d3d3d;')
        self.video_label.setText('No Video Feed\n\nStart the server and connect Glass')

        # Info overlay
        info_layout = QHBoxLayout()

        self.fps_label = QLabel('FPS: 0')
        self.mode_label = QLabel('Mode: N/A')
        self.detections_label = QLabel('Detections: 0')

        info_layout.addWidget(self.fps_label)
        info_layout.addWidget(self.mode_label)
        info_layout.addWidget(self.detections_label)
        info_layout.addStretch()

        layout.addWidget(self.video_label, 1)
        layout.addLayout(info_layout)

        return panel

    def create_control_panel(self):
        """Create the control panel"""
        panel = QWidget()
        layout = QVBoxLayout(panel)

        # Tabs for different sections
        tabs = QTabWidget()

        tabs.addTab(self.create_connection_tab(), 'Connection')
        tabs.addTab(self.create_control_tab(), 'Controls')
        tabs.addTab(self.create_recording_tab(), 'Recording')
        tabs.addTab(self.create_service_tab(), 'Server')
        tabs.addTab(self.create_logs_tab(), 'Logs')

        layout.addWidget(tabs)

        return panel

    def create_connection_tab(self):
        """Create connection settings tab"""
        tab = QWidget()
        layout = QVBoxLayout(tab)

        # Server settings
        group = QGroupBox('Server Connection')
        group_layout = QVBoxLayout()

        # Host and port
        host_layout = QHBoxLayout()
        host_layout.addWidget(QLabel('Host:'))
        self.host_input = QComboBox()
        self.host_input.setEditable(True)
        self.host_input.addItems(['localhost', '127.0.0.1', '192.168.1.100'])
        host_layout.addWidget(self.host_input)

        port_layout = QHBoxLayout()
        port_layout.addWidget(QLabel('Port:'))
        self.port_input = QSpinBox()
        self.port_input.setRange(1024, 65535)
        self.port_input.setValue(8080)
        port_layout.addWidget(self.port_input)

        group_layout.addLayout(host_layout)
        group_layout.addLayout(port_layout)

        # Connect button
        self.connect_btn = QPushButton('Connect to Server')
        self.connect_btn.clicked.connect(self.toggle_connection)
        group_layout.addWidget(self.connect_btn)

        # Status
        self.connection_status = QLabel('⚫ Disconnected')
        self.connection_status.setStyleSheet('color: #ff6b6b; font-weight: bold;')
        group_layout.addWidget(self.connection_status)

        self.glass_status = QLabel('⚫ Glass: Not Connected')
        self.glass_status.setStyleSheet('color: #ff6b6b;')
        group_layout.addWidget(self.glass_status)

        group.setLayout(group_layout)
        layout.addWidget(group)

        layout.addStretch()
        return tab

    def create_control_tab(self):
        """Create Glass control tab"""
        tab = QWidget()
        layout = QVBoxLayout(tab)

        # Mode control
        mode_group = QGroupBox('Display Mode')
        mode_layout = QVBoxLayout()

        self.mode_selector = QComboBox()
        self.mode_selector.addItems([
            'Thermal Only',
            'Thermal + RGB Fusion',
            'Advanced Inspection'
        ])
        self.mode_selector.currentIndexChanged.connect(self.change_mode)
        mode_layout.addWidget(self.mode_selector)

        mode_group.setLayout(mode_layout)
        layout.addWidget(mode_group)

        # Capture controls
        capture_group = QGroupBox('Capture')
        capture_layout = QVBoxLayout()

        snapshot_btn = QPushButton('📷 Take Snapshot')
        snapshot_btn.clicked.connect(self.take_snapshot)
        capture_layout.addWidget(snapshot_btn)

        self.record_btn = QPushButton('⏺ Start Recording')
        self.record_btn.clicked.connect(self.toggle_recording)
        capture_layout.addWidget(self.record_btn)

        capture_group.setLayout(capture_layout)
        layout.addWidget(capture_group)

        # Detection navigation
        detection_group = QGroupBox('Detection Navigation')
        detection_layout = QVBoxLayout()

        nav_layout = QHBoxLayout()
        prev_btn = QPushButton('◀ Previous')
        prev_btn.clicked.connect(self.previous_detection)
        next_btn = QPushButton('Next ▶')
        next_btn.clicked.connect(self.next_detection)
        nav_layout.addWidget(prev_btn)
        nav_layout.addWidget(next_btn)

        detection_layout.addLayout(nav_layout)
        detection_group.setLayout(detection_layout)
        layout.addWidget(detection_group)

        # Overlay controls
        overlay_group = QGroupBox('Overlay')
        overlay_layout = QVBoxLayout()

        toggle_overlay_btn = QPushButton('Toggle Overlay')
        toggle_overlay_btn.clicked.connect(self.toggle_overlay)
        overlay_layout.addWidget(toggle_overlay_btn)

        overlay_group.setLayout(overlay_layout)
        layout.addWidget(overlay_group)

        layout.addStretch()
        return tab

    def create_recording_tab(self):
        """Create recording management tab"""
        tab = QWidget()
        layout = QVBoxLayout(tab)

        # Recording status
        status_group = QGroupBox('Recording Status')
        status_layout = QVBoxLayout()

        self.recording_status = QLabel('⚫ Not Recording')
        self.recording_status.setStyleSheet('font-weight: bold;')
        status_layout.addWidget(self.recording_status)

        self.recording_duration = QLabel('Duration: 00:00')
        status_layout.addWidget(self.recording_duration)

        self.recording_frames_count = QLabel('Frames: 0')
        status_layout.addWidget(self.recording_frames_count)

        status_group.setLayout(status_layout)
        layout.addWidget(status_group)

        # Saved recordings
        recordings_group = QGroupBox('Saved Recordings')
        recordings_layout = QVBoxLayout()

        self.recordings_list = QListWidget()
        self.recordings_list.itemDoubleClicked.connect(self.play_recording)
        recordings_layout.addWidget(self.recordings_list)

        list_buttons = QHBoxLayout()
        refresh_btn = QPushButton('Refresh')
        refresh_btn.clicked.connect(self.refresh_recordings)
        open_folder_btn = QPushButton('Open Folder')
        open_folder_btn.clicked.connect(lambda: os.startfile(Config.RECORDINGS_DIR))
        list_buttons.addWidget(refresh_btn)
        list_buttons.addWidget(open_folder_btn)

        recordings_layout.addLayout(list_buttons)
        recordings_group.setLayout(recordings_layout)
        layout.addWidget(recordings_group)

        return tab

    def create_service_tab(self):
        """Create server management tab"""
        tab = QWidget()
        layout = QVBoxLayout(tab)

        # Server control
        control_group = QGroupBox('Server Control')
        control_layout = QVBoxLayout()

        self.start_server_btn = QPushButton('▶ Start Server')
        self.start_server_btn.clicked.connect(self.start_server)
        control_layout.addWidget(self.start_server_btn)

        self.stop_server_btn = QPushButton('⏹ Stop Server')
        self.stop_server_btn.clicked.connect(self.stop_server)
        self.stop_server_btn.setEnabled(False)
        control_layout.addWidget(self.stop_server_btn)

        restart_btn = QPushButton('🔄 Restart Server')
        restart_btn.clicked.connect(self.restart_server)
        control_layout.addWidget(restart_btn)

        self.server_status = QLabel('⚫ Server: Stopped')
        self.server_status.setStyleSheet('font-weight: bold;')
        control_layout.addWidget(self.server_status)

        control_group.setLayout(control_layout)
        layout.addWidget(control_group)

        # Server info
        info_group = QGroupBox('Server Information')
        info_layout = QVBoxLayout()

        self.server_url_label = QLabel(f'URL: http://{Config.SERVER_HOST}:{Config.SERVER_PORT}')
        info_layout.addWidget(self.server_url_label)

        self.gpu_status = QLabel('GPU: Checking...')
        info_layout.addWidget(self.gpu_status)

        info_group.setLayout(info_layout)
        layout.addWidget(info_group)

        # Auto-start option
        autostart_check = QCheckBox('Auto-start server on launch')
        layout.addWidget(autostart_check)

        layout.addStretch()
        return tab

    def create_logs_tab(self):
        """Create logs display tab"""
        tab = QWidget()
        layout = QVBoxLayout(tab)

        self.log_display = QTextEdit()
        self.log_display.setReadOnly(True)
        self.log_display.setFont(QFont('Consolas', 9))
        layout.addWidget(self.log_display)

        buttons = QHBoxLayout()
        clear_btn = QPushButton('Clear')
        clear_btn.clicked.connect(self.log_display.clear)
        save_btn = QPushButton('Save Log')
        save_btn.clicked.connect(self.save_log)
        buttons.addWidget(clear_btn)
        buttons.addWidget(save_btn)
        buttons.addStretch()

        layout.addLayout(buttons)

        return tab

    def setup_statusbar(self):
        """Setup status bar"""
        self.statusBar = QStatusBar()
        self.setStatusBar(self.statusBar)
        self.statusBar.showMessage('Ready')

    def apply_dark_theme(self):
        """Apply dark theme to application"""
        dark_stylesheet = """
            QMainWindow, QWidget {
                background-color: #2b2b2b;
                color: #ffffff;
            }
            QGroupBox {
                border: 1px solid #3d3d3d;
                border-radius: 5px;
                margin-top: 10px;
                padding-top: 10px;
                font-weight: bold;
            }
            QGroupBox::title {
                subcontrol-origin: margin;
                left: 10px;
                padding: 0 5px;
            }
            QPushButton {
                background-color: #3d3d3d;
                border: 1px solid #555555;
                border-radius: 3px;
                padding: 6px;
                min-width: 80px;
            }
            QPushButton:hover {
                background-color: #4d4d4d;
            }
            QPushButton:pressed {
                background-color: #1e1e1e;
            }
            QPushButton:disabled {
                background-color: #2b2b2b;
                color: #666666;
            }
            QLineEdit, QSpinBox, QComboBox, QTextEdit {
                background-color: #1e1e1e;
                border: 1px solid #3d3d3d;
                border-radius: 3px;
                padding: 4px;
            }
            QTabWidget::pane {
                border: 1px solid #3d3d3d;
            }
            QTabBar::tab {
                background-color: #3d3d3d;
                padding: 8px 16px;
                border: 1px solid #555555;
            }
            QTabBar::tab:selected {
                background-color: #4d4d4d;
            }
            QListWidget {
                background-color: #1e1e1e;
                border: 1px solid #3d3d3d;
            }
        """
        self.setStyleSheet(dark_stylesheet)

    # ==================== Connection Methods ====================

    def toggle_connection(self):
        """Toggle connection to server"""
        if self.socket_client and self.socket_client.running:
            self.disconnect_from_server()
        else:
            self.connect_to_server()

    def connect_to_server(self):
        """Connect to the thermal AR server"""
        host = self.host_input.currentText()
        port = self.port_input.value()

        self.log(f'Connecting to server at {host}:{port}...')

        self.socket_client = SocketIOClient(host, port)
        self.socket_client.frame_received.connect(self.update_video)
        self.socket_client.status_update.connect(self.handle_status_update)
        self.socket_client.glass_connected.connect(self.handle_glass_connection)
        self.socket_client.start()

        self.connect_btn.setText('Disconnect')
        self.connection_status.setText('🟢 Connected')
        self.connection_status.setStyleSheet('color: #51cf66; font-weight: bold;')

    def disconnect_from_server(self):
        """Disconnect from server"""
        if self.socket_client:
            self.socket_client.stop()
            self.socket_client.wait()
            self.socket_client = None

        self.connect_btn.setText('Connect to Server')
        self.connection_status.setText('⚫ Disconnected')
        self.connection_status.setStyleSheet('color: #ff6b6b; font-weight: bold;')
        self.video_label.setText('No Video Feed\n\nConnect to server')
        self.log('Disconnected from server')

    def handle_status_update(self, event, message):
        """Handle status updates from socket client"""
        self.log(f'[{event}] {message}')
        self.statusBar.showMessage(message)

    def handle_glass_connection(self, connected):
        """Handle Glass connection status"""
        self.glass_connected = connected
        if connected:
            self.glass_status.setText('🟢 Glass: Connected')
            self.glass_status.setStyleSheet('color: #51cf66;')
            self.log('Glass device connected')
        else:
            self.glass_status.setText('⚫ Glass: Not Connected')
            self.glass_status.setStyleSheet('color: #ff6b6b;')
            self.log('Glass device disconnected')

    # ==================== Video Display ====================

    def update_video(self, frame, annotations):
        """Update video display with new frame"""
        if frame is None:
            return

        # Convert frame to QPixmap
        height, width, channel = frame.shape
        bytes_per_line = 3 * width
        q_image = QImage(frame.data, width, height, bytes_per_line, QImage.Format_RGB888).rgbSwapped()
        pixmap = QPixmap.fromImage(q_image)

        # Scale to fit label
        scaled_pixmap = pixmap.scaled(
            self.video_label.size(),
            Qt.KeepAspectRatio,
            Qt.SmoothTransformation
        )

        self.video_label.setPixmap(scaled_pixmap)

        # Update info labels
        self.mode_label.setText(f"Mode: {annotations.get('mode', 'N/A')}")
        num_detections = len(annotations.get('detections', []))
        self.detections_label.setText(f'Detections: {num_detections}')

        # Recording
        if self.is_recording:
            self.recording_frames.append((frame, annotations, time.time()))
            self.recording_frames_count.setText(f'Frames: {len(self.recording_frames)}')

    # ==================== Control Methods ====================

    def change_mode(self, index):
        """Change Glass display mode"""
        modes = ['thermal_only', 'thermal_rgb_fusion', 'advanced_inspection']
        if self.socket_client and index < len(modes):
            self.socket_client.send_command('set_mode', {'mode': modes[index]})
            self.log(f'Changed mode to: {modes[index]}')

    def take_snapshot(self):
        """Trigger snapshot capture on Glass"""
        if self.socket_client:
            self.socket_client.send_command('capture_snapshot')
            self.log('Snapshot capture triggered')
            self.statusBar.showMessage('Snapshot captured')

    def toggle_recording(self):
        """Toggle recording"""
        if not self.is_recording:
            self.start_recording()
        else:
            self.stop_recording()

    def start_recording(self):
        """Start recording session"""
        self.is_recording = True
        self.recording_frames = []
        self.recording_start_time = time.time()

        self.record_btn.setText('⏹ Stop Recording')
        self.recording_status.setText('🔴 Recording')
        self.recording_status.setStyleSheet('color: #ff6b6b; font-weight: bold;')

        self.log('Recording started')

    def stop_recording(self):
        """Stop recording and save"""
        if not self.is_recording:
            return

        self.is_recording = False
        duration = time.time() - self.recording_start_time

        self.record_btn.setText('⏺ Start Recording')
        self.recording_status.setText('⚫ Not Recording')
        self.recording_status.setStyleSheet('font-weight: bold;')

        # Save recording
        if self.recording_frames:
            filename = f"recording_{datetime.now().strftime('%Y%m%d_%H%M%S')}.mp4"
            filepath = Config.RECORDINGS_DIR / filename
            self.save_recording(filepath, self.recording_frames)
            self.log(f'Recording saved: {filename} ({len(self.recording_frames)} frames, {duration:.1f}s)')
            self.refresh_recordings()

        self.recording_frames = []

    def save_recording(self, filepath, frames):
        """Save recording to video file"""
        if not frames:
            return

        # Get dimensions from first frame
        first_frame = frames[0][0]
        height, width = first_frame.shape[:2]

        # Create video writer
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out = cv2.VideoWriter(str(filepath), fourcc, 30.0, (width, height))

        for frame, annotations, timestamp in frames:
            out.write(frame)

        out.release()

    def previous_detection(self):
        """Navigate to previous detection"""
        if self.socket_client:
            self.socket_client.send_command('previous_detection')

    def next_detection(self):
        """Navigate to next detection"""
        if self.socket_client:
            self.socket_client.send_command('next_detection')

    def toggle_overlay(self):
        """Toggle annotation overlay on Glass"""
        if self.socket_client:
            self.socket_client.send_command('toggle_overlay')

    # ==================== Recording Management ====================

    def refresh_recordings(self):
        """Refresh list of saved recordings"""
        self.recordings_list.clear()

        recordings = sorted(Config.RECORDINGS_DIR.glob('*.mp4'), reverse=True)
        for recording in recordings:
            size_mb = recording.stat().st_size / (1024 * 1024)
            item_text = f"{recording.name} ({size_mb:.1f} MB)"
            self.recordings_list.addItem(item_text)

    def play_recording(self, item):
        """Play selected recording"""
        filename = item.text().split(' (')[0]
        filepath = Config.RECORDINGS_DIR / filename
        os.startfile(filepath)

    # ==================== Server Management ====================

    def start_server(self):
        """Start the thermal AR server"""
        server_script = Path(__file__).parent / 'thermal_ar_server.py'

        if not server_script.exists():
            QMessageBox.warning(self, 'Error', 'Server script not found: thermal_ar_server.py')
            return

        try:
            self.server_process = subprocess.Popen(
                [sys.executable, str(server_script)],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                creationflags=subprocess.CREATE_NEW_CONSOLE if sys.platform == 'win32' else 0
            )

            self.start_server_btn.setEnabled(False)
            self.stop_server_btn.setEnabled(True)
            self.server_status.setText('🟢 Server: Running')
            self.server_status.setStyleSheet('color: #51cf66; font-weight: bold;')

            self.log('Server started')

            # Auto-connect after a delay
            QTimer.singleShot(2000, self.connect_to_server)

        except Exception as e:
            QMessageBox.critical(self, 'Error', f'Failed to start server: {e}')
            self.log(f'Error starting server: {e}')

    def stop_server(self):
        """Stop the thermal AR server"""
        if self.server_process:
            self.server_process.terminate()
            self.server_process.wait(timeout=5)
            self.server_process = None

            self.start_server_btn.setEnabled(True)
            self.stop_server_btn.setEnabled(False)
            self.server_status.setText('⚫ Server: Stopped')
            self.server_status.setStyleSheet('font-weight: bold;')

            self.log('Server stopped')

            # Disconnect client
            if self.socket_client:
                self.disconnect_from_server()

    def restart_server(self):
        """Restart the server"""
        self.stop_server()
        QTimer.singleShot(1000, self.start_server)

    # ==================== Logging ====================

    def log(self, message):
        """Add message to log display"""
        timestamp = datetime.now().strftime('%H:%M:%S')
        log_entry = f'[{timestamp}] {message}'
        self.log_display.append(log_entry)
        print(log_entry)

    def save_log(self):
        """Save log to file"""
        filename = f"log_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        filepath = Config.LOGS_DIR / filename

        with open(filepath, 'w') as f:
            f.write(self.log_display.toPlainText())

        QMessageBox.information(self, 'Log Saved', f'Log saved to:\n{filepath}')

    # ==================== Cleanup ====================

    def closeEvent(self, event):
        """Handle application close"""
        # Stop recording if active
        if self.is_recording:
            self.stop_recording()

        # Disconnect client
        if self.socket_client:
            self.disconnect_from_server()

        # Stop server
        if self.server_process:
            reply = QMessageBox.question(
                self,
                'Stop Server',
                'Stop the server before closing?',
                QMessageBox.Yes | QMessageBox.No,
                QMessageBox.Yes
            )
            if reply == QMessageBox.Yes:
                self.stop_server()

        event.accept()

# ==================== Main ====================

def main():
    """Main application entry point"""
    app = QApplication(sys.argv)
    app.setApplicationName('Glass AR Companion')
    app.setOrganizationName('ThermalAR')

    window = GlassCompanionApp()
    window.show()

    sys.exit(app.exec_())

if __name__ == '__main__':
    main()
