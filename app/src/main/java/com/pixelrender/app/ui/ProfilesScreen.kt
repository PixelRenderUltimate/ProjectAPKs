package com.pixelrender.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pixelrender.app.UiState
import com.pixelrender.app.graphics.ProfileId
import com.pixelrender.app.graphics.ResolvedSetting
import com.pixelrender.app.graphics.SettingOutcome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(
    state: UiState,
    onSelectProfile: (ProfileId) -> Unit,
    onCustomScale: (Float) -> Unit,
    onApply: () -> Unit
) {
    val resolved = state.resolvedProfile
    val isDefault = state.selectedProfile == ProfileId.DEFAULT

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(
                "Graphics Profiles",
                "Setiap profil dicocokkan dengan kemampuan nyata perangkat ini"
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileId.entries.forEach { id ->
                        FilterChip(
                            selected = state.selectedProfile == id,
                            onClick = { onSelectProfile(id) },
                            label = { Text(id.label) }
                        )
                    }
                }
                Text(state.profile.description, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.selectedProfile == ProfileId.CUSTOM) {
            item {
                SectionCard(
                    "Skala resolusi render",
                    "Dihitung dari ukuran panel dengan rasio aspek tetap"
                ) {
                    if (state.displayPlans.isEmpty()) {
                        Text(
                            "Ukuran panel belum terbaca. Bind service di tab Shizuku.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.displayPlans.filter { !it.isPanelNative }.forEach { plan ->
                                FilterChip(
                                    selected = state.customScale == plan.scale,
                                    onClick = { onCustomScale(plan.scale) },
                                    enabled = plan.allowed,
                                    label = { Text("${plan.percent}%") }
                                )
                            }
                        }
                        state.displayPlans.firstOrNull { it.scale == state.customScale }?.let { plan ->
                            InfoRow(
                                "Target",
                                if (plan.allowed) plan.label else plan.reason,
                                mono = plan.allowed
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                "Yang akan terjadi",
                "Dilihat sebelum Apply, bukan sesudahnya"
            ) {
                if (isDefault) {
                    Text(
                        state.pendingDisplay
                            ?.let { "Mengembalikan ke: ${it.describe()}" }
                            ?: "Perangkat sudah default. Tidak ada yang perlu dikembalikan.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "${resolved.applyCount} dari ${resolved.settings.size} bagian profil " +
                                "akan diterapkan",
                        style = MaterialTheme.typography.titleSmall
                    )
                    resolved.settings.forEach { ResolvedRow(it) }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onApply,
                    enabled = state.applyBlockedReason == null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isDefault) "RESTORE DEFAULT" else "APPLY PROFILE") }
                state.applyBlockedReason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard("Kenapa banyak baris \"Tidak ada API\"") {
                Text(
                    "Texture filtering, anisotropic, LOD, dan MSAA adalah state milik proses " +
                            "game sendiri. Tidak ada API Android untuk mengubahnya dari aplikasi " +
                            "lain, dengan atau tanpa Shizuku.\n\n" +
                            "Profil tetap mencantumkannya supaya terlihat jelas bagian mana dari " +
                            "konsep \"Pixel\" yang memang tidak bisa dicapai, alih-alih " +
                            "dihilangkan diam-diam. Seluruh efek visual yang benar-benar terjadi " +
                            "berasal dari baris resolusi render.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ResolvedRow(setting: ResolvedSetting) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${setting.parameter.label}: ${setting.intent}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            OutcomeChip(setting.outcome)
        }
        Text(
            setting.detail,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (setting.outcome == SettingOutcome.WILL_APPLY) FontFamily.Monospace
            else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OutcomeChip(outcome: SettingOutcome) {
    val (bg, fg) = when (outcome) {
        SettingOutcome.WILL_APPLY -> Color(0xFF1E4630) to Color(0xFF8FE4B4)
        SettingOutcome.BLOCKED -> Color(0xFF4A3D18) to Color(0xFFE8CE84)
        SettingOutcome.UNVERIFIED -> Color(0xFF2C333A) to Color(0xFFB3BCC4)
        SettingOutcome.NO_API -> Color(0xFF4A2724) to Color(0xFFF0A79C)
    }
    Text(
        outcome.label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
