# Glass EE2 Testing Checklist

**Application:** Glass AR Thermal Inspection System
**Version:** 1.0.0 with Priority 1-3 fixes
**Target Device:** Google Glass Enterprise Edition 2
**Date:** 2025-11-13

---

## Pre-Testing Setup

### Hardware Required
- ✅ Google Glass Enterprise Edition 2
- ✅ FLIR Boson 320 thermal camera
- ✅ USB-C OTG adapter
- ✅ USB cable for ADB connection
- ✅ WiFi network (for connected mode testing)
- ✅ Thermal targets for testing (hot/cold objects)

### Software Setup
- [ ] Enable USB debugging on Glass EE2 (Settings → Developer options)
- [ ] Install ADB on computer
- [ ] Charge Glass EE2 to 100%
- [ ] Connect to WiFi network

### Installation
```bash
# Connect Glass to computer via USB
adb devices

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Grant permissions (if needed)
adb shell pm grant com.example.thermalarglass android.permission.CAMERA

# Launch app
adb shell am start -n com.example.thermalarglass/.MainActivity
```

---

## Priority 1: Runtime Permissions Testing ✅

### Test 1.1: First Launch Permission Request
**Expected:** Permission dialog appears on first launch

- [ ] Launch app for first time
- [ ] Permission dialog shows "Allow Thermal AR Glass to take pictures and record video?"
- [ ] Tap "Allow"
- [ ] Permission granted toast appears
- [ ] App continues to main screen

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 1.2: Permission Denial Handling
**Expected:** Alert shown with instructions, app doesn't crash

- [ ] Uninstall app: `adb uninstall com.example.thermalarglass`
- [ ] Reinstall app
- [ ] Launch app
- [ ] Tap "Deny" on permission dialog
- [ ] Alert area appears with "⚠ Camera permission required" message
- [ ] App doesn't crash
- [ ] Can still access settings

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 1.3: Permission Grant from Settings
**Expected:** App works after granting permission in settings

- [ ] Go to Settings → Apps → Thermal AR Glass → Permissions
- [ ] Grant Camera permission
- [ ] Return to app
- [ ] Restart app
- [ ] App works normally

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Priority 2: Snapshot Capture Testing ✅

### Test 2.1: Snapshot via Camera Button
**Expected:** Snapshot saved to Pictures/ThermalAR/

- [ ] Connect Boson 320 camera
- [ ] Verify thermal view appears
- [ ] Press Glass camera button
- [ ] Processing indicator appears briefly
- [ ] Camera shutter sound plays
- [ ] Haptic feedback felt
- [ ] Toast shows "Snapshot saved: thermal_YYYYMMDD_HHMMSS.png"

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 2.2: Snapshot via Double-Tap Gesture
**Expected:** Same as camera button

- [ ] Double-tap on touchpad
- [ ] Processing indicator appears
- [ ] Camera shutter sound plays
- [ ] Haptic feedback felt
- [ ] Toast shows filename

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 2.3: Verify Snapshot Files
**Expected:** PNG files saved with annotations

```bash
# Pull snapshots from Glass
adb shell ls /sdcard/Android/data/com.example.thermalarglass/files/Pictures/ThermalAR/
adb pull /sdcard/Android/data/com.example.thermalarglass/files/Pictures/ThermalAR/ ./snapshots/
```

- [ ] Files exist in ThermalAR directory
- [ ] Filenames format: thermal_YYYYMMDD_HHMMSS.png
- [ ] PNG files open correctly on computer
- [ ] Images are 640×360 resolution
- [ ] Thermal colormap visible
- [ ] Annotations included (if any from server)

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 2.4: Snapshot Without Thermal Camera
**Expected:** Toast shows "No frame available"

- [ ] Disconnect Boson camera
- [ ] Wait for RGB fallback
- [ ] Try to take snapshot
- [ ] Toast shows "No frame available to capture" OR snapshot of RGB view

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Priority 2: Video Recording Testing ✅

### Test 3.1: Start/Stop Recording
**Expected:** Frame sequence saved to Movies/ThermalAR/

- [ ] Connect Boson 320 camera
- [ ] Long press on touchpad to start recording
- [ ] Recording indicator (red dot) appears
- [ ] Toast shows "Recording started"
- [ ] Haptic feedback felt
- [ ] Wait 10 seconds
- [ ] Long press again to stop
- [ ] Recording indicator disappears
- [ ] Toast shows "Recording saved: recording_YYYYMMDD_HHMMSS\nXX frames (~X.Xs)"

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 3.2: Recording Progress Updates
**Expected:** Progress toast every ~3 seconds

