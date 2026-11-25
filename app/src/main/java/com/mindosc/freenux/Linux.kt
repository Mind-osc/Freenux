package com.mindosc.freenux

import android.content.Context
import android.system.Os
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Το Debian rootfs και πώς τρέχουμε εντολές μέσα του με proot. */
class Linux(private val ctx: Context) {
    val rootfs = File(ctx.filesDir, "rootfs")
    private val nativeDir: String = ctx.applicationInfo.nativeLibraryDir
    private val rootfsMarker = File(ctx.filesDir, "rootfs.ok")

    val rootfsReady get() = rootfsMarker.exists()
    val setupDone get() = rootfsReady && File(rootfs, "root/.freenux/setup.done").exists()
    val updateDone get() = File(rootfs, "root/.freenux/update.v3").exists()
    val claudeDone get() = File(rootfs, "root/.freenux/claude.done").exists()

    fun markRootfsReady() = rootfsMarker.writeText("ok")
    fun clearRootfsMarker() = rootfsMarker.delete()

    /** Αρχεία που γράφει το Android μέσα στο rootfs πριν από κάθε εκκίνηση. */
    fun prepareHostFiles() {
        val tmp = File(rootfs, "tmp").apply { mkdirs() }
        chmod(tmp, "1777")
        val etc = File(rootfs, "etc").apply { mkdirs() }
        File(etc, "resolv.conf").let { rm(it); it.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n") }
        File(etc, "hosts").writeText("127.0.0.1 localhost freenux\n::1 localhost ip6-localhost ip6-loopback\n")
        File(rootfs, "root/.freenux").mkdirs()

        val bin = File(rootfs, "usr/local/bin").apply { mkdirs() }
        for ((asset, name) in listOf(
            "setup.sh" to "freenux-setup",
            "desktop.sh" to "freenux-desktop",
            "claude.sh" to "freenux-claude",
            "mint.sh" to "freenux-mint",
            "update.sh" to "freenux-update",
            "desktop-x11.sh" to "freenux-desktop-x11",
            "xtest.py" to "freenux-xtest",
            "audio.sh" to "freenux-audio",
        )) {
            val f = File(bin, name)
            ctx.assets.open(asset).use { i -> f.outputStream().use { i.copyTo(it) } }
            chmod(f, "755")
        }

        val fake = File(rootfs, ".freenux-proc").apply { mkdirs() }
        FAKE_PROC.forEach { (n, c) -> File(fake, n).writeText(c) }
    }

    fun command(script: String, extraEnv: Map<String, String> = emptyMap()): ProcessBuilder {
        val tmp = File(ctx.cacheDir, "proot-tmp").apply { mkdirs() }
        val r = rootfs.absolutePath
        val args = mutableListOf(
            "$nativeDir/libproot.so",
            "--kill-on-exit", "--link2symlink", "--sysvipc", "-0",
            "-r", r,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "/dev/urandom:/dev/random",
            "-b", "$r/tmp:/dev/shm",
            "-w", "/root",
        )
        // Το Android κρύβει κάποια /proc αρχεία – δίνουμε ψεύτικα
        FAKE_PROC.keys.forEach { n ->
            if (!File("/proc/$n").canRead()) args += listOf("-b", "$r/.freenux-proc/$n:/proc/$n")
        }
        // Αρχεία κινητού μέσα στο Linux στο /sdcard
        if (File("/storage/emulated/0").canRead()) args += listOf("-b", "/storage/emulated/0:/sdcard")

        args += listOf(
            "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "LOGNAME=root",
            "TERM=xterm-256color", "LANG=C.UTF-8", "TMPDIR=/tmp",
            "PATH=/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        )
        extraEnv.forEach { (k, v) -> args += "$k=$v" }
        args += listOf("/bin/bash", "-lc", script)

        val pb = ProcessBuilder(args).redirectErrorStream(true)
        pb.environment().apply {
            put("PROOT_TMP_DIR", tmp.absolutePath)
            put("PROOT_LOADER", "$nativeDir/libproot-loader.so")
            put("LD_LIBRARY_PATH", nativeDir)
            remove("LD_PRELOAD")
        }
        return pb
    }

    /** Τρέχει script, στέλνει την έξοδο στο log, επιστρέφει exit code. */
    fun run(script: String): Int {
        prepareHostFiles()
        val p = command(script).start()
        p.inputStream.bufferedReader().forEachLine { LogBus.log(it) }
        return p.waitFor()
    }

    companion object {
        const val NOVNC_PORT = 6080

        fun portOpen(port: Int): Boolean = try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); true }
        } catch (_: Exception) { false }

        fun chmod(f: File, octal: String) {
            try { Os.chmod(f.path, octal.toInt(8)) } catch (_: Exception) {}
        }

        fun rm(f: File) {
            try { Os.remove(f.path) } catch (_: Exception) {}
        }

        private val FAKE_PROC = mapOf(
            "loadavg" to "0.12 0.07 0.02 2/165 765\n",
            "stat" to "cpu  1957 0 2877 93280 262 342 254 87 0 0\ncpu0 1957 0 2877 93280 262 342 254 87 0 0\n" +
                "intr 0\nctxt 0\nbtime 0\nprocesses 0\nprocs_running 1\nprocs_blocked 0\nsoftirq 0\n",
            "uptime" to "124.08 932.80\n",
            "version" to "Linux version 6.1.0-freenux (proot) #1 SMP PREEMPT\n",
            "vmstat" to "nr_free_pages 146031\nnr_inactive_anon 196744\nnr_active_anon 23140\n" +
                "nr_inactive_file 1181\nnr_active_file 2154\npgpgin 0\npgpgout 0\npswpin 0\npswpout 0\n",
        )
    }
}
