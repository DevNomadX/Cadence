package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.ScallopedCircleShape
import com.example.ui.theme.SuperellipseSquircleShape
import com.example.ui.theme.opaqueOver

// Cached M3 Expressive emblem shapes for zero per-frame path/outline allocations on low-end GPUs
private val FallbackScallopBadgeShape: Shape = ScallopedCircleShape(lobes = 8, amplitudeFraction = 0.068f)
private val FallbackSquircleBadgeShape: Shape = SuperellipseSquircleShape(exponent = 3.2f)

@Immutable
internal data class ArtworkFallbackStyle(
    val variantIndex: Int,
    val containerColor: Color,
    val badgeColor: Color,
    val emblemTint: Color,
    val rimColor: Color,
    val badgeShape: Shape,
    val emblemIcon: ImageVector
)

/**
 * Deterministically maps a track/album seed into one of 4 subtle, theme-synced M3 Expressive
 * tonal layers pre-blended over [ColorScheme.surfaceContainerHigh] via [Color.opaqueOver].
 */
internal fun resolveArtworkFallbackStyle(
    seed: String,
    colorScheme: ColorScheme
): ArtworkFallbackStyle {
    val variant = (seed.hashCode() and 0x7FFFFFFF) % 4
    val base = colorScheme.surfaceContainerHigh
    val rim = colorScheme.outlineVariant.copy(alpha = 0.25f)
    return when (variant) {
        0 -> {
            val bg = colorScheme.primaryContainer.opaqueOver(base, 0.42f)
            ArtworkFallbackStyle(
                variantIndex = 0,
                containerColor = bg,
                badgeColor = colorScheme.primaryContainer.opaqueOver(bg, 0.72f),
                emblemTint = colorScheme.onPrimaryContainer,
                rimColor = rim,
                badgeShape = FallbackScallopBadgeShape,
                emblemIcon = CadenceIcons.MusicNote
            )
        }

        1 -> {
            val bg = colorScheme.secondaryContainer.opaqueOver(base, 0.46f)
            ArtworkFallbackStyle(
                variantIndex = 1,
                containerColor = bg,
                badgeColor = colorScheme.secondaryContainer.opaqueOver(bg, 0.72f),
                emblemTint = colorScheme.onSecondaryContainer,
                rimColor = rim,
                badgeShape = FallbackSquircleBadgeShape,
                emblemIcon = CadenceIcons.Album
            )
        }

        2 -> {
            val bg = colorScheme.tertiaryContainer.opaqueOver(base, 0.42f)
            ArtworkFallbackStyle(
                variantIndex = 2,
                containerColor = bg,
                badgeColor = colorScheme.tertiaryContainer.opaqueOver(bg, 0.72f),
                emblemTint = colorScheme.onTertiaryContainer,
                rimColor = rim,
                badgeShape = FallbackScallopBadgeShape,
                emblemIcon = CadenceIcons.MusicNote
            )
        }

        else -> {
            val bg = colorScheme.surfaceContainerHighest.opaqueOver(base, 0.85f)
            ArtworkFallbackStyle(
                variantIndex = 3,
                containerColor = bg,
                badgeColor = colorScheme.primaryContainer.opaqueOver(bg, 0.48f),
                emblemTint = colorScheme.primary,
                rimColor = rim,
                badgeShape = FallbackSquircleBadgeShape,
                emblemIcon = CadenceIcons.Album
            )
        }
    }
}

/**
 * Shared artwork renderer used across Home, Library, Discover, Mini-Player, and Now Playing.
 * Renders real embedded cover art when available and falls back seamlessly to a cohesive,
 * theme-synced M3 Expressive music/vinyl placeholder whenever [artworkUri] is null, blank,
 * loading, or fails to decode (eliminating blank dark squares everywhere).
 */
@Composable
fun TrackArtwork(
    artworkUri: String?,
    seed: String,
    contentDescription: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    @DrawableRes artworkResId: Int? = null,
    decodeSizePx: Int = 512,
    showHairlineBorder: Boolean = false,
    trackId: String? = null
) {
    val performanceMode = LocalPerformanceMode.current
    val loadedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Box(modifier = modifier.clip(shape)) {
        when {
            artworkResId != null -> {
                Image(
                    painter = painterResource(id = artworkResId),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (showHairlineBorder) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, loadedBorderColor, shape)
                    )
                }
            }

            !artworkUri.isNullOrBlank() -> {
                val context = LocalContext.current
                val enableCrossfade = decodeSizePx > 256 && !performanceMode
                val model = remember(context, artworkUri, decodeSizePx, enableCrossfade) {
                    buildArtworkImageRequest(
                        context = context,
                        artworkPath = artworkUri,
                        decodeSizePx = decodeSizePx,
                        enableCrossfade = enableCrossfade
                    )
                }
                var imageLoaded by remember(artworkUri) { mutableStateOf(false) }
                var imageFailed by remember(artworkUri) { mutableStateOf(false) }

                if (!imageLoaded) {
                    ExpressiveArtworkFallback(
                        seed = seed,
                        contentDescription = contentDescription,
                        shape = shape,
                        trackId = trackId,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (!imageFailed) {
                    AsyncImage(
                        model = model,
                        contentDescription = if (imageLoaded) contentDescription else null,
                        contentScale = ContentScale.Crop,
                        onSuccess = {
                            imageLoaded = true
                            imageFailed = false
                        },
                        onError = {
                            imageFailed = true
                            imageLoaded = false
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (imageLoaded && showHairlineBorder) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, loadedBorderColor, shape)
                    )
                }
            }

            else -> {
                ExpressiveArtworkFallback(
                    seed = seed,
                    contentDescription = contentDescription,
                    shape = shape,
                    trackId = trackId,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Cohesive M3 Expressive fallback cover art placeholder with deterministic tonal surface blending,
 * crisp 1dp outline hairline rim, and a proportionally scaled (44%) centered music/vinyl badge.
 */
@Composable
fun ExpressiveArtworkFallback(
    seed: String,
    contentDescription: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    trackId: String? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val style = remember(seed, colorScheme) {
        resolveArtworkFallbackStyle(seed = seed, colorScheme = colorScheme)
    }
    val containerTag = if (trackId != null) "artwork_fallback_$trackId" else "artwork_fallback"
    val emblemTag = if (trackId != null) "artwork_fallback_emblem_$trackId" else "artwork_fallback_emblem"

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(style.containerColor)
            .border(1.dp, style.rimColor, shape)
            .semantics { this.contentDescription = contentDescription }
            .testTag(containerTag)
    ) {
        val minSide = minOf(maxWidth, maxHeight)
        val badgeSize = (minSide * 0.44f).coerceIn(20.dp, 116.dp)
        val iconSize = (badgeSize * 0.56f).coerceIn(12.dp, 64.dp)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(badgeSize)
                .clip(style.badgeShape)
                .background(style.badgeColor)
                .testTag(emblemTag)
        ) {
            Icon(
                imageVector = style.emblemIcon,
                contentDescription = null,
                tint = style.emblemTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
