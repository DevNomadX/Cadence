package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AudioQualityTier
import com.example.data.Track
import com.example.data.formatDuration
import com.example.playback.PlaybackProgress
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.PI
import kotlin.math.sin

private val HeroCardShape = RoundedCornerShape(28.dp)
private val HeroArtworkShape = RoundedCornerShape(18.dp)

@Immutable
internal data class HomeHeroColors(
    val topColor: Color,
    val bottomColor: Color,
    val cardBorder: Color,
    val accentOrbColor: Color,
    val accentRingColor: Color,
    val statusTextColor: Color,
    val statusIconTint: Color,
    val hifiPillContainer: Color,
    val hifiPillBorder: Color,
    val hifiPillText: Color,
    val titleColor: Color,
    val artistColor: Color,
    val timestampColor: Color,
    val playButtonContainer: Color,
    val playButtonContent: Color,
    val wavePlayedColor: Color,
    val waveHaloColor: Color,
    val waveUnplayedColor: Color,
    val artworkBorderColor: Color
)

internal fun resolveHomeHeroColors(colorScheme: ColorScheme): HomeHeroColors {
    // Calm, low-glare M3 Expressive tonal surface: 24% primaryContainer pre-blended over surfaceContainerHigh
    val topColor = colorScheme.primaryContainer.opaqueOver(colorScheme.surfaceContainerHigh, 0.24f)
    val bottomColor = colorScheme.primaryContainer.opaqueOver(colorScheme.surfaceContainer, 0.16f)
    val hifiPillBg = colorScheme.primary.opaqueOver(topColor, 0.14f)
    return HomeHeroColors(
        topColor = topColor,
        bottomColor = bottomColor,
        cardBorder = colorScheme.primary.copy(alpha = 0.20f),
        accentOrbColor = colorScheme.primary.copy(alpha = 0.05f),
        accentRingColor = colorScheme.primary.copy(alpha = 0.08f),
        statusTextColor = colorScheme.primary,
        statusIconTint = colorScheme.primary,
        hifiPillContainer = hifiPillBg,
        hifiPillBorder = colorScheme.primary.copy(alpha = 0.28f),
        hifiPillText = colorScheme.primary,
        titleColor = colorScheme.onSurface,
        artistColor = colorScheme.onSurfaceVariant,
        timestampColor = colorScheme.onSurfaceVariant,
        playButtonContainer = colorScheme.primary,
        playButtonContent = colorScheme.onPrimary,
        wavePlayedColor = colorScheme.primary,
        waveHaloColor = colorScheme.primary.copy(alpha = 0.22f),
        waveUnplayedColor = colorScheme.onSurfaceVariant.opaqueOver(bottomColor, 0.24f),
        artworkBorderColor = colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * Resolves the Hero card's Hi-Fi quality tier badge from real track audio metadata.
 * Displays "LOSSLESS" for lossless/24-bit/Hi-Res formats (FLAC, ALAC, WAV, PCM, >48 kHz)
 * and "HI-FI AUDIO" for high-bitrate/standard audio rather than raw file extensions like "MP3".
 */
internal fun resolveHomeHeroFormatBadge(track: Track, fallbackHiFi: String = "HI-FI AUDIO"): String {
    val spec = track.resolveAudioQualitySpec()
    return when {
        track.isLosslessFormat ||
            track.isHiRes ||
            spec?.tier == AudioQualityTier.HI_RES ||
            spec?.tier == AudioQualityTier.LOSSLESS -> "LOSSLESS"
        else -> fallbackHiFi.ifBlank { "HI-FI AUDIO" }
    }
}

/**
 * Main Now Playing Hero Card arranged in three clean vertical tiers:
 * 1. Top Tier: 100.dp rounded-18dp artwork on left + 4-line metadata stack on right
 *    (inline status row, 1-line title, 1-line artist, tonal Hi-Fi Audio pill).
 * 2. Middle Tier: Full-width isolated draw-phase sinusoidal wavy progress bar & mono timestamps.
 * 3. Bottom Tier: Single centered 54.dp Play/Pause button (circle when paused <-> 16.dp squircle when playing).
 */
@Composable
internal fun HomeJumpBackInHeroCard(
    track: Track,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    heroColors: HomeHeroColors,
    progressFlow: StateFlow<PlaybackProgress>,
    isActiveTab: Boolean,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val isLowEndMode = LocalPerformanceMode.current
    val fallbackHiFi = stringResource(R.string.badge_hifi_audio)
    val formatBadge = remember(
        track.id,
        track.codec,
        track.mimeType,
        track.bitrateKbps,
        track.sampleRateHz,
        track.bitDepth,
        fallbackHiFi
    ) {
        resolveHomeHeroFormatBadge(track, fallbackHiFi)
    }
    val heroBrush = remember(heroColors.topColor, heroColors.bottomColor) {
        Brush.verticalGradient(listOf(heroColors.topColor, heroColors.bottomColor))
    }

    val playInteraction = remember { MutableInteractionSource() }
    val isPlayPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (!isLowEndMode && isPlayPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "jump_back_in_play_scale"
    )
    val targetPlayCorner = if (isPlaying) 16.dp else 27.dp
    val animatedPlayCorner by animateDpAsState(
        targetValue = targetPlayCorner,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 540f),
        label = "jump_back_in_play_corner"
    )
    val playButtonShape = RoundedCornerShape(if (isLowEndMode) targetPlayCorner else animatedPlayCorner)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Surface(
            shape = HeroCardShape,
            color = heroColors.topColor,
            border = BorderStroke(1.dp, heroColors.cardBorder),
            modifier = Modifier
                .testTag("home_continue_card")
                .fillMaxWidth()
                .clip(HeroCardShape)
                .clickable(role = Role.Button, onClick = onExpand)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(heroBrush)
                    .drawBehind {
                        val center = Offset(size.width * 0.94f, size.height * 0.12f)
                        drawCircle(
                            color = heroColors.accentOrbColor,
                            radius = size.width * 0.36f,
                            center = center
                        )
                        drawCircle(
                            color = heroColors.accentRingColor,
                            radius = size.width * 0.46f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tier 1: 100.dp Rounded Artwork + 4-Line Metadata Stack
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = HeroArtworkShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, heroColors.artworkBorderColor),
                        modifier = Modifier
                            .testTag("home_hero_artwork")
                            .size(100.dp)
                    ) {
                        ExpressiveArtworkFrame(
                            track = track,
                            artworkShape = artworkShape,
                            isPlaying = isPlaying,
                            animateScale = false,
                            decodeSizePx = 300,
                            shapeOverride = HeroArtworkShape,
                            showHairlineBorder = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // 1. Clean Inline Playback Status ("NOW PLAYING" / "PAUSED")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("home_hero_status_badge")
                        ) {
                            Icon(
                                imageVector = CadenceIcons.GraphicEq,
                                contentDescription = null,
                                tint = heroColors.statusIconTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(if (isPlaying) R.string.now_playing_label else R.string.status_paused),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = heroColors.statusTextColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // 2. Song Title (1-line Bricolage Grotesque)
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = heroColors.titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_hero_title")
                        )

                        // 3. Artist Name (1-line Plus Jakarta Sans)
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = heroColors.artistColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_hero_artist")
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        // 4. Compact Hi-Fi Audio Tonal Pill (Below Artist)
                        Surface(
                            shape = CircleShape,
                            color = heroColors.hifiPillContainer,
                            border = BorderStroke(1.dp, heroColors.hifiPillBorder),
                            modifier = Modifier.testTag("home_hero_hifi_badge")
                        ) {
                            Text(
                                text = formatBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = heroColors.hifiPillText,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Tier 2: Full-Width Interactive Sinusoidal Wavy Progress Bar & Mono Timestamps
                HomeHeroWavyProgressSection(
                    trackDurationMs = track.durationMs,
                    isPlaying = isPlaying,
                    heroColors = heroColors,
                    progressFlow = progressFlow,
                    isActiveTab = isActiveTab,
                    onSeek = onSeek
                )

                // Tier 3: Single Centered 54.dp Morphing Play/Pause Button Only
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = playButtonShape,
                        color = heroColors.playButtonContainer,
                        contentColor = heroColors.playButtonContent,
                        modifier = Modifier
                            .testTag("home_continue_play_button")
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            }
                            .size(54.dp)
                            .clip(playButtonShape)
                            .clickable(
                                interactionSource = playInteraction,
                                indication = ripple(),
                                role = Role.Button,
                                onClick = onTogglePlayPause
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) CadenceIcons.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.action_pause else R.string.action_play
                                ),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroWavyProgressSection(
    trackDurationMs: Long,
    isPlaying: Boolean,
    heroColors: HomeHeroColors,
    progressFlow: StateFlow<PlaybackProgress>,
    isActiveTab: Boolean,
    onSeek: (Float) -> Unit
) {
    val dragFractionState = remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        HomeHeroWavyProgressBar(
            progressFlow = progressFlow,
            trackDurationMs = trackDurationMs,
            isPlaying = isPlaying,
            isActiveTab = isActiveTab,
            playedColor = heroColors.wavePlayedColor,
            haloColor = heroColors.waveHaloColor,
            unplayedColor = heroColors.waveUnplayedColor,
            dragFractionState = dragFractionState,
            onSeek = onSeek
        )
        HomeHeroTimestampsRow(
            progressFlow = progressFlow,
            trackDurationMs = trackDurationMs,
            isActiveTab = isActiveTab,
            timestampColor = heroColors.timestampColor,
            dragFractionState = dragFractionState
        )
    }
}

/**
 * Isolated draw-phase sinusoidal wavy seek bar.
 * Reads [progressFlow] strictly during the Canvas draw phase so rapid playback ticks
 * trigger zero recompositions on HomeScreen or the Hero card.
 */
@Composable
private fun HomeHeroWavyProgressBar(
    progressFlow: StateFlow<PlaybackProgress>,
    trackDurationMs: Long,
    isPlaying: Boolean,
    isActiveTab: Boolean,
    playedColor: Color,
    haloColor: Color,
    unplayedColor: Color,
    dragFractionState: MutableState<Float?>,
    onSeek: (Float) -> Unit
) {
    val isLowEndMode = LocalPerformanceMode.current
    val progressState = if (isActiveTab) {
        progressFlow.collectAsStateWithLifecycle()
    } else {
        null
    }

    val phaseState = if (isPlaying && isActiveTab && !isLowEndMode) {
        val transition = rememberInfiniteTransition(label = "home_hero_wave_phase")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "home_hero_wave_angle"
        )
    } else {
        null
    }

    val wavePath = remember { Path() }

    Canvas(
        modifier = Modifier
            .testTag("home_continue_progress")
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(onSeek) {
                detectTapGestures { offset ->
                    val thumbPad = 8.dp.toPx()
                    val usableWidth = (size.width - thumbPad * 2f).coerceAtLeast(1f)
                    val fraction = ((offset.x - thumbPad) / usableWidth).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
            .pointerInput(onSeek) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val thumbPad = 8.dp.toPx()
                        val usableWidth = (size.width - thumbPad * 2f).coerceAtLeast(1f)
                        dragFractionState.value = ((offset.x - thumbPad) / usableWidth).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val thumbPad = 8.dp.toPx()
                        val usableWidth = (size.width - thumbPad * 2f).coerceAtLeast(1f)
                        dragFractionState.value = ((change.position.x - thumbPad) / usableWidth).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val finalFraction = dragFractionState.value
                        dragFractionState.value = null
                        if (finalFraction != null) {
                            onSeek(finalFraction)
                        }
                    },
                    onDragCancel = {
                        dragFractionState.value = null
                    }
                )
            }
    ) {
        val snapshot = progressState?.value ?: progressFlow.value
        val totalMs = snapshot.durationMs.takeIf { it > 0L } ?: trackDurationMs.coerceAtLeast(1L)
        val liveFraction = (snapshot.positionMs.coerceIn(0L, totalMs).toFloat() / totalMs.toFloat())
            .coerceIn(0f, 1f)
        val dragFraction = dragFractionState.value
        val fraction = (dragFraction ?: liveFraction).coerceIn(0f, 1f)

        val thumbRadius = 5.5.dp.toPx()
        val haloRadius = if (dragFraction != null) 12.dp.toPx() else 9.5.dp.toPx()
        val strokeWidthPx = 3.2.dp.toPx()
        val padX = thumbRadius + 2.dp.toPx()
        val startX = padX
        val endX = (size.width - padX).coerceAtLeast(startX + 1f)
        val centerY = size.height / 2f
        val playheadX = startX + (endX - startX) * fraction

        if (playheadX < endX) {
            drawLine(
                color = unplayedColor,
                start = Offset(playheadX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        val playedLength = (playheadX - startX).coerceAtLeast(0f)
        if (playedLength > 1f) {
            val amplitude = if (isPlaying) 3.6.dp.toPx() else 2.0.dp.toPx()
            val wavelength = 24.dp.toPx().coerceAtLeast(12f)
            val phase = phaseState?.value ?: 0f
            val stepPx = 3.dp.toPx().coerceAtLeast(2f)

            wavePath.rewind()
            var x = startX
            var isFirst = true
            while (x <= playheadX) {
                val distFromStart = x - startX
                val distFromEnd = (playheadX - x).coerceAtLeast(0f)
                val startTaper = (distFromStart / (wavelength * 0.4f)).coerceIn(0f, 1f)
                val endTaper = (distFromEnd / (wavelength * 0.4f)).coerceIn(0f, 1f)
                val env = minOf(startTaper, endTaper)
                val angle = (distFromStart / wavelength) * (2.0 * PI) - phase
                val y = centerY + (sin(angle).toFloat() * amplitude * env)
                if (isFirst) {
                    wavePath.moveTo(x, y)
                    isFirst = false
                } else {
                    wavePath.lineTo(x, y)
                }
                if (x == playheadX) break
                x = (x + stepPx).coerceAtMost(playheadX)
            }

            drawPath(
                path = wavePath,
                color = playedColor,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }

        drawCircle(
            color = haloColor,
            radius = haloRadius,
            center = Offset(playheadX, centerY)
        )
        drawCircle(
            color = playedColor,
            radius = thumbRadius,
            center = Offset(playheadX, centerY)
        )
    }
}

/**
 * 1-second distinct leaf composable for elapsed and total timestamps in JetBrains Mono (tnum).
 */
@Composable
private fun HomeHeroTimestampsRow(
    progressFlow: StateFlow<PlaybackProgress>,
    trackDurationMs: Long,
    isActiveTab: Boolean,
    timestampColor: Color,
    dragFractionState: MutableState<Float?>
) {
    val timePair = if (!isActiveTab) {
        computeTimestampPair(progressFlow.value, trackDurationMs)
    } else {
        val pairFlow = remember(progressFlow, trackDurationMs) {
            progressFlow
                .map { p -> computeTimestampPair(p, trackDurationMs) }
                .distinctUntilChanged()
        }
        pairFlow.collectAsStateWithLifecycle(
            initialValue = computeTimestampPair(progressFlow.value, trackDurationMs)
        ).value
    }

    val dragFraction = dragFractionState.value
    val elapsedText = if (dragFraction != null) {
        val totalMs = progressFlow.value.durationMs.takeIf { it > 0L } ?: trackDurationMs.coerceAtLeast(1L)
        formatDuration((totalMs * dragFraction).toLong().coerceIn(0L, totalMs))
    } else {
        timePair.first
    }

    val monoStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = MonoFontFamily,
        fontSize = 11.5.sp,
        fontFeatureSettings = "tnum"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = elapsedText,
            style = monoStyle,
            color = timestampColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag("home_continue_elapsed")
        )
        Text(
            text = timePair.second,
            style = monoStyle,
            color = timestampColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag("home_continue_duration")
        )
    }
}

private fun computeTimestampPair(progress: PlaybackProgress, fallbackDurationMs: Long): Pair<String, String> {
    val totalMs = progress.durationMs.takeIf { it > 0L } ?: fallbackDurationMs.coerceAtLeast(1L)
    val posSecMs = (progress.positionMs.coerceIn(0L, totalMs) / 1000L) * 1000L
    return formatDuration(posSecMs) to formatDuration(totalMs)
}
