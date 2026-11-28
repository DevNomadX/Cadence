package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LibraryCategory
import com.example.data.LibrarySortOrder
import com.example.data.PlaylistSummary
import com.example.data.StableList
import com.example.data.Track
import com.example.data.TrackGroup
import com.example.data.formatDuration
import com.example.data.intelligence.LibraryMoodFilter
import com.example.data.library.LibrarySearchEngine
import com.example.data.library.ScanState
import com.example.data.toStableList
import com.example.ui.components.AddToPlaylistSheetContent
import com.example.ui.components.AudioPulseBars
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.FAST_THUMBNAIL_DECODE_PX
import com.example.ui.components.FastListThumbnailShape
import com.example.ui.components.ImportSourcesSheetContent
import com.example.ui.components.PlaylistNameDialog
import com.example.ui.components.TrackActionsSheetContent
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.icons.CadenceIcons
import com.example.ui.player.LibraryDrillDown
import com.example.ui.player.LibraryUiState
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

private val IdleScanStateFlow: StateFlow<ScanState> = MutableStateFlow(ScanState())

// Cached shapes for zero-allocation scrolling on low-end GPUs
private val TrackRowShape = RoundedCornerShape(20.dp)
private val GroupRowShape = RoundedCornerShape(22.dp)
private val EmptyCardShape = RoundedCornerShape(28.dp)
private val ScanBannerShape = RoundedCornerShape(20.dp)
private val ModalSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
private val CompactHiFiShape = RoundedCornerShape(5.dp)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onSelectTrack: (Track, StableList<Track>) -> Unit,
    onPlayTracks: (List<Track>, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (LibraryCategory) -> Unit,
    onSortChange: (LibrarySortOrder) -> Unit,
    onOpenDrillDown: (LibraryDrillDown) -> Unit,
    onCloseDrillDown: () -> Unit,
    onImportFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit,
    onRescanFolders: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddTrackToPlaylist: (Long, Track) -> Unit,
    onRemoveTrackFromPlaylist: (Long, String) -> Unit,
    onCreatePlaylist: (String, String?) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlayPlaylist: (Long, Boolean) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onOpenSettings: () -> Unit = {},
    scanStateFlow: StateFlow<ScanState> = IdleScanStateFlow,
    searchFocusNonce: Int = 0,
    isActiveTab: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    var showImportSheet by remember { mutableStateOf(false) }
    var activeTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylistPicker by remember { mutableStateOf<Track?>(null) }
    var trackForDetails by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var pendingTrackForNewPlaylist by remember { mutableStateOf<String?>(null) }
    var playlistToRename by remember { mutableStateOf<PlaylistSummary?>(null) }

    // Immediate local text state so typing renders at 60 FPS with zero ViewModel round-trip lag
    var rawSearchQuery by rememberSaveable { mutableStateOf(uiState.searchQuery) }
    // String-route saved mood filter ID so R8 minification never breaks state restoration
    var selectedMoodFilterId by rememberSaveable { mutableStateOf(LibraryMoodFilter.ALL.id) }
    val selectedMoodFilter = remember(selectedMoodFilterId) {
        LibraryMoodFilter.fromId(selectedMoodFilterId)
    }
    val latestOnSearchChange by rememberUpdatedState(onSearchChange)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    val isMainHub = uiState.drillDown == null &&
        (uiState.category == LibraryCategory.OVERVIEW || uiState.category == LibraryCategory.SONGS)
    val isSubView = uiState.drillDown == null && !isMainHub

    LaunchedEffect(searchFocusNonce, isActiveTab) {
        if (isActiveTab && searchFocusNonce > 0 && uiState.drillDown == null) {
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    // Debounce search dispatches to ViewModel (150ms) so fast typing never floods filtering
    LaunchedEffect(Unit) {
        snapshotFlow { rawSearchQuery }
            .drop(1)
            .debounce(150L)
            .distinctUntilChanged()
            .collect { debounced ->
                latestOnSearchChange(debounced)
            }
    }

    // Reset results list cleanly to top whenever the active filtered query or view changes
    LaunchedEffect(uiState.searchQuery, uiState.category, uiState.drillDown) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
    }

    // Dismiss keyboard & focus as soon as the user starts scrolling results
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    focusManager.clearFocus(force = false)
                    keyboardController?.hide()
                }
            }
    }

    val onClearSearchImmediate = remember(focusManager, keyboardController) {
        {
            rawSearchQuery = ""
            latestOnSearchChange("")
            focusManager.clearFocus(force = false)
            keyboardController?.hide()
            Unit
        }
    }

    val onReturnToHub = remember(onCategoryChange, onClearSearchImmediate) {
        {
            if (rawSearchQuery.isNotEmpty()) {
                onClearSearchImmediate()
            }
            onCategoryChange(LibraryCategory.OVERVIEW)
        }
    }

    val onSubmitSearch = remember(focusManager, keyboardController) {
        {
            latestOnSearchChange(rawSearchQuery)
            focusManager.clearFocus(force = false)
            keyboardController?.hide()
            Unit
        }
    }

    BackHandler(enabled = isActiveTab && uiState.drillDown != null) {
        onCloseDrillDown()
    }
    BackHandler(enabled = isActiveTab && isSubView) {
        onReturnToHub()
    }

    val activePlaylistId = (uiState.drillDown as? LibraryDrillDown.Playlist)?.playlistId

    // Pre-compute opaque blended row & badge surfaces once per theme change (zero per-row alpha blending)
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val activeRowColor = remember(colorScheme.primaryContainer, bg) {
        colorScheme.primaryContainer.opaqueOver(bg, 0.80f)
    }
    val idleRowColor = remember(colorScheme.surfaceContainerLow, bg) {
        colorScheme.surfaceContainerLow.opaqueOver(bg, 0.94f)
    }
    val activeRowBorder = remember(colorScheme.primary) {
        BorderStroke(0.6.dp, colorScheme.primary.copy(alpha = 0.28f))
    }
    val idleRowBorder = remember(colorScheme.outlineVariant) {
        BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.22f))
    }
    val idleHiFiBadgeColor = remember(colorScheme.tertiaryContainer, idleRowColor) {
        colorScheme.tertiaryContainer.opaqueOver(idleRowColor, 0.94f)
    }
    val activeHiFiBadgeColor = remember(colorScheme.tertiaryContainer, activeRowColor) {
        colorScheme.tertiaryContainer.opaqueOver(activeRowColor, 0.96f)
    }
    val hiFiBadgeTextColor = colorScheme.onTertiaryContainer
    val compactHiFiLabel = stringResource(R.string.badge_hifi_short)

    // Stabilized item-level callbacks to prevent full LazyColumn recomposition during flings
    val onOpenTrackMenu = remember { { track: Track -> activeTrackForMenu = track } }
    val onOpenImportSheet = remember { { showImportSheet = true } }
    val onOpenCreatePlaylist = remember {
        {
            pendingTrackForNewPlaylist = null
            showCreatePlaylistDialog = true
        }
    }
    val onRenamePlaylistClick = remember { { pl: PlaylistSummary -> playlistToRename = pl } }
    val onClearMoodFilter = remember { { selectedMoodFilterId = LibraryMoodFilter.ALL.id } }
    val onSelectMoodFilter = remember {
        { filter: LibraryMoodFilter -> selectedMoodFilterId = filter.id }
    }

    // Instant in-memory filtering across songs, albums, artists, folders, genres, and playlists
    val filteredDisplayedTracks by remember(
        uiState.displayedTracks,
        uiState.allTracks,
        uiState.category,
        uiState.searchQuery,
        rawSearchQuery,
        selectedMoodFilter
    ) {
        derivedStateOf {
            val base = if (uiState.displayedTracks.isEmpty() &&
                uiState.allTracks.isNotEmpty() &&
                isMainHub &&
                uiState.searchQuery.isBlank()
            ) {
                uiState.allTracks
            } else {
                uiState.displayedTracks
            }
            val moodTag = if (isMainHub) selectedMoodFilter.tag else null
            val moodFiltered = if (moodTag == null) {
                base
            } else {
                base.filter { track -> moodTag in track.resolvedMoodTags() }
            }
            val q = rawSearchQuery.trim()
            if (q.isEmpty() || q.equals(uiState.searchQuery.trim(), ignoreCase = true)) {
                moodFiltered.toStableList()
            } else {
                LibrarySearchEngine.searchTracks(moodFiltered, q).toStableList()
            }
        }
    }

    val filteredPlaylists by remember(uiState.playlists, uiState.searchQuery, rawSearchQuery) {
        derivedStateOf {
            val q = rawSearchQuery.trim()
            if (q.isEmpty() || q.equals(uiState.searchQuery.trim(), ignoreCase = true)) {
                uiState.playlists
            } else {
                LibrarySearchEngine.filterPlaylists(uiState.playlists, q).toStableList()
            }
        }
    }

    val filteredAlbums by remember(uiState.albums, uiState.searchQuery, rawSearchQuery) {
        derivedStateOf { filterGroupsInstant(uiState.albums, rawSearchQuery, uiState.searchQuery) }
    }
    val filteredArtists by remember(uiState.artists, uiState.searchQuery, rawSearchQuery) {
        derivedStateOf { filterGroupsInstant(uiState.artists, rawSearchQuery, uiState.searchQuery) }
    }
    val filteredFolders by remember(uiState.folders, uiState.searchQuery, rawSearchQuery) {
        derivedStateOf { filterGroupsInstant(uiState.folders, rawSearchQuery, uiState.searchQuery) }
    }
    val filteredGenres by remember(uiState.genres, uiState.searchQuery, rawSearchQuery) {
        derivedStateOf { filterGroupsInstant(uiState.genres, rawSearchQuery, uiState.searchQuery) }
    }

    val likedCount = remember(uiState.allTracks) {
        uiState.allTracks.count { it.isFavorite }
    }

    val onSelectDisplayedTrack = remember(filteredDisplayedTracks, onSelectTrack) {
        { track: Track -> onSelectTrack(track, filteredDisplayedTracks) }
    }
    val onSelectDrillTrack = remember(uiState.drillDownTracks, onSelectTrack) {
        { track: Track -> onSelectTrack(track, uiState.drillDownTracks) }
    }
    val onOpenBentoCategory = remember(onCategoryChange) {
        { category: LibraryCategory -> onCategoryChange(category) }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_root"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            // Pinned Header Region (always respects statusBarsPadding so scrolled items never overlap system clock/icons)
            when {
                uiState.drillDown != null -> {
                    val drill = uiState.drillDown
                    val drillTitle = when (drill) {
                        is LibraryDrillDown.Album -> drill.albumName
                        is LibraryDrillDown.Artist -> drill.artistName
                        is LibraryDrillDown.Folder -> drill.folderName
                        is LibraryDrillDown.Genre -> drill.genreName
                        is LibraryDrillDown.Playlist -> drill.playlistName
                    }
                    val drillTracks = uiState.drillDownTracks
                    DrillDownPinnedHeader(
                        title = drillTitle,
                        subtitle = "${drillTracks.size} track${if (drillTracks.size == 1) "" else "s"} • ${formatDuration(drillTracks.sumOf { it.durationMs })}",
                        onBack = onCloseDrillDown,
                        onRename = if (drill is LibraryDrillDown.Playlist) {
                            { playlistToRename = PlaylistSummary(drill.playlistId, drill.playlistName, drillTracks.size) }
                        } else null,
                        onDelete = if (drill is LibraryDrillDown.Playlist) {
                            { onDeletePlaylist(drill.playlistId) }
                        } else null
                    )
                    LibraryScrolledHeaderDivider(listState = listState)
                }

                isMainHub -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 8.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LibraryEditorialHeader(onOpenSettings = onOpenSettings)

                        IsolatedScanProgressBanner(
                            scanStateFlow = scanStateFlow,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        LibraryPersistentSearchBar(
                            query = rawSearchQuery,
                            onQueryChange = { rawSearchQuery = it },
                            onClear = onClearSearchImmediate,
                            onSubmit = onSubmitSearch,
                            placeholder = stringResource(R.string.library_search_hint),
                            focusRequester = searchFocusRequester,
                            inputTestTag = "library_search_input"
                        )
                    }
                    LibraryScrolledHeaderDivider(listState = listState)
                }

                else -> {
                    val (subTitle, subCountSubtitle) = when (uiState.category) {
                        LibraryCategory.FAVORITES -> stringResource(R.string.library_tab_favorites) to
                            stringResource(R.string.library_bento_favorites_count, filteredDisplayedTracks.size)
                        LibraryCategory.PLAYLISTS -> stringResource(R.string.library_tab_playlists) to
                            stringResource(
                                if (filteredPlaylists.size == 1) R.string.library_bento_playlists_singular else R.string.library_bento_playlists_plural,
                                filteredPlaylists.size
                            )
                        LibraryCategory.ALBUMS -> stringResource(R.string.library_tab_albums) to
                            stringResource(
                                if (filteredAlbums.size == 1) R.string.library_bento_albums_singular else R.string.library_bento_albums_plural,
                                filteredAlbums.size
                            )
                        LibraryCategory.ARTISTS -> stringResource(R.string.library_tab_artists) to
                            stringResource(
                                if (filteredArtists.size == 1) R.string.library_bento_artists_singular else R.string.library_bento_artists_plural,
                                filteredArtists.size
                            )
                        LibraryCategory.FOLDERS -> stringResource(R.string.library_tab_folders) to
                            stringResource(
                                if (filteredFolders.size == 1) R.string.library_bento_folders_singular else R.string.library_bento_folders_plural,
                                filteredFolders.size
                            )
                        LibraryCategory.RECENT -> stringResource(R.string.library_recently_played_title) to
                            "${filteredDisplayedTracks.size} song${if (filteredDisplayedTracks.size == 1) "" else "s"}"
                        LibraryCategory.MOST_PLAYED -> stringResource(R.string.library_tab_most_played) to
                            "${filteredDisplayedTracks.size} song${if (filteredDisplayedTracks.size == 1) "" else "s"}"
                        LibraryCategory.GENRES -> stringResource(R.string.library_tab_genres) to
                            "${filteredGenres.size} genre${if (filteredGenres.size == 1) "" else "s"}"
                        else -> stringResource(R.string.library_tab_songs) to ""
                    }

                    LibrarySubViewTopBar(
                        title = subTitle,
                        subtitle = subCountSubtitle,
                        query = rawSearchQuery,
                        onQueryChange = { rawSearchQuery = it },
                        onClearQuery = onClearSearchImmediate,
                        onSubmitQuery = onSubmitSearch,
                        onBack = onReturnToHub,
                        focusRequester = searchFocusRequester,
                        showSortSelector = uiState.category == LibraryCategory.FAVORITES,
                        sortOrder = uiState.sortOrder,
                        onSortChange = onSortChange,
                        trailingActionIcon = when (uiState.category) {
                            LibraryCategory.PLAYLISTS -> Icons.Rounded.Add
                            LibraryCategory.FOLDERS -> CadenceIcons.CreateNewFolder
                            else -> null
                        },
                        trailingActionContentDescription = when (uiState.category) {
                            LibraryCategory.PLAYLISTS -> stringResource(R.string.action_create_playlist)
                            LibraryCategory.FOLDERS -> stringResource(R.string.action_add_folder)
                            else -> null
                        },
                        trailingActionTestTag = when (uiState.category) {
                            LibraryCategory.PLAYLISTS -> "subview_create_playlist_button"
                            LibraryCategory.FOLDERS -> "subview_add_folder_button"
                            else -> null
                        },
                        onTrailingAction = when (uiState.category) {
                            LibraryCategory.PLAYLISTS -> onOpenCreatePlaylist
                            LibraryCategory.FOLDERS -> onAddFolder
                            else -> null
                        }
                    )
                    LibraryScrolledHeaderDivider(listState = listState)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("library_results_list"),
                contentPadding = PaddingValues(
                    top = 6.dp,
                    bottom = 180.dp + navBarBottom
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    uiState.drillDown != null -> {
                        val drillTracks = uiState.drillDownTracks
                        if (drillTracks.isNotEmpty()) {
                            item(key = "drill_actions", contentType = "drill_actions") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onPlayTracks(drillTracks, false) },
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_play_all))
                                    }
                                    FilledTonalButton(
                                        onClick = { onPlayTracks(drillTracks, true) },
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Icon(CadenceIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_shuffle_all))
                                    }
                                }
                            }

                            items(
                                items = drillTracks,
                                key = { "drill_${it.id}" },
                                contentType = { "track_row" }
                            ) { track ->
                                val isCurrentTrack = track.id == currentTrackId
                                LibraryTrackRow(
                                    track = track,
                                    artworkShape = artworkShape,
                                    isCurrent = isCurrentTrack,
                                    isPlaying = isPlaying,
                                    activeColor = activeRowColor,
                                    idleColor = idleRowColor,
                                    activeBorder = activeRowBorder,
                                    idleBorder = idleRowBorder,
                                    hiFiBadgeColor = if (isCurrentTrack) activeHiFiBadgeColor else idleHiFiBadgeColor,
                                    hiFiBadgeTextColor = hiFiBadgeTextColor,
                                    hiFiLabel = compactHiFiLabel,
                                    onTrackClick = onSelectDrillTrack,
                                    onToggleFavorite = onToggleFavorite,
                                    onMoreClick = onOpenTrackMenu
                                )
                            }
                        } else {
                            item(key = "drill_empty", contentType = "empty_card") {
                                LibraryEmptyCard(
                                    title = null,
                                    message = "No tracks in this collection yet.",
                                    showScanCta = false,
                                    onScanFolderClick = {},
                                    onImportClick = {}
                                )
                            }
                        }
                    }

                    isMainHub -> {
                        val isSearching = rawSearchQuery.isNotBlank()

                        if (!isSearching) {
                            item(key = "bento_hub", contentType = "bento_hub") {
                                LibraryBentoHubGrid(
                                    likedCount = likedCount,
                                    playlistCount = uiState.playlists.size,
                                    albumCount = uiState.albums.size,
                                    artistCount = uiState.artists.size,
                                    folderCount = uiState.folders.size,
                                    onOpenCategory = onOpenBentoCategory
                                )
                            }
                        } else if (filteredPlaylists.isNotEmpty() || filteredAlbums.isNotEmpty() || filteredArtists.isNotEmpty()) {
                            item(key = "search_collections", contentType = "search_collections") {
                                LibrarySearchCollectionsStrip(
                                    playlists = filteredPlaylists,
                                    albums = filteredAlbums,
                                    artists = filteredArtists,
                                    onOpenDrillDown = onOpenDrillDown
                                )
                            }
                        }

                        if (uiState.allTracks.isNotEmpty() || isSearching) {
                            item(key = "mood_filters", contentType = "mood_filters") {
                                LibraryMoodFilterRow(
                                    selectedFilter = selectedMoodFilter,
                                    onSelectFilter = onSelectMoodFilter,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            item(key = "songs_action_bar", contentType = "songs_action_bar") {
                                LibrarySongsActionBar(
                                    trackCount = filteredDisplayedTracks.size,
                                    sortOrder = uiState.sortOrder,
                                    onShuffleClick = {
                                        if (filteredDisplayedTracks.isNotEmpty()) {
                                            onPlayTracks(filteredDisplayedTracks, true)
                                        }
                                    },
                                    onCreatePlaylistClick = onOpenCreatePlaylist,
                                    onSortChange = onSortChange,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        val tracks = filteredDisplayedTracks
                        if (tracks.isNotEmpty()) {
                            items(
                                items = tracks,
                                key = { "lib_${it.id}" },
                                contentType = { "track_row" }
                            ) { track ->
                                val isCurrentTrack = track.id == currentTrackId
                                LibraryTrackRow(
                                    track = track,
                                    artworkShape = artworkShape,
                                    isCurrent = isCurrentTrack,
                                    isPlaying = isPlaying,
                                    activeColor = activeRowColor,
                                    idleColor = idleRowColor,
                                    activeBorder = activeRowBorder,
                                    idleBorder = idleRowBorder,
                                    hiFiBadgeColor = if (isCurrentTrack) activeHiFiBadgeColor else idleHiFiBadgeColor,
                                    hiFiBadgeTextColor = hiFiBadgeTextColor,
                                    hiFiLabel = compactHiFiLabel,
                                    onTrackClick = onSelectDisplayedTrack,
                                    onToggleFavorite = onToggleFavorite,
                                    onMoreClick = onOpenTrackMenu
                                )
                            }
                        } else if (uiState.isInitialLibraryLoaded) {
                            item(key = "empty_main_tracks", contentType = "empty_card") {
                                val trimmedQuery = rawSearchQuery.trim()
                                val isSearchActive = trimmedQuery.isNotEmpty()
                                val isMoodFilterActive = selectedMoodFilter != LibraryMoodFilter.ALL
                                val isEmptyFreshLibrary = !isSearchActive && !isMoodFilterActive
                                val msg = when {
                                    isSearchActive -> stringResource(R.string.library_empty_search_query, trimmedQuery)
                                    isMoodFilterActive -> stringResource(R.string.library_empty_mood_filter, selectedMoodFilter.label)
                                    else -> stringResource(R.string.library_empty_songs)
                                }
                                LibraryEmptyCard(
                                    title = if (isEmptyFreshLibrary) stringResource(R.string.library_empty_title) else null,
                                    message = msg,
                                    showScanCta = isEmptyFreshLibrary,
                                    isSearchEmpty = isSearchActive,
                                    onScanFolderClick = onAddFolder,
                                    onImportClick = onOpenImportSheet,
                                    onClearSearchClick = when {
                                        isSearchActive -> onClearSearchImmediate
                                        isMoodFilterActive -> onClearMoodFilter
                                        else -> null
                                    },
                                    clearActionLabel = if (!isSearchActive && isMoodFilterActive) {
                                        stringResource(R.string.action_show_all_moods)
                                    } else {
                                        stringResource(R.string.action_clear_search_label)
                                    }
                                )
                            }
                        }
                    }

                    // Dedicated Library Sub-Views
                    else -> when (uiState.category) {
                        LibraryCategory.FAVORITES,
                        LibraryCategory.RECENT,
                        LibraryCategory.MOST_PLAYED -> {
                            val tracks = filteredDisplayedTracks
                            if (tracks.isNotEmpty()) {
                                item(key = "subview_shuffle_bar", contentType = "subview_shuffle_bar") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { onPlayTracks(tracks, false) },
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_play_all))
                                        }
                                        FilledTonalButton(
                                            onClick = { onPlayTracks(tracks, true) },
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                        ) {
                                            Icon(CadenceIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_shuffle_all))
                                        }
                                    }
                                }

                                items(
                                    items = tracks,
                                    key = { "sub_${it.id}" },
                                    contentType = { "track_row" }
                                ) { track ->
                                    val isCurrentTrack = track.id == currentTrackId
                                    LibraryTrackRow(
                                        track = track,
                                        artworkShape = artworkShape,
                                        isCurrent = isCurrentTrack,
                                        isPlaying = isPlaying,
                                        activeColor = activeRowColor,
                                        idleColor = idleRowColor,
                                        activeBorder = activeRowBorder,
                                        idleBorder = idleRowBorder,
                                        hiFiBadgeColor = if (isCurrentTrack) activeHiFiBadgeColor else idleHiFiBadgeColor,
                                        hiFiBadgeTextColor = hiFiBadgeTextColor,
                                        hiFiLabel = compactHiFiLabel,
                                        onTrackClick = onSelectDisplayedTrack,
                                        onToggleFavorite = onToggleFavorite,
                                        onMoreClick = onOpenTrackMenu
                                    )
                                }
                            } else if (uiState.isInitialLibraryLoaded) {
                                item(key = "empty_subview_tracks", contentType = "empty_card") {
                                    val trimmedQuery = rawSearchQuery.trim()
                                    val isSearchActive = trimmedQuery.isNotEmpty()
                                    val msg = when {
                                        isSearchActive -> stringResource(R.string.library_empty_search_query, trimmedQuery)
                                        uiState.category == LibraryCategory.FAVORITES -> stringResource(R.string.library_empty_favorites)
                                        uiState.category == LibraryCategory.RECENT -> stringResource(R.string.library_empty_recent)
                                        else -> stringResource(R.string.library_empty_most_played)
                                    }
                                    LibraryEmptyCard(
                                        title = null,
                                        message = msg,
                                        showScanCta = false,
                                        isSearchEmpty = isSearchActive,
                                        onScanFolderClick = {},
                                        onImportClick = {},
                                        onClearSearchClick = if (isSearchActive) onClearSearchImmediate else null
                                    )
                                }
                            }
                        }

                        LibraryCategory.PLAYLISTS -> {
                            item(key = "create_playlist_bar", contentType = "create_playlist_bar") {
                                FilledTonalButton(
                                    onClick = onOpenCreatePlaylist,
                                    shape = RoundedCornerShape(22.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(50.dp)
                                        .testTag("create_playlist_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.action_create_playlist), style = MaterialTheme.typography.titleSmall)
                                }
                            }

                            if (filteredPlaylists.isEmpty() && uiState.isInitialLibraryLoaded) {
                                item(key = "empty_playlists", contentType = "empty_card") {
                                    val trimmedQuery = rawSearchQuery.trim()
                                    val isSearchActive = trimmedQuery.isNotEmpty()
                                    LibraryEmptyCard(
                                        title = null,
                                        message = if (isSearchActive) {
                                            stringResource(R.string.library_empty_search_query, trimmedQuery)
                                        } else {
                                            stringResource(R.string.library_empty_playlists)
                                        },
                                        showScanCta = false,
                                        isSearchEmpty = isSearchActive,
                                        onScanFolderClick = {},
                                        onImportClick = {},
                                        onClearSearchClick = if (isSearchActive) onClearSearchImmediate else null
                                    )
                                }
                            } else {
                                items(
                                    items = filteredPlaylists,
                                    key = { "pl_${it.id}" },
                                    contentType = { "playlist_row" }
                                ) { pl ->
                                    PlaylistRowCard(
                                        playlist = pl,
                                        artworkShape = artworkShape,
                                        surfaceColor = idleRowColor,
                                        border = idleRowBorder,
                                        onOpenDrillDown = onOpenDrillDown,
                                        onPlayPlaylist = onPlayPlaylist,
                                        onRenameClick = onRenamePlaylistClick,
                                        onDeletePlaylist = onDeletePlaylist
                                    )
                                }
                            }
                        }

                        LibraryCategory.ALBUMS -> {
                            if (filteredAlbums.isEmpty() && uiState.isInitialLibraryLoaded) {
                                item(key = "empty_albums", contentType = "empty_card") {
                                    val trimmedQuery = rawSearchQuery.trim()
                                    val isSearchActive = trimmedQuery.isNotEmpty()
                                    LibraryEmptyCard(
                                        title = null,
                                        message = if (isSearchActive) {
                                            stringResource(R.string.library_empty_search_query, trimmedQuery)
                                        } else {
                                            stringResource(R.string.library_empty_songs)
                                        },
                                        showScanCta = !isSearchActive,
                                        isSearchEmpty = isSearchActive,
                                        onScanFolderClick = onAddFolder,
                                        onImportClick = onOpenImportSheet,
                                        onClearSearchClick = if (isSearchActive) onClearSearchImmediate else null
                                    )
                                }
                            } else {
                                items(
                                    items = filteredAlbums,
                                    key = { "album_${it.key}" },
                                    contentType = { "group_row" }
                                ) { group ->
                                    val onClick = remember(group.key, onOpenDrillDown) {
                                        { onOpenDrillDown(LibraryDrillDown.Album(group.key)) }
                                    }
                                    GroupCollectionRow(
                                        group = group,
                                        artworkShape = artworkShape,
                                        surfaceColor = idleRowColor,
                                        border = idleRowBorder,
                                        onClick = onClick
                                    )
                                }
                            }
                        }

                        LibraryCategory.ARTISTS -> {
                            if (filteredArtists.isEmpty() && uiState.isInitialLibraryLoaded) {
                                item(key = "empty_artists", contentType = "empty_card") {
                                    val trimmedQuery = rawSearchQuery.trim()
                                    val isSearchActive = trimmedQuery.isNotEmpty()
                                    LibraryEmptyCard(
                                        title = null,
                                        message = if (isSearchActive) {
                                            stringResource(R.string.library_empty_search_query, trimmedQuery)
                                        } else {
                                            stringResource(R.string.library_empty_songs)
                                        },
                                        showScanCta = !isSearchActive,
                                        isSearchEmpty = isSearchActive,
                                        onScanFolderClick = onAddFolder,
                                        onImportClick = onOpenImportSheet,
                                        onClearSearchClick = if (isSearchActive) onClearSearchImmediate else null
                                    )
                                }
                            } else {
                                items(
                                    items = filteredArtists,
                                    key = { "artist_${it.key}" },
                                    contentType = { "group_row" }
                                ) { group ->
                                    val onClick = remember(group.key, onOpenDrillDown) {
                                        { onOpenDrillDown(LibraryDrillDown.Artist(group.key)) }
                                    }
                                    GroupCollectionRow(
                                        group = group,
                                        artworkShape = artworkShape,
                                        surfaceColor = idleRowColor,
                                        border = idleRowBorder,
                                        onClick = onClick
                                    )
                                }
                            }
                        }

                        LibraryCategory.FOLDERS -> {
                            item(key = "folders_actions_bar", contentType = "folders_actions_bar") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = onAddFolder,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("folders_add_folder_button")
                                    ) {
                                        Icon(CadenceIcons.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.action_add_folder),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = onOpenImportSheet,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .height(44.dp)
                                            .testTag("import_audio_button")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.action_import_audio),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            if (filteredFolders.isEmpty() && uiState.isInitialLibraryLoaded) {
                                item(key = "empty_folders", contentType = "empty_card") {
                                    val trimmedQuery = rawSearchQuery.trim()
                                    val isSearchActive = trimmedQuery.isNotEmpty()
                                    LibraryEmptyCard(
                                        title = null,
                                        message = if (isSearchActive) {
                                            stringResource(R.string.library_empty_search_query, trimmedQuery)
                                        } else {
                                            stringResource(R.string.library_empty_folders)
                                        },
                                        showScanCta = !isSearchActive,
                                        isSearchEmpty = isSearchActive,
                                        onScanFolderClick = onAddFolder,
                                        onImportClick = onOpenImportSheet,
                                        onClearSearchClick = if (isSearchActive) onClearSearchImmediate else null
                                    )
                                }
                            } else {
                                items(
                                    items = filteredFolders,
                                    key = { "folder_${it.key}" },
                                    contentType = { "group_row" }
                                ) { group ->
                                    val onClick = remember(group.key, group.title, onOpenDrillDown) {
                                        { onOpenDrillDown(LibraryDrillDown.Folder(group.key, group.title)) }
                                    }
                                    GroupCollectionRow(
                                        group = group,
                                        artworkShape = artworkShape,
                                        surfaceColor = idleRowColor,
                                        border = idleRowBorder,
                                        onClick = onClick
                                    )
                                }
                            }
                        }

                        LibraryCategory.GENRES -> {
                            if (filteredGenres.isEmpty() && uiState.isInitialLibraryLoaded) {
                                item(key = "empty_genres", contentType = "empty_card") {
                                    val trimmedQuery = rawSearchQuery.trim()
                                    val isSearchActive = trimmedQuery.isNotEmpty()
                                    LibraryEmptyCard(
                                        title = null,
                                        message = if (isSearchActive) {
                                            stringResource(R.string.library_empty_search_query, trimmedQuery)
                                        } else {
                                            "Genres appear automatically when tracks include genre metadata."
                                        },
                                        showScanCta = false,
                                        isSearchEmpty = isSearchActive,
                                        onScanFolderClick = {},
                                        onImportClick = {},
                                        onClearSearchClick = if (isSearchActive) onClearSearchImmediate else null
                                    )
                                }
                            } else {
                                items(
                                    items = filteredGenres,
                                    key = { "genre_${it.key}" },
                                    contentType = { "group_row" }
                                ) { group ->
                                    val onClick = remember(group.key, onOpenDrillDown) {
                                        { onOpenDrillDown(LibraryDrillDown.Genre(group.key)) }
                                    }
                                    GroupCollectionRow(
                                        group = group,
                                        artworkShape = artworkShape,
                                        surfaceColor = idleRowColor,
                                        border = idleRowBorder,
                                        onClick = onClick
                                    )
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = ModalSheetShape
        ) {
            ImportSourcesSheetContent(
                savedFolderCount = uiState.savedFolderCount,
                onAddFolder = {
                    showImportSheet = false
                    onAddFolder()
                },
                onImportFiles = {
                    showImportSheet = false
                    onImportFiles()
                },
                onScanDevice = {
                    showImportSheet = false
                    onScanDevice()
                },
                onRescanFolders = {
                    showImportSheet = false
                    onRescanFolders()
                }
            )
        }
    }

    activeTrackForMenu?.let { track ->
        ModalBottomSheet(
            onDismissRequest = { activeTrackForMenu = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = ModalSheetShape
        ) {
            TrackActionsSheetContent(
                track = track,
                artworkShape = artworkShape,
                onPlayNext = {
                    activeTrackForMenu = null
                    onPlayNext(track)
                },
                onAddToQueue = {
                    activeTrackForMenu = null
                    onAddToQueue(track)
                },
                onAddToPlaylist = {
                    activeTrackForMenu = null
                    trackForPlaylistPicker = track
                },
                onShowDetails = {
                    activeTrackForMenu = null
                    trackForDetails = track
                },
                onRemoveFromPlaylist = activePlaylistId?.let { plId ->
                    {
                        activeTrackForMenu = null
                        onRemoveTrackFromPlaylist(plId, track.id)
                    }
                },
                onRemoveFromLibrary = {
                    activeTrackForMenu = null
                    onRemoveTrack(track)
                }
            )
        }
    }

    trackForPlaylistPicker?.let { track ->
        ModalBottomSheet(
            onDismissRequest = { trackForPlaylistPicker = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = ModalSheetShape
        ) {
            AddToPlaylistSheetContent(
                playlists = uiState.playlists,
                onSelectPlaylist = { plId ->
                    onAddTrackToPlaylist(plId, track)
                    trackForPlaylistPicker = null
                },
                onCreateNewPlaylist = {
                    pendingTrackForNewPlaylist = track.id
                    trackForPlaylistPicker = null
                    showCreatePlaylistDialog = true
                }
            )
        }
    }

    if (showCreatePlaylistDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_create_playlist),
            confirmLabel = "Create",
            onDismiss = {
                showCreatePlaylistDialog = false
                pendingTrackForNewPlaylist = null
            },
            onConfirm = { name ->
                onCreatePlaylist(name, pendingTrackForNewPlaylist)
                showCreatePlaylistDialog = false
                pendingTrackForNewPlaylist = null
            }
        )
    }

    playlistToRename?.let { pl ->
        PlaylistNameDialog(
            title = stringResource(R.string.action_rename_playlist),
            initialName = pl.name,
            confirmLabel = "Rename",
            onDismiss = { playlistToRename = null },
            onConfirm = { newName ->
                onRenamePlaylist(pl.id, newName)
                playlistToRename = null
            }
        )
    }

    trackForDetails?.let { track ->
        TrackDetailsDialog(
            track = track,
            onDismiss = { trackForDetails = null }
        )
    }
}

private fun filterGroupsInstant(
    groups: StableList<TrackGroup>,
    rawQuery: String,
    debouncedQuery: String
): StableList<TrackGroup> {
    val q = rawQuery.trim()
    if (q.isEmpty() || q.equals(debouncedQuery.trim(), ignoreCase = true)) {
        return groups
    }
    return groups.filter {
        it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
    }.toStableList()
}

@Composable
private fun DrillDownPinnedHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val colorScheme = MaterialTheme.colorScheme
    val buttonSurface = remember(colorScheme.surfaceContainerHigh, colorScheme.background) {
        colorScheme.surfaceContainerHigh.opaqueOver(colorScheme.background, 0.96f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = buttonSurface,
            contentColor = colorScheme.onSurface,
            border = BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.28f)),
            modifier = Modifier
                .size(44.dp)
                .testTag("drill_back_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onRename != null) {
            IconButton(onClick = onRename, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.action_rename_playlist),
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = CadenceIcons.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete_playlist),
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LibraryScrolledHeaderDivider(listState: LazyListState) {
    val isScrolled by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }
    if (isScrolled) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.opaqueOver(
                MaterialTheme.colorScheme.background,
                0.55f
            ),
            modifier = Modifier.testTag("library_scrolled_divider")
        )
    }
}

