package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AudioFocusBehavior
import com.example.data.AudioSettings
import com.example.data.SavedFolderItem
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.data.Track
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import com.example.ui.theme.resolveCadenceColorScheme

private val ShortClipThresholdsSec = listOf(0, 15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themePrefs: ThemePreferences,
    audioSettings: AudioSettings,
    savedFolders: StableList<SavedFolderItem>,
    minTrackDurationSec: Int,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSelectPalette: (ExpressivePalette) -> Unit,
    onSelectAppearance: (AppearanceMode) -> Unit,
    onSelectArtworkShape: (ExpressiveArtworkShape) -> Unit,
    onToggleWavyScrubber: (Boolean) -> Unit,
    onToggleAmbientAura: (Boolean) -> Unit,
    onTogglePerformanceMode: (Boolean) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescanFolders: () -> Unit,
    onSelectMinTrackDurationSec: (Int) -> Unit,
    onToggleGaplessPlayback: (Boolean) -> Unit,
    onChangeCrossfadeSec: (Int) -> Unit,
    onTogglePauseOnHeadphoneDisconnect: (Boolean) -> Unit,
    onSelectAudioFocusBehavior: (AudioFocusBehavior) -> Unit,
    onToggleAcousticEnhance: (Boolean) -> Unit = {},
    scanAllFolders: Boolean = false,
    onToggleScanAllFolders: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAppearanceScreen by rememberSaveable { mutableStateOf(false) }
    var showAboutScreen by rememberSaveable { mutableStateOf(false) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCrossfadeSheet by remember { mutableStateOf(false) }
    var showAudioFocusSheet by remember { mutableStateOf(false) }
    var showFoldersSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val versionName = remember(context) { resolveAppVersionName(context) }
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (themePrefs.appearanceMode) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val systemPreviewScheme = remember(context, effectiveDark) {
        resolveCadenceColorScheme(
            palette = ExpressivePalette.DYNAMIC,
            darkTheme = effectiveDark,
            context = context
        )
    }

    if (showAppearanceScreen) {
        AppearanceThemeSubScreen(
            themePrefs = themePrefs,
            effectiveDark = effectiveDark,
            systemPreviewScheme = systemPreviewScheme,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onClose = { showAppearanceScreen = false },
            onSelectPalette = onSelectPalette,
            onSelectAppearance = onSelectAppearance,
            onSelectArtworkShape = onSelectArtworkShape,
            modifier = modifier
        )
        return
    }

    if (showAboutScreen) {
        AboutSubScreen(
            versionName = versionName,
            onClose = { showAboutScreen = false },
            modifier = modifier
        )
        return
    }

    BackHandler {
        if (isSearchOpen) {
            isSearchOpen = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val cardColor = colorScheme.surfaceContainerLow.opaqueOver(bg, 0.96f)
    val dividerColor = colorScheme.outlineVariant.opaqueOver(cardColor, 0.48f)
    val topBarRestColor = colorScheme.surface.opaqueOver(bg, 1.0f)
    val topBarScrolledColor = colorScheme.surfaceContainer.opaqueOver(bg, 1.0f)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val normalizedQuery = searchQuery.trim().lowercase()
    fun matches(vararg tokens: String): Boolean =
        normalizedQuery.isEmpty() || tokens.any { it.lowercase().contains(normalizedQuery) }

    // Search visibility flags per item & section
    val showAppearanceCard = matches(
        "appearance", "theme", "color", "palette", "dark", "light", "auto", "shape", "artwork",
        "personalize", themePrefs.palette.displayName, themePrefs.artworkShape.displayName
    )

    val showGapless = matches("gapless", "playback", "seamless", "album", "audio")
    val showAcoustic = matches("acoustic", "enhance", "eq", "warmth", "vocal", "audio")
    val showCrossfade = matches("crossfade", "duration", "blend", "transition", "fade", "audio")
    val showHeadphone = matches("headphone", "disconnect", "bluetooth", "pause", "unplug", "audio")
    val showAudioFocus = matches(
        "audio focus", "ducking", "duck", "mix", "pause",
        audioSettings.audioFocusBehavior.label
    )
    val showAudioCard = showGapless || showAcoustic || showCrossfade || showHeadphone || showAudioFocus

    val showScannedFolders = matches("scanned", "music", "folders", "directory", "storage", "library")
    val showAllFolders = matches("all folders", "device", "scan", "library", "storage")
    val showFolderActions = matches("add music", "add folder", "rescan", "library", "storage")
    val showShortClips = matches("filter", "short", "audio clips", "ringtones", "duration", "library")
    val showLibraryCard = showScannedFolders || showAllFolders || showFolderActions || showShortClips

    val showReduceAnim = matches("reduce animations", "performance", "smoother", "low ram", "interface")
    val showWavy = matches("wavy", "scrubber", "waveform", "slider", "interface")
    val showAmbient = matches("ambient", "aura", "backdrop", "halo", "glow", "interface")
    val showInterfaceCard = showReduceAnim || showWavy || showAmbient

    val showAboutRow = matches(
        "about", "version", versionName, "license", "licenses", "github",
        "cadence", "privacy", "offline", "source", "credits", "info", "media3"
    )

    val hasAnyResults = showAppearanceCard || showAudioCard || showLibraryCard ||
        showInterfaceCard || showAboutRow

    val modeDisplayLabel = when (themePrefs.appearanceMode) {
        AppearanceMode.SYSTEM -> stringResource(R.string.appearance_system)
        AppearanceMode.LIGHT -> stringResource(R.string.appearance_light)
        AppearanceMode.DARK -> stringResource(R.string.appearance_dark)
    }
    val modeIcon = when (themePrefs.appearanceMode) {
        AppearanceMode.SYSTEM -> CadenceIcons.BrightnessAuto
        AppearanceMode.LIGHT -> CadenceIcons.LightMode
        AppearanceMode.DARK -> CadenceIcons.DarkMode
    }
    val activePaletteSwatch = if (themePrefs.palette == ExpressivePalette.DYNAMIC) {
        systemPreviewScheme.primary
    } else {
        themePrefs.palette.previewPrimary
    }
    val folderSummary = remember(savedFolders) {
        when (savedFolders.size) {
            0 -> "No folders added"
            1 -> "1 folder • ${savedFolders.first().displayName}"
            else -> "${savedFolders.size} folders • ${savedFolders.first().displayName} +${savedFolders.size - 1}"
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchOpen) {
                        SettingsSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .testTag("settings_header_title")
                        )
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = {
                            if (isSearchOpen) {
                                isSearchOpen = false
                                searchQuery = ""
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(48.dp)
                            .testTag("settings_back_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colorScheme.surfaceContainerHigh,
                            contentColor = colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            if (isSearchOpen) {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                } else {
                                    isSearchOpen = false
                                }
                            } else {
                                isSearchOpen = true
                            }
                        },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(48.dp)
                            .testTag("settings_search_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isSearchOpen) {
                                colorScheme.primaryContainer
                            } else {
                                colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (isSearchOpen) {
                                colorScheme.onPrimaryContainer
                            } else {
                                colorScheme.onSurface
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (isSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (isSearchOpen) {
                                stringResource(R.string.action_clear_search)
                            } else {
                                stringResource(R.string.action_search)
                            }
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarRestColor,
                    scrolledContainerColor = topBarScrolledColor,
                    navigationIconContentColor = colorScheme.onSurface,
                    titleContentColor = colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior,
                modifier = Modifier.testTag("settings_top_bar")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("settings_list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = navBarBottom + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!hasAnyResults) {
                    item(key = "search_empty", contentType = "search_empty") {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = cardColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_search_empty")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = stringResource(R.string.settings_search_empty, searchQuery),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                FilledTonalButton(
                                    onClick = { searchQuery = "" },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("settings_search_clear")
                                ) {
                                    Text(text = stringResource(R.string.action_clear_search_label))
                                }
                            }
                        }
                    }
                }

                // --- 1. Appearance & Theme (Summary Bento Card with `>`) ---
                if (showAppearanceCard) {
                    item(key = "bento_appearance", contentType = "appearance_summary") {
                        AppearanceSummaryBentoCard(
                            paletteName = themePrefs.palette.displayName,
                            paletteSwatch = activePaletteSwatch,
                            modeLabel = modeDisplayLabel,
                            modeIcon = modeIcon,
                            artworkShape = themePrefs.artworkShape,
                            cardColor = cardColor,
                            onClick = { showAppearanceScreen = true }
                        )
                    }
                }

                // --- 2. Audio & Playback (Bento Card) ---
                if (showAudioCard) {
                    item(key = "bento_audio", contentType = "settings_card") {
                        BentoCategorySection(
                            title = stringResource(R.string.settings_section_playback),
                            icon = CadenceIcons.GraphicEq,
                            headerTag = "settings_category_audio",
                            cardColor = cardColor
                        ) {
                            var renderedBefore = false

                            if (showGapless) {
                                BentoToggleRow(
                                    icon = CadenceIcons.QueueMusic,
                                    title = stringResource(R.string.settings_gapless_title),
                                    description = stringResource(R.string.settings_gapless_desc),
                                    checked = audioSettings.gaplessPlaybackEnabled,
                                    onCheckedChange = onToggleGaplessPlayback,
                                    testTag = "toggle_gapless"
                                )
                                renderedBefore = true
                            }

                            if (showAcoustic) {
                                if (renderedBefore) SettingsDivider(dividerColor)
                                BentoToggleRow(
                                    icon = CadenceIcons.GraphicEq,
                                    title = stringResource(R.string.settings_acoustic_enhance_title),
                                    description = stringResource(R.string.settings_acoustic_enhance_desc),
                                    checked = audioSettings.acousticEnhanceEnabled,
                                    onCheckedChange = onToggleAcousticEnhance,
                                    testTag = "toggle_acoustic_enhance"
                                )
                                renderedBefore = true
                            }

                            if (showCrossfade) {
                                if (renderedBefore) SettingsDivider(dividerColor)
                                CrossfadeBentoRow(
                                    crossfadeSec = audioSettings.crossfadeDurationSec,
                                    onClick = { showCrossfadeSheet = true }
                                )
                                renderedBefore = true
                            }

                            if (showHeadphone) {
                                if (renderedBefore) SettingsDivider(dividerColor)
                                BentoToggleRow(
                                    icon = CadenceIcons.Headphones,
                                    title = stringResource(R.string.settings_headphone_title),
                                    description = stringResource(R.string.settings_headphone_desc),
                                    checked = audioSettings.pauseOnHeadphoneDisconnect,
                                    onCheckedChange = onTogglePauseOnHeadphoneDisconnect,
                                    testTag = "toggle_headphone_pause"
                                )
                                renderedBefore = true
                            }

                            if (showAudioFocus) {
                                if (renderedBefore) SettingsDivider(dividerColor)
                                BentoNavigationRow(
                                    icon = CadenceIcons.Speaker,
                                    title = stringResource(R.string.settings_audio_focus_title),
                                    subtitle = audioSettings.audioFocusBehavior.label,
                                    onClick = { showAudioFocusSheet = true },
                                    testTag = "audio_focus_row"
                                )
                            }
                        }
                    }
                }

                // --- 3. Library & Storage (Bento Card) ---
                if (showLibraryCard) {
                    item(key = "bento_library", contentType = "settings_card") {
                        BentoCategorySection(
                            title = stringResource(R.string.settings_section_library),
                            icon = CadenceIcons.FolderOpen,
                            headerTag = "settings_category_library",
                            cardColor = cardColor
                        ) {
                            var renderedBefore = false

                            if (showScannedFolders) {
                                BentoNavigationRow(
                                    icon = CadenceIcons.FolderOpen,
                                    title = stringResource(R.string.settings_folders_title),
                                    subtitle = folderSummary,
                                    onClick = { showFoldersSheet = true },
                                    testTag = "scanned_folders_row"
                                )
                                renderedBefore = true
                            }

                            if (showAllFolders) {
                                if (renderedBefore) SettingsDivider(dividerColor)
                                BentoToggleRow(
                                    icon = CadenceIcons.LibraryMusic,
                                    title = stringResource(R.string.settings_all_folders_title),
                                    description = stringResource(R.string.settings_all_folders_desc),
                                    checked = scanAllFolders,
                                    onCheckedChange = onToggleScanAllFolders,
                                    testTag = "settings_all_folders_switch"
                                )
                                renderedBefore = true
                            }

                            if (showFolderActions) {
                                if (renderedBefore) Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onAddFolder,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("settings_add_folder_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.settings_add_music_short),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = onRescanFolders,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("settings_rescan_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.settings_rescan_short),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                renderedBefore = true
                            }

                            if (showShortClips) {
                                if (renderedBefore) SettingsDivider(dividerColor)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = stringResource(R.string.settings_short_clip_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_short_clip_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ShortClipThresholdsSec.forEach { sec ->
                                            val selected = minTrackDurationSec == sec
                                            val onPickDur = remember(sec, onSelectMinTrackDurationSec) {
                                                { onSelectMinTrackDurationSec(sec) }
                                            }
                                            ShortClipPill(
                                                label = if (sec == 0) "Off" else "< ${sec}s",
                                                selected = selected,
                                                onClick = onPickDur,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("min_duration_$sec")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 4. Interface & Performance (Bento Card) ---
                if (showInterfaceCard) {
                    item(key = "bento_interface", contentType = "settings_card") {
                        BentoCategorySection(
                            title = stringResource(R.string.settings_section_performance),
                            icon = CadenceIcons.Speed,
                            headerTag = "settings_category_interface",
                            cardColor = cardColor
                        ) {
                            var renderedBefore = false

                            if (showReduceAnim) {
                                BentoToggleRow(
                                    icon = CadenceIcons.Speed,
                                    title = stringResource(R.string.settings_performance_mode_title),
                                    description = if (themePrefs.isAutoPerformanceMode) {
                                        "Auto-enabled for this low-RAM device. ${stringResource(R.string.settings_performance_mode_desc)}"
                                    } else {
                                        stringResource(R.string.settings_performance_mode_desc)
                                    },
                                    checked = themePrefs.performanceModeEnabled,
                                    onCheckedChange = onTogglePerformanceMode,
                                    badgeText = if (themePrefs.isAutoPerformanceMode) "AUTO" else null,
                                    testTag = "toggle_performance_mode"
                                )
                                renderedBefore = true
                            }

                            if (showWavy) {
                                if (renderedBefore) SettingsDivider(dividerColor)
                                BentoToggleRow(
                                    icon = CadenceIcons.Waves,
                                    title = stringResource(R.string.themes_wavy_scrubber_title),
                                    description = stringResource(R.string.themes_wavy_scrubber_desc),
                                    checked = themePrefs.effectiveWavyScrubber,
                                    enabled = !themePrefs.performanceModeEnabled,
                                    onCheckedChange = onToggleWavyScrubber,
                                    testTag = "toggle_wavy_scrubber"
                                )
                                renderedBefore = true
                            }

                            if (showAmbient) {
                                if (renderedBefore) SettingsDivider(dividerColor)
                                BentoToggleRow(
                                    icon = CadenceIcons.AutoAwesome,
                                    title = stringResource(R.string.themes_album_ambient_title),
                                    description = stringResource(R.string.themes_album_ambient_desc),
                                    checked = themePrefs.effectiveAmbientAura,
                                    enabled = !themePrefs.performanceModeEnabled,
                                    onCheckedChange = onToggleAmbientAura,
                                    testTag = "toggle_ambient_aura"
                                )
                            }
                        }
                    }
                }

                // --- 5. About (Clean Borderless Entry Row) ---
                if (showAboutRow) {
                    item(key = "settings_about_entry", contentType = "about_entry") {
                        CardlessAboutEntryRow(
                            versionName = versionName,
                            dividerColor = colorScheme.outlineVariant.opaqueOver(bg, 0.30f),
                            onClick = { showAboutScreen = true }
                        )
                    }
                }
            }
        }
    }

    if (showCrossfadeSheet) {
        CrossfadePickerSheet(
            currentSec = audioSettings.crossfadeDurationSec,
            onSelectSec = onChangeCrossfadeSec,
            onDismiss = { showCrossfadeSheet = false }
        )
    }

    if (showAudioFocusSheet) {
        AudioFocusPickerSheet(
            selectedBehavior = audioSettings.audioFocusBehavior,
            onSelectBehavior = onSelectAudioFocusBehavior,
            onDismiss = { showAudioFocusSheet = false }
        )
    }

    if (showFoldersSheet) {
        ScannedFoldersManagerSheet(
            savedFolders = savedFolders,
            onAddFolder = onAddFolder,
            onRemoveFolder = onRemoveFolder,
            onDismiss = { showFoldersSheet = false }
        )
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colorScheme.surfaceContainerHigh,
        modifier = modifier.height(44.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = colorScheme.onSurface
                ),
                cursorBrush = SolidColor(colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_search_input")
            )
        }
    }
}

@Composable
private fun AppearanceSummaryBentoCard(
    paletteName: String,
    paletteSwatch: Color,
    modeLabel: String,
    modeIcon: ImageVector,
    artworkShape: ExpressiveArtworkShape,
    cardColor: Color,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val pillBg = colorScheme.surfaceContainerHigh.opaqueOver(cardColor, 0.92f)
    val composeShape = remember(artworkShape) { artworkShape.toComposeShape() }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
        border = BorderStroke(
            width = 1.dp,
            color = colorScheme.outlineVariant.opaqueOver(cardColor, 0.40f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .testTag("settings_appearance_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_category_appearance"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = CadenceIcons.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_appearance_theme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.settings_appearance_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 3-column summary pill bar: active palette, theme mode, artwork shape
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStatPill(
                    label = paletteName,
                    pillBg = pillBg,
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(paletteSwatch)
                        )
                    },
                    modifier = Modifier
                        .weight(1.25f)
                        .testTag("appearance_summary_palette")
                )
                SummaryStatPill(
                    label = modeLabel,
                    pillBg = pillBg,
                    leading = {
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("appearance_summary_mode")
                )
                SummaryStatPill(
                    label = artworkShape.displayName,
                    pillBg = pillBg,
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(composeShape)
                                .background(colorScheme.primary)
                        )
                    },
                    modifier = Modifier
                        .weight(0.95f)
                        .testTag("appearance_summary_shape")
                )
            }
        }
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    pillBg: Color,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = pillBg,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            leading()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BentoCategorySection(
    title: String,
    icon: ImageVector,
    headerTag: String,
    cardColor: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag(headerTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = cardColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun RowIconBadge(
    icon: ImageVector,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (enabled) {
            colorScheme.surfaceContainerHigh
        } else {
            colorScheme.surfaceContainerHighest
        },
        contentColor = if (enabled) {
            colorScheme.primary
        } else {
            colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun BentoToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true,
    badgeText: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIconBadge(icon = icon, enabled = enabled)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (badgeText != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun CrossfadeBentoRow(
    crossfadeSec: Int,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag("crossfade_selector_pill"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIconBadge(icon = CadenceIcons.Tune)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_crossfade_title),
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_crossfade_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = CircleShape,
            color = if (crossfadeSec > 0) {
                colorScheme.primaryContainer
            } else {
                colorScheme.secondaryContainer
            }
        ) {
            Text(
                text = if (crossfadeSec == 0) "Off" else "${crossfadeSec}s",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (crossfadeSec > 0) {
                    colorScheme.onPrimaryContainer
                } else {
                    colorScheme.onSecondaryContainer
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun BentoNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIconBadge(icon = icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CardlessAboutEntryRow(
    versionName: String,
    dividerColor: Color,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(dividerColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 14.dp)
                .testTag("settings_about_row"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.secondaryContainer,
                contentColor = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = CadenceIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_section_about),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_about_title")
                )
                Text(
                    text = stringResource(R.string.settings_about_row_subtitle, versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_about_subtitle")
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ShortClipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (selected) {
            colorScheme.primaryContainer
        } else {
            colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            colorScheme.onPrimaryContainer
        } else {
            colorScheme.onSurfaceVariant
        },
        border = if (selected) {
            BorderStroke(1.5.dp, colorScheme.primary)
        } else {
            null
        },
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}
