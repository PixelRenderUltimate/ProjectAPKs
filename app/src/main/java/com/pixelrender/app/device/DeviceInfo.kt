package com.pixelrender.app.device

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import com.pixelrender.app.logging.Logger

data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val board: String,
    val hardware: String,
    val socManufacturer: String,
    val socModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildType: String,
    val fingerprint: String,
    val abis: List<String>,
    val primaryAbi: String,
    val is64Bit: Boolean,
    val totalRamBytes: Long,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val density: Float,
    val refreshRate: Float,
    val supportedRefreshRates: List<Float>,
    /** true jika build perangkat adalah userdebug/eng. Penting untuk GPU debug layers. */
    val isDebuggableBuild: Boolean
) {
    val ramGb: String get() = String.format("%.1f GB", totalRamBytes / 1024.0 / 1024.0 / 1024.0)
    val resolution: String get() = "${widthPx} x ${heightPx}"
}

object DeviceInfoCollector {

    fun collect(context: Context): DeviceInfo {
        val (w, h, dpi, density) = collectDisplayMetrics(context)
        val display = runCatching {
            context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull()

        val refresh = display?.refreshRate ?: 0f
        val modes = runCatching {
            display?.supportedModes?.map { it.refreshRate }?.distinct()?.sorted() ?: emptyList()
        }.getOrDefault(emptyList())

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val abis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()

        val info = DeviceInfo(
            manufacturer = Build.MANUFACTURER ?: "unknown",
            brand = Build.BRAND ?: "unknown",
            model = Build.MODEL ?: "unknown",
            device = Build.DEVICE ?: "unknown",
            board = Build.BOARD ?: "unknown",
            hardware = Build.HARDWARE ?: "unknown",
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MANUFACTURER ?: "unknown"
            } else "not exposed (API < 31)",
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL ?: "unknown"
            } else "not exposed (API < 31)",
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH ?: "unknown"
            } else "unknown",
            buildType = Build.TYPE ?: "unknown",
            fingerprint = Build.FINGERPRINT ?: "unknown",
            abis = abis,
            primaryAbi = abis.firstOrNull() ?: "unknown",
            is64Bit = abis.any { it.contains("64") },
            totalRamBytes = memInfo.totalMem,
            widthPx = w,
            heightPx = h,
            densityDpi = dpi,
            density = density,
            refreshRate = refresh,
            supportedRefreshRates = modes,
            isDebuggableBuild = Build.TYPE == "userdebug" || Build.TYPE == "eng"
        )

        Logger.i("Device detected", "${info.manufacturer} ${info.model} (${info.device})")
        Logger.i(
            "Android ${info.androidRelease} / API ${info.sdkInt} / build type ${info.buildType}"
        )
        Logger.i("ABI: ${info.primaryAbi}", info.abis.joinToString(", "))
        Logger.i("Display: ${info.resolution} @ ${info.densityDpi}dpi, ${info.refreshRate} Hz")
        Logger.i("RAM: ${info.ramGb}")
        return info
    }

    private data class Metrics(val w: Int, val h: Int, val dpi: Int, val density: Float)

    /**
     * Lewat DisplayManager, bukan WindowManager: collector dipanggil dengan
     * Application context, dan WindowManager adalah layanan visual yang
     * seharusnya hanya diakses dari context UI (StrictMode menandainya).
     */
    @Suppress("DEPRECATION")
    private fun collectDisplayMetrics(context: Context): Metrics {
        val fallback: DisplayMetrics = context.resources.displayMetrics
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val dm = DisplayMetrics()
        display?.getRealMetrics(dm)
        return if (dm.widthPixels > 0) {
            Metrics(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density)
        } else {
            Metrics(fallback.widthPixels, fallback.heightPixels, fallback.densityDpi, fallback.density)
        }
    }
}
