package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.StableList
import com.example.data.Track
import com.example.data.intelligence.DiscoverCategory
import com.example.data.intelligence.DiscoverSection
import com.example.data.intelligence.DiscoverTuneConfig
import com.example.data.intelligence.ListeningInsights
import com.example.data.intelligence.MoodSmartPlaylist
import com.example.data.intelligence.RoutineMix
import com.example.data.intelligence.RoutineMixType
import com.example.data.intelligence.SmartMoodTag
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.FAST_THUMBNAIL_DECODE_PX
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.FourPointStarShape
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ScallopedCircleShape
import com.example.ui.theme.SoftCloverShape
import com.example.ui.theme.SuperellipseSquircleShape
import com.example.ui.theme.opaqueOver
import java.util.Locale

// Cached shapes for zero per-frame allocations on low-end GPUs
private val RoutineCardShape = RoundedCornerShape(28.dp)
private val RoutineArtShape = RoundedCornerShape(18.dp)
private val MoodTileShape = RoundedCornerShape(24.dp)
private val DailyMixCardShape = RoundedCornerShape(26.dp)
private val DailyMixArtShape = RoundedCornerShape(20.dp)
private val SmartMixCardShape = RoundedCornerShape(22.dp)
private val SmartMixArtShape = RoundedCornerShape(14.dp)
private val InsightTileShape = RoundedCornerShape(22.dp)

private val ExpressiveCloverBadge = SoftCloverShape(amplitudeFraction = 0.115f)
private val ExpressiveStarBadge = FourPointStarShape(indentationFraction = 0.20f)
private val ExpressiveScallopBadge = ScallopedCircleShape(lobes = 10, amplitudeFraction = 0.06f)
private val ExpressiveSquircleBadge = SuperellipseSquircleShape(exponent = 3.2f)

@Composable
internal fun DiscoverEditorialHeader(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.discover_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = colorScheme.onBackground,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .testTag("discover_header_title")
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DiscoverHeaderCircleButton(
                icon = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.action_search),
                onClick = onOpenSearch,
                modifier = Modifier.testTag("discover_search_button")
            )
            DiscoverHeaderCircleButton(
                icon = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.action_settings),
                onClick = onOpenSettings,
                modifier = Modifier.testTag("discover_settings_button")
            )
        }
    }
}

@Composable
private fun DiscoverHeaderCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colorScheme.surfaceContainerHigh,
        contentColor = colorScheme.onSurface,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// --- Section 1: For This Moment (Context-Aware Routine Mixes) ---

@Composable
internal fun DiscoverForThisMomentShelf(
    routineMixes: List<RoutineMix>,
    artworkShape: ExpressiveArtworkShape,
    onPlayRoutineMix: (RoutineMix) -> Unit,
    onInspectInTuneMix: (RoutineMix) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover_routine_mixes_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.discover_routine_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_routine_mixes_header")
            )
            Text(
                text = stringResource(R.string.discover_routine_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_routine_mixes_subtitle")
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("discover_routine_mixes_row")
        ) {
            items(
                items = routineMixes,
                key = { mix -> "routine_${mix.type.id}" },
                contentType = { "discover_routine_card" }
            ) { mix ->
                DiscoverForThisMomentCard(
                    mix = mix,
                    artworkShape = artworkShape,
                    onPlayMix = { onPlayRoutineMix(mix) },
                    onInspectTuneMix = { onInspectInTuneMix(mix) }
                )
            }
        }
    }
}

