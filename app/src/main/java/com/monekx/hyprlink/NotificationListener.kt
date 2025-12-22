package com.monekx.hyprlink

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!sbn.isClearable) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getString(Notification.EXTRA_TEXT)
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        if (!title.isNullOrEmpty() || !text.isNullOrEmpty()) {
            val intent = Intent(this, HyprLinkService::class.java).apply {
                action = "SEND_NOTIFICATION"
                putExtra("title", title ?: "")
                putExtra("text", text ?: "")
                putExtra("app", appName)
            }
            startService(intent)
        }
    }
}