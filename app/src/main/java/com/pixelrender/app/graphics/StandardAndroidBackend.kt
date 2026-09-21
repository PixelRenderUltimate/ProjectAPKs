package com.pixelrender.app.graphics

import android.os.Build
import android.provider.Settings

/**
 * Backend tanpa privilese apa pun. Selalu tersedia.
 *
 * Yang bisa dilakukannya bukan mengubah setting, melainkan mengantar user ke
 * halaman Settings yang tepat. Banyak OEM (Samsung, Sony, sebagian Xiaomi)
 * menyediakan pengaturan resolusi layar di sana, dan menurunkannya memberi
 * efek yang sama dengan apa yang dikejar aplikasi ini, tanpa Shizuku sama
 * sekali. Karena ketersediaannya berbeda-beda per OEM dan tidak bisa dideteksi
 * secara andal, statusnya UNKNOWN, bukan SUPPORTED.
 */
object StandardAndroidBackend : GraphicsBackend {

    override val id = BackendId.STANDARD
    override val role = BackendRole.ASSISTED
    override val priority = 10

    override fun availability(ctx: BackendContext) =
        BackendAvailability(true, "Selalu tersedia, tidak butuh permission")

    override fun settingsShortcuts(ctx: BackendContext) = listOf(
        SettingsShortcut(
            label = "Pengaturan layar",
            action = Settings.ACTION_DISPLAY_SETTINGS,
            note = "Kalau OEM menyediakan pilihan resolusi layar, ada di sini. " +
                    "Menurunkannya memberi efek pixelated tanpa perlu Shizuku."
        ),
        SettingsShortcut(
            label = "Opsi pengembang",
            action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            note = "Berisi Force MSAA 4x dan pengaturan GPU debug layer. " +
                    "Force MSAA menambah anti-aliasing, jadi arahnya berlawanan " +
                    "dengan tujuan aplikasi ini."
        )
    )

    override fun apply(ctx: BackendContext, changes: List<ParameterChange>): BackendResult =
        BackendResult.Rejected(
            "Tanpa Shizuku, resolusi hanya bisa diubah olehmu sendiri lewat " +
                    "Settings > Display, kalau OEM menyediakan pilihannya. Pintasannya " +
                    "ada di tab Backend."
        )

    override fun capabilities(ctx: BackendContext): List<BackendCapability> {
        val caps = mutableListOf<BackendCapability>()

        caps += BackendCapability(
            parameter = GraphicsParameter.DISPLAY_RESOLUTION,
            deviceSupport = Support.SUPPORTED,
            externalControl = Support.UNKNOWN,
            evidence = "Resolusi aktif ${ctx.device.resolution}",
            reason = "Aplikasi biasa tidak bisa mengubah resolusi display. Yang bisa " +
                    "dilakukan hanya membuka Settings > Display supaya user mengubahnya " +
                    "sendiri, dan tidak semua OEM menyediakan pilihan itu.",
            requires = "OEM yang menyediakan pengaturan resolusi layar"
        )

        caps += BackendCapability(
            parameter = GraphicsParameter.REFRESH_RATE,
            deviceSupport = if (ctx.device.supportedRefreshRates.size > 1) Support.SUPPORTED
            else Support.UNSUPPORTED,
            externalControl = Support.UNKNOWN,
            evidence = "Mode: " +
                    ctx.device.supportedRefreshRates.joinToString("/") { "${it.toInt()}Hz" },
            reason = "Bisa diubah user lewat Settings pada sebagian besar perangkat, " +
                    "tetapi tidak bisa diubah oleh aplikasi biasa.",
            requires = "Pengaturan refresh rate dari OEM"
        )

        caps += BackendCapability(
            parameter = GraphicsParameter.GAME_MODE,
            deviceSupport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Support.SUPPORTED
            else Support.UNSUPPORTED,
            externalControl = Support.UNSUPPORTED,
            evidence = "GameManager ada sejak API 31, perangkat ini API ${Build.VERSION.SDK_INT}",
            reason = "GameManager.setGameMode() butuh permission MANAGE_GAME_MODE yang " +
                    "bertanda signature|privileged. Aplikasi biasa hanya bisa membaca mode " +
                    "dirinya sendiri.",
            requires = "Permission privileged, tidak bisa diberikan ke aplikasi pihak ketiga"
        )

        caps += BackendCapability(
            parameter = GraphicsParameter.SHARPENING,
            deviceSupport = Support.UNKNOWN,
            externalControl = Support.UNSUPPORTED,
            evidence = "Tidak ada API publik",
            reason = "Sharpening pada ponsel gaming adalah fitur post-processing display " +
                    "milik OEM. Tidak ada API AOSP publik maupun jalur shell untuk mengaturnya."
        )

        listOf(
            GraphicsParameter.TEXTURE_FILTERING,
            GraphicsParameter.ANISOTROPIC_FILTERING,
            GraphicsParameter.MIPMAP_LOD,
            GraphicsParameter.MSAA,
            GraphicsParameter.RENDER_SCALE_PER_GAME
        ).forEach { parameter ->
            caps += BackendCapability(
                parameter = parameter,
                deviceSupport = Support.UNKNOWN,
                externalControl = Support.UNSUPPORTED,
                evidence = "Tanpa privilese",
                reason = PER_PROCESS_REASON
            )
        }

        return caps
    }
}