@Composable
private fun DiscoverForThisMomentCard(
    mix: RoutineMix,
    artworkShape: ExpressiveArtworkShape,
    onPlayMix: () -> Unit,
    onInspectTuneMix: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val high = colorScheme.surfaceContainerHigh
    val cardColor = remember(mix.type, mix.isActiveNow, colorScheme) {
        if (mix.isActiveNow) {
            colorScheme.primaryContainer.opaqueOver(high, 0.30f)
        } else {
            when (mix.type) {
                RoutineMixType.LATE_NIGHT_WIND_DOWN -> colorScheme.secondaryContainer.opaqueOver(high, 0.30f)
                RoutineMixType.MORNING_FOCUS -> colorScheme.tertiaryContainer.opaqueOver(high, 0.30f)
                RoutineMixType.AFTERNOON_MOMENTUM -> colorScheme.primaryContainer.opaqueOver(high, 0.22f)
                RoutineMixType.EVENING_UNWIND -> colorScheme.secondaryContainer.opaqueOver(high, 0.24f)
                RoutineMixType.WEEKEND_ENERGY -> colorScheme.tertiaryContainer.opaqueOver(high, 0.28f)
            }
        }
    }
    val borderColor = if (mix.isActiveNow) {
        colorScheme.primary.copy(alpha = 0.26f)
    } else {
        colorScheme.outlineVariant.copy(alpha = 0.26f)
    }
    val badgeBg = remember(mix.isActiveNow, cardColor, colorScheme) {
        if (mix.isActiveNow) {
            colorScheme.primary.opaqueOver(cardColor, 0.16f)
        } else {
            colorScheme.surfaceContainerHighest.opaqueOver(cardColor, 0.92f)
        }
    }
    val routineIcon = when (mix.type) {
        RoutineMixType.LATE_NIGHT_WIND_DOWN -> CadenceIcons.NightsStay
        RoutineMixType.MORNING_FOCUS -> CadenceIcons.LightMode
        RoutineMixType.AFTERNOON_MOMENTUM -> CadenceIcons.Waves
        RoutineMixType.EVENING_UNWIND -> CadenceIcons.Spa
        RoutineMixType.WEEKEND_ENERGY -> CadenceIcons.Bolt
    }
    val badgeShape: Shape = when (mix.type) {
        RoutineMixType.LATE_NIGHT_WIND_DOWN -> ExpressiveScallopBadge
        RoutineMixType.MORNING_FOCUS -> ExpressiveStarBadge
        RoutineMixType.AFTERNOON_MOMENTUM -> ExpressiveCloverBadge
        RoutineMixType.EVENING_UNWIND -> ExpressiveSquircleBadge
        RoutineMixType.WEEKEND_ENERGY -> ExpressiveStarBadge
    }
    val leadTrack = mix.tracks.firstOrNull()
    val trackCountText = if (mix.tracks.size == 1) {
        stringResource(R.string.discover_mood_track_count_singular, mix.tracks.size)
    } else {
        stringResource(R.string.discover_mood_track_count_plural, mix.tracks.size)
    }

    Surface(
        shape = RoutineCardShape,
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .width(312.dp)
            .testTag("discover_routine_card_${mix.type.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar: 36.dp shaped badge + ACTIVE NOW pill + "${count} tracks >" action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = badgeShape,
                    color = if (mix.isActiveNow) colorScheme.primary else colorScheme.secondaryContainer,
                    contentColor = if (mix.isActiveNow) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = routineIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = badgeBg,
                    border = BorderStroke(
                        1.dp,
                        if (mix.isActiveNow) colorScheme.primary.copy(alpha = 0.28f) else colorScheme.outlineVariant.copy(alpha = 0.30f)
                    ),
                    modifier = Modifier.testTag("discover_routine_badge_${mix.type.id}")
                ) {
                    Text(
                        text = if (mix.isActiveNow) {
                            stringResource(R.string.discover_routine_active_now)
                        } else {
                            mix.timeWindowLabel
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (mix.isActiveNow) colorScheme.primary else colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onInspectTuneMix)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("discover_routine_count_${mix.type.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = trackCountText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Middle: 76.dp rounded-18.dp cover art beside mix title & 2-line description
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (leadTrack != null) {
                    ExpressiveArtworkFrame(
                        track = leadTrack,
                        artworkShape = artworkShape,
                        isPlaying = false,
                        animateScale = false,
                        decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                        shapeOverride = RoutineArtShape,
                        showHairlineBorder = true,
                        modifier = Modifier
                            .size(76.dp)
                            .testTag("discover_routine_art_${mix.type.id}")
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = mix.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("discover_routine_title_${mix.type.id}")
                    )
                    Text(
                        text = mix.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom equal-width pills: Play Mix + Tune Mix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onPlayMix)
                        .testTag("discover_routine_play_${mix.type.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.discover_routine_play_mix),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHighest.opaqueOver(cardColor, 0.94f),
                    contentColor = colorScheme.onSurface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.30f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onInspectTuneMix)
                        .testTag("discover_routine_tune_${mix.type.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = CadenceIcons.Tune,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.discover_routine_inspect_tune),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

// --- Section 2: Mood Playlists (132.dp × 132.dp Expressive Mood Tiles) ---

@Composable
internal fun DiscoverMoodPlaylistsEditorialShelf(
    playlists: List<MoodSmartPlaylist>,
    onPlayMoodPlaylist: (StableList<Track>) -> Unit,
    onSeeAllMoods: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val high = colorScheme.surfaceContainerHigh
    val lateNightSurface = remember(colorScheme.secondaryContainer, high) {
        colorScheme.secondaryContainer.opaqueOver(high, 0.38f)
    }
    val acousticSurface = remember(colorScheme.tertiaryContainer, high) {
        colorScheme.tertiaryContainer.opaqueOver(high, 0.38f)
    }
    val soulfulSurface = remember(colorScheme.primaryContainer, high) {
        colorScheme.primaryContainer.opaqueOver(high, 0.34f)
    }
    val upbeatSurface = remember(colorScheme.surfaceContainerHighest, high) {
        colorScheme.surfaceContainerHighest.opaqueOver(high, 0.84f)
    }
    val pillBg = remember(colorScheme.surfaceContainerHigh, colorScheme.background) {
        colorScheme.surfaceContainerHigh.opaqueOver(colorScheme.background, 0.94f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover_mood_playlists_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = CadenceIcons.AutoAwesome,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.discover_mood_playlists_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("discover_mood_playlists_header")
            )
            Surface(
                shape = CircleShape,
                color = pillBg,
                contentColor = colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.24f)),
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onSeeAllMoods)
                    .testTag("discover_mood_playlists_see_all")
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_see_all),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("discover_mood_playlists_row")
        ) {
            items(
                items = playlists,
                key = { playlist -> "discover_mood_${playlist.mood.name}" },
                contentType = { "discover_mood_card" }
            ) { playlist ->
                val (tileSurface, badgeBg, badgeTint, icon, badgeShape) = when (playlist.mood) {
                    SmartMoodTag.LATE_NIGHT -> MoodTileVisuals(
                        surface = lateNightSurface,
                        badgeBg = colorScheme.secondary,
                        badgeTint = colorScheme.onSecondary,
                        icon = CadenceIcons.NightsStay,
                        shape = ExpressiveScallopBadge
                    )
                    SmartMoodTag.ACOUSTIC -> MoodTileVisuals(
                        surface = acousticSurface,
                        badgeBg = colorScheme.tertiary,
                        badgeTint = colorScheme.onTertiary,
                        icon = CadenceIcons.Spa,
                        shape = ExpressiveCloverBadge
                    )
                    SmartMoodTag.SOULFUL -> MoodTileVisuals(
                        surface = soulfulSurface,
                        badgeBg = colorScheme.primary,
                        badgeTint = colorScheme.onPrimary,
                        icon = Icons.Rounded.Favorite,
                        shape = ExpressiveStarBadge
                    )
                    SmartMoodTag.UPBEAT -> MoodTileVisuals(
                        surface = upbeatSurface,
                        badgeBg = colorScheme.primaryContainer,
                        badgeTint = colorScheme.onPrimaryContainer,
                        icon = CadenceIcons.Bolt,
                        shape = ExpressiveSquircleBadge
                    )
                    SmartMoodTag.LOSSLESS_HIFI -> MoodTileVisuals(
                        surface = acousticSurface,
                        badgeBg = colorScheme.tertiaryContainer,
                        badgeTint = colorScheme.onTertiaryContainer,
                        icon = CadenceIcons.HighQuality,
                        shape = ExpressiveScallopBadge
                    )
                }
                val onClick = remember(playlist.tracks, onPlayMoodPlaylist) {
                    { onPlayMoodPlaylist(playlist.tracks) }
                }

                DiscoverMoodTileCard(
                    playlist = playlist,
                    visuals = MoodTileVisuals(tileSurface, badgeBg, badgeTint, icon, badgeShape),
                    onClick = onClick
                )
            }
        }
    }
}

private data class MoodTileVisuals(
    val surface: Color,
    val badgeBg: Color,
    val badgeTint: Color,
    val icon: ImageVector,
    val shape: Shape
)

@Composable
private fun DiscoverMoodTileCard(
    playlist: MoodSmartPlaylist,
    visuals: MoodTileVisuals,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val countLabel = if (playlist.totalMatchCount == 1) {
        stringResource(R.string.discover_mood_track_count_singular, playlist.totalMatchCount)
    } else {
        stringResource(R.string.discover_mood_track_count_plural, playlist.totalMatchCount)
    }
    val playDescription = stringResource(R.string.discover_mood_play_cd, playlist.title)
    val playChipBg = remember(colorScheme.surface, visuals.surface) {
        colorScheme.surface.opaqueOver(visuals.surface, 0.42f)
    }

    Surface(
        shape = MoodTileShape,
        color = visuals.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.24f)),
        modifier = Modifier
            .size(132.dp)
            .clip(MoodTileShape)
            .clickable(onClickLabel = playDescription, onClick = onClick)
            .testTag("discover_mood_card_${playlist.mood.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = visuals.shape,
                    color = visuals.badgeBg,
                    contentColor = visuals.badgeTint,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = visuals.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = playChipBg,
                    contentColor = colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = playDescription,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- Section 3: Made for you (Featured Daily Mix Card) ---

@Composable
internal fun DiscoverMadeForYouSection(
    section: DiscoverSection,
    topArtist: String,
    tuneConfig: DiscoverTuneConfig,
    artworkShape: ExpressiveArtworkShape,
    onPlayAll: () -> Unit,
    onTuneMix: () -> Unit,
    onResetTune: () -> Unit,
    onSavePlaylist: () -> Unit,
    onRefreshMix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val high = colorScheme.surfaceContainerHigh
    val cardColor = remember(colorScheme.primaryContainer, high) {
        colorScheme.primaryContainer.opaqueOver(high, 0.28f)
    }
    val pillBg = remember(colorScheme.primary, cardColor) {
        colorScheme.primary.opaqueOver(cardColor, 0.16f)
    }
    val tuneBtnBg = remember(tuneConfig.isCustomized, colorScheme, cardColor) {
        if (tuneConfig.isCustomized) {
            colorScheme.primary
        } else {
            colorScheme.surfaceContainerHighest.opaqueOver(cardColor, 0.94f)
        }
    }
    val leadTrack = section.tracks.firstOrNull()
    val artistLabel = topArtist.ifBlank { leadTrack?.artist.orEmpty() }.ifBlank { "your library" }
    val shapedSubtitle = stringResource(R.string.discover_daily_mix_shaped_by, artistLabel)
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover_section_${section.category.id}"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = CadenceIcons.AutoAwesome,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.discover_made_for_you_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("discover_made_for_you_header")
            )
            Surface(
                shape = CircleShape,
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .clickable(enabled = section.tracks.isNotEmpty(), role = Role.Button, onClick = onPlayAll)
                    .testTag("discover_play_all_${section.category.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.action_play_all),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Surface(
            shape = DailyMixCardShape,
            color = cardColor,
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.22f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(DailyMixCardShape)
                .clickable(enabled = section.tracks.isNotEmpty(), onClick = onPlayAll)
                .testTag("discover_daily_mix_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (leadTrack != null) {
                    ExpressiveArtworkFrame(
                        track = leadTrack,
                        artworkShape = artworkShape,
                        isPlaying = false,
                        animateScale = false,
                        decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                        shapeOverride = DailyMixArtShape,
                        showHairlineBorder = true,
                        modifier = Modifier
                            .size(104.dp)
                            .testTag("discover_daily_mix_art")
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = pillBg,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("discover_daily_mix_pill")
                        ) {
                            Text(
                                text = section.badgeText.ifBlank { stringResource(R.string.discover_daily_mix_pill) },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("discover_daily_mix_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_more_options),
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_play_all)) },
                                    onClick = {
                                        menuExpanded = false
                                        onPlayAll()
                                    },
                                    modifier = Modifier.testTag("discover_daily_mix_play")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.discover_save_mix)) },
                                    onClick = {
                                        menuExpanded = false
                                        onSavePlaylist()
                                    },
                                    modifier = Modifier.testTag("discover_daily_mix_save")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.smart_mix_refresh)) },
                                    onClick = {
                                        menuExpanded = false
                                        onRefreshMix()
                                    },
                                    modifier = Modifier.testTag("discover_daily_mix_refresh")
                                )
                            }
                        }
                    }

                    Text(
                        text = shapedSubtitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("discover_daily_mix_subtitle")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = tuneBtnBg,
                            contentColor = if (tuneConfig.isCustomized) colorScheme.onPrimary else colorScheme.onSurface,
                            border = if (tuneConfig.isCustomized) null else BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.28f)),
                            modifier = Modifier
                                .height(34.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClick = onTuneMix)
                                .testTag("discover_tune_daily_mix_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = CadenceIcons.Tune,
                                    contentDescription = null,
                                    tint = if (tuneConfig.isCustomized) colorScheme.onPrimary else colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (tuneConfig.isCustomized) {
                                        formatTuneBadgeLabel(tuneConfig)
                                    } else {
                                        stringResource(R.string.discover_tune_mix)
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (tuneConfig.isCustomized) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.surfaceContainerHigh,
                                contentColor = colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClick = onResetTune)
                                    .testTag("discover_reset_tune_${section.category.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.discover_reset_tune),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Section 4: Smart Mix (Wide Compact Mix Cards) ---

@Immutable
internal data class SmartMixCardItem(
    val id: String,
    val title: String,
    val moodTagsLine: String,
    val coverTrack: Track,
    val tracks: StableList<Track>
)

internal fun buildSmartMixCards(
    smartSection: DiscoverSection,
    moodPlaylists: List<MoodSmartPlaylist>,
    routineMixes: List<RoutineMix>
): List<SmartMixCardItem> {
    val cards = mutableListOf<SmartMixCardItem>()
    val leadTrack = smartSection.tracks.firstOrNull()
    if (leadTrack != null) {
        cards += SmartMixCardItem(
            id = "primary_smart",
            title = deriveSmartMixTitle(smartSection),
            moodTagsLine = deriveThreeMoodTags(smartSection.tracks),
            coverTrack = leadTrack,
            tracks = smartSection.tracks
        )
    }

    // Add up to 2 complementary compact mixes from live mood playlists or routines
    moodPlaylists.forEach { mood ->
        if (cards.size < 3 && mood.tracks.isNotEmpty()) {
            val cover = mood.tracks.first()
            val title = when (mood.mood) {
                SmartMoodTag.LATE_NIGHT -> "Evening Vibes"
                SmartMoodTag.ACOUSTIC -> "Acoustic Unwind"
                SmartMoodTag.SOULFUL -> "Warm Soul Mix"
                SmartMoodTag.UPBEAT -> "Energy Boost Mix"
                SmartMoodTag.LOSSLESS_HIFI -> "Hi-Fi Studio Mix"
            }
            if (cards.none { it.title.equals(title, ignoreCase = true) }) {
                cards += SmartMixCardItem(
                    id = "mood_${mood.mood.id}",
                    title = title,
                    moodTagsLine = deriveThreeMoodTags(mood.tracks, fallbackMood = mood.mood),
                    coverTrack = cover,
                    tracks = mood.tracks
                )
            }
        }
    }

    if (cards.size < 2) {
        routineMixes.drop(1).forEach { routine ->
            if (cards.size < 3 && routine.tracks.isNotEmpty()) {
                val cover = routine.tracks.first()
                if (cards.none { it.title.equals(routine.title, ignoreCase = true) }) {
                    cards += SmartMixCardItem(
                        id = "routine_${routine.type.id}",
                        title = routine.title,
                        moodTagsLine = deriveThreeMoodTags(routine.tracks),
                        coverTrack = cover,
                        tracks = routine.tracks
                    )
                }
            }
        }
    }

    return cards
}

private fun deriveSmartMixTitle(section: DiscoverSection): String {
    val cleanTitle = section.title.trim()
    return if (cleanTitle.equals("Smart Mix", ignoreCase = true) || cleanTitle.isEmpty()) {
        "Chill Focus Mix"
    } else {
        cleanTitle
    }
}

private fun deriveThreeMoodTags(
    tracks: List<Track>,
    fallbackMood: SmartMoodTag? = null
): String {
    val tags = LinkedHashSet<String>(4)
    tracks.asSequence()
        .map { it.genre.trim() }
        .filter { it.isNotBlank() && !it.equals("Audio", ignoreCase = true) && !it.equals("Unknown", ignoreCase = true) }
        .forEach { raw ->
            val shortTag = raw.split('/', ',', ';').firstOrNull()?.trim().orEmpty()
            if (shortTag.isNotBlank() && shortTag.length <= 14) {
                tags.add(shortTag.replaceFirstChar { it.titlecase(Locale.US) })
            }
        }
    val avgBpm = tracks.map { it.bpm }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 92.0
    if (fallbackMood != null) {
        when (fallbackMood) {
            SmartMoodTag.LATE_NIGHT -> {
                tags.add("Chill")
                tags.add("Nocturnal")
                tags.add("Ambient")
            }
            SmartMoodTag.ACOUSTIC -> {
                tags.add("Acoustic")
                tags.add("Warm")
                tags.add("Organic")
            }
            SmartMoodTag.SOULFUL -> {
                tags.add("Soulful")
                tags.add("Smooth")
                tags.add("Groove")
            }
            SmartMoodTag.UPBEAT -> {
                tags.add("Upbeat")
                tags.add("Energetic")
                tags.add("Pulse")
            }
            SmartMoodTag.LOSSLESS_HIFI -> {
                tags.add("Lossless")
                tags.add("Studio")
                tags.add("Hi-Fi")
            }
        }
    } else {
        when {
            avgBpm < 88.0 -> {
                tags.add("Lo-fi")
                tags.add("Chill")
                tags.add("Focus")
            }
            avgBpm > 116.0 -> {
                tags.add("Upbeat")
                tags.add("Flow")
                tags.add("Rhythm")
            }
            else -> {
                tags.add("Chill")
                tags.add("Focus")
                tags.add("Vibes")
            }
        }
    }
    return tags.take(3).joinToString(" · ")
}

@Composable
internal fun DiscoverSmartMixEditorialSection(
    section: DiscoverSection,
    moodPlaylists: List<MoodSmartPlaylist>,
    routineMixes: List<RoutineMix>,
    artworkShape: ExpressiveArtworkShape,
    onPlayAll: () -> Unit,
    onPlayMixCard: (StableList<Track>) -> Unit,
    onTuneSmartMix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val high = colorScheme.surfaceContainerHigh
    val cardColor = remember(colorScheme.secondaryContainer, high) {
        colorScheme.secondaryContainer.opaqueOver(high, 0.24f)
    }
    val playBtnBg = remember(colorScheme.primary, cardColor) {
        colorScheme.primary.opaqueOver(cardColor, 0.16f)
    }
    val tuneChipBg = remember(colorScheme.surfaceContainerHigh, colorScheme.background) {
        colorScheme.surfaceContainerHigh.opaqueOver(colorScheme.background, 0.94f)
    }
    val mixCards = remember(section, moodPlaylists, routineMixes) {
        buildSmartMixCards(section, moodPlaylists, routineMixes)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover_section_${section.category.id}"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = CadenceIcons.GraphicEq,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.discover_smart_mix_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("discover_smart_mix_header")
            )

            Surface(
                shape = CircleShape,
                color = tuneChipBg,
                contentColor = colorScheme.onSurface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.24f)),
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onTuneSmartMix)
                    .testTag("discover_tune_smart_mix_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = CadenceIcons.Tune,
                        contentDescription = stringResource(R.string.discover_tune_mix),
                        tint = colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Tune",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .clickable(enabled = section.tracks.isNotEmpty(), role = Role.Button, onClick = onPlayAll)
                    .testTag("discover_play_all_${section.category.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.action_play_all),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("discover_smart_mix_row")
        ) {
            itemsIndexed(
                items = mixCards,
                key = { _, item -> "smart_mix_card_${item.id}" },
                contentType = { _, _ -> "smart_mix_compact_card" }
            ) { index, item ->
                val onPlayItem = remember(item.tracks, onPlayMixCard) {
                    { onPlayMixCard(item.tracks) }
                }
                Surface(
                    shape = SmartMixCardShape,
                    color = cardColor,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.24f)),
                    modifier = Modifier
                        .width(250.dp)
                        .clip(SmartMixCardShape)
                        .clickable(onClick = onPlayItem)
                        .testTag("discover_smart_mix_card_$index")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExpressiveArtworkFrame(
                            track = item.coverTrack,
                            artworkShape = artworkShape,
                            isPlaying = false,
                            animateScale = false,
                            decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                            shapeOverride = SmartMixArtShape,
                            showHairlineBorder = true,
                            modifier = Modifier.size(56.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.moodTagsLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = playBtnBg,
                            contentColor = colorScheme.primary,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClick = onPlayItem)
                                .testTag("discover_smart_mix_play_$index")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.action_play),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Section 5: Listening Insights (2×2 Real-Data Bento Grid) ---

@Composable
internal fun DiscoverListeningInsightsSection(
    insights: ListeningInsights,
    onExpandInsights: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val high = colorScheme.surfaceContainerHigh
    val moodTileSurface = remember(colorScheme.primaryContainer, high) {
        colorScheme.primaryContainer.opaqueOver(high, 0.22f)
    }
    val artistTileSurface = remember(colorScheme.secondaryContainer, high) {
        colorScheme.secondaryContainer.opaqueOver(high, 0.22f)
    }
    val timeTileSurface = remember(high, bg) {
        high.opaqueOver(bg, 0.94f)
    }
    val hifiTileSurface = remember(colorScheme.tertiaryContainer, high) {
        colorScheme.tertiaryContainer.opaqueOver(high, 0.24f)
    }
    val chevronBg = remember(high, bg) {
        high.opaqueOver(bg, 0.94f)
    }
    val totalListeningFormatted = remember(insights.totalListeningMs) {
        formatEditorialListeningDuration(insights.totalListeningMs)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("discover_listening_insights_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = CadenceIcons.Lightbulb,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.discover_insights_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("discover_listening_insights_header")
            )
            Surface(
                shape = CircleShape,
                color = chevronBg,
                contentColor = colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.24f)),
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onExpandInsights)
                    .testTag("discover_insights_expand_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.discover_insights_details_title),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EditorialInsightBentoTile(
                    label = "Top Mood",
                    value = insights.topMood.ifBlank { "Balanced" },
                    subvalue = insights.topGenre.ifBlank { "Local library" },
                    tileSurface = moodTileSurface,
                    leadingBadge = {
                        Icon(
                            imageVector = CadenceIcons.TrendingUp,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("insight_top_mood")
                )
                EditorialInsightBentoTile(
                    label = "Top Artist",
                    value = insights.topArtist.ifBlank { "Various" },
                    subvalue = "${insights.completedPlaysTotal} full listens",
                    tileSurface = artistTileSurface,
                    leadingBadge = {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("insight_top_artist")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EditorialInsightBentoTile(
                    label = "Listening Time",
                    value = totalListeningFormatted,
                    subvalue = "${insights.totalTrackCount} tracks",
                    tileSurface = timeTileSurface,
                    leadingBadge = {
                        Icon(
                            imageVector = CadenceIcons.Schedule,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("insight_listening_time")
                )
                EditorialInsightBentoTile(
                    label = "Hi-Fi Library",
                    value = "${insights.hiFiPercentage}%",
                    subvalue = "${insights.hiFiTrackCount}/${insights.totalTrackCount} lossless",
                    tileSurface = hifiTileSurface,
                    leadingBadge = {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.tertiaryContainer,
                            contentColor = colorScheme.onTertiaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.badge_hifi_short),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("insight_hifi_percent")
                )
            }
        }
    }
}

@Composable
private fun EditorialInsightBentoTile(
    label: String,
    value: String,
    subvalue: String,
    tileSurface: Color,
    leadingBadge: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = InsightTileShape,
        color = tileSurface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.24f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                leadingBadge()
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subvalue,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ListeningInsightsDetailSheetContent(
    insights: ListeningInsights,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val totalListeningFormatted = remember(insights.totalListeningMs) {
        formatEditorialListeningDuration(insights.totalListeningMs)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = ExpressiveCloverBadge,
                color = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = CadenceIcons.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(R.string.discover_insights_details_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.discover_insights_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = InsightTileShape,
            color = colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightDetailRow("Top Mood", insights.topMood.ifBlank { "Balanced" })
                InsightDetailRow("Top Genre", insights.topGenre.ifBlank { "Local library" })
                InsightDetailRow("Top Artist", insights.topArtist.ifBlank { "Various" })
                InsightDetailRow("Listening Time", totalListeningFormatted)
                InsightDetailRow("Completed Plays", "${insights.completedPlaysTotal}")
                InsightDetailRow(
                    "Lossless & Hi-Fi",
                    "${insights.hiFiTrackCount}/${insights.totalTrackCount} (${insights.hiFiPercentage}%)"
                )
            }
        }
    }
}

@Composable
private fun InsightDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatTuneBadgeLabel(config: DiscoverTuneConfig): String {
    val parts = buildList {
        config.selectedMood?.let { mood ->
            add(
                mood.replace('_', ' ')
                    .split(' ')
                    .joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.US) } }
            )
        }
        config.energyFilter?.let { e ->
            add("${e.replaceFirstChar { it.titlecase(Locale.US) }} Energy")
        }
        if (config.balanceLabel != "Balanced") {
            add(config.balanceLabel)
        }
    }
    return if (parts.isEmpty()) "Tuned" else parts.joinToString(" · ")
}

internal fun formatEditorialListeningDuration(totalMs: Long): String {
    if (totalMs <= 0L) return "0m"
    val totalMinutes = (totalMs / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}
