package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.player.EqualizerPresets
import com.example.player.EqualizerSettings
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyGreenBright

import com.example.ui.theme.LocalizedStrings

@Composable
fun EqualizerDialog(
    currentSettings: EqualizerSettings,
    strings: LocalizedStrings? = null,
    onSaveSettings: (EqualizerSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var b60 by remember { mutableFloatStateOf(currentSettings.band60Hz) }
    var b230 by remember { mutableFloatStateOf(currentSettings.band230Hz) }
    var b910 by remember { mutableFloatStateOf(currentSettings.band910Hz) }
    var b3600 by remember { mutableFloatStateOf(currentSettings.band3600Hz) }
    var b14000 by remember { mutableFloatStateOf(currentSettings.band14000Hz) }
    var bassBoost by remember { mutableFloatStateOf(currentSettings.bassBoost) }
    var virtualizer by remember { mutableFloatStateOf(currentSettings.virtualizer3D) }
    var selectedPresetName by remember { mutableStateOf(currentSettings.presetName) }
    var enabled by remember { mutableStateOf(currentSettings.isEnabled) }

    val dialogTitle = strings?.eqTitle ?: "Equalizer FX Settings"
    val cancelText = strings?.eqCancel ?: "Cancel"
    val applyText = strings?.eqApply ?: "Apply Settings"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("equalizer_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dialogTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = SpotifyGreen)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Presets Carousel
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EqualizerPresets.ALL_PRESETS) { preset ->
                        val isSelected = selectedPresetName == preset.presetName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) SpotifyGreen else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    selectedPresetName = preset.presetName
                                    b60 = preset.band60Hz
                                    b230 = preset.band230Hz
                                    b910 = preset.band910Hz
                                    b3600 = preset.band3600Hz
                                    b14000 = preset.band14000Hz
                                    bassBoost = preset.bassBoost
                                    virtualizer = preset.virtualizer3D
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset.presetName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5 Bands Sliders
                val bands = listOf(
                    "60 Hz" to b60,
                    "230 Hz" to b230,
                    "910 Hz" to b910,
                    "3.6 kHz" to b3600,
                    "14 kHz" to b14000
                )

                bands.forEachIndexed { index, (label, valState) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp)
                        )
                        Slider(
                            value = valState,
                            onValueChange = { newVal ->
                                selectedPresetName = "Custom"
                                when (index) {
                                    0 -> b60 = newVal
                                    1 -> b230 = newVal
                                    2 -> b910 = newVal
                                    3 -> b3600 = newVal
                                    4 -> b14000 = newVal
                                }
                            },
                            valueRange = -10f..10f,
                            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${valState.toInt()} dB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bass Boost & Virtualizer
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Bass Boost: ${bassBoost.toInt()}%", style = MaterialTheme.typography.labelSmall, color = SpotifyGreenBright)
                        Slider(
                            value = bassBoost,
                            onValueChange = { bassBoost = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "3D Surround: ${virtualizer.toInt()}%", style = MaterialTheme.typography.labelSmall, color = SpotifyGreenBright)
                        Slider(
                            value = virtualizer,
                            onValueChange = { virtualizer = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(cancelText, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newSettings = EqualizerSettings(
                                isEnabled = enabled,
                                presetName = selectedPresetName,
                                band60Hz = b60,
                                band230Hz = b230,
                                band910Hz = b910,
                                band3600Hz = b3600,
                                band14000Hz = b14000,
                                bassBoost = bassBoost,
                                virtualizer3D = virtualizer
                            )
                            onSaveSettings(newSettings)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                    ) {
                        Text(applyText, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
