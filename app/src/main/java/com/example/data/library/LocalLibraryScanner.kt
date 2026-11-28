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
import com.example.data.intelligence.LocalMoodAndAffinityEngine
import com.example.data.intelligence.SmartMoodTag
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
import java.io.File
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
    val firstAddedTrackId: String? = null,
    val prunedCount: Int = 0,
    val mediaScannerInvokedPaths: List<String> = emptyList()
)

/**
 * High-speed, strict folder-scoped offline music scanner:
 * - Stage 1 (< 400ms target): Direct selected-folder walk + `MediaScannerConnection.scanFile` +
 *   single batched `MediaStore` query (strictly scoped to user-selected folders, filtering by valid
 *   audio MIME/extension and `DURATION >= 10_000L` ms without `IS_MUSIC` exclusions) + direct-file
 *   metadata fallback when `MediaStore` lags + single-transaction Room `@Upsert` and pruning.
 * - Stage 2 (background): Async `AudioMetadataEnricher` on `Dispatchers.IO` in small batches after
 *   Home is interactive.
 */
class LocalLibraryScanner(
    private val context: Context,
    private val trackDao: TrackDao,
    private val folderDao: FolderDao,
    private val artworkCache: ArtworkCacheManager,
    private val mediaFileScanner: MediaFileScanner = AndroidMediaFileScanner(context),
    private val directMetadataReader: DirectAudioMetadataReader = AndroidDirectAudioMetadataReader(),
    private val primaryExternalRootProvider: () -> String = FolderScopeMatcher::defaultPrimaryStorageRoot
) {
    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private val scanMutex = Mutex()
    private val enricher = AudioMetadataEnricher(context, trackDao, artworkCache)

    private data class DiscoveredSafFile(
        val uri: Uri,
        val folderName: String,
        val folderPath: String,
        val displayName: String?,
        val mimeType: String?,
        val fileSize: Long
    )

    private data class WalkedAudioFile(
        val file: File,
        val normalizedPath: String,
        val folder: NormalizedSelectedFolder,
        val lastModifiedMs: Long,
        val sizeBytes: Long
    )

    /**
     * Stage 2 background enrichment entrypoint — called asynchronously after Stage 1 unlocks the UI.
     */
    suspend fun runStage2BackgroundEnrichment(batchSize: Int = AudioMetadataEnricher.DEFAULT_BATCH_SIZE): Int =
        enricher.enrichPendingTracks(batchSize = batchSize)

    /**
     * Strictly folder-scoped sync used on startup, `onResume`, `FileObserver`/`ContentObserver`
     * change events, and manual rescan:
     * 1. Normalizes all selected folders (never pulls in unselected folders unless [scanAllFolders] is true).
     * 2. Walks selected directories directly on `Dispatchers.IO` for new/modified audio files.
     * 3. Invokes `MediaScannerConnection.scanFile` (3000ms safety timeout) on new/modified paths.
     * 4. Executes a single batched `MediaStore` Stage 1 metadata query scoped strictly to selected folders.
     * 5. Falls back directly to file metadata for any new selected-folder file where `MediaStore` lags.
     * 6. Atomically commits `@Upsert` additions/updates and prunes deleted or out-of-scope tracks in one `@Transaction`.
     */
    suspend fun syncSelectedFolders(
        scanAllFolders: Boolean = false,
        showProgress: Boolean = false,
        pruneOutOfScope: Boolean = true
    ): ScanResult = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            val savedFolders = folderDao.getSavedFoldersOnce()
            val primaryRoot = primaryExternalRootProvider()
            val normalizedFolders = savedFolders.map { folder ->
                FolderScopeMatcher.normalize(
                    rawUriOrPath = folder.treeUri,
                    displayName = folder.displayName,
                    primaryExternalRoot = primaryRoot
                )
            }

            if (!scanAllFolders && normalizedFolders.isEmpty()) {
                return@withContext ScanResult(0, 0, 0)
            }

            val scopeTitle = when {
                scanAllFolders -> "All Music Folders"
                normalizedFolders.size == 1 -> normalizedFolders.first().displayName
                else -> "${normalizedFolders.size} Music Folders"
            }

            if (showProgress) {
                _scanState.value = ScanState(
                    isScanning = true,
                    currentFolderName = scopeTitle,
                    statusLabel = "Checking $scopeTitle…"
                )
            }

            var added = 0
            var duplicates = 0
            var failed = 0
            var pruned = 0
            var firstAddedId: String? = null
            var scannedPaths = emptyList<String>()

            try {
                val existingEntities = trackDao.getAllTracksOnce()
                val existingById = existingEntities.associateBy { it.id }
                val existingByUri = existingEntities.mapNotNull { e ->
                    e.localUriString?.let { it to e }
                }.toMap()
                val existingByNormPath = HashMap<String, TrackEntity>(existingEntities.size)
                for (entity in existingEntities) {
                    resolveExistingFilePath(entity)?.let { path ->
                        existingByNormPath[path.lowercase(Locale.ROOT)] = entity
                    }
                }
                val knownDedupeKeys = existingEntities.mapTo(HashSet(existingEntities.size)) { it.dedupeKey }

                // Step 1: Direct folder walk on user's selected directories
                val walkedFiles = ArrayList<WalkedAudioFile>()
                val walkedFolderRoots = HashSet<String>()
                val safOnlyFolders = ArrayList<NormalizedSelectedFolder>()

                for (folder in normalizedFolders) {
                    currentCoroutineContext().ensureActive()
                    val dir = folder.resolveReadableDirectory()
                    if (dir != null) {
                        walkedFolderRoots += folder.rawUriOrPath
                        walkDirectoryRecursively(dir, folder, walkedFiles)
                    } else if (folder.treeUriPrefix != null) {
                        safOnlyFolders += folder
                    }
                }

                val walkedByLowerPath = walkedFiles.associateBy { it.normalizedPath.lowercase(Locale.ROOT) }
                val newOrModifiedFiles = walkedFiles.filter { walked ->
                    val existing = existingByNormPath[walked.normalizedPath.lowercase(Locale.ROOT)]
                        ?: existingByUri[Uri.fromFile(walked.file).toString()]
                    existing == null ||
                        walked.lastModifiedMs > existing.dateAdded + 1_000L ||
                        (walked.sizeBytes > 0L && walked.sizeBytes != existing.fileSizeBytes)
                }

                // Step 2: Invoke MediaScannerConnection.scanFile on new/modified paths (3000ms timeout)
                val scannedUriByLowerPath = HashMap<String, Uri?>()
                if (newOrModifiedFiles.isNotEmpty()) {
                    scannedPaths = newOrModifiedFiles.map { it.file.absolutePath }
                    if (showProgress) {
                        _scanState.value = ScanState(
                            isScanning = true,
                            currentFolderName = scopeTitle,
                            scannedCount = 0,
                            totalCount = walkedFiles.size.coerceAtLeast(scannedPaths.size),
                            statusLabel = "Indexing ${scannedPaths.size} new audio file${if (scannedPaths.size == 1) "" else "s"}…"
                        )
                    }
                    val scannedMap = mediaFileScanner.scanPaths(scannedPaths)
                    scannedMap.forEach { (normPath, uri) ->
                        scannedUriByLowerPath[normPath.lowercase(Locale.ROOT)] = uri
                    }
                }

                // Step 3: Stage 1 single batched MediaStore query strictly scoped to selected folders
                val coveredLowerPaths = HashSet<String>()
                val seenMediaStoreUris = HashSet<String>()
                val pendingUpserts = LinkedHashMap<String, TrackEntity>()
                val chunkBuffer = ArrayList<TrackEntity>(BATCH_SIZE)

                val mediaStoreQueried = queryMediaStoreStage1(
                    normalizedFolders = normalizedFolders,
                    scanAllFolders = scanAllFolders,
                    showProgress = showProgress,
                    scopeTitle = scopeTitle,
                    existingById = existingById,
                    existingByUri = existingByUri,
                    existingByNormPath = existingByNormPath,
                    knownDedupeKeys = knownDedupeKeys,
                    coveredLowerPaths = coveredLowerPaths,
                    seenMediaStoreUris = seenMediaStoreUris,
                    onEntityReady = { entity, isNew ->
                        pendingUpserts[entity.id] = entity
                        if (isNew) {
                            if (firstAddedId == null) firstAddedId = entity.id
                            added++
                        } else {
                            duplicates++
                        }
                        chunkBuffer += entity
                        if (chunkBuffer.size >= BATCH_SIZE) {
                            trackDao.upsertTracksAtomic(chunkBuffer.toList())
                            chunkBuffer.clear()
                        }
                    },
                    onRowFailed = { failed++ }
                )

                // Step 3b: Zero-delay direct-file metadata fallback for newly scanned files not yet in MediaStore
                val laggingFiles = newOrModifiedFiles.filter { walked ->
                    walked.normalizedPath.lowercase(Locale.ROOT) !in coveredLowerPaths
                }
                val fallbackTotal = laggingFiles.size
                for ((idx, walked) in laggingFiles.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    if (showProgress && (idx == 0 || idx % 8 == 0 || idx == fallbackTotal - 1)) {
                        _scanState.value = ScanState(
                            isScanning = true,
                            currentFolderName = walked.folder.displayName,
                            currentFileName = cleanAudioFileName(walked.file.name),
                            scannedCount = idx + 1,
                            totalCount = fallbackTotal,
                            addedCount = added,
                            statusLabel = "Adding ${idx + 1} of $fallbackTotal tracks…"
                        )
                    }
                    val lowerPath = walked.normalizedPath.lowercase(Locale.ROOT)
                    val scannedContentUri = scannedUriByLowerPath[lowerPath]
                    val existing = existingByNormPath[lowerPath]
                        ?: scannedContentUri?.let { existingByUri[it.toString()] }
                        ?: existingByUri[Uri.fromFile(walked.file).toString()]

                    val entity = buildEntityFromDirectFile(
                        walked = walked,
                        scannedContentUri = scannedContentUri,
                        existing = existing
                    )
                    if (entity == null) {
                        failed++
                        continue
                    }
                    val isNew = existing == null
                    if (isNew && entity.dedupeKey in knownDedupeKeys) {
                        duplicates++
                        coveredLowerPaths += lowerPath
                        continue
                    }
                    knownDedupeKeys += entity.dedupeKey
                    coveredLowerPaths += lowerPath
                    pendingUpserts[entity.id] = entity
                    if (isNew) {
                        if (firstAddedId == null) firstAddedId = entity.id
                        added++
                    }
                }

                // Step 3c: SAF fallback for non-filesystem document provider trees
                for (safFolder in safOnlyFolders) {
                    currentCoroutineContext().ensureActive()
                    val safResult = scanSafFolderTreeInternal(
                        treeUri = Uri.parse(safFolder.rawUriOrPath),
                        minDurationSec = 0,
                        resetOnFinish = false,
                        showProgress = showProgress
                    )
                    added += safResult.addedCount
                    duplicates += safResult.skippedDuplicates
                    failed += safResult.failedCount
                    if (firstAddedId == null) firstAddedId = safResult.firstAddedTrackId
                }

                // Step 4: Determine deleted or out-of-scope tracks to prune atomically
                val idsToPrune = computeTracksToPrune(
                    existingEntities = existingEntities,
                    normalizedFolders = normalizedFolders,
                    scanAllFolders = scanAllFolders,
                    pruneOutOfScope = pruneOutOfScope,
                    walkedByLowerPath = walkedByLowerPath,
                    walkedFolderRoots = walkedFolderRoots,
                    mediaStoreQueried = mediaStoreQueried,
                    seenMediaStoreUris = seenMediaStoreUris,
                    upsertedIds = pendingUpserts.keys
                )
                pruned = idsToPrune.size

                // Commit remaining upserts and deletions in a single Room @Transaction
                val finalUpserts = if (chunkBuffer.isNotEmpty()) {
                    chunkBuffer.toList()
                } else {
                    emptyList()
                }
                val unsavedPending = pendingUpserts.values.filter { it.id !in finalUpserts.mapTo(HashSet()) { u -> u.id } }
                val atomicUpserts = (finalUpserts + unsavedPending).distinctBy { it.id }
                if (atomicUpserts.isNotEmpty() || idsToPrune.isNotEmpty()) {
                    trackDao.syncFolderTracks(upserts = atomicUpserts, deleteIds = idsToPrune)
                }

                val now = System.currentTimeMillis()
                savedFolders.forEach { folder ->
                    folderDao.upsertFolder(folder.copy(lastScannedAt = now))
                }
            } catch (_: SecurityException) {
                failed++
            } finally {
                if (showProgress) {
                    _scanState.value = ScanState(isScanning = false)
                }
            }

            ScanResult(
                addedCount = added,
                skippedDuplicates = duplicates,
                failedCount = failed,
                firstAddedTrackId = firstAddedId,
                prunedCount = pruned,
                mediaScannerInvokedPaths = scannedPaths
            )
        }
    }

    private suspend fun queryMediaStoreStage1(
        normalizedFolders: List<NormalizedSelectedFolder>,
        scanAllFolders: Boolean,
        showProgress: Boolean,
        scopeTitle: String,
        existingById: Map<String, TrackEntity>,
        existingByUri: Map<String, TrackEntity>,
        existingByNormPath: Map<String, TrackEntity>,
        knownDedupeKeys: MutableSet<String>,
        coveredLowerPaths: MutableSet<String>,
        seenMediaStoreUris: MutableSet<String>,
        onEntityReady: suspend (TrackEntity, Boolean) -> Unit,
        onRowFailed: () -> Unit
    ): Boolean {
        val (selection, selectionArgs) = FolderScopeMatcher.buildMediaStoreFolderSelection(
            selectedFolders = normalizedFolders,
            scanAllFolders = scanAllFolders,
            minDurationMs = FolderScopeMatcher.MIN_MEDIASTORE_DURATION_MS
        )
        if (selection == null) return false

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.VOLUME_NAME)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.BITRATE)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        val cursor = try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
        } catch (_: SecurityException) {
            return false
        } catch (_: Exception) {
            return false
        } ?: return false

        var queriedSuccessfully = false
        cursor.use { c ->
            queriedSuccessfully = true
            val totalRows = c.count.coerceAtLeast(0)
            val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
            if (idCol < 0) return true

            val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            @Suppress("DEPRECATION")
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val mimeCol = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val sizeCol = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val dateAddedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val dateModCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val displayCol = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            } else -1
            val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else -1
            val volumeCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME)
            } else -1
            val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                c.getColumnIndex(MediaStore.Audio.Media.GENRE)
            } else -1
            val bitrateCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                c.getColumnIndex(MediaStore.Audio.Media.BITRATE)
            } else -1

            var scanned = 0
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                scanned++
                val mediaId = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                )
                val uriStr = contentUri.toString()
                val rawDataPath = if (dataCol >= 0) c.getString(dataCol) else null
                val rawRelPath = if (relPathCol >= 0) c.getString(relPathCol) else null
                val rawVolume = if (volumeCol >= 0) c.getString(volumeCol) else null
                val displayName = if (displayCol >= 0) c.getString(displayCol).orEmpty() else ""

                // Skip hidden/trashed files
                if (displayName.startsWith(".") || displayName.startsWith(".trashed", ignoreCase = true)) {
                    continue
                }

                // Zero-trust defense-in-depth scope check in Kotlin
                if (!FolderScopeMatcher.isTrackInScope(
                        dataPath = rawDataPath,
                        relativePath = rawRelPath,
                        volumeName = rawVolume,
                        uriString = uriStr,
                        selectedFolders = normalizedFolders,
                        scanAllFolders = scanAllFolders
                    )
                ) {
                    continue
                }

                val durationMs = if (durationCol >= 0) c.getLong(durationCol).coerceAtLeast(0L) else 0L
                if (durationMs < FolderScopeMatcher.MIN_MEDIASTORE_DURATION_MS) {
                    continue
                }

                val mime = (if (mimeCol >= 0) c.getString(mimeCol) else null)
                    ?.takeIf { it.isNotBlank() }
                    ?: inferAudioMimeFromName(displayName)
                if (!isSupportedAudioMeta(mime, displayName)) {
                    continue
                }

                val title = (if (titleCol >= 0) c.getString(titleCol) else null)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: cleanAudioFileName(displayName)
                val folderName = (if (bucketCol >= 0) c.getString(bucketCol) else null)
                    ?.takeIf { it.isNotBlank() }
                    ?: rawDataPath?.let { File(it).parentFile?.name }
                    ?: rawRelPath?.trimEnd('/')?.substringAfterLast('/')
                    ?: "Music"

                if (showProgress && (scanned == 1 || scanned % 12 == 0 || scanned == totalRows)) {
                    _scanState.value = ScanState(
                        isScanning = true,
                        currentFolderName = folderName,
                        currentFileName = title,
                        scannedCount = scanned,
                        totalCount = totalRows,
                        addedCount = seenMediaStoreUris.size,
                        statusLabel = "Indexing $scanned of $totalRows in $scopeTitle…"
                    )
                }

                val normDataPath = rawDataPath?.takeIf { it.isNotBlank() }?.let(FolderScopeMatcher::normalizeAbsolutePath)
                if (normDataPath != null) {
                    coveredLowerPaths += normDataPath.lowercase(Locale.ROOT)
                }
                seenMediaStoreUris += uriStr

                val existing = existingByUri[uriStr]
                    ?: normDataPath?.let { existingByNormPath[it.lowercase(Locale.ROOT)] }
                    ?: existingById["ms_$mediaId"]

                val fileSize = if (sizeCol >= 0) c.getLong(sizeCol).coerceAtLeast(0L) else 0L
                val dateAddedSec = if (dateAddedCol >= 0) c.getLong(dateAddedCol) else 0L
                val dateModSec = if (dateModCol >= 0) c.getLong(dateModCol) else 0L
                val effectiveTimestampMs = maxOf(dateAddedSec, dateModSec) * 1000L

                // Skip unchanged already-indexed tracks immediately for sub-400ms startup
                if (existing != null &&
                    existing.localUriString == uriStr &&
                    existing.fileSizeBytes == fileSize &&
                    effectiveTimestampMs <= existing.dateAdded + 1_000L
                ) {
                    onEntityReady(existing, false)
                    continue
                }

                val artist = (if (artistCol >= 0) c.getString(artistCol) else null)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: "Unknown Artist"
                val album = (if (albumCol >= 0) c.getString(albumCol) else null)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: "Unknown Album"
                val albumId = if (albumIdCol >= 0) c.getLong(albumIdCol) else 0L
                val genre = (if (genreCol >= 0) c.getString(genreCol) else null)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty()
                val folderPath = normDataPath?.let { File(it).parent }
                    ?: rawRelPath?.trimEnd('/')?.takeIf { it.isNotBlank() }
                    ?: folderName

                val dedupeKey = TrackEntity.buildDedupeKey(title, artist, durationMs)
                if (existing == null && dedupeKey in knownDedupeKeys) {
                    continue
                }

                var bitrateKbps = if (bitrateCol >= 0) (c.getInt(bitrateCol) / 1000).coerceAtLeast(0) else 0
                if (bitrateKbps <= 0 && fileSize > 0L && durationMs >= 1000L) {
                    bitrateKbps = ((fileSize * 8L) / durationMs).toInt().coerceAtLeast(0)
                }
                val codec = resolveAudioCodecLabel(
                    codec = existing?.codec.orEmpty(),
                    mimeType = mime,
                    uriString = displayName.ifBlank { rawDataPath.orEmpty() }
                )
                val albumArtUri = if (albumId > 0L) {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()
                } else existing?.artworkPath

                // Stage 1: pure metadata mood/affinity (zero bitmap/extractor disk I/O!)
                val fastTags = LocalMoodAndAffinityEngine.classifyTrackMoods(
                    title = title,
                    album = album,
                    artist = artist,
                    genre = genre,
                    folderName = folderName,
                    bpm = existing?.bpm ?: 90,
                    codec = codec,
                    mimeType = mime,
                    bitDepth = existing?.bitDepth ?: 0,
                    sampleRateHz = existing?.sampleRateHz ?: 0,
                    bitrateKbps = bitrateKbps,
                    artworkColorArgb = existing?.artworkColorArgb ?: 0,
                    localUriString = uriStr
                )

                val baseEntity = TrackEntity(
                    id = existing?.id ?: "ms_$mediaId",
                    title = title,
                    artist = artist,
                    album = album,
                    genre = genre,
                    durationMs = durationMs,
                    bpm = existing?.bpm ?: 90,
                    artworkResId = null,
                    artworkPath = albumArtUri,
                    localUriString = uriStr,
                    folderName = folderName,
                    folderPath = folderPath,
                    mimeType = mime,
                    codec = codec,
                    bitrateKbps = bitrateKbps,
                    sampleRateHz = existing?.sampleRateHz ?: 0,
                    bitDepth = existing?.bitDepth ?: 0,
                    fileSizeBytes = fileSize,
                    dateAdded = effectiveTimestampMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    playCount = existing?.playCount ?: 0,
                    completedPlayCount = existing?.completedPlayCount ?: 0,
                    skipCount = existing?.skipCount ?: 0,
                    lastPlayedAt = existing?.lastPlayedAt ?: 0L,
                    isFavorite = existing?.isFavorite ?: false,
                    synthProfileName = null,
                    suggestedPaletteId = existing?.suggestedPaletteId ?: paletteForHash(uriStr.hashCode()).id,
                    isUserImported = true,
                    dedupeKey = dedupeKey,
                    moodTags = SmartMoodTag.encode(fastTags),
                    artworkColorArgb = existing?.artworkColorArgb ?: 0
                )
                val affinity = LocalMoodAndAffinityEngine.computeLocalAffinity(baseEntity.toDomain()).score
                knownDedupeKeys += dedupeKey
                onEntityReady(baseEntity.copy(affinityScore = affinity), existing == null)
            }
        }
        return queriedSuccessfully
    }

    private suspend fun buildEntityFromDirectFile(
        walked: WalkedAudioFile,
        scannedContentUri: Uri?,
        existing: TrackEntity?
    ): TrackEntity? {
        val meta = directMetadataReader.readFileMetadata(walked.file) ?: return null
        val uriStr = scannedContentUri?.toString() ?: Uri.fromFile(walked.file).toString()
        val folderPath = walked.file.parentFile?.absolutePath
            ?.let(FolderScopeMatcher::normalizeAbsolutePath)
            ?: walked.folder.absoluteDirPath
            ?: walked.folder.displayName
        val folderName = walked.file.parentFile?.name?.takeIf { it.isNotBlank() } ?: walked.folder.displayName

        val codec = resolveAudioCodecLabel(
            codec = "",
            mimeType = meta.mimeType,
            uriString = walked.file.name
        )
        val bitrateKbps = when {
            meta.bitrateKbps > 0 -> meta.bitrateKbps
            walked.sizeBytes > 0L && meta.durationMs >= 1000L ->
                ((walked.sizeBytes * 8L) / meta.durationMs).toInt().coerceAtLeast(0)
            else -> 0
        }
        val cachedArt = if (meta.embeddedArtworkBytes != null) {
            artworkCache.cacheArtworkBytesWithColor(meta.embeddedArtworkBytes)
        } else null

        val tags = LocalMoodAndAffinityEngine.classifyTrackMoods(
            title = meta.title,
            album = meta.album,
            artist = meta.artist,
            genre = meta.genre,
            folderName = folderName,
            bpm = existing?.bpm ?: 90,
            codec = codec,
            mimeType = meta.mimeType,
            bitDepth = meta.bitDepth,
            sampleRateHz = meta.sampleRateHz,
            bitrateKbps = bitrateKbps,
            artworkColorArgb = cachedArt?.dominantColorArgb ?: existing?.artworkColorArgb ?: 0,
            localUriString = uriStr
        )

        val baseEntity = TrackEntity(
            id = existing?.id ?: "local_${sha256Id(walked.normalizedPath)}",
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            genre = meta.genre,
            durationMs = meta.durationMs,
            bpm = existing?.bpm ?: 90,
            artworkResId = null,
            artworkPath = cachedArt?.path ?: existing?.artworkPath,
            localUriString = uriStr,
            folderName = folderName,
            folderPath = folderPath,
            mimeType = meta.mimeType,
            codec = codec,
            bitrateKbps = bitrateKbps,
            sampleRateHz = meta.sampleRateHz.takeIf { it > 0 } ?: (existing?.sampleRateHz ?: 0),
            bitDepth = meta.bitDepth.takeIf { it > 0 } ?: (existing?.bitDepth ?: 0),
            fileSizeBytes = walked.sizeBytes,
            dateAdded = walked.lastModifiedMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            playCount = existing?.playCount ?: 0,
            completedPlayCount = existing?.completedPlayCount ?: 0,
            skipCount = existing?.skipCount ?: 0,
            lastPlayedAt = existing?.lastPlayedAt ?: 0L,
            isFavorite = existing?.isFavorite ?: false,
            synthProfileName = null,
            suggestedPaletteId = existing?.suggestedPaletteId ?: paletteForHash(uriStr.hashCode()).id,
            isUserImported = true,
            dedupeKey = TrackEntity.buildDedupeKey(meta.title, meta.artist, meta.durationMs),
            moodTags = SmartMoodTag.encode(tags),
            artworkColorArgb = cachedArt?.dominantColorArgb ?: existing?.artworkColorArgb ?: 0
        )
        val affinity = LocalMoodAndAffinityEngine.computeLocalAffinity(baseEntity.toDomain()).score
        return baseEntity.copy(affinityScore = affinity)
    }

    private fun computeTracksToPrune(
        existingEntities: List<TrackEntity>,
        normalizedFolders: List<NormalizedSelectedFolder>,
        scanAllFolders: Boolean,
        pruneOutOfScope: Boolean,
        walkedByLowerPath: Map<String, WalkedAudioFile>,
        walkedFolderRoots: Set<String>,
        mediaStoreQueried: Boolean,
        seenMediaStoreUris: Set<String>,
        upsertedIds: Set<String>
    ): List<String> {
        val idsToPrune = ArrayList<String>()
        for (entity in existingEntities) {
            if (!entity.isUserImported) continue
            if (entity.id in upsertedIds) continue
            val uriStr = entity.localUriString ?: continue

            // 1. Prune tracks outside the user's selected folders (excluding ad-hoc single-file imports)
            if (pruneOutOfScope && !scanAllFolders && entity.folderPath != "imported") {
                val inScope = FolderScopeMatcher.isPersistedTrackInScope(
                    folderPath = entity.folderPath,
                    localUriString = uriStr,
                    selectedFolders = normalizedFolders,
                    scanAllFolders = false
                )
                if (!inScope) {
                    idsToPrune += entity.id
                    continue
                }
            }

            // 2. Check direct filesystem existence for file:// tracks or tracks under walked folders
            val directPath = resolveExistingFilePath(entity)
            if (directPath != null) {
                val lowerPath = directPath.lowercase(Locale.ROOT)
                if (lowerPath !in walkedByLowerPath && !File(directPath).exists()) {
                    idsToPrune += entity.id
                    continue
                }
            }

            // 3. Check content:// MediaStore / SAF tracks that weren't seen in the fresh query
            if (uriStr.startsWith("content://", ignoreCase = true)) {
                val isMediaStoreUri = uriStr.startsWith(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString())
                if (isMediaStoreUri && mediaStoreQueried && uriStr !in seenMediaStoreUris) {
                    // Confirm not readable or moved out of scope
                    val stillReadable = runCatching {
                        context.contentResolver.openAssetFileDescriptor(Uri.parse(uriStr), "r")?.use { true } ?: false
                    }.getOrDefault(false)
                    if (!stillReadable || (pruneOutOfScope && !scanAllFolders)) {
                        idsToPrune += entity.id
                        continue
                    }
                } else if (!isMediaStoreUri && walkedFolderRoots.isEmpty()) {
                    val exists = runCatching {
                        context.contentResolver.openAssetFileDescriptor(Uri.parse(uriStr), "r")?.use { true } ?: false
                    }.getOrDefault(false)
                    if (!exists) {
                        idsToPrune += entity.id
                    }
                }
            }
        }
        return idsToPrune
    }

    private fun walkDirectoryRecursively(
        rootDir: File,
        folder: NormalizedSelectedFolder,
        out: MutableList<WalkedAudioFile>
    ) {
        val stack = ArrayDeque<File>()
        stack.addLast(rootDir)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrNull().orEmpty()
            for (child in children) {
                if (child.isDirectory) {
                    if (!FolderScopeMatcher.isHiddenOrTrashedDirectory(child) && child.canRead()) {
                        stack.addLast(child)
                    }
                } else if (FolderScopeMatcher.isSupportedAudioFile(child)) {
                    out += WalkedAudioFile(
                        file = child,
                        normalizedPath = FolderScopeMatcher.normalizeAbsolutePath(child.absolutePath),
                        folder = folder,
                        lastModifiedMs = child.lastModified(),
                        sizeBytes = child.length()
                    )
                }
            }
        }
    }

    private fun resolveExistingFilePath(entity: TrackEntity): String? {
        val uriStr = entity.localUriString ?: return null
        if (uriStr.startsWith("file://", ignoreCase = true)) {
            return runCatching { Uri.parse(uriStr).path }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(FolderScopeMatcher::normalizeAbsolutePath)
        }
        return null
    }

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
                    val entity = extractSafTrackStage1(
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
                        trackDao.upsertTracksAtomic(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) {
                    trackDao.upsertTracksAtomic(batch.toList())
                    batch.clear()
                }
            } finally {
                _scanState.value = ScanState(isScanning = false)
            }
            ScanResult(added, duplicates, failed, firstAddedId)
        }
    }

    suspend fun registerSafFolder(treeUri: Uri) = withContext(Dispatchers.IO) {
        persistReadPermission(treeUri)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
        val normalized = FolderScopeMatcher.normalize(
            rawUriOrPath = treeUri.toString(),
            displayName = rootDoc?.name.orEmpty(),
            primaryExternalRoot = primaryExternalRootProvider()
        )
        folderDao.upsertFolder(
            SavedFolderEntity(
                treeUri = treeUri.toString(),
                displayName = normalized.displayName,
                lastScannedAt = 0L
            )
        )
    }

    suspend fun scanSafFolderTree(treeUri: Uri, minDurationSec: Int = 0): ScanResult {
        withContext(Dispatchers.IO) {
            persistReadPermission(treeUri)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            val normalized = FolderScopeMatcher.normalize(
                rawUriOrPath = treeUri.toString(),
                displayName = rootDoc?.name.orEmpty(),
                primaryExternalRoot = primaryExternalRootProvider()
            )
            folderDao.upsertFolder(
                SavedFolderEntity(
                    treeUri = treeUri.toString(),
                    displayName = normalized.displayName,
                    lastScannedAt = System.currentTimeMillis()
                )
            )
        }
        // Use the unified fast Stage 1 folder-scoped sync (walks filesystem + MediaScanner + MediaStore + SAF fallback)
        return syncSelectedFolders(
            scanAllFolders = false,
            showProgress = true,
            pruneOutOfScope = false
        )
    }

    suspend fun rescanAllSavedFolders(
        scanAllFolders: Boolean = false,
        showProgress: Boolean = true
    ): ScanResult = syncSelectedFolders(
        scanAllFolders = scanAllFolders,
        showProgress = showProgress,
        pruneOutOfScope = true
    )

    suspend fun scanMediaStoreAudio(scanAllFolders: Boolean = true): ScanResult =
        syncSelectedFolders(
            scanAllFolders = scanAllFolders,
            showProgress = true,
            pruneOutOfScope = !scanAllFolders
        )

    suspend fun pruneMissingLocalTracks(): Int = withContext(Dispatchers.IO) {
        val localTracks = trackDao.getAllTracksOnce().filter { it.isUserImported && it.localUriString != null }
        val missingIds = mutableListOf<String>()
        for (track in localTracks) {
            val uriStr = track.localUriString ?: continue
            val exists = if (uriStr.startsWith("file://", ignoreCase = true)) {
                val path = runCatching { Uri.parse(uriStr).path }.getOrNull()
                !path.isNullOrBlank() && File(path).exists()
            } else {
                runCatching {
                    context.contentResolver.openAssetFileDescriptor(Uri.parse(uriStr), "r")?.use { true } ?: false
                }.getOrDefault(false)
            }
            if (!exists) {
                missingIds += track.id
            }
        }
        if (missingIds.isNotEmpty()) {
            trackDao.syncFolderTracks(upserts = emptyList(), deleteIds = missingIds)
        }
        missingIds.size
    }

    private suspend fun scanSafFolderTreeInternal(
        treeUri: Uri,
        minDurationSec: Int = 0,
        resetOnFinish: Boolean = true,
        showProgress: Boolean = true
    ): ScanResult = withContext(Dispatchers.IO) {
        val minDurationMs = (minDurationSec * 1000L).coerceAtLeast(FolderScopeMatcher.MIN_MEDIASTORE_DURATION_MS)
        persistReadPermission(treeUri)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ScanResult(0, 0, 1)

        val rootName = rootDoc.name?.takeIf { it.isNotBlank() } ?: "Music Folder"
        if (showProgress) {
            _scanState.value = ScanState(
                isScanning = true,
                currentFolderName = rootName,
                statusLabel = "Discovering audio files in $rootName…"
            )
        }
        var added = 0
        var duplicates = 0
        var failed = 0
        var firstAddedId: String? = null
        val batch = ArrayList<TrackEntity>(BATCH_SIZE)

        try {
            val existingTuples = trackDao.getTrackDedupeTuples()
            val knownUris = existingTuples.mapNotNullTo(HashSet(existingTuples.size)) { it.localUriString }
            val knownDedupeKeys = existingTuples.mapTo(HashSet(existingTuples.size)) { it.dedupeKey }

            val discovered = ArrayList<DiscoveredSafFile>()
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
                            discovered += DiscoveredSafFile(
                                uri = childUri,
                                folderName = currentFolder,
                                folderPath = currentPath,
                                displayName = childName,
                                mimeType = childMime,
                                fileSize = childSize
                            )
                        }
                    }
                }
            } else {
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
                        if (!isSupportedAudioMeta(child.type, child.name)) continue
                        discovered += DiscoveredSafFile(
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

            val totalFiles = discovered.size
            for ((index, candidate) in discovered.withIndex()) {
                currentCoroutineContext().ensureActive()
                val processed = index + 1
                if (showProgress && (processed == 1 || processed % 4 == 0 || processed == totalFiles)) {
                    _scanState.value = ScanState(
                        isScanning = true,
                        currentFolderName = candidate.folderName,
                        currentFileName = candidate.displayName?.let(::cleanAudioFileName).orEmpty(),
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

                val entity = extractSafTrackStage1(
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
                    trackDao.upsertTracksAtomic(batch.toList())
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                trackDao.upsertTracksAtomic(batch.toList())
                batch.clear()
            }
        } finally {
            if (resetOnFinish && showProgress) {
                _scanState.value = ScanState(isScanning = false)
            }
        }
        ScanResult(added, duplicates, failed, firstAddedId)
    }

    private suspend fun extractSafTrackStage1(
        uri: Uri,
        folderName: String,
        folderPath: String,
        knownFileName: String? = null,
        knownMime: String? = null,
        knownSize: Long = 0L,
        minDurationMs: Long = FolderScopeMatcher.MIN_MEDIASTORE_DURATION_MS
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
            ?: inferAudioMimeFromName(displayName.orEmpty())

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

            val durationMs = rawDuration?.toLongOrNull() ?: 0L
            if (durationMs < minDurationMs) return null

            val title = rawTitle?.takeIf { it.isNotBlank() } ?: cleanAudioFileName(displayName.orEmpty())
            val artist = rawArtist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val album = rawAlbum?.takeIf { it.isNotBlank() } ?: folderName
            val genre = rawGenre?.takeIf { it.isNotBlank() }.orEmpty()
            val effectiveMime = rawMime?.takeIf { it.isNotBlank() } ?: mimeType
            var bitrateKbps = ((rawBitrate?.toIntOrNull() ?: 0) / 1000).coerceAtLeast(0)
            val codec = resolveAudioCodecLabel(
                codec = "",
                mimeType = effectiveMime,
                uriString = displayName ?: uri.toString()
            )
            val embeddedArtBytes = retriever.embeddedPicture
            if (bitrateKbps <= 0 && fileSize > 0L && durationMs >= 1000L) {
                val audioBytes = (fileSize - (embeddedArtBytes?.size?.toLong() ?: 0L)).coerceAtLeast(0L)
                if (audioBytes > 0L) {
                    bitrateKbps = ((audioBytes * 8L) / durationMs).toInt().coerceAtLeast(0)
                }
            }
            val cachedArt = artworkCache.cacheArtworkBytesWithColor(embeddedArtBytes)
            val uriStr = uri.toString()

            val computedTags = LocalMoodAndAffinityEngine.classifyTrackMoods(
                title = title,
                album = album,
                artist = artist,
                genre = genre,
                folderName = folderName,
                bpm = 90,
                codec = codec,
                mimeType = effectiveMime,
                bitDepth = 0,
                sampleRateHz = 0,
                bitrateKbps = bitrateKbps,
                artworkColorArgb = cachedArt.dominantColorArgb,
                localUriString = uriStr
            )

            val baseEntity = TrackEntity(
                id = "local_${sha256Id(uriStr)}",
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                durationMs = durationMs,
                bpm = 90,
                artworkResId = null,
                artworkPath = cachedArt.path,
                localUriString = uriStr,
                folderName = folderName,
                folderPath = folderPath,
                mimeType = effectiveMime,
                codec = codec,
                bitrateKbps = bitrateKbps,
                sampleRateHz = 0,
                bitDepth = 0,
                fileSizeBytes = fileSize.coerceAtLeast(0L),
                dateAdded = System.currentTimeMillis(),
                playCount = 0,
                lastPlayedAt = 0L,
                isFavorite = false,
                synthProfileName = null,
                suggestedPaletteId = paletteForHash(uriStr.hashCode()).id,
                isUserImported = true,
                dedupeKey = TrackEntity.buildDedupeKey(title, artist, durationMs),
                moodTags = SmartMoodTag.encode(computedTags),
                artworkColorArgb = cachedArt.dominantColorArgb
            )
            val initialAffinity = LocalMoodAndAffinityEngine.computeLocalAffinity(baseEntity.toDomain()).score
            baseEntity.copy(affinityScore = initialAffinity)
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

    private fun isSupportedAudioMeta(mimeType: String?, displayName: String?): Boolean {
        val name = displayName.orEmpty()
        if (name.startsWith(".") || name.startsWith(".trashed", ignoreCase = true)) return false
        val mime = mimeType?.lowercase(Locale.ROOT).orEmpty()
        if (mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in FolderScopeMatcher.DIRECT_WALK_EXTENSIONS
    }

    private fun paletteForHash(hash: Int): ExpressivePalette {
        val choices = listOf(
            ExpressivePalette.BERRY,
            ExpressivePalette.MATCHA,
            ExpressivePalette.COBALT,
            ExpressivePalette.CITRUS,
            ExpressivePalette.ARCTIC_CYAN,
            ExpressivePalette.PEACH_BLOSSOM,
            ExpressivePalette.WISTERIA_MIST,
            ExpressivePalette.SPEARMINT_FIZZ,
            ExpressivePalette.OBSIDIAN_MONO
        )
        return choices[(hash and 0x7FFFFFFF) % choices.size]
    }

    private fun sha256Id(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val BATCH_SIZE = 50
    }
}
