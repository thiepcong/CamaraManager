package com.example.cameramanager.video.fragments

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.camera.video.VideoRecordEvent
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cameramanager.R
import com.example.cameramanager.databinding.FragmentCaptureBinding
import com.example.cameramanager.video.utils.GenericListAdapter
import com.example.cameravideomanager.CameraXVideoManager
import com.example.cameravideomanager.extensions.getNameString
import kotlinx.coroutines.launch

class CaptureFragment : Fragment(), CameraXVideoManager.CameraVideoCallback {
    private var _captureViewBinding: FragmentCaptureBinding? = null
    private val captureViewBinding get() = _captureViewBinding!!
    private val captureLiveStatus = MutableLiveData<String>()

    private lateinit var cameraManager: CameraXVideoManager

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container_video)
    }
    /**
     * One time initialize for CameraFragment (as a part of fragment layout's creation process).
     * This function performs the following:
     *   - initialize but disable all UI controls except the Quality selection.
     *   - set up the Quality selection recycler view.
     *   - bind use cases to a lifecycle camera, enable UI controls.
     */
    private fun initCameraFragment() {
        initializeUI()
        viewLifecycleOwner.lifecycleScope.launch {
            cameraManager.initCameraVideo()
            initializeQualitySectionsUI()
        }
    }

    /**
     * Initialize UI. Preview and Capture actions are configured in this function.
     * Note that preview and capture are both initialized either by UI or CameraX callbacks
     * (except the very 1st time upon entering to this fragment in onCreateView()
     */
    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    private fun initializeUI() {
        captureViewBinding.cameraButton.apply {
            setOnClickListener {
                initializeQualitySectionsUI()
                enableUI(false)
                cameraManager.switchLenCamera()
            }
            isEnabled = false
        }

        captureViewBinding.flash.setOnClickListener {
            cameraManager.turnFlash(true)
        }
        captureViewBinding.zoomin.setOnClickListener {
            cameraManager.zoom(1.2f)
        }
        captureViewBinding.zoomout.setOnClickListener {
            cameraManager.zoom(0.8f)
        }

        // audioEnabled by default is disabled.
        captureViewBinding.audioSelection.isChecked = cameraManager.audioEnabled
        captureViewBinding.audioSelection.setOnClickListener {
            cameraManager.audioEnabled = captureViewBinding.audioSelection.isChecked
        }

        // React to user touching the capture button
        captureViewBinding.captureButton.apply {
            setOnClickListener {
                cameraManager.handleCapture(
                    onStart = {},
                    onPause = {
                        captureViewBinding.stopButton.visibility = View.VISIBLE
                    },
                    onResume = {

                    }
                )
            }
            isEnabled = false
        }

        captureViewBinding.stopButton.apply {
            setOnClickListener {
                // stopping: hide it after getting a click before we go to viewing fragment
                captureViewBinding.stopButton.visibility = View.INVISIBLE

                cameraManager.stopRecording {
                    captureViewBinding.captureButton.setImageResource(R.drawable.ic_start)
                }
            }
            // ensure the stop button is initialized disabled & invisible
            visibility = View.INVISIBLE
            isEnabled = false
        }

        captureLiveStatus.observe(viewLifecycleOwner) {
            captureViewBinding.captureStatus.apply {
                post { text = it }
            }
        }
        captureLiveStatus.value = getString(R.string.Idle)
    }

    /**
     * Enable/disable UI:
     *    User could select the capture parameters when recording is not in session
     *    Once recording is started, need to disable able UI to avoid conflict.
     */
    private fun enableUI(enable: Boolean) {
        arrayOf(
            captureViewBinding.cameraButton,
            captureViewBinding.captureButton,
            captureViewBinding.stopButton,
            captureViewBinding.audioSelection,
            captureViewBinding.qualitySelection
        ).forEach {
            it.isEnabled = enable
        }
        // disable the camera button if no device to switch
        if (cameraManager.cameraCapabilities.size <= 1) {
            captureViewBinding.cameraButton.isEnabled = false
        }
        // disable the resolution list if no resolution to switch
        if (cameraManager.cameraCapabilities[cameraManager.cameraIndex].qualities.size <= 1) {
            captureViewBinding.qualitySelection.apply { isEnabled = false }
        }
    }

    // Camera UI  states and inputs
    enum class UiState {
        IDLE,       // Not recording, all UI controls are active.
        RECORDING,  // Camera is recording, only display Pause/Resume & Stop button.
        FINALIZED,  // Recording just completes, disable all RECORDING UI controls.
        RECOVERY    // For future use.
    }

    /**
     * initialize UI for recording:
     *  - at recording: hide audio, qualitySelection,change camera UI; enable stop button
     *  - otherwise: show all except the stop button
     */
    private fun showUI(state: UiState, status: String = "idle") {
        captureViewBinding.let {
            when (state) {
                UiState.IDLE -> {
                    it.captureButton.setImageResource(R.drawable.ic_start)
                    it.stopButton.visibility = View.INVISIBLE

                    it.cameraButton.visibility = View.VISIBLE
                    it.audioSelection.visibility = View.VISIBLE
                    it.qualitySelection.visibility = View.VISIBLE
                }

                UiState.RECORDING -> {
                    it.cameraButton.visibility = View.INVISIBLE
                    it.audioSelection.visibility = View.INVISIBLE
                    it.qualitySelection.visibility = View.INVISIBLE

                    it.captureButton.setImageResource(R.drawable.ic_pause)
                    it.captureButton.isEnabled = true
                    it.stopButton.visibility = View.VISIBLE
                    it.stopButton.isEnabled = true
                }

                UiState.FINALIZED -> {
                    it.captureButton.setImageResource(R.drawable.ic_start)
                    it.stopButton.visibility = View.INVISIBLE
                }

                else -> {
                    val errorMsg = "Error: showUI($state) is not supported"
                    Log.e(TAG, errorMsg)
                    return
                }
            }
            it.captureStatus.text = status
        }
    }

    private fun initializeQualitySectionsUI() {
        cameraManager.getQualitySections { qualities, currentQualityIndex ->
            val selectorStrings = qualities.map {
                it.getNameString()
            }
            captureViewBinding.qualitySelection.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = GenericListAdapter(
                    selectorStrings,
                    itemLayoutId = R.layout.video_quality_item
                ) { holderView, qcString, position ->

                    holderView.apply {
                        findViewById<TextView>(R.id.qualityTextView)?.text = qcString
                        // select the default quality selector
                        isSelected = (position == currentQualityIndex)
                    }

                    holderView.setOnClickListener { view ->
                        if (currentQualityIndex == position) return@setOnClickListener

                        captureViewBinding.qualitySelection.let {
                            // deselect the previous selection on UI.
                            it.findViewHolderForAdapterPosition(currentQualityIndex)
                                ?.itemView
                                ?.isSelected = false
                        }
                        // turn on the new selection on UI.
                        view.isSelected = true
                        cameraManager.qualityIndex = position

                        // rebind the use cases to put the new QualitySelection in action.
                        enableUI(false)
                        viewLifecycleOwner.lifecycleScope.launch {
                            cameraManager.bindCaptureUseCase()
                        }
                    }
                }
                isEnabled = false
            }
        }
    }

    // System function implementations
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

    override fun onDestroyView() {
        _captureViewBinding = null
        super.onDestroyView()
        cameraManager.destroy()
    }

    companion object {
        val TAG: String = CaptureFragment::class.java.simpleName
    }

    override fun onCaptureSuccess(uri: Uri) {
        val bundle = bundleOf("videoUri" to uri)
        // display the captured video
        lifecycleScope.launch {
            navController.navigate(R.id.action_captureFragment_to_videoViewerFragment, bundle)
        }
    }

    override fun onUpdateVideoEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Status -> {
                // placeholder: we update the UI with new status after this when() block,
                // nothing needs to do here.
            }

            is VideoRecordEvent.Start -> {
                showUI(UiState.RECORDING, event.getNameString())
            }

            is VideoRecordEvent.Finalize -> {
                showUI(UiState.FINALIZED, event.getNameString())
            }

            is VideoRecordEvent.Pause -> {
                captureViewBinding.captureButton.setImageResource(R.drawable.ic_resume)
            }

            is VideoRecordEvent.Resume -> {
                captureViewBinding.captureButton.setImageResource(R.drawable.ic_pause)
            }
        }
    }

    override fun onCaptureStatusUpdate(status: String) {
        captureLiveStatus.value = status
        Log.i(TAG, "recording event: $status")
    }

    override fun onEnableUI(enable: Boolean) {
        this.enableUI(enable)
    }

    override fun onError(message: String) {
        enableUI(true)
        showUI(UiState.IDLE, message)

        captureViewBinding.audioSelection.isChecked = cameraManager.audioEnabled
        initializeQualitySectionsUI()
    }
}