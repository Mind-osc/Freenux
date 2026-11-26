package com.mindosc.freenux

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.File
import kotlin.concurrent.thread

/** Μικρόφωνο κινητού → PulseAudio μέσα στο Linux (unix socket στον ιδιωτικό φάκελο, 48kHz mono 16-bit). */
class MicBridge(private val sockFile: File) {
    companion object {
        private const val RATE = 48000
    }

    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "freenux-mic") { loop() }
    }

    fun stop() { running = false; worker?.interrupt() }

    @SuppressLint("MissingPermission")
    private fun loop() {
        var announced = false
        while (running) {
            try {
                if (!sockFile.exists()) throw IllegalStateException("όχι ακόμα")
                LocalSocket().use { sock ->
                    sock.connect(LocalSocketAddress(sockFile.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
                    val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, RATE / 5))
                    if (rec.state != AudioRecord.STATE_INITIALIZED) {
                        LogBus.log("✗ Μικρόφωνο: δεν δόθηκε άδεια ή είναι πιασμένο")
                        rec.release(); running = false; return
                    }
                    try {
                        rec.startRecording()
                        if (!announced) { LogBus.log(">>> Μικρόφωνο κινητού συνδέθηκε ✓"); announced = true }
                        val out = sock.outputStream
                        val buf = ByteArray(1920) // 20 ms
                        while (running) {
                            val n = rec.read(buf, 0, buf.size)
                            if (n > 0) out.write(buf, 0, n)
                        }
                    } finally {
                        try { rec.stop() } catch (_: Exception) {}
                        rec.release()
                    }
                }
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {}
            try { Thread.sleep(2000) } catch (_: InterruptedException) { return }
        }
    }
}
