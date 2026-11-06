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
        // Switch 2: only AriseOS's own Orchid, which takes the useful parts of the other forks (Lichii, Frizti's
        // Lichii, Eglantine) as patches (tools/emulators/patches/orchid). Those builds stay in the repo as sources.
        // Orchid, built by AriseOS from its source (.github/workflows/build-orchid.yml) with AriseOS patches: the
        // upstream ARM64 binary of v0.8.1 cannot decompress Switch 2 game code, and the 256 GiB address block falls
        // back to a kernel-chosen place when the fixed hints are taken.
        Extra(
            "orchid", "Orchid", "Switch 2 · build AriseOS · ARM64", "Alway-L/AriseOS", R.drawable.emu_orchid,
            UserApps.Source.GitHubAppImage(
                "Alway-L/AriseOS", Regex("^orchid-arise-linux-aarch64\\.AppImage$"), Regex("^orchid-arise-"), previews = true,
            ),
        ),
    )

    /** Several emulators AriseOS builds come from its own repo; those are told apart by name too. */
    fun installed(apps: List<UserApps.App>, extra: Extra): UserApps.App? {
        // AriseOS's repo also held Lichii and Lichii Frizti, still installed for some: match those by name too.
        val shared = extra.repo.equals("Alway-L/AriseOS", ignoreCase = true) || all.count { it.repo.equals(extra.repo, ignoreCase = true) } > 1
        return apps.firstOrNull { it.repo.equals(extra.repo, ignoreCase = true) && (!shared || it.name.equals(extra.name, ignoreCase = true)) }
    }

    fun request(context: Context, extra: Extra): UserApps.Request {
        val icon = File(context.cacheDir, "extra-${extra.id}.png")
        runCatching { context.resources.openRawResource(extra.iconRes).use { input -> icon.outputStream().use { input.copyTo(it) } } }
        return UserApps.Request(extra.source, extra.name, icon.takeIf { it.isFile }?.path)
    }
}
