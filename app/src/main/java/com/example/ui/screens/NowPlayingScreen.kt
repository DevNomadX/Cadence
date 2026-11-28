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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.PlaylistSummary
import com.example.data.RepeatMode
import com.example.data.SleepTimerOption
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.data.Track
import com.example.data.formatDuration
import com.example.data.lyrics.LyricsUiState
import com.example.playback.AudioOutputMonitor
import com.example.playback.AudioOutputState
import com.example.playback.PlaybackProgress
import com.example.playback.rememberAudioOutputState
import com.example.ui.components.AddToPlaylistSheetContent
import com.example.ui.components.AudioOutputSheetContent
import com.example.ui.components.AudioPulseBars
import com.example.ui.components.ExpandableAudioQualityBadge
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.FAST_THUMBNAIL_DECODE_PX
import com.example.ui.components.LyricsSheetContent
import com.example.ui.components.MorphingPlayPauseButton
import com.example.ui.components.audioOutputIconFor
import com.example.ui.components.PlaylistNameDialog
import com.example.ui.components.QueueSheetContent
import com.example.ui.components.TrackActionsSheetContent
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.components.WavyExpressiveSlider
import com.example.ui.icons.CadenceIcons
import com.example.ui.player.NowPlayingUiState
import com.example.ui.player.SleepTimerState
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val DefaultLyricsFlow: StateFlow<LyricsUiState> = MutableStateFlow(LyricsUiState.Idle)

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
    onCycleSpeed: () -> Unit = {},
    onSelectPlaybackSpeed: (Float) -> Unit = {},
    onMatchMood: () -> Unit,
    onCollapse: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onAddFolder: () -> Unit = {},
    onScanDevice: () -> Unit = {},
    onSelectQueueIndex: (Int) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    onAddCurrentToPlaylist: (Long) -> Unit = {},
    onCreatePlaylistWithCurrent: (String) -> Unit = {},
    onPlayNextCurrent: () -> Unit = {},
    onAddCurrentToQueue: () -> Unit = {},
    onSelectSleepTimer: (SleepTimerOption) -> Unit = {},
    audioOutputStateOverride: AudioOutputState? = null,
    lyricsStateFlow: StateFlow<LyricsUiState> = DefaultLyricsFlow,
    onRequestLyrics: (Track) -> Unit = {},
    onSeekToMs: (Long) -> Unit = {},
    dismissDragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioOutputState = audioOutputStateOverride ?: rememberAudioOutputState()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var showTrackActions by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var showTrackDetails by remember { mutableStateOf(false) }

    val openQueue = remember { { showQueueSheet = true } }
    val openTimer = remember { { showTimerSheet = true } }
    val openAudioOutput = remember(context) {
        {
            if (!AudioOutputMonitor.openSystemOutputSwitcher(context)) {
                showAudioOutputSheet = true
            }
        }
    }
    val openTrackMenu = remember { { showTrackActions = true } }

    val currentTrack = uiState.currentTrack
    val openLyrics = remember(currentTrack, onRequestLyrics) {
        {
            if (currentTrack != null) {
                onRequestLyrics(currentTrack)
                showLyricsSheet = true
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 640.dp
        val isUltraShortPortrait = maxHeight < 560.dp
        val heroDecodeSizePx = if (themePrefs.performanceModeEnabled) 512 else 768

        if (currentTrack == null && !uiState.isInitialLibraryLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("initial_loading_placeholder")
            )
        } else if (currentTrack == null) {
            EmptyNowPlayingContent(
                artworkShape = themePrefs.artworkShape,
                onCollapse = onCollapse,
                onOpenSettings = onOpenSettings,
                onAddFolder = onAddFolder,
                onScanDevice = onScanDevice,
                dismissDragModifier = dismissDragModifier
            )
        } else if (isWide) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                NowPlayingDragHandle(modifier = dismissDragModifier)

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(dismissDragModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveArtworkFrame(
                            track = currentTrack,
                            artworkShape = themePrefs.artworkShape,
                            isPlaying = uiState.isPlaying,
                            decodeSizePx = heroDecodeSizePx,
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .fillMaxWidth(0.86f)
                                .aspectRatio(1f)
                                .testTag("hero_album_artwork")
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NowPlayingTopBar(
                            hasTrack = true,
                            isPlaying = uiState.isPlaying,
                            isPreparing = uiState.isPreparing,
                            audioOutputState = audioOutputState,
                            onCollapse = onCollapse,
                            onOpenAudioOutput = openAudioOutput,
                            onOpenQueue = openQueue,
                            modifier = dismissDragModifier
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
                            suggestedPalette = currentTrack.suggestedPalette,
                            activePalette = themePrefs.palette,
                            sleepTimerFlow = sleepTimerFlow,
                            onOpenLyrics = openLyrics,
                            onOpenTimer = openTimer,
                            onMatchMood = onMatchMood
                        )
                    }
                }
            }
        } else if (isUltraShortPortrait) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("now_playing_portrait_container")
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NowPlayingDragHandle(modifier = dismissDragModifier)
                    NowPlayingTopBar(
                        hasTrack = true,
                        isPlaying = uiState.isPlaying,
                        isPreparing = uiState.isPreparing,
                        audioOutputState = audioOutputState,
                        onCollapse = onCollapse,
                        onOpenAudioOutput = openAudioOutput,
                        onOpenQueue = openQueue,
                        modifier = dismissDragModifier
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dismissDragModifier),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveArtworkFrame(
                        track = currentTrack,
                        artworkShape = themePrefs.artworkShape,
                        isPlaying = uiState.isPlaying,
                        decodeSizePx = heroDecodeSizePx,
                        modifier = Modifier
                            .size(176.dp)
                            .aspectRatio(1f)
                            .testTag("hero_album_artwork")
                    )
                }

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
                    suggestedPalette = currentTrack.suggestedPalette,
                    activePalette = themePrefs.palette,
                    sleepTimerFlow = sleepTimerFlow,
                    onOpenLyrics = openLyrics,
                    onOpenTimer = openTimer,
                    onMatchMood = onMatchMood
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("now_playing_portrait_container")
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Swipe-down gesture attached strictly to Drag Handle, Top Header & Artwork Area (never the seek bar)
                NowPlayingDragHandle(modifier = dismissDragModifier)

                NowPlayingTopBar(
                    hasTrack = true,
                    isPlaying = uiState.isPlaying,
                    isPreparing = uiState.isPreparing,
                    audioOutputState = audioOutputState,
                    onCollapse = onCollapse,
                    onOpenAudioOutput = openAudioOutput,
                    onOpenQueue = openQueue,
                    modifier = dismissDragModifier
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .then(dismissDragModifier),
                    contentAlignment = Alignment.Center
                ) {
                    val artworkSize = minOf(maxWidth, maxHeight, 260.dp).coerceAtLeast(0.dp)
                    ExpressiveArtworkFrame(
                        track = currentTrack,
                        artworkShape = themePrefs.artworkShape,
                        isPlaying = uiState.isPlaying,
                        decodeSizePx = heroDecodeSizePx,
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

                    Spacer(Modifier.height(2.dp))

                    UtilityPillStrip(
                        suggestedPalette = currentTrack.suggestedPalette,
                        activePalette = themePrefs.palette,
                        sleepTimerFlow = sleepTimerFlow,
                        onOpenLyrics = openLyrics,
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

    if (showAudioOutputSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAudioOutputSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            AudioOutputSheetContent(
                outputState = audioOutputState,
                onOpenSoundSettings = { openBluetooth ->
                    showAudioOutputSheet = false
                    AudioOutputMonitor.openSystemAudioSettings(
                        context = context,
                        openBluetooth = openBluetooth
                    )
                }
            )
        }
    }

    if (showLyricsSheet && currentTrack != null) {
        val lyricsState by lyricsStateFlow.collectAsStateWithLifecycle()
        ModalBottomSheet(
            onDismissRequest = { showLyricsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            LyricsSheetContent(
                track = currentTrack,
                lyricsState = lyricsState,
                progressFlow = progressFlow,
                onSeekToMs = onSeekToMs
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
                },
                playbackSpeed = uiState.playbackSpeed,
                onSelectPlaybackSpeed = onSelectPlaybackSpeed
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
    onCollapse: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit,
    dismissDragModifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isUltraShort = maxHeight < 560.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isUltraShort) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isUltraShort) Arrangement.spacedBy(16.dp) else Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                NowPlayingDragHandle(modifier = dismissDragModifier)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dismissDragModifier),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassHeaderCircleButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.action_collapse_player),
                        onClick = onCollapse,
                        testTag = "collapse_now_playing_button"
                    )

                    GlassHeaderCircleButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                        onClick = onOpenSettings,
                        testTag = "settings_button"
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ExpressiveArtworkFrame(
                    track = null,
                    artworkShape = artworkShape,
                    isPlaying = false,
                    animateScale = false,
                    decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
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
                    Icon(CadenceIcons.CreateNewFolder, contentDescription = null, modifier = Modifier.size(20.dp))
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
                    Icon(CadenceIcons.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
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

/**
 * Centered 36dp × 4dp top drag handle wired to the sheet swipe-down dismissal gesture.
 */
@Composable
private fun NowPlayingDragHandle(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("now_playing_drag_handle")
            .then(modifier)
            .padding(top = 10.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f))
                .testTag("now_playing_drag_handle_pill")
        )
    }
}

