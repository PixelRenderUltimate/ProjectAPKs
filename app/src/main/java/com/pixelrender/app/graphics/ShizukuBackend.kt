package com.pixelrender.app.graphics

import android.os.Build
import com.pixelrender.app.shizuku.DisplayState

/**
 * Satu-satunya backend yang benar-benar bisa mengubah sesuatu untuk aplikasi
 * lain, dan cakupannya jauh lebih sempit daripada yang biasanya dijanjikan
 * aplikasi sejenis.
 *
 * Yang dipegangnya hanya yang dimediasi system_server dan boleh dipanggil UID
 * shell: ukuran dan density display, refresh rate, dan Game Mode. Sisanya
 * dilaporkan UNSUPPORTED dengan alasan, bukan didiamkan.
 */
object ShizukuBackend : GraphicsBackend {

    override val id = BackendId.SHIZUKU
    override val role = BackendRole.PRIVILEGED
    override val priority = 100

    override fun availability(ctx: BackendContext): BackendAvailability {
        val shizuku = ctx.shizuku
        val service = ctx.userService
        return when {
            !shizuku.running ->
                BackendAvailability(false, "Service Shizuku tidak berjalan")
            !shizuku.connected ->
                BackendAvailability(false, "Permission Shizuku belum diberikan")
            !service.bound ->
                BackendAvailability(false, "User service belum di-bind")
            !service.runningAsShell ->
                BackendAvailability(
                    false,
                    "User service berjalan sebagai UID ${service.serviceUid}, bukan shell"
                )
            else ->
                BackendAvailability(true, "User service aktif sebagai shell (uid 2000)")
        }
    }

    override fun capabilities(ctx: BackendContext): List<BackendCapability> {
        val available = availability(ctx).available
        val display = ctx.probe?.display
        val displayVerified = available && display != null && display.ok

        val caps = mutableListOf<BackendCapability>()

        // Satu-satunya jalur yang benar-benar menurunkan jumlah piksel game.
        caps += BackendCapability(
            parameter = GraphicsParameter.DISPLAY_RESOLUTION,
            deviceSupport = Support.SUPPORTED,
            externalControl = when {
                displayVerified -> Support.SUPPORTED
                available -> Support.UNKNOWN
                else -> Support.UNSUPPORTED
            },
            evidence = when {
                displayVerified -> "IWindowManager terbaca: panel ${display!!.physical}, " +
                        "aktif ${display.current}" +
                        if (display.sizeOverridden) " (sudah ada override)" else ""
                available -> "User service aktif, tetapi IWindowManager belum diprobe"
                else -> "Shizuku belum siap"
            },
            reason = "setForcedDisplaySize() dan clearForcedDisplaySize() pada IWindowManager " +
                    "memerlukan WRITE_SECURE_SETTINGS, yang dimiliki UID shell. Perubahannya " +
                    "berlaku untuk seluruh sistem, bukan per aplikasi, dan sepenuhnya " +
                    "reversible.",
            requires = "Shizuku dengan user service sebagai shell"
        )

        caps += BackendCapability(
            parameter = GraphicsParameter.DISPLAY_DENSITY,
            deviceSupport = Support.SUPPORTED,
            externalControl = when {
                displayVerified -> Support.SUPPORTED
                available -> Support.UNKNOWN
                else -> Support.UNSUPPORTED
            },
            evidence = if (displayVerified)
                "Density panel ${display!!.initialDensity}dpi, aktif ${display.baseDensity}dpi"
            else "Belum diprobe",
            reason = "setForcedDisplayDensityForUser() memakai permission yang sama. " +
                    "Density perlu diturunkan bersama resolusi supaya UI tidak jadi raksasa.",
            requires = "Shizuku dengan user service sebagai shell"
        )

        // Ini yang paling sering diklaim berlebihan oleh aplikasi lain.
        val overlayReadings = ctx.probe?.gameOverlay.orEmpty()
        val overlayReadable = overlayReadings.values.none { it.startsWith("gagal dibaca") } &&
                overlayReadings.isNotEmpty()
        caps += BackendCapability(
            parameter = GraphicsParameter.RENDER_SCALE_PER_GAME,
            deviceSupport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Support.UNKNOWN else Support.UNSUPPORTED,
            externalControl = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> Support.UNSUPPORTED
                available && overlayReadable -> Support.UNKNOWN
                available -> Support.UNKNOWN
                else -> Support.UNSUPPORTED
            },
            evidence = buildString {
                append("API ${Build.VERSION.SDK_INT}, butuh 33+")
                if (overlayReadings.isNotEmpty()) {
                    append("; game_overlay: ")
                    append(overlayReadings.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
                ctx.probe?.gameModes.orEmpty().forEach { (pkg, out) ->
                    append("; ").append(pkg).append(" -> ").append(out.replace("\n", " "))
                }
            },
            reason = "Android 13+ punya Game Mode intervention dengan downscaleFactor, dan " +
                    "namespace game_overlay bisa ditulis UID shell. Tetapi apakah " +
                    "SurfaceFlinger benar-benar menerapkan downscale tergantung OEM. " +
                    "Namespace yang bisa dibaca belum berarti downscale berfungsi, dan " +
                    "sintaks 'cmd game' berbeda antar versi Android. Karena itu jalur " +
                    "tulisnya belum diaktifkan: statusnya tetap UNKNOWN sampai output " +
                    "list-modes/list-configs dari perangkat nyata diperiksa.",
            requires = "Android 13+, Shizuku, dan dukungan OEM"
        )

        caps += BackendCapability(
            parameter = GraphicsParameter.GAME_MODE,
            deviceSupport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Support.SUPPORTED
            else Support.UNSUPPORTED,
            externalControl = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> Support.UNSUPPORTED
                available -> Support.PARTIALLY_SUPPORTED
                else -> Support.UNSUPPORTED
            },
            evidence = "API ${Build.VERSION.SDK_INT}, butuh 31+",
            reason = "UID shell boleh memanggil 'cmd game mode'. Tetapi ini hanya hint " +
                    "performa ke game dan scheduler; tidak membuat grafis jadi pixelated " +
                    "sama sekali.",
            requires = "Android 12+, Shizuku"
        )

        caps += BackendCapability(
            parameter = GraphicsParameter.REFRESH_RATE,
            deviceSupport = if (ctx.device.supportedRefreshRates.size > 1) Support.SUPPORTED
            else Support.UNSUPPORTED,
            externalControl = if (available) Support.PARTIALLY_SUPPORTED else Support.UNSUPPORTED,
            evidence = "Mode: " +
                    ctx.device.supportedRefreshRates.joinToString("/") { "${it.toInt()}Hz" },
            reason = "Settings peak dan minimum refresh rate bisa ditulis dengan " +
                    "WRITE_SECURE_SETTINGS, tetapi nama key-nya berbeda antar OEM sehingga " +
                    "harus diverifikasi per perangkat.",
            requires = "Shizuku"
        )

        // Batas tegas: Shizuku tidak menembus batas proses game.
        listOf(
            GraphicsParameter.TEXTURE_FILTERING,
            GraphicsParameter.ANISOTROPIC_FILTERING,
            GraphicsParameter.MIPMAP_LOD,
            GraphicsParameter.MSAA,
            GraphicsParameter.POST_PROCESSING
        ).forEach { parameter ->
            caps += BackendCapability(
                parameter = parameter,
                deviceSupport = Support.UNKNOWN,
                externalControl = Support.UNSUPPORTED,
                evidence = "Tidak ada binder shell yang menyentuh parameter ini",
                reason = "Shizuku memberi UID shell, bukan akses ke memori proses lain. " +
                        "$PER_PROCESS_REASON"
            )
        }

        return caps
    }

