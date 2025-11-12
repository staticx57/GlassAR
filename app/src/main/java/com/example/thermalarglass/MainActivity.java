package com.example.thermalarglass;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

// Using native USB implementation (no external library needed)

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * Main Activity for Glass Thermal AR Application
 * Captures Boson 320 thermal stream, sends to server, displays AR annotations
 */
public class MainActivity extends Activity implements SurfaceHolder.Callback {
    
    private static final String TAG = "ThermalARGlass";
    private static final String SERVER_URL = "http://192.168.1.100:8080"; // Change to your P16 IP
    
    // Boson 320 specs
    private static final int BOSON_WIDTH = 320;
    private static final int BOSON_HEIGHT = 256;
    private static final int TARGET_FPS = 60;
    
    // Glass display
    private static final int GLASS_WIDTH = 640;
    private static final int GLASS_HEIGHT = 360;
    
    // USB Camera - Native implementation
    private NativeUSBMonitor mUSBMonitor;
    private NativeUVCCamera mCamera;
    
    // Display
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;

    // UI Elements
    private TextView mConnectionStatus;
    private TextView mModeIndicator;
    private TextView mFrameCounter;
    private TextView mCenterTemperature;

    // Network
    private Socket mSocket;
    private boolean mConnected = false;

    // Display modes
    public static final String MODE_THERMAL_ONLY = "thermal_only";
    public static final String MODE_THERMAL_RGB_FUSION = "thermal_rgb_fusion";
    public static final String MODE_ADVANCED_INSPECTION = "advanced_inspection";

    // Current annotations from server
    private List<Detection> mDetections = new ArrayList<>();
    private ThermalAnalysis mThermalAnalysis = null;
    private String mCurrentMode = MODE_THERMAL_ONLY;

    // Glass EE2 built-in RGB camera
    private android.hardware.Camera mRgbCamera;
    private boolean mRgbCameraEnabled = false;
    private byte[] mLatestRgbFrame = null;

    // Frame counter
    private int mFrameCount = 0;

