package com.rosseti.cardata

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.io.File
import androidx.core.net.toUri

/**
 * Класс для управления процессом обновления приложения через Firebase Remote Config.
 * Позволяет проверять наличие новой версии, скачивать APK и инициировать установку.
 */
class UpdateManager(private val context: Context) {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // Проверка раз в час
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
    }

    /**
     * Проверяет наличие обновлений на сервере.
     */
    fun checkForUpdates() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val latestVersionCode = remoteConfig.getLong("latest_version_code")
                val currentVersionCode = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
                } catch (_: Exception) {
                    0L
                }

                if (latestVersionCode > currentVersionCode) {
                    val apkUrl = remoteConfig.getString("apk_url")
                    val versionName = remoteConfig.getString("latest_version_name")
                    showUpdateDialog(versionName, apkUrl)
                }
            }
        }
    }

    private fun showUpdateDialog(versionName: String, apkUrl: String) {
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("New version $versionName is available. Would you like to update?")
            .setPositiveButton("Update") { _, _ ->
                downloadAndInstallApk(apkUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstallApk(url: String) {
        if (url.isEmpty()) return

        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "tracklit_update.apk")
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(url.toUri())
            .setTitle("Downloading TrackLit Update")
            .setDescription("Downloading new version...")
            .setDestinationUri(Uri.fromFile(destination))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    installApk(destination)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, filter, Context.RECEIVER_EXPORTED)
        } else {
			ContextCompat.registerReceiver(
		        context,
		        onComplete,
		        filter,
		        ContextCompat.RECEIVER_NOT_EXPORTED
	        )
        }
        
        Toast.makeText(context, "Downloading started...", Toast.LENGTH_SHORT).show()
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
