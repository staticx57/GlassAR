# Google Glass Touchpad Controls Guide

Complete guide to native touchpad gestures for the Thermal AR Glass application.

---

## 🎮 Touchpad Gestures Overview

The Google Glass EE2 touchpad is located on the right temple and supports multiple gesture types. The Thermal AR Glass application utilizes all native Glass gestures for hands-free operation during inspections.

---

## ✋ Gesture Reference

### 1. **Single Tap** 👆
**Action:** Toggle Overlay On/Off

**How to perform:**
- Lightly tap once on the touchpad

**What it does:**
- Toggles the center reticle (crosshair) visibility
- Toggles the center temperature display
- Shows "Overlay ON" or "Overlay OFF" message
- Provides haptic feedback

**Use case:**
- Clear view without annotations for raw thermal viewing
- Re-enable overlay when needed

**Feedback:**
- Visual: Toast message
- Haptic: Short vibration
- UI: Crosshair and temp display appear/disappear

---

### 2. **Double Tap** 👆👆
**Action:** Capture Snapshot

**How to perform:**
- Quickly tap twice in succession

**What it does:**
- Captures current thermal frame with all annotations
- Saves image with metadata (timestamp, temperature data)
- Plays camera shutter sound
- Shows processing indicator briefly

**Use case:**
- Document findings during inspection
- Capture thermal anomalies with AI annotations
- Build evidence for reports

**Feedback:**
- Visual: Processing spinner, "Snapshot captured" message
- Audio: Camera shutter sound
- Haptic: Short vibration

---

### 3. **Long Press** 👆 (hold)
**Action:** Start/Stop Video Recording

**How to perform:**
- Press and hold on touchpad for 1-2 seconds

**What it does:**
- Toggles video recording mode
- Records thermal stream with annotations
- Shows red recording indicator in corner
- Captures synchronized audio

**Use case:**
- Record entire inspection walkthrough
- Narrate findings while recording
- Create video reports

**Feedback:**
- Visual: Red recording dot appears/disappears
- Toast: "Recording started" / "Recording stopped"
- Haptic: Short vibration

---

### 4. **Swipe Forward** →
**Action:** Cycle Display Modes

**How to perform:**
- Swipe from back of touchpad toward front (left to right)
- Must be horizontal swipe with sufficient velocity

**What it does:**
- Cycles through three display modes in sequence:
  1. **Thermal Only** → Shows only thermal camera feed
  2. **Thermal + RGB Fusion** → Overlays thermal on Glass camera
  3. **Advanced Inspection** → Full AI analysis with object detection
  4. (loops back to Thermal Only)

**Use case:**
- Switch inspection modes based on task
- Enable/disable AI features to save battery
- Adjust for different inspection scenarios

**Feedback:**
- Visual: Mode indicator updates in status bar
- Toast: Mode name displayed
- Haptic: Short vibration
- Server: Mode change notification sent

---

### 5. **Swipe Backward** ←
**Action:** Navigate Through Detections

**How to perform:**
- Swipe from front of touchpad toward back (right to left)
- Must be horizontal swipe with sufficient velocity

**What it does:**
- Cycles backward through detected objects/anomalies
- Highlights selected detection with:
  - Thicker cyan border (6px)
  - Larger text label
  - ">>>" markers around label
- Shows detection info: "Detection 2/5: Outlet (85%)"

**Use case:**
- Review each detected object individually
- Focus on specific anomalies
- Navigate findings methodically

**Feedback:**
- Visual: Selected detection highlighted in cyan
- Toast: Detection name and confidence
- Haptic: Short vibration

**Note:** If no detections present, shows "No detections to navigate"

---

### 6. **Swipe Down** ↓
**Action:** Dismiss Alerts / Reset View

**How to perform:**
- Swipe from top of touchpad downward
- Must be vertical swipe with sufficient velocity

**What it does:**
- **If alert is visible:** Dismisses the alert banner
- **Otherwise:** Resets view to default state
  - Resets detection index to 0
  - Shows crosshair and temperature
  - Clears any temporary UI states

**Use case:**
- Dismiss temperature alarms
- Clear notification banners
- Return to main thermal view
- Reset after reviewing detections

**Feedback:**
- Visual: Alert disappears or "Reset to main view" message
- Haptic: Short vibration

---

