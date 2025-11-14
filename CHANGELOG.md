# Changelog - UVC Streaming Fix Session

## Date: November 14, 2025

### Overview
Major overhaul of UVC camera streaming to fix multiple critical issues preventing FLIR Boson 320 thermal camera from working properly on Glass Enterprise Edition 2.

---

## ✅ FIXED - UVC Streaming Issues

### 1. InvalidMarkException Crash (CRITICAL FIX)
**Issue:** Application crashed immediately when streaming started
- **Location:** `NativeUVCCamera.java:503`
- **Cause:** Incorrect use of ByteBuffer mark()/reset() - calling position(0) after mark() invalidated the mark
- **Fix:** Replaced mark/reset pattern with manual position save/restore
- **Status:** ✅ FIXED - No more crashes

### 2. Headerless Packet Handling (CRITICAL FIX - Root Cause of Split Screen)
**Issue:** "Split screen" display corruption, frames never completing properly
- **Cause:** Boson camera sends raw I420 data without UVC payload headers
  - First byte (e.g., 128, 160, 191) was interpreted as invalid header length
  - Code set headerLength=0 but then skipped all frame completion logic
  - Frames accumulated forever or with wrong boundaries
- **Fix:**
  - Unified packet processing for both header and headerless modes
  - Frame completion logic now runs for ALL packet types
  - Size-based detection works correctly (I420 = 491,520 bytes)
  - Added diagnostic logging for invalid headers
- **Status:** ✅ FIXED - Streaming works perfectly (1,038 frames, 0 errors before disconnect)

### 3. UVC Header Validation
**Issue:** Accepted any header length value (1-255), causing potential data corruption
- **Fix:**
  - Validate header length must be 0 or 2-12 bytes per UVC spec
  - Reject values like 1, 255 that indicate corrupt headers or payload data
  - Log warnings for first 5 invalid headers only
- **Status:** ✅ FIXED

### 4. Frame Accumulation Timeout
**Issue:** Frames could accumulate forever if EOF bit missing
- **Fix:**
  - Added 1-second timeout for frame accumulation
  - Automatic reset and fresh start if timeout occurs
  - Logs show format, accumulated bytes, and EOF bit status
- **Status:** ✅ FIXED - Prevents stuck states

### 5. Buffer Overflow Recovery
**Issue:** Buffer overflow would restart accumulation mid-frame, corrupting data
- **Fix:**
  - Check for JPEG SOI (0xFF 0xD8) before accepting new frame start
  - Only start new frame if valid frame start detected
  - Discard garbage data until clean frame start found
- **Status:** ✅ FIXED

### 6. Compilation Errors
**Issues:**
- `NativeUVCCamera.java:492` - Variable 'accumulated' not defined
- `NativeUVCCamera.java:637` - Duplicate variable 'now' declaration
- `MainActivity.java:1867` - Missing method 'updateModeIndicator()'

**Fixes:**
- Changed to use frameBuffer.position() for logging condition
- Removed duplicate 'long' keyword to reuse existing variable
- Added updateModeIndicator() method to update mode TextView

**Status:** ✅ FIXED

### 7. Diagnostic Logging Enhancements
**Additions:**
- MJPEG decode logging with dimensions and bitmap config
- Size mismatch warnings when decoded size != expected
- Colormap application logging
- Rendering bitmap size logging before display
- UVC header details (length, EOF bit, error bit, bit field)

**Status:** ✅ IMPLEMENTED

---

## ⚠️ PARTIALLY FIXED - RGB Camera Fallback

### RGB Camera Fallback Issues
**Issue:** RGB camera fails to start when thermal camera disconnects
- **Error:** `java.lang.RuntimeException: startPreview failed`
- **Attempted Fixes:**
  1. ✅ Query supported preview sizes and find closest match (640x360)
  2. ✅ Validate surface before setPreviewDisplay()
  3. ✅ Wrap startPreview() in try-catch
  4. ✅ Set explicit NV21 preview format
  5. ✅ Configure focus mode (CONTINUOUS_VIDEO or AUTO)
  6. ✅ Add 100ms stabilization delay before startPreview()
  7. ✅ Enhanced error handling and logging

**Current Status:** ❌ STILL FAILING
- Camera opens successfully
- Finds correct preview size (640x360)
- Surface reports as valid
- Parameters set successfully
- But startPreview() throws RuntimeException

**Hypothesis:** Glass EE2 camera may have additional requirements or surface state issues after thermal camera disconnect. May need Camera2 API or different approach.

---

## 🔧 BUILD SYSTEM FIXES

### Gradle Compatibility Journey

#### Issue 1: Gradle 9.1 Incompatibility
**Problem:** User ran `gradle` (system Gradle 9.1) instead of `./gradlew` (wrapper Gradle 7.6)
- AGP 7.4.2 uses APIs deprecated in Gradle 9
- Build failed with deprecation errors

