# Settings Ownership & Negotiation Protocol

## Overview

Bidirectional settings synchronization with clear ownership boundaries and performance-based negotiation.

---

## Ownership Model

### Glass-Owned Settings (Authoritative)

Glass has full control over these settings:

| Setting | Description | Why Glass Owns It |
|---------|-------------|-------------------|
| `format` | Video format (MJPEG/Y16/I420) | Glass knows camera capabilities |
| `display_mode` | UI mode (MINIMAL/STANDARD/DETAILED) | User preference, local UI |
| `frame_skip` | Frame decimation factor | Glass knows network capacity |
| `compression_quality` | JPEG quality (if using) | Glass manages bandwidth |
| `local_ui_*` | UI preferences | Local display settings |

**Server behavior:** Accept and log, never override

---

### Server-Owned Settings (Authoritative)

Server has full control over these settings:

| Setting | Description | Why Server Owns It |
|---------|-------------|-------------------|
| `processing_mode` | AI mode (building/search_rescue/electronics) | Server manages models |
| `detection_threshold` | Min confidence for detections | Server-side ML parameter |
| `thermal_analysis_enabled` | Enable thermal analysis | Server capability |
| `max_detections` | Max objects to return | Server processing limit |

**Glass behavior:** Accept and apply, never override

---

### Negotiated Settings (Performance-Based)

Both sides can propose, winner determined by performance metrics:

| Setting | Negotiation Logic | Priority |
|---------|-------------------|----------|
| `target_fps` | Glass requests reduction if struggling, Server suggests based on capacity | Glass performance wins |
| `ml_model_complexity` | Glass requests lighter model if FPS drops, Server suggests based on accuracy needs | Glass performance wins if struggling |
| `batch_size` | Server suggests based on GPU, Glass can reject if latency too high | Latency wins |

**Resolution:** Use timestamps + performance metrics

---

## Negotiation Protocol

### 1. Glass-Owned Setting Change

**Scenario:** Glass switches from MJPEG to Y16

```json
// Glass → Server
{
  "glass_settings": {
    "format": {
      "value": "Y16",
      "ownership": "glass",
      "last_modified": 1234567890,
      "reason": "user_selected"
    }
  }
}

// Server → Glass
{
  "server_settings": {
    "format": {
      "value": "Y16",
      "accepted": true,
      "message": "Format updated to Y16"
    }
  }
}
```

Server **must accept** and update its expectations.

---

### 2. Server-Owned Setting Change

**Scenario:** Server switches mode from building to search_rescue

```json
// Server → Glass (in sync_response)
{
  "server_settings": {
    "processing_mode": {
      "value": "search_rescue",
      "ownership": "server",
      "last_modified": 1234567891,
      "reason": "operator_changed"
    }
  }
}

// Glass applies immediately
mCurrentMode = "search_rescue";
updateModeIndicator();
```

Glass **must accept** and apply immediately.

---

### 3. Negotiated Setting (Performance-Based)

**Scenario:** Glass FPS drops below 15, requests lighter ML model

```json
// Glass → Server
{
  "glass_settings": {
    "fps_actual": 12.5,
    "cpu_usage": 95,
    "thermal_throttling": true
  },
  "negotiation_request": {
    "setting": "ml_model_complexity",
    "proposed_value": "light",  // yolov8n
    "current_value": "medium",  // yolov8s
    "reason": "performance_degradation",
    "metrics": {
      "fps_target": 30,
      "fps_actual": 12.5,
      "frame_drop_rate": 0.58
    }
  }
}

// Server evaluates request
// If accuracy can be sacrificed for performance, accept
{
  "negotiation_response": {
    "setting": "ml_model_complexity",
    "decision": "accepted",  // or "rejected"
    "new_value": "light",
    "reason": "Glass performance critical, switching to YOLOv8n",
    "expected_improvement": "FPS should increase to 25-30"
  },
  "server_settings": {
    "ml_model": "yolov8n",
    "ml_model_complexity": "light"
  }
}
```

