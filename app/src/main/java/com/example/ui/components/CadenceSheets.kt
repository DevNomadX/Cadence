package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlaylistSummary
import com.example.data.StableList
import com.example.data.Track
import com.example.data.formatDuration
import com.example.data.formatFileSize
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.opaqueOver
import kotlin.math.abs

@Composable
fun QueueSheetContent(
    queueTracks: StableList<Track>,
    currentQueueIndex: Int,
    isPlaying: Boolean,
    artworkShape: ExpressiveArtworkShape,
    onSelectQueueIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onSaveAsPlaylist: () -> Unit
) {
    val activeRowColor = MaterialTheme.colorScheme.primaryContainer.opaqueOver(
        MaterialTheme.colorScheme.surfaceContainerLow,
        0.72f
    )
    val idleRowColor = MaterialTheme.colorScheme.surfaceContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.queue_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${queueTracks.size} track${if (queueTracks.size == 1) "" else "s"} in queue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onSaveAsPlaylist,
                    enabled = queueTracks.isNotEmpty(),
                    modifier = Modifier.testTag("save_queue_playlist_button")
                ) {
                    Icon(
                        imageVector = CadenceIcons.Save,
                        contentDescription = stringResource(R.string.action_save_queue_playlist),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onClearQueue,
                    enabled = queueTracks.size > 1,
                    modifier = Modifier.testTag("clear_queue_button")
                ) {
                    Icon(
                        imageVector = CadenceIcons.DeleteSweep,
                        contentDescription = stringResource(R.string.action_clear_queue),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = queueTracks,
                key = { index, track -> "${track.id}_$index" },
                contentType = { _, _ -> "queue_track_row" }
            ) { index, track ->
                val isCurrent = index == currentQueueIndex
                val rowClick = remember(index, onSelectQueueIndex) { { onSelectQueueIndex(index) } }
                val moveUpClick = remember(index, onMoveItem) { { onMoveItem(index, index - 1) } }
                val moveDownClick = remember(index, onMoveItem) { { onMoveItem(index, index + 1) } }
                val removeClick = remember(index, onRemoveItem) { { onRemoveItem(index) } }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isCurrent) activeRowColor else idleRowColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = rowClick)
                        .testTag("queue_item_$index")
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExpressiveArtworkFrame(
                            track = track,
                            artworkShape = artworkShape,
                            isPlaying = isCurrent && isPlaying,
                            animateScale = false,
                            decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                            shapeOverride = FastListThumbnailShape,
                            modifier = Modifier.size(46.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} · ${formatDuration(track.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isCurrent) {
                            AudioPulseBars(
                                isPlaying = isPlaying,
                                tint = MaterialTheme.colorScheme.primary,
                                barHeight = 16.dp,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (index > 0) {
                                    IconButton(
                                        onClick = moveUpClick,
                                        modifier = Modifier.size(width = 40.dp, height = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.action_move_up),
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (index < queueTracks.lastIndex) {
                                    IconButton(
                                        onClick = moveDownClick,
                                        modifier = Modifier.size(width = 40.dp, height = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.action_move_down),
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = removeClick,
                                    modifier = Modifier.size(width = 40.dp, height = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.action_remove_from_queue),
                                        modifier = Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackActionsSheetContent(
    track: Track,
    artworkShape: ExpressiveArtworkShape,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowDetails: () -> Unit,
    playbackSpeed: Float? = null,
    onSelectPlaybackSpeed: ((Float) -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ExpressiveArtworkFrame(
                track = track,
                artworkShape = artworkShape,
                isPlaying = false,
                animateScale = false,
                decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
                shapeOverride = FastListThumbnailShape,
                modifier = Modifier.size(52.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} · ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        SheetActionItem(
            icon = CadenceIcons.QueuePlayNext,
            label = stringResource(R.string.action_play_next),
            onClick = onPlayNext
        )
        SheetActionItem(
            icon = CadenceIcons.QueueMusic,
            label = stringResource(R.string.action_add_to_queue),
            onClick = onAddToQueue
        )
        SheetActionItem(
            icon = CadenceIcons.PlaylistAdd,
            label = stringResource(R.string.action_add_to_playlist),
            onClick = onAddToPlaylist
        )
        if (playbackSpeed != null && onSelectPlaybackSpeed != null) {
            PlaybackSpeedSelectorCard(
                currentSpeed = playbackSpeed,
                onSelectSpeed = onSelectPlaybackSpeed
            )
        }
        SheetActionItem(
            icon = Icons.Outlined.Info,
            label = stringResource(R.string.action_track_info),
            onClick = onShowDetails
        )
        if (onRemoveFromPlaylist != null) {
            SheetActionItem(
                icon = CadenceIcons.DeleteOutline,
                label = "Remove from playlist",
                onClick = onRemoveFromPlaylist
            )
        }
        if (onRemoveFromLibrary != null) {
            SheetActionItem(
                icon = CadenceIcons.DeleteOutline,
                label = stringResource(R.string.action_remove_track),
                onClick = onRemoveFromLibrary
            )
        }
    }
}

@Composable
fun AddToPlaylistSheetContent(
    playlists: StableList<PlaylistSummary>,
    onSelectPlaylist: (Long) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.action_add_to_playlist),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalButton(
                onClick = onCreateNewPlaylist,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_create_playlist))
            }
        }

        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.library_empty_playlists),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = playlists,
                    key = { it.id },
                    contentType = { "playlist_pick_row" }
                ) { pl ->
                    val selectClick = remember(pl.id, onSelectPlaylist) { { onSelectPlaylist(pl.id) } }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = selectClick)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = CadenceIcons.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${pl.trackCount} track${if (pl.trackCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportSourcesSheetContent(
    savedFolderCount: Int,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onScanDevice: () -> Unit,
    onRescanFolders: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Add Local Music",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        SheetActionItem(
            icon = CadenceIcons.CreateNewFolder,
            label = stringResource(R.string.action_add_folder),
            subtitle = "Zero-permission folder scan (FLAC, MP3, WAV, M4A, Opus, OGG)",
            onClick = onAddFolder
        )
        SheetActionItem(
            icon = CadenceIcons.AudioFile,
            label = stringResource(R.string.action_import_files),
            subtitle = "Pick individual audio tracks from storage",
            onClick = onImportFiles
        )
        SheetActionItem(
            icon = CadenceIcons.LibraryMusic,
            label = stringResource(R.string.action_scan_device),
            subtitle = "Index all music in Android MediaStore",
            onClick = onScanDevice
        )
        if (savedFolderCount > 0) {
            SheetActionItem(
                icon = Icons.Rounded.Refresh,
                label = stringResource(R.string.action_rescan_folders),
                subtitle = "Refresh $savedFolderCount saved music folder${if (savedFolderCount == 1) "" else "s"}",
                onClick = onRescanFolders
            )
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val badgeBg = MaterialTheme.colorScheme.primaryContainer.opaqueOver(
        MaterialTheme.colorScheme.surfaceContainer,
        0.72f
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = badgeBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackSpeedSelectorCard(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surfaceContainer
    val badgeBg = MaterialTheme.colorScheme.primaryContainer.opaqueOver(cardBg, 0.72f)
    val idleChipBg = MaterialTheme.colorScheme.surfaceContainerHigh.opaqueOver(cardBg, 0.94f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.opaqueOver(cardBg, 0.85f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overflow_playback_speed_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = badgeBg,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = CadenceIcons.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.playback_speed_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatSpeedLabel(currentSpeed),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = PLAYBACK_SPEED_OPTIONS,
                    key = { speed -> "overflow_speed_$speed" },
                    contentType = { "speed_chip" }
                ) { speed ->
                    val selected = abs(currentSpeed - speed) < 0.01f
                    val label = formatSpeedLabel(speed)
                    val onClick = remember(speed, onSelectSpeed) { { onSelectSpeed(speed) } }
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else idleChipBg,
                        border = if (selected) null else BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .height(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClick)
                            .testTag("overflow_speed_$label")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Playlist name") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirm(trimmed)
                    }
                },
                enabled = name.trim().isNotEmpty(),
                shape = CircleShape
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TrackDetailsDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val formatStr = buildString {
        append(track.resolvedCodec.ifBlank { track.mimeType.substringAfter('/').uppercase().ifBlank { "AUDIO" } })
        if (track.bitDepth > 0) append(" · ${track.bitDepth}-bit")
        if (track.sampleRateHz > 0) append(" · ${com.example.data.formatSampleRateKHz(track.sampleRateHz)}")
        if (track.bitrateKbps > 0) append(" · ${track.bitrateKbps} kbps")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Artist", track.artist)
                DetailRow("Album", track.album)
                if (track.genre.isNotBlank()) DetailRow("Genre", track.genre)
                DetailRow("Duration", formatDuration(track.durationMs))
                DetailRow("Format", formatStr)
                DetailRow("Size", formatFileSize(track.fileSizeBytes))
                DetailRow("Folder", track.folderPath.ifBlank { track.folderName })
                DetailRow("Plays", track.playCount.toString())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