@Composable
private fun IsolatedScanProgressBanner(
    scanStateFlow: StateFlow<ScanState>,
    modifier: Modifier = Modifier
) {
    val scan by scanStateFlow.collectAsStateWithLifecycle()
    val bannerColor = MaterialTheme.colorScheme.tertiaryContainer.opaqueOver(
        MaterialTheme.colorScheme.background,
        0.90f
    )
    AnimatedVisibility(visible = scan.isScanning) {
        Surface(
            shape = ScanBannerShape,
            color = bannerColor,
            modifier = modifier
                .fillMaxWidth()
                .testTag("scan_progress_banner")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = scan.statusLabel.ifBlank { "Scanning library…" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun LibraryTrackRow(
    track: Track,
    artworkShape: ExpressiveArtworkShape,
    isCurrent: Boolean,
    isPlaying: Boolean,
    activeColor: Color,
    idleColor: Color,
    activeBorder: BorderStroke,
    idleBorder: BorderStroke,
    hiFiBadgeColor: Color,
    hiFiBadgeTextColor: Color,
    hiFiLabel: String,
    onTrackClick: (Track) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoreClick: (Track) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val clickAction = remember(track, onTrackClick) { { onTrackClick(track) } }
    val favAction = remember(track.id, onToggleFavorite) { { onToggleFavorite(track.id) } }
    val moreAction = remember(track, onMoreClick) { { onMoreClick(track) } }

    Surface(
        onClick = clickAction,
        shape = TrackRowShape,
        color = if (isCurrent) activeColor else idleColor,
        border = if (isCurrent) activeBorder else idleBorder,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("track_row_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveArtworkFrame(
                track = track,
                artworkShape = artworkShape,
                isPlaying = isCurrent && isPlaying,
                animateScale = false,
                decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                shapeOverride = null,
                showHairlineBorder = true,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCurrent) {
                        AudioPulseBars(
                            isPlaying = isPlaying,
                            tint = colorScheme.primary,
                            barHeight = 12.dp
                        )
                    }
                    Text(
                        text = track.title.ifBlank { "Untitled Track" },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isCurrent) colorScheme.primary else colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("track_title_${track.id}")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = track.artist.ifBlank { "Unknown Artist" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrent) {
                            colorScheme.onPrimaryContainer
                        } else {
                            colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("track_artist_${track.id}")
                    )
                    Text(
                        text = "• ${formatDuration(track.durationMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontSize = 10.5.sp
                        ),
                        color = if (isCurrent) {
                            colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        } else {
                            colorScheme.outline
                        },
                        maxLines = 1,
                        softWrap = false
                    )
                    if (track.isHiFi) {
                        val hifiCd = stringResource(R.string.badge_hifi_cd, hiFiLabel)
                        Box(
                            modifier = Modifier
                                .background(color = hiFiBadgeColor, shape = CompactHiFiShape)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = hifiCd
                                }
                                .testTag("track_hifi_badge_${track.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hiFiLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp
                                ),
                                color = hiFiBadgeTextColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 36.dp, height = 48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = favAction)
                        .testTag("track_favorite_${track.id}")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (track.isFavorite) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 34.dp, height = 48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = moreAction)
                        .testTag("track_more_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCollectionRow(
    group: TrackGroup,
    artworkShape: ExpressiveArtworkShape,
    surfaceColor: Color,
    border: BorderStroke,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = GroupRowShape,
        color = surfaceColor,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (group.coverTrack != null) {
                ExpressiveArtworkFrame(
                    track = group.coverTrack,
                    artworkShape = artworkShape,
                    isPlaying = false,
                    animateScale = false,
                    decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                    shapeOverride = null,
                    showHairlineBorder = true,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Surface(
                    shape = FastListThumbnailShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = CadenceIcons.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.subtitle} • ${group.trackCount} track${if (group.trackCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(group.totalDurationMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun PlaylistRowCard(
    playlist: PlaylistSummary,
    artworkShape: ExpressiveArtworkShape,
    surfaceColor: Color,
    border: BorderStroke,
    onOpenDrillDown: (LibraryDrillDown) -> Unit,
    onPlayPlaylist: (Long, Boolean) -> Unit,
    onRenameClick: (PlaylistSummary) -> Unit,
    onDeletePlaylist: (Long) -> Unit
) {
    val openClick = remember(playlist.id, playlist.name, onOpenDrillDown) {
        { onOpenDrillDown(LibraryDrillDown.Playlist(playlist.id, playlist.name)) }
    }
    val playClick = remember(playlist.id, onPlayPlaylist) { { onPlayPlaylist(playlist.id, false) } }
    val renameClick = remember(playlist, onRenameClick) { { onRenameClick(playlist) } }
    val deleteClick = remember(playlist.id, onDeletePlaylist) { { onDeletePlaylist(playlist.id) } }

    Surface(
        onClick = openClick,
        shape = GroupRowShape,
        color = surfaceColor,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("playlist_card_${playlist.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (playlist.coverTrack != null) {
                ExpressiveArtworkFrame(
                    track = playlist.coverTrack,
                    artworkShape = artworkShape,
                    isPlaying = false,
                    animateScale = false,
                    decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                    shapeOverride = null,
                    showHairlineBorder = true,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Surface(
                    shape = FastListThumbnailShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = CadenceIcons.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = playClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.action_play),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = renameClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.action_rename_playlist),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = deleteClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = CadenceIcons.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete_playlist),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryMoodFilterRow(
    selectedFilter: LibraryMoodFilter,
    onSelectFilter: (LibraryMoodFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val inactiveContainer = remember(colorScheme.surfaceContainerHigh, bg) {
        colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.96f)
    }
    val filters = remember { LibraryMoodFilter.entries }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("library_mood_filter_row")
    ) {
        items(
            items = filters,
            key = { "mood_pill_${it.id}" },
            contentType = { "mood_pill" }
        ) { filter ->
            val selected = selectedFilter == filter
            val onClick = remember(filter, onSelectFilter) { { onSelectFilter(filter) } }
            val labelRes = when (filter) {
                LibraryMoodFilter.ALL -> R.string.library_mood_all
                LibraryMoodFilter.LATE_NIGHT -> R.string.library_mood_late_night
                LibraryMoodFilter.ACOUSTIC -> R.string.library_mood_acoustic
                LibraryMoodFilter.SOULFUL -> R.string.library_mood_soulful
                LibraryMoodFilter.UPBEAT -> R.string.library_mood_upbeat
                LibraryMoodFilter.LOSSLESS -> R.string.library_mood_lossless
            }
            val leadingIcon = when (filter) {
                LibraryMoodFilter.ALL -> null
                LibraryMoodFilter.LATE_NIGHT -> CadenceIcons.NightsStay
                LibraryMoodFilter.ACOUSTIC -> CadenceIcons.Spa
                LibraryMoodFilter.SOULFUL -> Icons.Rounded.Favorite
                LibraryMoodFilter.UPBEAT -> CadenceIcons.Bolt
                LibraryMoodFilter.LOSSLESS -> CadenceIcons.HighQuality
            }
            FilterChip(
                selected = selected,
                onClick = onClick,
                leadingIcon = if (leadingIcon != null) {
                    {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else {
                    null
                },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.primary,
                    selectedLabelColor = colorScheme.onPrimary,
                    selectedLeadingIconColor = colorScheme.onPrimary,
                    containerColor = inactiveContainer,
                    labelColor = colorScheme.onSurfaceVariant,
                    iconColor = colorScheme.onSurfaceVariant
                ),
                border = null,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("library_mood_pill_${filter.id}")
            )
        }
    }
}

@Composable
private fun LibraryEmptyCard(
    title: String?,
    message: String,
    showScanCta: Boolean,
    isSearchEmpty: Boolean = false,
    onScanFolderClick: () -> Unit,
    onImportClick: () -> Unit,
    onClearSearchClick: (() -> Unit)? = null,
    clearActionLabel: String = stringResource(R.string.action_clear_search_label)
) {
    Surface(
        shape = EmptyCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .testTag("library_empty_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            isSearchEmpty -> Icons.Rounded.Search
                            showScanCta -> CadenceIcons.FolderOpen
                            else -> CadenceIcons.MusicNote
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onClearSearchClick != null) {
                Spacer(Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = onClearSearchClick,
                    shape = CircleShape,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("empty_search_clear_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(text = clearActionLabel)
                }
            } else if (showScanCta) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onScanFolderClick,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("empty_library_scan_folder_button")
                ) {
                    Icon(CadenceIcons.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(text = stringResource(R.string.action_scan_local_folders))
                }
                FilledTonalButton(
                    onClick = onImportClick,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("empty_library_import_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(text = stringResource(R.string.action_import_audio))
                }
            }
        }
    }
}
