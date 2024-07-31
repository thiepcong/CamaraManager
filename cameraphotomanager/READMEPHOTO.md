# CameraXPhotoManager SDK

## Overview

The `CameraXPhotoManager` is an Android SDK designed to simplify the integration of camera functionalities in your application. It leverages the CameraX library to provide seamless camera operations including photo capture, image analysis, and handling of camera state changes. This SDK is designed to be easy to integrate, providing a robust solution for camera-based applications.

## Features

- **Photo Capture**: Capture high-quality photos with minimal latency.
- **Image Analysis**: Perform real-time image analysis using a custom LuminosityAnalyzer.
- **Camera Switching**: Easily switch between front and back cameras.
- **Volume Button Shutter**: Use the volume down button as a shutter trigger.
- **Lifecycle-Aware**: Automatically manages camera lifecycle, binding to the provided lifecycle owner.
- **Orientation Handling**: Handles orientation changes to ensure correct image capture.
- **Flash Control**: Turn the flash on or off during video recording.
- **Zoom Control**: Zoom in and out smoothly during video recording.

## Getting Started

### Prerequisites

- Android Studio
- Minimum SDK version 21
- CameraX dependencies

### Integration

1. **Add CameraX Dependencies**

   Add the following dependencies to your `build.gradle.kts` file:

   ```kotlin
   // CameraX core library
    val camerax_version = "1.3.4"
    implementation ("androidx.camera:camera-core:$camerax_version")
    // CameraX Camera2 extensions
    implementation ("androidx.camera:camera-camera2:$camerax_version")
    // CameraX Lifecycle library
    implementation ("androidx.camera:camera-lifecycle:$camerax_version")
    // CameraX View class
    implementation ("androidx.camera:camera-view:$camerax_version")
   ```

2. **Permission Handling**

   Ensure your `AndroidManifest.xml` includes the necessary camera and storage permissions:

   ```xml
   <uses-feature android:name="android.hardware.camera" />
   <uses-permission android:name="android.permission.CAMERA" />
   <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
   ```

   Implement runtime permission handling in your application. Before initializing CameraXPhotoManager, request the required permissions.

### Detailed Function Descriptions

#### `CameraXPhotoManager`

**Constructor:**

```kotlin
class CameraXPhotoManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val viewFinder: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val view: View,
    private val callback: CameraPhotoCallback? = null
)
```

- **context**: Application context.
- **windowManager**: Manages different window types.
- **previewView**: View to display the camera preview.
- **lifecycleOwner**: Manages the lifecycle of the camera.
- **rootView**: Root view of the fragment/activity.
- **callback**: Callback interface for handling camera events.

**Methods:**

- `takePhoto(onPhotoCaptured: (OutputFileResults) -> Unit, onError: (ImageCaptureException) -> Unit)`: Captures a photo and handles success and error callbacks.
- `switchLenCamera()`: Switches between front and back cameras.
- `bindCaptureUseCase()`: Binds the camera and video capture use cases to the lifecycle.
- `turnFlash(isFlashOn: Boolean)`: Turn the flash on or off.
- `zoom(rate: Float)`: Zoom in and out with rate.
- `hasBackCamera()`: Checks if the device has a back camera.
- `hasFrontCamera()`: Checks if the device has a front camera.
- `destroy()`: Cleans up resources when the camera is no longer needed.

**Callback Interface:**

```kotlin
interface CameraPhotoCallback {
    fun onVolumeDownReceiver()
    fun onCameraStateChange(cameraInfo: CameraInfo)
}
```

- **onVolumeDownReceiver()**: Triggered when the volume down button is pressed.
- **onCameraStateChange(cameraInfo: CameraInfo)**: Triggered when the camera state changes, providing the current `CameraInfo`.

## Usage
### 1. Requesting Permissions

Before initializing `CameraXPhotoManager`, you need to request the necessary camera and storage permissions. You can do this in a separate fragment or activity that precedes your CameraFragment. Here's an example of how to request permissions using `PermissionPhotoManager`:


```kotlin
class PermissionsFragment : Fragment() {
    private var permissionManager: PermissionPhotoManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionPhotoManager(requireContext(), this)
        permissionManager?.checkAndRequestPermissions {
            // Navigate to YourCameraFragment
        }
    }
}
```

### 2. **Initialization**

First, initialize `CameraXPhotoManager` in your fragment.

```kotlin
// If you want to use CameraPhotoCallback
YourFragment() : Fragment(), CameraXPhotoManager.CameraPhotoCallback

private lateinit var cameraXManager: CameraXPhotoManager

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    cameraXManager = CameraXPhotoManager(
        requireContext(),               // Context
        requireActivity().windowManager, // WindowManager
        fragmentCameraBinding.viewFinder,// PreviewView for camera preview
        viewLifecycleOwner,              // LifecycleOwner
        view,                            // Root view for UI
        this                             // Callback implementation Or null
    )

    fragmentCameraBinding.viewFinder.post {
        updateCameraUi()
        lifecycleScope.launch {
            updateCameraSwitchButton()
        }
    }
}
```

### 3. **Updating Camera UI**

Setup the camera controls and buttons in your UI.

```kotlin
private fun updateCameraUi() {
    // Set up the capture button to take a photo
    fragmentCameraBinding.cameraCaptureButton.setOnClickListener {
        cameraXManager.takePhoto({ output ->
            // Handle photo capture success
        }, { exc ->
            // Handle photo capture error
        })
    }

    // Set up the switch button to change the camera lens
    fragmentCameraBinding.cameraSwitchButton.setOnClickListener {
        cameraXManager.switchLenCamera()
    }
}
```
Controls zoom and flash:

```kotlin
fragmentCameraBinding.flash.setOnClickListener {
    cameraManager.turnFlash(true)
}
fragmentCameraBinding.zoomin.setOnClickListener {
    cameraManager.zoom(1.2f)
}
fragmentCameraBinding.zoomout.setOnClickListener {
    cameraManager.zoom(0.8f)
}
```
### 4. **Switching Cameras**

Enable or disable the camera switch button based on available cameras.

```kotlin
private fun updateCameraSwitchButton() {
    fragmentCameraBinding.cameraSwitchButton.isEnabled =
        cameraXManager.hasBackCamera() && cameraXManager.hasFrontCamera()
}
```

### 5. **Destroying the Manager**

Properly clean up resources when the view is destroyed.

```kotlin
override fun onDestroyView() {
    _fragmentCameraBinding = null
    super.onDestroyView()
    cameraXManager.destroy()
}
```

### 6. **Handling Volume Button as Shutter**

Implement the callback to handle volume button presses for capturing photos.

```kotlin
override fun onVolumeDownReceiver() {
    fragmentCameraBinding.cameraCaptureButton.performClick()
}
```

### 7. **Handling Camera State Changes**

Implement the callback to react to camera state changes.

```kotlin
override fun onCameraStateChange(cameraInfo: CameraInfo) {
    // Handle camera state changes, e.g., showing/hiding UI elements based on state
}
```
## Examples
Find example projects and detailed usage instructions in the [sample](../app/src/main/java/com/example/cameramanager/photo/fragments/CameraFragment.kt)  file.


## License
This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.