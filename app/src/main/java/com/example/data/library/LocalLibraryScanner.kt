package com.example.data.library

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.FolderDao
import com.example.data.db.SavedFolderEntity
import com.example.data.db.TrackDao
import com.example.data.db.TrackEntity
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
    val scannedCount: Int = 0,
    val addedCount: Int = 0,
    val statusLabel: String = ""
)

data class ScanResult(
    val addedCount: Int,
    val skippedDuplicates: Int,
    val failedCount: Int,
    val firstAddedTrackId: String? = null
)

/**
 * Non-blocking local music scanner supporting:
 * 1. Zero-permission SAF folder tree traversal (OpenDocumentTree)
 * 2. Zero-permission SAF multi-file import (OpenMultipleDocuments)
 * 3. Fast bulk MediaStore audio scanning (when READ_MEDIA_AUDIO is granted)
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

    suspend fun importAudioFiles(uris: List<Uri>, minDurationSec: Int = 0): ScanResult = scanMutex.withLock {
        withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ScanResult(0, 0, 0)
        val minDurationMs = (minDurationSec * 1000L).coerceAtLeast(500L)
        _scanState.value = ScanState(isScanning = true, statusLabel = "Importing audio files…")
        var added = 0
        var duplicates = 0
        var failed = 0
        var firstAddedId: String? = null
        val batch = mutableListOf<TrackEntity>()

        try {
            val existingTuples = trackDao.getTrackDedupeTuples()
            val knownUris = existingTuples.mapNotNullTo(HashSet(existingTuples.size)) { it.localUriString }
            val knownDedupeKeys = existingTuples.mapTo(HashSet(existingTuples.size)) { it.dedupeKey }

            for ((idx, uri) in uris.withIndex()) {
                _scanState.value = ScanState(
                    isScanning = true,
                    scannedCount = idx + 1,
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
            }
        } finally {
            _scanState.value = ScanState(isScanning = false)
        }
        ScanResult(added, duplicates, failed, firstAddedId)
        }
    }

    suspend fun scanSafFolderTree(treeUri: Uri, minDurationSec: Int = 0): ScanResult = scanMutex.withLock {
        scanSafFolderTreeInternal(treeUri, minDurationSec)
    }

    private suspend fun scanSafFolderTreeInternal(treeUri: Uri, minDurationSec: Int = 0): ScanResult = withContext(Dispatchers.IO) {
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

        _scanState.value = ScanState(isScanning = true, statusLabel = "Scanning $rootName…")
        var scanned = 0
        var added = 0
        var duplicates = 0
        var failed = 0
        var firstAddedId: String? = null
        val batch = mutableListOf<TrackEntity>()

        try {
            val existingTuples = trackDao.getTrackDedupeTuples()
            val knownUris = existingTuples.mapNotNullTo(HashSet(existingTuples.size)) { it.localUriString }
            val knownDedupeKeys = existingTuples.mapTo(HashSet(existingTuples.size)) { it.dedupeKey }

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
                            scanned++
                            if (scanned % 8 == 0 || scanned == 1) {
                                _scanState.value = ScanState(
                                    isScanning = true,
                                    scannedCount = scanned,
                                    addedCount = added,
                                    statusLabel = "Scanned $scanned tracks in $rootName…"
                                )
                            }

                            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            val uriStr = childUri.toString()
                            if (uriStr in knownUris) {
                                duplicates++
                                continue
                            }

                            val folderName = currentPath.substringAfterLast('/')
                            val entity = extractSafTrack(
                                uri = childUri,
                                folderName = folderName,
                                folderPath = currentPath,
                                knownFileName = childName,
                                knownMime = childMime,
                                knownSize = childSize,
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
                    }
                }
            } else {
                // Fallback for non-standard document providers lacking tree document IDs
                val stack = ArrayDeque<Pair<DocumentFile, String>>()
                stack.addLast(rootDoc to rootName)

                while (stack.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val (dir, currentPath) = stack.removeLast()
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
                        scanned++
                        if (scanned % 8 == 0 || scanned == 1) {
                            _scanState.value = ScanState(
                                isScanning = true,
                                scannedCount = scanned,
                                addedCount = added,
                                statusLabel = "Scanned $scanned tracks in $rootName…"
                            )
                        }

                        val uriStr = child.uri.toString()
                        if (uriStr in knownUris) {
                            duplicates++
                            continue
                        }

                        val folderName = currentPath.substringAfterLast('/')
                        val entity = extractSafTrack(
                            uri = child.uri,
                            folderName = folderName,
                            folderPath = currentPath,
                            knownFileName = child.name,
                            knownMime = child.type,
                            knownSize = child.length(),
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
                }
            }
            if (batch.isNotEmpty()) {
                trackDao.insertTracksIgnore(batch.toList())
            }
        } finally {
            _scanState.value = ScanState(isScanning = false)
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
            for (folder in folders) {
                currentCoroutineContext().ensureActive()
                val res = scanSafFolderTreeInternal(Uri.parse(folder.treeUri), minDurationSec)
                totalAdded += res.addedCount
                totalDupes += res.skippedDuplicates
                totalFailed += res.failedCount
                if (firstAdded == null) firstAdded = res.firstAddedTrackId
            }
            pruneMissingLocalTracks()
            ScanResult(totalAdded, totalDupes, totalFailed, firstAdded)
        }
    }

    /**
     * Fast bulk MediaStore audio library scan (when READ_MEDIA_AUDIO permission is granted).
     * Avoids expensive per-file MediaMetadataRetriever calls by reading indexed OS metadata directly.
     */
    suspend fun scanMediaStoreAudio(minDurationSec: Int = 0): ScanResult = scanMutex.withLock {
        withContext(Dispatchers.IO) {
        val minDurationMs = (minDurationSec * 1000L).coerceAtLeast(3000L)
        _scanState.value = ScanState(isScanning = true, statusLabel = "Scanning device music…")
        var scanned = 0
        var added = 0
        var duplicates = 0
        var failed = 0
        var firstAddedId: String? = null
        val batch = mutableListOf<TrackEntity>()

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
                    if (scanned % 15 == 0 || scanned == 1) {
                        _scanState.value = ScanState(
                            isScanning = true,
                            scannedCount = scanned,
                            addedCount = added,
                            statusLabel = "Indexed $scanned device tracks…"
                        )
                    }

                    val mediaId = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val uriStr = contentUri.toString()
                    if (uriStr in knownUris) {
                        duplicates++
                        continue
                    }

                    val displayName = cursor.getString(displayCol).orEmpty()
                    val title = cursor.getString(titleCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: cleanFileName(displayName)
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
                    val folderName = if (bucketCol >= 0) {
                        cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "Music"
                    } else "Music"
                    val folderPath = if (relPathCol >= 0) {
                        cursor.getString(relPathCol)?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: folderName
                    } else folderName
                    val genre = if (genreCol >= 0) {
                        cursor.getString(genreCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty()
                    } else ""
                    val bitrateKbps = if (bitrateCol >= 0) {
                        (cursor.getInt(bitrateCol) / 1000).coerceAtLeast(0)
                    } else 0

                    val dedupeKey = TrackEntity.buildDedupeKey(title, artist, durationMs)
                    if (dedupeKey in knownDedupeKeys) {
                        duplicates++
                        continue
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
                        bitrateKbps = bitrateKbps,
                        sampleRateHz = 0,
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
            val rawSampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            } else null

            val durationMs = rawDuration?.toLongOrNull() ?: 0L
            if (durationMs < minDurationMs) return null

            val title = rawTitle?.takeIf { it.isNotBlank() } ?: cleanFileName(displayName.orEmpty())
            val artist = rawArtist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val album = rawAlbum?.takeIf { it.isNotBlank() } ?: folderName
            val genre = rawGenre?.takeIf { it.isNotBlank() }.orEmpty()
            val bitrateKbps = (rawBitrate?.toIntOrNull() ?: 0) / 1000
            val sampleRateHz = rawSampleRate?.toIntOrNull() ?: 0

            val artworkPath = artworkCache.cacheArtworkBytes(retriever.embeddedPicture)
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
                mimeType = mimeType,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
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
            "m4a", "alac" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "aiff", "aif" -> "audio/aiff"
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
        private const val BATCH_SIZE = 25
        private val SUPPORTED_EXTENSIONS = setOf(
            "flac", "mp3", "wav", "aac", "m4a", "ogg", "oga", "opus", "alac", "aiff", "aif", "webm"
        )
    }
}
