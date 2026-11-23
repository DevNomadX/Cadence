package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.PlaylistSummary
import com.example.data.RepeatMode
import com.example.data.SleepTimerOption
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.data.Track
import com.example.data.formatDuration
import com.example.playback.PlaybackProgress
import com.example.ui.components.AddToPlaylistSheetContent
import com.example.ui.components.AudioPulseBars
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.HiFiQualityBadge
import com.example.ui.components.MorphingPlayPauseButton
import com.example.ui.components.PlaylistNameDialog
import com.example.ui.components.QueueSheetContent
import com.example.ui.components.TrackActionsSheetContent
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.components.WavyExpressiveSlider
import com.example.ui.player.NowPlayingUiState
import com.example.ui.player.SleepTimerState
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    uiState: NowPlayingUiState,
    themePrefs: ThemePreferences,
    progressFlow: StateFlow<PlaybackProgress>,
    sleepTimerFlow: StateFlow<SleepTimerState>,
    playlists: StableList<PlaylistSummary>,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCycleSpeed: () -> Unit,
    onMatchMood: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onAddCurrentToPlaylist: (Long) -> Unit,
    onCreatePlaylistWithCurrent: (String) -> Unit,
    onPlayNextCurrent: () -> Unit,
    onAddCurrentToQueue: () -> Unit,
    onSelectSleepTimer: (SleepTimerOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var showQueueSheet by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var showTrackActions by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var showTrackDetails by remember { mutableStateOf(false) }

    val openQueue = remember { { showQueueSheet = true } }
    val openTimer = remember { { showTimerSheet = true } }
    val openTrackMenu = remember { { showTrackActions = true } }

    val currentTrack = uiState.currentTrack

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 640.dp
        val isUltraShortPortrait = maxHeight < 580.dp

        if (currentTrack == null && !uiState.isInitialLibraryLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("initial_loading_placeholder")
            )
        } else if (currentTrack == null) {
            EmptyNowPlayingContent(
                artworkShape = themePrefs.artworkShape,
                onOpenSettings = onOpenSettings,
                onAddFolder = onAddFolder,
                onScanDevice = onScanDevice
            )
        } else if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveArtworkFrame(
                        track = currentTrack,
                        artworkShape = themePrefs.artworkShape,
                        isPlaying = uiState.isPlaying,
                        decodeSizePx = 768,
                        modifier = Modifier
                            .widthIn(max = 340.dp)
                            .fillMaxWidth(0.88f)
                            .aspectRatio(1f)
                            .testTag("hero_album_artwork")
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    NowPlayingTopBar(
                        hasTrack = true,
                        isPlaying = uiState.isPlaying,
                        isPreparing = uiState.isPreparing,
                        onOpenSettings = onOpenSettings,
                        onOpenQueue = openQueue
                    )
                    TrackIdentityBlock(
                        track = currentTrack,
                        onToggleFavorite = onToggleFavorite,
                        onOpenTrackMenu = openTrackMenu
                    )
                    ScrubberSection(
                        progressFlow = progressFlow,
                        fallbackDurationMs = currentTrack.durationMs,
                        isPlaying = uiState.isPlaying,
                        wavyEnabled = themePrefs.effectiveWavyScrubber,
                        onSeek = onSeek
                    )
                    ExpressiveTransportCluster(
                        uiState = uiState,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat
                    )
                    UtilityPillStrip(
                        playbackSpeed = uiState.playbackSpeed,
                        suggestedPalette = currentTrack.suggestedPalette,
                        activePalette = themePrefs.palette,
                        sleepTimerFlow = sleepTimerFlow,
                        onCycleSpeed = onCycleSpeed,
                        onOpenTimer = openTimer,
                        onMatchMood = onMatchMood
                    )
                }
            }
        } else if (isUltraShortPortrait) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 96.dp)
                    .testTag("now_playing_portrait_container"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NowPlayingTopBar(
                    hasTrack = true,
                    isPlaying = uiState.isPlaying,
                    isPreparing = uiState.isPreparing,
                    onOpenSettings = onOpenSettings,
                    onOpenQueue = openQueue
                )

                ExpressiveArtworkFrame(
                    track = currentTrack,
                    artworkShape = themePrefs.artworkShape,
                    isPlaying = uiState.isPlaying,
                    decodeSizePx = 768,
                    modifier = Modifier
                        .size(176.dp)
                        .aspectRatio(1f)
                        .testTag("hero_album_artwork")
                )

                TrackIdentityBlock(
                    track = currentTrack,
                    onToggleFavorite = onToggleFavorite,
                    onOpenTrackMenu = openTrackMenu
                )

                ScrubberSection(
                    progressFlow = progressFlow,
                    fallbackDurationMs = currentTrack.durationMs,
                    isPlaying = uiState.isPlaying,
                    wavyEnabled = themePrefs.effectiveWavyScrubber,
                    onSeek = onSeek
                )

                ExpressiveTransportCluster(
                    uiState = uiState,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat
                )

                UtilityPillStrip(
                    playbackSpeed = uiState.playbackSpeed,
                    suggestedPalette = currentTrack.suggestedPalette,
                    activePalette = themePrefs.palette,
                    sleepTimerFlow = sleepTimerFlow,
                    onCycleSpeed = onCycleSpeed,
                    onOpenTimer = openTimer,
                    onMatchMood = onMatchMood
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 96.dp)
                    .testTag("now_playing_portrait_container"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NowPlayingTopBar(
                    hasTrack = true,
                    isPlaying = uiState.isPlaying,
                    isPreparing = uiState.isPreparing,
                    onOpenSettings = onOpenSettings,
                    onOpenQueue = openQueue
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val artworkSize = minOf(maxWidth, maxHeight, 260.dp).coerceAtLeast(0.dp)
                    ExpressiveArtworkFrame(
                        track = currentTrack,
                        artworkShape = themePrefs.artworkShape,
                        isPlaying = uiState.isPlaying,
                        decodeSizePx = 768,
                        modifier = Modifier
                            .size(artworkSize)
                            .aspectRatio(1f)
                            .testTag("hero_album_artwork")
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TrackIdentityBlock(
                        track = currentTrack,
                        onToggleFavorite = onToggleFavorite,
                        onOpenTrackMenu = openTrackMenu
                    )

                    ScrubberSection(
                        progressFlow = progressFlow,
                        fallbackDurationMs = currentTrack.durationMs,
                        isPlaying = uiState.isPlaying,
                        wavyEnabled = themePrefs.effectiveWavyScrubber,
                        onSeek = onSeek
                    )

                    ExpressiveTransportCluster(
                        uiState = uiState,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat
                    )

                    UtilityPillStrip(
                        playbackSpeed = uiState.playbackSpeed,
                        suggestedPalette = currentTrack.suggestedPalette,
                        activePalette = themePrefs.palette,
                        sleepTimerFlow = sleepTimerFlow,
                        onCycleSpeed = onCycleSpeed,
                        onOpenTimer = openTimer,
                        onMatchMood = onMatchMood
                    )
                }
            }
        }
    }

    if (showQueueSheet && currentTrack != null) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            QueueSheetContent(
                queueTracks = uiState.queueTracks,
                currentQueueIndex = uiState.currentQueueIndex,
                isPlaying = uiState.isPlaying,
                artworkShape = themePrefs.artworkShape,
                onSelectQueueIndex = { idx ->
                    onSelectQueueIndex(idx)
                    showQueueSheet = false
                },
                onMoveItem = onMoveQueueItem,
                onRemoveItem = onRemoveQueueItem,
                onClearQueue = onClearQueue,
                onSaveAsPlaylist = {
                    showQueueSheet = false
                    showSaveQueueDialog = true
                }
            )
        }
    }

    if (showTimerSheet) {
        val timerState by sleepTimerFlow.collectAsStateWithLifecycle()
        ModalBottomSheet(
            onDismissRequest = { showTimerSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            SleepTimerSheetContent(
                selected = timerState.option,
                onSelect = { option ->
                    onSelectSleepTimer(option)
                    showTimerSheet = false
                }
            )
        }
    }

    if (showTrackActions && currentTrack != null) {
        ModalBottomSheet(
            onDismissRequest = { showTrackActions = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            TrackActionsSheetContent(
                track = currentTrack,
                artworkShape = themePrefs.artworkShape,
                onPlayNext = {
                    showTrackActions = false
                    onPlayNextCurrent()
                },
                onAddToQueue = {
                    showTrackActions = false
                    onAddCurrentToQueue()
                },
                onAddToPlaylist = {
                    showTrackActions = false
                    showPlaylistPicker = true
                },
                onShowDetails = {
                    showTrackActions = false
                    showTrackDetails = true
                }
            )
        }
    }

    if (showPlaylistPicker && currentTrack != null) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistPicker = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            AddToPlaylistSheetContent(
                playlists = playlists,
                onSelectPlaylist = { plId ->
                    onAddCurrentToPlaylist(plId)
                    showPlaylistPicker = false
                },
                onCreateNewPlaylist = {
                    showPlaylistPicker = false
                    showNewPlaylistDialog = true
                }
            )
        }
    }

    if (showSaveQueueDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_save_queue_playlist),
            confirmLabel = "Save",
            onDismiss = { showSaveQueueDialog = false },
            onConfirm = { name ->
                onSaveQueueAsPlaylist(name)
                showSaveQueueDialog = false
            }
        )
    }

    if (showNewPlaylistDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_create_playlist),
            confirmLabel = "Create",
            onDismiss = { showNewPlaylistDialog = false },
            onConfirm = { name ->
                onCreatePlaylistWithCurrent(name)
                showNewPlaylistDialog = false
            }
        )
    }

    if (showTrackDetails && currentTrack != null) {
        TrackDetailsDialog(
            track = currentTrack,
            onDismiss = { showTrackDetails = false }
        )
    }
}

