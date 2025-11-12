# Glass AR Companion App - Quick Start

**5-Minute Setup Guide for ThinkPad P16**

---

## 🚀 Installation (One-Time)

### Step 1: Install Python
```
Download from: https://www.python.org/
✓ Check "Add Python to PATH"
```

### Step 2: Run Setup
```cmd
cd C:\path\to\GlassAR
setup_companion_app.bat
```

**Wait 5-10 minutes for dependencies to install**

### Step 3: Launch
Double-click desktop shortcut: **Glass AR Companion.lnk**

---

## 🎯 First Use

### 1. Start Server
```
Server Tab → Click "▶ Start Server"
Wait for "🟢 Server: Running"
```

### 2. Connect
```
Connection Tab → Click "Connect to Server"
Wait for "🟢 Connected"
```

### 3. Connect Glass
```
Launch Thermal AR Glass app on Glass EE2
Wait for "🟢 Glass: Connected"
Live video appears!
```

---

## 🎮 Basic Controls

### View Live Feed
- **Main window** shows thermal stream in real-time
- **Info bar** shows FPS, mode, detection count

### Switch Modes
```
Controls Tab → Mode dropdown:
  • Thermal Only (battery saver)
  • Thermal + RGB Fusion
  • Advanced Inspection
```

### Take Snapshot
```
Controls Tab → Click "📷 Take Snapshot"
```

### Record Session
```
Controls Tab → Click "⏺ Start Recording"
(Click again to stop)
```

### Navigate Detections
```
Controls Tab → Click "◀ Previous" or "Next ▶"
Glass highlights selected detection
```

---

## 📁 File Locations

| Type | Location |
|------|----------|
| Recordings | `./recordings/*.mp4` |
| Snapshots | `./snapshots/*.jpg` |
| Logs | `./logs/*.txt` |

---

## ⚡ Troubleshooting

### "Can't connect to server"
✓ Click "Start Server" first
✓ Check firewall allows port 8080
✓ Verify Python installed

### "Glass not connected"
✓ Glass app running
✓ Same WiFi network
✓ Check server IP in Glass app

### "No video feed"
✓ Boson camera connected to Glass
✓ USB permissions granted
✓ Check Glass logs

### "Server won't start"
✓ Run `setup_companion_app.bat` again
✓ Check Python: `python --version`
✓ Run as administrator

---

## 🔥 Hot Keys

| Key | Action |
|-----|--------|
| **F11** | Fullscreen |
| **Ctrl+R** | Record |
| **Ctrl+S** | Snapshot |

---

## 📖 Full Documentation

For detailed guide: `COMPANION_APP_GUIDE.md`

---

**Ready in 5 minutes!** 🎉
