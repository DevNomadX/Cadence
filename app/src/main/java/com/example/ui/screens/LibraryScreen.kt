package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LibraryCategory
import com.example.data.LibrarySortOrder
import com.example.data.PlaylistSummary
import com.example.data.StableList
import com.example.data.Track
import com.example.data.TrackGroup
import com.example.data.formatDuration
import com.example.data.library.ScanState
import com.example.data.toStableMap
import com.example.ui.components.AddToPlaylistSheetContent
import com.example.ui.components.AudioPulseBars
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.ImportSourcesSheetContent
import com.example.ui.components.PlaylistNameDialog
import com.example.ui.components.SmartPlaylistSheetContent
import com.example.ui.components.TrackActionsSheetContent
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.player.LibraryDrillDown
import com.example.ui.player.LibraryUiState
import com.example.ui.player.SmartPlaylistUiState
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val IdleScanStateFlow: StateFlow<ScanState> = MutableStateFlow(ScanState())
private val IdleSmartPlaylistFlow: StateFlow<SmartPlaylistUiState> = MutableStateFlow(SmartPlaylistUiState())

@OptIn(ExperimentalMaterial3Api::class)
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
    smartPlaylistFlow: StateFlow<SmartPlaylistUiState> = IdleSmartPlaylistFlow,
    currentTrackTitle: String = "",
    onGenerateSmartPlaylist: (String?, Boolean) -> Unit = { _, _ -> },
    onSaveSmartPlaylist: (String, List<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showImportSheet by remember { mutableStateOf(false) }
    var showSmartMixSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var activeTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylistPicker by remember { mutableStateOf<Track?>(null) }
    var trackForDetails by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var pendingTrackForNewPlaylist by remember { mutableStateOf<String?>(null) }
    var playlistToRename by remember { mutableStateOf<PlaylistSummary?>(null) }

    BackHandler(enabled = uiState.drillDown != null) {
        onCloseDrillDown()
    }

    val activePlaylistId = (uiState.drillDown as? LibraryDrillDown.Playlist)?.playlistId

    // Pre-compute opaque blended row surfaces once per theme change so scrolling items do zero alpha-blending
    val bg = MaterialTheme.colorScheme.background
    val activeRowColor = MaterialTheme.colorScheme.primaryContainer.opaqueOver(bg, 0.80f)
    val idleRowColor = MaterialTheme.colorScheme.surfaceContainerLow.opaqueOver(bg, 0.94f)

    // Stabilized item-level callbacks to prevent full LazyColumn recomposition during flings
    val onOpenTrackMenu = remember { { track: Track -> activeTrackForMenu = track } }
    val onOpenImportSheet = remember { { showImportSheet = true } }
    val onOpenSmartMix = remember(onGenerateSmartPlaylist) {
        {
            onGenerateSmartPlaylist(null, false)
            showSmartMixSheet = true
        }
    }
    val onSelectDisplayedTrack = remember(uiState.displayedTracks, onSelectTrack) {
        { track: Track -> onSelectTrack(track, uiState.displayedTracks) }
    }
    val onSelectDrillTrack = remember(uiState.drillDownTracks, onSelectTrack) {
        { track: Track -> onSelectTrack(track, uiState.drillDownTracks) }
    }
    val onRenamePlaylistClick = remember { { pl: PlaylistSummary -> playlistToRename = pl } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 148.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.drillDown != null) {
                val drill = uiState.drillDown
                val drillTitle = when (drill) {
                    is LibraryDrillDown.Album -> drill.albumName
                    is LibraryDrillDown.Artist -> drill.artistName
                    is LibraryDrillDown.Folder -> drill.folderName
                    is LibraryDrillDown.Genre -> drill.genreName
                    is LibraryDrillDown.Playlist -> drill.playlistName
                }
                val drillTracks = uiState.drillDownTracks

                item(key = "drill_header", contentType = "drill_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onCloseDrillDown,
                                modifier = Modifier.testTag("drill_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = drillTitle,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${drillTracks.size} track${if (drillTracks.size == 1) "" else "s"} · ${formatDuration(drillTracks.sumOf { it.durationMs })}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (drill is LibraryDrillDown.Playlist) {
                                IconButton(
                                    onClick = {
                                        playlistToRename = PlaylistSummary(drill.playlistId, drill.playlistName, drillTracks.size)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.action_rename_playlist),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeletePlaylist(drill.playlistId) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.action_delete_playlist),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (drillTracks.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onPlayTracks(drillTracks, false) },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
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
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_shuffle_all))
                                }
                            }
                        }
                    }
                }

                if (drillTracks.isEmpty()) {
                    item(key = "drill_empty", contentType = "empty_card") {
                        LibraryEmptyCard(
                            title = null,
                            message = "No tracks in this collection yet.",
                            showScanCta = false,
                            onScanFolderClick = {},
                            onImportClick = {}
                        )
                    }
                } else {
                    items(
                        items = drillTracks,
                        key = { it.id },
                        contentType = { "track_row" }
                    ) { track ->
                        LibraryTrackRow(
                            track = track,
                            artworkShape = artworkShape,
                            isCurrent = track.id == currentTrackId,
                            isPlaying = isPlaying,
                            activeColor = activeRowColor,
                            idleColor = idleRowColor,
                            onTrackClick = onSelectDrillTrack,
                            onToggleFavorite = onToggleFavorite,
                            onMoreClick = onOpenTrackMenu
                        )
                    }
                }
            } else {
                item(key = "header", contentType = "library_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.library_title),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.library_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = onOpenSmartMix,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("smart_mix_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = stringResource(R.string.smart_mix_button),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = onOpenSettings,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("library_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = stringResource(R.string.action_settings),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Button(
                                onClick = onOpenImportSheet,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("import_audio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    text = "Import",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                item(key = "scan_banner", contentType = "scan_banner") {
                    IsolatedScanProgressBanner(scanStateFlow = scanStateFlow)
                }

                item(key = "search_bar", contentType = "search_bar") {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.library_search_hint),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = uiState.searchQuery.isNotEmpty(),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.action_clear_search)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_search_input")
                    )
                }

                item(key = "categories", contentType = "category_row") {
                    val categories = remember {
                        listOf(
                            LibraryCategory.SONGS to R.string.library_tab_songs,
                            LibraryCategory.PLAYLISTS to R.string.library_tab_playlists,
                            LibraryCategory.ALBUMS to R.string.library_tab_albums,
                            LibraryCategory.ARTISTS to R.string.library_tab_artists,
                            LibraryCategory.FOLDERS to R.string.library_tab_folders,
                            LibraryCategory.GENRES to R.string.library_tab_genres,
                            LibraryCategory.FAVORITES to R.string.library_tab_favorites,
                            LibraryCategory.RECENT to R.string.library_tab_recent,
                            LibraryCategory.MOST_PLAYED to R.string.library_tab_most_played
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = categories,
                            key = { it.first.id },
                            contentType = { "category_chip" }
                        ) { (cat, labelRes) ->
                            val selected = uiState.category == cat
                            val catClick = remember(cat, onCategoryChange) { { onCategoryChange(cat) } }
                            FilterChip(
                                selected = selected,
                                onClick = catClick,
                                label = {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = null,
                                modifier = Modifier
                                    .height(42.dp)
                                    .testTag("filter_${cat.id}")
                            )
                        }
                    }
                }

                when (uiState.category) {
                    LibraryCategory.SONGS,
                    LibraryCategory.FAVORITES,
                    LibraryCategory.RECENT,
                    LibraryCategory.MOST_PLAYED -> {
                        val tracks = uiState.displayedTracks
                        if (tracks.isNotEmpty()) {
                            item(key = "controls_strip", contentType = "controls_strip") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier
                                                .height(38.dp)
                                                .clip(CircleShape)
                                                .clickable { onPlayTracks(tracks, true) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "Shuffle (${tracks.size})",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }

                                    if (uiState.category == LibraryCategory.SONGS || uiState.category == LibraryCategory.FAVORITES) {
                                        Box {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier
                                                    .height(38.dp)
                                                    .clip(CircleShape)
                                                    .clickable { showSortMenu = true }
                                                    .testTag("sort_button")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = uiState.sortOrder.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false }
                                            ) {
                                                LibrarySortOrder.entries.forEach { order ->
                                                    DropdownMenuItem(
                                                        text = { Text(order.label) },
                                                        onClick = {
                                                            onSortChange(order)
                                                            showSortMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            items(
                                items = tracks,
                                key = { it.id },
                                contentType = { "track_row" }
                            ) { track ->
                                LibraryTrackRow(
                                    track = track,
                                    artworkShape = artworkShape,
                                    isCurrent = track.id == currentTrackId,
                                    isPlaying = isPlaying,
                                    activeColor = activeRowColor,
                                    idleColor = idleRowColor,
                                    onTrackClick = onSelectDisplayedTrack,
                                    onToggleFavorite = onToggleFavorite,
                                    onMoreClick = onOpenTrackMenu
                                )
                            }
                        } else {
                            item(key = "empty_tracks", contentType = "empty_card") {
                                val isEmptyFreshSongs = uiState.category == LibraryCategory.SONGS && uiState.searchQuery.isBlank()
                                val msg = when {
                                    uiState.searchQuery.isNotBlank() -> stringResource(R.string.library_empty_search)
                                    uiState.category == LibraryCategory.FAVORITES -> stringResource(R.string.library_empty_favorites)
                                    uiState.category == LibraryCategory.RECENT -> stringResource(R.string.library_empty_recent)
                                    uiState.category == LibraryCategory.MOST_PLAYED -> stringResource(R.string.library_empty_most_played)
                                    else -> stringResource(R.string.library_empty_songs)
                                }
                                LibraryEmptyCard(
                                    title = if (isEmptyFreshSongs) stringResource(R.string.library_empty_title) else null,
                                    message = msg,
                                    showScanCta = isEmptyFreshSongs,
                                    onScanFolderClick = onAddFolder,
                                    onImportClick = onOpenImportSheet
                                )
                            }
                        }
                    }

                    LibraryCategory.PLAYLISTS -> {
                        item(key = "create_playlist_bar", contentType = "create_playlist_bar") {
                            FilledTonalButton(
                                onClick = {
                                    pendingTrackForNewPlaylist = null
                                    showCreatePlaylistDialog = true
                                },
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("create_playlist_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_create_playlist), style = MaterialTheme.typography.titleSmall)
                            }
                        }

                        if (uiState.playlists.isEmpty()) {
                            item(key = "empty_playlists", contentType = "empty_card") {
                                LibraryEmptyCard(
                                    title = null,
                                    message = stringResource(R.string.library_empty_playlists),
                                    showScanCta = false,
                                    onScanFolderClick = {},
                                    onImportClick = {}
                                )
                            }
                        } else {
                            items(
                                items = uiState.playlists,
                                key = { it.id },
                                contentType = { "playlist_row" }
                            ) { pl ->
                                PlaylistRowCard(
                                    playlist = pl,
                                    artworkShape = artworkShape,
                                    surfaceColor = idleRowColor,
                                    onOpenDrillDown = onOpenDrillDown,
                                    onPlayPlaylist = onPlayPlaylist,
                                    onRenameClick = onRenamePlaylistClick,
                                    onDeletePlaylist = onDeletePlaylist
                                )
                            }
                        }
                    }

                    LibraryCategory.ALBUMS -> {
                        if (uiState.albums.isEmpty()) {
                            item(key = "empty_albums", contentType = "empty_card") {
                                LibraryEmptyCard(
                                    title = null,
                                    message = if (uiState.searchQuery.isNotBlank()) stringResource(R.string.library_empty_search) else stringResource(R.string.library_empty_songs),
                                    showScanCta = uiState.searchQuery.isBlank(),
                                    onScanFolderClick = onAddFolder,
                                    onImportClick = onOpenImportSheet
                                )
                            }
                        } else {
                            items(
                                items = uiState.albums,
                                key = { it.key },
                                contentType = { "group_row" }
                            ) { group ->
                                val onClick = remember(group.key, onOpenDrillDown) {
                                    { onOpenDrillDown(LibraryDrillDown.Album(group.key)) }
                                }
                                GroupCollectionRow(
                                    group = group,
                                    artworkShape = artworkShape,
                                    surfaceColor = idleRowColor,
                                    onClick = onClick
                                )
                            }
                        }
                    }

                    LibraryCategory.ARTISTS -> {
                        if (uiState.artists.isEmpty()) {
                            item(key = "empty_artists", contentType = "empty_card") {
                                LibraryEmptyCard(
                                    title = null,
                                    message = if (uiState.searchQuery.isNotBlank()) stringResource(R.string.library_empty_search) else stringResource(R.string.library_empty_songs),
                                    showScanCta = uiState.searchQuery.isBlank(),
                                    onScanFolderClick = onAddFolder,
                                    onImportClick = onOpenImportSheet
                                )
                            }
                        } else {
                            items(
                                items = uiState.artists,
                                key = { it.key },
                                contentType = { "group_row" }
                            ) { group ->
                                val onClick = remember(group.key, onOpenDrillDown) {
                                    { onOpenDrillDown(LibraryDrillDown.Artist(group.key)) }
                                }
                                GroupCollectionRow(
                                    group = group,
                                    artworkShape = artworkShape,
                                    surfaceColor = idleRowColor,
                                    onClick = onClick
                                )
                            }
                        }
                    }

                    LibraryCategory.FOLDERS -> {
                        if (uiState.folders.isEmpty()) {
                            item(key = "empty_folders", contentType = "empty_card") {
                                LibraryEmptyCard(
                                    title = null,
                                    message = stringResource(R.string.library_empty_folders),
                                    showScanCta = true,
                                    onScanFolderClick = onAddFolder,
                                    onImportClick = onOpenImportSheet
                                )
                            }
                        } else {
                            items(
                                items = uiState.folders,
                                key = { it.key },
                                contentType = { "group_row" }
                            ) { group ->
                                val onClick = remember(group.key, group.title, onOpenDrillDown) {
                                    { onOpenDrillDown(LibraryDrillDown.Folder(group.key, group.title)) }
                                }
                                GroupCollectionRow(
                                    group = group,
                                    artworkShape = artworkShape,
                                    surfaceColor = idleRowColor,
                                    onClick = onClick
                                )
                            }
                        }
                    }

                    LibraryCategory.GENRES -> {
                        if (uiState.genres.isEmpty()) {
                            item(key = "empty_genres", contentType = "empty_card") {
                                LibraryEmptyCard(
                                    title = null,
                                    message = "Genres appear automatically when tracks include genre metadata.",
                                    showScanCta = false,
                                    onScanFolderClick = {},
                                    onImportClick = {}
                                )
                            }
                        } else {
                            items(
                                items = uiState.genres,
                                key = { it.key },
                                contentType = { "group_row" }
                            ) { group ->
                                val onClick = remember(group.key, onOpenDrillDown) {
                                    { onOpenDrillDown(LibraryDrillDown.Genre(group.key)) }
                                }
                                GroupCollectionRow(
                                    group = group,
                                    artworkShape = artworkShape,
                                    surfaceColor = idleRowColor,
                                    onClick = onClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
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
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
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
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
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

    if (showSmartMixSheet) {
        val smartState by smartPlaylistFlow.collectAsStateWithLifecycle()
        val trackLookup = remember(uiState.allTracks) {
            uiState.allTracks.associateBy { it.id }.toStableMap()
        }
        ModalBottomSheet(
            onDismissRequest = { showSmartMixSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            SmartPlaylistSheetContent(
                output = smartState.output,
                isGenerating = smartState.isGenerating,
                selectedMood = smartState.selectedMood,
                seedFromCurrent = smartState.seedFromCurrent,
                currentTrackTitle = currentTrackTitle.ifBlank {
                    uiState.allTracks.firstOrNull { it.id == currentTrackId }?.title ?: "Current Track"
                },
                trackLookup = trackLookup,
                artworkShape = artworkShape,
                onSelectMood = { mood ->
                    onGenerateSmartPlaylist(mood, smartState.seedFromCurrent)
                },
                onToggleSeedFromCurrent = { seed ->
                    onGenerateSmartPlaylist(smartState.selectedMood, seed)
                },
                onRegenerate = {
                    onGenerateSmartPlaylist(smartState.selectedMood, smartState.seedFromCurrent)
                },
                onPlaySmartPlaylist = { tracks ->
                    showSmartMixSheet = false
                    onPlayTracks(tracks, false)
                },
                onSaveSmartPlaylist = { name, ids ->
                    showSmartMixSheet = false
                    onSaveSmartPlaylist(name, ids)
                }
            )
        }
    }
}

@Composable
private fun IsolatedScanProgressBanner(scanStateFlow: StateFlow<ScanState>) {
    val scan by scanStateFlow.collectAsStateWithLifecycle()
    val bannerColor = MaterialTheme.colorScheme.tertiaryContainer.opaqueOver(
        MaterialTheme.colorScheme.background,
        0.90f
    )
    AnimatedVisibility(visible = scan.isScanning) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bannerColor,
            modifier = Modifier
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
    onTrackClick: (Track) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoreClick: (Track) -> Unit
) {
    val clickAction = remember(track, onTrackClick) { { onTrackClick(track) } }
    val favAction = remember(track.id, onToggleFavorite) { { onToggleFavorite(track.id) } }
    val moreAction = remember(track, onMoreClick) { { onMoreClick(track) } }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (isCurrent) activeColor else idleColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = clickAction)
            .testTag("track_row_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveArtworkFrame(
                track = track,
                artworkShape = artworkShape,
                isPlaying = isCurrent && isPlaying,
                animateScale = false,
                decodeSizePx = 160,
                modifier = Modifier.size(56.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
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
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "· ${formatDuration(track.durationMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }

            if (isCurrent) {
                AudioPulseBars(
                    isPlaying = isPlaying,
                    tint = MaterialTheme.colorScheme.primary,
                    barHeight = 16.dp
                )
            }

            IconButton(
                onClick = favAction,
                modifier = Modifier.size(40.dp)
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

            IconButton(
                onClick = moreAction,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupCollectionRow(
    group: TrackGroup,
    artworkShape: ExpressiveArtworkShape,
    surfaceColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (group.coverTrack != null) {
                ExpressiveArtworkFrame(
                    track = group.coverTrack,
                    artworkShape = artworkShape,
                    isPlaying = false,
                    animateScale = false,
                    decodeSizePx = 160,
                    modifier = Modifier.size(56.dp)
                )
            } else {
                Surface(
                    shape = artworkShape.toComposeShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.subtitle} · ${group.trackCount} track${if (group.trackCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(group.totalDurationMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun PlaylistRowCard(
    playlist: PlaylistSummary,
    artworkShape: ExpressiveArtworkShape,
    surfaceColor: Color,
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
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = openClick)
            .testTag("playlist_card_${playlist.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (playlist.coverTrack != null) {
                ExpressiveArtworkFrame(
                    track = playlist.coverTrack,
                    artworkShape = artworkShape,
                    isPlaying = false,
                    animateScale = false,
                    decodeSizePx = 160,
                    modifier = Modifier.size(56.dp)
                )
            } else {
                Surface(
                    shape = artworkShape.toComposeShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = playClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.action_play),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = renameClick, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.action_rename_playlist),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(onClick = deleteClick, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete_playlist),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun LibraryEmptyCard(
    title: String?,
    message: String,
    showScanCta: Boolean,
    onScanFolderClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag("library_empty_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 36.dp),
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
                        imageVector = if (showScanCta) Icons.Rounded.FolderOpen else Icons.Rounded.MusicNote,
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
            if (showScanCta) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onScanFolderClick,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("empty_library_scan_folder_button")
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
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
