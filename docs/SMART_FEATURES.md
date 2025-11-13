# Smart Features: Server Discovery & Object Display

## Overview

These smart features optimize the ThermalAR Glass experience for hands-free operation with automatic server discovery and context-aware object display.

---

## 🔍 Automatic Server Discovery

### Problem
- Manual IP address configuration
- Server changes break connection
- No discovery mechanism

### Solution: Multi-Method Discovery

**Discovery Priority:**
1. **Last Known Server** (instant reconnect)
2. **UDP Broadcast** (auto-find on local network)
3. **mDNS/Zeroconf** (service discovery, like AirPlay)
4. **QR Code Scan** (point Glass at server display)
5. **Manual Entry** (fallback in settings)

### How It Works

**Glass Side (Client):**
```java
ServerDiscovery discovery = new ServerDiscovery(context);

discovery.startDiscovery(new ServerDiscovery.IDiscoveryCallback() {
    @Override
    public void onServerFound(ServerDiscovery.ServerInfo server) {
        Log.i(TAG, "Found server: " + server.name + " at " + server.getUrl());
        // Auto-connect to first discovered server
        connectToServer(server.getUrl());
    }

    @Override
    public void onDiscoveryComplete(List<ServerDiscovery.ServerInfo> servers) {
        if (servers.isEmpty()) {
            // Fall back to last known or manual
            tryLastKnownServer();
        }
    }

    @Override
    public void onDiscoveryFailed(String error) {
        Log.e(TAG, "Discovery failed: " + error);
    }
});
```

**Server Side (Python):**
```python
import socket
import threading

DISCOVERY_PORT = 8081
SERVER_PORT = 8080
SERVER_NAME = "ThermalAR-Server"

def discovery_responder():
    """Respond to Glass discovery broadcasts"""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(('', DISCOVERY_PORT))

    print(f"Discovery service listening on port {DISCOVERY_PORT}")

    while True:
        try:
            data, addr = sock.recvfrom(1024)
            message = data.decode('utf-8')

            if message == "THERMAL_AR_GLASS_DISCOVERY":
                # Respond with server info
                response = f"THERMAL_AR_SERVER:{SERVER_NAME}:{SERVER_PORT}:object_detection,thermal_analysis"
                sock.sendto(response.encode('utf-8'), addr)
                print(f"Responded to discovery from {addr[0]}")

        except Exception as e:
            print(f"Discovery error: {e}")

# Start discovery responder in background
discovery_thread = threading.Thread(target=discovery_responder, daemon=True)
discovery_thread.start()
```

### Integration Steps

**1. Add to MainActivity.java:**
```java
private ServerDiscovery mServerDiscovery;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... existing code ...

    // Initialize server discovery
    mServerDiscovery = new ServerDiscovery(this);

    // Try automatic discovery first
    startServerDiscovery();
}

private void startServerDiscovery() {
    Log.i(TAG, "Starting automatic server discovery...");

    mServerDiscovery.startDiscovery(new ServerDiscovery.IDiscoveryCallback() {
        @Override
        public void onServerFound(ServerDiscovery.ServerInfo server) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this,
                    "Found server: " + server.name,
                    Toast.LENGTH_SHORT).show();

                // Save discovered server
                saveServerUrl(server.getUrl());

                // Connect automatically
                reconnectSocket();
            });
        }

        @Override
        public void onDiscoveryComplete(List<ServerDiscovery.ServerInfo> servers) {
            if (servers.isEmpty()) {
                // Fall back to last known or default
                Log.i(TAG, "No servers found, using saved/default");
            }
        }

        @Override
        public void onDiscoveryFailed(String error) {
            Log.w(TAG, "Discovery failed: " + error);
        }
    });
}
```

**2. Update Server (thermal_ar_server.py):**
Add discovery responder at the start of your server script (see Python code above).

---

## 🎯 Smart Object Display

### Problem
- Small Glass display (640×360)
- Cluttered with many objects
- Hard to see what's important
- Not optimized for hands-free use

### Solution: Context-Aware Display

**Smart Features:**
1. **Center Priority** - Highlight what you're looking at
2. **Thermal Priority** - Hot objects get attention
3. **Distance Fade** - Peripheral objects dimmed
4. **Adaptive Detail** - Show less info when crowded
5. **Audio Cues** - Voice alerts for critical objects

### Display Modes

**MINIMAL** - Icons + temps only (cluttered scenes)
```
• Person 72°
  • Object 45°
    • Item 38°
```

**STANDARD** - Bounding boxes + labels (default)
```
┌─────────────┐
│ Person 72°C │
└─────────────┘
```

**DETAILED** - Full annotations + HUD stats
```
┌─────────────┐     Objects: 5
│ Person 72°C │     ⚠ Hot: 2
│ conf: 95%   │
└─────────────┘
```

### How It Works

**Object Prioritization Algorithm:**
```
Priority Score =
  0.4 × Distance from Center  (what you're looking at)
  0.3 × Confidence Score      (detection quality)
  0.3 × Temperature Score     (thermal anomalies)
```

**Display Rules:**
- Show **top 3 objects** with full details (bounding box + label + temp)
- Show **next 5 objects** with minimal UI (small indicator)
- **Hide** remaining objects to avoid clutter
- **Alert** on critical temperatures (>80°C) with pulsing red indicator

