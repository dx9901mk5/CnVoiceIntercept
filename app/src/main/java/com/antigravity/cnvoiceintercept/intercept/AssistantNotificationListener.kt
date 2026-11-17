package com.antigravity.cnvoiceintercept.intercept

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.antigravity.cnvoiceintercept.config.AppExpiry
import com.antigravity.cnvoiceintercept.config.AppPrefs
import com.antigravity.cnvoiceintercept.shizuku.ShizukuController

/**
 * 專職攔截「語音呼叫喚醒」時系統或陸版助理發出的收音狀態通知（例如「收音 (藍心小V)」）。
 * 當使用者在桌面手動點開陸版助理 App 圖示直接使用時，不會觸發此語音收音通知，因此不會干涉手動開啟使用。
 */
class AssistantNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (AppExpiry.isExpired()) return
        ShizukuController.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (AppExpiry.isExpired()) return
        if (!AppPrefs.isEnabled(applicationContext)) return
        if (sbn.packageName == packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val ticker = sbn.notification?.tickerText?.toString().orEmpty()

        val combined = "$title $text $subText $ticker"
        val keywords = AppPrefs.getNotifKeywordsList(applicationContext)

        if (keywords.any { kw -> combined.contains(kw, ignoreCase = true) }) {
            val targetPkgs = AppPrefs.getTargetPackagesList(applicationContext)
            val killPkg = if (sbn.packageName in targetPkgs) sbn.packageName else null
            InterceptCoordinator.onInterceptTriggered(
                context = applicationContext,
                source = "語音收音通知(${sbn.packageName})",
                detectedPackage = killPkg
            )
        }
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val cn = ComponentName(context, AssistantNotificationListener::class.java).flattenToString()
            return flat.split(":").any { it.equals(cn, ignoreCase = true) }
        }
    }
}
