package com.pixelrender.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pixelrender.app.UiState
import com.pixelrender.app.graphics.ProfileId
import com.pixelrender.app.graphics.ProfileState
import com.pixelrender.app.logging.Logger
import com.pixelrender.app.state.RecoveryState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: UiState,
    onSelectGame: (String) -> Unit,
    onOpenProfiles: () -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onRescan: () -> Unit,
    onRevertNow: () -> Unit,
    onAcknowledgeReboot: () -> Unit
) {
    val context = LocalContext.current
    val resolved = state.resolvedProfile

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.scanning || state.applying) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        if (state.recovery != RecoveryState.NONE) {
            item { RecoveryCard(state, onRevertNow, onAcknowledgeReboot, onRestore) }
        }

        item {
            SectionCard("PixelRender") {
                Text(
                    "Selected Game",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.installedGames.isEmpty()) {
                    Text(
                        "Free Fire dan Free Fire MAX tidak terpasang",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.installedGames.forEach { game ->
                            FilterChip(
                                selected = state.selectedGame == game.packageName,
                                onClick = { onSelectGame(game.packageName) },
                                label = { Text(game.label) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                InfoRow("Current Profile", state.profile.id.label)
                InfoRow(
                    "Aktif di perangkat",
                    state.activeProfile?.let { "${it.id.label} (${it.target})" } ?: "Default"
                )
                InfoRow("Status", state.profileState.label)
                Text(
                    statusDetail(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(state.profileState)
                )
                if (state.profile.id != ProfileId.DEFAULT) {
                    Text(
                        "${resolved.applyCount} dari ${resolved.settings.size} bagian profil " +
                                "bisa diterapkan di perangkat ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenProfiles) { Text("Ganti atau lihat rincian profil") }

                Button(
                    onClick = onApply,
                    enabled = state.applyBlockedReason == null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("APPLY PROFILE") }
                state.applyBlockedReason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onRestore,
                    enabled = state.pendingDisplay != null && !state.applying,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("RESTORE DEFAULT") }

                state.selectedGame?.let { pkg ->
                    val label = state.games.firstOrNull { it.packageName == pkg }?.label ?: pkg
                    OutlinedButton(
                        onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                            if (launch == null) {
                                Logger.e("Tidak bisa membuka $label", "Launch intent tidak ditemukan")
                            } else {
                                runCatching {
                                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onSuccess {
                                    Logger.i("Membuka $label", pkg)
                                }.onFailure {
                                    Logger.e("Tidak bisa membuka $label", it.message ?: it.javaClass.simpleName)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Buka $label") }
                }

                Text(
                    "Profil berlaku ke seluruh sistem, bukan hanya game yang dipilih. " +
                            "Pilihan game dipakai untuk tombol Buka.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard("Perangkat") {
                InfoRow("Perangkat", state.device?.let { "${it.manufacturer} ${it.model}" } ?: "-")
                InfoRow("GPU", state.gl?.renderer.orEmpty().ifBlank { "-" })
                InfoRow("Graphics API", state.vulkan?.let {
                    if (it.available) "Vulkan ${it.primaryDevice?.apiVersion ?: it.loaderApiVersion}"
                    else "Vulkan tidak tersedia"
                } ?: "-")
                InfoRow("OpenGL ES", state.gl?.esVersion ?: "-")
                InfoRow("Shizuku", state.shizuku.status.name)
                InfoRow("Backend aktif", state.activeBackend?.displayName ?: "-")
            }
        }

        item {
            OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
                Text("Pindai ulang")
            }
        }
    }
}

private fun statusDetail(state: UiState): String = when (state.profileState) {
    ProfileState.NOT_READY -> state.applyBlockedReason ?: "-"
    ProfileState.READY -> state.resolvedProfile.plan
        ?.let { "Siap menerapkan ${state.profile.id.label}: ${it.label}" }
        ?: "Siap"
    ProfileState.AWAITING_CONFIRMATION ->
        "Konfirmasi di dialog. Kalau tidak, resolusi kembali otomatis."
    ProfileState.ACTIVE -> "Terverifikasi: display aktif sama dengan target profil."
    ProfileState.ACTIVE_UNVERIFIED ->
        "Tercatat aktif, tetapi belum dibaca ulang. Bind service di tab Shizuku untuk memverifikasi."
    ProfileState.DRIFTED ->
        "Display aktif berbeda dari target profil, kemungkinan diubah lewat Settings atau " +
                "'wm size'. RESTORE DEFAULT tetap aman dipakai."
}

@Composable
private fun statusColor(profileState: ProfileState): Color = when (profileState) {
    ProfileState.ACTIVE -> Color(0xFF8FE4B4)
    ProfileState.READY -> MaterialTheme.colorScheme.primary
    ProfileState.AWAITING_CONFIRMATION,
    ProfileState.DRIFTED -> Color(0xFFE8CE84)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun RecoveryCard(
    state: UiState,
    onRevertNow: () -> Unit,
    onAcknowledgeReboot: () -> Unit,
    onRestore: () -> Unit
) {
    val active = state.activeProfile
    when (state.recovery) {
        RecoveryState.NONE -> Unit

        RecoveryState.UNCONFIRMED -> SectionCard(
            "Pemulihan",
            "Perubahan terakhir tidak pernah dikonfirmasi"
        ) {
            Text(
                "Aplikasi atau perangkat berhenti sebelum kamu memilih Pertahankan. " +
                        "Sesuai janji konfirmasi, perubahan ini dikembalikan otomatis begitu " +
                        "user service Shizuku terhubung.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.userService.bound) {
                Button(
                    onClick = onRevertNow,
                    enabled = !state.applying,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kembalikan sekarang") }
            } else {
                Text(shizukuHint(state), style = MaterialTheme.typography.bodySmall)
            }
            EmergencyCommands()
        }

        RecoveryState.REBOOTED -> SectionCard("Pemulihan", "Perangkat sudah reboot") {
            Text(
                "${active?.id?.label ?: "Profil"} (${active?.target ?: "-"}) diterapkan " +
                        "sebelum reboot, dan override resolusi bertahan setelah reboot. " +
                        "Tetap dipakai?",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAcknowledgeReboot) { Text("Pertahankan") }
                OutlinedButton(
                    onClick = onRestore,
                    enabled = state.userService.bound && !state.applying
                ) { Text("Kembalikan") }
            }
            if (!state.userService.bound) {
                Text(shizukuHint(state), style = MaterialTheme.typography.bodySmall)
            }
        }

        RecoveryState.SHIZUKU_LOST -> SectionCard("Pemulihan", "Shizuku tidak berjalan") {
            Text(
                "Resolusi masih diubah oleh PixelRender" +
                        (active?.let { " (${it.target})" } ?: "") +
                        ", tetapi Shizuku tidak berjalan sehingga aplikasi belum bisa " +
                        "mengembalikannya. Jalankan ulang Shizuku lewat Wireless debugging, " +
                        "atau gunakan adb:",
                style = MaterialTheme.typography.bodyMedium
            )
            EmergencyCommands()
        }
    }
}

private fun shizukuHint(state: UiState): String = when {
    !state.shizuku.running -> "Jalankan Shizuku lewat Wireless debugging dulu."
    !state.shizuku.connected -> "Berikan permission Shizuku di tab Shizuku."
    else -> "Menghubungkan user service..."
}

@Composable
private fun EmergencyCommands() {
    Text(
        "adb shell wm size reset\nadb shell wm density reset",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        "Perintah ini selalu membersihkan override. Kalau sebelum PixelRender kamu sudah " +
                "punya override sendiri, nilainya perlu di-set ulang manual.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