    // Battery monitoring
    private int mBatteryLevel = 100;
    private BroadcastReceiver mBatteryReceiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize display
        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);

        // Initialize UI elements
        mConnectionStatus = findViewById(R.id.connection_status);
        mModeIndicator = findViewById(R.id.mode_indicator);
        mFrameCounter = findViewById(R.id.frame_counter);
        mCenterTemperature = findViewById(R.id.center_temperature);

        // Initialize USB monitor for Boson (using native implementation)
        mUSBMonitor = new NativeUSBMonitor(this, mOnDeviceConnectListener);

        // Initialize network connection
        initializeSocket();

        // Initialize battery monitoring
        initializeBatteryMonitoring();

        Log.i(TAG, "Thermal AR Glass initialized");
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        mUSBMonitor.register();
        if (mSocket != null && !mSocket.connected()) {
            mSocket.connect();
        }
    }
    
    @Override
    protected void onStop() {
        // Stop thermal camera
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.close();
            mCamera = null;
        }

        // Stop RGB camera
        stopRgbCamera();

        mUSBMonitor.unregister();

        if (mSocket != null) {
            mSocket.disconnect();
        }

        // Unregister battery receiver
        if (mBatteryReceiver != null) {
            try {
                unregisterReceiver(mBatteryReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver not registered, ignore
            }
        }

        super.onStop();
    }

    private void initializeBatteryMonitoring() {
        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                if (level != -1 && scale != -1) {
                    mBatteryLevel = (int) ((level / (float) scale) * 100);

                    // Warn user if battery is low
                    if (mBatteryLevel < 20 && mBatteryLevel % 5 == 0) {
                        runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                "Low battery: " + mBatteryLevel + "%",
                                Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBatteryReceiver, filter);
    }
    
    private void initializeSocket() {
        try {
            mSocket = IO.socket(SERVER_URL);
            
            mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(() -> {
                        mConnected = true;
                        mConnectionStatus.setText("Connected");
                        mConnectionStatus.setTextColor(Color.GREEN);
                        Toast.makeText(MainActivity.this, "Connected to server", Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "Connected to processing server");
                    });
                }
            });

            mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(() -> {
                        mConnected = false;
                        mConnectionStatus.setText("Disconnected");
                        mConnectionStatus.setTextColor(Color.RED);
                        Toast.makeText(MainActivity.this, "Disconnected from server", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "Disconnected from server");
                    });
                }
            });
            
            mSocket.on("annotations", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    JSONObject data = (JSONObject) args[0];
                    handleAnnotations(data);
                }
            });
            
            mSocket.on("error", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    JSONObject error = (JSONObject) args[0];
                    Log.e(TAG, "Server error: " + error.toString());
                }
            });
            
            mSocket.connect();
            
        } catch (URISyntaxException e) {
            Log.e(TAG, "Socket initialization error", e);
        }
    }
    
    private final NativeUSBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new NativeUSBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Log.i(TAG, "USB device attached: " + device.getDeviceName() +
                  " VID:" + device.getVendorId() + " PID:" + device.getProductId());

            // Check if this is a UVC camera
            if (NativeUSBMonitor.isUVCCamera(device)) {
                Log.i(TAG, "UVC camera detected, requesting permission");
                mUSBMonitor.requestPermission(device);
            } else {
                Log.w(TAG, "Device is not a UVC camera");
            }
        }

        @Override
        public void onConnect(final UsbDevice device) {
            Log.i(TAG, "USB device connected, opening camera");

            runOnUiThread(() -> {
                if (mCamera != null) {
                    mCamera.close();
                }

                // Create and open native UVC camera
                mCamera = new NativeUVCCamera(MainActivity.this);

                if (mCamera.open(device)) {
                    try {
                        // Set Boson 320 resolution
                        mCamera.setPreviewSize(BOSON_WIDTH, BOSON_HEIGHT);
                        mCamera.setFrameCallback(mFrameCallback);

                        if (mCamera.startPreview()) {
                            Log.i(TAG, "Boson 320 camera started");
                            Toast.makeText(MainActivity.this, "Boson camera started", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "Failed to start preview");
                            Toast.makeText(MainActivity.this, "Failed to start camera", Toast.LENGTH_SHORT).show();
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error starting camera", e);
                        Toast.makeText(MainActivity.this, "Error starting camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "Failed to open camera");
                    Toast.makeText(MainActivity.this, "Failed to open camera", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onDetach(final UsbDevice device) {
            Log.i(TAG, "USB device detached");
            runOnUiThread(() -> {
                if (mCamera != null) {
                    mCamera.close();
                    mCamera = null;
                }
                Toast.makeText(MainActivity.this, "Camera disconnected", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onCancel(final UsbDevice device) {
            Log.i(TAG, "USB permission cancelled");
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "USB permission denied", Toast.LENGTH_SHORT).show();
            });
        }
    };
    
    private final NativeUVCCamera.IFrameCallback mFrameCallback = new NativeUVCCamera.IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {
            mFrameCount++;

            // Update frame counter UI
            runOnUiThread(() ->
                mFrameCounter.setText(String.valueOf(mFrameCount))
            );

            // Send frame to server if connected
            if (mConnected && mSocket != null) {
                // Convert frame to byte array
                byte[] frameData = new byte[frame.remaining()];
                frame.get(frameData);
                frame.rewind();  // Reset position for rendering

                // Encode to base64 for JSON transmission
                String frameBase64 = Base64.encodeToString(frameData, Base64.NO_WRAP);

                // Create JSON payload
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("frame", frameBase64);  // Now base64 encoded
                    payload.put("mode", mCurrentMode);
                    payload.put("frame_number", mFrameCount);
                    payload.put("timestamp", System.currentTimeMillis());

                    mSocket.emit("thermal_frame", payload);

                } catch (JSONException e) {
                    Log.e(TAG, "Error creating frame payload", e);
                }
            }

            // Render frame on display
            renderThermalFrame(frame);
        }
    };
    
    private void handleAnnotations(JSONObject data) {
        try {
            // Parse detections
            mDetections.clear();
            if (data.has("detections")) {
                JSONArray detectionsArray = data.getJSONArray("detections");
                for (int i = 0; i < detectionsArray.length(); i++) {
                    JSONObject det = detectionsArray.getJSONObject(i);
                    mDetections.add(Detection.fromJSON(det));
                }
            }
            
            // Parse thermal analysis
            if (data.has("thermal_anomalies")) {
                mThermalAnalysis = ThermalAnalysis.fromJSON(data.getJSONObject("thermal_anomalies"));
            } else if (data.has("component_temps")) {
                mThermalAnalysis = ThermalAnalysis.fromElectronicsJSON(data.getJSONArray("component_temps"));
            }
            
            // Update display will happen on next frame
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing annotations", e);
        }
    }
    
    private void renderThermalFrame(ByteBuffer frameData) {
        if (mSurfaceHolder == null) return;

        Canvas canvas = mSurfaceHolder.lockCanvas();
        if (canvas == null) return;

        try {
            // Clear canvas
            canvas.drawColor(Color.BLACK);

            // Convert thermal frame to bitmap and draw
            Bitmap thermalBitmap = convertThermalToBitmap(frameData);
            if (thermalBitmap != null) {
                // Scale to Glass display size
                Rect destRect = new Rect(0, 0, GLASS_WIDTH, GLASS_HEIGHT);
                canvas.drawBitmap(thermalBitmap, null, destRect, null);
            }

            // Draw annotations on top
            drawAnnotations(canvas);

        } finally {
            mSurfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private Bitmap convertThermalToBitmap(ByteBuffer frameData) {
        try {
            // Boson outputs 16-bit grayscale (actually YUYV, but we extract Y channel)
            // For simplicity, we'll convert to 8-bit grayscale and apply false color

            frameData.rewind();

            // Create bitmap (grayscale first)
            Bitmap bitmap = Bitmap.createBitmap(BOSON_WIDTH, BOSON_HEIGHT, Bitmap.Config.ARGB_8888);

            // Convert to pixel array
            int[] pixels = new int[BOSON_WIDTH * BOSON_HEIGHT];

            // Extract luminance values and apply thermal colormap
            for (int i = 0; i < pixels.length && frameData.remaining() >= 2; i++) {
                // Read 16-bit value (assuming YUV format - take Y channel)
                int y = frameData.get() & 0xFF;
                frameData.get();  // Skip U/V byte

                // Apply thermal colormap (iron/hot color scheme)
                pixels[i] = applyThermalColormap(y);
            }

            bitmap.setPixels(pixels, 0, BOSON_WIDTH, 0, 0, BOSON_WIDTH, BOSON_HEIGHT);
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error converting thermal frame", e);
            return null;
        }
    }

    private int applyThermalColormap(int value) {
        // Simple thermal colormap: Black -> Blue -> Purple -> Red -> Yellow -> White
        // Value range: 0-255

        int r, g, b;

        if (value < 64) {
            // Black to Blue
            r = 0;
            g = 0;
            b = value * 4;
        } else if (value < 128) {
            // Blue to Purple
            r = (value - 64) * 4;
            g = 0;
            b = 255;
        } else if (value < 192) {
            // Purple to Red
            r = 255;
            g = 0;
            b = 255 - ((value - 128) * 4);
        } else {
            // Red to Yellow to White
            r = 255;
            g = (value - 192) * 4;
            b = (value - 192) * 2;
        }

        // Clamp values
        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));

        return Color.argb(255, r, g, b);
    }
    
    private void drawAnnotations(Canvas canvas) {
        Paint boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3);
        
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24);
        textPaint.setAntiAlias(true);
        
        // Scale factor from Boson to Glass display
        float scaleX = (float) GLASS_WIDTH / BOSON_WIDTH;
        float scaleY = (float) GLASS_HEIGHT / BOSON_HEIGHT;
        
        // Draw object detections
        for (Detection det : mDetections) {
            // Scale bounding box
            Rect scaledBox = new Rect(
                (int) (det.bbox[0] * scaleX),
                (int) (det.bbox[1] * scaleY),
                (int) (det.bbox[2] * scaleX),
                (int) (det.bbox[3] * scaleY)
            );
            
            // Choose color based on confidence
            if (det.confidence > 0.8) {
                boxPaint.setColor(Color.GREEN);
            } else if (det.confidence > 0.5) {
                boxPaint.setColor(Color.YELLOW);
            } else {
                boxPaint.setColor(Color.GRAY);
            }
            
            canvas.drawRect(scaledBox, boxPaint);
            
            // Draw label
            String label = String.format("%s %.2f", det.className, det.confidence);
            canvas.drawText(label, scaledBox.left, scaledBox.top - 5, textPaint);
        }
        
        // Draw thermal anomalies
        if (mThermalAnalysis != null) {
            boxPaint.setStrokeWidth(4);
            
            // Hot spots in red
            boxPaint.setColor(Color.RED);
            for (ThermalAnomaly anomaly : mThermalAnalysis.hotSpots) {
                Rect scaledBox = new Rect(
                    (int) (anomaly.bbox[0] * scaleX),
                    (int) (anomaly.bbox[1] * scaleY),
                    (int) (anomaly.bbox[2] * scaleX),
                    (int) (anomaly.bbox[3] * scaleY)
                );
                canvas.drawRect(scaledBox, boxPaint);
                
                String tempLabel = String.format("%.1f°C", anomaly.temperature);
                canvas.drawText(tempLabel, scaledBox.left, scaledBox.bottom + 20, textPaint);
            }
            
            // Cold spots in blue
            boxPaint.setColor(Color.CYAN);
            for (ThermalAnomaly anomaly : mThermalAnalysis.coldSpots) {
                Rect scaledBox = new Rect(
                    (int) (anomaly.bbox[0] * scaleX),
                    (int) (anomaly.bbox[1] * scaleY),
                    (int) (anomaly.bbox[2] * scaleX),
                    (int) (anomaly.bbox[3] * scaleY)
                );
                canvas.drawRect(scaledBox, boxPaint);
                
                String tempLabel = String.format("%.1f°C", anomaly.temperature);
                canvas.drawText(tempLabel, scaledBox.left, scaledBox.bottom + 20, textPaint);
            }
        }
        
        // Draw status info
        textPaint.setColor(Color.GREEN);
        canvas.drawText(mConnected ? "Connected" : "Disconnected", 10, 30, textPaint);
        canvas.drawText(String.format("Mode: %s", mCurrentMode), 10, 60, textPaint);
        canvas.drawText(String.format("Frame: %d", mFrameCount), 10, 90, textPaint);
    }
    
    // Mode switching methods
    public void switchToThermalOnlyMode() {
        mCurrentMode = MODE_THERMAL_ONLY;

        // Disable RGB camera if enabled
        if (mRgbCameraEnabled) {
            stopRgbCamera();
        }

        // Update UI
        runOnUiThread(() -> {
            mModeIndicator.setText("Thermal Only");
            Toast.makeText(this, "Mode: Thermal Only", Toast.LENGTH_SHORT).show();
        });

        // Notify server
        notifyServerModeChange();
    }

    public void switchToThermalRgbFusionMode() {
        mCurrentMode = MODE_THERMAL_RGB_FUSION;

        // Enable RGB camera
        if (!mRgbCameraEnabled) {
            startRgbCamera();
        }

        // Update UI
        runOnUiThread(() -> {
            mModeIndicator.setText("Thermal+RGB Fusion");
            Toast.makeText(this, "Mode: Thermal+RGB Fusion", Toast.LENGTH_SHORT).show();
        });

        // Notify server
        notifyServerModeChange();
    }

    public void switchToAdvancedInspectionMode() {
        mCurrentMode = MODE_ADVANCED_INSPECTION;

        // Enable RGB camera for advanced mode
        if (!mRgbCameraEnabled) {
            startRgbCamera();
        }

        // Update UI
        runOnUiThread(() -> {
            mModeIndicator.setText("Advanced Inspection");
            Toast.makeText(this, "Mode: Advanced Inspection", Toast.LENGTH_SHORT).show();
        });

        // Notify server
        notifyServerModeChange();
    }

    private void notifyServerModeChange() {
        if (mSocket != null && mConnected) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("mode", mCurrentMode);
                mSocket.emit("set_mode", payload);
            } catch (JSONException e) {
                Log.e(TAG, "Error switching mode", e);
            }
        }
    }

    private void startRgbCamera() {
        try {
            // Open Glass EE2 built-in camera (usually camera 0)
            mRgbCamera = android.hardware.Camera.open(0);

            android.hardware.Camera.Parameters params = mRgbCamera.getParameters();
            // Set parameters for Glass EE2 camera (640x360 to match display)
            params.setPreviewSize(640, 360);
            mRgbCamera.setParameters(params);

            mRgbCamera.setPreviewCallback(new android.hardware.Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {
                    // Store latest RGB frame for fusion
                    mLatestRgbFrame = data;
                }
            });

            mRgbCamera.startPreview();
            mRgbCameraEnabled = true;

            Log.i(TAG, "RGB camera started");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start RGB camera", e);
            Toast.makeText(this, "Failed to start RGB camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRgbCamera() {
        if (mRgbCamera != null) {
            mRgbCamera.stopPreview();
            mRgbCamera.setPreviewCallback(null);
            mRgbCamera.release();
            mRgbCamera = null;
        }
        mRgbCameraEnabled = false;
        mLatestRgbFrame = null;
        Log.i(TAG, "RGB camera stopped");
    }
    
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "Surface created");
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, String.format("Surface changed: %dx%d", width, height));
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "Surface destroyed");
    }
    
    // Data classes
    static class Detection {
        float[] bbox;
        float confidence;
        String className;
        
        static Detection fromJSON(JSONObject json) throws JSONException {
            Detection det = new Detection();
            JSONArray bboxArray = json.getJSONArray("bbox");
            det.bbox = new float[4];
            for (int i = 0; i < 4; i++) {
                det.bbox[i] = (float) bboxArray.getDouble(i);
            }
            det.confidence = (float) json.getDouble("confidence");
            det.className = json.getString("class");
            return det;
        }
    }
    
    static class ThermalAnomaly {
        float[] bbox;
        float temperature;
        String type;
        
        static ThermalAnomaly fromJSON(JSONObject json) throws JSONException {
            ThermalAnomaly anomaly = new ThermalAnomaly();
            JSONArray bboxArray = json.getJSONArray("bbox");
            anomaly.bbox = new float[4];
            for (int i = 0; i < 4; i++) {
                anomaly.bbox[i] = (float) bboxArray.getDouble(i);
            }
            
            if (json.has("max_temp")) {
                anomaly.temperature = (float) json.getDouble("max_temp");
                anomaly.type = "hot";
            } else if (json.has("min_temp")) {
                anomaly.temperature = (float) json.getDouble("min_temp");
                anomaly.type = "cold";
            }
            
            return anomaly;
        }
    }
    
    static class ThermalAnalysis {
        List<ThermalAnomaly> hotSpots = new ArrayList<>();
        List<ThermalAnomaly> coldSpots = new ArrayList<>();
        float baselineTemp;
        
        static ThermalAnalysis fromJSON(JSONObject json) throws JSONException {
            ThermalAnalysis analysis = new ThermalAnalysis();
            
            if (json.has("hot_spots")) {
                JSONArray hotArray = json.getJSONArray("hot_spots");
                for (int i = 0; i < hotArray.length(); i++) {
                    analysis.hotSpots.add(ThermalAnomaly.fromJSON(hotArray.getJSONObject(i)));
                }
            }
            
            if (json.has("cold_spots")) {
                JSONArray coldArray = json.getJSONArray("cold_spots");
                for (int i = 0; i < coldArray.length(); i++) {
                    analysis.coldSpots.add(ThermalAnomaly.fromJSON(coldArray.getJSONObject(i)));
                }
            }
            
            if (json.has("baseline_temp")) {
                analysis.baselineTemp = (float) json.getDouble("baseline_temp");
            }
            
            return analysis;
        }
        
        static ThermalAnalysis fromElectronicsJSON(JSONArray components) throws JSONException {
            ThermalAnalysis analysis = new ThermalAnalysis();
            
            for (int i = 0; i < components.length(); i++) {
                JSONObject comp = components.getJSONObject(i);
                if (comp.getBoolean("is_hot")) {
                    ThermalAnomaly anomaly = new ThermalAnomaly();
                    JSONArray bboxArray = comp.getJSONArray("bbox");
                    anomaly.bbox = new float[4];
                    for (int j = 0; j < 4; j++) {
                        anomaly.bbox[j] = (float) bboxArray.getDouble(j);
                    }
                    anomaly.temperature = (float) comp.getDouble("max_temp");
                    anomaly.type = "hot_component";
                    analysis.hotSpots.add(anomaly);
                }
            }
            
            return analysis;
        }
    }
}
