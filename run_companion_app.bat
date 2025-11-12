@echo off
REM Glass AR Companion App Launcher

REM Activate virtual environment
call companion_env\Scripts\activate.bat

REM Run the companion app
python glass_companion_app.py

REM Keep window open if error occurs
if errorlevel 1 pause
