package com.droiddeck.launcher.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.Downloader
import com.droiddeck.launcher.core.Hashes
import org.json.JSONArray
import java.io.File

/**
 * AriseOS: emulators that have a native Android build. They run as their own Android app, straight on the
 * phone's GPU driver and with NCE (the game's ARM code on the CPU), with no Linux, proot or FEX in between,
 * which is far faster than their Linux builds. AriseOS downloads the APK from the project's GitHub releases,
 * hands it to Android's installer and opens the app from the desktop dock.
 */
object AndroidEmulators {
    class Extra(
        val id: String, val name: String, val system: String, val repo: String, val iconRes: Int,
        val asset: Regex, val tag: Regex, val packages: List<String>,
    )

    val all = listOf(
        Extra(
            "lichii-android", "Lichii Android", "Switch / Switch 2 · Android nativo", "pavelgajdo/Lichii-Android",
            R.drawable.emu_lichii, Regex("\\.apk$"), Regex("^android-"),
            listOf("dev.lichii.android", "dev.lichii.android.nightly"),
        ),
    )

    var working by mutableStateOf<String?>(null)
        private set
    var percent by mutableStateOf(-1)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Bumped when the app comes back to the front, so the dock checks again what is installed. */
    var refresh by mutableStateOf(0)

    fun installedPackage(context: Context, extra: Extra): String? = extra.packages.firstOrNull { pkg ->
        try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
    }

    fun launch(context: Context, extra: Extra): Boolean {
        val pkg = installedPackage(context, extra) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun install(context: Context, extra: Extra) {
        if (working != null) return
        val app = context.applicationContext
        working = extra.id; percent = -1; error = null
        Thread({
            val message = try { download(app, extra) } catch (e: Exception) { e.message ?: app.getString(R.string.user_apps_failed) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { working = null; error = message }
        }, "android-emulator-${extra.id}").start()
    }

    /** Returns an error message, or null once Android's installer has been opened. */
    private fun download(context: Context, extra: Extra): String? {
        val body = Downloader.downloadString("https://api.github.com/repos/${extra.repo}/releases?per_page=100")
            ?: return context.getString(R.string.user_apps_github_unreachable, extra.repo)
        val releases = JSONArray(body)
        var url: String? = null; var name = ""; var sha: String? = null
        loop@ for (i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            if (r.optBoolean("draft") || !extra.tag.containsMatchIn(r.optString("tag_name"))) continue
            val assets = r.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (extra.asset.containsMatchIn(a.getString("name"))) {
                    url = a.getString("browser_download_url"); name = a.getString("name")
                    sha = Hashes.githubSha256(a.optString("digest")); break@loop
                }
            }
        }
        if (url == null) return context.getString(R.string.user_apps_github_unreachable, extra.repo)
        val dir = File(context.cacheDir, "share/apk").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.name != name) it.delete() }
        val apk = File(dir, name)
        val ok = Downloader.downloadFile(url, apk, true, sha) { f -> android.os.Handler(android.os.Looper.getMainLooper()).post { percent = if (f < 0) -1 else (f * 100).toInt() } }
        if (!ok) return context.getString(R.string.user_apps_failed)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".logs", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return null
    }
}
