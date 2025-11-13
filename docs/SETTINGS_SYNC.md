# Settings Sync Protocol

## Overview

Bidirectional settings synchronization with **ownership model** and **performance-based negotiation** between Glass and server.

**⚠️ UPDATED:** This protocol now implements ownership boundaries and ML model negotiation.
For complete details on ownership rules, negotiation logic, and performance-based optimization, see **[SETTINGS_OWNERSHIP.md](./SETTINGS_OWNERSHIP.md)**.

**Key Features:**
- ✅ **Clear ownership** - Glass owns format/UI, Server owns ML/processing
- ✅ **Performance negotiation** - Automatic ML model adjustment based on FPS
- ✅ **Graceful degradation** - Glass requests lighter model when struggling
- ✅ **Intelligent upgrades** - Server suggests heavier model when Glass performs well

**Quick Summary:**
- **Glass-owned**: `format`, `display_mode`, `frame_skip` (authoritative)
- **Server-owned**: `processing_mode`, `ml_model`, `detection_threshold` (authoritative)
- **Negotiated**: `ml_model_complexity` (performance-based, Glass can request changes)

---

## Sync Protocol

### Glass → Server: `settings_sync`

**Frequency:** Every 30 seconds + on connection + on manual setting change

**Payload:**
```json
{
  "glass_settings": {
    "format": "MJPEG",              // Current video format
    "has_temperature": false,        // Radiometric capability
    "display_mode": "STANDARD",      // UI display mode
    "current_mode": "building",      // Processing mode
    "frame_skip": 2,                 // Frame decimation (1 = no skip)
    "server_url": "http://x.x.x.x:8080",
    "app_version": "1.0.0",
    "uptime_ms": 123456,
    "fps_actual": 28.5
  },
  "timestamp": 1234567890
}
```

### Server → Glass: `settings_sync_response`

**Payload:**
```json
{
  "server_settings": {
    "processing_mode": "building",    // Current server mode
    "model_loaded": true,             // AI model ready
    "capabilities": [                 // Available features
      "object_detection",
      "thermal_analysis",
      "person_detection"
    ],
    "max_fps": 30,                    // Server max processing rate
    "compression_enabled": true,      // WebSocket compression
    "server_version": "1.0.0",
    "format_preferred": "MJPEG"       // Suggested format for best performance
  },
  "sync_status": "ok",                // "ok" or "mismatch"
  "mismatches": [],                   // List of mismatched settings
  "timestamp": 1234567890
}
```

---

## Sync Triggers

1. **On Connection** - Immediate sync when socket connects
2. **Periodic** - Every 30 seconds while connected
3. **On Setting Change** - When user changes display mode, etc.
4. **On Reconnection** - After network interruption recovery

---

## Implementation

### Glass Side (MainActivity.java)

```java
// Settings sync constants
private static final int SETTINGS_SYNC_INTERVAL_MS = 30000;  // 30 seconds
private Handler mSyncHandler;
private Runnable mSyncRunnable;

// Initialize sync timer
private void initializeSettingsSync() {
    mSyncHandler = new Handler(Looper.getMainLooper());
    mSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (mConnected && mSocket != null) {
                sendSettingsSync();
                mSyncHandler.postDelayed(this, SETTINGS_SYNC_INTERVAL_MS);
            }
        }
    };
}

// Send settings sync
private void sendSettingsSync() {
    try {
        JSONObject payload = new JSONObject();
        JSONObject settings = new JSONObject();

        // Glass settings
        settings.put("format", mDetectedFormat != null ? mDetectedFormat.toString() : "unknown");
        settings.put("has_temperature", mDetectedFormat == BosonFormat.Y16);
        settings.put("display_mode", mSmartDisplay != null ?
            mSmartDisplay.getDisplayMode().toString() : "STANDARD");
        settings.put("current_mode", mCurrentMode);
        settings.put("frame_skip", FRAME_SKIP);
        settings.put("server_url", mServerUrl);
        settings.put("app_version", BuildConfig.VERSION_NAME);
        settings.put("uptime_ms", SystemClock.elapsedRealtime());
        settings.put("fps_actual", calculateCurrentFPS());

        payload.put("glass_settings", settings);
        payload.put("timestamp", System.currentTimeMillis());

        mSocket.emit("settings_sync", payload);
        Log.d(TAG, "Settings sync sent");

    } catch (JSONException e) {
        Log.e(TAG, "Error creating settings sync", e);
    }
}

// Handle settings sync response
private void handleSettingsSyncResponse(JSONObject data) {
    try {
        String syncStatus = data.getString("sync_status");
        JSONObject serverSettings = data.getJSONObject("server_settings");

        // Log server capabilities
        JSONArray capabilities = serverSettings.getJSONArray("capabilities");
        Log.i(TAG, "Server capabilities: " + capabilities.toString());

        // Check for mismatches
        if (data.has("mismatches") && data.getJSONArray("mismatches").length() > 0) {
            JSONArray mismatches = data.getJSONArray("mismatches");
            Log.w(TAG, "Settings mismatches detected: " + mismatches.toString());

            // Optionally show user notification
            Toast.makeText(this,
                "Settings updated from server",
                Toast.LENGTH_SHORT).show();
        }

        // Update local settings if needed
        String serverMode = serverSettings.getString("processing_mode");
        if (!serverMode.equals(mCurrentMode)) {
            Log.i(TAG, "Syncing mode: " + mCurrentMode + " → " + serverMode);
            mCurrentMode = serverMode;
        }

        // Check format preference
        if (serverSettings.has("format_preferred")) {
            String preferredFormat = serverSettings.getString("format_preferred");
            Log.i(TAG, "Server prefers format: " + preferredFormat);
        }

    } catch (JSONException e) {
        Log.e(TAG, "Error parsing settings sync response", e);
    }
}

// Start sync on connection
@Override
public void onConnected() {
    Log.i(TAG, "Connected to server");
    mConnected = true;

    // Immediate sync on connection
    sendSettingsSync();

    // Start periodic sync
    mSyncHandler.postDelayed(mSyncRunnable, SETTINGS_SYNC_INTERVAL_MS);
}

// Stop sync on disconnect
@Override
public void onDisconnected() {
    mConnected = false;
    mSyncHandler.removeCallbacks(mSyncRunnable);
}
```