### 7. **Camera Button** 📷
**Action:** Quick Snapshot (Hardware Button)

**How to perform:**
- Press the physical camera button on top of Glass

**What it does:**
- Same as double-tap: captures snapshot
- Faster than touchpad for quick captures
- Recommended for rapid documentation

**Use case:**
- Quick capture during walk-through
- Familiar camera operation
- When touchpad is less accessible

**Feedback:**
- Same as double-tap gesture

---

## 🎯 Gesture Quick Reference Table

| Gesture | Action | Primary Use | Visual Feedback |
|---------|--------|-------------|-----------------|
| Single Tap | Toggle overlay | Show/hide UI | Overlay visibility |
| Double Tap | Capture snapshot | Document findings | Spinner + sound |
| Long Press | Start/stop recording | Video documentation | Red dot indicator |
| Swipe Forward → | Next mode | Change inspection type | Mode name |
| Swipe Backward ← | Previous detection | Review findings | Cyan highlight |
| Swipe Down ↓ | Dismiss/reset | Clear alerts | Alert removal |
| Camera Button | Quick snapshot | Fast capture | Spinner + sound |

---

## 💡 Gesture Best Practices

### Touchpad Technique
- **Clean, deliberate gestures** - Avoid hesitant movements
- **Full swipes** - Start from edge of touchpad
- **Consistent speed** - Not too fast or too slow
- **Practice gestures** - Learn the timing before field use

### Common Mistakes
❌ **Too gentle:** Swipe may not register
❌ **Too diagonal:** Swipe interpreted as wrong direction
❌ **Too slow:** Swipe seen as pan rather than gesture
❌ **Multiple fingers:** Use single finger only

✅ **Correct technique:**
- Firm, confident contact
- Straight horizontal or vertical motion
- Quick, smooth movement
- Single finger contact

---

## 🔄 Gesture Workflow Examples

### Building Inspection Workflow

1. **Start inspection** - Swipe Forward → to "Advanced Inspection" mode
2. **Identify anomaly** - AI detects hot spot automatically
3. **Review detections** - Swipe Backward ← through each finding
4. **Capture evidence** - Double Tap to save snapshot
5. **Document verbally** - Long Press to start recording, narrate issue
6. **Stop recording** - Long Press again to stop
7. **Dismiss alert** (if temp alarm) - Swipe Down ↓
8. **Next room** - Continue inspection

### Electronics Inspection Workflow

1. **Position over PCB** - Frame the board
2. **Switch to Electronics mode** - Swipe Forward → (if needed)
3. **Wait for analysis** - AI detects hot components
4. **Navigate components** - Swipe Backward ← to cycle through hot parts
5. **Zoom on issue** - Focus on highlighted component
6. **Capture closeup** - Camera Button for quick snap
7. **Clear view** - Single Tap to hide overlay momentarily
8. **Re-enable overlay** - Single Tap again

### Quick Documentation Workflow

1. **Encounter issue** - Aim Glass at problem area
2. **Quick capture** - Camera Button (fastest method)
3. **Verify capture** - Check processing indicator
4. **Continue inspection** - Move to next area
5. **Repeat** - Capture multiple angles

---

## 🛠️ Troubleshooting Gestures

### Gesture Not Recognized

**Problem:** Swipe doesn't trigger action

**Solutions:**
1. Check swipe length - must be at least 50 pixels
2. Increase swipe speed - minimum velocity threshold required
3. Keep swipe straight - max 250 pixels off-axis deviation
4. Ensure touchpad is clean and dry
5. Check touchpad sensitivity in Glass settings

### Wrong Gesture Detected

**Problem:** Swipe down interpreted as swipe forward

**Solutions:**
1. Start swipe from correct edge
2. Maintain consistent direction throughout
3. Avoid curved or diagonal swipes
4. Practice gesture on Glass home screen first

### Double Tap Registers as Single Tap

**Problem:** Snapshots not capturing

**Solutions:**
1. Tap faster - reduce time between taps
2. Tap in same location - don't move finger between taps
3. Use lighter pressure - don't press too hard
4. Try camera button instead for reliable capture

### Haptic Feedback Not Working

**Problem:** No vibration confirmation

**Solutions:**
1. Check Glass settings - ensure haptic feedback enabled
2. Battery may be low - haptic disabled to save power
3. App may be in background - bring to foreground
4. Restart app if issue persists

