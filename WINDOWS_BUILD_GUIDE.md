# Windows Build Guide - Glass AR Thermal Inspection App

**Target Platform:** Google Glass Enterprise Edition 2
**Android API Level:** 27 (Android 8.1 Oreo)
**Build Tool:** Gradle 7.6
**Android Gradle Plugin:** 7.4.2

---

## Prerequisites

### 1. Java Development Kit (JDK)
- **Required:** JDK 11 or higher
- **Download:** https://adoptium.net/ (OpenJDK)
- **Verify installation:**
  ```cmd
  java -version
  javac -version
  ```
- **Expected output:** Java version 11 or higher

### 2. Android SDK
- **Required:** Android SDK with API Level 27
- **Download:** https://developer.android.com/studio
- **Option 1:** Install Android Studio (recommended)
- **Option 2:** Install Android command-line tools only

### 3. Android SDK Components
Install using Android Studio SDK Manager or `sdkmanager`:

**Required components:**
- Android SDK Platform 27 (Android 8.1)
- Android SDK Build-Tools 30.0.3 or higher
- Android SDK Platform-Tools
- Android SDK Tools
- Google USB Driver (for Glass EE2 connection)

**Using SDK Manager in Android Studio:**
```
Tools → SDK Manager → SDK Platforms → Check "Android 8.1 (Oreo) API Level 27"
Tools → SDK Manager → SDK Tools → Check required build tools
```

**Using command line:**
```cmd
sdkmanager "platforms;android-27"
sdkmanager "build-tools;30.0.3"
sdkmanager "platform-tools"
sdkmanager "extras;google;usb_driver"
```

### 4. Environment Variables
Set the following environment variables:

**ANDROID_HOME:**
```cmd
setx ANDROID_HOME "C:\Users\YourUsername\AppData\Local\Android\Sdk"
```

**Add to PATH:**
```cmd
setx PATH "%PATH%;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\tools;%ANDROID_HOME%\tools\bin"
```

**Verify:**
```cmd
echo %ANDROID_HOME%
adb version
```

### 5. Git (Optional but Recommended)
- **Download:** https://git-scm.com/download/win
- **Verify:** `git --version`

---

## Project Setup

### Step 1: Clone or Update Repository

**If you haven't cloned yet:**
```cmd
git clone https://github.com/staticx57/GlassAR.git
cd GlassAR
```

**If you already have the repository:**
```cmd
cd GlassAR
git fetch origin
git checkout claude/begin-app-build-setup-011CV4DP3o7vS6TFT83wmyQf
git pull
```

### Step 2: Verify Project Structure

Your project should have this structure:
```
GlassAR/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/thermalarglass/
│   │       │   ├── MainActivity.java
│   │       │   ├── NativeUSBMonitor.java
│   │       │   └── NativeUVCCamera.java
│   │       ├── res/
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
└── gradlew.bat
```

### Step 3: Configure Gradle Wrapper

The project uses Gradle wrapper (included). Check the configuration:

