package com.example.thermalarglass;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Settings Activity for Glass AR
 * Configure server URL and preferences via Vysor + remote keyboard
 * All settings compatible with Android API 27 (Glass EE2)
 */
public class SettingsActivity extends Activity {

    private static final String PREF_NAME = "GlassARPrefs";
    private static final String PREF_SERVER_URL = "server_url";
    private static final String PREF_AUTO_CONNECT = "auto_connect";
    private static final String PREF_RGB_FALLBACK = "rgb_fallback";
    private static final String PREF_DEFAULT_COLORMAP = "default_colormap";
    private static final String PREF_FRAME_RATE = "frame_rate";

    private static final String DEFAULT_SERVER_URL = "http://192.168.1.100:8080";

    private EditText mServerUrlInput;
    private CheckBox mAutoConnectCheckbox;
    private CheckBox mRgbFallbackCheckbox;
    private Spinner mColormapSpinner;
    private Spinner mFrameRateSpinner;
    private Button mSaveButton;
    private Button mCancelButton;
    private TextView mCurrentUrlText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize views
        mServerUrlInput = findViewById(R.id.server_url_input);
        mAutoConnectCheckbox = findViewById(R.id.auto_connect_checkbox);
        mRgbFallbackCheckbox = findViewById(R.id.rgb_fallback_checkbox);
        mColormapSpinner = findViewById(R.id.colormap_spinner);
        mFrameRateSpinner = findViewById(R.id.frame_rate_spinner);
        mSaveButton = findViewById(R.id.save_button);
        mCancelButton = findViewById(R.id.cancel_button);
        mCurrentUrlText = findViewById(R.id.current_url_text);

        // Load current settings
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String currentUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL);
        boolean autoConnect = prefs.getBoolean(PREF_AUTO_CONNECT, true);
        boolean rgbFallback = prefs.getBoolean(PREF_RGB_FALLBACK, true);
        String defaultColormap = prefs.getString(PREF_DEFAULT_COLORMAP, "iron");
        int frameRate = prefs.getInt(PREF_FRAME_RATE, 10);

        // Set server URL
        mCurrentUrlText.setText("Current: " + currentUrl);
        mServerUrlInput.setText(currentUrl);
        mServerUrlInput.setSelection(currentUrl.length()); // Cursor at end

        // Set checkboxes
        mAutoConnectCheckbox.setChecked(autoConnect);
        mRgbFallbackCheckbox.setChecked(rgbFallback);

        // Setup colormap spinner
        String[] colormaps = {"iron", "rainbow", "white_hot", "arctic", "grayscale"};
        ArrayAdapter<String> colormapAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, colormaps);
        colormapAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mColormapSpinner.setAdapter(colormapAdapter);

        // Set colormap selection
        int colormapPos = 0;
        for (int i = 0; i < colormaps.length; i++) {
            if (colormaps[i].equals(defaultColormap)) {
                colormapPos = i;
                break;
            }
        }
        mColormapSpinner.setSelection(colormapPos);

        // Setup frame rate spinner
        String[] frameRates = {"5 FPS", "10 FPS", "15 FPS", "20 FPS", "30 FPS"};
        ArrayAdapter<String> frameRateAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, frameRates);
        frameRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mFrameRateSpinner.setAdapter(frameRateAdapter);

        // Set frame rate selection
        int[] fpsValues = {5, 10, 15, 20, 30};
        int fpsPos = 1; // Default to 10 FPS
        for (int i = 0; i < fpsValues.length; i++) {
            if (fpsValues[i] == frameRate) {
                fpsPos = i;
                break;
            }
        }
        mFrameRateSpinner.setSelection(fpsPos);

        // Save button
        mSaveButton.setOnClickListener(v -> saveSettings());

        // Cancel button
        mCancelButton.setOnClickListener(v -> finish());

        // Handle Enter key on keyboard
        mServerUrlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    saveSettings();
                    return true;
                }
                return false;
            }
        });

        // Auto-focus input field
        mServerUrlInput.requestFocus();
    }

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

        // Get all settings
        boolean autoConnect = mAutoConnectCheckbox.isChecked();
        boolean rgbFallback = mRgbFallbackCheckbox.isChecked();
        String colormap = (String) mColormapSpinner.getSelectedItem();
        String frameRateStr = (String) mFrameRateSpinner.getSelectedItem();
        int frameRate = Integer.parseInt(frameRateStr.split(" ")[0]); // Extract number from "10 FPS"

        // Save all to SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_SERVER_URL, newUrl);
        editor.putBoolean(PREF_AUTO_CONNECT, autoConnect);
        editor.putBoolean(PREF_RGB_FALLBACK, rgbFallback);
        editor.putString(PREF_DEFAULT_COLORMAP, colormap);
        editor.putInt(PREF_FRAME_RATE, frameRate);
        editor.apply();

        Toast.makeText(this, "Settings saved!\nRestart app to apply changes", Toast.LENGTH_LONG).show();

        // Close settings
        finish();
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
