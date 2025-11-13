package com.example.thermalarglass;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native UVC (USB Video Class) Camera implementation using Android's native USB APIs
 * Supports FLIR Boson 320 and other UVC-compliant cameras
 */
public class NativeUVCCamera {

    private static final String TAG = "NativeUVCCamera";

    // UVC Constants
    private static final int UVC_CLASS = 14;  // Video class
    private static final int UVC_SUBCLASS_VIDEOCONTROL = 1;
    private static final int UVC_SUBCLASS_VIDEOSTREAMING = 2;

    // USB Request Type Recipients (not available in UsbConstants for API 27)
    private static final int USB_RECIP_INTERFACE = 0x01;

    // UVC Control Requests
    private static final int UVC_SET_CUR = 0x01;
    private static final int UVC_GET_CUR = 0x81;
    private static final int UVC_GET_MIN = 0x82;
    private static final int UVC_GET_MAX = 0x83;
    private static final int UVC_GET_RES = 0x84;

    // UVC Video Streaming Interface Control Selectors
    private static final int VS_PROBE_CONTROL = 0x01;
    private static final int VS_COMMIT_CONTROL = 0x02;

    private Context mContext;
    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private UsbInterface mVideoStreamingInterface;
    private UsbEndpoint mStreamingEndpoint;

    private ExecutorService mExecutor;
    private AtomicBoolean mStreaming = new AtomicBoolean(false);

    private IFrameCallback mFrameCallback;

    private int mWidth = 320;
    private int mHeight = 256;

    public interface IFrameCallback {
        void onFrame(ByteBuffer frame);
    }

    public NativeUVCCamera(Context context) {
        mContext = context;
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    /**
     * Opens the USB device and prepares for video streaming
     */
    public boolean open(UsbDevice device) {
        mDevice = device;

        try {
            // Open connection to device
            mConnection = mUsbManager.openDevice(mDevice);
            if (mConnection == null) {
                Log.e(TAG, "Failed to open USB connection");
                return false;
            }

            // Find video streaming interface
            mVideoStreamingInterface = findVideoStreamingInterface(mDevice);
            if (mVideoStreamingInterface == null) {
                Log.e(TAG, "No video streaming interface found");
                return false;
            }

            // Claim interface
            boolean claimed = mConnection.claimInterface(mVideoStreamingInterface, true);
            if (!claimed) {
                Log.e(TAG, "Failed to claim video streaming interface");
                return false;
            }

            // Find streaming endpoint (usually isochronous or bulk IN)
            mStreamingEndpoint = findStreamingEndpoint(mVideoStreamingInterface);
            if (mStreamingEndpoint == null) {
                Log.e(TAG, "No streaming endpoint found");
                return false;
            }

            Log.i(TAG, "Successfully opened UVC camera: " + device.getDeviceName());
            Log.i(TAG, "Endpoint type: " + getEndpointTypeString(mStreamingEndpoint.getType()));
            Log.i(TAG, "Max packet size: " + mStreamingEndpoint.getMaxPacketSize());

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            return false;
        }
    }

    /**
     * Sets the preview size for video streaming
     */
    public boolean setPreviewSize(int width, int height) {
        mWidth = width;
        mHeight = height;

        // Negotiate format with camera using UVC probe/commit protocol
        return negotiateFormat(width, height);
    }

    /**
     * Sets the frame callback to receive video frames
     */
    public void setFrameCallback(IFrameCallback callback) {
        mFrameCallback = callback;
    }

    /**
     * Starts video streaming
     */
    public boolean startPreview() {
        if (mConnection == null || mStreamingEndpoint == null) {
            Log.e(TAG, "Camera not opened");
            return false;
        }

        mStreaming.set(true);

        // Start streaming thread
        mExecutor = Executors.newSingleThreadExecutor();
        mExecutor.submit(this::streamingLoop);

        Log.i(TAG, "Video streaming started");
        return true;
    }

    /**
     * Stops video streaming
     */
    public void stopPreview() {
        mStreaming.set(false);

        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }

        Log.i(TAG, "Video streaming stopped");
    }

