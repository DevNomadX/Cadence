package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.StableList
import com.example.data.Track
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.ambientAuraBackdrop
import com.example.ui.player.PlayerViewModel
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.opaqueOver

enum class CadenceTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    NOW_PLAYING("now_playing", R.string.nav_now_playing, Icons.Rounded.Album, Icons.Outlined.Album),
    LIBRARY("library", R.string.nav_library, Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic)
}

@Composable
fun CadenceApp(
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val themePrefs by viewModel.themeState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlayingState.collectAsStateWithLifecycle()
    val hasDrillDown by viewModel.hasLibraryDrillDown.collectAsStateWithLifecycle()

    var activeTab by rememberSaveable { mutableStateOf(CadenceTab.NOW_PLAYING) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    CadenceSnackbarObserver(
        snackbarFlow = viewModel.snackbarMessage,
        snackbarHostState = snackbarHostState,
        onShown = viewModel::clearSnackbar
    )

    val audioFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(uris)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.addMusicFolder(treeUri)
        }
    }

    val readAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.scanDeviceMediaStore()
        } else {
            folderPickerLauncher.launch(null)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Playback works regardless; notification shows if granted */ }

    val ensureNotificationPermissionOnce = remember(context, notificationPermissionLauncher) {
        {
            if (!askedNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askedNotificationPermission = true
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    val triggerDeviceScan = remember(context, viewModel, readAudioPermissionLauncher, folderPickerLauncher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    viewModel.scanDeviceMediaStore()
                } else {
                    readAudioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                }
            } else {
                // On Android <= 12, use zero-permission SAF folder picker to comply strictly with Play storage rules
                folderPickerLauncher.launch(null)
            }
        }
    }

    // Stabilized action lambdas so screen & list composables skip recomposition cleanly
    val onOpenSettings = remember { { showSettings = true } }
    val onCloseSettings = remember { { showSettings = false } }
    val onExpandMiniPlayer = remember { { activeTab = CadenceTab.NOW_PLAYING } }
    val onSelectTab = remember {
        { tab: CadenceTab ->
            showSettings = false
            activeTab = tab
        }
    }
    val onImportFiles = remember(audioFilesLauncher) {
        { audioFilesLauncher.launch(arrayOf("audio/*")) }
    }
    val onAddFolder = remember(folderPickerLauncher) {
        { folderPickerLauncher.launch(null) }
    }
    val onTogglePlayPause = remember(viewModel, ensureNotificationPermissionOnce) {
        {
            ensureNotificationPermissionOnce()
            viewModel.togglePlayPause()
        }
    }
    val onToggleFavoriteCurrent = remember(viewModel) { { viewModel.toggleFavorite() } }
    val onAddCurrentToPlaylist = remember(viewModel) {
        { plId: Long ->
            viewModel.nowPlayingState.value.currentTrack?.let { track ->
                viewModel.addTrackToPlaylist(plId, track)
            }
            Unit
        }
    }
    val onCreatePlaylistWithCurrent = remember(viewModel) {
        { name: String ->
            viewModel.nowPlayingState.value.currentTrack?.let { track ->
                viewModel.createPlaylist(name, track.id)
            }
            Unit
        }
    }
    val onPlayNextCurrent = remember(viewModel) {
        {
            viewModel.nowPlayingState.value.currentTrack?.let(viewModel::playNext)
            Unit
        }
    }
    val onAddCurrentToQueue = remember(viewModel) {
        {
            viewModel.nowPlayingState.value.currentTrack?.let(viewModel::addToQueue)
            Unit
        }
    }
    val onSelectTrackFromLibrary = remember(viewModel, ensureNotificationPermissionOnce) {
        { track: Track, contextList: StableList<Track> ->
            ensureNotificationPermissionOnce()
            viewModel.selectTrackFromList(track, contextList)
            activeTab = CadenceTab.NOW_PLAYING
        }
    }
    val onPlayTracksFromLibrary = remember(viewModel, ensureNotificationPermissionOnce) {
        { list: List<Track>, shuffle: Boolean ->
            ensureNotificationPermissionOnce()
            viewModel.playTracks(list, startIndex = 0, shuffle = shuffle)
            activeTab = CadenceTab.NOW_PLAYING
        }
    }
    val onPlayPlaylistFromLibrary = remember(viewModel, ensureNotificationPermissionOnce) {
        { plId: Long, shuffle: Boolean ->
            ensureNotificationPermissionOnce()
            viewModel.playPlaylist(plId, shuffle)
            activeTab = CadenceTab.NOW_PLAYING
        }
    }
    val onGenerateSmartPlaylist = remember(viewModel) {
        { mood: String?, seed: Boolean ->
            viewModel.generateSmartPlaylist(targetMood = mood, seedFromCurrent = seed)
        }
    }

    BackHandler(enabled = !showSettings && activeTab != CadenceTab.NOW_PLAYING && !hasDrillDown) {
        activeTab = CadenceTab.NOW_PLAYING
    }

    CadenceTheme(
        palette = themePrefs.palette,
        appearanceMode = themePrefs.appearanceMode,
        performanceMode = themePrefs.performanceModeEnabled
    ) {
        val performanceMode = LocalPerformanceMode.current
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .ambientAuraBackdrop(
                    enabled = themePrefs.effectiveAmbientAura,
                    isPlaying = nowPlaying.isPlaying
                )
        ) {
            val useNavigationRail = maxWidth >= 720.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = if (useNavigationRail) 12.dp else 8.dp)
                    )
                },
                bottomBar = {
                    if (!useNavigationRail && !showSettings) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val currentTrack = nowPlaying.currentTrack
                            val showMiniPlayer = activeTab != CadenceTab.NOW_PLAYING && currentTrack != null

                            if (performanceMode) {
                                if (currentTrack != null && activeTab != CadenceTab.NOW_PLAYING) {
                                    FloatingMiniPlayerPill(
                                        track = currentTrack,
                                        isPlaying = nowPlaying.isPlaying,
                                        artworkShape = themePrefs.artworkShape,
                                        onExpand = onExpandMiniPlayer,
                                        onTogglePlayPause = onTogglePlayPause,
                                        onNext = viewModel::nextTrack
                                    )
                                }
                            } else {
                                AnimatedVisibility(
                                    visible = showMiniPlayer,
                                    enter = slideInVertically { it } + fadeIn(),
                                    exit = slideOutVertically { it } + fadeOut()
                                ) {
                                    if (currentTrack != null) {
                                        FloatingMiniPlayerPill(
                                            track = currentTrack,
                                            isPlaying = nowPlaying.isPlaying,
                                            artworkShape = themePrefs.artworkShape,
                                            onExpand = onExpandMiniPlayer,
                                            onTogglePlayPause = onTogglePlayPause,
                                            onNext = viewModel::nextTrack
                                        )
                                    }
                                }
                            }

                            FloatingExpressiveNavDock(
                                activeTab = activeTab,
                                onSelectTab = onSelectTab
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        val railBg = MaterialTheme.colorScheme.surfaceContainerLow.opaqueOver(
                            MaterialTheme.colorScheme.background,
                            0.94f
                        )
                        NavigationRail(
                            containerColor = railBg,
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                        ) {
                            Spacer(Modifier.height(16.dp))
                            CadenceTab.entries.forEach { tab ->
                                val selected = !showSettings && activeTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { onSelectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            NavigationRailItem(
                                selected = showSettings,
                                onClick = onOpenSettings,
                                icon = {
                                    Icon(
                                        imageVector = if (showSettings) Icons.Rounded.Settings else Icons.Outlined.Settings,
                                        contentDescription = stringResource(R.string.nav_settings)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_settings)) },
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .testTag("rail_settings_button")
                            )
                        }
                    }

                    val contentKey = if (showSettings) "settings" else activeTab.route

                    if (performanceMode) {
                        Box(modifier = Modifier.weight(1f)) {
                            CadenceDestinationContent(
                                contentKey = contentKey,
                                viewModel = viewModel,
                                themePrefs = themePrefs,
                                nowPlaying = nowPlaying,
                                onOpenSettings = onOpenSettings,
                                onCloseSettings = onCloseSettings,
                                onTogglePlayPause = onTogglePlayPause,
                                onToggleFavoriteCurrent = onToggleFavoriteCurrent,
                                onAddCurrentToPlaylist = onAddCurrentToPlaylist,
                                onCreatePlaylistWithCurrent = onCreatePlaylistWithCurrent,
                                onPlayNextCurrent = onPlayNextCurrent,
                                onAddCurrentToQueue = onAddCurrentToQueue,
                                onSelectTrackFromLibrary = onSelectTrackFromLibrary,
                                onPlayTracksFromLibrary = onPlayTracksFromLibrary,
                                onPlayPlaylistFromLibrary = onPlayPlaylistFromLibrary,
                                onImportFiles = onImportFiles,
                                onAddFolder = onAddFolder,
                                onScanDevice = triggerDeviceScan,
                                onGenerateSmartPlaylist = onGenerateSmartPlaylist
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = contentKey,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            modifier = Modifier.weight(1f),
                            label = "destination_content"
                        ) { key ->
                            CadenceDestinationContent(
                                contentKey = key,
                                viewModel = viewModel,
                                themePrefs = themePrefs,
                                nowPlaying = nowPlaying,
                                onOpenSettings = onOpenSettings,
                                onCloseSettings = onCloseSettings,
                                onTogglePlayPause = onTogglePlayPause,
                                onToggleFavoriteCurrent = onToggleFavoriteCurrent,
                                onAddCurrentToPlaylist = onAddCurrentToPlaylist,
                                onCreatePlaylistWithCurrent = onCreatePlaylistWithCurrent,
                                onPlayNextCurrent = onPlayNextCurrent,
                                onAddCurrentToQueue = onAddCurrentToQueue,
                                onSelectTrackFromLibrary = onSelectTrackFromLibrary,
                                onPlayTracksFromLibrary = onPlayTracksFromLibrary,
                                onPlayPlaylistFromLibrary = onPlayPlaylistFromLibrary,
                                onImportFiles = onImportFiles,
                                onAddFolder = onAddFolder,
                                onScanDevice = triggerDeviceScan,
                                onGenerateSmartPlaylist = onGenerateSmartPlaylist
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CadenceDestinationContent(
    contentKey: String,
    viewModel: PlayerViewModel,
    themePrefs: com.example.data.ThemePreferences,
    nowPlaying: com.example.ui.player.NowPlayingUiState,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavoriteCurrent: () -> Unit,
    onAddCurrentToPlaylist: (Long) -> Unit,
    onCreatePlaylistWithCurrent: (String) -> Unit,
    onPlayNextCurrent: () -> Unit,
    onAddCurrentToQueue: () -> Unit,
    onSelectTrackFromLibrary: (Track, StableList<Track>) -> Unit,
    onPlayTracksFromLibrary: (List<Track>, Boolean) -> Unit,
    onPlayPlaylistFromLibrary: (Long, Boolean) -> Unit,
    onImportFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit,
    onGenerateSmartPlaylist: (String?, Boolean) -> Unit
) {
    when (contentKey) {
        "settings" -> {
            val audioSettings by viewModel.audioSettingsState.collectAsStateWithLifecycle()
            val savedFolders by viewModel.savedFoldersState.collectAsStateWithLifecycle()
            val minDurationSec by viewModel.minTrackDurationSecState.collectAsStateWithLifecycle()
            SettingsScreen(
                themePrefs = themePrefs,
                audioSettings = audioSettings,
                savedFolders = savedFolders,
                minTrackDurationSec = minDurationSec,
                currentTrack = nowPlaying.currentTrack,
                isPlaying = nowPlaying.isPlaying,
                onBack = onCloseSettings,
                onSelectPalette = viewModel::setPalette,
                onSelectAppearance = viewModel::setAppearanceMode,
                onSelectArtworkShape = viewModel::setArtworkShape,
                onToggleWavyScrubber = viewModel::setWavyScrubberEnabled,
                onToggleAmbientAura = viewModel::setAmbientAuraEnabled,
                onTogglePerformanceMode = viewModel::setPerformanceModeEnabled,
                onAddFolder = onAddFolder,
                onRemoveFolder = viewModel::removeSavedFolder,
                onRescanFolders = viewModel::rescanSavedFolders,
                onSelectMinTrackDurationSec = viewModel::setMinTrackDurationSec,
                onToggleGaplessPlayback = viewModel::setGaplessPlaybackEnabled,
                onChangeCrossfadeSec = viewModel::setCrossfadeDurationSec,
                onTogglePauseOnHeadphoneDisconnect = viewModel::setPauseOnHeadphoneDisconnect,
                onSelectAudioFocusBehavior = viewModel::setAudioFocusBehavior
            )
        }

        CadenceTab.LIBRARY.route -> {
            val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
            LibraryScreen(
                uiState = libraryState,
                currentTrackId = nowPlaying.currentTrack?.id,
                isPlaying = nowPlaying.isPlaying,
                artworkShape = themePrefs.artworkShape,
                onSelectTrack = onSelectTrackFromLibrary,
                onPlayTracks = onPlayTracksFromLibrary,
                onToggleFavorite = viewModel::toggleFavorite,
                onSearchChange = viewModel::updateSearchQuery,
                onCategoryChange = viewModel::setLibraryCategory,
                onSortChange = viewModel::setSortOrder,
                onOpenDrillDown = viewModel::openDrillDown,
                onCloseDrillDown = viewModel::closeDrillDown,
                onImportFiles = onImportFiles,
                onAddFolder = onAddFolder,
                onScanDevice = onScanDevice,
                onRescanFolders = viewModel::rescanSavedFolders,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                onCreatePlaylist = viewModel::createPlaylist,
                onRenamePlaylist = viewModel::renamePlaylist,
                onDeletePlaylist = viewModel::deletePlaylist,
                onPlayPlaylist = onPlayPlaylistFromLibrary,
                onRemoveTrack = viewModel::removeTrack,
                onOpenSettings = onOpenSettings,
                scanStateFlow = viewModel.scanState,
                smartPlaylistFlow = viewModel.smartPlaylistState,
                currentTrackTitle = nowPlaying.currentTrack?.title.orEmpty(),
                onGenerateSmartPlaylist = onGenerateSmartPlaylist,
                onSaveSmartPlaylist = viewModel::saveSmartPlaylist
            )
        }

        else -> {
            val playlists by viewModel.playlistsState.collectAsStateWithLifecycle()
            NowPlayingScreen(
                uiState = nowPlaying,
                themePrefs = themePrefs,
                progressFlow = viewModel.progressState,
                sleepTimerFlow = viewModel.sleepTimerState,
                playlists = playlists,
                onTogglePlayPause = onTogglePlayPause,
                onNext = viewModel::nextTrack,
                onPrevious = viewModel::previousTrack,
                onSeek = viewModel::seekToFraction,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleFavorite = onToggleFavoriteCurrent,
                onCycleSpeed = viewModel::cyclePlaybackSpeed,
                onMatchMood = viewModel::matchCurrentTrackMood,
                onOpenSettings = onOpenSettings,
                onAddFolder = onAddFolder,
                onScanDevice = onScanDevice,
                onSelectQueueIndex = viewModel::selectQueueIndex,
                onMoveQueueItem = viewModel::moveQueueItem,
                onRemoveQueueItem = viewModel::removeQueueItem,
                onClearQueue = viewModel::clearQueue,
                onSaveQueueAsPlaylist = viewModel::saveQueueAsPlaylist,
                onAddCurrentToPlaylist = onAddCurrentToPlaylist,
                onCreatePlaylistWithCurrent = onCreatePlaylistWithCurrent,
                onPlayNextCurrent = onPlayNextCurrent,
                onAddCurrentToQueue = onAddCurrentToQueue,
                onSelectSleepTimer = viewModel::setSleepTimer
            )
        }
    }
}

@Composable
private fun FloatingMiniPlayerPill(
    track: Track,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val pillBg = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(
        MaterialTheme.colorScheme.background,
        0.96f
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = pillBg,
        tonalElevation = if (performanceMode) 0.dp else 4.dp,
        shadowElevation = if (performanceMode) 0.dp else 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onExpand)
            .testTag("mini_player_pill")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveArtworkFrame(
                track = track,
                artworkShape = artworkShape,
                isPlaying = isPlaying,
                animateScale = false,
                decodeSizePx = 128,
                modifier = Modifier.size(44.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mini_play_pause_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.action_pause else R.string.action_play
                    ),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mini_next_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.action_next),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FloatingExpressiveNavDock(
    activeTab: CadenceTab,
    onSelectTab: (CadenceTab) -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val dockBg = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(
        MaterialTheme.colorScheme.background,
        0.96f
    )
    Surface(
        shape = CircleShape,
        color = dockBg,
        tonalElevation = if (performanceMode) 0.dp else 4.dp,
        shadowElevation = if (performanceMode) 0.dp else 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CadenceTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val onPickTab = remember(tab, onSelectTab) { { onSelectTab(tab) } }
                Surface(
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        dockBg
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPickTab)
                        .testTag("nav_${tab.route}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(tab.labelRes),
                            modifier = Modifier.size(21.dp)
                        )
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CadenceSnackbarObserver(
    snackbarFlow: kotlinx.coroutines.flow.StateFlow<String?>,
    snackbarHostState: SnackbarHostState,
    onShown: () -> Unit
) {
    val message by snackbarFlow.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onShown()
        }
    }
}
