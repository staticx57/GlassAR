package com.example.thermalarglass;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Smart display manager for Glass AR annotations
 * Optimized for small display, hands-free use, glanceable UI
 */
public class SmartDisplayManager {

    private static final String TAG = "SmartDisplayManager";

    // Display constraints for Glass EE2
    private static final int GLASS_WIDTH = 640;
    private static final int GLASS_HEIGHT = 360;

    // Center focus area (40% of screen center)
    private static final float CENTER_FOCUS_WIDTH_RATIO = 0.4f;
    private static final float CENTER_FOCUS_HEIGHT_RATIO = 0.4f;

    // Display limits
    private static final int MAX_PRIMARY_OBJECTS = 3;  // Show full details
    private static final int MAX_SECONDARY_OBJECTS = 5;  // Show minimal indicators
    private static final float MIN_CONFIDENCE_THRESHOLD = 0.5f;

    // Priority weights
    private static final float WEIGHT_CENTER_DISTANCE = 0.4f;
    private static final float WEIGHT_CONFIDENCE = 0.3f;
    private static final float WEIGHT_TEMPERATURE = 0.3f;

    // Temperature thresholds for alerts
    private static final float TEMP_CRITICAL = 80.0f;  // °C
    private static final float TEMP_WARNING = 60.0f;   // °C

    private Paint mPrimaryPaint;
    private Paint mSecondaryPaint;
    private Paint mTextPaint;
    private Paint mIconPaint;
    private Paint mAlertPaint;

    private RectF mCenterFocusArea;

    public enum DisplayMode {
        MINIMAL,    // Icons + temps only
        STANDARD,   // Bounding boxes + labels
        DETAILED    // Full annotations with stats
    }

    private DisplayMode mDisplayMode = DisplayMode.STANDARD;

    public static class AnnotatedObject {
        public float[] bbox;           // [x1, y1, x2, y2]
        public float confidence;
        public String className;
        public float temperature;      // Optional
        public boolean isThermalAnomaly;
        public long detectionTime;

        public AnnotatedObject(float[] bbox, float confidence, String className) {
            this.bbox = bbox;
            this.confidence = confidence;
            this.className = className;
            this.temperature = Float.NaN;
            this.isThermalAnomaly = false;
            this.detectionTime = System.currentTimeMillis();
        }

        // Calculate center of bounding box
        public float getCenterX() {
            return (bbox[0] + bbox[2]) / 2.0f;
        }

        public float getCenterY() {
            return (bbox[1] + bbox[3]) / 2.0f;
        }

        // Calculate distance from screen center
        public float getDistanceFromCenter(float centerX, float centerY) {
            float dx = getCenterX() - centerX;
            float dy = getCenterY() - centerY;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }

    public SmartDisplayManager() {
        initPaints();
        initFocusArea();
    }

    private void initPaints() {
        // Primary objects (center focus, high priority)
        mPrimaryPaint = new Paint();
        mPrimaryPaint.setStyle(Paint.Style.STROKE);
        mPrimaryPaint.setStrokeWidth(4);
        mPrimaryPaint.setAntiAlias(true);

        // Secondary objects (peripheral, lower priority)
        mSecondaryPaint = new Paint();
        mSecondaryPaint.setStyle(Paint.Style.STROKE);
        mSecondaryPaint.setStrokeWidth(2);
        mSecondaryPaint.setAntiAlias(true);
        mSecondaryPaint.setAlpha(180);  // Semi-transparent

        // Text labels
        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(20);
        mTextPaint.setColor(Color.WHITE);

        // Alert indicators
        mAlertPaint = new Paint();
        mAlertPaint.setStyle(Paint.Style.FILL);
        mAlertPaint.setAntiAlias(true);
    }

    private void initFocusArea() {
        float centerX = GLASS_WIDTH / 2.0f;
        float centerY = GLASS_HEIGHT / 2.0f;
        float focusWidth = GLASS_WIDTH * CENTER_FOCUS_WIDTH_RATIO;
        float focusHeight = GLASS_HEIGHT * CENTER_FOCUS_HEIGHT_RATIO;

        mCenterFocusArea = new RectF(
            centerX - focusWidth / 2,
            centerY - focusHeight / 2,
            centerX + focusWidth / 2,
            centerY + focusHeight / 2
        );
    }

    /**
     * Set display mode
     */
    public void setDisplayMode(DisplayMode mode) {
        mDisplayMode = mode;
    }

    /**
     * Draw annotations intelligently on Glass display
     */
    public void drawAnnotations(Canvas canvas, List<AnnotatedObject> objects, float scaleX, float scaleY) {
        if (objects == null || objects.isEmpty()) {
            return;
        }

        // Filter by confidence
        List<AnnotatedObject> filtered = filterByConfidence(objects);

        // Prioritize objects
        List<AnnotatedObject> prioritized = prioritizeObjects(filtered);

        // Draw based on mode
        switch (mDisplayMode) {
            case MINIMAL:
                drawMinimalAnnotations(canvas, prioritized, scaleX, scaleY);
                break;
            case STANDARD:
                drawStandardAnnotations(canvas, prioritized, scaleX, scaleY);
                break;
            case DETAILED:
                drawDetailedAnnotations(canvas, prioritized, scaleX, scaleY);
                break;
        }
    }

