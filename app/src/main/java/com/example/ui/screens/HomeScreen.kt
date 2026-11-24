package com.example.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AudioQualityTier
import com.example.data.LibraryCategory
import com.example.data.StableList
import com.example.data.Track
import com.example.data.formatDuration
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
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private val IdleHomeProgressFlow: StateFlow<PlaybackProgress> = MutableStateFlow(PlaybackProgress())

private val HeroArtworkShape = RoundedCornerShape(20.dp)
private val RecentArtworkShape = RoundedCornerShape(20.dp)
private val MoodArtworkShape = RoundedCornerShape(16.dp)
private val LibraryCardShape = RoundedCornerShape(24.dp)
private val LibraryIconContainerShape = RoundedCornerShape(16.dp)

@Immutable
internal data class HomeHeroColors(
    val topColor: Color,
    val bottomColor: Color,
    val cardBorder: Color,
    val pillContainer: Color,
    val pillBorder: Color,
    val pulseTint: Color,
    val titleColor: Color,
    val artistColor: Color,
    val badgeTextColor: Color,
    val timestampColor: Color,
    val playButtonContainer: Color,
    val playButtonContent: Color,
    val wavePlayedColor: Color,
    val waveHaloColor: Color,
    val waveUnplayedColor: Color,
    val artworkBorderColor: Color
)

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

internal fun resolveHomeHeroColors(colorScheme: ColorScheme): HomeHeroColors {
    val topColor = colorScheme.primaryContainer.opaqueOver(colorScheme.surfaceContainerHigh, 0.94f)
    val bottomColor = colorScheme.primaryContainer.opaqueOver(colorScheme.surfaceContainer, 0.82f)
    return HomeHeroColors(
        topColor = topColor,
        bottomColor = bottomColor,
        cardBorder = colorScheme.primary.copy(alpha = 0.22f),
        pillContainer = colorScheme.surface.opaqueOver(topColor, 0.28f),
        pillBorder = colorScheme.primary.copy(alpha = 0.22f),
        pulseTint = colorScheme.primary,
        titleColor = colorScheme.onPrimaryContainer,
        artistColor = colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
        badgeTextColor = colorScheme.onPrimaryContainer,
        timestampColor = colorScheme.onPrimaryContainer,
        playButtonContainer = colorScheme.primary,
        playButtonContent = colorScheme.onPrimary,
        wavePlayedColor = colorScheme.primary,
        waveHaloColor = colorScheme.primary.copy(alpha = 0.22f),
        waveUnplayedColor = colorScheme.onPrimaryContainer.opaqueOver(bottomColor, 0.24f),
        artworkBorderColor = colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
    )
}

internal fun resolveHomeLibraryGridColors(colorScheme: ColorScheme): HomeLibraryGridColors {
    val bg = colorScheme.background
    val low = colorScheme.surfaceContainerLow
    val shuffleTop = colorScheme.surfaceContainerHighest.opaqueOver(bg, 0.98f)
    val shuffleBottom = colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.98f)
    return HomeLibraryGridColors(
        favorites = HomeLibraryCardPalette(
            topColor = colorScheme.primaryContainer.opaqueOver(bg, 0.96f),
            bottomColor = colorScheme.primaryContainer.opaqueOver(low, 0.86f),
            borderColor = colorScheme.primary.copy(alpha = 0.18f),
            iconContainerColor = colorScheme.primary,
            iconTint = colorScheme.onPrimary,
            titleColor = colorScheme.onPrimaryContainer,
            subtitleColor = colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
        ),
        topRotation = HomeLibraryCardPalette(
            topColor = colorScheme.tertiaryContainer.opaqueOver(bg, 0.96f),
            bottomColor = colorScheme.tertiaryContainer.opaqueOver(low, 0.86f),
            borderColor = colorScheme.tertiary.copy(alpha = 0.18f),
            iconContainerColor = colorScheme.tertiary,
            iconTint = colorScheme.onTertiary,
            titleColor = colorScheme.onTertiaryContainer,
            subtitleColor = colorScheme.onTertiaryContainer.copy(alpha = 0.76f)
        ),
        newAdds = HomeLibraryCardPalette(
            topColor = colorScheme.secondaryContainer.opaqueOver(bg, 0.96f),
            bottomColor = colorScheme.secondaryContainer.opaqueOver(low, 0.86f),
            borderColor = colorScheme.secondary.copy(alpha = 0.18f),
            iconContainerColor = colorScheme.secondary,
            iconTint = colorScheme.onSecondary,
            titleColor = colorScheme.onSecondaryContainer,
            subtitleColor = colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
        ),
        shuffleAll = HomeLibraryCardPalette(
            topColor = shuffleTop,
            bottomColor = shuffleBottom,
            borderColor = colorScheme.outlineVariant.copy(alpha = 0.38f),
            iconContainerColor = colorScheme.primary.opaqueOver(shuffleTop, 0.16f),
            iconTint = colorScheme.primary,
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.onSurfaceVariant
        )
    )
}

