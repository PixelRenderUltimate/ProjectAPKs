package com.pixelrender.app.shizuku

// Murni: tanpa Android SDK dan tanpa org.json. Diuji di app/src/test.

/** State display dari IWindowManager: ukuran panel (initial) dan yang aktif (base). */
data class DisplayState(
    val ok: Boolean,
    val initialWidth: Int = 0,
    val initialHeight: Int = 0,
    val baseWidth: Int = 0,
    val baseHeight: Int = 0,
    val initialDensity: Int = 0,
    val baseDensity: Int = 0,
    val sizeOverridden: Boolean = false,
    val densityOverridden: Boolean = false,
    val error: String = ""
) {
    val physical: String get() = "$initialWidth x $initialHeight"
    val current: String get() = "$baseWidth x $baseHeight"
    val anyOverride: Boolean get() = sizeOverridden || densityOverridden
}
