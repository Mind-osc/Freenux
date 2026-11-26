package com.mindosc.freenux

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.io.File

/** Στέλνει τις ειδοποιήσεις του Android στο XFCE (γραμμή σε /tmp/freenux-notify μέσα στο Linux). */
class NotifyBridge : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!LinuxService.desktopRunning) return
        if (!LinuxService.prefs(this).getBoolean("notify", true)) return
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (sbn.isOngoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) { sbn.packageName }

        fun clean(s: String, max: Int) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').take(max)
        val line = clean(app, 40) + "\t" + clean(title, 120) + "\t" + clean(text, 300) + "\n"
        try {
            File(Linux(this).rootfs, "tmp/freenux-notify").appendText(line)
        } catch (_: Exception) {}
    }
}
