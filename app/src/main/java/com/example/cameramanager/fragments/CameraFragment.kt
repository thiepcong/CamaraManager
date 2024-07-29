/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.cameramanager.fragments

import com.example.cameramanager.R
import android.annotation.SuppressLint
import android.content.*
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraInfoUnavailableException
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.cameramanager.KEY_EVENT_ACTION
import com.example.cameramanager.utils.ANIMATION_FAST_MILLIS
import com.example.cameramanager.utils.ANIMATION_SLOW_MILLIS
import com.example.cameramanager.utils.simulateClick
import com.example.cameramanager.KEY_EVENT_EXTRA
import com.example.cameramanager.databinding.CameraUiContainerBinding
import com.example.cameramanager.databinding.FragmentCameraBinding
import com.example.cameraxmanager.CameraXManager
import com.example.cameraxmanager.PermissionManager
import kotlinx.coroutines.launch
/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : Fragment() {

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private var cameraUiContainerBinding: CameraUiContainerBinding? = null

    private lateinit var broadcastManager: LocalBroadcastManager

    private var displayId: Int = -1
    private lateinit var windowManager: WindowManager

    private lateinit var cameraXManager: CameraXManager

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /** Volume down button receiver used to trigger shutter */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, simulate a shutter button click
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    cameraUiContainerBinding?.cameraCaptureButton?.simulateClick()
                }
            }
        }
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                cameraXManager.imageCapture?.targetRotation = view.display.rotation
                cameraXManager.imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionManager.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(R.id.action_cameraFragment_to_permissionsFragment)
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        cameraXManager.destroy()

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    private fun setGalleryThumbnail(filename: String) {
        // Run the operations in the view's thread
        cameraUiContainerBinding?.photoViewButton?.let { photoViewButton ->
            photoViewButton.post {
                // Remove thumbnail padding
                photoViewButton.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

                // Load thumbnail into circular button using Glide
                Glide.with(photoViewButton)
                    .load(filename)
                    .apply(RequestOptions.circleCropTransform())
                    .into(photoViewButton)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraXManager = CameraXManager(requireContext(),requireActivity().windowManager,fragmentCameraBinding.viewFinder, viewLifecycleOwner)

        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

        // Initialize WindowManager to retrieve display metrics
        windowManager = requireActivity().windowManager

        // Initialize MediaStoreUtils for fetching this app's images
//        mediaStoreUtils = MediaStoreUtils(requireContext())

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {

            // Keep track of the display in which this view is attached
            displayId = fragmentCameraBinding.viewFinder.display.displayId

            // Build UI controls
            updateCameraUi()

            // Set up the camera and its use cases
            lifecycleScope.launch {
                cameraXManager.setUpCamera()
                // Enable or disable switching between cameras
                updateCameraSwitchButton()
            }
        }
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Rebind the camera with the updated display metrics
        cameraXManager.bindCameraUseCases()

        // Enable or disable switching between cameras
        updateCameraSwitchButton()
    }


    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun updateCameraUi() {

        // Remove previous UI if any
        cameraUiContainerBinding?.root?.let {
            fragmentCameraBinding.root.removeView(it)
        }

        cameraUiContainerBinding = CameraUiContainerBinding.inflate(
                LayoutInflater.from(requireContext()),
                fragmentCameraBinding.root,
                true
        )

        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch {
            val thumbnailUri = cameraXManager.getLatestImageFilename()
            thumbnailUri?.let {
                setGalleryThumbnail(it)
            }
        }

        // Listener for button used to capture photo
        cameraUiContainerBinding?.cameraCaptureButton?.setOnClickListener {
            cameraXManager.takePhoto({ output->
                val savedUri = output.savedUri
                Log.d(TAG, "Photo capture succeeded: $savedUri")

                // We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Update the gallery thumbnail with latest picture taken
                    setGalleryThumbnail(savedUri.toString())
                }

                // Implicit broadcasts will be ignored for devices running API level >= 24
                // so if you only target API level 24+ you can remove this statement
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    // Suppress deprecated Camera usage needed for API level 23 and below
                    @Suppress("DEPRECATION")
                    requireActivity().sendBroadcast(
                        Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
                    )
                }
            },{exc->
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            })

                // We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    // Display flash animation to indicate that photo was captured
                    fragmentCameraBinding.root.postDelayed({
                        fragmentCameraBinding.root.foreground = ColorDrawable(Color.WHITE)
                        fragmentCameraBinding.root.postDelayed(
                                { fragmentCameraBinding.root.foreground = null }, ANIMATION_FAST_MILLIS
                        )
                    }, ANIMATION_SLOW_MILLIS)
                }
            }


        // Setup for button used to switch cameras
        cameraUiContainerBinding?.cameraSwitchButton?.let {

            // Disable the button until the camera is set up
            it.isEnabled = false

            // Listener for button used to switch cameras. Only called if the button is enabled
            it.setOnClickListener {
                cameraXManager.switchLenCamera()
            }
        }

        // Listener for button used to view the most recent photo
        cameraUiContainerBinding?.photoViewButton?.setOnClickListener {
            // Only navigate when the gallery has photos
            lifecycleScope.launch {
                if (cameraXManager.getImages().isNotEmpty()) {
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(R.id.action_cameraFragment_to_galleryFragment)

                }
            }
        }
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        try {
            cameraUiContainerBinding?.cameraSwitchButton?.isEnabled = cameraXManager.hasBackCamera() && cameraXManager.hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            cameraUiContainerBinding?.cameraSwitchButton?.isEnabled = false
        }
    }

    companion object {
        private const val TAG = "CameraXBasic"
    }
}