/**
 * Theme-aware 4-element glassmorphic Now Playing top header (all 4 items equal 42dp height, zero blur):
 * - Left group (8dp gap):
 *   1. Standalone 42dp circular minimize chevron (⌄) button
 *   2. Separate 42dp CircleShape status pill (3-bar draw-phase equalizer + "NOW PLAYING" / "PAUSED")
 * - Right group (8dp gap):
 *   3. 42dp circular Audio Output / Bluetooth switcher
 *   4. 42dp circular Queue button
 */
@Composable
private fun NowPlayingTopBar(
    hasTrack: Boolean,
    isPlaying: Boolean,
    isPreparing: Boolean,
    audioOutputState: AudioOutputState,
    onCollapse: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        !hasTrack -> stringResource(R.string.status_idle)
        isPreparing -> stringResource(R.string.status_loading)
        isPlaying -> stringResource(R.string.now_playing_label)
        else -> stringResource(R.string.status_paused)
    }
    val glassSurfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
    val glassBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    val headerContentTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("now_playing_top_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Group: 42dp Minimize Circle + 8dp gap + separate 42dp Status Pill
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassHeaderCircleButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.action_collapse_player),
                onClick = onCollapse,
                testTag = "collapse_now_playing_button"
            )

            Row(
                modifier = Modifier
                    .testTag("now_playing_status_pill")
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(glassSurfaceColor, CircleShape)
                    .border(glassBorder, CircleShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioPulseBars(
                    isPlaying = isPlaying,
                    tint = MaterialTheme.colorScheme.primary,
                    barHeight = 14.dp,
                    barCount = 3
                )
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    color = headerContentTint,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right Group: 42dp Audio Output Circle + 8dp gap + 42dp Queue Circle
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassHeaderCircleButton(
                icon = audioOutputIconFor(audioOutputState.activeRoute),
                contentDescription = "${stringResource(R.string.action_audio_output)}: ${audioOutputState.activeDeviceName}",
                onClick = onOpenAudioOutput,
                testTag = "audio_output_button"
            )

            if (hasTrack) {
                GlassHeaderCircleButton(
                    icon = CadenceIcons.QueueMusic,
                    contentDescription = stringResource(R.string.action_queue),
                    onClick = onOpenQueue,
                    testTag = "queue_button"
                )
            }
        }
    }
}

