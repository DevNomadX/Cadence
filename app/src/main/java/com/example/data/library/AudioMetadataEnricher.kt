package com.example.data.library

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import com.example.data.Track
import com.example.data.db.TrackDao
import com.example.data.db.TrackEntity
import com.example.data.intelligence.LocalMoodAndAffinityEngine
import com.example.data.intelligence.SmartMoodTag
import com.example.data.resolveAudioCodecLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Triggers Android's `MediaScannerConnection.scanFile` for newly discovered files so
 * `MediaStore` indexes them immediately. Injectable for deterministic unit tests.
 */
fun interface MediaFileScanner {
    suspend fun scanPaths(paths: List<String>): Map<String, Uri?>
}

class AndroidMediaFileScanner(
    private val context: Context,
    private val timeoutMs: Long = DEFAULT_SCAN_TIMEOUT_MS
) : MediaFileScanner {

    override suspend fun scanPaths(paths: List<String>): Map<String, Uri?> {
        if (paths.isEmpty()) return emptyMap()
        val results = ConcurrentHashMap<String, Uri?>()
        val remaining = AtomicInteger(paths.size)

        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                try {
                    MediaScannerConnection.scanFile(
                        context.applicationContext,
                        paths.toTypedArray(),
                        null
                    ) { scannedPath, uri ->
                        if (scannedPath != null) {
                            results[FolderScopeMatcher.normalizeAbsolutePath(scannedPath)] = uri
                        }
                        if (remaining.decrementAndGet() <= 0 && cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                } catch (_: Exception) {
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }
            }
        }
        return results.toMap()
    }

    companion object {
        const val DEFAULT_SCAN_TIMEOUT_MS: Long = 3_000L
    }
}

data class DirectAudioFileMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val mimeType: String,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val bitDepth: Int,
    val embeddedArtworkBytes: ByteArray? = null
)

/**
 * Fallback direct metadata reader used when `MediaStore` lags behind a newly copied/downloaded
 * file inside one of the user's selected folders.
 */
fun interface DirectAudioMetadataReader {
    fun readFileMetadata(file: File): DirectAudioFileMetadata?
}

