package com.droiddeck.launcher.runtime

import android.util.Log
import java.io.File

/**
 * AriseOS: the recommended settings for the Switch 2 emulators (Eglantine, Lichii, Orchid), written once into
 * each one's qt-config.ini so they start tuned for the phone. All three come from Eden and share its settings
 * and enum order (src/common/settings.h, settings_enums.h). Eglantine keeps its config in ~/.config/eglantine;
 * Lichii and Orchid both use ~/.config/eden.
 *
 * Eden reads a key only when "key\default" is false, so every value is written with that flag.
 * After the first time a marker file keeps the user's own changes from being overwritten.
 */
object SwitchPresets {
    private const val TAG = "SwitchPresets"
    // v2 also turns off the startup Vulkan check and puts Vulkan back: the check re-runs the program in a child
    // process, which under FEX in proot cannot start (execveat), so it always reported "broken Vulkan" and fell back
    // to OpenGL even though the Vulkan thunk works.
    // v3: once more, for configs the false "broken Vulkan" fallback had switched to OpenGL before the fix.
    // v4: Dynarmic instead of NCE. In the Linux runtime the 12 GiB host memory a Switch 2 game needs cannot be
    // mapped (mmap ENOMEM), and NCE then still runs without fastmem and the game hangs right after it starts.
    // v5: NCE again. AriseOS's own builds now get the 256 GiB fastmem block (arise_patches.py), which NCE needs;
    // without it NCE hung, so v4 had moved to Dynarmic (much slower: the game code is recompiled).
    private const val MARKER = ".ariseos-preset-5"

    /** (key, value) in the [Renderer] section. */
    internal val RENDERER = listOf(
        "backend" to "1",                    // Vulkan
        "resolution_setup" to "3",           // 1x
        "gpu_accuracy" to "0",               // Fast
        "use_asynchronous_shaders" to "true",
        "astc_recompression" to "2",         // BC3
        "vram_usage_mode" to "0",            // Conservative
        "use_reactive_flushing" to "true",
        "use_vsync" to "3",                  // FIFO Relaxed
        "scaling_filter" to "5",             // ScaleForce
        "anti_aliasing" to "1",              // FXAA
    )

    /** (key, value) in the [Cpu] section: NCE (1), the game's ARM code straight on the phone's CPU. */
    internal val CPU = listOf("cpu_backend" to "1")

    /** (key, value) in the [Debugging] section. */
    internal val DEBUGGING = listOf("perform_vulkan_check" to "false")

    /** AppImage id under /opt/appimages/user → config folder name. */
    // Orchid is AriseOS's own build and carries these settings as its defaults (tools/emulators/patches/orchid),
    // so a reinstall starts tuned and nothing here overrides what the user changed. The Lichii builds share its
    // ~/.config/eden and are no longer offered: only Eglantine, with its own folder, still gets the preset.
    private val EMULATORS = mapOf("eglantine" to "eglantine")

    fun apply(root: File) {
        for ((id, dir) in EMULATORS) {
            if (!File(root, "opt/appimages/user/$id").isDirectory) continue
            val config = File(root, "root/.config/$dir")
            val marker = File(config, MARKER)
            if (marker.exists()) continue
            try {
                config.mkdirs()
                val ini = File(config, "qt-config.ini")
                val text = merge(if (ini.isFile) ini.readText() else "", "Renderer", RENDERER)
                ini.writeText(merge(merge(text, "Debugging", DEBUGGING), "Cpu", CPU))
                marker.writeText("$id\n")
                Log.i(TAG, "recommended settings written for $id in ~/.config/$dir")
            } catch (e: Exception) {
                Log.w(TAG, "could not write the recommended settings for $id", e)
            }
        }
    }

    /** Sets each key (with "key\default=false") in [section] of an INI text, keeping everything else. */
    internal fun merge(text: String, section: String, values: List<Pair<String, String>>): String {
        val lines = text.lines().dropLastWhile { it.isEmpty() }.toMutableList()
        var start = lines.indexOfFirst { it.trim() == "[$section]" }
        if (start < 0) {
            if (lines.isNotEmpty()) lines += ""
            lines += "[$section]"
            start = lines.size - 1
        }
        var end = (start + 1 until lines.size).firstOrNull { lines[it].trim().startsWith("[") } ?: lines.size
        val wanted = LinkedHashMap<String, String>()
        for ((k, v) in values) { wanted["$k\\default"] = "false"; wanted[k] = v }
        for (i in start + 1 until end) {
            val key = lines[i].substringBefore('=', "").trim()
            wanted.remove(key)?.let { lines[i] = "$key=$it" }
        }
        val missing = wanted.map { (k, v) -> "$k=$v" }
        lines.addAll(end, missing)
        end += missing.size
        return lines.joinToString("\n") + "\n"
    }
}
