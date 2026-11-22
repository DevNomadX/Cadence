package com.example.ui.player

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AudioFocusBehavior
import com.example.data.AudioSettings
import com.example.data.LibraryCategory
import com.example.data.LibrarySortOrder
import com.example.data.PlaylistSummary
import com.example.data.PreferencesRepository
import com.example.data.RepeatMode
import com.example.data.SavedFolderItem
import com.example.data.SleepTimerOption
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.data.Track
import com.example.data.TrackGroup
import com.example.data.intelligence.MusicIntelligenceEngine
import com.example.data.intelligence.MusicIntelligenceOutput
import com.example.data.intelligence.MusicIntelligenceRequest
import com.example.data.library.LibraryRepository
import com.example.data.library.ScanState
import com.example.data.toStableList
import com.example.playback.PlaybackController
import com.example.playback.PlaybackProgress
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class SleepTimerState(
    val option: SleepTimerOption = SleepTimerOption.OFF,
    val remainingMs: Long? = null
)

@Immutable
data class NowPlayingUiState(
    val currentTrack: Track? = null,
    val queueTracks: StableList<Track> = StableList(),
    val currentQueueIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isInitialLibraryLoaded: Boolean = true
)

@Immutable
sealed interface LibraryDrillDown {
    @Immutable
    data class Album(val albumName: String) : LibraryDrillDown

    @Immutable
    data class Artist(val artistName: String) : LibraryDrillDown

    @Immutable
    data class Folder(val folderPath: String, val folderName: String) : LibraryDrillDown

    @Immutable
    data class Genre(val genreName: String) : LibraryDrillDown

    @Immutable
    data class Playlist(val playlistId: Long, val playlistName: String) : LibraryDrillDown
}

@Immutable
data class SmartPlaylistUiState(
    val selectedMood: String? = null,
    val seedFromCurrent: Boolean = false,
    val isGenerating: Boolean = false,
    val output: MusicIntelligenceOutput? = null
)

