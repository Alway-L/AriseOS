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
        Extra(
            "eglantine", "Eglantine", "Switch 2 · ARM64", "Djihads80/Eglantine", R.drawable.emu_eglantine,
            UserApps.Source.GitHubAppImage(
                "Djihads80/Eglantine", Regex("^eglantine-linux-aarch64-pgo\\.AppImage$"), Regex("^(v|daily-)"), previews = true,
                preferred = listOf(Regex("^eglantine-linux-aarch64\\.AppImage$")),
            ),
        ),
        Extra(
            "lichii", "Lichii", "Switch / Switch 2 · ARM64", "zortos293/Lichii", R.drawable.emu_lichii,
            UserApps.Source.GitHubAppImage(
                "zortos293/Lichii", Regex("^lichii-linux-aarch64-pgo\\.AppImage$"), Regex("^build-"), previews = true,
                preferred = listOf(Regex("^lichii-linux-aarch64\\.AppImage$")),
            ),
        ),
        // Orchid (Eglantine fork, very experimental): its CI also builds linux-aarch64, which runs natively (no FEX,
        // no double translation); the x86-64 build through FEX is only the fallback when a release lacks it.
        Extra(
            "orchid", "Orchid", "Switch 2 · experimental", "MythosOfs/Orchid", R.drawable.emu_orchid,
            UserApps.Source.GitHubAppImage(
                "MythosOfs/Orchid", Regex("^orchid-.*-linux-x86_64-pgo\\.AppImage$"), Regex("^v"),
                // The plain build first: the -pgo one is tuned with a profile recorded on x86-64, and its ARM64 build
                // failed to unpack the game's code (NSO decompression) where the x86-64 build loaded it.
                preferred = listOf(Regex("^orchid-.*-linux-aarch64\\.AppImage$"), Regex("^orchid-.*-linux-aarch64-pgo\\.AppImage$")),
            ),
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
