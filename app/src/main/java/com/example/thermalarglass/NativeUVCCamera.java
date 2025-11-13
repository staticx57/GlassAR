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
            return false;
        }

        try {
            // Find video control interface
            UsbInterface controlInterface = findVideoControlInterface(mDevice);
            if (controlInterface == null) {
                Log.w(TAG, "No video control interface found, skipping format negotiation");
                return true;  // Some cameras work without explicit negotiation
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
                Log.w(TAG, "SET_CUR(PROBE) failed: " + result + ", continuing anyway");
                return true;  // Many cameras still work
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

                // Parse camera's response to see what it will actually provide
                int formatIndex = responseData[2] & 0xFF;
                int frameIndex = responseData[3] & 0xFF;
                int frameSize = ((responseData[21] & 0xFF) << 24) |
                               ((responseData[20] & 0xFF) << 16) |
                               ((responseData[19] & 0xFF) << 8) |
                               (responseData[18] & 0xFF);

                Log.i(TAG, "Camera response: formatIndex=" + formatIndex +
                          ", frameIndex=" + frameIndex +
                          ", frameSize=" + frameSize + " bytes");
            } else {
                Log.w(TAG, "GET_CUR(PROBE) failed: " + result);
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
                Log.w(TAG, "SET_CUR(COMMIT) failed: " + result + ", continuing anyway");
            } else {
                Log.d(TAG, "SET_CUR(COMMIT) sent: " + result + " bytes");
            }

            Log.i(TAG, "Format negotiation completed for " + width + "x" + height);
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
        // Y16 format: 320×256×2 = 163,840 bytes
        // I420 format: 640×512×1.5 = 491,520 bytes
        int maxFrameSize = 640 * 512 * 3 / 2;  // Max for I420
        ByteBuffer frameBuffer = ByteBuffer.allocate(maxFrameSize);

        int endpointType = mStreamingEndpoint.getType();
        String transferType = endpointType == UsbConstants.USB_ENDPOINT_XFER_ISOC ? "isochronous" : "bulk";

        Log.i(TAG, "Streaming loop started");
        Log.i(TAG, "  Endpoint type: " + transferType);
        Log.i(TAG, "  Buffer size: " + bufferSize + " bytes");
        Log.i(TAG, "  Max frame size: " + maxFrameSize + " bytes");
        Log.i(TAG, "  Max packet size: " + mStreamingEndpoint.getMaxPacketSize() + " bytes");

        int frameCount = 0;
        int errorCount = 0;
        long lastLogTime = System.currentTimeMillis();

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
                    // Log first few transfers for debugging
                    if (frameCount < 5) {
                        Log.d(TAG, "Transfer #" + frameCount + ": " + bytesRead + " bytes received");
                    }

                    // Check for UVC payload header (first 2-12 bytes)
                    int headerLength = buffer[0] & 0xFF;

                    if (headerLength > 0 && headerLength < bytesRead) {
                        // Extract payload data (skip header)
                        int payloadLength = bytesRead - headerLength;

                        // Accumulate frame data
                        if (frameBuffer.remaining() >= payloadLength) {
                            frameBuffer.put(buffer, headerLength, payloadLength);
                        } else {
                            Log.w(TAG, "Frame buffer overflow! Remaining: " + frameBuffer.remaining() +
                                      ", needed: " + payloadLength);
                        }

                        // Check if frame is complete (end-of-frame bit in header)
                        boolean endOfFrame = (buffer[1] & 0x02) != 0;

                        if (endOfFrame && frameBuffer.position() > 0) {
                            // Frame complete - deliver to callback
                            frameBuffer.flip();

                            frameCount++;
                            if (frameCount <= 3) {
                                Log.i(TAG, "Frame #" + frameCount + " complete: " +
                                          frameBuffer.remaining() + " bytes");
                            }

                            if (mFrameCallback != null) {
                                mFrameCallback.onFrame(frameBuffer);
                            }

                            // Reset for next frame
                            frameBuffer.clear();

                            // Periodic status logging (every 5 seconds)
                            long now = System.currentTimeMillis();
                            if (now - lastLogTime > 5000) {
                                Log.i(TAG, "Streaming status: " + frameCount + " frames received, " +
                                          errorCount + " errors");
                                lastLogTime = now;
                            }
                        }
                    } else if (headerLength == 0) {
                        // Some cameras send packets with no header
                        if (frameCount < 5) {
                            Log.w(TAG, "Received packet with zero header length");
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
