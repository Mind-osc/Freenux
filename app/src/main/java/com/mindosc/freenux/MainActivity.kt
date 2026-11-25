package com.mindosc.freenux

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var linux: Linux
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private val listener: () -> Unit = { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        linux = Linux(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(0xFF1B1F2A.toInt())
        }
        fun text(s: String, size: Float, color: Int = 0xFFE6E6E6.toInt()) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color); setPadding(0, pad / 4, 0, pad / 4)
        }
        fun button(s: String, onClick: () -> Unit) = Button(this).apply {
            text = s; isAllCaps = false; setOnClickListener { onClick() }
            root.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        root.addView(text("Freenux", 28f, 0xFFA6E3A1.toInt()))
        root.addView(text("Debian + XFCE στο κινητό. Σύνδεσε οθόνη και γίνεται PC.", 14f))
        status = text("", 16f, 0xFFF9E2AF.toInt())
        root.addView(status)

        button("① Εγκατάσταση Debian + desktop") { LinuxService.send(this, LinuxService.ACTION_INSTALL) }
        button("Εγκατάσταση Claude Code") { LinuxService.send(this, LinuxService.ACTION_CLAUDE) }
        button("Άνοιγμα desktop εδώ") { openDesktop(Display.DEFAULT_DISPLAY) }
        button("Άνοιγμα στην εξωτερική οθόνη") {
            val ext = LinuxService.externalDisplays(this).firstOrNull()
            if (ext == null) LogBus.log("Δεν βρέθηκε εξωτερική οθόνη.") else openDesktop(ext.displayId)
        }
        button("Επανεκκίνηση desktop (αν κόλλησε / μαύρη οθόνη)") {
            LinuxService.send(this, LinuxService.ACTION_RESTART)
        }
        button("Τερματισμός Linux") { LinuxService.send(this, LinuxService.ACTION_STOP) }
        button("Άδειες (αρχεία, πάνω από εφαρμογές, μπαταρία)") { askPermissions() }

        root.addView(Switch(this).apply {
            text = "Αυτόματο desktop όταν συνδέω οθόνη"
            setTextColor(0xFFE6E6E6.toInt())
            isChecked = LinuxService.autoEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                LinuxService.prefs(this@MainActivity).edit().putBoolean("auto", on).apply()
                if (on) LinuxService.send(this@MainActivity, LinuxService.ACTION_WATCH)
            }
        })

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 11f; setTextColor(0xFFB0B8C8.toInt())
            setTextIsSelectable(true)
        }
        logScroll = ScrollView(this).apply {
            addView(logView)
            setBackgroundColor(0xFF11141B.toInt())
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
        }
        root.addView(logScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = pad })
        setContentView(root)

        if (LinuxService.autoEnabled(this)) {
            LinuxService.send(this, LinuxService.ACTION_WATCH)
            // Αν η οθόνη είναι ήδη συνδεδεμένη
            if (linux.setupDone) LinuxService.externalDisplays(this).firstOrNull()?.let { openDesktop(it.displayId) }
        }
    }

    override fun onResume() {
        super.onResume()
        LogBus.add(listener)
        refresh()
    }

    override fun onPause() {
        LogBus.remove(listener)
        super.onPause()
    }

    private fun refresh() {
        val inst = when {
            linux.setupDone -> "εγκατεστημένο" + if (linux.claudeDone) " · Claude Code ✓" else ""
            linux.rootfsReady -> "μισή εγκατάσταση – πάτα ①"
            else -> "όχι ακόμα – πάτα ①"
        }
        status.text = "Debian: $inst\nΚατάσταση: ${LogBus.state}"
        logView.text = LogBus.text()
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun openDesktop(displayId: Int) {
        if (!linux.setupDone) { LogBus.log("Κάνε πρώτα «Εγκατάσταση»."); return }
        LinuxService.send(this, LinuxService.ACTION_START)
        LinuxService.launchDesktop(this, displayId)
    }

    private fun askPermissions() {
        requestPermissions(arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ), 1)
        if (!Settings.canDrawOverlays(this)) {
            // Χρειάζεται για να ανοίγει μόνο του το desktop όταν η εφαρμογή είναι στο παρασκήνιο
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }
}
