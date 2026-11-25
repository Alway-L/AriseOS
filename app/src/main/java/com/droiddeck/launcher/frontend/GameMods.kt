package com.droiddeck.launcher.frontend

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mods unpacked from a .zip into a game's folder. Every file a mod replaces is copied aside first, so
 * removing the mod deletes what it added and puts the game's own files back.
 */
object GameMods {
    class Mod(val id: String, val name: String, val files: Int, val installed: Long)

    private fun root(context: Context, game: Library.SteamGame) = File(context.filesDir, "mods/${game.gameId}")

    fun list(context: Context, game: Library.SteamGame): List<Mod> =
        root(context, game).listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching {
                val j = JSONObject(f.readText())
                Mod(f.name.removeSuffix(".json"), j.getString("name"), j.getJSONArray("files").length(), j.optLong("installed"))
            }.getOrNull()
        }.sortedByDescending { it.installed }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "mod.zip"

    private fun entries(context: Context, uri: Uri): List<String> {
        val names = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) names += e.name.replace('\\', '/')
                }
            }
        }
        return names
    }

    /** A single top folder is dropped unless the game itself has a folder of that name. */
    private fun strip(names: List<String>, dir: File): String? {
        val tops = names.map { it.substringBefore('/', "") }.toSet()
        val top = tops.singleOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return if (File(dir, top).isDirectory) null else "$top/"
    }

    /** Returns null when the mod is installed, else what went wrong. */
    fun install(context: Context, game: Library.SteamGame, uri: Uri): String? {
        val dir = game.gameFiles?.takeIf { it.isDirectory } ?: return "game folder not found"
        val name = displayName(context, uri).removeSuffix(".zip").removeSuffix(".ZIP")
        val names = entries(context, uri)
        if (names.isEmpty()) return "empty or not a .zip"
        val prefix = strip(names, dir)
        val id = System.currentTimeMillis().toString()
        val modRoot = root(context, game)
        val backup = File(modRoot, "$id.backup")
        val canonical = dir.canonicalPath + File.separator
        val files = JSONArray()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    var rel = e.name.replace('\\', '/')
                    if (prefix != null) rel = rel.removePrefix(prefix)
                    if (rel.isEmpty()) continue
                    val target = File(dir, rel)
                    if (!target.canonicalPath.startsWith(canonical)) continue
                    val replaced = target.isFile
                    if (replaced) {
                        val saved = File(backup, rel)
                        if (!saved.exists()) { saved.parentFile?.mkdirs(); target.copyTo(saved) }
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    files.put(JSONObject().put("path", rel).put("replaced", replaced))
                }
            }
        } ?: return "could not read the file"
        modRoot.mkdirs()
        File(modRoot, "$id.json").writeText(
            JSONObject().put("name", name).put("installed", System.currentTimeMillis()).put("files", files).toString(),
        )
        return null
    }

    fun remove(context: Context, game: Library.SteamGame, mod: Mod) {
        val dir = game.gameFiles ?: return
        val modRoot = root(context, game)
        val record = File(modRoot, "${mod.id}.json")
        val backup = File(modRoot, "${mod.id}.backup")
        val files = runCatching { JSONObject(record.readText()).getJSONArray("files") }.getOrNull() ?: JSONArray()
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val rel = f.getString("path")
            val target = File(dir, rel)
            val saved = File(backup, rel)
            if (f.optBoolean("replaced") && saved.isFile) saved.copyTo(target, overwrite = true) else target.delete()
        }
        backup.deleteRecursively()
        record.delete()
    }
}
