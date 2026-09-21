package com.pixelrender.app.game

import android.content.Context
import android.content.pm.PackageManager
import com.pixelrender.app.logging.Logger

data class GameTarget(
    val label: String,
    val packageName: String,
    val installed: Boolean,
    val versionName: String? = null
)

/**
 * Hanya membaca metadata package lewat PackageManager.
 * Tidak membaca, menyalin, atau mengubah APK / data game.
 */
object GameDetector {

    private val KNOWN_TARGETS = listOf(
        "Free Fire" to "com.dts.freefireth",
        "Free Fire MAX" to "com.dts.freefiremax"
    )

    fun detect(context: Context): List<GameTarget> {
        val pm = context.packageManager
        return KNOWN_TARGETS.map { (label, pkg) ->
            val info = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
            val target = GameTarget(
                label = label,
                packageName = pkg,
                installed = info != null,
                versionName = info?.versionName
            )
            if (target.installed) {
                Logger.ok("Game terdeteksi: $label", "$pkg ${target.versionName ?: ""}")
            } else {
                Logger.i("Game tidak terpasang: $label", pkg)
            }
            target
        }
    }
}
