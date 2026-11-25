package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.AudioFocusBehavior
import com.example.data.AudioSettings
import com.example.data.SavedFolderItem
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.data.Track
import com.example.ui.components.AudioPulseBars
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import com.example.ui.theme.resolveCadenceColorScheme
import kotlin.math.roundToInt

private val ShortClipThresholdsSec = listOf(0, 15, 30, 60)

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
    userDisplayName: String = "",
    onUpdateDisplayName: (String) -> Unit = {},
    scanAllFolders: Boolean = false,
    onToggleScanAllFolders: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showDisplayNameDialog by remember { mutableStateOf(false) }

    val bg = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow.opaqueOver(bg, 0.96f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.opaqueOver(cardColor, 0.50f)
    val paletteRows = remember { ExpressivePalette.entries.chunked(2) }
    val context = LocalContext.current
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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("settings_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 48.dp + navBarBottom
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "settings_header", contentType = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_back_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_header_title")
                    )
                }
            }

            // --- Profile / Display Name ---

            item(key = "section_profile", contentType = "profile_row") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cardColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { showDisplayNameDialog = true }
                        .testTag("settings_display_name_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
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
                                text = stringResource(R.string.settings_display_name_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = userDisplayName.ifBlank {
                                    stringResource(R.string.settings_display_name_empty)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (userDisplayName.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("settings_display_name_value")
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.settings_display_name_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // --- Theme Studio (Preserved Cadence Expressive Controls) ---

            item(key = "live_preview", contentType = "preview_card") {
                ExpressiveLivePreviewCard(
                    themePrefs = themePrefs,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying
                )
            }

            item(key = "appearance", contentType = "appearance_selector") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.themes_section_appearance))
                    AppearanceSegmentedRow(
                        selected = themePrefs.appearanceMode,
                        onSelect = onSelectAppearance
                    )
                }
            }

            item(key = "palettes", contentType = "palette_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.themes_section_palette))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        paletteRows.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { palette ->
                                    val onPick = remember(palette, onSelectPalette) {
                                        { onSelectPalette(palette) }
                                    }
                                    val isDynamic = palette == ExpressivePalette.DYNAMIC
                                    PaletteSwatchCard(
                                        palette = palette,
                                        selected = themePrefs.palette == palette,
                                        onClick = onPick,
                                        swatchPrimary = if (isDynamic) systemPreviewScheme.primary else palette.previewPrimary,
                                        swatchTertiary = if (isDynamic) systemPreviewScheme.tertiary else palette.previewTertiary,
                                        swatchContainer = if (isDynamic) systemPreviewScheme.primaryContainer else palette.previewContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "shapes", contentType = "shape_selector") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.themes_section_shape))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExpressiveArtworkShape.entries.forEach { shape ->
                            val onPickShape = remember(shape, onSelectArtworkShape) {
                                { onSelectArtworkShape(shape) }
                            }
                            ArtworkShapeOptionCard(
                                shape = shape,
                                selected = themePrefs.artworkShape == shape,
                                onClick = onPickShape,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // --- Library & Folders ---

            item(key = "section_library", contentType = "settings_card") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.settings_section_library))
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = cardColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_folders_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (savedFolders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_folders_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    savedFolders.forEachIndexed { index, folder ->
                                        val onDeleteThis = remember(folder.treeUri, onRemoveFolder) {
                                            { onRemoveFolder(folder.treeUri) }
                                        }
                                        SavedFolderRow(
                                            folder = folder,
                                            index = index,
                                            onRemove = onDeleteThis
                                        )
                                    }
                                }
                            }

                            ExpressiveToggleRow(
                                title = stringResource(R.string.settings_all_folders_title),
                                description = stringResource(R.string.settings_all_folders_desc),
                                checked = scanAllFolders,
                                onCheckedChange = onToggleScanAllFolders,
                                testTag = "settings_all_folders_switch"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onAddFolder,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("settings_add_folder_button")
                                ) {
                                    Icon(
                                        imageVector = CadenceIcons.CreateNewFolder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.action_add_folder),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                FilledTonalButton(
                                    onClick = onRescanFolders,
                                    shape = CircleShape,
                                    modifier = Modifier
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
                                        text = "Rescan",
                                        maxLines = 1
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(dividerColor)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_short_clip_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_short_clip_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ShortClipThresholdsSec.forEach { sec ->
                                        val selected = minTrackDurationSec == sec
                                        val onPickDur = remember(sec, onSelectMinTrackDurationSec) {
                                            { onSelectMinTrackDurationSec(sec) }
                                        }
                                        FilterChip(
                                            selected = selected,
                                            onClick = onPickDur,
                                            label = {
                                                Text(
                                                    text = if (sec == 0) "Off" else "< ${sec}s",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            },
                                            shape = CircleShape,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.testTag("min_duration_$sec")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Playback & Audio ---

            item(key = "section_playback", contentType = "settings_card") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.settings_section_playback))
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = cardColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            ExpressiveToggleRow(
                                title = stringResource(R.string.settings_gapless_title),
                                description = stringResource(R.string.settings_gapless_desc),
                                checked = audioSettings.gaplessPlaybackEnabled,
                                onCheckedChange = onToggleGaplessPlayback,
                                testTag = "toggle_gapless"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(dividerColor)
                            )

                            ExpressiveToggleRow(
                                title = stringResource(R.string.settings_acoustic_enhance_title),
                                description = stringResource(R.string.settings_acoustic_enhance_desc),
                                checked = audioSettings.acousticEnhanceEnabled,
                                onCheckedChange = onToggleAcousticEnhance,
                                testTag = "toggle_acoustic_enhance"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(dividerColor)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_crossfade_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_crossfade_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = if (audioSettings.crossfadeDurationSec == 0) {
                                                "Off"
                                            } else {
                                                "${audioSettings.crossfadeDurationSec}s"
                                            },
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = MonoFontFamily
                                            ),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Slider(
                                    value = audioSettings.crossfadeDurationSec.toFloat(),
                                    onValueChange = { raw ->
                                        val rounded = raw.roundToInt().coerceIn(0, 10)
                                        if (rounded != audioSettings.crossfadeDurationSec) {
                                            onChangeCrossfadeSec(rounded)
                                        }
                                    },
                                    valueRange = 0f..10f,
                                    steps = 9,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("crossfade_slider")
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(dividerColor)
                            )

                            ExpressiveToggleRow(
                                title = stringResource(R.string.settings_headphone_title),
                                description = stringResource(R.string.settings_headphone_desc),
                                checked = audioSettings.pauseOnHeadphoneDisconnect,
                                onCheckedChange = onTogglePauseOnHeadphoneDisconnect,
                                testTag = "toggle_headphone_pause"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(dividerColor)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_audio_focus_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_audio_focus_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AudioFocusBehavior.entries.forEach { behavior ->
                                    val selected = audioSettings.audioFocusBehavior == behavior
                                    val onSelectFocus = remember(behavior, onSelectAudioFocusBehavior) {
                                        { onSelectAudioFocusBehavior(behavior) }
                                    }
                                    AudioFocusOptionRow(
                                        behavior = behavior,
                                        selected = selected,
                                        onClick = onSelectFocus
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Performance & Interface ---

            item(key = "section_performance", contentType = "settings_card") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.settings_section_performance))
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = cardColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            ExpressiveToggleRow(
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(dividerColor)
                            )
                            ExpressiveToggleRow(
                                title = stringResource(R.string.themes_wavy_scrubber_title),
                                description = stringResource(R.string.themes_wavy_scrubber_desc),
                                checked = themePrefs.effectiveWavyScrubber,
                                enabled = !themePrefs.performanceModeEnabled,
                                onCheckedChange = onToggleWavyScrubber,
                                testTag = "toggle_wavy_scrubber"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(dividerColor)
                            )
                            ExpressiveToggleRow(
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
        }
    }

    if (showDisplayNameDialog) {
        DisplayNameEditDialog(
            initialName = userDisplayName,
            onDismiss = { showDisplayNameDialog = false },
            onSave = { updated ->
                onUpdateDisplayName(updated.trim())
                showDisplayNameDialog = false
            },
            onClear = {
                onUpdateDisplayName("")
                showDisplayNameDialog = false
            }
        )
    }
}

@Composable
private fun DisplayNameEditDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var draftName by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = stringResource(R.string.settings_display_name_dialog_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            OutlinedTextField(
                value = draftName,
                onValueChange = { next ->
                    if (next.length <= 36) {
                        draftName = next
                    }
                },
                label = { Text(stringResource(R.string.onboarding_name_label)) },
                placeholder = { Text(stringResource(R.string.onboarding_name_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSave(draftName) }
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_display_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(draftName) },
                shape = CircleShape,
                modifier = Modifier.testTag("settings_display_name_save")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialName.isNotBlank() || draftName.isNotBlank()) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.testTag("settings_display_name_clear")
                    ) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("settings_display_name_cancel")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .widthIn(max = 400.dp)
            .testTag("settings_display_name_dialog")
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SavedFolderRow(
    folder: SavedFolderItem,
    index: Int,
    onRemove: () -> Unit
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = CadenceIcons.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = folder.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("remove_folder_$index")
            ) {
                Icon(
                    imageVector = CadenceIcons.DeleteOutline,
                    contentDescription = stringResource(R.string.action_remove_folder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AudioFocusOptionRow(
    behavior: AudioFocusBehavior,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerLow
    val rowBg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.opaqueOver(bg, 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = rowBg,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("audio_focus_${behavior.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = behavior.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = behavior.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveLivePreviewCard(
    themePrefs: ThemePreferences,
    currentTrack: Track?,
    isPlaying: Boolean
) {
    val palette = themePrefs.palette
    val shape = themePrefs.artworkShape
    val bg = MaterialTheme.colorScheme.background
    val previewContainer = MaterialTheme.colorScheme.primaryContainer.opaqueOver(bg, 0.72f)
    val chipSurface = MaterialTheme.colorScheme.surface.opaqueOver(previewContainer, 0.80f)

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = previewContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ExpressiveArtworkFrame(
                track = currentTrack,
                artworkShape = shape,
                isPlaying = isPlaying,
                animateScale = false,
                decodeSizePx = 220,
                modifier = Modifier.size(84.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = CadenceIcons.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ACTIVE STYLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = palette.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = chipSurface
                    ) {
                        Text(
                            text = shape.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    AudioPulseBars(
                        isPlaying = isPlaying,
                        tint = MaterialTheme.colorScheme.primary,
                        barHeight = 14.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSegmentedRow(
    selected: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit
) {
    val options = remember {
        listOf(
            Triple(AppearanceMode.SYSTEM, R.string.appearance_system, CadenceIcons.BrightnessAuto),
            Triple(AppearanceMode.LIGHT, R.string.appearance_light, CadenceIcons.LightMode),
            Triple(AppearanceMode.DARK, R.string.appearance_dark, CadenceIcons.DarkMode)
        )
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (mode, labelRes, icon) ->
                val isSelected = selected == mode
                val onPick = remember(mode, onSelect) { { onSelect(mode) } }
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPick)
                        .testTag("appearance_${mode.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatchCard(
    palette: ExpressivePalette,
    selected: Boolean,
    onClick: () -> Unit,
    swatchPrimary: Color = palette.previewPrimary,
    swatchTertiary: Color = palette.previewTertiary,
    swatchContainer: Color = palette.previewContainer,
    modifier: Modifier = Modifier
) {
    val performanceMode = LocalPerformanceMode.current
    val scale = if (performanceMode) {
        1.0f
    } else {
        animateFloatAsState(
            targetValue = if (selected) 1.0f else 0.98f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "swatch_scale"
        ).value
    }

    val bg = MaterialTheme.colorScheme.background
    val cardBg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.opaqueOver(bg, 0.78f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val outlineBorderColor = MaterialTheme.colorScheme.outlineVariant.opaqueOver(bg, 0.50f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, outlineBorderColor)
        },
        modifier = modifier
            .then(if (scale != 1.0f) Modifier.scale(scale) else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("palette_${palette.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(swatchPrimary)
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(swatchTertiary)
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(swatchContainer)
                    )
                }

                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = palette.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = palette.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtworkShapeOptionCard(
    shape: ExpressiveArtworkShape,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composeShape = remember(shape) { shape.toComposeShape() }
    val bg = MaterialTheme.colorScheme.background
    val idleIconTint = MaterialTheme.colorScheme.primary.opaqueOver(
        MaterialTheme.colorScheme.surfaceContainerLow,
        0.55f
    )
    val idleBorder = MaterialTheme.colorScheme.outlineVariant.opaqueOver(bg, 0.45f)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, idleBorder)
        },
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("shape_${shape.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(composeShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else idleIconTint
                    )
            )
            Text(
                text = shape.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpressiveToggleRow(
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
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
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = CadenceIcons.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
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
