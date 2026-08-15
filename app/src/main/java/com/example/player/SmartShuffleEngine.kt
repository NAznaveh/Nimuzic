package com.example.player

import android.content.Context
import android.content.SharedPreferences
import com.example.data.models.Track
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class PlayedHistoryItem(
    val trackId: Long,
    val artist: String,
    val album: String,
    val title: String,
    val genre: String,
    val language: String,
    val completionRatio: Float, // 0.0f to 1.0f
    val wasSkippedEarly: Boolean, // skipped within 5 seconds
    val wasRepeatedConsecutively: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class EngineSnapshot(
    val seedTrack: Track?,
    val seedWeight: Double,
    val seedRemainingPlays: Int,
    val shortTermHistory: List<PlayedHistoryItem>,
    val recentTrackIds: List<Long>,
    val consecutiveBoostArtist: String?,
    val consecutiveBoostGenre: String?,
    val consecutiveBoostRemainingPlays: Int,
    val artistCompletions: Map<String, Int>,
    val artistSkips: Map<String, Int>,
    val genreCompletions: Map<String, Int>,
    val genreSkips: Map<String, Int>,
    val trackCompletions: Map<Long, Int>,
    val trackSkips: Map<Long, Int>
)

/**
 * Smart Shuffle Engine
 * Analyzes listening behavior, calculates dynamic scores for all candidate songs,
 * and uses weighted probabilistic selection to generate intelligent personalized queues.
 */
class SmartShuffleEngine(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_shuffle_prefs", Context.MODE_PRIVATE)

    // Short-term session history (Last 10 played tracks)
    private val shortTermHistory = mutableListOf<PlayedHistoryItem>()

    // Recency tracker (Last 15 played track IDs)
    private val recentTrackIds = mutableListOf<Long>()

    // Consecutive play boost counter for specific characteristics (lasts 15 plays)
    private var consecutiveBoostArtist: String? = null
    private var consecutiveBoostGenre: String? = null
    private var consecutiveBoostRemainingPlays: Int = 0

    // Long-term historical counts (Persisted in SharedPreferences)
    private val artistCompletions = mutableMapOf<String, Int>()
    private val artistSkips = mutableMapOf<String, Int>()
    private val genreCompletions = mutableMapOf<String, Int>()
    private val genreSkips = mutableMapOf<String, Int>()
    private val trackCompletions = mutableMapOf<Long, Int>()
    private val trackSkips = mutableMapOf<Long, Int>()

    // Scenario 1: Seed Track Context tracking
    private var seedTrack: Track? = null
    private var seedWeight: Double = 0.0
    private var seedRemainingPlays: Int = 0

    init {
        loadEngineData()
    }

    /**
     * Snapshot engine state under a brief lock (<0.01ms) for lock-free background calculations.
     */
    @Synchronized
    fun getSnapshot(): EngineSnapshot {
        return EngineSnapshot(
            seedTrack = seedTrack,
            seedWeight = seedWeight,
            seedRemainingPlays = seedRemainingPlays,
            shortTermHistory = shortTermHistory.toList(),
            recentTrackIds = recentTrackIds.toList(),
            consecutiveBoostArtist = consecutiveBoostArtist,
            consecutiveBoostGenre = consecutiveBoostGenre,
            consecutiveBoostRemainingPlays = consecutiveBoostRemainingPlays,
            artistCompletions = artistCompletions.toMap(),
            artistSkips = artistSkips.toMap(),
            genreCompletions = genreCompletions.toMap(),
            genreSkips = genreSkips.toMap(),
            trackCompletions = trackCompletions.toMap(),
            trackSkips = trackSkips.toMap()
        )
    }

    /**
     * Scenario 1: Set a seed track when user manually selects a song
     */
    @Synchronized
    fun setSeedTrack(track: Track) {
        seedTrack = track
        seedWeight = 1.0
        seedRemainingPlays = 5
    }

    /**
     * Scenario 2: Clear seed track to rely purely on global personalized history
     */
    @Synchronized
    fun clearSeedTrack() {
        seedTrack = null
        seedWeight = 0.0
        seedRemainingPlays = 0
    }

    @Synchronized
    fun hasSeedTrack(): Boolean = seedTrack != null && seedWeight > 0.05 && seedRemainingPlays > 0

    /**
     * Record a track playback event (completion, skip, replay)
     */
    @Synchronized
    fun recordPlaybackEvent(
        track: Track,
        positionMs: Long,
        durationMs: Long,
        isExplicitSkip: Boolean,
        isConsecutiveReplay: Boolean
    ) {
        val dur = if (durationMs > 0) durationMs else if (track.durationMs > 0) track.durationMs else 210000L
        val ratio = (positionMs.toFloat() / dur.toFloat()).coerceIn(0.0f, 1.0f)
        val wasSkippedEarly = isExplicitSkip && positionMs < 5000L

        val genre = inferGenre(track)
        val language = inferLanguage(track)

        val item = PlayedHistoryItem(
            trackId = track.id,
            artist = normalize(track.artist),
            album = normalize(track.album),
            title = normalize(track.title),
            genre = genre,
            language = language,
            completionRatio = ratio,
            wasSkippedEarly = wasSkippedEarly,
            wasRepeatedConsecutively = isConsecutiveReplay
        )

        // Add to short-term history (keep max 10)
        shortTermHistory.add(0, item)
        if (shortTermHistory.size > 10) {
            shortTermHistory.removeAt(shortTermHistory.lastIndex)
        }

        // Add to recency list (keep max 15)
        recentTrackIds.remove(track.id)
        recentTrackIds.add(0, track.id)
        if (recentTrackIds.size > 15) {
            recentTrackIds.removeAt(recentTrackIds.lastIndex)
        }

        // Update long-term stats
        val artist = item.artist
        if (wasSkippedEarly) {
            artistSkips[artist] = (artistSkips[artist] ?: 0) + 1
            genreSkips[genre] = (genreSkips[genre] ?: 0) + 1
            trackSkips[track.id] = (trackSkips[track.id] ?: 0) + 1
        } else if (ratio >= 0.8f) {
            artistCompletions[artist] = (artistCompletions[artist] ?: 0) + 1
            genreCompletions[genre] = (genreCompletions[genre] ?: 0) + 1
            trackCompletions[track.id] = (trackCompletions[track.id] ?: 0) + 1
        }

        // Handle Consecutive Replay rule (Requirement #5)
        if (isConsecutiveReplay) {
            consecutiveBoostArtist = artist
            consecutiveBoostGenre = genre
            consecutiveBoostRemainingPlays = 15
        } else if (consecutiveBoostRemainingPlays > 0) {
            consecutiveBoostRemainingPlays--
        }

        // Scenario 1: Seed Context decay and learn from skips
        if (seedTrack != null && seedWeight > 0.0) {
            if (wasSkippedEarly || isExplicitSkip) {
                // Skips reduce seed influence faster
                seedWeight *= 0.35
                seedRemainingPlays--
            } else if (ratio >= 0.8f) {
                // High completion -> gradual decay over plays
                seedWeight *= 0.75
                seedRemainingPlays--
            } else {
                seedWeight *= 0.5
                seedRemainingPlays--
            }

            if (seedRemainingPlays <= 0 || seedWeight < 0.05) {
                clearSeedTrack()
            }
        }

        saveEngineData()
    }

    /**
     * Select next track index using Smart Shuffle algorithm
     */
    fun selectNextTrackIndex(queue: List<Track>, currentIndex: Int): Int {
        if (queue.isEmpty()) return 0
        if (queue.size == 1) return 0

        val snapshot = getSnapshot()
        val currentTrack = queue.getOrNull(currentIndex)

        // Calculate scores for all eligible candidate tracks
        val candidateScores = queue.indices.map { idx ->
            val track = queue[idx]
            val isCurrent = (idx == currentIndex || track.id == currentTrack?.id)
            val rawScore = calculateTrackScore(snapshot, idx, track, queue, currentTrack, currentIndex)
            val weight = if (isCurrent) 0.0 else rawScore.coerceAtLeast(0.05)
            idx to weight
        }

        // Weighted Probabilistic Selection (Requirement #10)
        val totalScore = candidateScores.sumOf { it.second }
        if (totalScore <= 0.0) {
            // Fallback to random pick excluding current
            val candidateIndices = queue.indices.filter { it != currentIndex }
            return candidateIndices.randomOrNull() ?: 0
        }

        val randomVal = Random.nextDouble(0.0, totalScore)
        var cumulative = 0.0
        for ((idx, score) in candidateScores) {
            cumulative += score
            if (randomVal <= cumulative) {
                return idx
            }
        }

        return candidateScores.maxByOrNull { it.second }?.first ?: 0
    }

    /**
     * Generate dynamic upcoming recommendations list for Smart Shuffle window without blocking UI thread.
     */
    fun getUpcomingRecommendations(
        candidatePool: List<Track>,
        currentTrack: Track?,
        count: Int = 20
    ): List<Track> {
        if (candidatePool.isEmpty() || count <= 0) return emptyList()

        val snapshot = getSnapshot()
        val remainingCandidates = candidatePool
            .filter { it.id != currentTrack?.id }
            .distinctBy { it.id }

        if (remainingCandidates.isEmpty()) return emptyList()

        // Calculate score for each candidate track ONCE relative to snapshot and current track context
        val scoredList = remainingCandidates.map { track ->
            val rawScore = calculateTrackScore(
                snapshot = snapshot,
                candidateIndex = -1,
                candidate = track,
                queue = candidatePool,
                currentTrack = currentTrack,
                currentIndex = -1
            )
            track to rawScore.coerceAtLeast(0.1)
        }.toMutableList()

        val result = mutableListOf<Track>()
        val selectCount = minOf(count, scoredList.size)

        // Weighted sampling without replacement
        while (scoredList.isNotEmpty() && result.size < selectCount) {
            val totalScore = scoredList.sumOf { it.second }
            val chosenIndex = if (totalScore <= 0.0) {
                Random.nextInt(scoredList.size)
            } else {
                var cursor = Random.nextDouble(0.0, totalScore)
                var idx = scoredList.lastIndex
                for (i in scoredList.indices) {
                    cursor -= scoredList[i].second
                    if (cursor <= 0.0) {
                        idx = i
                        break
                    }
                }
                idx
            }

            val chosenTrack = scoredList.removeAt(chosenIndex).first
            result.add(chosenTrack)
        }

        return result
    }

    /**
     * Calculate dynamic score for a single candidate track using immutable snapshot data
     */
    private fun calculateTrackScore(
        snapshot: EngineSnapshot,
        candidateIndex: Int,
        candidate: Track,
        queue: List<Track>,
        currentTrack: Track?,
        currentIndex: Int
    ): Double {
        var score = 10.0 // Base Score

        val candidateArtist = normalize(candidate.artist)
        val candidateGenre = inferGenre(candidate)
        val candidateLanguage = inferLanguage(candidate)

        // 1. Recency Penalty (Requirement #9)
        val recencyIndex = snapshot.recentTrackIds.indexOf(candidate.id)
        if (recencyIndex != -1) {
            when (recencyIndex) {
                0 -> score -= 100.0 // Current or just played
                1 -> score -= 40.0
                2 -> score -= 25.0
                3 -> score -= 15.0
                in 4..7 -> score -= 8.0
                else -> score -= 3.0
            }
        }
        if (candidateIndex == currentIndex) {
            score -= 100.0
        }

        // Scenario 1: Seed Track Context boost
        if (snapshot.seedTrack != null && snapshot.seedWeight > 0.05 && snapshot.seedRemainingPlays > 0) {
            val seed = snapshot.seedTrack
            val seedArtist = normalize(seed.artist)
            val seedGenre = inferGenre(seed)
            val seedLanguage = inferLanguage(seed)

            val similarity = calculateSimilarity(
                candidateArtist, candidateGenre, candidateLanguage, candidate.album, candidate.title,
                seedArtist, seedGenre, seedLanguage, seed.album, seed.title
            )

            var seedScoreBoost = 0.0
            if (candidateArtist.isNotBlank() && candidateArtist == seedArtist) {
                seedScoreBoost += 16.0
            }
            if (candidateGenre.isNotBlank() && candidateGenre == seedGenre && candidateGenre != "General") {
                seedScoreBoost += 8.0
            }
            if (candidateLanguage == seedLanguage) {
                seedScoreBoost += 4.0
            }
            if (candidate.album.isNotBlank() && normalize(candidate.album) == normalize(seed.album)) {
                seedScoreBoost += 6.0
            }
            seedScoreBoost += similarity * 12.0

            val playsFactor = (snapshot.seedRemainingPlays.toDouble() / 5.0).coerceIn(0.2, 1.0)
            score += seedScoreBoost * snapshot.seedWeight * playsFactor
        }

        // 2. Short-Term Preference Model (Requirement #6 & #1)
        var shortTermScore = 0.0
        snapshot.shortTermHistory.forEachIndexed { index, hist ->
            val decayWeight = 1.0 - (index * 0.08) // Recent plays weigh more
            if (decayWeight > 0) {
                val similarity = calculateSimilarity(
                    candidateArtist, candidateGenre, candidateLanguage, candidate.album, candidate.title,
                    hist.artist, hist.genre, hist.language, hist.album, hist.title
                )

                if (hist.wasSkippedEarly) {
                    // Penalty for early skip (Requirement #3)
                    if (candidate.id == hist.trackId) {
                        shortTermScore -= 12.0 * decayWeight
                    } else {
                        shortTermScore -= 6.0 * similarity * decayWeight
                    }
                } else if (hist.completionRatio >= 0.8f) {
                    // Boost for completion (Requirement #4)
                    if (candidate.id == hist.trackId) {
                        shortTermScore += 8.0 * decayWeight
                    } else {
                        shortTermScore += 5.0 * similarity * decayWeight
                    }
                }
            }
        }

        // 3. Consecutive Replay Boost (Requirement #5)
        if (snapshot.consecutiveBoostRemainingPlays > 0 && snapshot.consecutiveBoostArtist != null) {
            val boostDecay = snapshot.consecutiveBoostRemainingPlays.toDouble() / 15.0
            if (candidateArtist == snapshot.consecutiveBoostArtist) {
                shortTermScore += 12.0 * boostDecay
            } else if (candidateGenre == snapshot.consecutiveBoostGenre) {
                shortTermScore += 6.0 * boostDecay
            }
        }

        // 4. Long-Term Preference Model (Requirement #6)
        var longTermScore = 0.0
        val comp = snapshot.artistCompletions[candidateArtist] ?: 0
        val skip = snapshot.artistSkips[candidateArtist] ?: 0
        val totalInteractions = comp + skip
        if (totalInteractions > 0) {
            val ratio = (comp - skip).toDouble() / totalInteractions.toDouble()
            longTermScore += ratio * 5.0
        }

        val trackComp = snapshot.trackCompletions[candidate.id] ?: 0
        val trackSkip = snapshot.trackSkips[candidate.id] ?: 0
        if (trackComp + trackSkip > 0) {
            longTermScore += (trackComp - trackSkip) * 2.0
        }

        // Combine Short-term (0.7) and Long-term (0.3)
        score += (shortTermScore * 0.7) + (longTermScore * 0.3)

        // 5. Diversity Penalty (Requirement #7)
        if (currentTrack != null) {
            if (normalize(currentTrack.artist) == candidateArtist && snapshot.consecutiveBoostArtist != candidateArtist) {
                score -= 15.0 // Avoid repeating same artist consecutively
            }
            if (normalize(currentTrack.album) == normalize(candidate.album) && candidate.album.isNotBlank()) {
                score -= 10.0 // Avoid repeating same album consecutively
            }
        }

        // Floor at 0.1 so every track has a tiny non-zero probability
        return max(0.1, score)
    }

    /**
     * Reusable similarity calculator (Requirement #13)
     */
    fun calculateSimilarity(
        artist1: String, genre1: String, lang1: String, album1: String, title1: String,
        artist2: String, genre2: String, lang2: String, album2: String, title2: String
    ): Double {
        var similarity = 0.0

        if (artist1.isNotBlank() && artist1 == artist2) similarity += 0.4
        if (genre1.isNotBlank() && genre1 == genre2) similarity += 0.25
        if (lang1.isNotBlank() && lang1 == lang2) similarity += 0.15
        if (album1.isNotBlank() && normalize(album1) == normalize(album2)) similarity += 0.1

        val words1 = title1.split(" ", "-", "_").filter { it.length > 2 }
        val words2 = title2.split(" ", "-", "_").filter { it.length > 2 }
        val commonWords = words1.intersect(words2.toSet())
        if (commonWords.isNotEmpty()) {
            similarity += 0.1
        }

        return min(1.0, similarity)
    }

    private fun inferGenre(track: Track): String {
        val text = "${track.title} ${track.artist} ${track.album}".lowercase()
        return when {
            text.contains("pop") || text.contains("پاپ") -> "Pop"
            text.contains("rock") || text.contains("راک") -> "Rock"
            text.contains("traditional") || text.contains("سنتی") || text.contains("ملی") -> "Traditional"
            text.contains("remix") || text.contains("ریمیکس") -> "Remix"
            text.contains("rap") || text.contains("hip hop") || text.contains("رپ") -> "Rap"
            text.contains("beat") || text.contains("instrumental") || text.contains("بی کلام") -> "Instrumental"
            else -> "General"
        }
    }

    private fun inferLanguage(track: Track): String {
        val text = "${track.title} ${track.artist} ${track.album}"
        val containsPersian = text.any { it in '\u0600'..'\u06FF' }
        return if (containsPersian) "Persian" else "English"
    }

    private fun normalize(str: String): String {
        return str.trim().lowercase()
    }

    @Synchronized
    private fun loadEngineData() {
        try {
            // 1. Long-term stats
            val statsJson = prefs.getString("long_term_stats_json", null)
            if (!statsJson.isNullOrBlank()) {
                val json = org.json.JSONObject(statsJson)
                jsonToMap(json.optJSONObject("artistCompletions"), artistCompletions)
                jsonToMap(json.optJSONObject("artistSkips"), artistSkips)
                jsonToMap(json.optJSONObject("genreCompletions"), genreCompletions)
                jsonToMap(json.optJSONObject("genreSkips"), genreSkips)
                jsonToLongMap(json.optJSONObject("trackCompletions"), trackCompletions)
                jsonToLongMap(json.optJSONObject("trackSkips"), trackSkips)
            } else {
                // Legacy fallback
                val statsString = prefs.getString("long_term_stats", null)
                if (!statsString.isNullOrBlank()) {
                    val parts = statsString.split("|")
                    if (parts.size >= 6) {
                        parseStringMap(parts[0], artistCompletions)
                        parseStringMap(parts[1], artistSkips)
                        parseStringMap(parts[2], genreCompletions)
                        parseStringMap(parts[3], genreSkips)
                        parseLongMap(parts[4], trackCompletions)
                        parseLongMap(parts[5], trackSkips)
                    }
                }
            }

            // 2. Short-term history
            shortTermHistory.clear()
            val historyJson = prefs.getString("short_term_history_json", null)
            if (!historyJson.isNullOrBlank()) {
                val arr = org.json.JSONArray(historyJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    shortTermHistory.add(
                        PlayedHistoryItem(
                            trackId = obj.getLong("trackId"),
                            artist = obj.optString("artist", ""),
                            album = obj.optString("album", ""),
                            title = obj.optString("title", ""),
                            genre = obj.optString("genre", ""),
                            language = obj.optString("language", ""),
                            completionRatio = obj.optDouble("completionRatio", 1.0).toFloat(),
                            wasSkippedEarly = obj.optBoolean("wasSkippedEarly", false),
                            wasRepeatedConsecutively = obj.optBoolean("wasRepeatedConsecutively", false),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            } else {
                // Legacy fallback
                val historyStr = prefs.getString("short_term_history", null)
                if (!historyStr.isNullOrBlank()) {
                    historyStr.split("|||").forEach { rawItem ->
                        val tokens = rawItem.split(":::")
                        if (tokens.size >= 10) {
                            val trackId = tokens[0].toLongOrNull()
                            if (trackId != null) {
                                shortTermHistory.add(
                                    PlayedHistoryItem(
                                        trackId = trackId,
                                        artist = decode(tokens[1]),
                                        album = decode(tokens[2]),
                                        title = decode(tokens[3]),
                                        genre = decode(tokens[4]),
                                        language = decode(tokens[5]),
                                        completionRatio = tokens[6].toFloatOrNull() ?: 1.0f,
                                        wasSkippedEarly = tokens[7].toBooleanStrictOrNull() ?: false,
                                        wasRepeatedConsecutively = tokens[8].toBooleanStrictOrNull() ?: false,
                                        timestamp = tokens[9].toLongOrNull() ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3. Recency track IDs
            recentTrackIds.clear()
            val recentStr = prefs.getString("recent_track_ids", null)
            if (!recentStr.isNullOrBlank()) {
                recentStr.split(",").mapNotNull { it.trim().toLongOrNull() }.forEach { id ->
                    recentTrackIds.add(id)
                }
            }

            // 4. Consecutive boost
            val boostJson = prefs.getString("consecutive_boost_json", null)
            if (!boostJson.isNullOrBlank()) {
                val obj = org.json.JSONObject(boostJson)
                consecutiveBoostArtist = obj.optString("artist").takeIf { it.isNotBlank() }
                consecutiveBoostGenre = obj.optString("genre").takeIf { it.isNotBlank() }
                consecutiveBoostRemainingPlays = obj.optInt("remainingPlays", 0)
            } else {
                val boostStr = prefs.getString("consecutive_boost", null)
                if (!boostStr.isNullOrBlank()) {
                    val parts = boostStr.split(":::")
                    if (parts.size >= 3) {
                        consecutiveBoostArtist = if (parts[0].isNotBlank()) parts[0] else null
                        consecutiveBoostGenre = if (parts[1].isNotBlank()) parts[1] else null
                        consecutiveBoostRemainingPlays = parts[2].toIntOrNull() ?: 0
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun saveEngineData() {
        try {
            // 1. Long term maps
            val statsObj = org.json.JSONObject().apply {
                put("artistCompletions", mapToJson(artistCompletions))
                put("artistSkips", mapToJson(artistSkips))
                put("genreCompletions", mapToJson(genreCompletions))
                put("genreSkips", mapToJson(genreSkips))
                put("trackCompletions", mapToJson(trackCompletions))
                put("trackSkips", mapToJson(trackSkips))
            }

            // 2. Short term history
            val historyArr = org.json.JSONArray()
            shortTermHistory.forEach { item ->
                val obj = org.json.JSONObject().apply {
                    put("trackId", item.trackId)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("title", item.title)
                    put("genre", item.genre)
                    put("language", item.language)
                    put("completionRatio", item.completionRatio.toDouble())
                    put("wasSkippedEarly", item.wasSkippedEarly)
                    put("wasRepeatedConsecutively", item.wasRepeatedConsecutively)
                    put("timestamp", item.timestamp)
                }
                historyArr.put(obj)
            }

            // 3. Recency IDs
            val recencySerialized = recentTrackIds.joinToString(",")

            // 4. Consecutive boost
            val boostObj = org.json.JSONObject().apply {
                put("artist", consecutiveBoostArtist ?: "")
                put("genre", consecutiveBoostGenre ?: "")
                put("remainingPlays", consecutiveBoostRemainingPlays)
            }

            prefs.edit()
                .putString("long_term_stats_json", statsObj.toString())
                .putString("short_term_history_json", historyArr.toString())
                .putString("recent_track_ids", recencySerialized)
                .putString("consecutive_boost_json", boostObj.toString())
                .apply()
        } catch (_: Exception) {}
    }

    private fun <K> mapToJson(map: Map<K, Int>): org.json.JSONObject {
        val obj = org.json.JSONObject()
        map.forEach { (k, v) -> obj.put(k.toString(), v) }
        return obj
    }

    private fun jsonToMap(obj: org.json.JSONObject?, targetMap: MutableMap<String, Int>) {
        targetMap.clear()
        if (obj == null) return
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            targetMap[key] = obj.optInt(key, 0)
        }
    }

    private fun jsonToLongMap(obj: org.json.JSONObject?, targetMap: MutableMap<Long, Int>) {
        targetMap.clear()
        if (obj == null) return
        val keys = obj.keys()
        while (keys.hasNext()) {
            val keyStr = keys.next()
            val keyLong = keyStr.toLongOrNull()
            if (keyLong != null) {
                targetMap[keyLong] = obj.optInt(keyStr, 0)
            }
        }
    }

    private fun parseStringMap(str: String, targetMap: MutableMap<String, Int>) {
        targetMap.clear()
        if (str.isBlank()) return
        str.split(";").forEach { pair ->
            val kv = pair.split("=")
            if (kv.size == 2) {
                kv[1].toIntOrNull()?.let { count ->
                    targetMap[kv[0]] = count
                }
            }
        }
    }

    private fun parseLongMap(str: String, targetMap: MutableMap<Long, Int>) {
        targetMap.clear()
        if (str.isBlank()) return
        str.split(";").forEach { pair ->
            val kv = pair.split("=")
            if (kv.size == 2) {
                val key = kv[0].toLongOrNull()
                val valCount = kv[1].toIntOrNull()
                if (key != null && valCount != null) {
                    targetMap[key] = valCount
                }
            }
        }
    }

    private fun decode(str: String): String {
        return try {
            java.net.URLDecoder.decode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
    }
}
