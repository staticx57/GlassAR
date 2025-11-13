package com.example.thermalarglass;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.media.MediaRecorder;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

// Using native USB implementation (no external library needed)

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * Main Activity for Glass Thermal AR Application
 * Captures Boson 320 thermal stream, sends to server, displays AR annotations
 */
public class MainActivity extends Activity implements SurfaceHolder.Callback {
    
    private static final String TAG = "ThermalARGlass";
    private static final String DEFAULT_SERVER_URL = "http://192.168.1.100:8080"; // Default server
    private static final String PREF_NAME = "GlassARPrefs";
    private static final String PREF_SERVER_URL = "server_url";
    private static final int PERMISSION_REQUEST_CAMERA = 1;
    
    // Boson 320 specs
    private static final int BOSON_WIDTH = 320;
    private static final int BOSON_HEIGHT = 256;
    private static final int TARGET_FPS = 60;

    // Boson format specifications
    // FLIR Boson can include 2 telemetry rows at the bottom of frames (per SDK)
    private static final int Y16_FRAME_SIZE = 320 * 256 * 2;           // 163,840 bytes (16-bit grayscale, no telemetry)
    private static final int Y16_FRAME_SIZE_WITH_TELEM = 320 * 258 * 2; // 165,120 bytes (with 2 telemetry rows)
    private static final int I420_FRAME_SIZE = 640 * 512 * 3 / 2;      // 491,520 bytes (YUV420 planar, no telemetry)
    private static final int I420_FRAME_SIZE_WITH_TELEM = 640 * 514 * 3 / 2; // 494,592 bytes (with 2 telemetry rows)
    private static final int I420_WIDTH = 640;
    private static final int I420_HEIGHT = 512;

    // Format detection
    private enum BosonFormat {
        Y16,    // 16-bit radiometric (320×256)
        I420    // 8-bit colorized YUV420 (640×512)
    }
    private BosonFormat mDetectedFormat = null;

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
    private TextView mBatteryIndicator;
    private TextView mNetworkIndicator;
    private ImageView mCenterReticle;
    private ImageView mRecordingIndicator;
    private LinearLayout mAlertArea;
    private TextView mAlertText;
    private ProgressBar mProcessingIndicator;
    private android.widget.Button mSettingsButton;

    // Glass touchpad gesture detection
    private GestureDetector mGestureDetector;

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

    // Colormap settings
    private String mCurrentColormap = "iron"; // Default colormap
    private String[] mAvailableColormaps = {"iron", "rainbow", "white_hot", "arctic", "grayscale"};
    private int mCurrentColormapIndex = 0;

    // Glass EE2 built-in RGB camera
    private android.hardware.Camera mRgbCamera;
    private boolean mRgbCameraEnabled = false;
    private byte[] mLatestRgbFrame = null;

    // Camera mode tracking
    private boolean mThermalCameraActive = false;
    private boolean mUsingRgbFallback = false;

    // Frame counter
    private int mFrameCount = 0;

    // Latest thermal frame for snapshot capture
    private Bitmap mLatestThermalBitmap = null;
    private ByteBuffer mLatestFrameData = null;

    // Video recording (frame-based for Glass EE2)
    private int mRecordingFrameInterval = 3; // Capture every 3rd frame (~10 fps from 30fps source)
    private int mRecordingFrameCounter = 0;
    private File mRecordingDir = null;
    private int mRecordingSavedFrames = 0;

    // Battery monitoring
    private int mBatteryLevel = 100;
    private BroadcastReceiver mBatteryReceiver;

