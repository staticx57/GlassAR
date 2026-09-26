# TODO List - Glass AR Thermal Camera Project

## 🎯 Current Session Status

### ✅ WORKING - Thermal Camera UVC Streaming
**Status:** FULLY FUNCTIONAL ✅✅✅
- [x] InvalidMarkException fixed
- [x] Headerless packet handling implemented
- [x] Frame completion logic works correctly
- [x] I420 format (640×512) detected and rendered
- [x] 1,038 frames streamed with 0 errors
- [x] Stable ~19 FPS performance
- [x] Snapshot capture working (165KB PNG files)
- [x] Clean disconnect handling

**Evidence:**
```
✓ I420 frame (640×512, no telemetry)
✓ Frame #5 delivered: 491520 bytes (I420, 640×512)
Streaming status: 1038 frames received, 0 errors
✓ Snapshot saved successfully: thermal_20251114_010530.png (165159 bytes)
```

---

## ⚠️ AWAITING ON-DEVICE VERIFICATION - RGB Camera Fallback

### Issue: startPreview() RuntimeException
**Status:** ROOT CAUSE IDENTIFIED, FIX APPLIED - NOT YET TESTED ON HARDWARE ⚠️
**Priority:** HIGH

**What We Know:**
- Camera opens successfully (camera 0)
- Finds 28 supported preview sizes
- Selects correct size: 640×360
- Surface reports as valid
- Parameters set successfully (NV21 format, focus mode)
- BUT: `startPreview()` throws RuntimeException

**What We've Tried (all targeted the symptom, not the cause):**
- [x] Query supported sizes and pick closest match ❌ Didn't help
- [x] Validate surface before use ❌ Surface is valid but still fails
- [x] Set explicit preview format (NV21) ❌ Didn't help
- [x] Configure focus mode (CONTINUOUS_VIDEO/AUTO) ❌ Didn't help
- [x] Add 100ms stabilization delay ❌ Didn't help
- [x] Enhanced error handling ❌ Still fails
- [x] Wrap all operations in try-catch ❌ Catches but doesn't fix

**Root cause found (2026-09-24):** `startRgbCameraFallback()` called
`mRgbCamera.setPreviewDisplay(mSurfaceHolder)` using the *same* `SurfaceHolder`
that `renderThermalFrame()` drives via `Canvas` software rendering
(`lockCanvas()`/`unlockCanvasAndPost()`). A `Surface` that has had a Canvas
producer attached cannot reliably be handed to `android.hardware.Camera` as a
preview target on Glass EE2's camera HAL — the legacy Camera API's native
`startPreview()` fails to take over the surface, throwing `RuntimeException`.
None of the seven prior fixes addressed this because they all assumed the
surface itself was fine and tuned camera parameters/timing instead.

**Fix applied (untested on hardware):**
- Added a second, dedicated `SurfaceView` (`rgb_surface_view` in
  `activity_main.xml`) that is never touched by Canvas rendering — only by
  `Camera.setPreviewDisplay()`.
- `startRgbCameraFallback()` now targets `mRgbSurfaceHolder` instead of
  `mSurfaceHolder`, and toggles `View` visibility between the thermal surface
  and the RGB surface.
- `stopRgbCamera()` reverts visibility back to the thermal surface whenever
  RGB fallback stops or fails at any point.
- Compiles cleanly (`gradlew compileDebugJavaWithJavac` passes) but **has not
  been run on an actual Glass EE2 device with the RGB camera** — needs
  real-hardware confirmation that `startPreview()` no longer throws, and that
  toggling between the two SurfaceViews doesn't introduce visible flicker or
  timing issues on reconnect.

**Error Stack Trace:**
```
E ThermalARGlass: startPreview() failed
E ThermalARGlass: java.lang.RuntimeException: startPreview failed
E ThermalARGlass:     at android.hardware.Camera.startPreview(Native Method)
E ThermalARGlass:     at com.example.thermalarglass.MainActivity.startRgbCameraFallback(MainActivity.java:2946)
```

**Next Steps to Try:**

1. **Test Camera in Isolation**
   - [ ] Create minimal test app with ONLY RGB camera
   - [ ] See if Glass EE2 camera works without thermal camera first
   - [ ] Determine if it's a camera issue or state issue

2. **Try Camera2 API**
   - [ ] Replace deprecated Camera API with Camera2
   - [ ] Camera2 is more robust and better supported on modern Android
   - [ ] May handle Glass EE2 quirks better

3. **Surface Recreation**
   - [ ] Try destroying and recreating surface after thermal disconnect
   - [ ] Current surface may be in incompatible state
   - [ ] Use SurfaceView lifecycle callbacks differently

