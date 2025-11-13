# FLIR Boson UVC Streaming Configuration Guide

## Problem Summary

The FLIR Boson camera was sending **MJPEG compressed video** instead of uncompressed radiometric (Y16) format. This caused all frames to be rejected as "unknown format" because:

- MJPEG frames have **variable sizes** (150KB-350KB depending on image complexity)
- Code expected **fixed-size** uncompressed frames:
  - Y16: 163,840 bytes (320×256×2 bytes/pixel)
  - I420: 491,520 bytes (640×512×1.5 bytes/pixel)

## Solution Implemented

### Changes Made (Commit a9263e3)

1. **MJPEG Format Detection**
   - Detects JPEG magic bytes (0xFF 0xD8) at frame start
   - Identifies MJPEG frames automatically

2. **MJPEG Frame Completion**
   - Uses UVC end-of-frame bit + JPEG EOI marker (0xFF 0xD9)
   - Handles variable-size frame boundaries correctly

3. **MJPEG Decompression**
   - Uses Android `BitmapFactory` to decompress JPEG frames
   - Applies thermal colormap to grayscale output

4. **Increased Buffer Size**
   - Frame buffer increased from 491KB to 1MB
   - Accommodates variable MJPEG frame sizes

## Testing the Fix

### Build and Deploy

```bash
# Build the APK
cd /path/to/GlassAR
./gradlew assembleDebug

# Install on Glass EE2
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor logs
adb logcat ThermalARGlass:I NativeUVCCamera:I *:S
```

### Expected Log Output

When streaming works correctly, you should see:

```
I ThermalARGlass: USB device connected, opening camera
I NativeUVCCamera: Successfully opened UVC camera
I NativeUVCCamera: ✓ MJPEG format detected (JPEG magic bytes found)
I NativeUVCCamera:   Boson is sending compressed MJPEG instead of uncompressed Y16/I420
I NativeUVCCamera:   Will use end-of-frame bit and JPEG EOI marker for frame detection
I NativeUVCCamera: ✓ MJPEG frame complete: 245632 bytes (EOF=true, EOI=true)
I ThermalARGlass: ✓ DETECTED: MJPEG format (JPEG compressed, variable size)
I ThermalARGlass:   ⚠ NOTE: MJPEG loses radiometric data! Use Boson serial port for temperatures
I ThermalARGlass: ✓ MJPEG decoded: 245632 bytes → 320×256 bitmap
I ThermalARGlass: ✓ Frame #1 rendered successfully to display
```

## IMPORTANT: Temperature Data Limitation

### MJPEG Format Drawback

**MJPEG compression LOSES radiometric temperature data!**

- JPEG compression converts 16-bit thermal values → 8-bit grayscale
- Accurate temperature readings are NOT available from MJPEG frames
- You will see thermal imagery but cannot extract precise temperatures

### Solutions for Temperature Data

You have two options:

#### Option 1: Configure Boson for Y16 Format (Recommended)

Configure the Boson to output **Y16 format (16-bit radiometric)** via the serial SDK:

**Python SDK Example:**
```python
import sys
sys.path.append('/path/to/GlassAR/3.0 IDD & SDK/SDK_USER_PERMISSIONS/SDK_USER_PERMISSIONS')
from ClientFiles_Python import Client_API as CamAPI

# Connect to Boson serial port
# Note: Find the serial port using 'adb shell ls /dev/tty*'
cam = CamAPI.pyClient(manualport="/dev/ttyUSB0")  # Adjust port as needed

# Set output format to IR16 (16-bit radiometric)
from ClientFiles_Python.EnumTypes import *

cam.dvoSetOutputFormat(FLR_DVO_IR16)
cam.dvoSetOutputIr16Format(FLR_DVO_IR16_16B)
cam.sysctrlSetUsbVideoIR16Mode(FLR_SYSCTRL_USBIR16_MODE_16)

# Verify settings
result, format = cam.dvoGetOutputFormat()
print(f"Output format: {format}")  # Should be FLR_DVO_IR16

# Save to camera flash (persists across reboots)
cam.bosonWriteDynamicHeaderToFlash()

cam.Close()
```

**After configuration:**
- Boson will output Y16 format via UVC
- App will auto-detect Y16 and extract radiometric temperatures
- Temperature readings will be accurate

#### Option 2: Use Serial Port for Temperature Queries

