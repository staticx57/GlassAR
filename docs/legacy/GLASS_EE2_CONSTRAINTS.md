# Google Glass Enterprise Edition 2 - Technical Constraints

## Device Overview

The Google Glass Enterprise Edition 2 (discontinued as of March 2023, support ended September 2023) is a specialized wearable AR device with specific technical limitations that must be respected during development.

---

## Critical Android Platform Constraints

### Android Version - **FIXED, CANNOT BE CHANGED**

```
OS Version:      Android 8.1 Oreo
API Level:       27 (FIXED)
Security Patch:  Final patch as of Sept 2023

⚠️ IMPORTANT: These values are IMMUTABLE
- compileSdk: 27
- minSdk: 27
- targetSdk: 27
```

### No Google Mobile Services (GMS)

Glass EE2 **does NOT include**:
- Google Play Services
- Play Store
- Firebase Cloud Messaging (FCM)
- Google Maps API
- Google Sign-In
- Any GMS-dependent libraries

**Development Impact:**
- Cannot use Firebase libraries
- Cannot use Play Services location
- Must use alternative authentication methods
- Must sideload APKs via `adb install`

### Development Kit (GDK) Status

- **GDK is DEPRECATED** on Glass EE2
- Use **standard Android SDK API 27** instead
- No special Glass-specific APIs
- Standard Android development patterns apply

---

## Hardware Specifications

### Processor & Memory

```
CPU:           Qualcomm Snapdragon XR1
Architecture:  ARM64-v8a, armeabi-v7a
RAM:           3 GB
Storage:       32 GB eMMC (not all available to apps)
```

**Performance Constraints:**
- Moderate CPU power - optimize for efficiency
- Limited RAM - be mindful of memory usage
- No high-end GPU - offload heavy processing to server

### Display

```
Type:           Optical see-through prism
Resolution:     640 x 360 pixels (right eye only)
Refresh Rate:   60 Hz
FOV:            ~23 degrees diagonal
Brightness:     Auto-adjusting

Display Coordinates: (0,0) = top-left, (640,360) = bottom-right
```

**UI Constraints:**
- Small display area - minimize UI elements
- High ambient light may reduce visibility
- User's primary view is the real world, not the screen
- Text must be large and high-contrast (minimum 14sp)

### Battery

```
Capacity:       780 mAh (integrated in frame)
Runtime:        ~2-3 hours typical use
                ~1-2 hours with thermal camera + processing
Charging:       USB-C, charges while in use
```

**Power Management:**
- Thermal camera + WiFi + processing = heavy drain
- Monitor battery level and warn user <20%
- Consider reducing processing rate on low battery
- Plan for tethered operation during long sessions

### Thermal Management

```
Operating Temp: 0°C to 35°C
Passive Cooling: Frame acts as heatsink
Thermal Limit:   Device throttles when hot
```

**Thermal Constraints for Our Use Case:**
- Adding Boson 320 camera increases heat generation
- Extended thermal imaging sessions cause device heating
- May experience throttling after 20-30 minutes
- Consider forced cooldown breaks

---

## Connectivity

### WiFi

```
Standards:      802.11ac (WiFi 5), 802.11ax (WiFi 6) capable
Bands:          2.4 GHz, 5 GHz
Recommended:    WiFi 6 (802.11ax) for best latency
Max Bandwidth:  ~300 Mbps theoretical
```

**Network Requirements for Thermal AR:**
- **Target latency**: <5ms on local network
- **Required bandwidth**: ~10-15 Mbps for thermal stream
- **Recommended setup**: Dedicated WiFi 6 network (Aruba AP)
- **802.11r fast roaming**: Essential for mobile use

### Bluetooth

```
Version:        Bluetooth 5.0
Profiles:       A2DP, HFP, HSP
Use Cases:      Audio, peripherals
```

**Not used in this project** (USB-C camera, WiFi for data)

### USB-C

```
Connector:      USB Type-C
Standards:      USB 3.1 Gen 1 (5 Gbps theoretical)
Host Mode:      Yes (OTG supported)
Power:          15W max draw for accessories
```

