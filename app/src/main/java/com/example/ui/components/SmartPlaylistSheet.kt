package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.StableMap
import com.example.data.Track
import com.example.data.intelligence.MusicIntelligenceOutput
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.MonoFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

private data class MoodOption(val id: String?, val label: String)
private data class EnergyOption(val id: String?, val label: String)

private val SMART_MOOD_OPTIONS = listOf(
    MoodOption(null, "Auto"),
    MoodOption("calm", "Calm"),
    MoodOption("focused", "Focus"),
    MoodOption("warm", "Warm"),
    MoodOption("late_night", "Late Night"),
    MoodOption("energetic", "Energetic"),
    MoodOption("organic", "Organic"),
    MoodOption("uplifting", "Uplifting")
)

private val SMART_ENERGY_OPTIONS = listOf(
    EnergyOption(null, "Any Energy"),
    EnergyOption("low", "Low"),
    EnergyOption("medium", "Medium"),
    EnergyOption("high", "High")
)

/**
 * Material 3 Expressive modal bottom sheet for offline local Smart Mix & Daily Mix tuning
 * powered by Cadence's zero-server MusicIntelligenceEngine.
 */
@Composable
fun SmartPlaylistSheetContent(
    output: MusicIntelligenceOutput?,
    isGenerating: Boolean,
    selectedMood: String?,
    seedFromCurrent: Boolean,
    currentTrackTitle: String,
    trackLookup: StableMap<String, Track>,
    artworkShape: ExpressiveArtworkShape,
    onSelectMood: (String?) -> Unit,
    onToggleSeedFromCurrent: (Boolean) -> Unit,
    onRegenerate: () -> Unit,
    onPlaySmartPlaylist: (List<Track>) -> Unit,
    onSaveSmartPlaylist: (String, List<String>) -> Unit,
    selectedEnergy: String? = null,
    discoveryBalance: Float = 0.5f,
    onSelectEnergy: (String?) -> Unit = {},
    onChangeDiscoveryBalance: (Float) -> Unit = {},
    onResetTune: () -> Unit = {}
) {
    var showRawJson by remember { mutableStateOf(false) }
    val isTuned = selectedMood != null || selectedEnergy != null || abs(discoveryBalance - 0.5f) >= 0.05f
    val balanceSummary = when {
        discoveryBalance <= 0.32f -> "Familiar"
        discoveryBalance >= 0.68f -> "Fresh"
        else -> "Balanced"
    }

    val resolvedTracks = remember(output, trackLookup) {
        output?.recommendedTracks?.mapNotNull { rec -> trackLookup[rec.trackId] }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.smart_mix_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.smart_mix_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTuned) {
                        TextButton(
                            onClick = onResetTune,
                            modifier = Modifier.testTag("smart_reset_tune_button")
                        ) {
                            Text(stringResource(R.string.discover_reset_tune))
                        }
                    }
                    TextButton(
                        onClick = onRegenerate,
                        modifier = Modifier.testTag("smart_mix_regenerate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
            }

            // Mood selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SMART_MOOD_OPTIONS.forEach { opt ->
                    val selected = selectedMood == opt.id
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMood(opt.id) },
                        label = { Text(opt.label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("smart_mood_${opt.id ?: "auto"}")
                    )
                }
            }

            // Energy level selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.discover_tune_energy),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SMART_ENERGY_OPTIONS.forEach { opt ->
                    val selected = selectedEnergy == opt.id
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectEnergy(opt.id) },
                        label = { Text(opt.label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("smart_energy_${opt.id ?: "any"}")
                    )
                }
            }

            // Familiar Favorites <-> Fresh Rediscovery balance slider
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.discover_tune_balance),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = balanceSummary,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("smart_balance_label")
                        )
                    }
                    Slider(
                        value = discoveryBalance.coerceIn(0f, 1f),
                        onValueChange = onChangeDiscoveryBalance,
                        valueRange = 0f..1f,
                        steps = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_balance_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.discover_balance_familiar),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.discover_balance_fresh),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Seed toggle chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilterChip(
                    selected = seedFromCurrent,
                    enabled = trackLookup.isNotEmpty(),
                    onClick = { onToggleSeedFromCurrent(!seedFromCurrent) },
                    label = {
                        Text(
                            text = "Seed: $currentTrackTitle",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier.testTag("smart_seed_toggle")
                )

                TextButton(
                    onClick = { showRawJson = !showRawJson },
                    modifier = Modifier.testTag("smart_toggle_json_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (showRawJson) "Hide JSON" else "JSON")
                }
            }

            if (isGenerating && output == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Analyzing local library…", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (output != null) {

            // Profile summary card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = output.playlistProfile.suggestedTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("smart_mix_result_title")
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            val cohesionPct = ((output.playlistProfile.cohesionScore.takeIf { it.isFinite() } ?: 0.0)
                                .coerceIn(0.0, 1.0) * 100.0).roundToInt()
                            Text(
                                text = "$cohesionPct% cohesion",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Mood classifications
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        output.moodClassifications.forEach { mood ->
                            val confPct = ((mood.confidence.takeIf { it.isFinite() } ?: 0.0)
                                .coerceIn(0.0, 1.0) * 100.0).roundToInt()
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${mood.mood} $confPct%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Tempo descriptor line
                    val tempo = output.tempoDescriptors
                    val inferredSuffix = if (tempo.tempoInferred) " · inferred" else ""
                    Text(
                        text = "${tempo.dominantBpmRange.min}–${tempo.dominantBpmRange.max} BPM · ${tempo.descriptor.replace('_', ' ')} · ${tempo.pacingCurve.replace('_', ' ')}$inferredSuffix",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

                // Optional raw JSON preview for inspection
                AnimatedVisibility(visible = showRawJson) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                                .testTag("smart_mix_raw_json")
                        ) {
                            Text(
                                text = output.rawJson,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (resolvedTracks.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Scan a local music folder or import audio files first to generate offline smart playlists.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    // Ranked tracks list (bounded <= 15 tracks, smooth single-scroll container)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        output.recommendedTracks.forEach { rec ->
                            val track = trackLookup[rec.trackId] ?: return@forEach
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("smart_track_${rec.trackId}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "#${rec.rank}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    ExpressiveArtworkFrame(
                                        track = track,
                                        artworkShape = artworkShape,
                                        isPlaying = false,
                                        animateScale = false,
                                        decodeSizePx = 96,
                                        shapeOverride = FastListThumbnailShape,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title.ifBlank { "Untitled Track" },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.artist.ifBlank { "Unknown Artist" }} · ${rec.reason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val scorePct = ((rec.score.takeIf { it.isFinite() } ?: 0.0)
                                        .coerceIn(0.0, 1.0) * 100.0).roundToInt()
                                    Text(
                                        text = "$scorePct",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pinned action row: Play Mix + Save Playlist (always visible & clickable)
        if (output != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPlaySmartPlaylist(resolvedTracks) },
                    enabled = resolvedTracks.isNotEmpty(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("smart_mix_play_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play Mix")
                }

                FilledTonalButton(
                    onClick = {
                        onSaveSmartPlaylist(
                            output.playlistProfile.suggestedTitle,
                            resolvedTracks.map { it.id }
                        )
                    },
                    enabled = resolvedTracks.isNotEmpty(),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("smart_mix_save_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Save Playlist")
                }
            }
        }
    }
}
