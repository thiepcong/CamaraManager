package com.example.cameravideomanager

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.cameravideomanager.extensions.getAspectRatio
import com.example.cameravideomanager.extensions.getAspectRatioString
import com.example.cameravideomanager.extensions.getNameString
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class CameraXVideoManager(
    private val context: Context,
    private val viewFinder: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val callback: CameraVideoCallback? = null
) {

    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(context) }
    private var enumerationDeferred: Deferred<Unit>? = null

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var currentRecording: Recording? = null
    private lateinit var recordingState: VideoRecordEvent

    private var camera: Camera? = null

    val cameraCapabilities = mutableListOf<CameraCapability>()
    var cameraIndex = 0
    var qualityIndex = DEFAULT_QUALITY_IDX
    var audioEnabled = true

    init {
        enumerationDeferred = lifecycleOwner.lifecycleScope.async {
            val provider = ProcessCameraProvider.getInstance(context).await()

            provider.unbindAll()
            for (camSelector in arrayOf(
                CameraSelector.DEFAULT_BACK_CAMERA,
                CameraSelector.DEFAULT_FRONT_CAMERA
            )) {
                try {
                    if (provider.hasCamera(camSelector)) {
                        val res = provider.bindToLifecycle(lifecycleOwner, camSelector)
                        QualitySelector
                            .getSupportedQualities(res.cameraInfo)
                            .filter { quality ->
                                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD).contains(
                                    quality
                                )
                            }.also {
                                cameraCapabilities.add(CameraCapability(camSelector, it))
                            }
                        camera = res
                    }
                } catch (exc: Exception) {
                    callback?.onError(exc.toString())
                    Log.e(TAG, "Camera Face $camSelector is not supported")
                }
            }
        }
    }

    fun switchLenCamera() {
        cameraIndex = (cameraIndex + 1) % cameraCapabilities.size
        // camera device change is in effect instantly:
        //   - reset quality selection
        //   - restart preview
        qualityIndex = DEFAULT_QUALITY_IDX

        // Re-bind use cases to update selected camera
        lifecycleOwner.lifecycleScope.launch {
            bindCaptureUseCase()
        }
    }

    fun handleCapture(onStart: (() -> Unit)?, onPause: (() -> Unit)?, onResume: (() -> Unit)?) {
        if (!::recordingState.isInitialized ||
            recordingState is VideoRecordEvent.Finalize
        ) {
            callback?.onEnableUI(false)
            startRecording()
            onStart?.invoke()
        } else {
            when (recordingState) {
                is VideoRecordEvent.Start -> {
                    pauseRecording()
                    onPause?.invoke()
                }

                is VideoRecordEvent.Pause -> {
                    resumeRecording()
                    onResume?.invoke()
                }

                is VideoRecordEvent.Resume -> {
                    pauseRecording()
                    onPause?.invoke()
                }

                else -> throw IllegalStateException("recordingState in unknown state")
            }
        }
    }

    fun stopRecording(onDone: () -> Unit) {
        if (currentRecording == null || recordingState is VideoRecordEvent.Finalize) {
            return
        }

        val recording = currentRecording
        if (recording != null) {
            recording.stop()
            currentRecording = null
        }
        onDone.invoke()
    }

    private fun pauseRecording() {
        currentRecording?.pause()
        callback?.onCaptureStatusUpdate("Recording paused")
    }

    private fun resumeRecording() {
        currentRecording?.resume()
        callback?.onCaptureStatusUpdate("Recording resumed")
    }

    fun zoom(rate: Float) {
        val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1F
        val newZoomRatio = currentZoomRatio * rate
        camera?.cameraControl?.setZoomRatio(newZoomRatio)
    }

    fun turnFlash(isFlashOn: Boolean) {
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    /**
     *  initializeQualitySectionsUI():
     *    Populate a RecyclerView to display camera capabilities:
     *       - one front facing
     *       - one back facing
     *    User selection is saved to qualityIndex, will be used
     *    in the bindCaptureUsecase().
     */
    fun getQualitySections(onSelector: (qualities: List<Quality>, currentQualityIndex: Int) -> Unit) {
        val qualities = cameraCapabilities[cameraIndex].qualities
        onSelector.invoke(qualities, qualityIndex)
    }

    suspend fun bindCaptureUseCase() {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val cameraSelector = getCameraSelector(cameraIndex)

        val quality = cameraCapabilities[cameraIndex].qualities[qualityIndex]
        val qualitySelector = QualitySelector.from(Quality.SD)

        viewFinder.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val dimensionRatio1 = quality.getAspectRatioString(
                quality,
                (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            )
            dimensionRatio = dimensionRatio1
        }
        val preview = Preview.Builder()
            .setTargetAspectRatio(quality.getAspectRatio(quality))
            .build().apply {
                setSurfaceProvider(viewFinder.surfaceProvider)
            }

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                videoCapture,
                preview
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            resetAtr()
            callback?.onError("bindToLifecycle failed: $exc")
        }
        callback?.onEnableUI(true)
    }

    private fun resetAtr() {
        cameraIndex = 0
        qualityIndex = DEFAULT_QUALITY_IDX
        audioEnabled = false
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val name = "CameraX-recording-" +
                SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                    .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        currentRecording = videoCapture.output
            .prepareRecording(context, mediaStoreOutput)
            .apply { if (audioEnabled) withAudioEnabled() }
            .start(mainThreadExecutor, captureListener)

        Log.i(TAG, "Recording started")
    }

    private val captureListener = Consumer<VideoRecordEvent> { event ->
        if (event !is VideoRecordEvent.Status) recordingState = event
        updateUI(event)

        if (event is VideoRecordEvent.Finalize) {
            callback?.onCaptureSuccess(event.outputResults.outputUri)
        }
    }

    private fun getCameraSelector(idx: Int): CameraSelector {
        if (cameraCapabilities.isEmpty()) {
            callback?.onError("Error: This device does not have any camera, bailing out")
        }
        return cameraCapabilities[idx % cameraCapabilities.size].camSelector
    }

    private fun updateUI(event: VideoRecordEvent) {
        val state =
            if (event is VideoRecordEvent.Status) recordingState.getNameString() else event.getNameString()
        callback?.onUpdateVideoEvent(event)
        val stats = event.recordingStats
        val size = stats.numBytesRecorded / 1000
        val time = java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(stats.recordedDurationNanos)
        val text = "${state}: recorded ${size}KB, in ${time}seconds"
        callback?.onCaptureStatusUpdate("${state}: recorded ${size}KB, in ${time}second")
        if (event is VideoRecordEvent.Finalize) {
            callback?.onCaptureStatusUpdate("$text\nFile saved to: ${event.outputResults.outputUri}")
        }

        Log.i(TAG, "recording event: $text")
    }

    suspend fun initCameraVideo() {
        if (enumerationDeferred != null) {
            enumerationDeferred!!.await()
            enumerationDeferred = null
        }
        bindCaptureUseCase()
    }

    fun destroy() {
        currentRecording?.stop()
        currentRecording = null
        enumerationDeferred = null
        camera = null
    }

    data class CameraCapability(val camSelector: CameraSelector, val qualities: List<Quality>)

    interface CameraVideoCallback {
        fun onCaptureSuccess(uri: Uri)
        fun onCaptureStatusUpdate(status: String)
        fun onError(message: String)
        fun onEnableUI(enable: Boolean)
        fun onUpdateVideoEvent(event: VideoRecordEvent)
    }


    companion object {
        const val DEFAULT_QUALITY_IDX = 0
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}