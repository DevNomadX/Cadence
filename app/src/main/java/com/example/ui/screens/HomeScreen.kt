package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LibraryCategory
import com.example.data.StableList
import com.example.data.Track
import com.example.data.intelligence.MoodSmartPlaylist
import com.example.data.intelligence.SmartMoodTag
import com.example.playback.PlaybackProgress
import com.example.ui.components.AudioPulseBars
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.FAST_THUMBNAIL_DECODE_PX
import com.example.ui.icons.CadenceIcons
import com.example.ui.player.HomeUiState
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ScallopedCircleShape
import com.example.ui.theme.SoftCloverShape
import com.example.ui.theme.SuperellipseSquircleShape
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.os.Build
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalTime
import java.util.Calendar
import kotlin.math.abs
import kotlin.random.Random

private val IdleHomeProgressFlow: StateFlow<PlaybackProgress> = MutableStateFlow(PlaybackProgress())

private val BentoTileShape = RoundedCornerShape(22.dp)
private val DailyPickCoverShape = RoundedCornerShape(20.dp)
private val GalleryCoverShape = RoundedCornerShape(24.dp)
private val MoodCardShape = RoundedCornerShape(24.dp)
private val MoodArtworkShape = RoundedCornerShape(16.dp)

// Expressive geometric icon badge shapes for the 2x2 Bento Library Grid
private val FavoritesBadgeShape = ScallopedCircleShape(lobes = 10, amplitudeFraction = 0.06f)
private val TopRotationBadgeShape = SoftCloverShape(amplitudeFraction = 0.115f)
private val NewAddsBadgeShape = SuperellipseSquircleShape(exponent = 3.2f)
private val ShuffleBadgeShape = CircleShape

@Immutable
internal data class HomeLibraryCardPalette(
    val topColor: Color,
    val bottomColor: Color,
    val borderColor: Color,
    val iconContainerColor: Color,
    val iconTint: Color,
    val titleColor: Color,
    val subtitleColor: Color
)

@Immutable
internal data class HomeLibraryGridColors(
    val favorites: HomeLibraryCardPalette,
    val topRotation: HomeLibraryCardPalette,
    val newAdds: HomeLibraryCardPalette,
    val shuffleAll: HomeLibraryCardPalette
)

internal fun resolveHomeLibraryGridColors(colorScheme: ColorScheme): HomeLibraryGridColors {
    val high = colorScheme.surfaceContainerHigh
    val mid = colorScheme.surfaceContainer
    val bg = colorScheme.background
    val calmBorder = colorScheme.outlineVariant.copy(alpha = 0.25f)
    val shuffleTop = colorScheme.surfaceContainerHighest.opaqueOver(high, 0.72f)
    val shuffleBottom = colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.98f)
    return HomeLibraryGridColors(
        favorites = HomeLibraryCardPalette(
            topColor = colorScheme.primary.opaqueOver(high, 0.12f),
            bottomColor = colorScheme.primary.opaqueOver(mid, 0.10f),
            borderColor = calmBorder,
            iconContainerColor = colorScheme.primary,
            iconTint = colorScheme.onPrimary,
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.onSurfaceVariant
        ),
        topRotation = HomeLibraryCardPalette(
            topColor = colorScheme.tertiary.opaqueOver(high, 0.12f),
            bottomColor = colorScheme.tertiary.opaqueOver(mid, 0.10f),
            borderColor = calmBorder,
            iconContainerColor = colorScheme.tertiary,
            iconTint = colorScheme.onTertiary,
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.onSurfaceVariant
        ),
        newAdds = HomeLibraryCardPalette(
            topColor = colorScheme.secondary.opaqueOver(high, 0.12f),
            bottomColor = colorScheme.secondary.opaqueOver(mid, 0.10f),
            borderColor = calmBorder,
            iconContainerColor = colorScheme.secondary,
            iconTint = colorScheme.onSecondary,
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.onSurfaceVariant
        ),
        shuffleAll = HomeLibraryCardPalette(
            topColor = shuffleTop,
            bottomColor = shuffleBottom,
            borderColor = calmBorder,
            iconContainerColor = colorScheme.primary.opaqueOver(shuffleTop, 0.16f),
            iconTint = colorScheme.primary,
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.onSurfaceVariant
        )
    )
}

