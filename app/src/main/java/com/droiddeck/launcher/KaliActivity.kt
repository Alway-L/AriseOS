package com.droiddeck.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.droiddeck.launcher.runtime.KaliLinux
import com.droiddeck.launcher.ui.DroidDeckTheme
import com.droiddeck.launcher.ui.LocalPalette
import com.droiddeck.launcher.ui.PrimaryButton
import com.droiddeck.launcher.ui.SecondaryButton
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class KaliActivity : ComponentActivity() {
    private var session: TerminalSession? = null
    private var terminalView: TerminalView? = null
    private var ctrl by mutableStateOf(false)
    private var alt by mutableStateOf(false)
    private var installed by mutableStateOf(false)
    private var finished by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        com.droiddeck.launcher.core.AppLanguage.applyTo(this, newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = com.droiddeck.launcher.core.AppUiPrefs.orientation(this)
        installed = KaliLinux.installed(this)
        setContent {
            DroidDeckTheme(appScale = com.droiddeck.launcher.core.AppUiPrefs.scale(this)) {
                Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
                    val state = KaliLinux.state
                    if (installed && state !is KaliLinux.State.Working) Terminal()
                    else Setup(state)
                }
            }
        }
    }

    override fun onDestroy() {
        stopDesktop(keepState = true)
        session?.finishIfRunning()
        session = null
        super.onDestroy()
    }

    @Composable
    private fun Setup(state: KaliLinux.State) {
        val pal = LocalPalette.current
        val colors = MaterialTheme.colorScheme
        if (state is KaliLinux.State.Idle && KaliLinux.installed(this) && !installed) installed = true
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(pal.primary, pal.primary2))),
                contentAlignment = Alignment.Center,
            ) { Text("K", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White) }
            Text(stringResource(R.string.kali_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Text(stringResource(R.string.kali_intro), fontSize = 14.sp, color = colors.onSurfaceVariant)
            when (state) {
                is KaliLinux.State.Working -> {
                    Text(
                        stringResource(
                            when (state.stage) {
                                KaliLinux.Stage.DOWNLOADING -> R.string.kali_downloading
                                KaliLinux.Stage.EXTRACTING -> R.string.kali_extracting
                                KaliLinux.Stage.CONFIGURING -> R.string.kali_configuring
                                KaliLinux.Stage.REMOVING -> R.string.kali_removing
                            },
                        ) + if (state.fraction >= 0f) " · ${(state.fraction * 100).toInt()}%" else "",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                    )
                    if (state.fraction >= 0f) LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth().height(6.dp))
                    else LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp))
                    Text(stringResource(R.string.kali_keep_open), fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
                else -> {
                    if (state is KaliLinux.State.Failed) Text(
                        stringResource(R.string.kali_failed, state.reason), fontSize = 13.sp, color = colors.error,
                    )
                    EditionCard(stringResource(R.string.kali_full), stringResource(R.string.kali_full_hint), true) {
                        KaliLinux.clearFailure(); KaliLinux.install(this@KaliActivity, KaliLinux.Edition.FULL)
                    }
                    EditionCard(stringResource(R.string.kali_minimal), stringResource(R.string.kali_minimal_hint), false) {
                        KaliLinux.clearFailure(); KaliLinux.install(this@KaliActivity, KaliLinux.Edition.MINIMAL)
                    }
                    Text(stringResource(R.string.kali_limits), fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun EditionCard(title: String, hint: String, recommended: Boolean, onClick: () -> Unit) {
        val pal = LocalPalette.current
        val colors = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(16.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().clip(shape).background(colors.surface)
                .border(1.dp, if (recommended) pal.signal.copy(alpha = 0.6f) else pal.line, shape).clickable(onClick = onClick).padding(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                Text(hint, fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
            if (recommended) PrimaryButton(stringResource(R.string.kali_install), compact = true, onClick = onClick)
            else SecondaryButton(stringResource(R.string.kali_install), compact = true, onClick = onClick)
        }
    }

    private sealed class Desk {
        object Off : Desk()
        object Starting : Desk()
        object On : Desk()
        object NeedsPackages : Desk()
        class Failed(val detail: String) : Desk()
    }

    private var desk by mutableStateOf<Desk>(Desk.Off)
    private var tab by mutableStateOf(0)
    private var deskProcess: Process? = null
    private var vnc: com.droiddeck.launcher.ui.VncView? = null

    private val deskTail = ArrayDeque<String>()

    private fun deskLog(fallback: String, lines: Int = 10): String = synchronized(deskTail) { deskTail.toList().takeLast(lines).joinToString("\n") }.ifBlank { fallback }
    private var showLog by mutableStateOf(false)

    private fun startDesktop() {
        if (desk == Desk.Starting || desk == Desk.On) return
        desk = Desk.Starting
        synchronized(deskTail) { deskTail.clear() }
        val m = resources.displayMetrics
        fun even(v: Float) = (v.toInt() / 2) * 2
        val w = even(maxOf(m.widthPixels, m.heightPixels) / 1.5f).coerceAtLeast(1024)
        val h = even(minOf(m.widthPixels, m.heightPixels) / 1.5f).coerceAtLeast(600)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        Thread({
            val tail = deskTail
            try {
                val process = KaliLinux.desktopProcess(this, w, h).start()
                deskProcess = process
                Thread({
                    runCatching {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            Log.i("KaliDesktop", line)
                            synchronized(tail) { tail.addLast(line); while (tail.size > 80) tail.removeFirst() }
                        }
                    }
                }, "kali-desktop-log").apply { isDaemon = true; start() }
                val deadline = System.currentTimeMillis() + 180_000
                var up = false
                while (System.currentTimeMillis() < deadline && process.isAlive) {
                    up = runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", KaliLinux.DESKTOP_PORT), 500) } }.isSuccess
                    if (up) break
                    Thread.sleep(500)
                }
                runOnUiThread {
                    when {
                        up -> { desk = Desk.On; vnc?.connect(KaliLinux.DESKTOP_PORT) }
                        !process.isAlive && process.exitValue() == KaliLinux.NEEDS_PACKAGES -> desk = Desk.NeedsPackages
                        else -> {
                            desk = Desk.Failed(synchronized(tail) { tail.joinToString("\n") })
                            stopDesktop(keepState = true)
                        }
                    }
                }
                val code = process.waitFor()
                Thread.sleep(300)
                runOnUiThread {
                    if (desk == Desk.On) {
                        vnc?.disconnect()
                        desk = if (code == KaliLinux.NEEDS_PACKAGES) Desk.NeedsPackages else Desk.Failed(deskLog("exit $code"))
                        requestedOrientation = com.droiddeck.launcher.core.AppUiPrefs.orientation(this)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { desk = Desk.Failed(e.message ?: e.toString()) }
            }
        }, "kali-desktop").apply { isDaemon = true; start() }
    }

    private fun stopDesktop(keepState: Boolean = false) {
        vnc?.disconnect()
        deskProcess?.let { p ->
            val pid = com.droiddeck.launcher.core.HostProcess.pidOf(p)
            if (pid > 0) runCatching { android.system.Os.kill(pid, android.system.OsConstants.SIGQUIT) } else p.destroy()
            Thread { if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }.start()
        }
        deskProcess = null
        if (!keepState) desk = Desk.Off
        requestedOrientation = com.droiddeck.launcher.core.AppUiPrefs.orientation(this)
    }

    private fun runInTerminal(command: String) {
        tab = 0
        session?.write(command + "\n")
        terminalView?.post { showKeyboard() }
    }

    @Composable
    private fun Terminal() {
        val pal = LocalPalette.current
        val colors = MaterialTheme.colorScheme
        var confirmRemove by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().background(colors.surface).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.kali_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Key(stringResource(R.string.kali_tab_terminal), tab == 0) { tab = 0 }
                Key(stringResource(R.string.kali_tab_desktop), tab == 1) { tab = 1 }
                if (tab == 0) {
                    SecondaryButton(stringResource(R.string.drawer_keyboard), compact = true) { showKeyboard() }
                    SecondaryButton(stringResource(R.string.kali_all_tools), compact = true) { runInTerminal("apt update && apt install -y kali-linux-default") }
                    SecondaryButton(stringResource(R.string.kali_remove), compact = true) { confirmRemove = true }
                } else if (desk == Desk.On) {
                    SecondaryButton(stringResource(R.string.drawer_keyboard), compact = true) {
                        vnc?.let { v -> v.requestFocus(); (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, InputMethodManager.SHOW_IMPLICIT) }
                    }
                    Key("ESC") { vnc?.tapKey(0xff1b) }
                    Key("TAB") { vnc?.tapKey(0xff09) }
                    Key("CTRL+C") { vnc?.tapKey('c'.code, 0xffe3) }
                    Key("CTRL+V") { vnc?.tapKey('v'.code, 0xffe3) }
                    Key("ALT+TAB") { vnc?.tapKey(0xff09, 0xffe9) }
                    SecondaryButton(stringResource(R.string.kali_desktop_log), compact = true) { showLog = true }
                    SecondaryButton(stringResource(R.string.kali_desktop_stop), compact = true) { stopDesktop() }
                }
            }
            if (tab == 0) Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().background(colors.surface).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Key("ESC") { send("\u001b") }
                Key("TAB") { send("\t") }
                Key(if (ctrl) "CTRL •" else "CTRL", ctrl) { ctrl = !ctrl }
                Key(if (alt) "ALT •" else "ALT", alt) { alt = !alt }
                Key("↑") { send("\u001b[A") }
                Key("↓") { send("\u001b[B") }
                Key("←") { send("\u001b[D") }
                Key("→") { send("\u001b[C") }
                Key("^C") { send("\u0003") }
                Key("^D") { send("\u0004") }
                Key("|") { send("|") }
                Key("/") { send("/") }
                Key("-") { send("-") }
            }
            if (tab == 0 && finished) Text(
                stringResource(R.string.kali_session_ended), fontSize = 13.sp, color = pal.signal,
                modifier = Modifier.fillMaxWidth().background(colors.surface).clickable { restart() }.padding(12.dp),
            )
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                AndroidView(
                    factory = { ctx -> createTerminal(ctx) },
                    update = { it.visibility = if (tab == 0) android.view.View.VISIBLE else android.view.View.GONE },
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                )
                AndroidView(
                    factory = { ctx -> com.droiddeck.launcher.ui.VncView(ctx).also { v ->
                        vnc = v
                        v.onState = { connected, error ->
                            if (!connected && desk == Desk.On && deskProcess?.isAlive == true) { desk = Desk.Failed(deskLog(error ?: "")); stopDesktop(keepState = true) }
                        }
                    } },
                    update = { it.visibility = if (tab == 1 && desk == Desk.On) android.view.View.VISIBLE else android.view.View.GONE },
                    modifier = Modifier.fillMaxSize(),
                )
                if (tab == 1 && desk != Desk.On) DesktopPanel()
            }
        }
        if (showLog) AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(stringResource(R.string.kali_desktop_log)) },
            text = {
                Text(
                    deskLog("—", 60), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text(stringResource(R.string.common_close)) } },
        )
        if (confirmRemove) AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.kali_remove_title)) },
            text = { Text(stringResource(R.string.kali_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    stopDesktop()
                    session?.finishIfRunning()
                    session = null
                    terminalView = null
                    installed = false
                    KaliLinux.remove(this@KaliActivity) {}
                }) { Text(stringResource(R.string.kali_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    @Composable
    private fun DesktopPanel() {
        val colors = MaterialTheme.colorScheme
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Text(stringResource(R.string.kali_desktop_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Text(stringResource(R.string.kali_desktop_intro), fontSize = 14.sp, color = colors.onSurfaceVariant)
            when (val d = desk) {
                Desk.Starting -> {
                    Text(stringResource(R.string.kali_desktop_starting), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp))
                }
                Desk.NeedsPackages -> {
                    Text(stringResource(R.string.kali_desktop_missing), fontSize = 14.sp, color = colors.onBackground)
                    PrimaryButton(stringResource(R.string.kali_desktop_install)) { desk = Desk.Off; runInTerminal("apt update && apt install -y ${KaliLinux.DESKTOP_PACKAGES}") }
                }
                else -> {
                    if (d is Desk.Failed) Text(stringResource(R.string.kali_desktop_failed, d.detail), fontSize = 13.sp, color = colors.error)
                    PrimaryButton(stringResource(R.string.kali_desktop_start)) { startDesktop() }
                    Text(stringResource(R.string.kali_desktop_touch), fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun Key(label: String, on: Boolean = false, onClick: () -> Unit) {
        val pal = LocalPalette.current
        val colors = MaterialTheme.colorScheme
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else colors.onBackground,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) pal.primary else colors.surfaceVariant)
                .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    private fun send(text: String) {
        session?.write(text)
        terminalView?.requestFocus()
    }

    private fun createTerminal(ctx: Context): TerminalView {
        val client = Client()
        val view = TerminalView(ctx, null).apply {
            setTerminalViewClient(client)
            setTextSize((14 * resources.displayMetrics.density).toInt())
            isFocusable = true
            isFocusableInTouchMode = true
        }
        terminalView = view
        val (cmd, env) = KaliLinux.command(this)
        val s = TerminalSession(cmd.first(), KaliLinux.rootfs(this).path, cmd.toTypedArray(), env, 5000, client)
        s.mSessionName = "Kali Linux"
        session = s
        finished = false
        view.attachSession(s)
        view.post { showKeyboard() }
        return view
    }

    private fun restart() {
        val view = terminalView ?: return
        val client = Client()
        val (cmd, env) = KaliLinux.command(this)
        val s = TerminalSession(cmd.first(), KaliLinux.rootfs(this).path, cmd.toTypedArray(), env, 5000, client)
        session = s
        finished = false
        view.setTerminalViewClient(client)
        view.attachSession(s)
    }

    private fun showKeyboard() {
        terminalView?.let { view ->
            view.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private inner class Client : TerminalSessionClient, TerminalViewClient {
        override fun onTextChanged(changedSession: TerminalSession) { terminalView?.onScreenUpdated() }
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) {
            if (finishedSession === session) finished = true
            terminalView?.onScreenUpdated()
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Kali", text))
        }
        override fun onPasteTextFromClipboard(session: TerminalSession) {
            val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            if (clip != null && clip.itemCount > 0) session.write(clip.getItemAt(0).coerceToText(this@KaliActivity).toString())
        }
        override fun onBell(session: TerminalSession) { terminalView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
        override fun onColorsChanged(changedSession: TerminalSession) { terminalView?.invalidate() }
        override fun onTerminalCursorStateChange(state: Boolean) { terminalView?.setTerminalCursorBlinkerRate(if (state) 600 else 0) }
        override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
        override fun logError(tag: String, message: String) { Log.e(tag, message) }
        override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
        override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
        override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
        override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
        override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, e.message, e) }

        override fun onScale(scale: Float): Float = 1f
        override fun onSingleTapUp(e: MotionEvent) = showKeyboard()
        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
        override fun shouldEnforceCharBasedInput(): Boolean = true
        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
        override fun isTerminalViewSelected(): Boolean = true
        override fun copyModeChanged(copyMode: Boolean) = Unit
        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
        override fun onLongPress(event: MotionEvent): Boolean = false
        override fun readControlKey(): Boolean = ctrl.also { if (it) ctrl = false }
        override fun readAltKey(): Boolean = alt.also { if (it) alt = false }
        override fun readShiftKey(): Boolean = false
        override fun readFnKey(): Boolean = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
        override fun onEmulatorSet() { terminalView?.onScreenUpdated() }
    }
}
