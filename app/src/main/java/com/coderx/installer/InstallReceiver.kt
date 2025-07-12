package com.coderx.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class InstallReceiver : BroadcastReceiver() {

    companion object {
        const val PACKAGE_INSTALLED_ACTION = "com.coderx.installer.SESSION_API_PACKAGE_INSTALLED"
        private const val TAG = "InstallReceiver"

        // Static reference to MainActivity for communication
        // This will be set when MainActivity is created
        var mainActivityInstance: MainActivity? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra("session_id", -1)

        Log.d(TAG, "Install receiver: status=$status, sessionId=$sessionId")

        // Get the MainActivity instance
        val mainActivity = mainActivityInstance
        if (mainActivity == null) {
            Log.e(TAG, "MainActivity instance not available")
            return
        }

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmationIntent != null) {
                    try {
                        mainActivity.updateInstallProgress("Waiting for user confirmation...")
                        // Start the confirmation activity automatically
                        mainActivity.startActivity(confirmationIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start confirmation intent: ${e.message}", e)
                        mainActivity.handleInstallationError("Failed to show installation prompt: ${e.message}")
                    }
                } else {
                    mainActivity.handleInstallationError("No confirmation intent provided")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                mainActivity.handleInstallationSuccess()
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Unknown error"
                val errorCode = when (status) {
                    PackageInstaller.STATUS_FAILURE -> "Installation failed"
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation aborted"
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation blocked"
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "Package conflict"
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Incompatible package"
                    PackageInstaller.STATUS_FAILURE_INVALID -> "Invalid package"
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Insufficient storage"
                    else -> "Unknown error (code: $status)"
                }
                mainActivity.handleInstallationError("$errorCode: $message")
            }
        }
    }
}