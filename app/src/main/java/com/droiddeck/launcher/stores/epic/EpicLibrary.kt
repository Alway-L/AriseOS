package com.droiddeck.launcher.stores.epic

import android.content.Context
import com.droiddeck.launcher.R
import com.droiddeck.launcher.stores.CatalogItem
import com.droiddeck.launcher.stores.Store
import com.droiddeck.launcher.stores.cleanStoreText
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

data class EpicGame(
    val appName: String,
    val namespace: String,
    val catalogItemId: String,
    val title: String,
    val artCover: String = "",
    val artSquare: String = "",
    val developer: String = "",
    val description: String = "",
    val version: String = "",
    val installSize: Long = 0L,
    val canRunOffline: Boolean = true,
    val releaseDate: String = "",
    val publisher: String = "",
    val isDlc: Boolean = false,
    val artLogo: String = "",
)

object EpicLibrary {
    const val KEY_CACHE = "library_cache"
    const val KEY_SYNCED_AT = "library_synced_at_v2"  // v2: AriseOS 1.3.2 re-reads the whole library once
    private const val THROTTLE_MS = 6 * 60 * 60 * 1000L
    private const val MAX_SIZE = 1L shl 40

    sealed class SyncResult {
        class Ok(val games: List<EpicGame>) : SyncResult()
        class Failed(val message: String) : SyncResult()
        object NotLoggedIn : SyncResult()
        object Throttled : SyncResult()
        object Busy : SyncResult()
    }

    private val running = AtomicBoolean(false)

    fun toCacheJson(games: List<EpicGame>): String = JSONArray().apply {
        for (g in games) put(
            JSONObject()
                .put("appName", g.appName).put("namespace", g.namespace).put("catalogItemId", g.catalogItemId)
                .put("title", g.title).put("artCover", g.artCover).put("artSquare", g.artSquare)
                .put("developer", g.developer).put("description", g.description).put("version", g.version)
                .put("installSize", g.installSize).put("canRunOffline", g.canRunOffline)
                .put("releaseDate", g.releaseDate).put("publisher", g.publisher)
                .apply { if (g.artLogo.isNotEmpty()) put("artLogo", g.artLogo) },
        )
    }.toString()