    // Server URL configuration receiver
    private BroadcastReceiver mServerConfigReceiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check for camera permission (required for API 23+, including Glass EE2 on API 27)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Camera permission not granted, requesting permission");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
            // Don't return - continue initialization for UI, cameras will be started after permission granted
        }

        // Initialize display
        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);

        // Initialize UI elements
        mConnectionStatus = findViewById(R.id.connection_status);
        mModeIndicator = findViewById(R.id.mode_indicator);
        mFrameCounter = findViewById(R.id.frame_counter);
        mCenterTemperature = findViewById(R.id.center_temperature);
        mBatteryIndicator = findViewById(R.id.battery_indicator);
        mNetworkIndicator = findViewById(R.id.network_indicator);
        mCenterReticle = findViewById(R.id.center_reticle);
        mRecordingIndicator = findViewById(R.id.recording_indicator);
        mAlertArea = findViewById(R.id.alert_area);
        mAlertText = findViewById(R.id.alert_text);
        mProcessingIndicator = findViewById(R.id.processing_indicator);
        mSettingsButton = findViewById(R.id.settings_button);

        // Settings button click handler (for Vysor access)
        if (mSettingsButton != null) {
            mSettingsButton.setOnClickListener(v -> {
                Log.i(TAG, "Settings button clicked");
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            });
        }

        // Initialize Glass touchpad gesture detector
        mGestureDetector = createGestureDetector();

        // Initialize USB monitor for Boson (using native implementation)
        mUSBMonitor = new NativeUSBMonitor(this, mOnDeviceConnectListener);

        // Initialize network connection
        initializeSocket();

        // Initialize battery monitoring
        initializeBatteryMonitoring();

        // Initialize server URL configuration receiver
        initializeServerConfigReceiver();

        // Load settings and apply defaults
        loadSettings();

        // Start RGB camera as fallback if no thermal camera detected within 5 seconds (if enabled in settings)
        // Extended delay to give thermal camera time to connect via USB
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            boolean rgbFallbackEnabled = prefs.getBoolean("rgb_fallback", true);

            if (!mThermalCameraActive && rgbFallbackEnabled) {
                Log.i(TAG, "No thermal camera detected after 5s, starting RGB fallback");
                startRgbCameraFallback();
            } else if (mThermalCameraActive) {
                Log.i(TAG, "Thermal camera active, skipping RGB fallback");
            } else {
                Log.i(TAG, "RGB fallback disabled in settings");
            }
        }, 5000);

        Log.i(TAG, "Thermal AR Glass initialized");
    }

    /**
     * Load settings from SharedPreferences and apply them
     */
    private void loadSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Load default colormap
        String defaultColormap = prefs.getString("default_colormap", "iron");
        mCurrentColormap = defaultColormap;

        // Find colormap index
        for (int i = 0; i < mAvailableColormaps.length; i++) {
            if (mAvailableColormaps[i].equals(defaultColormap)) {
                mCurrentColormapIndex = i;
                break;
            }
        }

        Log.i(TAG, "Loaded settings - Colormap: " + mCurrentColormap);
    }

    /**
     * Creates GestureDetector for Glass touchpad gestures
     */
    private GestureDetector createGestureDetector() {
        return new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            // Swipe gesture constants for Glass
            private static final int SWIPE_MIN_DISTANCE = 50;
            private static final int SWIPE_MAX_OFF_PATH = 250;
            private static final int SWIPE_THRESHOLD_VELOCITY = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                try {
                    // Check if swipe is too vertical (off path)
                    if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
                        return false;
                    }

                    // Horizontal swipe detection
                    if (Math.abs(e1.getX() - e2.getX()) > SWIPE_MIN_DISTANCE &&
                        Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {

                        if (e1.getX() - e2.getX() > 0) {
                            // Swipe backward (right to left)
                            onSwipeBackward();
                        } else {
                            // Swipe forward (left to right)
                            onSwipeForward();
                        }
                        return true;
                    }

                    // Vertical swipe detection (swipe down to dismiss)
                    if (e1.getY() - e2.getY() < -SWIPE_MIN_DISTANCE &&
                        Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
                        onSwipeDown();
                        return true;
                    }

                    // Vertical swipe up - open settings
                    if (e1.getY() - e2.getY() > SWIPE_MIN_DISTANCE &&
                        Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
                        onSwipeUp();
                        return true;
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Gesture detection error", e);
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                onTap();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                onLongTap();
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                onDoubleTapGesture();
                return true;
            }
        });
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Forward touchpad motion events to gesture detector
        if (mGestureDetector != null) {
            return mGestureDetector.onTouchEvent(event);
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle Glass hardware button
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            // Camera button pressed - take snapshot
            captureSnapshot();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Camera permission granted");
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();

                // Permission granted - cameras will be initialized normally in onStart()
                // or by the RGB fallback handler
            } else {
                Log.w(TAG, "Camera permission denied");
                Toast.makeText(this,
                    "Camera permission required for thermal imaging. Please grant permission in Settings.",
                    Toast.LENGTH_LONG).show();

                // Show alert that camera permission is required
                runOnUiThread(() -> {
                    if (mAlertArea != null && mAlertText != null) {
                        mAlertArea.setVisibility(View.VISIBLE);
                        mAlertText.setText("⚠ Camera permission required\nGrant permission in Settings");
                    }
                });
            }
        }
    }

    // ========== Touchpad Gesture Handlers ==========

    /**
     * Handle touchpad tap gesture
     * Action: Toggle annotation overlay visibility
     */
    private void onTap() {
        Log.i(TAG, "Touchpad: Tap");

        // Toggle center reticle visibility
        if (mCenterReticle != null) {
            int currentVisibility = mCenterReticle.getVisibility();
            int newVisibility = (currentVisibility == View.VISIBLE) ? View.GONE : View.VISIBLE;
            mCenterReticle.setVisibility(newVisibility);
            mCenterTemperature.setVisibility(newVisibility);

            Toast.makeText(this,
                newVisibility == View.VISIBLE ? "Overlay ON" : "Overlay OFF",
                Toast.LENGTH_SHORT).show();
        }

        // Provide haptic feedback
        performHapticFeedback();
    }

    /**
     * Handle touchpad double-tap gesture
     * Action: Take snapshot
     */
    private void onDoubleTapGesture() {
        Log.i(TAG, "Touchpad: Double tap");
        captureSnapshot();
    }

    /**
     * Handle touchpad long press gesture
     * Action: Start/stop recording
     */
    private void onLongTap() {
        Log.i(TAG, "Touchpad: Long press");
        toggleRecording();
    }

    /**
     * Handle swipe forward gesture (toward front of Glass)
     * Action: Cycle through display modes
     */
    private void onSwipeForward() {
        Log.i(TAG, "Touchpad: Swipe forward");

        // Cycle through modes: Thermal Only -> RGB Fusion -> Advanced -> Thermal Only
        switch (mCurrentMode) {
            case MODE_THERMAL_ONLY:
                switchToThermalRgbFusionMode();
                break;
            case MODE_THERMAL_RGB_FUSION:
                switchToAdvancedInspectionMode();
                break;
            case MODE_ADVANCED_INSPECTION:
                switchToThermalOnlyMode();
                break;
        }

        performHapticFeedback();
    }

    /**
     * Handle swipe backward gesture (toward back of Glass)
     * Action: Navigate detections (connected mode) or cycle colormaps (standalone mode)
     */
    private void onSwipeBackward() {
        Log.i(TAG, "Touchpad: Swipe backward");

        // Connected mode: Navigate through detections
        if (mConnected && !mDetections.isEmpty()) {
            // Navigate to previous detection (wrap around)
            mCurrentDetectionIndex--;
            if (mCurrentDetectionIndex < 0) {
                mCurrentDetectionIndex = mDetections.size() - 1;
            }

            highlightDetection(mCurrentDetectionIndex);
            Log.i(TAG, "Navigated to detection: " + mCurrentDetectionIndex);
            performHapticFeedback();
            return;
        }

        // Standalone mode: Cycle colormaps (only when thermal camera active)
        if (!mThermalCameraActive) {
            Toast.makeText(this,
                mConnected ? "No detections available" : "Colormap cycling only available in thermal mode",
                Toast.LENGTH_SHORT).show();
            return;
        }

        // Cycle to next colormap
        mCurrentColormapIndex = (mCurrentColormapIndex + 1) % mAvailableColormaps.length;
        mCurrentColormap = mAvailableColormaps[mCurrentColormapIndex];

        // Update UI
        Toast.makeText(this, "Colormap: " + mCurrentColormap, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Switched to colormap: " + mCurrentColormap);

        performHapticFeedback();
    }

    /**
     * Handle swipe down gesture
     * Action: Dismiss alerts or return to main view
     */
    private void onSwipeDown() {
        Log.i(TAG, "Touchpad: Swipe down");

        // Dismiss alert if showing
        if (mAlertArea != null && mAlertArea.getVisibility() == View.VISIBLE) {
            mAlertArea.setVisibility(View.GONE);
            Toast.makeText(this, "Alert dismissed", Toast.LENGTH_SHORT).show();
        } else {
            // Reset to default view
            resetToMainView();
        }

        performHapticFeedback();
    }

    /**
     * Handle swipe up gesture
     * Action: Open settings activity
     */
    private void onSwipeUp() {
        Log.i(TAG, "Touchpad: Swipe up - Opening settings");

        android.content.Intent intent = new android.content.Intent(this, SettingsActivity.class);
        startActivity(intent);

        performHapticFeedback();
    }

    // ========== Gesture Action Implementations ==========

    // Detection navigation tracking
    private int mCurrentDetectionIndex = 0;
    private boolean mIsRecording = false;

    /**
     * Captures a snapshot of current thermal frame with annotations
     */
    private void captureSnapshot() {
        Log.i(TAG, "Capturing snapshot");

        // Show visual feedback
        if (mProcessingIndicator != null) {
            mProcessingIndicator.setVisibility(View.VISIBLE);
        }

        // Save snapshot in background thread
        new Thread(() -> {
            try {
                // Create snapshot bitmap with annotations
                Bitmap snapshot = createSnapshotBitmap();

                if (snapshot == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "No frame available to capture", Toast.LENGTH_SHORT).show();
                        if (mProcessingIndicator != null) {
                            mProcessingIndicator.setVisibility(View.GONE);
                        }
                    });
                    return;
                }

                // Create Pictures/ThermalAR directory and VALIDATE it succeeded
                File picturesDir = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "ThermalAR");
                if (!picturesDir.exists()) {
                    boolean created = picturesDir.mkdirs();
                    if (!created) {
                        Log.e(TAG, "FAILED to create snapshot directory: " + picturesDir.getAbsolutePath());
                        Log.e(TAG, "Check storage permissions and available space");
                        runOnUiThread(() -> {
                            Toast.makeText(this,
                                "Failed to create snapshot directory - check storage permissions",
                                Toast.LENGTH_LONG).show();
                            if (mProcessingIndicator != null) {
                                mProcessingIndicator.setVisibility(View.GONE);
                            }
                        });
                        return;
                    }
                    Log.i(TAG, "Created snapshot directory: " + picturesDir.getAbsolutePath());
                }

                // Generate filename with timestamp
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                String timestamp = sdf.format(new Date());
                String filename = "thermal_" + timestamp + ".png";
                File file = new File(picturesDir, filename);

                // Save bitmap to file with VALIDATION
                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(file);

                    // VALIDATE: Check if compression succeeded
                    boolean compressed = snapshot.compress(Bitmap.CompressFormat.PNG, 100, out);
                    if (!compressed) {
                        Log.e(TAG, "FAILED to compress bitmap to PNG");
                        throw new IOException("Bitmap compression failed");
                    }

                    out.flush();
                    out.close();
                    out = null;  // Prevent double-close in finally

                    // VALIDATE: Verify file was created and has content
                    if (!file.exists()) {
                        Log.e(TAG, "FAILED - snapshot file does not exist after write");
                        throw new IOException("File not created");
                    }

                    long fileSize = file.length();
                    if (fileSize == 0) {
                        Log.e(TAG, "FAILED - snapshot file is empty (0 bytes)");
                        throw new IOException("File is empty");
                    }

                    Log.i(TAG, "✓ Snapshot saved successfully: " + file.getAbsolutePath() + " (" + fileSize + " bytes)");

                    // Show success message
                    final long finalFileSize = fileSize;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Snapshot saved: " + filename + " (" + finalFileSize + " bytes)", Toast.LENGTH_SHORT).show();
                        if (mProcessingIndicator != null) {
                            mProcessingIndicator.setVisibility(View.GONE);
                        }
                        performHapticFeedback();
                    });

                } finally {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing output stream", e);
                        }
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "Failed to save snapshot", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to save snapshot: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (mProcessingIndicator != null) {
                        mProcessingIndicator.setVisibility(View.GONE);
                    }
                });
            }
        }).start();

        // Play camera shutter sound
        playCameraShutterSound();
    }

    /**
     * Creates a snapshot bitmap with thermal image and annotations
     */
    private Bitmap createSnapshotBitmap() {
        if (mLatestThermalBitmap == null) {
            return null;
        }

        // Create a new bitmap for the snapshot (Glass display size)
        Bitmap snapshot = Bitmap.createBitmap(GLASS_WIDTH, GLASS_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(snapshot);

        // Draw black background
        canvas.drawColor(Color.BLACK);

        // Scale and draw thermal bitmap
        Rect destRect = new Rect(0, 0, GLASS_WIDTH, GLASS_HEIGHT);
        canvas.drawBitmap(mLatestThermalBitmap, null, destRect, null);

        // Draw annotations on top
        drawAnnotations(canvas);

        return snapshot;
    }

    /**
     * Toggles video recording on/off
     */
    private void toggleRecording() {
        if (mIsRecording) {
            stopRecording();
        } else {
            startRecording();
        }
        performHapticFeedback();
    }

    /**
     * Starts video recording (frame sequence capture)
     */
    private void startRecording() {
        Log.i(TAG, "Starting recording");

        try {
            // Create recording directory with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());
            String dirName = "recording_" + timestamp;

            // Create base videos directory and VALIDATE
            File videosDir = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "ThermalAR");
            if (!videosDir.exists()) {
                boolean created = videosDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "FAILED to create videos directory: " + videosDir.getAbsolutePath());
                    Log.e(TAG, "Check storage permissions and available space");
                    Toast.makeText(this,
                        "Failed to create recording directory - check storage permissions",
                        Toast.LENGTH_LONG).show();
                    mIsRecording = false;
                    return;
                }
                Log.i(TAG, "Created videos directory: " + videosDir.getAbsolutePath());
            }

            // Create specific recording directory and VALIDATE
            mRecordingDir = new File(videosDir, dirName);
            if (!mRecordingDir.exists()) {
                boolean created = mRecordingDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "FAILED to create recording directory: " + mRecordingDir.getAbsolutePath());
                    Log.e(TAG, "Check storage permissions and available space");
                    Toast.makeText(this,
                        "Failed to create recording directory - check storage permissions",
                        Toast.LENGTH_LONG).show();
                    mIsRecording = false;
                    return;
                }
                Log.i(TAG, "Created recording directory: " + mRecordingDir.getAbsolutePath());
            }

            mRecordingFrameCounter = 0;
            mRecordingSavedFrames = 0;
            mIsRecording = true;

            runOnUiThread(() -> {
                if (mRecordingIndicator != null) {
                    mRecordingIndicator.setVisibility(View.VISIBLE);
                }
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
            });

            Log.i(TAG, "✓ Recording to: " + mRecordingDir.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            mIsRecording = false;
        }
    }

    /**
     * Stops video recording and saves frame sequence
     */
    private void stopRecording() {
        Log.i(TAG, "Stopping recording");
        mIsRecording = false;

        runOnUiThread(() -> {
            if (mRecordingIndicator != null) {
                mRecordingIndicator.setVisibility(View.GONE);
            }
        });

        // Save recording info file in background
        new Thread(() -> {
            try {
                if (mRecordingDir == null || mRecordingSavedFrames == 0) {
                    runOnUiThread(() ->
                        Toast.makeText(this, "No frames recorded", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                // Create info file with recording details
                File infoFile = new File(mRecordingDir, "recording_info.txt");
                FileOutputStream out = new FileOutputStream(infoFile);
                String info = String.format(Locale.US,
                    "Recording Info\n" +
                    "==============\n" +
                    "Frames saved: %d\n" +
                    "Approx duration: %.1f seconds\n" +
                    "Frame rate: ~10 fps (every 3rd frame)\n" +
                    "Resolution: %dx%d\n" +
                    "\n" +
                    "To convert to video using ffmpeg:\n" +
                    "ffmpeg -framerate 10 -pattern_type glob -i 'frame_*.png' -c:v libx264 -pix_fmt yuv420p output.mp4\n",
                    mRecordingSavedFrames,
                    mRecordingSavedFrames / 10.0f,
                    GLASS_WIDTH, GLASS_HEIGHT
                );
                out.write(info.getBytes());
                out.close();

                final String dirName = mRecordingDir.getName();
                final int frameCount = mRecordingSavedFrames;

                runOnUiThread(() -> {
                    Toast.makeText(this,
                        String.format(Locale.US, "Recording saved: %s\n%d frames (~%.1fs)",
                            dirName, frameCount, frameCount / 10.0f),
                        Toast.LENGTH_LONG).show();
                });

                Log.i(TAG, "Recording complete: " + mRecordingDir.getAbsolutePath() +
                    " (" + mRecordingSavedFrames + " frames)");

            } catch (IOException e) {
                Log.e(TAG, "Failed to save recording info", e);
            }

            mRecordingDir = null;
            mRecordingSavedFrames = 0;
            mRecordingFrameCounter = 0;

        }).start();
    }

    /**
     * Saves a frame during recording
     * Called from renderThermalFrame() when recording is active
     */
    private void saveRecordingFrame(Bitmap frame) {
        if (!mIsRecording || mRecordingDir == null || frame == null) {
            return;
        }

        // Only save every Nth frame to reduce storage and processing
        mRecordingFrameCounter++;
        if (mRecordingFrameCounter % mRecordingFrameInterval != 0) {
            return;
        }

        // Save frame in background thread
        final Bitmap frameCopy = frame.copy(frame.getConfig(), false);
        new Thread(() -> {
            FileOutputStream out = null;
            try {
                String filename = String.format(Locale.US, "frame_%06d.png", mRecordingSavedFrames);
                File frameFile = new File(mRecordingDir, filename);

                out = new FileOutputStream(frameFile);

                // VALIDATE: Check if compression succeeded
                boolean compressed = frameCopy.compress(Bitmap.CompressFormat.PNG, 100, out);
                if (!compressed) {
                    Log.e(TAG, "FAILED to compress recording frame " + mRecordingSavedFrames);
                    throw new IOException("Bitmap compression failed");
                }

                out.flush();
                out.close();
                out = null;  // Prevent double-close

                // VALIDATE: File exists and has content
                if (!frameFile.exists() || frameFile.length() == 0) {
                    Log.e(TAG, "FAILED to save recording frame - file missing or empty");
                    throw new IOException("Frame file not created properly");
                }

                // Only increment if save actually succeeded
                mRecordingSavedFrames++;

                // Update UI every 30 frames (~3 seconds)
                if (mRecordingSavedFrames % 30 == 0) {
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                            String.format(Locale.US, "Recording: %d frames (~%.1fs)",
                                mRecordingSavedFrames, mRecordingSavedFrames / 10.0f),
                            Toast.LENGTH_SHORT).show();
                    });
                }

            } catch (IOException e) {
                Log.e(TAG, "Failed to save recording frame " + mRecordingSavedFrames, e);
                // Note: Frame counter NOT incremented on failure
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing frame output stream", e);
                    }
                }
            }
        }).start();
    }

    /**
     * Highlights a specific detection by index
     */
    private void highlightDetection(int index) {
        if (index < 0 || index >= mDetections.size()) {
            return;
        }

        Detection det = mDetections.get(index);
        String message = String.format("Detection %d/%d: %s (%.1f%%)",
            index + 1, mDetections.size(), det.className, det.confidence * 100);

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        // Trigger redraw to highlight this detection
        // The render method will use mCurrentDetectionIndex to emphasize the selected detection
    }

    /**
     * Resets view to main thermal display
     */
    private void resetToMainView() {
        mCurrentDetectionIndex = 0;

        // Reset UI elements to default state
        if (mCenterReticle != null) {
            mCenterReticle.setVisibility(View.VISIBLE);
        }
        if (mCenterTemperature != null) {
            mCenterTemperature.setVisibility(View.VISIBLE);
        }

        Toast.makeText(this, "Reset to main view", Toast.LENGTH_SHORT).show();
    }

    /**
     * Provides haptic feedback for gesture confirmation
     */
    private void performHapticFeedback() {
        // Glass EE2 supports haptic feedback
        mSurfaceView.performHapticFeedback(
            android.view.HapticFeedbackConstants.VIRTUAL_KEY,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        );
    }

    /**
     * Plays camera shutter sound
     */
    private void playCameraShutterSound() {
        // Use MediaActionSound for standard camera sound
        android.media.MediaActionSound sound = new android.media.MediaActionSound();
        sound.play(android.media.MediaActionSound.SHUTTER_CLICK);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        mUSBMonitor.register();

        // Respect auto-connect setting
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean autoConnect = prefs.getBoolean("auto_connect", true);

        if (mSocket != null && !mSocket.connected() && autoConnect) {
            mSocket.connect();
            Log.i(TAG, "Auto-connecting to server (enabled in settings)");
        } else if (!autoConnect) {
            Log.i(TAG, "Auto-connect disabled in settings");
        }

        // Restart RGB camera fallback if it was active and no thermal camera
        // This handles the case where user opens settings and returns
        boolean rgbFallbackEnabled = prefs.getBoolean("rgb_fallback", true);
        if (mUsingRgbFallback && !mThermalCameraActive && rgbFallbackEnabled) {
            Log.i(TAG, "Resuming from settings - restarting RGB camera fallback");
            // Small delay to ensure surface is ready
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!mThermalCameraActive && mSurfaceHolder != null) {
                    startRgbCameraFallback();
                }
            }, 500);
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

        // Unregister server config receiver
        if (mServerConfigReceiver != null) {
            try {
                unregisterReceiver(mServerConfigReceiver);
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

                    // Update battery indicator
                    updateBatteryIndicator();

                    // Send battery status to server
                    sendBatteryStatus();

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

    /**
     * Initialize server URL configuration receiver
     * Allows setting server URL via ADB or Vysor:
     * adb shell am broadcast -a com.example.thermalarglass.SET_SERVER_URL --es url "http://YOUR_IP:8080"
     */
    private void initializeServerConfigReceiver() {
        mServerConfigReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.thermalarglass.SET_SERVER_URL".equals(intent.getAction())) {
                    String url = intent.getStringExtra("url");
                    if (url != null && !url.isEmpty()) {
                        setServerUrl(url);
                        runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                "Server URL updated: " + url,
                                Toast.LENGTH_LONG).show()
                        );
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter("com.example.thermalarglass.SET_SERVER_URL");
        registerReceiver(mServerConfigReceiver, filter);
        Log.i(TAG, "Server config receiver registered. Use ADB to configure: adb shell am broadcast -a com.example.thermalarglass.SET_SERVER_URL --es url \"http://YOUR_IP:8080\"");
    }

    /**
     * Update battery indicator UI
     */
    private void updateBatteryIndicator() {
        runOnUiThread(() -> {
            if (mBatteryIndicator == null) return;

            String icon;
            int color;

            if (mBatteryLevel > 80) {
                icon = "🔋"; // Full battery
                color = Color.parseColor("#00FF00"); // Green
            } else if (mBatteryLevel > 50) {
                icon = "🔋"; // Good battery
                color = Color.parseColor("#FFFF00"); // Yellow
            } else if (mBatteryLevel > 20) {
                icon = "🔋"; // Low battery
                color = Color.parseColor("#FFA500"); // Orange
            } else {
                icon = "🪫"; // Critical battery
                color = Color.parseColor("#FF0000"); // Red
            }

            mBatteryIndicator.setText(icon + " " + mBatteryLevel + "%");
            mBatteryIndicator.setTextColor(color);
        });
    }

    /**
     * Send battery status to server/companion app
     */
    private void sendBatteryStatus() {
        if (mSocket != null && mConnected) {
            try {
                JSONObject data = new JSONObject();
                data.put("battery_level", mBatteryLevel);
                data.put("is_charging", isCharging());
                data.put("timestamp", System.currentTimeMillis());
                mSocket.emit("battery_status", data);
            } catch (JSONException e) {
                Log.e(TAG, "Error sending battery status", e);
            }
        }
    }

    /**
     * Check if device is charging
     */
    private boolean isCharging() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                   status == BatteryManager.BATTERY_STATUS_FULL;
        }
        return false;
    }

    /**
     * Update network quality indicator
     */
    private void updateNetworkIndicator(int signalStrength, int latencyMs) {
        runOnUiThread(() -> {
            if (mNetworkIndicator == null) return;

            String icon;
            int color;

            if (latencyMs < 50 && signalStrength > 80) {
                icon = "📶"; // Excellent
                color = Color.parseColor("#00FF00"); // Green
            } else if (latencyMs < 100 && signalStrength > 60) {
                icon = "📶"; // Good
                color = Color.parseColor("#FFFF00"); // Yellow
            } else if (latencyMs < 200 && signalStrength > 40) {
                icon = "📡"; // Fair
                color = Color.parseColor("#FFA500"); // Orange
            } else {
                icon = "📡"; // Poor
                color = Color.parseColor("#FF0000"); // Red
            }

            mNetworkIndicator.setText(icon);
            mNetworkIndicator.setTextColor(color);
        });

        // Send network stats to server/companion
        sendNetworkStats(latencyMs, signalStrength);
    }

    /**
     * Send network statistics to server/companion app
     */
    private void sendNetworkStats(int latencyMs, int signalStrength) {
        if (mSocket != null && mConnected) {
            try {
                JSONObject data = new JSONObject();
                data.put("latency_ms", latencyMs);
                data.put("signal_strength", signalStrength);
                data.put("timestamp", System.currentTimeMillis());
                mSocket.emit("network_stats", data);
            } catch (JSONException e) {
                Log.e(TAG, "Error sending network stats", e);
            }
        }
    }

    /**
     * Start periodic network statistics updates
     */
    private void startNetworkStatsUpdates() {
        // Use a handler to send network stats every 5 seconds
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mConnected) {
                    // Calculate network stats (simplified - measure ping to server)
                    int latencyMs = measureLatency();
                    int signalStrength = getWifiSignalStrength();

                    updateNetworkIndicator(signalStrength, latencyMs);

                    // Schedule next update
                    handler.postDelayed(this, 5000);
                }
            }
        }, 5000);
    }

    /**
     * Measure network latency (simplified)
     */
    private int measureLatency() {
        // Simplified latency measurement
        // In production, you'd ping the server and measure round-trip time
        // For now, return a reasonable estimate based on connection quality
        return 50; // Default to 50ms (update with actual measurement if needed)
    }

    /**
     * Get WiFi signal strength as percentage
     * Returns 0% if WiFi unavailable or disabled
     */
    private int getWifiSignalStrength() {
        try {
            android.net.wifi.WifiManager wifiManager =
                (android.net.wifi.WifiManager) getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null) {
                Log.w(TAG, "WiFi manager unavailable");
                return 0; // No WiFi manager = no signal
            }

            // Check if WiFi is enabled
            if (!wifiManager.isWifiEnabled()) {
                Log.w(TAG, "WiFi is disabled");
                return 0; // WiFi disabled = 0% signal
            }

            android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) {
                Log.w(TAG, "WiFi info unavailable");
                return 0;
            }

            int rssi = wifiInfo.getRssi();

            // Check for invalid RSSI (e.g., not connected)
            if (rssi == 0 || rssi < -100 || rssi > 0) {
                Log.w(TAG, "Invalid RSSI: " + rssi);
                return 0;
            }

            // Convert RSSI to percentage (typical range: -100 to -50 dBm)
            // -100 dBm = 0%, -50 dBm = 100%
            return Math.max(0, Math.min(100, (rssi + 100) * 2));

        } catch (SecurityException e) {
            Log.e(TAG, "Missing WiFi permission (should not happen with ACCESS_WIFI_STATE)", e);
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "Error getting WiFi signal strength", e);
            return 0;
        }
    }

    /**
     * Extract temperature measurements from thermal frame
     * Boson 320 outputs 16-bit thermal data (YUYV format)
     */
    private ThermalData extractTemperatures(byte[] thermalFrame) {
        try {
            // Validate frame size
            int expectedSize = BOSON_WIDTH * BOSON_HEIGHT * 2; // YUYV = 2 bytes/pixel
            if (thermalFrame == null || thermalFrame.length < expectedSize) {
                Log.w(TAG, "Invalid thermal frame size: " +
                      (thermalFrame != null ? thermalFrame.length : "null") +
                      " (expected: " + expectedSize + ")");
                return null;  // Return null to indicate extraction failed
            }

            // Calculate center pixel offset (center of 320x256 frame)
            int centerY = BOSON_HEIGHT / 2;
            int centerX = BOSON_WIDTH / 2;
            int centerOffset = (centerY * BOSON_WIDTH + centerX) * 2; // *2 for YUYV (2 bytes per pixel)

            // Bounds check for center pixel
            if (centerOffset + 1 >= thermalFrame.length) {
                Log.e(TAG, "Center pixel offset out of bounds: " + centerOffset +
                      " (frame length: " + thermalFrame.length + ")");
                return null;  // Return null to indicate extraction failed
            }

            // Extract center pixel value
            int centerPixel = (thermalFrame[centerOffset] & 0xFF) | ((thermalFrame[centerOffset + 1] & 0xFF) << 8);
            float centerTemp = applyCalibration(centerPixel);

            // Calculate min, max, and average temperatures
            float minTemp = Float.MAX_VALUE;
            float maxTemp = Float.MIN_VALUE;
            float sum = 0;
            int count = 0;

            // Iterate through all pixels (YUYV format - 2 bytes per pixel)
            for (int i = 0; i < thermalFrame.length - 1; i += 2) {
                int pixel = (thermalFrame[i] & 0xFF) | ((thermalFrame[i + 1] & 0xFF) << 8);
                float temp = applyCalibration(pixel);

                minTemp = Math.min(minTemp, temp);
                maxTemp = Math.max(maxTemp, temp);
                sum += temp;
                count++;
            }

            // Prevent division by zero
            float avgTemp = count > 0 ? sum / count : 0;

            return new ThermalData(centerTemp, minTemp, maxTemp, avgTemp);

        } catch (Exception e) {
            Log.e(TAG, "Error extracting temperatures", e);
            // Return null to indicate extraction failed
            return null;
        }
    }

    /**
     * Apply Boson 320 calibration to convert raw pixel value to temperature (Celsius)
     * Boson 320 calibration formula: T = (pixel - 8192) * 0.01 + 20.0
     * This is a simplified calibration - actual Boson calibration may vary
     */
    private float applyCalibration(int pixelValue) {
        // Boson 320 typical calibration
        // Raw values typically range from ~7000-10000 for normal temperature ranges
        return (pixelValue - 8192) * 0.01f + 20.0f;
    }

    /**
     * Send thermal data measurements to server/companion app
     */
    private void sendThermalData(ThermalData thermalData) {
        if (mSocket != null && mConnected) {
            try {
                JSONObject data = new JSONObject();
                data.put("center_temp", thermalData.centerTemp);
                data.put("min_temp", thermalData.minTemp);
                data.put("max_temp", thermalData.maxTemp);
                data.put("avg_temp", thermalData.avgTemp);
                data.put("timestamp", System.currentTimeMillis());
                mSocket.emit("thermal_data", data);
            } catch (JSONException e) {
                Log.e(TAG, "Error sending thermal data", e);
            }
        }
    }

    /**
     * Handle auto-snapshot settings from companion app
     */
    private void handleAutoSnapshotSettings(JSONObject data) {
        try {
            boolean enabled = data.getBoolean("enabled");
            double tempThreshold = data.getDouble("temp_threshold");
            double confThreshold = data.getDouble("confidence_threshold");
            int cooldownSeconds = data.getInt("cooldown_seconds");

            // Store settings (add member variables at top of class if needed)
            // mAutoSnapshotEnabled = enabled;
            // mAutoSnapshotTempThreshold = tempThreshold;
            // mAutoSnapshotConfThreshold = confThreshold;
            // mAutoSnapshotCooldown = cooldownSeconds;

            Log.i(TAG, String.format("Auto-snapshot settings updated: enabled=%b, temp=%.1f°C, conf=%.2f, cooldown=%ds",
                    enabled, tempThreshold, confThreshold, cooldownSeconds));

            runOnUiThread(() ->
                Toast.makeText(this, "Auto-snapshot: " + (enabled ? "Enabled" : "Disabled"),
                    Toast.LENGTH_SHORT).show()
            );
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing auto-snapshot settings", e);
        }
    }

    /**
     * Handle colormap change from companion app
     */
    private void handleColormapChange(JSONObject data) {
        try {
            String colormap = data.getString("colormap");

            // Store current colormap
            mCurrentColormap = colormap;

            Log.i(TAG, "Colormap changed to: " + colormap);

            runOnUiThread(() ->
                Toast.makeText(this, "Colormap: " + colormap, Toast.LENGTH_SHORT).show()
            );

            // Colormap will be applied in frame processing (applyThermalColormap)
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing colormap change", e);
        }
    }

    /**
     * Get configured server URL from SharedPreferences
     * Falls back to default if not configured
     *
     * To configure server URL via ADB:
     * adb shell am broadcast -a com.example.thermalarglass.SET_SERVER_URL --es url "http://YOUR_IP:8080"
     */
    private String getServerUrl() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String url = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL);
        Log.i(TAG, "Using server URL: " + url);
        return url;
    }

    /**
     * Set server URL and reconnect
     */
    private void setServerUrl(String url) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREF_SERVER_URL, url).apply();
        Log.i(TAG, "Server URL updated to: " + url);

        // Reconnect with new URL
        if (mSocket != null) {
            mSocket.disconnect();
        }
        initializeSocket();
    }

    private void initializeSocket() {
        try {
            String serverUrl = getServerUrl();
            mSocket = IO.socket(serverUrl);
            
            mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(() -> {
                        mConnected = true;
                        mConnectionStatus.setText("Connected");
                        mConnectionStatus.setTextColor(Color.GREEN);
                        Toast.makeText(MainActivity.this, "Connected to server", Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "Connected to processing server");

                        // Register as Glass device
                        mSocket.emit("register_glass");

                        // Start periodic network stats updates (every 5 seconds)
                        startNetworkStatsUpdates();
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

            // Remote control handlers
            mSocket.on("set_auto_snapshot", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    JSONObject data = (JSONObject) args[0];
                    handleAutoSnapshotSettings(data);
                }
            });

            mSocket.on("set_colormap", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    JSONObject data = (JSONObject) args[0];
                    handleColormapChange(data);
                }
            });

            mSocket.on("set_mode", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    JSONObject data = (JSONObject) args[0];
                    try {
                        String mode = data.getString("mode");
                        mCurrentMode = mode;
                        runOnUiThread(() -> {
                            mModeIndicator.setText("Mode: " + mode);
                            Log.i(TAG, "Mode changed to: " + mode);
                        });
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing mode change", e);
                    }
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
                // Stop RGB fallback if running
                if (mUsingRgbFallback) {
                    Log.i(TAG, "Stopping RGB fallback, switching to thermal");
                    stopRgbCamera();
                    mUsingRgbFallback = false;
                }

                if (mCamera != null) {
                    mCamera.close();
                }

                // Create and open native UVC camera
                mCamera = new NativeUVCCamera(MainActivity.this);

                if (mCamera.open(device)) {
                    try {
                        // Set Boson 320 resolution and VALIDATE it succeeded
                        boolean formatSet = mCamera.setPreviewSize(BOSON_WIDTH, BOSON_HEIGHT);
                        if (!formatSet) {
                            Log.e(TAG, "FAILED to set preview size to " + BOSON_WIDTH + "x" + BOSON_HEIGHT);
                            Log.e(TAG, "Camera format negotiation failed - incompatible resolution or camera error");
                            Toast.makeText(MainActivity.this,
                                "Failed to configure camera format - check camera compatibility",
                                Toast.LENGTH_LONG).show();
                            mCamera.close();
                            return;
                        }

                        Log.i(TAG, "Preview size set successfully: " + BOSON_WIDTH + "x" + BOSON_HEIGHT);
                        mCamera.setFrameCallback(mFrameCallback);

                        if (mCamera.startPreview()) {
                            mThermalCameraActive = true;
                            Log.i(TAG, "✓ Boson 320 camera started successfully");

                            // Dismiss USB disconnect alert if showing
                            if (mAlertArea != null && mAlertText != null) {
                                mAlertArea.setVisibility(View.GONE);
                            }

                            Toast.makeText(MainActivity.this, "Thermal camera connected", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "Failed to start preview");
                            Toast.makeText(MainActivity.this, "Failed to start camera", Toast.LENGTH_SHORT).show();
                            mCamera.close();
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error starting camera", e);
                        Toast.makeText(MainActivity.this, "Error starting camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        if (mCamera != null) {
                            mCamera.close();
                        }
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
                // Stop recording if active
                if (mIsRecording) {
                    Log.w(TAG, "USB disconnected during recording, stopping recording");
                    stopRecording();
                    Toast.makeText(MainActivity.this, "Recording stopped - USB disconnected", Toast.LENGTH_LONG).show();
                }

                if (mCamera != null) {
                    mCamera.close();
                    mCamera = null;
                }
                mThermalCameraActive = false;

                // Show alert in alert area
                if (mAlertArea != null && mAlertText != null) {
                    mAlertArea.setVisibility(View.VISIBLE);
                    mAlertText.setText("⚠ Thermal camera disconnected\nReconnect USB or using RGB fallback");
                }

                // Restart RGB camera as fallback (with delay to allow alert to show)
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                    boolean rgbFallbackEnabled = prefs.getBoolean("rgb_fallback", true);

                    if (rgbFallbackEnabled) {
                        Log.i(TAG, "Thermal camera disconnected, starting RGB fallback");
                        startRgbCameraFallback();
                        Toast.makeText(MainActivity.this, "Switched to RGB camera", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Thermal camera disconnected", Toast.LENGTH_LONG).show();
                    }
                }, 1000);
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

            // Ensure ByteBuffer is at position 0 before processing
            frame.rewind();

            // Send frame to server if connected
            if (mConnected && mSocket != null) {
                // Convert frame to byte array
                byte[] frameData = new byte[frame.remaining()];
                frame.get(frameData);
                frame.rewind();  // Reset position after extraction

                // Extract temperature measurements from thermal data
                ThermalData thermalData = extractTemperatures(frameData);

                // VALIDATE: Check if extraction succeeded
                if (thermalData == null) {
                    Log.w(TAG, "Failed to extract temperature data from frame - skipping temperature update");
                    // Don't update display or send to server if extraction failed
                    runOnUiThread(() -> {
                        if (mCenterTemperature != null) {
                            mCenterTemperature.setText("--°C");  // Show invalid
                        }
                    });
                } else {
                    // Update center temperature display
                    runOnUiThread(() -> {
                        if (mCenterTemperature != null) {
                            mCenterTemperature.setText(String.format("%.1f°C", thermalData.centerTemp));
                        }
                    });

                    // Send thermal data measurements to companion app
                    sendThermalData(thermalData);
                }

                // Encode to base64 for JSON transmission
                String frameBase64 = Base64.encodeToString(frameData, Base64.NO_WRAP);

                // Create JSON payload with temperature data
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("frame", frameBase64);  // Now base64 encoded
                    payload.put("mode", mCurrentMode);
                    payload.put("frame_number", mFrameCount);
                    payload.put("timestamp", System.currentTimeMillis());

                    // Include temperature measurements
                    payload.put("center_temp", thermalData.centerTemp);
                    payload.put("min_temp", thermalData.minTemp);
                    payload.put("max_temp", thermalData.maxTemp);
                    payload.put("avg_temp", thermalData.avgTemp);

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
        if (mSurfaceHolder == null) {
            Log.w(TAG, "Cannot render - surface holder is null");
            return;
        }

        Canvas canvas = mSurfaceHolder.lockCanvas();
        if (canvas == null) {
            Log.w(TAG, "Cannot render - canvas is null");
            return;
        }

        try {
            // Clear canvas
            canvas.drawColor(Color.BLACK);

            // Convert thermal frame to bitmap and draw
            Bitmap thermalBitmap = convertThermalToBitmap(frameData);
            if (thermalBitmap != null) {
                // Store latest frame for snapshot capture
                mLatestThermalBitmap = thermalBitmap;

                // Store frame data copy for snapshot
                if (mLatestFrameData == null || mLatestFrameData.capacity() != frameData.capacity()) {
                    mLatestFrameData = ByteBuffer.allocate(frameData.capacity());
                }
                frameData.rewind();
                mLatestFrameData.clear();
                mLatestFrameData.put(frameData);
                frameData.rewind();

                // Scale to Glass display size
                Rect destRect = new Rect(0, 0, GLASS_WIDTH, GLASS_HEIGHT);
                canvas.drawBitmap(thermalBitmap, null, destRect, null);

                // Log successful render (only first 5 frames)
                if (mFrameCount <= 5) {
                    Log.i(TAG, "✓ Frame #" + mFrameCount + " rendered successfully to display");
                }
            } else {
                Log.e(TAG, "✗ Frame #" + mFrameCount + " - convertThermalToBitmap returned NULL");
                // Draw error indicator
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setTextSize(30);
                canvas.drawText("NO FRAME DATA", 50, 100, paint);
            }

            // Draw annotations on top
            drawAnnotations(canvas);

            // Save frame if recording is active
            if (mIsRecording && thermalBitmap != null) {
                // Create snapshot with annotations for recording
                Bitmap recordingFrame = createSnapshotBitmap();
                saveRecordingFrame(recordingFrame);
            }

        } finally {
            mSurfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private Bitmap convertThermalToBitmap(ByteBuffer frameData) {
        try {
            // Validate frame data
            if (frameData == null) {
                Log.e(TAG, "✗ convertThermalToBitmap: frame data is NULL");
                return null;
            }

            int available = frameData.remaining();

            // Auto-detect format on first frame
            if (mDetectedFormat == null) {
                Log.i(TAG, ">>> FORMAT AUTO-DETECTION <<<");
                Log.i(TAG, "  Received frame size: " + available + " bytes");
                Log.i(TAG, "  Y16 expected: " + Y16_FRAME_SIZE + " or " + Y16_FRAME_SIZE_WITH_TELEM + " bytes (320×256 or 320×258 with telemetry)");
                Log.i(TAG, "  I420 expected: " + I420_FRAME_SIZE + " or " + I420_FRAME_SIZE_WITH_TELEM + " bytes (640×512 or 640×514 with telemetry)");

                if (available == Y16_FRAME_SIZE || available == Y16_FRAME_SIZE_WITH_TELEM) {
                    mDetectedFormat = BosonFormat.Y16;
                    if (available == Y16_FRAME_SIZE_WITH_TELEM) {
                        Log.i(TAG, "✓ DETECTED: Y16 format (320×258, 16-bit radiometric WITH telemetry)");
                        Log.w(TAG, "  ⚠ Telemetry rows detected - will strip last 2 rows (1280 bytes)");
                    } else {
                        Log.i(TAG, "✓ DETECTED: Y16 format (320×256, 16-bit radiometric, no telemetry)");
                    }
                } else if (available == I420_FRAME_SIZE || available == I420_FRAME_SIZE_WITH_TELEM) {
                    mDetectedFormat = BosonFormat.I420;
                    if (available == I420_FRAME_SIZE_WITH_TELEM) {
                        Log.i(TAG, "✓ DETECTED: I420 format (640×514, YUV420 colorized WITH telemetry)");
                        Log.w(TAG, "  ⚠ Telemetry rows detected - will strip last 2 rows");
                    } else {
                        Log.i(TAG, "✓ DETECTED: I420 format (640×512, YUV420 colorized, no telemetry)");
                    }
                } else {
                    Log.e(TAG, "✗ UNKNOWN FRAME SIZE: " + available + " bytes");
                    Log.e(TAG, "  Expected Y16: " + Y16_FRAME_SIZE + " or " + Y16_FRAME_SIZE_WITH_TELEM);
                    Log.e(TAG, "  Expected I420: " + I420_FRAME_SIZE + " or " + I420_FRAME_SIZE_WITH_TELEM);
                    Log.e(TAG, "  Check UVC negotiation logs above");
                    return null;
                }
            }

            // Process based on detected format
            if (mDetectedFormat == BosonFormat.Y16) {
                return convertY16ToBitmap(frameData, available);
            } else if (mDetectedFormat == BosonFormat.I420) {
                return convertI420ToBitmap(frameData, available);
            }

            Log.e(TAG, "✗ No format detected - this should not happen!");
            return null;

        } catch (Exception e) {
            Log.e(TAG, "✗ EXCEPTION in convertThermalToBitmap", e);
            return null;
        }
    }

    /**
     * Convert Y16 format (16-bit radiometric) to bitmap
     * Format: 320×256, 2 bytes per pixel (Little Endian)
     */
    private Bitmap convertY16ToBitmap(ByteBuffer frameData, int available) {
        try {
            if (available < Y16_FRAME_SIZE) {
                Log.w(TAG, "Incomplete Y16 frame: expected " + Y16_FRAME_SIZE + " bytes, got " + available);
                return null;
            }

            // Make defensive copy - only copy image data, skip telemetry rows if present
            frameData.rewind();
            byte[] frameCopy = new byte[Y16_FRAME_SIZE];
            frameData.get(frameCopy);

            // If frame has telemetry (320×258), we already copied only first 163,840 bytes (320×256)
            // The last 1,280 bytes (2 telemetry rows) are automatically ignored

            // Create bitmap (320×256)
            Bitmap bitmap = Bitmap.createBitmap(BOSON_WIDTH, BOSON_HEIGHT, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[BOSON_WIDTH * BOSON_HEIGHT];

            // Extract 16-bit values from Y16 format
            int byteIndex = 0;
            for (int i = 0; i < pixels.length; i++) {
                if (byteIndex + 1 >= frameCopy.length) {
                    Log.w(TAG, "Buffer underrun at pixel " + i);
                    break;
                }

                // Read 16-bit Y16 value (Little Endian)
                int lowByte = frameCopy[byteIndex] & 0xFF;
                int highByte = frameCopy[byteIndex + 1] & 0xFF;
                int y16Value = (highByte << 8) | lowByte;

                // Scale 16-bit (0-65535) to 8-bit (0-255) for colormap
                int y8Value = (y16Value >> 8);

                byteIndex += 2;

                // Apply thermal colormap
                pixels[i] = applyThermalColormap(y8Value);
            }

            bitmap.setPixels(pixels, 0, BOSON_WIDTH, 0, 0, BOSON_WIDTH, BOSON_HEIGHT);
            frameData.rewind();

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error converting Y16 frame", e);
            return null;
        }
    }

    /**
     * Convert I420 format (YUV420 colorized) to bitmap
     * Format: 640×512, YUV420 planar (Y plane + U plane + V plane)
     */
    private Bitmap convertI420ToBitmap(ByteBuffer frameData, int available) {
        try {
            if (available < I420_FRAME_SIZE) {
                Log.w(TAG, "Incomplete I420 frame: expected " + I420_FRAME_SIZE + " bytes, got " + available);
                return null;
            }

            // Make defensive copy - only copy image data, skip telemetry rows if present
            frameData.rewind();
            byte[] frameCopy = new byte[I420_FRAME_SIZE];
            frameData.get(frameCopy);

            // If frame has telemetry (640×514), we already copied only first 491,520 bytes (640×512)
            // The telemetry rows at the end are automatically ignored

            // Create bitmap (640×512)
            Bitmap bitmap = Bitmap.createBitmap(I420_WIDTH, I420_HEIGHT, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[I420_WIDTH * I420_HEIGHT];

            // I420 structure:
            // Y plane: 640×512 bytes (full resolution luminance)
            // U plane: 320×256 bytes (subsampled chrominance)
            // V plane: 320×256 bytes (subsampled chrominance)
            int ySize = I420_WIDTH * I420_HEIGHT;
            int uvSize = (I420_WIDTH / 2) * (I420_HEIGHT / 2);

            // Extract Y plane and apply colormap (ignoring U/V for thermal visualization)
            for (int i = 0; i < pixels.length; i++) {
                if (i >= ySize) {
                    Log.w(TAG, "Y plane underrun at pixel " + i);
                    break;
                }

                // Read Y value from Y plane
                int yValue = frameCopy[i] & 0xFF;

                // Apply thermal colormap to luminance
                pixels[i] = applyThermalColormap(yValue);
            }

            bitmap.setPixels(pixels, 0, I420_WIDTH, 0, 0, I420_WIDTH, I420_HEIGHT);
            frameData.rewind();

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error converting I420 frame", e);
            return null;
        }
    }

    private int applyThermalColormap(int value) {
        // Apply colormap based on current selection
        // Value range: 0-255

        int r, g, b;

        switch (mCurrentColormap) {
            case "iron":
            default:
                // Iron/Hot colormap: Black -> Blue -> Purple -> Red -> Yellow -> White
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
                break;

            case "rainbow":
                // Rainbow colormap: Blue -> Cyan -> Green -> Yellow -> Red
                if (value < 51) {
                    // Blue to Cyan
                    r = 0;
                    g = value * 5;
                    b = 255;
                } else if (value < 102) {
                    // Cyan to Green
                    r = 0;
                    g = 255;
                    b = 255 - ((value - 51) * 5);
                } else if (value < 153) {
                    // Green to Yellow
                    r = (value - 102) * 5;
                    g = 255;
                    b = 0;
                } else if (value < 204) {
                    // Yellow to Orange
                    r = 255;
                    g = 255 - ((value - 153) * 2);
                    b = 0;
                } else {
                    // Orange to Red
                    r = 255;
                    g = 255 - ((value - 204) * 5);
                    b = 0;
                }
                break;

            case "white_hot":
                // White hot colormap: Black -> Gray -> White
                r = value;
                g = value;
                b = value;
                break;

            case "arctic":
                // Arctic colormap: Blue -> Cyan -> White
                if (value < 128) {
                    // Blue to Cyan
                    r = 0;
                    g = value * 2;
                    b = 255;
                } else {
                    // Cyan to White
                    r = (value - 128) * 2;
                    g = 255;
                    b = 255;
                }
                break;

            case "grayscale":
                // Grayscale: Black to White
                r = value;
                g = value;
                b = value;
                break;
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
        for (int i = 0; i < mDetections.size(); i++) {
            Detection det = mDetections.get(i);
            boolean isHighlighted = (i == mCurrentDetectionIndex);
            // Scale bounding box
            Rect scaledBox = new Rect(
                (int) (det.bbox[0] * scaleX),
                (int) (det.bbox[1] * scaleY),
                (int) (det.bbox[2] * scaleX),
                (int) (det.bbox[3] * scaleY)
            );
            
            // Choose color based on confidence and highlight state
            if (isHighlighted) {
                // Highlighted detection has thicker border and cyan color
                boxPaint.setStrokeWidth(6);
                boxPaint.setColor(Color.CYAN);
            } else if (det.confidence > 0.8) {
                boxPaint.setStrokeWidth(3);
                boxPaint.setColor(Color.GREEN);
            } else if (det.confidence > 0.5) {
                boxPaint.setStrokeWidth(3);
                boxPaint.setColor(Color.YELLOW);
            } else {
                boxPaint.setStrokeWidth(3);
                boxPaint.setColor(Color.GRAY);
            }

            canvas.drawRect(scaledBox, boxPaint);
            
            // Draw label with highlight indicator
            String label = isHighlighted ?
                String.format(">>> %s %.2f <<<", det.className, det.confidence) :
                String.format("%s %.2f", det.className, det.confidence);

            if (isHighlighted) {
                textPaint.setTextSize(28);
                textPaint.setColor(Color.CYAN);
            } else {
                textPaint.setTextSize(24);
                textPaint.setColor(Color.WHITE);
            }

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
            Log.i(TAG, "Opening RGB camera (camera 0)...");
            mRgbCamera = android.hardware.Camera.open(0);

            // VALIDATE: Check if camera opened successfully
            if (mRgbCamera == null) {
                Log.e(TAG, "Failed to open RGB camera - returned null");
                Log.e(TAG, "Camera may be in use by another app or hardware unavailable");
                Toast.makeText(this, "RGB camera not available", Toast.LENGTH_SHORT).show();
                mRgbCameraEnabled = false;
                return;
            }

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

            Log.i(TAG, "✓ RGB camera started successfully");

        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to start RGB camera - RuntimeException: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to start RGB camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();

            // Clean up on failure
            if (mRgbCamera != null) {
                try {
                    mRgbCamera.release();
                } catch (Exception ex) {
                    Log.e(TAG, "Error releasing camera on failure", ex);
                }
                mRgbCamera = null;
            }
            mRgbCameraEnabled = false;

        } catch (Exception e) {
            Log.e(TAG, "Failed to start RGB camera: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to start RGB camera", Toast.LENGTH_SHORT).show();
            mRgbCameraEnabled = false;
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

    /**
     * Start RGB camera as fallback when no thermal camera available
     * Displays RGB preview directly on screen
     */
    private void startRgbCameraFallback() {
        // Check camera permission first
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start RGB fallback - camera permission not granted");
            Toast.makeText(this, "Camera permission required for RGB fallback", Toast.LENGTH_LONG).show();
            return;
        }

        // Check if surface is ready
        if (mSurfaceHolder == null) {
            Log.w(TAG, "Cannot start RGB fallback - surface not ready");
            return;
        }

        try {
            // Stop any existing RGB camera first
            if (mRgbCamera != null) {
                Log.i(TAG, "Stopping existing RGB camera before restart");
                stopRgbCamera();
            }

            // Open Glass EE2 built-in camera (usually camera 0)
            Log.i(TAG, "Opening RGB camera (camera 0)...");
            mRgbCamera = android.hardware.Camera.open(0);

            if (mRgbCamera == null) {
                Log.e(TAG, "Failed to open RGB camera - returned null");
                Toast.makeText(this, "RGB camera not available", Toast.LENGTH_LONG).show();
                return;
            }

            android.hardware.Camera.Parameters params = mRgbCamera.getParameters();
            // Set parameters for Glass EE2 camera (640x360 to match display)
            Log.i(TAG, "Setting RGB camera preview size to 640x360");
            params.setPreviewSize(640, 360);
            mRgbCamera.setParameters(params);

            // Set preview display to show on screen
            try {
                Log.i(TAG, "Setting RGB camera preview display");
                mRgbCamera.setPreviewDisplay(mSurfaceHolder);
            } catch (java.io.IOException e) {
                Log.e(TAG, "Failed to set preview display", e);
                mRgbCamera.release();
                mRgbCamera = null;
                Toast.makeText(this, "Failed to set RGB camera display", Toast.LENGTH_LONG).show();
                return;
            }

            Log.i(TAG, "Starting RGB camera preview");
            mRgbCamera.startPreview();
            mRgbCameraEnabled = true;
            mUsingRgbFallback = true;

            // Update UI
            runOnUiThread(() -> {
                mModeIndicator.setText("RGB Camera");
                mCenterTemperature.setText("--°C");
                mFrameCounter.setText("RGB Mode");

                // Dismiss any USB disconnect alerts
                if (mAlertArea != null) {
                    mAlertArea.setVisibility(View.GONE);
                }
            });

            Log.i(TAG, "RGB fallback camera started successfully");
            Toast.makeText(this, "Using RGB camera", Toast.LENGTH_SHORT).show();

        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to start RGB fallback camera - RuntimeException: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to start RGB camera: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // Clean up on failure
            if (mRgbCamera != null) {
                try {
                    mRgbCamera.release();
                } catch (Exception ex) {
                    Log.e(TAG, "Error releasing camera on failure", ex);
                }
                mRgbCamera = null;
            }
            mRgbCameraEnabled = false;
            mUsingRgbFallback = false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start RGB fallback camera - Exception: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to start RGB camera", Toast.LENGTH_LONG).show();
        }
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
    static class ThermalData {
        float centerTemp;
        float minTemp;
        float maxTemp;
        float avgTemp;

        ThermalData(float centerTemp, float minTemp, float maxTemp, float avgTemp) {
            this.centerTemp = centerTemp;
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
            this.avgTemp = avgTemp;
        }
    }

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