Query temperatures via serial SDK while using MJPEG for video:

**Python SDK Example:**
```python
from ClientFiles_Python import Client_API as CamAPI

cam = CamAPI.pyClient(manualport="/dev/ttyUSB0")

# Get spot meter temperature (center of image)
result, temp_kelvin = cam.bosonGetSpotMeterValue()
temp_celsius = temp_kelvin / 100.0 - 273.15
print(f"Center temperature: {temp_celsius:.1f}°C")

# Get min/max/avg temperatures
# (requires enabling statistics)
cam.roicEnableStatistics(1)  # Enable stats
result, min_val, max_val, avg_val = cam.roicGetStatistics()

cam.Close()
```

## Finding the Boson Serial Port

The Boson has **two USB interfaces**:
1. **UVC interface** - Video streaming (already working)
2. **Serial interface** - SDK command & control

### On Android/Glass EE2

```bash
# List USB serial devices
adb shell ls /dev/tty*USB*

# Common ports:
# - /dev/ttyUSB0
# - /dev/ttyUSB1
# - /dev/ttyACM0
```

### On Desktop (for testing SDK)

**Linux:**
```bash
ls /dev/ttyUSB*
# Usually /dev/ttyUSB0 or /dev/ttyUSB1
```

**Windows:**
```
Device Manager → Ports (COM & LPT)
# Look for "USB Serial Port (COMx)"
```

## SDK Documentation References

- **SDK Location:** `3.0 IDD & SDK/SDK_USER_PERMISSIONS/SDK_USER_PERMISSIONS/`
- **SDK README:** `SDK_README.txt`
- **Full Documentation:** `Boson_SDK_Documentation_rev300.pdf`
- **Python SDK:** `ClientFiles_Python/Client_API.py`
- **C SDK:** `ClientFiles_C/Client_API.h`

## Summary of SDK Commands

### Video Format Configuration

```python
# Set to Y16 radiometric format
cam.dvoSetOutputFormat(FLR_DVO_IR16)
cam.dvoSetOutputIr16Format(FLR_DVO_IR16_16B)
cam.sysctrlSetUsbVideoIR16Mode(FLR_SYSCTRL_USBIR16_MODE_16)

# Get current format
result, format = cam.dvoGetOutputFormat()
# Returns: FLR_DVO_IR16, FLR_DVO_RGB, FLR_DVO_YCBCR, etc.
```

### Temperature Queries

```python
# Spot meter (center pixel)
result, temp_k = cam.bosonGetSpotMeterValue()

# Statistics (min/max/avg)
cam.roicEnableStatistics(1)
result, min, max, avg = cam.roicGetStatistics()

# Camera serial number
result, sn = cam.bosonGetCameraSN()
```

### Flat Field Correction

```python
# Run FFC (improves image quality)
cam.bosonRunFFC()

# Set FFC mode
cam.bosonSetFFCMode(FLR_BOSON_MANUAL_FFC)  # Manual
cam.bosonSetFFCMode(FLR_BOSON_AUTO_FFC)    # Auto
```

## Next Steps

1. **Test MJPEG streaming** - Verify video works with current fix
2. **Locate serial port** - Find Boson's serial interface on Glass EE2
3. **Configure Y16 format** - Use SDK to set radiometric output
4. **Verify temperature extraction** - Check that temperature readings work

## Troubleshooting

### "No UVC camera detected"

- Check USB connection
- Verify device permissions: `adb shell ls -l /dev/bus/usb/*/*`
- Check logcat for USB attach events

### "MJPEG decode failed"

- Frame may be corrupted
- Check UVC transfer errors in logcat
- Try USB 2.0 port (not 3.0)

### "Cannot open serial port"

- Port may be in use by another process
- Check permissions: `adb shell ls -l /dev/ttyUSB*`
- Try different port numbers

### "Format negotiation failed"

- Camera may not support requested format
- Check camera capabilities via SDK
- Verify firmware version

## Additional Resources

- [FLIR Boson Datasheet](https://www.flir.com/products/boson/)
- [UVC 1.5 Specification](https://www.usb.org/document-library/video-class-v15-document-set)
- Android USB Host Documentation

---

**Created:** 2025-11-13
**Related Commit:** a9263e3 - Fix FLIR Boson UVC streaming - Add MJPEG support
