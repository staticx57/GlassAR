================================================================================
Glass AR Thermal Inspection - Windows Build Instructions
================================================================================

QUICK START (5 Steps):

1. Clone Repository
   git clone https://github.com/staticx57/GlassAR.git
   cd GlassAR
   git checkout claude/begin-app-build-setup-011CV4DP3o7vS6TFT83wmyQf

2. Verify Prerequisites
   verify-build-ready.bat

3. Configure SDK
   copy local.properties.template local.properties
   notepad local.properties
   (Edit with your Android SDK path)

4. Build App
   build.bat

5. Install to Glass
   build.bat install

================================================================================

PREREQUISITES:

✓ Java JDK 11+ (https://adoptium.net/)
✓ Android SDK with API 27 (Android Studio or SDK tools)
✓ ANDROID_HOME environment variable set
✓ ADB in PATH

VERIFY: Run "verify-build-ready.bat"

================================================================================

DOCUMENTATION:

📄 QUICKSTART_WINDOWS.md  - Detailed step-by-step guide
📘 docs/USER_GUIDE.md     - Complete user manual
📗 docs/DEVELOPMENT.md    - Developer documentation
📖 DOCUMENTATION.md       - Documentation navigation

================================================================================

BUILD COMMANDS:

build.bat           - Build debug APK
build.bat install   - Install to Glass
build.bat run       - Launch app on Glass
build.bat logs      - View app logs
build.bat clean     - Clean build directory

================================================================================

TROUBLESHOOTING:

Problem: Build fails
Solution: Run "verify-build-ready.bat" to check prerequisites

Problem: Can't find SDK
Solution: Set ANDROID_HOME environment variable

Problem: Glass not detected
Solution: Enable USB debugging on Glass (Developer options)

Full troubleshooting: See QUICKSTART_WINDOWS.md or docs/USER_GUIDE.md

================================================================================

APK OUTPUT LOCATION:

After successful build:
- app\build\outputs\apk\debug\app-debug.apk
- glass-ar-debug.apk (copied to project root)

================================================================================

EXPECTED BUILD TIME:

First build: 10-15 minutes (Gradle downloads dependencies)
Subsequent builds: 2-3 minutes (incremental compilation)

================================================================================

NEXT STEPS AFTER BUILD:

1. Install to Glass: build.bat install
2. Connect Boson 320 thermal camera
3. Launch app on Glass
4. Start thermal inspection!

For server features:
1. Start server on ThinkPad P16: python thermal_ar_server.py
2. Launch companion app: python glass_companion_app.py
3. Connect Glass to WiFi

================================================================================

SUPPORT:

Repository: https://github.com/staticx57/GlassAR
Branch: claude/begin-app-build-setup-011CV4DP3o7vS6TFT83wmyQf

For issues: See docs/USER_GUIDE.md#troubleshooting

================================================================================
