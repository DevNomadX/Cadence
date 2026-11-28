package com.example.ui.screens

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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.StableList
import com.example.data.Track
import com.example.data.intelligence.DiscoverCategory
import com.example.data.intelligence.DiscoverSection
import com.example.data.intelligence.DiscoverTuneConfig
import com.example.data.toStableMap
import com.example.ui.components.AudioPulseBars
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.FAST_THUMBNAIL_DECODE_PX
import com.example.ui.components.FastListThumbnailShape
import com.example.ui.components.SmartPlaylistSheetContent
import com.example.ui.icons.CadenceIcons
import com.example.ui.player.DiscoverUiState
import com.example.ui.player.SmartPlaylistUiState
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val IdleSmartMixFlow: StateFlow<SmartPlaylistUiState> = MutableStateFlow(SmartPlaylistUiState())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onSelectTrackFromSection: (Track, StableList<Track>) -> Unit,
    onPlaySection: (StableList<Track>) -> Unit,
    onRefreshDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onSaveMixAsPlaylist: (String, List<String>) -> Unit,
    smartPlaylistFlow: StateFlow<SmartPlaylistUiState> = IdleSmartMixFlow,
    currentTrackTitle: String = "",
    onGenerateSmartPlaylist: (String?, Boolean) -> Unit = { _, _ -> },
    onUpdateTuneConfig: (DiscoverTuneConfig) -> Unit = {},
    onResetTuneConfig: () -> Unit = {},
    onGenerateTunedSmartPlaylist: ((String?, Boolean, String?, Float) -> Unit)? = null,
    onOpenSearch: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    var showSmartMixSheet by remember { mutableStateOf(false) }
    var showInsightsSheet by remember { mutableStateOf(false) }

    val triggerTunedGeneration = remember(onGenerateTunedSmartPlaylist, onGenerateSmartPlaylist, onUpdateTuneConfig) {
        { mood: String?, seed: Boolean, energy: String?, balance: Float ->
            if (onGenerateTunedSmartPlaylist != null) {
                onGenerateTunedSmartPlaylist(mood, seed, energy, balance)
            } else {
                onUpdateTuneConfig(DiscoverTuneConfig(mood, energy, balance))
                onGenerateSmartPlaylist(mood, seed)
            }
        }
    }

    val onOpenSmartMixTuner = remember(triggerTunedGeneration, uiState.tuneConfig) {
        {
            val cfg = uiState.tuneConfig
            triggerTunedGeneration(cfg.selectedMood, false, cfg.energyFilter, cfg.discoveryBalance)
            showSmartMixSheet = true
        }
    }

    val bg = MaterialTheme.colorScheme.background
    val cardSurface = MaterialTheme.colorScheme.surfaceContainerLow.opaqueOver(bg, 0.94f)
    val activeTrackSurface = MaterialTheme.colorScheme.primaryContainer.opaqueOver(bg, 0.80f)

    if (!uiState.isInitialLibraryLoaded) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("discover_loading_placeholder")
        )
        return
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("discover_root"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("discover_list"),
            contentPadding = PaddingValues(
                top = statusBarTop + 8.dp,
                bottom = 180.dp + navBarBottom
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item(key = "discover_header", contentType = "discover_header") {
                DiscoverEditorialHeader(
                    onOpenSearch = onOpenSearch,
                    onOpenSettings = onOpenSettings
                )
            }

            if (uiState.errorMessage != null && uiState.sections.isEmpty()) {
                item(key = "discover_error", contentType = "discover_error") {
                    DiscoverErrorCard(
                        message = uiState.errorMessage,
                        onRetry = onRefreshDiscover,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else if (uiState.sections.isEmpty()) {
                item(key = "discover_empty", contentType = "discover_empty") {
                    DiscoverEmptyCard(
                        onAddFolder = onAddFolder,
                        onImportFiles = onImportFiles,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else {
                val activeRoutineMixes = uiState.routineMixes.filter { it.tracks.isNotEmpty() }
                val activeMoodPlaylists = uiState.moodPlaylists.filter { it.tracks.isNotEmpty() }
                val madeForYouSection = uiState.sections.firstOrNull { it.category == DiscoverCategory.MADE_FOR_YOU }
                val smartMixSection = uiState.sections.firstOrNull { it.category == DiscoverCategory.SMART_MIX }
                val secondarySections = uiState.sections.filter {
                    it.category != DiscoverCategory.MADE_FOR_YOU && it.category != DiscoverCategory.SMART_MIX
                }

                // Section 1: For This Moment (Context-Aware Routine Mixes)
                if (activeRoutineMixes.isNotEmpty()) {
                    item(key = "discover_routine_mixes", contentType = "discover_routine_mixes") {
                        DiscoverForThisMomentShelf(
                            routineMixes = activeRoutineMixes,
                            artworkShape = artworkShape,
                            onPlayRoutineMix = { mix -> onPlaySection(mix.tracks) },
                            onInspectInTuneMix = { mix ->
                                val cfg = mix.tuneConfig
                                triggerTunedGeneration(
                                    cfg.selectedMood,
                                    false,
                                    cfg.energyFilter,
                                    cfg.discoveryBalance
                                )
                                showSmartMixSheet = true
                            }
                        )
                    }
                }

                // Section 2: Mood Playlists (132.dp expressive tonal mood tiles)
                if (activeMoodPlaylists.isNotEmpty()) {
                    item(key = "discover_mood_playlists", contentType = "discover_mood_playlists") {
                        DiscoverMoodPlaylistsEditorialShelf(
                            playlists = activeMoodPlaylists,
                            onPlayMoodPlaylist = onPlaySection,
                            onSeeAllMoods = onOpenSmartMixTuner
                        )
                    }
                }

                // Section 3: Made for you (Featured Daily Mix card)
                if (madeForYouSection != null) {
                    item(key = "section_made_for_you", contentType = "discover_hero_mix") {
                        DiscoverMadeForYouSection(
                            section = madeForYouSection,
                            topArtist = uiState.insights.topArtist,
                            tuneConfig = uiState.tuneConfig,
                            artworkShape = artworkShape,
                            onPlayAll = { onPlaySection(madeForYouSection.tracks) },
                            onTuneMix = onOpenSmartMixTuner,
                            onResetTune = onResetTuneConfig,
                            onSavePlaylist = {
                                onSaveMixAsPlaylist(madeForYouSection.title, madeForYouSection.tracks.map { it.id })
                            },
                            onRefreshMix = onRefreshDiscover
                        )
                    }
                }

                // Section 4: Smart Mix (Wide compact mix cards with 3-tag mood line)
                if (smartMixSection != null) {
                    item(key = "section_smart_mix", contentType = "discover_smart_mix") {
                        DiscoverSmartMixEditorialSection(
                            section = smartMixSection,
                            moodPlaylists = activeMoodPlaylists,
                            routineMixes = activeRoutineMixes,
                            artworkShape = artworkShape,
                            onPlayAll = { onPlaySection(smartMixSection.tracks) },
                            onPlayMixCard = onPlaySection,
                            onTuneSmartMix = onOpenSmartMixTuner
                        )
                    }
                }

                // Section 5: Listening Insights (2×2 Real-Data Bento Grid)
                if (uiState.insights.totalTrackCount > 0) {
                    item(key = "discover_listening_insights", contentType = "discover_insights") {
                        DiscoverListeningInsightsSection(
                            insights = uiState.insights,
                            onExpandInsights = { showInsightsSheet = true }
                        )
                    }
                }

                // Additional personalized discovery shelves (Forgotten Gems, Rediscover, Similar Vibe, Explore)
                itemsIndexed(
                    items = secondarySections,
                    key = { index, section -> "section_${index}_${section.category.id}" },
                    contentType = { _, _ -> "discover_carousel" }
                ) { _, section ->
                    val onPlayAll = remember(section.tracks, onPlaySection) {
                        { onPlaySection(section.tracks) }
                    }
                    val onSaveSection = remember(section.title, section.tracks, onSaveMixAsPlaylist) {
                        { onSaveMixAsPlaylist(section.title, section.tracks.map { it.id }) }
                    }
                    val onTrackClick = remember(section.tracks, onSelectTrackFromSection) {
                        { track: Track -> onSelectTrackFromSection(track, section.tracks) }
                    }
                    val sectionIcon = when (section.category) {
                        DiscoverCategory.FORGOTTEN_FAVORITES -> Icons.Rounded.Favorite
                        DiscoverCategory.REDISCOVER -> CadenceIcons.History
                        DiscoverCategory.CONTINUE_THE_VIBE -> CadenceIcons.Waves
                        DiscoverCategory.EXPLORE_YOUR_LIBRARY -> CadenceIcons.Explore
                        else -> CadenceIcons.AutoAwesome
                    }
                    DiscoverCarouselSection(
                        section = section,
                        icon = sectionIcon,
                        containerColor = Color.Transparent,
                        cardColor = cardSurface,
                        activeCardColor = activeTrackSurface,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        artworkShape = artworkShape,
                        onPlayAll = onPlayAll,
                        onSavePlaylist = onSaveSection,
                        onTrackClick = onTrackClick
                    )
                }
            }
        }
    }

    if (showInsightsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInsightsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            ListeningInsightsDetailSheetContent(insights = uiState.insights)
        }
    }

    if (showSmartMixSheet) {
        val smartState by smartPlaylistFlow.collectAsStateWithLifecycle()
        val trackLookup = remember(uiState.allTracks) {
            uiState.allTracks.associateBy { it.id }.toStableMap()
        }
        val activeSeedTrack = remember(uiState.allTracks, currentTrackId) {
            uiState.allTracks.firstOrNull { it.id == currentTrackId }
        }
        ModalBottomSheet(
            onDismissRequest = { showSmartMixSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) }
        ) {
            SmartPlaylistSheetContent(
                output = smartState.output,
                isGenerating = smartState.isGenerating,
                selectedMood = smartState.selectedMood,
                selectedEnergy = smartState.energyFilter,
                discoveryBalance = smartState.discoveryBalance,
                seedFromCurrent = smartState.seedFromCurrent,
                currentTrackTitle = currentTrackTitle.ifBlank {
                    activeSeedTrack?.title ?: "Current Track"
                },
                currentTrackArtist = activeSeedTrack?.artist.orEmpty(),
                trackLookup = trackLookup,
                artworkShape = artworkShape,
                onSelectMood = { mood ->
                    triggerTunedGeneration(
                        mood,
                        smartState.seedFromCurrent,
                        null,
                        smartState.discoveryBalance
                    )
                },
                onChangeDiscoveryBalance = { balance ->
                    triggerTunedGeneration(
                        smartState.selectedMood,
                        smartState.seedFromCurrent,
                        null,
                        balance
                    )
                },
                onResetTune = {
                    onResetTuneConfig()
                    triggerTunedGeneration(null, true, null, 0.5f)
                },
                onToggleSeedFromCurrent = { seed ->
                    triggerTunedGeneration(
                        smartState.selectedMood,
                        seed,
                        null,
                        smartState.discoveryBalance
                    )
                },
                onRegenerate = {
                    triggerTunedGeneration(
                        smartState.selectedMood,
                        smartState.seedFromCurrent,
                        null,
                        smartState.discoveryBalance
                    )
                },
                onPlaySmartPlaylist = { tracks ->
                    showSmartMixSheet = false
                    if (tracks.isNotEmpty()) {
                        onPlaySection(StableList(tracks))
                    }
                },
                onSaveSmartPlaylist = { name, ids ->
                    showSmartMixSheet = false
                    onSaveMixAsPlaylist(name, ids)
                }
            )
        }
    }
}

@Composable
private fun DiscoverSectionHeader(
    section: DiscoverSection,
    icon: ImageVector,
    onPlayAll: () -> Unit,
    onSavePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onSavePlaylist,
                    enabled = section.tracks.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("discover_save_${section.category.id}")
                ) {
                    Icon(
                        imageVector = CadenceIcons.PlaylistAdd,
                        contentDescription = stringResource(R.string.discover_save_mix),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .height(34.dp)
                        .clip(CircleShape)
                        .clickable(enabled = section.tracks.isNotEmpty(), onClick = onPlayAll)
                        .testTag("discover_play_all_${section.category.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.action_play_all),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (section.badgeText.isNotBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text(
                        text = section.badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = section.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DiscoverCarouselSection(
    section: DiscoverSection,
    icon: ImageVector,
    containerColor: Color,
    cardColor: Color,
    activeCardColor: Color,
    currentTrackId: String?,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onPlayAll: () -> Unit,
    onSavePlaylist: () -> Unit,
    onTrackClick: (Track) -> Unit
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DiscoverSectionHeader(
                section = section,
                icon = icon,
                onPlayAll = onPlayAll,
                onSavePlaylist = onSavePlaylist,
                modifier = Modifier.padding(horizontal = if (containerColor == Color.Transparent) 20.dp else 16.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(
                    horizontal = if (containerColor == Color.Transparent) 20.dp else 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(
                    items = section.tracks,
                    key = { index, track -> "carousel_${section.category.id}_${index}_${track.id}" },
                    contentType = { _, _ -> "discover_song_card" }
                ) { _, track ->
                    DiscoverSongCard(
                        track = track,
                        cue = section.trackCues[track.id].orEmpty(),
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        artworkShape = artworkShape,
                        cardColor = if (track.id == currentTrackId) activeCardColor else cardColor,
                        onClick = onTrackClick,
                        testTag = "discover_card_${section.category.id}_${track.id}"
                    )
                }
            }
        }
    }

    if (containerColor != Color.Transparent) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = containerColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("discover_section_${section.category.id}")
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp)) {
                content()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("discover_section_${section.category.id}")
        ) {
            content()
        }
    }
}

@Composable
private fun DiscoverSongCard(
    track: Track,
    cue: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    cardColor: Color,
    onClick: (Track) -> Unit,
    testTag: String
) {
    val clickAction = remember(track, onClick) { { onClick(track) } }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardColor,
        modifier = Modifier
            .width(152.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = clickAction)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
                ExpressiveArtworkFrame(
                    track = track,
                    artworkShape = artworkShape,
                    isPlaying = isCurrent && isPlaying,
                    animateScale = false,
                    decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                    shapeOverride = FastListThumbnailShape,
                    showHairlineBorder = true,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = CircleShape,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCurrent) {
                            AudioPulseBars(
                                isPlaying = isPlaying,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                barHeight = 12.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.action_play),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = track.title.ifBlank { "Untitled Track" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (cue.isNotBlank()) {
                    Text(
                        text = cue,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverEmptyCard(
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag("discover_empty_card")
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
                        imageVector = CadenceIcons.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.discover_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.discover_empty_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAddFolder,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("discover_empty_scan_folder_button")
            ) {
                Icon(CadenceIcons.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_scan_local_folders))
            }
            FilledTonalButton(
                onClick = onImportFiles,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("discover_empty_import_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_import_files))
            }
        }
    }
}

@Composable
private fun DiscoverErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag("discover_error_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.discover_error_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = message.ifBlank { stringResource(R.string.discover_error_fallback) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onRetry,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("discover_retry_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

