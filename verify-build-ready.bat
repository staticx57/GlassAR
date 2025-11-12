@echo off
REM Glass AR - Pre-Build Verification Script
REM Checks that all prerequisites are met before building

echo ========================================
echo Glass AR Build Readiness Check
echo ========================================
echo.

set CHECKS_PASSED=0
set CHECKS_FAILED=0

REM Check 1: Java JDK
echo [1/6] Checking Java JDK...
java -version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Java JDK installed
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] Java JDK not found
    echo       Download from: https://adoptium.net/
    set /a CHECKS_FAILED+=1
)
echo.

REM Check 2: ANDROID_HOME
echo [2/6] Checking ANDROID_HOME...
if defined ANDROID_HOME (
    echo [OK] ANDROID_HOME is set: %ANDROID_HOME%
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] ANDROID_HOME not set
    echo       Set with: setx ANDROID_HOME "C:\Path\To\Android\Sdk"
    set /a CHECKS_FAILED+=1
)
echo.

REM Check 3: Android SDK exists
echo [3/6] Checking Android SDK...
if exist "%ANDROID_HOME%\platforms" (
    echo [OK] Android SDK found
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] Android SDK not found at %ANDROID_HOME%
    echo       Install Android Studio or SDK tools
    set /a CHECKS_FAILED+=1
)
echo.

REM Check 4: Android API 27
echo [4/6] Checking Android API 27...
if exist "%ANDROID_HOME%\platforms\android-27" (
    echo [OK] Android API 27 installed
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] Android API 27 not found
    echo       Install: sdkmanager "platforms;android-27"
    set /a CHECKS_FAILED+=1
)
echo.

REM Check 5: ADB
echo [5/6] Checking ADB...
adb version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] ADB installed
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] ADB not found in PATH
    echo       Add %ANDROID_HOME%\platform-tools to PATH
    set /a CHECKS_FAILED+=1
)
echo.

REM Check 6: local.properties
echo [6/6] Checking local.properties...
if exist "local.properties" (
    echo [OK] local.properties exists
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] local.properties not found
    echo       Copy local.properties.template to local.properties
    echo       Then edit with your SDK path
    set /a CHECKS_FAILED+=1
)
echo.

REM Summary
echo ========================================
echo Checks Passed: %CHECKS_PASSED%/6
echo Checks Failed: %CHECKS_FAILED%/6
echo ========================================
echo.

if %CHECKS_FAILED% EQU 0 (
    echo [SUCCESS] All checks passed!
    echo Ready to build: run "build.bat"
    echo.
    exit /b 0
) else (
    echo [ATTENTION] Some checks failed.
    echo Please fix the issues above before building.
    echo.
    echo Quick fixes:
    echo - Install Java JDK 11+: https://adoptium.net/
    echo - Install Android SDK: https://developer.android.com/studio
    echo - Set ANDROID_HOME: setx ANDROID_HOME "C:\Path\To\Sdk"
    echo - Copy local.properties: copy local.properties.template local.properties
    echo.
    exit /b 1
)
