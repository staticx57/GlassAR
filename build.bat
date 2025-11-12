@echo off
REM Glass AR Thermal Inspection - Windows Build Script
REM Target: Google Glass Enterprise Edition 2
REM API Level: 27 (Android 8.1 Oreo)

echo ========================================
echo Glass AR Thermal Inspection App
echo Windows Build Script
echo ========================================
echo.

REM Check if ANDROID_HOME is set
if "%ANDROID_HOME%"=="" (
    echo ERROR: ANDROID_HOME environment variable is not set
    echo Please set ANDROID_HOME to your Android SDK location
    echo Example: setx ANDROID_HOME "C:\Users\YourUsername\AppData\Local\Android\Sdk"
    echo.
    pause
    exit /b 1
)

echo Android SDK: %ANDROID_HOME%
echo.

REM Check if Android SDK exists
if not exist "%ANDROID_HOME%\platforms\android-27" (
    echo WARNING: Android API 27 not found in SDK
    echo Please install: sdkmanager "platforms;android-27"
    echo.
    pause
)

REM Check command line argument
if "%1"=="" goto build_debug
if /i "%1"=="clean" goto clean_project
if /i "%1"=="debug" goto build_debug
if /i "%1"=="release" goto build_release
if /i "%1"=="install" goto install_apk
if /i "%1"=="run" goto run_app
if /i "%1"=="logs" goto view_logs
if /i "%1"=="help" goto show_help

:show_help
echo Usage: build.bat [command]
echo.
echo Commands:
echo   clean       Clean build directory
echo   debug       Build debug APK (default)
echo   release     Build release APK
echo   install     Build and install debug APK to connected Glass device
echo   run         Install and launch app on Glass
echo   logs        View app logs from Glass device
echo   help        Show this help message
echo.
goto end

:clean_project
echo Cleaning project...
call gradlew.bat clean
if %ERRORLEVEL% neq 0 (
    echo Build FAILED
    pause
    exit /b %ERRORLEVEL%
)
echo Clean successful!
goto end

:build_debug
echo Building debug APK...
echo.
call gradlew.bat clean assembleDebug
if %ERRORLEVEL% neq 0 (
    echo Build FAILED
    echo.
    echo Troubleshooting:
    echo 1. Check that ANDROID_HOME is set correctly
    echo 2. Verify Android SDK API 27 is installed
    echo 3. Check internet connection (Gradle needs to download dependencies)
    echo 4. Run: gradlew.bat assembleDebug --stacktrace
    echo.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo APK Location:
echo app\build\outputs\apk\debug\app-debug.apk
echo.

REM Copy APK to project root for easy access
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "glass-ar-debug.apk" >nul
    echo APK copied to: glass-ar-debug.apk
    echo.
)

echo To install on Glass:
echo   adb install -r glass-ar-debug.apk
echo.
echo Or use:
echo   build.bat install
echo.
goto end

:build_release
echo Building release APK...
echo.
call gradlew.bat clean assembleRelease
if %ERRORLEVEL% neq 0 (
    echo Build FAILED
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo APK Location:
echo app\build\outputs\apk\release\app-release-unsigned.apk
echo.
echo NOTE: Release APK is unsigned. Sign it before distribution.
echo.
goto end

:install_apk
echo Checking for connected Glass device...
adb devices | find "device" >nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: No Glass device connected
    echo.
    echo Please:
    echo 1. Connect Glass via USB
    echo 2. Enable USB debugging on Glass
    echo 3. Authorize computer on Glass when prompted
    echo.
    pause
    exit /b 1
)

echo Building debug APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo Build FAILED
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Installing APK to Glass...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
if %ERRORLEVEL% neq 0 (
    echo Installation FAILED
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo INSTALLATION SUCCESSFUL!
echo ========================================
echo.
echo To launch app:
echo   build.bat run
echo.
goto end

:run_app
echo Launching Thermal AR Glass app...
adb shell am start -n com.example.thermalarglass/.MainActivity
if %ERRORLEVEL% neq 0 (
    echo Failed to launch app
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo App launched on Glass!
echo.
echo To view logs:
echo   build.bat logs
echo.
goto end

:view_logs
echo Viewing app logs (Ctrl+C to stop)...
echo.
adb logcat -c
adb logcat | findstr /C:"ThermalARGlass" /C:"AndroidRuntime"
goto end

:end
echo.
pause
