package com.pixelrender.app.shizuku

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayWritePolicyTest {

    @Test
    fun applyOnlyAllowsProportionalDownscale() {
        assertNull("50% harus lolos", DisplayWritePolicy.checkSize(1080, 2400, 540, 1200, false))
        assertNotNull("upscale ditolak", DisplayWritePolicy.checkSize(1080, 2400, 1440, 3200, false))
        assertNotNull("rasio berubah ditolak", DisplayWritePolicy.checkSize(1080, 2400, 540, 1000, false))
        assertNotNull("di bawah 30% ditolak", DisplayWritePolicy.checkSize(1080, 2400, 300, 666, false))
        assertNotNull("sisi < 240 ditolak", DisplayWritePolicy.checkSize(1080, 2400, 200, 444, false))
        assertNotNull("nol ditolak", DisplayWritePolicy.checkSize(1080, 2400, 0, 1200, false))
    }

    @Test
    fun restoreAcceptsUsersOwnPriorOverride() {
        assertNull("override lama lebih besar", DisplayWritePolicy.checkSize(1080, 2400, 1440, 3200, true))
        assertNull("override lama beda rasio", DisplayWritePolicy.checkSize(1080, 2400, 1080, 1920, true))
        assertNotNull("restore > 2x ditolak", DisplayWritePolicy.checkSize(1080, 2400, 2400, 5400, true))
    }

    @Test
    fun densityBounds() {
        assertNull(DisplayWritePolicy.checkDensity(420, 210, false))
        assertNotNull("density naik ditolak", DisplayWritePolicy.checkDensity(420, 500, false))
        assertNotNull("density < 30% ditolak", DisplayWritePolicy.checkDensity(420, 100, false))
        assertNull("restore density lama", DisplayWritePolicy.checkDensity(420, 600, true))
        assertNotNull("restore density > 2x", DisplayWritePolicy.checkDensity(420, 1000, true))
    }
}
