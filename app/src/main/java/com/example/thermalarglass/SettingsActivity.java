package com.example.thermalarglass;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Enhanced Settings Activity for Glass AR
 * Comprehensive configuration with Vysor + remote keyboard support
 * Includes network testing, system info, and immediate setting application
 * All features compatible with Android API 27 (Glass EE2)
 */
public class SettingsActivity extends Activity {

    private static final String TAG = "SettingsActivity";
    private static final String PREF_NAME = "GlassARPrefs";
    private static final String PREF_SERVER_URL = "server_url";
    private static final String PREF_AUTO_CONNECT = "auto_connect";
    private static final String PREF_RGB_FALLBACK = "rgb_fallback";
    private static final String PREF_DEFAULT_COLORMAP = "default_colormap";
    private static final String PREF_FRAME_RATE = "frame_rate";
    private static final String PREF_HAPTIC_FEEDBACK = "haptic_feedback";
    private static final String PREF_AUDIO_FEEDBACK = "audio_feedback";
    private static final String PREF_TEMP_UNIT = "temperature_unit";
    private static final String PREF_RECORDING_INTERVAL = "recording_interval";
    private static final String PREF_BATTERY_ALERT = "battery_alert_threshold";
    private static final String PREF_DETECTION_CONFIDENCE = "detection_confidence";

    private static final String DEFAULT_SERVER_URL = "http://192.168.1.100:8080";

    // UI Controls
    private EditText mServerUrlInput;
    private CheckBox mAutoConnectCheckbox;
    private CheckBox mRgbFallbackCheckbox;
    private CheckBox mHapticFeedbackCheckbox;
    private CheckBox mAudioFeedbackCheckbox;
    private Spinner mColormapSpinner;
    private Spinner mFrameRateSpinner;
    private Spinner mTempUnitSpinner;
    private Spinner mRecordingIntervalSpinner;
    private Spinner mBatteryAlertSpinner;
    private Spinner mDetectionConfidenceSpinner;
    private Button mSaveButton;
    private Button mCancelButton;
    private Button mTestConnectionButton;
    private Button mResetDefaultsButton;
    private TextView mCurrentUrlText;
    private TextView mSystemInfoText;
    private TextView mNetworkInfoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Log.i(TAG, "Settings activity opened");

        // Initialize all views
        initializeViews();