/**
 * Returns the device's current local hour of day in `0..23`, using `LocalTime.now().hour` on API 26+
 * with a `Calendar` fallback for API 24–25.
 */
internal fun currentLocalHourOfDay(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalTime.now().hour
    } else {
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
}

/**
 * Formats the clean time-of-day Home greeting across all 24 hours:
 * - `00:00 – 04:59`: "Late night vibes"
 * - `05:00 – 11:59`: "Good morning!"
 * - `12:00 – 16:59`: "Good afternoon!"
 * - `17:00 – 20:59`: "Good evening!"
 * - `21:00 – 23:59`: "Good night!"
 */
fun formatHomeGreeting(
    hourOfDay: Int = currentLocalHourOfDay()
): String {
    val normalizedHour = ((hourOfDay % 24) + 24) % 24
    return when (normalizedHour) {
        in 0..4 -> "Late night vibes"
        in 5..11 -> "Good morning!"
        in 12..16 -> "Good afternoon!"
        in 17..20 -> "Good evening!"
        else -> "Good night!"
    }
}

/**
 * Styles the Home greeting with [baseColor] for the leading text and [accentColor] (`primary`)
 * for the final word (e.g., "Late night " + "vibes", "Good " + "morning!").
 */
internal fun formatHighlightedHomeGreeting(
    greeting: String,
    baseColor: Color,
    accentColor: Color
): AnnotatedString {
    val trimmed = greeting.trim()
    if (trimmed.isEmpty()) return AnnotatedString("")
    val lastSpace = trimmed.lastIndexOf(' ')
    return buildAnnotatedString {
        if (lastSpace <= 0 || lastSpace >= trimmed.lastIndex) {
            withStyle(SpanStyle(color = accentColor)) {
                append(trimmed)
            }
        } else {
            withStyle(SpanStyle(color = baseColor)) {
                append(trimmed.substring(0, lastSpace + 1))
            }
            withStyle(SpanStyle(color = accentColor)) {
                append(trimmed.substring(lastSpace + 1))
            }
        }
    }
}

/**
 * Returns the time- and context-aware pool of concise 1-line Home screen subheadings.
 * Every candidate is intentionally kept compact so it never truncates with "..." on 360.dp screens.
 */
internal fun homeSubtitleCandidates(
    hourOfDay: Int = currentLocalHourOfDay(),
    trackCount: Int = 0,
    hasLosslessTracks: Boolean = false
): List<String> {
    val normalizedHour = ((hourOfDay % 24) + 24) % 24
    return when (normalizedHour) {
        in 0..4 -> buildList {
            add("Quiet tracks for midnight")
            add("Low-light listening")
            add("Your late-night rotation")
            if (hasLosslessTracks) add("Soft after-hours lossless")
        }
        in 5..11 -> buildList {
            add("Set your morning tempo")
            add("Fresh tracks for today")
            add("Morning soundcheck ready")
            if (trackCount > 0) add("$trackCount tracks ready today")
        }
        in 12..16 -> buildList {
            if (trackCount > 0) add("$trackCount offline tracks ready")
            if (hasLosslessTracks) add("Your lossless collection")
            add("From your local library")
            add("Steady afternoon rhythm")
        }
        in 17..20 -> buildList {
            if (hasLosslessTracks) add("Your lossless collection")
            if (trackCount > 0) add("$trackCount offline tracks ready")
            add("From your local library")
            add("Ease into the evening")
        }
        else -> buildList {
            add("Low-light listening")
            add("Your late-night rotation")
            if (hasLosslessTracks) add("Warm lossless acoustics")
            else add("Soft grooves for tonight")
        }
    }
}

