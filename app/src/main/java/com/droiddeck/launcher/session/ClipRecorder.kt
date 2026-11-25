package com.droiddeck.launcher.session

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Records the screen to an MP4 in the gallery (Movies/AriseOS) through Android's screen capture. */
object ClipRecorder {
    var recording by mutableStateOf(false)
        private set

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pending: Uri? = null
    private var onStopped: ((Boolean) -> Unit)? = null

    fun request(context: Context): Intent =
        (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()

    private fun size(context: Context): Triple<Int, Int, Int> {
        val m = context.resources.displayMetrics
        var w = m.widthPixels; var h = m.heightPixels
        val scale = 1920f / maxOf(w, h)
        if (scale < 1f) { w = (w * scale).toInt(); h = (h * scale).toInt() }
        return Triple(w and 1.inv(), h and 1.inv(), m.densityDpi)
    }

    /** Returns false when recording could not start. */
    fun start(context: Context, resultCode: Int, data: Intent, onStopped: (Boolean) -> Unit): Boolean {
        if (recording) return true
        val app = context.applicationContext
        val (w, h, dpi) = size(app)
        val name = "AriseOS_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        return try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else @Suppress("DEPRECATION") MediaRecorder()
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(w, h)
            r.setVideoFrameRate(60)
            r.setVideoEncodingBitRate(16_000_000)
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AriseOS")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = app.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                pending = uri
                pfd = app.contentResolver.openFileDescriptor(uri, "w")
                r.setOutputFile(pfd!!.fileDescriptor)
            } else {
                val dir = File(app.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AriseOS").apply { mkdirs() }
                r.setOutputFile(File(dir, name).path)
            }
            r.prepare()
            val manager = app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = manager.getMediaProjection(resultCode, data) ?: throw IllegalStateException("no projection")
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { Handler(Looper.getMainLooper()).post { stop(app) } }
            }, Handler(Looper.getMainLooper()))
            display = p.createVirtualDisplay("AriseOS clip", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
            r.start()
            recorder = r; projection = p; this.onStopped = onStopped
            recording = true
            true
        } catch (e: Exception) {
            cleanup(app, false)
            false
        }
    }

    fun stop(context: Context) {
        if (!recording) return
        val ok = try { recorder?.stop(); true } catch (_: RuntimeException) { false }
        cleanup(context.applicationContext, ok)
        onStopped?.invoke(ok)
        onStopped = null
    }

    private fun cleanup(context: Context, keep: Boolean) {
        runCatching { recorder?.release() }
        runCatching { display?.release() }
        runCatching { projection?.stop() }
        runCatching { pfd?.close() }
        pending?.let { uri ->
            runCatching {
                if (keep) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                else context.contentResolver.delete(uri, null, null)
            }
        }
        recorder = null; display = null; projection = null; pfd = null; pending = null
        recording = false
    }
}
