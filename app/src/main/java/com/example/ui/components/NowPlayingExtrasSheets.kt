package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.Track
import com.example.data.formatDuration
import com.example.data.lyrics.LyricsUiState
import com.example.playback.AudioOutputDevice
import com.example.playback.AudioOutputRouteType
import com.example.playback.AudioOutputState
import com.example.playback.PlaybackProgress
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val PLAYBACK_SPEED_OPTIONS: List<Float> = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

fun audioOutputIconFor(routeType: AudioOutputRouteType): ImageVector = when (routeType) {
    AudioOutputRouteType.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    AudioOutputRouteType.WIRED_HEADSET -> Icons.Rounded.Headphones
    AudioOutputRouteType.USB_AUDIO -> Icons.Rounded.Usb
    AudioOutputRouteType.EXTERNAL -> Icons.Rounded.SpeakerGroup
    AudioOutputRouteType.SPEAKER -> Icons.Rounded.Speaker
}

/**
 * Lightweight Material 3 fallback modal sheet for Audio Output / Device Switching when
 * Android's native System Media Output Switcher dialog is unavailable on a given ROM.
 * Lists connected [AudioOutputDevice] routes with active route status and a quick jump to
 * system Sound / Bluetooth settings.
 */
@Composable
fun AudioOutputSheetContent(
    outputState: AudioOutputState,
    onOpenSoundSettings: (openBluetooth: Boolean) -> Unit
) {
    val sheetBg = MaterialTheme.colorScheme.surfaceContainerLow
    val cardBg = MaterialTheme.colorScheme.surfaceContainer.opaqueOver(sheetBg, 0.96f)
    val activeBg = MaterialTheme.colorScheme.primaryContainer.opaqueOver(sheetBg, 0.94f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.opaqueOver(sheetBg, 0.85f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .testTag("audio_output_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = audioOutputIconFor(outputState.activeRoute),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.audio_output_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.audio_output_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            outputState.devices.forEachIndexed { index, device ->
                val isBluetooth = device.routeType == AudioOutputRouteType.BLUETOOTH
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (device.isActive) activeBg else cardBg,
                    border = if (device.isActive) null else BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenSoundSettings(isBluetooth) }
                        .testTag("audio_output_device_${index}_${device.routeType.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = audioOutputIconFor(device.routeType),
                            contentDescription = null,
                            tint = if (device.isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (device.isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (device.isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    if (device.isActive) R.string.audio_output_active else R.string.audio_output_connected
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (device.isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (device.isActive) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.audio_output_active),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        FilledTonalButton(
            onClick = { onOpenSoundSettings(false) },
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("audio_output_system_settings_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.audio_output_open_sound_settings),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Offline Lyrics modal sheet showing embedded tags or matching `.lrc` sidecar files
 * loaded strictly on Dispatchers.IO, with synced line highlighting and clean empty fallback.
 */
@Composable
fun LyricsSheetContent(
    track: Track,
    lyricsState: LyricsUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    onSeekToMs: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .testTag("lyrics_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (lyricsState is LyricsUiState.Available && lyricsState.lyrics.sourceLabel.isNotBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = lyricsState.lyrics.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        when (lyricsState) {
            is LyricsUiState.Idle,
            is LyricsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }

            is LyricsUiState.Empty -> {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_empty_state")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = stringResource(R.string.lyrics_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.lyrics_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            is LyricsUiState.Available -> {
                val lyrics = lyricsState.lyrics
                val listState = rememberLazyListState()
                val activeLineIndexFlow = remember(progressFlow, lyrics) {
                    progressFlow.map { progress ->
                        if (!lyrics.isSynced) {
                            -1
                        } else {
                            val pos = progress.positionMs
                            var active = -1
                            for (i in lyrics.lines.indices) {
                                val ts = lyrics.lines[i].timestampMs ?: continue
                                if (ts <= pos + 150L) active = i else break
                            }
                            active
                        }
                    }.distinctUntilChanged()
                }
                val activeIndex by activeLineIndexFlow.collectAsStateWithLifecycle(initialValue = -1)

                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0 && activeIndex < lyrics.lines.size) {
                        listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 420.dp)
                        .testTag("lyrics_lines_list"),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = lyrics.lines,
                        key = { idx, line -> "lyric_${idx}_${line.timestampMs ?: -1L}" },
                        contentType = { _, _ -> "lyric_line" }
                    ) { idx, line ->
                        val isActive = lyrics.isSynced && idx == activeIndex
                        val clickableModifier = if (line.timestampMs != null) {
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSeekToMs(line.timestampMs) }
                        } else {
                            Modifier
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(clickableModifier)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (line.timestampMs != null) {
                                Text(
                                    text = formatDuration(line.timestampMs),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Text(
                                text = line.text,
                                style = if (isActive) {
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else if (!lyrics.isSynced) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatSpeedLabel(speed: Float): String {
    val formatted = "%.2f".format(java.util.Locale.US, speed).trimEnd('0').trimEnd('.')
    return "${formatted}x"
}
