package com.pixelrender.app.graphics

import com.pixelrender.app.shizuku.DisplayState
import kotlin.math.roundToInt

enum class ProfileId(val label: String) {
    DEFAULT("Default"),
    PIXEL_LOW("Pixel Low"),
    PIXEL_MEDIUM("Pixel Medium"),
    PIXEL_EXTREME("Pixel Extreme"),
    CUSTOM("Custom")
}

/**
 * Satu bagian profil.
 *
 * [intent] sengaja berupa deskripsi kualitatif ("off", "agresif"), bukan angka.
 * Untuk parameter yang tidak punya API, menulis angka seperti "LOD bias +2.0"
 * akan memberi kesan ada nilai yang benar-benar diterapkan, padahal tidak.
 * Satu-satunya angka nyata adalah [scale] untuk resolusi render.
 */
data class ProfileSetting(
    val parameter: GraphicsParameter,
    val intent: String,
    val scale: Float? = null
)

data class GraphicsProfile(
    val id: ProfileId,
    val description: String,
    val settings: List<ProfileSetting>
)

object GraphicsProfiles {

    const val LOW_SCALE = 0.75f
    const val MEDIUM_SCALE = 0.5f
    const val EXTREME_SCALE = 0.4f

    fun forId(id: ProfileId, customScale: Float): GraphicsProfile = when (id) {
        ProfileId.DEFAULT -> GraphicsProfile(
            id,
            "Kembali ke state perangkat sebelum PixelRender menulis apa pun. " +
                    "Menerapkan profil ini sama dengan Restore.",
            emptyList()
        )
        ProfileId.PIXEL_LOW -> pixel(
            id, "Sedikit lebih kasar. Tekstur halus mulai kehilangan detail.",
            LOW_SCALE, filtering = "rendah", lod = "moderat", aa = "rendah / off"
        )
        ProfileId.PIXEL_MEDIUM -> pixel(
            id, "Jelas kotak-kotak di tepi objek dan tekstur jauh.",
            MEDIUM_SCALE, filtering = "sangat rendah", lod = "lebih kuat", aa = "off"
        )
        ProfileId.PIXEL_EXTREME -> pixel(
            id, "Sangat kasar, paling mendekati tampilan blok.",
            EXTREME_SCALE, filtering = "minimum", lod = "agresif", aa = "off"
        )
        ProfileId.CUSTOM -> GraphicsProfile(
            id,
            "Pilih sendiri skala resolusi render dari langkah yang aman untuk panel ini.",
            listOf(
                ProfileSetting(
                    GraphicsParameter.DISPLAY_RESOLUTION,
                    "${percent(customScale)}% dari panel",
                    customScale
                )
            )
        )
    }

    private fun pixel(
        id: ProfileId,
        description: String,
        scale: Float,
        filtering: String,
        lod: String,
        aa: String
    ) = GraphicsProfile(
        id,
        description,
        listOf(
            ProfileSetting(GraphicsParameter.DISPLAY_RESOLUTION, "${percent(scale)}% dari panel", scale),
            ProfileSetting(GraphicsParameter.TEXTURE_FILTERING, filtering),
            ProfileSetting(GraphicsParameter.ANISOTROPIC_FILTERING, "off"),
            ProfileSetting(GraphicsParameter.MIPMAP_LOD, lod),
            ProfileSetting(GraphicsParameter.MSAA, aa)
        )
    )

    private fun percent(scale: Float): Int = (scale * 100).roundToInt()
}

enum class SettingOutcome(val label: String) {
    WILL_APPLY("Diterapkan"),
    NO_API("Tidak ada API"),
    UNVERIFIED("Belum terverifikasi"),
    BLOCKED("Terhalang")
}

data class ResolvedSetting(
    val parameter: GraphicsParameter,
    val intent: String,
    val outcome: SettingOutcome,
    val detail: String
)

data class ResolvedProfile(
    val profile: GraphicsProfile,
    val settings: List<ResolvedSetting>,
    /** Target konkret. Null kalau resolusi tidak bisa diterapkan. */
    val plan: DisplayPlanner.Plan?,
    /** Null berarti profil bisa diterapkan. */
    val blockedReason: String?
) {
    val applyCount: Int get() = settings.count { it.outcome == SettingOutcome.WILL_APPLY }
}