4. **Minimal Parameters**
   - [ ] Try startPreview() with ZERO parameters set
   - [ ] Let camera use all defaults
   - [ ] Add parameters one at a time to find culprit

5. **Different Preview Size**
   - [ ] Try native camera size (not 640×360)
   - [ ] Check what Glass EE2's preferred size is
   - [ ] May need exact match, not "closest"

6. **Thread/Timing Issues**
   - [ ] Try starting camera on different thread
   - [ ] May need to be on main thread or camera thread specifically
   - [ ] Current timing may be too fast/slow

7. **Permission/Resource Issues**
   - [ ] Check if thermal camera is releasing camera resource properly
   - [ ] May need explicit camera.release() + delay before RGB
   - [ ] Check if another process is holding camera

---

## 🔧 BUILD SYSTEM

### ✅ WORKING - Gradle 7.6 Wrapper
**Status:** FULLY FUNCTIONAL ✅
- [x] Build completes successfully
- [x] Uses AGP 7.4.2
- [x] Compatible with API 27
- [x] Works on Glass Enterprise Edition 2

**Command:**
```bash
./gradlew clean installDebug   # Linux/Mac
gradlew.bat clean installDebug  # Windows
```

**DO NOT USE:**
```bash
gradle clean installDebug  # System Gradle 9.1 - INCOMPATIBLE
```

### ❌ ATTEMPTED - Gradle 9.x / AGP 8.x Upgrade
**Status:** ABANDONED (Intentionally Reverted) ❌
- [x] Attempted AGP 8.2.0 upgrade
- [x] Tried modern dependencyResolutionManagement
- [x] Hit "Cannot mutate dependencies" error
- [x] Reverted to working Gradle 7.6 + AGP 7.4.2 configuration

**Lesson Learned:**
Use project's Gradle wrapper, not system Gradle. Upgrading AGP without full testing causes more issues than it solves.

---

## 📊 Known Issues & Limitations

### Display Issues
- [ ] **NEED TO VERIFY:** Is I420 rendering correctly after headerless fix?
  - User initially reported "split screen"
  - Fix was applied for headerless packet handling
  - Need confirmation that display is now correct
  - May need to test with different thermal scenes

### Format Support
- [x] I420 (640×512) - ✅ WORKING
- [ ] Y16 (320×256 radiometric) - ⚠️ UNTESTED
- [ ] MJPEG (variable size) - ⚠️ DETECTION CODE PRESENT BUT UNTESTED

### Performance
- Current: ~19 FPS (94 frames per 5 seconds)
- Theoretical: 60 FPS possible
- [ ] Investigate if FPS can be improved
- [ ] Check USB transfer bottlenecks
- [ ] Profile frame processing time

### Render pipeline optimization (2026-09-26) - AWAITING ON-DEVICE VERIFICATION ⚠️
**Status:** IMPLEMENTED, NOT YET MEASURED ON HARDWARE

`convertY16ToBitmap()` / `convertI420ToBitmap()` were allocating a brand-new
`Bitmap` (up to 1.3MB) plus a brand-new `int[]` pixel buffer (up to 1.3MB)
*every single frame*, at up to 60fps - a likely contributor to the ~19fps
ceiling via GC pressure on Glass EE2's Snapdragon 710. Changed to reuse one
persistent Bitmap/pixel-array/byte-array set per format across frames.

Because the render bitmap is now mutable and reused, `captureSnapshot()`
(which reads it from a background thread) now takes an explicit defensive
`.copy()` synchronously before handing off to that thread, to avoid reading a
frame the render loop is mid-overwrite on. The recording path
(`createSnapshotBitmap()` called directly from `renderThermalFrame()`) needed
no such change - it already runs synchronously on the render thread.

**Needs on-device confirmation:** actual FPS improvement, no visual
corruption/tearing, snapshots and recorded frames still look correct.

### Thermal+RGB fusion mode (2026-09-26) - AWAITING ON-DEVICE VERIFICATION ⚠️
**Status:** IMPLEMENTED, NOT YET TESTED ON HARDWARE

Fusion mode previously opened the RGB camera and stored frames in
`mLatestRgbFrame`, but nothing ever read that field - it was pure background
camera/CPU/battery cost with zero visual effect, and `startRgbCamera()` never
called `setPreviewDisplay()`/`setPreviewTexture()` before `startPreview()`,
which likely threw on API 27 (same class of bug as the RGB fallback issue
above) meaning fusion mode may never have actually started successfully.

