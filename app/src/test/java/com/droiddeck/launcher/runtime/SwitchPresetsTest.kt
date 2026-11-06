package com.droiddeck.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchPresetsTest {
    @Test fun anEmptyConfigGetsTheRendererSection() {
        val out = SwitchPresets.merge("", "Renderer", listOf("backend" to "1"))
        assertEquals("[Renderer]\nbackend\\default=false\nbackend=1\n", out)
    }

    @Test fun existingKeysAreReplacedAndOtherSectionsKept() {
        val ini = "[Core]\nuse_multi_core=true\n\n[Renderer]\nbackend\\default=true\nbackend=0\nfoo=bar\n[UI]\nx=1\n"
        val out = SwitchPresets.merge(ini, "Renderer", listOf("backend" to "1", "use_vsync" to "3"))
        assertTrue(out.contains("[Core]\nuse_multi_core=true\n"))
        assertTrue(out.contains("[Renderer]\nbackend\\default=false\nbackend=1\nfoo=bar\nuse_vsync\\default=false\nuse_vsync=3\n[UI]\nx=1\n"))
    }
}
