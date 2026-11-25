package com.example.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
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
import com.example.data.UserProfile
import com.example.data.intelligence.DailyPicksCurator
import com.example.data.intelligence.DiscoverSection
import com.example.data.intelligence.DiscoverTuneConfig
import com.example.data.intelligence.ListeningInsights
import com.example.data.intelligence.LocalMoodAndAffinityEngine
import com.example.data.intelligence.MoodSmartPlaylist
import com.example.data.intelligence.MusicIntelligenceEngine
import com.example.data.intelligence.MusicIntelligenceOutput
import com.example.data.intelligence.MusicIntelligenceRequest
import com.example.data.intelligence.RoutineMix
import com.example.data.intelligence.currentLocalDayOfWeek
import com.example.data.intelligence.currentLocalEpochDay
import com.example.data.library.FolderScopeMatcher
import com.example.data.library.LibraryRepository
import com.example.data.library.LibrarySearchEngine
import com.example.data.library.ScanState
import com.example.data.library.SelectedFoldersWatcher
import com.example.data.lyrics.LyricsUiState
import com.example.data.lyrics.OfflineLyricsLoader
import com.example.data.toStableList
import com.example.playback.PlaybackController
import com.example.playback.PlaybackProgress
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import com.example.ui.theme.toAppCompatNightMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

@Keep
@Immutable
data class SmartPlaylistUiState(
    val selectedMood: String? = null,
    val energyFilter: String? = null,
    val discoveryBalance: Float = 0.5f,
    val seedFromCurrent: Boolean = false,
    val isGenerating: Boolean = false,
    val output: MusicIntelligenceOutput? = null
)

@Keep
@Immutable
data class HomeUiState(
    val allTracks: StableList<Track> = StableList(),
    val dailyPicks: StableList<Track> = StableList(),
    val recentlyPlayed: StableList<Track> = StableList(),
    val recentlyAdded: StableList<Track> = StableList(),
    val likedTracks: StableList<Track> = StableList(),
    val mostPlayedTracks: StableList<Track> = StableList(),
    val moodCollections: StableList<MoodSmartPlaylist> = StableList(),
    val isInitialLibraryLoaded: Boolean = true
)