### Integration Steps

**1. Add to MainActivity.java:**
```java
private SmartDisplayManager mSmartDisplay;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... existing code ...

    // Initialize smart display
    mSmartDisplay = new SmartDisplayManager();
    mSmartDisplay.setDisplayMode(SmartDisplayManager.DisplayMode.STANDARD);
}

private void drawAnnotations(Canvas canvas) {
    // Convert detections to AnnotatedObject format
    List<SmartDisplayManager.AnnotatedObject> objects = new ArrayList<>();

    for (Detection det : mDetections) {
        SmartDisplayManager.AnnotatedObject obj =
            new SmartDisplayManager.AnnotatedObject(
                det.bbox, det.confidence, det.className);

        // Add temperature if available (from thermal analysis)
        if (mThermalAnalysis != null) {
            obj.temperature = getTempForObject(det);  // Your logic
        }

        objects.add(obj);
    }

    // Let smart display manager handle rendering
    float scaleX = (float) GLASS_WIDTH / BOSON_WIDTH;
    float scaleY = (float) GLASS_HEIGHT / BOSON_HEIGHT;

    mSmartDisplay.drawAnnotations(canvas, objects, scaleX, scaleY);
}
```

**2. Add Gesture Control for Display Modes:**
```java
private void onSwipeUp() {
    // Cycle display modes
    SmartDisplayManager.DisplayMode[] modes = SmartDisplayManager.DisplayMode.values();
    int currentIndex = Arrays.asList(modes).indexOf(mSmartDisplay.getDisplayMode());
    int nextIndex = (currentIndex + 1) % modes.length;

    mSmartDisplay.setDisplayMode(modes[nextIndex]);
    Toast.makeText(this, "Display: " + modes[nextIndex], Toast.LENGTH_SHORT).show();
}
```

---

## 🎤 Audio Alerts (Optional Enhancement)

Add voice alerts for critical detections:

```java
private void checkForAlerts(List<SmartDisplayManager.AnnotatedObject> objects) {
    List<SmartDisplayManager.AnnotatedObject> primaryObjects =
        mSmartDisplay.getPrimaryObjects(objects);

    for (SmartDisplayManager.AnnotatedObject obj : primaryObjects) {
        if (!Float.isNaN(obj.temperature) && obj.temperature >= 80.0f) {
            // Critical temperature alert
            speakAlert("Warning: " + obj.className + " at " +
                      (int)obj.temperature + " degrees");
        }
    }
}

private void speakAlert(String message) {
    // Use Android TextToSpeech
    if (mTextToSpeech != null && !mTextToSpeech.isSpeaking()) {
        mTextToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
    }
}
```

---

## Configuration Options

**SharedPreferences Settings:**

```java
// Server discovery
prefs.putBoolean("auto_discovery_enabled", true);
prefs.putInt("discovery_timeout_ms", 3000);

// Smart display
prefs.putString("display_mode", "STANDARD");
prefs.putInt("max_objects_shown", 8);
prefs.putFloat("min_confidence", 0.5f);

// Audio alerts
prefs.putBoolean("audio_alerts_enabled", true);
prefs.putFloat("alert_temp_threshold", 80.0f);
```

---

## Testing

### Test Server Discovery

**1. Start Server with Discovery:**
```bash
python thermal_ar_server.py
# Should print: "Discovery service listening on port 8081"
```

**2. Test from Glass:**
```bash
# Watch logs
adb logcat ServerDiscovery:I *:S

# Should see:
# I ServerDiscovery: Sending UDP broadcast discovery on port 8081
# I ServerDiscovery: ✓ Discovered server: ThermalAR-Server (192.168.1.100:8080)
```

### Test Smart Display

**1. Test with Few Objects:**
- Expected: Full bounding boxes and labels for all

**2. Test with Many Objects (>10):**
- Expected: Top 3 get full details, next 5 get minimal UI, rest hidden

**3. Test Center Focus:**
- Move camera to point at different objects
- Expected: Object in center gets priority even if confidence is lower

**4. Test Temperature Priority:**
- Send hot object (>80°C)
- Expected: Red alert indicator, moves to top priority

---

## Benefits

### Server Discovery
✅ Zero-configuration networking
✅ Automatic reconnection
✅ Multi-server support
✅ Resilient to network changes

### Smart Display
✅ Less cluttered UI
✅ Focus on what matters
✅ Optimized for small screen
✅ Better hands-free experience
✅ Temperature-aware priorities

---

## Future Enhancements

### Additional Discovery Methods
- **NFC Tap** - Tap Glass to NFC tag with server info
- **QR Code** - Scan QR code on server display
- **Bluetooth Beacon** - BLE advertisement
- **Cloud Relay** - Connect via cloud service

### Advanced Display Features
- **Spatial Audio** - 3D audio cues for object direction
- **Gesture Focus** - Tap to lock focus on specific object
- **History Trail** - Show object movement over time
- **Distance Estimation** - Show approximate distance to objects

---

**Created:** 2025-11-13
**Files:**
- `ServerDiscovery.java` - Auto-discovery client
- `SmartDisplayManager.java` - Context-aware display