**Decision Logic:**
- If Glass FPS < 15 → Accept lighter model
- If Glass FPS > 25 → Can suggest heavier model for better accuracy
- If mission-critical mode (search_rescue) → May reject to maintain accuracy

---

### 4. Timestamp-Based Conflict Resolution

**Scenario:** Both sides changed same setting simultaneously

```json
// Glass changed target_fps at timestamp 1234567890
{
  "glass_settings": {
    "target_fps": {
      "value": 20,
      "last_modified": 1234567890
    }
  }
}

// Server changed target_fps at timestamp 1234567892 (2s later)
{
  "server_settings": {
    "target_fps": {
      "value": 30,
      "last_modified": 1234567892
    }
  }
}

// Resolution: Server timestamp is newer → Server wins
// Glass updates to 30 FPS
```

**Rule:** Most recent timestamp wins for negotiated settings

---

## Performance-Based ML Model Negotiation

### Glass Performance Metrics

```json
{
  "performance_metrics": {
    "fps_actual": 12.5,
    "fps_target": 30,
    "frame_drop_rate": 0.58,
    "cpu_usage": 95,
    "thermal_state": "throttling",  // normal/warm/hot/throttling
    "battery_level": 45,
    "network_latency_ms": 85,
    "processing_latency_ms": 150
  }
}
```

### Server ML Models Available

| Model | Complexity | Accuracy | Speed | Use Case |
|-------|------------|----------|-------|----------|
| yolov8n | Light | 70% mAP | Fast | Performance-critical |
| yolov8s | Medium | 80% mAP | Moderate | Balanced (default) |
| yolov8m | Heavy | 85% mAP | Slow | Accuracy-critical |

### Negotiation Triggers

**Glass requests lighter model when:**
- FPS < 15 for 5+ seconds
- Thermal throttling detected
- Battery < 20% and need to conserve

**Server suggests heavier model when:**
- Glass FPS > 25 consistently
- Mission-critical mode (search_rescue)
- Accuracy complaints from operator

### Example Negotiation Flow

```
┌─────────┐                              ┌─────────┐
│  Glass  │                              │  Server │
└────┬────┘                              └────┬────┘
     │                                        │
     │ Normal operation                       │
     │ FPS: 28-30, Model: yolov8s (medium)    │
     │                                        │
     │ [FPS drops to 12]                      │
     │                                        │
     │ settings_sync                          │
     │ {fps: 12, negotiation_request:        │
     │  "switch to yolov8n"}                  │
     │───────────────────────────────────────>│
     │                                        │
     │                                        │ Evaluates:
     │                                        │ - Not search_rescue mode ✓
     │                                        │ - FPS critically low ✓
     │                                        │ - Accept request
     │                                        │
     │                                        │ [Loads yolov8n model]
     │                                        │
     │ settings_sync_response                 │
     │ {decision: "accepted",                 │
     │  ml_model: "yolov8n"}                  │
     │<───────────────────────────────────────│
     │                                        │
     │ [FPS recovers to 28-30]                │
     │                                        │
```

---

## Implementation Rules

### Glass Implementation

