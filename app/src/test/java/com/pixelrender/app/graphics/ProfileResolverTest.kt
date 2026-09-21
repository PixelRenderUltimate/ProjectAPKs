package com.pixelrender.app.graphics

import com.pixelrender.app.shizuku.DisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileResolverTest {

    private fun caps(resolution: Support, others: Support = Support.UNSUPPORTED) =
        GraphicsParameter.entries.map { p ->
            val control = if (p == GraphicsParameter.DISPLAY_RESOLUTION ||
                p == GraphicsParameter.DISPLAY_DENSITY) resolution else others
            Capability(p, Support.SUPPORTED, control, null, emptyList())
        }

    private val panel = DisplayState(
        ok = true, initialWidth = 1080, initialHeight = 2400,
        baseWidth = 1080, baseHeight = 2400, initialDensity = 420, baseDensity = 420
    )
    private val panel720 = panel.copy(initialWidth = 720, initialHeight = 1600, initialDensity = 320)

    private fun resolve(
        id: ProfileId,
        caps: List<Capability> = caps(Support.SUPPORTED),
        backend: BackendId? = BackendId.SHIZUKU,
        display: DisplayState? = panel,
        pending: Boolean = false
    ) = ProfileResolver.resolve(GraphicsProfiles.forId(id, 0.66f), caps, backend, display, pending)

    @Test
    fun onlyResolutionIsEverApplied() {
        listOf(ProfileId.PIXEL_LOW, ProfileId.PIXEL_MEDIUM, ProfileId.PIXEL_EXTREME).forEach { id ->
            val r = resolve(id)
            assertNull("$id harus bisa diterapkan", r.blockedReason)
            assertEquals("$id: bagian yang diterapkan", 1, r.applyCount)
            r.settings.filter { it.parameter != GraphicsParameter.DISPLAY_RESOLUTION }.forEach {
                assertEquals("$id ${it.parameter}", SettingOutcome.NO_API, it.outcome)
            }
        }
    }

    /** Klaim SUPPORTED dari backend untuk parameter lain tetap tidak diterapkan. */
    @Test
    fun falseSupportedClaimsAreNotApplied() {
        val r = resolve(ProfileId.PIXEL_EXTREME, caps(Support.SUPPORTED, others = Support.SUPPORTED))
        assertEquals(1, r.applyCount)
        assertTrue(r.settings.filter { it.parameter != GraphicsParameter.DISPLAY_RESOLUTION }
            .all { it.outcome == SettingOutcome.UNVERIFIED })
    }

    @Test
    fun blockedWithoutVerifiedPrivilegedBackend() {
        assertNull(resolve(ProfileId.PIXEL_MEDIUM, caps(Support.UNKNOWN)).plan)
        listOf(BackendId.VULKAN, BackendId.OPENGL, BackendId.STANDARD, null).forEach { b ->
            val r = resolve(ProfileId.PIXEL_MEDIUM, backend = b)
            assertNull("backend $b tidak boleh menghasilkan rencana", r.plan)
            assertNotNull(r.blockedReason)
        }
        assertNull("panel belum terbaca", resolve(ProfileId.PIXEL_LOW, display = null).plan)
    }

    /** Tidak ada fallback diam-diam ke skala lain. */
    @Test
    fun unsafeScaleIsBlockedNotSubstituted() {
        val r = resolve(ProfileId.PIXEL_EXTREME, display = panel720)
        assertNull(r.plan)
        assertEquals(
            SettingOutcome.BLOCKED,
            r.settings.first { it.parameter == GraphicsParameter.DISPLAY_RESOLUTION }.outcome
        )
    }

    @Test
    fun defaultOnlyRunsWhenSomethingIsPending() {
        assertNotNull(resolve(ProfileId.DEFAULT, pending = false).blockedReason)
        assertNull(resolve(ProfileId.DEFAULT, pending = true).blockedReason)
    }

    @Test
    fun mediumTargetMatchesPlanner() {
        val plan = resolve(ProfileId.PIXEL_MEDIUM).plan
        assertEquals("540 x 1200 @210dpi", plan?.label)
    }
}
