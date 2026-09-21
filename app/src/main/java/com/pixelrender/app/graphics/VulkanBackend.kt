package com.pixelrender.app.graphics

import android.os.Build

/**
 * Melaporkan kemampuan GPU lewat Vulkan, plus satu-satunya jalur layer resmi
 * yang Android punya, berikut alasan kenapa jalur itu tidak berlaku di HP
 * retail.
 */
object VulkanBackend : GraphicsBackend {

    override val id = BackendId.VULKAN
    override val role = BackendRole.DETECTION_ONLY
    override val priority = 30

    override fun availability(ctx: BackendContext): BackendAvailability {
        val vk = ctx.vulkan
        return if (vk.available) {
            val dev = vk.primaryDevice
            BackendAvailability(
                true,
                "Vulkan ${dev?.apiVersion ?: vk.loaderApiVersion}" +
                        (dev?.let { " pada ${it.name}" } ?: "")
            )
        } else {
            BackendAvailability(false, vk.reason ?: "Vulkan tidak tersedia")
        }
    }

    override fun capabilities(ctx: BackendContext): List<BackendCapability> {
        val vk = ctx.vulkan
        if (!vk.available) return emptyList()
        val dev = vk.primaryDevice
        val caps = mutableListOf<BackendCapability>()

        if (dev != null) {
            caps += BackendCapability(
                parameter = GraphicsParameter.ANISOTROPIC_FILTERING,
                deviceSupport = if (dev.samplerAnisotropy) Support.SUPPORTED else Support.UNSUPPORTED,
                externalControl = Support.UNSUPPORTED,
                evidence = "samplerAnisotropy=${dev.samplerAnisotropy}, " +
                        "maxSamplerAnisotropy=${dev.maxSamplerAnisotropy}x",
                reason = PER_PROCESS_REASON
            )
            caps += BackendCapability(
                parameter = GraphicsParameter.MSAA,
                deviceSupport = if ((dev.colorSampleCounts.maxOrNull() ?: 0) >= 2)
                    Support.SUPPORTED else Support.UNSUPPORTED,
                externalControl = Support.UNSUPPORTED,
                evidence = "framebufferColorSampleCounts: " +
                        dev.colorSampleCounts.joinToString("/") { "${it}x" },
                reason = PER_PROCESS_REASON
            )
            caps += BackendCapability(
                parameter = GraphicsParameter.MIPMAP_LOD,
                deviceSupport = Support.SUPPORTED,
                externalControl = Support.UNSUPPORTED,
                evidence = "VkSamplerCreateInfo punya mipLodBias, minLod, dan maxLod",
                reason = PER_PROCESS_REASON
            )
        }

        val debuggableBuild = ctx.device.isDebuggableBuild
        caps += BackendCapability(
            parameter = GraphicsParameter.LAYER_INJECTION,
            deviceSupport = Support.SUPPORTED,
            externalControl = if (debuggableBuild) Support.PARTIALLY_SUPPORTED
            else Support.UNSUPPORTED,
            evidence = "Build type ${ctx.device.buildType}" +
                    if (debuggableBuild) " (debuggable)" else " (user build)",
            reason = "GraphicsEnvironment hanya memuat GPU debug layer kalau aplikasi target " +
                    "debuggable, atau build perangkat userdebug/eng. Free Fire adalah APK " +
                    "release di build user, jadi jalur ini tertutup. Ini batasan platform, " +
                    "bukan sesuatu yang diakali dari sisi aplikasi.",
            requires = "Build userdebug/eng, atau aplikasi target yang debuggable"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps += BackendCapability(
                parameter = GraphicsParameter.POST_PROCESSING,
                deviceSupport = Support.UNKNOWN,
                externalControl = Support.UNSUPPORTED,
                evidence = "Tidak ada API publik",
                reason = "Post-processing adalah bagian dari render pipeline game itu sendiri. " +
                        "Mengubahnya dari luar berarti injeksi kode ke proses game."
            )
        }
        return caps
    }
}
