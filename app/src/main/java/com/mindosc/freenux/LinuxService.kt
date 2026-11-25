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
        const val ACTION_RESTART = "restart"
        private const val CH = "freenux"

        @Volatile var desktopRunning = false
            private set

        fun send(ctx: Context, action: String) {
            ctx.startForegroundService(Intent(ctx, LinuxService::class.java).setAction(action))
        }

        const val X11_PKG = "com.termux.x11"
        const val X11_APK_URL =
            "https://github.com/termux/termux-x11/releases/download/nightly/termux-x11-universal-release.apk"

        fun x11Installed(ctx: Context) = try {
            ctx.packageManager.getApplicationInfo(X11_PKG, 0); true
        } catch (_: Exception) { false }

        /** Γρήγορη οθόνη: native X11 μέσω Termux:X11 αντί για VNC. */
        fun useX11(ctx: Context) = x11Installed(ctx) && prefs(ctx).getBoolean("x11", true)

        fun prefs(ctx: Context) = ctx.getSharedPreferences("freenux", Context.MODE_PRIVATE)
        fun autoEnabled(ctx: Context) = prefs(ctx).getBoolean("auto", true)

        fun isExternal(d: Display?) =
            d != null && d.displayId != Display.DEFAULT_DISPLAY && (d.flags and Display.FLAG_PRIVATE) == 0

        fun externalDisplays(ctx: Context): List<Display> =
            (ctx.getSystemService(DISPLAY_SERVICE) as DisplayManager).displays.filter { isExternal(it) }

        /** Ανοίγει το desktop στη συγκεκριμένη οθόνη. */
        fun launchDesktop(ctx: Context, displayId: Int) {
            val i = if (useX11(ctx))
                Intent().setClassName(X11_PKG, "$X11_PKG.MainActivity")
            else Intent(ctx, DesktopActivity::class.java)
            i
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(DesktopActivity.EXTRA_EXTERNAL, displayId != Display.DEFAULT_DISPLAY)
            if (displayId != Display.DEFAULT_DISPLAY && !useX11(ctx)) i.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            try { ctx.startActivity(i, opts.toBundle()) } catch (e: Exception) {
                LogBus.log("Δεν άνοιξε στην οθόνη $displayId: ${e.message}")
            }
        }
    }

    private lateinit var linux: Linux
    private lateinit var dm: DisplayManager
    private var desktop: Process? = null
    private var xserver: Process? = null
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
            ACTION_RESTART -> restartDesktop()
            ACTION_STOP -> stopDesktop(stopService = !autoEnabled(this))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        dm.unregisterDisplayListener(displayListener)
        desktop?.destroy()
        xserver?.destroy()
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
            if (code != 0) {
                LogBus.log("✗ Η ρύθμιση σταμάτησε (κωδικός $code). Πάτα ξανά ① για συνέχεια.")
                return
            }
            LogBus.log("✓ Βασική εγκατάσταση ολοκληρώθηκε")
        }
        if (!linux.updateDone) {
            LogBus.log("Ενημέρωση: εφαρμογή Claude, θέματα, εικονίδια, φόντα…")
            val code = linux.run("bash /usr/local/bin/freenux-update")
            LogBus.log(if (code == 0) "✓ Όλα έτοιμα" else "✗ Η ενημέρωση σταμάτησε (κωδικός $code). Πάτα ξανά ①.")
        } else LogBus.log("Όλα εγκατεστημένα και ενημερωμένα.")
    }

    @Synchronized
    private fun startDesktop() {
        if (desktop?.isAlive == true) return
        if (!linux.setupDone) { LogBus.log("Κάνε πρώτα «Εγκατάσταση»."); return }
        linux.prepareHostFiles()
        LogBus.setState("Ξεκινάει…")
        val x11 = useX11(this)
        if (x11 && !startXServer()) return
        LogBus.log(if (x11) ">>> Λειτουργία: γρήγορη οθόνη (Termux:X11)" else ">>> Λειτουργία: συμβατή (VNC)")
        val p = linux.command(if (x11) "/usr/local/bin/freenux-desktop-x11" else "/usr/local/bin/freenux-desktop").start()
        desktop = p
        if (wake?.isHeld != true) wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "freenux:desktop").apply { acquire() }
        thread { p.inputStream.bufferedReader().forEachLine { LogBus.log(it) } }
        val x0 = java.io.File(linux.rootfs, "tmp/.X11-unix/X0")
        thread {
            for (i in 0 until 3000) {
                if (!p.isAlive) break
                if (if (x11) x0.exists() else Linux.portOpen(Linux.NOVNC_PORT)) {
                    desktopRunning = true
                    LogBus.setState("Τρέχει")
                    updateNotification("Το desktop τρέχει")
                    break
                }
                Thread.sleep(200)
            }
            val code = p.waitFor()
            if (desktop !== p && desktop != null) return@thread // έγινε επανεκκίνηση
            desktopRunning = false
            wake?.let { if (it.isHeld) it.release() }
            LogBus.log("Το desktop σταμάτησε ($code)")
            LogBus.setState("Σταματημένο")
            updateNotification("Σε αναμονή για οθόνη")
        }
    }

    /** Ξεκινάει τον X server του Termux:X11 με socket μέσα στο /tmp του Debian. */
    private fun startXServer(): Boolean {
        if (xserver?.isAlive == true) return true
        val apk = try { packageManager.getApplicationInfo(X11_PKG, 0).sourceDir } catch (_: Exception) {
            LogBus.log("Το Termux:X11 δεν είναι εγκατεστημένο."); return false
        }
        val tmp = java.io.File(linux.rootfs, "tmp")
        val sock = java.io.File(tmp, ".X11-unix").apply { mkdirs() }
        Linux.chmod(sock, "1777")
        Linux.rm(java.io.File(sock, "X0")); Linux.rm(java.io.File(tmp, ".X0-lock"))
        val pb = ProcessBuilder("/system/bin/app_process", "-Xnoimage-dex2oat", "/",
            "--nice-name=freenux-x11", "$X11_PKG.CmdEntryPoint", ":0",
            // Η κοινή μνήμη (MIT-SHM) δεν περνάει σωστά από το proot και κολλάει τα προγράμματα
            "-extension", "MIT-SHM",
            // Το DRI3 κάνει τη Mesa μέσα στο proot να κολλάει (GTK δεν ανοίγει ποτέ παράθυρο)
            "-extension", "DRI3").redirectErrorStream(true)
        pb.environment().apply {
            put("CLASSPATH", apk)
            put("TMPDIR", tmp.absolutePath)
            remove("LD_LIBRARY_PATH"); remove("LD_PRELOAD")
        }
        LogBus.log(">>> X server (Termux:X11)")
        val x = pb.start()
        xserver = x
        thread { x.inputStream.bufferedReader().forEachLine { LogBus.log("[X11] $it") } }
        return true
    }

    /** Reset: κλείνει όλο το Linux desktop και το ξεκινάει από την αρχή. */
    private fun restartDesktop() {
        LogBus.log(">>> Επανεκκίνηση desktop")
        LogBus.setState("Επανεκκίνηση…")
        val old = listOfNotNull(desktop, xserver)
        desktop = null
        xserver = null
        thread {
            old.forEach {
                it.destroy()
                if (!it.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) it.destroyForcibly()
            }
            for (i in 0 until 25) { if (!Linux.portOpen(Linux.NOVNC_PORT)) break; Thread.sleep(200) }
            startDesktop()
        }
    }

    private fun stopDesktop(stopService: Boolean) {
        desktop?.destroy()
        desktop = null
        xserver?.destroy()
        xserver = null
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