class AndroidDirectAudioMetadataReader : DirectAudioMetadataReader {
    override fun readFileMetadata(file: File): DirectAudioFileMetadata? {
        if (!file.exists() || !file.isFile) return null
        val fallbackTitle = cleanAudioFileName(file.name)
        val fallbackAlbum = file.parentFile?.name?.takeIf { it.isNotBlank() } ?: "Music"
        val inferredMime = inferAudioMimeFromName(file.name)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.takeIf { it.isNotBlank() }
                ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: fallbackAlbum
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() }.orEmpty()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val bitrateKbps = ((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0) / 1000).coerceAtLeast(0)
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?.takeIf { it.isNotBlank() } ?: inferredMime
            val sampleRateHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            } else 0
            val bitDepth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            } else 0
            val artBytes = runCatching { retriever.embeddedPicture }.getOrNull()

            if (durationMs in 1L until FolderScopeMatcher.MIN_MEDIASTORE_DURATION_MS) {
                return null
            }

            DirectAudioFileMetadata(
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                durationMs = if (durationMs > 0L) durationMs else estimateDurationFromSize(file.length()),
                mimeType = mime,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
                bitDepth = bitDepth,
                embeddedArtworkBytes = artBytes
            )
        } catch (_: Exception) {
            // If MediaMetadataRetriever fails (e.g., partial/unsupported header), still surface
            // valid non-empty audio files cleanly with filename metadata so playback/re-probe works.
            if (file.length() <= 0L) return null
            DirectAudioFileMetadata(
                title = fallbackTitle,
                artist = "Unknown Artist",
                album = fallbackAlbum,
                genre = "",
                durationMs = estimateDurationFromSize(file.length()),
                mimeType = inferredMime,
                bitrateKbps = 0,
                sampleRateHz = 0,
                bitDepth = 0,
                embeddedArtworkBytes = null
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun estimateDurationFromSize(bytes: Long): Long {
        if (bytes <= 0L) return FolderScopeMatcher.MIN_MEDIASTORE_DURATION_MS
        // Approximate 192 kbps (24 KB/s) with a 10s floor so newly written files appear immediately
        val estMs = (bytes / 24L).coerceAtLeast(FolderScopeMatcher.MIN_MEDIASTORE_DURATION_MS)
        return estMs
    }
}

data class ProbedAudioFormat(
    val codec: String,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val bitDepth: Int
)

/**
 * Stage 2 background audio enrichment:
 * Runs asynchronously on `Dispatchers.IO` in small batches after Stage 1 opens the UI,
 * probing sample rate, bit depth, container codec (ALAC/AAC/FLAC), dominant artwork color,
 * and local mood/affinity intelligence without delaying startup.
 */
class AudioMetadataEnricher(
    private val context: Context,
    private val trackDao: TrackDao,
    private val artworkCache: ArtworkCacheManager
) {
    private val enrichMutex = Mutex()

    suspend fun enrichPendingTracks(batchSize: Int = DEFAULT_BATCH_SIZE): Int = enrichMutex.withLock {
        withContext(Dispatchers.IO) {
            val allEntities = trackDao.getAllTracksOnce()
            val pending = allEntities.filter { entity ->
                entity.isUserImported && needsEnrichment(entity)
            }
            if (pending.isEmpty()) return@withContext 0

            val nowMs = System.currentTimeMillis()
            val localHour = LocalMoodAndAffinityEngine.currentLocalHour(nowMs)
            val maxPlays = allEntities.maxOfOrNull { it.playCount.coerceAtLeast(0) }?.coerceAtLeast(1) ?: 1
            var enrichedCount = 0

            for (batch in pending.chunked(batchSize.coerceAtLeast(1))) {
                currentCoroutineContext().ensureActive()
                for (entity in batch) {
                    currentCoroutineContext().ensureActive()
                    val uri = entity.localUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
                    var codec = entity.codec
                    var bitrateKbps = entity.bitrateKbps
                    var sampleRateHz = entity.sampleRateHz
                    var bitDepth = entity.bitDepth
                    var artworkPath = entity.artworkPath
                    var artworkColorArgb = entity.artworkColorArgb

                    val needsFormatProbe =
                        sampleRateHz == 0 ||
                            (bitDepth == 0 && (codec in Track.LOSSLESS_CODECS || codec == "M4A")) ||
                            codec == "M4A" ||
                            codec.isBlank() ||
                            bitrateKbps <= 0
                    if (needsFormatProbe && uri != null) {
                        val probed = probeAudioFormatWithExtractor(context, uri)
                        if (probed != null) {
                            if (probed.codec.isNotBlank()) codec = probed.codec
                            if (probed.bitrateKbps > 0 && bitrateKbps <= 0) bitrateKbps = probed.bitrateKbps
                            if (probed.sampleRateHz > 0 && sampleRateHz <= 0) sampleRateHz = probed.sampleRateHz
                            if (probed.bitDepth > 0 && bitDepth <= 0) bitDepth = probed.bitDepth
                        }
                    }

                    if (bitrateKbps <= 0 && entity.fileSizeBytes > 0L && entity.durationMs >= 1000L) {
                        bitrateKbps = ((entity.fileSizeBytes * 8L) / entity.durationMs).toInt().coerceAtLeast(0)
                    }

                    if (artworkColorArgb == 0 && !artworkPath.isNullOrBlank()) {
                        artworkColorArgb = artworkCache.extractDominantColorFromPath(artworkPath)
                    }

                    val (encodedTags, affinity) = withContext(Dispatchers.Default) {
                        val updatedDomain = entity.copy(
                            codec = codec,
                            bitrateKbps = bitrateKbps,
                            sampleRateHz = sampleRateHz,
                            bitDepth = bitDepth,
                            artworkPath = artworkPath,
                            artworkColorArgb = artworkColorArgb
                        ).toDomain()
                        val tags = LocalMoodAndAffinityEngine.classifyTrackMoods(updatedDomain)
                        val encoded = SmartMoodTag.encode(tags)
                        val score = LocalMoodAndAffinityEngine.computeLocalAffinity(
                            track = updatedDomain.copy(moodTags = SmartMoodTag.decode(encoded)),
                            nowMs = nowMs,
                            localHourOfDay = localHour,
                            maxLibraryPlays = maxPlays
                        ).score
                        encoded to score
                    }

                    trackDao.updateEnrichedAudioMetadata(
                        trackId = entity.id,
                        codec = codec,
                        bitrateKbps = bitrateKbps,
                        sampleRateHz = sampleRateHz,
                        bitDepth = bitDepth,
                        artworkPath = artworkPath,
                        artworkColorArgb = artworkColorArgb,
                        moodTags = encodedTags,
                        affinityScore = affinity
                    )
                    enrichedCount++
                }
                // Yield between small batches so playback and UI frames remain priority
                yield()
            }
            enrichedCount
        }
    }

    private fun needsEnrichment(entity: TrackEntity): Boolean =
        entity.sampleRateHz == 0 ||
            (entity.bitDepth == 0 && (entity.codec in Track.LOSSLESS_CODECS || entity.codec == "M4A")) ||
            entity.codec.isBlank() ||
            entity.codec == "M4A" ||
            entity.moodTags.isBlank() ||
            (entity.artworkColorArgb == 0 && !entity.artworkPath.isNullOrBlank())

    companion object {
        const val DEFAULT_BATCH_SIZE: Int = 12

        fun probeAudioFormatWithExtractor(context: Context, uri: Uri): ProbedAudioFormat? {
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
    }
}

internal fun cleanAudioFileName(name: String): String =
    name.substringBeforeLast('.')
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "Untitled Track" }

internal fun inferAudioMimeFromName(name: String): String =
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
