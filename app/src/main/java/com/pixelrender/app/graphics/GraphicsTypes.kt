package com.pixelrender.app.graphics

// Tipe-tipe murni, tanpa ketergantungan Android SDK, supaya bisa diuji
// sebagai unit test JVM biasa (lihat app/src/test).

enum class Support { SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED, UNKNOWN;

    /**
     * Urutan saat pendapat beberapa backend digabung.
     * UNKNOWN sengaja ditaruh di atas UNSUPPORTED: kalau satu backend yakin
     * tidak bisa dan backend lain belum tahu, jawaban yang jujur adalah
     * "belum tahu", bukan "tidak bisa".
     */
    val rank: Int
        get() = when (this) {
            SUPPORTED -> 4
            PARTIALLY_SUPPORTED -> 3
            UNKNOWN -> 2
            UNSUPPORTED -> 1
        }
}

enum class GraphicsParameter(val label: String) {
    TEXTURE_FILTERING("Texture filtering"),
    ANISOTROPIC_FILTERING("Anisotropic filtering"),
    MIPMAP_LOD("Mipmap / LOD"),
    MSAA("MSAA / anti-aliasing"),
    DISPLAY_RESOLUTION("Display resolution (sistem)"),
    DISPLAY_DENSITY("Display density (sistem)"),
    RENDER_SCALE_PER_GAME("Render scale per-game"),
    REFRESH_RATE("Refresh rate"),
    GAME_MODE("Game Mode"),
    SHARPENING("Sharpening"),
    POST_PROCESSING("Post-processing"),
    LAYER_INJECTION("Vulkan / GLES layer injection")
}

enum class BackendId(val displayName: String) {
    AUTO("Auto"),
    STANDARD("Standard"),
    SHIZUKU("Shizuku"),
    VULKAN("Vulkan"),
    OPENGL("OpenGL ES"),
    UNSUPPORTED("Tidak ada")
}

enum class BackendRole(val label: String) {
    /** Hanya membaca kemampuan GPU. Tidak pernah mengubah apa pun. */
    DETECTION_ONLY("Deteksi saja"),

    /** Tidak bisa mengubah sendiri, tapi bisa mengarahkan user ke UI sistem. */
    ASSISTED("Lewat UI sistem"),

    /** Bisa mengubah lewat binder dengan UID shell. */
    PRIVILEGED("Privileged")
}

data class BackendAvailability(val available: Boolean, val reason: String)

/** Pintasan ke halaman Settings sistem. Dibuka user sendiri, bukan oleh aplikasi. */
data class SettingsShortcut(val label: String, val action: String, val note: String)

data class BackendCapability(
    val parameter: GraphicsParameter,
    val deviceSupport: Support,
    val externalControl: Support,
    val evidence: String,
    val reason: String,
    val requires: String? = null
)

data class ParameterChange(val parameter: GraphicsParameter, val value: String)

/** Nilai khusus pada restore: "tidak ada override sebelumnya, bersihkan saja". */
const val CLEAR_VALUE = "clear"

sealed interface BackendResult {
    /** [verification] wajib berisi hasil pembacaan ulang, bukan sekadar "berhasil". */
    data class Applied(
        val changes: List<ParameterChange>,
        val verification: String
    ) : BackendResult

    data class Rejected(val reason: String) : BackendResult
    data class NotImplemented(val availableFromPhase: Int) : BackendResult
}

/** Alasan yang sama dipakai banyak backend, jadi ditaruh di satu tempat. */
internal const val PER_PROCESS_REASON =
    "State sampler dan framebuffer OpenGL ES maupun Vulkan bersifat per-proses. " +
            "Nilainya di-set oleh proses game sendiri saat membuat sampler dan render pass. " +
            "Android tidak menyediakan API apa pun untuk mengubahnya dari proses lain, " +
            "dengan atau tanpa Shizuku."
