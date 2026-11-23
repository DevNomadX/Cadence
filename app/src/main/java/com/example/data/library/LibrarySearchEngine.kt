package com.example.data.library

import com.example.data.PlaylistSummary
import com.example.data.Track
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

/**
 * Pre-normalized, immutable search entry for a single [Track].
 * Built once per Room emission on [kotlinx.coroutines.Dispatchers.Default] so subsequent
 * debounced keystrokes execute zero Unicode normalization allocations.
 */
data class IndexedTrack(
    val track: Track,
    val normTitle: String,
    val normArtist: String,
    val normAlbum: String,
    val normGenre: String,
    val normFilename: String,
    val normFolder: String,
    val combinedFields: String
)

/**
 * Fast, diacritic-insensitive, multi-token local library search & relevance ranking engine.
 *
 * - Normalizes whitespace, case, and Unicode diacritics ([Normalizer.Form.NFD]).
 * - Supports out-of-order multi-token queries across title, artist, album, genre, filename, and folder
 *   (e.g., `"arijit tum"` matches `"Tum Hi Ho"` by `"Arijit Singh"`).
 * - Ranks exact title matches, title prefix matches, and title phrase matches at the top while
 *   preserving relative order for ties.
 */
object LibrarySearchEngine {

    /**
     * Builds a pre-normalized index for [tracks]. Call on [kotlinx.coroutines.Dispatchers.Default].
     */
    fun buildIndex(tracks: List<Track>): List<IndexedTrack> {
        if (tracks.isEmpty()) return emptyList()
        return ArrayList<IndexedTrack>(tracks.size).apply {
            for (track in tracks) {
                val nTitle = normalize(track.title)
                val nArtist = normalize(track.artist)
                val nAlbum = normalize(track.album)
                val nGenre = normalize(track.genre)
                val nFile = normalize(extractFileName(track.localUriString))
                val nFolder = normalize(track.folderName)
                val combined = buildString(
                    nTitle.length + nArtist.length + nAlbum.length + nGenre.length + nFile.length + nFolder.length + 5
                ) {
                    append(nTitle)
                    if (nArtist.isNotEmpty()) { append(' '); append(nArtist) }
                    if (nAlbum.isNotEmpty()) { append(' '); append(nAlbum) }
                    if (nGenre.isNotEmpty()) { append(' '); append(nGenre) }
                    if (nFile.isNotEmpty()) { append(' '); append(nFile) }
                    if (nFolder.isNotEmpty()) { append(' '); append(nFolder) }
                }
                add(
                    IndexedTrack(
                        track = track,
                        normTitle = nTitle,
                        normArtist = nArtist,
                        normAlbum = nAlbum,
                        normGenre = nGenre,
                        normFilename = nFile,
                        normFolder = nFolder,
                        combinedFields = combined
                    )
                )
            }
        }
    }

    /**
     * Filters and ranks [indexedTracks] against [rawQuery].
     * Returns `null` when [rawQuery] is blank so callers can skip relevance reordering and keep
     * the user's chosen [com.example.data.LibrarySortOrder].
     */
    fun searchIndexed(
        indexedTracks: List<IndexedTrack>,
        rawQuery: String
    ): List<Track>? {
        val normQuery = normalize(rawQuery)
        if (normQuery.isEmpty()) return null

        val tokens = normQuery.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val scored = ArrayList<ScoredMatch>()
        for (idx in indexedTracks.indices) {
            val entry = indexedTracks[idx]
            val score = scoreEntry(entry, normQuery, tokens)
            if (score > 0) {
                scored.add(ScoredMatch(entry.track, score, idx))
            }
        }

        if (scored.isEmpty()) return emptyList()

        scored.sortWith(
            compareByDescending<ScoredMatch> { it.score }
                .thenBy { it.originalIndex }
        )

        return ArrayList<Track>(scored.size).apply {
            for (m in scored) add(m.track)
        }
    }

    /**
     * Convenience wrapper for direct one-shot filtering in tests or standalone callers.
     */
    fun searchTracks(tracks: List<Track>, rawQuery: String): List<Track> {
        val indexed = buildIndex(tracks)
        return searchIndexed(indexed, rawQuery) ?: tracks
    }

    /**
     * Diacritic- and multi-token-aware playlist filtering.
     */
    fun filterPlaylists(playlists: List<PlaylistSummary>, rawQuery: String): List<PlaylistSummary> {
        val normQuery = normalize(rawQuery)
        if (normQuery.isEmpty()) return playlists
        val tokens = normQuery.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return playlists

        return playlists.filter { pl ->
            val normName = normalize(pl.name)
            tokens.all { token -> normName.contains(token) }
        }
    }

