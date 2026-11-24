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
        File(root, "etc/apt/apt.conf.d").mkdirs()
        File(root, "etc/apt/apt.conf.d/99arise").writeText("APT::Sandbox::User \"root\";\nDpkg::Use-Pty \"0\";\n")
        File(root, "root/.hushlogin").createNewFile()
        File(root, "etc/profile.d").mkdirs()
        File(root, "etc/profile.d/arise-kali.sh").writeText(
            "export PS1='\\[\\e[1;34m\\]┌──(\\[\\e[1;35m\\]arise㉿kali\\[\\e[1;34m\\])-[\\[\\e[0m\\]\\w\\[\\e[1;34m\\]]\\n└─\\[\\e[1;35m\\]#\\[\\e[0m\\] '\n",
        )
    }

    /** The shell command and the host environment for a Kali terminal. */
    fun command(context: Context): Pair<List<String>, Array<String>> = prootCommand(context, listOf("/bin/bash", "--login"))

    const val DESKTOP_PORT = 5901
    const val NEEDS_PACKAGES = 3
    const val DESKTOP_PACKAGES = "kali-desktop-xfce tigervnc-standalone-server dbus-x11 adwaita-icon-theme librsvg2-common"

    /**
     * Xtigervnc on :1 for the in-app viewer, with Kali's XFCE desktop inside; exits 3 when those packages are missing.
     * apt's triggers can skip the GTK icon and pixbuf-loader caches under proot, and libwnck aborts xfce4-panel without them.
     * The desktop lives as long as Xtigervnc: a part of XFCE that dies is started again.
     */
    fun desktopScript(width: Int, height: Int): String = """
        command -v Xtigervnc >/dev/null 2>&1 && command -v startxfce4 >/dev/null 2>&1 && [ -d /usr/share/icons/Adwaita ] || exit $NEEDS_PACKAGES
        ls /usr/lib/*/gdk-pixbuf-2.0/2.10.0/loaders/*svg* >/dev/null 2>&1 || exit $NEEDS_PACKAGES
        export HOME=/root USER=root DISPLAY=:1 XDG_RUNTIME_DIR=/tmp/runtime-root XKL_XMODMAP_DISABLE=1 NO_AT_BRIDGE=1
        unset SESSION_MANAGER DBUS_SESSION_BUS_ADDRESS
        mkdir -p /tmp/.X11-unix /tmp/.ICE-unix /tmp/runtime-root && chmod 1777 /tmp/.X11-unix /tmp/.ICE-unix && chmod 700 /tmp/runtime-root
        if [ ! -e /root/.cache/arise-gtk-caches ]; then
          echo "preparing icon caches (first time only)"
          for q in /usr/lib/*/gdk-pixbuf-2.0/gdk-pixbuf-query-loaders; do [ -x "${'$'}q" ] && "${'$'}q" --update-cache; done
          if command -v gtk-update-icon-cache >/dev/null 2>&1; then
            for d in /usr/share/icons/*/; do [ -f "${'$'}d/index.theme" ] && gtk-update-icon-cache -f -q "${'$'}d" 2>/dev/null; done
          fi
          command -v fc-cache >/dev/null 2>&1 && fc-cache -f >/dev/null 2>&1
          mkdir -p /root/.cache && touch /root/.cache/arise-gtk-caches
        fi
        rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
        Xtigervnc :1 -geometry ${width}x${height} -depth 24 -rfbport $DESKTOP_PORT -localhost=1 -SecurityTypes None -AlwaysShared -AcceptSetDesktopSize=0 &
        x=${'$'}!
        for i in ${'$'}(seq 1 50); do [ -e /tmp/.X11-unix/X1 ] && break; sleep 0.2; done
        eval "${'$'}(dbus-launch --sh-syntax 2>/dev/null)" || true
        startxfce4 &
        sleep 8
        command -v xfconf-query >/dev/null 2>&1 && xfconf-query -c xfwm4 -p /general/use_compositing -n -t bool -s false 2>/dev/null
        while kill -0 ${'$'}x 2>/dev/null; do
          if ! pgrep -x xfce4-session >/dev/null 2>&1; then
            pgrep -x xfwm4 >/dev/null 2>&1 || { xfwm4 --replace >/dev/null 2>&1 & }
            pgrep -x xfdesktop >/dev/null 2>&1 || { xfdesktop >/dev/null 2>&1 & }
          fi
          pgrep -x xfce4-panel >/dev/null 2>&1 || { echo "restarting the panel"; xfce4-panel >/dev/null 2>&1 & }
          sleep 4
        done
        [ -n "${'$'}{DBUS_SESSION_BUS_PID:-}" ] && kill ${'$'}DBUS_SESSION_BUS_PID 2>/dev/null
    """.trimIndent()

    fun desktopProcess(context: Context, width: Int, height: Int): ProcessBuilder {
        val (cmd, env) = prootCommand(context, listOf("/bin/sh", "-c", desktopScript(width, height)))
        return ProcessBuilder(cmd).redirectErrorStream(true).directory(rootfs(context)).also { b ->
            b.environment().apply {
                clear()
                env.forEach { e -> e.indexOf('=').takeIf { it > 0 }?.let { put(e.substring(0, it), e.substring(it + 1)) } }
            }
        }
    }

    private fun prootCommand(context: Context, guest: List<String>): Pair<List<String>, Array<String>> {
        val root = rootfs(context)
        val cmd = LinuxRuntime.prootPrefix(context, root, "/root", true)
        cmd.add(1, "--link2symlink")
        cmd.add(2, "--sysvipc")
        val binds = mutableListOf("/dev", "/proc", "/sys", "/dev/urandom:/dev/random", "/proc/self/fd:/dev/fd",
            "/proc/self/fd/0:/dev/stdin", "/proc/self/fd/1:/dev/stdout", "/proc/self/fd/2:/dev/stderr",
            "${File(root, "tmp").path}:/dev/shm")
        val noSelinux = File(dir(context), ".no-selinux").apply { mkdirs() }
        binds += "${noSelinux.path}:/sys/fs/selinux"
        Environment.getExternalStorageDirectory().takeIf { it.isDirectory }?.let { binds += "${it.path}:/sdcard" }
        binds.forEach { cmd.add("-b"); cmd.add(it) }
        cmd += listOf(
            "/usr/bin/env", "-i", "HOME=/root", "USER=root", "LOGNAME=root", "TERM=xterm-256color", "LANG=C.UTF-8", "COLORTERM=truecolor",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        ) + guest
        val env = LinkedHashMap(System.getenv())
        env["PROOT_LOADER"] = LinuxRuntime.prootLoader(context).path
        env["PROOT_TMP_DIR"] = LinuxRuntime.prootTempDir(context).path
        if (SessionPrefs.prootNoSeccomp(context)) env["PROOT_NO_SECCOMP"] = "1"
        LinuxRuntime.prootLibraryPath(context).takeIf { it.isNotEmpty() }?.let { env["LD_LIBRARY_PATH"] = it }
        return cmd to env.map { (k, v) -> "$k=$v" }.toTypedArray()
    }
}
