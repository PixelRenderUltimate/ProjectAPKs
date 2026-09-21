package com.pixelrender.app.graphics

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Menghitung target resolusi dari ukuran PANEL (bukan resolusi aktif, yang
 * mungkin sudah di-override), dengan rasio aspek tetap dan density yang ikut
 * turun sebanding.
 *
 * Density ikut turun supaya ukuran UI dalam dp tetap sama: layout tidak
 * berubah, hanya jumlah piksel yang dirender yang berkurang. Itulah yang
 * menghasilkan tampilan kotak-kotak saat sistem meng-upscale ke panel.
 */
object DisplayPlanner {

    /** Langkah skala tetap, bukan slider bebas: tiap langkah bisa dihitung dan dijelaskan. */
    val SCALE_STEPS: List<Float> = listOf(1.0f, 0.9f, 0.8f, 0.75f, 0.66f, 0.5f, 0.4f)

    /** Lebih ketat dari batas service, supaya aplikasi tidak pernah mengirim nilai di tepi. */
    private const val MIN_SHORT_SIDE = 320
    private const val MIN_DENSITY = 100

    data class Plan(
        val scale: Float,
        val width: Int,
        val height: Int,
        val density: Int,
        val allowed: Boolean,
        val reason: String
    ) {
        val percent: Int get() = (scale * 100).roundToInt()
        val isPanelNative: Boolean get() = scale >= 1f
        val label: String get() = "$width x $height @${density}dpi"
    }

    fun plans(panelWidth: Int, panelHeight: Int, panelDensity: Int): List<Plan> =
        SCALE_STEPS.map { plan(panelWidth, panelHeight, panelDensity, it) }

    fun plan(panelWidth: Int, panelHeight: Int, panelDensity: Int, scale: Float): Plan {
        if (panelWidth <= 0 || panelHeight <= 0 || panelDensity <= 0) {
            return Plan(scale, 0, 0, 0, false, "Ukuran panel belum terbaca")
        }
        if (scale >= 1f) {
            return Plan(
                scale, panelWidth, panelHeight, panelDensity,
                allowed = false,
                reason = "Resolusi asli panel. Pakai Restore untuk kembali ke sini."
            )
        }

        val width = even((panelWidth * scale).roundToInt())
        // Tinggi diturunkan dari lebar agar rasio aspek identik dengan panel.
        val height = even(((panelHeight.toLong() * width + panelWidth / 2) / panelWidth).toInt())
        val density = ((panelDensity.toLong() * width + panelWidth / 2) / panelWidth).toInt()

        val reason = when {
            min(width, height) < MIN_SHORT_SIDE ->
                "Sisi terpendek ${min(width, height)}px di bawah $MIN_SHORT_SIDE px"
            density < MIN_DENSITY ->
                "Density ${density}dpi di bawah $MIN_DENSITY dpi"
            else -> ""
        }
        return Plan(scale, width, height, density, reason.isEmpty(), reason)
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1
}
