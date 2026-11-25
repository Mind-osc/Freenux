package com.mindosc.freenux

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/** Παίρνει τον ήχο του Linux (PulseAudio, PCM 48kHz stereo 16-bit) από TCP και τον παίζει στο κινητό. */
class AudioBridge {
    companion object {
        const val PORT = 4656
        private const val RATE = 48000
    }

    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "freenux-audio") { loop() }
    }

    fun stop() {
        running = false
        worker?.interrupt()
    }

    private fun loop() {
        var announced = false
        while (running) {
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress("127.0.0.1", PORT), 1000)
                    sock.soTimeout = 0
                    sock.tcpNoDelay = true
                    if (!announced) { LogBus.log(">>> Ήχος συνδέθηκε στο κινητό ✓"); announced = true }
                    play(sock)
                }
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                // Ο PulseAudio δεν έχει ξεκινήσει ακόμα – ξαναδοκιμάζουμε
            }
            try { Thread.sleep(2000) } catch (_: InterruptedException) { return }
        }
    }

    private fun play(sock: Socket) {
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(min * 2, RATE * 4 / 10) // ~100 ms
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            val input = sock.getInputStream()
            val buf = ByteArray(4096)
            var carry = 0 // κρατάμε ευθυγράμμιση στα 4 bytes (stereo frame)
            while (running) {
                val n = input.read(buf, carry, buf.size - carry)
                if (n < 0) break
                val total = carry + n
                val whole = total - total % 4
                if (whole > 0) track.write(buf, 0, whole)
                carry = total - whole
                if (carry > 0) System.arraycopy(buf, whole, buf, 0, carry)
            }
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }
}
