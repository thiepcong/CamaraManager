# CameraXVideoManager SDK

## Overview

The `CameraXVideoManager` is an Android SDK designed to facilitate video recording functionalities in your application. It utilizes the CameraX library to provide robust video recording operations including handling various camera qualities, switching between cameras, and managing recording states. The SDK is designed for easy integration and effective video recording control.

## Features

- **Video Recording**: Start, pause, resume, and stop video recording with real-time status updates.
- **Camera Switching**: Easily switch between front and back cameras.
- **Quality Selection**: Choose from various video qualities (e.g., UHD, FHD, HD, SD).
- **Audio Control**: Toggle audio recording on or off.
- **Lifecycle-Aware**: Automatically manages camera lifecycle, binding to the provided lifecycle owner.
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
   val camerax_version = "1.1.0-beta01"
   implementation("androidx.camera:camera-core:$camerax_version")
   // CameraX Camera2 extensions
   implementation("androidx.camera:camera-camera2:$camerax_version")
   // CameraX Lifecycle library
   implementation("androidx.camera:camera-lifecycle:$camerax_version")
   // CameraX Video
    implementation ("androidx.camera:camera-video:${camerax_version}")
   // CameraX View class
   implementation("androidx.camera:camera-view:$camerax_version")
   ```

2. **Permission Handling**

   Ensure your `AndroidManifest.xml` includes the necessary camera and storage permissions:

   ```xml
    <uses-feature android:name="android.hardware.camera.any" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
   ```

   Implement runtime permission handling in your application. Request the required permissions before initializing `CameraXVideoManager`.

### Detailed Function Descriptions

#### `CameraXVideoManager`

**Constructor:**

```kotlin
class CameraXVideoManager(
    private val context: Context,
    private val viewFinder: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val callback: CameraVideoCallback
)
```

- **context**: Application context.
- **previewView**: View to display the camera preview.
- **lifecycleOwner**: Manages the lifecycle of the camera.
- **callback**: Callback interface for handling camera events.

**Methods:**

- `switchLenCamera()`: Switches between front and back cameras.
- `onStart: (() -> Unit)?, onPause: (() -> Unit)?, onResume: (() -> Unit)?`: Starts or pauses or resume video recording.
- `getQualitySections(onSelector: (selectorStrings: List<String>, qualityIndex: Int) -> Unit)`: Provides available video quality options.
- `bindCaptureUseCase()`: Binds the camera and video capture use cases to the lifecycle.
- `turnFlash(isFlashOn: Boolean)`: Turn the flash on or off.
- `zoom(rate: Float)`: Zoom in and out with rate.
- `startRecording()`: Starts recording a video.
- `pauseRecording()`: Pauses recording a video.
- `resumeRecording()`: Resumes recording a video.
- `stopRecording(onDone: () -> Unit)`: Stops the current recording.
- `initCameraVideo()`: Initializes the camera and binds the use cases.
- `destroy()`: Cleans up resources when the camera is no longer needed.

**Callback Interface:**

```kotlin
interface CameraVideoCallback {
    fun onCaptureSuccess(uri: Uri)
    fun onCaptureStatusUpdate(status: String)
    fun onError(message: String)
    fun onEnableUI(enable: Boolean)
    fun onUpdateVideoEvent(event: VideoRecordEvent)
}
```

- **onCaptureSuccess(uri: Uri)**: Called when a video is successfully recorded.
- **onCaptureStatusUpdate(status: String)**: Provides updates on the recording status.
- **onError(message: String)**: Reports errors.
- **onEnableUI(enable: Boolean)**: Enables or disables UI controls.
- **onUpdateVideoEvent(event: VideoRecordEvent)**: Updates on video recording events.

## Usage

### 1. Requesting Permissions

Before initializing `CameraXVideoManager`, you need to request the necessary camera and storage permissions. You can handle permissions in a fragment or activity that precedes your CameraFragment.

```kotlin
class PermissionsFragment : Fragment() {
    private var permissionManager: PermissionVideoManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionVideoManager(requireContext(), this)
        permissionManager?.checkAndRequestPermissions {
            // Navigate to YourCaptureFragment
        }
    }
}
```

### 2. Initialization

Initialize `CameraXVideoManager` in your fragment:

```kotlin
class CaptureFragment : Fragment(), CameraXVideoManager.CameraVideoCallback {
    private var _captureViewBinding: FragmentCaptureBinding? = null
    private val captureViewBinding get() = _captureViewBinding!!

    private lateinit var cameraManager: CameraXVideoManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _captureViewBinding = FragmentCaptureBinding.inflate(inflater, container, false)
        return captureViewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraManager = CameraXVideoManager(
            requireContext(),
            captureViewBinding.previewView,
            viewLifecycleOwner,
            this
        )
        initCameraFragment()
    }

    private fun initCameraFragment() {
        // Initialize UI and camera
    }

    override fun onDestroyView() {
        _captureViewBinding = null
        super.onDestroyView()
        cameraManager.destroy()
    }

    // Implement CameraVideoCallback methods
}
```

### 3. Handling UI and Recording

Set up the UI controls for recording:

```kotlin
private fun initializeUI() {
    captureViewBinding.cameraButton.setOnClickListener {
        cameraManager.switchLenCamera()
    }

    captureViewBinding.captureButton.setOnClickListener {
        cameraManager.handleCapture(
            onStart = { /* Handle start */ },
            onPause = { /* Handle pause */ },
            onResume = { /* Handle resume */ },
        )
    }

    captureViewBinding.stopButton.setOnClickListener {
        cameraManager.stopRecording {
            // Stop done
        }
    }
}
```
Controls zoom and flash:

```kotlin
captureViewBinding.flash.setOnClickListener {
    cameraManager.turnFlash(true)
}
captureViewBinding.zoomin.setOnClickListener {
    cameraManager.zoom(1.2f)
}
captureViewBinding.zoomout.setOnClickListener {
    cameraManager.zoom(0.8f)
}
```

### 4. Handling Quality Selection

Initialize the quality selection UI:

```kotlin
private fun initializeQualitySectionsUI() {
    cameraManager.getQualitySections { qualities, currentQualityIndex  ->
        // Handle available qualities/currentQualityIndex
    }
}
```
Update the quality selection UI:

```kotlin
// position get from qualities above
cameraManager.qualityIndex = position
// Update quality selection UI
```

### 5. Handling Video Events

Implement methods to handle video recording events:

```kotlin
override fun onCaptureSuccess(uri: Uri) {
    // Handle capture success
}

override fun onUpdateVideoEvent(event: VideoRecordEvent) {
    // Update UI based on video record event
}

override fun onCaptureStatusUpdate(status: String) {
    // Update UI with capture status
}

override fun onEnableUI(enable: Boolean) {
    // Enable or disable UI controls
}

override fun onError(message: String) {
    // Handle errors
}
```

## Examples

Find example projects and detailed usage instructions in the [sample](../app/src/main/java/com/example/cameramanager/video/fragments/CaptureFragment.kt) file.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
