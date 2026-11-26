package com.mindosc.freenux

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Το κινητό ως touchpad + πληκτρολόγιο για το Linux (όταν το desktop είναι σε εξωτερική οθόνη).
 * 1 δάχτυλο: κίνηση · άγγιγμα: αριστερό κλικ · 2 δάχτυλα: κύλιση · άγγιγμα με 2: δεξί κλικ
 * παρατεταμένο πάτημα και σύρσιμο: drag
 */
class TouchpadActivity : Activity() {

    private val queue = LinkedBlockingQueue<String>()
    @Volatile private var alive = true
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val d = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF11141B.toInt())
        }
        info = TextView(this).apply {
            text = "Touchpad – σύνδεση…"
            setTextColor(0xFF7F8796.toInt()); textSize = 13f; gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        }
        root.addView(info)

        val pad = View(this).apply { setBackgroundColor(0xFF1B1F2A.toInt()) }
        pad.setOnTouchListener(Gestures(d))
        root.addView(pad, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply {
            setMargins((10 * d).toInt(), 0, (10 * d).toInt(), (10 * d).toInt())
        })

        // Κρυφό πεδίο για το πληκτρολόγιο του κινητού
        val keys = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setText(SENTINEL); setSelection(SENTINEL.length)
            setTextColor(Color.TRANSPARENT); setBackgroundColor(Color.TRANSPARENT); isCursorVisible = false
        }
        var busy = false
        keys.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (busy) return
                if (count > 0) send("t " + s.subSequence(start, start + count).toString().replace("\n", ""))
                repeat(maxOf(0, before - count)) { send("k BackSpace") }
            }
            override fun afterTextChanged(e: Editable) {
                if (busy || e.toString() == SENTINEL) return
                busy = true; e.replace(0, e.length, SENTINEL); keys.setSelection(SENTINEL.length); busy = false
            }
        })
        keys.setOnEditorActionListener { _, _, _ -> send("k Return"); true }
        keys.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (code) {
                KeyEvent.KEYCODE_DEL -> { send("k BackSpace"); true }
                KeyEvent.KEYCODE_ENTER -> { send("k Return"); true }
                else -> false
            }
        }
        root.addView(keys, LinearLayout.LayoutParams(1, 1))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun b(t: String, f: () -> Unit) = bar.addView(Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { f() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        b("Αριστ.") { send("c 1") }
        b("Δεξί") { send("c 3") }
        b("⌨") {
            keys.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(keys, InputMethodManager.SHOW_FORCED)
        }
        b("Esc") { send("k Escape") }
        b("Tab") { send("k Tab") }
        b("✕") { finish() }
        root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)

        thread(name = "touchpad-net") { netLoop() }
    }

    override fun onDestroy() { alive = false; queue.offer(""); super.onDestroy() }

    private fun send(cmd: String) { queue.offer(cmd) }

    private fun status(t: String) = runOnUiThread { info.text = t }

    private fun netLoop() {
        while (alive) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", 4658), 1500)
                    s.tcpNoDelay = true
                    status("Touchpad · 1 δάχτυλο: κίνηση · άγγιγμα: κλικ · 2 δάχτυλα: κύλιση / δεξί κλικ")
                    val out: OutputStream = s.getOutputStream()
                    while (alive) {
                        val c = queue.take()
                        if (c.isEmpty()) break
                        out.write((c + "\n").toByteArray()); out.flush()
                    }
                }
            } catch (_: Exception) {
                status("Touchpad – περιμένω το desktop… (πρέπει να τρέχει το Linux)")
                SystemClock.sleep(1500)
            }
        }
    }

    private inner class Gestures(val d: Float) : View.OnTouchListener {
        private var lastX = 0f; private var lastY = 0f
        private var downT = 0L; private var moved = 0f
        private var maxPointers = 1
        private var fx = 0f; private var fy = 0f; private var scrollAcc = 0f
        private var dragging = false
        private val longPress = Runnable {
            if (moved < 10 * d && maxPointers == 1) {
                dragging = true; send("d 1")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.x; lastY = e.y; downT = e.eventTime; moved = 0f; maxPointers = 1
                    fx = 0f; fy = 0f; scrollAcc = 0f; dragging = false
                    v.postDelayed(longPress, 450)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    maxPointers = maxOf(maxPointers, e.pointerCount)
                    lastX = avgX(e); lastY = avgY(e)
                    v.removeCallbacks(longPress)
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = if (e.pointerCount > 1) avgX(e) else e.x
                    val y = if (e.pointerCount > 1) avgY(e) else e.y
                    val dx = x - lastX; val dy = y - lastY
                    lastX = x; lastY = y
                    moved += abs(dx) + abs(dy)
                    if (moved > 10 * d) v.removeCallbacks(longPress)
                    if (e.pointerCount >= 2) {
                        scrollAcc += dy
                        val step = 28 * d
                        val n = (scrollAcc / step).toInt()
                        if (n != 0) { send("s ${-n}"); scrollAcc -= n * step }
                    } else {
                        // Επιτάχυνση: γρήγορες κινήσεις πάνε πιο μακριά
                        val speed = hypot(dx, dy) / d
                        val gain = 1.1f + minOf(speed * 0.12f, 2.4f)
                        fx += dx * gain; fy += dy * gain
                        val ix = fx.toInt(); val iy = fy.toInt()
                        if (ix != 0 || iy != 0) { send("m $ix $iy"); fx -= ix; fy -= iy }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    val quick = e.eventTime - downT < 250 && moved < 12 * d
                    when {
                        dragging -> send("u 1")
                        quick && maxPointers >= 2 -> send("c 3")
                        quick -> send("c 1")
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> { v.removeCallbacks(longPress); if (dragging) send("u 1"); dragging = false }
            }
            return true
        }

        private fun avgX(e: MotionEvent) = (0 until e.pointerCount).map { e.getX(it) }.average().toFloat()
        private fun avgY(e: MotionEvent) = (0 until e.pointerCount).map { e.getY(it) }.average().toFloat()
    }

    companion object { private const val SENTINEL = "  " }
}
