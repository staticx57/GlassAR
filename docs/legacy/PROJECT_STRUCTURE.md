# Thermal AR Glass - Project Structure & Quick Start

## Project Organization

```
thermal-ar-glass/
│
├── server/                          # ThinkPad P16 processing server
│   ├── thermal_ar_server.py         # Main processing server
│   ├── requirements.txt             # Python dependencies
│   ├── test_system.py               # Testing and calibration utility
│   └── models/                      # AI models directory
│       └── yolov8l.pt              # YOLOv8 model (auto-downloaded)
│
├── glass-client/                    # Google Glass Android app
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/example/thermalarglass/
│   │   │   │   └── MainActivity.java
│   │   │   └── res/
│   │   │       ├── layout/
│   │   │       │   └── activity_main.xml
│   │   │       └── values/
│   │   │           └── strings.xml
│   │   └── build.gradle             # Android build configuration
│   └── README.md                    # Glass client specific docs
│
├── hardware/                        # 3D printing and hardware
│   ├── boson_glass_mount.scad       # OpenSCAD mount design
│   ├── mount_v1.stl                 # Generated STL (after rendering)
│   └── assembly_instructions.pdf    # Assembly guide (create this)
│
├── network/                         # Network configuration
│   └── aruba_config_guide.md        # Aruba AP setup guide
│
├── docs/                            # Documentation
│   ├── README.md                    # Main documentation
│   ├── setup_guide.md               # Detailed setup instructions
│   └── troubleshooting.md           # Common issues and solutions
│
└── recordings/                      # Session recordings (created at runtime)
    └── [date]/                      # Sessions organized by date
        ├── thermal_video.mp4
        ├── annotations.json
        └── metadata.json
```

## Quick Start Guide

### Prerequisites Checklist

Before starting, ensure you have:

**Hardware:**
- [ ] Google Glass Enterprise Edition 2
- [ ] FLIR Boson 320 60Hz Core with lens
- [ ] ThinkPad P16 Gen 2 (or similar with RTX GPU)
- [ ] USB-C cable for Boson
- [ ] USB-C OTG adapter for Glass
- [ ] WiFi 6 network (Aruba APs recommended)
- [ ] 3D printer access (or printed parts)

**Software:**
- [ ] Windows 11 or Ubuntu 22.04 on ThinkPad
- [ ] CUDA 12.x installed
- [ ] Python 3.10+ installed
- [ ] Android Studio installed
- [ ] ADB tools installed
- [ ] OpenSCAD (for mount customization)

**Network:**
- [ ] Both devices on same network
- [ ] Port 8080 available
- [ ] Know your ThinkPad's IP address

### 30-Minute Setup (After Prerequisites)

#### Step 1: Server Setup (10 minutes)

```bash
# Clone or download project files
cd thermal-ar-glass/server

# Create virtual environment
python -m venv venv
source venv/bin/activate  # Linux/Mac
# OR: venv\Scripts\activate  # Windows

# Install dependencies
pip install -r requirements.txt

# Test system
python test_system.py
# Select option 1 for full diagnostic

# Note your server IP (will need for Glass app)
# Windows: ipconfig
# Linux: ip addr show
```

#### Step 2: Glass App (10 minutes)

```bash
# Open Android Studio
# File → Open → Select glass-client directory

# Edit MainActivity.java line 24:
# Change SERVER_URL to your ThinkPad IP:
private static final String SERVER_URL = "http://192.168.1.100:8080";

# Connect Glass via USB
adb devices

# Build and install
# Build → Build Bundle(s) / APK(s) → Build APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Step 3: Hardware Assembly (10 minutes if parts ready)

```bash
# If mount already printed:
1. Test fit frame clamp on Glass
2. Insert Boson into cage
3. Route USB-C cable
4. Attach to Glass

