package com.pixelrender.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pixelrender.app.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.pixelrender.app.graphics.BackendId
import com.pixelrender.app.graphics.Capability
import com.pixelrender.app.graphics.Support
import com.pixelrender.app.logging.Logger
import com.pixelrender.app.shizuku.CommandPolicy
import com.pixelrender.app.testing.TestVerdict
import com.pixelrender.app.shizuku.ShizukuStatus
import com.pixelrender.app.shizuku.UserServiceStatus

internal val ScreenPadding = 16.dp

@Composable
fun DeviceInfoScreen(
    state: UiState,
    onManualVerdict: (String, TestVerdict?) -> Unit,
    buildReport: () -> String
) {
    val device = state.device
    val gl = state.gl
    val vk = state.vulkan

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TestingSummaryCard(state, buildReport) }
        items(state.testResults) { result -> TestResultCard(result, onManualVerdict) }
        item {
            val gpu = state.gpuIdentity
            SectionCard("GPU") {
                InfoRow("Keluarga", gpu.family.label)
                InfoRow("GLES lewat ANGLE", if (gpu.viaAngle) "ya" else "tidak")
                Text(
                    gpu.evidence,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SectionCard("Perangkat") {
                InfoRow("Manufacturer", device?.manufacturer ?: "-")
                InfoRow("Brand", device?.brand ?: "-")
                InfoRow("Model", device?.model ?: "-")
                InfoRow("Device", device?.device ?: "-")
                InfoRow("Board", device?.board ?: "-")
                InfoRow("Hardware", device?.hardware ?: "-")
                InfoRow("SoC", device?.let { "${it.socManufacturer} ${it.socModel}" } ?: "-")
                InfoRow("RAM", device?.ramGb ?: "-")
            }
        }
        item {
            SectionCard("Sistem") {
                InfoRow("Android", device?.androidRelease ?: "-")
                InfoRow("SDK", device?.sdkInt?.toString() ?: "-")
                InfoRow("Security patch", device?.securityPatch ?: "-")
                InfoRow("Build type", device?.buildType ?: "-")
                InfoRow("ABI utama", device?.primaryAbi ?: "-", mono = true)
                InfoRow("Semua ABI", device?.abis?.joinToString(", ") ?: "-", mono = true)
            }
        }
        item {
            SectionCard("Display") {
                InfoRow("Resolusi", device?.resolution ?: "-")
                InfoRow("Density", device?.let { "${it.densityDpi} dpi (${it.density}x)" } ?: "-")
                InfoRow("Refresh rate", device?.let { "${it.refreshRate} Hz" } ?: "-")
                InfoRow(
                    "Mode tersedia",
                    device?.supportedRefreshRates?.joinToString(" / ") { "${it.toInt()}Hz" } ?: "-"
                )
            }
        }
        item {
            SectionCard("OpenGL ES", gl?.error?.let { "Probe gagal: $it" }) {
                InfoRow("Vendor", gl?.vendor ?: "-")
                InfoRow("Renderer", gl?.renderer ?: "-")
                InfoRow("Versi", gl?.versionString ?: "-", mono = true)
                InfoRow("GLSL", gl?.glslVersion ?: "-", mono = true)
                InfoRow("Max texture size", gl?.maxTextureSize?.toString() ?: "-")
                InfoRow("GL_MAX_SAMPLES", gl?.maxSamples?.toString() ?: "-")
                InfoRow(
                    "Anisotropic",
                    gl?.let {
                        if (it.anisotropySupported) "ya, max ${it.maxAnisotropy.toInt()}x" else "tidak"
                    } ?: "-"
                )
                InfoRow("Jumlah ekstensi", gl?.extensions?.size?.toString() ?: "-")
            }
        }
        item {
            SectionCard(
                "Vulkan",
                if (vk?.nativeProbeUsed == false) "Probe native tidak jalan: ${vk.reason}" else null
            ) {
                if (vk?.available != true) {
                    Text(
                        "Vulkan tidak tersedia. ${vk?.reason ?: ""}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val dev = vk.primaryDevice
                    InfoRow("Loader API", vk.loaderApiVersion)
                    InfoRow("System feature", vk.systemFeatureVersion.ifBlank { "-" })
                    InfoRow("Hardware level", vk.hardwareLevel.toString())
                    InfoRow("Device", dev?.name ?: "-")
                    InfoRow("Device API", dev?.apiVersion ?: "-")
                    InfoRow("Vendor", dev?.vendorName ?: "-")
                    InfoRow("Tipe", dev?.type ?: "-")
                    InfoRow(
                        "Sample counts",
                        dev?.colorSampleCounts?.joinToString("/") { "${it}x" } ?: "-"
                    )
                    InfoRow(
                        "Sampler anisotropy",
                        dev?.let {
                            if (it.samplerAnisotropy) "ya, max ${it.maxSamplerAnisotropy}x" else "tidak"
                        } ?: "-"
                    )
                    InfoRow("Instance extensions", vk.instanceExtensions.size.toString())
                    InfoRow("Device extensions", (dev?.deviceExtensions?.size ?: 0).toString())
                }
            }
        }
        val exts = vk?.primaryDevice?.deviceExtensions.orEmpty()
        if (exts.isNotEmpty()) {
            item {
                SectionCard("Vulkan device extensions", "${exts.size} ekstensi") {
                    Text(
                        exts.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        val glExts = gl?.extensions.orEmpty()
        if (glExts.isNotEmpty()) {
            item {
                SectionCard("OpenGL ES extensions", "${glExts.size} ekstensi") {
                    Text(
                        glExts.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapabilityCard(cap: Capability) {
    SectionCard(cap.name) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("Device", cap.deviceSupport)
            StatusChip("Control", cap.externalControl)
        }
        cap.controlledBy?.let { backend ->
            Text(
                "Dipegang backend ${backend.displayName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

        // Pendapat tiap backend ditampilkan utuh, bukan hanya kesimpulannya.
        cap.verdicts.forEach { verdict ->
            Text(
                "${verdict.backend.displayName} -> device ${verdict.deviceSupport}, " +
                        "control ${verdict.externalControl}",
                style = MaterialTheme.typography.labelMedium,
                color = if (verdict.externalControl == Support.SUPPORTED)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "  ${verdict.evidence}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        Text(cap.reason, style = MaterialTheme.typography.bodySmall)
        cap.requires?.let {
            Text(
                "Butuh: $it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AdvancedScreen(state: UiState, onSelectBackend: (BackendId) -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(
                "Backend",
                "Hanya backend yang benar-benar tersedia yang bisa dipilih"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.selectableBackends.forEach { id ->
                        FilterChip(
                            selected = state.selectedBackend == id,
                            onClick = { onSelectBackend(id) },
                            label = { Text(id.displayName) }
                        )
                    }
                }
                InfoRow("Backend aktif", state.activeBackend?.displayName ?: "-")
                Text(
                    "Auto memilih backend tersedia dengan prioritas tertinggi. Kalau " +
                            "backend yang dipilih manual tidak tersedia, aplikasi kembali ke " +
                            "Auto dan mencatatnya di Logs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(state.backends) { status ->
            SectionCard(status.name, status.backend.role.label) {
                InfoRow("Tersedia", if (status.available) "ya" else "tidak")
                InfoRow("Prioritas", status.backend.priority.toString())
                Text(
                    status.availability.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.available) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                val owned = state.capabilities.filter { it.controlledBy == status.id }
                if (owned.isNotEmpty()) {
                    Text(
                        "Memegang: " + owned.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (state.shortcuts.isNotEmpty()) {
            item {
                SectionCard(
                    "Pintasan Settings sistem",
                    "Dibuka dan diubah olehmu sendiri, bukan oleh aplikasi"
                ) {
                    state.shortcuts.forEach { shortcut ->
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(shortcut.action)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure {
                                    Logger.e(
                                        "Tidak bisa membuka ${shortcut.label}",
                                        it.message ?: it.javaClass.simpleName
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(shortcut.label) }
                        Text(
                            shortcut.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            SectionCard("Capability matrix") {
                Text(
                    "Device = apakah GPU perangkat ini mendukung parameter tersebut.\n" +
                            "Control = apakah ada API Android yang sah untuk mengubahnya pada " +
                            "aplikasi lain, misalnya Free Fire.\n\n" +
                            "Device SUPPORTED + Control UNSUPPORTED adalah hasil yang normal, " +
                            "bukan bug. Pendapat tiap backend ditampilkan utuh di setiap kartu.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(state.capabilities) { cap -> CapabilityCard(cap) }
        if (state.capabilities.isEmpty()) {
            item { Text("Menunggu hasil deteksi...") }
        }
    }
}

@Composable
fun ShizukuScreen(
    state: UiState,
    onGrant: () -> Unit,
    onRefresh: () -> Unit,
    onBind: () -> Unit,
    onUnbind: () -> Unit,
    onProbe: () -> Unit,
    onRestore: () -> Unit,
    onClearState: () -> Unit,
    onClearHistory: () -> Unit
) {
    val s = state.shizuku
    val timeFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }
    val svc = state.userService

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Koneksi Shizuku", "Backend privileged opsional") {
                InfoRow("Status", s.status.name)
                InfoRow("Service berjalan", if (s.running) "ya" else "tidak")
                InfoRow("Permission", if (s.connected) "granted" else "belum")
                InfoRow("Versi", if (s.version >= 0) s.version.toString() else "-")
                InfoRow(
                    "UID",
                    if (s.uid >= 0) "${s.uid}${if (s.isShellUid) " (shell)" else ""}" else "-"
                )
                if (s.suiDetected) InfoRow("Varian", "Sui")
                Text(
                    when (s.status) {
                        ShizukuStatus.NOT_INSTALLED ->
                            "Shizuku belum terpasang. Aplikasi tetap berjalan penuh dengan " +
                                    "backend Standard, hanya saja kontrol resolusi sistem tidak tersedia."
                        ShizukuStatus.INSTALLED_NOT_RUNNING ->
                            "Shizuku terpasang tetapi service belum jalan. Mulai service dari " +
                                    "aplikasi Shizuku lewat Wireless debugging atau ADB."
                        ShizukuStatus.PERMISSION_DENIED ->
                            "Permission pernah ditolak. Berikan manual dari aplikasi Shizuku."
                        ShizukuStatus.RUNNING_NO_PERMISSION ->
                            "Service berjalan. Berikan permission untuk melanjutkan."
                        ShizukuStatus.CONNECTED ->
                            "Terhubung. Lanjutkan dengan bind user service di bawah."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onGrant, enabled = s.running && !s.connected) {
                        Text("Grant Permission")
                    }
                    OutlinedButton(onClick = onRefresh) { Text("Cek ulang") }
                }
            }
        }

        item {
            SectionCard(
                "User service",
                "Proses terpisah dengan UID shell. Tidak didaftarkan di manifest."
            ) {
                InfoRow("Status", svc.status.name)
                InfoRow("UID service", if (svc.serviceUid >= 0) svc.serviceUid.toString() else "-")
                InfoRow(
                    "Policy version",
                    if (svc.policyVersion >= 0) svc.policyVersion.toString() else "-"
                )
                if (svc.message.isNotBlank()) {
                    Text(
                        svc.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (svc.status == UserServiceStatus.FAILED)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBind, enabled = s.connected && !svc.bound) {
                        Text("Bind service")
                    }
                    OutlinedButton(onClick = onUnbind, enabled = svc.bound) { Text("Unbind") }
                }
            }
        }

        item {
            SectionCard(
                "Probe read-only",
                "PHASE 2 hanya membaca. Tidak ada command yang mengubah perangkat."
            ) {
                if (state.probing) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(
                    onClick = onProbe,
                    enabled = svc.bound && !state.probing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Jalankan probe") }

                val results = state.probe?.results.orEmpty()
                if (results.isNotEmpty()) {
                    val okCount = results.count { it.ok }
                    Text(
                        "$okCount dari ${results.size} command sukses",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    results.forEach { r ->
                        Text(
                            "\$ ${r.command}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (r.ok) Color(0xFF8FE4B4) else Color(0xFFF0A79C)
                        )
                        Text(
                            "  " + (if (r.ok) r.output.ifBlank { "(kosong)" } else r.error),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        state.probe?.display?.let { display ->
            item {
                SectionCard(
                    "IWindowManager",
                    "Dibaca lewat binder, bukan parsing teks"
                ) {
                    if (!display.ok) {
                        Text(
                            "Gagal dibaca: ${display.error}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        InfoRow("Ukuran fisik panel", display.physical, mono = true)
                        InfoRow("Ukuran aktif", display.current, mono = true)
                        InfoRow("Density fisik", "${display.initialDensity} dpi")
                        InfoRow("Density aktif", "${display.baseDensity} dpi")
                        InfoRow("Override ukuran", if (display.sizeOverridden) "ya" else "tidak")
                        InfoRow("Override density", if (display.densityOverridden) "ya" else "tidak")
                        if (display.anyOverride) {
                            Text(
                                "Perangkat sudah punya override aktif sebelum aplikasi ini " +
                                        "menyentuh apa pun. Nilai itulah yang disimpan sebagai " +
                                        "baseline, bukan resolusi panel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        val overlay = state.probe?.gameOverlay.orEmpty()
        if (overlay.isNotEmpty()) {
            item {
                SectionCard(
                    "Game Mode intervention",
                    "Menentukan apakah render scale per-game mungkin di perangkat ini"
                ) {
                    overlay.forEach { (pkg, value) -> InfoRow(pkg, value, mono = true) }
                }
            }
        }

        item {
            SectionCard(
                "Baseline dan restore",
                "Target restore dicatat ke disk sebelum penulisan pertama"
            ) {
                if (state.baselines.isEmpty()) {
                    Text(
                        "Belum ada baseline. Jalankan probe terlebih dulu.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    state.baselines.forEach { InfoRow(it.key, it.originalValue, mono = true) }
                }
                InfoRow("Perubahan tertunda", state.pendingChanges.size.toString())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRestore) { Text("Restore") }
                    OutlinedButton(onClick = onClearState) { Text("Hapus baseline") }
                }
                Text(
                    "Restore mengembalikan ke state tepat sebelum PixelRender menulis pertama " +
                            "kali, termasuk override milikmu sendiri kalau sebelumnya sudah ada, " +
                            "lalu membaca ulang untuk memverifikasi. 'Hapus baseline' tidak " +
                            "menghapus target restore yang masih tertunda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                "Riwayat & laporan verifikasi",
                "Setiap apply dan restore dibaca ulang dari perangkat"
            ) {
                if (state.history.isEmpty()) {
                    Text("Belum ada perubahan.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.history.forEach { r ->
                        Text(
                            "${timeFormat.format(Date(r.at))}  ${r.kind.label}: ${r.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (r.verified) Color(0xFF8FE4B4) else Color(0xFFF0A79C)
                        )
                        Text(
                            "  diharapkan: ${r.expected}\n  terbaca   : ${r.actual}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "  " + (if (r.verified) "TERVERIFIKASI" else "TIDAK TERVERIFIKASI") +
                                    if (r.note.isNotBlank()) " - ${r.note}" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = onClearHistory) { Text("Hapus riwayat") }
                }
            }
        }

        item {
            SectionCard(
                "Allowlist command",
                "Hanya ini yang bisa dijalankan. Divalidasi dua kali, di aplikasi dan di service."
            ) {
                CommandPolicy.allowlist().forEach { (cmd, desc) ->
                    Text(
                        cmd,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "  $desc",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Command dikirim sebagai argv terpisah ke ProcessBuilder, tidak pernah " +
                            "lewat shell, jadi tidak ada ruang untuk interpolasi. Command yang " +
                            "menulis ditolak selama PHASE < 4.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            SectionCard("Batasan yang dipegang proyek ini") {
                Text(
                    "Shizuku dipakai sebagai UID shell, bukan root. Shizuku tidak memberi " +
                            "akses ke memori proses game, tidak bisa mengubah state GL/Vulkan " +
                            "milik game, dan tidak dipakai untuk menjalankan perintah root.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun LogScreen(entries: List<Logger.Entry>, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(ScreenPadding)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${entries.size} baris", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onClear) { Text("Bersihkan") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(entries) { entry ->
                val color = when (entry.level) {
                    Logger.Level.OK -> Color(0xFF8FE4B4)
                    Logger.Level.WARN -> Color(0xFFE8CE84)
                    Logger.Level.ERROR -> Color(0xFFF0A79C)
                    Logger.Level.INFO -> MaterialTheme.colorScheme.onSurface
                }
                Column {
                    Text(
                        "[${entry.time}] ${entry.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = color
                    )
                    entry.detail?.let {
                        Text(
                            "          $it",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("PixelRender", "PHASE 1-7 - sampai pengujian lintas perangkat") {
                Text(
                    "Satu-satunya yang ditulis aplikasi ini adalah ukuran dan density " +
                            "display lewat IWindowManager. Target restore dan status konfirmasi " +
                            "dicatat ke disk lebih dulu, jadi perubahan yang tidak dikonfirmasi " +
                            "tetap dikembalikan walaupun aplikasi sempat mati. Semua command " +
                            "shell tetap hanya bersifat baca.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            SectionCard("Batasan Android yang perlu diketahui") {
                Text(
                    "Texture filtering, anisotropic filtering, LOD bias, dan MSAA adalah state " +
                            "per-proses di OpenGL ES dan Vulkan. Nilainya di-set oleh game " +
                            "sendiri saat membuat sampler dan render pass. Android tidak " +
                            "menyediakan API publik maupun binder shell untuk mengubahnya dari " +
                            "aplikasi lain, baik dengan Shizuku maupun tanpa.\n\n" +
                            "Yang tersisa sebagai jalur sah: menurunkan resolusi dan density " +
                            "display seluruh sistem lewat WRITE_SECURE_SETTINGS. Itu menurunkan " +
                            "jumlah piksel yang dirender game, dan hasilnya memang terlihat " +
                            "lebih kasar.\n\n" +
                            "Aplikasi ini tidak akan pernah melakukan injeksi, hook, memory " +
                            "editing, atau modifikasi file game.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