**gradle/wrapper/gradle-wrapper.properties:**
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-7.6-bin.zip
```

This will automatically download Gradle 7.6 on first build.

---

## Building the Application

### Method 1: Using Gradle Wrapper (Recommended)

**Clean the project:**
```cmd
gradlew.bat clean
```

**Build debug APK:**
```cmd
gradlew.bat assembleDebug
```

**Build release APK:**
```cmd
gradlew.bat assembleRelease
```

**Expected output:**
```
BUILD SUCCESSFUL in 45s
```

**APK location:**
```
app/build/outputs/apk/debug/app-debug.apk
```

### Method 2: Using Android Studio

1. **Open Android Studio**
2. **File → Open** → Select `GlassAR` folder
3. **Wait for Gradle sync** to complete
4. **Build → Make Project** (or press `Ctrl+F9`)
5. **Build → Build Bundle(s) / APK(s) → Build APK(s)**

**APK location:**
- Android Studio will show a notification with "Locate" link
- Default: `app/build/outputs/apk/debug/app-debug.apk`

---

## Build Script for Windows

I've created a build script for convenience: `build.bat`

**To use:**
```cmd
build.bat
```

This will:
1. Clean the project
2. Build debug APK
3. Show APK location
4. Copy APK to project root for easy access

---

## Troubleshooting

### Issue 1: "ANDROID_HOME is not set"

**Solution:**
```cmd
setx ANDROID_HOME "C:\Users\YourUsername\AppData\Local\Android\Sdk"
```
**Then restart Command Prompt**

### Issue 2: "SDK location not found"

**Create `local.properties` file in project root:**
```properties
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```
**Note:** Use double backslashes `\\` or forward slashes `/`

### Issue 3: "Failed to find target with hash string 'android-27'"

**Solution:**
```cmd
sdkmanager "platforms;android-27"
```

### Issue 4: Gradle download fails

**Check internet connection and try again:**
```cmd
gradlew.bat clean --refresh-dependencies
```

### Issue 5: "Unsupported class file major version"

**Your JDK is too new. Install JDK 11-17:**
- Download from https://adoptium.net/
- Set `JAVA_HOME` to JDK 11 path
- Restart Command Prompt

### Issue 6: Build succeeds but app crashes on device

**Check logs:**
```cmd
adb logcat | findstr ThermalARGlass
```

**Common causes:**
- Missing permissions in AndroidManifest.xml (already added)
- Incompatible libraries (check dependencies)
- Boson 320 camera not connected

---

## Deploying to Glass EE2

### Step 1: Enable Developer Options on Glass EE2

1. **Settings → System → About**
2. **Tap "Build number" 7 times**
3. **Developer options enabled**

### Step 2: Enable USB Debugging

1. **Settings → System → Developer options**
2. **Enable "USB debugging"**

### Step 3: Connect Glass to PC

1. **Connect Glass via USB-C cable**
2. **On Glass, allow USB debugging** when prompted
3. **Verify connection:**
   ```cmd
   adb devices
   ```
   **Expected output:**
   ```
   List of devices attached
   serial_number    device
   ```

### Step 4: Install APK

**Using ADB:**
```cmd
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Using build script:**
```cmd
build.bat install
```

**Expected output:**
```
Performing Streamed Install
Success
```

### Step 5: Launch App on Glass

**Via ADB:**
```cmd
adb shell am start -n com.example.thermalarglass/.MainActivity
```

**Or manually on Glass:**
- Swipe forward on touchpad to open launcher
- Tap on "Thermal AR Glass" app

---

## Testing on Glass EE2

### Prerequisites for Testing:
1. ✅ Glass EE2 device charged and USB debugging enabled
2. ✅ FLIR Boson 320 thermal camera with USB-C adapter
3. ✅ WiFi connection on Glass (same network as server)
4. ✅ Thermal AR server running on ThinkPad P16

### Test Procedure:

**1. Start the server on ThinkPad P16:**
```bash
python thermal_ar_server.py
```

**2. Connect Boson 320 to Glass:**
- Use USB-C OTG adapter if needed
- Glass will request USB permission - allow it

**3. Launch app on Glass**

**4. Verify functionality:**
- [ ] Thermal camera stream displays
- [ ] Center temperature shows on overlay
- [ ] Connection status shows "Connected"
- [ ] Battery indicator shows percentage
- [ ] Network indicator shows signal strength

**5. Test companion app integration:**
- [ ] Launch companion app on ThinkPad P16
- [ ] Connect to server
- [ ] Verify Glass appears as connected
- [ ] Test temperature measurements widget
- [ ] Test colormap selector (change from iron to rainbow)
- [ ] Test auto-snapshot settings
- [ ] Verify battery and network indicators update

**6. Test touchpad gestures:**
- [ ] Tap: Toggle overlay
- [ ] Double-tap: Capture snapshot
- [ ] Long press: Start/stop recording
- [ ] Swipe forward: Cycle display modes
- [ ] Swipe backward: Navigate detections
- [ ] Swipe down: Dismiss alerts

---

## Build Configuration

### Gradle Configuration

**Project-level build.gradle:**
```gradle
buildscript {
    dependencies {
        classpath 'com.android.tools.build:gradle:7.4.2'
    }
}
```