- [ ] Start recording
- [ ] Wait 3 seconds
- [ ] Toast shows "Recording: 30 frames (~3.0s)"
- [ ] Wait another 3 seconds
- [ ] Toast shows "Recording: 60 frames (~6.0s)"
- [ ] Stop recording

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 3.3: Verify Recording Files
**Expected:** Frame sequence + info file

```bash
# List recording directories
adb shell ls /sdcard/Android/data/com.example.thermalarglass/files/Movies/ThermalAR/

# Pull recording
adb pull /sdcard/Android/data/com.example.thermalarglass/files/Movies/ThermalAR/recording_YYYYMMDD_HHMMSS/ ./recordings/

# Convert to video on desktop (requires ffmpeg)
cd recordings/recording_YYYYMMDD_HHMMSS/
ffmpeg -framerate 10 -pattern_type glob -i 'frame_*.png' -c:v libx264 -pix_fmt yuv420p output.mp4
```

- [ ] Recording directory exists
- [ ] Directory name: recording_YYYYMMDD_HHMMSS
- [ ] Contains frame_000000.png, frame_000001.png, etc.
- [ ] Contains recording_info.txt
- [ ] Info file shows correct frame count
- [ ] Info file includes ffmpeg command
- [ ] PNG frames are 640×360
- [ ] Frames show thermal view + annotations
- [ ] ffmpeg conversion works (if tested on desktop)

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Priority 3: USB Error Recovery Testing ✅

### Test 4.1: USB Disconnect During Normal Operation
**Expected:** Alert shown, RGB fallback starts

- [ ] Connect Boson camera
- [ ] Verify thermal view active
- [ ] Disconnect USB cable
- [ ] Alert area appears: "⚠ Thermal camera disconnected"
- [ ] After 1 second, RGB camera starts
- [ ] Toast shows "Switched to RGB camera"
- [ ] Mode indicator shows "RGB Camera"

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 4.2: USB Disconnect During Recording
**Expected:** Recording stops, files saved

- [ ] Connect Boson camera
- [ ] Start recording (long press)
- [ ] Wait 5 seconds
- [ ] Disconnect USB cable
- [ ] Toast shows "Recording stopped - USB disconnected"
- [ ] Recording indicator disappears
- [ ] Alert area shows disconnect message
- [ ] Recording files saved (verify with adb pull)

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 4.3: USB Reconnection
**Expected:** Thermal camera restarts, alert dismissed

- [ ] With USB disconnected and RGB active
- [ ] Reconnect Boson camera
- [ ] Wait for USB permission dialog (if appears)
- [ ] Grant permission
- [ ] RGB camera stops
- [ ] Thermal camera starts
- [ ] Alert area disappears
- [ ] Toast shows "Thermal camera connected"

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 4.4: Hot-Swap Multiple Times
**Expected:** Graceful switching without crashes

- [ ] Connect Boson → thermal view
- [ ] Disconnect → RGB fallback
- [ ] Reconnect → thermal view
- [ ] Disconnect → RGB fallback
- [ ] Reconnect → thermal view
- [ ] No crashes observed
- [ ] All transitions smooth

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Glass EE2 Hardware Feature Testing

### Test 5.1: All Touchpad Gestures
**Expected:** All 8 gestures work correctly

| Gesture | Action | Expected Result | Pass/Fail |
|---------|--------|----------------|-----------|
| Single tap | Toggle overlay | Reticle shows/hides, toast appears | [ ] |
| Double tap | Snapshot | Snapshot saved, sound plays | [ ] |
| Long press | Recording | Starts/stops recording | [ ] |
| Swipe forward | Cycle modes | Thermal Only → RGB Fusion → Advanced → repeat | [ ] |
| Swipe backward | Cycle colormaps | Iron → Rainbow → White Hot → Arctic → Grayscale → repeat | [ ] |
| Swipe down | Dismiss alert | Alert area hides | [ ] |
| Swipe up | Open settings | SettingsActivity opens | [ ] |
| Camera button | Snapshot | Snapshot saved, sound plays | [ ] |

**Notes:** _____________________________

### Test 5.2: Haptic Feedback
**Expected:** Vibration felt on all gestures