@Keep
@Immutable
data class DiscoverUiState(
    val allTracks: StableList<Track> = StableList(),
    val routineMixes: StableList<RoutineMix> = StableList(),
    val sections: StableList<DiscoverSection> = StableList(),
    val moodPlaylists: StableList<MoodSmartPlaylist> = StableList(),
    val anchorTrackTitle: String? = null,
    val insights: ListeningInsights = ListeningInsights(),
    val tuneConfig: DiscoverTuneConfig = DiscoverTuneConfig(),
    val isInitialLibraryLoaded: Boolean = true,
    val refreshCount: Int = 0,
    val errorMessage: String? = null
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

    private val discoverRefreshNonceFlow = MutableStateFlow(0)
    private val discoverTuneConfigFlow = MutableStateFlow(DiscoverTuneConfig())
    private val currentEpochDayFlow = MutableStateFlow(currentLocalEpochDay())
    private val currentTimeContextSlotFlow = MutableStateFlow(computeTimeContextSlot())

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

    val scanAllFoldersState: StateFlow<Boolean> = preferencesRepo.scanAllFoldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    val audioSettingsState: StateFlow<AudioSettings> = preferencesRepo.audioSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AudioSettings())

    val minTrackDurationSecState: StateFlow<Int> = preferencesRepo.playbackPreferencesFlow
        .map { it.minTrackDurationSec }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            PreferencesRepository.DEFAULT_MIN_TRACK_DURATION_SEC
        )

    private var sleepTimerJob: Job? = null
    private var playlistObserveJob: Job? = null
    private var lyricsLoadJob: Job? = null
    private var backgroundEnrichmentJob: Job? = null
    private var startupSyncCompleted: Boolean = false

    private val selectedFoldersWatcher = SelectedFoldersWatcher(
        context = application,
        scope = viewModelScope,
        onFolderChangeDetected = {
            syncSelectedFoldersInBackground()
        }
    )

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

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
        },
        onTrackCompleted = { trackId ->
            viewModelScope.launch {
                libraryRepo.recordTrackCompletion(trackId)
            }
        },
        onTrackSkippedEarly = { trackId ->
            recordSkippedTrack(trackId)
            viewModelScope.launch {
                libraryRepo.recordTrackSkip(trackId)
            }
        }
    )

    private val initialThemeSnapshot: ThemePreferences =
        PreferencesRepository.readStartupThemeSnapshot(application)

    val themeState: StateFlow<ThemePreferences> = preferencesRepo.themePreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = initialThemeSnapshot
        )

    private val initialUserProfileSnapshot: UserProfile =
        PreferencesRepository.readStartupUserProfileSnapshot(application)

    val userProfileState: StateFlow<UserProfile> = preferencesRepo.userProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = initialUserProfileSnapshot
        )

    val progressState: StateFlow<PlaybackProgress> = playbackController.progressState

    private data class NowPlayingPrefsSlice(
        val shuffleEnabled: Boolean,
        val repeatMode: RepeatMode,
        val playbackSpeed: Float,
        val lastTrackId: String?,
        val minTrackDurationSec: Int
    )

    private val nowPlayingPrefsFlow = preferencesRepo.playbackPreferencesFlow
        .map { prefs ->
            NowPlayingPrefsSlice(
                shuffleEnabled = prefs.shuffleEnabled,
                repeatMode = prefs.repeatMode,
                playbackSpeed = prefs.playbackSpeed,
                lastTrackId = prefs.lastTrackId,
                minTrackDurationSec = prefs.minTrackDurationSec
            )
        }
        .distinctUntilChanged()

    private val libraryPrefsFlow = preferencesRepo.playbackPreferencesFlow
        .map { prefs -> prefs.sortOrder to prefs.minTrackDurationSec }
        .distinctUntilChanged()

    val nowPlayingState: StateFlow<NowPlayingUiState> = combine(
        libraryRepo.allTracksFlow,
        playbackController.playbackState,
        nowPlayingPrefsFlow
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
        .distinctUntilChanged()
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
        libraryPrefsFlow,
        searchQueryFlow,
        drillDownFlow,
        activePlaylistTracksFlow
    ) { cat, (sortOrder, minTrackDurationSec), query, drill, plTracks ->
        FilterInputs(cat, sortOrder, minTrackDurationSec, query, drill, plTracks)
    }.distinctUntilChanged()

    private val eligibleTracksWithIndexFlow = combine(
        libraryRepo.allTracksFlow,
        preferencesRepo.playbackPreferencesFlow.map { it.minTrackDurationSec }.distinctUntilChanged()
    ) { rawTracks, minDurSec ->
        val minDurMs = minDurSec * 1000L
        val eligible = if (minDurMs > 0L) {
            rawTracks.filter { it.durationMs >= minDurMs }
        } else {
            rawTracks
        }
        eligible to LibrarySearchEngine.buildIndex(eligible)
    }.flowOn(Dispatchers.Default)

    val libraryState: StateFlow<LibraryUiState> = combine(
        eligibleTracksWithIndexFlow,
        libraryRepo.playlistsFlow,
        libraryRepo.savedFoldersFlow,
        filterInputsFlow
    ) { (allTracks, indexedTracks), playlists, savedFolders, inputs ->
        val q = inputs.searchQuery.trim()
        val rankedSearchMatches = if (q.isEmpty()) {
            null
        } else {
            LibrarySearchEngine.searchIndexed(indexedTracks, q)
        }
        val matchingTracks = rankedSearchMatches ?: allTracks

        val displayed = when {
            // When actively searching, preserve relevance order (exact/prefix title matches first)
            rankedSearchMatches != null -> when (inputs.category) {
                LibraryCategory.FAVORITES -> rankedSearchMatches.filter { it.isFavorite }
                LibraryCategory.RECENT -> rankedSearchMatches.filter { it.lastPlayedAt > 0L }
                LibraryCategory.MOST_PLAYED -> rankedSearchMatches.filter { it.playCount > 0 }
                else -> rankedSearchMatches
            }
            inputs.category == LibraryCategory.SONGS ->
                LibraryRepository.sortTracks(allTracks, inputs.sortOrder)
            inputs.category == LibraryCategory.FAVORITES ->
                LibraryRepository.sortTracks(allTracks.filter { it.isFavorite }, inputs.sortOrder)
            inputs.category == LibraryCategory.RECENT ->
                allTracks.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }
            inputs.category == LibraryCategory.MOST_PLAYED ->
                allTracks.filter { it.playCount > 0 }
                    .sortedWith(compareByDescending<Track> { it.playCount }.thenByDescending { it.lastPlayedAt })
            else -> LibraryRepository.sortTracks(allTracks, inputs.sortOrder)
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

        val filteredPlaylists = LibrarySearchEngine.filterPlaylists(playlists, q)

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

    private val currentTrackIdForDiscoverFlow = playbackController.playbackState
        .map { it.currentTrackId }
        .distinctUntilChanged()

    private val minDurationForDiscoverFlow = preferencesRepo.playbackPreferencesFlow
        .map { it.minTrackDurationSec }
        .distinctUntilChanged()

    val homeState: StateFlow<HomeUiState> = combine(
        libraryRepo.allTracksFlow,
        minDurationForDiscoverFlow,
        currentEpochDayFlow
    ) { rawTracks, minDurSec, epochDay ->
        val minDurMs = minDurSec.coerceAtLeast(0) * 1000L
        val eligible = (if (minDurMs > 0L) {
            rawTracks.filter { it.durationMs >= minDurMs }
        } else {
            rawTracks
        })
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }

        val dailyPicks = DailyPicksCurator.generateDailyPicks(
            libraryTracks = eligible,
            epochDay = epochDay,
            engine = intelligenceEngine
        )

        val recentlyPlayed = eligible
            .filter { it.lastPlayedAt > 0L }
            .sortedByDescending { it.lastPlayedAt }
            .take(10)

        val recentlyAdded = eligible
            .sortedByDescending { it.dateAdded }
            .take(14)

        val likedTracks = eligible
            .filter { it.isFavorite }
            .sortedByDescending { it.dateAdded }

        val mostPlayedTracks = eligible
            .filter { it.playCount > 0 }
            .sortedWith(
                compareByDescending<Track> { it.playCount }
                    .thenByDescending { it.completedPlayCount }
                    .thenByDescending { it.lastPlayedAt }
            )

        val moodCollections = LocalMoodAndAffinityEngine
            .buildMoodSmartPlaylists(tracks = eligible)
            .toStableList()

        HomeUiState(
            allTracks = eligible.toStableList(),
            dailyPicks = dailyPicks,
            recentlyPlayed = recentlyPlayed.toStableList(),
            recentlyAdded = recentlyAdded.toStableList(),
            likedTracks = likedTracks.toStableList(),
            mostPlayedTracks = mostPlayedTracks.toStableList(),
            moodCollections = moodCollections,
            isInitialLibraryLoaded = true
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HomeUiState(isInitialLibraryLoaded = false)
        )

    private val discoverRefreshTriggerFlow = combine(
        discoverRefreshNonceFlow,
        currentTimeContextSlotFlow
    ) { nonce, _ -> nonce }

    val discoverState: StateFlow<DiscoverUiState> = combine(
        libraryRepo.allTracksFlow,
        currentTrackIdForDiscoverFlow,
        minDurationForDiscoverFlow,
        discoverRefreshTriggerFlow,
        discoverTuneConfigFlow
    ) { rawTracks, currentTrackId, minDurSec, nonce, tuneConfig ->
        val minDurMs = minDurSec.coerceAtLeast(0) * 1000L
        val eligibleTracks = (if (minDurMs > 0L) {
            rawTracks.filter { it.durationMs >= minDurMs }
        } else {
            rawTracks
        })
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }

        val skippedSnapshot = synchronized(recentSkippedTrackIds) { recentSkippedTrackIds.toList() }
        try {
            val feed = intelligenceEngine.generateDiscoverFeed(
                libraryTracks = eligibleTracks,
                currentTrackId = currentTrackId,
                skippedTrackIds = skippedSnapshot,
                shuffleNonce = nonce,
                tuneConfig = tuneConfig
            )
            DiscoverUiState(
                allTracks = eligibleTracks.toStableList(),
                routineMixes = feed.routineMixes,
                sections = feed.sections,
                moodPlaylists = feed.moodPlaylists,
                anchorTrackTitle = feed.anchorTrackTitle,
                insights = feed.insights,
                tuneConfig = tuneConfig,
                isInitialLibraryLoaded = true,
                refreshCount = nonce,
                errorMessage = null
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e("PlayerViewModel", "Failed to build Discover feed", t)
            DiscoverUiState(
                allTracks = eligibleTracks.toStableList(),
                routineMixes = StableList(),
                sections = StableList(),
                moodPlaylists = StableList(),
                anchorTrackTitle = null,
                insights = ListeningInsights(),
                tuneConfig = tuneConfig,
                isInitialLibraryLoaded = true,
                refreshCount = nonce,
                errorMessage = t.message?.takeIf { it.isNotBlank() } ?: "Unable to build local mixes right now."
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .catch { t ->
            Log.e("PlayerViewModel", "Discover stream encountered an error", t)
            emit(
                DiscoverUiState(
                    isInitialLibraryLoaded = true,
                    errorMessage = t.message?.takeIf { it.isNotBlank() } ?: "Unable to load Discover right now."
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DiscoverUiState(isInitialLibraryLoaded = false)
        )

    init {
        playbackController.connect()
        viewModelScope.launch {
            combine(
                libraryRepo.savedFoldersFlow,
                preferencesRepo.scanAllFoldersFlow
            ) { folders, scanAll ->
                folders.map { item ->
                    FolderScopeMatcher.normalize(
                        rawUriOrPath = item.treeUri,
                        displayName = item.displayName
                    )
                } to scanAll
            }.collect { (normalized, scanAll) ->
                selectedFoldersWatcher.updateFolders(normalized, scanAll)
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                libraryRepo.ensureInitialized()
            }
            val savedFolders = libraryRepo.getSavedFoldersOnce()
            val scanAll = preferencesRepo.isScanAllFoldersEnabled()
            val existingCount = libraryRepo.getAllTracksOnce().size

            // Stage 1 fast startup scan strictly on selected folders (or all folders if explicitly enabled)
            if (savedFolders.isNotEmpty() || scanAll) {
                val showStartupWave = existingCount == 0
                libraryRepo.scanner.syncSelectedFolders(
                    scanAllFolders = scanAll,
                    showProgress = showStartupWave,
                    pruneOutOfScope = true
                )
            }
            startupSyncCompleted = true
            restorePlaybackSessionIfIdle()
            scheduleStage2Enrichment()
        }
    }

    fun onAppResumed() {
        // Auto-refresh date-seeded Daily Picks across midnight & keep Routine Mixes aligned with clock
        val latestDay = currentLocalEpochDay()
        if (currentEpochDayFlow.value != latestDay) {
            currentEpochDayFlow.value = latestDay
        }
        val latestSlot = computeTimeContextSlot()
        if (currentTimeContextSlotFlow.value != latestSlot) {
            currentTimeContextSlotFlow.value = latestSlot
        }
        selectedFoldersWatcher.start()
        if (startupSyncCompleted) {
            viewModelScope.launch {
                syncSelectedFoldersInBackground()
            }
        }
    }

    private fun computeTimeContextSlot(nowMs: Long = System.currentTimeMillis()): Long {
        val day = currentLocalEpochDay(nowMs)
        val hour = LocalMoodAndAffinityEngine.currentLocalHour(nowMs)
        val dow = currentLocalDayOfWeek(nowMs)
        return (day shl 8) xor (dow.toLong() shl 5) xor hour.toLong()
    }

    fun onAppPaused() {
        selectedFoldersWatcher.stop()
    }

    private suspend fun syncSelectedFoldersInBackground() {
        val scanAll = preferencesRepo.isScanAllFoldersEnabled()
        val savedFolders = libraryRepo.getSavedFoldersOnce()
        if (!scanAll && savedFolders.isEmpty()) return
        val result = libraryRepo.scanner.syncSelectedFolders(
            scanAllFolders = scanAll,
            showProgress = false,
            pruneOutOfScope = true
        )
        if (result.addedCount > 0) {
            scheduleStage2Enrichment()
        }
    }

    private fun scheduleStage2Enrichment() {
        backgroundEnrichmentJob?.cancel()
        backgroundEnrichmentJob = viewModelScope.launch(Dispatchers.IO) {
            libraryRepo.runBackgroundEnrichment()
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
        synchronized(recentSkippedTrackIds) {
            recentSkippedTrackIds.remove(trackId)
            recentSkippedTrackIds.addFirst(trackId)
            while (recentSkippedTrackIds.size > 10) {
                recentSkippedTrackIds.removeLast()
            }
        }
    }

    fun previousTrack() = playbackController.previous()

    fun seekToFraction(fraction: Float) {
        val track = nowPlayingState.value.currentTrack ?: return
        val dur = progressState.value.durationMs
            .takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(1L)
        playbackController.seekTo((fraction.coerceIn(0f, 1f) * dur).toLong())
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.75f, 2.0f)
        playbackController.setPlaybackSpeed(clamped)
        viewModelScope.launch { preferencesRepo.setPlaybackSpeed(clamped) }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val current = nowPlayingState.value.playbackSpeed
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val nextSpeed = speeds[(idx + 1) % speeds.size]
        setPlaybackSpeed(nextSpeed)
    }

    // --- Offline Lyrics ---

    fun loadLyricsForTrack(track: Track) {
        val existing = _lyricsState.value
        if (existing is LyricsUiState.Available && existing.lyrics.trackId == track.id) return
        if (existing is LyricsUiState.Empty && existing.trackId == track.id) return

        lyricsLoadJob?.cancel()
        _lyricsState.value = LyricsUiState.Loading(track.id)
        lyricsLoadJob = viewModelScope.launch {
            val loaded = OfflineLyricsLoader.loadLyricsForTrack(getApplication(), track)
            _lyricsState.value = if (loaded != null && loaded.lines.isNotEmpty()) {
                LyricsUiState.Available(loaded)
            } else {
                LyricsUiState.Empty(trackId = track.id, trackTitle = track.title)
            }
        }
    }

    fun seekToMs(positionMs: Long) {
        playbackController.seekTo(positionMs)
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
            if (!userProfileState.value.hasCompletedOnboarding) {
                preferencesRepo.completeOnboarding(null)
            }
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
            if (!userProfileState.value.hasCompletedOnboarding) {
                preferencesRepo.completeOnboarding(null)
            }
            // Selecting an explicit music folder narrows scope strictly to selected folders
            preferencesRepo.setScanAllFoldersEnabled(false)
            val minSec = preferencesRepo.getMinTrackDurationSec()
            val result = libraryRepo.scanner.scanSafFolderTree(treeUri, minSec)
            scheduleStage2Enrichment()
            showSnackbar(buildScanSummary(result.addedCount, result.skippedDuplicates, result.failedCount, result.prunedCount))
        }
    }

    fun removeSavedFolder(treeUri: String) {
        viewModelScope.launch {
            libraryRepo.removeSavedFolder(treeUri)
            val scanAll = preferencesRepo.isScanAllFoldersEnabled()
            if (!scanAll) {
                val remaining = libraryRepo.getSavedFoldersOnce()
                if (remaining.isEmpty()) {
                    libraryRepo.pruneAllFolderTracksIfNoScope()
                } else {
                    libraryRepo.scanner.syncSelectedFolders(
                        scanAllFolders = false,
                        showProgress = false,
                        pruneOutOfScope = true
                    )
                }
            }
            showSnackbar("Removed music folder")
        }
    }

    fun setScanAllFoldersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setScanAllFoldersEnabled(enabled)
            if (enabled) {
                val result = libraryRepo.scanner.syncSelectedFolders(
                    scanAllFolders = true,
                    showProgress = true,
                    pruneOutOfScope = false
                )
                scheduleStage2Enrichment()
                showSnackbar(buildScanSummary(result.addedCount, result.skippedDuplicates, result.failedCount, result.prunedCount))
            } else {
                val remaining = libraryRepo.getSavedFoldersOnce()
                if (remaining.isEmpty()) {
                    libraryRepo.pruneAllFolderTracksIfNoScope()
                    showSnackbar("Restricted to selected folders")
                } else {
                    val result = libraryRepo.scanner.syncSelectedFolders(
                        scanAllFolders = false,
                        showProgress = true,
                        pruneOutOfScope = true
                    )
                    scheduleStage2Enrichment()
                    showSnackbar(buildScanSummary(result.addedCount, result.skippedDuplicates, result.failedCount, result.prunedCount))
                }
            }
        }
    }

    fun rescanSavedFolders() {
        viewModelScope.launch {
            val scanAll = preferencesRepo.isScanAllFoldersEnabled()
            val savedFolders = libraryRepo.getSavedFoldersOnce()
            if (!scanAll && savedFolders.isEmpty()) {
                showSnackbar("Add a music folder first or enable All Folders")
                return@launch
            }
            val result = libraryRepo.scanner.rescanAllSavedFolders(
                scanAllFolders = scanAll,
                showProgress = true
            )
            scheduleStage2Enrichment()
            showSnackbar(buildScanSummary(result.addedCount, result.skippedDuplicates, result.failedCount, result.prunedCount))
        }
    }

    fun scanDeviceMediaStore() {
        viewModelScope.launch {
            if (!userProfileState.value.hasCompletedOnboarding) {
                preferencesRepo.completeOnboarding(null)
            }
            val savedFolders = libraryRepo.getSavedFoldersOnce()
            val scanAll = if (savedFolders.isEmpty()) {
                preferencesRepo.setScanAllFoldersEnabled(true)
                true
            } else {
                preferencesRepo.isScanAllFoldersEnabled()
            }
            val result = libraryRepo.scanner.scanMediaStoreAudio(scanAllFolders = scanAll)
            scheduleStage2Enrichment()
            showSnackbar(buildScanSummary(result.addedCount, result.skippedDuplicates, result.failedCount, result.prunedCount))
        }
    }

    fun removeTrack(track: Track) {
        viewModelScope.launch {
            playbackController.removeTrackById(track.id)
            libraryRepo.removeTrack(track.id)
            showSnackbar("Removed \"${track.title}\"")
        }
    }

    private fun buildScanSummary(
        added: Int,
        duplicates: Int,
        failed: Int,
        pruned: Int = 0
    ): String = when {
        added > 0 && pruned > 0 -> "Added $added · removed $pruned missing track${if (pruned == 1) "" else "s"}"
        added > 0 -> "Added $added track${if (added == 1) "" else "s"}" +
            if (duplicates > 0) " · $duplicates unchanged" else ""
        pruned > 0 -> "Removed $pruned deleted track${if (pruned == 1) "" else "s"}"
        duplicates > 0 -> "Library up to date ($duplicates already indexed)"
        failed > 0 -> "No readable audio files found"
        else -> "No new audio files found"
    }

    // --- User Profile & Onboarding ---

    fun setUserDisplayName(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            preferencesRepo.setUserDisplayName(trimmed)
            showSnackbar(if (trimmed.isEmpty()) "Display name cleared" else "Updated name to $trimmed")
        }
    }

    fun completeOnboarding(displayName: String? = null) {
        viewModelScope.launch {
            preferencesRepo.completeOnboarding(displayName)
        }
    }

    // --- Offline Local Music Intelligence & Smart Playlists ---

    fun refreshDiscoverFeed() {
        discoverRefreshNonceFlow.value += 1
    }

    fun updateDiscoverTuneConfig(config: DiscoverTuneConfig) {
        val clamped = config.copy(discoveryBalance = config.discoveryBalance.coerceIn(0f, 1f))
        discoverTuneConfigFlow.value = clamped
        _smartPlaylistState.value = _smartPlaylistState.value.copy(
            selectedMood = clamped.selectedMood,
            energyFilter = clamped.energyFilter,
            discoveryBalance = clamped.discoveryBalance
        )
    }

    fun resetDiscoverTuneConfig() {
        updateDiscoverTuneConfig(DiscoverTuneConfig())
    }

    fun generateSmartPlaylist(
        targetMood: String? = discoverTuneConfigFlow.value.selectedMood,
        seedFromCurrent: Boolean = _smartPlaylistState.value.seedFromCurrent,
        energyFilter: String? = discoverTuneConfigFlow.value.energyFilter,
        discoveryBalance: Float = discoverTuneConfigFlow.value.discoveryBalance,
        maxTracks: Int = 15
    ) {
        val clampedBalance = discoveryBalance.coerceIn(0f, 1f)
        discoverTuneConfigFlow.value = DiscoverTuneConfig(
            selectedMood = targetMood,
            energyFilter = energyFilter,
            discoveryBalance = clampedBalance
        )
        _smartPlaylistState.value = _smartPlaylistState.value.copy(
            selectedMood = targetMood,
            energyFilter = energyFilter,
            discoveryBalance = clampedBalance,
            seedFromCurrent = seedFromCurrent,
            isGenerating = true
        )
        val seedId = if (seedFromCurrent) nowPlayingState.value.currentTrack?.id else null
        val skippedSnapshot = synchronized(recentSkippedTrackIds) { recentSkippedTrackIds.toList() }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val tracks = libraryRepo.getAllTracksOnce()
                        .filter { it.id.isNotBlank() }
                        .distinctBy { it.id }
                    val history = intelligenceEngine.deriveListeningHistory(tracks, skippedSnapshot)
                    val request = MusicIntelligenceRequest(
                        seedTrackId = seedId,
                        targetMood = targetMood,
                        energyFilter = energyFilter,
                        discoveryBalance = clampedBalance,
                        maxTracks = maxTracks
                    )
                    intelligenceEngine.analyzeAndRecommend(request, history, tracks)
                }
                _smartPlaylistState.value = _smartPlaylistState.value.copy(
                    isGenerating = false,
                    output = result
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e("PlayerViewModel", "Failed to generate smart playlist", t)
                _smartPlaylistState.value = _smartPlaylistState.value.copy(isGenerating = false)
                showSnackbar("Could not generate Smart Mix right now")
            }
        }
    }

    fun saveSmartPlaylist(name: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            libraryRepo.createPlaylist(name, trackIds)
            showSnackbar("Saved playlist \"$name\" (${trackIds.size} tracks)")
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
        AppCompatDelegate.setDefaultNightMode(mode.toAppCompatNightMode())
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

    fun setAcousticEnhanceEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setAcousticEnhanceEnabled(enabled) }
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
        selectedFoldersWatcher.stop()
        backgroundEnrichmentJob?.cancel()
        sleepTimerJob?.cancel()
        playlistObserveJob?.cancel()
        lyricsLoadJob?.cancel()
        // Release MediaController only — CadencePlaybackService continues playing in background!
        playbackController.releaseController()
    }
}
