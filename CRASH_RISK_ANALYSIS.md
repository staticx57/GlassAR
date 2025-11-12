# Glass AR Application - Crash Risk Analysis

**Analysis Date:** 2025-11-12
**Status:** 🟡 Medium Risk - Graceful fallbacks needed

---

## Critical Crash Risks

### 1. **Thermal Camera Not Connected** 🔴 HIGH RISK
**Location:** MainActivity.java - onStart(), mOnDeviceConnectListener
**Risk:** App waits indefinitely for USB thermal camera, no activity if camera not connected
**Impact:** App appears frozen, no visual feedback

**Current Behavior:**
```java
// App starts -> waits for USB device attachment
// If no Boson 320 connected = no frames, black screen
```

**Fix Required:**
- Start RGB camera by default on app launch
- Show "Waiting for thermal camera..." message
- Automatically switch when thermal camera connected
- **Priority:** P0 - Critical

---

### 2. **Null Pointer Exceptions in UI Updates** 🟠 MEDIUM RISK
**Location:** Multiple methods updating UI elements
**Risk:** UI elements might be null if findViewById() fails
**Impact:** App crashes when updating UI

**Vulnerable Methods:**
- `updateBatteryIndicator()` - line 531 (mBatteryIndicator check exists ✅)
- `updateNetworkIndicator()` - line 591 (mNetworkIndicator check exists ✅)
- Frame callback center temp update - line 999 (mCenterTemperature check exists ✅)

**Status:** ✅ **Already Protected** - All UI updates have null checks

---

### 3. **Temperature Extraction Array Bounds** 🟠 MEDIUM RISK
**Location:** extractTemperatures() - line 693
**Risk:** Frame size mismatch could cause ArrayIndexOutOfBoundsException
**Impact:** App crashes during frame processing

**Current Code:**
```java
int centerOffset = (centerY * BOSON_WIDTH + centerX) * 2;
int centerPixel = (thermalFrame[centerOffset] & 0xFF) | ((thermalFrame[centerOffset + 1] & 0xFF) << 8);
```

**Fix Required:**
- Add bounds checking before array access
- Validate frame size matches expected Boson 320 dimensions
- Return default ThermalData on size mismatch
- **Priority:** P1 - High

---

### 4. **WiFi State Access Without WiFi** 🟡 LOW-MEDIUM RISK
**Location:** getWifiSignalStrength() - line 673
**Risk:** Could crash if WiFi is disabled or unavailable
**Impact:** Network indicator fails, possible crash

**Current Code:**
```java
android.net.wifi.WifiManager wifiManager =
    (android.net.wifi.WifiManager) getSystemService(Context.WIFI_SERVICE);
if (wifiManager != null) {
    android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    int rssi = wifiInfo.getRssi();
    // ...
}
```

**Risk:** `getConnectionInfo()` or `getRssi()` could throw exception if WiFi off

**Fix Required:**
- Add WiFi state check before accessing
- Add try-catch around RSSI access
- Return default signal strength (0%) if WiFi unavailable
- **Priority:** P2 - Medium

---

### 5. **Socket.IO Connection Failures** 🟢 LOW RISK
**Location:** initializeSocket() - line 815, frame callback - line 988
**Risk:** Network operations could fail
**Impact:** Loss of server communication

**Current Protection:**
```java
if (mConnected && mSocket != null) {
    // Socket operations
}
```

**Status:** ✅ **Already Protected** - All Socket.IO operations guarded by connection check

---

### 6. **RGB Camera Initialization Failure** 🟡 MEDIUM RISK
**Location:** startRgbCamera() - line 1233
**Risk:** Camera.open() could fail or throw exception
**Impact:** Fusion modes crash when switching

**Current Code:**
```java
try {
    mRgbCamera = android.hardware.Camera.open(0);
    // ...
} catch (Exception e) {
    Log.e(TAG, "Failed to start RGB camera", e);
    Toast.makeText(this, "Failed to start RGB camera", Toast.LENGTH_SHORT).show();
}
```

**Status:** ✅ **Already Protected** - Has try-catch with graceful error handling

---

### 7. **JSON Parsing Errors** 🟢 LOW RISK
**Location:** handleAnnotations() - line 1036, handleAutoSnapshotSettings() - line 765
**Risk:** Malformed JSON could cause JSONException
**Impact:** Feature fails but app doesn't crash

**Current Protection:**
```java
try {
    JSONObject data = (JSONObject) args[0];
    // Parse JSON
} catch (JSONException e) {
    Log.e(TAG, "Error parsing...", e);
}
```

**Status:** ✅ **Already Protected** - All JSON operations wrapped in try-catch

---

### 8. **Surface Holder Null During Rendering** 🟡 LOW-MEDIUM RISK
**Location:** renderThermalFrame() - line 1070
**Risk:** mSurfaceHolder could be null if surface destroyed
**Impact:** App crashes when rendering frame

**Current Code:**
```java
private void renderThermalFrame(ByteBuffer frameData) {
    if (mSurfaceHolder == null) return;  // ✅ Protected

    Canvas canvas = mSurfaceHolder.lockCanvas();
    if (canvas == null) return;  // ✅ Protected
```