Implemented: the RGB camera now targets an invisible `SurfaceTexture` (never
touching either on-screen `SurfaceView`), uses `setPreviewCallbackWithBuffer`
+ `addCallbackBuffer` so Android reuses two fixed byte buffers instead of
allocating a new one per frame, and a dedicated background `HandlerThread`
computes a Sobel-style edge/outline overlay from the NV21 Y-plane at half
resolution, throttled to recompute at most every 120ms (fusion mode is
allowed to run slower than thermal-only, by design). `renderThermalFrame()`
draws the latest published overlay on top of the thermal image whenever
`mCurrentMode == MODE_THERMAL_RGB_FUSION`.

**Needs on-device confirmation:**
- [ ] RGB camera actually opens and starts preview in fusion mode
- [ ] Edge overlay is visually useful (color/opacity/threshold may need tuning)
- [ ] No performance regression to thermal-only mode (overlay work is meant to be fully decoupled/background)
- [ ] Battery impact of running a second camera concurrently

---

## 🎯 Future Enhancements

### High Priority
1. [ ] **Verify RGB camera fallback fix on hardware** - Root cause fixed (dual-surface conflict), needs on-device confirmation
2. [ ] **Verify render pipeline optimization on hardware** - Bitmap reuse implemented, needs FPS/correctness confirmation
3. [ ] **Verify Thermal+RGB fusion mode on hardware** - Edge overlay implemented, needs confirmation it actually works and looks useful
4. [ ] **Verify display is correct** - Confirm split screen is fixed
5. [ ] **Test Y16 format** - Radiometric data more useful than I420
6. [ ] **Test MJPEG format** - Should work with current code

### Medium Priority
5. [ ] **Add frame rate control** - Allow user to select FPS
6. [ ] **Add format selector** - Allow switching between Y16/I420/MJPEG
7. [ ] **Optimize rendering** - Reduce latency, improve FPS
8. [ ] **Add temperature overlay** - Show temps on I420 frames

### Low Priority
9. [ ] **Add recording mode** - Save thermal video
10. [ ] **Add telemetry parsing** - Extract camera metadata
11. [ ] **Add calibration** - Improve temperature accuracy
12. [ ] **Add zoom/pan** - Navigate thermal image

---

## 🐛 Bugs to Investigate

### Critical
- [ ] RGB camera fallback - fix applied (separate SurfaceView), needs on-device retest

### High
- [ ] Verify I420 display is correct after headerless fix
- [ ] Test Y16 format (may also need headerless handling)

### Medium
- [ ] Check if MJPEG format actually works
- [ ] Verify snapshot includes latest frame (not stale)
- [ ] Check USB disconnect/reconnect reliability

### Low
- [ ] Frame counter might not reset on reconnect
- [ ] Server connection timing with camera connect
- [ ] Alert dismissal timing

---

## 📝 Documentation Needs

### Code Documentation
- [ ] Add JavaDoc comments to NativeUVCCamera
- [ ] Document headerless packet handling approach
- [ ] Add comments explaining frame completion logic
- [ ] Document I420 vs Y16 vs MJPEG differences

### User Documentation
- [ ] Create user guide for thermal camera setup
- [ ] Document server connection process
- [ ] Add troubleshooting guide
- [ ] Create calibration instructions

### Developer Documentation
- [ ] Document build process
- [ ] Add UVC streaming architecture diagram
- [ ] Document frame flow from camera to display
- [ ] Add debugging guide

---

## 🔬 Testing Needed

### Functional Testing
- [x] UVC streaming stability - ✅ PASSED (1038 frames, 0 errors)
- [x] Frame delivery - ✅ PASSED (correct size, format detected)
- [x] Snapshot capture - ✅ PASSED (PNG saved successfully)
- [ ] RGB camera fallback - ⚠️ FIX APPLIED, UNTESTED ON HARDWARE
- [ ] Camera reconnect - ⚠️ NEEDS TESTING
- [ ] Y16 format - ⚠️ UNTESTED
- [ ] MJPEG format - ⚠️ UNTESTED

### Performance Testing
- [ ] Frame rate under load
- [ ] Memory usage during streaming
- [ ] CPU usage during streaming
- [ ] Battery drain with thermal camera

### Edge Cases
- [ ] Rapid connect/disconnect
- [ ] Multiple cameras (if supported)
- [ ] Camera disconnect during snapshot
- [ ] Camera disconnect during recording
- [ ] Surface destroy during streaming

---

## 🚀 Deployment Checklist

### Before Release
- [x] Code compiles without errors
- [x] UVC streaming works reliably
- [ ] RGB fallback works (BLOCKING - fix applied, awaiting on-device confirmation)
- [ ] All formats tested (Y16, I420, MJPEG)
- [ ] Documentation complete
- [ ] User guide written
- [ ] Known issues documented

