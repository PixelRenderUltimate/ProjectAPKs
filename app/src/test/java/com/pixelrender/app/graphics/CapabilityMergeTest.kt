package com.pixelrender.app.graphics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityMergeTest {

    private fun v(b: BackendId, device: Support, control: Support) =
        BackendVerdict(b, device, control, "evidence-$b", "reason-$b", null)

    private fun merge(p: GraphicsParameter, vararg verdicts: BackendVerdict) =
        CapabilityMerge.merge(mapOf(p to verdicts.toList())).single()

    /** "Belum tahu" lebih jujur daripada "tidak bisa". */
    @Test
    fun unknownOutranksUnsupported() {
        val c = merge(
            GraphicsParameter.RENDER_SCALE_PER_GAME,
            v(BackendId.STANDARD, Support.UNKNOWN, Support.UNSUPPORTED),
            v(BackendId.SHIZUKU, Support.UNKNOWN, Support.UNKNOWN)
        )
        assertEquals(Support.UNKNOWN, c.externalControl)
        assertNull("UNKNOWN tidak boleh punya pemegang", c.controlledBy)
    }

    @Test
    fun supportedWinsAndNamesItsBackend() {
        val c = merge(
            GraphicsParameter.DISPLAY_RESOLUTION,
            v(BackendId.STANDARD, Support.SUPPORTED, Support.UNKNOWN),
            v(BackendId.SHIZUKU, Support.SUPPORTED, Support.SUPPORTED)
        )
        assertEquals(Support.SUPPORTED, c.externalControl)
        assertEquals(BackendId.SHIZUKU, c.controlledBy)
        assertEquals("pendapat terkuat di depan", BackendId.SHIZUKU, c.verdicts.first().backend)
        assertEquals("semua pendapat dipertahankan", 2, c.verdicts.size)
    }

    @Test
    fun partialAlsoNamesItsBackend() {
        val c = merge(
            GraphicsParameter.GAME_MODE,
            v(BackendId.STANDARD, Support.SUPPORTED, Support.UNSUPPORTED),
            v(BackendId.SHIZUKU, Support.SUPPORTED, Support.PARTIALLY_SUPPORTED)
        )
        assertEquals(BackendId.SHIZUKU, c.controlledBy)
    }

    @Test
    fun deviceSupportTakesStrongestOpinion() {
        val c = merge(
            GraphicsParameter.ANISOTROPIC_FILTERING,
            v(BackendId.OPENGL, Support.UNSUPPORTED, Support.UNSUPPORTED),
            v(BackendId.VULKAN, Support.SUPPORTED, Support.UNSUPPORTED)
        )
        assertEquals(Support.SUPPORTED, c.deviceSupport)
        assertEquals(Support.UNSUPPORTED, c.externalControl)
        assertNull(c.controlledBy)
    }

    @Test
    fun parametersWithoutOpinionsAreDropped() {
        val merged = CapabilityMerge.merge(
            mapOf(
                GraphicsParameter.MSAA to emptyList(),
                GraphicsParameter.SHARPENING to listOf(
                    v(BackendId.STANDARD, Support.UNKNOWN, Support.UNSUPPORTED)
                )
            )
        )
        assertEquals(1, merged.size)
        assertTrue(merged.none { it.parameter == GraphicsParameter.MSAA })
    }
}
