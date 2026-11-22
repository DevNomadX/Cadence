package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.Track
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.LocalPerformanceMode
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@Composable
private fun rememberIsLifecycleResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return isResumed
}

/**
 * Material 3 Expressive organic wavy progress scrubber.
 * Low-end optimized:
 * - Reads [progressProvider] strictly inside Canvas Draw phase (0 recompositions during playback!)
 * - Defers infinite phase, amplitude, and thumb-scale reads to Draw phase
 * - Respects [LocalPerformanceMode] to eliminate wave/spring work on low-RAM hardware
 */
@Composable
fun WavyExpressiveSlider(
    progressProvider: () -> Float,
    isPlaying: Boolean,
    wavyEnabled: Boolean,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val thumbColor = MaterialTheme.colorScheme.primary
    val performanceMode = LocalPerformanceMode.current
    val isResumed = rememberIsLifecycleResumed()
    val density = LocalDensity.current
    val latestOnSeek by rememberUpdatedState(onSeekFinished)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val effectiveWavy = wavyEnabled && !performanceMode
    val shouldAnimateWave = isPlaying && effectiveWavy && isResumed
    val waveAmplitudeState: State<Float> = if (performanceMode) {
        remember { mutableFloatStateOf(0f) }
    } else {
        animateFloatAsState(
            targetValue = if (isPlaying && effectiveWavy) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "wave_amplitude"
        )
    }

    val thumbHeightScaleState: State<Float> = if (performanceMode) {
        remember(isDragging) { mutableFloatStateOf(if (isDragging) 1.2f else 1f) }
    } else {
        animateFloatAsState(
            targetValue = if (isDragging) 1.24f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "thumb_scale"
        )
    }

    val phaseState: State<Float> = if (shouldAnimateWave) {
        val infiniteTransition = rememberInfiniteTransition(label = "wavy_scrubber_phase")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val wavePath = remember { Path() }
    val strokePx = with(density) { 4.5.dp.toPx() }
    val waveStroke = remember(strokePx) {
        Stroke(
            width = strokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("playback_scrubber")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val horizontalPad = 10.dp.toPx()
                    val usable = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                    val fraction = ((offset.x - horizontalPad) / usable).coerceIn(0f, 1f)
                    latestOnSeek(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val horizontalPad = 10.dp.toPx()
                        val usable = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                        dragFraction = ((offset.x - horizontalPad) / usable).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val horizontalPad = 10.dp.toPx()
                        val usable = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                        dragFraction = ((change.position.x - horizontalPad) / usable).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        latestOnSeek(dragFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        ) {
            val displayFraction = if (isDragging) dragFraction else progressProvider().coerceIn(0f, 1f)
            val horizontalPad = 10.dp.toPx()
            val startX = horizontalPad
            val endX = size.width - horizontalPad
            val trackWidth = (endX - startX).coerceAtLeast(1f)
            val centerY = size.height / 2f
            val activeEndX = startX + trackWidth * displayFraction
            val thumbGap = 7.dp.toPx()
            val waveAmplitudeFactor = waveAmplitudeState.value
            val phase = phaseState.value
            val thumbHeightScale = thumbHeightScaleState.value

            // Inactive track rail (right side of thumb)
            val inactiveStartX = (activeEndX + thumbGap).coerceAtMost(endX)
            if (inactiveStartX < endX) {
                drawLine(
                    color = trackBgColor,
                    start = Offset(inactiveStartX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }

            // Active expressive wavy track (left side of thumb)
            val waveEndX = (activeEndX - thumbGap).coerceAtLeast(startX)
            if (waveEndX > startX) {
                if (waveAmplitudeFactor <= 0.01f) {
                    drawLine(
                        color = primaryColor,
                        start = Offset(startX, centerY),
                        end = Offset(waveEndX, centerY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                } else {
                    val maxAmp = 4.2.dp.toPx() * waveAmplitudeFactor
                    val wavelengthPx = 28.dp.toPx()
                    val stepPx = 3.dp.toPx().coerceAtLeast(3f)
                    val edgeTaperPx = 14.dp.toPx()
                    val startTaperPx = 10.dp.toPx()
                    val twoPiOverWave = (2f * PI.toFloat()) / wavelengthPx

                    wavePath.rewind()
                    var x = startX
                    var isFirst = true
                    while (x <= waveEndX) {
                        val distFromEdge = ((waveEndX - x) / edgeTaperPx).coerceIn(0f, 1f)
                        val startTaper = ((x - startX) / startTaperPx).coerceIn(0f, 1f)
                        val angle = (x - startX) * twoPiOverWave - phase
                        val y = centerY + sin(angle) * maxAmp * (distFromEdge * startTaper)
                        if (isFirst) {
                            wavePath.moveTo(x, y)
                            isFirst = false
                        } else {
                            wavePath.lineTo(x, y)
                        }
                        x += stepPx
                    }
                    wavePath.lineTo(waveEndX, centerY)

                    drawPath(
                        path = wavePath,
                        color = primaryColor,
                        style = waveStroke
                    )
                }
            }

            // Sculptural vertical pill thumb
            val thumbW = 5.5.dp.toPx()
            val thumbH = 22.dp.toPx() * thumbHeightScale
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(activeEndX - thumbW / 2f, centerY - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
            )
        }
    }
}

/**
 * Signature Material 3 Expressive morphing Play/Pause hero button.
 * Morphs cleanly with bouncy spring physics between a circle/pill (paused)
 * and a sculptural rounded square (playing). Snaps directly in Performance Mode.
 */
@Composable
fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    isPreparing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val performanceMode = LocalPerformanceMode.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale = if (performanceMode) {
        if (isPressed) 0.94f else 1f
    } else {
        animateFloatAsState(
            targetValue = if (isPressed) 0.91f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "play_button_scale"
        ).value
    }

    val cornerRadius = if (performanceMode) {
        if (isPlaying) 24.dp else 40.dp
    } else {
        animateDpAsState(
            targetValue = if (isPlaying) 24.dp else 40.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "play_button_corner"
        ).value
    }

    val buttonWidth = if (performanceMode) {
        if (isPlaying) 88.dp else 76.dp
    } else {
        animateDpAsState(
            targetValue = if (isPlaying) 88.dp else 76.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "play_button_width"
        ).value
    }

    val labelText = stringResource(
        if (isPlaying) R.string.action_pause else R.string.action_play
    )
    val buttonShape = RoundedCornerShape(cornerRadius)

    Surface(
        shape = buttonShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = if (performanceMode) 0.dp else 6.dp,
        shadowElevation = if (performanceMode) 0.dp else 6.dp,
        modifier = modifier
            .width(buttonWidth)
            .height(74.dp)
            .let { if (scale != 1f) it.scale(scale) else it }
            .clip(buttonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = labelText
            }
            .testTag("play_pause_button")
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isPreparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else if (performanceMode) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (scaleIn(
                            initialScale = 0.72f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(tween(150))) togetherWith
                            (scaleOut(targetScale = 0.72f, animationSpec = tween(120)) + fadeOut(tween(120)))
                    },
                    label = "play_pause_icon"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

/**
 * Displays track artwork clipped into the selected Material 3 Expressive organic shape.
 * Low-end optimized:
 * - Uses non-subcomposing Coil AsyncImage (zero nested SubcomposeLayout in LazyColumn rows)
 * - Skips animateFloatAsState allocation & elevation shadow layers when animateScale == false or in Performance Mode
 * - Disables crossfade on compact thumbnails (<= 160px) or in Performance Mode
 */
@Composable
fun ExpressiveArtworkFrame(
    track: Track?,
    artworkShape: ExpressiveArtworkShape,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    animateScale: Boolean = true,
    decodeSizePx: Int = 512
) {
    val performanceMode = LocalPerformanceMode.current
    val shape = artworkShape.toComposeShape()
    val shouldScale = animateScale && !performanceMode
    val scale = if (shouldScale) {
        animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0.94f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "artwork_breathing_scale"
        ).value
    } else {
        1f
    }

    val cd = stringResource(R.string.album_art_cd, track?.title ?: stringResource(R.string.app_name))
    val surfaceBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val placeholderPainter = remember(surfaceBg) { ColorPainter(surfaceBg) }

    Surface(
        shape = shape,
        color = surfaceBg,
        tonalElevation = if (shouldScale) 4.dp else 0.dp,
        shadowElevation = if (shouldScale && isPlaying) 12.dp else if (shouldScale) 4.dp else 0.dp,
        modifier = if (shouldScale) modifier.scale(scale) else modifier
    ) {
        when {
            track == null -> {
                ThemeReactiveBauhausArtwork(
                    seed = 7,
                    contentDescription = cd,
                    modifier = Modifier.fillMaxSize()
                )
            }

            track.artworkResId != null -> {
                Image(
                    painter = painterResource(id = track.artworkResId),
                    contentDescription = cd,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            !track.artworkPath.isNullOrBlank() -> {
                val context = LocalContext.current
                val enableCrossfade = decodeSizePx > 160 && !performanceMode
                val model = remember(track.artworkPath, decodeSizePx, enableCrossfade) {
                    val data: Any = if (track.artworkPath.startsWith("content://") || track.artworkPath.startsWith("file://")) {
                        track.artworkPath
                    } else {
                        File(track.artworkPath)
                    }
                    ImageRequest.Builder(context)
                        .data(data)
                        .size(decodeSizePx)
                        .allowRgb565(true)
                        .apply {
                            if (enableCrossfade) crossfade(140) else crossfade(false)
                        }
                        .build()
                }
                AsyncImage(
                    model = model,
                    contentDescription = cd,
                    placeholder = placeholderPainter,
                    error = placeholderPainter,
                    fallback = placeholderPainter,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                ThemeReactiveBauhausArtwork(
                    seed = track.id.hashCode(),
                    contentDescription = cd,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Generative minimalist Bauhaus cover art that adapts directly to the active M3 Expressive theme.
 * Uses drawWithCache so gradient shaders and stroke objects are created once per size/theme change.
 */
@Composable
fun ThemeReactiveBauhausArtwork(
    seed: Int,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiary
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val secondary = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .background(primaryContainer)
            .semantics { this.contentDescription = contentDescription }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val variant = (seed and 0x7FFFFFFF) % 3
                val bgBrush = Brush.linearGradient(
                    colors = listOf(primaryContainer, tertiaryContainer),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
                val sunCenter = when (variant) {
                    0 -> Offset(w * 0.62f, h * 0.36f)
                    1 -> Offset(w * 0.38f, h * 0.34f)
                    else -> Offset(w * 0.50f, h * 0.42f)
                }
                val ringStroke = Stroke(width = w * 0.018f)
                val tertiaryTint = tertiary.copy(alpha = 0.88f)
                val secondaryTint = secondary.copy(alpha = 0.72f)
                val ringTint = Color.White.copy(alpha = 0.35f)
                val lowLeftCenter = Offset(w * 0.28f, h * 0.92f)
                val lowRightCenter = Offset(w * 0.76f, h * 0.74f)

                onDrawBehind {
                    drawRect(brush = bgBrush)
                    drawCircle(color = primary, radius = w * 0.34f, center = sunCenter)
                    drawCircle(color = tertiaryTint, radius = w * 0.46f, center = lowLeftCenter)
                    drawCircle(color = secondaryTint, radius = w * 0.16f, center = lowRightCenter)
                    drawCircle(color = ringTint, radius = w * 0.24f, center = sunCenter, style = ringStroke)
                }
            }
    )
}

/**
 * Minimalist 4-bar rhythmic equalizer indicator.
 * Low-end optimized: draws all 4 bars in a single Canvas and reads State<Float> handles strictly
 * during Draw phase (0 recompositions, 0 relayouts, 0 per-frame List allocations).
 * Static in Performance Mode to save GPU/CPU cycles on low-end devices.
 */
@Composable
fun AudioPulseBars(
    isPlaying: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary,
    barHeight: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val performanceMode = LocalPerformanceMode.current
    val isResumed = rememberIsLifecycleResumed()
    val animate = isPlaying && isResumed && !performanceMode

    val bar1State: State<Float>
    val bar2State: State<Float>
    val bar3State: State<Float>
    val bar4State: State<Float>

    if (animate) {
        val transition = rememberInfiniteTransition(label = "eq_bars")
        bar1State = transition.animateFloat(
            initialValue = 0.28f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                tween(420, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "eq_1"
        )
        bar2State = transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 0.32f,
            animationSpec = infiniteRepeatable(
                tween(540, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "eq_2"
        )
        bar3State = transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.88f,
            animationSpec = infiniteRepeatable(
                tween(370, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "eq_3"
        )
        bar4State = transition.animateFloat(
            initialValue = 0.76f,
            targetValue = 0.24f,
            animationSpec = infiniteRepeatable(
                tween(490, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "eq_4"
        )
    } else {
        val activeStatic = isPlaying
        bar1State = remember(activeStatic) { mutableFloatStateOf(if (activeStatic) 0.55f else 0.28f) }
        bar2State = remember(activeStatic) { mutableFloatStateOf(if (activeStatic) 0.92f else 0.45f) }
        bar3State = remember(activeStatic) { mutableFloatStateOf(if (activeStatic) 0.68f else 0.32f) }
        bar4State = remember(activeStatic) { mutableFloatStateOf(if (activeStatic) 0.44f else 0.22f) }
    }

    // Total width: 4 * 3.dp + 3 * 2.5.dp = 19.5.dp
    Canvas(
        modifier = modifier
            .width(19.5.dp)
            .height(barHeight)
    ) {
        val barWidthPx = 3.dp.toPx()
        val gapPx = 2.5.dp.toPx()
        val minBarPx = 3.dp.toPx()
        val radius = CornerRadius(barWidthPx * 0.5f, barWidthPx * 0.5f)
        val totalH = size.height

        val h1 = (totalH * bar1State.value).coerceAtLeast(minBarPx)
        val h2 = (totalH * bar2State.value).coerceAtLeast(minBarPx)
        val h3 = (totalH * bar3State.value).coerceAtLeast(minBarPx)
        val h4 = (totalH * bar4State.value).coerceAtLeast(minBarPx)

        var x = 0f
        drawRoundRect(tint, Offset(x, totalH - h1), Size(barWidthPx, h1), radius)
        x += barWidthPx + gapPx
        drawRoundRect(tint, Offset(x, totalH - h2), Size(barWidthPx, h2), radius)
        x += barWidthPx + gapPx
        drawRoundRect(tint, Offset(x, totalH - h3), Size(barWidthPx, h3), radius)
        x += barWidthPx + gapPx
        drawRoundRect(tint, Offset(x, totalH - h4), Size(barWidthPx, h4), radius)
    }
}

/**
 * Soft atmospheric radial aura backdrop that adapts to the active M3 Expressive theme.
 * Low-end optimized:
 * - Renders solid surface background in Performance Mode (0 radial gradient overdraw)
 * - Caches both RadialGradient shaders in drawWithCache (0 Shader/List allocations per frame)
 * - Reads driftState strictly in onDrawBehind via Canvas translate (0 root recompositions/sec)
 */
@Composable
fun Modifier.ambientAuraBackdrop(
    enabled: Boolean,
    isPlaying: Boolean
): Modifier {
    val performanceMode = LocalPerformanceMode.current
    val effectiveEnabled = enabled && !performanceMode
    val primary = MaterialTheme.colorScheme.primaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val surface = MaterialTheme.colorScheme.background

    if (!effectiveEnabled) {
        return this.background(surface)
    }

    val isResumed = rememberIsLifecycleResumed()
    val driftState: State<Float> = if (isPlaying && isResumed) {
        val transition = rememberInfiniteTransition(label = "aura_drift")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "aura_pos"
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }

    return this.drawWithCache {
        val topRadius = size.maxDimension * 0.58f
        val bottomRadius = size.maxDimension * 0.52f
        val topBrush = if (topRadius > 0f) {
            Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.55f), Color.Transparent),
                center = Offset.Zero,
                radius = topRadius
            )
        } else null
        val bottomBrush = if (bottomRadius > 0f) {
            Brush.radialGradient(
                colors = listOf(tertiary.copy(alpha = 0.42f), Color.Transparent),
                center = Offset.Zero,
                radius = bottomRadius
            )
        } else null

        onDrawBehind {
            drawRect(surface)
            if (topBrush == null || bottomBrush == null) return@onDrawBehind

            val drift = driftState.value
            val topX = size.width * (0.22f + 0.16f * drift)
            val topY = size.height * (0.14f + 0.05f * drift)
            translate(left = topX, top = topY) {
                drawCircle(brush = topBrush, radius = topRadius, center = Offset.Zero)
            }

            val bottomX = size.width * (0.82f - 0.14f * drift)
            val bottomY = size.height * (0.76f - 0.06f * drift)
            translate(left = bottomX, top = bottomY) {
                drawCircle(brush = bottomBrush, radius = bottomRadius, center = Offset.Zero)
            }
        }
    }
}