**Status:** ✅ **Already Protected** - Has null checks

---

## Implementation Plan

### Phase 1: Critical Fixes (1-2 hours)

#### 1.1 Add Array Bounds Checking in Temperature Extraction
```java
private ThermalData extractTemperatures(byte[] thermalFrame) {
    try {
        // Validate frame size
        int expectedSize = BOSON_WIDTH * BOSON_HEIGHT * 2; // YUYV = 2 bytes/pixel
        if (thermalFrame == null || thermalFrame.length < expectedSize) {
            Log.w(TAG, "Invalid thermal frame size: " +
                  (thermalFrame != null ? thermalFrame.length : "null"));
            return new ThermalData(0, 0, 0, 0);
        }

        // Calculate center pixel offset with bounds check
        int centerOffset = (BOSON_HEIGHT / 2) * BOSON_WIDTH + (BOSON_WIDTH / 2);
        centerOffset *= 2; // YUYV format

        if (centerOffset + 1 >= thermalFrame.length) {
            Log.e(TAG, "Center offset out of bounds");
            return new ThermalData(0, 0, 0, 0);
        }

        // ... rest of extraction
    } catch (Exception e) {
        Log.e(TAG, "Error extracting temperatures", e);
        return new ThermalData(0, 0, 0, 0);
    }
}
```

#### 1.2 Improve WiFi Signal Strength Error Handling
```java
private int getWifiSignalStrength() {
    try {
        android.net.wifi.WifiManager wifiManager =
            (android.net.wifi.WifiManager) getSystemService(Context.WIFI_SERVICE);

        if (wifiManager == null) {
            Log.w(TAG, "WiFi manager unavailable");
            return 0; // No signal
        }

        // Check if WiFi is enabled
        if (!wifiManager.isWifiEnabled()) {
            Log.w(TAG, "WiFi is disabled");
            return 0;
        }

        android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            Log.w(TAG, "WiFi info unavailable");
            return 0;
        }

        int rssi = wifiInfo.getRssi();
        // Convert RSSI to percentage (typical range: -100 to -50 dBm)
        return Math.max(0, Math.min(100, (rssi + 100) * 2));

    } catch (SecurityException e) {
        Log.e(TAG, "Missing WiFi permission", e);
        return 0;
    } catch (Exception e) {
        Log.e(TAG, "Error getting WiFi signal strength", e);
        return 0;
    }
}
```

### Phase 2: RGB Camera Fallback (2-3 hours) - DEFERRED
**Note:** RGB camera fallback implementation deferred to next session
**Reason:** Complex feature requiring significant refactoring
**Workaround:** App works if thermal camera is connected at startup

---

## Testing Checklist

### Crash Resistance Tests

- [ ] **No Thermal Camera Test**
  - Start app without Boson 320 connected
  - Expected: App should show message, not crash
  - Current: App waits (no crash, but no feedback)

- [ ] **WiFi Disabled Test**
  - Disable WiFi and start app
  - Expected: Network indicator shows 0%, no crash
  - Status: Needs bounds check improvement ⚠️

- [ ] **Network Disconnection Test**
  - Start app, disconnect from server mid-session
  - Expected: Connection status updates, no crash
  - Status: Already protected ✅

- [ ] **Malformed JSON Test**
  - Send invalid JSON from server
  - Expected: Log error, continue operation
  - Status: Already protected ✅

- [ ] **Camera Permission Denied Test**
  - Deny camera permission
  - Expected: Show error, continue with thermal only
  - Status: Already protected ✅

- [ ] **Surface Destroyed During Render Test**
  - Minimize app during active rendering
  - Expected: Gracefully stop rendering
  - Status: Already protected ✅

- [ ] **Wrong Frame Size Test**
  - Connect non-Boson USB camera
  - Expected: Log error, return default temperatures
  - Status: Needs bounds checking ⚠️

---

## Risk Summary

| Risk Level | Count | Status |
|------------|-------|--------|
| 🔴 High    | 1     | RGB fallback not implemented |
| 🟠 Medium  | 3     | 2 need fixes, 1 already protected |
| 🟡 Low-Med | 2     | 1 needs fix, 1 already protected |
| 🟢 Low     | 2     | Already protected |

**Overall Assessment:** App is relatively crash-resistant with good error handling in most areas. Critical fixes needed:
1. Add bounds checking in temperature extraction
2. Improve WiFi state handling
3. (Deferred) Implement RGB camera fallback for missing thermal camera

---

## Recommended Immediate Actions

### Must Fix Now:
1. ✅ Add bounds checking to `extractTemperatures()`
2. ✅ Improve WiFi state checking in `getWifiSignalStrength()`

### Can Defer:
3. ⏳ RGB camera fallback (complex feature, requires significant refactoring)
4. ⏳ Runtime permission requests (Glass EE2 uses system-level permissions)

---

**Next Steps:** Implement Phase 1 critical fixes to prevent crashes from frame size mismatches and WiFi state issues.

**Generated:** 2025-11-12
**Analyst:** Claude AI Assistant
