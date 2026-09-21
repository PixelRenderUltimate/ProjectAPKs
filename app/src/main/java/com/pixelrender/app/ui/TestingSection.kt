package com.pixelrender.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pixelrender.app.UiState
import com.pixelrender.app.logging.Logger
import com.pixelrender.app.testing.TestKind
import com.pixelrender.app.testing.TestPlan
import com.pixelrender.app.testing.TestResult
import com.pixelrender.app.testing.TestVerdict

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestingSummaryCard(state: UiState, buildReport: () -> String) {
    val context = LocalContext.current
    val summary = TestPlan.summary(state.testResults)

    SectionCard(
        "Pengujian perangkat",
        "Sel matriks: ${TestPlan.cell(state.testEvidence)}"
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            summary.filter { it.value > 0 }.forEach { (verdict, count) ->
                VerdictChip(verdict, "${verdict.label} $count")
            }
        }
        Text(
            "Uji otomatis dinilai dari bukti yang benar-benar terbaca di perangkat ini. " +
                    "Uji manual kamu nilai sendiri di bawah. Kirim laporan dari tiap HP supaya " +
                    "hasil antar-GPU bisa dibandingkan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val report = buildReport()
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "PixelRender - laporan diagnostik")
                    .putExtra(Intent.EXTRA_TEXT, report)
                runCatching {
                    context.startActivity(Intent.createChooser(send, "Bagikan laporan"))
                }.onFailure {
                    Logger.e("Tidak bisa membagikan laporan", it.message ?: it.javaClass.simpleName)
                }
            }) { Text("Bagikan laporan") }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                if (clipboard == null) {
                    Logger.e("Clipboard tidak tersedia")
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("PixelRender", buildReport()))
                    Logger.ok("Laporan diagnostik disalin ke clipboard")
                }
            }) { Text("Salin") }
        }
        Text(
            "Laporan tidak berisi IMEI, nomor seri, akun, lokasi, atau daftar aplikasi.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TestResultCard(result: TestResult, onManualVerdict: (String, TestVerdict?) -> Unit) {
    SectionCard(result.case.title, result.case.kind.label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerdictChip(result.verdict, result.verdict.label)
            Text(
                "  ${result.evidence}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "Cara: ${result.case.howTo}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (result.case.kind == TestKind.MANUAL) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onManualVerdict(result.case.id, TestVerdict.PASS) }) { Text("Lulus") }
                TextButton(onClick = { onManualVerdict(result.case.id, TestVerdict.FAIL) }) { Text("Gagal") }
                TextButton(onClick = { onManualVerdict(result.case.id, TestVerdict.SKIPPED) }) { Text("Lewati") }
                TextButton(onClick = { onManualVerdict(result.case.id, null) }) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun VerdictChip(verdict: TestVerdict, text: String) {
    val (bg, fg) = when (verdict) {
        TestVerdict.PASS -> Color(0xFF1E4630) to Color(0xFF8FE4B4)
        TestVerdict.FAIL -> Color(0xFF4A2724) to Color(0xFFF0A79C)
        TestVerdict.PENDING -> Color(0xFF4A3D18) to Color(0xFFE8CE84)
        TestVerdict.NOT_APPLICABLE, TestVerdict.SKIPPED -> Color(0xFF2C333A) to Color(0xFFB3BCC4)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