    /**
     * Closes the camera and releases resources
     */
    public void close() {
        stopPreview();

        if (mConnection != null) {
            if (mVideoStreamingInterface != null) {
                mConnection.releaseInterface(mVideoStreamingInterface);
            }
            mConnection.close();
            mConnection = null;
        }

        mDevice = null;
        mVideoStreamingInterface = null;
        mStreamingEndpoint = null;

        Log.i(TAG, "Camera closed");
    }

    /**
     * Finds the video streaming interface in the USB device
     */
    private UsbInterface findVideoStreamingInterface(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);

            // Look for video class with streaming subclass
            if (intf.getInterfaceClass() == UVC_CLASS &&
                intf.getInterfaceSubclass() == UVC_SUBCLASS_VIDEOSTREAMING) {
                return intf;
            }
        }
        return null;
    }

    /**
     * Finds the streaming endpoint (isochronous or bulk IN)
     */
    private UsbEndpoint findStreamingEndpoint(UsbInterface intf) {
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint endpoint = intf.getEndpoint(i);

            // Look for IN endpoint (device to host)
            if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                // Prefer isochronous, but bulk also works
                int type = endpoint.getType();
                if (type == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                    type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    return endpoint;
                }
            }
        }
        return null;
    }

    /**
     * Negotiates video format with camera using UVC probe/commit protocol
     */
    private boolean negotiateFormat(int width, int height) {
        if (mConnection == null) {
            Log.e(TAG, "Cannot negotiate format - no USB connection");
            return false;
        }

        try {
            // Find video control interface
            UsbInterface controlInterface = findVideoControlInterface(mDevice);
            if (controlInterface == null) {
                Log.w(TAG, "No video control interface found - basic UVC cameras may still work");
                // Only proceed if we have streaming endpoint (fallback mode)
                if (mStreamingEndpoint == null) {
                    Log.e(TAG, "No streaming endpoint available - cannot proceed");
                    return false;
                }
                Log.i(TAG, "Attempting to stream without format negotiation (basic mode)");
                return true;  // Allow basic cameras without control interface
            }

            // Build probe control structure with requested parameters
            byte[] probeData = buildProbeData(width, height);

            Log.i(TAG, "Negotiating format with camera for " + width + "x" + height);

            // Step 1: SET_CUR(PROBE) - Tell camera what we want
            int result = mConnection.controlTransfer(
                UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE,
                UVC_SET_CUR,
                VS_PROBE_CONTROL << 8,
                mVideoStreamingInterface.getId(),
                probeData,
                probeData.length,
                5000
            );

            if (result < 0) {
                Log.e(TAG, "SET_CUR(PROBE) FAILED: " + result);
                Log.e(TAG, "Camera rejected format negotiation - incompatible format or camera malfunction");
                return false;  // FAIL HARD - negotiation is critical
            }
            Log.d(TAG, "SET_CUR(PROBE) sent: " + result + " bytes");

            // Step 2: GET_CUR(PROBE) - Read back what camera will actually provide
            byte[] responseData = new byte[26];
            result = mConnection.controlTransfer(
                UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE,
                UVC_GET_CUR,
                VS_PROBE_CONTROL << 8,
                mVideoStreamingInterface.getId(),
                responseData,
                responseData.length,
                5000
            );

            if (result > 0) {
                Log.d(TAG, "GET_CUR(PROBE) received: " + result + " bytes");

                // Parse and VALIDATE camera's response
                int formatIndex = responseData[2] & 0xFF;
                int frameIndex = responseData[3] & 0xFF;
                int frameSize = ((responseData[21] & 0xFF) << 24) |
                               ((responseData[20] & 0xFF) << 16) |
                               ((responseData[19] & 0xFF) << 8) |
                               (responseData[18] & 0xFF);

                Log.i(TAG, "Camera response: formatIndex=" + formatIndex +
                          ", frameIndex=" + frameIndex +
                          ", frameSize=" + frameSize + " bytes");

                // VALIDATE: Check if frame size makes sense
                int expectedMinSize = width * height;  // Minimum for any format
                int expectedMaxSize = width * height * 4;  // Maximum (RGBA)

                if (frameSize < expectedMinSize || frameSize > expectedMaxSize) {
                    Log.e(TAG, "VALIDATION FAILED: Frame size " + frameSize +
                              " outside expected range [" + expectedMinSize + "-" + expectedMaxSize + "]");
                    Log.e(TAG, "Camera may not support requested resolution");
                    return false;
                }
            } else {
                Log.e(TAG, "GET_CUR(PROBE) FAILED: " + result);
                Log.e(TAG, "Cannot verify camera format - negotiation incomplete");
                return false;
            }

            // Step 3: SET_CUR(COMMIT) - Commit to the negotiated format
            result = mConnection.controlTransfer(
                UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE,
                UVC_SET_CUR,
                VS_COMMIT_CONTROL << 8,
                mVideoStreamingInterface.getId(),
                probeData,
                probeData.length,
                5000
            );

            if (result < 0) {
                Log.e(TAG, "SET_CUR(COMMIT) FAILED: " + result);
                Log.e(TAG, "Camera failed to commit format - negotiation incomplete");
                return false;
            }
            Log.d(TAG, "SET_CUR(COMMIT) sent: " + result + " bytes");

            Log.i(TAG, "✓ Format negotiation COMPLETED SUCCESSFULLY for " + width + "x" + height);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Format negotiation FAILED with exception", e);
            return false;  // FAIL on exception
        }
    }

    /**
     * Finds the video control interface
     */
    private UsbInterface findVideoControlInterface(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);

            if (intf.getInterfaceClass() == UVC_CLASS &&
                intf.getInterfaceSubclass() == UVC_SUBCLASS_VIDEOCONTROL) {
                return intf;
            }
        }
        return null;
    }

    /**
     * Builds UVC probe/commit data structure
     */
    private byte[] buildProbeData(int width, int height) {
        byte[] data = new byte[26];  // UVC 1.1 probe control structure size

        // Frame interval (100ns units) - 60 FPS = 166666 (0x28B0A in hex)
        int frameInterval = 166666;

        // bmHint (offset 0-1): Bitfield indicating what fields shall be kept fixed
        data[0] = 0x01;  // dwFrameInterval
        data[1] = 0x00;

        // bFormatIndex (offset 2): Video format index - let camera choose
        data[2] = 0x00;  // 0 = let camera choose best format

        // bFrameIndex (offset 3): Video frame index - let camera choose
        data[3] = 0x00;  // 0 = let camera choose best frame

        // dwFrameInterval (offset 4-7): Frame interval in 100ns units
        data[4] = (byte)(frameInterval & 0xFF);
        data[5] = (byte)((frameInterval >> 8) & 0xFF);
        data[6] = (byte)((frameInterval >> 16) & 0xFF);
        data[7] = (byte)((frameInterval >> 24) & 0xFF);

        // wKeyFrameRate (offset 8-9): Not used for uncompressed
        data[8] = 0x00;
        data[9] = 0x00;

        // wPFrameRate (offset 10-11): Not used for uncompressed
        data[10] = 0x00;
        data[11] = 0x00;

        // wCompQuality (offset 12-13): Not used for uncompressed
        data[12] = 0x00;
        data[13] = 0x00;

        // wCompWindowSize (offset 14-15): Not used for uncompressed
        data[14] = 0x00;
        data[15] = 0x00;

        // wDelay (offset 16-17): Internal video streaming delay in ms
        data[16] = 0x00;
        data[17] = 0x00;

        // dwMaxVideoFrameSize (offset 18-21): Maximum video frame or codec size in bytes
        // Calculate based on requested resolution
        // For Y16: width * height * 2 bytes per pixel
        // For I420: width * height * 1.5 bytes per pixel
        // Use larger size to accommodate both formats
        int maxFrameSize = width * height * 2;  // Y16 format (worst case)
        data[18] = (byte)(maxFrameSize & 0xFF);
        data[19] = (byte)((maxFrameSize >> 8) & 0xFF);
        data[20] = (byte)((maxFrameSize >> 16) & 0xFF);
        data[21] = (byte)((maxFrameSize >> 24) & 0xFF);

        // dwMaxPayloadTransferSize (offset 22-25): Maximum payload transfer size
        // Use endpoint max packet size as guide
        int maxPayloadSize = maxFrameSize;
        data[22] = (byte)(maxPayloadSize & 0xFF);
        data[23] = (byte)((maxPayloadSize >> 8) & 0xFF);
        data[24] = (byte)((maxPayloadSize >> 16) & 0xFF);
        data[25] = (byte)((maxPayloadSize >> 24) & 0xFF);

        Log.i(TAG, "Probe data built for " + width + "x" + height +
                   ", max frame size: " + maxFrameSize + " bytes");

        return data;
    }

    /**
     * Main streaming loop that reads frames from USB endpoint
     */
    private void streamingLoop() {
        int bufferSize = mStreamingEndpoint.getMaxPacketSize() * 32;  // Buffer for multiple packets
        byte[] buffer = new byte[bufferSize];

        // Frame accumulation buffer - allocate for largest possible frame
        // MJPEG: Variable size, typically 150KB-350KB for Boson
        // Y16 format: 320×256×2 = 163,840 bytes
        // I420 format: 640×512×1.5 = 491,520 bytes
        int maxFrameSize = 1024 * 1024;  // 1MB buffer for MJPEG or uncompressed
        ByteBuffer frameBuffer = ByteBuffer.allocate(maxFrameSize);

        // Expected frame sizes based on negotiation
        // Y16 format can include 2 telemetry rows (per FLIR Boson SDK)
        final int Y16_SIZE = mWidth * mHeight * 2;              // 163,840 bytes for 320×256 (no telemetry)
        final int Y16_SIZE_WITH_TELEM = mWidth * (mHeight + 2) * 2;  // 165,120 bytes for 320×258 (with telemetry)
        final int I420_SIZE = 640 * 512 * 3 / 2;               // 491,520 bytes
        final int I420_SIZE_WITH_TELEM = 640 * 514 * 3 / 2;    // 494,592 bytes (with telemetry)

        // MJPEG detection
        boolean mjpegDetected = false;

        int endpointType = mStreamingEndpoint.getType();
        String transferType = endpointType == UsbConstants.USB_ENDPOINT_XFER_ISOC ? "isochronous" : "bulk";

        Log.i(TAG, "Streaming loop started");
        Log.i(TAG, "  Endpoint type: " + transferType);
        Log.i(TAG, "  Buffer size: " + bufferSize + " bytes");
        Log.i(TAG, "  Max frame size: " + maxFrameSize + " bytes");
        Log.i(TAG, "  Expected Y16 sizes: " + Y16_SIZE + " (no telem) or " + Y16_SIZE_WITH_TELEM + " (with telem) bytes");
        Log.i(TAG, "  Expected I420 sizes: " + I420_SIZE + " (no telem) or " + I420_SIZE_WITH_TELEM + " (with telem) bytes");
        Log.i(TAG, "  Max packet size: " + mStreamingEndpoint.getMaxPacketSize() + " bytes");

        int frameCount = 0;
        int errorCount = 0;
        long lastLogTime = System.currentTimeMillis();
        long frameStartTime = System.currentTimeMillis();
        final long FRAME_TIMEOUT_MS = 1000;  // 1 second timeout for frame accumulation

        while (mStreaming.get()) {
            try {
                // Read data from USB endpoint
                // Note: bulkTransfer works for both bulk and isochronous endpoints on many devices
                int bytesRead = mConnection.bulkTransfer(
                    mStreamingEndpoint,
                    buffer,
                    bufferSize,
                    100  // 100ms timeout
                );

                if (bytesRead > 0) {
                    // Check for UVC payload header (first 2-12 bytes)
                    int headerLength = buffer[0] & 0xFF;

                    // VALIDATE: UVC spec requires header length 2-12 bytes (or 0 for no header)
                    // Reject obviously invalid values to prevent data corruption
                    if (headerLength == 1 || headerLength > 12) {
                        if (frameCount < 5) {
                            Log.w(TAG, "Invalid UVC header length: " + headerLength +
                                      " (expected 0, or 2-12) - treating as no header");
                        }
                        headerLength = 0;  // Treat as no header
                    }

                    if (headerLength > 0 && headerLength < bytesRead) {
                        // Log header details for first few packets (debugging)
                        if (frameCount == 0 && frameBuffer.position() < 1000) {
                            int bitField = buffer[1] & 0xFF;
                            boolean eof = (bitField & 0x02) != 0;
                            boolean error = (bitField & 0x40) != 0;
                            Log.d(TAG, "UVC Header: len=" + headerLength +
                                      ", EOF=" + eof + ", ERR=" + error +
                                      ", bits=0x" + String.format("%02X", bitField));
                        }

                        // Extract payload data (skip header)
                        int payloadLength = bytesRead - headerLength;

                        // Accumulate frame data
                        if (frameBuffer.remaining() >= payloadLength) {
                            frameBuffer.put(buffer, headerLength, payloadLength);
                        } else {
                            Log.w(TAG, "Frame buffer overflow! Remaining: " + frameBuffer.remaining() +
                                      ", needed: " + payloadLength + " - discarding and starting new frame");
                            frameBuffer.clear();
                            mjpegDetected = false;  // Reset format detection for new frame
                            frameStartTime = System.currentTimeMillis();  // Reset timeout

                            // Only add current payload if it looks like a frame start (JPEG SOI for MJPEG)
                            if (payloadLength >= 2 &&
                                buffer[headerLength] == (byte)0xFF &&
                                buffer[headerLength + 1] == (byte)0xD8) {
                                Log.i(TAG, "New frame starts with JPEG SOI - good recovery");
                                frameBuffer.put(buffer, headerLength, payloadLength);
                            } else {
                                Log.w(TAG, "Discarding payload - doesn't start with JPEG SOI");
                                // Don't add anything, wait for next packet
                            }
                        }

                        // Frame completion detection
                        int accumulated = frameBuffer.position();
                        boolean frameComplete = false;
                        boolean endOfFrame = (buffer[1] & 0x02) != 0;

                        // MJPEG detection: Check if accumulated data starts with JPEG magic bytes
                        if (!mjpegDetected && accumulated >= 2) {
                            // Save current position before checking magic bytes
                            int savedPosition = frameBuffer.position();
                            frameBuffer.position(0);
                            byte firstByte = frameBuffer.get();
                            byte secondByte = frameBuffer.get();
                            // Restore position
                            frameBuffer.position(savedPosition);

                            // JPEG magic bytes: 0xFF 0xD8
                            if (firstByte == (byte)0xFF && secondByte == (byte)0xD8) {
                                mjpegDetected = true;
                                Log.i(TAG, "✓ MJPEG format detected (JPEG magic bytes found)");
                                Log.i(TAG, "  Boson is sending compressed MJPEG instead of uncompressed Y16/I420");
                                Log.i(TAG, "  Will use end-of-frame bit and JPEG EOI marker for frame detection");
                            }
                        }

                        // MJPEG frame completion: Use end-of-frame bit OR JPEG EOI marker
                        if (mjpegDetected) {
                            // Check for JPEG end-of-image marker (0xFF 0xD9) in last 2 bytes of payload
                            boolean jpegEOI = false;
                            if (accumulated >= 2) {
                                int pos = frameBuffer.position();
                                byte lastByte = frameBuffer.get(pos - 1);
                                byte secondLastByte = frameBuffer.get(pos - 2);
                                jpegEOI = (secondLastByte == (byte)0xFF && lastByte == (byte)0xD9);
                            }

                            // Frame complete if end-of-frame bit is set OR JPEG EOI marker found
                            if (endOfFrame || jpegEOI) {
                                frameComplete = true;
                                if (frameCount < 5) {
                                    Log.i(TAG, "✓ MJPEG frame complete: " + accumulated + " bytes (EOF=" + endOfFrame + ", EOI=" + jpegEOI + ")");
                                }
                            }
                        }
                        // Uncompressed frame completion: SIZE-BASED detection
                        else {
                            // Diagnostic: Log when we're close to target size
                            if (frameCount < 5 && accumulated > 160000) {
                                Log.d(TAG, "Frame accumulation: " + accumulated + " bytes, EOF bit=" + endOfFrame);
                                Log.d(TAG, "  Valid sizes: Y16=" + Y16_SIZE + ", Y16+telem=" + Y16_SIZE_WITH_TELEM +
                                          ", I420=" + I420_SIZE + ", I420+telem=" + I420_SIZE_WITH_TELEM);
                            }

                            // Accept exact frame sizes (with or without telemetry)
                            if (accumulated == Y16_SIZE) {
                                frameComplete = true;
                                if (frameCount < 5) Log.i(TAG, "✓ Y16 frame (320×256, no telemetry)");
                            } else if (accumulated == Y16_SIZE_WITH_TELEM) {
                                frameComplete = true;
                                if (frameCount < 5) Log.i(TAG, "✓ Y16 frame (320×258, WITH 2 telemetry rows)");
                            } else if (accumulated == I420_SIZE) {
                                frameComplete = true;
                                if (frameCount < 5) Log.i(TAG, "✓ I420 frame (640×512, no telemetry)");
                            } else if (accumulated == I420_SIZE_WITH_TELEM) {
                                frameComplete = true;
                                if (frameCount < 5) Log.i(TAG, "✓ I420 frame (640×514, WITH 2 telemetry rows)");
                            }
                            // If accumulated exceeds maximum possible size, frame is corrupted - restart
                            else if (accumulated > I420_SIZE_WITH_TELEM) {
                                Log.w(TAG, "Frame buffer overflow: " + accumulated + " bytes exceeds maximum " +
                                          I420_SIZE_WITH_TELEM + " - restarting frame");
                                frameBuffer.clear();
                            }
                        }

                        // Check for frame accumulation timeout
                        long now = System.currentTimeMillis();
                        if (!frameComplete && accumulated > 0 && (now - frameStartTime) > FRAME_TIMEOUT_MS) {
                            Log.w(TAG, "Frame accumulation timeout! " + accumulated +
                                      " bytes accumulated in " + (now - frameStartTime) + "ms - resetting");
                            Log.w(TAG, "  Format: " + (mjpegDetected ? "MJPEG" : "uncompressed") +
                                      ", EOF bit seen: " + endOfFrame);
                            frameBuffer.clear();
                            mjpegDetected = false;
                            frameStartTime = now;
                        }

                        if (frameComplete && frameBuffer.position() > 0) {
                            // Frame complete - deliver to callback
                            frameBuffer.flip();

                            frameCount++;
                            if (frameCount <= 10) {
                                String format = " (unknown size)";
                                int size = frameBuffer.remaining();
                                if (size == Y16_SIZE) format = " (Y16, 320×256)";
                                else if (size == Y16_SIZE_WITH_TELEM) format = " (Y16+telem, 320×258)";
                                else if (size == I420_SIZE) format = " (I420, 640×512)";
                                else if (size == I420_SIZE_WITH_TELEM) format = " (I420+telem, 640×514)";

                                Log.i(TAG, "✓ Frame #" + frameCount + " delivered: " + size + " bytes" + format);
                            }

                            if (mFrameCallback != null) {
                                mFrameCallback.onFrame(frameBuffer);
                            }

                            // Reset for next frame
                            frameBuffer.clear();
                            frameStartTime = System.currentTimeMillis();  // Reset timeout for next frame

                            // Periodic status logging (every 5 seconds)
                            now = System.currentTimeMillis();
                            if (now - lastLogTime > 5000) {
                                Log.i(TAG, "Streaming status: " + frameCount + " frames received, " +
                                          errorCount + " errors");
                                lastLogTime = now;
                            }
                        }
                    } else if (headerLength == 0) {
                        // Some cameras send packets with no header - treat as payload
                        if (frameBuffer.remaining() >= bytesRead) {
                            frameBuffer.put(buffer, 0, bytesRead);
                        }
                    }
                } else if (bytesRead < 0) {
                    // Error occurred
                    errorCount++;
                    if (errorCount <= 10) {
                        Log.e(TAG, "Transfer error: " + bytesRead);
                    }
                    Thread.sleep(10);  // Brief pause before retry
                }

            } catch (Exception e) {
                if (mStreaming.get()) {
                    Log.e(TAG, "Streaming error", e);
                }
                break;
            }
        }

        Log.i(TAG, "Streaming loop ended");
    }

    /**
     * Helper to get human-readable endpoint type
     */
    private String getEndpointTypeString(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL: return "Control";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC: return "Isochronous";
            case UsbConstants.USB_ENDPOINT_XFER_BULK: return "Bulk";
            case UsbConstants.USB_ENDPOINT_XFER_INT: return "Interrupt";
            default: return "Unknown";
        }
    }
}