# If not printed yet:
1. Open boson_glass_mount.scad in OpenSCAD
2. Adjust parameters if needed
3. Export STL
4. Send to 3D printer (4-5 hour print)
5. Assemble when ready
```

### First Test Run

#### Terminal 1 (Server):
```bash
cd thermal-ar-glass/server
source venv/bin/activate
python thermal_ar_server.py
```

Expected output:
```
============================================================
Thermal AR Processing Server
ThinkPad P16 Gen 2 with RTX 4000 Ada
============================================================
Loading AI models...
Using GPU: NVIDIA RTX 4000 Ada Generation Laptop GPU
Starting server on port 8080...
Dashboard available at: http://localhost:8080
Waiting for Google Glass connection...
```

#### Glass:
1. Power on Glass
2. Connect to WiFi
3. Plug in Boson via OTG adapter
4. Launch "Thermal AR Glass" app
5. Grant USB permissions
6. Watch for "Connected to server" message

#### Verify:
- Server shows: "Glass client connected"
- Glass shows: Green "Connected" status
- Thermal feed appears on Glass display
- Server terminal shows processing stats

### Development Workflow

#### Making Changes to Server:

```bash
# Edit thermal_ar_server.py
# Ctrl+C to stop server
# python thermal_ar_server.py to restart
# Changes take effect immediately
```

#### Making Changes to Glass App:

```bash
# Edit Java code in Android Studio
# Build → Build Bundle(s) / APK(s) → Build APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
# App updates on Glass
```

#### Iterating Mount Design:

```bash
# Edit boson_glass_mount.scad
# Adjust parameters at top of file
# F6 in OpenSCAD to render
# Export STL
# Print updated version
```

## File Transfer to Claude Code

All code files have been created in `/mnt/user-data/outputs/` and are ready to download:

### Files Created:

1. **thermal_ar_server.py** - Main processing server (Python)
2. **MainActivity.java** - Glass client app (Android/Java)
3. **boson_glass_mount.scad** - 3D printable mount (OpenSCAD)
4. **requirements.txt** - Python dependencies
5. **build.gradle** - Android dependencies
6. **activity_main.xml** - Android UI layout
7. **README.md** - Complete documentation
8. **aruba_config_guide.md** - Network setup guide
9. **test_system.py** - Testing utilities

### Download Instructions:

Each file is available as a downloadable artifact. Click the links below each code file to download.

### Importing to Claude Code:

```bash
# After downloading files:

# 1. Create project directory
mkdir thermal-ar-glass
cd thermal-ar-glass

# 2. Organize downloaded files
mkdir -p server glass-client/app/src/main/java/com/example/thermalarglass
mkdir -p glass-client/app/src/main/res/layout hardware network

# 3. Move files to appropriate locations
mv thermal_ar_server.py server/
mv MainActivity.java glass-client/app/src/main/java/com/example/thermalarglass/
mv boson_glass_mount.scad hardware/
# ... etc

# 4. Initialize git repository
git init
git add .
git commit -m "Initial commit - Thermal AR Glass system"

# 5. Open in Claude Code
claude-code .
```

## Common Tasks

### Run Diagnostics
```bash
cd server
python test_system.py
# Select option 1
```

### Check Glass Logs
```bash
adb logcat | grep ThermalAR
```

### Monitor Network Performance
```bash
# From ThinkPad, ping Glass
ping [GLASS_IP]

# From Glass (via ADB)
adb shell ping [THINKPAD_IP]
```

### Benchmark Performance
```bash
cd server
python test_system.py
# Select option 2
```

### Update AI Model
```bash
cd server
# Download new model
# Replace in models/ directory
# Restart server
```

## Next Steps

After basic setup works:

1. **Calibrate thermal readings** - Use test_system.py option 3
2. **Train custom models** - Collect data during inspections
3. **Optimize network** - Follow aruba_config_guide.md
4. **Iterate mount design** - Print v2 with improvements
5. **Add features** - Voice commands, recording, reports

## Troubleshooting Quick Reference

**Glass won't connect:**
- Check both on same WiFi
- Verify server IP in Glass app
- Restart both server and Glass app
- Check firewall allows port 8080

**No thermal feed:**
- Check Boson USB connection
- Grant USB permissions on Glass
- Check ADB logs for errors
- Try unplugging/replugging Boson

**Low FPS:**
- Check GPU utilization on server
- Reduce model size (yolov8m instead of yolov8l)
- Check network latency (should be <10ms)
- Close other applications

**Poor detection accuracy:**
- More lighting needed
- Too much background clutter
- Model not trained for specific objects
- Consider training custom model

## Support Resources

- **Documentation:** See docs/README.md
- **Network Setup:** network/aruba_config_guide.md
- **Hardware:** hardware/ directory
- **Logs:** Check server terminal and adb logcat

## Version History

- **v1.0** - Initial release
  - Basic thermal streaming
  - Object detection (YOLOv8)
  - Building inspection mode
  - Electronics inspection mode
  - 3D printable mount design

- **Future versions:**
  - Voice commands
  - Recording system
  - Report generation
  - Multi-user support
  - Cloud integration

## Contributing

To add features or improvements:

1. Test on your hardware setup
2. Document changes in code comments
3. Update README.md with new features
4. Consider submitting improvements

## License

MIT License - Use freely for personal and commercial projects

## Acknowledgments

Built with:
- Google Glass EE2 platform
- FLIR Boson thermal imaging
- NVIDIA GPU acceleration
- Ultralytics YOLOv8
- Python, PyTorch, OpenCV
- Android SDK

---

**Ready to start?** Follow the Quick Start Guide above!

For detailed setup, see docs/README.md
For network optimization, see network/aruba_config_guide.md
For hardware assembly, see hardware/ directory
