package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SavedFolderItem
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.ExpressivePalette
import com.example.ui.theme.opaqueOver
import com.example.ui.theme.resolveCadenceColorScheme

@Composable
internal fun OnboardingAppearanceStep(
    themePrefs: ThemePreferences,
    onSelectPalette: (ExpressivePalette) -> Unit,
    onSelectAppearance: (AppearanceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (themePrefs.appearanceMode) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val systemPreviewScheme = remember(effectiveDark, context) {
        resolveCadenceColorScheme(
            palette = ExpressivePalette.DYNAMIC,
            darkTheme = effectiveDark,
            context = context
        )
    }
    val paletteRows = remember { ExpressivePalette.entries.chunked(2) }
    val colorScheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_step_2"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "step2_header", contentType = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_step2_headline),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_step2_title")
                )
                Text(
                    text = stringResource(R.string.onboarding_step2_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_step2_subtitle")
                )
            }
        }

        item(key = "step2_appearance_mode", contentType = "appearance_selector") {
            OnboardingAppearanceSegmentedRow(
                selected = themePrefs.appearanceMode,
                onSelect = onSelectAppearance,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        paletteRows.forEachIndexed { rowIndex, pair ->
            item(key = "onboarding_palette_row_$rowIndex", contentType = "palette_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { palette ->
                        val onPick = remember(palette, onSelectPalette) {
                            { onSelectPalette(palette) }
                        }
                        val isDynamic = palette == ExpressivePalette.DYNAMIC
                        OnboardingPaletteCard(
                            palette = palette,
                            selected = themePrefs.palette == palette,
                            swatchPrimary = if (isDynamic) systemPreviewScheme.primary else palette.previewPrimary,
                            swatchSecondary = if (isDynamic) systemPreviewScheme.secondary else palette.previewSecondary,
                            swatchTertiary = if (isDynamic) systemPreviewScheme.tertiary else palette.previewTertiary,
                            onClick = onPick,
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

@Composable
private fun OnboardingAppearanceSegmentedRow(
    selected: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = remember {
        listOf(
            Triple(AppearanceMode.SYSTEM, R.string.appearance_system, CadenceIcons.BrightnessAuto),
            Triple(AppearanceMode.LIGHT, R.string.appearance_light, CadenceIcons.LightMode),
            Triple(AppearanceMode.DARK, R.string.appearance_dark, CadenceIcons.DarkMode)
        )
    }
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val containerColor = colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.94f)

    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .testTag("onboarding_appearance_selector")
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
                    onClick = onPick,
                    shape = CircleShape,
                    color = if (isSelected) colorScheme.primary else containerColor,
                    contentColor = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("onboarding_appearance_${mode.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPaletteCard(
    palette: ExpressivePalette,
    selected: Boolean,
    swatchPrimary: Color,
    swatchSecondary: Color,
    swatchTertiary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val cardBg = if (selected) {
        colorScheme.primaryContainer.opaqueOver(bg, 0.80f)
    } else {
        colorScheme.surfaceContainerLow.opaqueOver(bg, 0.94f)
    }
    val borderColor = if (selected) {
        colorScheme.primary
    } else {
        colorScheme.outlineVariant.opaqueOver(bg, 0.50f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier.testTag("onboarding_palette_${palette.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(swatchPrimary)
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(swatchSecondary)
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(swatchTertiary)
                    )
                }

                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = palette.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_palette_title_${palette.id}")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingLibrarySetupStep(
    isAudioPermissionGranted: Boolean,
    isNotificationPermissionGranted: Boolean,
    showNotificationPermissionRow: Boolean,
    scanAllFolders: Boolean,
    savedFolders: StableList<SavedFolderItem>,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSelectScanAllFolders: (Boolean) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val cardColor = colorScheme.surfaceContainerLow.opaqueOver(bg, 0.94f)
    val borderColor = colorScheme.outlineVariant.opaqueOver(bg, 0.42f)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_step_3"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "step3_header", contentType = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_step3_headline),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_step3_title")
                )
                Text(
                    text = stringResource(R.string.onboarding_step3_subtitle),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        letterSpacing = (-0.1).sp
                    ),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_step3_subtitle")
                )
            }
        }

        // Audio Permission Card
        item(key = "step3_audio_permission", contentType = "audio_permission") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_audio_permission_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = CadenceIcons.AudioFile,
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
                                text = stringResource(R.string.onboarding_audio_perm_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    if (isAudioPermissionGranted) {
                                        R.string.onboarding_audio_perm_desc_granted
                                    } else {
                                        R.string.onboarding_audio_perm_desc_needed
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isAudioPermissionGranted) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {}
                                .testTag("onboarding_audio_granted_badge")
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_badge_granted),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onRequestAudioPermission,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("onboarding_grant_audio_button")
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_cta_grant_access),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Optional Notification Permission Row (API 33+)
        if (showNotificationPermissionRow) {
            item(key = "step3_notification_permission", contentType = "notification_permission") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_notification_permission_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = CadenceIcons.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_notif_perm_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_notif_title")
                            )
                            Text(
                                text = stringResource(R.string.onboarding_notif_perm_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isNotificationPermissionGranted) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("onboarding_notifications_allowed_badge")
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_badge_allowed),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        } else {
                            Surface(
                                onClick = onRequestNotificationPermission,
                                shape = CircleShape,
                                color = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("onboarding_allow_notifications_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_cta_allow),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Folder Selection Card
        item(key = "step3_folder_selection", contentType = "folder_selection") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_folder_selection_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.tertiaryContainer,
                            contentColor = colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = CadenceIcons.FolderOpen,
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
                                text = stringResource(R.string.onboarding_folders_card_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.onboarding_folders_card_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OnboardingFolderModeOption(
                        title = stringResource(R.string.onboarding_folder_mode_all),
                        subtitle = stringResource(R.string.onboarding_folder_mode_all_desc),
                        selected = scanAllFolders,
                        onClick = { onSelectScanAllFolders(true) },
                        testTag = "onboarding_folder_mode_all"
                    )

                    OnboardingFolderModeOption(
                        title = stringResource(R.string.onboarding_folder_mode_specific),
                        subtitle = stringResource(R.string.onboarding_folder_mode_specific_desc),
                        selected = !scanAllFolders,
                        onClick = { onSelectScanAllFolders(false) },
                        testTag = "onboarding_folder_mode_specific"
                    )

                    if (!scanAllFolders) {
                        if (savedFolders.isEmpty()) {
                            Text(
                                text = stringResource(R.string.onboarding_folders_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                savedFolders.forEachIndexed { index, folder ->
                                    val onRemoveThis = remember(folder.treeUri, onRemoveFolder) {
                                        { onRemoveFolder(folder.treeUri) }
                                    }
                                    OnboardingRemovableFolderChip(
                                        name = folder.displayName,
                                        index = index,
                                        onRemove = onRemoveThis
                                    )
                                }
                            }
                        }

                        FilledTonalButton(
                            onClick = onAddFolder,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("onboarding_add_folder_button")
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_cta_add_folder),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
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

@Composable
private fun OnboardingFolderModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val cardBg = colorScheme.surfaceContainerLow
    val optionBg = if (selected) {
        colorScheme.primaryContainer.opaqueOver(cardBg, 0.85f)
    } else {
        colorScheme.surfaceContainerHigh
    }
    val border = if (selected) BorderStroke(1.5.dp, colorScheme.primary) else null

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = optionBg,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingRemovableFolderChip(
    name: String,
    index: Int,
    onRemove: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colorScheme.secondaryContainer,
        contentColor = colorScheme.onSecondaryContainer,
        modifier = Modifier.testTag("onboarding_folder_chip_$index")
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = CadenceIcons.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .testTag("onboarding_remove_folder_$index"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_remove_folder),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