**Critical for Boson 320:**
- FLIR Boson 320 connects via USB-C
- UVCCamera library required for camera access
- Verify power draw <15W with camera + Glass
- USB-C cable routing important for ergonomics

---

## USB Camera Constraints (Boson 320)

### UVCCamera Library

```java
implementation 'com.herohan:UVCCamera:3.3.0'
```

**API 27 Compatibility:**
- UVCCamera 3.3.0 tested with API 27
- Some features may require `android.hardware.usb.host` permission
- Manual permission request needed (no runtime permission on API 27)

### Boson 320 Stream

```
Resolution:     320 x 256 pixels
Frame Rate:     60 Hz
Format:         16-bit grayscale (thermal)
Bandwidth:      ~19.6 MB/s
Pixel Format:   YUYV or RAW16
```

**Bandwidth Impact:**
- 60 fps @ 320x256x16-bit = ~19.6 MB/s from camera
- Process at 30 fps to reduce load
- Transmit at 30 fps to reduce WiFi bandwidth
- Base64 encoding adds ~33% overhead

---

## Software Development Constraints

### Libraries Compatible with API 27

#### ✅ **SAFE TO USE:**

```gradle
// Android Support Libraries (pre-AndroidX)
implementation 'com.android.support:appcompat-v7:27.1.1'
implementation 'com.android.support:design:27.1.1'

// Socket.IO (use v2.x for API 27 compatibility)
implementation 'io.socket:socket.io-client:2.0.1'

// JSON
implementation 'com.google.code.gson:gson:2.8.9'

// USB Camera
implementation 'com.herohan:UVCCamera:3.3.0'

// Logging
implementation 'com.jakewharton.timber:timber:5.0.1'
```

#### ❌ **CANNOT USE:**

```gradle
// AndroidX (requires API 28+)
implementation 'androidx.appcompat:appcompat:1.x.x'  // NO

// Jetpack Compose (requires API 21+, but Glass EE2 has issues)
implementation 'androidx.compose.*'  // NO

// CameraX (requires API 21+, but targets modern cameras)
implementation 'androidx.camera:camera-*'  // NO

// Any library requiring GMS
implementation 'com.google.android.gms:*'  // NO
implementation 'com.google.firebase:*'  // NO

// ML Kit (requires GMS)
implementation 'com.google.mlkit:*'  // NO
```

### Permissions Required

```xml
<!-- Manifest permissions for thermal AR app -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- USB Host (for Boson camera) -->
<uses-feature android:name="android.hardware.usb.host" />
```

---

## Architecture Constraints

### Processing Split

Due to Glass EE2 limitations, we use **thin client architecture**:

```
Glass EE2 (Client)          ThinkPad P16 (Server)
├─ Boson camera capture     ├─ YOLOv8 inference (GPU)
├─ Frame encoding (base64)  ├─ Thermal analysis
├─ Network transmission     ├─ Anomaly detection
├─ Annotation rendering     └─ Session recording
└─ UI display
```

**Why this architecture:**
- Glass EE2 lacks GPU for YOLOv8
- Limited CPU power for real-time ML
- Server has RTX 4000 Ada for fast inference
- Keeps Glass cool and power-efficient

### Latency Budget

```
Target end-to-end: <10ms (glass-to-glass)

Breakdown:
- Camera capture:     16.7ms (60 Hz frame)
- Encoding (base64):  ~2ms
- WiFi transmission:  <5ms (WiFi 6 on local network)
- Server processing:  <33ms (30 fps target)
- Annotation return:  <5ms
- Rendering:          16.7ms (60 Hz display)
```

**Optimization strategies:**
- Process every 2nd frame (30 fps)
- Cache annotations between frames
- Use WiFi 6 for low latency
- Optimize server processing <20ms

---

## Testing & Debugging

### ADB Access

```bash
# Connect Glass via USB-C (developer mode enabled)
adb devices

# Install APK
adb install -r app-debug.apk

# View logs
adb logcat -s ThermalARGlass

# Push files
adb push file.txt /sdcard/

# Shell access
adb shell
```

