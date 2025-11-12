"""
Testing and Calibration Utility for Thermal AR System
Use this script to test components individually and calibrate the system
"""

import cv2
import numpy as np
import torch
import time
import socket
import json
from datetime import datetime

class SystemTester:
    """Test individual components of the Thermal AR system"""
    
    def __init__(self):
        self.results = {}
        
    def test_gpu(self):
        """Test NVIDIA GPU availability and performance"""
        print("\n" + "="*60)
        print("GPU TEST")
        print("="*60)
        
        if not torch.cuda.is_available():
            print("❌ CUDA not available!")
            print("   Install CUDA toolkit and PyTorch with CUDA support")
            self.results['gpu'] = False
            return False
        
        print(f"✓ CUDA available")
        print(f"✓ GPU: {torch.cuda.get_device_name(0)}")
        print(f"✓ CUDA version: {torch.version.cuda}")
        print(f"✓ PyTorch version: {torch.__version__}")
        
        # Test GPU memory
        total_memory = torch.cuda.get_device_properties(0).total_memory / 1e9
        print(f"✓ Total VRAM: {total_memory:.1f} GB")
        
        # Test GPU speed with simple operation
        print("\nTesting GPU performance...")
        size = 1000
        x = torch.randn(size, size).cuda()
        
        start = time.time()
        for _ in range(100):
            y = torch.matmul(x, x)
        torch.cuda.synchronize()
        elapsed = time.time() - start
        
        gflops = (100 * 2 * size**3) / elapsed / 1e9
        print(f"✓ GPU performance: {gflops:.1f} GFLOPS")
        
        self.results['gpu'] = True
        return True
    
    def test_opencv(self):
        """Test OpenCV installation and CUDA support"""
        print("\n" + "="*60)
        print("OPENCV TEST")
        print("="*60)
        
        print(f"✓ OpenCV version: {cv2.__version__}")
        
        # Check CUDA support in OpenCV
        cuda_enabled = cv2.cuda.getCudaEnabledDeviceCount() > 0
        if cuda_enabled:
            print(f"✓ OpenCV CUDA support: Enabled")
            print(f"✓ CUDA devices: {cv2.cuda.getCudaEnabledDeviceCount()}")
        else:
            print(f"⚠ OpenCV CUDA support: Disabled (not critical)")
        
        self.results['opencv'] = True
        return True
    
    def test_yolo(self):
        """Test YOLOv8 model loading and inference"""
        print("\n" + "="*60)
        print("YOLO MODEL TEST")
        print("="*60)
        
        try:
            from ultralytics import YOLO
            
            print("Loading YOLOv8 model...")
            model = YOLO('yolov8l.pt')
            print("✓ Model loaded successfully")
            
            # Test inference speed
            test_image = np.random.randint(0, 255, (320, 256, 3), dtype=np.uint8)
            
            print("\nRunning inference speed test...")
            times = []
            for i in range(10):
                start = time.time()
                results = model(test_image, verbose=False)
                elapsed = (time.time() - start) * 1000
                times.append(elapsed)
                if i == 0:
                    print(f"First inference (includes warmup): {elapsed:.1f}ms")
            
            avg_time = np.mean(times[1:])  # Exclude first warmup run
            fps = 1000 / avg_time
            
            print(f"✓ Average inference time: {avg_time:.1f}ms")
            print(f"✓ Maximum FPS: {fps:.1f}")
            
            if avg_time < 33:  # Can do 30fps
                print(f"✓ Performance: Excellent (30+ FPS)")
            elif avg_time < 50:
                print(f"⚠ Performance: Good (20+ FPS)")
            else:
                print(f"⚠ Performance: Marginal (may need optimization)")
            
            self.results['yolo'] = True
            return True
            
        except Exception as e:
            print(f"❌ Error loading YOLO: {e}")
            self.results['yolo'] = False
            return False
    
    def test_network(self, glass_ip=None):
        """Test network connectivity and latency"""
        print("\n" + "="*60)
        print("NETWORK TEST")
        print("="*60)
        
        # Get local IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(('8.8.8.8', 80))
            local_ip = s.getsockname()[0]
        except:
            local_ip = 'Unable to determine'
        finally:
            s.close()
        
        print(f"✓ Server IP: {local_ip}")
        print(f"  (Use this IP in Glass app configuration)")
        
        if glass_ip:
            print(f"\nTesting connectivity to Glass at {glass_ip}...")
            import platform
            import subprocess
            
            param = '-n' if platform.system().lower() == 'windows' else '-c'
            command = ['ping', param, '10', glass_ip]
            
            try:
                output = subprocess.check_output(command, stderr=subprocess.STDOUT, universal_newlines=True)
                
                # Parse ping output for latency
                if 'Average' in output or 'avg' in output:
                    print("✓ Glass is reachable")
                    print("\nPing statistics:")
                    for line in output.split('\n'):
                        if 'Average' in line or 'avg' in line or 'min/avg/max' in line:
                            print(f"  {line.strip()}")
                else:
                    print("✓ Glass is reachable")
                
            except subprocess.CalledProcessError:
                print(f"❌ Unable to reach Glass at {glass_ip}")
                print("   Verify Glass is on network and IP is correct")
                self.results['network'] = False
                return False
        
        # Test port 8080 is not in use
        test_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        test_socket.settimeout(1)
        result = test_socket.connect_ex(('localhost', 8080))
        test_socket.close()
        
        if result == 0:
            print("⚠ Port 8080 is already in use")
            print("   Stop other services or change port in server.py")
        else:
            print("✓ Port 8080 is available")
        
        self.results['network'] = True
        return True
    
    def run_all_tests(self, glass_ip=None):
        """Run all system tests"""
        print("\n" + "="*70)
        print(" THERMAL AR SYSTEM DIAGNOSTIC TEST")
        print("="*70)
        print(f"Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        
        self.test_gpu()
        self.test_opencv()
        self.test_yolo()
        self.test_network(glass_ip)
        
        # Summary
        print("\n" + "="*70)
        print(" TEST SUMMARY")
        print("="*70)
        
        all_passed = all(self.results.values())
        
        for test, passed in self.results.items():
            status = "✓ PASS" if passed else "❌ FAIL"
            print(f"{test.upper():20s}: {status}")
        
        print("="*70)
        if all_passed:
            print("✓ All tests passed! System is ready.")
        else:
            print("❌ Some tests failed. Review errors above.")
        print("="*70 + "\n")
        
        return all_passed


class ThermalCalibrator:
    """Calibrate Boson 320 thermal readings"""
    
    def __init__(self):
        self.calibration_data = {
            'offset': 0,
            'scale': 0.01,
            'reference_temp': 20.0
        }
    
    def calibrate_with_reference(self, raw_frame, known_temp):
        """
        Calibrate using a known reference temperature
        
        Args:
            raw_frame: Raw 16-bit thermal frame from Boson
            known_temp: Known temperature in Celsius (e.g., from IR thermometer)
        """
        print(f"\nCalibrating with reference temperature: {known_temp}°C")
        
        # Get median value from center region (assuming reference is in center)
        h, w = raw_frame.shape
        center_region = raw_frame[h//4:3*h//4, w//4:3*w//4]
        median_raw = np.median(center_region)
        
        print(f"Median raw value in center: {median_raw}")
        
        # Calculate offset to match known temperature
        self.calibration_data['offset'] = known_temp - (median_raw * self.calibration_data['scale'])
        
        print(f"Calibration complete:")
        print(f"  Scale: {self.calibration_data['scale']}")
        print(f"  Offset: {self.calibration_data['offset']:.2f}°C")
        
        return self.calibration_data
    
    def save_calibration(self, filename='boson_calibration.json'):
        """Save calibration to file"""
        with open(filename, 'w') as f:
            json.dump(self.calibration_data, f, indent=2)
        print(f"Calibration saved to {filename}")
    
    def load_calibration(self, filename='boson_calibration.json'):
        """Load calibration from file"""
        try:
            with open(filename, 'r') as f:
                self.calibration_data = json.load(f)
            print(f"Calibration loaded from {filename}")
            return self.calibration_data
        except FileNotFoundError:
            print(f"No calibration file found at {filename}")
            return None
    
    def apply_calibration(self, raw_frame):
        """Apply calibration to raw thermal frame"""
        temp_celsius = (raw_frame.astype(np.float32) * self.calibration_data['scale'] + 
                       self.calibration_data['offset'])
        return temp_celsius


class LatencyBenchmark:
    """Benchmark end-to-end latency"""
    
    def __init__(self):
        self.measurements = []
    
    def measure_processing_latency(self, processor, test_frames=100):
        """Measure processing latency"""
        print("\n" + "="*60)
        print("PROCESSING LATENCY BENCHMARK")
        print("="*60)
        
        # Generate test frames (320x256 thermal)
        test_frame = np.random.randint(0, 16384, (256, 320), dtype=np.uint16)
        
        latencies = []
        
        print(f"Processing {test_frames} test frames...")
        for i in range(test_frames):
            start = time.time()
            
            # Simulate processing pipeline
            result = processor.process_frame(test_frame.tobytes(), mode='building')
            
            latency = (time.time() - start) * 1000  # Convert to ms
            latencies.append(latency)
            
            if (i + 1) % 20 == 0:
                print(f"  Processed {i+1}/{test_frames} frames...")
        
        # Statistics
        print(f"\nResults:")
        print(f"  Mean latency:   {np.mean(latencies):.2f}ms")
        print(f"  Median latency: {np.median(latencies):.2f}ms")
        print(f"  Min latency:    {np.min(latencies):.2f}ms")
        print(f"  Max latency:    {np.max(latencies):.2f}ms")
        print(f"  Std deviation:  {np.std(latencies):.2f}ms")
        
        # Check if can achieve target FPS
        mean_latency = np.mean(latencies)
        target_30fps = 33.3
        target_60fps = 16.7
        
        print(f"\nPerformance assessment:")
        if mean_latency < target_60fps:
            print(f"  ✓ Can achieve 60 FPS (latency < {target_60fps}ms)")
        elif mean_latency < target_30fps:
            print(f"  ✓ Can achieve 30 FPS (latency < {target_30fps}ms)")
        else:
            print(f"  ⚠ May struggle to maintain 30 FPS")
            print(f"    Consider using smaller YOLO model or optimizations")
        
        return latencies


def main():
    """Main test runner"""
    print("""
    ╔════════════════════════════════════════════════════════════════╗
    ║         THERMAL AR SYSTEM - TEST & CALIBRATION UTILITY         ║
    ╚════════════════════════════════════════════════════════════════╝
    """)
    
    print("\nSelect test mode:")
    print("1. Run all diagnostic tests")
    print("2. Benchmark processing latency")
    print("3. Thermal calibration (requires Boson connected)")
    print("4. Network connectivity test")
    print("5. Exit")
    
    choice = input("\nEnter choice (1-5): ").strip()
    
    if choice == '1':
        # Full diagnostic
        tester = SystemTester()
        glass_ip = input("Enter Glass IP address (or press Enter to skip): ").strip()
        tester.run_all_tests(glass_ip if glass_ip else None)
    
    elif choice == '2':
        # Latency benchmark
        print("\nStarting latency benchmark...")
        print("Note: This requires the server to be configured.")
        
        try:
            from thermal_ar_server import Boson320Processor
            processor = Boson320Processor()
            
            benchmark = LatencyBenchmark()
            benchmark.measure_processing_latency(processor)
        except ImportError:
            print("Error: Cannot import thermal_ar_server.py")
            print("Make sure the file is in the same directory.")
    
    elif choice == '3':
        # Thermal calibration
        print("\nThermal Calibration")
        print("="*60)
        print("Instructions:")
        print("1. Point Boson at object with known temperature")
        print("2. Measure temperature with IR thermometer")
        print("3. Enter the known temperature below")
        print()
        
        calibrator = ThermalCalibrator()
        
        # This would need actual Boson frame - placeholder for now
        print("Note: This requires Boson to be connected and streaming.")
        print("Implement actual frame capture in production version.")
        
    elif choice == '4':
        # Network test only
        tester = SystemTester()
        glass_ip = input("Enter Glass IP address: ").strip()
        tester.test_network(glass_ip)
    
    else:
        print("Exiting...")


if __name__ == '__main__':
    main()
