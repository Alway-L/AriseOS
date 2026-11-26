package com.droiddeck.launcher.session

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the screen and the game's sound to an MP4 in the gallery (Movies/AriseOS) through Android's
 * screen capture. Sound needs Android 10 and the microphone permission; without them the clip is silent.
 */
object ClipRecorder {
    private const val TAG = "ClipRecorder"
    private const val RATE = 48_000
    private const val CHANNELS = 2

    var recording by mutableStateOf(false)
        private set

    fun canRecordSound(context: Context) = Build.VERSION.SDK_INT >= 29 &&
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private class Session(
        val projection: MediaProjection,
        val video: MediaCodec,
        val audio: MediaCodec?,
        val record: AudioRecord?,
        val muxer: MediaMuxer,
        val pfd: ParcelFileDescriptor?,
        val pending: Uri?,
        val baseUs: Long,
    ) {
        var display: VirtualDisplay? = null
        val lock = Object()
        var videoTrack = -1
        var audioTrack = -1
        var started = false
        var wroteVideo = false
        @Volatile var stopping = false
        val threads = mutableListOf<Thread>()
    }

    private var session: Session? = null
    private var onStopped: ((Boolean) -> Unit)? = null

    fun request(context: Context): Intent =
        (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()

    private fun size(context: Context): Triple<Int, Int, Int> {
        val m = context.resources.displayMetrics
        var w = m.widthPixels; var h = m.heightPixels
        val scale = 1920f / maxOf(w, h)
        if (scale < 1f) { w = (w * scale).toInt(); h = (h * scale).toInt() }
        return Triple(w and 15.inv(), h and 15.inv(), m.densityDpi)
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun soundSource(context: Context, projection: MediaProjection): AudioRecord? {
        if (!canRecordSound(context)) return null
        return runCatching {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(min, 8192) * 4)
                .setAudioPlaybackCaptureConfig(config).build()
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.onFailure { Log.w(TAG, "no sound capture", it) }.getOrNull()
    }

    /** Returns false when recording could not start. */
    fun start(context: Context, resultCode: Int, data: Intent, onStopped: (Boolean) -> Unit): Boolean {
        if (recording) return true
        val app = context.applicationContext
        val (w, h, dpi) = size(app)
        val name = "AriseOS_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        var pfd: ParcelFileDescriptor? = null
        var pending: Uri? = null
        var projection: MediaProjection? = null
        var video: MediaCodec? = null
        var audio: MediaCodec? = null
        var record: AudioRecord? = null
        return try {
            val muxer = if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AriseOS")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = app.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                pending = uri
                val fd = app.contentResolver.openFileDescriptor(uri, "rw") ?: throw IllegalStateException("no file")
                pfd = fd
                MediaMuxer(fd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                val dir = File(app.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AriseOS").apply { mkdirs() }
                MediaMuxer(File(dir, name).path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 16_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 60)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1_000_000L / 30)
            }
            val v = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            video = v
            v.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = v.createInputSurface()

            val manager = app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = manager.getMediaProjection(resultCode, data) ?: throw IllegalStateException("no projection")
            projection = p
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { Handler(Looper.getMainLooper()).post { stop(app) } }
            }, Handler(Looper.getMainLooper()))

            val rec = soundSource(app, p)
            record = rec
            var a: MediaCodec? = null
            if (rec != null) {
                val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, CHANNELS).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                a = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                audio = a
                a.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            val s = Session(p, v, a, rec, muxer, pfd, pending, System.nanoTime() / 1000)
            v.start()
            a?.start()
            rec?.startRecording()
            s.display = p.createVirtualDisplay("AriseOS clip", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null)
            s.threads += Thread({ drain(s, v, false) }, "clip-video")
            if (a != null && rec != null) {
                s.threads += Thread({ feed(s, rec, a) }, "clip-sound-in")
                s.threads += Thread({ drain(s, a, true) }, "clip-sound-out")
            }
            s.threads.forEach { it.start() }
            session = s
            this.onStopped = onStopped
            recording = true
            true
        } catch (e: Exception) {
            Log.w(TAG, "clip could not start", e)
            runCatching { record?.release() }
            runCatching { audio?.release() }
            runCatching { video?.release() }
            runCatching { projection?.stop() }
            runCatching { pfd?.close() }
            pending?.let { runCatching { app.contentResolver.delete(it, null, null) } }
            false
        }
    }

    private fun feed(s: Session, record: AudioRecord, codec: MediaCodec) {
        val chunk = ByteArray(4096)
        val bytesPerSecond = RATE * CHANNELS * 2
        var sent = 0L
        var firstUs = -1L
        try {
            while (true) {
                val n = if (s.stopping) 0 else record.read(chunk, 0, chunk.size)
                if (n < 0) break
                val index = codec.dequeueInputBuffer(10_000)
                if (index < 0) continue
                val buf = codec.getInputBuffer(index) ?: continue
                buf.clear()
                if (firstUs < 0) firstUs = System.nanoTime() / 1000 - s.baseUs
                val pts = firstUs + sent * 1_000_000L / bytesPerSecond
                if (s.stopping) {
                    codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                buf.put(chunk, 0, n)
                codec.queueInputBuffer(index, 0, n, pts, 0)
                sent += n
            }
        } catch (e: Exception) {
            Log.w(TAG, "sound input stopped", e)
        }
    }

    private fun drain(s: Session, codec: MediaCodec, sound: Boolean) {
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized(s.lock) {
                        if (sound) s.audioTrack = s.muxer.addTrack(codec.outputFormat) else s.videoTrack = s.muxer.addTrack(codec.outputFormat)
                        if (s.videoTrack >= 0 && (s.audio == null || s.audioTrack >= 0)) {
                            s.muxer.start(); s.started = true
                            runCatching { s.video.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }
                        }
                    }
                    continue
                }
                if (index < 0) continue
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (!config && info.size > 0) {
                    val buf = codec.getOutputBuffer(index)
                    synchronized(s.lock) {
                        val track = if (sound) s.audioTrack else s.videoTrack
                        val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        if ((sound || s.wroteVideo || key) && s.started && buf != null && track >= 0) {
                            if (!sound) {
                                info.presentationTimeUs -= s.baseUs
                                s.wroteVideo = true
                            }
                            if (info.presentationTimeUs >= 0) s.muxer.writeSampleData(track, buf, info)
                        }
                    }
                }
                codec.releaseOutputBuffer(index, false)
                if (eos) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "encoder stopped", e)
        }
    }

    fun stop(context: Context) {
        val s = session ?: return
        session = null
        s.stopping = true
        runCatching { s.display?.release() }
        runCatching { s.video.signalEndOfInputStream() }
        s.threads.forEach { runCatching { it.join(3000) } }
        val ok = synchronized(s.lock) { s.started && s.wroteVideo }
        runCatching { s.record?.stop() }
        runCatching { s.record?.release() }
        runCatching { s.video.stop() }
        runCatching { s.video.release() }
        runCatching { s.audio?.stop() }
        runCatching { s.audio?.release() }
        val saved = ok && runCatching { s.muxer.stop() }.isSuccess
        runCatching { s.muxer.release() }
        runCatching { s.projection.stop() }
        runCatching { s.pfd?.close() }
        val app = context.applicationContext
        s.pending?.let { uri ->
            runCatching {
                if (saved) app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                else app.contentResolver.delete(uri, null, null)
            }
        }
        recording = false
        onStopped?.invoke(saved)
        onStopped = null
    }
}
