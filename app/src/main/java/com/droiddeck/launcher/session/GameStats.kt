package com.droiddeck.launcher.session

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject
import java.time.LocalDate

/** Play time, frame rate and temperature per game, sampled while a session shows a game. */
object GameStats {
    private const val PREFS = "game_stats"
    const val TICK_SECONDS = 5

    class Game(val key: String, val totalSeconds: Long, val sessions: Int, val lastPlayed: Long, val lastAvgFps: Float?, val lastMaxTemp: Float?)

    /** Game frames the compositor presented, counted by the HUD host while it runs. */
    val frames = java.util.concurrent.atomic.AtomicLong()
    private var lastFrames = -1L
    private var lastTickAt = 0L
    private var currentKey: String? = null
    private var fpsSum = 0.0
    private var fpsCount = 0
    private var maxTemp: Float? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun batteryTemp(context: Context): Float? {
        val t = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        return if (t == Int.MIN_VALUE) null else t / 10f
    }

    /** One sample: [game] is the focused game's key (a Steam game id or "program:…"), or null when none is shown. */
    @Synchronized
    fun tick(context: Context, game: String?, hudFps: Float?) {
        val now = System.nanoTime()
        val count = frames.get()
        val counted = if (lastFrames >= 0 && count > lastFrames && lastTickAt > 0)
            ((count - lastFrames) * 1_000_000_000.0 / (now - lastTickAt)).toFloat() else null
        lastFrames = count; lastTickAt = now
        val fps = counted ?: hudFps
        if (game == null) { currentKey = null; return }
        val p = prefs(context)
        val obj = p.getString("g:$game", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        if (game != currentKey) {
            currentKey = game
            fpsSum = 0.0; fpsCount = 0; maxTemp = null
            obj.put("sessions", obj.optInt("sessions") + 1)
        }
        if (fps != null && fps > 1f) { fpsSum += fps; fpsCount++ }
        batteryTemp(context)?.let { t -> maxTemp = maxOf(maxTemp ?: t, t) }
        obj.put("seconds", obj.optLong("seconds") + TICK_SECONDS)
        obj.put("last", System.currentTimeMillis())
        if (fpsCount > 0) obj.put("fps", fpsSum / fpsCount)
        maxTemp?.let { obj.put("temp", it.toDouble()) }
        val day = "d:${LocalDate.now()}"
        p.edit()
            .putString("g:$game", obj.toString())
            .putLong(day, p.getLong(day, 0L) + TICK_SECONDS)
            .putString("recent", game)
            .apply()
    }

    fun game(context: Context, key: String): Game? =
        prefs(context).getString("g:$key", null)?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { o ->
            Game(
                key, o.optLong("seconds"), o.optInt("sessions"), o.optLong("last"),
                o.optDouble("fps").takeIf { !it.isNaN() }?.toFloat(), o.optDouble("temp").takeIf { !it.isNaN() }?.toFloat(),
            )
        }

    fun recent(context: Context): Game? = prefs(context).getString("recent", null)?.let { game(context, it) }

    fun todaySeconds(context: Context): Long = prefs(context).getLong("d:${LocalDate.now()}", 0L)

    fun yesterdaySeconds(context: Context): Long = prefs(context).getLong("d:${LocalDate.now().minusDays(1)}", 0L)

    fun format(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${"%02d".format(m)}" else "${m} min"
    }
}