internal val HomeSessionPrompts = listOf(
    "Ready for some music?",
    "What's the vibe today?",
    "Press play and unwind.",
    "Your offline soundstage awaits.",
    "Pick up where you left off."
)

/**
 * Formats the time-of-day Home greeting with optional user display name.
 * Examples: "Good morning!", "Good afternoon!", "Good evening!", "Good morning, Alex!".
 */
fun formatHomeGreeting(
    displayName: String,
    hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
): String {
    val greeting = when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val cleanName = displayName.trim()
    return if (cleanName.isEmpty()) "$greeting!" else "$greeting, $cleanName!"
}

internal fun resolveHomeHeroQualityLabel(track: Track, fallbackHq: String): String {
    val spec = track.resolveAudioQualitySpec()
    return when {
        spec?.tier == AudioQualityTier.HI_RES -> "HI-RES"
        spec?.tier == AudioQualityTier.LOSSLESS -> "LOSSLESS"
        track.isHiFi -> "HI-FI"
        spec?.tier == AudioQualityTier.HIGH_QUALITY -> fallbackHq
        else -> fallbackHq
    }
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
            secondary = colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
        )
        SmartMoodTag.ACOUSTIC -> MoodPalette(
            top = colorScheme.tertiaryContainer.opaqueOver(bg, 0.96f),
            bottom = colorScheme.tertiaryContainer.opaqueOver(low, 0.84f),
            accent = colorScheme.tertiary,
            primary = colorScheme.onTertiaryContainer,
            secondary = colorScheme.onTertiaryContainer.copy(alpha = 0.76f)
        )
        SmartMoodTag.SOULFUL -> MoodPalette(
            top = colorScheme.primaryContainer.opaqueOver(bg, 0.96f),
            bottom = colorScheme.primaryContainer.opaqueOver(low, 0.84f),
            accent = colorScheme.primary,
            primary = colorScheme.onPrimaryContainer,
            secondary = colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
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

private data class MoodPalette(
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val primary: Color,
    val secondary: Color
)

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
    userDisplayName: String = "",
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
    val cardSurface = colorScheme.surfaceContainerLow.opaqueOver(bg, 0.94f)
    val headerButtonSurface = colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.92f)
    val headerButtonBorder = colorScheme.onSurface.copy(alpha = 0.08f)

    // Pre-blended adaptive M3 surfaces for the Hero card & 2x2 Your Library grid
    val heroColors = remember(colorScheme) { resolveHomeHeroColors(colorScheme) }
    val libraryColors = remember(colorScheme) { resolveHomeLibraryGridColors(colorScheme) }

    val greetingText = remember(userDisplayName) {
        formatHomeGreeting(userDisplayName)
    }
    val sessionPromptIndex = rememberSaveable {
        Random.nextInt(HomeSessionPrompts.size)
    }
    val sessionSubtitle = HomeSessionPrompts[sessionPromptIndex.coerceIn(0, HomeSessionPrompts.lastIndex)]

    if (!uiState.isInitialLibraryLoaded) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("home_loading_placeholder")
        )
        return
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("home_list"),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 180.dp
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. Clean & Compact Greetings Header + Search & Settings Circle Buttons
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
                            text = greetingText,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = DisplayFontFamily,
                                fontSize = 24.sp,
                                lineHeight = 30.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_header_title")
                        )
                        Text(
                            text = sessionSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_header_subtitle")
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeHeaderCircleButton(
                            icon = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.action_search),
                            containerColor = headerButtonSurface,
                            borderColor = headerButtonBorder,
                            onClick = onOpenSearch,
                            testTag = "home_search_button"
                        )
                        HomeHeaderCircleButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                            containerColor = headerButtonSurface,
                            borderColor = headerButtonBorder,
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
                        cardSurface = cardSurface,
                        onAddFolder = onAddFolder,
                        onScanDevice = onScanDevice
                    )
                }
            } else {
                // 2. Main Now Playing Hero Card (310.dp Height, Adaptive Theme Container Surface)
                if (currentTrack != null) {
                    item(key = "home_continue_${currentTrack.id}", contentType = "home_continue") {
                        HomeNowPlayingHeroCard(
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

                // 3. Your Library — 2x2 Expressive Grid (156.dp Height Cards)
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_your_library),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = DisplayFontFamily,
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_your_library_header")
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_shortcuts_grid"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HomeLibraryGridCard(
                                    title = stringResource(R.string.home_shortcut_liked),
                                    subtitle = "$likedCount ${if (likedCount == 1) "song" else "songs"}",
                                    icon = Icons.Rounded.Favorite,
                                    palette = libraryColors.favorites,
                                    onClick = onLikedClick,
                                    testTag = "home_shortcut_liked",
                                    modifier = Modifier.weight(1f)
                                )
                                HomeLibraryGridCard(
                                    title = stringResource(R.string.home_shortcut_most_played),
                                    subtitle = if (mostPlayedCount > 0) {
                                        "$mostPlayedCount ${if (mostPlayedCount == 1) "listen" else "top listens"}"
                                    } else {
                                        "Most played"
                                    },
                                    icon = CadenceIcons.LocalFireDepartment,
                                    palette = libraryColors.topRotation,
                                    onClick = onMostPlayedClick,
                                    testTag = "home_shortcut_most_played",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HomeLibraryGridCard(
                                    title = stringResource(R.string.home_shortcut_recent_added),
                                    subtitle = if (recentAddedCount > 0) {
                                        "$recentAddedCount ${if (recentAddedCount == 1) "track" else "fresh tracks"}"
                                    } else {
                                        "Fresh tracks"
                                    },
                                    icon = CadenceIcons.LibraryAdd,
                                    palette = libraryColors.newAdds,
                                    onClick = onRecentlyAddedClick,
                                    testTag = "home_shortcut_recent_added",
                                    modifier = Modifier.weight(1f)
                                )
                                HomeLibraryGridCard(
                                    title = stringResource(R.string.home_shortcut_shuffle_all),
                                    subtitle = "$totalCount ${if (totalCount == 1) "song" else "songs"}",
                                    icon = CadenceIcons.Shuffle,
                                    palette = libraryColors.shuffleAll,
                                    onClick = onShuffleAllClick,
                                    testTag = "home_shortcut_shuffle_all",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 4. Recently Played — Clean Borderless Horizontal Shelf
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
                                    fontSize = 18.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .testTag("home_recently_played_header")
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                                    HomeRecentTile(
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

                // 5. Made for Your Mood — Conditional Section (omitted completely when empty)
                if (uiState.moodCollections.isNotEmpty()) {
                    item(key = "home_mood_section", contentType = "home_mood_section") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.home_made_for_mood),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = DisplayFontFamily,
                                    fontSize = 18.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
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
    borderColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .testTag(testTag)
            .size(42.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
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

@Composable
private fun HomeNowPlayingHeroCard(
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
    val fallbackHq = stringResource(R.string.badge_hq_short)
    val qualityLabel = remember(track.id, track.mimeType, track.bitrateKbps, track.sampleRateHz, track.bitDepth, fallbackHq) {
        resolveHomeHeroQualityLabel(track, fallbackHq)
    }
    val heroBrush = remember(heroColors.topColor, heroColors.bottomColor) {
        Brush.verticalGradient(listOf(heroColors.topColor, heroColors.bottomColor))
    }
    val playPillInteractionSource = remember { MutableInteractionSource() }
    val isPlayPillPressed by playPillInteractionSource.collectIsPressedAsState()
    val playPillScale by animateFloatAsState(
        targetValue = if (isPlayPillPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
        label = "home_hero_play_pill_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = heroColors.topColor,
            border = BorderStroke(1.dp, heroColors.cardBorder),
            modifier = Modifier
                .testTag("home_continue_card")
                .fillMaxWidth()
                .height(310.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable(role = Role.Button, onClick = onExpand)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(heroBrush)
                    .padding(18.dp)
            ) {
                // Top Status Row: NOW PLAYING / PAUSED + Real-Metadata Audio Quality Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = heroColors.pillContainer,
                        border = BorderStroke(1.dp, heroColors.pillBorder),
                        modifier = Modifier.testTag("home_hero_status_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AudioPulseBars(
                                isPlaying = isPlaying,
                                tint = heroColors.pulseTint,
                                barHeight = 10.dp
                            )
                            Text(
                                text = stringResource(if (isPlaying) R.string.now_playing_label else R.string.status_paused),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp
                                ),
                                color = heroColors.badgeTextColor,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = heroColors.pillContainer,
                        border = BorderStroke(1.dp, heroColors.pillBorder),
                        modifier = Modifier.testTag("home_hero_hifi_badge")
                    ) {
                        Text(
                            text = qualityLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp
                            ),
                            color = heroColors.badgeTextColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Middle Track Row: 108.dp Artwork + Full-Width Title & Artist (zero side-by-side button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = HeroArtworkShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, heroColors.artworkBorderColor),
                        modifier = Modifier
                            .testTag("home_hero_artwork")
                            .size(108.dp)
                    ) {
                        ExpressiveArtworkFrame(
                            track = track,
                            artworkShape = artworkShape,
                            isPlaying = isPlaying,
                            animateScale = false,
                            decodeSizePx = 320,
                            shapeOverride = HeroArtworkShape,
                            showHairlineBorder = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = DisplayFontFamily,
                                fontSize = 20.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = heroColors.titleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_hero_title")
                        )
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
                    }
                }

                Spacer(Modifier.weight(1f))

                // M3 Expressive Stadium Play/Pause Pill positioned bottom-right above wavy progress bar
                Surface(
                    shape = CircleShape,
                    color = heroColors.playButtonContainer,
                    contentColor = heroColors.playButtonContent,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("home_continue_play_button")
                        .graphicsLayer {
                            scaleX = playPillScale
                            scaleY = playPillScale
                        }
                        .height(42.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = playPillInteractionSource,
                            indication = ripple(),
                            role = Role.Button,
                            onClick = onTogglePlayPause
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPlaying) CadenceIcons.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("home_continue_play_label")
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Bottom Exclusive Interactive Sinusoidal Wavy Progress Indicator + Mono Timestamps
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
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
 * Isolated draw-phase sinusoidal wavy progress bar exclusive to the Home Hero card.
 * Collects [progressFlow] only while Home is active and reads it inside Canvas draw phase
 * so playback ticks trigger zero parent recompositions.
 * Supports both tap-to-seek and horizontal drag-to-seek without triggering the parent card click.
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
    val progressState = if (isActiveTab) {
        progressFlow.collectAsStateWithLifecycle()
    } else {
        null
    }

    val phaseState = if (isPlaying && isActiveTab) {
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
            .height(34.dp)
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

        val thumbRadius = 6.dp.toPx() // 12.dp diameter playhead
        val haloRadius = if (dragFraction != null) 13.dp.toPx() else 10.dp.toPx()
        val strokeWidthPx = 3.5.dp.toPx()
        val padX = thumbRadius + 2.dp.toPx()
        val startX = padX
        val endX = (size.width - padX).coerceAtLeast(startX + 1f)
        val centerY = size.height / 2f
        val playheadX = startX + (endX - startX) * fraction

        // Unplayed segment (straight rounded line)
        if (playheadX < endX) {
            drawLine(
                color = unplayedColor,
                start = Offset(playheadX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        // Played segment (smooth single-line sinusoidal wave)
        val playedLength = (playheadX - startX).coerceAtLeast(0f)
        if (playedLength > 1f) {
            val amplitude = if (isPlaying) 3.8.dp.toPx() else 2.2.dp.toPx()
            val wavelength = 26.dp.toPx().coerceAtLeast(12f)
            val phase = phaseState?.value ?: 0f
            val stepPx = 3.dp.toPx().coerceAtLeast(2f)

            wavePath.rewind()
            var x = startX
            var isFirst = true
            while (x <= playheadX) {
                val distFromStart = x - startX
                val distFromEnd = (playheadX - x).coerceAtLeast(0f)
                // Smoothly taper amplitude into endpoints & playhead so the wave joins cleanly
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

        // Playhead soft halo + 12.dp solid peach circle
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
 * 1-second distinct leaf composable for elapsed and total duration timestamps.
 * Never triggers recomposition on HomeScreen or the Hero card during playback ticks.
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
            modifier = Modifier.testTag("home_continue_elapsed")
        )
        Text(
            text = timePair.second,
            style = monoStyle,
            color = timestampColor,
            maxLines = 1,
            modifier = Modifier.testTag("home_continue_duration")
        )
    }
}

private fun computeTimestampPair(progress: PlaybackProgress, fallbackDurationMs: Long): Pair<String, String> {
    val totalMs = progress.durationMs.takeIf { it > 0L } ?: fallbackDurationMs.coerceAtLeast(1L)
    val posSecMs = (progress.positionMs.coerceIn(0L, totalMs) / 1000L) * 1000L
    return formatDuration(posSecMs) to formatDuration(totalMs)
}

@Composable
private fun HomeLibraryGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    palette: HomeLibraryCardPalette,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val gradient = remember(palette.topColor, palette.bottomColor) {
        Brush.verticalGradient(listOf(palette.topColor, palette.bottomColor))
    }

    Surface(
        shape = LibraryCardShape,
        color = palette.topColor,
        border = BorderStroke(1.dp, palette.borderColor),
        modifier = modifier
            .testTag(testTag)
            .height(156.dp)
            .clip(LibraryCardShape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = LibraryIconContainerShape,
                color = palette.iconContainerColor,
                contentColor = palette.iconTint,
                modifier = Modifier
                    .testTag("${testTag}_icon")
                    .size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DisplayFontFamily,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = palette.titleColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = palette.subtitleColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Clean borderless Recently Played tile: 132.dp square rounded album artwork
 * followed directly beneath by 1-line title and 1-line artist with zero heavy card framing.
 */
@Composable
private fun HomeRecentTile(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .testTag("home_recent_tile_${track.id}")
            .width(132.dp)
            .clip(RecentArtworkShape)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExpressiveArtworkFrame(
            track = track,
            artworkShape = artworkShape,
            isPlaying = isPlaying,
            animateScale = false,
            decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
            shapeOverride = RecentArtworkShape,
            showHairlineBorder = true,
            modifier = Modifier
                .testTag("home_recent_art_${track.id}")
                .size(132.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
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
    }
}

@Composable
private fun HomeMoodCard(
    playlist: MoodSmartPlaylist,
    artworkShape: ExpressiveArtworkShape,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
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
        shape = RoundedCornerShape(24.dp),
        color = display.topColor,
        border = BorderStroke(1.dp, display.accentColor.copy(alpha = 0.20f)),
        modifier = Modifier
            .testTag("home_mood_card_${playlist.mood.id}")
            .width(228.dp)
            .height(148.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
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
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = display.primaryText,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
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
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = display.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = display.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cardSurface,
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
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAddFolder,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_scan_folder_button")
            ) {
                Icon(CadenceIcons.CreateNewFolder, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_scan_local_folders))
            }
            FilledTonalButton(
                onClick = onScanDevice,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_scan_device_button")
            ) {
                Icon(CadenceIcons.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_scan_device))
            }
        }
    }
}
