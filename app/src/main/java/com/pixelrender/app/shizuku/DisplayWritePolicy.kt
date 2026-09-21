package com.pixelrender.app.shizuku

import kotlin.math.abs
import kotlin.math.min

/**
 * Batas keras untuk setiap penulisan ukuran dan density display. Dipakai di
 * dalam user service sebagai penjaga terakhir, jadi berlaku walaupun proses
 * aplikasi mengirim nilai yang salah.
 *
 * Mode apply hanya boleh MENURUNKAN resolusi dengan rasio aspek yang sama.
 * Mode restore lebih longgar karena harus bisa mengembalikan override lama
 * milik user, yang bentuknya bisa apa saja.
 */
object DisplayWritePolicy {

    private const val MIN_SCALE_APPLY = 0.30
    private const val MIN_SCALE_RESTORE = 0.25
    private const val MAX_SCALE_RESTORE = 2.0
    private const val MIN_SHORT_SIDE = 240
    private const val MAX_ASPECT_DEVIATION = 0.02

    /** @return null kalau boleh, atau alasan penolakan. */
    fun checkSize(
        initialWidth: Int,
        initialHeight: Int,
        width: Int,
        height: Int,
        restoring: Boolean
    ): String? {
        if (initialWidth <= 0 || initialHeight <= 0) return "Ukuran panel tidak terbaca"
        if (width <= 0 || height <= 0) return "Ukuran harus positif"
        if (min(width, height) < MIN_SHORT_SIDE) {
            return "Sisi terpendek $width x $height di bawah batas $MIN_SHORT_SIDE px"
        }

        val scaleW = width.toDouble() / initialWidth
        val scaleH = height.toDouble() / initialHeight

        if (restoring) {
            if (scaleW !in MIN_SCALE_RESTORE..MAX_SCALE_RESTORE ||
                scaleH !in MIN_SCALE_RESTORE..MAX_SCALE_RESTORE
            ) return "Nilai restore di luar rentang aman terhadap ukuran panel"
            return null
        }

        if (width > initialWidth || height > initialHeight) {
            return "Menaikkan resolusi di atas panel tidak diizinkan"
        }
        if (scaleW < MIN_SCALE_APPLY || scaleH < MIN_SCALE_APPLY) {
            return "Skala di bawah ${(MIN_SCALE_APPLY * 100).toInt()}% dari panel"
        }
        val panelAspect = initialWidth.toDouble() / initialHeight
        val targetAspect = width.toDouble() / height
        if (abs(targetAspect - panelAspect) / panelAspect > MAX_ASPECT_DEVIATION) {
            return "Rasio aspek berubah, gambar akan terdistorsi"
        }
        return null
    }

    fun checkDensity(initialDensity: Int, density: Int, restoring: Boolean): String? {
        if (initialDensity <= 0) return "Density panel tidak terbaca"
        if (density <= 0) return "Density harus positif"
        val scale = density.toDouble() / initialDensity
        if (restoring) {
            return if (scale in MIN_SCALE_RESTORE..MAX_SCALE_RESTORE) null
            else "Density restore di luar rentang aman"
        }
        if (density > initialDensity) return "Menaikkan density di atas panel tidak diizinkan"
        if (scale < MIN_SCALE_APPLY) return "Density di bawah ${(MIN_SCALE_APPLY * 100).toInt()}% dari panel"
        return null
    }
}
