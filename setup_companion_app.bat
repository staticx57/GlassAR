@echo off
REM Glass AR Companion App - Windows Setup Script
REM ThinkPad P16 Installation

echo ========================================
echo Glass AR Companion App Setup
echo ========================================
echo.

REM Check Python installation
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python not found!
    echo Please install Python 3.8 or higher from https://www.python.org/
    pause
    exit /b 1
)

echo [1/5] Python found
python --version

REM Create virtual environment
echo.
echo [2/5] Creating virtual environment...
if exist "companion_env" (
    echo Virtual environment already exists
) else (
    python -m venv companion_env
    if errorlevel 1 (
        echo ERROR: Failed to create virtual environment
        pause
        exit /b 1
    )
)

REM Activate virtual environment
echo.
echo [3/5] Activating virtual environment...
call companion_env\Scripts\activate.bat
if errorlevel 1 (
    echo ERROR: Failed to activate virtual environment
    pause
    exit /b 1
)

REM Install dependencies
echo.
echo [4/5] Installing dependencies...
echo This may take a few minutes...
python -m pip install --upgrade pip
pip install -r companion_requirements.txt
if errorlevel 1 (
    echo ERROR: Failed to install dependencies
    pause
    exit /b 1
)

REM Create desktop shortcut
echo.
echo [5/5] Creating desktop shortcut...
powershell -Command "$WshShell = New-Object -ComObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut('%USERPROFILE%\Desktop\Glass AR Companion.lnk'); $Shortcut.TargetPath = '%CD%\run_companion_app.bat'; $Shortcut.WorkingDirectory = '%CD%'; $Shortcut.IconLocation = '%SystemRoot%\System32\shell32.dll,13'; $Shortcut.Description = 'Glass AR Companion App'; $Shortcut.Save()"

echo.
echo ========================================
echo Setup Complete!
echo ========================================
echo.
echo Desktop shortcut created: Glass AR Companion.lnk
echo.
echo To start the companion app:
echo   1. Double-click the desktop shortcut
echo   2. Or run: run_companion_app.bat
echo.
pause
