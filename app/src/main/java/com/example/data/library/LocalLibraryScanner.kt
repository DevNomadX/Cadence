package com.example.data.library

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.documentfile.provider.DocumentFile
import com.example.data.Track
import com.example.data.db.FolderDao
import com.example.data.db.SavedFolderEntity
import com.example.data.db.TrackDao
import com.example.data.db.TrackEntity
import com.example.data.resolveAudioCodecLabel
import com.example.ui.theme.ExpressivePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

@Immutable
data class ScanState(
    val isScanning: Boolean = false,
    val currentFolderName: String = "",
    val currentFileName: String = "",
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val addedCount: Int = 0,
    val statusLabel: String = ""
) {
    val progressFraction: Float?
        get() = if (totalCount > 0) {
            (scannedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
}

data class ScanResult(
    val addedCount: Int,
    val skippedDuplicates: Int,
    val failedCount: Int,
    val firstAddedTrackId: String? = null
)

/**
 * Non-blocking local music scanner supporting:
 * 1. Zero-permission SAF folder tree traversal (OpenDocumentTree) with two-phase discovery + 50-track batch Room inserts
 * 2. Zero-permission SAF multi-file import (OpenMultipleDocuments) with batch Room inserts
 * 3. Fast bulk MediaStore audio scanning (when READ_MEDIA_AUDIO is granted) with 50-track batch Room inserts
 */
class LocalLibraryScanner(
    private val context: Context,
    private val trackDao: TrackDao,
    private val folderDao: FolderDao,
    private val artworkCache: ArtworkCacheManager
) {
    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private val scanMutex = Mutex()

    private data class DiscoveredAudioFile(
        val uri: Uri,
        val folderName: String,
        val folderPath: String,
        val displayName: String?,
        val mimeType: String?,
        val fileSize: Long
    )

    suspend fun importAudioFiles(uris: List<Uri>, minDurationSec: Int = 0): ScanResult = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext ScanResult(0, 0, 0)
            val minDurationMs = (minDurationSec * 1000L).coerceAtLeast(500L)
            _scanState.value = ScanState(
                isScanning = true,
                currentFolderName = "Imported Files",
                scannedCount = 0,
                totalCount = uris.size,
                addedCount = 0,
                statusLabel = "Preparing ${uris.size} audio file${if (uris.size == 1) "" else "s"}…"
            )
            var added = 0
            var duplicates = 0
            var failed = 0
            var firstAddedId: String? = null
            val batch = ArrayList<TrackEntity>(BATCH_SIZE)

            try {
                val existingTuples = trackDao.getTrackDedupeTuples()
                val knownUris = existingTuples.mapNotNullTo(HashSet(existingTuples.size)) { it.localUriString }
                val knownDedupeKeys = existingTuples.mapTo(HashSet(existingTuples.size)) { it.dedupeKey }

                for ((idx, uri) in uris.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    _scanState.value = ScanState(
                        isScanning = true,
                        currentFolderName = "Imported Files",
                        currentFileName = uri.lastPathSegment?.substringAfterLast('/').orEmpty(),
                        scannedCount = idx + 1,
                        totalCount = uris.size,
                        addedCount = added,
                        statusLabel = "Indexing ${idx + 1} of ${uris.size}…"
                    )
                    persistReadPermission(uri)
                    val uriStr = uri.toString()
                    if (uriStr in knownUris) {
                        duplicates++
                        continue
                    }
                    val entity = extractSafTrack(
                        uri = uri,
                        folderName = "Imported",
                        folderPath = "imported",
                        minDurationMs = minDurationMs
                    )
                    if (entity == null) {
                        failed++
                        continue
                    }
                    if (entity.dedupeKey in knownDedupeKeys) {
                        duplicates++
                        continue
                    }
                    knownUris += uriStr
                    knownDedupeKeys += entity.dedupeKey
                    batch += entity
                    if (firstAddedId == null) firstAddedId = entity.id
                    added++
                    if (batch.size >= BATCH_SIZE) {
                        trackDao.insertTracksIgnore(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) {
                    trackDao.insertTracksIgnore(batch.toList())
                    batch.clear()
                }
            } finally {
                _scanState.value = ScanState(isScanning = false)
            }
            ScanResult(added, duplicates, failed, firstAddedId)
        }
    }

    suspend fun scanSafFolderTree(treeUri: Uri, minDurationSec: Int = 0): ScanResult = scanMutex.withLock {
        scanSafFolderTreeInternal(treeUri, minDurationSec, resetOnFinish = true)
    }

    private suspend fun scanSafFolderTreeInternal(
        treeUri: Uri,
        minDurationSec: Int = 0,
        resetOnFinish: Boolean = true
    ): ScanResult = withContext(Dispatchers.IO) {
        val minDurationMs = (minDurationSec * 1000L).coerceAtLeast(500L)
        persistReadPermission(treeUri)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ScanResult(0, 0, 1)

        val rootName = rootDoc.name?.takeIf { it.isNotBlank() } ?: "Music Folder"
        folderDao.upsertFolder(
            SavedFolderEntity(
                treeUri = treeUri.toString(),
                displayName = rootName,
                lastScannedAt = System.currentTimeMillis()
            )
        )

        _scanState.value = ScanState(
            isScanning = true,
            currentFolderName = rootName,
            statusLabel = "Discovering audio files in $rootName…"
        )
        var added = 0
        var duplicates = 0
        var failed = 0
        var firstAddedId: String? = null
        val batch = ArrayList<TrackEntity>(BATCH_SIZE)

        try {
            val existingTuples = trackDao.getTrackDedupeTuples()
            val knownUris = existingTuples.mapNotNullTo(HashSet(existingTuples.size)) { it.localUriString }
            val knownDedupeKeys = existingTuples.mapTo(HashSet(existingTuples.size)) { it.dedupeKey }

            // Phase 1: Fast recursive discovery so we know exact totalCount for smooth linear progress
            val discovered = ArrayList<DiscoveredAudioFile>()
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            if (rootDocId != null) {
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                )
                val stack = ArrayDeque<Pair<String, String>>()
                stack.addLast(rootDocId to rootName)

                while (stack.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val (parentDocId, currentPath) = stack.removeLast()
                    val currentFolder = currentPath.substringAfterLast('/')
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        if (idCol < 0) return@use

                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val childDocId = cursor.getString(idCol) ?: continue
                            val childName = if (nameCol >= 0) cursor.getString(nameCol) else null
                            val childMime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                            val childSize = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L

                            if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                val subName = childName?.takeIf { it.isNotBlank() } ?: "Subfolder"
                                if (!subName.startsWith(".")) {
                                    stack.addLast(childDocId to "$currentPath/$subName")
                                }
                                continue
                            }

                            if (!isSupportedAudioMeta(childMime, childName)) continue
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            discovered += DiscoveredAudioFile(
                                uri = childUri,
                                folderName = currentFolder,
                                folderPath = currentPath,
                                displayName = childName,
                                mimeType = childMime,
                                fileSize = childSize
                            )
                            if (discovered.size % 25 == 0) {
                                _scanState.value = ScanState(
                                    isScanning = true,
                                    currentFolderName = currentFolder,
                                    scannedCount = 0,
                                    totalCount = 0,
                                    addedCount = 0,
                                    statusLabel = "Found ${discovered.size} audio files in $rootName…"
                                )
                            }
                        }
                    }
                }
            } else {
                // Fallback for non-standard document providers lacking tree document IDs
                val stack = ArrayDeque<Pair<DocumentFile, String>>()
                stack.addLast(rootDoc to rootName)

                while (stack.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val (dir, currentPath) = stack.removeLast()
                    val currentFolder = currentPath.substringAfterLast('/')
                    val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
                    for (child in children) {
                        if (child.isDirectory) {
                            val subName = child.name ?: "Subfolder"
                            if (!subName.startsWith(".")) {
                                stack.addLast(child to "$currentPath/$subName")
                            }
                            continue
                        }
                        if (!isSupportedAudioDocument(child)) continue
                        discovered += DiscoveredAudioFile(
                            uri = child.uri,
                            folderName = currentFolder,
                            folderPath = currentPath,
                            displayName = child.name,
                            mimeType = child.type,
                            fileSize = child.length()
                        )
                    }
                }
            }

            // Phase 2: Extract metadata + artwork & batch-insert into Room every 50 tracks
            val totalFiles = discovered.size
            for ((index, candidate) in discovered.withIndex()) {
                currentCoroutineContext().ensureActive()
                val processed = index + 1
                if (processed == 1 || processed % 4 == 0 || processed == totalFiles) {
                    _scanState.value = ScanState(
                        isScanning = true,
                        currentFolderName = candidate.folderName,
                        currentFileName = candidate.displayName?.let(::cleanFileName).orEmpty(),
                        scannedCount = processed,
                        totalCount = totalFiles,
                        addedCount = added,
                        statusLabel = "Indexing $processed of $totalFiles in $rootName…"
                    )
                }

                val uriStr = candidate.uri.toString()
                if (uriStr in knownUris) {
                    duplicates++
                    continue
                }

                val entity = extractSafTrack(
                    uri = candidate.uri,
                    folderName = candidate.folderName,
                    folderPath = candidate.folderPath,
                    knownFileName = candidate.displayName,
                    knownMime = candidate.mimeType,
                    knownSize = candidate.fileSize,
                    minDurationMs = minDurationMs
                )
                if (entity == null) {
                    failed++
                    continue
                }
                if (entity.dedupeKey in knownDedupeKeys) {
                    duplicates++
                    continue
                }
                knownUris += uriStr
                knownDedupeKeys += entity.dedupeKey
                batch += entity
                if (firstAddedId == null) firstAddedId = entity.id
                added++

                if (batch.size >= BATCH_SIZE) {
                    trackDao.insertTracksIgnore(batch.toList())
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                trackDao.insertTracksIgnore(batch.toList())
                batch.clear()
            }
        } finally {
            if (resetOnFinish) {
                _scanState.value = ScanState(isScanning = false)
            }
        }
        ScanResult(added, duplicates, failed, firstAddedId)
    }

    suspend fun rescanAllSavedFolders(minDurationSec: Int = 0): ScanResult = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            val folders = folderDao.getSavedFoldersOnce()
            if (folders.isEmpty()) return@withContext ScanResult(0, 0, 0)
            var totalAdded = 0
            var totalDupes = 0
            var totalFailed = 0
            var firstAdded: String? = null
            try {
                for (folder in folders) {
                    currentCoroutineContext().ensureActive()
                    val res = scanSafFolderTreeInternal(
                        treeUri = Uri.parse(folder.treeUri),
                        minDurationSec = minDurationSec,
                        resetOnFinish = false
                    )
                    totalAdded += res.addedCount
                    totalDupes += res.skippedDuplicates
                    totalFailed += res.failedCount
                    if (firstAdded == null) firstAdded = res.firstAddedTrackId
                }
                pruneMissingLocalTracks()
            } finally {
                _scanState.value = ScanState(isScanning = false)
            }
            ScanResult(totalAdded, totalDupes, totalFailed, firstAdded)
        }
    }

    /**
     * Fast bulk MediaStore audio library scan (when READ_MEDIA_AUDIO permission is granted).
     * Batches Room inserts in chunks of 50 tracks on Dispatchers.IO.
     */
    suspend fun scanMediaStoreAudio(minDurationSec: Int = 0): ScanResult = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            val minDurationMs = (minDurationSec * 1000L).coerceAtLeast(3000L)
            _scanState.value = ScanState(
                isScanning = true,
                currentFolderName = "Device Music",
                statusLabel = "Scanning device music…"
            )
            var scanned = 0
            var added = 0
            var duplicates = 0
            var failed = 0
            var firstAddedId: String? = null
            val batch = ArrayList<TrackEntity>(BATCH_SIZE)

            val projection = buildList {
                add(MediaStore.Audio.Media._ID)
                add(MediaStore.Audio.Media.TITLE)
                add(MediaStore.Audio.Media.ARTIST)
                add(MediaStore.Audio.Media.ALBUM)
                add(MediaStore.Audio.Media.ALBUM_ID)
                add(MediaStore.Audio.Media.DURATION)
                add(MediaStore.Audio.Media.MIME_TYPE)
                add(MediaStore.Audio.Media.SIZE)
                add(MediaStore.Audio.Media.DATE_ADDED)
                add(MediaStore.Audio.Media.DISPLAY_NAME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                    add(MediaStore.Audio.Media.RELATIVE_PATH)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(MediaStore.Audio.Media.GENRE)
                    add(MediaStore.Audio.Media.BITRATE)
                }
            }.toTypedArray()

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMs"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            try {
                val existingTuples = trackDao.getTrackDedupeTuples()
                val knownUris = existingTuples.mapNotNullTo(HashSet(existingTuples.size)) { it.localUriString }
                val knownDedupeKeys = existingTuples.mapTo(HashSet(existingTuples.size)) { it.dedupeKey }

                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val totalRows = cursor.count.coerceAtLeast(0)
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                    } else -1
                    val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else -1
                    val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                    } else -1
                    val bitrateCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
                    } else -1

                    while (cursor.moveToNext()) {
                        scanned++
                        val mediaId = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            mediaId
                        )
                        val uriStr = contentUri.toString()

                        val displayName = cursor.getString(displayCol).orEmpty()
                        val title = cursor.getString(titleCol)
                            ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: cleanFileName(displayName)
                        val folderName = if (bucketCol >= 0) {
                            cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "Music"
                        } else "Music"

                        if (scanned == 1 || scanned % 10 == 0 || scanned == totalRows) {
                            _scanState.value = ScanState(
                                isScanning = true,
                                currentFolderName = folderName,
                                currentFileName = title,
                                scannedCount = scanned,
                                totalCount = totalRows,
                                addedCount = added,
                                statusLabel = "Indexing $scanned of $totalRows tracks…"
                            )
                        }

                        if (uriStr in knownUris) {
                            duplicates++
                            continue
                        }

                        val artist = cursor.getString(artistCol)
                            ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: "Unknown Artist"
                        val album = cursor.getString(albumCol)
                            ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: "Unknown Album"
                        val albumId = cursor.getLong(albumIdCol)
                        val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)
                        if (durationMs < 1000L) {
                            failed++
                            continue
                        }

                        val mime = cursor.getString(mimeCol)?.takeIf { it.isNotBlank() } ?: "audio/*"
                        val fileSize = cursor.getLong(sizeCol).coerceAtLeast(0L)
                        val dateAdded = cursor.getLong(dateCol) * 1000L
                        val folderPath = if (relPathCol >= 0) {
                            cursor.getString(relPathCol)?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: folderName
                        } else folderName
                        val genre = if (genreCol >= 0) {
                            cursor.getString(genreCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty()
                        } else ""
                        var bitrateKbps = if (bitrateCol >= 0) {
                            (cursor.getInt(bitrateCol) / 1000).coerceAtLeast(0)
                        } else 0

                        val dedupeKey = TrackEntity.buildDedupeKey(title, artist, durationMs)
                        if (dedupeKey in knownDedupeKeys) {
                            duplicates++
                            continue
                        }

                        var codec = resolveAudioCodecLabel(
                            codec = "",
                            mimeType = mime,
                            uriString = displayName
                        )
                        var sampleRateHz = 0
                        var bitDepth = 0

                        // Inspect container format on Dispatchers.IO for lossless, ambiguous (m4a/mp4),
                        // or high-bitrate candidates so Hi-Fi / Hi-Res badges have exact specs without UI I/O.
                        val needsFormatProbe = codec in Track.LOSSLESS_CODECS ||
                            codec == "M4A" ||
                            codec.isEmpty() ||
                            bitrateKbps >= 300 ||
                            bitrateKbps == 0
                        if (needsFormatProbe) {
                            val probed = probeAudioFormatWithExtractor(contentUri)
                            if (probed != null) {
                                if (probed.codec.isNotBlank()) codec = probed.codec
                                if (probed.bitrateKbps > 0 && bitrateKbps <= 0) bitrateKbps = probed.bitrateKbps
                                if (probed.sampleRateHz > 0) sampleRateHz = probed.sampleRateHz
                                if (probed.bitDepth > 0) bitDepth = probed.bitDepth
                            }
                        }
                        if (bitrateKbps <= 0 && fileSize > 0L && durationMs >= 1000L) {
                            bitrateKbps = ((fileSize * 8L) / durationMs).toInt().coerceAtLeast(0)
                        }

                        val albumArtUri = if (albumId > 0L) {
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            ).toString()
                        } else null

                        val entity = TrackEntity(
                            id = "ms_$mediaId",
                            title = title,
                            artist = artist,
                            album = album,
                            genre = genre,
                            durationMs = durationMs,
                            bpm = 90,
                            artworkResId = null,
                            artworkPath = albumArtUri,
                            localUriString = uriStr,
                            folderName = folderName,
                            folderPath = folderPath,
                            mimeType = mime,
                            codec = codec,
                            bitrateKbps = bitrateKbps,
                            sampleRateHz = sampleRateHz,
                            bitDepth = bitDepth,
                            fileSizeBytes = fileSize,
                            dateAdded = if (dateAdded > 0L) dateAdded else System.currentTimeMillis(),
                            playCount = 0,
                            lastPlayedAt = 0L,
                            isFavorite = false,
                            synthProfileName = null,
                            suggestedPaletteId = paletteForHash(uriStr.hashCode()).id,
                            isUserImported = true,
                            dedupeKey = dedupeKey
                        )

                        knownUris += uriStr
                        knownDedupeKeys += dedupeKey
                        batch += entity
                        if (firstAddedId == null) firstAddedId = entity.id
                        added++

                        if (batch.size >= BATCH_SIZE) {
                            trackDao.insertTracksIgnore(batch.toList())
                            batch.clear()
                        }
                    }
                }
                if (batch.isNotEmpty()) {
                    trackDao.insertTracksIgnore(batch.toList())
                    batch.clear()
                }
            } catch (_: SecurityException) {
                failed++
            } finally {
                _scanState.value = ScanState(isScanning = false)
            }

            ScanResult(added, duplicates, failed, firstAddedId)
        }
    }

    /**
     * Checks previously indexed local tracks and prunes any files that were deleted or revoked.
     */
    suspend fun pruneMissingLocalTracks(): Int = withContext(Dispatchers.IO) {
        val localTracks = trackDao.getAllTracksOnce().filter { it.isUserImported && it.localUriString != null }
        val missingIds = mutableListOf<String>()
        for (track in localTracks) {
            val uri = Uri.parse(track.localUriString)
            val exists = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
            if (!exists) {
                missingIds += track.id
            }
        }
        if (missingIds.isNotEmpty()) {
            trackDao.deleteTracksByIds(missingIds)
        }
        missingIds.size
    }

    private suspend fun extractSafTrack(
        uri: Uri,
        folderName: String,
        folderPath: String,
        knownFileName: String? = null,
        knownMime: String? = null,
        knownSize: Long = 0L,
        minDurationMs: Long = 500L
    ): TrackEntity? {
        val resolver = context.contentResolver
        var displayName = knownFileName
        var fileSize = knownSize

        if (displayName.isNullOrBlank() || fileSize <= 0L) {
            runCatching {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (displayName.isNullOrBlank() && nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx)
                        }
                        if (fileSize <= 0L && sizeIdx >= 0) {
                            fileSize = cursor.getLong(sizeIdx)
                        }
                    }
                }
            }
        }

        val mimeType = knownMime
            ?: runCatching { resolver.getType(uri) }.getOrNull()
            ?: inferMimeFromName(displayName.orEmpty())

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val rawGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val rawMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val rawSampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            } else null
            val rawBitsPerSample = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
            } else null

            val durationMs = rawDuration?.toLongOrNull() ?: 0L
            if (durationMs < minDurationMs) return null

            val title = rawTitle?.takeIf { it.isNotBlank() } ?: cleanFileName(displayName.orEmpty())
            val artist = rawArtist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val album = rawAlbum?.takeIf { it.isNotBlank() } ?: folderName
            val genre = rawGenre?.takeIf { it.isNotBlank() }.orEmpty()
            val effectiveMime = rawMime?.takeIf { it.isNotBlank() } ?: mimeType
            var bitrateKbps = ((rawBitrate?.toIntOrNull() ?: 0) / 1000).coerceAtLeast(0)
            var sampleRateHz = rawSampleRate?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            var bitDepth = rawBitsPerSample?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            var codec = resolveAudioCodecLabel(
                codec = "",
                mimeType = effectiveMime,
                uriString = displayName ?: uri.toString()
            )

            // Single-pass MediaExtractor probe on Dispatchers.IO whenever sample rate, bit depth,
            // bitrate, or container codec (e.g. M4A -> ALAC vs AAC) isn't already resolved.
            val needsExtractorProbe = sampleRateHz == 0 ||
                (bitDepth == 0 && (codec in Track.LOSSLESS_CODECS || codec == "M4A")) ||
                codec == "M4A" ||
                codec.isEmpty() ||
                bitrateKbps == 0
            if (needsExtractorProbe) {
                val probed = probeAudioFormatWithExtractor(uri)
                if (probed != null) {
                    if (probed.codec.isNotBlank()) codec = probed.codec
                    if (probed.bitrateKbps > 0 && bitrateKbps <= 0) bitrateKbps = probed.bitrateKbps
                    if (probed.sampleRateHz > 0 && sampleRateHz <= 0) sampleRateHz = probed.sampleRateHz
                    if (probed.bitDepth > 0 && bitDepth <= 0) bitDepth = probed.bitDepth
                }
            }

            val embeddedArtBytes = retriever.embeddedPicture
            if (bitrateKbps <= 0 && fileSize > 0L && durationMs >= 1000L) {
                val audioBytes = (fileSize - (embeddedArtBytes?.size?.toLong() ?: 0L)).coerceAtLeast(0L)
                if (audioBytes > 0L) {
                    bitrateKbps = ((audioBytes * 8L) / durationMs).toInt().coerceAtLeast(0)
                }
            }

            val artworkPath = artworkCache.cacheArtworkBytes(embeddedArtBytes)
            val uriStr = uri.toString()

            TrackEntity(
                id = "local_${sha256Id(uriStr)}",
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                durationMs = durationMs,
                bpm = 90,
                artworkResId = null,
                artworkPath = artworkPath,
                localUriString = uriStr,
                folderName = folderName,
                folderPath = folderPath,
                mimeType = effectiveMime,
                codec = codec,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
                bitDepth = bitDepth,
                fileSizeBytes = fileSize.coerceAtLeast(0L),
                dateAdded = System.currentTimeMillis(),
                playCount = 0,
                lastPlayedAt = 0L,
                isFavorite = false,
                synthProfileName = null,
                suggestedPaletteId = paletteForHash(uriStr.hashCode()).id,
                isUserImported = true,
                dedupeKey = TrackEntity.buildDedupeKey(title, artist, durationMs)
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class ProbedAudioFormat(
        val codec: String,
        val bitrateKbps: Int,
        val sampleRateHz: Int,
        val bitDepth: Int
    )

    private fun probeAudioFormatWithExtractor(uri: Uri): ProbedAudioFormat? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val trackMime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!trackMime.startsWith("audio/", ignoreCase = true)) continue

                val codec = resolveAudioCodecLabel(codec = "", mimeType = trackMime)
                val sampleRateHz = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(0)
                } else 0
                val bitrateKbps = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    (format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000).coerceAtLeast(0)
                } else 0

                var bitDepth = 0
                if (format.containsKey("bits-per-sample")) {
                    bitDepth = format.getInteger("bits-per-sample").coerceAtLeast(0)
                }
                if (bitDepth == 0 && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    bitDepth = pcmEncodingToBitDepth(format.getInteger(MediaFormat.KEY_PCM_ENCODING))
                }

                return ProbedAudioFormat(
                    codec = codec,
                    bitrateKbps = bitrateKbps,
                    sampleRateHz = sampleRateHz,
                    bitDepth = bitDepth
                )
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun pcmEncodingToBitDepth(pcmEncoding: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> return 24
                AudioFormat.ENCODING_PCM_32BIT -> return 32
            }
        }
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 8
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_FLOAT -> 32
            else -> 0
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Works for active session even if provider doesn't grant persistable rights
        }
    }

    private fun isSupportedAudioDocument(doc: DocumentFile): Boolean =
        isSupportedAudioMeta(doc.type, doc.name)

    private fun isSupportedAudioMeta(mimeType: String?, displayName: String?): Boolean {
        val mime = mimeType?.lowercase(Locale.ROOT).orEmpty()
        if (mime.startsWith("audio/")) return true
        val ext = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return ext in SUPPORTED_EXTENSIONS
    }

    private fun cleanFileName(name: String): String =
        name.substringBeforeLast('.')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Untitled Track" }

    private fun inferMimeFromName(name: String): String =
        when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "alac" -> "audio/alac"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "aiff", "aif" -> "audio/aiff"
            "ape" -> "audio/x-ape"
            "dsf" -> "audio/x-dsf"
            "dff" -> "audio/x-dff"
            "wv" -> "audio/x-wavpack"
            else -> "audio/*"
        }

    private fun paletteForHash(hash: Int): ExpressivePalette {
        val choices = listOf(
            ExpressivePalette.TERRACOTTA,
            ExpressivePalette.MATCHA,
            ExpressivePalette.COBALT,
            ExpressivePalette.BERRY,
            ExpressivePalette.CITRUS
        )
        return choices[(hash and 0x7FFFFFFF) % choices.size]
    }

    private fun sha256Id(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val BATCH_SIZE = 50
        private val SUPPORTED_EXTENSIONS = setOf(
            "flac", "mp3", "wav", "aac", "m4a", "ogg", "oga", "opus",
            "alac", "aiff", "aif", "ape", "dsf", "dff", "wv", "webm"
        )
    }
}