    /**
     * Filter objects by confidence threshold
     */
    private List<AnnotatedObject> filterByConfidence(List<AnnotatedObject> objects) {
        List<AnnotatedObject> filtered = new ArrayList<>();
        for (AnnotatedObject obj : objects) {
            if (obj.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                filtered.add(obj);
            }
        }
        return filtered;
    }

    /**
     * Prioritize objects based on multiple factors:
     * - Distance from center (what user is looking at)
     * - Confidence score
     * - Temperature (thermal anomalies)
     */
    private List<AnnotatedObject> prioritizeObjects(List<AnnotatedObject> objects) {
        float centerX = GLASS_WIDTH / 2.0f;
        float centerY = GLASS_HEIGHT / 2.0f;

        // Calculate priority score for each object
        for (AnnotatedObject obj : objects) {
            float distScore = calculateDistanceScore(obj, centerX, centerY);
            float confScore = obj.confidence;
            float tempScore = calculateTemperatureScore(obj);

            obj.detectionTime = (long) (
                distScore * WEIGHT_CENTER_DISTANCE +
                confScore * WEIGHT_CONFIDENCE +
                tempScore * WEIGHT_TEMPERATURE
            );  // Reusing detectionTime field for priority score
        }

        // Sort by priority (higher score = higher priority)
        Collections.sort(objects, new Comparator<AnnotatedObject>() {
            @Override
            public int compare(AnnotatedObject o1, AnnotatedObject o2) {
                return Long.compare(o2.detectionTime, o1.detectionTime);
            }
        });

        return objects;
    }

    /**
     * Calculate distance score (closer to center = higher score)
     */
    private float calculateDistanceScore(AnnotatedObject obj, float centerX, float centerY) {
        float distance = obj.getDistanceFromCenter(centerX, centerY);
        float maxDistance = (float) Math.sqrt(centerX * centerX + centerY * centerY);
        return 1.0f - (distance / maxDistance);  // Normalize: 1.0 at center, 0.0 at corners
    }

    /**
     * Calculate temperature score (hotter = higher priority)
     */
    private float calculateTemperatureScore(AnnotatedObject obj) {
        if (Float.isNaN(obj.temperature)) {
            return 0.0f;
        }

        // Normalize temperature: 0-100°C → 0.0-1.0
        float normalized = Math.min(obj.temperature / 100.0f, 1.0f);

        // Boost critical temperatures
        if (obj.temperature >= TEMP_CRITICAL) {
            return 1.0f;
        } else if (obj.temperature >= TEMP_WARNING) {
            return 0.7f + normalized * 0.3f;
        }

        return normalized;
    }

    /**
     * Draw minimal annotations (icons + temps only)
     */
    private void drawMinimalAnnotations(Canvas canvas, List<AnnotatedObject> objects, float scaleX, float scaleY) {
        int count = 0;

        for (AnnotatedObject obj : objects) {
            if (count >= MAX_PRIMARY_OBJECTS) break;

            Rect scaledBox = scaleBox(obj.bbox, scaleX, scaleY);

            // Draw small indicator dot at object center
            float centerX = scaledBox.centerX();
            float centerY = scaledBox.centerY();

            Paint indicatorPaint = getIndicatorPaint(obj);
            canvas.drawCircle(centerX, centerY, 8, indicatorPaint);

            // Draw temperature if available
            if (!Float.isNaN(obj.temperature)) {
                String tempLabel = String.format("%.0f°", obj.temperature);
                mTextPaint.setTextSize(16);
                canvas.drawText(tempLabel, centerX + 12, centerY + 5, mTextPaint);
            }

            count++;
        }
    }

    /**
     * Draw standard annotations (bounding boxes + labels)
     */
    private void drawStandardAnnotations(Canvas canvas, List<AnnotatedObject> objects, float scaleX, float scaleY) {
        int primaryCount = 0;
        int secondaryCount = 0;

        for (AnnotatedObject obj : objects) {
            Rect scaledBox = scaleBox(obj.bbox, scaleX, scaleY);
            boolean isPrimary = primaryCount < MAX_PRIMARY_OBJECTS;

            if (isPrimary) {
                // Primary object: Full details
                drawPrimaryObject(canvas, obj, scaledBox);
                primaryCount++;
            } else if (secondaryCount < MAX_SECONDARY_OBJECTS) {
                // Secondary object: Minimal indicator
                drawSecondaryObject(canvas, obj, scaledBox);
                secondaryCount++;
            } else {
                // Limit reached
                break;
            }
        }
    }

    /**
     * Draw detailed annotations (full stats)
     */
    private void drawDetailedAnnotations(Canvas canvas, List<AnnotatedObject> objects, float scaleX, float scaleY) {
        // Similar to standard but with more info
        drawStandardAnnotations(canvas, objects, scaleX, scaleY);

        // Add HUD overlay with stats
        drawHUDOverlay(canvas, objects);
    }

