package com.example.cameraphotomanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
private var PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)
class PermissionPhotoManager(private val context: Context, caller: ActivityResultCaller) {
    init {
        // Add the storage access permission request for Android 9 and below.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permissionList = PERMISSIONS_REQUIRED.toMutableList()
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            PERMISSIONS_REQUIRED = permissionList.toTypedArray()
        }
    }

    private val activityResultLauncher =
        caller.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in PERMISSIONS_REQUIRED && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
            } else {
                onPermissionsGranted()
            }
        }

    fun checkAndRequestPermissions(onGranted: () -> Unit) {
        if (!hasPermissions()) {
            onPermissionsGranted = onGranted
            activityResultLauncher.launch(PERMISSIONS_REQUIRED)
        } else {
            onGranted()
        }
    }

    private var onPermissionsGranted: () -> Unit = {}

    private fun hasPermissions() = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        /** Convenience method used to check if all permissions required by this app are granted */
        fun hasPermissions(context: Context) = arrayOf(Manifest.permission.CAMERA).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}