    // ------------------------------------------------------------ PHASE 4

    private val WRITABLE = setOf(
        GraphicsParameter.DISPLAY_RESOLUTION,
        GraphicsParameter.DISPLAY_DENSITY
    )

    override fun apply(ctx: BackendContext, changes: List<ParameterChange>): BackendResult {
        val availability = availability(ctx)
        if (!availability.available) return BackendResult.Rejected(availability.reason)
        val channel = ctx.privileged
            ?: return BackendResult.Rejected("User service belum terhubung")

        val unsupported = changes.filter { it.parameter !in WRITABLE }
        if (unsupported.isNotEmpty()) {
            return BackendResult.Rejected(
                "Tidak ada jalur API untuk: " + unsupported.joinToString { it.parameter.label }
            )
        }

        val size = changes.firstOrNull { it.parameter == GraphicsParameter.DISPLAY_RESOLUTION }
            ?.value?.let { parseSize(it) }
            ?: return BackendResult.Rejected("Ukuran target tidak ada atau formatnya salah")
        val density = changes.firstOrNull { it.parameter == GraphicsParameter.DISPLAY_DENSITY }
            ?.value?.toIntOrNull()
            ?: return BackendResult.Rejected("Density target tidak ada atau bukan angka")

        val sizeResult = channel.setDisplaySize(0, size.first, size.second, restoring = false)
        if (!sizeResult.ok) return BackendResult.Rejected("Ukuran ditolak: ${sizeResult.error}")

        val densityResult = channel.setDisplayDensity(0, density, restoring = false)
        if (!densityResult.ok) return BackendResult.Rejected("Density ditolak: ${densityResult.error}")

        // Verifikasi dari pembacaan ulang, bukan dari "tidak ada exception".
        val after = readBack(channel, densityResult.after) {
            it.baseWidth == size.first && it.baseHeight == size.second && it.baseDensity == density
        }
        val verified = after != null && after.ok &&
                after.baseWidth == size.first &&
                after.baseHeight == size.second &&
                after.baseDensity == density

        return if (verified) {
            BackendResult.Applied(
                changes,
                "Terbaca ulang: aktif ${after!!.current} @${after.baseDensity}dpi"
            )
        } else {
            BackendResult.Rejected(
                "Nilai terbaca ulang tidak sama dengan yang ditulis: " +
                        (after?.let { "${it.current} @${it.baseDensity}dpi" } ?: "tidak terbaca") +
                        ". Kemungkinan OEM membatasi override resolusi."
            )
        }
    }