@Composable
private fun EmptyNowPlayingContent(
    artworkShape: com.example.ui.theme.ExpressiveArtworkShape,
    onOpenSettings: () -> Unit,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isUltraShort = maxHeight < 580.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isUltraShort) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isUltraShort) Arrangement.spacedBy(16.dp) else Arrangement.SpaceBetween
        ) {
            NowPlayingTopBar(
                hasTrack = false,
                isPlaying = false,
                isPreparing = false,
                onOpenSettings = onOpenSettings,
                onOpenQueue = {}
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ExpressiveArtworkFrame(
                    track = null,
                    artworkShape = artworkShape,
                    isPlaying = false,
                    animateScale = false,
                    modifier = Modifier
                        .size(if (isUltraShort) 152.dp else 196.dp)
                        .testTag("empty_now_playing_art")
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.now_playing_empty_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.now_playing_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 340.dp)
                )
            }

            Column(
                modifier = Modifier.widthIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddFolder,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("empty_scan_folder_button")
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_scan_local_folders),
                        style = MaterialTheme.typography.labelLarge
                    )
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
                    Text(
                        text = stringResource(R.string.action_scan_device),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    hasTrack: Boolean,
    isPlaying: Boolean,
    isPreparing: Boolean,
    onOpenSettings: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val statusText = when {
        !hasTrack -> stringResource(R.string.status_idle)
        isPreparing -> stringResource(R.string.status_loading)
        isPlaying -> stringResource(R.string.now_playing_label)
        else -> stringResource(R.string.status_paused)
    }
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(
        MaterialTheme.colorScheme.background,
        0.92f
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = pillColor,
            modifier = Modifier.testTag("now_playing_status_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioPulseBars(
                    isPlaying = isPlaying,
                    tint = MaterialTheme.colorScheme.primary,
                    barHeight = 13.dp
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            if (hasTrack) {
                FilledTonalIconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("queue_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.action_queue),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackIdentityBlock(
    track: Track,
    onToggleFavorite: () -> Unit,
    onOpenTrackMenu: () -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val favScale = if (performanceMode) {
        1f
    } else {
        animateFloatAsState(
            targetValue = if (track.isFavorite) 1.15f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "favorite_scale"
        ).value
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (performanceMode) {
            TrackTitleColumn(track = track, modifier = Modifier.weight(1f))
        } else {
            AnimatedContent(
                targetState = track.id,
                transitionSpec = {
                    (slideInVertically { it / 4 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 4 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "track_title_transition"
            ) { _ ->
                TrackTitleColumn(track = track)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("favorite_button")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_favorite),
                    tint = if (track.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(26.dp)
                        .let { if (favScale != 1f) it.scale(favScale) else it }
                )
            }
            IconButton(
                onClick = onOpenTrackMenu,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("now_playing_more_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun TrackTitleColumn(track: Track, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("now_playing_title")
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Text(
                text = track.genreTag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (track.isHiFi) {
            Spacer(Modifier.height(2.dp))
            HiFiQualityBadge(
                label = track.hiFiLabel,
                specDetail = track.hiFiSpecSummary,
                backgroundBehind = MaterialTheme.colorScheme.background,
                modifier = Modifier.testTag("now_playing_hifi_badge")
            )
        }
    }
}

/**
 * Strictly decoupled progress container:
 * - WavyExpressiveSlider reads progress in Draw phase via lambda (0 recompositions during playback!)
 * - ElapsedTimeText only recomposes when the displayed whole second changes (1x/sec max)
 * - DurationText only recomposes when track duration changes
 */
@Composable
private fun ScrubberSection(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long,
    isPlaying: Boolean,
    wavyEnabled: Boolean,
    onSeek: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ScrubberCanvasHost(
            progressFlow = progressFlow,
            fallbackDurationMs = fallbackDurationMs,
            isPlaying = isPlaying,
            wavyEnabled = wavyEnabled,
            onSeek = onSeek
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ElapsedTimeText(
                progressFlow = progressFlow,
                fallbackDurationMs = fallbackDurationMs
            )
            DurationTimeText(
                progressFlow = progressFlow,
                fallbackDurationMs = fallbackDurationMs
            )
        }
    }
}

@Composable
private fun ScrubberCanvasHost(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long,
    isPlaying: Boolean,
    wavyEnabled: Boolean,
    onSeek: (Float) -> Unit
) {
    val progressState = progressFlow.collectAsStateWithLifecycle()
    val progressProvider = remember(progressState, fallbackDurationMs) {
        {
            val p = progressState.value
            val dur = p.durationMs.takeIf { it > 0L } ?: fallbackDurationMs.coerceAtLeast(1L)
            (p.positionMs.coerceIn(0L, dur).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
    }
    WavyExpressiveSlider(
        progressProvider = progressProvider,
        isPlaying = isPlaying,
        wavyEnabled = wavyEnabled,
        onSeekFinished = onSeek
    )
}

@Composable
private fun ElapsedTimeText(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long
) {
    val elapsedSecondFlow = remember(progressFlow, fallbackDurationMs) {
        progressFlow
            .map { p ->
                val dur = p.durationMs.takeIf { it > 0L } ?: fallbackDurationMs.coerceAtLeast(1L)
                p.positionMs.coerceIn(0L, dur) / 1000L
            }
            .distinctUntilChanged()
    }
    val elapsedSec by elapsedSecondFlow.collectAsStateWithLifecycle(initialValue = 0L)
    Text(
        text = formatDuration(elapsedSec * 1000L),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DurationTimeText(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long
) {
    val durationFlow = remember(progressFlow, fallbackDurationMs) {
        progressFlow
            .map { p -> p.durationMs.takeIf { it > 0L } ?: fallbackDurationMs.coerceAtLeast(1L) }
            .distinctUntilChanged()
    }
    val durationMs by durationFlow.collectAsStateWithLifecycle(initialValue = fallbackDurationMs.coerceAtLeast(1L))
    Text(
        text = formatDuration(durationMs),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ExpressiveTransportCluster(
    uiState: NowPlayingUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    val inactiveIconBg = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(
        MaterialTheme.colorScheme.background,
        0.72f
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onToggleShuffle,
            modifier = Modifier
                .size(48.dp)
                .testTag("shuffle_button"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (uiState.shuffleEnabled) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    inactiveIconBg
                },
                contentColor = if (uiState.shuffleEnabled) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.action_shuffle),
                modifier = Modifier.size(20.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onPrevious,
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    bottomStart = 28.dp,
                    topEnd = 16.dp,
                    bottomEnd = 16.dp
                ),
                modifier = Modifier
                    .width(56.dp)
                    .height(60.dp)
                    .testTag("previous_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.action_previous),
                    modifier = Modifier.size(28.dp)
                )
            }

            MorphingPlayPauseButton(
                isPlaying = uiState.isPlaying,
                isPreparing = uiState.isPreparing,
                onClick = onTogglePlayPause
            )

            FilledTonalIconButton(
                onClick = onNext,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    bottomStart = 16.dp,
                    topEnd = 28.dp,
                    bottomEnd = 28.dp
                ),
                modifier = Modifier
                    .width(56.dp)
                    .height(60.dp)
                    .testTag("next_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.action_next),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        val repeatActive = uiState.repeatMode != RepeatMode.OFF
        FilledTonalIconButton(
            onClick = onCycleRepeat,
            modifier = Modifier
                .size(48.dp)
                .testTag("repeat_button"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (repeatActive) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    inactiveIconBg
                },
                contentColor = if (repeatActive) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        ) {
            Icon(
                imageVector = if (uiState.repeatMode == RepeatMode.ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = stringResource(R.string.action_repeat),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun UtilityPillStrip(
    playbackSpeed: Float,
    suggestedPalette: com.example.ui.theme.ExpressivePalette,
    activePalette: com.example.ui.theme.ExpressivePalette,
    sleepTimerFlow: StateFlow<SleepTimerState>,
    onCycleSpeed: () -> Unit,
    onOpenTimer: () -> Unit,
    onMatchMood: () -> Unit
) {
    val sleepTimer by sleepTimerFlow.collectAsStateWithLifecycle()
    val speedLabel = remember(playbackSpeed) {
        "${"%.2f".format(playbackSpeed).trimEnd('0').trimEnd('.')}×"
    }
    val timerLabel = when {
        sleepTimer.option == SleepTimerOption.OFF -> "Timer"
        sleepTimer.option == SleepTimerOption.END_OF_TRACK -> "Track end"
        sleepTimer.remainingMs != null -> formatDuration(sleepTimer.remainingMs!!)
        else -> sleepTimer.option.label
    }
    val moodMatches = activePalette == suggestedPalette

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveMiniActionPill(
            icon = Icons.Rounded.Speed,
            label = speedLabel,
            active = playbackSpeed != 1.0f,
            onClick = onCycleSpeed,
            testTag = "speed_pill"
        )

        ExpressiveMiniActionPill(
            icon = Icons.Rounded.Bedtime,
            label = timerLabel,
            active = sleepTimer.option != SleepTimerOption.OFF,
            onClick = onOpenTimer,
            testTag = "timer_pill"
        )

        ExpressiveMiniActionPill(
            icon = Icons.Rounded.AutoAwesome,
            label = if (moodMatches) "Synced" else "Match Mood",
            active = moodMatches,
            onClick = onMatchMood,
            testTag = "match_mood_pill"
        )
    }
}

@Composable
private fun ExpressiveMiniActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val idlePillColor = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(
        MaterialTheme.colorScheme.background,
        0.88f
    )
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            idlePillColor
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SleepTimerSheetContent(
    selected: SleepTimerOption,
    onSelect: (SleepTimerOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.sleep_timer_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        SleepTimerOption.entries.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelect(option) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
