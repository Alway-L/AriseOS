package com.droiddeck.launcher.runtime

import android.content.Context
import com.droiddeck.launcher.R
import java.io.File

/**
 * Emulators AriseOS installs from their own GitHub releases as added apps. Both ship only x86-64 builds,
 * so they run through FEX; the archive one is unpacked and started through its own program.
 */
object ExtraEmulators {
    class Extra(
        val id: String, val name: String, val system: String, val repo: String, val iconRes: Int,
        val source: UserApps.Source,
    )

    val all = listOf(
        Extra("pcsx2", "PCSX2", "PS2 · x86-64 via FEX", "PCSX2/pcsx2", R.drawable.emu_pcsx2, UserApps.Source.GitHub("PCSX2/pcsx2")),
        Extra(
            "kytyps5", "KytyPS5", "PS5 · experimental, x86-64 via FEX", "KytyPS5/KytyPS5", R.drawable.emu_kytyps5,
            UserApps.Source.GitHubArchive("KytyPS5/KytyPS5", Regex("Linux-x86_64\\.tar\\.gz$"), "kyty_launcher"),
        ),
    )

    fun installed(apps: List<UserApps.App>, extra: Extra): UserApps.App? =
        apps.firstOrNull { it.repo.equals(extra.repo, ignoreCase = true) }

    fun request(context: Context, extra: Extra): UserApps.Request {
        val icon = File(context.cacheDir, "extra-${extra.id}.png")
        runCatching { context.resources.openRawResource(extra.iconRes).use { input -> icon.outputStream().use { input.copyTo(it) } } }
        return UserApps.Request(extra.source, extra.name, icon.takeIf { it.isFile }?.path)
    }
}
