package com.droiddeck.launcher.input

import android.content.Context
import android.content.SharedPreferences

/** On-screen button names per controller family; positions and signals stay those of an Xbox pad. */
object ButtonStyle {
    const val PREFS = "steam_touch"
    const val KEY = "buttonStyle"

    fun current(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, SteamTouchControls.STYLE_DECK)
            .coerceIn(0, SteamTouchControls.STYLES.size - 1)

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Keys are the Xbox-position names the overlays use.
    private val NAMES = listOf(
        mapOf("a" to "A", "b" to "B", "x" to "X", "y" to "Y", "lb" to "L1", "rb" to "R1", "lt" to "L2", "rt" to "R2",
            "select" to "⧉", "start" to "☰", "ls" to "L3", "rs" to "R3"),
        mapOf("a" to "A", "b" to "B", "x" to "X", "y" to "Y", "lb" to "LB", "rb" to "RB", "lt" to "LT", "rt" to "RT",
            "select" to "⧉", "start" to "☰", "ls" to "LS", "rs" to "RS"),
        mapOf("a" to "B", "b" to "A", "x" to "Y", "y" to "X", "lb" to "L", "rb" to "R", "lt" to "ZL", "rt" to "ZR",
            "select" to "−", "start" to "+", "ls" to "LS", "rs" to "RS"),
        mapOf("a" to "✕", "b" to "○", "x" to "□", "y" to "△", "lb" to "L1", "rb" to "R1", "lt" to "L2", "rt" to "R2",
            "select" to "⧉", "start" to "☰", "ls" to "L3", "rs" to "R3"),
    )

    fun name(style: Int, key: String): String? = NAMES.getOrNull(style)?.get(key)
}