```java
private void handleSettingsSyncResponse(JSONObject data) {
    JSONObject serverSettings = data.getJSONObject("server_settings");

    // 1. Server-owned settings: Accept immediately
    if (serverSettings.has("processing_mode")) {
        JSONObject modeSetting = serverSettings.getJSONObject("processing_mode");
        if ("server".equals(modeSetting.getString("ownership"))) {
            String newMode = modeSetting.getString("value");
            mCurrentMode = newMode;  // Apply immediately
            Log.i(TAG, "Server changed mode to: " + newMode);
        }
    }

    // 2. Glass-owned settings: Confirm acceptance
    if (data.has("accepted_settings")) {
        JSONArray accepted = data.getJSONArray("accepted_settings");
        Log.i(TAG, "Server accepted Glass settings: " + accepted.toString());
    }

    // 3. Negotiated settings: Check decision
    if (data.has("negotiation_response")) {
        handleNegotiationResponse(data.getJSONObject("negotiation_response"));
    }
}

private void requestModelDowngrade() {
    if (mCurrentFPS < 15 && !mNegotiationPending) {
        JSONObject negotiation = new JSONObject();
        negotiation.put("setting", "ml_model_complexity");
        negotiation.put("proposed_value", "light");
        negotiation.put("reason", "performance_degradation");

        JSONObject metrics = new JSONObject();
        metrics.put("fps_actual", mCurrentFPS);
        metrics.put("fps_target", 30);
        negotiation.put("metrics", metrics);

        mSocket.emit("negotiation_request", negotiation);
        mNegotiationPending = true;
    }
}
```

### Server Implementation

```python
@socketio.on('settings_sync')
def handle_settings_sync(data):
    glass_settings = data.get('glass_settings', {})
    accepted_settings = []
    rejected_settings = []

    # 1. Glass-owned settings: Accept all
    for setting_name in GLASS_OWNED_SETTINGS:
        if setting_name in glass_settings:
            setting = glass_settings[setting_name]
            # Update server expectations
            update_glass_setting(setting_name, setting['value'])
            accepted_settings.append(setting_name)
            logger.info(f"Accepted Glass setting: {setting_name}={setting['value']}")

    # 2. Evaluate performance and negotiate if needed
    performance = data.get('performance_metrics', {})
    negotiation = evaluate_performance_negotiation(performance)

    # 3. Build response
    response = {
        'server_settings': get_server_owned_settings(),
        'accepted_settings': accepted_settings,
        'rejected_settings': rejected_settings
    }

    if negotiation:
        response['negotiation_response'] = negotiation

    emit('settings_sync_response', response)

def evaluate_performance_negotiation(performance):
    fps = performance.get('fps_actual', 30)

    # Glass is struggling
    if fps < 15 and current_ml_model != 'yolov8n':
        return {
            'setting': 'ml_model_complexity',
            'decision': 'accepted',
            'new_value': 'light',
            'reason': 'Glass FPS critically low, switching to lighter model'
        }

    # Glass is doing well, can upgrade
    if fps > 25 and current_ml_model == 'yolov8n' and current_mode != 'search_rescue':
        return {
            'setting': 'ml_model_complexity',
            'decision': 'suggested',
            'proposed_value': 'medium',
            'reason': 'Glass performance good, can upgrade for better accuracy'
        }

    return None
```

---

## Settings Reference

### Complete Settings Table

| Setting | Type | Ownership | Default | Range/Options |
|---------|------|-----------|---------|---------------|
| `format` | enum | Glass | MJPEG | MJPEG, Y16, I420 |
| `display_mode` | enum | Glass | STANDARD | MINIMAL, STANDARD, DETAILED |
| `frame_skip` | int | Glass | 1 | 1-5 |
| `processing_mode` | enum | Server | building | building, search_rescue, electronics |
| `ml_model` | enum | Server | yolov8s | yolov8n, yolov8s, yolov8m |
| `detection_threshold` | float | Server | 0.5 | 0.1-0.9 |
| `target_fps` | int | Negotiated | 30 | 10-60 |
| `ml_model_complexity` | enum | Negotiated | medium | light, medium, heavy |

---

## Benefits

✅ **Clear ownership** - No ambiguity about who controls what
✅ **Performance-driven** - System adapts to real-world conditions
✅ **Graceful degradation** - Automatically reduces load when struggling
✅ **Operator override** - Server can maintain critical settings when needed
✅ **Conflict resolution** - Timestamp-based resolution for edge cases
✅ **Transparent** - All changes logged and communicated

---

**Created:** 2025-11-13
**Branch:** claude/debug-flir-boson-uvc-01YZahd82idMfsTBELEZq7L4
