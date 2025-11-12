# Quick Start for Windows Build

**Target:** Build Glass AR app on your Windows machine
**Time Required:** 15-30 minutes (first time setup)

---

## Pre-Flight Checklist

### 1. Verify Prerequisites

**Check Java:**
```cmd
java -version
```
Expected: Java 11 or higher

**Check Android SDK:**
```cmd
echo %ANDROID_HOME%
```
Expected: Path to Android SDK (e.g., `C:\Users\...\AppData\Local\Android\Sdk`)

**Check ADB:**
```cmd
adb version
```
Expected: Android Debug Bridge version

---

## Step-by-Step Build Process

### Step 1: Clone Repository (2 minutes)

```cmd
cd C:\Users\YourUsername\Projects
git clone https://github.com/staticx57/GlassAR.git
cd GlassAR
git checkout claude/begin-app-build-setup-011CV4DP3o7vS6TFT83wmyQf
```

**Verify:**
```cmd
dir
```
You should see: `app/`, `build.bat`, `README.md`, `docs/`, etc.

---

### Step 2: Configure SDK Path (1 minute)

```cmd
copy local.properties.template local.properties
notepad local.properties
```

**Edit this line:**
```properties
sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

**Replace with YOUR actual SDK path** (use double backslashes `\\`)

**To find your SDK path:**
- Open Android Studio → Tools → SDK Manager → Copy "Android SDK Location"
- OR check: `echo %ANDROID_HOME%`

---

### Step 3: Update Server IP (1 minute)

**Only if using server features:**

```cmd
notepad app\src\main\java\com\example\thermalarglass\MainActivity.java
```

Find line 52:
```java
private static final String SERVER_URL = "http://192.168.1.100:8080";
```

Change `192.168.1.100` to your ThinkPad P16 IP address.

**To find ThinkPad IP:**
```cmd
ipconfig
```
Look for "IPv4 Address" under your WiFi adapter.

**Skip this step if using standalone mode only.**

---

### Step 4: Build the App (5-10 minutes)

```cmd
build.bat
```

**What happens:**
1. Gradle downloads dependencies (first time only - ~5 minutes)
2. Compiles Java code
3. Packages APK
4. Shows APK location

**Expected output:**
```
BUILD SUCCESSFUL in 45s

APK Location:
app\build\outputs\apk\debug\app-debug.apk

APK copied to: glass-ar-debug.apk
```

**If build fails:** See [Troubleshooting](#troubleshooting) below

---

### Step 5: Prepare Glass EE2 (2 minutes)

**On Glass:**
1. Settings → System → About
2. Tap "Build number" 7 times (enables Developer options)
3. Settings → System → Developer options
4. Enable "USB debugging"

**Connect to PC:**
1. Connect Glass via USB-C cable
2. Glass will show "Allow USB debugging?" → Tap "OK"

**Verify connection:**
```cmd
adb devices
```

Expected:
```
List of devices attached
serialnumber    device
```

---

### Step 6: Install to Glass (1 minute)

```cmd
build.bat install
```

**Or manually:**
```cmd
adb install -r glass-ar-debug.apk
```

**Expected:**
```
Performing Streamed Install
Success
```

---

### Step 7: Test the App (2 minutes)

**Launch app:**
```cmd
build.bat run
```

**Or manually on Glass:**
- Swipe forward on touchpad
- Tap "Thermal AR Glass"

**What to expect:**
- App opens
- Shows "Waiting for camera" or "Disconnected" (normal without Boson 320)
- UI elements visible (battery, connection status, frame counter)

---

### Step 8: Connect Boson 320 (1 minute)

**Hardware connection:**
1. Boson 320 → USB-C cable → USB-C OTG adapter → Glass
2. Glass will prompt "Allow USB device?" → Tap "OK"
3. Thermal stream should appear within 2-3 seconds

**Success indicators:**
- Live thermal display on Glass
- Center temperature showing (e.g., "23.5°C")
- Frame counter incrementing
- No crashes

---

## Troubleshooting

### Build Fails: "ANDROID_HOME not set"

**Fix:**
```cmd
setx ANDROID_HOME "C:\Users\YourUsername\AppData\Local\Android\Sdk"
```
Then restart Command Prompt and try again.

---

### Build Fails: "SDK location not found"

**Fix:**
```cmd
notepad local.properties
```
Verify path is correct with double backslashes:
```properties
sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

