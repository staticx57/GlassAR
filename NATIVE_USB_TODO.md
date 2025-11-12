# Native USB Implementation TODO

## Current Status

The app is configured to build without external USB camera libraries. Android's native `android.hardware.usb` APIs should be used to access the FLIR Boson 320.

## Implementation Steps

### 1. USB Device Detection

```java
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;

UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

for (UsbDevice device : deviceList.values()) {
    // Check for Boson 320 vendor/product IDs
    if (device.getVendorId() == 2507 && device.getProductId() == 1792) {
        // Found Boson 320
    }
}
```

### 2. Request Permissions

```java
PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0,
    new Intent(ACTION_USB_PERMISSION), 0);
usbManager.requestPermission(device, permissionIntent);
```

### 3. Open Device Connection

```java
UsbDeviceConnection connection = usbManager.openDevice(device);
UsbInterface intf = device.getInterface(1); // Video streaming interface
connection.claimInterface(intf, true);
```

### 4. Setup Video Streaming

For UVC cameras, you need to:
1. Send UVC control commands to configure resolution/format
2. Set up isochronous or bulk transfer endpoints
3. Read video frames from the streaming endpoint

This requires understanding the UVC protocol spec.

## Alternative: Use Camera2 API

Android Camera2 API may support UVC cameras on some devices:

```java
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraDevice;

CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
String[] cameraIds = manager.getCameraIdList();

// Try each camera to see if Boson is detected
for (String id : cameraIds) {
    CameraCharacteristics chars = manager.getCameraCharacteristics(id);
    // Check if this is an external USB camera
}
```

## Recommendation

For production use, consider:
1. Using UVCCamera library (once you build/obtain the AAR)
2. Or implementing full UVC protocol with native USB APIs
3. Test thoroughly with actual Boson 320 hardware

The current MainActivity.java has the UVCCamera code commented out and needs to be replaced with one of the above approaches.
