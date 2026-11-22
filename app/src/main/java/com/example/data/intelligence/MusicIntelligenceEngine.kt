package com.example.data.intelligence

import androidx.compose.runtime.Immutable
import com.example.data.Track
import com.example.ui.theme.ExpressivePalette
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class MusicIntelligenceRequest(
    val seedTrackId: String? = null,
    val targetMood: String? = null,
    val maxTracks: Int = 12,
    val timeOfDay: String? = currentTimeOfDayBucket()
)

@Immutable
data class ListeningHistoryInput(
    val recentTrackIds: List<String> = emptyList(),
    val skippedTrackIds: List<String> = emptyList(),
    val favoriteTrackIds: List<String> = emptyList(),
    val topGenres: List<String> = emptyList()
)

@Immutable
data class PlaylistProfile(
    val suggestedTitle: String,
    val suggestedPalette: String,
    val cohesionScore: Double
) {
    fun toExpressivePalette(): ExpressivePalette = ExpressivePalette.fromId(suggestedPalette)
}

@Immutable
data class MoodClassification(
    val mood: String,
    val confidence: Double,
    val energyLevel: String,
    val valence: String
)

@Immutable
data class BpmRange(
    val min: Int,
    val max: Int
)

@Immutable
data class TempoDescriptors(
    val dominantBpmRange: BpmRange,
    val descriptor: String,
    val pacingCurve: String,
    val tempoInferred: Boolean
)

@Immutable
data class RecommendedTrack(
    val rank: Int,
    val trackId: String,
    val score: Double,
    val moodTag: String,
    val tempoDescriptor: String,
    val reason: String
)

@Immutable
data class MusicIntelligenceOutput(
    val schemaVersion: String,
    val playlistProfile: PlaylistProfile,
    val moodClassifications: List<MoodClassification>,
    val tempoDescriptors: TempoDescriptors,
    val recommendedTracks: List<RecommendedTrack>,
    val rawJson: String
)

fun currentTimeOfDayBucket(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String =
    when (hourOfDay) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "late_night"
    }

/**
 * Offline, zero-server Music Intelligence Engine for Cadence.
 *
 * Exposes the exact System Prompt and RFC 8259 JSON Input/Output schema for seamless
 * local playlist generation directly against Cadence's Room Track library and listening history.
 * Runs deterministically on-device with zero network or server dependencies, and produces/parses
 * strict JSON compatible with on-device LLM runtimes.
 */
class MusicIntelligenceEngine {

