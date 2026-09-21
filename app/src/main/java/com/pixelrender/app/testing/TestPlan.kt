package com.pixelrender.app.testing

import com.pixelrender.app.device.GpuFamily
import com.pixelrender.app.state.OperationKind
import com.pixelrender.app.state.OperationRecord

// Murni: tanpa Android SDK. Diuji di app/src/test.

enum class TestKind(val label: String) {
    AUTO("Otomatis, dinilai dari bukti"),
    MANUAL("Manual, dinilai olehmu")
}

enum class TestVerdict(val label: String) {
    PASS("Lulus"),
    FAIL("Gagal"),
    PENDING("Belum dijalankan"),
    NOT_APPLICABLE("Tidak berlaku"),
    SKIPPED("Dilewati")
}

data class TestCase(
    val id: String,
    val title: String,
    val kind: TestKind,
    val howTo: String
)

data class TestResult(val case: TestCase, val verdict: TestVerdict, val evidence: String)

/**
 * Bukti yang dikumpulkan dari state aplikasi. Tidak ada field yang diisi
 * tangan; satu-satunya masukan manual adalah peta verdict untuk uji MANUAL.
 */
data class TestEvidence(
    val gpuFamily: GpuFamily,
    val glAvailable: Boolean,
    val vulkanAvailable: Boolean,
    val vulkanNativeProbe: Boolean,
    val nativeProbeExpected: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuConnected: Boolean,
    val serviceUid: Int,
    /** Alasan Pixel Medium ditahan. Dipakai untuk uji tanpa Shizuku. */
    val pixelMediumBlockedReason: String?,
    /** Pasangan command -> sukses dari probe terakhir. */
    val probeResults: List<Pair<String, Boolean>>,
    val displayReadable: Boolean,
    val gameInstalled: Boolean,
    val gameModesCaptured: Boolean,
    /** Terbaru di depan, seperti ChangeJournal.history(). */
    val history: List<OperationRecord>
)

object TestPlan {

    val cases: List<TestCase> = listOf(
        TestCase(
            "detect.gpu", "GPU terdeteksi dan terklasifikasi", TestKind.AUTO,
            "Otomatis saat aplikasi dibuka."
        ),
        TestCase(
            "detect.gl", "Probe OpenGL ES berjalan", TestKind.AUTO,
            "Otomatis saat aplikasi dibuka."
        ),
        TestCase(
            "detect.vulkan", "Probe Vulkan berjalan", TestKind.AUTO,
            "Otomatis saat aplikasi dibuka."
        ),
        TestCase(
            "fallback.noshizuku", "Tanpa Shizuku: aplikasi jalan dan Apply ditahan dengan alasan",
            TestKind.AUTO,
            "Hentikan service Shizuku (atau uji di HP tanpa Shizuku), lalu buka aplikasi."
        ),
        TestCase(
            "shizuku.connect", "Shizuku: permission diberikan, service berjalan sebagai shell",
            TestKind.AUTO,
            "Tab Shizuku: Grant Permission, lalu Bind service."
        ),
        TestCase(
            "shizuku.probe", "Shizuku: probe read-only dan IWindowManager terbaca",
            TestKind.AUTO, "Tab Shizuku: Jalankan probe."
        ),
        TestCase(
            "shizuku.gamemode", "Output Game Mode Free Fire tertangkap", TestKind.AUTO,
            "Tab Shizuku: Jalankan probe dengan Free Fire terpasang."
        ),
        TestCase(
            "apply.revert", "Apply lalu Kembalikan: kembali dan terverifikasi", TestKind.AUTO,
            "Home: APPLY PROFILE, lalu tekan Kembalikan di dialog."
        ),
        TestCase(
            "apply.visual", "Tampilan Free Fire benar-benar lebih kotak-kotak", TestKind.MANUAL,
            "APPLY PROFILE, Pertahankan, buka Free Fire, bandingkan dengan sebelumnya."
        ),
        TestCase(
            "recovery.forceclose", "Force-close saat hitung mundur: dikembalikan otomatis",
            TestKind.AUTO,
            "APPLY PROFILE, force-close aplikasi sebelum 15 detik, buka lagi dengan Shizuku aktif."
        ),
        TestCase(
            "recovery.reboot", "Reboot dengan profil aktif: notifikasi dan pilihan pemulihan",
            TestKind.MANUAL,
            "Pertahankan sebuah profil, reboot, periksa notifikasi dan kartu Pemulihan di Home."
        ),
        TestCase(
            "drift.detect", "Perubahan dari luar aplikasi terdeteksi", TestKind.MANUAL,
            "Saat profil aktif, ubah resolusi lewat Settings atau 'adb shell wm size'. " +
                    "Status di Home harus menjadi 'Berubah di luar aplikasi'."
        ),
        TestCase(
            "restore.default", "RESTORE DEFAULT kembali ke kondisi asli dan terverifikasi",
            TestKind.AUTO, "Home: RESTORE DEFAULT saat ada profil aktif."
        )
    )

    private val CORE_PROBE = listOf("id", "wm size", "wm density")

    /** Uji manual yang butuh profil aktif, jadi butuh Shizuku untuk disiapkan. */
    private val MANUAL_NEEDS_SHIZUKU = setOf("apply.visual", "drift.detect")

    fun evaluate(evidence: TestEvidence, manual: Map<String, TestVerdict>): List<TestResult> =
        cases.map { case ->
            val (verdict, detail) = when (case.kind) {
                TestKind.MANUAL -> manualVerdict(case, evidence, manual)
                TestKind.AUTO -> autoVerdict(case.id, evidence)
            }
            TestResult(case, verdict, detail)
        }