/**
 * Picks a deterministic, flicker-free 1-line Home subtitle for the current session, hour, and library state.
 */
fun formatHomeSubtitle(
    hourOfDay: Int = currentLocalHourOfDay(),
    trackCount: Int = 0,
    hasLosslessTracks: Boolean = false,
    sessionSeed: Int = 0
): String {
    val candidates = homeSubtitleCandidates(
        hourOfDay = hourOfDay,
        trackCount = trackCount,
        hasLosslessTracks = hasLosslessTracks
    )
    val normalizedHour = ((hourOfDay % 24) + 24) % 24
    val index = abs(sessionSeed + normalizedHour * 31) % candidates.size
    return candidates[index]
}

@Immutable
internal data class HomeMoodDisplay(
    val title: String,
    val subtitle: String,
    val topColor: Color,
    val bottomColor: Color,
    val accentColor: Color,
    val primaryText: Color,
    val secondaryText: Color
)

private data class MoodPalette(
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val primary: Color,
    val secondary: Color
)

internal fun resolveHomeMoodDisplay(
    playlist: MoodSmartPlaylist,
    colorScheme: ColorScheme
): HomeMoodDisplay {
    val hasCustomTitle = playlist.title.isNotBlank() && playlist.title != playlist.mood.label
    val defaultTitle = when (playlist.mood) {
        SmartMoodTag.LATE_NIGHT -> "Nostalgic Nights"
        SmartMoodTag.ACOUSTIC -> "Acoustic Sessions"
        SmartMoodTag.SOULFUL -> "Soulful Warmth"
        SmartMoodTag.UPBEAT -> "Upbeat Energy"
        SmartMoodTag.LOSSLESS_HIFI -> "Studio Lossless"
    }
    val defaultSubtitle = when (playlist.mood) {
        SmartMoodTag.LATE_NIGHT -> "Melodies that stay with you"
        SmartMoodTag.ACOUSTIC -> "Warm strings & stripped-back takes"
        SmartMoodTag.SOULFUL -> "Rich vocals & timeless grooves"
        SmartMoodTag.UPBEAT -> "Bright rhythms to lift the room"
        SmartMoodTag.LOSSLESS_HIFI -> "Uncompressed master-grade sound"
    }
    val bg = colorScheme.background
    val low = colorScheme.surfaceContainerLow
    val highest = colorScheme.surfaceContainerHighest
    val (top, bottom, accent, primary, secondary) = when (playlist.mood) {
        SmartMoodTag.LATE_NIGHT -> MoodPalette(
            top = colorScheme.secondaryContainer.opaqueOver(bg, 0.96f),
            bottom = colorScheme.secondaryContainer.opaqueOver(low, 0.84f),
            accent = colorScheme.secondary,
            primary = colorScheme.onSecondaryContainer,
            secondary = colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
        )
        SmartMoodTag.ACOUSTIC -> MoodPalette(
            top = colorScheme.tertiaryContainer.opaqueOver(bg, 0.96f),
            bottom = colorScheme.tertiaryContainer.opaqueOver(low, 0.84f),
            accent = colorScheme.tertiary,
            primary = colorScheme.onTertiaryContainer,
            secondary = colorScheme.onTertiaryContainer.copy(alpha = 0.78f)
        )
        SmartMoodTag.SOULFUL -> MoodPalette(
            top = colorScheme.primaryContainer.opaqueOver(bg, 0.96f),
            bottom = colorScheme.primaryContainer.opaqueOver(low, 0.84f),
            accent = colorScheme.primary,
            primary = colorScheme.onPrimaryContainer,
            secondary = colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
        )
        SmartMoodTag.UPBEAT -> MoodPalette(
            top = colorScheme.secondaryContainer.opaqueOver(highest, 0.56f),
            bottom = colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.98f),
            accent = colorScheme.secondary,
            primary = colorScheme.onSurface,
            secondary = colorScheme.onSurfaceVariant
        )
        SmartMoodTag.LOSSLESS_HIFI -> MoodPalette(
            top = colorScheme.tertiaryContainer.opaqueOver(highest, 0.56f),
            bottom = colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.98f),
            accent = colorScheme.tertiary,
            primary = colorScheme.onSurface,
            secondary = colorScheme.onSurfaceVariant
        )
    }
    return HomeMoodDisplay(
        title = if (hasCustomTitle) playlist.title else defaultTitle,
        subtitle = if (hasCustomTitle && playlist.subtitle.isNotBlank()) playlist.subtitle else defaultSubtitle,
        topColor = top,
        bottomColor = bottom,
        accentColor = accent,
        primaryText = primary,
        secondaryText = secondary
    )
}

