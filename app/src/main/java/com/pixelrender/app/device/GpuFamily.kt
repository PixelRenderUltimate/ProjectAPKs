package com.pixelrender.app.device

// Murni: tanpa Android SDK. Diuji di app/src/test.

enum class GpuFamily(val label: String) {
    ADRENO("Qualcomm Adreno"),
    MALI("ARM Mali"),
    IMMORTALIS("ARM Immortalis"),
    XCLIPSE("Samsung Xclipse"),
    POWERVR("Imagination PowerVR"),
    MALEOON("Huawei Maleoon"),
    NVIDIA("NVIDIA"),
    SOFTWARE("Software / emulator"),
    OTHER("GPU lain"),
    UNKNOWN("Tidak terdeteksi")
}

data class GpuIdentity(
    val family: GpuFamily,
    /** GLES diterjemahkan ANGLE ke Vulkan, misalnya pada Exynos dengan Xclipse. */
    val viaAngle: Boolean,
    val evidence: String
)

/**
 * Menentukan keluarga GPU dari string yang dilaporkan driver, dengan vendor
 * ID Vulkan sebagai cadangan. Dipakai untuk menempatkan laporan pengujian di
 * sel matriks yang benar, bukan untuk mengubah perilaku aplikasi.
 */
object GpuClassifier {

    private val ANGLE = Regex("\\bANGLE\\b")
    private val IMG_B_SERIES = Regex("\\bimg\\s*b[xe][a-z]*-")

    private val VENDOR_IDS = mapOf(
        0x5143 to GpuFamily.ADRENO,
        0x13B5 to GpuFamily.MALI,
        0x144D to GpuFamily.XCLIPSE,
        0x1010 to GpuFamily.POWERVR,
        0x10DE to GpuFamily.NVIDIA,
        0x19E5 to GpuFamily.MALEOON,
        0x1AE0 to GpuFamily.SOFTWARE
    )

    fun classify(
        glRenderer: String?,
        glVendor: String?,
        vulkanDeviceName: String?,
        vulkanVendorId: Int?
    ): GpuIdentity {
        val viaAngle = glRenderer?.let { ANGLE.containsMatchIn(it) } == true
        val sources = listOfNotNull(
            glRenderer?.takeIf { it.isNotBlank() }?.let { "GL_RENDERER: $it" },
            vulkanDeviceName?.takeIf { it.isNotBlank() }?.let { "Vulkan: $it" },
            glVendor?.takeIf { it.isNotBlank() }?.let { "GL_VENDOR: $it" }
        )
        val text = sources.joinToString(" | ").lowercase()

        // Emulator dicek pertama: emulator sering menyebut GPU host di string-nya.
        val byName = when {
            listOf("swiftshader", "llvmpipe", "lavapipe", "emulator", "gfxstream", "virgl")
                .any { it in text } -> GpuFamily.SOFTWARE
            "immortalis" in text -> GpuFamily.IMMORTALIS // sebelum "mali"
            "mali" in text -> GpuFamily.MALI
            "adreno" in text -> GpuFamily.ADRENO
            "xclipse" in text -> GpuFamily.XCLIPSE
            "powervr" in text || "rogue" in text || IMG_B_SERIES.containsMatchIn(text) ->
                GpuFamily.POWERVR
            "maleoon" in text -> GpuFamily.MALEOON
            "nvidia" in text || "tegra" in text -> GpuFamily.NVIDIA
            else -> null
        }
        if (byName != null) {
            return GpuIdentity(byName, viaAngle, sources.firstOrNull().orEmpty())
        }

        val byVendor = vulkanVendorId?.let { VENDOR_IDS[it] }
        if (byVendor != null) {
            return GpuIdentity(
                byVendor, viaAngle,
                "Vulkan vendorID 0x${Integer.toHexString(vulkanVendorId).uppercase()}"
            )
        }

        return if (sources.isEmpty()) {
            GpuIdentity(GpuFamily.UNKNOWN, viaAngle, "GL_RENDERER dan Vulkan tidak terbaca")
        } else {
            GpuIdentity(GpuFamily.OTHER, viaAngle, sources.first())
        }
    }
}
