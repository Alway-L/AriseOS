package com.droiddeck.launcher.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.droiddeck.launcher.R

/** Samsung's Gaming Hub / Game Booster, where the performance profile is chosen; apps cannot set it. */
object GameBooster {
    private val PACKAGES = listOf(
        "com.samsung.android.game.gamehome",
        "com.samsung.android.game.gametools",
    )

    fun available(context: Context): Boolean =
        PACKAGES.any { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun open(context: Context) {
        val candidates = PACKAGES.mapNotNull { context.packageManager.getLaunchIntentForPackage(it) } +
            Intent("com.samsung.settings.ADVANCED_FEATURES") +
            Intent(Settings.ACTION_SETTINGS)
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Toast.makeText(context, R.string.game_booster_toast, Toast.LENGTH_LONG).show()
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
