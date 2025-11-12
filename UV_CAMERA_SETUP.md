# UVCCamera Library Setup

The UVCCamera library is required to access the FLIR Boson 320 thermal camera via USB. This library is not available in public Maven repositories and must be added manually.

## Option 1: Download Pre-built AAR (Recommended)

1. Download the pre-built UVCCamera library:
   - Visit: https://github.com/saki4510t/UVCCamera/releases
   - Download the latest release AAR file

2. Place the AAR in your project:
   ```
   mkdir app/libs
   copy uvccamera.aar app/libs/
   ```

3. Update `app/build.gradle`:
   ```gradle
   dependencies {
       implementation files('libs/uvccamera.aar')
       // ... other dependencies
   }
   ```

## Option 2: Build from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/saki4510t/UVCCamera.git
   cd UVCCamera
   ```

2. Build with Android Studio or Gradle:
   ```bash
   ./gradlew assembleRelease
   ```

3. Copy the generated AAR:
   ```
   copy libuvccamera/build/outputs/aar/libuvccamera-release.aar ../Glass\ AR/app/libs/uvccamera.aar
   ```

## Option 3: Use a Fork with Maven Support

Some forks publish to JitPack. Try:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.jiangdongguo:AndroidUSBCamera:3.3.0'
    // or
    implementation 'com.github.herohan:UVCCamera:v3.3.0'
}
```

## Current Build Status

The APK can be built without UVCCamera for testing server communication, but won't be able to access the thermal camera until the library is added.

## Next Steps

1. Choose one of the options above
2. Add the library to your project
3. Rebuild: `gradlew.bat assembleDebug`
4. Install on Glass and test with Boson 320 connected
