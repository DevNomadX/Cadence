package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
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
import com.example.ui.theme.ScallopedCookieShape
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.PI
import kotlin.math.sin

private val HeroCardShape = RoundedCornerShape(30.dp)
private val HeroArtworkShape = RoundedCornerShape(20.dp)
internal val PermanentScallopedPlayShape = ScallopedCookieShape(lobes = 8, lobeDepth = 1f)

@Immutable
internal data class HomeHeroColors(
    val cardSurface: Color,
    val cardBorder: Color,
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
    val onContainer = colorScheme.onPrimaryContainer
    return HomeHeroColors(
        cardSurface = colorScheme.primaryContainer,
        cardBorder = onContainer.copy(alpha = 0.14f),
        hifiPillContainer = onContainer.copy(alpha = 0.12f),
        hifiPillBorder = onContainer.copy(alpha = 0.22f),
        hifiPillText = onContainer,
        titleColor = onContainer,
        artistColor = onContainer.copy(alpha = 0.78f),
        timestampColor = onContainer.copy(alpha = 0.80f),
        playButtonContainer = colorScheme.primary,
        playButtonContent = colorScheme.onPrimary,
        wavePlayedColor = colorScheme.primary,
        waveHaloColor = colorScheme.primary.copy(alpha = 0.22f),
        waveUnplayedColor = onContainer.copy(alpha = 0.24f),
        artworkBorderColor = onContainer.copy(alpha = 0.16f)
    )
}

/**
 * Resolves the Hero card's compact Hi-Fi quality tier badge from real track audio metadata.
 * Displays "LOSSLESS" for lossless/24-bit/Hi-Res formats (FLAC, ALAC, WAV, PCM, >48 kHz),
 * "AAC" for AAC/M4A streams, and "HI-FI" for high-bitrate/standard audio (never raw "MP3").
 */
internal fun resolveHomeHeroFormatBadge(track: Track, fallbackHiFi: String = "HI-FI"): String {
    val spec = track.resolveAudioQualitySpec()
    return when {
        track.isLosslessFormat ||
            track.isHiRes ||
            spec?.tier == AudioQualityTier.HI_RES ||
            spec?.tier == AudioQualityTier.LOSSLESS -> "LOSSLESS"
        track.resolvedCodec == "AAC" -> "AAC"
        else -> fallbackHiFi.ifBlank { "HI-FI" }
    }
}

/**
 * Flagship M3 Expressive Main Now Playing Hero Card:
 * 1. Solid opaque `primaryContainer` surface (`RoundedCornerShape(30.dp)`) with `onPrimaryContainer` tokens.
 * 2. Compact upper Box: 112.dp rounded-20dp artwork + static 14.dp waveform Hi-Fi badge, 22.sp title,
 *    14.sp artist (with 66.dp end clearance), and bottom-right 58.dp permanently 8-lobe scalloped Play/Pause button.
 * 3. Lower full-width isolated draw-phase sinusoidal wavy seek bar (3.5.dp stroke, 12.dp thumb) + 12.sp mono timestamps.
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
    val fallbackHiFi = stringResource(R.string.badge_hifi_short)
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

    val playInteraction = remember { MutableInteractionSource() }
    val isPlayPressed by playInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (!isLowEndMode && isPlayPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "jump_back_in_play_scale"
    )
    // Subtle rotation & breathing pulse on the permanently scalloped 8-lobe button ONLY while playing
    val activePlayTransition = if (isPlaying && isActiveTab && !isLowEndMode) {
        rememberInfiniteTransition(label = "jump_back_in_scallop_active")
    } else {
        null
    }
    val scallopRotationState = activePlayTransition?.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "jump_back_in_scallop_rotation"
    )
    val scallopPulseState = activePlayTransition?.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jump_back_in_scallop_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Surface(
            shape = HeroCardShape,
            color = heroColors.cardSurface,
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
                    .padding(18.dp)
            ) {
                // Upper Section: 112.dp Artwork + Full-Width Metadata cleanly above Bottom-Right 58.dp 8-Lobe Play/Pause Button
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 40.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = HeroArtworkShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, heroColors.artworkBorderColor),
                            modifier = Modifier
                                .testTag("home_hero_artwork")
                                .size(112.dp)
                        ) {
                            ExpressiveArtworkFrame(
                                track = track,
                                artworkShape = artworkShape,
                                isPlaying = isPlaying,
                                animateScale = false,
                                decodeSizePx = 336,
                                shapeOverride = HeroArtworkShape,
                                showHairlineBorder = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp, bottom = 26.dp)
                        ) {
                            // Static Hi-Fi / Lossless Audio Badge (Zero animation)
                            Surface(
                                shape = CircleShape,
                                color = heroColors.hifiPillContainer,
                                border = BorderStroke(1.dp, heroColors.hifiPillBorder),
                                modifier = Modifier
                                    .height(26.dp)
                                    .testTag("home_hero_hifi_badge")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = CadenceIcons.GraphicEq,
                                        contentDescription = null,
                                        tint = heroColors.hifiPillText,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .testTag("home_hero_hifi_icon")
                                    )
                                    Text(
                                        text = formatBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp
                                        ),
                                        color = heroColors.hifiPillText,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Song Title (1-line Bricolage Grotesque, 22.sp Bold, full horizontal width)
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = DisplayFontFamily,
                                    fontSize = 22.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = heroColors.titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .testTag("home_hero_title")
                            )

                            // Artist Subtitle (1-line Plus Jakarta Sans, 14.sp, full horizontal width above 58.dp button)
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp
                                ),
                                color = heroColors.artistColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .testTag("home_hero_artist")
                            )
                        }
                    }

                    // Bottom-Right 58.dp Permanently 8-Lobe Scalloped M3 Expressive Play/Pause Button
                    Surface(
                        shape = PermanentScallopedPlayShape,
                        color = heroColors.playButtonContainer,
                        contentColor = heroColors.playButtonContent,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .testTag("home_continue_play_button")
                            .graphicsLayer {
                                val combinedScale = pressScale * (scallopPulseState?.value ?: 1f)
                                scaleX = combinedScale
                                scaleY = combinedScale
                                rotationZ = scallopRotationState?.value ?: 0f
                            }
                            .size(58.dp)
                            .clip(PermanentScallopedPlayShape)
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
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        rotationZ = -(scallopRotationState?.value ?: 0f)
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lower Wavy Seek Bar & JetBrains Mono Timestamps
                HomeHeroWavyProgressSection(
                    trackDurationMs = track.durationMs,
                    isPlaying = isPlaying,
                    heroColors = heroColors,
                    progressFlow = progressFlow,
                    isActiveTab = isActiveTab,
                    onSeek = onSeek
                )
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
            .height(24.dp)
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

        val thumbRadius = 6.dp.toPx()
        val haloRadius = if (dragFraction != null) 12.dp.toPx() else 9.5.dp.toPx()
        val strokeWidthPx = 3.5.dp.toPx()
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
        fontSize = 12.sp,
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
