package com.droiddeck.launcher.runtime

import com.droiddeck.launcher.R

/** Windows PC launchers that droiddeck-pclauncher installs and opens under Proton, one prefix each. */
object PcLaunchers {
    const val PROGRAM = "/usr/local/bin/droiddeck-pclauncher"

    class Launcher(val id: String, val name: String, val iconRes: Int)

    val all = listOf(
        Launcher("ea", "EA app", R.drawable.store_ea),
        Launcher("ubisoft", "Ubisoft Connect", R.drawable.store_ubisoft),
        Launcher("rockstar", "Rockstar Games", R.drawable.store_rockstar),
    )

    fun app(launcher: Launcher) = UserApps.App(
        "pc:${launcher.id}", UserApps.Kind.SCRIPT, launcher.name, launcher.iconRes, null, PROGRAM, listOf(launcher.id),
    )
}
