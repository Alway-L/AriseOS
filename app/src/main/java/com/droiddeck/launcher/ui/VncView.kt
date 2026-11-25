package com.droiddeck.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

/** A minimal RFB 3.8 viewer for a local, password-less VNC server: raw and copy-rect updates, mouse from touch, keys as X keysyms. */
class VncView(context: Context) : View(context) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var socket: Socket? = null
    private var out: DataOutputStream? = null
    private val sendLock = Any()
    private val sender = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    @Volatile private var fbW = 0
    @Volatile private var fbH = 0
    private var pixels = IntArray(0)
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val black = Paint().apply { color = 0xFF000000.toInt() }
    private val matrix = Matrix()
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var cursorX = 0
    private var cursorY = 0
    private var buttons = 0
    var onState: (connected: Boolean, error: String?) -> Unit = { _, _ -> }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun connect(port: Int) {
        disconnect()
        Thread({
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress("127.0.0.1", port), 3000)
                socket = s
                val input = DataInputStream(s.getInputStream().buffered(1 shl 16))
                val o = DataOutputStream(s.getOutputStream().buffered(4096))
                out = o
                handshake(input, o)
                main.post { onState(true, null) }
                loop(input)
            } catch (e: IOException) {
                if (socket != null) main.post { onState(false, e.message ?: e.toString()) }
            } catch (e: Exception) {
                Log.e("VncView", "rfb", e)
                main.post { onState(false, e.message ?: e.toString()) }
            } finally {
                runCatching { socket?.close() }
                socket = null
            }
        }, "vnc-client").apply { isDaemon = true; start() }
    }

    fun disconnect() {
        val s = socket
        socket = null
        runCatching { s?.close() }
    }

    private fun handshake(input: DataInputStream, o: DataOutputStream) {
        val version = ByteArray(12).also { input.readFully(it) }
        if (!String(version).startsWith("RFB ")) throw IOException("not a VNC server")
        o.write("RFB 003.008\n".toByteArray()); o.flush()
        val count = input.readUnsignedByte()
        if (count == 0) throw IOException(reason(input))
        val types = ByteArray(count).also { input.readFully(it) }
        if (types.none { it.toInt() == 1 }) throw IOException("the server wants a password")
        o.writeByte(1); o.flush()
        if (input.readInt() != 0) throw IOException(reason(input))
        o.writeByte(1); o.flush()
        val w = input.readUnsignedShort()
        val h = input.readUnsignedShort()
        input.skipBytes(16)
        input.skipBytes(input.readInt())
        resize(w, h)
        synchronized(sendLock) {
            o.writeByte(0); o.write(ByteArray(3))
            o.writeByte(32); o.writeByte(24); o.writeByte(0); o.writeByte(1)
            o.writeShort(255); o.writeShort(255); o.writeShort(255)
            o.writeByte(16); o.writeByte(8); o.writeByte(0); o.write(ByteArray(3))
            val encodings = intArrayOf(1, 0, -223)
            o.writeByte(2); o.writeByte(0); o.writeShort(encodings.size)
            encodings.forEach { o.writeInt(it) }
            request(o, false)
            o.flush()
        }
    }

    private fun reason(input: DataInputStream): String = ByteArray(input.readInt()).also { input.readFully(it) }.let { String(it) }

    private fun request(o: DataOutputStream, incremental: Boolean) {
        o.writeByte(3); o.writeByte(if (incremental) 1 else 0)
        o.writeShort(0); o.writeShort(0); o.writeShort(fbW); o.writeShort(fbH)
    }

    private fun resize(w: Int, h: Int) {
        fbW = w; fbH = h
        synchronized(this) {
            pixels = IntArray(w * h)
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        main.post { layoutFrame(); invalidate() }
    }

    private fun loop(input: DataInputStream) {
        var row = ByteArray(0)
        while (socket != null) {
            when (input.readUnsignedByte()) {
                0 -> {
                    input.readUnsignedByte()
                    val rects = input.readUnsignedShort()
                    for (r in 0 until rects) {
                        val x = input.readUnsignedShort(); val y = input.readUnsignedShort()
                        val w = input.readUnsignedShort(); val h = input.readUnsignedShort()
                        when (input.readInt()) {
                            0 -> {
                                if (row.size < w * 4) row = ByteArray(w * 4)
                                synchronized(this) {
                                    for (j in 0 until h) {
                                        input.readFully(row, 0, w * 4)
                                        val base = (y + j) * fbW + x
                                        if (y + j >= fbH) continue
                                        var k = 0
                                        for (i in 0 until w) {
                                            pixels[base + i] = (0xFF shl 24) or ((row[k + 2].toInt() and 0xFF) shl 16) or
                                                ((row[k + 1].toInt() and 0xFF) shl 8) or (row[k].toInt() and 0xFF)
                                            k += 4
                                        }
                                    }
                                }
                            }
                            1 -> {
                                val sx = input.readUnsignedShort(); val sy = input.readUnsignedShort()
                                synchronized(this) {
                                    val copy = IntArray(w * h)
                                    for (j in 0 until h) System.arraycopy(pixels, (sy + j) * fbW + sx, copy, j * w, w)
                                    for (j in 0 until h) System.arraycopy(copy, j * w, pixels, (y + j) * fbW + x, w)
                                }
                            }
                            -223 -> resize(w, h)
                            else -> throw IOException("unsupported encoding")
                        }
                    }
                    synchronized(this) { bitmap?.setPixels(pixels, 0, fbW, 0, 0, fbW, fbH) }
                    main.post { invalidate() }
                    synchronized(sendLock) { out?.let { request(it, true); it.flush() } }
                }
                1 -> { input.readUnsignedByte(); input.readUnsignedShort(); input.skipBytes(input.readUnsignedShort() * 6) }
                2 -> Unit
                3 -> { input.skipBytes(3); input.skipBytes(input.readInt()) }
                else -> throw IOException("unknown server message")
            }
        }
    }

    private fun layoutFrame() {
        if (fbW == 0 || width == 0) return
        scale = minOf(width.toFloat() / fbW, height.toFloat() / fbH)
        offX = (width - fbW * scale) / 2f
        offY = (height - fbH * scale) / 2f
        matrix.setScale(scale, scale)
        matrix.postTranslate(offX, offY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutFrame()

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), black)
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        synchronized(this) { bitmap?.let { canvas.drawBitmap(it, matrix, paint) } }
        canvas.restore()
    }

    private fun pointer(mask: Int) {
        val o = out ?: return
        val x = cursorX
        val y = cursorY
        sender.execute {
            runCatching {
                synchronized(sendLock) {
                    o.writeByte(5); o.writeByte(mask); o.writeShort(x); o.writeShort(y); o.flush()
                }
            }
        }
    }

    private fun key(keysym: Int, down: Boolean) {
        val o = out ?: return
        sender.execute {
            runCatching {
                synchronized(sendLock) {
                    o.writeByte(4); o.writeByte(if (down) 1 else 0); o.writeShort(0); o.writeInt(keysym); o.flush()
                }
            }
        }
    }

    fun tapKey(keysym: Int, vararg modifiers: Int) {
        modifiers.forEach { key(it, true) }
        key(keysym, true); key(keysym, false)
        modifiers.reversed().forEach { key(it, false) }
    }

    fun typeText(text: String) {
        text.codePoints().forEach { cp ->
            when (cp) {
                '\n'.code -> tapKey(0xff0d)
                '\t'.code -> tapKey(0xff09)
                else -> tapKey(if (cp in 0x20..0xff) cp else 0x01000000 or cp)
            }
        }
    }

    private fun moveTo(e: MotionEvent) {
        if (scale <= 0f) return
        cursorX = ((e.x - offX) / scale).toInt().coerceIn(0, maxOf(0, fbW - 1))
        cursorY = ((e.y - offY) / scale).toInt().coerceIn(0, maxOf(0, fbH - 1))
    }

    private var dragging = false
    private var scrollAcc = 0f
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            moveTo(e); pointer(1); pointer(0); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            moveTo(e); pointer(1); pointer(0); pointer(1); pointer(0); return true
        }
        override fun onLongPress(e: MotionEvent) {
            moveTo(e); pointer(4); pointer(0)
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        requestFocus()
        if (e.pointerCount >= 2) {
            if (e.actionMasked == MotionEvent.ACTION_MOVE && e.historySize > 0) {
                scrollAcc += e.getY(0) - e.getHistoricalY(0, 0)
                while (abs(scrollAcc) > 24f) {
                    val button = if (scrollAcc > 0) 8 else 16
                    pointer(button); pointer(0)
                    scrollAcc -= if (scrollAcc > 0) 24f else -24f
                }
            }
            dragging = false
            return true
        }
        gestures.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { moveTo(e); downX = e.x; downY = e.y; dragging = false; scrollAcc = 0f; pointer(0) }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(e.x - downX) > slop || abs(e.y - downY) > slop)) dragging = true
                moveTo(e)
                if (dragging) { buttons = 1; pointer(buttons) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moveTo(e)
                if (dragging) { buttons = 0; pointer(0) }
                dragging = false
            }
        }
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_HOVER_MOVE) { moveTo(e); pointer(buttons); return true }
        if (e.actionMasked == MotionEvent.ACTION_SCROLL) {
            val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (v != 0f) { pointer(if (v > 0) 8 else 16); pointer(0) }
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean { typeText(text.toString()); return true }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(maxOf(1, beforeLength)) { tapKey(0xff08) }; return true
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event)
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val sym = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> 0xff0d
            KeyEvent.KEYCODE_DEL -> 0xff08
            KeyEvent.KEYCODE_FORWARD_DEL -> 0xffff
            KeyEvent.KEYCODE_TAB -> 0xff09
            KeyEvent.KEYCODE_ESCAPE -> 0xff1b
            KeyEvent.KEYCODE_DPAD_LEFT -> 0xff51
            KeyEvent.KEYCODE_DPAD_UP -> 0xff52
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0xff53
            KeyEvent.KEYCODE_DPAD_DOWN -> 0xff54
            KeyEvent.KEYCODE_MOVE_HOME -> 0xff50
            KeyEvent.KEYCODE_MOVE_END -> 0xff57
            KeyEvent.KEYCODE_PAGE_UP -> 0xff55
            KeyEvent.KEYCODE_PAGE_DOWN -> 0xff56
            else -> 0
        }
        if (sym != 0) {
            val mods = buildList { if (event.isCtrlPressed) add(0xffe3); if (event.isAltPressed) add(0xffe9); if (event.isShiftPressed) add(0xffe1) }
            tapKey(sym, *mods.toIntArray()); return true
        }
        val cp = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
        if (cp > 0) {
            val mods = buildList { if (event.isCtrlPressed) add(0xffe3); if (event.isAltPressed) add(0xffe9) }
            tapKey(if (cp in 0x20..0xff) cp else 0x01000000 or cp, *mods.toIntArray()); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        disconnect()
        super.onDetachedFromWindow()
    }
}
