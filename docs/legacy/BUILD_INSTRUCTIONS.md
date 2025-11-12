# Building the Thermal AR Glass Android App

This guide explains how to build the Android APK for the Glass EE2 Thermal AR application.

---

## Prerequisites

### Required Software

1. **Java Development Kit (JDK) 8**
   - Download: https://adoptium.net/temurin/releases/?version=8
   - Set `JAVA_HOME` environment variable
   - Verify: `java -version` (should show 1.8.x)

2. **Android SDK** (API 27 - Android 8.1 Oreo)
   - Install via Android Studio or command-line tools
   - Required SDK packages:
     - Android SDK Platform 27
     - Android SDK Build-Tools 27.0.3 or higher
     - Android Support Repository

3. **Gradle** (included via wrapper - no manual installation needed)

### Optional (Recommended)

- **Android Studio** - for easier development and debugging
- **ADB (Android Debug Bridge)** - for installing APK on Glass

---

## Project Structure

The Android project is now properly structured:

```
Glass AR/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/thermalarglass/
│   │   │   └── MainActivity.java
│   │   ├── res/
│   │   │   ├── drawable/
│   │   │   │   ├── ic_crosshair.xml
│   │   │   │   ├── ic_record.xml
│   │   │   │   └── ic_launcher_foreground.xml
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   ├── colors.xml
│   │   │   │   └── styles.xml
│   │   │   └── xml/
│   │   │       └── device_filter.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle          (app-level)
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle               (project-level)
├── settings.gradle
├── gradle.properties
├── gradlew.bat               (Windows)
├── local.properties          (auto-generated, in .gitignore)
└── .gitignore
```

---

## Three Display Modes

The app now supports three modes:

### 1. **Thermal Only Mode** (Default)
- Displays thermal camera feed only
- No RGB camera active (saves battery)
- Best for pure thermal inspection

### 2. **Thermal + RGB Fusion Mode**
- Overlays thermal data on RGB camera feed from Glass EE2
- Uses Glass built-in camera for visual context
- Moderate battery usage

### 3. **Advanced Inspection Mode**
- Full AI-powered object detection + thermal analysis
- Uses both thermal and RGB cameras
- Server processes with YOLOv8
- Highest battery usage

Switch modes using:
```java
switchToThermalOnlyMode();
switchToThermalRgbFusionMode();
switchToAdvancedInspectionMode();
```

---

## Building the APK

### Method 1: Using Gradle Wrapper (Command Line)

#### On Windows:

```cmd
cd "C:\Users\stati\Desktop\Projects\Glass AR"
gradlew.bat assembleDebug
```

#### Build Output:

The APK will be generated at:
```
app\build\outputs\apk\debug\app-debug.apk
```

### Method 2: Using Android Studio

1. Open Android Studio
2. **File → Open** → Navigate to `Glass AR` folder
3. Wait for Gradle sync to complete
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK location shown in notification

---

## Installing on Glass EE2

### Prerequisites

1. Enable Developer Options on Glass:
   - **Settings → System → About**
   - Tap **Build number** 7 times
   - Developer options now available

2. Enable USB Debugging:
   - **Settings → System → Developer options**
   - Enable **USB debugging**

3. Connect Glass via USB-C cable

### Install APK

```cmd
# Verify Glass is connected
adb devices

# Install APK (replace with -r to reinstall)
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Launch app
adb shell am start -n com.example.thermalarglass/.MainActivity
```

---

## Configuration

### Update Server IP Address

Before building, update the server IP in `MainActivity.java`:

```java
private static final String SERVER_URL = "http://192.168.1.100:8080";
```

Change `192.168.1.100` to your ThinkPad P16's actual IP address.

### USB Device Filter

The app is configured to detect FLIR Boson 320 cameras. If your camera has different vendor/product IDs:

1. Connect camera to Glass
2. Run: `adb shell lsusb`
3. Update `app/src/main/res/xml/device_filter.xml` with correct IDs

---

## Troubleshooting Build Issues

### Issue: "SDK location not found"

**Solution:** Create or update `local.properties`:

```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
```

Replace `YOUR_USERNAME` with your actual Windows username.

### Issue: "Java version incompatible"

**Solution:** Use JDK 8 (1.8.x):

```cmd
set JAVA_HOME=C:\Path\To\JDK8
gradlew.bat assembleDebug
```

### Issue: "Failed to resolve dependencies"

**Solution:** Check internet connection and try:

```cmd
gradlew.bat clean
gradlew.bat assembleDebug --refresh-dependencies
```

### Issue: "Gradle version incompatible"