- [ ] Tap - haptic feedback
- [ ] Double tap - haptic feedback (on snapshot)
- [ ] Long press - haptic feedback (on recording toggle)
- [ ] Swipe forward - haptic feedback
- [ ] Swipe backward - haptic feedback
- [ ] Swipe down - haptic feedback
- [ ] Swipe up - haptic feedback

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 5.3: Display Rendering (640×360)
**Expected:** Proper scaling, no distortion

- [ ] Thermal view fills screen correctly
- [ ] No black bars or stretching
- [ ] Text readable
- [ ] Colormap applied correctly
- [ ] Annotations visible (if server connected)

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 5.4: Battery Monitoring
**Expected:** Battery indicator updates

- [ ] Battery indicator visible
- [ ] Shows percentage
- [ ] Color changes: Green (>80%), Yellow (>50%), Orange (>20%), Red (<20%)
- [ ] Low battery warning at 20%

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Settings Menu Testing

### Test 6.1: Open Settings via Swipe Up
**Expected:** Settings activity opens

- [ ] Swipe up on touchpad
- [ ] Haptic feedback felt
- [ ] SettingsActivity appears
- [ ] Landscape orientation maintained

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 6.2: Server URL Configuration
**Expected:** Server URL persists

- [ ] Open settings
- [ ] Enter new server URL: http://192.168.1.200:8080
- [ ] Tap Save
- [ ] Toast shows "Settings saved! Restart app to apply changes"
- [ ] Close app
- [ ] Reopen app
- [ ] Check logs for new server URL

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 6.3: Settings Configuration via Vysor
**Expected:** Remote keyboard works

If you have Vysor + Bluetooth keyboard:
- [ ] Open Vysor connection to Glass
- [ ] Open settings
- [ ] Click in Server URL field
- [ ] Type using keyboard
- [ ] Characters appear
- [ ] Save works

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## RGB Camera Fallback Testing

### Test 7.1: Auto-Fallback on No Thermal Camera
**Expected:** RGB camera starts after 2 seconds

- [ ] Disconnect Boson camera
- [ ] Launch app
- [ ] Wait 2 seconds
- [ ] RGB camera preview appears
- [ ] Mode indicator shows "RGB Camera"
- [ ] Center temperature shows "--°C"
- [ ] Frame counter shows "RGB Mode"

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 7.2: RGB Fallback Disabled in Settings
**Expected:** No fallback, just black screen

- [ ] Open settings
- [ ] Uncheck "RGB Fallback" checkbox
- [ ] Save
- [ ] Restart app (without Boson connected)
- [ ] Wait 2 seconds
- [ ] No RGB camera starts
- [ ] Screen remains black or shows error

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Thermal Colormap Testing

### Test 8.1: All 5 Colormaps
**Expected:** Swipe backward cycles through all

- [ ] Connect Boson camera
- [ ] Verify thermal view active
- [ ] Swipe backward → Toast "Colormap: rainbow"
- [ ] Colormap changes to rainbow (blue→green→yellow→red)
- [ ] Swipe backward → Toast "Colormap: white_hot"
- [ ] Colormap changes to grayscale
- [ ] Swipe backward → Toast "Colormap: arctic"
- [ ] Colormap changes to blue/cyan/white
- [ ] Swipe backward → Toast "Colormap: grayscale"
- [ ] Colormap changes to monochrome
- [ ] Swipe backward → Toast "Colormap: iron"
- [ ] Colormap back to default (black→blue→red→white)

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Connected Mode Testing (Optional)

### Test 9.1: Server Connection
**Expected:** Connects to thermal_ar_server.py

**Prerequisites:** Server running on 192.168.1.100:8080

- [ ] Start server on network
- [ ] Launch Glass app
- [ ] Connection status shows "Connected" in green
- [ ] Network indicator appears
- [ ] Frames sent to server (check server logs)

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 9.2: Annotations from Server
**Expected:** Bounding boxes drawn on detections

- [ ] Point camera at detectable object
- [ ] Wait for server processing
- [ ] Bounding boxes appear on display
- [ ] Labels show class name + confidence
- [ ] Colors: Green (>80%), Yellow (>50%), Gray (<50%)

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Battery Life Testing

### Test 10.1: Thermal Only Mode
**Expected:** ~3 hours battery life