### Testing Requirements
- [ ] Test on actual Glass EE2 device
- [ ] Test with actual Boson 320 camera
- [ ] Test connect/disconnect cycles
- [ ] Test all display modes
- [ ] Test server integration
- [ ] Test snapshot and recording

---

## 💡 Ideas for Investigation

### RGB Camera Fallback Alternatives
1. **Different Camera API**
   - Try Camera2 API instead of deprecated Camera API
   - May have better Glass EE2 support

2. **Texture View Instead of Surface View**
   - TextureView more flexible than SurfaceView
   - May handle state transitions better

3. **Picture-in-Picture Mode**
   - Show small RGB preview in corner
   - Less critical if it fails
   - Could use different view entirely

4. **Static Image Fallback**
   - Instead of live camera, show static "No Camera" image
   - At least provides graceful degradation
   - User knows thermal camera is disconnected

### Performance Improvements
1. **USB Transfer Optimization**
   - Increase buffer sizes
   - Use isochronous transfers if available
   - Batch multiple packets

2. **Rendering Optimization**
   - Use hardware acceleration
   - Reduce colormap complexity
   - Cache bitmap allocations

3. **Thread Optimization**
   - Use separate thread for USB reading
   - Use separate thread for rendering
   - Minimize thread synchronization

---

## 📅 Timeline Estimates

### Immediate (Next Session)
- [ ] Test RGB camera fallback fix on real Glass EE2 hardware (confirm startPreview no longer throws)
- [ ] Test Y16 format with Boson (1 hour)

### Short Term (This Week)
- [ ] Fix RGB camera fallback (4-8 hours)
- [ ] Verify display correctness (1 hour)
- [ ] Test all formats (2 hours)
- [ ] Add basic documentation (2 hours)

### Medium Term (This Month)
- [ ] Add frame rate control (4 hours)
- [ ] Add format selector (4 hours)
- [ ] Performance optimization (8 hours)
- [ ] Complete documentation (4 hours)

---

## 🎓 Lessons Learned

### What Worked
1. ✅ **Manual position save/restore** - More reliable than mark/reset
2. ✅ **Unified packet handling** - Single code path for header/headerless
3. ✅ **Size-based frame detection** - Works well for fixed-size formats
4. ✅ **Diagnostic logging** - Critical for debugging UVC issues
5. ✅ **Using Gradle wrapper** - Avoids version conflicts

### What Didn't Work
1. ❌ **Assuming UVC compliance** - Boson sends headerless packets
2. ❌ **mark/reset pattern** - Fragile with position changes
3. ❌ **Upgrading to AGP 8.x** - Caused more problems than solved
4. ❌ **Standard RGB camera setup** - Glass EE2 has quirks
5. ❌ **Simple parameter changes** - RGB issue is deeper

### Key Insights
1. **FLIR Boson cameras are quirky** - Don't follow UVC spec strictly
2. **Glass EE2 has unique requirements** - Can't treat like standard Android
3. **Gradle wrapper is essential** - System Gradle causes conflicts
4. **Logging is critical** - Can't debug UVC without detailed logs
5. **Size-based detection works** - When headers unreliable, use frame size

---

## 🆘 Help Needed

### Questions for Community/Experts
1. **Glass EE2 Camera API**: Anyone successfully used Camera API on Glass EE2?
2. **FLIR Boson UVC**: Is headerless mode normal? Any documentation?
3. **Surface State**: How to properly reset surface after camera switch?
4. **Camera2 on Glass**: Does Camera2 API work better on Glass than Camera API?

### Resources Needed
- [ ] Glass EE2 camera API documentation
- [ ] FLIR Boson UVC implementation guide
- [ ] Android Camera2 API examples for Glass
- [ ] UVC streaming best practices

---

## 📋 Session Summary

### Major Wins 🎉
- UVC streaming fully functional and stable
- 1,038 frames with 0 errors proves reliability
- Headerless packet handling working perfectly
- Build system stable with Gradle 7.6

### Remaining Challenges 🚧
- RGB camera fallback - root cause found and fix applied (2026-09-24), not yet verified on hardware
- Need to verify display is rendering correctly
- Untested formats (Y16, MJPEG)

### Next Priority 🎯
**Test RGB camera fallback fix on real Glass EE2 hardware** - Confirm `startPreview()` no longer throws now that it targets a dedicated SurfaceView instead of the one driven by Canvas thermal rendering.

---

*Last Updated: November 14, 2025*
*Session: UVC Streaming Fix*
*Status: UVC Streaming ✅ | RGB Fallback ❌ | Build System ✅*