    fun cell(evidence: TestEvidence): String =
        "${evidence.gpuFamily.label}, " +
                if (evidence.shizukuConnected) "dengan Shizuku" else "tanpa Shizuku"

    fun summary(results: List<TestResult>): Map<TestVerdict, Int> =
        TestVerdict.entries.associateWith { v -> results.count { it.verdict == v } }

    private fun manualVerdict(
        case: TestCase,
        evidence: TestEvidence,
        manual: Map<String, TestVerdict>
    ): Pair<TestVerdict, String> {
        manual[case.id]?.let { return it to "dinilai olehmu" }
        // recovery.reboot sengaja tidak ada di sini: uji itu justru diamati
        // setelah reboot, saat Shizuku mode ADB belum berjalan.
        return if (!evidence.shizukuConnected && case.id in MANUAL_NEEDS_SHIZUKU) {
            TestVerdict.NOT_APPLICABLE to "butuh Shizuku untuk menerapkan profil"
        } else {
            TestVerdict.PENDING to "belum dinilai"
        }
    }

    private fun autoVerdict(id: String, e: TestEvidence): Pair<TestVerdict, String> = when (id) {
        "detect.gpu" ->
            if (e.gpuFamily != GpuFamily.UNKNOWN) TestVerdict.PASS to e.gpuFamily.label
            else TestVerdict.FAIL to "GL_RENDERER dan Vulkan tidak terbaca"

        "detect.gl" ->
            if (e.glAvailable) TestVerdict.PASS to "context EGL berhasil dibuat"
            else TestVerdict.FAIL to "context EGL gagal dibuat"

        "detect.vulkan" -> when {
            !e.vulkanAvailable -> TestVerdict.NOT_APPLICABLE to "perangkat tidak melaporkan Vulkan"
            e.vulkanNativeProbe -> TestVerdict.PASS to "probe native berjalan"
            !e.nativeProbeExpected -> TestVerdict.PASS to "build tanpa probe native, data PackageManager dipakai"
            else -> TestVerdict.FAIL to "Vulkan ada tetapi probe native gagal"
        }

        "fallback.noshizuku" -> when {
            e.shizukuConnected -> TestVerdict.NOT_APPLICABLE to
                    "Shizuku sedang terhubung; hentikan service Shizuku untuk menguji ini"
            e.pixelMediumBlockedReason != null -> TestVerdict.PASS to
                    "Apply ditahan: ${e.pixelMediumBlockedReason}"
            else -> TestVerdict.FAIL to "Apply tidak ditahan padahal tidak ada backend privileged"
        }

        "shizuku.connect" -> when {
            !e.shizukuInstalled && !e.shizukuConnected -> TestVerdict.NOT_APPLICABLE to "Shizuku tidak terpasang"
            e.shizukuConnected && e.serviceUid == 2000 -> TestVerdict.PASS to "user service uid 2000 (shell)"
            e.shizukuConnected && e.serviceUid == 0 -> TestVerdict.NOT_APPLICABLE to
                    "Shizuku berjalan sebagai root; PixelRender sengaja hanya memakai mode shell"
            e.shizukuConnected && e.serviceUid > 0 -> TestVerdict.FAIL to
                    "user service berjalan sebagai uid ${e.serviceUid}, bukan shell"
            else -> TestVerdict.PENDING to "berikan permission lalu Bind service"
        }

        "shizuku.probe" -> when {
            e.probeResults.isEmpty() && !e.shizukuConnected ->
                TestVerdict.NOT_APPLICABLE to "butuh Shizuku"
            e.probeResults.isEmpty() -> TestVerdict.PENDING to "jalankan probe di tab Shizuku"
            else -> {
                val failed = CORE_PROBE.filter { core ->
                    e.probeResults.none { (cmd, ok) -> cmd == core && ok }
                }
                val okCount = e.probeResults.count { it.second }
                when {
                    failed.isEmpty() && e.displayReadable -> TestVerdict.PASS to
                            "$okCount/${e.probeResults.size} command sukses, IWindowManager terbaca"
                    failed.isNotEmpty() -> TestVerdict.FAIL to
                            "command inti gagal: ${failed.joinToString()}"
                    else -> TestVerdict.FAIL to "IWindowManager tidak terbaca"
                }
            }
        }

        "shizuku.gamemode" -> when {
            !e.gameInstalled -> TestVerdict.NOT_APPLICABLE to "Free Fire tidak terpasang"
            e.gameModesCaptured -> TestVerdict.PASS to "output list-modes/list-configs ada di laporan"
            !e.shizukuConnected -> TestVerdict.NOT_APPLICABLE to "butuh Shizuku"
            else -> TestVerdict.PENDING to "jalankan probe di tab Shizuku"
        }

        "apply.revert" -> fromHistory(e, OperationKind.REVERT)
        "recovery.forceclose" -> fromHistory(e, OperationKind.AUTO_REVERT)
        "restore.default" -> fromHistory(e, OperationKind.RESTORE)

        else -> TestVerdict.PENDING to "tidak dikenal"
    }

    /** Hasil terbaru yang dipakai: kalau masalah sudah diperbaiki lalu diuji ulang, itu yang dihitung. */
    private fun fromHistory(e: TestEvidence, kind: OperationKind): Pair<TestVerdict, String> {
        val latest = e.history.firstOrNull { it.kind == kind }
        return when {
            latest != null && latest.verified -> TestVerdict.PASS to
                    "terverifikasi: ${latest.actual}"
            latest != null -> TestVerdict.FAIL to latest.note.ifBlank { "tidak terverifikasi" }
            !e.shizukuConnected -> TestVerdict.NOT_APPLICABLE to "butuh Shizuku"
            else -> TestVerdict.PENDING to "belum ada catatan ${kind.label}"
        }
    }
}