- [ ] Charge Glass to 100%
- [ ] Launch app with Boson camera
- [ ] Record start time
- [ ] Let run continuously
- [ ] Record battery percentage every 30 minutes
- [ ] Note time when battery reaches 0%

**Battery Life:** __________ hours

**Notes:** _____________________________

### Test 10.2: Thermal + RGB Fusion Mode
**Expected:** ~2 hours battery life

- [ ] Charge Glass to 100%
- [ ] Launch app, swipe forward to RGB Fusion mode
- [ ] Record start time
- [ ] Let run continuously
- [ ] Record battery percentage every 30 minutes

**Battery Life:** __________ hours

**Notes:** _____________________________

---

## Performance Testing

### Test 11.1: Frame Rate
**Expected:** 30-60 fps sustained

- [ ] Connect Boson camera
- [ ] Monitor frame counter
- [ ] Calculate FPS over 10 seconds
- [ ] Verify smooth playback (no stuttering)

**Measured FPS:** __________ fps

**Notes:** _____________________________

### Test 11.2: Thermal Display Latency
**Expected:** <5ms camera-to-display

- [ ] Move camera rapidly
- [ ] Observe display lag
- [ ] Compare to expected real-time performance

**Latency:** Negligible / Noticeable / Significant

**Notes:** _____________________________

---

## Edge Cases & Stress Testing

### Test 12.1: Rapid Gestures
**Expected:** No crashes or UI freezes

- [ ] Rapidly swipe forward/backward 10 times
- [ ] No crashes
- [ ] No UI freezes
- [ ] All gestures processed

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 12.2: Low Battery (<10%)
**Expected:** Warnings shown, graceful degradation

- [ ] Run until battery <10%
- [ ] Low battery warnings appear
- [ ] App continues functioning
- [ ] No unexpected crashes

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 12.3: WiFi Disconnect During Connected Mode
**Expected:** App continues in standalone mode

- [ ] Connect to server
- [ ] Disable WiFi
- [ ] Connection status shows "Disconnected" in red
- [ ] App continues working (standalone mode)
- [ ] No crashes

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

### Test 12.4: Snapshot Storage Full
**Expected:** Error message shown

- [ ] Fill storage to near capacity
- [ ] Try to take snapshot
- [ ] Error toast appears
- [ ] App doesn't crash

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Regression Testing

### Test 13.1: All Previous Features Still Work
**Expected:** No regressions from fixes

- [ ] Temperature display updates
- [ ] Battery monitoring works
- [ ] Network indicator shows WiFi strength
- [ ] Mode switching works
- [ ] Alert area functions
- [ ] All UI elements visible
- [ ] Socket.IO connection works
- [ ] Server communication works

**Result:** __________ (PASS/FAIL)
**Notes:** _____________________________

---

## Test Summary

**Date Tested:** __________
**Tester Name:** __________
**Glass EE2 Serial:** __________
**APK Version:** __________

**Total Tests:** 60+
**Tests Passed:** __________
**Tests Failed:** __________
**Tests Skipped:** __________

**Pass Rate:** __________%

**Critical Issues Found:**
1. _____________________________
2. _____________________________
3. _____________________________

**Non-Critical Issues:**
1. _____________________________
2. _____________________________
3. _____________________________

**Recommendations:**
- _____________________________
- _____________________________
- _____________________________

---

## Build Commands for Testing

```bash
# Build debug APK
./gradlew assembleDebug

# Install to Glass
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat -s ThermalARGlass:I *:E

# Clear app data (fresh start)
adb shell pm clear com.example.thermalarglass

# Pull all captured media
adb pull /sdcard/Android/data/com.example.thermalarglass/files/ ./glass_media/

# Check storage usage
adb shell du -sh /sdcard/Android/data/com.example.thermalarglass/
```

---

## Known Limitations (Not Bugs)

1. **Video recording** - Saves frames, not MP4 (requires post-processing with ffmpeg)
2. **Android Support Library** - Deprecated APIs OK for EE2 (locked at API 27)
3. **Camera API** - Using deprecated android.hardware.Camera (works perfectly on EE2)
4. **10 fps recording** - Intentional to save storage (every 3rd frame captured)
5. **No AndroidX** - Not migrated (unnecessary for locked OS)

---

**Testing Complete:** [ ] YES [ ] NO
**Ready for Production:** [ ] YES [ ] NO [ ] WITH FIXES

**Approval Signature:** ____________________
**Date:** ____________________
