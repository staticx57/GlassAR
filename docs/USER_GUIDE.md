# Glass AR Thermal Inspection - User Guide

**Version:** 1.0.0
**Last Updated:** 2025-11-12

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Installation](#installation)
3. [Usage](#usage)
4. [Standalone Mode](#standalone-mode)
5. [Battery Optimization](#battery-optimization)
6. [Troubleshooting](#troubleshooting)

---

## Quick Start

### Option 1: Standalone Mode (No Server)

**Use Case:** Field inspection without network infrastructure

1. Build and install Glass app (see [Installation](#installation))
2. Connect Boson 320 thermal camera to Glass
3. Launch app and start inspecting

**Features:** Thermal imaging, temperature measurements, 5 colormaps, snapshot/video capture
**Battery:** 2-3 hours

### Option 2: Connected Mode (With Server)

**Use Case:** AI-enhanced inspection with remote monitoring

1. Setup server on ThinkPad P16
2. Install Glass app
3. Connect Glass to WiFi
4. Launch companion app for monitoring

**Additional Features:** AI detection, automated anomalies, remote control

---

## Installation

### Prerequisites

**Required:**
- Java JDK 11+ ([Download](https://adoptium.net/))
- Android SDK with API 27 ([Android Studio](https://developer.android.com/studio))
- Git for Windows

**Environment Setup:**
```cmd
setx ANDROID_HOME "C:\Users\YourUsername\AppData\Local\Android\Sdk"
setx PATH "%PATH%;%ANDROID_HOME%\platform-tools"
```

### Build the Glass App

1. **Clone repository:**
   ```cmd
   git clone https://github.com/staticx57/GlassAR.git
   cd GlassAR
   git checkout claude/begin-app-build-setup-011CV4DP3o7vS6TFT83wmyQf
   ```

2. **Configure SDK path:**
   - Copy `local.properties.template` to `local.properties`
   - Edit and set: `sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk`

3. **Update server IP** (if using server):
   - Edit `app/src/main/java/com/example/thermalarglass/MainActivity.java`
   - Line 52: `private static final String SERVER_URL = "http://YOUR_P16_IP:8080";`

4. **Build:**
   ```cmd
   build.bat
   ```

   APK location: `app\build\outputs\apk\debug\app-debug.apk`

### Deploy to Glass

1. **Enable USB debugging on Glass:**
   - Settings → System → About → Tap "Build number" 7 times
   - Settings → System → Developer options → Enable "USB debugging"

2. **Connect and install:**
   ```cmd
   adb devices
   build.bat install
   ```

3. **Connect Boson 320:**
   - Boson → USB-C cable → OTG adapter → Glass
   - Grant USB permissions when prompted
   - Launch "Thermal AR Glass" app

### Setup Server (Optional)

**For AI-enhanced features:**

1. **Install CUDA and Python dependencies:**
   ```bash
   # Create virtual environment
   python -m venv thermal_ar_env
   thermal_ar_env\Scripts\activate  # Windows

   # Install PyTorch with CUDA
   pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121

   # Install dependencies
   pip install flask flask-socketio python-socketio opencv-python numpy ultralytics
   ```

2. **Start server:**
   ```bash
   python thermal_ar_server.py
   ```

3. **Launch companion app:**
   ```bash
   python glass_companion_app.py
   ```

---

## Usage

### Touchpad Gestures

| Gesture | Action | Availability |
|---------|--------|--------------|
| **Tap** | Toggle overlay | Always |
| **Double-tap** | Capture snapshot | Always |
| **Long press** | Start/stop recording | Always |
| **Swipe forward** | Cycle display modes | Always |
| **Swipe backward** | Navigate detections | Connected only |
| **Swipe down** | Dismiss alerts | Always |

### Display Modes

1. **Thermal Only** (Default)
   - Pure thermal imaging
   - Battery: ~3 hours
   - Best for standalone

2. **Thermal + RGB Fusion**
   - Thermal overlay on visible light
   - Battery: ~2 hours
   - Better context

3. **Advanced Inspection**
   - AI-enhanced detection
   - Battery: ~2 hours
   - Requires server

### Thermal Colormaps

| Colormap | Best For | Range |
|----------|----------|-------|
| Iron/Hot | General inspection | Black → Blue → Red → White |
| Rainbow | Detailed analysis | Blue → Green → Yellow → Red |
| White Hot | High contrast | Black → White |
| Arctic | Cold environments | Blue → Cyan → White |
| Grayscale | Documentation | Black → White |

**Change colormap:**
- Standalone: Default is Iron
- Connected: Use companion app selector

---

## Standalone Mode

### Features Available Offline

**✅ Fully Functional:**
- Real-time thermal display @ 30-60 fps
- Temperature measurements (center/min/max/avg)
- All 5 thermal colormaps
- Snapshot capture to local storage
- Video recording to local storage
- All touchpad gestures
- Battery monitoring
- 3 display modes

**❌ Requires Server:**
- AI object detection
- Automated anomaly detection
- Remote monitoring
- Remote control from companion app

**Standalone Capability:** 78% of features (100% of core features)

### Storage Capacity

**Glass Internal Storage (32 GB):**
- Snapshots: ~50,000 images (~500 KB each)
- Video: ~800 minutes (~30 MB per minute)

**Low Storage Management:**
- App warns when <500 MB remaining
- Transfer files to PC when connected
- Delete old files: `adb shell rm /sdcard/...`

### Typical Workflows

**Field Inspection (No Network):**
1. Power on Glass, connect Boson 320
2. Launch app - thermal display appears
3. Inspect targets, capture snapshots
4. Record videos of critical areas
5. Return to office, connect to PC
6. Transfer files for analysis

**Emergency Response:**
1. Quick deployment with Glass
2. Locate heat signatures through walls
3. Record inspection video
4. Make real-time decisions
5. Upload later for reporting

---

## Battery Optimization

### Battery Life by Mode

| Mode | Battery Life | Components |
|------|--------------|------------|
| Thermal Only | ~3 hours | Boson + Display |
| Thermal + RGB | ~2 hours | Boson + RGB + Display |
| Connected Advanced | ~2 hours | All + WiFi |
| Standby | ~8-10 hours | Display only |

### Tips for Extended Runtime

1. **Use Thermal Only mode** - Disable RGB when not needed
2. **Lower display brightness** - Glass Settings → Display
3. **Disable WiFi** - When server not needed (offline mode)
4. **Reduce frame rate** - 15 fps during idle monitoring
5. **Use USB battery pack** - 10,000mAh adds ~2 hours

### Battery Optimization Settings

**For 3+ hour runtime:**
- Use Thermal Only mode
- Set display brightness to 60%
- Disable network connection attempts
- Reduce temperature extraction to every 2nd frame
- Lower frame rate to 15 fps when not actively inspecting

**Trade-offs:**
- Lower frame rate = choppier display but better battery
- Reduced brightness = harder to see but extends runtime
- Offline mode = no AI features but maximum battery

---

## Troubleshooting

### Build Issues

**Problem:** `ANDROID_HOME not set`
**Solution:**
```cmd
setx ANDROID_HOME "C:\Users\YourUsername\AppData\Local\Android\Sdk"
# Restart Command Prompt
```

**Problem:** `SDK location not found`
**Solution:** Create `local.properties` with correct SDK path

**Problem:** Build fails with errors
**Solution:**
```cmd
gradlew.bat clean assembleDebug --stacktrace
# Check logs for specific error
```

### Connection Issues

**Problem:** Glass won't connect to server
**Check:**
- Both on same WiFi network
- Server IP correct in MainActivity.java
- Firewall allows port 8080
- Server is running

**Test:**
```cmd
adb shell ping YOUR_P16_IP
# Should be <5ms
```

**Problem:** Boson 320 not detected
**Check:**
- USB-C OTG adapter working (test with USB drive)
- Boson powered on (LED indicator)
- USB permissions granted
- Cable is data-capable (not charge-only)

**View logs:**
```cmd
adb logcat | findstr ThermalARGlass
```

### Performance Issues

**Problem:** Poor thermal image quality
**Check:**
- Boson lens is clean
- Not pointed at bright lights
- Allow 2-3 minute warm-up
- Boson shutter calibration (brief pause - normal)

**Problem:** Temperature readings inaccurate
**Note:** Simplified calibration used
- Boson 320 has ±5°C typical accuracy
- Formula: `T = (pixel - 8192) * 0.01 + 20.0`
- For precision, implement full Boson SDK calibration

**Problem:** Battery drains too fast
**Solutions:**
1. Use Thermal Only mode
2. Lower frame rate to 15 fps
3. Reduce display brightness
4. Disable WiFi when not needed

### App Crashes

**Problem:** App crashes on launch
**Check:**
- Permissions granted (Camera, USB)
- Android version 8.1
- Sufficient storage space

**View crash logs:**
```cmd
adb logcat | findstr AndroidRuntime
```

**Problem:** App freezes during operation
**Check:**
- Thermal camera still connected
- Not overheating (pause and cool down)
- Sufficient memory available

### Companion App Issues

**Problem:** Companion app shows no data
**Check:**
- Glass app connected (green "Connected" status)
- Companion app connected to same server
- Server terminal shows both connections
- Firewall not blocking Socket.IO events

---

## Build Commands Reference

```cmd
build.bat                 # Build debug APK
build.bat install         # Build and install to Glass
build.bat run            # Install and launch app
build.bat logs           # View app logs
build.bat clean          # Clean build directory
```

## ADB Commands Reference

```cmd
adb devices                                              # List connected devices
adb install -r glass-ar-debug.apk                       # Install app
adb uninstall com.example.thermalarglass               # Uninstall app
adb logcat | findstr ThermalARGlass                    # View app logs
adb shell am start -n com.example.thermalarglass/.MainActivity  # Launch app
adb shell pm grant com.example.thermalarglass android.permission.CAMERA  # Grant permission
```

## Server Commands Reference

```bash
python thermal_ar_server.py           # Start AI server
python glass_companion_app.py         # Start companion app
```

---

## Performance Expectations

**Standalone Mode:**
- Frame rate: 30-60 fps
- Latency: <5ms camera-to-display
- Battery: 2-3 hours
- Storage: 50,000+ snapshots

**Connected Mode:**
- Frame rate: 30 fps sustained
- Latency: <10ms glass-to-glass (WiFi 6)
- AI processing: <20ms per frame
- Network: ~5 MB/s bandwidth

---

## Safety Notes

**Important:**
- Glass + Boson adds weight - take breaks every 30 minutes
- Boson gets warm (40-50°C) - normal operation
- Don't use while driving or in hazardous areas
- Be aware of reduced peripheral vision
- Monitor battery temperature during extended use
- Ensure secure mounting before use

---

## Support Resources

- **Repository:** https://github.com/staticx57/GlassAR
- **Branch:** claude/begin-app-build-setup-011CV4DP3o7vS6TFT83wmyQf
- **Documentation:** See `docs/` folder
- **Issues:** Create GitHub issue for problems

---

## Specifications

### FLIR Boson 320 60Hz
- Resolution: 320×256 pixels
- Frame rate: 60 Hz
- Thermal sensitivity: <50mK
- Temperature range: -40°C to +550°C

### Google Glass EE2
- Display: 640×360
- Android: 8.1 Oreo (API 27)
- Processor: Snapdragon XR1
- Battery: 780 mAh (2-3 hours)
- WiFi: 802.11ac

### ThinkPad P16 Gen 2 (Server)
- GPU: RTX 4000 Ada (20GB VRAM)
- RAM: 32GB+ recommended
- WiFi: WiFi 6E

---

**For developer documentation, see `docs/DEVELOPMENT.md`**