---

## 🔧 Technical Implementation Details

### Gesture Detection Parameters

```java
// Swipe gesture thresholds
private static final int SWIPE_MIN_DISTANCE = 50;      // Minimum pixels
private static final int SWIPE_MAX_OFF_PATH = 250;     // Maximum deviation
private static final int SWIPE_THRESHOLD_VELOCITY = 100; // Minimum velocity
```

### Gesture Processing Flow

```
User Touch → onGenericMotionEvent() → GestureDetector
    ↓
Gesture Recognition (onFling, onSingleTap, etc.)
    ↓
Gesture Handler (onSwipeForward, onTap, etc.)
    ↓
Action Execution + Haptic Feedback + Visual Update
```

### Haptic Feedback

All gestures trigger Glass's haptic feedback system:
```java
mSurfaceView.performHapticFeedback(
    HapticFeedbackConstants.VIRTUAL_KEY,
    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
);
```

### Sound Effects

Snapshot captures include camera shutter sound:
```java
MediaActionSound sound = new MediaActionSound();
sound.play(MediaActionSound.SHUTTER_CLICK);
```

---

## 📱 Alternative Input Methods

While touchpad gestures are primary input, the app also supports:

### Voice Commands (Future Enhancement)
- "Ok Glass, capture" → Take snapshot
- "Ok Glass, start recording" → Begin video
- "Ok Glass, next mode" → Cycle display mode
- "Ok Glass, show stats" → Display info

### Server-Triggered Actions
- Remote snapshot capture from dashboard
- Mode changes from supervisor
- Alert acknowledgment from mobile app

---

## 🎓 Training Tips

### For New Users

1. **Practice in safe environment** - Learn gestures before field use
2. **Start with single gesture** - Master one at a time
3. **Use visual feedback** - Watch for confirmation messages
4. **Feel the haptics** - Learn to recognize vibration patterns
5. **Build muscle memory** - Repeat gestures until automatic

### Gesture Learning Order

**Beginner:**
1. Single Tap (toggle overlay)
2. Camera Button (capture)
3. Swipe Down (dismiss)

**Intermediate:**
4. Swipe Forward (modes)
5. Double Tap (capture)

**Advanced:**
6. Swipe Backward (navigate)
7. Long Press (recording)

---

## 📊 Gesture Performance Metrics

### Expected Response Times

| Gesture | Recognition Time | Action Complete |
|---------|-----------------|-----------------|
| Single Tap | <50ms | <100ms |
| Double Tap | <200ms | <500ms (capture) |
| Long Press | 1000-1500ms | Immediate |
| Swipe | <100ms | <200ms |

### Battery Impact

- **Gesture detection:** Minimal (~1% per hour)
- **Haptic feedback:** Low (~2% per hour with frequent use)
- **Video recording:** High (~20% per hour)

---

## 🔒 Accessibility Features

### For Users with Limited Dexterity

- **Longer tap windows** - Adjust timeout in settings
- **Reduced swipe sensitivity** - Lower velocity threshold
- **Hardware button preferred** - Use camera button over touchpad
- **Voice commands** - Alternative to touchpad (future)

### For Users with Visual Impairments

- **Audio feedback** - Spoken confirmations (future)
- **Stronger haptics** - Enhanced vibration patterns
- **High contrast mode** - Easier to see UI changes

---

## 🆘 Support

If gestures are not working properly:

1. **Check logs:**
   ```bash
   adb logcat -s ThermalARGlass | grep "Touchpad"
   ```

2. **Verify touchpad function:**
   - Test on Glass home screen
   - Check in Glass settings app
   - Restart Glass if unresponsive

3. **Report issues:**
   - Note which gesture fails
   - Describe expected vs actual behavior
   - Include logcat output
   - Mention Glass EE2 software version

---

## 📚 Additional Resources

- **Glass EE2 User Guide:** https://support.google.com/glass-enterprise/
- **Android GestureDetector Docs:** https://developer.android.com/reference/android/view/GestureDetector
- **Glass Input Guidelines:** https://developers.google.com/glass-enterprise/guides/inputs

---

**Last Updated:** 2025-11-12
**Compatible with:** Google Glass Enterprise Edition 2
**App Version:** 1.0+
**Status:** Production Ready ✅