/**
 * Flagship Material 3 Expressive Home screen:
 * 1. Editorial Top Header (Bricolage Grotesque greeting + Plus Jakarta Sans subtitle + 42.dp Search/Settings circles)
 * 2. Signature "Jump Back In" Hero Card (96.dp artwork split + format badge + Stadium Play/Pause Pill + wavy scrubber)
 * 3. Compact 2x2 Bento Library Grid (88.dp tiles, 44.dp expressive icon badges, dynamic M3 tonal roles)
 * 4. Gallery-Grade "Recently Played" Shelf (140.dp rounded-24dp borderless album covers)
 * 5. Conditional "Made for Your Mood" Collections (omitted when empty)
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    currentTrack: Track?,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onExpandNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectTrack: (Track, StableList<Track>) -> Unit,
    onPlayTracks: (List<Track>, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit,
    progressFlow: StateFlow<PlaybackProgress> = IdleHomeProgressFlow,
    onSeek: (Float) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenLibraryShortcut: (LibraryCategory) -> Unit = {},
    isActiveTab: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val emptyCardSurface = remember(colorScheme.surfaceContainerLow, bg) {
        colorScheme.surfaceContainerLow.opaqueOver(bg, 0.96f)
    }
    val headerCircleSurface = colorScheme.surfaceContainerHigh
    val heroColors = remember(colorScheme) { resolveHomeHeroColors(colorScheme) }
    val bentoColors = remember(colorScheme) { resolveHomeLibraryGridColors(colorScheme) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var currentHour by remember { mutableIntStateOf(currentLocalHourOfDay()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentHour = currentLocalHourOfDay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val greetingText = remember(currentHour) {
        formatHomeGreeting(hourOfDay = currentHour)
    }
    val highlightedGreeting = remember(greetingText, colorScheme.onBackground, colorScheme.primary) {
        formatHighlightedHomeGreeting(
            greeting = greetingText,
            baseColor = colorScheme.onBackground,
            accentColor = colorScheme.primary
        )
    }
    val sessionSeed = rememberSaveable {
        Random.nextInt(10_000)
    }
    val trackCount = uiState.allTracks.size
    val hasLosslessTracks = remember(uiState.allTracks) {
        uiState.allTracks.any { it.isLosslessFormat || it.isHiRes }
    }
    val sessionSubtitle = remember(currentHour, trackCount, hasLosslessTracks, sessionSeed) {
        formatHomeSubtitle(
            hourOfDay = currentHour,
            trackCount = trackCount,
            hasLosslessTracks = hasLosslessTracks,
            sessionSeed = sessionSeed
        )
    }

    if (!uiState.isInitialLibraryLoaded) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("home_loading_placeholder")
        )
        return
    }

    val hasActiveTrack = currentTrack != null
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomClearance = (if (hasActiveTrack) 168.dp else 104.dp) + navBarBottom

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_root"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("home_list"),
            contentPadding = PaddingValues(
                top = statusBarTop + 8.dp,
                bottom = bottomClearance
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. Editorial Top Header
            item(key = "home_header", contentType = "home_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = highlightedGreeting,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_header_title")
                        )
                        Text(
                            text = sessionSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_header_subtitle")
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeHeaderCircleButton(
                            icon = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.action_search),
                            containerColor = headerCircleSurface,
                            onClick = onOpenSearch,
                            testTag = "home_search_button"
                        )
                        HomeHeaderCircleButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                            containerColor = headerCircleSurface,
                            onClick = onOpenSettings,
                            testTag = "settings_button"
                        )
                    }
                }
            }

            if (uiState.allTracks.isEmpty()) {
                item(key = "home_empty", contentType = "home_empty") {
                    HomeEmptyCard(
                        artworkShape = artworkShape,
                        cardSurface = emptyCardSurface,
                        onAddFolder = onAddFolder,
                        onScanDevice = onScanDevice
                    )
                }
            } else {
                // 2. Signature "Jump Back In" Hero Card
                if (currentTrack != null) {
                    item(key = "home_continue_${currentTrack.id}", contentType = "home_continue") {
                        HomeJumpBackInHeroCard(
                            track = currentTrack,
                            isPlaying = isPlaying,
                            artworkShape = artworkShape,
                            heroColors = heroColors,
                            progressFlow = progressFlow,
                            isActiveTab = isActiveTab,
                            onExpand = onExpandNowPlaying,
                            onTogglePlayPause = onTogglePlayPause,
                            onSeek = onSeek
                        )
                    }
                }

                // 2.5 Auto-Refreshing Daily Picks (5-6 songs, strictly automatic date rotation, no manual refresh button)
                if (uiState.dailyPicks.isNotEmpty()) {
                    item(key = "home_daily_picks_shelf", contentType = "home_daily_picks_shelf") {
                        val distinctPicks = remember(uiState.dailyPicks) {
                            uiState.dailyPicks.distinctBy { it.id }
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.testTag("home_daily_picks_section")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.home_daily_picks_title),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = DisplayFontFamily,
                                        fontSize = 19.sp,
                                        lineHeight = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.2).sp
                                    ),
                                    color = colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("home_daily_picks_header")
                                )
                                Text(
                                    text = stringResource(R.string.home_daily_picks_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("home_daily_picks_subtitle")
                                )
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("home_daily_picks_row")
                            ) {
                                items(
                                    items = distinctPicks,
                                    key = { track -> "home_daily_pick_${track.id}" },
                                    contentType = { "home_daily_pick_tile" }
                                ) { track ->
                                    val isCurrent = track.id == currentTrack?.id
                                    val clickHandler = remember(track.id, uiState.dailyPicks) {
                                        { onSelectTrack(track, uiState.dailyPicks) }
                                    }
                                    HomeDailyPickTile(
                                        track = track,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && isPlaying,
                                        artworkShape = artworkShape,
                                        onClick = clickHandler
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Bento-Style Library Grid (2x2, 88.dp height, 10.dp gap, 44.dp expressive badges)
                item(key = "home_shortcuts_grid", contentType = "home_shortcuts_grid") {
                    val onLikedClick = remember(uiState.likedTracks, onPlayTracks, onOpenLibraryShortcut) {
                        {
                            if (uiState.likedTracks.isNotEmpty()) {
                                onPlayTracks(uiState.likedTracks, false)
                            } else {
                                onOpenLibraryShortcut(LibraryCategory.FAVORITES)
                            }
                        }
                    }
                    val onMostPlayedClick = remember(uiState.mostPlayedTracks, onPlayTracks, onOpenLibraryShortcut) {
                        {
                            if (uiState.mostPlayedTracks.isNotEmpty()) {
                                onPlayTracks(uiState.mostPlayedTracks, false)
                            } else {
                                onOpenLibraryShortcut(LibraryCategory.MOST_PLAYED)
                            }
                        }
                    }
                    val onRecentlyAddedClick = remember(uiState.recentlyAdded, onPlayTracks, onOpenLibraryShortcut) {
                        {
                            if (uiState.recentlyAdded.isNotEmpty()) {
                                onPlayTracks(uiState.recentlyAdded, false)
                            } else {
                                onOpenLibraryShortcut(LibraryCategory.SONGS)
                            }
                        }
                    }
                    val onShuffleAllClick = remember(uiState.allTracks, onPlayTracks) {
                        { onPlayTracks(uiState.allTracks, true) }
                    }

                    val likedCount = uiState.likedTracks.size
                    val mostPlayedCount = uiState.mostPlayedTracks.size
                    val recentAddedCount = uiState.recentlyAdded.size
                    val totalCount = uiState.allTracks.size

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_your_library),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = DisplayFontFamily,
                                fontSize = 19.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_your_library_header")
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_shortcuts_grid"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HomeLibraryBentoTile(
                                    title = stringResource(R.string.home_shortcut_liked),
                                    subtitle = "$likedCount ${if (likedCount == 1) "song" else "songs"}",
                                    icon = Icons.Rounded.Favorite,
                                    badgeShape = FavoritesBadgeShape,
                                    palette = bentoColors.favorites,
                                    onClick = onLikedClick,
                                    testTag = "home_shortcut_liked",
                                    modifier = Modifier.weight(1f)
                                )
                                HomeLibraryBentoTile(
                                    title = stringResource(R.string.home_shortcut_most_played),
                                    subtitle = if (mostPlayedCount > 0) {
                                        "$mostPlayedCount ${if (mostPlayedCount == 1) "track" else "tracks"}"
                                    } else {
                                        "Top picks"
                                    },
                                    icon = CadenceIcons.LocalFireDepartment,
                                    badgeShape = TopRotationBadgeShape,
                                    palette = bentoColors.topRotation,
                                    onClick = onMostPlayedClick,
                                    testTag = "home_shortcut_most_played",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HomeLibraryBentoTile(
                                    title = stringResource(R.string.home_shortcut_recent_added),
                                    subtitle = if (recentAddedCount > 0) {
                                        "$recentAddedCount ${if (recentAddedCount == 1) "new" else "fresh"}"
                                    } else {
                                        "Fresh adds"
                                    },
                                    icon = CadenceIcons.LibraryAdd,
                                    badgeShape = NewAddsBadgeShape,
                                    palette = bentoColors.newAdds,
                                    onClick = onRecentlyAddedClick,
                                    testTag = "home_shortcut_recent_added",
                                    modifier = Modifier.weight(1f)
                                )
                                HomeLibraryBentoTile(
                                    title = stringResource(R.string.home_shortcut_shuffle_all),
                                    subtitle = "$totalCount ${if (totalCount == 1) "song" else "songs"}",
                                    icon = CadenceIcons.Shuffle,
                                    badgeShape = ShuffleBadgeShape,
                                    palette = bentoColors.shuffleAll,
                                    onClick = onShuffleAllClick,
                                    testTag = "home_shortcut_shuffle_all",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 4. Gallery-Grade "Recently Played" Shelf (140.dp rounded-24dp borderless covers)
                if (uiState.recentlyPlayed.isNotEmpty()) {
                    item(key = "home_recent_shelf", contentType = "home_recent_shelf") {
                        val distinctRecent = remember(uiState.recentlyPlayed) {
                            uiState.recentlyPlayed.distinctBy { it.id }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.home_recently_played),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = DisplayFontFamily,
                                    fontSize = 19.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.2).sp
                                ),
                                color = colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .testTag("home_recently_played_header")
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("home_recent_row")
                            ) {
                                items(
                                    items = distinctRecent,
                                    key = { track -> "home_recent_${track.id}" },
                                    contentType = { "home_recent_tile" }
                                ) { track ->
                                    val isCurrent = track.id == currentTrack?.id
                                    val clickHandler = remember(track.id, uiState.recentlyPlayed) {
                                        { onSelectTrack(track, uiState.recentlyPlayed) }
                                    }
                                    HomeGalleryRecentTile(
                                        track = track,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && isPlaying,
                                        artworkShape = artworkShape,
                                        onClick = clickHandler
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Conditional "Made for Your Mood" Collections (omitted when empty)
                if (uiState.moodCollections.isNotEmpty()) {
                    item(key = "home_mood_section", contentType = "home_mood_section") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.home_made_for_mood),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = DisplayFontFamily,
                                    fontSize = 19.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.2).sp
                                ),
                                color = colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .testTag("home_mood_section_header")
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.testTag("home_mood_row")
                            ) {
                                items(
                                    items = uiState.moodCollections,
                                    key = { playlist -> "home_mood_${playlist.mood.id}" },
                                    contentType = { "home_mood_card" }
                                ) { playlist ->
                                    val onPlayMood = remember(playlist, onPlayTracks) {
                                        { onPlayTracks(playlist.tracks, false) }
                                    }
                                    HomeMoodCard(
                                        playlist = playlist,
                                        artworkShape = artworkShape,
                                        colorScheme = colorScheme,
                                        onClick = onPlayMood
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

@Composable
private fun HomeHeaderCircleButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val isLowEndMode = LocalPerformanceMode.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!isLowEndMode && isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "home_header_circle_scale"
    )

    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .testTag(testTag)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Compact, tactile 88.dp M3 Expressive Bento tile:
 * 44.dp geometric icon badge on the left paired with stacked 1-line title + live count on the right.
 */
