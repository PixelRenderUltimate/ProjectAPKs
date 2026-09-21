package com.pixelrender.app.graphics

import com.pixelrender.app.shizuku.DisplayWritePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DisplayPlannerTest {

    private val panels = listOf(
        Triple(1080, 2400, 420), Triple(1080, 2340, 440), Triple(1080, 2412, 400),
        Triple(1080, 2460, 440), Triple(1116, 2484, 440), Triple(1200, 2670, 450),
        Triple(1220, 2712, 440), Triple(1260, 2800, 480), Triple(1264, 2780, 450),
        Triple(1440, 3120, 560), Triple(1440, 3200, 560), Triple(720, 1600, 320),
        Triple(720, 1612, 300), Triple(1600, 2560, 320), Triple(2560, 1600, 320),
        Triple(1812, 2176, 390), Triple(1080, 1920, 480)
    )

    /**
     * Invarian terpenting: setiap rencana yang aplikasi tandai "allowed" HARUS
     * diterima service. Kalau tidak, tombol Apply menjanjikan hal yang akan
     * ditolak di ujung.
     */
    @Test
    fun everyAllowedPlanIsAcceptedByTheService() {
        var tested = 0
        panels.forEach { (w, h, d) ->
            DisplayPlanner.plans(w, h, d).filter { it.allowed }.forEach { p ->
                tested++
                assertNull("service tolak ukuran ${w}x$h ${p.percent}%",
                    DisplayWritePolicy.checkSize(w, h, p.width, p.height, false))
                assertNull("service tolak density ${w}x$h ${p.percent}%",
                    DisplayWritePolicy.checkDensity(d, p.density, false))
                assertTrue("dimensi genap: ${p.label}", p.width % 2 == 0 && p.height % 2 == 0)
                val deviation = abs(p.width.toDouble() / p.height - w.toDouble() / h) / (w.toDouble() / h)
                assertTrue("rasio aspek menyimpang: ${p.label}", deviation < 0.01)
                assertTrue("tidak boleh naik: ${p.label}", p.width <= w && p.height <= h && p.density <= d)
            }
        }
        assertTrue("jumlah rencana yang diuji terlalu sedikit: $tested", tested >= 90)
    }

    @Test
    fun panelNativeScaleIsNeverApplicable() {
        panels.forEach { (w, h, d) ->
            assertFalse(DisplayPlanner.plan(w, h, d, 1.0f).allowed)
        }
    }

    @Test
    fun knownValues() {
        val medium = DisplayPlanner.plan(1080, 2400, 420, 0.5f)
        assertEquals(540, medium.width)
        assertEquals(1200, medium.height)
        assertEquals(210, medium.density)
        // Klaim di README: 40% di panel 720p diblokir karena sisi 288 px.
        val extreme720 = DisplayPlanner.plan(720, 1600, 320, 0.4f)
        assertFalse(extreme720.allowed)
        assertEquals(288, extreme720.width)
    }
}
