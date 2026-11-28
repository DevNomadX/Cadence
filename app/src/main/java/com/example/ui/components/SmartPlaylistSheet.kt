package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.StableMap
import com.example.data.Track
import com.example.data.formatDuration
import com.example.data.intelligence.MusicIntelligenceOutput
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
private data class MoodOption(val id: String?, val label: String)

private val SMART_MOOD_OPTIONS = listOf(
    MoodOption(null, "Auto"),
    MoodOption("calm", "Calm"),
    MoodOption("focused", "Focus"),
    MoodOption("warm", "Warm"),
    MoodOption("upbeat", "Upbeat")
)

private val PreviewArtworkShape = RoundedCornerShape(12.dp)
private val MixHeaderCardShape = RoundedCornerShape(22.dp)
private val BalanceCardShape = RoundedCornerShape(20.dp)
private val PreviewRowShape = RoundedCornerShape(16.dp)

internal fun formatSmartMixSubtitle(
    output: MusicIntelligenceOutput,
    trackCount: Int
): String {
    val minBpm = output.tempoDescriptors.dominantBpmRange.min
    val maxBpm = output.tempoDescriptors.dominantBpmRange.max
    val avgBpm = when {
        minBpm > 0 && maxBpm > 0 -> ((minBpm + maxBpm) / 2.0).roundToInt()
        maxBpm > 0 -> maxBpm
        minBpm > 0 -> minBpm
        else -> 0
    }
    val vibeLabel = output.tempoDescriptors.descriptor
        .replace('_', ' ')
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
            }
        }
        .ifBlank { "Curated Flow" }
    val countLabel = if (trackCount == 1) "1 track" else "$trackCount tracks"
    return buildList {
        if (avgBpm > 0) add("$avgBpm BPM")
        add(vibeLabel)
        add(countLabel)
    }.joinToString(" • ")
}