@Composable
private fun GlassHeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f), CircleShape)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                CircleShape
            )
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TrackIdentityBlock(
    track: Track,
    onToggleFavorite: () -> Unit,
    onOpenTrackMenu: () -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val audioSpec = remember(
        track.id,
        track.codec,
        track.mimeType,
        track.localUriString,
        track.bitDepth,
        track.sampleRateHz,
        track.bitrateKbps
    ) {
        track.resolveAudioQualitySpec()
    }
    var audioSpecsExpanded by remember(track.id) { mutableStateOf(false) }

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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                        .size(48.dp)
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

        if (audioSpec != null) {
            Spacer(Modifier.height(10.dp))
            ExpandableAudioQualityBadge(
                spec = audioSpec,
                expanded = audioSpecsExpanded,
                onToggleExpanded = { audioSpecsExpanded = !audioSpecsExpanded },
                backgroundBehind = MaterialTheme.colorScheme.background
            )
        }
    }
}

@Composable
private fun TrackTitleColumn(track: Track, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
                letterSpacing = (-0.2).sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("now_playing_title")
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("now_playing_artist")
        )
    }
}

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
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
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
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = MonoFontFamily,
            fontFeatureSettings = "tnum"
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("scrubber_elapsed_time")
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
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = MonoFontFamily,
            fontFeatureSettings = "tnum"
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("scrubber_duration_time")
    )
}

