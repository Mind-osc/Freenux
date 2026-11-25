package com.mindosc.freenux

import android.system.Os
import android.system.OsConstants
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Backup όλου του Linux από τη μεριά του Android (έξω από το proot), ώστε να
 * διαβάζονται και τα αρχεία .l2s.* που φτιάχνει το proot για τα hard links.
 */
object Backup {
    private val EXCLUDE = setOf(
        "proc", "sys", "dev", "tmp", "sdcard", "storage", ".freenux-proc",
        "root/.cache", "var/cache/apt/archives",
    )

    private var files = 0
    private var bytes = 0L

    fun create(rootfs: File, out: File) {
        out.parentFile?.mkdirs()
        val part = File(out.path + ".part")
        files = 0; bytes = 0
        LogBus.log(">>> Backup σε ${out.path} (10–20 λεπτά, άφησε την εφαρμογή ανοιχτή)")
        FileOutputStream(part).use { fos ->
            val gz = GzipCompressorOutputStream(BufferedOutputStream(fos, 1 shl 17),
                GzipParameters().apply { compressionLevel = 1 })
            TarArchiveOutputStream(gz, 1 shl 16).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                walk(rootfs, "", tar)
                tar.finish()
            }
        }
        if (out.exists()) out.renameTo(File(out.parentFile, "freenux-backup.old.tar.gz"))
        part.renameTo(out)
        LogBus.log(">>> ✓ Backup έτοιμο: $files αρχεία, ${out.length() shr 20} MB συμπιεσμένο")
    }

    private fun walk(dir: File, rel: String, tar: TarArchiveOutputStream) {
        val names = dir.list() ?: return
        names.sort()
        for (name in names) {
            val path = rel + name
            if (path in EXCLUDE) continue
            val f = File(dir, name)
            val st = try { Os.lstat(f.path) } catch (_: Exception) { continue }
            val mode = st.st_mode
            when {
                OsConstants.S_ISLNK(mode) -> {
                    val e = TarArchiveEntry("./$path", TarConstants.LF_SYMLINK)
                    e.linkName = try { Os.readlink(f.path) } catch (_: Exception) { continue }
                    e.mode = mode and 0xFFF or 0xA000
                    e.modTime = java.util.Date(st.st_mtime * 1000)
                    tar.putArchiveEntry(e); tar.closeArchiveEntry()
                }
                OsConstants.S_ISDIR(mode) -> {
                    val e = TarArchiveEntry("./$path/")
                    e.mode = mode and 0xFFF or 0x4000
                    e.modTime = java.util.Date(st.st_mtime * 1000)
                    tar.putArchiveEntry(e); tar.closeArchiveEntry()
                    walk(f, "$path/", tar)
                }
                OsConstants.S_ISREG(mode) -> {
                    val e = TarArchiveEntry("./$path")
                    e.size = st.st_size
                    e.mode = mode and 0xFFF or 0x8000
                    e.modTime = java.util.Date(st.st_mtime * 1000)
                    try {
                        FileInputStream(f).use { input ->
                            tar.putArchiveEntry(e)
                            var left = st.st_size
                            val buf = ByteArray(1 shl 16)
                            while (left > 0) {
                                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (n < 0) break
                                tar.write(buf, 0, n); left -= n
                            }
                            // Αν το αρχείο μίκρυνε στο μεταξύ, συμπληρώνουμε με μηδενικά
                            while (left > 0) { val z = minOf(buf.size.toLong(), left).toInt(); java.util.Arrays.fill(buf, 0, z, 0); tar.write(buf, 0, z); left -= z }
                            tar.closeArchiveEntry()
                        }
                        bytes += st.st_size
                    } catch (ex: Exception) {
                        LogBus.log("   (παράλειψη $path: ${ex.message})")
                        continue
                    }
                }
                else -> continue // sockets, fifos, συσκευές
            }
            if (++files % 5000 == 0) LogBus.log("… $files αρχεία, ${bytes shr 20} MB")
        }
    }
}
