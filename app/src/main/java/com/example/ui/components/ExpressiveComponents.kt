package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.example.R
import com.example.data.AudioQualitySpec
import com.example.data.Track
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
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

internal const val SCRUBBER_UNPLAYED_TRACK_DP = 3f
internal const val SCRUBBER_THUMB_CORE_DP = 14f
internal const val SCRUBBER_THUMB_HALO_IDLE_DP = 24f
internal const val SCRUBBER_THUMB_HALO_DRAG_DP = 30f
internal const val SCRUBBER_THUMB_LOBE_COUNT = 8
internal const val SCRUBBER_THUMB_ROTATION_DURATION_MS = 6000
internal const val SCRUBBER_TOUCH_HEIGHT_DP = 48f

/**
 * Builds an expressive multi-lobe scalloped/notched badge path centered at `(0f, 0f)` whose outer lobe
 * peaks fit cleanly inside [outerRadiusPx]. Pre-computed per radius so the Canvas draw loop performs
 * zero per-frame trigonometric allocations.
 */
internal fun buildScallopedThumbPath(
    outerRadiusPx: Float,
    lobes: Int = SCRUBBER_THUMB_LOBE_COUNT,
    amplitudeFraction: Float = 0.14f,
    samples: Int = 72
): Path {
    val path = Path()
    val safeRadius = outerRadiusPx.coerceAtLeast(1f)
    val safeLobes = lobes.coerceAtLeast(3)
    val clampedAmp = amplitudeFraction.coerceIn(0.04f, 0.25f)
    val baseRadius = safeRadius / (1f + clampedAmp)
    val stepRad = (2.0 * PI) / samples
    for (i in 0..samples) {
        val theta = i * stepRad
        val r = baseRadius * (1f + clampedAmp * cos(safeLobes * theta).toFloat())
        val x = r * cos(theta).toFloat()
        val y = r * sin(theta).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Material 3 Expressive organic wavy progress scrubber with an 8-lobe rotating scalloped seek badge
 * and a luminous breathing radial glow aura layered behind the translucent halo during playback.
 * Low-end optimized:
 * - Reads [progressProvider] and animation states strictly inside Canvas Draw phase (0 recompositions)
 * - Runs infinite rotation & glow breathing animations strictly when `isPlaying && isResumed && !performanceMode`,
 *   freezing the thumb's exact rotation angle with zero idle frames when paused
 * - Pre-builds the 8-lobe scalloped Path once per radius (0 per-frame Path allocations)
 * - Preserves 48.dp touch hit-slop and crisp tap/drag-to-seek precision
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
    val unplayedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val thumbHaloColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val performanceMode = LocalPerformanceMode.current
    val isResumed = rememberIsLifecycleResumed()
    val density = LocalDensity.current
    val latestOnSeek by rememberUpdatedState(onSeekFinished)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val effectiveWavy = wavyEnabled && !performanceMode
    val shouldAnimateWave = isPlaying && effectiveWavy && isResumed
    val shouldAnimateThumb = isPlaying && isResumed && !performanceMode

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

    val haloDiameterDpState: State<Float> = if (performanceMode) {
        remember(isDragging) {
            mutableFloatStateOf(if (isDragging) SCRUBBER_THUMB_HALO_DRAG_DP else SCRUBBER_THUMB_HALO_IDLE_DP)
        }
    } else {
        animateFloatAsState(
            targetValue = if (isDragging) SCRUBBER_THUMB_HALO_DRAG_DP else SCRUBBER_THUMB_HALO_IDLE_DP,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "scrubber_halo_diameter"
        )
    }

    // Smoothly fades the luminous glow aura in when playing and settles cleanly back to static halo when paused
    val glowVisibilityState: State<Float> = if (performanceMode) {
        remember { mutableFloatStateOf(0f) }
    } else {
        animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "scrubber_glow_visibility"
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

    // Smooth 6s/rev thumb rotation + breathing glow aura — active ONLY while playing, freezing angle when paused
    val frozenThumbAngleDeg = remember { mutableFloatStateOf(0f) }
    val rotationBaseAngleDeg = remember(shouldAnimateThumb) { frozenThumbAngleDeg.floatValue }
    val thumbRotationDeltaState: State<Float>
    val glowPulseScaleState: State<Float>
    val glowPulseAlphaState: State<Float>
    if (shouldAnimateThumb) {
        val thumbTransition = rememberInfiniteTransition(label = "scrubber_thumb_motion")
        thumbRotationDeltaState = thumbTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SCRUBBER_THUMB_ROTATION_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "thumb_rotation_deg"
        )
        glowPulseScaleState = thumbTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "thumb_glow_scale"
        )
        glowPulseAlphaState = thumbTransition.animateFloat(
            initialValue = 0.84f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "thumb_glow_alpha"
        )
    } else {
        thumbRotationDeltaState = remember { mutableFloatStateOf(0f) }
        glowPulseScaleState = remember { mutableFloatStateOf(1.0f) }
        glowPulseAlphaState = remember { mutableFloatStateOf(1.0f) }
    }

    val wavePath = remember { Path() }
    val activeStrokePx = with(density) { 3.8.dp.toPx() }
    val unplayedStrokePx = with(density) { SCRUBBER_UNPLAYED_TRACK_DP.dp.toPx() }
    val coreRadiusPx = with(density) { (SCRUBBER_THUMB_CORE_DP / 2f).dp.toPx() }
    val scallopedThumbPath = remember(coreRadiusPx) {
        buildScallopedThumbPath(outerRadiusPx = coreRadiusPx, lobes = SCRUBBER_THUMB_LOBE_COUNT)
    }
    val waveStroke = remember(activeStrokePx) {
        Stroke(
            width = activeStrokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SCRUBBER_TOUCH_HEIGHT_DP.dp)
            .testTag("playback_scrubber")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val horizontalPad = 18.dp.toPx()
                    val usable = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                    val fraction = ((offset.x - horizontalPad) / usable).coerceIn(0f, 1f)
                    latestOnSeek(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val horizontalPad = 18.dp.toPx()
                        val usable = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                        dragFraction = ((offset.x - horizontalPad) / usable).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val horizontalPad = 18.dp.toPx()
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
                .height(SCRUBBER_TOUCH_HEIGHT_DP.dp)
                .testTag("playback_scrubber_canvas")
        ) {
            val displayFraction = if (isDragging) dragFraction else progressProvider().coerceIn(0f, 1f)
            val horizontalPad = 18.dp.toPx()
            val startX = horizontalPad
            val endX = size.width - horizontalPad
            val trackWidth = (endX - startX).coerceAtLeast(1f)
            val centerY = size.height / 2f
            val activeEndX = startX + trackWidth * displayFraction
            val waveAmplitudeFactor = waveAmplitudeState.value
            val phase = phaseState.value
            val haloRadiusPx = (haloDiameterDpState.value / 2f).dp.toPx()
            val glowVisibility = glowVisibilityState.value

            // Unplayed straight 3dp track rail (right side of seek handle)
            if (activeEndX < endX) {
                drawLine(
                    color = unplayedTrackColor,
                    start = Offset(activeEndX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = unplayedStrokePx,
                    cap = StrokeCap.Round
                )
            }

            // Played smooth sinusoidal wavy track (left side up to scalloped badge center)
            if (activeEndX > startX) {
                if (waveAmplitudeFactor <= 0.01f) {
                    drawLine(
                        color = primaryColor,
                        start = Offset(startX, centerY),
                        end = Offset(activeEndX, centerY),
                        strokeWidth = activeStrokePx,
                        cap = StrokeCap.Round
                    )
                } else {
                    val maxAmp = 4.0.dp.toPx() * waveAmplitudeFactor
                    val wavelengthPx = 28.dp.toPx()
                    val stepPx = 3.dp.toPx().coerceAtLeast(3f)
                    val edgeTaperPx = 14.dp.toPx()
                    val startTaperPx = 10.dp.toPx()
                    val twoPiOverWave = (2f * PI.toFloat()) / wavelengthPx

                    wavePath.rewind()
                    var x = startX
                    var isFirst = true
                    while (x <= activeEndX) {
                        val distFromEdge = ((activeEndX - x) / edgeTaperPx).coerceIn(0f, 1f)
                        val startTaper = ((x - startX) / startTaperPx).coerceAtLeast(0f).coerceAtMost(1f)
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
                    wavePath.lineTo(activeEndX, centerY)

                    drawPath(
                        path = wavePath,
                        color = primaryColor,
                        style = waveStroke
                    )
                }
            }

            val thumbCenter = Offset(activeEndX, centerY)

            // 1. Soft breathing radial glow aura layered around the translucent halo when playing
            if (glowVisibility > 0.01f) {
                val glowScale = glowPulseScaleState.value
                val glowAlpha = glowVisibility * glowPulseAlphaState.value
                val glowRadiusPx = (haloRadiusPx * 1.45f * glowScale).coerceAtLeast(haloRadiusPx + 1f)
                val glowBrush = Brush.radialGradient(
                    0.0f to primaryColor.copy(alpha = 0.42f * glowAlpha),
                    0.52f to primaryColor.copy(alpha = 0.16f * glowAlpha),
                    1.0f to Color.Transparent,
                    center = thumbCenter,
                    radius = glowRadiusPx
                )
                drawCircle(
                    brush = glowBrush,
                    radius = glowRadiusPx,
                    center = thumbCenter
                )
            }

            // 2. Preserved translucent circular halo ring (24dp idle, expands to 30dp on drag)
            drawCircle(
                color = thumbHaloColor,
                radius = haloRadiusPx,
                center = thumbCenter
            )

            // 3. Rotating 8-lobe scalloped badge (6s/rev while playing, frozen angle when paused)
            val currentAngleDeg = if (shouldAnimateThumb) {
                val nextAngle = (rotationBaseAngleDeg + thumbRotationDeltaState.value) % 360f
                frozenThumbAngleDeg.floatValue = nextAngle
                nextAngle
            } else {
                frozenThumbAngleDeg.floatValue
            }
            translate(left = thumbCenter.x, top = thumbCenter.y) {
                rotate(degrees = currentAngleDeg, pivot = Offset.Zero) {
                    drawPath(
                        path = scallopedThumbPath,
                        color = primaryColor
                    )
                }
            }
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
        if (isPlaying) 24.dp else 38.dp
    } else {
        animateDpAsState(
            targetValue = if (isPlaying) 24.dp else 38.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "play_button_corner"
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
            .size(76.dp)
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
                    imageVector = if (isPlaying) CadenceIcons.Pause else Icons.Rounded.PlayArrow,
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
                        imageVector = if (playing) CadenceIcons.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

/**
 * Hardware-accelerated convex shape for compact scrolling list thumbnails (Library, Queue,
 * Playlists, Mini-Player) so low-end GPUs never allocate per-row stencil-buffer clip paths.
 */
val FastListThumbnailShape: Shape = RoundedCornerShape(14.dp)

/**
 * Shared target pixel decode size for all list rows, shortcut cards, LazyRow shelves,
 * and mini-player thumbnails so Coil reuses one memory-cache entry across every tab.
 */
const val FAST_THUMBNAIL_DECODE_PX: Int = 160

/**
 * Builds a low-overhead Coil [ImageRequest] with explicit pixel [CoilSize], hardware bitmaps,
 * deterministic cache keys, and zero crossfade on fast-scrolling list thumbnails.
 */
internal fun buildArtworkImageRequest(
    context: android.content.Context,
    artworkPath: String,
    decodeSizePx: Int,
    enableCrossfade: Boolean
): ImageRequest {
    val data: Any = if (artworkPath.startsWith("content://") || artworkPath.startsWith("file://")) {
        artworkPath
    } else {
        File(artworkPath)
    }
    val cacheKey = "art_${artworkPath}_$decodeSizePx"
    return ImageRequest.Builder(context)
        .data(data)
        .size(CoilSize(decodeSizePx, decodeSizePx))
        .allowHardware(true)
        .allowRgb565(true)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .apply {
            if (enableCrossfade) crossfade(140) else crossfade(false)
        }
        .build()
}

/**
 * Displays track artwork clipped into the selected Material 3 Expressive organic shape
 * (or [shapeOverride] such as [FastListThumbnailShape] for scrolling LazyColumn rows).
 */
@Composable
fun ExpressiveArtworkFrame(
    track: Track?,
    artworkShape: ExpressiveArtworkShape,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    animateScale: Boolean = true,
    decodeSizePx: Int = 512,
    shapeOverride: Shape? = null,
    showHairlineBorder: Boolean = false
) {
    val performanceMode = LocalPerformanceMode.current
    val shape = shapeOverride ?: artworkShape.toComposeShape()
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

    val hairlineBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Surface(
        shape = shape,
        color = surfaceBg,
        tonalElevation = if (shouldScale) 4.dp else 0.dp,
        shadowElevation = if (shouldScale && isPlaying) 12.dp else if (shouldScale) 4.dp else 0.dp,
        modifier = if (shouldScale) modifier.scale(scale) else modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    val enableCrossfade = decodeSizePx > 256 && !performanceMode
                    val model = remember(context, track.artworkPath, decodeSizePx, enableCrossfade) {
                        buildArtworkImageRequest(
                            context = context,
                            artworkPath = track.artworkPath,
                            decodeSizePx = decodeSizePx,
                            enableCrossfade = enableCrossfade
                        )
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
            if (showHairlineBorder) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, hairlineBorderColor, shape)
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

    val gradientColors = remember(primaryContainer, tertiaryContainer) {
        listOf(primaryContainer, tertiaryContainer)
    }
    val cachedDrawModifier = remember(seed, gradientColors, primary, tertiary, secondary) {
        val tertiaryTint = tertiary.copy(alpha = 0.88f)
        val secondaryTint = secondary.copy(alpha = 0.72f)
        val ringTint = Color.White.copy(alpha = 0.35f)
        val variant = (seed and 0x7FFFFFFF) % 3
        Modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val bgBrush = Brush.linearGradient(
                colors = gradientColors,
                start = Offset.Zero,
                end = Offset(w, h)
            )
            val sunCenter = when (variant) {
                0 -> Offset(w * 0.62f, h * 0.36f)
                1 -> Offset(w * 0.38f, h * 0.34f)
                else -> Offset(w * 0.50f, h * 0.42f)
            }
            val ringStroke = Stroke(width = w * 0.018f)
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
    }

    Box(
        modifier = modifier
            .background(primaryContainer)
            .semantics { this.contentDescription = contentDescription }
            .then(cachedDrawModifier)
    )
}

/**
 * Minimalist rhythmic equalizer indicator (3 or 4 bars).
 * Low-end optimized: draws all bars in a single Canvas and reads State<Float> handles strictly
 * during Draw phase (0 recompositions, 0 relayouts, 0 per-frame List allocations).
 * Static in Performance Mode to save GPU/CPU cycles on low-end devices.
 */
@Composable
fun AudioPulseBars(
    isPlaying: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary,
    barHeight: Dp = 16.dp,
    barCount: Int = 4,
    modifier: Modifier = Modifier
) {
    val clampedBars = barCount.coerceIn(3, 4)
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
        bar4State = if (clampedBars == 4) {
            transition.animateFloat(
                initialValue = 0.76f,
                targetValue = 0.24f,
                animationSpec = infiniteRepeatable(
                    tween(490, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "eq_4"
            )
        } else {
            remember { mutableFloatStateOf(0f) }
        }
    } else {
        val activeStatic = isPlaying
        bar1State = remember(activeStatic) { mutableFloatStateOf(if (activeStatic) 0.55f else 0.38f) }
        bar2State = remember(activeStatic) { mutableFloatStateOf(if (activeStatic) 0.92f else 0.72f) }
        bar3State = remember(activeStatic) { mutableFloatStateOf(if (activeStatic) 0.68f else 0.48f) }
        bar4State = remember(activeStatic) { mutableFloatStateOf(if (activeStatic) 0.44f else 0.28f) }
    }

    // Width: 3 bars -> 3*3.dp + 2*2.5.dp = 14.dp; 4 bars -> 4*3.dp + 3*2.5.dp = 19.5.dp
    val totalWidth = if (clampedBars == 3) 14.dp else 19.5.dp
    Canvas(
        modifier = modifier
            .width(totalWidth)
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

        var x = 0f
        drawRoundRect(tint, Offset(x, totalH - h1), Size(barWidthPx, h1), radius)
        x += barWidthPx + gapPx
        drawRoundRect(tint, Offset(x, totalH - h2), Size(barWidthPx, h2), radius)
        x += barWidthPx + gapPx
        drawRoundRect(tint, Offset(x, totalH - h3), Size(barWidthPx, h3), radius)
        if (clampedBars == 4) {
            val h4 = (totalH * bar4State.value).coerceAtLeast(minBarPx)
            x += barWidthPx + gapPx
            drawRoundRect(tint, Offset(x, totalH - h4), Size(barWidthPx, h4), radius)
        }
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

    val topColors = remember(primary) { listOf(primary.copy(alpha = 0.55f), Color.Transparent) }
    val bottomColors = remember(tertiary) { listOf(tertiary.copy(alpha = 0.42f), Color.Transparent) }
    val cachedAuraModifier = remember(surface, topColors, bottomColors, driftState) {
        Modifier.drawWithCache {
            val topRadius = size.maxDimension * 0.58f
            val bottomRadius = size.maxDimension * 0.52f
            val topBrush = if (topRadius > 0f) {
                Brush.radialGradient(
                    colors = topColors,
                    center = Offset.Zero,
                    radius = topRadius
                )
            } else null
            val bottomBrush = if (bottomRadius > 0f) {
                Brush.radialGradient(
                    colors = bottomColors,
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

    return this.then(cachedAuraModifier)
}

private val HiFiBadgeShape: Shape = RoundedCornerShape(6.dp)

/**
 * Lightweight, opaque Material 3 Expressive Hi-Fi audio quality badge.
 * Low-end GPU safe:
 * - Zero animations, shadows, elevation layers, or Subcompose layouts
 * - Pre-blends [MaterialTheme.colorScheme.tertiaryContainer] with [Color.opaqueOver] so there
 *   is zero translucent alpha-blending overhead during LazyColumn flings
 * - Automatically adapts to Light/Dark mode and every custom Expressive palette
 */
@Composable
fun HiFiQualityBadge(
    label: String,
    modifier: Modifier = Modifier,
    specDetail: String = "",
    backgroundBehind: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer.opaqueOver(backgroundBehind, 0.94f),
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer
) {
    val displayText = if (specDetail.isNotBlank()) "$label · $specDetail" else label
    val accessibilityDesc = stringResource(R.string.badge_hifi_cd, displayText)

    Box(
        modifier = modifier
            .background(color = containerColor, shape = HiFiBadgeShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDesc
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            ),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val AudioSpecPanelShape: Shape = RoundedCornerShape(14.dp)

/**
 * Crisp 3-bar vertical rounded audio waveform icon drawn cleanly inside 14dp × 14dp.
 */
@Composable
private fun AudioWaveformMiniIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barWidthPx = (size.width * 0.19f).coerceAtLeast(2f)
        val gapPx = (size.width - barWidthPx * 3f) / 2f
        val radius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
        val h1 = size.height * 0.54f
        val h2 = size.height * 0.94f
        val h3 = size.height * 0.68f
        val cy = size.height / 2f

        var x = 0f
        drawRoundRect(tint, Offset(x, cy - h1 / 2f), Size(barWidthPx, h1), radius)
        x += barWidthPx + gapPx
        drawRoundRect(tint, Offset(x, cy - h2 / 2f), Size(barWidthPx, h2), radius)
        x += barWidthPx + gapPx
        drawRoundRect(tint, Offset(x, cy - h3 / 2f), Size(barWidthPx, h3), radius)
    }
}

/**
 * Tactile, theme-aware 32dp expandable Audio Quality pill for Now Playing.
 * - Collapsed: 32dp CircleShape glass pill (`surfaceContainerHigh @ 70%`, 1dp hairline border
 *   `onSurface @ 8%`), leading 14dp 3-bar vertical audio waveform icon (`primary` tint),
 *   `JetBrains Mono` labelSmall text, and a trailing 14dp chevron rotating 180° in the GPU draw phase.
 * - Expanded: Smooth spring-animated inline metadata breakdown populated strictly from real
 *   extracted Track properties (Format, Bit Depth when lossless/hi-res, Sample Rate, Bitrate).
 */
@Composable
fun ExpandableAudioQualityBadge(
    spec: AudioQualitySpec,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundBehind: Color = MaterialTheme.colorScheme.background
) {
    val performanceMode = LocalPerformanceMode.current
    val scheme = MaterialTheme.colorScheme

    val pillContainer = scheme.surfaceContainerHigh.copy(alpha = 0.7f)
    val pillBorder = scheme.onSurface.copy(alpha = 0.08f)
    val pillContent = scheme.onSurface
    val panelContainer = scheme.surfaceContainerHigh.opaqueOver(backgroundBehind, 0.88f)

    val chevronRotation = if (performanceMode) {
        if (expanded) 180f else 0f
    } else {
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "audio_badge_chevron_rotation"
        ).value
    }

    val containerAnimModifier = if (performanceMode) {
        Modifier
    } else {
        Modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val dividerColor = scheme.outlineVariant.opaqueOver(panelContainer, 0.65f)
    val toggleLabel = stringResource(
        if (expanded) R.string.badge_audio_specs_collapse else R.string.badge_audio_specs_expand
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(containerAnimModifier),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .testTag("now_playing_hifi_badge")
                .height(32.dp)
                .clip(CircleShape)
                .background(color = pillContainer, shape = CircleShape)
                .border(BorderStroke(1.dp, pillBorder), CircleShape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = toggleLabel,
                    onClick = onToggleExpanded
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioWaveformMiniIcon(
                tint = scheme.primary,
                modifier = Modifier
                    .size(14.dp)
                    .testTag("audio_badge_waveform_icon")
            )
            Text(
                text = spec.headline,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                ),
                color = pillContent,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
                    .testTag("audio_badge_chevron")
            )
        }

        if (expanded && spec.detailItems.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AudioSpecPanelShape)
                    .background(panelContainer, AudioSpecPanelShape)
                    .border(BorderStroke(1.dp, pillBorder), AudioSpecPanelShape)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("now_playing_audio_specs_panel"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                spec.detailItems.forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(dividerColor)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {}
                            .testTag("audio_spec_${item.testTagKey}"),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = item.label.uppercase(Locale.US),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 0.6.sp
                            ),
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = MonoFontFamily
                            ),
                            color = scheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