/**
 * Transport Controls Row with exact M3 Expressive shapes and uniform 10dp horizontal spacing:
 * - Shuffle (44dp circle)
 * - Previous (58x64dp asymmetric D-shape: 32dp outer / 16dp inner corners)
 * - Play/Pause (76dp morphing full circle <-> 24dp squircle)
 * - Next (58x64dp asymmetric D-shape: 16dp inner / 32dp outer corners)
 * - Repeat (44dp circle)
 */
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
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transport_controls_row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onToggleShuffle,
            shape = CircleShape,
            modifier = Modifier
                .size(44.dp)
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
                imageVector = CadenceIcons.Shuffle,
                contentDescription = stringResource(R.string.action_shuffle),
                modifier = Modifier.size(20.dp)
            )
        }

        FilledTonalIconButton(
            onClick = onPrevious,
            shape = RoundedCornerShape(
                topStart = 32.dp,
                bottomStart = 32.dp,
                topEnd = 16.dp,
                bottomEnd = 16.dp
            ),
            modifier = Modifier
                .width(58.dp)
                .height(64.dp)
                .testTag("previous_button"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = CadenceIcons.SkipPrevious,
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
                topEnd = 32.dp,
                bottomEnd = 32.dp
            ),
            modifier = Modifier
                .width(58.dp)
                .height(64.dp)
                .testTag("next_button"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = CadenceIcons.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                modifier = Modifier.size(28.dp)
            )
        }

        val repeatActive = uiState.repeatMode != RepeatMode.OFF
        FilledTonalIconButton(
            onClick = onCycleRepeat,
            shape = CircleShape,
            modifier = Modifier
                .size(44.dp)
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
                    CadenceIcons.RepeatOne
                } else {
                    CadenceIcons.Repeat
                },
                contentDescription = stringResource(R.string.action_repeat),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 3-Pill bottom utility strip: Lyrics, Timer, Match Mood.
 * (Playback speed moved into Audio DSP sheet & track overflow menu.)
 */
@Composable
private fun UtilityPillStrip(
    suggestedPalette: com.example.ui.theme.ExpressivePalette,
    activePalette: com.example.ui.theme.ExpressivePalette,
    sleepTimerFlow: StateFlow<SleepTimerState>,
    onOpenLyrics: () -> Unit,
    onOpenTimer: () -> Unit,
    onMatchMood: () -> Unit
) {
    val sleepTimer by sleepTimerFlow.collectAsStateWithLifecycle()
    val timerLabel = when {
        sleepTimer.option == SleepTimerOption.OFF -> "Timer"
        sleepTimer.option == SleepTimerOption.END_OF_TRACK -> "Track end"
        sleepTimer.remainingMs != null -> formatDuration(sleepTimer.remainingMs!!)
        else -> sleepTimer.option.label
    }
    val moodMatches = activePalette == suggestedPalette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("utility_pill_strip"),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveMiniActionPill(
            icon = CadenceIcons.Lyrics,
            label = stringResource(R.string.action_lyrics),
            active = false,
            onClick = onOpenLyrics,
            testTag = "lyrics_pill"
        )

        ExpressiveMiniActionPill(
            icon = CadenceIcons.Bedtime,
            label = timerLabel,
            active = sleepTimer.option != SleepTimerOption.OFF,
            onClick = onOpenTimer,
            testTag = "timer_pill"
        )

        ExpressiveMiniActionPill(
            icon = CadenceIcons.AutoAwesome,
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
            modifier = Modifier.padding(horizontal = 12.dp),
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
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
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
