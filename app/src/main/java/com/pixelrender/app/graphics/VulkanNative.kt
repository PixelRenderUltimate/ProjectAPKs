package com.pixelrender.app.graphics

/**
 * Jembatan JNI tipis ke probe Vulkan. Semua logika ada di
 * src/main/cpp/vulkan_probe.cpp. Jika library native gagal dimuat,
 * aplikasi tetap berjalan dan jatuh ke deteksi berbasis PackageManager.
 */
object VulkanNative {

    val nativeLibraryLoaded: Boolean = runCatching {
        System.loadLibrary("pixelrender_native")
    }.isSuccess

    private external fun nativeQueryJson(): String

    fun queryJson(): String {
        if (!nativeLibraryLoaded) {
            return """{"available":false,"reason":"native library gagal dimuat"}"""
        }
        return runCatching { nativeQueryJson() }.getOrElse { t ->
            """{"available":false,"reason":"${t.javaClass.simpleName}"}"""
        }
    }
}
