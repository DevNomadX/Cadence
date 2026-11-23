package com.example.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.example.data.StableList
import com.example.data.Track
import com.example.data.toStableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

@Keep
@Immutable
data class LyricLine(
    val timestampMs: Long? = null,
    val text: String
)

@Keep
@Immutable
data class TrackLyrics(
    val trackId: String,
    val lines: StableList<LyricLine> = StableList(),
    val isSynced: Boolean = false,
    val sourceLabel: String = ""
)

@Keep
@Immutable
sealed interface LyricsUiState {
    @Keep
    @Immutable
    data object Idle : LyricsUiState

    @Keep
    @Immutable
    data class Loading(val trackId: String) : LyricsUiState

    @Keep
    @Immutable
    data class Available(val lyrics: TrackLyrics) : LyricsUiState

    @Keep
    @Immutable
    data class Empty(val trackId: String, val trackTitle: String) : LyricsUiState
}

/**
 * Offline lyrics loader that runs strictly on Dispatchers.IO.
 * Lookup order:
 * 1. Matching sidecar `.lrc` file (for file:// paths or sibling SAF/file URIs)
 * 2. Embedded lyrics tags inside the audio file (ID3v2 USLT, Vorbis LYRICS/UNSYNCEDLYRICS, MP4 ©lyr)
 */
object OfflineLyricsLoader {

    private const val MAX_HEADER_SCAN_BYTES = 512 * 1024 // 512 KB cap for low-RAM safety
    private val LRC_TAG_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val LRC_META_REGEX = Regex("""^\[(ti|ar|al|au|by|offset|re|ve|length):.*]$""", RegexOption.IGNORE_CASE)

    suspend fun loadLyricsForTrack(context: Context, track: Track): TrackLyrics? =
        withContext(Dispatchers.IO) {
            val uriString = track.localUriString?.takeIf { it.isNotBlank() } ?: return@withContext null

            // 1. Sidecar .lrc file lookup
            readSidecarLrc(context, uriString)?.let { rawLrc ->
                val parsed = parseLyricsText(rawLrc, track.id, "Sidecar .lrc")
                if (parsed.lines.isNotEmpty()) return@withContext parsed
            }

            // 2. Embedded lyrics tag scan (bounded read)
            readEmbeddedLyrics(context, uriString)?.let { rawEmbedded ->
                val parsed = parseLyricsText(rawEmbedded, track.id, "Embedded tag")
                if (parsed.lines.isNotEmpty()) return@withContext parsed
            }

            null
        }

    fun parseLyricsText(rawText: String, trackId: String, sourceLabel: String = ""): TrackLyrics {
        val cleaned = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (cleaned.isEmpty()) {
            return TrackLyrics(trackId = trackId, lines = StableList(), isSynced = false, sourceLabel = sourceLabel)
        }

        val timedLines = ArrayList<LyricLine>()
        val plainLines = ArrayList<LyricLine>()

        cleaned.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || LRC_META_REGEX.matches(trimmed)) return@forEach