**App-level build.gradle:**
```gradle
android {
    compileSdkVersion 27
    defaultConfig {
        applicationId "com.example.thermalarglass"
        minSdkVersion 27
        targetSdkVersion 27
        versionCode 1
        versionName "1.0"
    }
}

dependencies {
    implementation 'io.socket:socket.io-client:2.1.0'
    // Other dependencies...
}
```

---

## Performance Optimization (Optional)

### Enable ProGuard for Release Builds

**In app/build.gradle:**
```gradle
buildTypes {
    release {
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

### Build with Performance Profiling

```cmd
gradlew.bat assembleDebug --profile
```

**Profile report location:**
```
build/reports/profile/
```

---

## Expected Build Output

### Successful Build Output:
```
> Task :app:compileDebugJavaWithJavac
> Task :app:dexBuilderDebug
> Task :app:mergeDebugDexFiles
> Task :app:packageDebug
> Task :app:assembleDebug

BUILD SUCCESSFUL in 45s
47 actionable tasks: 47 executed
```

### APK Details:
- **Size:** ~2-5 MB (debug), ~1-3 MB (release with ProGuard)
- **Min SDK:** API 27 (Android 8.1)
- **Target SDK:** API 27 (Android 8.1)
- **Permissions:** Internet, Camera, USB Host, WiFi State

---

## Next Steps After Build

1. ✅ Build succeeds → Deploy to Glass EE2
2. ✅ App installs → Test with Boson 320 camera
3. ✅ Camera works → Test Socket.IO connectivity
4. ✅ Network works → Test companion app integration
5. ✅ All features work → Test crash scenarios
6. ✅ Crash tests pass → Production testing with real use cases

---

## Build Artifacts

After successful build, you will have:

**Debug APK:**
- `app/build/outputs/apk/debug/app-debug.apk`
- Ready for testing (not optimized)
- Includes debugging symbols

**Release APK (if built):**
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- Optimized and minified
- Requires signing for distribution

**Build Reports:**
- `app/build/reports/` - Lint, test results
- `app/build/outputs/logs/` - Build logs

---

## Signing APK for Distribution (Optional)

### Generate Keystore:
```cmd
keytool -genkey -v -keystore glass-ar-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias glass-ar
```

### Sign APK:
```cmd
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore glass-ar-keystore.jks app-release-unsigned.apk glass-ar
```

### Align APK:
```cmd
zipalign -v 4 app-release-unsigned.apk app-release.apk
```

---

## Common Build Commands Reference

| Command | Description |
|---------|-------------|
| `gradlew.bat clean` | Clean build directory |
| `gradlew.bat assembleDebug` | Build debug APK |
| `gradlew.bat assembleRelease` | Build release APK |
| `gradlew.bat installDebug` | Build and install debug APK |
| `gradlew.bat tasks` | List all available tasks |
| `gradlew.bat dependencies` | Show dependency tree |
| `gradlew.bat --refresh-dependencies` | Force dependency refresh |
| `adb install -r app.apk` | Install APK (replace existing) |
| `adb uninstall com.example.thermalarglass` | Uninstall app |
| `adb logcat -c && adb logcat` | Clear and view logs |

---

## Support & Troubleshooting

### View Build Logs:
```cmd
gradlew.bat assembleDebug --stacktrace
gradlew.bat assembleDebug --info
gradlew.bat assembleDebug --debug
```

### Clean and Rebuild:
```cmd
gradlew.bat clean
gradlew.bat assembleDebug --refresh-dependencies
```

### Check Dependency Conflicts:
```cmd
gradlew.bat app:dependencies
```

---

## Contact & Documentation

**Project Repository:** https://github.com/staticx57/GlassAR
**Branch:** `claude/begin-app-build-setup-011CV4DP3o7vS6TFT83wmyQf`
**Documentation:**
- `README.md` - Project overview
- `SESSION_IMPLEMENTATION_SUMMARY.md` - Latest changes
- `CRASH_RISK_ANALYSIS.md` - Crash prevention analysis
- `UNCONNECTED_FEATURES_ANALYSIS.md` - Feature integration status

---

**Last Updated:** 2025-11-12
**Build Tool Version:** Gradle 7.6
**Android Gradle Plugin:** 7.4.2
**Target Device:** Google Glass Enterprise Edition 2
