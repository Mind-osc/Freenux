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
    private lateinit var x11Switch: Switch
    private lateinit var x11Get: Button
    private lateinit var sshSwitch: Switch
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
        // Κουμπιά και διακόπτες σε δική τους περιοχή που κυλάει· το log από κάτω με δική του
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun button(s: String, onClick: () -> Unit) = Button(this).apply {
            text = s; isAllCaps = false; setOnClickListener { onClick() }
            controls.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        root.addView(text("Freenux", 28f, 0xFFA6E3A1.toInt()))
        root.addView(text("Debian + XFCE στο κινητό. Σύνδεσε οθόνη και γίνεται PC.", 14f))
        status = text("", 16f, 0xFFF9E2AF.toInt())
        root.addView(status)
        root.addView(ScrollView(this).apply { addView(controls) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        button("① Εγκατάσταση / Ενημέρωση") { LinuxService.send(this, LinuxService.ACTION_INSTALL) }
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
        button("Backup στο κινητό (/sdcard/Freenux)") {
            confirm("Backup", "Αποθήκευση όλου του Linux στο /sdcard/Freenux. Θέλει 10–20 λεπτά και αρκετό χώρο.") {
                LinuxService.send(this, LinuxService.ACTION_BACKUP)
            }
        }
        button("Επαναφορά από backup") {
            confirm("Επαναφορά", "Το τωρινό Linux θα σβηστεί και θα αντικατασταθεί από το backup στο /sdcard/Freenux. Συνέχεια;") {
                LinuxService.send(this, LinuxService.ACTION_RESTORE)
            }
        }
        button("Touchpad (το κινητό ως ποντίκι και πληκτρολόγιο)") {
            startActivity(Intent(this, TouchpadActivity::class.java))
        }
        button("Πακέτα εφαρμογών (Γραφείο, Γραφίστας, Προγραμματιστής, VLC, Windows)") { chooseBundles() }
        button("Άδειες (αρχεία, πάνω από εφαρμογές, μπαταρία)") { askPermissions() }
        button("Άδεια ειδοποιήσεων (για να βλέπεις τις ειδοποιήσεις στο Linux)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        x11Get = button("Κατέβασε Termux:X11 (γρήγορη οθόνη, έως 120Hz)") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LinuxService.X11_APK_URL)))
        }
        controls.addView(Switch(this).apply {
            text = "Το εικονίδιο ανοίγει κατευθείαν το desktop"
            setTextColor(0xFFE6E6E6.toInt())
            isChecked = quickStart()
            setOnCheckedChangeListener { _, on -> LinuxService.prefs(this@MainActivity).edit().putBoolean("quick", on).apply() }
        })
        x11Switch = Switch(this).apply {
            text = "Γρήγορη οθόνη (Termux:X11 αντί για VNC)"
            setTextColor(0xFFE6E6E6.toInt())
            setOnCheckedChangeListener { _, on ->
                if (LinuxService.prefs(this@MainActivity).getBoolean("x11", true) != on) {
                    LinuxService.prefs(this@MainActivity).edit().putBoolean("x11", on).apply()
                    LogBus.log(if (on) "Γρήγορη οθόνη: ΝΑΙ – πάτα «Επανεκκίνηση desktop»" else "Γρήγορη οθόνη: ΟΧΙ (VNC) – πάτα «Επανεκκίνηση desktop»")
                }
            }
        }
        controls.addView(x11Switch)
        controls.addView(Switch(this).apply {
            text = "GPU επιτάχυνση (πειραματικό)"
            setTextColor(0xFFE6E6E6.toInt())
            isChecked = LinuxService.gpuEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                LinuxService.prefs(this@MainActivity).edit().putBoolean("gpu", on).apply()
                LogBus.log("GPU: " + (if (on) "ΝΑΙ" else "ΟΧΙ") + " – πάτα «Επανεκκίνηση desktop»")
            }
        })

        controls.addView(Switch(this).apply {
            text = "Ειδοποιήσεις του κινητού μέσα στο Linux"
            setTextColor(0xFFE6E6E6.toInt())
            isChecked = LinuxService.prefs(this@MainActivity).getBoolean("notify", true)
            setOnCheckedChangeListener { _, on -> LinuxService.prefs(this@MainActivity).edit().putBoolean("notify", on).apply() }
        })
        controls.addView(Switch(this).apply {
            text = "Αυτόματο backup κάθε εβδομάδα (όταν φορτίζει)"
            setTextColor(0xFFE6E6E6.toInt())
            isChecked = LinuxService.prefs(this@MainActivity).getBoolean("autobackup", false)
            setOnCheckedChangeListener { _, on ->
                LinuxService.prefs(this@MainActivity).edit().putBoolean("autobackup", on).apply()
                BackupJob.schedule(this@MainActivity, on)
                LogBus.log(if (on) "Αυτόματο backup: κάθε εβδομάδα όταν φορτίζει" else "Αυτόματο backup: ΟΧΙ")
            }
        })
        controls.addView(Switch(this).apply {
            text = "Touchpad στο κινητό όταν το desktop ανοίγει σε μόνιτορ"
            setTextColor(0xFFE6E6E6.toInt())
            isChecked = LinuxService.prefs(this@MainActivity).getBoolean("touchpad", true)
            setOnCheckedChangeListener { _, on -> LinuxService.prefs(this@MainActivity).edit().putBoolean("touchpad", on).apply() }
        })
        controls.addView(Switch(this).apply {
            text = "Μικρόφωνο κινητού στο Linux"
            setTextColor(0xFFE6E6E6.toInt())
            isChecked = LinuxService.prefs(this@MainActivity).getBoolean("mic", false)
            setOnCheckedChangeListener { _, on ->
                if (on && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
                }
                LinuxService.prefs(this@MainActivity).edit().putBoolean("mic", on).apply()
                LinuxService.send(this@MainActivity, LinuxService.ACTION_MIC)
                LogBus.log(if (on) "Μικρόφωνο: ΝΑΙ (όσο τρέχει το desktop θα ανάβει η πράσινη ένδειξη του Android)" else "Μικρόφωνο: ΟΧΙ")
            }
        })
        sshSwitch = Switch(this).apply {
            text = "SSH από άλλο υπολογιστή (θύρα 8022)"
            setTextColor(0xFFE6E6E6.toInt())
            isChecked = LinuxService.prefs(this@MainActivity).getBoolean("ssh", false)
            setOnCheckedChangeListener { _, on ->
                if (!isPressed) return@setOnCheckedChangeListener
                if (on) askSshPassword() else {
                    LinuxService.prefs(this@MainActivity).edit().putBoolean("ssh", false).apply()
                    linux.setSsh(false)
                    LogBus.log("SSH: ΟΧΙ – πάτα «Επανεκκίνηση desktop»")
                }
            }
        }
        controls.addView(sshSwitch)

        controls.addView(Switch(this).apply {
            text = "Αυτόματο desktop όταν συνδέω οθόνη"
            setTextColor(0xFFE6E6E6.toInt())
            isChecked = LinuxService.autoEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                LinuxService.prefs(this@MainActivity).edit().putBoolean("auto", on).apply()
                if (on) LinuxService.send(this@MainActivity, LinuxService.ACTION_WATCH)
            }
        })

        val logRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(logRow.apply {
            fun small(t: String, f: () -> Unit) = Button(this@MainActivity).apply {
                text = t; isAllCaps = false; setOnClickListener { f() }
                logRow.addView(this, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            }
            small("📋 Αντιγραφή log") {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Freenux log", LogBus.text()))
                android.widget.Toast.makeText(this@MainActivity, "Το log αντιγράφηκε", android.widget.Toast.LENGTH_SHORT).show()
            }
            small("📤 Κοινοποίηση log") {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Freenux log")
                    putExtra(Intent.EXTRA_TEXT, LogBus.text())
                }, "Κοινοποίηση log"))
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = pad / 2 })

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 11f; setTextColor(0xFFB0B8C8.toInt())
            setTextIsSelectable(true)
        }
        logScroll = ScrollView(this).apply {
            addView(logView)
            setBackgroundColor(0xFF11141B.toInt())
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
        }
        root.addView(logScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = pad / 2 })
        setContentView(root)

        if (LinuxService.autoEnabled(this)) LinuxService.send(this, LinuxService.ACTION_WATCH)

        // Ένα κουμπί: από το εικονίδιο της εφαρμογής πάμε κατευθείαν στο desktop
        val fromLauncher = intent?.action == Intent.ACTION_MAIN && !intent.getBooleanExtra(EXTRA_SETTINGS, false)
        if (savedInstanceState == null && fromLauncher && quickStart() && linux.setupDone) {
            val ext = LinuxService.externalDisplays(this).firstOrNull()
            openDesktop(ext?.displayId ?: Display.DEFAULT_DISPLAY)
            if (ext == null) finish() // στο κινητό: αφήνουμε μόνο το desktop
        } else if (linux.setupDone && LinuxService.autoEnabled(this)) {
            // Αν η οθόνη είναι ήδη συνδεδεμένη
            LinuxService.externalDisplays(this).firstOrNull()?.let { openDesktop(it.displayId) }
        }
    }

    private fun quickStart() = LinuxService.prefs(this).getBoolean("quick", true)

    companion object { const val EXTRA_SETTINGS = "settings" }

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
        val x11 = LinuxService.x11Installed(this)
        x11Get.visibility = if (x11) android.view.View.GONE else android.view.View.VISIBLE
        x11Switch.isEnabled = x11
        x11Switch.isChecked = LinuxService.useX11(this)
        val inst = when {
            linux.setupDone && !linux.updateDone -> "υπάρχει ενημέρωση – πάτα ①"
            linux.setupDone -> "εγκατεστημένο" + if (linux.claudeDone) " · Claude Code ✓" else ""
            linux.rootfsReady -> "μισή εγκατάσταση – πάτα ①"
            else -> "όχι ακόμα – πάτα ①"
        }
        val mode = if (LinuxService.useX11(this)) "γρήγορη (Termux:X11)" else "VNC"
        val ssh = if (LinuxService.prefs(this).getBoolean("ssh", false))
            "\nSSH: ssh root@${wifiIp() ?: "(χωρίς Wi-Fi)"} -p 8022" else ""
        status.text = "Debian: $inst\nΟθόνη: $mode\nΚατάσταση: ${LogBus.state}$ssh"
        logView.text = LogBus.text()
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun openDesktop(displayId: Int) {
        if (!linux.setupDone) { LogBus.log("Κάνε πρώτα «Εγκατάσταση»."); return }
        LinuxService.send(this, LinuxService.ACTION_START)
        LinuxService.launchDesktop(this, displayId)
    }

    private fun chooseBundles() {
        val names = arrayOf(
            "Γραφείο – LibreOffice (με ελληνικά)",
            "Γραφίστας – GIMP, Inkscape, Krita",
            "Προγραμματιστής – VS Code, Git, Python, Node",
            "Ψυχαγωγία – VLC",
            "Εφαρμογές Windows – Box64 + Wine (πειραματικό, ~500 MB)",
        )
        val keys = arrayOf("office", "graphics", "dev", "media", "windows")
        val picked = BooleanArray(names.size)
        android.app.AlertDialog.Builder(this)
            .setTitle("Πακέτα εφαρμογών")
            .setMultiChoiceItems(names, picked) { _, i, on -> picked[i] = on }
            .setPositiveButton("Εγκατάσταση") { _, _ ->
                val list = keys.filterIndexed { i, _ -> picked[i] }.toTypedArray()
                if (list.isEmpty()) return@setPositiveButton
                startForegroundService(Intent(this, LinuxService::class.java)
                    .setAction(LinuxService.ACTION_BUNDLES).putExtra(LinuxService.EXTRA_BUNDLES, list))
            }
            .setNegativeButton("Άκυρο", null)
            .show()
    }

    private fun askSshPassword() {
        val input = android.widget.EditText(this).apply {
            hint = "κωδικός για τον χρήστη root"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Κωδικός SSH")
            .setMessage("Με αυτόν θα μπαίνεις από άλλο υπολογιστή. Βάλε κάτι δυνατό: όποιος είναι στο ίδιο Wi-Fi μπορεί να δοκιμάσει να συνδεθεί.")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val pw = input.text.toString()
                if (pw.length < 6) {
                    LogBus.log("SSH: ο κωδικός θέλει τουλάχιστον 6 χαρακτήρες")
                    sshSwitch.isChecked = false
                    return@setPositiveButton
                }
                LinuxService.prefs(this).edit().putBoolean("ssh", true).apply()
                linux.setSsh(true, pw)
                LogBus.log("SSH: ΝΑΙ – πάτα «Επανεκκίνηση desktop»")
                refresh()
            }
            .setNegativeButton("Άκυρο") { _, _ -> sshSwitch.isChecked = false }
            .setOnCancelListener { sshSwitch.isChecked = false }
            .show()
    }

    private fun wifiIp(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && (it.name.startsWith("wlan") || it.name.startsWith("swlan")) }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }?.hostAddress
    } catch (_: Exception) { null }

    private fun confirm(title: String, msg: String, ok: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title).setMessage(msg)
            .setPositiveButton("Ναι") { _, _ -> ok() }
            .setNegativeButton("Όχι", null)
            .show()
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