    /**
     * Serializes request parameters, listening history, and Room Track metadata into the exact
     * RFC 8259 JSON input payload expected by the Music Intelligence Engine contract.
     */
    fun buildInputJson(
        request: MusicIntelligenceRequest,
        history: ListeningHistoryInput,
        libraryTracks: List<Track>
    ): String {
        val root = JSONObject()

        val reqObj = JSONObject().apply {
            put("seedTrackId", request.seedTrackId ?: JSONObject.NULL)
            put("targetMood", request.targetMood ?: JSONObject.NULL)
            put("maxTracks", request.maxTracks.coerceIn(1, 50))
            put("timeOfDay", request.timeOfDay ?: JSONObject.NULL)
        }
        root.put("request", reqObj)

        val historyObj = JSONObject().apply {
            put("recentTrackIds", JSONArray(history.recentTrackIds))
            put("skippedTrackIds", JSONArray(history.skippedTrackIds))
            put("favoriteTrackIds", JSONArray(history.favoriteTrackIds))
            put("topGenres", JSONArray(history.topGenres))
        }
        root.put("listeningHistory", historyObj)

        val tracksArr = JSONArray()
        for (t in libraryTracks) {
            tracksArr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("album", t.album)
                    put("genre", t.genre.ifBlank { t.genreTag })
                    put("bpm", t.bpm)
                    put("durationMs", t.durationMs)
                    put("playCount", t.playCount)
                    put("lastPlayedAt", t.lastPlayedAt)
                    put("isFavorite", t.isFavorite)
                }
            )
        }
        root.put("libraryTracks", tracksArr)

        return root.toString()
    }

    /**
     * Derives a ListeningHistoryInput directly from Room Track records plus session skip history.
     */
    fun deriveListeningHistory(
        libraryTracks: List<Track>,
        skippedTrackIds: List<String> = emptyList()
    ): ListeningHistoryInput {
        val recent = libraryTracks
            .filter { it.lastPlayedAt > 0L }
            .sortedByDescending { it.lastPlayedAt }
            .take(15)
            .map { it.id }

        val favorites = libraryTracks
            .filter { it.isFavorite }
            .map { it.id }

        val topGenres = libraryTracks
            .filter { it.genre.isNotBlank() }
            .groupBy { it.genre.trim() }
            .entries
            .sortedByDescending { (_, list) ->
                list.sumOf { it.playCount + if (it.isFavorite) 3 else 0 }
            }
            .take(5)
            .map { it.key }

        return ListeningHistoryInput(
            recentTrackIds = recent,
            skippedTrackIds = skippedTrackIds.distinct(),
            favoriteTrackIds = favorites,
            topGenres = topGenres
        )
    }

    /**
     * Executes full offline local music intelligence analysis directly from Cadence's Track list
     * and returns a parsed, verified MusicIntelligenceOutput along with its strict RFC 8259 rawJson.
     */
    fun analyzeAndRecommend(
        request: MusicIntelligenceRequest,
        history: ListeningHistoryInput,
        libraryTracks: List<Track>
    ): MusicIntelligenceOutput {
        val inputJson = buildInputJson(request, history, libraryTracks)
        val outputJson = generateOfflineRecommendationJson(inputJson)
        val validIds = libraryTracks.mapTo(HashSet(libraryTracks.size)) { it.id }
        return parseOutputJson(outputJson, validIds)
    }

    /**
     * Accepts a raw JSON input string matching the engine Input Schema and outputs a strictly
     * valid RFC 8259 JSON string matching the Required Output JSON Schema with zero server calls.
     */
    fun generateOfflineRecommendationJson(inputJson: String): String {
        val root = JSONObject(inputJson)
        val reqObj = root.optJSONObject("request") ?: JSONObject()
        val historyObj = root.optJSONObject("listeningHistory") ?: JSONObject()
        val tracksArr = root.optJSONArray("libraryTracks") ?: JSONArray()

        val seedTrackId = reqObj.optString("seedTrackId", "").takeIf { it.isNotBlank() && !reqObj.isNull("seedTrackId") }
        val rawTargetMood = reqObj.optString("targetMood", "").takeIf { it.isNotBlank() && !reqObj.isNull("targetMood") }
        val maxTracks = reqObj.optInt("maxTracks", 12).coerceIn(1, 50)
        val timeOfDay = reqObj.optString("timeOfDay", "").takeIf { it.isNotBlank() && !reqObj.isNull("timeOfDay") }

        val recentIds = historyObj.optJSONArray("recentTrackIds").toStringList()
        val skippedIds = historyObj.optJSONArray("skippedTrackIds").toStringList().toSet()
        val favoriteIds = historyObj.optJSONArray("favoriteTrackIds").toStringList().toSet()
        val topGenres = historyObj.optJSONArray("topGenres").toStringList()
            .map { it.lowercase(Locale.ROOT) }
            .toSet()

        val candidates = ArrayList<CandidateTrack>(tracksArr.length())
        for (i in 0 until tracksArr.length()) {
            val obj = tracksArr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            if (id.isEmpty()) continue
            val title = obj.optString("title", "Untitled")
            val artist = obj.optString("artist", "Unknown Artist")
            val album = obj.optString("album", "")
            val genre = obj.optString("genre", "")
            val rawBpm = obj.optInt("bpm", 0)
            val durationMs = obj.optLong("durationMs", 0L)
            val playCount = obj.optInt("playCount", 0)
            val lastPlayedAt = obj.optLong("lastPlayedAt", 0L)
            val isFavorite = obj.optBoolean("isFavorite", false) || id in favoriteIds

            val inferred = rawBpm <= 0
            val effectiveBpm = if (inferred) inferBpm(genre, title, album, durationMs) else rawBpm.coerceIn(45, 200)
            val tempoDesc = classifyTempoDescriptor(effectiveBpm)
            val moodScores = computeTrackMoodVector(genre, title, album, effectiveBpm)
            val primaryMood = moodScores.maxByOrNull { it.value }?.key ?: "calm"

            candidates += CandidateTrack(
                id = id,
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                bpm = effectiveBpm,
                bpmInferred = inferred,
                tempoDescriptor = tempoDesc,
                playCount = playCount,
                lastPlayedAt = lastPlayedAt,
                isFavorite = isFavorite,
                moodScores = moodScores,
                primaryMood = primaryMood
            )
        }

        if (candidates.isEmpty()) {
            return buildEmptyOutputJson().toString()
        }

        val seedTrack = seedTrackId?.let { id -> candidates.firstOrNull { it.id == id } }
        val normalizedTargetMood = normalizeRequestedMood(rawTargetMood, timeOfDay, seedTrack)
        val anchorBpm = seedTrack?.bpm ?: targetBpmForContext(normalizedTargetMood, timeOfDay)
        val maxPlayCount = candidates.maxOf { it.playCount }.coerceAtLeast(1)
        val recentTopThree = recentIds.take(3).toSet()

        val scored = candidates.map { c ->
            val isSeed = seedTrack != null && c.id == seedTrack.id

            // 1. Mood & Context Alignment (35%)
            val targetAffinity = c.moodScores[normalizedTargetMood] ?: 0.45
            val seedMoodSimilarity = if (seedTrack != null) {
                cosineMoodSimilarity(c.moodScores, seedTrack.moodScores)
            } else {
                targetAffinity
            }
            val timeAffinity = timeOfDayAlignment(c.bpm, c.primaryMood, timeOfDay)
            val moodAlignment = (targetAffinity * 0.50 + seedMoodSimilarity * 0.30 + timeAffinity * 0.20)
                .coerceIn(0.0, 1.0)

            // 2. Tempo & Flow Continuity (25%)
            val bpmDelta = abs(c.bpm - anchorBpm)
            val tempoContinuity = when {
                bpmDelta <= 8 -> 1.0
                bpmDelta <= 15 -> 0.88 - ((bpmDelta - 8) * 0.015)
                bpmDelta <= 30 -> 0.72 - ((bpmDelta - 15) * 0.02)
                else -> (0.42 - ((bpmDelta - 30) * 0.008)).coerceAtLeast(0.15)
            }

            // 3. User Affinity (25%)
            val playNorm = (c.playCount.toDouble() / maxPlayCount.toDouble()).coerceIn(0.0, 1.0)
            val favBonus = if (c.isFavorite) 0.20 else 0.0
            val genreMatch = if (c.genre.lowercase(Locale.ROOT) in topGenres) 0.15 else 0.0
            val affinity = (0.45 + playNorm * 0.35 + favBonus + genreMatch).coerceIn(0.0, 1.0)

            // 4. Anti-Fatigue & Diversity (15%)
            var freshness = 0.85
            if (c.id in skippedIds && !isSeed) {
                freshness -= 0.35
            }
            if (c.id in recentTopThree && !isSeed) {
                freshness -= 0.18
            }
            freshness = freshness.coerceIn(0.10, 1.0)

            var composite = (
                moodAlignment * 0.35 +
                    tempoContinuity * 0.25 +
                    affinity * 0.25 +
                    freshness * 0.15
                ).coerceIn(0.0, 1.0)

            if (isSeed) {
                composite = (composite * 0.30 + 0.69).coerceIn(0.95, 0.99)
            }

            val reason = buildConciseReason(
                candidate = c,
                isSeed = isSeed,
                bpmDelta = bpmDelta,
                targetMood = normalizedTargetMood,
                wasDampenedForRecency = c.id in recentTopThree && !isSeed,
                wasSkipped = c.id in skippedIds && !isSeed
            )

            ScoredCandidate(
                candidate = c,
                score = round2(composite),
                reason = reason
            )
        }.sortedByDescending { it.score }

        // Select top maxTracks with artist-spacing diversity so consecutive tracks avoid repeating artist
        val selected = selectWithArtistSpacing(scored, maxTracks)

        // Compute pacing curve & optionally order non-seed tracks for smooth energy flow
        val pacingCurve = determinePacingCurve(normalizedTargetMood, timeOfDay)
        val ordered = applyPacingFlow(selected, seedTrackId, pacingCurve)

        val anyInferred = ordered.any { it.candidate.bpmInferred }
        val minBpm = ordered.minOf { it.candidate.bpm }
        val maxBpm = ordered.maxOf { it.candidate.bpm }
        val avgBpm = ordered.map { it.candidate.bpm }.average().roundToInt()
        val dominantDescriptor = classifyTempoDescriptor(avgBpm)

        val moodClassifications = aggregateMoodClassifications(ordered, normalizedTargetMood)
        val primaryMood = moodClassifications.firstOrNull()?.mood ?: normalizedTargetMood
        val paletteId = paletteForMood(primaryMood)
        val cohesionScore = computeCohesionScore(ordered, minBpm, maxBpm)
        val suggestedTitle = generatePlaylistTitle(primaryMood, timeOfDay, seedTrack)

        val outRoot = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put(
                "playlistProfile",
                JSONObject().apply {
                    put("suggestedTitle", suggestedTitle)
                    put("suggestedPalette", paletteId)
                    put("cohesionScore", round2(cohesionScore))
                }
            )
            put(
                "moodClassifications",
                JSONArray().apply {
                    moodClassifications.forEach { m ->
                        put(
                            JSONObject().apply {
                                put("mood", m.mood)
                                put("confidence", round2(m.confidence))
                                put("energyLevel", m.energyLevel)
                                put("valence", m.valence)
                            }
                        )
                    }
                }
            )
            put(
                "tempoDescriptors",
                JSONObject().apply {
                    put(
                        "dominantBpmRange",
                        JSONObject().apply {
                            put("min", minBpm)
                            put("max", maxBpm)
                        }
                    )
                    put("descriptor", dominantDescriptor)
                    put("pacingCurve", pacingCurve)
                    put("tempoInferred", anyInferred)
                }
            )
            put(
                "recommendedTracks",
                JSONArray().apply {
                    ordered.forEachIndexed { idx, item ->
                        put(
                            JSONObject().apply {
                                put("rank", idx + 1)
                                put("trackId", item.candidate.id)
                                put("score", item.score)
                                put("moodTag", item.candidate.primaryMood)
                                put("tempoDescriptor", item.candidate.tempoDescriptor)
                                put("reason", item.reason)
                            }
                        )
                    }
                }
            )
        }

        return outRoot.toString(2)
    }

    /**
     * Parses and validates a JSON string conforming to the Music Intelligence Output Schema.
     * Drops any trackId not present in validTrackIds when provided, guaranteeing zero hallucinated IDs.
     */
    fun parseOutputJson(
        rawJson: String,
        validTrackIds: Set<String>? = null
    ): MusicIntelligenceOutput {
        val cleaned = rawJson
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val root = JSONObject(cleaned)
        val schemaVersion = root.optString("schemaVersion", SCHEMA_VERSION)

        val profileObj = root.optJSONObject("playlistProfile") ?: JSONObject()
        val profile = PlaylistProfile(
            suggestedTitle = profileObj.optString("suggestedTitle", "Cadence Smart Mix")
                .ifBlank { "Cadence Smart Mix" },
            suggestedPalette = profileObj.optString("suggestedPalette", "terracotta")
                .takeIf { it in VALID_PALETTES } ?: "terracotta",
            cohesionScore = profileObj.optDouble("cohesionScore", 0.0).coerceIn(0.0, 1.0)
        )

        val moodsArr = root.optJSONArray("moodClassifications") ?: JSONArray()
        val moods = buildList {
            for (i in 0 until moodsArr.length()) {
                val obj = moodsArr.optJSONObject(i) ?: continue
                add(
                    MoodClassification(
                        mood = obj.optString("mood", "calm"),
                        confidence = obj.optDouble("confidence", 0.75).coerceIn(0.0, 1.0),
                        energyLevel = obj.optString("energyLevel", "medium"),
                        valence = obj.optString("valence", "neutral")
                    )
                )
            }
        }

        val tempoObj = root.optJSONObject("tempoDescriptors") ?: JSONObject()
        val rangeObj = tempoObj.optJSONObject("dominantBpmRange") ?: JSONObject()
        val tempoDescriptors = TempoDescriptors(
            dominantBpmRange = BpmRange(
                min = rangeObj.optInt("min", 0),
                max = rangeObj.optInt("max", 0)
            ),
            descriptor = tempoObj.optString("descriptor", "downtempo_chill"),
            pacingCurve = tempoObj.optString("pacingCurve", "steady"),
            tempoInferred = tempoObj.optBoolean("tempoInferred", false)
        )

        val recArr = root.optJSONArray("recommendedTracks") ?: JSONArray()
        val seen = HashSet<String>()
        val recommendations = buildList {
            for (i in 0 until recArr.length()) {
                val obj = recArr.optJSONObject(i) ?: continue
                val trackId = obj.optString("trackId", "").trim()
                if (trackId.isEmpty()) continue
                if (validTrackIds != null && trackId !in validTrackIds) continue
                if (!seen.add(trackId)) continue
                add(
                    RecommendedTrack(
                        rank = size + 1,
                        trackId = trackId,
                        score = obj.optDouble("score", 0.5).coerceIn(0.0, 1.0),
                        moodTag = obj.optString("moodTag", "calm"),
                        tempoDescriptor = obj.optString("tempoDescriptor", "downtempo_chill"),
                        reason = obj.optString("reason", "Matched mood and flow")
                    )
                )
            }
        }

        return MusicIntelligenceOutput(
            schemaVersion = schemaVersion,
            playlistProfile = profile,
            moodClassifications = moods,
            tempoDescriptors = tempoDescriptors,
            recommendedTracks = recommendations,
            rawJson = cleaned
        )
    }

    // --- Internal Deterministic Music Intelligence Helpers ---

    private data class CandidateTrack(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val bpm: Int,
        val bpmInferred: Boolean,
        val tempoDescriptor: String,
        val playCount: Int,
        val lastPlayedAt: Long,
        val isFavorite: Boolean,
        val moodScores: Map<String, Double>,
        val primaryMood: String
    )

    private data class ScoredCandidate(
        val candidate: CandidateTrack,
        val score: Double,
        val reason: String
    )

    private fun buildEmptyOutputJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put(
            "playlistProfile",
            JSONObject().apply {
                put("suggestedTitle", "Empty Local Mix")
                put("suggestedPalette", "terracotta")
                put("cohesionScore", 0.0)
            }
        )
        put("moodClassifications", JSONArray())
        put(
            "tempoDescriptors",
            JSONObject().apply {
                put("dominantBpmRange", JSONObject().apply { put("min", 0); put("max", 0) })
                put("descriptor", "downtempo_chill")
                put("pacingCurve", "steady")
                put("tempoInferred", true)
            }
        )
        put("recommendedTracks", JSONArray())
    }

    private fun inferBpm(genre: String, title: String, album: String, durationMs: Long): Int {
        val text = "$genre $title $album".lowercase(Locale.ROOT)
        return when {
            text.containsAny("ambient", "nocturne", "drone", "sleep", "meditat", "rain", "drift") -> 66
            text.containsAny("lofi", "lo-fi", "chill", "rhodes", "downtempo", "jazz", "acoustic", "piano", "felt", "botanical") -> 82
            text.containsAny("minimal", "ceramic", "indie", "soul", "r&b", "folk", "groove", "study", "focus") -> 102
            text.containsAny("house", "pop", "disco", "funk", "chroma", "electronic", "dance", "synthwave") -> 122
            text.containsAny("techno", "dnb", "drum", "rock", "metal", "workout", "run", "fast", "punk") -> 138
            durationMs > 300_000L -> 78
            else -> 92
        }
    }

    private fun classifyTempoDescriptor(bpm: Int): String = when {
        bpm < 70 -> "largo_ambient"
        bpm <= 94 -> "downtempo_chill"
        bpm <= 114 -> "midtempo_groove"
        bpm <= 132 -> "upbeat_driving"
        else -> "fast_intense"
    }

    private fun computeTrackMoodVector(genre: String, title: String, album: String, bpm: Int): Map<String, Double> {
        val text = "$genre $title $album".lowercase(Locale.ROOT)
        val scores = LinkedHashMap<String, Double>(CANONICAL_MOODS.size)
        for (m in CANONICAL_MOODS) scores[m] = 0.25

        // Tempo-derived baseline energy priors
        when {
            bpm < 75 -> {
                scores.boost("calm", 0.38)
                scores.boost("nocturnal", 0.30)
                scores.boost("minimal", 0.24)
                scores.boost("melancholic", 0.18)
            }
            bpm <= 96 -> {
                scores.boost("warm", 0.34)
                scores.boost("calm", 0.28)
                scores.boost("focused", 0.30)
                scores.boost("organic", 0.25)
            }
            bpm <= 116 -> {
                scores.boost("focused", 0.34)
                scores.boost("uplifting", 0.26)
                scores.boost("warm", 0.22)
                scores.boost("minimal", 0.22)
            }
            else -> {
                scores.boost("energetic", 0.44)
                scores.boost("uplifting", 0.36)
                scores.boost("cinematic", 0.18)
            }
        }

        // Semantic metadata signals
        if (text.containsAny("night", "midnight", "nocturne", "indigo", "moon", "after hours", "dusk", "shadow")) {
            scores.boost("nocturnal", 0.42)
            scores.boost("melancholic", 0.18)
        }
        if (text.containsAny("sun", "solstice", "amber", "warm", "rhodes", "gold", "horizon", "honey", "terracotta")) {
            scores.boost("warm", 0.42)
            scores.boost("uplifting", 0.20)
        }
        if (text.containsAny("moss", "rain", "botanical", "forest", "green", "garden", "leaf", "acoustic", "wood", "felt")) {
            scores.boost("organic", 0.44)
            scores.boost("calm", 0.26)
        }
        if (text.containsAny("ceramic", "minimal", "studio", "study", "focus", "flow", "bell", "clean", "mono")) {
            scores.boost("minimal", 0.36)
            scores.boost("focused", 0.36)
        }
        if (text.containsAny("chroma", "prism", "pulse", "pop", "bright", "dance", "electric", "kinetic", "citrus", "spark")) {
            scores.boost("energetic", 0.38)
            scores.boost("uplifting", 0.38)
        }
        if (text.containsAny("cinema", "orchestra", "score", "symphon", "epic", "vast", "strings")) {
            scores.boost("cinematic", 0.44)
        }
        if (text.containsAny("blue", "sad", "rain", "grey", "alone", "fade", "echo", "minor", "autumn")) {
            scores.boost("melancholic", 0.34)
        }

        return scores.mapValues { (_, v) -> v.coerceIn(0.05, 0.99) }
    }

    private fun normalizeRequestedMood(
        rawTargetMood: String?,
        timeOfDay: String?,
        seedTrack: CandidateTrack?
    ): String {
        val cleaned = rawTargetMood?.trim()?.lowercase(Locale.ROOT)
        when (cleaned) {
            "focus", "focused", "study", "deep_focus" -> return "focused"
            "late_night", "nocturnal", "midnight", "night" -> return "nocturnal"
            "calm", "chill", "relax", "ambient" -> return "calm"
            "warm", "cozy", "sunset" -> return "warm"
            "energetic", "energy", "workout", "upbeat" -> return "energetic"
            "uplifting", "bright", "morning", "happy" -> return "uplifting"
            "melancholic", "moody", "reflective" -> return "melancholic"
            "organic", "botanical", "acoustic" -> return "organic"
            "minimal", "clean" -> return "minimal"
            "cinematic", "epic" -> return "cinematic"
        }
        if (seedTrack != null) return seedTrack.primaryMood
        return when (timeOfDay) {
            "morning" -> "uplifting"
            "afternoon" -> "focused"
            "evening" -> "warm"
            "late_night" -> "nocturnal"
            else -> "calm"
        }
    }

    private fun targetBpmForContext(mood: String, timeOfDay: String?): Int = when (mood) {
        "nocturnal", "melancholic" -> 74
        "calm", "organic" -> 80
        "warm" -> 88
        "focused", "minimal" -> 98
        "cinematic" -> 100
        "uplifting" -> 112
        "energetic" -> 124
        else -> when (timeOfDay) {
            "late_night" -> 74
            "morning" -> 108
            else -> 92
        }
    }

    private fun timeOfDayAlignment(bpm: Int, primaryMood: String, timeOfDay: String?): Double =
        when (timeOfDay) {
            "morning" -> if (bpm in 85..130 || primaryMood in setOf("uplifting", "warm", "organic")) 0.90 else 0.55
            "afternoon" -> if (bpm in 85..125 || primaryMood in setOf("focused", "minimal", "energetic")) 0.90 else 0.60
            "evening" -> if (bpm in 72..105 || primaryMood in setOf("warm", "calm", "organic")) 0.92 else 0.58
            "late_night" -> if (bpm <= 92 || primaryMood in setOf("nocturnal", "calm", "melancholic", "minimal")) 0.95 else 0.42
            else -> 0.75
        }

    private fun cosineMoodSimilarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for (mood in CANONICAL_MOODS) {
            val va = a[mood] ?: 0.0
            val vb = b[mood] ?: 0.0
            dot += va * vb
            magA += va * va
            magB += vb * vb
        }
        val denom = kotlin.math.sqrt(magA) * kotlin.math.sqrt(magB)
        return if (denom > 0.0) (dot / denom).coerceIn(0.0, 1.0) else 0.5
    }

    private fun selectWithArtistSpacing(
        sorted: List<ScoredCandidate>,
        maxTracks: Int
    ): List<ScoredCandidate> {
        val remaining = sorted.toMutableList()
        val chosen = ArrayList<ScoredCandidate>(minOf(maxTracks, sorted.size))

        while (chosen.size < maxTracks && remaining.isNotEmpty()) {
            val prevArtist = chosen.lastOrNull()?.candidate?.artist?.lowercase(Locale.ROOT)
            val pickIndex = if (prevArtist != null) {
                val differentArtistIdx = remaining.indexOfFirst {
                    it.candidate.artist.lowercase(Locale.ROOT) != prevArtist
                }
                if (differentArtistIdx in 0..3) differentArtistIdx else 0
            } else {
                0
            }
            chosen += remaining.removeAt(pickIndex)
        }
        return chosen
    }

    private fun determinePacingCurve(mood: String, timeOfDay: String?): String = when {
        mood in setOf("nocturnal", "calm", "melancholic") || timeOfDay == "late_night" -> "wind_down"
        mood in setOf("energetic", "uplifting") || timeOfDay == "morning" -> "gradual_build"
        mood in setOf("focused", "minimal") -> "steady"
        else -> "wave"
    }

    private fun applyPacingFlow(
        selected: List<ScoredCandidate>,
        seedTrackId: String?,
        pacingCurve: String
    ): List<ScoredCandidate> {
        if (selected.size <= 2) return selected
        val anchor = seedTrackId?.let { id -> selected.firstOrNull { it.candidate.id == id } }
        val rest = if (anchor != null) selected.filterNot { it.candidate.id == anchor.candidate.id } else selected

        val flowed = when (pacingCurve) {
            "wind_down" -> rest.sortedWith(
                compareByDescending<ScoredCandidate> { it.candidate.bpm }.thenByDescending { it.score }
            )
            "gradual_build" -> rest.sortedWith(
                compareBy<ScoredCandidate> { it.candidate.bpm }.thenByDescending { it.score }
            )
            else -> rest // Keep top score + artist spacing for steady/wave
        }
        return if (anchor != null) listOf(anchor) + flowed else flowed
    }

    private fun aggregateMoodClassifications(
        ordered: List<ScoredCandidate>,
        fallbackMood: String
    ): List<MoodClassification> {
        val avgByMood = CANONICAL_MOODS.associateWith { mood ->
            ordered.map { it.candidate.moodScores[mood] ?: 0.2 }.average()
        }
        val topMoods = avgByMood.entries
            .sortedByDescending { it.value }
            .take(3)
            .filter { it.value >= 0.42 }
            .ifEmpty { listOf(avgByMood.entries.first { it.key == fallbackMood }) }

        return topMoods.map { (mood, confidence) ->
            MoodClassification(
                mood = mood,
                confidence = confidence.coerceIn(0.45, 0.98),
                energyLevel = energyForMood(mood),
                valence = valenceForMood(mood)
            )
        }
    }

    private fun energyForMood(mood: String): String = when (mood) {
        "calm", "nocturnal", "melancholic", "organic" -> "low"
        "energetic" -> "high"
        else -> "medium"
    }

    private fun valenceForMood(mood: String): String = when (mood) {
        "nocturnal", "melancholic" -> "dark"
        "warm", "uplifting", "energetic", "organic" -> "bright"
        else -> "neutral"
    }

    private fun paletteForMood(mood: String): String = when (mood) {
        "warm" -> "terracotta"
        "organic", "calm" -> "matcha"
        "nocturnal", "melancholic", "cinematic" -> "cobalt"
        "minimal", "focused" -> "berry"
        "uplifting", "energetic" -> "citrus"
        else -> "terracotta"
    }

    private fun computeCohesionScore(
        ordered: List<ScoredCandidate>,
        minBpm: Int,
        maxBpm: Int
    ): Double {
        if (ordered.isEmpty()) return 0.0
        val avgScore = ordered.map { it.score }.average()
        val bpmSpreadPenalty = ((maxBpm - minBpm) / 140.0).coerceIn(0.0, 0.25)
        return (avgScore - bpmSpreadPenalty * 0.4 + 0.08).coerceIn(0.35, 0.98)
    }

    private fun generatePlaylistTitle(
        primaryMood: String,
        timeOfDay: String?,
        seedTrack: CandidateTrack?
    ): String {
        if (seedTrack != null) {
            return when (primaryMood) {
                "nocturnal" -> "${seedTrack.title} · After Hours"
                "focused" -> "${seedTrack.title} · Deep Focus"
                "energetic" -> "${seedTrack.title} · Kinetic Flow"
                "organic" -> "${seedTrack.title} · Botanical Drift"
                else -> "${seedTrack.title} · Smart Mix"
            }
        }
        return when (primaryMood) {
            "nocturnal" -> "Indigo After Hours"
            "calm" -> if (timeOfDay == "evening") "Evening Unwind" else "Quiet Horizon"
            "warm" -> "Warm Terracotta Glow"
            "focused" -> "Minimal Deep Focus"
            "organic" -> "Botanical Acoustics"
            "energetic" -> "Citrus Kinetic Pulse"
            "uplifting" -> if (timeOfDay == "morning") "Sunlit Morning Lift" else "Bright Momentum"
            "melancholic" -> "Rain & Echoes"
            "cinematic" -> "Widescreen Drift"
            else -> "Cadence Smart Mix"
        }
    }

    private fun buildConciseReason(
        candidate: CandidateTrack,
        isSeed: Boolean,
        bpmDelta: Int,
        targetMood: String,
        wasDampenedForRecency: Boolean,
        wasSkipped: Boolean
    ): String = when {
        isSeed -> "Seed anchor, ${candidate.bpm} BPM ${candidate.primaryMood} match"
        candidate.isFavorite && bpmDelta <= 12 -> "Favorite track with smooth ${candidate.bpm} BPM transition"
        wasSkipped -> "Mood match; lowered due to recent skip"
        wasDampenedForRecency -> "Strong $targetMood fit; dampened for recency"
        bpmDelta <= 10 -> "Cohesive ${candidate.bpm} BPM flow and $targetMood tone"
        candidate.playCount >= 5 -> "Frequent listen aligned with $targetMood profile"
        else -> "Aligned $targetMood mood at ${candidate.bpm} BPM"
    }

    private fun MutableMap<String, Double>.boost(key: String, delta: Double) {
        this[key] = (this[key] ?: 0.25) + delta
    }

    private fun String.containsAny(vararg tokens: String): Boolean =
        tokens.any { this.contains(it) }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                val s = optString(i, "")
                if (s.isNotBlank()) add(s)
            }
        }
    }

    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0

    companion object {
        const val SCHEMA_VERSION = "1.0"

        val CANONICAL_MOODS = listOf(
            "calm", "warm", "focused", "melancholic", "nocturnal",
            "uplifting", "energetic", "organic", "minimal", "cinematic"
        )

        val VALID_PALETTES = setOf("terracotta", "matcha", "cobalt", "berry", "citrus")

        val SYSTEM_PROMPT: String = """
SYSTEM PROMPT — CADENCE LOCAL MUSIC INTELLIGENCE ENGINE

ROLE & OPERATING MODE
You are an offline, deterministic music intelligence and local playlist curation engine embedded inside a mobile music player ("Cadence"). You operate with zero network or server dependency. Your sole function is to analyze a JSON payload containing local track metadata and user listening history, infer mood and tempo profiles, and return a strictly valid JSON object containing mood classifications, tempo descriptors, and a ranked list of recommended local track IDs.

STRICT OUTPUT CONTRACT
1. Output ONLY a single, valid RFC 8259 JSON object.
2. Do NOT wrap the output in Markdown code fences (no ```json or ```).
3. Do NOT include any preamble, explanation, comments, trailing commas, or extra keys outside the schema.
4. NEVER fabricate, alter, or invent track IDs. Every trackId in `recommendedTracks` MUST exist verbatim in `input.libraryTracks[].id`.
5. If a numeric metadata field (such as `bpm`) is 0, null, or missing, infer an estimated tempo band from `genre`, `title`, `album`, and `durationMs`, and set `"tempoInferred": true`.

INPUT SCHEMA
You will receive a JSON object with:
- `request`: { `seedTrackId`: string | null, `targetMood`: string | null, `maxTracks`: integer (1..50), `timeOfDay`: "morning" | "afternoon" | "evening" | "late_night" | null }
- `listeningHistory`: { `recentTrackIds`: string[], `skippedTrackIds`: string[], `favoriteTrackIds`: string[], `topGenres`: string[] }
- `libraryTracks`: [ { `id`: string, `title`: string, `artist`: string, `album`: string, `genre`: string, `bpm`: integer, `durationMs`: integer, `playCount`: integer, `lastPlayedAt`: integer, `isFavorite`: boolean } ]

ANALYSIS & RANKING HEURISTICS
Compute a normalized composite score `0.00..1.00` for each candidate track using:
1. Mood & Context Alignment (35%): Match against `seedTrackId` mood/genre, `targetMood`, and `timeOfDay` energy curve.
2. Tempo & Flow Continuity (25%): Favor smooth BPM proximity (±15 BPM of seed or adjacent track) and coherent groove transitions.
3. User Affinity (25%): Boost `isFavorite` (+0.20), moderate-to-high `playCount`, and alignment with `topGenres`.
4. Anti-Fatigue & Diversity (15%): Penalize tracks in `skippedTrackIds` (-0.35), slightly dampen the 3 most recently played tracks unless explicitly seeded, and avoid placing two tracks by the same `artist` consecutively when other candidates exist.

TEMPO DESCRIPTOR VOCABULARY
- `< 70 BPM`: "largo_ambient"
- `70-94 BPM`: "downtempo_chill"
- `95-114 BPM`: "midtempo_groove"
- `115-132 BPM`: "upbeat_driving"
- `> 132 BPM`: "fast_intense"

MOOD CLASSIFICATION VOCABULARY
"calm", "warm", "focused", "melancholic", "nocturnal", "uplifting", "energetic", "organic", "minimal", "cinematic".

REQUIRED OUTPUT JSON SCHEMA
{
  "schemaVersion": "1.0",
  "playlistProfile": { "suggestedTitle": string, "suggestedPalette": "terracotta" | "matcha" | "cobalt" | "berry" | "citrus", "cohesionScore": number },
  "moodClassifications": [ { "mood": string, "confidence": number, "energyLevel": "low" | "medium" | "high", "valence": "dark" | "neutral" | "bright" } ],
  "tempoDescriptors": { "dominantBpmRange": { "min": integer, "max": integer }, "descriptor": "largo_ambient" | "downtempo_chill" | "midtempo_groove" | "upbeat_driving" | "fast_intense", "pacingCurve": "steady" | "gradual_build" | "wind_down" | "wave", "tempoInferred": boolean },
  "recommendedTracks": [ { "rank": integer, "trackId": string, "score": number, "moodTag": string, "tempoDescriptor": string, "reason": string } ]
}
        """.trimIndent()
    }
}
