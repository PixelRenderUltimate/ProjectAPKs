package com.pixelrender.app.shizuku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPolicyTest {

    @Test
    fun readCommandsAreAllowed() {
        listOf(
            listOf("id"),
            listOf("wm", "size"),
            listOf("wm", "density"),
            listOf("settings", "get", "global", "enable_gpu_debug_layers"),
            listOf("settings", "get", "secure", "display_density_forced"),
            listOf("device_config", "get", "game_overlay", "com.dts.freefireth"),
            listOf("device_config", "list", "game_overlay"),
            listOf("cmd", "game", "list-modes", "com.dts.freefireth"),
            listOf("cmd", "game", "list-configs", "com.dts.freefiremax"),
            listOf("getprop", "ro.board.platform"),
            listOf("getprop", "ro.hardware.vulkan")
        ).forEach { cmd ->
            val verdict = CommandPolicy.validate(cmd)
            assertTrue("harus lolos: $cmd -> ${verdict.reason}", verdict.allowed)
        }
    }

    @Test
    fun writeFormsAndInjectionAreRejected() {
        listOf(
            emptyList(),
            listOf("wm", "size", "540x1200"),
            listOf("wm", "size", "reset"),
            listOf("wm", "density", "200"),
            listOf("settings", "put", "global", "x", "1"),
            listOf("settings", "get", "global", "a;reboot"),
            listOf("settings", "get", "global", "a", "b"),
            listOf("settings", "get", "system2", "key"),
            listOf("settings", "get", "global", ""),
            listOf("getprop", "persist.sys.foo"),
            listOf("getprop", "ro.hardware.vulkan\n"),
            listOf("sh", "-c", "id"),
            listOf("su"),
            listOf("id", "; reboot"),
            listOf("id", "&&", "reboot"),
            listOf("cmd", "game", "mode", "performance", "com.dts.freefireth"),
            listOf("cmd", "game", "set", "--downscale", "0.5", "com.dts.freefireth"),
            listOf("device_config", "put", "game_overlay", "com.dts.freefireth", "mode=2"),
            listOf("device_config", "get", "game_overlay", "com.dts.freefireth;reboot"),
            listOf("device_config", "get", "game_overlay", "\$(reboot)"),
            listOf("device_config", "get", "other_ns", "com.dts.freefireth"),
            listOf("cmd", "game", "list-modes", "not a package"),
            listOf("dumpsys", "window"),
            listOf("WM", "size")
        ).forEach { cmd ->
            assertFalse("harus DITOLAK: $cmd", CommandPolicy.validate(cmd).allowed)
        }
    }

    @Test
    fun allowlistContainsNoWriteVerbs() {
        val writeWords = setOf("put", "set", "reset", "delete", "mode", "clear")
        CommandPolicy.allowlist().forEach { (cmd, _) ->
            assertTrue(
                "allowlist memuat kata tulis: $cmd",
                cmd.split(" ").none { it in writeWords }
            )
        }
    }
}
