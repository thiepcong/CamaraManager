package com.example.cameramanager.photo.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.example.cameramanager.R
import com.example.cameraxmanager.PermissionPhotoManager

/**
 * The sole purpose of this fragment is to request permissions and, once granted, display the
 * camera fragment to the user.
 */
class PermissionsFragment : Fragment() {
    private var permissionManager: PermissionPhotoManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionPhotoManager(requireContext(), this)
        permissionManager?.checkAndRequestPermissions {
            navigateToCamera()
        }
    }

    private fun navigateToCamera() {
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(R.id.action_permissionsFragment_to_cameraFragment)
        }
    }
}
