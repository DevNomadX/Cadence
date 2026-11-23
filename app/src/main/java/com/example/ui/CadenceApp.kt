package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.StableList
import com.example.data.Track
import com.example.data.library.ScanState
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.FastListThumbnailShape
import com.example.ui.components.ambientAuraBackdrop
import com.example.ui.player.PlayerViewModel
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Keep
enum class CadenceTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    NOW_PLAYING("now_playing", R.string.nav_now_playing, Icons.Rounded.Album, Icons.Outlined.Album),
    LIBRARY("library", R.string.nav_library, Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    DISCOVER("discover", R.string.nav_discover, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome);

    companion object {
        fun fromRoute(route: String?): CadenceTab =
            entries.firstOrNull { it.route == route } ?: NOW_PLAYING
    }
}

private val CadenceTabSaver: Saver<CadenceTab, String> = Saver(
    save = { it.route },
    restore = { CadenceTab.fromRoute(it) }
)

@Composable
fun CadenceApp(
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val themePrefs by viewModel.themeState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlayingState.collectAsStateWithLifecycle()
    val hasDrillDown by viewModel.hasLibraryDrillDown.collectAsStateWithLifecycle()

    var activeTab by rememberSaveable(stateSaver = CadenceTabSaver) { mutableStateOf(CadenceTab.NOW_PLAYING) }
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

    BackHandler(enabled = !showSettings && activeTab != CadenceTab.NOW_PLAYING && !(activeTab == CadenceTab.LIBRARY && hasDrillDown)) {
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

            CadenceScanGate(
                scanStateFlow = viewModel.scanState,
                artworkShape = themePrefs.artworkShape
            ) {
                val currentTrack = nowPlaying.currentTrack
                val showFloatingDock = !useNavigationRail && !showSettings
                val showMiniPlayer = showFloatingDock && activeTab != CadenceTab.NOW_PLAYING && currentTrack != null
                val contentKey = if (showSettings) "settings" else activeTab.route

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("cadence_floating_root")
                ) {
                    // Full-bleed screen content extending all the way behind the floating pills
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("cadence_content_container")
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
                                    .navigationBarsPadding()
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

                        val contentInsetsModifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                )
                            )

                        if (performanceMode) {
                            Box(modifier = contentInsetsModifier) {
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
                                modifier = contentInsetsModifier,
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

                    // Truly floating bottom overlay with lightweight zero-blur vertical gradient scrim
                    if (showFloatingDock) {
                        FloatingBottomDockOverlay(
                            activeTab = activeTab,
                            currentTrack = currentTrack,
                            isPlaying = nowPlaying.isPlaying,
                            artworkShape = themePrefs.artworkShape,
                            onSelectTab = onSelectTab,
                            onExpandMiniPlayer = onExpandMiniPlayer,
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = viewModel::nextTrack,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(
                                bottom = when {
                                    !showFloatingDock -> 16.dp
                                    showMiniPlayer -> 154.dp
                                    else -> 84.dp
                                }
                            )
                    )
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
                scanStateFlow = viewModel.scanState
            )
        }

        CadenceTab.DISCOVER.route -> {
            val discoverState by viewModel.discoverState.collectAsStateWithLifecycle()
            val onPlaySection = remember(onPlayTracksFromLibrary) {
                { tracks: StableList<Track> -> onPlayTracksFromLibrary(tracks, false) }
            }
            DiscoverScreen(
                uiState = discoverState,
                currentTrackId = nowPlaying.currentTrack?.id,
                isPlaying = nowPlaying.isPlaying,
                artworkShape = themePrefs.artworkShape,
                onSelectTrackFromSection = onSelectTrackFromLibrary,
                onPlaySection = onPlaySection,
                onRefreshDiscover = viewModel::refreshDiscoverFeed,
                onOpenSettings = onOpenSettings,
                onAddFolder = onAddFolder,
                onImportFiles = onImportFiles,
                onSaveMixAsPlaylist = viewModel::saveSmartPlaylist,
                smartPlaylistFlow = viewModel.smartPlaylistState,
                currentTrackTitle = nowPlaying.currentTrack?.title.orEmpty(),
                onGenerateSmartPlaylist = onGenerateSmartPlaylist
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

/**
 * Truly floating bottom overlay combining a lightweight zero-blur vertical gradient scrim
 * with the floating mini-player pill and expanding floating navigation dock.
 * Parent containers never clip so pill edges remain razor-sharp with zero corner artifacts.
 */
@Composable
internal fun FloatingBottomDockOverlay(
    activeTab: CadenceTab,
    currentTrack: Track?,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onSelectTab: (CadenceTab) -> Unit,
    onExpandMiniPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val performanceMode = LocalPerformanceMode.current
    val bg = MaterialTheme.colorScheme.background
    val scrimBrush = remember(bg) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                bg.copy(alpha = 0.92f),
                bg
            )
        )
    }
    val showMiniPlayer = activeTab != CadenceTab.NOW_PLAYING && currentTrack != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(scrimBrush)
            .navigationBarsPadding()
            .padding(top = 28.dp, bottom = 10.dp)
            .testTag("floating_bottom_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (performanceMode) {
                if (activeTab != CadenceTab.NOW_PLAYING && currentTrack != null) {
                    FloatingMiniPlayerPill(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        artworkShape = artworkShape,
                        onExpand = onExpandMiniPlayer,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext
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
                            isPlaying = isPlaying,
                            artworkShape = artworkShape,
                            onExpand = onExpandMiniPlayer,
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = onNext
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

@Composable
internal fun FloatingMiniPlayerPill(
    track: Track,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val pillBg = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.96f)
    val pillBorder = MaterialTheme.colorScheme.outlineVariant.opaqueOver(bg, 0.85f)
    Surface(
        onClick = onExpand,
        shape = CircleShape,
        color = pillBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, pillBorder),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
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
                shapeOverride = FastListThumbnailShape,
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

/**
 * Google Photos-inspired expanding floating bottom navigation dock:
 * - Only the active tab expands into a highlighted pill displaying both icon & text label.
 * - Inactive tabs collapse into compact 50dp circular icon buttons with hidden text labels.
 * - Uses CircleShape, 0dp shadowElevation, and a crisp 1dp pre-blended outlineVariant border
 *   so corners render razor-sharp with zero clipped shadow artifacts.
 */
@Composable
internal fun FloatingExpressiveNavDock(
    activeTab: CadenceTab,
    onSelectTab: (CadenceTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val performanceMode = LocalPerformanceMode.current
    val bg = MaterialTheme.colorScheme.background
    val dockBg = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.96f)
    val dockBorder = MaterialTheme.colorScheme.outlineVariant.opaqueOver(bg, 0.85f)
    val inactiveCircleBg = MaterialTheme.colorScheme.surfaceContainer.opaqueOver(
        dockBg,
        0.82f
    )
    val dockAnimModifier = if (performanceMode) {
        Modifier
    } else {
        Modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Surface(
        shape = CircleShape,
        color = dockBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, dockBorder),
        modifier = modifier
            .wrapContentWidth()
            .then(dockAnimModifier)
            .testTag("floating_nav_dock")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CadenceTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val onPickTab = remember(tab, onSelectTab) { { onSelectTab(tab) } }
                val tabLabel = stringResource(tab.labelRes)
                val pillAnimModifier = if (performanceMode) {
                    Modifier
                } else {
                    Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }

                Surface(
                    onClick = onPickTab,
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        inactiveCircleBg
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .height(50.dp)
                        .widthIn(min = 50.dp)
                        .then(pillAnimModifier)
                        .testTag("nav_${tab.route}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (selected) 18.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tabLabel,
                            modifier = Modifier.size(22.dp)
                        )
                        if (performanceMode) {
                            if (selected) {
                                Text(
                                    text = tabLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .testTag("nav_label_${tab.route}")
                                )
                            }
                        } else {
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(tween(180)) + expandHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    expandFrom = Alignment.Start
                                ),
                                exit = fadeOut(tween(120)) + shrinkHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    shrinkTowards = Alignment.Start
                                )
                            ) {
                                Text(
                                    text = tabLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .testTag("nav_label_${tab.route}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CadenceSnackbarObserver(
    snackbarFlow: StateFlow<String?>,
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

/**
 * Isolated scanning gate so progress ticks never recompose [CadenceApp] or the [Scaffold].
 * While a folder/device scan is active, detaches the live Library/NowPlaying tree completely
 * and renders [FullScreenScanningView] until the Room batch inserts finish cleanly.
 */
@Composable
private fun CadenceScanGate(
    scanStateFlow: StateFlow<ScanState>,
    artworkShape: ExpressiveArtworkShape,
    content: @Composable () -> Unit
) {
    val isScanningFlow = remember(scanStateFlow) {
        scanStateFlow.map { it.isScanning }.distinctUntilChanged()
    }
    val isScanning by isScanningFlow.collectAsStateWithLifecycle(initialValue = scanStateFlow.value.isScanning)
    val performanceMode = LocalPerformanceMode.current

    if (performanceMode) {
        if (isScanning) {
            FullScreenScanningHost(
                scanStateFlow = scanStateFlow,
                artworkShape = artworkShape
            )
        } else {
            content()
        }
    } else {
        AnimatedContent(
            targetState = isScanning,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cadence_scan_gate"
        ) { scanning ->
            if (scanning) {
                FullScreenScanningHost(
                    scanStateFlow = scanStateFlow,
                    artworkShape = artworkShape
                )
            } else {
                content()
            }
        }
    }
}

@Composable
private fun FullScreenScanningHost(
    scanStateFlow: StateFlow<ScanState>,
    artworkShape: ExpressiveArtworkShape
) {
    val scanState by scanStateFlow.collectAsStateWithLifecycle()
    FullScreenScanningView(
        scanState = scanState,
        artworkShape = artworkShape
    )
}

@Composable
internal fun FullScreenScanningView(
    scanState: ScanState,
    artworkShape: ExpressiveArtworkShape,
    modifier: Modifier = Modifier
) {
    val performanceMode = LocalPerformanceMode.current
    // Absorb back presses gracefully while indexing batch writes so the scan finishes intact
    BackHandler(enabled = true) {}

    val pulseScale = if (performanceMode) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "scan_emblem_pulse")
        transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = AnimRepeatMode.Reverse
            ),
            label = "scan_scale"
        ).value
    }

    val counterText = when {
        scanState.totalCount > 0 ->
            "${scanState.scannedCount} / ${scanState.totalCount} files · ${scanState.addedCount} added"
        scanState.scannedCount > 0 ->
            "${scanState.scannedCount} scanned · ${scanState.addedCount} added"
        else -> stringResource(R.string.scanning_discovering)
    }

    val detailText = when {
        scanState.currentFileName.isNotBlank() -> scanState.currentFileName
        scanState.statusLabel.isNotBlank() -> scanState.statusLabel
        else -> stringResource(R.string.scanning_library_subtitle)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .testTag("full_screen_scanning_view"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = artworkShape.toComposeShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(116.dp)
                    .let { if (pulseScale != 1f) it.scale(pulseScale) else it }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (scanState.currentFolderName.isNotBlank()) {
                            Icons.Rounded.FolderOpen
                        } else {
                            Icons.Rounded.LibraryMusic
                        },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.scanning_library_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            if (scanState.currentFolderName.isNotBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("scanning_folder_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = scanState.currentFolderName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            val progress = scanState.progressFraction
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .testTag("scanning_progress_bar")
                )
            } else {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .testTag("scanning_progress_bar")
                )
            }

            Text(
                text = counterText,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("scanning_counter_text")
            )

            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scanning_detail_text")
            )
        }
    }
}
