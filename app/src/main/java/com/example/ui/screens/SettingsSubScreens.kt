package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AudioFocusBehavior
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
import kotlin.math.roundToInt

private val CrossfadeQuickPresets = listOf(0, 3, 6, 9, 12)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceThemeSubScreen(
    themePrefs: ThemePreferences,
    effectiveDark: Boolean,
    systemPreviewScheme: ColorScheme,
    currentTrack: Track?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onSelectPalette: (ExpressivePalette) -> Unit,
    onSelectAppearance: (AppearanceMode) -> Unit,
    onSelectArtworkShape: (ExpressiveArtworkShape) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val topBarRestColor = colorScheme.surface.opaqueOver(bg, 1.0f)
    val topBarScrolledColor = colorScheme.surfaceContainer.opaqueOver(bg, 1.0f)
    val paletteRows = remember { ExpressivePalette.entries.chunked(2) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_section_appearance_theme),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .testTag("appearance_subscreen_title")
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(48.dp)
                            .testTag("appearance_back_button"),
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
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarRestColor,
                    scrolledContainerColor = topBarScrolledColor,
                    navigationIconContentColor = colorScheme.onSurface,
                    titleContentColor = colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior,
                modifier = Modifier.testTag("appearance_top_bar")
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
                    .testTag("appearance_subscreen_list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = navBarBottom + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "live_preview", contentType = "preview_card") {
                    ExpressiveLivePreviewCard(
                        themePrefs = themePrefs,
                        effectiveDark = effectiveDark,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying
                    )
                }

                item(key = "appearance", contentType = "appearance_selector") {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SubScreenSectionLabel(stringResource(R.string.themes_section_appearance))
                        AppearanceSegmentedRow(
                            selected = themePrefs.appearanceMode,
                            onSelect = onSelectAppearance
                        )
                    }
                }

                item(key = "palettes_label", contentType = "subsection_label") {
                    Box(modifier = Modifier.padding(top = 6.dp)) {
                        SubScreenSectionLabel(stringResource(R.string.themes_section_palette))
                    }
                }

                paletteRows.forEachIndexed { rowIndex, pair ->
                    item(key = "palette_row_$rowIndex", contentType = "palette_row") {
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
                                    swatchSecondary = if (isDynamic) systemPreviewScheme.secondary else palette.previewSecondary,
                                    swatchTertiary = if (isDynamic) systemPreviewScheme.tertiary else palette.previewTertiary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                item(key = "shapes", contentType = "shape_selector") {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SubScreenSectionLabel(stringResource(R.string.themes_section_shape))
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrossfadePickerSheet(
    currentSec: Int,
    onSelectSec: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("crossfade_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_crossfade_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (currentSec == 0) "Off" else "${currentSec}s",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("crossfade_sheet_value")
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("crossfade_sheet_close")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Slider(
                value = currentSec.coerceIn(0, 12).toFloat(),
                onValueChange = { raw ->
                    val rounded = raw.roundToInt().coerceIn(0, 12)
                    if (rounded != currentSec) {
                        onSelectSec(rounded)
                    }
                },
                valueRange = 0f..12f,
                steps = 11,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crossfade_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CrossfadeQuickPresets.forEach { sec ->
                    val selected = currentSec == sec
                    val onPick = remember(sec, onSelectSec) { { onSelectSec(sec) } }
                    Surface(
                        shape = CircleShape,
                        color = if (selected) {
                            colorScheme.primary
                        } else {
                            colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (selected) {
                            colorScheme.onPrimary
                        } else {
                            colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onPick)
                            .testTag("crossfade_preset_$sec")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (sec == 0) "Off" else "${sec}s",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = MonoFontFamily
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioFocusPickerSheet(
    selectedBehavior: AudioFocusBehavior,
    onSelectBehavior: (AudioFocusBehavior) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("audio_focus_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_audio_focus_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_audio_focus_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            AudioFocusBehavior.entries.forEach { behavior ->
                val selected = selectedBehavior == behavior
                val onPick = remember(behavior, onSelectBehavior, onDismiss) {
                    {
                        onSelectBehavior(behavior)
                        onDismiss()
                    }
                }
                AudioFocusOptionRow(
                    behavior = behavior,
                    selected = selected,
                    onClick = onPick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScannedFoldersManagerSheet(
    savedFolders: StableList<SavedFolderItem>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("folders_manager_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_folders_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = if (savedFolders.isEmpty()) {
                            stringResource(R.string.settings_folders_empty)
                        } else {
                            "${savedFolders.size} ${if (savedFolders.size == 1) "folder" else "folders"} indexed locally"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("folders_sheet_close")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            if (savedFolders.isNotEmpty()) {
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

            Button(
                onClick = {
                    onDismiss()
                    onAddFolder()
                },
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sheet_add_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
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
        }
    }
}

@Composable
private fun SubScreenSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
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
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
    effectiveDark: Boolean,
    currentTrack: Track?,
    isPlaying: Boolean
) {
    val palette = themePrefs.palette
    val shape = themePrefs.artworkShape
    val bg = MaterialTheme.colorScheme.background
    val previewContainer = MaterialTheme.colorScheme.primaryContainer.opaqueOver(bg, 0.72f)
    val chipSurface = MaterialTheme.colorScheme.surface.opaqueOver(previewContainer, 0.80f)
    val modeLabel = when (themePrefs.appearanceMode) {
        AppearanceMode.SYSTEM -> if (effectiveDark) "Auto · Dark" else "Auto · Light"
        AppearanceMode.LIGHT -> stringResource(R.string.appearance_light)
        AppearanceMode.DARK -> stringResource(R.string.appearance_dark)
    }

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = previewContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_active_style_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExpressiveArtworkFrame(
                track = currentTrack,
                artworkShape = shape,
                isPlaying = isPlaying,
                animateScale = false,
                decodeSizePx = 220,
                modifier = Modifier.size(80.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = CadenceIcons.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = chipSurface
                    ) {
                        Text(
                            text = shape.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = chipSurface
                    ) {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
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
    swatchSecondary: Color = palette.previewSecondary,
    swatchTertiary: Color = palette.previewTertiary,
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
    val swatchDotBorderColor = MaterialTheme.colorScheme.outlineVariant.opaqueOver(cardBg, 0.35f)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardBg,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, outlineBorderColor)
        },
        modifier = modifier
            .then(if (scale != 1.0f) Modifier.scale(scale) else Modifier)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("palette_${palette.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
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
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(swatchPrimary)
                            .border(0.5.dp, swatchDotBorderColor, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(swatchSecondary)
                            .border(0.5.dp, swatchDotBorderColor, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(swatchTertiary)
                            .border(0.5.dp, swatchDotBorderColor, CircleShape)
                    )
                }

                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
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
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("palette_title_${palette.id}")
                )
                Text(
                    text = palette.tagline,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp,
                        lineHeight = 14.5.sp,
                        letterSpacing = (-0.1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("palette_tagline_${palette.id}")
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
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
