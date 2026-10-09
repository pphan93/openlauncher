package com.openlauncher.app.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.google.gson.Gson
import com.openlauncher.app.BuildConfig
import com.openlauncher.app.viewmodel.UpdateStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val gson = Gson()
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    fun checkForUpdates() {
        if (_status.value is UpdateStatus.Checking || _status.value is UpdateStatus.Downloading) return

        scope.launch(Dispatchers.IO) {
            _status.value = UpdateStatus.Checking
            try {
                val release = fetchLatestRelease()
                if (release == null) {
                    _status.value = UpdateStatus.UpToDate(BuildConfig.VERSION_NAME)
                    return@launch
                }
                val latestVersion = release.tagName.trim().removePrefix("v").removePrefix("V")
                if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                    _status.value = UpdateStatus.UpToDate(BuildConfig.VERSION_NAME)
                    return@launch
                }

                val apk = release.assets.firstOrNull {
                    val uri = Uri.parse(it.downloadUrl)
                    it.name.endsWith(".apk", ignoreCase = true) &&
                        uri.scheme == "https" &&
                        uri.host?.equals("github.com", ignoreCase = true) == true
                } ?: error("Release ${release.tagName} does not contain a downloadable APK file.")

                downloadUpdate(latestVersion, apk.downloadUrl)
            } catch (error: Exception) {
                _status.value = UpdateStatus.Error(
                    error.message ?: "Unable to check GitHub for updates."
                )
            }
        }
    }

    fun resumePendingInstall() {
        val current = _status.value
        when (current) {
            is UpdateStatus.AwaitingInstallPermission -> requestInstall(current.version, current.apkUri)
            is UpdateStatus.AwaitingInstallConfirmation -> requestInstall(current.version, current.apkUri)
            else -> Unit
        }
    }

    fun resumeAfterInstallPermission() {
        val current = _status.value as? UpdateStatus.AwaitingInstallPermission ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        ) {
            requestInstall(current.version, current.apkUri)
        }
    }

    private fun fetchLatestRelease(): GitHubRelease? {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "OpenLauncher/${BuildConfig.VERSION_NAME}")

            when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use {
                    gson.fromJson(it, GitHubRelease::class.java)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> error("GitHub update check failed (HTTP $responseCode).")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadUpdate(version: String, downloadUrl: String) {
        val fileName = "openlauncher-$version-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Open Launcher $version")
            .setDescription("Downloading application update")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadId = downloadManager.enqueue(request)
        _status.value = UpdateStatus.Downloading(version, null)

        while (scope.isActive) {
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            cursor.use {
                if (!it.moveToFirst()) {
                    _status.value = UpdateStatus.Error("The update download could not be found.")
                    return
                }

                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
                            ?: error("Android could not open the downloaded APK.")
                        withContext(Dispatchers.Main) {
                            requestInstall(version, apkUri.toString())
                        }
                        return
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        _status.value = UpdateStatus.Error("Update download failed (code $reason).")
                        return
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val percent = if (total > 0L) ((downloaded * 100L) / total).toInt() else null
                        _status.value = UpdateStatus.Downloading(version, percent)
                    }
                }
            }
            delay(500)
        }
    }

    private fun requestInstall(version: String, apkUri: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _status.value = UpdateStatus.AwaitingInstallPermission(version, apkUri)
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(permissionIntent) }
                .onFailure { _status.value = UpdateStatus.Error("Unable to open install permission settings.") }
            return
        }

        _status.value = UpdateStatus.AwaitingInstallConfirmation(version, apkUri)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(apkUri), APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(installIntent) }
            .onFailure { _status.value = UpdateStatus.Error("Unable to open the Android package installer.") }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = VERSION_NUMBER.findAll(latest).map { it.value.toIntOrNull() ?: 0 }.toList()
        val currentParts = VERSION_NUMBER.findAll(current).map { it.value.toIntOrNull() ?: 0 }.toList()
        if (latestParts.isEmpty()) error("The latest release has an invalid version: $latest")

        repeat(maxOf(latestParts.size, currentParts.size)) { index ->
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private data class GitHubRelease(
        @com.google.gson.annotations.SerializedName("tag_name") val tagName: String,
        val assets: List<GitHubAsset> = emptyList()
    )

    private data class GitHubAsset(
        val name: String,
        @com.google.gson.annotations.SerializedName("browser_download_url") val downloadUrl: String
    )

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/pphan93/openlauncher/releases/latest"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        val VERSION_NUMBER = Regex("\\d+")
    }
}
