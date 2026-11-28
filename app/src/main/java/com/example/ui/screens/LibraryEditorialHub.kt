package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LibraryCategory
import com.example.data.LibrarySortOrder
import com.example.data.PlaylistSummary
import com.example.data.StableList
import com.example.data.TrackGroup
import com.example.ui.icons.CadenceIcons
import com.example.ui.player.LibraryDrillDown
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver

// Cached M3 Expressive shapes for zero-allocation recomposition on low-end GPUs
private val BentoCardShape = RoundedCornerShape(22.dp)
private val SearchCollectionPillShape = RoundedCornerShape(18.dp)

@Composable
internal fun LibraryEditorialHeader(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val prefix = stringResource(R.string.library_title_prefix)
    val accent = stringResource(R.string.library_title_accent)
    val styledTitle = remember(prefix, accent, colorScheme.onBackground, colorScheme.primary) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = colorScheme.onBackground)) {
                append(prefix.trimEnd())
                append(' ')
            }
            withStyle(SpanStyle(color = colorScheme.primary)) {
                append(accent.trimStart())
            }
        }
    }
    val settingsBtnSurface = remember(colorScheme.surfaceContainerHigh, colorScheme.background) {
        colorScheme.surfaceContainerHigh.opaqueOver(colorScheme.background, 0.96f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                text = styledTitle,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_header_title")
            )
            Text(
                text = stringResource(R.string.library_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_header_subtitle")
            )
        }

        Surface(
            onClick = onOpenSettings,
            shape = CircleShape,
            color = settingsBtnSurface,
            contentColor = colorScheme.onSurface,
            border = BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.28f)),
            modifier = Modifier
                .size(48.dp)
                .testTag("library_settings_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
internal fun LibraryPersistentSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    inputTestTag: String = "library_search_input",
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val pillColor = remember(colorScheme.surfaceContainerHigh, bg) {
        colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.96f)
    }
    val pillBorder = remember(colorScheme.outlineVariant) {
        BorderStroke(0.6.dp, colorScheme.outlineVariant.copy(alpha = 0.28f))
    }

    Surface(
        shape = CircleShape,
        color = pillColor,
        border = pillBorder,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = if (query.isNotEmpty()) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag(inputTestTag)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("library_search_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_clear_search),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun LibraryBentoHubGrid(
    likedCount: Int,
    playlistCount: Int,
    albumCount: Int,
    artistCount: Int,
    folderCount: Int,
    onOpenCategory: (LibraryCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val cardSurface = remember(colorScheme.surfaceContainerLow, bg) {
        colorScheme.surfaceContainerLow.opaqueOver(bg, 0.94f)
    }
    val cardBorder = remember(colorScheme.outlineVariant) {
        BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.28f))
    }
    val primaryBadgeBg = remember(colorScheme.primaryContainer, cardSurface) {
        colorScheme.primaryContainer.opaqueOver(cardSurface, 0.92f)
    }
    val secondaryBadgeBg = remember(colorScheme.secondaryContainer, cardSurface) {
        colorScheme.secondaryContainer.opaqueOver(cardSurface, 0.92f)
    }
    val tertiaryBadgeBg = remember(colorScheme.tertiaryContainer, cardSurface) {
        colorScheme.tertiaryContainer.opaqueOver(cardSurface, 0.92f)
    }

    val favoritesSubtitle = stringResource(R.string.library_bento_favorites_count, likedCount)
    val playlistsSubtitle = stringResource(
        if (playlistCount == 1) R.string.library_bento_playlists_singular else R.string.library_bento_playlists_plural,
        playlistCount
    )
    val albumsSubtitle = stringResource(
        if (albumCount == 1) R.string.library_bento_albums_singular else R.string.library_bento_albums_plural,
        albumCount
    )
    val artistsSubtitle = stringResource(
        if (artistCount == 1) R.string.library_bento_artists_singular else R.string.library_bento_artists_plural,
        artistCount
    )
    val foldersSubtitle = stringResource(
        if (folderCount == 1) R.string.library_bento_folders_singular else R.string.library_bento_folders_plural,
        folderCount
    )
    val recentSubtitle = stringResource(R.string.library_bento_recent_subtitle)

    val onOpenFavorites = remember(onOpenCategory) { { onOpenCategory(LibraryCategory.FAVORITES) } }
    val onOpenPlaylists = remember(onOpenCategory) { { onOpenCategory(LibraryCategory.PLAYLISTS) } }
    val onOpenAlbums = remember(onOpenCategory) { { onOpenCategory(LibraryCategory.ALBUMS) } }
    val onOpenArtists = remember(onOpenCategory) { { onOpenCategory(LibraryCategory.ARTISTS) } }
    val onOpenFolders = remember(onOpenCategory) { { onOpenCategory(LibraryCategory.FOLDERS) } }
    val onOpenRecent = remember(onOpenCategory) { { onOpenCategory(LibraryCategory.RECENT) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("library_bento_grid"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryBentoCard(
                title = stringResource(R.string.library_tab_favorites),
                subtitle = favoritesSubtitle,
                icon = Icons.Rounded.Favorite,
                badgeBackground = primaryBadgeBg,
                badgeIconTint = colorScheme.primary,
                cardSurface = cardSurface,
                cardBorder = cardBorder,
                onClick = onOpenFavorites,
                cardTestTag = "library_bento_favorites",
                subtitleTestTag = "library_bento_subtitle_favorites",
                modifier = Modifier.weight(1f)
            )
            LibraryBentoCard(
                title = stringResource(R.string.library_tab_playlists),
                subtitle = playlistsSubtitle,
                icon = CadenceIcons.QueueMusic,
                badgeBackground = tertiaryBadgeBg,
                badgeIconTint = colorScheme.tertiary,
                cardSurface = cardSurface,
                cardBorder = cardBorder,
                onClick = onOpenPlaylists,
                cardTestTag = "library_bento_playlists",
                subtitleTestTag = "library_bento_subtitle_playlists",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryBentoCard(
                title = stringResource(R.string.library_tab_albums),
                subtitle = albumsSubtitle,
                icon = CadenceIcons.Album,
                badgeBackground = secondaryBadgeBg,
                badgeIconTint = colorScheme.secondary,
                cardSurface = cardSurface,
                cardBorder = cardBorder,
                onClick = onOpenAlbums,
                cardTestTag = "library_bento_albums",
                subtitleTestTag = "library_bento_subtitle_albums",
                modifier = Modifier.weight(1f)
            )
            LibraryBentoCard(
                title = stringResource(R.string.library_tab_artists),
                subtitle = artistsSubtitle,
                icon = Icons.Rounded.Person,
                badgeBackground = primaryBadgeBg,
                badgeIconTint = colorScheme.primary,
                cardSurface = cardSurface,
                cardBorder = cardBorder,
                onClick = onOpenArtists,
                cardTestTag = "library_bento_artists",
                subtitleTestTag = "library_bento_subtitle_artists",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryBentoCard(
                title = stringResource(R.string.library_tab_folders),
                subtitle = foldersSubtitle,
                icon = CadenceIcons.FolderOpen,
                badgeBackground = secondaryBadgeBg,
                badgeIconTint = colorScheme.secondary,
                cardSurface = cardSurface,
                cardBorder = cardBorder,
                onClick = onOpenFolders,
                cardTestTag = "library_bento_folders",
                subtitleTestTag = "library_bento_subtitle_folders",
                modifier = Modifier.weight(1f)
            )
            LibraryBentoCard(
                title = stringResource(R.string.library_recently_played_title),
                subtitle = recentSubtitle,
                icon = CadenceIcons.History,
                badgeBackground = tertiaryBadgeBg,
                badgeIconTint = colorScheme.tertiary,
                cardSurface = cardSurface,
                cardBorder = cardBorder,
                onClick = onOpenRecent,
                cardTestTag = "library_bento_recent",
                subtitleTestTag = "library_bento_subtitle_recent",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LibraryBentoCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeBackground: Color,
    badgeIconTint: Color,
    cardSurface: Color,
    cardBorder: BorderStroke,
    onClick: () -> Unit,
    cardTestTag: String,
    subtitleTestTag: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = BentoCardShape,
        color = cardSurface,
        border = cardBorder,
        modifier = modifier
            .defaultMinSize(minHeight = 68.dp)
            .testTag(cardTestTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = badgeBackground,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeIconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(subtitleTestTag)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun LibrarySongsActionBar(
    trackCount: Int,
    sortOrder: LibrarySortOrder,
    onShuffleClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onSortChange: (LibrarySortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val countPillBg = remember(colorScheme.surfaceContainerHigh, bg) {
        colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.95f)
    }
    val shufflePillBg = remember(colorScheme.secondaryContainer, bg) {
        colorScheme.secondaryContainer.opaqueOver(bg, 0.94f)
    }
    val addBtnBg = remember(colorScheme.primaryContainer, bg) {
        colorScheme.primaryContainer.opaqueOver(bg, 0.94f)
    }
    val sortBorder = remember(colorScheme.outlineVariant) {
        BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.26f))
    }
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("library_songs_action_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.library_tab_songs),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                ),
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("library_songs_heading")
            )
            Surface(
                shape = CircleShape,
                color = countPillBg
            ) {
                Text(
                    text = trackCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("library_songs_count_pill")
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                onClick = onShuffleClick,
                enabled = trackCount > 0,
                shape = CircleShape,
                color = shufflePillBg,
                contentColor = colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .height(36.dp)
                    .testTag("library_shuffle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = CadenceIcons.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.action_shuffle_all),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Surface(
                onClick = onCreatePlaylistClick,
                shape = CircleShape,
                color = addBtnBg,
                contentColor = colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("library_new_playlist_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.action_create_playlist),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box {
                Surface(
                    onClick = { showSortMenu = true },
                    shape = CircleShape,
                    color = countPillBg,
                    contentColor = colorScheme.onSurfaceVariant,
                    border = sortBorder,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("sort_button")
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = sortOrder.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
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

@Composable
internal fun LibrarySubViewTopBar(
    title: String,
    subtitle: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSubmitQuery: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    showSortSelector: Boolean = false,
    sortOrder: LibrarySortOrder = LibrarySortOrder.DATE_ADDED_DESC,
    onSortChange: (LibrarySortOrder) -> Unit = {},
    trailingActionIcon: ImageVector? = null,
    trailingActionContentDescription: String? = null,
    trailingActionTestTag: String? = null,
    onTrailingAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val buttonSurface = remember(colorScheme.surfaceContainerHigh, bg) {
        colorScheme.surfaceContainerHigh.opaqueOver(bg, 0.96f)
    }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
                    .testTag("library_subview_back_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("library_subview_title")
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("library_subview_subtitle")
                )
            }

            if (showSortSelector) {
                Box {
                    Surface(
                        onClick = { showSortMenu = true },
                        shape = CircleShape,
                        color = buttonSurface,
                        contentColor = colorScheme.onSurfaceVariant,
                        border = BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.26f)),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("sort_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = sortOrder.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
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

            if (trailingActionIcon != null && onTrailingAction != null) {
                Surface(
                    onClick = onTrailingAction,
                    shape = CircleShape,
                    color = colorScheme.primaryContainer.opaqueOver(bg, 0.94f),
                    contentColor = colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(44.dp)
                        .then(if (trailingActionTestTag != null) Modifier.testTag(trailingActionTestTag) else Modifier)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = trailingActionIcon,
                            contentDescription = trailingActionContentDescription,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        LibraryPersistentSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onClear = onClearQuery,
            onSubmit = onSubmitQuery,
            placeholder = stringResource(R.string.library_subview_search_hint, title),
            focusRequester = focusRequester,
            inputTestTag = "library_search_input"
        )
    }
}

private data class SearchCollectionChip(
    val key: String,
    val title: String,
    val kindLabel: String,
    val icon: ImageVector,
    val drillDown: LibraryDrillDown
)

@Composable
internal fun LibrarySearchCollectionsStrip(
    playlists: StableList<PlaylistSummary>,
    albums: StableList<TrackGroup>,
    artists: StableList<TrackGroup>,
    onOpenDrillDown: (LibraryDrillDown) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = remember(playlists, albums, artists) {
        buildList {
            playlists.take(4).forEach { pl ->
                add(
                    SearchCollectionChip(
                        key = "pl_${pl.id}",
                        title = pl.name,
                        kindLabel = "Playlist",
                        icon = CadenceIcons.QueueMusic,
                        drillDown = LibraryDrillDown.Playlist(pl.id, pl.name)
                    )
                )
            }
            albums.take(4).forEach { album ->
                add(
                    SearchCollectionChip(
                        key = "alb_${album.key}",
                        title = album.title,
                        kindLabel = "Album",
                        icon = CadenceIcons.Album,
                        drillDown = LibraryDrillDown.Album(album.key)
                    )
                )
            }
            artists.take(4).forEach { artist ->
                add(
                    SearchCollectionChip(
                        key = "art_${artist.key}",
                        title = artist.title,
                        kindLabel = "Artist",
                        icon = Icons.Rounded.Person,
                        drillDown = LibraryDrillDown.Artist(artist.key)
                    )
                )
            }
        }
    }

    if (chips.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val chipSurface = remember(colorScheme.surfaceContainerLow, bg) {
        colorScheme.surfaceContainerLow.opaqueOver(bg, 0.94f)
    }
    val chipBorder = remember(colorScheme.outlineVariant) {
        BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.28f))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("library_search_collections_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.library_search_matches_collections),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = chips,
                key = { it.key },
                contentType = { "search_collection_chip" }
            ) { chip ->
                val onClick = remember(chip.drillDown, onOpenDrillDown) {
                    { onOpenDrillDown(chip.drillDown) }
                }
                Surface(
                    onClick = onClick,
                    shape = SearchCollectionPillShape,
                    color = chipSurface,
                    border = chipBorder,
                    modifier = Modifier.testTag("library_search_collection_${chip.key}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = chip.icon,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = chip.title,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = chip.kindLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
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