**Attempted Fix 1:** Upgrade to AGP 8.2.0
- Updated build.gradle to use AGP 8.2.0
- Added modern settings.gradle with dependencyResolutionManagement
- Result: ❌ FAILED with "Cannot mutate dependencies after configuration resolved"

**Attempted Fix 2:** Change to PREFER_SETTINGS mode
- Changed from FAIL_ON_PROJECT_REPOS to PREFER_SETTINGS
- Result: ❌ STILL FAILED - Same error

**Attempted Fix 3:** Remove dependencyResolutionManagement
- Removed dependency resolution from settings.gradle
- Added repositories directly to app/build.gradle
- Result: ❌ STILL FAILED - AGP 8.x conflicts

**FINAL SOLUTION:** Revert to Gradle 7.6 wrapper configuration
- Reverted AGP 8.2.0 → 7.4.2
- Restored allprojects block
- Restored packagingOptions and lintOptions syntax
- Restored package attribute in AndroidManifest.xml
- Use Gradle wrapper: `./gradlew` or `gradlew.bat`
- Result: ✅ WORKING PERFECTLY

---

## 📊 Performance Metrics

### UVC Streaming Performance
- **Format:** I420 (640×512)
- **Frame Rate:** ~19 FPS (94 frames per 5 seconds)
- **Stability:** 1,038 frames with 0 errors before disconnect
- **Frame Size:** 491,520 bytes (exactly as expected)
- **Snapshots:** Working perfectly (165,159 bytes PNG saved)

### Diagnostic Output
```
✓ I420 frame (640×512, no telemetry)
✓ Frame #5 delivered: 491520 bytes (I420, 640×512)
Streaming status: 1038 frames received, 0 errors
```

---

## 📝 Files Modified

### Core Fixes
1. **NativeUVCCamera.java**
   - Fixed InvalidMarkException
   - Implemented headerless packet handling
   - Added frame timeout protection
   - Enhanced diagnostic logging
   - Added UVC header validation

2. **MainActivity.java**
   - Fixed compilation errors (updateModeIndicator)
   - Enhanced MJPEG diagnostic logging
   - Improved RGB camera fallback (partial)
   - Added preview size detection
   - Added surface validation
   - Added stabilization delay

### Build Configuration
3. **build.gradle**
   - Maintained AGP 7.4.2 for Gradle 7.6 compatibility
   - Maintained allprojects block

4. **app/build.gradle**
   - Maintained packagingOptions syntax
   - Maintained lintOptions syntax

5. **AndroidManifest.xml**
   - Maintained package attribute (required for AGP 7)

6. **settings.gradle**
   - Minimal configuration (no dependency management)

---

## 🎯 Success Criteria

### ✅ Achieved
- [x] UVC streaming works without crashes
- [x] Frames delivered correctly without corruption
- [x] I420 format (640×512) correctly detected
- [x] Stable streaming (1000+ frames, 0 errors)
- [x] Snapshot capture working
- [x] Build completes successfully with Gradle wrapper
- [x] Clean camera disconnect handling

### ❌ Not Achieved
- [ ] RGB camera fallback functional after thermal disconnect
- [ ] Gradle 9.x compatibility (intentionally reverted)

---

## 🔍 Technical Details

### FLIR Boson 320 Behavior
- Sends **I420 format at 640×512** resolution (upscaled from 320×256)
- Operates in **headerless mode** (no UVC payload headers)
- First byte of each packet is payload data, NOT header length
- Frame size is exactly 491,520 bytes (640 × 512 × 1.5 for YUV420)
- No telemetry rows in this mode

### Frame Detection Method
- **Size-based detection** for headerless I420 frames
- Accumulates packets until exactly 491,520 bytes received
- No EOF bit available (headerless mode)
- Timeout protection prevents infinite accumulation

---

## 🚀 Recommendations

### RGB Camera Fallback
1. Investigate Glass EE2 camera API requirements
2. Consider using Camera2 API instead of deprecated Camera API
3. May need to recreate surface or use different preview approach
4. Test with minimal parameters (no focus mode, default format)

### Future Enhancements
1. Add support for Y16 format (320×256 radiometric)
2. Add MJPEG format support (if Boson configured for it)
3. Optimize frame rate (currently ~19 FPS, could target 30 FPS)
4. Add frame rate metrics to UI

---

## 📚 Lessons Learned

1. **Always use project's Gradle wrapper** - System Gradle may be incompatible
2. **Boson cameras often send headerless UVC data** - Don't assume UVC compliance
3. **Frame completion logic must handle ALL packet types** - Headerless, header, MJPEG
4. **ByteBuffer mark/reset is fragile** - Manual position save/restore is safer
5. **Glass EE2 Camera API has quirks** - Standard Android approaches may not work

---

## 🔗 Related Issues
- Original error: InvalidMarkException at NativeUVCCamera.java:503
- Split screen issue: Headerless packet handling
- Build failures: Gradle version mismatch

---

## ✍️ Author Notes
Session focused on making FLIR Boson 320 work reliably on Glass EE2. Major success with UVC streaming - completely stable. RGB fallback remains challenging and requires further investigation.