@Composable
private fun HomeLibraryBentoTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeShape: Shape,
    palette: HomeLibraryCardPalette,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val isLowEndMode = LocalPerformanceMode.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!isLowEndMode && isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 580f),
        label = "home_bento_tile_scale"
    )
    val gradient = remember(palette.topColor, palette.bottomColor) {
        Brush.verticalGradient(listOf(palette.topColor, palette.bottomColor))
    }

    Surface(
        shape = BentoTileShape,
        color = palette.topColor,
        border = BorderStroke(1.dp, palette.borderColor),
        modifier = modifier
            .testTag(testTag)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(88.dp)
            .clip(BentoTileShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = badgeShape,
                color = palette.iconContainerColor,
                contentColor = palette.iconTint,
                modifier = Modifier
                    .testTag("${testTag}_icon")
                    .size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = DisplayFontFamily,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.25).sp
                    ),
                    color = palette.titleColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    ),
                    color = palette.subtitleColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Sleek 136.dp M3 Expressive Daily Pick card (rounded-20.dp artwork with 1.dp hairline rim,
 * unclipped 1-line title, and unclipped 1-line artist subtitle).
 */
@Composable
private fun HomeDailyPickTile(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isLowEndMode = LocalPerformanceMode.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!isLowEndMode && isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 580f),
        label = "home_daily_pick_tile_scale"
    )

    Column(
        modifier = Modifier
            .testTag("home_daily_pick_tile_${track.id}")
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .width(136.dp)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.size(136.dp)) {
            Surface(
                shape = DailyPickCoverShape,
                color = Color.Transparent,
                border = if (isCurrent) {
                    BorderStroke(2.dp, colorScheme.primary)
                } else {
                    BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f))
                },
                modifier = Modifier
                    .testTag("home_daily_pick_art_${track.id}")
                    .fillMaxSize()
            ) {
                ExpressiveArtworkFrame(
                    track = track,
                    artworkShape = artworkShape,
                    isPlaying = isPlaying,
                    animateScale = false,
                    decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                    shapeOverride = DailyPickCoverShape,
                    showHairlineBorder = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isCurrent) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) {
                        AudioPulseBars(
                            isPlaying = isPlaying,
                            tint = colorScheme.onPrimary,
                            barHeight = 10.dp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            color = if (isCurrent) colorScheme.primary else colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_daily_pick_title_${track.id}")
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 18.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_daily_pick_artist_${track.id}")
        )
    }
}

