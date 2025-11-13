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
            return false;
        }

        try {
            // Find video control interface
            UsbInterface controlInterface = findVideoControlInterface(mDevice);
            if (controlInterface == null) {
                Log.w(TAG, "No video control interface found, skipping format negotiation");
                return true;  // Some cameras work without explicit negotiation
            }

            // Build probe control structure (simplified version)
            byte[] probeData = buildProbeData(width, height);

            // Send SET_CUR(PROBE) request
            int result = mConnection.controlTransfer(
                UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_RECIP_INTERFACE,
                UVC_SET_CUR,
                VS_PROBE_CONTROL << 8,
                mVideoStreamingInterface.getId(),
                probeData,
                probeData.length,
                5000
            );

            if (result < 0) {
                Log.w(TAG, "Probe control failed, continuing anyway");
                return true;  // Many cameras still work
            }

            // Send SET_CUR(COMMIT) request
            result = mConnection.controlTransfer(
                UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_RECIP_INTERFACE,
                UVC_SET_CUR,
                VS_COMMIT_CONTROL << 8,
                mVideoStreamingInterface.getId(),
                probeData,
                probeData.length,
                5000
            );

            Log.i(TAG, "Format negotiation completed: " + width + "x" + height);
            return true;

        } catch (Exception e) {
            Log.w(TAG, "Format negotiation error, continuing anyway", e);
            return true;  // Proceed even if negotiation fails
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
        byte[] data = new byte[26];  // Minimum probe control structure size

        // Frame interval (100ns units) - 60 FPS = 166666 (0x28B0A in hex)
        int frameInterval = 166666;
        data[0] = 0x01;  // bmHint: dwFrameInterval
        data[1] = 0x00;

        // Format and frame index (simplified - using defaults)
        data[2] = 0x01;  // bFormatIndex
        data[3] = 0x01;  // bFrameIndex

        // Frame interval
        data[4] = (byte)(frameInterval & 0xFF);
        data[5] = (byte)((frameInterval >> 8) & 0xFF);
        data[6] = (byte)((frameInterval >> 16) & 0xFF);
        data[7] = (byte)((frameInterval >> 24) & 0xFF);

        return data;
    }

    /**
     * Main streaming loop that reads frames from USB endpoint
     */
    private void streamingLoop() {
        int bufferSize = mStreamingEndpoint.getMaxPacketSize() * 32;  // Buffer for multiple packets
        byte[] buffer = new byte[bufferSize];

        // Frame accumulation buffer (for Boson 320: 320x256x2 = 163840 bytes for YUYV)
        int frameSize = mWidth * mHeight * 2;  // YUYV format
        ByteBuffer frameBuffer = ByteBuffer.allocate(frameSize);

        Log.i(TAG, "Streaming loop started, buffer size: " + bufferSize);

        while (mStreaming.get()) {
            try {
                // Read data from USB endpoint
                int bytesRead = mConnection.bulkTransfer(
                    mStreamingEndpoint,
                    buffer,
                    bufferSize,
                    100  // 100ms timeout
                );

                if (bytesRead > 0) {
                    // Check for UVC payload header (first 2-12 bytes)
                    int headerLength = buffer[0] & 0xFF;

                    if (headerLength > 0 && headerLength < bytesRead) {
                        // Extract payload data (skip header)
                        int payloadLength = bytesRead - headerLength;

                        // Accumulate frame data
                        if (frameBuffer.remaining() >= payloadLength) {
                            frameBuffer.put(buffer, headerLength, payloadLength);
                        }

                        // Check if frame is complete (end-of-frame bit in header)
                        boolean endOfFrame = (buffer[1] & 0x02) != 0;

                        if (endOfFrame && frameBuffer.position() > 0) {
                            // Frame complete - deliver to callback
                            frameBuffer.flip();

                            if (mFrameCallback != null) {
                                mFrameCallback.onFrame(frameBuffer);
                            }

                            // Reset for next frame
                            frameBuffer.clear();
                        }
                    }
                } else if (bytesRead < 0) {
                    // Error occurred
                    Log.e(TAG, "Bulk transfer error: " + bytesRead);
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
