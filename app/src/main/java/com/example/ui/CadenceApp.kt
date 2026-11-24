package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.LibraryCategory
import com.example.data.StableList
import com.example.data.Track
import com.example.data.library.ScanState
import com.example.playback.PlaybackProgress
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.FAST_THUMBNAIL_DECODE_PX
import com.example.ui.components.FastListThumbnailShape
import com.example.ui.components.ambientAuraBackdrop
import com.example.ui.icons.CadenceIcons
import com.example.ui.player.PlayerViewModel
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Keep
@Immutable
enum class CadenceTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    LIBRARY("library", R.string.nav_library, CadenceIcons.LibraryMusic, CadenceIcons.LibraryMusicOutlined),
    DISCOVER("discover", R.string.nav_discover, CadenceIcons.AutoAwesome, CadenceIcons.AutoAwesomeOutlined);

    companion object {
        fun fromRoute(route: String?): CadenceTab =
            entries.firstOrNull { it.route == route } ?: HOME
    }
}

@Keep
@Immutable
data class FloatingNavBarState(
    val activeTab: CadenceTab
)

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
    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlayingState.collectAsStateWithLifecycle()
    val hasDrillDown by viewModel.hasLibraryDrillDown.collectAsStateWithLifecycle()

    var activeTab by rememberSaveable(stateSaver = CadenceTabSaver) { mutableStateOf(CadenceTab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    var librarySearchFocusNonce by rememberSaveable { mutableIntStateOf(0) }
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
    val onOpenSettings = remember {
        {
            isPlayerExpanded = false
            showSettings = true
        }
    }
    val onCloseSettings = remember { { showSettings = false } }
    val onExpandMiniPlayer = remember { { isPlayerExpanded = true } }
    val onCollapseNowPlaying = remember { { isPlayerExpanded = false } }
    val onSelectTab = remember {
        { tab: CadenceTab ->
            isPlayerExpanded = false
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
    val onNextTrack = remember(viewModel) { { viewModel.nextTrack() } }
    val onPreviousTrack = remember(viewModel) { { viewModel.previousTrack() } }
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
        }
    }
    val onPlayTracksFromLibrary = remember(viewModel, ensureNotificationPermissionOnce) {
        { list: List<Track>, shuffle: Boolean ->
            ensureNotificationPermissionOnce()
            viewModel.playTracks(list, startIndex = 0, shuffle = shuffle)
        }
    }
    val onPlayPlaylistFromLibrary = remember(viewModel, ensureNotificationPermissionOnce) {
        { plId: Long, shuffle: Boolean ->
            ensureNotificationPermissionOnce()
            viewModel.playPlaylist(plId, shuffle)
        }
    }
    val onGenerateSmartPlaylist = remember(viewModel) {
        { mood: String?, seed: Boolean ->
            viewModel.generateSmartPlaylist(targetMood = mood, seedFromCurrent = seed)
        }
    }
    val onGenerateTunedSmartPlaylist = remember(viewModel) {
        { mood: String?, seed: Boolean, energy: String?, balance: Float ->
            viewModel.generateSmartPlaylist(
                targetMood = mood,
                seedFromCurrent = seed,
                energyFilter = energy,
                discoveryBalance = balance
            )
        }
    }
    val onOpenLibrarySearch = remember(viewModel) {
        {
            viewModel.closeDrillDown()
            isPlayerExpanded = false
            showSettings = false
            activeTab = CadenceTab.LIBRARY
            librarySearchFocusNonce += 1
        }
    }
    val onOpenLibraryShortcut = remember(viewModel) {
        { category: LibraryCategory ->
            viewModel.setLibraryCategory(category)
            isPlayerExpanded = false
            showSettings = false
            activeTab = CadenceTab.LIBRARY
        }
    }

    // Back collapses the expanded Now Playing sheet first, returning to the exact tab & scroll position
    BackHandler(enabled = isPlayerExpanded) {
        isPlayerExpanded = false
    }

    BackHandler(enabled = !isPlayerExpanded && !showSettings && activeTab != CadenceTab.HOME && !(activeTab == CadenceTab.LIBRARY && hasDrillDown)) {
        activeTab = CadenceTab.HOME
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
                val showOnboarding = nowPlaying.isInitialLibraryLoaded &&
                    !userProfile.hasCompletedOnboarding &&
                    nowPlaying.currentTrack == null &&
                    nowPlaying.queueTracks.isEmpty()

                if (showOnboarding) {
                    OnboardingScreen(
                        initialName = userProfile.displayName,
                        artworkShape = themePrefs.artworkShape,
                        onContinueAndScanFolder = { enteredName ->
                            viewModel.completeOnboarding(enteredName)
                            onAddFolder()
                        },
                        onContinueAndScanDevice = { enteredName ->
                            viewModel.completeOnboarding(enteredName)
                            triggerDeviceScan()
                        },
                        onSkip = { enteredName ->
                            viewModel.completeOnboarding(enteredName)
                        }
                    )
                    return@CadenceScanGate
                }

                val currentTrack = nowPlaying.currentTrack
                val showFloatingDock = !useNavigationRail && !showSettings
                val showMiniPlayer = showFloatingDock && currentTrack != null

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

                        PersistentTabHost(
                            activeTab = activeTab,
                            showSettings = showSettings,
                            modifier = contentInsetsModifier,
                            settingsContent = {
                                SettingsTabHost(
                                    viewModel = viewModel,
                                    themePrefs = themePrefs,
                                    userDisplayName = userProfile.displayName,
                                    currentTrack = currentTrack,
                                    isPlaying = nowPlaying.isPlaying,
                                    onCloseSettings = onCloseSettings,
                                    onAddFolder = onAddFolder
                                )
                            }
                        ) { tab, isTabActive ->
                            when (tab) {
                                CadenceTab.HOME -> {
                                    HomeTabHost(
                                        viewModel = viewModel,
                                        currentTrack = currentTrack,
                                        isPlaying = nowPlaying.isPlaying,
                                        artworkShape = themePrefs.artworkShape,
                                        userDisplayName = userProfile.displayName,
                                        isActiveTab = isTabActive,
                                        onExpandNowPlaying = onExpandMiniPlayer,
                                        onTogglePlayPause = onTogglePlayPause,
                                        onSelectTrack = onSelectTrackFromLibrary,
                                        onPlayTracks = onPlayTracksFromLibrary,
                                        onOpenSettings = onOpenSettings,
                                        onAddFolder = onAddFolder,
                                        onScanDevice = triggerDeviceScan,
                                        onOpenLibrarySearch = onOpenLibrarySearch,
                                        onOpenLibraryShortcut = onOpenLibraryShortcut
                                    )
                                }

                                CadenceTab.LIBRARY -> {
                                    LibraryTabHost(
                                        viewModel = viewModel,
                                        currentTrackId = currentTrack?.id,
                                        isPlaying = nowPlaying.isPlaying,
                                        artworkShape = themePrefs.artworkShape,
                                        searchFocusNonce = librarySearchFocusNonce,
                                        isActiveTab = isTabActive,
                                        onSelectTrack = onSelectTrackFromLibrary,
                                        onPlayTracks = onPlayTracksFromLibrary,
                                        onImportFiles = onImportFiles,
                                        onAddFolder = onAddFolder,
                                        onScanDevice = triggerDeviceScan,
                                        onPlayPlaylist = onPlayPlaylistFromLibrary,
                                        onOpenSettings = onOpenSettings
                                    )
                                }

                                CadenceTab.DISCOVER -> {
                                    DiscoverTabHost(
                                        viewModel = viewModel,
                                        currentTrackId = currentTrack?.id,
                                        currentTrackTitle = currentTrack?.title.orEmpty(),
                                        isPlaying = nowPlaying.isPlaying,
                                        artworkShape = themePrefs.artworkShape,
                                        onSelectTrackFromSection = onSelectTrackFromLibrary,
                                        onPlayTracksFromLibrary = onPlayTracksFromLibrary,
                                        onOpenSettings = onOpenSettings,
                                        onAddFolder = onAddFolder,
                                        onImportFiles = onImportFiles,
                                        onGenerateSmartPlaylist = onGenerateSmartPlaylist,
                                        onGenerateTunedSmartPlaylist = onGenerateTunedSmartPlaylist
                                    )
                                }
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
                            onNext = onNextTrack,
                            onPrevious = onPreviousTrack,
                            progressFlow = viewModel.progressState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    } else if (useNavigationRail && !showSettings && currentTrack != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 24.dp, bottom = 16.dp)
                        ) {
                            FloatingMiniPlayerPill(
                                track = currentTrack,
                                isPlaying = nowPlaying.isPlaying,
                                artworkShape = themePrefs.artworkShape,
                                onExpand = onExpandMiniPlayer,
                                onTogglePlayPause = onTogglePlayPause,
                                onNext = onNextTrack,
                                onPrevious = onPreviousTrack,
                                progressFlow = viewModel.progressState
                            )
                        }
                    }

                    // Expandable full-screen Now Playing sheet: composed ONLY while expanded/animating
                    ExpandableNowPlayingOverlay(
                        isExpanded = isPlayerExpanded,
                        onCollapse = onCollapseNowPlaying,
                        ambientAuraEnabled = themePrefs.effectiveAmbientAura,
                        isPlaying = nowPlaying.isPlaying
                    ) { dismissDragModifier ->
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
                            onSelectPlaybackSpeed = viewModel::setPlaybackSpeed,
                            onMatchMood = viewModel::matchCurrentTrackMood,
                            onCollapse = onCollapseNowPlaying,
                            onOpenSettings = onOpenSettings,
                            onAddFolder = onAddFolder,
                            onScanDevice = triggerDeviceScan,
                            onSelectQueueIndex = viewModel::selectQueueIndex,
                            onMoveQueueItem = viewModel::moveQueueItem,
                            onRemoveQueueItem = viewModel::removeQueueItem,
                            onClearQueue = viewModel::clearQueue,
                            onSaveQueueAsPlaylist = viewModel::saveQueueAsPlaylist,
                            onAddCurrentToPlaylist = onAddCurrentToPlaylist,
                            onCreatePlaylistWithCurrent = onCreatePlaylistWithCurrent,
                            onPlayNextCurrent = onPlayNextCurrent,
                            onAddCurrentToQueue = onAddCurrentToQueue,
                            onSelectSleepTimer = viewModel::setSleepTimer,
                            lyricsStateFlow = viewModel.lyricsState,
                            onRequestLyrics = viewModel::loadLyricsForTrack,
                            onSeekToMs = viewModel::seekToMs,
                            dismissDragModifier = dismissDragModifier
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(
                                bottom = when {
                                    isPlayerExpanded -> 24.dp
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

/**
 * GPU-translated expandable full-screen Now Playing sheet overlay.
 * - Composes [content] strictly while expanded or animating (`isExpanded || progress > 0f`)
 *   so minimized state consumes zero background memory on low-RAM devices.
 * - Exposes [dismissDragModifier] so callers attach swipe-down strictly to the top header and
 *   artwork area—never fighting horizontal seek-bar scrubbing.
 */
@Composable
internal fun ExpandableNowPlayingOverlay(
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    ambientAuraEnabled: Boolean,
    isPlaying: Boolean,
    content: @Composable (dismissDragModifier: Modifier) -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val coroutineScope = rememberCoroutineScope()
    val expansionAnimatable = remember { Animatable(if (isExpanded) 1f else 0f) }
    val dragOffsetAnimatable = remember { Animatable(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 108.dp.toPx() }

    LaunchedEffect(isExpanded, performanceMode) {
        if (isExpanded) {
            dragOffsetAnimatable.snapTo(0f)
            if (performanceMode) {
                expansionAnimatable.snapTo(1f)
            } else {
                expansionAnimatable.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        } else {
            if (containerHeightPx > 0f && dragOffsetAnimatable.value > 0f) {
                val effectiveProgress =
                    (expansionAnimatable.value - (dragOffsetAnimatable.value / containerHeightPx)).coerceIn(0f, 1f)
                expansionAnimatable.snapTo(effectiveProgress)
                dragOffsetAnimatable.snapTo(0f)
            }
            if (performanceMode) {
                expansionAnimatable.snapTo(0f)
            } else {
                expansionAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val shouldCompose = isExpanded || expansionAnimatable.value > 0.001f
    if (!shouldCompose) return

    val dismissDragModifier = remember(onCollapse, dismissThresholdPx) {
        Modifier.pointerInput(onCollapse, dismissThresholdPx) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    val next = (dragOffsetAnimatable.value + dragAmount).coerceAtLeast(0f)
                    if (next > 0f || dragAmount > 0f) {
                        change.consume()
                        coroutineScope.launch {
                            dragOffsetAnimatable.snapTo(next)
                        }
                    }
                },
                onDragEnd = {
                    val threshold = if (containerHeightPx > 0f) {
                        minOf(dismissThresholdPx, containerHeightPx * 0.18f)
                    } else {
                        dismissThresholdPx
                    }
                    if (dragOffsetAnimatable.value >= threshold) {
                        onCollapse()
                    } else {
                        coroutineScope.launch {
                            dragOffsetAnimatable.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                },
                onDragCancel = {
                    coroutineScope.launch {
                        dragOffsetAnimatable.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size -> containerHeightPx = size.height.toFloat() }
            .graphicsLayer {
                val h = if (containerHeightPx > 0f) containerHeightPx else size.height
                val p = expansionAnimatable.value.coerceIn(0f, 1f)
                translationY = ((1f - p) * h) + dragOffsetAnimatable.value
                alpha = (p * 1.45f).coerceIn(0f, 1f)
            }
            .background(MaterialTheme.colorScheme.background)
            .ambientAuraBackdrop(
                enabled = ambientAuraEnabled,
                isPlaying = isPlaying
            )
            .pointerInput(Unit) { /* Consume stray taps so tabs behind stay untouched */ }
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .testTag("now_playing_sheet_overlay")
    ) {
        content(dismissDragModifier)
    }
}

/**
 * Instant zero-rebuild tab host backed by [rememberSaveableStateHolder].
 * - Lazily mounts each [CadenceTab] on first visit and keeps all visited tabs warm in memory
 *   so switching between Home, Library, and Discover never tears down or re-measures LazyLists.
 * - Toggles visibility purely in the GPU layer ([zIndex] + [graphicsLayer] alpha) and disables
 *   pointer hit-testing & accessibility semantics on inactive tabs.
 */
@Composable
internal fun PersistentTabHost(
    activeTab: CadenceTab,
    showSettings: Boolean,
    modifier: Modifier = Modifier,
    settingsContent: @Composable () -> Unit = {},
    tabContent: @Composable (tab: CadenceTab, isActive: Boolean) -> Unit
) {
    val stateHolder = rememberSaveableStateHolder()
    val visitedTabs = remember { mutableStateMapOf<CadenceTab, Boolean>() }
    if (visitedTabs[activeTab] != true) {
        visitedTabs[activeTab] = true
    }

    Box(
        modifier = modifier.testTag("persistent_tab_host")
    ) {
        CadenceTab.entries.forEach { tab ->
            if (visitedTabs[tab] == true) {
                key(tab.route) {
                    val isTabActive = !showSettings && activeTab == tab
                    val inactiveBlockModifier = if (isTabActive) {
                        Modifier
                    } else {
                        Modifier
                            .clearAndSetSemantics {}
                            .pointerInput(tab) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isTabActive) 1f else 0f)
                            .graphicsLayer {
                                alpha = if (isTabActive) 1f else 0f
                            }
                            .then(inactiveBlockModifier)
                    ) {
                        stateHolder.SaveableStateProvider(key = tab.route) {
                            tabContent(tab, isTabActive)
                        }
                    }
                }
            }
        }

        if (showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) { /* Consume stray taps so warm tabs behind stay untouched */ }
            ) {
                settingsContent()
            }
        }
    }
}

@Composable
private fun HomeTabHost(
    viewModel: PlayerViewModel,
    currentTrack: Track?,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    userDisplayName: String,
    isActiveTab: Boolean,
    onExpandNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectTrack: (Track, StableList<Track>) -> Unit,
    onPlayTracks: (List<Track>, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit,
    onOpenLibrarySearch: () -> Unit,
    onOpenLibraryShortcut: (LibraryCategory) -> Unit
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val onToggleFavorite = remember(viewModel) { viewModel::toggleFavorite }
    val onSeek = remember(viewModel) { viewModel::seekToFraction }
    HomeScreen(
        uiState = homeState,
        currentTrack = currentTrack,
        isPlaying = isPlaying,
        artworkShape = artworkShape,
        onExpandNowPlaying = onExpandNowPlaying,
        onTogglePlayPause = onTogglePlayPause,
        onSelectTrack = onSelectTrack,
        onPlayTracks = onPlayTracks,
        onToggleFavorite = onToggleFavorite,
        onOpenSettings = onOpenSettings,
        onAddFolder = onAddFolder,
        onScanDevice = onScanDevice,
        userDisplayName = userDisplayName,
        progressFlow = viewModel.progressState,
        onSeek = onSeek,
        onOpenSearch = onOpenLibrarySearch,
        onOpenLibraryShortcut = onOpenLibraryShortcut,
        isActiveTab = isActiveTab
    )
}

@Composable
private fun LibraryTabHost(
    viewModel: PlayerViewModel,
    currentTrackId: String?,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    searchFocusNonce: Int,
    isActiveTab: Boolean,
    onSelectTrack: (Track, StableList<Track>) -> Unit,
    onPlayTracks: (List<Track>, Boolean) -> Unit,
    onImportFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit,
    onPlayPlaylist: (Long, Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val onToggleFavorite = remember(viewModel) { viewModel::toggleFavorite }
    val onSearchChange = remember(viewModel) { viewModel::updateSearchQuery }
    val onCategoryChange = remember(viewModel) { viewModel::setLibraryCategory }
    val onSortChange = remember(viewModel) { viewModel::setSortOrder }
    val onOpenDrillDown = remember(viewModel) { viewModel::openDrillDown }
    val onCloseDrillDown = remember(viewModel) { viewModel::closeDrillDown }
    val onRescanFolders = remember(viewModel) { viewModel::rescanSavedFolders }
    val onPlayNext = remember(viewModel) { viewModel::playNext }
    val onAddToQueue = remember(viewModel) { viewModel::addToQueue }
    val onAddTrackToPlaylist = remember(viewModel) { viewModel::addTrackToPlaylist }
    val onRemoveTrackFromPlaylist = remember(viewModel) { viewModel::removeTrackFromPlaylist }
    val onCreatePlaylist = remember(viewModel) { viewModel::createPlaylist }
    val onRenamePlaylist = remember(viewModel) { viewModel::renamePlaylist }
    val onDeletePlaylist = remember(viewModel) { viewModel::deletePlaylist }
    val onRemoveTrack = remember(viewModel) { viewModel::removeTrack }

    LibraryScreen(
        uiState = libraryState,
        currentTrackId = currentTrackId,
        isPlaying = isPlaying,
        artworkShape = artworkShape,
        onSelectTrack = onSelectTrack,
        onPlayTracks = onPlayTracks,
        onToggleFavorite = onToggleFavorite,
        onSearchChange = onSearchChange,
        onCategoryChange = onCategoryChange,
        onSortChange = onSortChange,
        onOpenDrillDown = onOpenDrillDown,
        onCloseDrillDown = onCloseDrillDown,
        onImportFiles = onImportFiles,
        onAddFolder = onAddFolder,
        onScanDevice = onScanDevice,
        onRescanFolders = onRescanFolders,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onAddTrackToPlaylist = onAddTrackToPlaylist,
        onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylist,
        onCreatePlaylist = onCreatePlaylist,
        onRenamePlaylist = onRenamePlaylist,
        onDeletePlaylist = onDeletePlaylist,
        onPlayPlaylist = onPlayPlaylist,
        onRemoveTrack = onRemoveTrack,
        onOpenSettings = onOpenSettings,
        scanStateFlow = viewModel.scanState,
        searchFocusNonce = searchFocusNonce,
        isActiveTab = isActiveTab
    )
}

@Composable
private fun DiscoverTabHost(
    viewModel: PlayerViewModel,
    currentTrackId: String?,
    currentTrackTitle: String,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onSelectTrackFromSection: (Track, StableList<Track>) -> Unit,
    onPlayTracksFromLibrary: (List<Track>, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onGenerateSmartPlaylist: (String?, Boolean) -> Unit,
    onGenerateTunedSmartPlaylist: (String?, Boolean, String?, Float) -> Unit
) {
    val discoverState by viewModel.discoverState.collectAsStateWithLifecycle()
    val onPlaySection = remember(onPlayTracksFromLibrary) {
        { tracks: StableList<Track> -> onPlayTracksFromLibrary(tracks, false) }
    }
    val onRefreshDiscover = remember(viewModel) { viewModel::refreshDiscoverFeed }
    val onSaveMixAsPlaylist = remember(viewModel) { viewModel::saveSmartPlaylist }
    val onUpdateTuneConfig = remember(viewModel) { viewModel::updateDiscoverTuneConfig }
    val onResetTuneConfig = remember(viewModel) { viewModel::resetDiscoverTuneConfig }

    DiscoverScreen(
        uiState = discoverState,
        currentTrackId = currentTrackId,
        isPlaying = isPlaying,
        artworkShape = artworkShape,
        onSelectTrackFromSection = onSelectTrackFromSection,
        onPlaySection = onPlaySection,
        onRefreshDiscover = onRefreshDiscover,
        onOpenSettings = onOpenSettings,
        onAddFolder = onAddFolder,
        onImportFiles = onImportFiles,
        onSaveMixAsPlaylist = onSaveMixAsPlaylist,
        smartPlaylistFlow = viewModel.smartPlaylistState,
        currentTrackTitle = currentTrackTitle,
        onGenerateSmartPlaylist = onGenerateSmartPlaylist,
        onUpdateTuneConfig = onUpdateTuneConfig,
        onResetTuneConfig = onResetTuneConfig,
        onGenerateTunedSmartPlaylist = onGenerateTunedSmartPlaylist
    )
}

@Composable
private fun SettingsTabHost(
    viewModel: PlayerViewModel,
    themePrefs: com.example.data.ThemePreferences,
    userDisplayName: String,
    currentTrack: Track?,
    isPlaying: Boolean,
    onCloseSettings: () -> Unit,
    onAddFolder: () -> Unit
) {
    val audioSettings by viewModel.audioSettingsState.collectAsStateWithLifecycle()
    val savedFolders by viewModel.savedFoldersState.collectAsStateWithLifecycle()
    val minDurationSec by viewModel.minTrackDurationSecState.collectAsStateWithLifecycle()
    SettingsScreen(
        themePrefs = themePrefs,
        audioSettings = audioSettings,
        savedFolders = savedFolders,
        minTrackDurationSec = minDurationSec,
        currentTrack = currentTrack,
        isPlaying = isPlaying,
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
        onSelectAudioFocusBehavior = viewModel::setAudioFocusBehavior,
        onToggleAcousticEnhance = viewModel::setAcousticEnhanceEnabled,
        userDisplayName = userDisplayName,
        onUpdateDisplayName = viewModel::setUserDisplayName
    )
}

/**
 * Truly floating bottom overlay combining a lightweight zero-blur vertical gradient scrim
 * with the floating mini-player pill (consistently visible across Home, Library, and Discover
 * whenever a track is loaded) and the expanding floating navigation dock.
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
    onPrevious: () -> Unit = {},
    progressFlow: StateFlow<PlaybackProgress>? = null,
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
    val navState = remember(activeTab) { FloatingNavBarState(activeTab) }
    val showMiniPlayer = currentTrack != null

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
                if (currentTrack != null) {
                    FloatingMiniPlayerPill(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        artworkShape = artworkShape,
                        onExpand = onExpandMiniPlayer,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        progressFlow = progressFlow
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
                            onNext = onNext,
                            onPrevious = onPrevious,
                            progressFlow = progressFlow
                        )
                    }
                }
            }

            FloatingExpressiveNavDock(
                navState = navState,
                onSelectTab = onSelectTab
            )
        }
    }
}

private enum class MiniPlayerDragAxis {
    NONE,
    HORIZONTAL,
    VERTICAL
}

@Composable
internal fun FloatingMiniPlayerPill(
    track: Track,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    progressFlow: StateFlow<PlaybackProgress>? = null
) {
    val performanceMode = LocalPerformanceMode.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val horizontalDragOffset = remember { Animatable(0f) }

    val density = LocalDensity.current
    val horizontalSkipThresholdPx = with(density) { 52.dp.toPx() }
    val maxDragFeedbackPx = with(density) { 64.dp.toPx() }
    val verticalExpandThresholdPx = with(density) { 36.dp.toPx() }

    val latestOnExpand by rememberUpdatedState(onExpand)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)
    val latestPerfMode by rememberUpdatedState(performanceMode)

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
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dedicated gesture target covering artwork & track metadata so 48dp buttons remain unobstructed
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            translationX = horizontalDragOffset.value
                        }
                        .pointerInput(
                            horizontalSkipThresholdPx,
                            maxDragFeedbackPx,
                            verticalExpandThresholdPx
                        ) {
                            var axis = MiniPlayerDragAxis.NONE
                            var totalX = 0f
                            var totalY = 0f
                            var hapticFired = false

                            detectDragGestures(
                                onDragStart = {
                                    axis = MiniPlayerDragAxis.NONE
                                    totalX = 0f
                                    totalY = 0f
                                    hapticFired = false
                                },
                                onDrag = { change, dragAmount ->
                                    totalX += dragAmount.x
                                    totalY += dragAmount.y
                                    if (axis == MiniPlayerDragAxis.NONE && (abs(totalX) > 6f || abs(totalY) > 6f)) {
                                        axis = if (abs(totalX) >= abs(totalY)) {
                                            MiniPlayerDragAxis.HORIZONTAL
                                        } else {
                                            MiniPlayerDragAxis.VERTICAL
                                        }
                                    }

                                    when (axis) {
                                        MiniPlayerDragAxis.HORIZONTAL -> {
                                            change.consume()
                                            val visualOffset = (totalX * 0.65f)
                                                .coerceIn(-maxDragFeedbackPx, maxDragFeedbackPx)
                                            coroutineScope.launch {
                                                horizontalDragOffset.snapTo(visualOffset)
                                            }
                                            if (!hapticFired && abs(totalX) >= horizontalSkipThresholdPx) {
                                                hapticFired = true
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }

                                        MiniPlayerDragAxis.VERTICAL -> {
                                            if (totalY < 0f) {
                                                change.consume()
                                                if (!hapticFired && -totalY >= verticalExpandThresholdPx) {
                                                    hapticFired = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }

                                        MiniPlayerDragAxis.NONE -> Unit
                                    }
                                },
                                onDragEnd = {
                                    when (axis) {
                                        MiniPlayerDragAxis.HORIZONTAL -> {
                                            if (totalX <= -horizontalSkipThresholdPx) {
                                                latestOnNext()
                                            } else if (totalX >= horizontalSkipThresholdPx) {
                                                latestOnPrevious()
                                            }
                                            coroutineScope.launch {
                                                if (latestPerfMode) {
                                                    horizontalDragOffset.snapTo(0f)
                                                } else {
                                                    horizontalDragOffset.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        MiniPlayerDragAxis.VERTICAL -> {
                                            if (-totalY >= verticalExpandThresholdPx) {
                                                latestOnExpand()
                                            }
                                        }

                                        MiniPlayerDragAxis.NONE -> Unit
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        if (latestPerfMode) {
                                            horizontalDragOffset.snapTo(0f)
                                        } else {
                                            horizontalDragOffset.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        .testTag("mini_player_gesture_body"),
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
                        showHairlineBorder = true,
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
                        imageVector = if (isPlaying) CadenceIcons.Pause else Icons.Rounded.PlayArrow,
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
                        imageVector = CadenceIcons.SkipNext,
                        contentDescription = stringResource(R.string.action_next),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (progressFlow != null) {
                MiniPlayerProgressIndicator(
                    progressFlow = progressFlow,
                    fallbackDurationMs = track.durationMs,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }
        }
    }
}

/**
 * Isolated draw-phase 2.dp hairline progress bar flush along the bottom edge of [FloatingMiniPlayerPill].
 * Collects [progressFlow] strictly inside this tiny leaf and reads state inside Canvas draw phase,
 * guaranteeing 0 recompositions of [FloatingMiniPlayerPill], [FloatingBottomDockOverlay], or [FloatingExpressiveNavDock].
 */
@Composable
private fun MiniPlayerProgressIndicator(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val progressState = progressFlow.collectAsStateWithLifecycle()
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Canvas(modifier = modifier.testTag("mini_player_progress_bar")) {
        val progress = progressState.value
        val dur = progress.durationMs.takeIf { it > 0L } ?: fallbackDurationMs.coerceAtLeast(1L)
        val fraction = (progress.positionMs.coerceIn(0L, dur).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        val y = size.height / 2f
        drawLine(
            color = trackColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height,
            cap = StrokeCap.Butt
        )
        val fillEnd = size.width * fraction
        if (fillEnd > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, y),
                end = Offset(fillEnd, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
        }
    }
}

private val NavPillExpandSpringSpec = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private val NavPillColorSpringSpec = spring<Color>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * Google Photos-inspired expanding floating bottom navigation dock:
 * - Isolated with [@Immutable] [FloatingNavBarState] and stable tab callbacks so heavy screen
 *   recompositions never stall tab transitions.
 * - Uses a single unified spring spec inside [AnimatedVisibility] (zero competing nested
 *   `animateContentSize` modifiers) and pre-blended [Color.opaqueOver] colors with
 *   [animateColorAsState] and 0dp shadow elevation for stutter-free 60fps pill transitions.
 */
@Composable
internal fun FloatingExpressiveNavDock(
    activeTab: CadenceTab,
    onSelectTab: (CadenceTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val navState = remember(activeTab) { FloatingNavBarState(activeTab) }
    FloatingExpressiveNavDock(
        navState = navState,
        onSelectTab = onSelectTab,
        modifier = modifier
    )
}

@Composable
internal fun FloatingExpressiveNavDock(
    navState: FloatingNavBarState,
    onSelectTab: (CadenceTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = MaterialTheme.colorScheme.background
    val dockBg = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.96f)
    val dockBorder = MaterialTheme.colorScheme.outlineVariant.opaqueOver(bg, 0.85f)
    val activePillBg = MaterialTheme.colorScheme.primaryContainer.opaqueOver(dockBg, 1f)
    val inactivePillBg = MaterialTheme.colorScheme.surfaceContainer.opaqueOver(dockBg, 0.82f)
    val activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = CircleShape,
        color = dockBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, dockBorder),
        modifier = modifier
            .wrapContentWidth()
            .testTag("floating_nav_dock")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CadenceTab.entries.forEach { tab ->
                val selected = tab == navState.activeTab
                val onPickTab = remember(tab, onSelectTab) { { onSelectTab(tab) } }
                FloatingNavTabPill(
                    tab = tab,
                    selected = selected,
                    activePillBg = activePillBg,
                    inactivePillBg = inactivePillBg,
                    activeContentColor = activeContentColor,
                    inactiveContentColor = inactiveContentColor,
                    onClick = onPickTab
                )
            }
        }
    }
}

@Composable
private fun FloatingNavTabPill(
    tab: CadenceTab,
    selected: Boolean,
    activePillBg: Color,
    inactivePillBg: Color,
    activeContentColor: Color,
    inactiveContentColor: Color,
    onClick: () -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val tabLabel = stringResource(tab.labelRes)

    val containerColor = if (performanceMode) {
        if (selected) activePillBg else inactivePillBg
    } else {
        animateColorAsState(
            targetValue = if (selected) activePillBg else inactivePillBg,
            animationSpec = NavPillColorSpringSpec,
            label = "nav_pill_bg_${tab.route}"
        ).value
    }

    val contentColor = if (performanceMode) {
        if (selected) activeContentColor else inactiveContentColor
    } else {
        animateColorAsState(
            targetValue = if (selected) activeContentColor else inactiveContentColor,
            animationSpec = NavPillColorSpringSpec,
            label = "nav_pill_fg_${tab.route}"
        ).value
    }

    Row(
        modifier = Modifier
            .height(50.dp)
            .widthIn(min = 50.dp)
            .clip(CircleShape)
            .background(color = containerColor, shape = CircleShape)
            .clickable(
                role = Role.Tab,
                onClickLabel = tabLabel,
                onClick = onClick
            )
            .padding(horizontal = 14.dp)
            .testTag("nav_${tab.route}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tabLabel,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        if (performanceMode) {
            if (selected) {
                Text(
                    text = tabLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 4.dp)
                        .testTag("nav_label_${tab.route}")
                )
            }
        } else {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                ) + expandHorizontally(
                    animationSpec = NavPillExpandSpringSpec,
                    expandFrom = Alignment.Start
                ),
                exit = fadeOut(
                    animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
                ) + shrinkHorizontally(
                    animationSpec = NavPillExpandSpringSpec,
                    shrinkTowards = Alignment.Start
                )
            ) {
                Text(
                    text = tabLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 4.dp)
                        .testTag("nav_label_${tab.route}")
                )
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
                            CadenceIcons.FolderOpen
                        } else {
                            CadenceIcons.LibraryMusic
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
                            imageVector = CadenceIcons.FolderOpen,
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