    fun fromCacheJson(text: String?): List<EpicGame> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val games = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull { o ->
            val appName = o.optString("appName", "").ifEmpty { return@mapNotNull null }
            EpicGame(
                appName = appName,
                namespace = o.optString("namespace", ""),
                catalogItemId = o.optString("catalogItemId", ""),
                title = o.optString("title", "").ifEmpty { appName },
                artCover = o.optString("artCover", ""),
                artSquare = o.optString("artSquare", ""),
                developer = o.optString("developer", ""),
                description = o.optString("description", ""),
                version = o.optString("version", ""),
                installSize = o.optLong("installSize", 0L).takeIf { it in 0..MAX_SIZE } ?: 0L,
                canRunOffline = o.optBoolean("canRunOffline", true),
                releaseDate = o.optString("releaseDate", ""),
                publisher = o.optString("publisher", ""),
                artLogo = o.optString("artLogo", ""),
            )
        }
        return tidy(games)
    }

    private fun tidy(games: List<EpicGame>): List<EpicGame> =
        games.distinctBy { it.appName }.sortedBy { it.title.lowercase() }

    fun cached(context: Context): List<EpicGame> = fromCacheJson(EpicPrefs.get(context).getString(KEY_CACHE, null))

    fun forget(context: Context) {
        EpicPrefs.get(context).edit().remove(KEY_CACHE).remove(KEY_SYNCED_AT).apply()
    }

    fun toCatalogItem(context: Context, game: EpicGame): CatalogItem {
        val prefs = EpicPrefs.get(context)
        val wide = game.artSquare.ifEmpty { null }
        val tall = game.artCover.ifEmpty { null }
        val title = game.title.ifEmpty { game.appName }
        return CatalogItem(
            store = Store.EPIC,
            id = game.appName,
            title = title,
            imageUrl = wide ?: tall,
            tallImageUrl = tall ?: wide,
            developer = game.developer,
            publisher = game.publisher,
            releaseDate = game.releaseDate.ifEmpty { prefs.getString("release_${game.appName}", "").orEmpty() },
            description = cleanStoreText(game.description),
            owned = true,
            sizeBytes = prefs.getLong("size_${game.appName}", 0L).takeIf { it > 0 } ?: game.installSize,
            storeUrl = "https://store.epicgames.com/en-US/browse?q=" + URLEncoder.encode(title, "UTF-8"),
            extra = mapOf("namespace" to game.namespace, "catalogItemId" to game.catalogItemId, "version" to game.version),
            logoUrl = game.artLogo.ifEmpty { null },
        )
    }

    internal fun gameFrom(record: JSONObject, details: JSONObject?, before: EpicGame?): EpicGame {
        val appName = record.optString("appName")
        val d = details ?: JSONObject()
        fun pick(key: String, fallback: String) = d.optString(key, "").ifEmpty { fallback }
        return EpicGame(
            appName = appName,
            namespace = record.optString("namespace"),
            catalogItemId = record.optString("catalogItemId"),
            title = pick("title", before?.title?.takeIf { it != appName } ?: appName),
            artCover = pick("tall", before?.artCover.orEmpty()),
            artSquare = pick("wide", before?.artSquare.orEmpty()),
            developer = pick("developer", before?.developer.orEmpty()),
            description = pick("description", before?.description.orEmpty()),
            version = before?.version.orEmpty(),
            installSize = before?.installSize ?: 0L,
            canRunOffline = if (details != null) d.optBoolean("canRunOffline", true) else before?.canRunOffline ?: true,
            releaseDate = pick("releaseDate", before?.releaseDate.orEmpty()),
            publisher = pick("publisher", before?.publisher.orEmpty()),
            isDlc = d.optBoolean("dlc", false),
            artLogo = pick("logo", before?.artLogo.orEmpty()),
        )
    }

    /** Whether the game still lacks its catalog name and art (its details could not be fetched yet). */
    internal fun missingDetails(g: EpicGame) = g.title == g.appName && g.artCover.isEmpty() && g.artSquare.isEmpty()

    internal fun visible(games: List<EpicGame>): List<EpicGame> {
        val base = games.filterNot { it.isDlc }
        return tidy(if (base.isEmpty()) games else base)
    }

    /**
     * AriseOS: the catalog is asked once per owned game, which for a big library (hundreds of games) used to run
     * into Epic's rate limit; every failed answer left a game named by its hash with no art. Ask a few at a time
     * and try a failed one again after a pause.
     */
    private fun detailsFor(token: String, records: List<JSONObject>, progress: (Int) -> Unit): Array<JSONObject?> {
        val out = arrayOfNulls<JSONObject>(records.size)
        val done = java.util.concurrent.atomic.AtomicInteger()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            records.indices.map { i ->
                pool.submit {
                    val r = records[i]
                    for (attempt in 0 until 4) {
                        out[i] = try { EpicApiClient.catalogDetails(token, r.optString("namespace"), r.optString("catalogItemId")) } catch (_: Exception) { null }
                        if (out[i] != null) break
                        Thread.sleep(700L * (attempt + 1))
                    }
                    progress(done.incrementAndGet())
                }
            }.forEach { it.get() }
        } finally { pool.shutdownNow() }
        return out
    }

    fun sync(context: Context, force: Boolean, status: (String) -> Unit): SyncResult {
        if (!running.compareAndSet(false, true)) return SyncResult.Busy
        try {
            val app = context.applicationContext
            val prefs = EpicPrefs.get(app)
            val cache = cached(app)
            status(app.getString(R.string.stores_sync_sign_in))
            if (!EpicCredentialStore.isLoggedIn(app)) return SyncResult.NotLoggedIn
            val syncedAt = prefs.getLong(KEY_SYNCED_AT, 0L)
            if (!force && cache.isNotEmpty() && System.currentTimeMillis() - syncedAt in 0 until THROTTLE_MS) return SyncResult.Throttled
            val token = EpicCredentialStore.getValidAccessToken(app) ?: return SyncResult.NotLoggedIn

            status(app.getString(R.string.stores_sync_game_list))
            val answer = EpicNative.call("library.owned", JSONObject().put("accessToken", token))
                ?: return SyncResult.Failed(app.getString(R.string.stores_sync_failed))
            if (answer.optBoolean("signIn", false)) return SyncResult.NotLoggedIn
            if (answer.has("error")) return SyncResult.Failed(answer.optString("error").ifEmpty { app.getString(R.string.stores_sync_failed) })
            val records = answer.optJSONArray("records") ?: JSONArray()
            if (records.length() == 0) {
                return if (cache.isNotEmpty()) SyncResult.Ok(cache)
                else SyncResult.Failed(app.getString(R.string.stores_sync_no_games, Store.EPIC.label))
            }

            val before = cache.associateBy { it.appName }
            val list = (0 until records.length()).mapNotNull { records.optJSONObject(it) }
            val total = list.size
            // Games already known with their name and art keep them; only new or nameless ones are asked again.
            val ask = list.filter { r -> force || (before[r.optString("appName")]?.let(::missingDetails) ?: true) }
            val answers = detailsFor(token, ask) { n -> if (n % 5 == 0 || n == ask.size) status(app.getString(R.string.stores_sync_details, n, ask.size)) }
            val byApp = HashMap<String, JSONObject?>()
            ask.forEachIndexed { i, r -> byApp[r.optString("appName")] = answers[i] }
            val games = ArrayList<EpicGame>(total)
            for (record in list) {
                val app0 = record.optString("appName")
                val details = byApp[app0]
                // Something the catalog answers for but with no title at all is not a game (Fab / engine content).
                if (details != null && details.optString("title").isEmpty() && before[app0] == null) continue
                games += gameFrom(record, details, before[app0])
            }
            val shown = visible(games)
            prefs.edit().putString(KEY_CACHE, toCacheJson(shown)).putLong(KEY_SYNCED_AT, System.currentTimeMillis()).apply()
            return SyncResult.Ok(shown)
        } catch (e: Exception) {
            return SyncResult.Failed(e.message ?: context.getString(R.string.stores_sync_failed))
        } finally {
            running.set(false)
        }
    }
}