    /**
     * Draw primary object with full details
     */
    private void drawPrimaryObject(Canvas canvas, AnnotatedObject obj, Rect box) {
        // Choose color based on temperature and confidence
        Paint paint = getPrimaryPaint(obj);
        canvas.drawRect(box, paint);

        // Draw label with temperature
        String label;
        if (!Float.isNaN(obj.temperature)) {
            label = String.format("%s %.0f°C", obj.className, obj.temperature);
        } else {
            label = String.format("%s %.0f%%", obj.className, obj.confidence * 100);
        }

        mTextPaint.setTextSize(22);
        mTextPaint.setColor(Color.WHITE);

        // Draw label background for readability
        float textWidth = mTextPaint.measureText(label);
        canvas.drawRect(box.left, box.top - 30, box.left + textWidth + 10, box.top, mAlertPaint);

        canvas.drawText(label, box.left + 5, box.top - 8, mTextPaint);

        // Draw alert indicator for critical temperatures
        if (!Float.isNaN(obj.temperature) && obj.temperature >= TEMP_CRITICAL) {
            drawAlertIndicator(canvas, box);
        }
    }

    /**
     * Draw secondary object with minimal UI
     */
    private void drawSecondaryObject(Canvas canvas, AnnotatedObject obj, Rect box) {
        mSecondaryPaint.setColor(getObjectColor(obj));
        canvas.drawRect(box, mSecondaryPaint);

        // Minimal label (icon or first letter)
        String shortLabel = obj.className.substring(0, 1).toUpperCase();
        mTextPaint.setTextSize(16);
        canvas.drawText(shortLabel, box.left + 5, box.top + 20, mTextPaint);
    }

    /**
     * Draw HUD overlay with statistics
     */
    private void drawHUDOverlay(Canvas canvas, List<AnnotatedObject> objects) {
        // Top-right corner stats
        int x = GLASS_WIDTH - 150;
        int y = 30;

        mTextPaint.setTextSize(18);
        mTextPaint.setColor(Color.CYAN);

        canvas.drawText("Objects: " + objects.size(), x, y, mTextPaint);

        // Count by type
        int hotCount = 0;
        for (AnnotatedObject obj : objects) {
            if (!Float.isNaN(obj.temperature) && obj.temperature >= TEMP_WARNING) {
                hotCount++;
            }
        }

        if (hotCount > 0) {
            canvas.drawText("⚠ Hot: " + hotCount, x, y + 25, mTextPaint);
        }
    }

    /**
     * Draw alert indicator for critical objects
     */
    private void drawAlertIndicator(Canvas canvas, Rect box) {
        // Pulsing red circle at top-right of box
        mAlertPaint.setColor(Color.RED);
        canvas.drawCircle(box.right - 10, box.top + 10, 8, mAlertPaint);
    }

    /**
     * Get paint for primary object based on properties
     */
    private Paint getPrimaryPaint(AnnotatedObject obj) {
        mPrimaryPaint.setColor(getObjectColor(obj));
        return mPrimaryPaint;
    }

    /**
     * Get paint for indicator dots
     */
    private Paint getIndicatorPaint(AnnotatedObject obj) {
        mAlertPaint.setColor(getObjectColor(obj));
        return mAlertPaint;
    }

    /**
     * Get color based on object properties
     */
    private int getObjectColor(AnnotatedObject obj) {
        // Priority: Temperature > Confidence
        if (!Float.isNaN(obj.temperature)) {
            if (obj.temperature >= TEMP_CRITICAL) {
                return Color.RED;  // Critical
            } else if (obj.temperature >= TEMP_WARNING) {
                return Color.rgb(255, 165, 0);  // Orange warning
            } else {
                return Color.YELLOW;  // Warm
            }
        }

        // Confidence-based coloring
        if (obj.confidence > 0.8) {
            return Color.GREEN;
        } else if (obj.confidence > 0.6) {
            return Color.YELLOW;
        } else {
            return Color.GRAY;
        }
    }

    /**
     * Scale bounding box to display coordinates
     */
    private Rect scaleBox(float[] bbox, float scaleX, float scaleY) {
        return new Rect(
            (int) (bbox[0] * scaleX),
            (int) (bbox[1] * scaleY),
            (int) (bbox[2] * scaleX),
            (int) (bbox[3] * scaleY)
        );
    }

    /**
     * Check if object is in center focus area
     */
    public boolean isInCenterFocus(AnnotatedObject obj) {
        float centerX = obj.getCenterX();
        float centerY = obj.getCenterY();
        return mCenterFocusArea.contains(centerX, centerY);
    }

    /**
     * Get primary (high priority) objects for audio alerts
     */
    public List<AnnotatedObject> getPrimaryObjects(List<AnnotatedObject> objects) {
        List<AnnotatedObject> prioritized = prioritizeObjects(filterByConfidence(objects));
        return prioritized.subList(0, Math.min(MAX_PRIMARY_OBJECTS, prioritized.size()));
    }
}
