package com.mindosc.freenux

import android.content.Context
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Κατεβάζει το επίσημο Debian rootfs και το αποσυμπιέζει. */
class RootfsInstaller(private val ctx: Context, private val linux: Linux) {

    fun install() {
        val (name, url) = findTarball()
        LogBus.log("Βρέθηκε: $name")
        val tar = File(ctx.cacheDir, name)
        download(url, tar)

        LogBus.log("Αποσυμπίεση… (λίγα λεπτά)")
        linux.clearRootfsMarker()
        if (linux.rootfs.exists()) {
            Runtime.getRuntime().exec(arrayOf("chmod", "-R", "u+rwX", linux.rootfs.path)).waitFor()
            Runtime.getRuntime().exec(arrayOf("rm", "-rf", linux.rootfs.path)).waitFor()
        }
        linux.rootfs.mkdirs()
        extract(tar, linux.rootfs)
        tar.delete()
        linux.prepareHostFiles()
        linux.markRootfsReady()
        LogBus.log("Το Debian αποσυμπιέστηκε.")
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "Freenux")
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }

    /** Επίσημο Debian rootfs (debuerreotype – το ίδιο που χρησιμοποιεί το Docker). */
    private fun findTarball(): Pair<String, String> {
        LogBus.log("Αναζήτηση Debian rootfs…")
        val path = "debuerreotype/docker-debian-artifacts/dist-arm64v8/trixie/oci/blobs/rootfs.tar.gz"
        val urls = listOf(
            "https://raw.githubusercontent.com/$path",
            "https://github.com/" + path.replaceFirst("/dist-arm64v8/", "/raw/dist-arm64v8/"),
        )
        for (u in urls) {
            try {
                val c = open(u).apply { requestMethod = "HEAD" }
                if (c.responseCode in 200..299) return "debian-trixie-arm64.tar.gz" to u
                LogBus.log("$u → HTTP ${c.responseCode}")
            } catch (e: Exception) { LogBus.log("$u → ${e.message}") }
        }
        error("Δεν βρέθηκε Debian rootfs για arm64 (έλεγξε το ίντερνετ)")
    }

    private fun download(url: String, out: File) {
        val c = open(url)
        val total = c.contentLengthLong
        var done = 0L
        var last = -1
        c.inputStream.use { inp ->
            FileOutputStream(out).use { o ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct / 10 != last / 10) {
                            last = pct
                            LogBus.log("Λήψη $pct%  (${done shr 20}/${total shr 20} MB)")
                        }
                    }
                }
            }
        }
    }

    private fun extract(file: File, dest: File) {
        val rootDirs = setOf("bin", "boot", "dev", "etc", "home", "lib", "lib64", "media", "mnt",
            "opt", "proc", "root", "run", "sbin", "srv", "sys", "tmp", "usr", "var")
        var strip: String? = null
        var first = true
        var count = 0

        fun clean(raw: String): String {
            var n = raw.removePrefix("./").removePrefix("/")
            strip?.let { s -> n = if (n == s.trimEnd('/')) "" else n.removePrefix(s) }
            return n
        }

        val raw = BufferedInputStream(FileInputStream(file), 1 shl 16)
        raw.mark(4)
        val b0 = raw.read(); val b1 = raw.read()
        raw.reset()
        val unpacked = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(raw, 1 shl 16) else XZInputStream(raw)
        TarArchiveInputStream(unpacked).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                if (first) {
                    first = false
                    val comp = e.name.removePrefix("./").substringBefore('/')
                    if (comp.isNotEmpty() && comp !in rootDirs) strip = "$comp/"
                }
                val name = clean(e.name)
                if (name.isEmpty() || name.split('/').contains("..")) continue
                val out = File(dest, name)
                out.parentFile?.mkdirs()
                when {
                    e.isDirectory -> {
                        out.mkdirs()
                        chmod(out, (e.mode and 0x1FF) or 0x1C0)
                    }
                    e.isSymbolicLink -> {
                        Linux.rm(out)
                        try { Os.symlink(e.linkName, out.path) } catch (_: Exception) {}
                    }
                    e.isLink -> { // hard link → αντίγραφο
                        val target = File(dest, clean(e.linkName))
                        if (target.isFile) {
                            Linux.rm(out)
                            target.copyTo(out, overwrite = true)
                            chmod(out, (e.mode and 0x1FF) or 0x180)
                        }
                    }
                    e.isFile -> {
                        Linux.rm(out)
                        FileOutputStream(out).use { tar.copyTo(it, 1 shl 16) }
                        chmod(out, (e.mode and 0x1FF) or 0x180)
                    }
                    else -> {} // συσκευές κ.λπ. – παράλειψη
                }
                if (++count % 2000 == 0) LogBus.log("… $count αρχεία")
            }
        }
        LogBus.log("Σύνολο $count αρχεία")
    }

    private fun chmod(f: File, mode: Int) {
        try { Os.chmod(f.path, mode) } catch (_: Exception) {}
    }
}
