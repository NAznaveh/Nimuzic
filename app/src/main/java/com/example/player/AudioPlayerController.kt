package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.data.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class SleepTimerStatus(
    val isActive: Boolean = false,
    val isEndOfSong: Boolean = false,
    val totalMinutes: Int = 0,
    val remainingSeconds: Long = 0L,
    val targetTimestampMs: Long = 0L
)

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val playbackSessionId: Long = 0L,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 210000L,
    val playbackSpeed: Float = 1.0f,
    val shuffleMode: ShuffleMode = ShuffleMode.FAIR_NON_REPEAT,
    val repeatMode: RepeatMode = RepeatMode.REPEAT_ALL,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = -1,
    val fairShuffleRemainingQueue: List<Track> = emptyList(),
    val standardShuffleRemainingQueue: List<Track> = emptyList(),
    val smartShuffleUpcoming: List<Track> = emptyList(),
    val forcedNextTrackId: Long? = null,
    val fairShufflePlayedCount: Int = 0,
    val fairShuffleTotalCount: Int = 0,
    val totalStreamSeconds: Long = 0L,
    val equalizerSettings: EqualizerSettings = EqualizerPresets.BASS_BOOST,
    val visualizerAmplitudes: List<Float> = List(12) { 0.2f },
    val isLyricsVisible: Boolean = false,
    val sleepTimerStatus: SleepTimerStatus = SleepTimerStatus()
) {
    val upNextQueue: List<Track> by lazy {
        val base = when (shuffleMode) {
            ShuffleMode.FAIR_NON_REPEAT -> fairShuffleRemainingQueue
            ShuffleMode.SMART_SHUFFLE -> smartShuffleUpcoming
            ShuffleMode.STANDARD_RANDOM -> standardShuffleRemainingQueue
            ShuffleMode.OFF -> if (currentQueueIndex >= 0 && currentQueueIndex < queue.lastIndex) queue.drop(currentQueueIndex + 1) else emptyList()
        }
        val distinctBase = base.distinctBy { it.id }
        val forced = forcedNextTrackId?.let { id -> distinctBase.firstOrNull { it.id == id } }
        if (forced != null) listOf(forced) + distinctBase.filter { it.id != forced.id } else distinctBase
    }
}

class AudioPlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var activeAudioSessionId: Int = 0
    private var positionUpdateJob: Job? = null
    private val prefs = context.getSharedPreferences("nimusic_player_prefs", Context.MODE_PRIVATE)

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var transitionWakeLock: PowerManager.WakeLock? = null

    private fun acquireTransitionWakeLock() {
        try {
            if (transitionWakeLock == null) {
                transitionWakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NiMusic:TrackTransitionWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            transitionWakeLock?.acquire(10_000L)
        } catch (e: Exception) {
            Log.w("AudioPlayer", "Could not acquire transition wake lock: ${e.message}")
        }
    }

    private fun releaseTransitionWakeLock() {
        try {
            if (transitionWakeLock?.isHeld == true) {
                transitionWakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun loadEqualizerSettings(): EqualizerSettings {
        val p = context.getSharedPreferences("nimusic_equalizer_prefs", Context.MODE_PRIVATE)
        return EqualizerSettings(
            isEnabled = p.getBoolean("enabled", true),
            presetName = p.getString("preset", EqualizerPresets.BASS_BOOST.presetName) ?: EqualizerPresets.BASS_BOOST.presetName,
            band60Hz = p.getFloat("b60", EqualizerPresets.BASS_BOOST.band60Hz),
            band230Hz = p.getFloat("b230", EqualizerPresets.BASS_BOOST.band230Hz),
            band910Hz = p.getFloat("b910", EqualizerPresets.BASS_BOOST.band910Hz),
            band3600Hz = p.getFloat("b3600", EqualizerPresets.BASS_BOOST.band3600Hz),
            band14000Hz = p.getFloat("b14000", EqualizerPresets.BASS_BOOST.band14000Hz),
            bassBoost = p.getFloat("bass", EqualizerPresets.BASS_BOOST.bassBoost),
            virtualizer3D = p.getFloat("virtualizer", EqualizerPresets.BASS_BOOST.virtualizer3D)
        )
    }

    private fun persistEqualizerSettings(settings: EqualizerSettings) {
        context.getSharedPreferences("nimusic_equalizer_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", settings.isEnabled)
            .putString("preset", settings.presetName)
            .putFloat("b60", settings.band60Hz)
            .putFloat("b230", settings.band230Hz)
            .putFloat("b910", settings.band910Hz)
            .putFloat("b3600", settings.band3600Hz)
            .putFloat("b14000", settings.band14000Hz)
            .putFloat("bass", settings.bassBoost)
            .putFloat("virtualizer", settings.virtualizer3D)
            .apply()
    }

    private fun releaseAudioEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        activeAudioSessionId = 0
    }

    private fun applyAudioEffects(settings: EqualizerSettings = _playerState.value.equalizerSettings) {
        val sessionId = activeAudioSessionId
        if (sessionId == 0) return
        releaseAudioEffects()
        activeAudioSessionId = sessionId
        try {
            val eq = Equalizer(0, sessionId)
            eq.enabled = settings.isEnabled
            val minLevel = eq.bandLevelRange[0].toInt()
            val maxLevel = eq.bandLevelRange[1].toInt()
            for (band in 0 until eq.numberOfBands.toInt()) {
                val centerHz = eq.getCenterFreq(band.toShort()) / 1000
                val nearest = when {
                    centerHz <= 120 -> settings.band60Hz
                    centerHz <= 500 -> settings.band230Hz
                    centerHz <= 1800 -> settings.band910Hz
                    centerHz <= 7000 -> settings.band3600Hz
                    else -> settings.band14000Hz
                }
                eq.setBandLevel(band.toShort(), (nearest * 100f).coerceIn(minLevel.toFloat(), maxLevel.toFloat()).toInt().toShort())
            }
            equalizer = eq
        } catch (e: Exception) {
            Log.w("AudioEffects", "Equalizer unavailable: ${e.message}")
        }
        try {
            val bb = BassBoost(0, sessionId)
            bb.enabled = settings.isEnabled
            bb.setStrength((settings.bassBoost.coerceIn(0f, 100f) * 10f).toInt().toShort())
            bassBoost = bb
        } catch (e: Exception) {
            Log.w("AudioEffects", "BassBoost unavailable: ${e.message}")
        }
        try {
            val vz = Virtualizer(0, sessionId)
            vz.enabled = settings.isEnabled
            vz.setStrength((settings.virtualizer3D.coerceIn(0f, 100f) * 10f).toInt().toShort())
            virtualizer = vz
        } catch (e: Exception) {
            Log.w("AudioEffects", "Virtualizer unavailable: ${e.message}")
        }
    }
    private var focusRequest: AudioFocusRequest? = null

    val smartShuffleEngine = SmartShuffleEngine(context)
    private var lastPlayedTrackId: Long? = null
    private var consecutivePlaybackFailures = 0
    @Volatile private var lastTrackTransitionTime = 0L
    @Volatile private var currentPlaybackSessionId = 0L
    @Volatile private var handledSessionId = 0L
    private var smartShuffleJob: Job? = null

    @Synchronized
    private fun tryHandleSessionTransition(sessionId: Long, action: () -> Unit): Boolean {
        if (sessionId != currentPlaybackSessionId) {
            Log.d("AudioPlayer", "Ignoring transition request for stale session $sessionId (current: $currentPlaybackSessionId)")
            return false
        }
        if (handledSessionId == sessionId) {
            Log.d("AudioPlayer", "Ignoring duplicate transition request for session $sessionId")
            return false
        }
        handledSessionId = sessionId
        action()
        return true
    }

    @Volatile private var wasPlayingBeforeFocusLoss = false
    @Volatile private var focusLossSessionId = -1L
    private val saveStateMutex = kotlinx.coroutines.sync.Mutex()

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            wasPlayingBeforeFocusLoss = false
                            focusLossSessionId = -1L
                            if (_playerState.value.isPlaying) {
                                mediaPlayer?.let { mp -> try { mp.pause() } catch (_: Exception) {} }
                                _playerState.value = _playerState.value.copy(isPlaying = false)
                                notificationManager.updateNotification(_playerState.value.currentTrack, false, _playerState.value.currentPositionMs)
                            }
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            if (_playerState.value.isPlaying) {
                                wasPlayingBeforeFocusLoss = true
                                focusLossSessionId = currentPlaybackSessionId
                                mediaPlayer?.let { mp -> try { if (mp.isPlaying) mp.pause() } catch (_: Exception) {} }
                                _playerState.value = _playerState.value.copy(isPlaying = false)
                                notificationManager.updateNotification(_playerState.value.currentTrack, false, _playerState.value.currentPositionMs)
                            }
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            if (wasPlayingBeforeFocusLoss && focusLossSessionId == currentPlaybackSessionId) {
                                wasPlayingBeforeFocusLoss = false
                                focusLossSessionId = -1L
                                mediaPlayer?.let { mp ->
                                    try {
                                        mp.start()
                                        _playerState.value = _playerState.value.copy(isPlaying = true)
                                        notificationManager.updateNotification(_playerState.value.currentTrack, true, _playerState.value.currentPositionMs)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
                .build()
            val res = audioManager.requestAudioFocus(focusRequest!!)
            return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val res = audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS && _playerState.value.isPlaying) {
                        wasPlayingBeforeFocusLoss = false
                        focusLossSessionId = -1L
                        togglePlayPause()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private val notificationManager = MediaNotificationManager(
        context = context,
        onPlayPause = { togglePlayPause() },
        onNext = { nextTrack() },
        onPrevious = { previousTrack() },
        onSeekTo = { pos -> seekTo(pos) }
    )

    private val _playerState = MutableStateFlow(PlayerState(equalizerSettings = loadEqualizerSettings()))
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Fair Shuffle State Tracking
    private val playedTrackIdsInCycle = mutableSetOf<Long>()
    private var totalStreamSeconds: Long = prefs.getLong("total_stream_seconds", 0L)
    private var accumulatedMillis: Long = 0L
    private var tickCounter: Int = 0

    init {
        val initialShuffle = runCatching { ShuffleMode.valueOf(prefs.getString("shuffle_mode", ShuffleMode.FAIR_NON_REPEAT.name) ?: ShuffleMode.FAIR_NON_REPEAT.name) }.getOrDefault(ShuffleMode.FAIR_NON_REPEAT)
        val initialRepeat = runCatching { RepeatMode.valueOf(prefs.getString("repeat_mode", RepeatMode.REPEAT_ALL.name) ?: RepeatMode.REPEAT_ALL.name) }.getOrDefault(RepeatMode.REPEAT_ALL)
        _playerState.value = _playerState.value.copy(
            shuffleMode = initialShuffle,
            repeatMode = initialRepeat,
            totalStreamSeconds = totalStreamSeconds
        )
        startPositionAndVisualizerLoop()
    }

    fun savePlayerState() {
        val state = _playerState.value
        val playedIds = playedTrackIdsInCycle.toList()
        scope.launch(Dispatchers.IO) {
            saveStateMutex.withLock {
                try {
                    val queueIds = state.queue.joinToString(",") { it.id.toString() }
                    val playedIdsStr = playedIds.joinToString(",")

                    prefs.edit()
                        .putLong("last_track_id", state.currentTrack?.id ?: -1L)
                        .putLong("last_position_ms", state.currentPositionMs)
                        .putString("queue_track_ids", queueIds)
                        .putInt("current_queue_index", state.currentQueueIndex)
                        .putString("shuffle_mode", state.shuffleMode.name)
                        .putString("repeat_mode", state.repeatMode.name)
                        .putLong("forced_next_track_id", state.forcedNextTrackId ?: -1L)
                        .putFloat("playback_speed", state.playbackSpeed)
                        .putString("fair_shuffle_played_ids", playedIdsStr)
                        .putLong("total_stream_seconds", totalStreamSeconds)
                        .apply()
                } catch (_: Exception) {}
            }
        }
    }

    fun restoreSavedState(allTracks: List<Track>) {
        if (allTracks.isEmpty()) return

        val savedTrackId = prefs.getLong("last_track_id", -1L)
        val savedPositionMs = prefs.getLong("last_position_ms", 0L)
        val queueIdsStr = prefs.getString("queue_track_ids", "") ?: ""
        val savedQueueIndex = prefs.getInt("current_queue_index", -1)
        val shuffleModeStr = prefs.getString("shuffle_mode", ShuffleMode.FAIR_NON_REPEAT.name) ?: ShuffleMode.FAIR_NON_REPEAT.name
        val repeatModeStr = prefs.getString("repeat_mode", RepeatMode.REPEAT_ALL.name) ?: RepeatMode.REPEAT_ALL.name
        val savedForcedNextId = prefs.getLong("forced_next_track_id", -1L)
        val savedSpeed = prefs.getFloat("playback_speed", 1.0f)
        val playedIdsStr = prefs.getString("fair_shuffle_played_ids", "") ?: ""
        totalStreamSeconds = prefs.getLong("total_stream_seconds", 0L)

        val shuffleMode = runCatching { ShuffleMode.valueOf(shuffleModeStr) }.getOrDefault(ShuffleMode.FAIR_NON_REPEAT)
        val repeatMode = runCatching { RepeatMode.valueOf(repeatModeStr) }.getOrDefault(RepeatMode.REPEAT_ALL)

        val queueTrackIds = if (queueIdsStr.isNotBlank()) {
            queueIdsStr.split(",").mapNotNull { it.toLongOrNull() }
        } else emptyList()

        val tracksMap = allTracks.associateBy { it.id }
        val restoredQueue = if (queueTrackIds.isNotEmpty()) {
            queueTrackIds.mapNotNull { id -> tracksMap[id] }
        } else {
            allTracks
        }

        if (restoredQueue.isEmpty()) return

        val currentTrack = if (savedTrackId > 0) {
            tracksMap[savedTrackId] ?: restoredQueue.getOrNull(savedQueueIndex.coerceIn(0, restoredQueue.lastIndex)) ?: restoredQueue.first()
        } else {
            restoredQueue.first()
        }

        val validQueueIndex = restoredQueue.indexOfFirst { it.id == currentTrack.id }.let { idx ->
            if (idx >= 0) idx else savedQueueIndex.coerceIn(0, restoredQueue.lastIndex)
        }

        playedTrackIdsInCycle.clear()
        if (playedIdsStr.isNotBlank()) {
            playedIdsStr.split(",").mapNotNull { it.toLongOrNull() }.forEach { id ->
                if (tracksMap.containsKey(id)) {
                    playedTrackIdsInCycle.add(id)
                }
            }
        }

        val restoredFairRemaining = restoredQueue
            .filter { !playedTrackIdsInCycle.contains(it.id) }
            .shuffled()

        _playerState.value = _playerState.value.copy(
            currentTrack = currentTrack,
            queue = restoredQueue,
            fairShuffleRemainingQueue = restoredFairRemaining,
            currentQueueIndex = validQueueIndex,
            currentPositionMs = savedPositionMs,
            durationMs = if (currentTrack.durationMs > 0) currentTrack.durationMs else 210000L,
            isPlaying = false,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            forcedNextTrackId = savedForcedNextId.takeIf { it > 0L && restoredQueue.any { track -> track.id == it } },
            playbackSpeed = savedSpeed.coerceIn(0.5f, 3.0f),
            fairShufflePlayedCount = playedTrackIdsInCycle.size.coerceAtMost(restoredQueue.size),
            fairShuffleTotalCount = restoredQueue.size,
            totalStreamSeconds = totalStreamSeconds
        )

        notificationManager.updateNotification(currentTrack, false, savedPositionMs)
        if (shuffleMode == ShuffleMode.SMART_SHUFFLE) {
            recalculateSmartShuffleUpcoming()
        }

        // Restore active sleep timer if valid
        val isEndOfSongTimer = prefs.getBoolean("sleep_timer_end_of_song", false)
        val savedTimerTargetMs = prefs.getLong("sleep_timer_target_ms", 0L)
        val savedTimerMinutes = prefs.getInt("sleep_timer_minutes", 0)

        if (isEndOfSongTimer) {
            cancelSleepTimer()
        } else if (savedTimerTargetMs > 0L) {
            val now = System.currentTimeMillis()
            if (now >= savedTimerTargetMs) {
                cancelSleepTimer()
            } else {
                startSleepTimerCountdown(savedTimerTargetMs, savedTimerMinutes)
            }
        }
    }

    private var sleepTimerJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes == -1) {
            _playerState.value = _playerState.value.copy(
                sleepTimerStatus = SleepTimerStatus(
                    isActive = true,
                    isEndOfSong = true,
                    totalMinutes = -1
                )
            )
            prefs.edit()
                .putBoolean("sleep_timer_end_of_song", true)
                .putLong("sleep_timer_target_ms", -1L)
                .putInt("sleep_timer_minutes", -1)
                .apply()
        } else if (minutes > 0) {
            val durationMs = minutes * 60 * 1000L
            val targetMs = System.currentTimeMillis() + durationMs
            prefs.edit()
                .putBoolean("sleep_timer_end_of_song", false)
                .putLong("sleep_timer_target_ms", targetMs)
                .putInt("sleep_timer_minutes", minutes)
                .apply()

            startSleepTimerCountdown(targetMs, minutes)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _playerState.value = _playerState.value.copy(
            sleepTimerStatus = SleepTimerStatus(isActive = false)
        )
        prefs.edit()
            .putBoolean("sleep_timer_end_of_song", false)
            .putLong("sleep_timer_target_ms", 0L)
            .putInt("sleep_timer_minutes", 0)
            .apply()
    }

    private fun startSleepTimerCountdown(targetMs: Long, totalMins: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remainingMs = targetMs - now
                if (remainingMs <= 0L) {
                    if (_playerState.value.isPlaying) {
                        try {
                            mediaPlayer?.pause()
                        } catch (_: Exception) {}
                        _playerState.value = _playerState.value.copy(isPlaying = false)
                        notificationManager.updateNotification(_playerState.value.currentTrack, false, _playerState.value.currentPositionMs)
                    }
                    cancelSleepTimer()
                    break
                } else {
                    val remainingSecs = remainingMs / 1000L
                    _playerState.value = _playerState.value.copy(
                        sleepTimerStatus = SleepTimerStatus(
                            isActive = true,
                            isEndOfSong = false,
                            totalMinutes = totalMins,
                            remainingSeconds = remainingSecs,
                            targetTimestampMs = targetMs
                        )
                    )
                }
                delay(500L)
            }
        }
    }

    fun setQueueAndPlay(tracks: List<Track>, startIndex: Int = 0, isManualSelect: Boolean = true) {
        if (tracks.isEmpty()) return

        var selectedIndex = startIndex.coerceIn(0, tracks.lastIndex)

        // Initial Shuffle Selection Requirement:
        // When Shuffle Mode is active and not a manual single-track select,
        // choose a genuinely random eligible track index rather than defaulting to index 0.
        if (!isManualSelect && _playerState.value.shuffleMode != ShuffleMode.OFF && tracks.size > 1) {
            val candidateIndices = tracks.indices.filter { idx ->
                _playerState.value.currentTrack?.id == null || tracks[idx].id != _playerState.value.currentTrack?.id
            }
            selectedIndex = if (candidateIndices.isNotEmpty()) {
                candidateIndices.random()
            } else {
                Random.nextInt(tracks.size)
            }
        }

        val selectedTrack = tracks[selectedIndex]

        if (isManualSelect) {
            smartShuffleEngine.setSeedTrack(selectedTrack)
        }

        _playerState.value = _playerState.value.copy(
            queue = tracks,
            currentQueueIndex = selectedIndex,
            forcedNextTrackId = null
        )

        resetFairShufflePool(tracks, selectedTrack.id)
        resetStandardShufflePool(tracks, selectedTrack.id)
        playTrack(selectedTrack, isExplicitSkip = false, isManualSelect = isManualSelect)
    }

    fun recalculateSmartShuffleUpcoming() {
        smartShuffleJob?.cancel()
        val capturedSession = currentPlaybackSessionId
        val capturedTrack = _playerState.value.currentTrack
        smartShuffleJob = scope.launch(Dispatchers.Default) {
            val state = _playerState.value
            val baseQueue = state.queue
            val current = state.currentTrack
            if (baseQueue.isEmpty() || state.shuffleMode != ShuffleMode.SMART_SHUFFLE) return@launch
            val recs = smartShuffleEngine.getUpcomingRecommendations(baseQueue, current, 20)
            withContext(Dispatchers.Main) {
                if (isActive &&
                    _playerState.value.shuffleMode == ShuffleMode.SMART_SHUFFLE &&
                    currentPlaybackSessionId == capturedSession &&
                    _playerState.value.currentTrack?.id == capturedTrack?.id) {
                    _playerState.value = _playerState.value.copy(smartShuffleUpcoming = recs)
                    savePlayerState()
                }
            }
        }
    }

    fun playTrack(track: Track, isExplicitSkip: Boolean = false, isManualSelect: Boolean = false) {
        acquireTransitionWakeLock()
        lastTrackTransitionTime = System.currentTimeMillis()
        val sessionId = ++currentPlaybackSessionId
        handledSessionId = 0L // Reset handled session marker for the new session

        val currentQueue = _playerState.value.queue
        val indexInQueue = currentQueue.indexOfFirst { it.id == track.id }
        val updatedIndex = if (indexInQueue >= 0) indexInQueue else {
            val newQ = currentQueue + track
            _playerState.value = _playerState.value.copy(queue = newQ)
            newQ.lastIndex
        }

        if (isManualSelect) {
            smartShuffleEngine.setSeedTrack(track)
        }

        // Record playback event for previously playing track ONCE (at transition time)
        val previousTrack = _playerState.value.currentTrack
        if (previousTrack != null) {
            val isConsecutive = (previousTrack.id == track.id)
            smartShuffleEngine.recordPlaybackEvent(
                track = previousTrack,
                positionMs = _playerState.value.currentPositionMs,
                durationMs = _playerState.value.durationMs,
                isExplicitSkip = isExplicitSkip,
                isConsecutiveReplay = isConsecutive
            )
        }
        lastPlayedTrackId = track.id
        playedTrackIdsInCycle.add(track.id)

        // Fair Shuffle dynamic queue update
        var remainingFair = _playerState.value.fairShuffleRemainingQueue.filter { it.id != track.id }
        if (remainingFair.isEmpty() && currentQueue.size > 1) {
            playedTrackIdsInCycle.clear()
            playedTrackIdsInCycle.add(track.id)
            remainingFair = currentQueue.filter { it.id != track.id }.shuffled()
        }

        // Standard Shuffle dynamic queue update
        var remainingStandard = _playerState.value.standardShuffleRemainingQueue.filter { it.id != track.id }
        if (remainingStandard.isEmpty() && currentQueue.size > 1) {
            remainingStandard = currentQueue.filter { it.id != track.id }.shuffled()
        }

        // Smart Shuffle dynamic queue update
        val remainingSmart = _playerState.value.smartShuffleUpcoming.filter { it.id != track.id }

        _playerState.value = _playerState.value.copy(
            currentTrack = track,
            currentQueueIndex = updatedIndex,
            currentPositionMs = 0L,
            durationMs = if (track.durationMs > 0) track.durationMs else 210000L,
            isPlaying = false, // Must be false until MediaPlayer onPrepared starts playback!
            fairShuffleRemainingQueue = remainingFair,
            standardShuffleRemainingQueue = remainingStandard,
            smartShuffleUpcoming = remainingSmart,
            fairShufflePlayedCount = playedTrackIdsInCycle.size.coerceAtMost(currentQueue.size),
            fairShuffleTotalCount = currentQueue.size
        )

        recalculateSmartShuffleUpcoming()
        savePlayerState()

        requestAudioFocus()
        notificationManager.updateNotification(track, isPlaying = false, currentPositionMs = 0L, isPreparing = true)
        startMediaPlayer(track, startPositionMs = 0L, sessionId = sessionId)
    }

    private fun resolveAudioUrlOrPath(track: Track): String? {
        val path = track.downloadedPath
        if (path.isNotEmpty()) {
            if (path.startsWith("content://")) return path
            val downloadedFile = java.io.File(path)
            if (downloadedFile.exists() && downloadedFile.length() > 0) {
                return path
            }
        }
        val url = track.audioUrl
        if (url.isNotEmpty()) {
            if (url.startsWith("content://")) return url
            if (url.startsWith("http://") || url.startsWith("https://")) return url
            val urlFile = java.io.File(url)
            if (urlFile.exists() && urlFile.length() > 0) {
                return url
            }
        }
        return null
    }

    private fun handlePlaybackFailure(failedTrack: Track, sessionId: Long) {
        releaseTransitionWakeLock()
        tryHandleSessionTransition(sessionId) {
            consecutivePlaybackFailures++
            val queueSize = _playerState.value.queue.size
            Log.w("AudioPlayer", "Playback failed for '${failedTrack.title}'. Consecutive failures: $consecutivePlaybackFailures / $queueSize")

            try {
                mediaPlayer?.setOnPreparedListener(null)
                mediaPlayer?.setOnCompletionListener(null)
                mediaPlayer?.setOnErrorListener(null)
                mediaPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null

            if (queueSize > 1 && consecutivePlaybackFailures < queueSize) {
                nextTrack(isExplicitSkip = false)
            } else {
                consecutivePlaybackFailures = 0
                _playerState.value = _playerState.value.copy(isPlaying = false)
                notificationManager.updateNotification(failedTrack, isPlaying = false, currentPositionMs = 0L, isPreparing = false)
            }
        }
    }

    private fun startMediaPlayer(track: Track, startPositionMs: Long = 0L, sessionId: Long = currentPlaybackSessionId) {
        if (sessionId != currentPlaybackSessionId) {
            releaseTransitionWakeLock()
            return
        }
        try {
            mediaPlayer?.let { mp ->
                try { mp.setOnPreparedListener(null) } catch (_: Exception) {}
                try { mp.setOnCompletionListener(null) } catch (_: Exception) {}
                try { mp.setOnErrorListener(null) } catch (_: Exception) {}
                try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
                try { mp.release() } catch (_: Exception) {}
            }
            mediaPlayer = null

            val urlToPlay = resolveAudioUrlOrPath(track)
            if (urlToPlay.isNullOrBlank()) {
                Log.e("AudioPlayer", "Cannot play track '${track.title}': Audio URL/path is invalid or file missing")
                handlePlaybackFailure(track, sessionId)
                return
            }

            val newPlayer = MediaPlayer().apply {
                try {
                    setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                } catch (e: Exception) {
                    Log.w("AudioPlayer", "Could not set wake mode: ${e.message}")
                }
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                if (urlToPlay.startsWith("content://")) {
                    setDataSource(context, android.net.Uri.parse(urlToPlay))
                } else {
                    setDataSource(urlToPlay)
                }

                setOnPreparedListener { mp ->
                    releaseTransitionWakeLock()
                    if (sessionId != currentPlaybackSessionId || mediaPlayer != mp) {
                        try { mp.release() } catch (_: Exception) {}
                        return@setOnPreparedListener
                    }
                    consecutivePlaybackFailures = 0
                    if (startPositionMs > 0L) {
                        try { mp.seekTo(startPositionMs.toInt()) } catch (_: Exception) {}
                    }
                    activeAudioSessionId = mp.audioSessionId
                    applyAudioEffects()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && _playerState.value.playbackSpeed != 1.0f) {
                        try { mp.playbackParams = mp.playbackParams.setSpeed(_playerState.value.playbackSpeed) } catch (_: Exception) {}
                    }
                    try {
                        mp.start()
                    } catch (e: Exception) {
                        Log.e("AudioPlayer", "Error starting MediaPlayer in onPrepared: ${e.message}")
                        handlePlaybackFailure(track, sessionId)
                        return@setOnPreparedListener
                    }
                    val actualDur = try { mp.duration.toLong() } catch (_: Exception) { 0L }
                    _playerState.value = _playerState.value.copy(
                        isPlaying = true,
                        playbackSessionId = sessionId,
                        durationMs = if (actualDur > 0) actualDur else track.durationMs
                    )
                    notificationManager.updateNotification(track, isPlaying = true, currentPositionMs = startPositionMs, isPreparing = false)
                }
                setOnCompletionListener { mp ->
                    if (sessionId != currentPlaybackSessionId || mediaPlayer != mp) return@setOnCompletionListener
                    onTrackCompleted(sessionId)
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e("AudioPlayer", "MediaPlayer Error what=$what extra=$extra for track '${track.title}'")
                    if (sessionId == currentPlaybackSessionId && mediaPlayer == mp) {
                        handlePlaybackFailure(track, sessionId)
                    }
                    true
                }
                prepareAsync()
            }
            mediaPlayer = newPlayer
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error initializing MediaPlayer for '${track.title}': ${e.message}")
            handlePlaybackFailure(track, sessionId)
        }
    }

    fun updateCurrentTrackLyrics(trackId: Long, lyrics: String) {
        val state = _playerState.value
        if (state.currentTrack?.id != trackId && state.queue.none { it.id == trackId }) return
        val updatedQueue = state.queue.map { track ->
            if (track.id == trackId) track.copy(lyrics = lyrics) else track
        }
        val updatedCurrent = state.currentTrack?.let { current ->
            if (current.id == trackId) current.copy(lyrics = lyrics) else current
        }
        _playerState.value = state.copy(
            queue = updatedQueue,
            currentTrack = updatedCurrent
        )
    }

    fun togglePlayPause() {
        wasPlayingBeforeFocusLoss = false
        focusLossSessionId = -1L
        val state = _playerState.value
        if (state.isPlaying) {
            mediaPlayer?.let { mp ->
                try { mp.pause() } catch (e: Exception) { Log.e("AudioPlayer", "Toggle pause error: ${e.message}") }
            }
            _playerState.value = _playerState.value.copy(isPlaying = false)
            notificationManager.updateNotification(_playerState.value.currentTrack, false, _playerState.value.currentPositionMs)
            savePlayerState()
        } else {
            requestAudioFocus()
            val currentTrack = _playerState.value.currentTrack ?: return
            val mp = mediaPlayer
            var resumedSuccessfully = false
            if (mp != null) {
                try {
                    mp.start()
                    resumedSuccessfully = true
                    _playerState.value = _playerState.value.copy(isPlaying = true)
                    notificationManager.updateNotification(currentTrack, true, _playerState.value.currentPositionMs)
                    savePlayerState()
                } catch (e: Exception) {
                    Log.w("AudioPlayer", "Failed to resume existing MediaPlayer, re-preparing: ${e.message}")
                    resumedSuccessfully = false
                }
            }
            if (!resumedSuccessfully) {
                acquireTransitionWakeLock()
                val sessionId = ++currentPlaybackSessionId
                handledSessionId = 0L
                notificationManager.updateNotification(currentTrack, isPlaying = false, currentPositionMs = state.currentPositionMs, isPreparing = true)
                startMediaPlayer(currentTrack, startPositionMs = state.currentPositionMs, sessionId = sessionId)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val state = _playerState.value
        val track = state.currentTrack ?: return
        val maxDur = state.durationMs.coerceAtLeast(1000L)
        val clampedPos = positionMs.coerceIn(0L, maxDur)
        _playerState.value = state.copy(currentPositionMs = clampedPos)
        mediaPlayer?.let { mp ->
            try {
                mp.seekTo(clampedPos.toInt())
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Seek error: ${e.message}")
            }
        }
        notificationManager.updateNotification(track, state.isPlaying, clampedPos, isPreparing = false)
        savePlayerState()
    }

    fun nextTrack(isExplicitSkip: Boolean = true) {
        val now = System.currentTimeMillis()
        if (isExplicitSkip && now - lastTrackTransitionTime < 300L) return

        val state = _playerState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        if (state.shuffleMode == ShuffleMode.OFF && state.repeatMode == RepeatMode.OFF && !isExplicitSkip && state.currentQueueIndex >= queue.lastIndex) {
            _playerState.value = state.copy(isPlaying = false, currentPositionMs = 0L)
            mediaPlayer?.let { mp -> try { mp.pause() } catch (_: Exception) {} }
            notificationManager.updateNotification(state.currentTrack, false, 0L, isPreparing = false)
            return
        }

        var targetTrack: Track? = null

        state.forcedNextTrackId?.let { forcedId ->
            val forcedTrack = queue.firstOrNull { it.id == forcedId }
            if (forcedTrack != null && forcedTrack.id != state.currentTrack?.id) {
                _playerState.value = state.copy(forcedNextTrackId = null)
                targetTrack = forcedTrack
            } else {
                _playerState.value = state.copy(forcedNextTrackId = null)
            }
        }

        if (targetTrack == null) {
            targetTrack = when (state.shuffleMode) {
                ShuffleMode.OFF -> {
                    val nextIdx = (state.currentQueueIndex + 1) % queue.size
                    queue[nextIdx]
                }
                ShuffleMode.STANDARD_RANDOM -> {
                    var remaining = state.standardShuffleRemainingQueue.filter { it.id != state.currentTrack?.id }
                    if (remaining.isEmpty()) {
                        remaining = queue.filter { it.id != state.currentTrack?.id }.shuffled()
                    }
                    remaining.firstOrNull() ?: queue.filter { it.id != state.currentTrack?.id }.randomOrNull() ?: queue.first()
                }
                ShuffleMode.FAIR_NON_REPEAT -> {
                    var remaining = state.fairShuffleRemainingQueue.filter { it.id != state.currentTrack?.id }
                    if (remaining.isEmpty()) {
                        // Pool exhausted, reset
                        playedTrackIdsInCycle.clear()
                        state.currentTrack?.id?.let { playedTrackIdsInCycle.add(it) }
                        remaining = queue.filter { it.id != state.currentTrack?.id }.shuffled()
                    }
                    remaining.firstOrNull() ?: queue.filter { it.id != state.currentTrack?.id }.randomOrNull() ?: queue.first()
                }
                ShuffleMode.SMART_SHUFFLE -> {
                    val upcoming = state.smartShuffleUpcoming.filter { it.id != state.currentTrack?.id }
                    upcoming.firstOrNull() ?: run {
                        val nextIdx = smartShuffleEngine.selectNextTrackIndex(queue, state.currentQueueIndex)
                        val selected = queue.getOrNull(nextIdx)
                        if (selected?.id == state.currentTrack?.id && queue.size > 1) {
                            queue.firstOrNull { it.id != state.currentTrack?.id } ?: selected
                        } else {
                            selected
                        }
                    } ?: queue.first()
                }
            }
        }

        targetTrack?.let { playTrack(it, isExplicitSkip = isExplicitSkip) }
    }

    fun previousTrack() {
        val now = System.currentTimeMillis()
        if (now - lastTrackTransitionTime < 300L) return
        lastTrackTransitionTime = now

        val state = _playerState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        if (state.currentPositionMs > 3000L) {
            seekTo(0L)
            return
        }

        val prevIdx = if (state.currentQueueIndex > 0) state.currentQueueIndex - 1 else queue.lastIndex
        playTrack(queue[prevIdx])
    }

    fun setShuffleMode(mode: ShuffleMode) {
        _playerState.value = _playerState.value.copy(shuffleMode = mode)
        val currentId = _playerState.value.currentTrack?.id
        val currentQueue = _playerState.value.queue
        when (mode) {
            ShuffleMode.FAIR_NON_REPEAT -> resetFairShufflePool(currentQueue, currentId)
            ShuffleMode.STANDARD_RANDOM -> resetStandardShufflePool(currentQueue, currentId)
            ShuffleMode.SMART_SHUFFLE -> recalculateSmartShuffleUpcoming()
            ShuffleMode.OFF -> {}
        }
        savePlayerState()
    }

    fun toggleShuffleMode() {
        val current = _playerState.value.shuffleMode
        val nextMode = when (current) {
            ShuffleMode.OFF -> ShuffleMode.FAIR_NON_REPEAT
            ShuffleMode.FAIR_NON_REPEAT -> ShuffleMode.SMART_SHUFFLE
            ShuffleMode.SMART_SHUFFLE -> ShuffleMode.STANDARD_RANDOM
            ShuffleMode.STANDARD_RANDOM -> ShuffleMode.OFF
        }
        setShuffleMode(nextMode)
    }

    fun toggleRepeatMode() {
        val current = _playerState.value.repeatMode
        val nextMode = when (current) {
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
        _playerState.value = _playerState.value.copy(repeatMode = nextMode)
        savePlayerState()
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 3.0f)
        _playerState.value = _playerState.value.copy(playbackSpeed = safeSpeed)
        prefs.edit().putFloat("playback_speed", safeSpeed).apply()
        mediaPlayer?.let { mp ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    mp.playbackParams = mp.playbackParams.setSpeed(safeSpeed)
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Speed set error: ${e.message}")
            }
        }
    }

    fun updateEqualizer(settings: EqualizerSettings) {
        persistEqualizerSettings(settings)
        _playerState.value = _playerState.value.copy(equalizerSettings = settings)
        applyAudioEffects(settings)
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val queue = _playerState.value.queue.toMutableList()
        if (fromIndex in queue.indices && toIndex in queue.indices) {
            val item = queue.removeAt(fromIndex)
            queue.add(toIndex, item)

            var newCurrentIndex = _playerState.value.currentQueueIndex
            if (fromIndex == newCurrentIndex) {
                newCurrentIndex = toIndex
            } else if (fromIndex < newCurrentIndex && toIndex >= newCurrentIndex) {
                newCurrentIndex--
            } else if (fromIndex > newCurrentIndex && toIndex <= newCurrentIndex) {
                newCurrentIndex++
            }

            _playerState.value = _playerState.value.copy(
                queue = queue,
                currentQueueIndex = newCurrentIndex
            )
            savePlayerState()
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _playerState.value
        val upNext = state.upNextQueue
        val trackToRemove = upNext.getOrNull(index) ?: return

        val newForcedId = if (state.forcedNextTrackId == trackToRemove.id) null else state.forcedNextTrackId
        val newFair = state.fairShuffleRemainingQueue.filter { it.id != trackToRemove.id }
        val newStandard = state.standardShuffleRemainingQueue.filter { it.id != trackToRemove.id }
        val newSmart = state.smartShuffleUpcoming.filter { it.id != trackToRemove.id }

        val newQueue = if (state.shuffleMode == ShuffleMode.OFF) {
            val q = state.queue.toMutableList()
            val actualIdx = state.currentQueueIndex + 1 + index
            if (actualIdx in q.indices && q[actualIdx].id == trackToRemove.id) {
                q.removeAt(actualIdx)
            } else {
                q.removeAll { it.id == trackToRemove.id && it.id != state.currentTrack?.id }
            }
            q
        } else {
            state.queue
        }

        _playerState.value = state.copy(
            queue = newQueue,
            currentQueueIndex = newQueue.indexOfFirst { it.id == state.currentTrack?.id }.coerceAtLeast(0),
            fairShuffleRemainingQueue = newFair,
            standardShuffleRemainingQueue = newStandard,
            smartShuffleUpcoming = newSmart,
            forcedNextTrackId = newForcedId,
            fairShufflePlayedCount = newFair.size.coerceAtMost(newQueue.size),
            fairShuffleTotalCount = newQueue.size
        )
        savePlayerState()
    }

    fun moveUpNextItem(index: Int, direction: Int) {
        val state = _playerState.value
        if (direction !in listOf(-1, 1)) return
        val upNext = state.upNextQueue.toMutableList()
        val targetIndex = index + direction
        if (index !in upNext.indices || targetIndex !in upNext.indices) return

        val item = upNext.removeAt(index)
        upNext.add(targetIndex, item)

        when (state.shuffleMode) {
            ShuffleMode.FAIR_NON_REPEAT -> {
                _playerState.value = state.copy(fairShuffleRemainingQueue = upNext)
            }
            ShuffleMode.STANDARD_RANDOM -> {
                _playerState.value = state.copy(standardShuffleRemainingQueue = upNext)
            }
            ShuffleMode.SMART_SHUFFLE -> {
                _playerState.value = state.copy(smartShuffleUpcoming = upNext)
            }
            ShuffleMode.OFF -> {
                val currentQueueIndex = state.currentQueueIndex
                if (currentQueueIndex >= 0) {
                    val remainingQueue = state.queue.drop(currentQueueIndex + 1).toMutableList()
                    if (index in remainingQueue.indices && targetIndex - 0 in remainingQueue.indices) {
                        val reorderedItem = remainingQueue.removeAt(index)
                        val targetInRemaining = (targetIndex).coerceIn(0, remainingQueue.size)
                        remainingQueue.add(targetInRemaining, reorderedItem)
                        val newQueue = state.queue.take(currentQueueIndex + 1) + remainingQueue
                        _playerState.value = state.copy(queue = newQueue)
                    }
                }
            }
        }
        savePlayerState()
    }

    fun clearQueue() {
        val sessionId = ++currentPlaybackSessionId
        handledSessionId = 0L
        wasPlayingBeforeFocusLoss = false
        focusLossSessionId = -1L
        smartShuffleJob?.cancel()
        mediaPlayer?.let { mp ->
            try { mp.setOnPreparedListener(null) } catch (_: Exception) {}
            try { mp.setOnCompletionListener(null) } catch (_: Exception) {}
            try { mp.setOnErrorListener(null) } catch (_: Exception) {}
            try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
        releaseAudioEffects()

        _playerState.value = _playerState.value.copy(
            queue = emptyList(),
            currentQueueIndex = -1,
            currentTrack = null,
            isPlaying = false,
            playbackSessionId = sessionId,
            fairShuffleRemainingQueue = emptyList(),
            standardShuffleRemainingQueue = emptyList(),
            smartShuffleUpcoming = emptyList(),
            forcedNextTrackId = null
        )
        notificationManager.updateNotification(null, false, 0L)
        savePlayerState()
    }

    private fun resetStandardShufflePool(tracks: List<Track>, currentTrackId: Long?) {
        val remaining = tracks.filter { it.id != currentTrackId }.shuffled()
        _playerState.value = _playerState.value.copy(
            standardShuffleRemainingQueue = remaining
        )
        savePlayerState()
    }

    // Fair Shuffle pool algorithm
    private fun resetFairShufflePool(tracks: List<Track>, currentTrackId: Long?) {
        playedTrackIdsInCycle.clear()
        if (currentTrackId != null) {
            playedTrackIdsInCycle.add(currentTrackId)
        }
        val remaining = tracks.filter { it.id != currentTrackId }.shuffled()
        _playerState.value = _playerState.value.copy(
            fairShuffleRemainingQueue = remaining,
            fairShufflePlayedCount = playedTrackIdsInCycle.size.coerceAtMost(tracks.size),
            fairShuffleTotalCount = tracks.size
        )
        savePlayerState()
    }

    private fun updateFairShuffleStats() {
        val total = _playerState.value.queue.size
        _playerState.value = _playerState.value.copy(
            fairShufflePlayedCount = playedTrackIdsInCycle.size.coerceAtMost(total),
            fairShuffleTotalCount = total
        )
    }

    private fun onTrackCompleted(sessionId: Long) {
        tryHandleSessionTransition(sessionId) {
            val state = _playerState.value
            // If Sleep Timer is active and set to End of Song, stop playback and cancel timer
            if (state.sleepTimerStatus.isActive && state.sleepTimerStatus.isEndOfSong) {
                try {
                    mediaPlayer?.pause()
                } catch (_: Exception) {}
                _playerState.value = state.copy(isPlaying = false)
                notificationManager.updateNotification(state.currentTrack, false, state.currentPositionMs)
                cancelSleepTimer()
                return@tryHandleSessionTransition
            }

            when (state.repeatMode) {
                RepeatMode.REPEAT_ONE -> {
                    seekTo(0L)
                    state.currentTrack?.let { playTrack(it, isExplicitSkip = false) }
                }
                else -> nextTrack(isExplicitSkip = false)
            }
        }
    }

    private fun startPositionAndVisualizerLoop() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            var lastTickTime = System.currentTimeMillis()
            val amplitudes = FloatArray(12) { 0.2f }
            while (isActive) {
                delay(200)
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTickTime).coerceAtLeast(0L)
                lastTickTime = now

                val state = _playerState.value
                if (state.isPlaying) {
                    val mp = mediaPlayer
                    if (mp != null) {
                        val isMpPlaying = try { mp.isPlaying } catch (_: Exception) { false }
                        if (isMpPlaying) {
                            accumulatedMillis += elapsed
                            if (accumulatedMillis >= 1000L) {
                                val secondsToAdd = accumulatedMillis / 1000L
                                totalStreamSeconds += secondsToAdd
                                accumulatedMillis %= 1000L
                            }

                            tickCounter++
                            if (tickCounter % 10 == 0) { // Every 2 seconds save state
                                savePlayerState()
                            }

                            val currentPos = try { mp.currentPosition.toLong() } catch (_: Exception) { state.currentPositionMs }
                            val dur = state.durationMs.coerceAtLeast(1000L)
                            val nextPos = currentPos.coerceAtMost(dur)

                            for (i in 0 until 12) {
                                amplitudes[i] = 0.15f + Random.nextFloat() * 0.85f
                            }

                            _playerState.value = _playerState.value.copy(
                                currentPositionMs = nextPos,
                                visualizerAmplitudes = amplitudes.toList(),
                                totalStreamSeconds = totalStreamSeconds
                            )
                        }
                    }
                } else {
                    lastTickTime = System.currentTimeMillis()
                }
            }
        }
    }

    fun addToQueue(track: Track) {
        val state = _playerState.value
        if (state.queue.any { it.id == track.id }) return
        val currentQueue = state.queue.toMutableList()
        currentQueue.add(track)
        if (state.currentTrack == null || state.queue.isEmpty()) {
            _playerState.value = state.copy(
                queue = currentQueue,
                currentQueueIndex = 0
            )
            playTrack(track)
        } else {
            val updatedRemaining = if (state.shuffleMode == ShuffleMode.FAIR_NON_REPEAT) {
                (state.fairShuffleRemainingQueue + track).distinctBy { it.id }.shuffled()
            } else state.fairShuffleRemainingQueue
            _playerState.value = state.copy(
                queue = currentQueue,
                fairShuffleRemainingQueue = updatedRemaining,
                fairShuffleTotalCount = currentQueue.size
            )
            savePlayerState()
        }
    }

    fun playNextInQueue(track: Track) {
        val state = _playerState.value
        if (state.queue.any { it.id == track.id }) return
        val currentQueue = state.queue.toMutableList()
        if (state.currentTrack == null || currentQueue.isEmpty()) {
            _playerState.value = state.copy(
                queue = listOf(track),
                currentQueueIndex = 0
            )
            playTrack(track)
        } else {
            val insertIndex = (state.currentQueueIndex + 1).coerceAtMost(currentQueue.size)
            currentQueue.add(insertIndex, track)
            val updatedRemaining = if (state.shuffleMode == ShuffleMode.FAIR_NON_REPEAT) {
                (state.fairShuffleRemainingQueue + track).distinctBy { it.id }
            } else state.fairShuffleRemainingQueue
            val updatedSmart = if (state.shuffleMode == ShuffleMode.SMART_SHUFFLE) {
                listOf(track) + state.smartShuffleUpcoming.filter { it.id != track.id }
            } else state.smartShuffleUpcoming
            _playerState.value = state.copy(
                queue = currentQueue,
                fairShuffleRemainingQueue = updatedRemaining,
                smartShuffleUpcoming = updatedSmart,
                fairShuffleTotalCount = currentQueue.size,
                forcedNextTrackId = track.id
            )
            savePlayerState()
        }
    }

    fun removeTrackCompletely(trackId: Long) {
        val state = _playerState.value
        val currentTrackId = state.currentTrack?.id
        val isCurrentTrack = currentTrackId == trackId

        val updatedQueue = state.queue.filter { it.id != trackId }
        val newIndex = if (updatedQueue.isNotEmpty()) {
            state.currentQueueIndex.coerceAtMost(updatedQueue.lastIndex)
        } else 0

        if (updatedQueue.isEmpty()) {
            if (_playerState.value.isPlaying) togglePlayPause()
            _playerState.value = state.copy(
                currentTrack = null,
                queue = emptyList(),
                isPlaying = false,
                currentPositionMs = 0L,
                forcedNextTrackId = null
            )
        } else if (isCurrentTrack) {
            val nextTrack = updatedQueue[newIndex]
            _playerState.value = state.copy(
                queue = updatedQueue,
                currentQueueIndex = newIndex,
                forcedNextTrackId = if (state.forcedNextTrackId == trackId) null else state.forcedNextTrackId
            )
            playTrack(nextTrack)
        } else {
            _playerState.value = state.copy(
                queue = updatedQueue,
                currentQueueIndex = newIndex,
                forcedNextTrackId = if (state.forcedNextTrackId == trackId) null else state.forcedNextTrackId
            )
        }
        savePlayerState()
    }

    fun release() {
        releaseTransitionWakeLock()
        currentPlaybackSessionId++
        handledSessionId = 0L
        wasPlayingBeforeFocusLoss = false
        focusLossSessionId = -1L
        positionUpdateJob?.cancel()
        smartShuffleJob?.cancel()
        sleepTimerJob?.cancel()
        mediaPlayer?.let { mp ->
            try { mp.setOnPreparedListener(null) } catch (_: Exception) {}
            try { mp.setOnCompletionListener(null) } catch (_: Exception) {}
            try { mp.setOnErrorListener(null) } catch (_: Exception) {}
            try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
        releaseAudioEffects()
        notificationManager.release()
    }

    companion object {
        @Volatile private var INSTANCE: AudioPlayerController? = null

        fun getInstance(context: Context): AudioPlayerController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioPlayerController(context.applicationContext).also { INSTANCE = it }
            }
    }
}