enum class ProfileState(val label: String) {
    NOT_READY("Not ready"),
    READY("Ready"),
    AWAITING_CONFIRMATION("Awaiting confirmation"),
    ACTIVE("Active"),
    ACTIVE_UNVERIFIED("Active (belum diverifikasi)"),
    DRIFTED("Berubah di luar aplikasi")
}

/**
 * Mencocokkan profil dengan matriks kemampuan perangkat ini.
 *
 * Tidak ada fallback diam-diam: kalau skala profil tidak aman untuk panel ini,
 * profil ditandai terhalang beserta alasannya, bukan diganti ke skala lain
 * tanpa sepengetahuan user.
 */
object ProfileResolver {

    private const val NO_API =
        "Android tidak menyediakan API untuk mengubah ini di aplikasi lain"

    fun resolve(
        profile: GraphicsProfile,
        capabilities: List<Capability>,
        activeBackend: BackendId?,
        panel: DisplayState?,
        hasPendingChanges: Boolean
    ): ResolvedProfile {
        if (profile.id == ProfileId.DEFAULT) {
            return ResolvedProfile(
                profile = profile,
                settings = emptyList(),
                plan = null,
                blockedReason = if (hasPendingChanges) null
                else "Perangkat sudah dalam kondisi default"
            )
        }

        var plan: DisplayPlanner.Plan? = null

        val resolved = profile.settings.map { setting ->
            val capability = capabilities.firstOrNull { it.parameter == setting.parameter }

            if (setting.parameter == GraphicsParameter.DISPLAY_RESOLUTION) {
                val scale = setting.scale
                when {
                    scale == null -> blocked(setting, "Skala tidak ditentukan")
                    panel == null || !panel.ok -> blocked(
                        setting,
                        "Ukuran panel belum terbaca. Bind service di tab Shizuku."
                    )
                    else -> {
                        val p = DisplayPlanner.plan(
                            panel.initialWidth, panel.initialHeight, panel.initialDensity, scale
                        )
                        when {
                            !p.allowed -> blocked(setting, p.reason)
                            capability?.externalControl != Support.SUPPORTED -> ResolvedSetting(
                                setting.parameter, setting.intent, SettingOutcome.UNVERIFIED,
                                "Belum ada backend yang terverifikasi bisa mengubah resolusi"
                            )
                            activeBackend != BackendId.SHIZUKU -> blocked(
                                setting,
                                "Backend aktif (${activeBackend?.displayName ?: "-"}) " +
                                        "tidak memegang parameter ini"
                            )
                            else -> {
                                plan = p
                                ResolvedSetting(
                                    setting.parameter, setting.intent,
                                    SettingOutcome.WILL_APPLY, p.label
                                )
                            }
                        }
                    }
                }
            } else {
                when (capability?.externalControl) {
                    // Belum ada parameter lain yang SUPPORTED. Kalau suatu hari ada,
                    // tetap tidak diterapkan sampai jalur tulisnya dibuat dan diverifikasi.
                    Support.SUPPORTED -> ResolvedSetting(
                        setting.parameter, setting.intent, SettingOutcome.UNVERIFIED,
                        "Didukung, tetapi belum ada jalur tulis yang terverifikasi"
                    )
                    Support.UNKNOWN -> ResolvedSetting(
                        setting.parameter, setting.intent, SettingOutcome.UNVERIFIED,
                        "Belum bisa dipastikan di perangkat ini"
                    )
                    else -> ResolvedSetting(
                        setting.parameter, setting.intent, SettingOutcome.NO_API, NO_API
                    )
                }
            }
        }

        val blockedReason = if (plan != null) null else {
            resolved.firstOrNull { it.parameter == GraphicsParameter.DISPLAY_RESOLUTION }?.detail
                ?: "Tidak ada bagian profil ini yang bisa diterapkan di perangkat ini"
        }
        return ResolvedProfile(profile, resolved, plan, blockedReason)
    }

    private fun blocked(setting: ProfileSetting, reason: String) =
        ResolvedSetting(setting.parameter, setting.intent, SettingOutcome.BLOCKED, reason)
}