/**
 * Material 3 Expressive Tune Mix modal bottom sheet for offline local Smart Mix & Daily Mix tuning
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
    currentTrackArtist: String = "",
    selectedEnergy: String? = null,
    discoveryBalance: Float = 0.5f,
    onChangeDiscoveryBalance: (Float) -> Unit = {},
    onResetTune: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetBg = colorScheme.surfaceContainerLow

    // Pre-blended opaque tonal surfaces (zero runtime alpha overdraw)
    val balanceCardSurface = remember(colorScheme.surfaceContainer, sheetBg) {
        colorScheme.surfaceContainer.opaqueOver(sheetBg, 0.96f)
    }
    val headerCardSurface = remember(colorScheme.surfaceContainerHigh, sheetBg) {
        colorScheme.surfaceContainerHigh.opaqueOver(sheetBg, 0.96f)
    }
    val seedPillSurface = remember(colorScheme.surfaceContainerHigh, sheetBg) {
        colorScheme.surfaceContainerHigh.opaqueOver(sheetBg, 0.94f)
    }
    val saveButtonSurface = remember(colorScheme.secondaryContainer, sheetBg) {
        colorScheme.secondaryContainer.opaqueOver(sheetBg, 0.96f)
    }
    val previewRowSurface = remember(colorScheme.surfaceContainer, sheetBg) {
        colorScheme.surfaceContainer.opaqueOver(sheetBg, 0.88f)
    }

    val isTuned = selectedMood != null || selectedEnergy != null || abs(discoveryBalance - 0.5f) >= 0.05f
    val balanceSummary = when {
        discoveryBalance <= 0.32f -> "Familiar"
        discoveryBalance >= 0.68f -> "Fresh"
        else -> "Balanced"
    }

    val resolvedTracks = remember(output, trackLookup) {
        output?.recommendedTracks
            ?.mapNotNull { rec -> trackLookup[rec.trackId] }
            ?.distinctBy { it.id }
            .orEmpty()
    }

    val mixSubtitle = remember(output, resolvedTracks.size) {
        output?.let { formatSmartMixSubtitle(it, resolvedTracks.size) }.orEmpty()
    }

    val seedTrackDisplay = remember(currentTrackTitle, currentTrackArtist) {
        val cleanTitle = currentTrackTitle.trim().ifBlank { "Current Track" }
        val cleanArtist = currentTrackArtist.trim()
        if (cleanArtist.isNotEmpty() && !cleanTitle.equals(cleanArtist, ignoreCase = true)) {
            "$cleanTitle · $cleanArtist"
        } else {
            cleanTitle
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Clean Top Sheet Header: "Tune Mix" on left + Reset & Refresh on right (48.dp targets)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.discover_tune_mix),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("smart_mix_sheet_title")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(
                    onClick = onResetTune,
                    enabled = isTuned || !seedFromCurrent,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("smart_reset_tune_button")
                ) {
                    Text(
                        text = stringResource(R.string.discover_reset_tune),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                TextButton(
                    onClick = onRegenerate,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("smart_mix_regenerate_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.smart_mix_refresh),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 2. Scrollable Controls + Unified Mix Header + Clean Track Preview LazyColumn
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .testTag("smart_mix_list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mood Filter Row Only (Auto, Calm, Focus, Warm, Upbeat)
            item(key = "smart_mood_row", contentType = "smart_mood_row") {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_mood_row"),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = SMART_MOOD_OPTIONS,
                        key = { opt -> "mood_${opt.id ?: "auto"}" },
                        contentType = { "mood_chip" }
                    ) { opt ->
                        val selected = selectedMood == opt.id
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectMood(opt.id) },
                            label = {
                                Text(
                                    text = opt.label,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primaryContainer,
                                selectedLabelColor = colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("smart_mood_${opt.id ?: "auto"}")
                        )
                    }
                }
            }

            // Mix Balance Slider (Familiar Favorites <-> Fresh Rediscovery)
            item(key = "smart_balance_card", contentType = "smart_balance_card") {
                Surface(
                    shape = BalanceCardShape,
                    color = balanceCardSurface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.discover_tune_balance),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = balanceSummary,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = colorScheme.primary,
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
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = stringResource(R.string.discover_balance_fresh),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Active Seed Track Pill with trailing × button (or tap to re-seed when cleared)
            if (trackLookup.isNotEmpty()) {
                item(key = "smart_seed_pill", contentType = "smart_seed_pill") {
                    SmartSeedPill(
                        seedFromCurrent = seedFromCurrent,
                        seedTrackDisplay = seedTrackDisplay,
                        surfaceColor = seedPillSurface,
                        onToggleSeed = onToggleSeedFromCurrent,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            if (isGenerating && output == null) {
                item(key = "smart_mix_loading", contentType = "smart_mix_loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.smart_mix_analyzing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (output != null) {
                // Unified Mix Header Card (Title + Single Concise Vibe & Track Count Subtitle)
                item(key = "smart_mix_header_card", contentType = "smart_mix_header_card") {
                    Surface(
                        shape = MixHeaderCardShape,
                        color = headerCardSurface,
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.18f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .testTag("smart_mix_header_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = output.playlistProfile.suggestedTitle,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("smart_mix_result_title")
                            )
                            Text(
                                text = mixSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("smart_mix_result_subtitle")
                            )
                        }
                    }
                }

                if (resolvedTracks.isEmpty()) {
                    item(key = "smart_mix_empty", contentType = "smart_mix_empty") {
                        Surface(
                            shape = BalanceCardShape,
                            color = balanceCardSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.smart_mix_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    // Clean Preview Track Rows (44.dp art + Title + Artist + subtle duration)
                    items(
                        items = resolvedTracks,
                        key = { track -> "smart_track_${track.id}" },
                        contentType = { "smart_track_row" }
                    ) { track ->
                        SmartMixPreviewTrackRow(
                            track = track,
                            artworkShape = artworkShape,
                            rowSurface = previewRowSurface,
                            artworkRimColor = colorScheme.onSurface.copy(alpha = 0.08f),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }
        }

        // 3. Sticky Bottom Action Bar: Save Playlist + Play Mix (52.dp single-line pills)
        if (output != null) {
            HorizontalDivider(
                color = colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("smart_mix_bottom_bar"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        onSaveSmartPlaylist(
                            output.playlistProfile.suggestedTitle,
                            resolvedTracks.map { it.id }
                        )
                    },
                    enabled = resolvedTracks.isNotEmpty(),
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = saveButtonSurface,
                        contentColor = colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("smart_mix_save_button")
                ) {
                    Icon(
                        imageVector = CadenceIcons.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.smart_mix_save_playlist),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { onPlaySmartPlaylist(resolvedTracks) },
                    enabled = resolvedTracks.isNotEmpty(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("smart_mix_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.smart_mix_play_mix),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartSeedPill(
    seedFromCurrent: Boolean,
    seedTrackDisplay: String,
    surfaceColor: Color,
    onToggleSeed: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = if (seedFromCurrent) {
        colorScheme.primary.copy(alpha = 0.24f)
    } else {
        colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    Surface(
        shape = CircleShape,
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(
                enabled = !seedFromCurrent,
                role = Role.Button,
                onClick = { onToggleSeed(true) }
            )
            .testTag("smart_seed_toggle")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = if (seedFromCurrent) 2.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (seedFromCurrent) CadenceIcons.MusicNote else CadenceIcons.AutoAwesome,
                contentDescription = null,
                tint = if (seedFromCurrent) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (seedFromCurrent) {
                    stringResource(R.string.smart_mix_seed_label, seedTrackDisplay)
                } else {
                    stringResource(R.string.smart_mix_add_seed, seedTrackDisplay)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (seedFromCurrent) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (seedFromCurrent) {
                IconButton(
                    onClick = { onToggleSeed(false) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("smart_seed_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.smart_mix_clear_seed),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartMixPreviewTrackRow(
    track: Track,
    artworkShape: ExpressiveArtworkShape,
    rowSurface: Color,
    artworkRimColor: Color,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val durationLabel = remember(track.durationMs) {
        if (track.durationMs > 0L) formatDuration(track.durationMs) else ""
    }

    Surface(
        shape = PreviewRowShape,
        color = rowSurface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_track_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = PreviewArtworkShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, artworkRimColor),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("smart_track_art_${track.id}")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ExpressiveArtworkFrame(
                        track = track,
                        artworkShape = artworkShape,
                        isPlaying = false,
                        animateScale = false,
                        decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                        shapeOverride = PreviewArtworkShape,
                        showHairlineBorder = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.title.ifBlank { "Untitled Track" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (durationLabel.isNotEmpty()) {
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
