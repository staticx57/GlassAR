package com.example.thermalarglass;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;

/**
 * Native USB Monitor for detecting and managing USB device connections
 * Replacement for external USBMonitor library using Android's native APIs
 */
public class NativeUSBMonitor {

    private static final String TAG = "NativeUSBMonitor";
    private static final String ACTION_USB_PERMISSION = "com.example.thermalarglass.USB_PERMISSION";

    private Context mContext;
    private UsbManager mUsbManager;
    private OnDeviceConnectListener mListener;
    private boolean mRegistered = false;

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && mListener != null) {
                    Log.i(TAG, "USB device attached: " + device.getDeviceName());
                    mListener.onAttach(device);
                }

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && mListener != null) {
                    Log.i(TAG, "USB device detached: " + device.getDeviceName());
                    mListener.onDetach(device);
                }

            } else if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null && mListener != null) {
                            Log.i(TAG, "USB permission granted for: " + device.getDeviceName());
                            mListener.onConnect(device);
                        }
                    } else {
                        if (device != null && mListener != null) {
                            Log.w(TAG, "USB permission denied for: " + device.getDeviceName());
                            mListener.onCancel(device);
                        }
                    }
                }
            }
        }
    };

    public interface OnDeviceConnectListener {
        void onAttach(UsbDevice device);
        void onConnect(UsbDevice device);
        void onDetach(UsbDevice device);
        void onCancel(UsbDevice device);
    }

    public NativeUSBMonitor(Context context, OnDeviceConnectListener listener) {
        mContext = context;
        mListener = listener;
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    /**
     * Registers the USB monitor to receive device events
     */
    public void register() {
        if (mRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);

        mContext.registerReceiver(mUsbReceiver, filter);
        mRegistered = true;

        Log.i(TAG, "USB monitor registered");

        // Check for already connected devices
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (mListener != null) {
                Log.i(TAG, "Found existing USB device: " + device.getDeviceName() +
                      " VID:" + device.getVendorId() + " PID:" + device.getProductId());
                mListener.onAttach(device);
            }
        }
    }

    /**
     * Unregisters the USB monitor
     */
    public void unregister() {
        if (!mRegistered) {
            return;
        }

        try {
            mContext.unregisterReceiver(mUsbReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered", e);
        }

        mRegistered = false;
        Log.i(TAG, "USB monitor unregistered");
    }

    /**
     * Requests permission to access the USB device
     */
    public void requestPermission(UsbDevice device) {
        if (device == null) {
            return;
        }

        // Check if we already have permission
        if (mUsbManager.hasPermission(device)) {
            Log.i(TAG, "Already have permission for device: " + device.getDeviceName());
            if (mListener != null) {
                mListener.onConnect(device);
            }
            return;
        }

        // Request permission
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
            mContext,
            0,
            new Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        Log.i(TAG, "Requesting USB permission for: " + device.getDeviceName());
        mUsbManager.requestPermission(device, permissionIntent);
    }

    /**
     * Checks if the device is a FLIR Boson camera
     */
    public static boolean isBosonCamera(UsbDevice device) {
        // FLIR Boson vendor/product IDs
        // Vendor ID: 2507 (0x09CB)
        // Product ID: varies, but 1792 (0x0700) is common
        return device.getVendorId() == 2507;
    }

    /**
     * Checks if the device is a UVC camera
     */
    public static boolean isUVCCamera(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            if (device.getInterface(i).getInterfaceClass() == 14) {  // Video class
                return true;
            }
        }
        return false;
    }
}
