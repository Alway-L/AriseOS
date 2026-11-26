package com.droiddeck.launcher.runtime

import com.droiddeck.launcher.R

/** Windows PC launchers that droiddeck-pclauncher installs and opens under Proton, one prefix each. */
object PcLaunchers {
    const val PROGRAM = "/usr/local/bin/droiddeck-pclauncher"

    class Launcher(val id: String, val name: String, val iconRes: Int, val dot: Long)

    val all = listOf(
        Launcher("ea", "EA app", R.drawable.store_ea, 0xFFFF4747),
        Launcher("ubisoft", "Ubisoft Connect", R.drawable.store_ubisoft, 0xFF2E7DFF),
        Launcher("rockstar", "Rockstar", R.drawable.store_rockstar, 0xFFF5B81C),
    )

    fun app(launcher: Launcher) = UserApps.App(
        "pc:${launcher.id}", UserApps.Kind.SCRIPT, launcher.name, launcher.iconRes, null, PROGRAM, listOf(launcher.id),
    )
}
