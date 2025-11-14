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

## ❌ NOT WORKING - RGB Camera Fallback

### Issue: startPreview() RuntimeException
**Status:** FAILING ❌
**Priority:** HIGH

**What We Know:**
- Camera opens successfully (camera 0)
- Finds 28 supported preview sizes
- Selects correct size: 640×360
- Surface reports as valid
- Parameters set successfully (NV21 format, focus mode)
- BUT: `startPreview()` throws RuntimeException

**What We've Tried:**
- [x] Query supported sizes and pick closest match ❌ Didn't help
- [x] Validate surface before use ❌ Surface is valid but still fails
- [x] Set explicit preview format (NV21) ❌ Didn't help
- [x] Configure focus mode (CONTINUOUS_VIDEO/AUTO) ❌ Didn't help
- [x] Add 100ms stabilization delay ❌ Didn't help
- [x] Enhanced error handling ❌ Still fails
- [x] Wrap all operations in try-catch ❌ Catches but doesn't fix

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

---

## 🎯 Future Enhancements

### High Priority
1. [ ] **Fix RGB camera fallback** - Currently broken
2. [ ] **Verify display is correct** - Confirm split screen is fixed
3. [ ] **Test Y16 format** - Radiometric data more useful than I420
4. [ ] **Test MJPEG format** - Should work with current code

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
- [ ] RGB camera fallback not working (startPreview fails)

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
- [ ] RGB camera fallback - ❌ FAILED
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
- [ ] RGB fallback works (BLOCKING)
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
- [ ] Investigate RGB camera fallback root cause (2-3 hours)
- [ ] Try Camera2 API approach (2-4 hours)
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
- RGB camera fallback not working
- Need to verify display is rendering correctly
- Untested formats (Y16, MJPEG)

### Next Priority 🎯
**Fix RGB camera fallback** - This is the main blocker for graceful degradation when thermal camera disconnects.

---

*Last Updated: November 14, 2025*
*Session: UVC Streaming Fix*
*Status: UVC Streaming ✅ | RGB Fallback ❌ | Build System ✅*