            val matches = LRC_TAG_REGEX.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lyricBody = LRC_TAG_REGEX.replace(trimmed, "").trim()
                if (lyricBody.isNotEmpty()) {
                    matches.forEach { match ->
                        val min = match.groupValues[1].toLongOrNull() ?: 0L
                        val sec = match.groupValues[2].toLongOrNull() ?: 0L
                        val fracRaw = match.groupValues.getOrNull(3).orEmpty()
                        val ms = when (fracRaw.length) {
                            1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                            2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                            3 -> fracRaw.toLongOrNull() ?: 0L
                            else -> 0L
                        }
                        timedLines += LyricLine(
                            timestampMs = min * 60_000L + sec * 1_000L + ms,
                            text = lyricBody
                        )
                    }
                }
            } else {
                plainLines += LyricLine(timestampMs = null, text = trimmed)
            }
        }

        return if (timedLines.isNotEmpty()) {
            TrackLyrics(
                trackId = trackId,
                lines = timedLines.sortedBy { it.timestampMs ?: 0L }.toStableList(),
                isSynced = true,
                sourceLabel = sourceLabel.ifBlank { "Synced .lrc" }
            )
        } else {
            TrackLyrics(
                trackId = trackId,
                lines = plainLines.toStableList(),
                isSynced = false,
                sourceLabel = sourceLabel.ifBlank { "Lyrics" }
            )
        }
    }

    private fun readSidecarLrc(context: Context, uriString: String): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null

        // Direct file:// or absolute path
        val directPath = when {
            uri.scheme == "file" -> uri.path
            uri.scheme == null -> uriString
            else -> null
        }
        if (!directPath.isNullOrBlank()) {
            val audioFile = File(directPath)
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcFile.isFile && lrcFile.canRead() && lrcFile.length() in 1L..262_144L) {
                return runCatching { lrcFile.readText(Charsets.UTF_8) }.getOrNull()
            }
        }

        // SAF content:// sibling lookup if the document ID carries a file path with extension
        if (uri.scheme == "content" && DocumentsContract.isDocumentUri(context, uri)) {
            runCatching {
                val docId = DocumentsContract.getDocumentId(uri)
                val dotIdx = docId.lastIndexOf('.')
                if (dotIdx > 0) {
                    val lrcDocId = docId.substring(0, dotIdx) + ".lrc"
                    val lrcUri = DocumentsContract.buildDocumentUriUsingTree(uri, lrcDocId)
                    context.contentResolver.openInputStream(lrcUri)?.use { stream ->
                        return readBoundedText(stream, 262_144)
                    }
                }
            }
        }

        return null
    }

    private fun readEmbeddedLyrics(context: Context, uriString: String): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val bytes = runCatching {
            val stream: InputStream? = if (uri.scheme == "file" || uri.scheme == null) {
                val f = File(uri.path ?: uriString)
                if (f.isFile && f.canRead()) f.inputStream() else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { readBoundedBytes(it, MAX_HEADER_SCAN_BYTES) }
        }.getOrNull() ?: return null

        if (bytes.size < 16) return null

        // 1. ID3v2 header (MP3 / AIFF)
        if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            extractId3v2Uslt(bytes)?.let { return it }
        }

        // 2. FLAC / OGG / Opus Vorbis Comments (LYRICS= or UNSYNCEDLYRICS=)
        extractVorbisLyrics(bytes)?.let { return it }

        // 3. MP4 / M4A ©lyr atom
        extractMp4LyricsAtom(bytes)?.let { return it }

        return null
    }

    internal fun extractId3v2Uslt(bytes: ByteArray): String? {
        if (bytes.size < 10) return null
        val majorVersion = bytes[3].toInt() and 0xFF
        if (majorVersion < 3 || majorVersion > 4) return null

        val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        val limit = minOf(bytes.size, 10 + tagSize)
        var pos = 10

        while (pos + 10 <= limit) {
            val frameId = String(bytes, pos, 4, Charsets.ISO_8859_1)
            if (frameId[0] == '\u0000') break

            val frameSize = if (majorVersion == 4) {
                ((bytes[pos + 4].toInt() and 0x7F) shl 21) or
                    ((bytes[pos + 5].toInt() and 0x7F) shl 14) or
                    ((bytes[pos + 6].toInt() and 0x7F) shl 7) or
                    (bytes[pos + 7].toInt() and 0x7F)
            } else {
                ((bytes[pos + 4].toInt() and 0xFF) shl 24) or
                    ((bytes[pos + 5].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 6].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 7].toInt() and 0xFF)
            }

            if (frameSize <= 0 || pos + 10 + frameSize > bytes.size) break

            if (frameId == "USLT" && frameSize > 4) {
                val payloadStart = pos + 10
                val encodingByte = bytes[payloadStart].toInt() and 0xFF
                // Skip encoding (1 byte) + language (3 bytes)
                val descStart = payloadStart + 4
                val payloadEnd = payloadStart + frameSize
                val (charset, isDoubleNull) = when (encodingByte) {
                    1 -> Charsets.UTF_16 to true
                    2 -> Charsets.UTF_16BE to true
                    3 -> Charsets.UTF_8 to false
                    else -> Charsets.ISO_8859_1 to false
                }
                val textStart = skipNullTerminatedDescription(bytes, descStart, payloadEnd, isDoubleNull)
                if (textStart < payloadEnd) {
                    val decoded = decodeSafe(bytes, textStart, payloadEnd - textStart, charset).trim()
                    if (decoded.isNotEmpty()) return decoded
                }
            }

            pos += 10 + frameSize
        }
        return null
    }

    private fun skipNullTerminatedDescription(
        bytes: ByteArray,
        start: Int,
        end: Int,
        isDoubleNull: Boolean
    ): Int {
        var i = start
        if (isDoubleNull) {
            while (i + 1 < end) {
                if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) {
                    return i + 2
                }
                i += 2
            }
        } else {
            while (i < end) {
                if (bytes[i] == 0.toByte()) {
                    return i + 1
                }
                i++
            }
        }
        return start
    }

    internal fun extractVorbisLyrics(bytes: ByteArray): String? {
        val keys = listOf("LYRICS=", "UNSYNCEDLYRICS=", "lyrics=", "unsyncedlyrics=")
        for (key in keys) {
            val keyBytes = key.toByteArray(Charsets.US_ASCII)
            val idx = indexOfSubarray(bytes, keyBytes)
            if (idx >= 4) {
                // Vorbis comment entries are prefixed by a 4-byte little-endian length
                val len = (bytes[idx - 4].toInt() and 0xFF) or
                    ((bytes[idx - 3].toInt() and 0xFF) shl 8) or
                    ((bytes[idx - 2].toInt() and 0xFF) shl 16) or
                    ((bytes[idx - 1].toInt() and 0xFF) shl 24)
                val valueLen = len - keyBytes.size
                val valueStart = idx + keyBytes.size
                if (valueLen in 1..200_000 && valueStart + valueLen <= bytes.size) {
                    val text = decodeSafe(bytes, valueStart, valueLen, Charsets.UTF_8).trim()
                    if (text.isNotEmpty()) return text
                }
            }
        }
        return null
    }

    private fun extractMp4LyricsAtom(bytes: ByteArray): String? {
        // ©lyr = 0xA9, 'l', 'y', 'r'
        val atomTag = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val tagIdx = indexOfSubarray(bytes, atomTag)
        if (tagIdx < 0) return null

        val dataTag = "data".toByteArray(Charsets.US_ASCII)
        val searchEnd = minOf(bytes.size, tagIdx + 64)
        var dataIdx = -1
        for (i in tagIdx + 4..searchEnd - 4) {
            if (bytes[i] == dataTag[0] && bytes[i + 1] == dataTag[1] &&
                bytes[i + 2] == dataTag[2] && bytes[i + 3] == dataTag[3]
            ) {
                dataIdx = i
                break
            }
        }
        if (dataIdx < 4) return null

        val dataAtomSize = ((bytes[dataIdx - 4].toInt() and 0xFF) shl 24) or
            ((bytes[dataIdx - 3].toInt() and 0xFF) shl 16) or
            ((bytes[dataIdx - 2].toInt() and 0xFF) shl 8) or
            (bytes[dataIdx - 1].toInt() and 0xFF)
        val textStart = dataIdx + 12 // 'data'(4) + type/flags(4) + locale(4)
        val textLen = dataAtomSize - 16
        if (textLen in 1..200_000 && textStart + textLen <= bytes.size) {
            val text = decodeSafe(bytes, textStart, textLen, Charsets.UTF_8).trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    private fun indexOfSubarray(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        val maxStart = haystack.size - needle.size
        outer@ for (i in 0..maxStart) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun decodeSafe(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String {
        return runCatching {
            String(bytes, offset, length, charset).trimStart('\uFEFF')
        }.getOrDefault("")
    }

    private fun readBoundedBytes(stream: InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var remaining = maxBytes
        while (remaining > 0) {
            val read = stream.read(buf, 0, minOf(buf.size, remaining))
            if (read <= 0) break
            out.write(buf, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    private fun readBoundedText(stream: InputStream, maxBytes: Int): String =
        String(readBoundedBytes(stream, maxBytes), Charsets.UTF_8)
}
