package com.pixelrender.app.graphics

import android.content.Context
import android.content.pm.PackageManager
import com.pixelrender.app.logging.Logger
import org.json.JSONObject

data class VulkanDevice(
    val name: String,
    val apiVersion: String,
    val driverVersionRaw: Long,
    val vendorId: Int,
    val deviceId: Int,
    val type: String,
    val samplerAnisotropy: Boolean,
    val maxSamplerAnisotropy: Int,
    val maxImageDimension2D: Int,
    val colorSampleCounts: List<Int>,
    val deviceExtensions: List<String>
) {
    val vendorName: String
        get() = when (vendorId) {
            0x13B5 -> "ARM"
            0x5143 -> "Qualcomm"
            0x1010 -> "Imagination Technologies"
            0x10DE -> "NVIDIA"
            0x8086 -> "Intel"
            0x1002 -> "AMD"
            0x144D -> "Samsung"
            else -> "0x${Integer.toHexString(vendorId)}"
        }
}

data class VulkanInfo(
    val available: Boolean,
    val loaderApiVersion: String = "",
    val instanceExtensions: List<String> = emptyList(),
    val devices: List<VulkanDevice> = emptyList(),
    /** Dari PackageManager, independen dari probe native. */
    val systemFeatureVersion: String = "",
    val hardwareLevel: Int = -1,
    val computeSupported: Boolean = false,
    val nativeProbeUsed: Boolean = false,
    val reason: String? = null
) {
    val primaryDevice: VulkanDevice? get() = devices.firstOrNull()
}

object VulkanCapability {

    fun query(context: Context): VulkanInfo {
        val pm = context.packageManager

        // 1. Sumber kebenaran yang selalu tersedia: system feature.
        val featureVersion = pm.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            ?.version ?: 0
        val level = pm.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }
            ?.version ?: -1
        val compute = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE)
        val featureVersionString =
            if (featureVersion > 0) decodeVersion(featureVersion) else ""

        if (featureVersion == 0) {
            Logger.w("Vulkan tidak dilaporkan oleh PackageManager")
        } else {
            Logger.i("Vulkan system feature: $featureVersionString (hardware level $level)")
        }

        // 2. Probe native untuk detail (extension list, limits).
        val json = VulkanNative.queryJson()
        val parsed = runCatching { JSONObject(json) }.getOrNull()

        if (parsed == null || !parsed.optBoolean("available", false)) {
            val reason = parsed?.optString("reason").takeUnless { it.isNullOrBlank() }
                ?: "probe native tidak tersedia"
            if (featureVersion > 0) {
                Logger.w("Probe Vulkan native gagal, memakai data PackageManager", reason)
                return VulkanInfo(
                    available = true,
                    loaderApiVersion = featureVersionString,
                    systemFeatureVersion = featureVersionString,
                    hardwareLevel = level,
                    computeSupported = compute,
                    nativeProbeUsed = false,
                    reason = reason
                )
            }
            Logger.w("Vulkan tidak tersedia pada perangkat ini", reason)
            return VulkanInfo(
                available = false,
                systemFeatureVersion = featureVersionString,
                hardwareLevel = level,
                computeSupported = compute,
                nativeProbeUsed = false,
                reason = reason
            )
        }

        val instanceExts = parsed.optJSONArray("instanceExtensions").toStringList()
        val devicesArray = parsed.optJSONArray("devices")
        val devices = buildList {
            for (i in 0 until (devicesArray?.length() ?: 0)) {
                val d = devicesArray!!.getJSONObject(i)
                add(
                    VulkanDevice(
                        name = d.optString("name"),
                        apiVersion = d.optString("apiVersion"),
                        driverVersionRaw = d.optLong("driverVersionRaw"),
                        vendorId = d.optInt("vendorId"),
                        deviceId = d.optInt("deviceId"),
                        type = d.optString("type"),
                        samplerAnisotropy = d.optBoolean("samplerAnisotropy"),
                        maxSamplerAnisotropy = d.optInt("maxSamplerAnisotropy"),
                        maxImageDimension2D = d.optInt("maxImageDimension2D"),
                        colorSampleCounts = d.optJSONArray("colorSampleCounts").toIntList(),
                        deviceExtensions = d.optJSONArray("deviceExtensions").toStringList()
                    )
                )
            }
        }

        val info = VulkanInfo(
            available = true,
            loaderApiVersion = parsed.optString("loaderApiVersion"),
            instanceExtensions = instanceExts,
            devices = devices,
            systemFeatureVersion = featureVersionString,
            hardwareLevel = level,
            computeSupported = compute,
            nativeProbeUsed = true
        )

        devices.firstOrNull()?.let { d ->
            Logger.ok("Vulkan ${d.apiVersion} - ${d.name}", "vendor: ${d.vendorName}")
            Logger.i(
                "Vulkan limits",
                "samplerAnisotropy=${d.samplerAnisotropy} (max ${d.maxSamplerAnisotropy}x), " +
                        "MSAA=${d.colorSampleCounts.joinToString("/")}x, " +
                        "device extensions=${d.deviceExtensions.size}"
            )
        }
        if (devices.isEmpty()) {
            Logger.w("Vulkan loader ada tetapi tidak ada physical device yang dilaporkan")
        }
        return info
    }

    private fun decodeVersion(v: Int): String =
        "${(v shr 22) and 0x7F}.${(v shr 12) and 0x3FF}.${v and 0xFFF}"

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }.sorted()
    }

    private fun org.json.JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return (0 until length()).map { optInt(it) }
    }
}
