package com.pixelrender.app.testing

import com.pixelrender.app.device.GpuFamily
import com.pixelrender.app.state.OperationKind
import com.pixelrender.app.state.OperationRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class TestPlanTest {

    private fun record(kind: OperationKind, verified: Boolean, note: String = "") =
        OperationRecord(kind, "uji", "x", "540 x 1200 @210dpi", verified, note, 0L)

    private val noShizuku = TestEvidence(
        gpuFamily = GpuFamily.MALI,
        glAvailable = true,
        vulkanAvailable = true,
        vulkanNativeProbe = true,
        nativeProbeExpected = true,
        shizukuInstalled = false,
        shizukuConnected = false,
        serviceUid = -1,
        pixelMediumBlockedReason = "Ukuran panel belum terbaca",
        probeResults = emptyList(),
        displayReadable = false,
        gameInstalled = true,
        gameModesCaptured = false,
        history = emptyList()
    )

    private val withShizuku = noShizuku.copy(
        shizukuInstalled = true,
        shizukuConnected = true,
        serviceUid = 2000,
        pixelMediumBlockedReason = null,
        probeResults = listOf("id" to true, "wm size" to true, "wm density" to true, "getprop ro.board.platform" to false),
        displayReadable = true,
        gameModesCaptured = true,
        // Terbaru di depan: REVERT terakhir gagal walaupun yang lama berhasil.
        history = listOf(
            record(OperationKind.RESTORE, true),
            record(OperationKind.REVERT, false, "Shizuku terputus"),
            record(OperationKind.REVERT, true)
        )
    )

    private fun verdicts(e: TestEvidence, manual: Map<String, TestVerdict> = emptyMap()) =
        TestPlan.evaluate(e, manual).associate { it.case.id to it.verdict }

    @Test
    fun withoutShizuku() {
        val v = verdicts(noShizuku)
        assertEquals(TestVerdict.PASS, v["detect.gpu"])
        assertEquals(TestVerdict.PASS, v["detect.gl"])
        assertEquals(TestVerdict.PASS, v["detect.vulkan"])
        assertEquals(TestVerdict.PASS, v["fallback.noshizuku"])
        assertEquals(TestVerdict.NOT_APPLICABLE, v["shizuku.connect"])
        assertEquals(TestVerdict.NOT_APPLICABLE, v["apply.revert"])
        assertEquals(TestVerdict.NOT_APPLICABLE, v["apply.visual"])
        // Diamati setelah reboot, saat Shizuku belum jalan: harus tetap bisa dinilai.
        assertEquals(TestVerdict.PENDING, v["recovery.reboot"])
    }

    @Test
    fun withShizuku() {
        val v = verdicts(withShizuku)
        assertEquals(TestVerdict.PASS, v["shizuku.connect"])
        assertEquals(TestVerdict.PASS, v["shizuku.probe"])
        assertEquals(TestVerdict.PASS, v["shizuku.gamemode"])
        assertEquals(TestVerdict.NOT_APPLICABLE, v["fallback.noshizuku"])
        assertEquals("hasil terbaru yang dihitung", TestVerdict.FAIL, v["apply.revert"])
        assertEquals(TestVerdict.PASS, v["restore.default"])
        assertEquals(TestVerdict.PENDING, v["recovery.forceclose"])
        assertEquals(TestVerdict.PENDING, v["apply.visual"])
    }

    @Test
    fun serviceUidRules() {
        assertEquals(TestVerdict.NOT_APPLICABLE, verdicts(withShizuku.copy(serviceUid = 0))["shizuku.connect"])
        assertEquals(TestVerdict.FAIL, verdicts(withShizuku.copy(serviceUid = 10123))["shizuku.connect"])
    }

    @Test
    fun coreProbeFailureIsReported() {
        val e = withShizuku.copy(probeResults = listOf("id" to true, "wm size" to false, "wm density" to true))
        assertEquals(TestVerdict.FAIL, verdicts(e)["shizuku.probe"])
    }

    @Test
    fun vulkanWithoutNativeProbe() {
        val stripped = noShizuku.copy(vulkanNativeProbe = false, nativeProbeExpected = false)
        assertEquals(TestVerdict.PASS, verdicts(stripped)["detect.vulkan"])
        val broken = noShizuku.copy(vulkanNativeProbe = false, nativeProbeExpected = true)
        assertEquals(TestVerdict.FAIL, verdicts(broken)["detect.vulkan"])
    }

    @Test
    fun manualVerdictsWinAndSummaryAddsUp() {
        val results = TestPlan.evaluate(withShizuku, mapOf("apply.visual" to TestVerdict.PASS))
        assertEquals(TestVerdict.PASS, results.first { it.case.id == "apply.visual" }.verdict)
        assertEquals(TestPlan.cases.size, TestPlan.summary(results).values.sum())
        assertEquals("ARM Mali, dengan Shizuku", TestPlan.cell(withShizuku))
    }
}
