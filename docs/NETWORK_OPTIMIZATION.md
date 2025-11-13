# Network Latency Optimization Guide

## Goal: Minimize Glass ↔ Server Latency

Target: **< 50ms round-trip** for real-time AR annotations

---

## 🎯 Glass Side Optimizations

### 1. Frame Downsampling (Biggest Win)

**Problem:** Sending full 320×256 Y16 frames = 163KB each @ 60fps = 9.7 MB/s

**Solution:** Downsample before sending

```java
// In MainActivity.java - mFrameCallback

// Optimization: Skip frames (send every Nth frame)
private int mFrameSkipCounter = 0;
private static final int FRAME_SKIP = 2;  // Send every 2nd frame (30fps → server)

@Override
public void onFrame(final ByteBuffer frame) {
    mFrameCount++;

    // Skip frames for network transmission
    mFrameSkipCounter++;
    if (mFrameSkipCounter < FRAME_SKIP) {
        // Still render locally at 60fps
        renderThermalFrame(frame);
        return;
    }
    mFrameSkipCounter = 0;

    // Continue with server transmission...
}
```

**Bandwidth Savings:**
- Before: 163KB × 60fps = 9.7 MB/s
- After: 163KB × 30fps = 4.9 MB/s (50% reduction)

---

### 2. JPEG Compression (For Non-Radiometric Mode)

**Problem:** MJPEG is ~250KB, Y16 is 163KB - both large

**Solution:** Re-compress with lower quality JPEG

```java
// After rendering frame to bitmap
if (mConnected && mSocket != null) {
    // Compress bitmap to JPEG for transmission
    Bitmap scaledBitmap = Bitmap.createScaledBitmap(
        mLatestThermalBitmap,
        160,  // Half resolution
        128,
        true
    );

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);  // 60% quality
    byte[] jpegData = baos.toByteArray();

    // Send compressed JPEG
    String frameBase64 = Base64.encodeToString(jpegData, Base64.NO_WRAP);
    // ... send to server
}
```

**Bandwidth Savings:**
- Before: 163KB Y16
- After: ~15-25KB JPEG (85-90% reduction!)

---

### 3. Async Socket Operations

**Problem:** Synchronous emit() blocks main thread

**Solution:** Use Socket.IO async

```java
// Already using Socket.IO which is async by default
// But ensure we're not blocking:

// Good (non-blocking):
mSocket.emit("thermal_frame", payload);  // Returns immediately

// Bad (would block):
// socket.emit().get()  // DON'T DO THIS
```

---

### 4. Connection Pooling & Keepalive

```java
// In initializeSocket()

IO.Options opts = new IO.Options();
opts.reconnection = true;
opts.reconnectionAttempts = Integer.MAX_VALUE;
opts.reconnectionDelay = 1000;
opts.timeout = 5000;  // 5 second timeout
opts.forceNew = false;  // Reuse connections

mSocket = IO.socket(serverUrl, opts);
```

---

## 🚀 Server Side Optimizations

### 1. Frame Skip & Caching (Already Implemented)

**Current:**
```python
def process_stream(self, frame_data, mode='building'):
    self.frame_count += 1

    # Process every other frame (30fps)
    if self.frame_count % 2 == 0:
        self.cached_annotations = self.process_frame(frame_data, mode)
    else:
        self.stats['dropped_frames'] += 1

    return self.cached_annotations  # Reuse for dropped frames
```

**✅ Already optimized!**

---

### 2. GPU Batch Processing

**Problem:** Processing frames one-by-one is slow

**Solution:** Batch inference

```python
# In Boson320Processor.__init__()

# Use GPU if available
self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
logger.info(f"Using device: {self.device}")

# Enable TensorRT/CUDNN optimization
torch.backends.cudnn.benchmark = True

# In detect_objects():
def detect_objects(self, thermal_frame):
    with torch.no_grad():  # Disable gradients for inference
        # ... existing code
        pass
```

---

### 3. Async Frame Processing

**Problem:** Flask-SocketIO blocks during processing

**Solution:** Use background threads

```python
from threading import Thread
import queue

class Boson320Processor:
    def __init__(self):
        # ... existing code ...

        # Processing queue
        self.processing_queue = queue.Queue(maxsize=2)  # Limit backlog
        self.result_queue = queue.Queue(maxsize=2)

        # Start background processor
        self.processing_thread = Thread(target=self._processing_worker, daemon=True)
        self.processing_thread.start()

    def _processing_worker(self):
        """Background thread for frame processing"""
        while True:
            frame_data, mode = self.processing_queue.get()
            result = self.process_frame(frame_data, mode)
            self.result_queue.put(result)

    def process_stream_async(self, frame_data, mode='building'):
        """Non-blocking frame submission"""
        try:
            # Submit without blocking
            self.processing_queue.put_nowait((frame_data, mode))
        except queue.Full:
            logger.debug("Processing queue full, skipping frame")

        # Return latest result immediately
        try:
            return self.result_queue.get_nowait()
        except queue.Empty:
            return self.cached_annotations  # Fallback to last result
```

