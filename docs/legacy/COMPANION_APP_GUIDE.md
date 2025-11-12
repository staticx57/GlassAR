# Glass AR Companion App - Complete Guide

Desktop application for ThinkPad P16 to remotely view, control, and record thermal inspections from Google Glass EE2.

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [Installation](#installation)
4. [Quick Start](#quick-start)
5. [User Interface](#user-interface)
6. [Features Guide](#features-guide)
7. [Troubleshooting](#troubleshooting)
8. [Advanced Usage](#advanced-usage)

---

## 🎯 Overview

The Glass AR Companion App is a Windows desktop application that runs on your ThinkPad P16, providing:

- **Live Thermal View** - See exactly what the Glass user sees in real-time
- **Remote Control** - Control Glass functions from your desktop
- **Session Recording** - Record inspections for review and documentation
- **Server Management** - Start/stop the AI processing server with one click
- **Multi-Monitor Support** - Display thermal feed on external monitors

### System Architecture

```
Google Glass EE2 (Field Inspector)
    ↓ WiFi 6 (60fps thermal stream)
ThinkPad P16 Server
    ├─→ AI Processing (YOLOv8 on RTX 4000 Ada)
    ├─→ Companion App GUI (this application)
    └─→ Recordings & Data Storage
```

---

## ✨ Features

### Live Viewing
- **Real-time thermal feed** at up to 60 FPS
- **AI annotations overlay** showing detected objects and anomalies
- **Multi-monitor support** for large displays
- **Picture-in-picture mode** for background monitoring

### Remote Control
- **Mode switching** - Change between Thermal Only, RGB Fusion, and Advanced Inspection
- **Capture control** - Trigger snapshots remotely
- **Recording control** - Start/stop video recording
- **Detection navigation** - Cycle through detected anomalies
- **Overlay toggle** - Show/hide annotations

### Recording & Playback
- **Session recording** with full thermal data and annotations
- **Automatic saving** with timestamps
- **Playback viewer** for review
- **Export to standard formats** (MP4, AVI)

### Server Management
- **One-click start/stop** of thermal AR server
- **Auto-connect** to server on startup
- **GPU monitoring** and diagnostics
- **Service status** indicators

### Monitoring & Diagnostics
- **Connection status** for Glass and server
- **Frame rate monitoring**
- **Detection statistics**
- **Event logs** with timestamps
- **Export logs** for troubleshooting

---

## 💻 Installation

### Prerequisites

**Hardware:**
- ThinkPad P16 Gen 2 (or compatible)
- NVIDIA RTX 4000 Ada GPU
- 16GB+ RAM
- Windows 10/11 (64-bit)

**Software:**
- Python 3.8 or higher
- CUDA 12.x drivers
- 10GB free disk space (for recordings)

### Installation Steps

#### 1. Install Python

Download and install Python from https://www.python.org/

**Important:** Check "Add Python to PATH" during installation

Verify installation:
```cmd
python --version
```

#### 2. Install CUDA Drivers

If not already installed for the server:
```
Download from: https://developer.nvidia.com/cuda-downloads
Install CUDA 12.x for Windows
```

#### 3. Run Setup Script

Navigate to the project directory and run:
```cmd
setup_companion_app.bat
```

This will:
1. Create a Python virtual environment
2. Install all dependencies (~500MB)
3. Create a desktop shortcut
4. Verify installation

**Installation takes 5-10 minutes** depending on internet speed.

#### 4. Launch Application

Double-click the desktop shortcut:
```
Glass AR Companion.lnk
```

Or run manually:
```cmd
run_companion_app.bat
```

---

## 🚀 Quick Start

### First Launch

1. **Start the companion app**
   - Double-click desktop shortcut
   - Or run `run_companion_app.bat`

2. **Start the server** (if not already running)
   - Go to "Server" tab
   - Click "▶ Start Server"
   - Wait for "🟢 Server: Running" status

3. **Connect to server**
   - Go to "Connection" tab
   - Click "Connect to Server"
   - Status shows "🟢 Connected"

4. **Connect Glass**
   - Launch Thermal AR Glass app on Glass EE2
   - Glass status shows "🟢 Glass: Connected"
   - Live thermal feed appears

5. **Start inspection**
   - Video feed shows real-time thermal stream
   - Use "Controls" tab to switch modes
   - Click "⏺ Start Recording" to record

---

## 🖥️ User Interface

### Main Window Layout

```
┌─────────────────────────────────────────────────────────┐
│  Glass AR Companion - ThinkPad P16               ─ □ X │
├─────────────────────┬───────────────────────────────────┤
│                     │  Tabs:                            │
│                     │  ┌──────────────────────────────┐ │
│   Live Thermal      │  │ ▸ Connection                 │ │
│   Video Feed        │  │   Controls                   │ │
│                     │  │   Recording                  │ │
│   [640x480]         │  │   Server                     │ │
│                     │  │   Logs                       │ │
│                     │  └──────────────────────────────┘ │
│                     │                                   │
│   FPS: 30           │  [Tab Content Area]              │
│   Mode: Advanced    │                                   │
│   Detections: 3     │                                   │
│                     │                                   │
├─────────────────────┴───────────────────────────────────┤
│ Status: Connected to server | Glass: Connected  Ready  │
└─────────────────────────────────────────────────────────┘
```

### Tab Navigation

**Connection Tab** - Server connection settings
- Host/Port configuration
- Connect/Disconnect button
- Connection status indicators
- Glass connection status

**Controls Tab** - Remote Glass control
- Display mode selector
- Capture/Recording buttons
- Detection navigation
- Overlay controls

**Recording Tab** - Session management
- Recording status and duration
- Saved recordings list
- Playback controls
- Export options

**Server Tab** - Server management
- Start/Stop/Restart buttons
- Server status indicator
- GPU information
- Auto-start settings

**Logs Tab** - Event logging
- Real-time event log
- Timestamp for each entry
- Save log button
- Clear log button

---

## 📖 Features Guide

### Live Thermal Viewing

#### Video Display

The main video area shows:
- **Thermal image** in false color (iron colormap)
- **AI annotations** overlaid (bounding boxes, labels)
- **Detection highlights** when navigating
- **Mode indicator** showing current Glass mode

#### Info Overlay

Below the video:
- **FPS** - Current frame rate
- **Mode** - Active inspection mode
- **Detections** - Number of objects/anomalies detected

#### Fullscreen Mode

Press **F11** to toggle fullscreen for presentations.

---

### Remote Control

#### Display Mode Switching

Change Glass inspection mode:
1. Go to **Controls** tab
2. Select mode from dropdown:
   - **Thermal Only** - Pure thermal view (battery saver)
   - **Thermal + RGB Fusion** - Thermal overlaid on RGB
   - **Advanced Inspection** - Full AI analysis
3. Mode changes instantly on Glass

#### Capture Control

**Take Snapshot:**
1. Click **📷 Take Snapshot** button
2. Companion shows "Snapshot captured"
3. Image saved on Glass device

**Start/Stop Recording:**
1. Click **⏺ Start Recording**
2. Button changes to **⏹ Stop Recording**
3. Recording indicator shows in top-right
4. Click again to stop and save

#### Detection Navigation

When AI detects multiple objects:
1. Click **◀ Previous** or **Next ▶**
2. Glass highlights selected detection
3. Cycles through all detections
4. Shows "Detection X/Y: ClassName"

#### Overlay Toggle

Hide/show annotations:
1. Click **Toggle Overlay**
2. Crosshair and temperature display hide
3. Click again to restore

---

### Recording Features

#### Start Recording

1. **Controls Tab** → Click **⏺ Start Recording**
2. Recording status shows **🔴 Recording**
3. Frame counter increases
4. Duration timer runs

#### Stop Recording

1. Click **⏹ Stop Recording**
2. Companion saves automatically
3. File saved to `./recordings/` folder
4. Named with timestamp: `recording_20250112_143000.mp4`

#### Playback

1. Go to **Recording** tab
2. List shows all saved recordings
3. Double-click to play in default player
4. Or click **Open Folder** to browse

#### Recording Information

Each recording includes:
- **Video** - H.264 compressed MP4
- **Annotations** - JSON sidecar file
- **Metadata** - Timestamp, duration, frame count
- **Thumbnails** - Preview images

---

### Server Management

#### Starting the Server

**Automatic (Recommended):**
1. Launch companion app
2. Click **▶ Start Server** in Server tab
3. Wait 2-3 seconds for startup
4. Companion auto-connects

**Manual:**
```cmd
python thermal_ar_server.py
```

#### Server Status

Indicators show:
- **🟢 Server: Running** - Ready for connections
- **⚫ Server: Stopped** - Not running
- **🟡 Server: Starting** - Initializing

#### GPU Monitoring

Server tab shows:
- GPU model (RTX 4000 Ada)
- CUDA availability
- Memory usage
- Temperature (if available)

#### Restart Server

If server becomes unresponsive:
1. Click **🔄 Restart Server**
2. Companion disconnects
3. Server stops and restarts
4. Auto-reconnects when ready

---

### Logs & Diagnostics

#### Event Log

All events logged with timestamps:
```
[14:30:00] Connecting to server at localhost:8080...
[14:30:01] Connected to server
[14:30:05] Glass device connected
[14:30:10] Mode changed to: advanced_inspection
[14:30:15] Snapshot capture triggered
```

#### Saving Logs

1. Go to **Logs** tab
2. Click **Save Log**
3. Saved to `./logs/log_TIMESTAMP.txt`

#### Troubleshooting Information

Logs help diagnose:
- Connection issues
- Frame drop reasons
- Server errors
- Glass disconnections

---

## 🔧 Troubleshooting

### Connection Issues

#### Companion Can't Connect to Server

**Symptoms:**
- "Connection error" message
- "Disconnected" status

**Solutions:**
1. **Start the server first**
   - Server tab → ▶ Start Server
   - Wait for "Running" status

2. **Check host/port**
   - Default: localhost:8080
   - If server on different machine, use IP address
   - Verify with `ipconfig` in cmd

3. **Firewall blocking**
   - Windows Firewall may block port 8080
   - Allow Python through firewall
   - Or temporarily disable for testing

4. **Server already running elsewhere**
   - Check for existing server process
   - Task Manager → Look for "python.exe"
   - Kill duplicate processes

#### Glass Not Connecting

**Symptoms:**
- "Glass: Not Connected" persists
- Video feed shows "No Video Feed"

**Solutions:**
1. **Check Glass app**
   - Ensure Thermal AR Glass app is running
   - Check server IP in MainActivity.java
   - Restart Glass app

2. **WiFi connection**
   - Glass and P16 on same network
   - Test with: `ping GLASS_IP`
   - Check WiFi signal strength

3. **Server IP mismatch**
   - Glass app has wrong server IP
   - Update in MainActivity.java line 45
   - Rebuild and reinstall APK

---

### Video Issues

#### No Video Feed

**Symptoms:**
- Black screen or "No Video Feed" text
- FPS shows 0

**Solutions:**
1. **Glass not streaming**
   - Check Glass logs: `adb logcat -s ThermalARGlass`
   - Boson camera may not be connected
   - Restart Glass app

2. **Frame processing error**
   - Check server logs
   - May be GPU issue
   - Restart server

#### Choppy/Laggy Video

**Symptoms:**
- FPS drops below 15
- Stuttering playback
- Frame skips

**Solutions:**
1. **Network congestion**
   - Check WiFi signal strength
   - Reduce interference (move closer to AP)
   - Use 5GHz band if possible

2. **GPU overload**
   - Lower processing mode on Glass
   - Reduce frame rate (30fps instead of 60fps)
   - Check GPU temperature

3. **CPU/Memory bottleneck**
   - Close other applications
   - Check Task Manager for high usage
   - Restart companion app

---

### Recording Issues

#### Recording Won't Start

**Symptoms:**
- Click "Start Recording" but nothing happens
- Recording status stays "Not Recording"

**Solutions:**
1. **Disk space**
   - Check free space (need ~10MB/minute)
   - Clear old recordings if needed

2. **Permissions**
   - Run as administrator
   - Check write permissions on `./recordings/` folder

#### Can't Play Recording

**Symptoms:**
- Double-click does nothing
- Player shows error

**Solutions:**
1. **Install codec**
   - Download K-Lite Codec Pack
   - Or use VLC Media Player

2. **File corrupted**
   - If recording stopped abnormally
   - Try with video repair tool

---

### Server Issues

#### Server Won't Start

**Symptoms:**
- Click "Start Server" but fails
- Error message appears

**Solutions:**
1. **Python not found**
   - Verify: `python --version`
   - Reinstall Python
   - Add to PATH

2. **Dependencies missing**
   - Run setup again: `setup_companion_app.bat`
   - Check for error messages

3. **Port already in use**
   - Another process using port 8080
   - Change port or kill other process
   - `netstat -ano | findstr :8080`

#### Server Crashes

**Symptoms:**
- Server stops unexpectedly
- "Server: Stopped" after running

**Solutions:**
1. **Check logs**
   - Look for error messages
   - Python exceptions in console

2. **GPU error**
   - CUDA may not be installed
   - Update NVIDIA drivers
   - Test: `python -c "import torch; print(torch.cuda.is_available())"`

3. **Out of memory**
   - Close other GPU applications
   - Reduce batch size if applicable

---

## 🔬 Advanced Usage

### Multi-Monitor Setup

#### Display Thermal Feed on Secondary Monitor

1. Drag companion app window to second monitor
2. Press **F11** for fullscreen
3. Primary monitor for controls

#### Picture-in-Picture

1. Resize companion app window small
2. Keep "always on top" (future feature)
3. Monitor while working in other apps

---

### Customization

#### Changing Default Port

Edit `glass_companion_app.py`:
```python
class Config:
    SERVER_PORT = 8080  # Change this
```

#### Custom Recording Directory

```python
class Config:
    RECORDINGS_DIR = Path("D:/Recordings")  # Custom path
```

#### Auto-Start Server

Check "Auto-start server on launch" in Server tab.

---

### Network Configuration

#### Remote Connection (Different Machine)

If server runs on different PC:

1. Get server PC IP: `ipconfig`
2. Companion app → Connection tab
3. Change host from "localhost" to IP (e.g., "192.168.1.100")
4. Click "Connect to Server"

#### Multiple Companion Apps

Multiple desktops can connect simultaneously:
- All see same thermal feed
- All can control Glass
- Last command sent wins

---

### Command Line Options

Run with custom settings:
```cmd
python glass_companion_app.py --host 192.168.1.100 --port 8080
```

**Options:**
- `--host` - Server hostname/IP
- `--port` - Server port
- `--fullscreen` - Start in fullscreen mode
- `--no-autoconnect` - Don't auto-connect on startup

---

### Integration with Other Tools

#### Export to External Tools

Recordings can be opened in:
- **VLC Media Player** - Playback and frame export
- **Adobe Premiere** - Professional editing
- **OpenCV** - Python analysis scripts
- **MATLAB** - Thermal data analysis

#### API Access

For programmatic control:
```python
import socketio

sio = socketio.Client()
sio.connect('http://localhost:8080')

# Trigger snapshot
sio.emit('capture_snapshot')

# Change mode
sio.emit('set_mode', {'mode': 'thermal_only'})
```

---

## 📊 Performance Tips

### Optimize Frame Rate

**For maximum performance:**
1. Close unnecessary applications
2. Use wired Ethernet if possible
3. Keep P16 plugged in (full power mode)
4. Disable Windows visual effects
5. Use SSD for recordings

### Reduce Latency

**For real-time control:**
1. Use 5GHz WiFi band
2. Minimize network hops
3. Use "Thermal Only" mode
4. Reduce Glass transmit resolution

### Battery Life (Glass)

**Extend Glass runtime:**
1. Use "Thermal Only" mode
2. Lower screen brightness
3. Connect USB power bank
4. Disable RGB camera when not needed

---

## 🆘 Getting Help

### Built-in Diagnostics

1. **Logs Tab** - Check for error messages
2. **Server Tab** - GPU status
3. **Connection Tab** - Network indicators

### Error Codes

- **Connection timeout** - Server not responding
- **GPU not found** - CUDA/drivers issue
- **Frame decode error** - Corrupted data
- **Permission denied** - Run as administrator

### Support Resources

- **Documentation**: `COMPANION_APP_GUIDE.md` (this file)
- **Server docs**: `thermal_ar_server.py` comments
- **Glass docs**: `BUILD_INSTRUCTIONS.md`

---

## 📝 Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| **F11** | Toggle fullscreen |
| **Ctrl+R** | Start/stop recording |
| **Ctrl+S** | Take snapshot |
| **Ctrl+M** | Cycle modes |
| **Ctrl+L** | Clear logs |
| **Ctrl+Q** | Quit application |
| **Left/Right** | Navigate detections |

---

## 🔄 Updates

### Checking for Updates

Currently manual:
1. Pull latest from Git repository
2. Run `setup_companion_app.bat` again
3. Restart companion app

### What's New

**Version 1.0:**
- Initial release
- Live thermal viewing
- Remote control
- Recording and playback
- Server management
- Dark theme UI

---

## 📋 System Requirements Summary

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| OS | Windows 10 64-bit | Windows 11 64-bit |
| CPU | Intel i5-8000 series | Intel i7-12000+ |
| RAM | 8GB | 16GB+ |
| GPU | GTX 1060 | RTX 4000 Ada |
| Storage | 50GB HDD | 100GB+ SSD |
| Network | WiFi 5 | WiFi 6 |
| Python | 3.8+ | 3.10+ |

---

**Version:** 1.0
**Last Updated:** 2025-01-12
**Platform:** Windows 10/11
**Compatible with:** Glass AR v1.0, Server v1.0