/**
 * Gallery-grade borderless Recently Played tile:
 * 140.dp rounded-24.dp album cover with 1.dp hairline rim, bold 1-line title, muted 1-line artist.
 */
@Composable
private fun HomeGalleryRecentTile(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isLowEndMode = LocalPerformanceMode.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!isLowEndMode && isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 580f),
        label = "home_gallery_tile_scale"
    )

    Column(
        modifier = Modifier
            .testTag("home_recent_tile_${track.id}")
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .width(140.dp)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.size(140.dp)) {
            Surface(
                shape = GalleryCoverShape,
                color = Color.Transparent,
                border = if (isCurrent) {
                    BorderStroke(2.dp, colorScheme.primary)
                } else {
                    BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.10f))
                },
                modifier = Modifier
                    .testTag("home_recent_art_${track.id}")
                    .fillMaxSize()
            ) {
                ExpressiveArtworkFrame(
                    track = track,
                    artworkShape = artworkShape,
                    isPlaying = isPlaying,
                    animateScale = false,
                    decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                    shapeOverride = GalleryCoverShape,
                    showHairlineBorder = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isCurrent) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) {
                        AudioPulseBars(
                            isPlaying = isPlaying,
                            tint = colorScheme.onPrimary,
                            barHeight = 10.dp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            color = if (isCurrent) colorScheme.primary else colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_recent_title_${track.id}")
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 18.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_recent_artist_${track.id}")
        )
    }
}

