package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LibraryCategory
import com.example.data.StableList
import com.example.data.Track
import com.example.data.formatDuration
import com.example.playback.PlaybackProgress
import com.example.ui.components.AudioPulseBars
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.FAST_THUMBNAIL_DECODE_PX
import com.example.ui.components.FastListThumbnailShape
import com.example.ui.components.HiFiQualityBadge
import com.example.ui.player.HomeUiState
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val IdleHomeProgressFlow: StateFlow<PlaybackProgress> = MutableStateFlow(PlaybackProgress())

/**
 * Formats the single-line time-of-day Home header greeting with optional user display name.
 * Examples: "Good morning, Alex", "Good evening", "Late night, Maya".
 */
fun formatHomeGreeting(
    displayName: String,
    hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
): String {
    val greeting = when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night"
    }
    val cleanName = displayName.trim()
    return if (cleanName.isEmpty()) greeting else "$greeting, $cleanName"
}

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
    onOpenSearch: () -> Unit = {},
    onOpenLibraryShortcut: (LibraryCategory) -> Unit = {},
    isActiveTab: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val bg = MaterialTheme.colorScheme.background
    val cardSurface = MaterialTheme.colorScheme.surfaceContainerLow.opaqueOver(bg, 0.94f)
    val heroSurface = MaterialTheme.colorScheme.primaryContainer.opaqueOver(bg, 0.54f)
    val activeRowSurface = MaterialTheme.colorScheme.primaryContainer.opaqueOver(bg, 0.78f)
    val badgeBg = MaterialTheme.colorScheme.tertiaryContainer.opaqueOver(cardSurface, 0.92f)
    val activeBadgeBg = MaterialTheme.colorScheme.tertiaryContainer.opaqueOver(activeRowSurface, 0.92f)

    // Distinct pre-blended tonal fills for the 2x2 Quick-Shortcut Grid (zero runtime alpha blending)
    val likedSurface = MaterialTheme.colorScheme.primaryContainer.opaqueOver(bg, 0.44f)
    val mostPlayedSurface = MaterialTheme.colorScheme.secondaryContainer.opaqueOver(bg, 0.48f)
    val recentAddedSurface = MaterialTheme.colorScheme.tertiaryContainer.opaqueOver(bg, 0.44f)
    val shuffleSurface = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.96f)

    val greetingText = remember(userDisplayName) {
        formatHomeGreeting(userDisplayName)
    }

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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Personalized Time-of-Day Header + Quick Search & Settings
            item(key = "home_header", contentType = "home_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = greetingText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .testTag("home_header_title")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("home_search_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.action_search),
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("settings_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                                modifier = Modifier.size(21.dp)
                            )
                        }
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
                // 1. Conditional Continue Listening / Quick Resume Card
                if (currentTrack != null) {
                    item(key = "home_continue_${currentTrack.id}", contentType = "home_continue") {
                        ContinueListeningCard(
                            track = currentTrack,
                            isPlaying = isPlaying,
                            artworkShape = artworkShape,
                            heroSurface = heroSurface,
                            progressFlow = progressFlow,
                            isActiveTab = isActiveTab,
                            onExpand = onExpandNowPlaying,
                            onTogglePlayPause = onTogglePlayPause
                        )
                    }
                }

                // 2. 2x2 Quick-Shortcut Grid (Zero-Thought Personal Playback)
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .testTag("home_shortcuts_grid"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeShortcutCard(
                                title = stringResource(R.string.home_shortcut_liked),
                                subtitle = "${uiState.likedTracks.size} ${if (uiState.likedTracks.size == 1) "track" else "tracks"}",
                                icon = Icons.Rounded.Favorite,
                                containerColor = likedSurface,
                                iconContainerColor = MaterialTheme.colorScheme.primary,
                                iconTint = MaterialTheme.colorScheme.onPrimary,
                                onClick = onLikedClick,
                                testTag = "home_shortcut_liked",
                                modifier = Modifier.weight(1f)
                            )
                            HomeShortcutCard(
                                title = stringResource(R.string.home_shortcut_most_played),
                                subtitle = if (uiState.mostPlayedTracks.isNotEmpty()) {
                                    "${uiState.mostPlayedTracks.size} top listens"
                                } else {
                                    "Your top hits"
                                },
                                icon = Icons.Rounded.LocalFireDepartment,
                                containerColor = mostPlayedSurface,
                                iconContainerColor = MaterialTheme.colorScheme.secondary,
                                iconTint = MaterialTheme.colorScheme.onSecondary,
                                onClick = onMostPlayedClick,
                                testTag = "home_shortcut_most_played",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeShortcutCard(
                                title = stringResource(R.string.home_shortcut_recent_added),
                                subtitle = "${uiState.recentlyAdded.size} fresh adds",
                                icon = Icons.Rounded.LibraryAdd,
                                containerColor = recentAddedSurface,
                                iconContainerColor = MaterialTheme.colorScheme.tertiary,
                                iconTint = MaterialTheme.colorScheme.onTertiary,
                                onClick = onRecentlyAddedClick,
                                testTag = "home_shortcut_recent_added",
                                modifier = Modifier.weight(1f)
                            )
                            HomeShortcutCard(
                                title = stringResource(R.string.home_shortcut_shuffle_all),
                                subtitle = "${uiState.allTracks.size} songs",
                                icon = Icons.Rounded.Shuffle,
                                containerColor = shuffleSurface,
                                iconContainerColor = MaterialTheme.colorScheme.onSurface,
                                iconTint = MaterialTheme.colorScheme.surface,
                                onClick = onShuffleAllClick,
                                testTag = "home_shortcut_shuffle_all",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. Recently Played Horizontal Shelf
                if (uiState.recentlyPlayed.isNotEmpty()) {
                    item(key = "home_recent_shelf", contentType = "home_recent_shelf") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.home_recently_played),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.testTag("home_recently_played_header")
                                )
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("home_recent_row")
                            ) {
                                itemsIndexed(
                                    items = uiState.recentlyPlayed,
                                    key = { idx, track -> "home_recent_${idx}_${track.id}" },
                                    contentType = { _, _ -> "home_recent_card" }
                                ) { _, track ->
                                    val isCurrent = track.id == currentTrack?.id
                                    val clickHandler = remember(track.id, uiState.recentlyPlayed) {
                                        { onSelectTrack(track, uiState.recentlyPlayed) }
                                    }
                                    HomeRecentTile(
                                        track = track,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && isPlaying,
                                        artworkShape = artworkShape,
                                        cardSurface = if (isCurrent) activeRowSurface else cardSurface,
                                        onClick = clickHandler
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Recently Added Song List Header
                item(key = "home_added_header", contentType = "home_added_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_recently_added),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("home_recently_added_header")
                        )
                        FilledTonalButton(
                            onClick = { onPlayTracks(uiState.allTracks, true) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("home_shuffle_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_shuffle_all),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Recently Added Compact Track Rows
                itemsIndexed(
                    items = uiState.recentlyAdded,
                    key = { idx, track -> "home_added_${idx}_${track.id}" },
                    contentType = { _, _ -> "home_track_row" }
                ) { _, track ->
                    val isCurrent = track.id == currentTrack?.id
                    val selectClick = remember(track.id, uiState.recentlyAdded) {
                        { onSelectTrack(track, uiState.recentlyAdded) }
                    }
                    val favClick = remember(track.id, onToggleFavorite) {
                        { onToggleFavorite(track.id) }
                    }
                    HomeTrackRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        artworkShape = artworkShape,
                        rowSurface = if (isCurrent) activeRowSurface else cardSurface,
                        badgeContainerColor = if (isCurrent) activeBadgeBg else badgeBg,
                        onClick = selectClick,
                        onToggleFavorite = favClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningCard(
    track: Track,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    heroSurface: Color,
    progressFlow: StateFlow<PlaybackProgress>,
    isActiveTab: Boolean,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    Surface(
        onClick = onExpand,
        shape = RoundedCornerShape(28.dp),
        color = heroSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("home_continue_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ExpressiveArtworkFrame(
                    track = track,
                    artworkShape = artworkShape,
                    isPlaying = isPlaying,
                    animateScale = false,
                    decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                    shapeOverride = FastListThumbnailShape,
                    modifier = Modifier.size(68.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AudioPulseBars(
                            isPlaying = isPlaying,
                            tint = MaterialTheme.colorScheme.primary,
                            barHeight = 11.dp
                        )
                        Text(
                            text = stringResource(R.string.home_continue_listening),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (track.isHiFi) {
                            HiFiQualityBadge(
                                label = stringResource(R.string.badge_hifi_short),
                                backgroundBehind = heroSurface
                            )
                        }
                    }
                }
            }

            // Isolated progress & one-tap Resume/Pause pill so ticks never recompose HomeScreen
            ContinueListeningProgressRow(
                trackDurationMs = track.durationMs,
                isPlaying = isPlaying,
                heroSurface = heroSurface,
                progressFlow = progressFlow,
                isActiveTab = isActiveTab,
                onTogglePlayPause = onTogglePlayPause
            )
        }
    }
}

@Composable
private fun ContinueListeningProgressRow(
    trackDurationMs: Long,
    isPlaying: Boolean,
    heroSurface: Color,
    progressFlow: StateFlow<PlaybackProgress>,
    isActiveTab: Boolean,
    onTogglePlayPause: () -> Unit
) {
    val trackBg = MaterialTheme.colorScheme.surface.opaqueOver(heroSurface, 0.55f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ContinueListeningProgressBar(
                progressFlow = progressFlow,
                trackDurationMs = trackDurationMs,
                isActiveTab = isActiveTab,
                fillColor = primaryColor,
                trackColor = trackBg
            )
            ContinueListeningRemainingText(
                progressFlow = progressFlow,
                trackDurationMs = trackDurationMs,
                isActiveTab = isActiveTab
            )
        }

        Button(
            onClick = onTogglePlayPause,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .height(42.dp)
                .testTag("home_continue_play_button")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(if (isPlaying) R.string.action_pause else R.string.home_resume),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

/**
 * Draw-phase progress bar leaf: collects [progressFlow] only while Home is active and reads
 * state inside [Canvas] draw phase so playback ticks never trigger layout or parent recomposition.
 */
@Composable
private fun ContinueListeningProgressBar(
    progressFlow: StateFlow<PlaybackProgress>,
    trackDurationMs: Long,
    isActiveTab: Boolean,
    fillColor: Color,
    trackColor: Color
) {
    if (!isActiveTab) {
        val snapshot = progressFlow.value
        val totalMs = snapshot.durationMs.takeIf { it > 0L } ?: trackDurationMs.coerceAtLeast(1L)
        val fraction = (snapshot.positionMs.coerceIn(0L, totalMs).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .testTag("home_continue_progress")
        ) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = trackColor, cornerRadius = radius)
            val fillWidth = size.width * fraction
            if (fillWidth > 0f) {
                drawRoundRect(color = fillColor, size = Size(fillWidth, size.height), cornerRadius = radius)
            }
        }
        return
    }

    val progressState = progressFlow.collectAsStateWithLifecycle()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .testTag("home_continue_progress")
    ) {
        val progress = progressState.value
        val totalMs = progress.durationMs.takeIf { it > 0L } ?: trackDurationMs.coerceAtLeast(1L)
        val fraction = (progress.positionMs.coerceIn(0L, totalMs).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val fillWidth = size.width * fraction
        if (fillWidth > 0f) {
            drawRoundRect(color = fillColor, size = Size(fillWidth, size.height), cornerRadius = radius)
        }
    }
}

/**
 * 1-second distinct leaf composable for remaining time: never collects while Home is inactive,
 * and emits at most once per second while active.
 */
@Composable
private fun ContinueListeningRemainingText(
    progressFlow: StateFlow<PlaybackProgress>,
    trackDurationMs: Long,
    isActiveTab: Boolean
) {
    val label = if (!isActiveTab) {
        formatRemainingLabel(progressFlow.value, trackDurationMs)
    } else {
        val labelFlow = remember(progressFlow, trackDurationMs) {
            progressFlow
                .map { p -> formatRemainingLabel(p, trackDurationMs) }
                .distinctUntilChanged()
        }
        labelFlow.collectAsStateWithLifecycle(
            initialValue = formatRemainingLabel(progressFlow.value, trackDurationMs)
        ).value
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.testTag("home_continue_remaining")
    )
}

private fun formatRemainingLabel(progress: PlaybackProgress, fallbackDurationMs: Long): String {
    val totalMs = progress.durationMs.takeIf { it > 0L } ?: fallbackDurationMs.coerceAtLeast(1L)
    val posMs = progress.positionMs.coerceIn(0L, totalMs)
    val remainingSec = ((totalMs - posMs).coerceAtLeast(0L)) / 1000L
    return if (posMs > 0L) {
        "${formatDuration(remainingSec * 1000L)} left"
    } else {
        formatDuration(totalMs)
    }
}

@Composable
private fun HomeShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    iconContainerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        modifier = modifier
            .height(84.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                contentColor = iconTint,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeRecentTile(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    cardSurface: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = cardSurface,
        modifier = Modifier
            .width(138.dp)
            .testTag("home_recent_tile_${track.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveArtworkFrame(
                track = track,
                artworkShape = artworkShape,
                isPlaying = isPlaying,
                animateScale = false,
                decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                shapeOverride = FastListThumbnailShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
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
}

@Composable
private fun HomeTrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    rowSurface: Color,
    badgeContainerColor: Color,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = rowSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("home_track_row_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveArtworkFrame(
                track = track,
                artworkShape = artworkShape,
                isPlaying = isPlaying,
                animateScale = false,
                decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                shapeOverride = FastListThumbnailShape,
                modifier = Modifier.size(48.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isHiFi) {
                        HiFiQualityBadge(
                            label = stringResource(R.string.badge_hifi_short),
                            containerColor = badgeContainerColor
                        )
                    }
                }
                Text(
                    text = "${track.artist} · ${formatDuration(track.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrent) {
                AudioPulseBars(
                    isPlaying = isPlaying,
                    tint = MaterialTheme.colorScheme.primary,
                    barHeight = 14.dp
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_favorite),
                    tint = if (track.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
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
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(20.dp))
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
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_scan_device))
            }
        }
    }
}
