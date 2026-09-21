package com.pixelrender.app.diagnostics

import com.pixelrender.app.BuildConfig
import com.pixelrender.app.UiState
import com.pixelrender.app.logging.Logger
import com.pixelrender.app.testing.TestPlan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Laporan teks untuk satu sel matriks pengujian. Dirancang supaya bisa
 * ditempel apa adanya ke chat atau issue tracker.
 *
 * Privasi: hanya data perangkat dan hasil aplikasi ini. Tidak ada IMEI, nomor
 * seri, akun, lokasi, atau daftar aplikasi lain selain dua target game.
 */
object DiagnosticReport {

    private const val MAX_LOG_LINES = 150
    private const val MAX_OUTPUT_CHARS = 400

    fun build(s: UiState, logs: List<Logger.Entry>, now: Long = System.currentTimeMillis()): String =
        buildString {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val evidence = s.testEvidence
            val results = s.testResults
            val summary = TestPlan.summary(results)

            line("PixelRender - laporan diagnostik")
            line("Versi: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                    "build ${BuildConfig.BUILD_TYPE}, probe Vulkan native: ${yes(BuildConfig.NATIVE_VULKAN_PROBE)}")
            line("Waktu: ${time.format(Date(now))}")
            line("Privasi: tanpa IMEI, nomor seri, akun, lokasi, atau daftar aplikasi.")
            line("Sel matriks: ${TestPlan.cell(evidence)}")
            line("Ringkasan uji: " + summary.entries.joinToString(", ") { "${it.key.label} ${it.value}" })

            section("Perangkat")
            s.device?.let { d ->
                kv("Perangkat", "${d.manufacturer} ${d.model} (${d.device}), brand ${d.brand}")
                kv("Board / hardware", "${d.board} / ${d.hardware}")
                kv("SoC", "${d.socManufacturer} ${d.socModel}")
                kv("Android", "${d.androidRelease} (API ${d.sdkInt}), patch ${d.securityPatch}, build ${d.buildType}")
                kv("Fingerprint", d.fingerprint)
                kv("ABI", d.abis.joinToString(", "))
                kv("RAM", d.ramGb)
                kv("Display aktif", "${d.resolution} @${d.densityDpi}dpi, ${d.refreshRate} Hz")
                kv("Mode refresh", d.supportedRefreshRates.joinToString("/") { "${it.toInt()}Hz" })
            } ?: line("(belum terdeteksi)")

            section("GPU")
            val gpu = s.gpuIdentity
            kv("Keluarga", "${gpu.family.label}${if (gpu.viaAngle) ", GLES lewat ANGLE" else ""}")
            kv("Dasar klasifikasi", gpu.evidence)
            s.gl?.let { gl ->
                if (!gl.available) kv("OpenGL ES", "gagal: ${gl.error}")
                else {
                    kv("GL vendor / renderer", "${gl.vendor} / ${gl.renderer}")
                    kv("GL version", gl.versionString)
                    kv("GLSL", gl.glslVersion)
                    kv("GLES", gl.esVersion)
                    kv("Max texture / samples", "${gl.maxTextureSize} / ${gl.maxSamples}")
                    kv("Anisotropic", if (gl.anisotropySupported) "ya, ${gl.maxAnisotropy.toInt()}x" else "tidak")
                    kv("LOD bias ext", yes(gl.lodBiasSupported))
                }
            }
            s.vulkan?.let { vk ->
                if (!vk.available) kv("Vulkan", "tidak tersedia: ${vk.reason}")
                else {
                    kv("Vulkan loader / system feature / level",
                        "${vk.loaderApiVersion} / ${vk.systemFeatureVersion} / ${vk.hardwareLevel}")
                    kv("Probe native", yes(vk.nativeProbeUsed) + (vk.reason?.let { " ($it)" } ?: ""))
                    vk.primaryDevice?.let { d ->
                        kv("Device", "${d.name}, API ${d.apiVersion}, ${d.type}")
                        kv("Vendor ID", "0x${Integer.toHexString(d.vendorId).uppercase()} (${d.vendorName}), " +
                                "deviceID 0x${Integer.toHexString(d.deviceId).uppercase()}")
                        kv("Driver (raw)", d.driverVersionRaw.toString())
                        kv("Sampler anisotropy", "${d.samplerAnisotropy}, max ${d.maxSamplerAnisotropy}x")
                        kv("MSAA", d.colorSampleCounts.joinToString("/") { "${it}x" })
                    }
                }
            }

            section("Display (IWindowManager)")
            val display = s.probe?.display
            if (display == null) line("(belum dibaca; butuh user service Shizuku)")
            else if (!display.ok) line("gagal: ${display.error}")
            else {
                kv("Panel", "${display.physical} @${display.initialDensity}dpi")
                kv("Aktif", "${display.current} @${display.baseDensity}dpi")
                kv("Override", "ukuran ${yes(display.sizeOverridden)}, density ${yes(display.densityOverridden)}")
            }

            section("Shizuku")
            kv("Status", "${s.shizuku.status}, versi ${s.shizuku.version}, uid ${s.shizuku.uid}" +
                    if (s.shizuku.suiDetected) ", Sui" else "")
            kv("User service", "${s.userService.status}, uid ${s.userService.serviceUid}, " +
                    "policy ${s.userService.policyVersion}")

            section("Backend")
            s.backends.forEach { b ->
                line("- ${b.name} (${b.backend.role.label}): " +
                        (if (b.available) "tersedia" else "tidak tersedia") + " - ${b.availability.reason}")
            }
            kv("Aktif", s.activeBackend?.displayName ?: "-")

            section("Capability matrix")
            s.capabilities.forEach { c ->
                line("- ${c.name}: device=${c.deviceSupport}, control=${c.externalControl}" +
                        (c.controlledBy?.let { " via ${it.displayName}" } ?: ""))
                c.verdicts.forEach { v ->
                    line("    ${v.backend.displayName}: ${v.deviceSupport}/${v.externalControl} - ${v.evidence}")
                }
            }

            section("Probe")
            val probe = s.probe
            if (probe == null || probe.results.isEmpty()) line("(belum dijalankan)")
            else probe.results.forEach { r ->
                val out = (if (r.ok) r.output else r.error).replace("\n", " | ")
                line("\$ ${r.command} -> ${if (r.ok) "ok" else "gagal (exit ${r.exitCode})"}: ${out.take(MAX_OUTPUT_CHARS)}")
            }
            probe?.gameOverlay?.forEach { (pkg, v) -> kv("game_overlay $pkg", v) }
            probe?.gameModes?.forEach { (pkg, v) -> kv("cmd game $pkg", v.replace("\n", " | ").take(MAX_OUTPUT_CHARS * 2)) }

            section("Profil dan pemulihan")
            kv("Game", s.selectedGame ?: "-")
            s.games.forEach { g -> kv(g.label, if (g.installed) "terpasang ${g.versionName ?: ""}" else "tidak terpasang") }
            kv("Profil dipilih", s.profile.id.label)
            kv("Profil aktif", s.activeProfile?.let { "${it.id.label} ${it.target}" } ?: "Default")
            kv("Status profil", s.profileState.label)
            kv("Journal / pemulihan", "${s.journalState} / ${s.recovery}")
            kv("Target restore", s.pendingDisplay?.describe() ?: "-")

            section("Pengujian")
            results.forEach { r ->
                line("[${r.verdict.label}] ${r.case.id} - ${r.case.title}: ${r.evidence}")
            }

            section("Riwayat verifikasi")
            if (s.history.isEmpty()) line("(kosong)")
            s.history.forEach { h ->
                line("${time.format(Date(h.at))} ${h.kind.label} ${h.label}: " +
                        "diharapkan ${h.expected}, terbaca ${h.actual}, " +
                        (if (h.verified) "TERVERIFIKASI" else "TIDAK TERVERIFIKASI") +
                        if (h.note.isNotBlank()) " - ${h.note}" else "")
            }

            section("Ekstensi (untuk membandingkan GPU)")
            kv("GL", "${s.gl?.extensions?.size ?: 0}")
            s.gl?.extensions?.let { line(it.joinToString(" ")) }
            val vkExt = s.vulkan?.primaryDevice?.deviceExtensions.orEmpty()
            kv("Vulkan device", "${vkExt.size}")
            if (vkExt.isNotEmpty()) line(vkExt.joinToString(" "))

            section("Log (${minOf(logs.size, MAX_LOG_LINES)} baris terakhir)")
            logs.takeLast(MAX_LOG_LINES).forEach { e ->
                line("[${e.time}] ${e.level}: ${e.message}" + (e.detail?.let { " -> $it" } ?: ""))
            }
        }

    private fun StringBuilder.line(text: String) { append(text).append('\n') }
    private fun StringBuilder.kv(key: String, value: String) = line("$key: $value")
    private fun StringBuilder.section(title: String) { append('\n').append("== ").append(title).append(" ==\n") }
    private fun yes(b: Boolean) = if (b) "ya" else "tidak"
}