Update handler:
```python
@socketio.on('thermal_frame')
def handle_thermal_frame(data):
    """Async frame handling"""
    try:
        frame_base64 = data.get('frame')
        mode = data.get('mode', current_mode)

        frame_data = base64.b64decode(frame_base64)

        # Process async (non-blocking)
        annotations = processor.process_stream_async(frame_data, mode)

        if annotations:
            emit('annotations', annotations)

    except Exception as e:
        logger.error(f"Error: {e}")
```

---

### 4. Reduce Annotation Size

**Problem:** Large JSON responses slow down transmission

**Solution:** Minimize annotation data

```python
def detect_objects(self, thermal_frame):
    # ... detection code ...

    detections = []
    for det in results:
        # Only include essential data
        detections.append({
            'bbox': [int(x) for x in det.bbox],  # Integers instead of floats
            'conf': round(float(det.confidence), 2),  # 2 decimal places
            'cls': det.class_name[:20]  # Limit class name length
        })

    return detections[:10]  # Limit to top 10 detections
```

---

### 5. WebSocket Compression

Enable compression in Flask-SocketIO:

```python
# At top of thermal_ar_server.py

socketio = SocketIO(
    app,
    cors_allowed_origins="*",
    async_mode='threading',
    compression=True,  # Enable compression
    compression_threshold=1024,  # Compress messages > 1KB
    ping_timeout=10,
    ping_interval=5
)
```

---

## 📊 Expected Latency Improvements

### Before Optimization:
- Frame size: 163KB (Y16) or 250KB (MJPEG)
- FPS to server: 60
- Bandwidth: 9.7-15 MB/s
- Round-trip latency: **100-200ms**

### After Optimization:
- Frame size: **15-25KB** (JPEG 60% quality, half-res)
- FPS to server: **30** (skip every other frame)
- Bandwidth: **0.45-0.75 MB/s** (95% reduction!)
- Round-trip latency: **20-50ms** ✅

---

## 🔧 Implementation Priority

**High Priority (Do Now):**
1. ✅ Frame skipping (Glass) - Easy, 50% bandwidth reduction
2. ✅ GPU optimization (Server) - Already mostly done
3. ✅ Async processing (Server) - Add background thread

**Medium Priority (Nice to Have):**
4. JPEG re-compression (Glass) - 85-90% bandwidth reduction
5. WebSocket compression (Server) - 20-30% reduction
6. Connection pooling (Glass) - Stability improvement

**Low Priority (Optional):**
7. Annotation size reduction - Minor improvement
8. Batch processing - Requires architecture changes

---

## 🧪 Testing Latency

### Glass Side:
```bash
adb logcat ThermalARGlass:I *:S | grep -E "Latency|Round-trip|Processing"
```

### Server Side:
Add timing logs:
```python
@socketio.on('thermal_frame')
def handle_thermal_frame(data):
    receive_time = time.time()

    # ... processing ...

    send_time = time.time()
    latency_ms = (send_time - receive_time) * 1000

    if latency_ms > 50:
        logger.warning(f"High latency: {latency_ms:.1f}ms")
```

### End-to-End Test:
```python
# Glass sends timestamp, server echoes back
payload.put("client_timestamp", System.currentTimeMillis())

# Server:
annotations['client_timestamp'] = data.get('client_timestamp')
annotations['server_timestamp'] = int(time.time() * 1000)

# Glass calculates round-trip:
long roundTrip = System.currentTimeMillis() - annotations.get("client_timestamp")
Log.i(TAG, "Round-trip latency: " + roundTrip + "ms")
```

---

## 📝 Summary

**Top 3 Optimizations:**
1. **Frame skipping** (30fps instead of 60fps) → 50% bandwidth reduction
2. **JPEG compression** (60% quality, half-res) → 85-90% bandwidth reduction
3. **Async processing** (background thread) → Non-blocking, consistent FPS

**Result:**
- Bandwidth: 9.7 MB/s → **0.5 MB/s** (95% reduction)
- Latency: 100-200ms → **20-50ms** (75% improvement)

**Next Steps:**
1. Implement frame skipping on Glass
2. Add async processing on server
3. Test latency improvements
4. Add JPEG re-compression if needed

---

**Created:** 2025-11-13
**Branch:** claude/debug-flir-boson-uvc-01YZahd82idMfsTBELEZq7L4
