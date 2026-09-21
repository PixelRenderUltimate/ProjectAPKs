package com.pixelrender.app.shizuku

import com.pixelrender.app.game.GameTarget
import com.pixelrender.app.logging.Logger
import org.json.JSONObject

/** Parsing JSON dari user service. Dipisah supaya DisplayState tetap tipe murni. */
object DisplayStateParser {
    fun parse(json: String?): DisplayState? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            if (!o.optBoolean("ok", false)) {
                DisplayState(
                    ok = false,
                    error = listOf(o.optString("error"), o.optString("reason"))
                        .filter { it.isNotBlank() }
                        .joinToString(": ")
                )
            } else {
                DisplayState(
                    ok = true,
                    initialWidth = o.optInt("initialWidth"),
                    initialHeight = o.optInt("initialHeight"),
                    baseWidth = o.optInt("baseWidth"),
                    baseHeight = o.optInt("baseHeight"),
                    initialDensity = o.optInt("initialDensity"),
                    baseDensity = o.optInt("baseDensity"),
                    sizeOverridden = o.optBoolean("sizeOverridden"),
                    densityOverridden = o.optBoolean("densityOverridden")
                )
            }
        }.getOrElse { DisplayState(ok = false, error = "JSON tidak bisa diparse") }
    }
}

data class ProbeReport(
    val results: List<CommandResult> = emptyList(),
    val display: DisplayState? = null,
    val gameOverlay: Map<String, String> = emptyMap(),
    /** Output 'cmd game list-modes' dan 'list-configs' per package. */
    val gameModes: Map<String, String> = emptyMap()
)

/**
 * Probe read-only. Tidak ada satu pun command di sini yang menulis.
 *
 * Hasil terpenting adalah pembacaan IWindowManager. Kalau initial != base,
 * berarti sudah ada override resolusi yang aktif di perangkat sebelum aplikasi
 * ini menyentuh apa pun, dan itu harus tercatat sebelum boleh menulis.
 */
object ShizukuProbe {

    fun run(client: ShizukuUserServiceClient, games: List<GameTarget>): ProbeReport {
        Logger.i("=== Probe read-only dimulai ===")

        val results = mutableListOf<CommandResult>()

        fun exec(vararg argv: String): CommandResult {
            val r = client.exec(argv.toList())
            results += r
            if (r.ok) {
                Logger.ok("\$ ${r.command}", r.output.replace("\n", " | ").ifBlank { "(kosong)" })
            } else {
                Logger.e("\$ ${r.command}", r.error.ifBlank { "exit ${r.exitCode}" })
            }
            return r
        }

        exec("id")
        exec("wm", "size")
        exec("wm", "density")
        exec("getprop", "ro.board.platform")
        exec("getprop", "ro.hardware.vulkan")
        exec("settings", "get", "global", "enable_gpu_debug_layers")
        exec("device_config", "list", "game_overlay")

        val overlay = mutableMapOf<String, String>()
        val modes = mutableMapOf<String, String>()
        games.filter { it.installed }.forEach { game ->
            val pkg = game.packageName

            val r = exec("device_config", "get", "game_overlay", pkg)
            val value = r.output.trim()
            overlay[pkg] = when {
                !r.ok -> "gagal dibaca: ${r.error}"
                value.isBlank() || value == "null" -> "tidak ada intervention terpasang"
                else -> value
            }

            // Sintaks 'cmd game' berbeda antar versi Android. Kalau gagal,
            // outputnya tetap disimpan apa adanya, bukan ditafsirkan.
            val listModes = exec("cmd", "game", "list-modes", pkg)
            val listConfigs = exec("cmd", "game", "list-configs", pkg)
            modes[pkg] = buildString {
                append("list-modes: ")
                append(if (listModes.ok) listModes.output.ifBlank { "(kosong)" } else "gagal (${listModes.error.ifBlank { "exit ${listModes.exitCode}" }})")
                append(" | list-configs: ")
                append(if (listConfigs.ok) listConfigs.output.ifBlank { "(kosong)" } else "gagal (${listConfigs.error.ifBlank { "exit ${listConfigs.exitCode}" }})")
            }
        }

        val display = client.readDisplayState(0)
        if (display != null && display.ok) {
            Logger.ok(
                "IWindowManager terbaca",
                "panel ${display.physical} @${display.initialDensity}dpi, " +
                        "aktif ${display.current} @${display.baseDensity}dpi"
            )
            if (display.anyOverride) {
                Logger.w(
                    "Perangkat SUDAH punya override resolusi/density aktif",
                    "Nilai aktif ini yang akan dipakai sebagai target restore."
                )
            }
        } else {
            Logger.e(
                "IWindowManager tidak terbaca",
                display?.error ?: "user service tidak mengembalikan respons"
            )
        }

        val failed = results.count { !it.ok }
        Logger.i("=== Probe selesai, ${results.size - failed}/${results.size} command sukses ===")
        return ProbeReport(results, display, overlay, modes)
    }
}
