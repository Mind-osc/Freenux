package com.mindosc.freenux

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.concurrent.thread

/** Πλήρης οθόνη με το Linux desktop (noVNC μέσα σε WebView). */
class DesktopActivity : Activity() {

    companion object { const val EXTRA_EXTERNAL = "external" }

    private lateinit var web: WebView
    @Volatile private var alive = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.builtInZoomControls = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(0xFF111111.toInt())
        }
        setContentView(web)
        web.loadData(
            "<html><body style='margin:0;background:#111;color:#ccc;font:20px sans-serif;" +
                "display:flex;align-items:center;justify-content:center;height:100vh'>" +
                "Ξεκινάει το Debian… (την πρώτη φορά με Mint θέλει 1–2 λεπτά)</body></html>",
            "text/html; charset=utf-8", null
        )

        @Suppress("DEPRECATION")
        val onExternal = intent.getBooleanExtra(EXTRA_EXTERNAL, false) ||
            windowManager.defaultDisplay.displayId != android.view.Display.DEFAULT_DISPLAY
        // Εξωτερική οθόνη: το desktop παίρνει την ανάλυσή της. Κινητό: κλιμάκωση.
        val resize = if (onExternal) "remote" else "scale"
        val url = "http://127.0.0.1:${Linux.NOVNC_PORT}/vnc.html?autoconnect=true&reconnect=true" +
            "&reconnect_delay=1000&resize=$resize&show_dot=true&path=websockify" +
            "&quality=5&compression=1"

        LinuxService.send(this, LinuxService.ACTION_START)
        thread {
            for (i in 0 until 2400) {
                if (!alive) return@thread
                if (Linux.portOpen(Linux.NOVNC_PORT)) {
                    runOnUiThread { web.loadUrl(url); web.requestFocus() }
                    return@thread
                }
                Thread.sleep(200)
            }
            runOnUiThread {
                web.loadData("<html><body style='background:#111;color:#f88;font:20px sans-serif'>" +
                    "Το desktop δεν ξεκίνησε. Δες το log στην εφαρμογή.</body></html>",
                    "text/html; charset=utf-8", null)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    // Το δεξί κλικ του ποντικιού στέλνει συχνά "πίσω" – το αγνοούμε για να μην κλείνει το desktop.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    override fun onDestroy() {
        alive = false
        web.destroy()
        super.onDestroy()
    }
}