---

### Build Fails: "Failed to find target android-27"

**Fix:**
Open Android Studio → SDK Manager → SDK Platforms → Check "Android 8.1 (Oreo) API 27" → Apply

Or via command line:
```cmd
sdkmanager "platforms;android-27"
```

---

### ADB Doesn't Recognize Glass

**Check:**
1. USB cable is data-capable (not charge-only)
2. USB debugging enabled on Glass
3. "Allow USB debugging" prompt accepted on Glass
4. Try different USB port on PC

**Reset ADB:**
```cmd
adb kill-server
adb start-server
adb devices
```

---

### App Installs But Won't Launch

**Check permissions:**
```cmd
adb shell pm grant com.example.thermalarglass android.permission.CAMERA
adb shell pm grant com.example.thermalarglass android.permission.ACCESS_WIFI_STATE
```

**View crash logs:**
```cmd
build.bat logs
```

---

### Boson 320 Not Detected

**Check:**
1. USB-C OTG adapter is working (test with USB drive)
2. Boson has power (LED should be on)
3. USB permissions granted in app
4. Cable is good quality

**View USB events:**
```cmd
adb logcat | findstr USB
```

---

## Testing Checklist

**Standalone Mode (No Server):**
- [ ] Thermal display appears
- [ ] Temperature updates (center/min/max)
- [ ] Tap gesture toggles overlay
- [ ] Double-tap captures snapshot (check logs)
- [ ] Swipe forward cycles modes
- [ ] Battery indicator shows percentage
- [ ] App runs for 5+ minutes without crash

**Connected Mode (With Server):**
- [ ] Start server: `python thermal_ar_server.py`
- [ ] Connection status shows "Connected" (green)
- [ ] Launch companion app
- [ ] Glass appears in companion app
- [ ] Battery widget updates
- [ ] Temperature widget updates
- [ ] Colormap selector changes display
- [ ] No errors in server console

---

## Next Steps

**After successful build:**

1. **Field Test Standalone Mode**
   - Take Glass outdoors with Boson 320
   - Test for 30+ minutes
   - Verify battery life
   - Test all gestures

2. **Setup Server (Optional)**
   - Install Python dependencies on ThinkPad P16
   - Configure network
   - Test companion app integration

3. **Documentation**
   - Read [User Guide](docs/USER_GUIDE.md) for detailed usage
   - Read [Developer Guide](docs/DEVELOPMENT.md) for architecture

---

## Common Commands Reference

**Build:**
```cmd
build.bat              # Build debug APK
build.bat clean        # Clean build directory
```

**Deploy:**
```cmd
build.bat install      # Install to Glass
build.bat run          # Install and launch
```

**Debug:**
```cmd
build.bat logs         # View app logs
adb devices           # List connected devices
adb logcat -c         # Clear logs
```

**Server (on ThinkPad P16):**
```bash
python thermal_ar_server.py           # Start AI server
python glass_companion_app.py         # Start companion app
```

---

## Expected Timeline

**First-time setup:** 15-30 minutes
- Prerequisites: 5 minutes (if already installed)
- Clone and configure: 5 minutes
- Build: 5-10 minutes (Gradle downloads)
- Install and test: 5 minutes

**Subsequent builds:** 2-3 minutes
- Code changes: 30 seconds
- Build: 1-2 minutes (incremental)
- Install: 30 seconds

---

## Success Criteria

✅ **Build successful** - No errors, APK created
✅ **Install successful** - "Success" message from ADB
✅ **App launches** - Opens on Glass without crash
✅ **Boson detected** - Thermal stream appears
✅ **Temperature works** - Center temp displays
✅ **Gestures work** - Tap/swipe respond
✅ **No crashes** - Runs for 5+ minutes

---

## Support

**If stuck:**
1. Check [User Guide - Troubleshooting](docs/USER_GUIDE.md#troubleshooting)
2. View build logs: `build.bat --stacktrace`
3. Check ADB logs: `build.bat logs`
4. Create GitHub issue with logs

**Documentation:**
- Quick overview: [README.md](README.md)
- Installation details: [docs/USER_GUIDE.md](docs/USER_GUIDE.md#installation)
- Development info: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)

---

**Ready to build!** 🚀

Run: `git clone https://github.com/staticx57/GlassAR.git`