@Composable
private fun HomeMoodCard(
    playlist: MoodSmartPlaylist,
    artworkShape: ExpressiveArtworkShape,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val isLowEndMode = LocalPerformanceMode.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!isLowEndMode && isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 580f),
        label = "home_mood_card_scale"
    )
    val display = remember(playlist.mood, playlist.title, playlist.subtitle, colorScheme) {
        resolveHomeMoodDisplay(playlist, colorScheme)
    }
    val gradient = remember(display.topColor, display.bottomColor) {
        Brush.verticalGradient(listOf(display.topColor, display.bottomColor))
    }
    val badgeBg = remember(colorScheme.surface, display.topColor) {
        colorScheme.surface.opaqueOver(display.topColor, 0.28f)
    }
    val leadTrack = playlist.tracks.firstOrNull()
    val countLabel = if (playlist.tracks.size == 1) "1 song" else "${playlist.tracks.size} songs"

    Surface(
        shape = MoodCardShape,
        color = display.topColor,
        border = BorderStroke(1.dp, display.accentColor.copy(alpha = 0.20f)),
        modifier = Modifier
            .testTag("home_mood_card_${playlist.mood.id}")
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .width(232.dp)
            .height(144.dp)
            .clip(MoodCardShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .drawBehind {
                    drawCircle(
                        color = display.accentColor.copy(alpha = 0.08f),
                        radius = size.height * 0.65f,
                        center = Offset(size.width * 0.94f, size.height * 0.96f)
                    )
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = MoodArtworkShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, display.primaryText.copy(alpha = 0.14f)),
                    modifier = Modifier.size(54.dp)
                ) {
                    ExpressiveArtworkFrame(
                        track = leadTrack,
                        artworkShape = artworkShape,
                        isPlaying = false,
                        animateScale = false,
                        decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                        shapeOverride = MoodArtworkShape,
                        showHairlineBorder = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = badgeBg,
                    border = BorderStroke(1.dp, display.accentColor.copy(alpha = 0.22f))
                ) {
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = display.primaryText,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = display.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DisplayFontFamily,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = display.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = display.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = display.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HomeEmptyCard(
    artworkShape: ExpressiveArtworkShape,
    cardSurface: Color,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cardSurface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.32f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("home_empty_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveArtworkFrame(
                track = null,
                artworkShape = artworkShape,
                isPlaying = false,
                animateScale = false,
                modifier = Modifier.size(144.dp)
            )
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAddFolder,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("empty_scan_folder_button")
            ) {
                Icon(CadenceIcons.CreateNewFolder, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_scan_local_folders),
                    maxLines = 1,
                    softWrap = false
                )
            }
            FilledTonalButton(
                onClick = onScanDevice,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_scan_device_button")
            ) {
                Icon(CadenceIcons.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_scan_device),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
