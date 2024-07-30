package com.example.cameramanager.video.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.example.cameramanager.R
import com.example.cameramanager.databinding.FragmentPermissionsVideoBinding
import com.example.cameravideomanager.PermissionVideoManager

/**
 * This [Fragment] requests permissions and, once granted, it will navigate to the next fragment
 */
class PermissionsVideoFragment : Fragment() {
    private var permissionManager: PermissionVideoManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = PermissionVideoManager(requireContext(), this)
        permissionManager?.checkAndRequestPermissions {
            navigateToCapture()
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentPermissionsVideoBinding.inflate(inflater, container, false).root
    }

    private fun navigateToCapture() {
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(requireActivity(), R.id.fragment_container_video).navigate(
                R.id.action_permissionsVideoFragment_to_captureFragment)
        }
    }
}