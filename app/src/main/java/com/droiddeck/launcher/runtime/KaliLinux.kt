package com.droiddeck.launcher.runtime

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droiddeck.launcher.core.Downloader
import com.droiddeck.launcher.core.FileUtils
import com.droiddeck.launcher.session.SessionPrefs
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/** Kali NetHunter Rootless's own arm64 rootfs, kept apart from the Arch runtime and run in its own proot. */
object KaliLinux {
    private const val TAG = "KaliLinux"
    private const val BASE_URL = "https://kali.download/nethunter-images/current/rootfs"
    /** The NetHunter rootfs archives keep everything under this directory. */
    private const val ARCHIVE_DIR = "kali-arm64"

    enum class Edition(val id: String) { MINIMAL("minimal"), FULL("full") }

    sealed class State {
        object Idle : State()
        class Working(val stage: Stage, val fraction: Float) : State()
        class Failed(val reason: String) : State()
    }

    enum class Stage { DOWNLOADING, EXTRACTING, CONFIGURING, REMOVING }

    var state by mutableStateOf<State>(State.Idle)
        private set

    fun dir(context: Context) = File(context.filesDir, "kali")
    fun rootfs(context: Context) = File(dir(context), "rootfs")
    private fun marker(context: Context) = File(dir(context), ".installed")

    fun installed(context: Context): Boolean = marker(context).isFile && File(rootfs(context), "bin").exists()

    fun edition(context: Context): String? = marker(context).takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    fun install(context: Context, edition: Edition) {
        if (state is State.Working) return
        val app = context.applicationContext
        state = State.Working(Stage.DOWNLOADING, 0f)
        Thread({
            try {
                val dir = dir(app).apply { mkdirs() }
                val archive = File(dir, "kali-nethunter-rootfs-${edition.id}-arm64.tar.xz")
                val url = "$BASE_URL/${archive.name}"
                val ok = Downloader.downloadFile(url, archive, true) { f -> state = State.Working(Stage.DOWNLOADING, if (f < 0f) -1f else f.coerceIn(0f, 1f)) }
                if (!ok) error("download failed: $url")
                state = State.Working(Stage.EXTRACTING, -1f)
                val root = rootfs(app)
                FileUtils.delete(root)
                root.mkdirs()
                val total = archive.length().coerceAtLeast(1L)
                val counting = object : java.io.FilterInputStream(BufferedInputStream(FileInputStream(archive), 1 shl 16)) {
                    var read = 0L
                    override fun read(b: ByteArray, off: Int, len: Int): Int =
                        super.read(b, off, len).also { if (it > 0) { read += it; state = State.Working(Stage.EXTRACTING, read.toFloat() / total) } }
                }
                val extracted = LinuxRuntimeInstaller.extractStream(app, XZCompressorInputStream(counting), root, root, ARCHIVE_DIR, null)
                if (!extracted) error("extract failed")
                state = State.Working(Stage.CONFIGURING, -1f)
                configure(root)
                archive.delete()
                marker(app).writeText(edition.id)
                state = State.Idle
            } catch (e: Throwable) {
                Log.e(TAG, "install", e)
                state = State.Failed(e.message ?: e.toString())
            }
        }, "kali-install").apply { isDaemon = true; start() }
    }

    fun remove(context: Context, done: () -> Unit) {
        if (state is State.Working) return
        val app = context.applicationContext
        state = State.Working(Stage.REMOVING, -1f)
        Thread({
            FileUtils.delete(dir(app))
            state = State.Idle
            done()
        }, "kali-remove").apply { isDaemon = true; start() }
    }

    fun clearFailure() {
        if (state is State.Failed) state = State.Idle
    }

    private fun configure(root: File) {
        File(root, "etc").mkdirs()
        val resolv = File(root, "etc/resolv.conf")
        if (java.nio.file.Files.isSymbolicLink(resolv.toPath())) resolv.delete()
        resolv.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(root, "etc/hosts").takeIf { !it.isFile || it.length() == 0L }?.writeText("127.0.0.1 localhost kali\n::1 localhost\n")
        File(root, "etc/hostname").writeText("kali\n")
        File(root, "root").mkdirs()
        File(root, "tmp").apply { mkdirs(); setWritable(true, false) }
        File(root, "root/.hushlogin").createNewFile()
        File(root, "etc/profile.d").mkdirs()
        File(root, "etc/profile.d/arise-kali.sh").writeText(
            "export PS1='\\[\\e[1;34m\\]┌──(\\[\\e[1;35m\\]arise㉿kali\\[\\e[1;34m\\])-[\\[\\e[0m\\]\\w\\[\\e[1;34m\\]]\\n└─\\[\\e[1;35m\\]#\\[\\e[0m\\] '\n",
        )
    }

    /** The shell command and the host environment for a Kali terminal. */
    fun command(context: Context): Pair<List<String>, Array<String>> {
        val root = rootfs(context)
        val cmd = LinuxRuntime.prootPrefix(context, root, "/root", true)
        cmd.add(1, "--link2symlink")
        val binds = mutableListOf("/dev", "/proc", "/sys", "/dev/urandom:/dev/random", "/proc/self/fd:/dev/fd",
            "/proc/self/fd/0:/dev/stdin", "/proc/self/fd/1:/dev/stdout", "/proc/self/fd/2:/dev/stderr",
            "${File(root, "tmp").path}:/dev/shm")
        Environment.getExternalStorageDirectory().takeIf { it.isDirectory }?.let { binds += "${it.path}:/sdcard" }
        binds.forEach { cmd.add("-b"); cmd.add(it) }
        cmd += listOf(
            "/usr/bin/env", "-i", "HOME=/root", "USER=root", "LOGNAME=root", "TERM=xterm-256color", "LANG=C.UTF-8", "COLORTERM=truecolor",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", "/bin/bash", "--login",
        )
        val env = LinkedHashMap(System.getenv())
        env["PROOT_LOADER"] = LinuxRuntime.prootLoader(context).path
        env["PROOT_TMP_DIR"] = LinuxRuntime.prootTempDir(context).path
        if (SessionPrefs.prootNoSeccomp(context)) env["PROOT_NO_SECCOMP"] = "1"
        LinuxRuntime.prootLibraryPath(context).takeIf { it.isNotEmpty() }?.let { env["LD_LIBRARY_PATH"] = it }
        return cmd to env.map { (k, v) -> "$k=$v" }.toTypedArray()
    }
}