    /**
     * Normalizes text for fast diacritic- and case-insensitive matching:
     * - Trims leading/trailing whitespace
     * - Decomposes Unicode characters with [Normalizer.Form.NFD] and strips combining diacritic marks
     * - Replaces filename separators (`_`, `-`, `.`, `/`, `:`) with spaces
     * - Lowercases using [Locale.ROOT] and collapses consecutive spaces
     */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        val decomposed = if (isPureAscii(trimmed)) {
            trimmed
        } else {
            Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        }

        val sb = StringBuilder(decomposed.length)
        var lastWasSpace = true
        for (i in decomposed.indices) {
            val ch = decomposed[i]
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) {
                continue
            }
            val mapped = when {
                ch == '_' || ch == '-' || ch == '.' || ch == '/' || ch == ':' || ch.isWhitespace() -> ' '
                ch in 'A'..'Z' -> (ch.code + 32).toChar()
                ch.code > 127 -> ch.lowercaseChar()
                else -> ch
            }
            if (mapped == ' ') {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(mapped)
                lastWasSpace = false
            }
        }
        val len = sb.length
        if (len > 0 && sb[len - 1] == ' ') {
            sb.setLength(len - 1)
        }
        return sb.toString()
    }

    /**
     * Extracts a clean human-readable filename (without extension) from a content/SAF/file URI.
     */
    internal fun extractFileName(uriString: String?): String {
        if (uriString.isNullOrBlank()) return ""
        val decoded = try {
            URLDecoder.decode(uriString, "UTF-8")
        } catch (_: Exception) {
            uriString
        }
        val segment = decoded
            .substringAfterLast('/')
            .substringAfterLast(':')
            .substringBefore('?')
        val dotIdx = segment.lastIndexOf('.')
        return if (dotIdx > 0 && segment.length - dotIdx <= 6) {
            segment.substring(0, dotIdx)
        } else {
            segment
        }
    }

    private fun scoreEntry(
        entry: IndexedTrack,
        normQuery: String,
        tokens: List<String>
    ): Int {
        // Fast reject: every query token must exist somewhere across the track's searchable fields
        val combined = entry.combinedFields
        for (i in tokens.indices) {
            if (!combined.contains(tokens[i])) return 0
        }

        val title = entry.normTitle
        val artist = entry.normArtist

        // Tier 1: Exact title match
        if (title == normQuery) return 1000

        // Tier 2: Title starts with full query
        if (title.startsWith(normQuery)) return 850

        // Tier 3: Title contains full query phrase (word-boundary bonus)
        val phraseIdx = title.indexOf(normQuery)
        if (phraseIdx >= 0) {
            return if (phraseIdx == 0 || title[phraseIdx - 1] == ' ') 740 else 660
        }

        // Tier 4+: Multi-token scoring prioritizing title matches & word-prefix matches
        var titleTokenMatches = 0
        var titlePrefixMatches = 0
        var artistTokenMatches = 0
        var albumOrGenreMatches = 0
        var filenameMatches = 0

        for (i in tokens.indices) {
            val tok = tokens[i]
            val tIdx = title.indexOf(tok)
            if (tIdx >= 0) {
                titleTokenMatches++
                if (tIdx == 0 || title[tIdx - 1] == ' ') {
                    titlePrefixMatches++
                }
            }
            if (artist.contains(tok)) artistTokenMatches++
            if (entry.normAlbum.contains(tok) || entry.normGenre.contains(tok)) albumOrGenreMatches++
            if (entry.normFilename.contains(tok)) filenameMatches++
        }

        // All tokens present inside title
        if (titleTokenMatches == tokens.size) {
            return 520 + titlePrefixMatches * 30
        }

        // Cross-field match (e.g., artist + title)
        var score = 140
        if (titlePrefixMatches > 0) score += 220 + titlePrefixMatches * 25
        else if (titleTokenMatches > 0) score += 160 + titleTokenMatches * 20

        if (artist == normQuery) score += 150
        else if (artist.startsWith(normQuery)) score += 110
        else score += artistTokenMatches * 35

        score += albumOrGenreMatches * 18
        score += filenameMatches * 12
        return score
    }

    private fun isPureAscii(s: String): Boolean {
        for (i in s.indices) {
            if (s[i].code >= 128) return false
        }
        return true
    }

    private class ScoredMatch(
        val track: Track,
        val score: Int,
        val originalIndex: Int
    )
}