### Developer Mode

Enable via: **Settings → System → About → Tap "Build number" 7 times**

### Logging Best Practices

- Use `android.util.Log` with consistent TAG
- Filter logs in logcat: `adb logcat -s YourTAG`
- Avoid excessive logging (impacts performance)

---

## Physical Constraints

### Mounting Boson 320

```
Camera Weight:    ~10g (Boson 320 core)
Lens Weight:      ~5-15g (varies by lens)
Mount Material:   PETG or ABS (3D printed)
Total Added Mass: ~30-40g on right side
```

**Ergonomic concerns:**
- Right-side weight bias (camera mounted right)
- Comfort for extended wear (2+ hours)
- Cable management for USB-C
- Field of view alignment between Boson and Glass display

### Cable Routing

```
Boson → USB-C cable → Glass USB-C port

Options:
1. Direct connection (short cable, rigid)
2. Flexible cable routed along frame
3. Coiled cable with strain relief
```

---

## Known Limitations

### 1. **No On-Device ML**
- Glass cannot run YOLOv8 efficiently
- Server dependency required

### 2. **Single Eye Display**
- Right eye only (no stereoscopic AR)
- Depth perception relies on real-world view

### 3. **Limited Hands-Free Input**
- Touchpad on side (difficult while wearing)
- Voice commands require Google Assistant (not available)
- Alternative: Bluetooth remote or phone companion app

### 4. **No App Store**
- Must sideload via ADB
- No OTA update mechanism (build your own)

### 5. **Battery Life**
- 2-3 hours max with thermal system
- Plan for USB-C power bank or tethered operation

---

## Development Workflow

### Iteration Cycle

```
1. Edit code in Android Studio
2. Build APK: ./gradlew assembleDebug
3. Install: adb install -r app/build/outputs/apk/debug/app-debug.apk
4. Test on Glass
5. Monitor logs: adb logcat -s ThermalARGlass
6. Iterate
```

### Server Development

```
1. Edit thermal_ar_server.py
2. Ctrl+C to stop server
3. python thermal_ar_server.py
4. Test with Glass connected
```

---

## Safety & Compliance

### Thermal Camera Safety

- Boson 320 radiometric mode may require export license (ITAR/EAR)
- Ensure compliance if used for commercial purposes
- Privacy concerns: thermal imaging may capture people

### Electrical Safety

- USB-C power draw: monitor total <15W
- Overheating: implement thermal monitoring
- Battery safety: Glass has built-in protections

---

## Legacy Status Warning

⚠️ **IMPORTANT: Glass Enterprise Edition 2 is discontinued**

- Sales ended: March 15, 2023
- Support ended: September 15, 2023
- No security updates after September 2023
- Use for development/research only
- Do not deploy in production requiring security compliance

---

## References

- [Glass EE2 Developer Guide](https://developers.google.com/glass-enterprise)
- [Glass EE2 Samples (GitHub)](https://github.com/googlesamples/glass-enterprise-samples)
- [Android 8.1 API Reference](https://developer.android.com/sdk/api_diff/27/changes)
- [FLIR Boson Documentation](https://flir.custhelp.com/app/answers/detail/a_id/3501/)
- [UVCCamera Library](https://github.com/saki4510t/UVCCamera)

---

## Summary for Developers

**DO:**
- ✅ Use API 27 exclusively
- ✅ Use Android Support Libraries (not AndroidX)
- ✅ Test on actual Glass hardware
- ✅ Optimize for battery life
- ✅ Design for small, bright UI
- ✅ Offload heavy processing to server
- ✅ Plan for <10ms latency target

**DON'T:**
- ❌ Try to update Android version
- ❌ Use Google Play Services
- ❌ Use AndroidX libraries
- ❌ Run ML models on-device
- ❌ Expect long battery life with thermal camera
- ❌ Rely on cloud services (use local server)
- ❌ Deploy in security-critical production environments

---

**Last Updated:** 2025-11-11
**Document Version:** 1.0
**Author:** Claude Code Development System