**Solution:** The wrapper uses Gradle 6.7.1 which is compatible with Android Gradle Plugin 3.6.4 and API 27. Do not update these versions.

### Issue: "Build fails on Windows with long paths"

**Solution:** Enable long paths in Windows:

1. Run as Administrator: `regedit`
2. Navigate to: `HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Control\FileSystem`
3. Set `LongPathsEnabled` to `1`
4. Restart computer

---

## Build Variants

### Debug Build (Default)
```cmd
gradlew.bat assembleDebug
```
- Includes debugging symbols
- No code optimization
- Larger APK size
- Easier to debug

### Release Build (Production)
```cmd
gradlew.bat assembleRelease
```
- Code optimized and minified
- Smaller APK size
- Requires signing key

**Note:** Release builds need a signing key. For testing, use debug builds.

---

## Testing on Glass

### View Logs

```cmd
# View all logs
adb logcat

# Filter for our app
adb logcat -s ThermalARGlass

# Save logs to file
adb logcat -s ThermalARGlass > glass_logs.txt
```

### Debug with Android Studio

1. Build and install debug APK
2. In Android Studio: **Run → Attach Debugger to Android Process**
3. Select `com.example.thermalarglass`
4. Set breakpoints in code

### Performance Monitoring

```cmd
# Check battery level
adb shell dumpsys battery

# Monitor CPU/Memory
adb shell top | grep thermalarglass

# Check thermal status
adb shell dumpsys thermalservice
```

---

## Common Runtime Issues

### USB Camera Not Detected

1. Check USB-C cable supports data (not charge-only)
2. Grant USB permission when prompted on Glass
3. Verify device_filter.xml has correct vendor/product IDs

### Cannot Connect to Server

1. Verify server IP in `MainActivity.java`
2. Ensure Glass and server on same WiFi network
3. Check server is running: `ping 192.168.1.100`
4. Verify firewall allows port 8080

### Thermal Display Blank

1. Check camera is streaming (logcat for "Boson camera started")
2. Verify frame callback is receiving data
3. Check SurfaceView is properly initialized

### Battery Drains Quickly

Expected behavior with thermal camera:
- Thermal Only mode: ~2-3 hours
- RGB Fusion mode: ~1.5-2 hours
- Advanced Inspection mode: ~1-2 hours

Use USB-C power bank for extended sessions.

---

## Build Performance

### Speed Up Builds

1. **Enable Gradle Daemon** (edit `gradle.properties`):
   ```properties
   org.gradle.daemon=true
   org.gradle.parallel=true
   org.gradle.configureondemand=true
   ```

2. **Increase Heap Size** (already set in gradle.properties):
   ```properties
   org.gradle.jvmargs=-Xmx2048m
   ```

3. **Use Build Cache**:
   ```cmd
   gradlew.bat assembleDebug --build-cache
   ```

### Clean Build

If experiencing build issues:

```cmd
gradlew.bat clean
gradlew.bat assembleDebug
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build APK

on: [push]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 8
        uses: actions/setup-java@v2
        with:
          java-version: '8'
          distribution: 'adopt'
      - name: Build with Gradle
        run: ./gradlew assembleDebug
      - name: Upload APK
        uses: actions/upload-artifact@v2
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

---

## APK Signing (Release Builds)

### Generate Signing Key

```cmd
keytool -genkey -v -keystore thermal-ar-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias thermal-ar
```

### Configure in `app/build.gradle`

```gradle
android {
    signingConfigs {
        release {
            storeFile file("../thermal-ar-key.jks")
            storePassword "YOUR_KEYSTORE_PASSWORD"
            keyAlias "thermal-ar"
            keyPassword "YOUR_KEY_PASSWORD"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

**Security:** Never commit keystore file or passwords to Git!

---

## Resources

- [Android Developers: Build Your App](https://developer.android.com/studio/build)
- [Gradle Build Tool](https://gradle.org/)
- [Glass EE2 Documentation](https://developers.google.com/glass-enterprise)
- [FLIR Boson SDK](https://flir.custhelp.com/app/answers/detail/a_id/3501/)

---

## Support

For build issues:
1. Check this guide's Troubleshooting section
2. Review `DEVELOPMENT_SUMMARY.md`
3. Check Glass EE2 constraints in `GLASS_EE2_CONSTRAINTS.md`

---

**Last Updated:** 2025-11-11
**Gradle Version:** 6.7.1
**Android Gradle Plugin:** 3.6.4
**Target API:** 27 (Android 8.1 Oreo)
**Build Tools:** 27.0.3+
