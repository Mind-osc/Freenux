package com.mindosc.freenux

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import kotlin.concurrent.thread

/** Κρατάει ζωντανό το Linux και ανοίγει το desktop όταν συνδέεται εξωτερική οθόνη. */
class LinuxService : Service() {

    companion object {
        const val ACTION_WATCH = "watch"
        const val ACTION_INSTALL = "install"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_CLAUDE = "claude"
        private const val CH = "freenux"

        @Volatile var desktopRunning = false
            private set

        fun send(ctx: Context, action: String) {
            ctx.startForegroundService(Intent(ctx, LinuxService::class.java).setAction(action))
        }

        fun prefs(ctx: Context) = ctx.getSharedPreferences("freenux", Context.MODE_PRIVATE)
        fun autoEnabled(ctx: Context) = prefs(ctx).getBoolean("auto", true)

        fun isExternal(d: Display?) =
            d != null && d.displayId != Display.DEFAULT_DISPLAY && (d.flags and Display.FLAG_PRIVATE) == 0

        fun externalDisplays(ctx: Context): List<Display> =
            (ctx.getSystemService(DISPLAY_SERVICE) as DisplayManager).displays.filter { isExternal(it) }

        /** Ανοίγει το desktop στη συγκεκριμένη οθόνη. */
        fun launchDesktop(ctx: Context, displayId: Int) {
            val i = Intent(ctx, DesktopActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(DesktopActivity.EXTRA_EXTERNAL, displayId != Display.DEFAULT_DISPLAY)
            if (displayId != Display.DEFAULT_DISPLAY) i.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            try { ctx.startActivity(i, opts.toBundle()) } catch (e: Exception) {
                LogBus.log("Δεν άνοιξε στην οθόνη $displayId: ${e.message}")
            }
        }
    }

    private lateinit var linux: Linux
    private lateinit var dm: DisplayManager
    private var desktop: Process? = null
    @Volatile private var busy = false
    private var wake: PowerManager.WakeLock? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) {
            val d = dm.getDisplay(id)
            if (!isExternal(d)) return
            LogBus.log("Εξωτερική οθόνη: ${d.name}")
            if (!autoEnabled(this@LinuxService) || !linux.setupDone) return
            startDesktop()
            launchDesktop(this@LinuxService, id)
        }
        override fun onDisplayRemoved(id: Int) {}
        override fun onDisplayChanged(id: Int) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        linux = Linux(this)
        dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "Freenux", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(1, notification("Σε αναμονή για οθόνη"))
        dm.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INSTALL -> background("Εγκατάσταση…") { install() }
            ACTION_CLAUDE -> background("Εγκατάσταση Claude Code…") {
                if (!linux.setupDone) LogBus.log("Κάνε πρώτα την εγκατάσταση του Debian.")
                else if (linux.run("bash /usr/local/bin/freenux-claude") == 0) LogBus.log("✓ Claude Code εγκαταστάθηκε")
                else LogBus.log("✗ Η εγκατάσταση του Claude Code απέτυχε (δες το log)")
            }
            ACTION_START -> startDesktop()
            ACTION_STOP -> stopDesktop(stopService = !autoEnabled(this))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        dm.unregisterDisplayListener(displayListener)
        desktop?.destroy()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun background(state: String, job: () -> Unit) {
        if (busy) { LogBus.log("Τρέχει ήδη μια εργασία…"); return }
        busy = true
        LogBus.setState(state)
        thread {
            try { job() } catch (e: Exception) { LogBus.log("Σφάλμα: ${e.message}") }
            busy = false
            LogBus.setState(if (desktopRunning) "Τρέχει" else "Σταματημένο")
        }
    }

    private fun install() {
        if (!linux.rootfsReady) RootfsInstaller(this, linux).install()
        if (!linux.setupDone) {
            LogBus.log("Ρύθμιση Debian + XFCE + Firefox (χρειάζεται ίντερνετ, ~1–1.5 GB)…")
            val code = linux.run("bash /usr/local/bin/freenux-setup")
            LogBus.log(if (code == 0) "✓ Εγκατάσταση ολοκληρώθηκε" else "✗ Η ρύθμιση σταμάτησε (κωδικός $code). Πάτα ξανά Εγκατάσταση για συνέχεια.")
        } else LogBus.log("Ήδη εγκατεστημένο.")
    }

    @Synchronized
    private fun startDesktop() {
        if (desktop?.isAlive == true) return
        if (!linux.setupDone) { LogBus.log("Κάνε πρώτα «Εγκατάσταση»."); return }
        linux.prepareHostFiles()
        LogBus.setState("Ξεκινάει…")
        val p = linux.command("/usr/local/bin/freenux-desktop").start()
        desktop = p
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "freenux:desktop").apply { acquire() }
        thread { p.inputStream.bufferedReader().forEachLine { LogBus.log(it) } }
        thread {
            for (i in 0 until 600) {
                if (!p.isAlive) break
                if (Linux.portOpen(Linux.NOVNC_PORT)) {
                    desktopRunning = true
                    LogBus.setState("Τρέχει")
                    updateNotification("Το desktop τρέχει")
                    break
                }
                Thread.sleep(200)
            }
            val code = p.waitFor()
            desktopRunning = false
            wake?.let { if (it.isHeld) it.release() }
            LogBus.log("Το desktop σταμάτησε ($code)")
            LogBus.setState("Σταματημένο")
            updateNotification("Σε αναμονή για οθόνη")
        }
    }

    private fun stopDesktop(stopService: Boolean) {
        desktop?.destroy()
        desktop = null
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, LinuxService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Freenux")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Τερματισμός", stop).build())
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, notification(text))
    }
}