    override fun restore(ctx: BackendContext, original: List<ParameterChange>): BackendResult {
        val channel = ctx.privileged
            ?: return BackendResult.Rejected("User service belum terhubung, restore tidak bisa jalan")

        val sizeTarget = original.firstOrNull { it.parameter == GraphicsParameter.DISPLAY_RESOLUTION }
            ?.value ?: return BackendResult.Rejected("Target restore ukuran tidak tersimpan")
        val densityTarget = original.firstOrNull { it.parameter == GraphicsParameter.DISPLAY_DENSITY }
            ?.value ?: return BackendResult.Rejected("Target restore density tidak tersimpan")

        val clearSize = sizeTarget == CLEAR_VALUE
        val clearDensity = densityTarget == CLEAR_VALUE

        if (clearSize || clearDensity) {
            val r = channel.clearDisplayOverride(0, clearSize, clearDensity)
            if (!r.ok) return BackendResult.Rejected("Clear override gagal: ${r.error}")
        }
        if (!clearSize) {
            val size = parseSize(sizeTarget)
                ?: return BackendResult.Rejected("Target ukuran tersimpan rusak: $sizeTarget")
            val r = channel.setDisplaySize(0, size.first, size.second, restoring = true)
            if (!r.ok) return BackendResult.Rejected("Restore ukuran gagal: ${r.error}")
        }
        if (!clearDensity) {
            val density = densityTarget.toIntOrNull()
                ?: return BackendResult.Rejected("Target density tersimpan rusak: $densityTarget")
            val r = channel.setDisplayDensity(0, density, restoring = true)
            if (!r.ok) return BackendResult.Rejected("Restore density gagal: ${r.error}")
        }

        fun sizeOk(st: DisplayState) = if (clearSize) !st.sizeOverridden
        else "${st.baseWidth}x${st.baseHeight}" == sizeTarget

        fun densityOk(st: DisplayState) = if (clearDensity) !st.densityOverridden
        else st.baseDensity.toString() == densityTarget

        val after = readBack(channel, null) { sizeOk(it) && densityOk(it) }
            ?: return BackendResult.Rejected("Restore dijalankan tetapi state tidak bisa dibaca ulang")
        val sizeMatches = sizeOk(after)
        val densityMatches = densityOk(after)

        return if (after.ok && sizeMatches && densityMatches) {
            BackendResult.Applied(
                original,
                "Terbaca ulang: aktif ${after.current} @${after.baseDensity}dpi" +
                        if (!after.anyOverride) " (bawaan panel)" else ""
            )
        } else {
            BackendResult.Rejected(
                "Restore tidak terverifikasi: aktif ${after.current} @${after.baseDensity}dpi"
            )
        }
    }

    /**
     * Membaca ulang sampai nilai cocok atau percobaan habis. Tidak mengubah
     * kesimpulan: kalau tetap tidak cocok, hasilnya tetap gagal.
     */
    private fun readBack(
        channel: PrivilegedChannel,
        first: DisplayState?,
        matches: (DisplayState) -> Boolean
    ): DisplayState? {
        var state: DisplayState? = first?.takeIf { it.ok } ?: channel.readDisplayState(0)
        for (attempt in 1..READ_BACK_ATTEMPTS) {
            val current = state
            if (current != null && current.ok && matches(current)) return current
            if (attempt == READ_BACK_ATTEMPTS) break
            Thread.sleep(READ_BACK_DELAY_MS)
            state = channel.readDisplayState(0)
        }
        return state
    }

    private const val READ_BACK_ATTEMPTS = 3
    private const val READ_BACK_DELAY_MS = 150L

    private fun parseSize(value: String): Pair<Int, Int>? {
        val parts = value.lowercase().split("x")
        if (parts.size != 2) return null
        val w = parts[0].trim().toIntOrNull() ?: return null
        val h = parts[1].trim().toIntOrNull() ?: return null
        return w to h
    }
}
