package com.antigravity.cnvoiceintercept.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.antigravity.cnvoiceintercept.BuildConfig
import com.antigravity.cnvoiceintercept.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    const val GITHUB_REPO_URL = "https://github.com/dx9901mk5/CnVoiceIntercept"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/dx9901mk5/CnVoiceIntercept/releases/latest"
    private const val UPDATE_CHANNEL_ID = "app_update_channel"
    private const val UPDATE_NOTIF_ID = 2002

    data class UpdateInfo(
        val latestVersion: String,
        val currentVersion: String,
        val hasUpdate: Boolean,
        val releasePageUrl: String,
        val apkDownloadUrl: String?,
        val releaseNotes: String
    )

    fun checkForUpdate(
        context: Context,
        sendNotificationIfNew: Boolean = true,
        onResult: (Result<UpdateInfo>) -> Unit
    ) {
        val currentVersion = BuildConfig.VERSION_NAME
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())

        Thread {
            try {
                val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "CnVoiceIntercept/$currentVersion")
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    mainHandler.post {
                        onResult(Result.failure(RuntimeException("HTTP $code")))
                    }
                    return@Thread
                }

                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").trim()
                val latestVersion = tagName.removePrefix("v").removePrefix("V")
                val htmlUrl = json.optString("html_url", "$GITHUB_REPO_URL/releases")
                val notes = json.optString("body", "")

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", null)
                            break
                        }
                    }
                }

                val hasUpdate = isNewerVersion(latestVersion, currentVersion)
                val info = UpdateInfo(
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    hasUpdate = hasUpdate,
                    releasePageUrl = htmlUrl,
                    apkDownloadUrl = apkUrl,
                    releaseNotes = notes
                )

                if (hasUpdate && sendNotificationIfNew) {
                    postUpdateNotification(appContext, info)
                }

                mainHandler.post { onResult(Result.success(info)) }
            } catch (e: Exception) {
                mainHandler.post { onResult(Result.failure(e)) }
            }
        }.start()
    }

    private fun postUpdateNotification(context: Context, info: UpdateInfo) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(ch)
            }

            val targetUrl = info.apkDownloadUrl ?: info.releasePageUrl
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_notif_title, info.latestVersion))
                .setContentText(context.getString(R.string.update_notif_text, info.currentVersion))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()

            nm.notify(UPDATE_NOTIF_ID, notif)
        }
    }

    internal fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