### Server Side (thermal_ar_server.py)

```python
# Settings sync handler
@socketio.on('settings_sync')
def handle_settings_sync(data):
    """Periodic settings synchronization from Glass"""
    try:
        glass_settings = data.get('glass_settings', {})
        client_timestamp = data.get('timestamp')

        logger.info(f"Settings sync from Glass:")
        logger.info(f"  Format: {glass_settings.get('format')}")
        logger.info(f"  Display Mode: {glass_settings.get('display_mode')}")
        logger.info(f"  Processing Mode: {glass_settings.get('current_mode')}")
        logger.info(f"  FPS: {glass_settings.get('fps_actual'):.1f}")

        # Check for mismatches
        mismatches = []

        # Compare processing mode
        glass_mode = glass_settings.get('current_mode', '')
        if glass_mode != current_mode:
            mismatches.append({
                'setting': 'current_mode',
                'glass_value': glass_mode,
                'server_value': current_mode
            })
            logger.warning(f"Mode mismatch: Glass={glass_mode}, Server={current_mode}")

        # Check format compatibility
        glass_format = glass_settings.get('format', 'unknown')
        if glass_format not in ['MJPEG', 'Y16', 'I420', 'unknown']:
            mismatches.append({
                'setting': 'format',
                'glass_value': glass_format,
                'server_value': 'unsupported'
            })

        # Build response
        response = {
            'server_settings': {
                'processing_mode': current_mode,
                'model_loaded': processor is not None,
                'capabilities': ['object_detection', 'thermal_analysis'],
                'max_fps': 30,
                'compression_enabled': True,
                'server_version': '1.0.0',
                'format_preferred': 'MJPEG'  # Best compression
            },
            'sync_status': 'ok' if len(mismatches) == 0 else 'mismatch',
            'mismatches': mismatches,
            'timestamp': int(time.time() * 1000),
            'client_timestamp': client_timestamp
        }

        # Send response
        emit('settings_sync_response', response)
        logger.debug(f"Settings sync response sent ({len(mismatches)} mismatches)")

    except Exception as e:
        logger.error(f"Settings sync error: {e}")
        emit('settings_sync_response', {
            'sync_status': 'error',
            'error': str(e),
            'timestamp': int(time.time() * 1000)
        })
```

---

## Benefits

✅ **Automatic alignment** - Settings stay synced without manual intervention
✅ **Capability discovery** - Glass knows what server can do
✅ **Mismatch detection** - Warns when settings are out of sync
✅ **Performance tuning** - Server can suggest optimal format/settings
✅ **Debugging aid** - Logs help diagnose configuration issues
✅ **Version tracking** - Both sides know each other's versions

---

## Testing

### 1. Test Periodic Sync

**Start server:**
```bash
python thermal_ar_server.py
```

**Monitor server logs:**
```bash
# Should see settings sync every 30 seconds:
# "Settings sync from Glass: Format=MJPEG, Mode=building, FPS=28.5"
```

**Monitor Glass logs:**
```bash
adb logcat MainActivity:I *:S | grep -i "sync"
# Should see:
# I MainActivity: Settings sync sent
# I MainActivity: Server capabilities: [object_detection, thermal_analysis]
```

### 2. Test Mismatch Detection

**Change server mode:**
```python
# In server, change current_mode to 'search_rescue'
current_mode = 'search_rescue'
```

**Expected:**
- Server logs: "Mode mismatch: Glass=building, Server=search_rescue"
- Glass receives mismatch notification
- Glass updates to match server mode

### 3. Test Connection Sync

**Disconnect and reconnect:**
- Settings should sync immediately on reconnection
- No delay waiting for 30-second timer

---

## Future Enhancements

- **Bidirectional mode sync** - Server can change Glass mode
- **Settings presets** - Save/load configuration profiles
- **Remote control** - Server can trigger Glass actions
- **Health monitoring** - Track Glass battery, thermal status
- **Bandwidth adaptation** - Auto-adjust quality based on network

---

**Created:** 2025-11-13
**Branch:** claude/debug-flir-boson-uvc-01YZahd82idMfsTBELEZq7L4