        // Load current settings
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String currentUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL);
        boolean autoConnect = prefs.getBoolean(PREF_AUTO_CONNECT, true);
        boolean rgbFallback = prefs.getBoolean(PREF_RGB_FALLBACK, true);
        boolean hapticFeedback = prefs.getBoolean(PREF_HAPTIC_FEEDBACK, true);
        boolean audioFeedback = prefs.getBoolean(PREF_AUDIO_FEEDBACK, true);
        String defaultColormap = prefs.getString(PREF_DEFAULT_COLORMAP, "iron");
        int frameRate = prefs.getInt(PREF_FRAME_RATE, 10);
        String tempUnit = prefs.getString(PREF_TEMP_UNIT, "celsius");
        int recordingInterval = prefs.getInt(PREF_RECORDING_INTERVAL, 3);
        int batteryAlert = prefs.getInt(PREF_BATTERY_ALERT, 20);
        float detectionConfidence = prefs.getFloat(PREF_DETECTION_CONFIDENCE, 0.5f);

        // Set server URL
        mCurrentUrlText.setText("Current: " + currentUrl);
        mServerUrlInput.setText(currentUrl);
        mServerUrlInput.setSelection(currentUrl.length()); // Cursor at end

        // Set checkboxes
        mAutoConnectCheckbox.setChecked(autoConnect);
        mRgbFallbackCheckbox.setChecked(rgbFallback);
        mHapticFeedbackCheckbox.setChecked(hapticFeedback);
        mAudioFeedbackCheckbox.setChecked(audioFeedback);

        // Setup all spinners
        setupSpinners(defaultColormap, frameRate, tempUnit, recordingInterval, batteryAlert, detectionConfidence);

        // Setup button handlers
        mSaveButton.setOnClickListener(v -> saveSettings());
        mCancelButton.setOnClickListener(v -> finish());
        mTestConnectionButton.setOnClickListener(v -> testConnection());
        mResetDefaultsButton.setOnClickListener(v -> resetToDefaults());

        // Handle Enter key on keyboard
        mServerUrlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                saveSettings();
                return true;
            }
            return false;
        });

        // Display system and network info
        displaySystemInfo();
        displayNetworkInfo();

        // Auto-focus input field
        mServerUrlInput.requestFocus();

        Log.i(TAG, "Settings initialized successfully");
    }

    /**
     * Initialize all UI views
     */
    private void initializeViews() {
        mServerUrlInput = findViewById(R.id.server_url_input);
        mAutoConnectCheckbox = findViewById(R.id.auto_connect_checkbox);
        mRgbFallbackCheckbox = findViewById(R.id.rgb_fallback_checkbox);
        mHapticFeedbackCheckbox = findViewById(R.id.haptic_feedback_checkbox);
        mAudioFeedbackCheckbox = findViewById(R.id.audio_feedback_checkbox);
        mColormapSpinner = findViewById(R.id.colormap_spinner);
        mFrameRateSpinner = findViewById(R.id.frame_rate_spinner);
        mTempUnitSpinner = findViewById(R.id.temp_unit_spinner);
        mRecordingIntervalSpinner = findViewById(R.id.recording_interval_spinner);
        mBatteryAlertSpinner = findViewById(R.id.battery_alert_spinner);
        mDetectionConfidenceSpinner = findViewById(R.id.detection_confidence_spinner);
        mSaveButton = findViewById(R.id.save_button);
        mCancelButton = findViewById(R.id.cancel_button);
        mTestConnectionButton = findViewById(R.id.test_connection_button);
        mResetDefaultsButton = findViewById(R.id.reset_defaults_button);
        mCurrentUrlText = findViewById(R.id.current_url_text);
        mSystemInfoText = findViewById(R.id.system_info_text);
        mNetworkInfoText = findViewById(R.id.network_info_text);
    }

    /**
     * Setup all spinner adapters and selections
     */
    private void setupSpinners(String defaultColormap, int frameRate, String tempUnit,
                               int recordingInterval, int batteryAlert, float detectionConfidence) {
        // Colormap spinner
        String[] colormaps = {"iron", "rainbow", "white_hot", "arctic", "grayscale"};
        setupSpinner(mColormapSpinner, colormaps, findIndex(colormaps, defaultColormap));

        // Frame rate spinner
        String[] frameRates = {"5 FPS", "10 FPS", "15 FPS", "20 FPS", "30 FPS"};
        int[] fpsValues = {5, 10, 15, 20, 30};
        setupSpinner(mFrameRateSpinner, frameRates, findIndex(fpsValues, frameRate));

        // Temperature unit spinner
        String[] tempUnits = {"Celsius (°C)", "Fahrenheit (°F)"};
        setupSpinner(mTempUnitSpinner, tempUnits, tempUnit.equals("fahrenheit") ? 1 : 0);

        // Recording interval spinner
        String[] recordingIntervals = {"Every frame (30 fps)", "Every 2nd (15 fps)", "Every 3rd (10 fps)", "Every 5th (6 fps)", "Every 10th (3 fps)"};
        int[] intervalValues = {1, 2, 3, 5, 10};
        setupSpinner(mRecordingIntervalSpinner, recordingIntervals, findIndex(intervalValues, recordingInterval));

        // Battery alert spinner
        String[] batteryAlerts = {"10%", "15%", "20%", "25%", "30%", "Disabled"};
        int[] batteryValues = {10, 15, 20, 25, 30, 0};
        setupSpinner(mBatteryAlertSpinner, batteryAlerts, findIndex(batteryValues, batteryAlert));

        // Detection confidence spinner
        String[] confidenceLevels = {"Low (30%)", "Medium-Low (40%)", "Medium (50%)", "Medium-High (60%)", "High (70%)", "Very High (80%)"};
        float[] confidenceValues = {0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f};
        setupSpinner(mDetectionConfidenceSpinner, confidenceLevels, findIndex(confidenceValues, detectionConfidence));
    }

    /**
     * Helper to setup spinner with adapter
     */
    private void setupSpinner(Spinner spinner, String[] items, int selectedIndex) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedIndex);
    }

    /**
     * Find index of value in array
     */
    private int findIndex(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return 0;
    }

    private int findIndex(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) return i;
        }
        return 0;
    }

    private int findIndex(float[] array, float value) {
        for (int i = 0; i < array.length; i++) {
            if (Math.abs(array[i] - value) < 0.01f) return i;
        }
        return 0;
    }

    /**
     * Save all settings to SharedPreferences
     */
    private void saveSettings() {
        String newUrl = mServerUrlInput.getText().toString().trim();

        // Validate URL
        if (newUrl.isEmpty()) {
            Toast.makeText(this, "Server URL cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
            Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_LONG).show();
            return;
        }

        // Validate IP and port format
        if (!validateServerUrl(newUrl)) {
            return;
        }

        // Get all settings
        boolean autoConnect = mAutoConnectCheckbox.isChecked();
        boolean rgbFallback = mRgbFallbackCheckbox.isChecked();
        boolean hapticFeedback = mHapticFeedbackCheckbox.isChecked();
        boolean audioFeedback = mAudioFeedbackCheckbox.isChecked();
        String colormap = (String) mColormapSpinner.getSelectedItem();
        String frameRateStr = (String) mFrameRateSpinner.getSelectedItem();
        int frameRate = Integer.parseInt(frameRateStr.split(" ")[0]); // Extract number from "10 FPS"

        // Temperature unit
        String tempUnitDisplay = (String) mTempUnitSpinner.getSelectedItem();
        String tempUnit = tempUnitDisplay.contains("Fahrenheit") ? "fahrenheit" : "celsius";

        // Recording interval
        String recordingIntervalStr = (String) mRecordingIntervalSpinner.getSelectedItem();
        int[] intervalValues = {1, 2, 3, 5, 10};
        int recordingInterval = intervalValues[mRecordingIntervalSpinner.getSelectedItemPosition()];

        // Battery alert
        String batteryAlertStr = (String) mBatteryAlertSpinner.getSelectedItem();
        int batteryAlert = batteryAlertStr.equals("Disabled") ? 0 : Integer.parseInt(batteryAlertStr.replace("%", ""));

        // Detection confidence
        float[] confidenceValues = {0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f};
        float detectionConfidence = confidenceValues[mDetectionConfidenceSpinner.getSelectedItemPosition()];

        // Save all to SharedPreferences and VALIDATE
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_SERVER_URL, newUrl);
        editor.putBoolean(PREF_AUTO_CONNECT, autoConnect);
        editor.putBoolean(PREF_RGB_FALLBACK, rgbFallback);
        editor.putBoolean(PREF_HAPTIC_FEEDBACK, hapticFeedback);
        editor.putBoolean(PREF_AUDIO_FEEDBACK, audioFeedback);
        editor.putString(PREF_DEFAULT_COLORMAP, colormap);
        editor.putInt(PREF_FRAME_RATE, frameRate);
        editor.putString(PREF_TEMP_UNIT, tempUnit);
        editor.putInt(PREF_RECORDING_INTERVAL, recordingInterval);
        editor.putInt(PREF_BATTERY_ALERT, batteryAlert);
        editor.putFloat(PREF_DETECTION_CONFIDENCE, detectionConfidence);

        // VALIDATE: Use commit() to verify settings were saved
        boolean saved = editor.commit();
        if (!saved) {
            Log.e(TAG, "FAILED to save settings to SharedPreferences");
            Log.e(TAG, "Check storage permissions and available space");
            Toast.makeText(this,
                "ERROR: Failed to save settings - check storage permissions",
                Toast.LENGTH_LONG).show();
            return;
        }

        Log.i(TAG, "✓ Settings saved successfully: URL=" + newUrl + ", Colormap=" + colormap +
              ", FPS=" + frameRate + ", TempUnit=" + tempUnit + ", RecInterval=" + recordingInterval);

        Toast.makeText(this, "✓ Settings saved successfully!\nMost changes apply immediately", Toast.LENGTH_LONG).show();

        // Close settings
        finish();
    }

    /**
     * Validate server URL format
     */
    private boolean validateServerUrl(String url) {
        try {
            // Remove protocol
            String urlWithoutProtocol = url.replace("http://", "").replace("https://", "");

            // Check for port
            if (!urlWithoutProtocol.contains(":")) {
                Toast.makeText(this, "URL must include port (e.g., :8080)", Toast.LENGTH_LONG).show();
                return false;
            }

            // Split IP and port
            String[] parts = urlWithoutProtocol.split(":");
            String ip = parts[0];
            String portStr = parts[1];

            // Validate port
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                Toast.makeText(this, "Port must be between 1-65535", Toast.LENGTH_LONG).show();
                return false;
            }

            // Validate IP format (basic check)
            String[] ipParts = ip.split("\\.");
            if (ipParts.length != 4) {
                Toast.makeText(this, "Invalid IP address format", Toast.LENGTH_LONG).show();
                return false;
            }

            for (String part : ipParts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    Toast.makeText(this, "Invalid IP address (0-255 per octet)", Toast.LENGTH_LONG).show();
                    return false;
                }
            }

            return true;

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format in URL", Toast.LENGTH_LONG).show();
            return false;
        } catch (Exception e) {
            Toast.makeText(this, "Invalid URL format", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /**
     * Test connection to server
     */
    private void testConnection() {
        String url = mServerUrlInput.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, "Enter server URL first", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show();
        mTestConnectionButton.setEnabled(false);

        new Thread(() -> {
            String result;
            boolean success = false;

            try {
                URL serverUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) serverUrl.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                conn.disconnect();

                // VALIDATE: Only HTTP 200 is true success
                if (responseCode == 200) {
                    result = "✓ Connection successful!\nServer responded: HTTP 200 OK";
                    success = true;
                } else if (responseCode == 404) {
                    result = "⚠ Server reachable but endpoint not found (HTTP 404)\n" +
                            "Server may not be running or configured correctly";
                    success = false;  // NOT a success - endpoint doesn't exist
                } else if (responseCode >= 500) {
                    result = "✗ Server error: HTTP " + responseCode + "\n" +
                            "Server is having internal issues";
                    success = false;
                } else if (responseCode >= 400) {
                    result = "✗ Client error: HTTP " + responseCode + "\n" +
                            "Check server URL and configuration";
                    success = false;
                } else {
                    result = "⚠ Unexpected response: HTTP " + responseCode + "\n" +
                            "Server may not be configured correctly";
                    success = false;
                }

            } catch (IOException e) {
                result = "✗ Connection failed\n" + e.getMessage();
                Log.e(TAG, "Connection test failed", e);
            }

            final String finalResult = result;
            final boolean finalSuccess = success;

            runOnUiThread(() -> {
                Toast.makeText(this, finalResult, Toast.LENGTH_LONG).show();
                mTestConnectionButton.setEnabled(true);

                if (finalSuccess) {
                    mTestConnectionButton.setText("✓ Test Passed");
                    new Handler(Looper.getMainLooper()).postDelayed(() ->
                        mTestConnectionButton.setText("Test Connection"), 3000);
                }
            });

        }).start();
    }

    /**
     * Reset all settings to default values
     */
    private void resetToDefaults() {
        Log.i(TAG, "Resetting settings to defaults");

        // Reset UI controls
        mServerUrlInput.setText(DEFAULT_SERVER_URL);
        mAutoConnectCheckbox.setChecked(true);
        mRgbFallbackCheckbox.setChecked(true);
        mHapticFeedbackCheckbox.setChecked(true);
        mAudioFeedbackCheckbox.setChecked(true);

        // Reset spinners to defaults
        mColormapSpinner.setSelection(0); // iron
        mFrameRateSpinner.setSelection(1); // 10 FPS
        mTempUnitSpinner.setSelection(0); // Celsius
        mRecordingIntervalSpinner.setSelection(2); // Every 3rd frame
        mBatteryAlertSpinner.setSelection(2); // 20%
        mDetectionConfidenceSpinner.setSelection(2); // 50%

        mCurrentUrlText.setText("Current: " + DEFAULT_SERVER_URL);

        Toast.makeText(this, "✓ Reset to defaults\nDon't forget to Save!", Toast.LENGTH_LONG).show();
        Log.i(TAG, "Settings reset to defaults");
    }

    /**
     * Display system information
     */
    private void displaySystemInfo() {
        try {
            // Get app version
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;

            // Get battery level
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            int batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

            // Format system info
            String info = String.format(Locale.US,
                "App Version: %s\n" +
                "Android API: 27 (8.1 Oreo)\n" +
                "Device: Google Glass EE2\n" +
                "Battery: %d%%",
                version, batteryLevel);

            mSystemInfoText.setText(info);

        } catch (PackageManager.NameNotFoundException e) {
            mSystemInfoText.setText("System info unavailable");
            Log.e(TAG, "Failed to get system info", e);
        }
    }

    /**
     * Display network information
     */
    private void displayNetworkInfo() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnected()) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wm.getConnectionInfo();
                    String ssid = wifiInfo.getSSID().replace("\"", "");
                    int ip = wifiInfo.getIpAddress();
                    String ipAddress = Formatter.formatIpAddress(ip);

                    String info = String.format(Locale.US,
                        "✓ WiFi Connected\n" +
                        "SSID: %s\n" +
                        "Glass IP: %s",
                        ssid, ipAddress);

                    mNetworkInfoText.setText(info);
                } else {
                    mNetworkInfoText.setText("✓ Network connected (non-WiFi)");
                }
            } else {
                mNetworkInfoText.setText("✗ No network connection\nConnect to WiFi for server features");
            }

        } catch (Exception e) {
            mNetworkInfoText.setText("Network info unavailable");
            Log.e(TAG, "Failed to get network info", e);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle Glass camera button as back/cancel
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
