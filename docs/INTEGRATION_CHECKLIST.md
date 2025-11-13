# Integration Checklist - Smart Features

Quick checklist for integrating the smart server discovery and object display features.

## ✅ What's Ready to Use

### 1. Server Discovery (Auto-find server on network)
- ✅ **ServerDiscovery.java** - Client-side discovery
- ✅ **thermal_ar_server.py** - Server-side responder (already integrated)
- ✅ UDP broadcast protocol on port 8081
- ✅ Documentation in `docs/SMART_FEATURES.md`

### 2. Smart Object Display (Context-aware Glass UI)
- ✅ **SmartDisplayManager.java** - Intelligent display prioritization
- ✅ Three display modes (Minimal, Standard, Detailed)
- ✅ Temperature-aware coloring
- ✅ Center-focus algorithm
- ✅ Documentation in `docs/SMART_FEATURES.md`

---

## 🔧 Integration Steps

### Quick Integration (30 min)

**Step 1: Test Server Discovery**

Server side is already done! Just test it:

```bash
# Start server (discovery auto-starts)
python thermal_ar_server.py

# Should see:
# ✓ Discovery service listening on port 8081
#   Glass devices will auto-discover this server
```

**Step 2: Add Discovery to MainActivity** (Optional - can do later)

Add these to `MainActivity.java`:

```java
// At class level
private ServerDiscovery mServerDiscovery;

// In onCreate()
mServerDiscovery = new ServerDiscovery(this);
startServerDiscovery();

// Add method
private void startServerDiscovery() {
    mServerDiscovery.startDiscovery(new ServerDiscovery.IDiscoveryCallback() {
        @Override
        public void onServerFound(ServerDiscovery.ServerInfo server) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this,
                    "Found: " + server.name, Toast.LENGTH_SHORT).show();
                saveServerUrl(server.getUrl());
                reconnectSocket();
            });
        }

        @Override
        public void onDiscoveryComplete(List<ServerDiscovery.ServerInfo> servers) {
            // Fallback to saved if none found
        }

        @Override
        public void onDiscoveryFailed(String error) {
            Log.w(TAG, "Discovery failed: " + error);
        }
    });
}
```

**Step 3: Add Smart Display** (Recommended - big improvement)

Replace your `drawAnnotations()` method:

```java
// At class level
private SmartDisplayManager mSmartDisplay;

// In onCreate()
mSmartDisplay = new SmartDisplayManager();
mSmartDisplay.setDisplayMode(SmartDisplayManager.DisplayMode.STANDARD);

// Replace drawAnnotations() method
private void drawAnnotations(Canvas canvas) {
    // Convert your Detection objects
    List<SmartDisplayManager.AnnotatedObject> objects = new ArrayList<>();

    for (Detection det : mDetections) {
        SmartDisplayManager.AnnotatedObject obj =
            new SmartDisplayManager.AnnotatedObject(
                det.bbox, det.confidence, det.className);

        // Add temperature if available
        if (mThermalAnalysis != null) {
            // Your logic to match detection with temp
            obj.temperature = getTemperatureForDetection(det);
        }

        objects.add(obj);
    }

    // Smart display handles the rest
    float scaleX = (float) GLASS_WIDTH / BOSON_WIDTH;
    float scaleY = (float) GLASS_HEIGHT / BOSON_HEIGHT;

    mSmartDisplay.drawAnnotations(canvas, objects, scaleX, scaleY);
}
```

---

## 🧪 Testing

### Test Server Discovery

```bash
# Terminal 1: Start server
python thermal_ar_server.py

# Terminal 2: Monitor Glass logs
adb logcat ServerDiscovery:I MainActivity:I *:S

# Expected on Glass:
# I ServerDiscovery: Sending UDP broadcast discovery
# I ServerDiscovery: ✓ Discovered server: ThermalAR-Server (192.168.1.x:8080)
# I MainActivity: Found: ThermalAR-Server
```

### Test Smart Display

**Test 1: Few Objects (< 3)**
- Expected: All objects shown with full details

**Test 2: Many Objects (> 10)**
- Expected: Top 3 with full details, next 5 minimal, rest hidden

**Test 3: Hot Object**
- Send object with temp > 80°C
- Expected: Red alert indicator, top priority

**Test 4: Center Focus**
- Point camera at different objects
- Expected: Center object prioritized even if lower confidence

---

## 📊 Before vs After

### Server Connection

**Before:**
```java
// Hardcoded IP
String serverUrl = "http://192.168.1.100:8080";
// Fails if IP changes
```

**After:**
```java
// Auto-discovery
startServerDiscovery();
// Finds server automatically
```

### Object Display

**Before:**
```java
// Show ALL objects
for (Detection det : allDetections) {
    drawBox(det);
    drawLabel(det);
}
// Cluttered on small screen
```

**After:**
```java
// Smart prioritization
mSmartDisplay.drawAnnotations(canvas, objects, scaleX, scaleY);
// Shows top 3 detailed, next 5 minimal
// Hot objects prioritized
// Center-focused
```

---

## 🎨 Display Mode Options

**Switch modes with gesture:**
```java
private void onSwipeUp() {
    SmartDisplayManager.DisplayMode[] modes =
        SmartDisplayManager.DisplayMode.values();
    int current = getCurrentModeIndex();
    int next = (current + 1) % modes.length;

    mSmartDisplay.setDisplayMode(modes[next]);
    Toast.makeText(this, "Display: " + modes[next],
        Toast.LENGTH_SHORT).show();
}
```

**Modes:**
- **MINIMAL** - Dots + temps (for crowded scenes)
- **STANDARD** - Boxes + labels (default, balanced)
- **DETAILED** - Full info + HUD stats (maximum detail)

---

## 🚀 Benefits

### Server Discovery
✅ **Zero-config** - Just start server, Glass finds it
✅ **Resilient** - Survives IP changes, network switches
✅ **Multi-server** - Can choose from multiple servers
✅ **Fast** - 3 second discovery timeout

### Smart Display
✅ **Less clutter** - Only show what matters
✅ **Context-aware** - Prioritizes what you're looking at
✅ **Temperature-smart** - Hot objects get attention
✅ **Glanceable** - Quick info at a glance
✅ **Hands-free friendly** - Optimized for Glass use case

---

## 📝 Notes

- Server discovery is **backward compatible** - works with or without old manual config
- Smart display is **plug-and-play** - just replace `drawAnnotations()` method
- Both features are **independent** - can integrate one without the other
- **Fully documented** in `docs/SMART_FEATURES.md`

---

## 🔮 Future Enhancements

Easy additions:
- **Audio alerts** - Voice alerts for critical temps
- **QR code** - Scan server QR for instant connect
- **Gesture mode switch** - Swipe to change display mode
- **History trail** - Show object movement over time

---

**Created:** 2025-11-13
**Branch:** claude/debug-flir-boson-uvc-01YZahd82idMfsTBELEZq7L4
**Status:** Ready to integrate