@Immutable
data class LibraryUiState(
    val allTracks: StableList<Track> = StableList(),
    val displayedTracks: StableList<Track> = StableList(),
    val albums: StableList<TrackGroup> = StableList(),
    val artists: StableList<TrackGroup> = StableList(),
    val folders: StableList<TrackGroup> = StableList(),
    val genres: StableList<TrackGroup> = StableList(),
    val playlists: StableList<PlaylistSummary> = StableList(),
    val savedFolderCount: Int = 0,
    val category: LibraryCategory = LibraryCategory.SONGS,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.DATE_ADDED_DESC,
    val searchQuery: String = "",
    val drillDown: LibraryDrillDown? = null,
    val drillDownTracks: StableList<Track> = StableList(),
    val isInitialLibraryLoaded: Boolean = true
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepo = PreferencesRepository(application)
    private val libraryRepo = LibraryRepository(application, preferencesRepo)
    private val intelligenceEngine = MusicIntelligenceEngine()
    private val recentSkippedTrackIds = ArrayDeque<String>()

    private val sleepTimerFlow = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimerFlow.asStateFlow()

    val scanState: StateFlow<ScanState> get() = libraryRepo.scanState

    private val _smartPlaylistState = MutableStateFlow(SmartPlaylistUiState())
    val smartPlaylistState: StateFlow<SmartPlaylistUiState> = _smartPlaylistState.asStateFlow()

    private val categoryFlow = MutableStateFlow(LibraryCategory.SONGS)
    private val searchQueryFlow = MutableStateFlow("")
    private val drillDownFlow = MutableStateFlow<LibraryDrillDown?>(null)
    val hasLibraryDrillDown: StateFlow<Boolean> = drillDownFlow
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    private val activePlaylistTracksFlow = MutableStateFlow<List<Track>>(emptyList())
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val playlistsState: StateFlow<StableList<PlaylistSummary>> = libraryRepo.playlistsFlow
        .map { it.toStableList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), StableList())

    val savedFoldersState: StateFlow<StableList<SavedFolderItem>> = libraryRepo.savedFoldersFlow
        .map { it.toStableList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), StableList())

    val audioSettingsState: StateFlow<AudioSettings> = preferencesRepo.audioSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AudioSettings())

    val minTrackDurationSecState: StateFlow<Int> = preferencesRepo.playbackPreferencesFlow
        .map { it.minTrackDurationSec }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            PreferencesRepository.DEFAULT_MIN_TRACK_DURATION_SEC
        )

    private var sleepTimerJob: Job? = null
    private var playlistObserveJob: Job? = null

    private val playbackController: PlaybackController = PlaybackController(
        context = application,
        scope = viewModelScope,
        onTrackPlayStarted = { trackId ->
            viewModelScope.launch {
                libraryRepo.recordTrackPlay(trackId)
                preferencesRepo.savePlaybackPosition(trackId, 0L)
            }
        },
        onQueueChanged = { ids ->
            viewModelScope.launch {
                libraryRepo.saveQueueTrackIds(ids)
            }
        },
        onPositionCheckpoint = { trackId, posMs ->
            viewModelScope.launch {
                preferencesRepo.savePlaybackPosition(trackId, posMs)
            }
        },
        onPlaybackError = { message ->
            showSnackbar(message)
        },
        onTrackNaturallyEnded = {
            if (sleepTimerFlow.value.option == SleepTimerOption.END_OF_TRACK) {
                playbackController.pause()
                sleepTimerFlow.value = SleepTimerState(SleepTimerOption.OFF, null)
                showSnackbar("Paused at end of track")
            }
        }
    )

    val themeState: StateFlow<ThemePreferences> = preferencesRepo.themePreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThemePreferences()
        )

    val progressState: StateFlow<PlaybackProgress> = playbackController.progressState

    val nowPlayingState: StateFlow<NowPlayingUiState> = combine(
        libraryRepo.allTracksFlow,
        playbackController.playbackState,
        preferencesRepo.playbackPreferencesFlow
    ) { allTracks, playback, prefs ->
        val minDurMs = prefs.minTrackDurationSec * 1000L
        val eligibleTracks = if (minDurMs > 0L) allTracks.filter { it.durationMs >= minDurMs } else allTracks
        val trackMap = allTracks.associateBy { it.id }

        val queue = playback.queueTrackIds.mapNotNull { trackMap[it] }.ifEmpty { eligibleTracks }
        val currentTrack = playback.currentTrackId?.let { trackMap[it] }
            ?: prefs.lastTrackId?.let { trackMap[it] }
            ?: queue.firstOrNull()
        val currentIdx = if (currentTrack == null) {
            0
        } else if (playback.queueTrackIds.isNotEmpty()) {
            playback.currentQueueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        } else {
            queue.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)
        }

        NowPlayingUiState(
            currentTrack = currentTrack,
            queueTracks = queue.toStableList(),
            currentQueueIndex = currentIdx,
            isPlaying = playback.isPlaying && currentTrack != null,
            isPreparing = playback.isPreparing,
            playbackSpeed = if (playback.isConnected) playback.playbackSpeed else prefs.playbackSpeed,
            shuffleEnabled = if (playback.isConnected) playback.shuffleEnabled else prefs.shuffleEnabled,
            repeatMode = if (playback.isConnected) playback.repeatMode else prefs.repeatMode,
            isInitialLibraryLoaded = true
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = NowPlayingUiState(isInitialLibraryLoaded = false)
        )

    private data class FilterInputs(
        val category: LibraryCategory,
        val sortOrder: LibrarySortOrder,
        val minTrackDurationSec: Int,
        val searchQuery: String,
        val drillDown: LibraryDrillDown?,
        val playlistTracks: List<Track>
    )

    private val filterInputsFlow = combine(
        categoryFlow,
        preferencesRepo.playbackPreferencesFlow,
        searchQueryFlow,
        drillDownFlow,
        activePlaylistTracksFlow
    ) { cat, prefs, query, drill, plTracks ->
        FilterInputs(cat, prefs.sortOrder, prefs.minTrackDurationSec, query, drill, plTracks)
    }

    val libraryState: StateFlow<LibraryUiState> = combine(
        libraryRepo.allTracksFlow,
        libraryRepo.playlistsFlow,
        libraryRepo.savedFoldersFlow,
        filterInputsFlow
    ) { rawTracks, playlists, savedFolders, inputs ->
        val minDurMs = inputs.minTrackDurationSec * 1000L
        val allTracks = if (minDurMs > 0L) {
            rawTracks.filter { it.durationMs >= minDurMs }
        } else {
            rawTracks
        }

        val q = inputs.searchQuery.trim()
        val matchingTracks = if (q.isEmpty()) {
            allTracks
        } else {
            allTracks.filter { t ->
                t.title.contains(q, ignoreCase = true) ||
                    t.artist.contains(q, ignoreCase = true) ||
                    t.album.contains(q, ignoreCase = true) ||
                    t.genre.contains(q, ignoreCase = true) ||
                    t.folderName.contains(q, ignoreCase = true)
            }
        }

        val displayed = when (inputs.category) {
            LibraryCategory.SONGS -> LibraryRepository.sortTracks(matchingTracks, inputs.sortOrder)
            LibraryCategory.FAVORITES -> LibraryRepository.sortTracks(
                matchingTracks.filter { it.isFavorite },
                inputs.sortOrder
            )
            LibraryCategory.RECENT -> matchingTracks
                .filter { it.lastPlayedAt > 0L }
                .sortedByDescending { it.lastPlayedAt }
            LibraryCategory.MOST_PLAYED -> matchingTracks
                .filter { it.playCount > 0 }
                .sortedWith(compareByDescending<Track> { it.playCount }.thenByDescending { it.lastPlayedAt })
            else -> LibraryRepository.sortTracks(matchingTracks, inputs.sortOrder)
        }

        val drillTracks = when (val drill = inputs.drillDown) {
            null -> emptyList()
            is LibraryDrillDown.Album -> LibraryRepository.sortTracks(
                allTracks.filter { it.album.ifBlank { "Unknown Album" } == drill.albumName },
                inputs.sortOrder
            )
            is LibraryDrillDown.Artist -> LibraryRepository.sortTracks(
                allTracks.filter { it.artist.ifBlank { "Unknown Artist" } == drill.artistName },
                inputs.sortOrder
            )
            is LibraryDrillDown.Folder -> LibraryRepository.sortTracks(
                allTracks.filter { it.folderPath.ifBlank { it.folderName } == drill.folderPath },
                inputs.sortOrder
            )
            is LibraryDrillDown.Genre -> LibraryRepository.sortTracks(
                allTracks.filter { it.genre.trim() == drill.genreName },
                inputs.sortOrder
            )
            is LibraryDrillDown.Playlist -> inputs.playlistTracks
        }

        val filteredPlaylists = if (q.isEmpty()) {
            playlists
        } else {
            playlists.filter { it.name.contains(q, ignoreCase = true) }
        }

        LibraryUiState(
            allTracks = allTracks.toStableList(),
            displayedTracks = displayed.toStableList(),
            albums = LibraryRepository.groupAlbums(matchingTracks).toStableList(),
            artists = LibraryRepository.groupArtists(matchingTracks).toStableList(),
            folders = LibraryRepository.groupFolders(matchingTracks).toStableList(),
            genres = LibraryRepository.groupGenres(matchingTracks).toStableList(),
            playlists = filteredPlaylists.toStableList(),
            savedFolderCount = savedFolders.size,
            category = inputs.category,
            sortOrder = inputs.sortOrder,
            searchQuery = inputs.searchQuery,
            drillDown = inputs.drillDown,
            drillDownTracks = drillTracks.toStableList(),
            isInitialLibraryLoaded = true
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = LibraryUiState(isInitialLibraryLoaded = false)
        )

    init {
        playbackController.connect()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                libraryRepo.ensureInitialized()
            }
            restorePlaybackSessionIfIdle()
        }
    }

    private suspend fun restorePlaybackSessionIfIdle() {
        val connected = playbackController.playbackState.first { it.isConnected }
        if (connected.queueTrackIds.isNotEmpty() || playbackController.hasActiveQueue()) {
            return
        }

        val (allTracks, prefs, savedQueueIds) = withContext(Dispatchers.IO) {
            val tracks = libraryRepo.getAllTracksOnce()
            if (tracks.isEmpty()) {
                Triple(emptyList<Track>(), null, emptyList<String>())
            } else {
                Triple(
                    tracks,
                    preferencesRepo.playbackPreferencesFlow.first(),
                    libraryRepo.loadSavedQueueTrackIds()
                )
            }
        }
        if (allTracks.isEmpty() || prefs == null) {
            // Fresh empty library: stay completely idle with zero background synthesis or fake tracks
            return
        }

        val trackMap = allTracks.associateBy { it.id }
        val restoredQueue = savedQueueIds.mapNotNull { trackMap[it] }.ifEmpty { allTracks }
        val startIdx = prefs.lastTrackId?.let { id ->
            restoredQueue.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        } ?: 0

        playbackController.setQueue(
            tracks = restoredQueue,
            startIndex = startIdx,
            startPositionMs = prefs.lastPositionMs,
            autoPlay = false
        )
        playbackController.setRepeatMode(prefs.repeatMode)
        playbackController.setShuffleEnabled(prefs.shuffleEnabled)
        playbackController.setPlaybackSpeed(prefs.playbackSpeed)
    }

    // --- Playback Controls ---

    fun togglePlayPause() {
        if (!playbackController.hasActiveQueue()) {
            val state = nowPlayingState.value
            if (state.queueTracks.isEmpty()) return
            viewModelScope.launch {
                playbackController.setQueue(
                    tracks = state.queueTracks,
                    startIndex = state.currentQueueIndex,
                    autoPlay = true
                )
            }
            return
        }
        playbackController.togglePlayPause()
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val ordered = if (shuffle) tracks.shuffled() else tracks
        val safeIdx = if (shuffle) 0 else startIndex.coerceIn(0, ordered.lastIndex)
        viewModelScope.launch {
            playbackController.setQueue(
                tracks = ordered,
                startIndex = safeIdx,
                startPositionMs = 0L,
                autoPlay = true
            )
            if (shuffle) {
                playbackController.setShuffleEnabled(true)
                preferencesRepo.setShuffleEnabled(true)
            }
        }
    }

    fun selectTrackFromList(track: Track, contextTracks: List<Track>) {
        val list = contextTracks.ifEmpty { libraryState.value.allTracks }
        val idx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playTracks(list, startIndex = idx, shuffle = false)
    }

    fun selectQueueIndex(index: Int) {
        playbackController.playAtQueueIndex(index)
    }

    fun nextTrack() {
        val progress = progressState.value
        val active = nowPlayingState.value
        val track = active.currentTrack
        if (track != null) {
            val dur = progress.durationMs.takeIf { it > 0L } ?: track.durationMs
            if (active.isPlaying && dur > 0L && progress.positionMs < (dur * 0.30).toLong()) {
                recordSkippedTrack(track.id)
            }
        }
        playbackController.next()
    }

    private fun recordSkippedTrack(trackId: String) {
        recentSkippedTrackIds.remove(trackId)
        recentSkippedTrackIds.addFirst(trackId)
        while (recentSkippedTrackIds.size > 10) {
            recentSkippedTrackIds.removeLast()
        }
    }

    fun previousTrack() = playbackController.previous()

    fun seekToFraction(fraction: Float) {
        val track = nowPlayingState.value.currentTrack ?: return
        val dur = progressState.value.durationMs
            .takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(1L)
        playbackController.seekTo((fraction.coerceIn(0f, 1f) * dur).toLong())
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)
        val current = nowPlayingState.value.playbackSpeed
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val nextSpeed = speeds[(idx + 1) % speeds.size]
        playbackController.setPlaybackSpeed(nextSpeed)
        viewModelScope.launch { preferencesRepo.setPlaybackSpeed(nextSpeed) }
    }

    fun toggleShuffle() {
        val next = !nowPlayingState.value.shuffleEnabled
        playbackController.setShuffleEnabled(next)
        viewModelScope.launch { preferencesRepo.setShuffleEnabled(next) }
    }

    fun cycleRepeatMode() {
        val next = nowPlayingState.value.repeatMode.next()
        playbackController.setRepeatMode(next)
        viewModelScope.launch { preferencesRepo.setRepeatMode(next) }
    }

    fun toggleFavorite(trackId: String? = nowPlayingState.value.currentTrack?.id) {
        val id = trackId ?: return
        viewModelScope.launch {
            libraryRepo.toggleFavorite(id)
        }
    }

    // --- Queue Operations ---

    fun playNext(track: Track) {
        viewModelScope.launch {
            val ok = playbackController.playNext(track)
            if (ok) {
                showSnackbar("Playing \"${track.title}\" next")
            } else {
                showSnackbar("Preparing playback service — try again")
            }
        }
    }

    fun addToQueue(track: Track) {
        viewModelScope.launch {
            val ok = playbackController.addToQueue(track)
            if (ok) {
                showSnackbar("Added \"${track.title}\" to queue")
            } else {
                showSnackbar("Preparing playback service — try again")
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackController.moveQueueItem(fromIndex, toIndex)
    }

    fun removeQueueItem(index: Int) {
        playbackController.removeQueueItem(index)
    }

    fun clearQueue() {
        playbackController.clearUpcomingQueue()
        showSnackbar("Cleared upcoming queue")
    }

    fun saveQueueAsPlaylist(name: String) {
        val ids = nowPlayingState.value.queueTracks.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            libraryRepo.createPlaylist(name, ids)
            showSnackbar("Saved playlist \"$name\"")
        }
    }

    // --- Playlists ---

    fun createPlaylist(name: String, initialTrackId: String? = null) {
        viewModelScope.launch {
            val initial = listOfNotNull(initialTrackId)
            libraryRepo.createPlaylist(name, initial)
            showSnackbar("Created playlist \"${name.trim().ifBlank { "Untitled Playlist" }}\"")
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            libraryRepo.renamePlaylist(playlistId, newName)
            val currentDrill = drillDownFlow.value
            if (currentDrill is LibraryDrillDown.Playlist && currentDrill.playlistId == playlistId) {
                drillDownFlow.value = LibraryDrillDown.Playlist(playlistId, newName.trim())
            }
            showSnackbar("Renamed playlist")
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            libraryRepo.deletePlaylist(playlistId)
            val currentDrill = drillDownFlow.value
            if (currentDrill is LibraryDrillDown.Playlist && currentDrill.playlistId == playlistId) {
                closeDrillDown()
            }
            showSnackbar("Deleted playlist")
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            val added = libraryRepo.addTrackToPlaylist(playlistId, track.id)
            showSnackbar(
                if (added) "Added \"${track.title}\" to playlist" else "Already in playlist"
            )
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            libraryRepo.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun playPlaylist(playlistId: Long, shuffle: Boolean = false) {
        viewModelScope.launch {
            val tracks = libraryRepo.getPlaylistTracksOnce(playlistId)
            if (tracks.isEmpty()) {
                showSnackbar("This playlist is empty")
            } else {
                playTracks(tracks, startIndex = 0, shuffle = shuffle)
            }
        }
    }

    // --- Library Scanning & Browsing ---

    fun setLibraryCategory(category: LibraryCategory) {
        categoryFlow.value = category
        closeDrillDown()
    }

    fun setSortOrder(order: LibrarySortOrder) {
        viewModelScope.launch { preferencesRepo.setSortOrder(order) }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun openDrillDown(drillDown: LibraryDrillDown) {
        playlistObserveJob?.cancel()
        drillDownFlow.value = drillDown
        if (drillDown is LibraryDrillDown.Playlist) {
            playlistObserveJob = viewModelScope.launch {
                libraryRepo.observePlaylistTracks(drillDown.playlistId).collect { list ->
                    activePlaylistTracksFlow.value = list
                }
            }
        } else {
            activePlaylistTracksFlow.value = emptyList()
        }
    }

    fun closeDrillDown() {
        playlistObserveJob?.cancel()
        drillDownFlow.value = null
        activePlaylistTracksFlow.value = emptyList()
    }

    fun importAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val minSec = preferencesRepo.getMinTrackDurationSec()
            val result = libraryRepo.scanner.importAudioFiles(uris, minSec)
            val msg = buildScanSummary(result.addedCount, result.skippedDuplicates, result.failedCount)
            showSnackbar(msg)
            if (result.firstAddedTrackId != null) {
                val all = libraryRepo.getAllTracksOnce()
                val idx = all.indexOfFirst { it.id == result.firstAddedTrackId }
                if (idx >= 0) {
                    playTracks(all, startIndex = idx, shuffle = false)
                }
            }
        }
    }

    fun addMusicFolder(treeUri: Uri) {
        viewModelScope.launch {
            val minSec = preferencesRepo.getMinTrackDurationSec()
            val result = libraryRepo.scanner.scanSafFolderTree(treeUri, minSec)
            showSnackbar(buildScanSummary(result.addedCount, result.skippedDuplicates, result.failedCount))
        }
    }

    fun removeSavedFolder(treeUri: String) {
        viewModelScope.launch {
            libraryRepo.removeSavedFolder(treeUri)
            showSnackbar("Removed music folder")
        }
    }

    fun rescanSavedFolders() {
        viewModelScope.launch {
            val minSec = preferencesRepo.getMinTrackDurationSec()
            val result = libraryRepo.scanner.rescanAllSavedFolders(minSec)
            showSnackbar(buildScanSummary(result.addedCount, result.skippedDuplicates, result.failedCount))
        }
    }

    fun scanDeviceMediaStore() {
        viewModelScope.launch {
            val minSec = preferencesRepo.getMinTrackDurationSec()
            val result = libraryRepo.scanner.scanMediaStoreAudio(minSec)
            libraryRepo.scanner.pruneMissingLocalTracks()
            showSnackbar(buildScanSummary(result.addedCount, result.skippedDuplicates, result.failedCount))
        }
    }

    fun removeTrack(track: Track) {
        viewModelScope.launch {
            playbackController.removeTrackById(track.id)
            libraryRepo.removeTrack(track.id)
            showSnackbar("Removed \"${track.title}\"")
        }
    }

    private fun buildScanSummary(added: Int, duplicates: Int, failed: Int): String = when {
        added > 0 -> "Added $added track${if (added == 1) "" else "s"}" +
            if (duplicates > 0) " · $duplicates skipped" else ""
        duplicates > 0 -> "Library up to date ($duplicates already indexed)"
        failed > 0 -> "No readable audio files found"
        else -> "No new audio files found"
    }

    // --- Offline Local Music Intelligence & Smart Playlists ---

    fun generateSmartPlaylist(
        targetMood: String? = _smartPlaylistState.value.selectedMood,
        seedFromCurrent: Boolean = _smartPlaylistState.value.seedFromCurrent,
        maxTracks: Int = 15
    ) {
        _smartPlaylistState.value = _smartPlaylistState.value.copy(
            selectedMood = targetMood,
            seedFromCurrent = seedFromCurrent,
            isGenerating = true
        )
        val seedId = if (seedFromCurrent) nowPlayingState.value.currentTrack?.id else null
        val skippedSnapshot = recentSkippedTrackIds.toList()

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val tracks = libraryRepo.getAllTracksOnce()
                val history = intelligenceEngine.deriveListeningHistory(tracks, skippedSnapshot)
                val request = MusicIntelligenceRequest(
                    seedTrackId = seedId,
                    targetMood = targetMood,
                    maxTracks = maxTracks
                )
                intelligenceEngine.analyzeAndRecommend(request, history, tracks)
            }
            _smartPlaylistState.value = _smartPlaylistState.value.copy(
                isGenerating = false,
                output = result
            )
        }
    }

    fun saveSmartPlaylist(name: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            libraryRepo.createPlaylist(name, trackIds)
            showSnackbar("Saved smart playlist \"$name\" (${trackIds.size} tracks)")
        }
    }

    // --- Themes, Library, Audio & Performance Settings ---

    fun setPalette(palette: ExpressivePalette) {
        viewModelScope.launch { preferencesRepo.setPalette(palette) }
    }

    fun matchCurrentTrackMood() {
        val suggested = nowPlayingState.value.currentTrack?.suggestedPalette ?: return
        setPalette(suggested)
        showSnackbar("Theme synced to ${suggested.displayName}")
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        viewModelScope.launch { preferencesRepo.setAppearanceMode(mode) }
    }

    fun setArtworkShape(shape: ExpressiveArtworkShape) {
        viewModelScope.launch { preferencesRepo.setArtworkShape(shape) }
    }

    fun setWavyScrubberEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setWavyScrubberEnabled(enabled) }
    }

    fun setAmbientAuraEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setAmbientAuraEnabled(enabled) }
    }

    fun setPerformanceModeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setPerformanceModeEnabled(enabled) }
    }

    fun setMinTrackDurationSec(seconds: Int) {
        viewModelScope.launch { preferencesRepo.setMinTrackDurationSec(seconds) }
    }

    fun setGaplessPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setGaplessPlaybackEnabled(enabled) }
    }

    fun setCrossfadeDurationSec(seconds: Int) {
        viewModelScope.launch { preferencesRepo.setCrossfadeDurationSec(seconds) }
    }

    fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setPauseOnHeadphoneDisconnect(enabled) }
    }

    fun setAudioFocusBehavior(behavior: AudioFocusBehavior) {
        viewModelScope.launch { preferencesRepo.setAudioFocusBehavior(behavior) }
    }

    // --- Sleep Timer ---

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        when (option) {
            SleepTimerOption.OFF -> {
                sleepTimerFlow.value = SleepTimerState(SleepTimerOption.OFF, null)
                showSnackbar("Sleep timer turned off")
            }

            SleepTimerOption.END_OF_TRACK -> {
                sleepTimerFlow.value = SleepTimerState(SleepTimerOption.END_OF_TRACK, null)
                showSnackbar("Playback will pause after this track")
            }

            else -> {
                val totalMs = option.durationMs ?: return
                sleepTimerFlow.value = SleepTimerState(option, totalMs)
                showSnackbar("Sleep timer set for ${option.label}")
                sleepTimerJob = viewModelScope.launch {
                    var remaining = totalMs
                    while (isActive && remaining > 0L) {
                        delay(1_000L)
                        remaining = (remaining - 1_000L).coerceAtLeast(0L)
                        sleepTimerFlow.value = SleepTimerState(option, remaining)
                    }
                    if (isActive) {
                        playbackController.pause()
                        sleepTimerFlow.value = SleepTimerState(SleepTimerOption.OFF, null)
                        showSnackbar("Sleep timer paused playback")
                    }
                }
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
        playlistObserveJob?.cancel()
        // Release MediaController only — CadencePlaybackService continues playing in background!
        playbackController.releaseController()
    }
}
