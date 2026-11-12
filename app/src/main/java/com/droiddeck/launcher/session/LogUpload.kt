package com.droiddeck.launcher.session

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.droiddeck.launcher.BuildConfig
import com.droiddeck.launcher.R
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipFile

/** Session logs to the phone, to a GitHub repository (with a device profile), or to another app. */
object LogUpload {
    private const val PREFS = "arise_log_upload"
    private const val DEFAULT_REPO = "Alway-L/AriseOS-logs"
    // The GitHub contents API takes the file base64-encoded in one JSON body.
    private const val MAX_UPLOAD_BYTES = 40L * 1024 * 1024
    private val main = Handler(Looper.getMainLooper())

    fun repo(context: Context): String = prefs(context).getString("repo", null)?.takeIf { it.contains('/') } ?: DEFAULT_REPO
    private fun token(context: Context): String? = prefs(context).getString("token", null)?.takeIf { it.isNotBlank() }
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun choose(activity: Activity, zip: File) {
        val options = arrayOf(
            activity.getString(R.string.logupload_github),
            activity.getString(R.string.logupload_phone),
            activity.getString(R.string.logupload_both),
            activity.getString(R.string.logshare_chooser),
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.logupload_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toGitHub(activity, zip)
                    1 -> toPhone(activity, zip)
                    2 -> { toPhone(activity, zip); toGitHub(activity, zip) }
                    else -> runCatching { activity.startActivity(SessionLogShare.shareIntent(activity, zip)) }
                }
            }
            .setNeutralButton(R.string.logupload_settings) { _, _ -> configure(activity) { } }
            .show()
    }

    fun toPhone(activity: Activity, zip: File) {
        val dest = runCatching {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AriseOS/Logs").apply { mkdirs() }
            File(dir, zip.name).also { zip.copyTo(it, overwrite = true) }
        }.getOrNull()
        if (dest != null) Toast.makeText(activity, activity.getString(R.string.logupload_saved, "Download/AriseOS/Logs/${dest.name}"), Toast.LENGTH_LONG).show()
        else runCatching { activity.startActivity(SessionLogShare.shareIntent(activity, zip)) }
    }

    fun toGitHub(activity: Activity, zip: File) {
        if (token(activity) == null) {
            configure(activity) { toGitHub(activity, zip) }
            return
        }
        Toast.makeText(activity, R.string.logupload_sending, Toast.LENGTH_SHORT).show()
        val app = activity.applicationContext
        Thread({
            val result = runCatching { upload(app, zip) }
            main.post {
                val text = result.fold(
                    { path -> app.getString(R.string.logupload_sent, path) },
                    { e -> app.getString(R.string.logupload_failed, e.message ?: e.javaClass.simpleName) },
                )
                Toast.makeText(app, text, Toast.LENGTH_LONG).show()
            }
        }, "upload-logs").start()
    }

    fun configure(activity: Activity, then: () -> Unit) {
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val repoField = EditText(activity).apply { hint = DEFAULT_REPO; setText(repo(activity)); isSingleLine = true }
        val tokenField = EditText(activity).apply {
            hint = activity.getString(R.string.logupload_token_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (token(activity) != null) setText(token(activity))
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(repoField)
            addView(tokenField)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.logupload_settings)
            .setMessage(R.string.logupload_settings_help)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs(activity).edit()
                    .putString("repo", repoField.text.toString().trim().ifEmpty { DEFAULT_REPO })
                    .putString("token", tokenField.text.toString().trim())
                    .apply()
                if (token(activity) != null) then()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun upload(context: Context, zip: File): String {
        require(zip.length() <= MAX_UPLOAD_BYTES) { "zip ${zip.length() shr 20} MiB > ${MAX_UPLOAD_BYTES shr 20} MiB" }
        val token = token(context) ?: error("no token")
        val profile = deviceProfile(context, zip)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val folder = "${deviceSlug()}/${stamp}_${zip.nameWithoutExtension.removePrefix("DroidDeck-")}"
        val summary = "${Build.MANUFACTURER} ${Build.MODEL}, AriseOS ${BuildConfig.VERSION_NAME}, ${profile.optString("emulator", "?")}"
        put(context, token, "$folder/device.json", profile.toString(2).toByteArray(), "logs: $summary")
        put(context, token, "$folder/${zip.name}", zip.readBytes(), "logs: $summary")
        return "${repo(context)}/$folder"
    }

    private fun put(context: Context, token: String, path: String, bytes: ByteArray, message: String) {
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .toString().toByteArray()
        val url = URL("https://api.github.com/repos/${repo(context)}/contents/$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setFixedLengthStreamingMode(body.size)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "AriseOS/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                    ?.let { runCatching { JSONObject(it).optString("message") }.getOrNull() } ?: ""
                error("GitHub $code $detail".trim())
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun deviceSlug(): String =
        "${Build.MANUFACTURER}-${Build.MODEL}".lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun deviceProfile(context: Context, zip: File): JSONObject {
        val json = JSONObject()
        json.put("written", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date()))
        json.put("app", JSONObject().put("version", BuildConfig.VERSION_NAME).put("code", BuildConfig.VERSION_CODE))
        json.put("device", JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("board", Build.BOARD)
            .put("hardware", Build.HARDWARE)
            .put("soc", if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE)
            .put("android", Build.VERSION.RELEASE)
            .put("api", Build.VERSION.SDK_INT)
            .put("kernel", System.getProperty("os.version") ?: ""))
        val mem = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mem)
        json.put("memory", JSONObject()
            .put("ram_mib", mem.totalMem shr 20)
            .put("available_mib", mem.availMem shr 20)
            .put("swap_mib", meminfo("SwapTotal") shr 10)
            .put("swap_free_mib", meminfo("SwapFree") shr 10))
        json.put("cpu", JSONObject()
            .put("cores", Runtime.getRuntime().availableProcessors())
            .put("max_mhz", (0 until Runtime.getRuntime().availableProcessors()).map { cpu ->
                read("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")?.toLongOrNull()?.div(1000) ?: 0
            }.joinToString(",")))
        json.put("gpu", JSONObject()
            .put("model", read("/sys/class/kgsl/kgsl-3d0/gpu_model") ?: "")
            .put("max_mhz", read("/sys/class/kgsl/kgsl-3d0/max_gpuclk")?.toLongOrNull()?.div(1_000_000) ?: 0))
        emulatorSettings(zip)?.let { (name, settings) -> json.put("emulator", name).put("emulator_settings", settings) }
        return json
    }

    private val SETTING_KEYS = listOf(
        "memory_layout_mode", "resolution_setup", "backend", "cpu_backend", "cpu_accuracy", "gpu_accuracy",
        "use_asynchronous_shaders", "vsync_mode", "use_disk_shader_cache", "scaling_filter", "anti_aliasing",
        "use_speed_limit", "speed_limit", "use_docked_mode", "renderer_backend",
    )

    private fun emulatorSettings(zip: File): Pair<String, JSONObject>? = runCatching {
        ZipFile(zip).use { z ->
            val entry = z.entries().asSequence().firstOrNull { it.name.startsWith("emulators/eden/") && it.name.endsWith("qt-config.ini") }
                ?: z.entries().asSequence().firstOrNull { it.name.startsWith("emulators/") && it.name.endsWith("qt-config.ini") }
                ?: return null
            val settings = JSONObject()
            z.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val key = line.substringBefore('=').substringAfterLast('\\')
                    if (!line.contains("\\default=") && key in SETTING_KEYS) settings.put(key, line.substringAfter('='))
                }
            }
            val name = if (entry.name.contains("/eden/")) "kanario" else entry.name.removePrefix("emulators/").substringBefore('/')
            name to settings
        }
    }.getOrNull()

    private fun read(path: String): String? = runCatching { File(path).readText().trim().lineSequence().first().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun meminfo(key: String): Long = runCatching {
        File("/proc/meminfo").useLines { lines -> lines.first { it.startsWith("$key:") }.split(Regex("\\s+"))[1].toLong() }
    }.getOrDefault(0L)
}
