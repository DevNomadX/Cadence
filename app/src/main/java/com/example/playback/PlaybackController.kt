package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.RepeatMode
import com.example.data.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
data class PlaybackState(
    val currentTrackId: String? = null,
    val queueTrackIds: List<String> = emptyList(),
    val currentQueueIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isConnected: Boolean = false,
    val errorMessage: String? = null
)

@Immutable
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Bridges the UI/ViewModel layer with CadencePlaybackService via Media3 MediaController.
 * Splits low-frequency PlaybackState from high-frequency PlaybackProgress to eliminate recomposition storms.
 */
class PlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onTrackPlayStarted: (String) -> Unit,
    private val onQueueChanged: (List<String>) -> Unit,
    private val onPositionCheckpoint: (String, Long) -> Unit,
    private val onPlaybackError: (String) -> Unit,
    private val onTrackNaturallyEnded: () -> Unit
) {
    private val mapper = MediaItemMapper(context)
    private val queueMutex = Mutex()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var pendingAction: (suspend () -> Unit)? = null
    private var lastRecordedPlayTrackId: String? = null
    private var lastCheckpointTimeMs: Long = 0L

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgress())
    val progressState: StateFlow<PlaybackProgress> = _progressState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromPlayer()
            if (isPlaying) {
                val activeId = controller?.currentMediaItem?.mediaId
                if (activeId != null && activeId != lastRecordedPlayTrackId) {
                    lastRecordedPlayTrackId = activeId
                    onTrackPlayStarted(activeId)
                }
                startProgressPolling()
            } else {
                stopProgressPolling()
                saveCheckpointNow()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromPlayer()
            if (playbackState == Player.STATE_ENDED) {
                stopProgressPolling()
                onTrackNaturallyEnded()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromPlayer()
            val id = mediaItem?.mediaId
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                onTrackNaturallyEnded()
            }
            val mc = controller
            // Only record play count / reset checkpoint when playback is actually active,
            // preventing cold-start session restoration from clobbering saved position or playCount.
            if (id != null && mc != null && (mc.isPlaying || mc.playWhenReady)) {
                if (id != lastRecordedPlayTrackId || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    lastRecordedPlayTrackId = id
                    onTrackPlayStarted(id)
                }
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncFromPlayer()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncFromPlayer()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncFromPlayer()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            syncFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            val player = controller
            val msg = "Skipped unplayable audio (${error.errorCodeName})"
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isPreparing = false,
                errorMessage = msg
            )
            onPlaybackError(msg)
            if (player != null && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, CadencePlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val mc = future.get()
                    controller = mc
                    mc.addListener(playerListener)
                    syncFromPlayer()
                    if (mc.isPlaying) {
                        startProgressPolling()
                    }
                    val pending = pendingAction
                    pendingAction = null
                    if (pending != null) {
                        scope.launch { pending() }
                    }
                } catch (e: Exception) {
                    onPlaybackError(e.localizedMessage ?: "Playback service unavailable")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun hasActiveQueue(): Boolean {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            (controller?.mediaItemCount ?: 0) > 0
        } else {
            _playbackState.value.queueTrackIds.isNotEmpty()
        }
    }

    suspend fun setQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ): Unit = queueMutex.withLock {
        if (tracks.isEmpty()) return@withLock
        val mc = withContext(Dispatchers.Main.immediate) {
            val current = controller
            if (current == null) {
                pendingAction = { setQueue(tracks, startIndex, startPositionMs, autoPlay) }
                connect()
            }
            current
        } ?: return@withLock

        _playbackState.value = _playbackState.value.copy(isPreparing = true, errorMessage = null)

        // Map all items concurrently off the main thread so multi-core devices synthesize/prepare quickly
        val mapped = withContext(Dispatchers.Default) {
            coroutineScope {
                tracks.map { track ->
                    async { mapper.toMediaItem(track) }
                }.awaitAll().filterNotNull()
            }
        }
        if (mapped.isEmpty()) {
            _playbackState.value = _playbackState.value.copy(isPreparing = false)
            onPlaybackError("No playable tracks found")
            return@withLock
        }

        val targetId = tracks.getOrNull(startIndex)?.id
        val safeIndex = targetId?.let { id -> mapped.indexOfFirst { it.mediaId == id }.takeIf { it >= 0 } }
            ?: startIndex.coerceIn(0, mapped.lastIndex)

        withContext(Dispatchers.Main) {
            if (!autoPlay) {
                // Prevent restored paused track from triggering spurious playCount increment
                lastRecordedPlayTrackId = null
            }
            mc.setMediaItems(mapped, safeIndex, startPositionMs.coerceAtLeast(0L))
            mc.prepare()
            mc.playWhenReady = autoPlay
            val ids = mapped.map { it.mediaId }
            onQueueChanged(ids)
            syncFromPlayer()
        }
    }

    fun togglePlayPause() {
        val mc = controller ?: return
        if (mc.isPlaying) {
            mc.pause()
        } else {
            if (mc.playbackState == Player.STATE_ENDED) {
                mc.seekTo(0, 0L)
            }
            if (mc.playbackState == Player.STATE_IDLE) {
                mc.prepare()
            }
            mc.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun playAtQueueIndex(index: Int) {
        val mc = controller ?: return
        if (index in 0 until mc.mediaItemCount) {
            mc.seekTo(index, 0L)
            mc.prepare()
            mc.play()
        }
    }

    fun next() {
        val mc = controller ?: return
        if (mc.hasNextMediaItem()) {
            mc.seekToNextMediaItem()
            mc.prepare()
            mc.play()
        } else if (mc.mediaItemCount > 0) {
            mc.seekTo(0, 0L)
            mc.prepare()
            mc.play()
        }
    }

    fun previous() {
        val mc = controller ?: return
        if (mc.currentPosition > 3_000L) {
            mc.seekTo(0L)
        } else if (mc.hasPreviousMediaItem()) {
            mc.seekToPreviousMediaItem()
            mc.prepare()
            mc.play()
        } else {
            mc.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        val mc = controller ?: return
        val duration = mc.duration.takeIf { it > 0L } ?: _progressState.value.durationMs
        val target = positionMs.coerceIn(0L, duration.coerceAtLeast(1L))
        mc.seekTo(target)
        _progressState.value = _progressState.value.copy(positionMs = target)
        saveCheckpointNow()
    }

    fun setRepeatMode(mode: RepeatMode) {
        val mc = controller ?: return
        mc.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        syncFromPlayer()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        val mc = controller ?: return
        mc.shuffleModeEnabled = enabled
        syncFromPlayer()
    }

    fun setPlaybackSpeed(speed: Float) {
        val mc = controller ?: return
        mc.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 2.0f))
        syncFromPlayer()
    }

    suspend fun playNext(track: Track): Boolean = queueMutex.withLock {
        val mc = withContext(Dispatchers.Main.immediate) { controller } ?: return@withLock false
        val item = withContext(Dispatchers.Default) { mapper.toMediaItem(track) } ?: return@withLock false
        withContext(Dispatchers.Main) {
            val insertIdx = if (mc.mediaItemCount == 0) {
                0
            } else {
                (mc.currentMediaItemIndex + 1).coerceAtMost(mc.mediaItemCount)
            }
            mc.addMediaItem(insertIdx, item)
            if (mc.mediaItemCount == 1) {
                mc.prepare()
                mc.play()
            }
            notifyQueueSnapshot(mc)
        }
        true
    }

    suspend fun addToQueue(track: Track): Boolean = queueMutex.withLock {
        val mc = withContext(Dispatchers.Main.immediate) { controller } ?: return@withLock false
        val item = withContext(Dispatchers.Default) { mapper.toMediaItem(track) } ?: return@withLock false
        withContext(Dispatchers.Main) {
            mc.addMediaItem(item)
            if (mc.mediaItemCount == 1) {
                mc.prepare()
                mc.play()
            }
            notifyQueueSnapshot(mc)
        }
        true
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val mc = controller ?: return
        if (fromIndex !in 0 until mc.mediaItemCount || toIndex !in 0 until mc.mediaItemCount) return
        if (fromIndex == toIndex) return
        mc.moveMediaItem(fromIndex, toIndex)
        notifyQueueSnapshot(mc)
    }

    fun removeQueueItem(index: Int) {
        val mc = controller ?: return
        if (index !in 0 until mc.mediaItemCount) return
        mc.removeMediaItem(index)
        notifyQueueSnapshot(mc)
    }

    fun removeTrackById(trackId: String) {
        val mc = controller ?: return
        var removedAny = false
        for (i in mc.mediaItemCount - 1 downTo 0) {
            if (mc.getMediaItemAt(i).mediaId == trackId) {
                mc.removeMediaItem(i)
                removedAny = true
            }
        }
        if (removedAny) {
            notifyQueueSnapshot(mc)
        }
    }

    fun clearUpcomingQueue() {
        val mc = controller ?: return
        if (mc.mediaItemCount <= 1) return
        val currentIdx = mc.currentMediaItemIndex.coerceIn(0, mc.mediaItemCount - 1)
        if (currentIdx + 1 < mc.mediaItemCount) {
            mc.removeMediaItems(currentIdx + 1, mc.mediaItemCount)
        }
        if (currentIdx > 0) {
            mc.removeMediaItems(0, currentIdx)
        }
        notifyQueueSnapshot(mc)
    }

    private fun notifyQueueSnapshot(mc: MediaController) {
        val ids = List(mc.mediaItemCount) { idx -> mc.getMediaItemAt(idx).mediaId }
        onQueueChanged(ids)
        syncFromPlayer()
    }

    private fun syncFromPlayer() {
        val mc = controller ?: return
        val count = mc.mediaItemCount
        val ids = List(count) { idx -> mc.getMediaItemAt(idx).mediaId }
        val currentIdx = if (count > 0) mc.currentMediaItemIndex.coerceIn(0, count - 1) else 0
        val currentId = mc.currentMediaItem?.mediaId ?: ids.getOrNull(currentIdx)
        val repeat = when (mc.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        _playbackState.value = PlaybackState(
            currentTrackId = currentId,
            queueTrackIds = ids,
            currentQueueIndex = currentIdx,
            isPlaying = mc.isPlaying,
            isPreparing = mc.playbackState == Player.STATE_BUFFERING,
            playbackSpeed = mc.playbackParameters.speed,
            shuffleEnabled = mc.shuffleModeEnabled,
            repeatMode = repeat,
            isConnected = true,
            errorMessage = null
        )
        // Return 0L when duration is still TIME_UNSET so UI falls back to the active track's duration
        val dur = mc.duration.takeIf { it > 0L } ?: 0L
        _progressState.value = PlaybackProgress(
            positionMs = mc.currentPosition.coerceAtLeast(0L),
            durationMs = dur
        )
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val mc = controller
                if (mc != null && mc.isPlaying) {
                    val pos = mc.currentPosition.coerceAtLeast(0L)
                    val dur = mc.duration.takeIf { it > 0L } ?: 0L
                    _progressState.value = PlaybackProgress(positionMs = pos, durationMs = dur)

                    val now = System.currentTimeMillis()
                    if (now - lastCheckpointTimeMs >= 5_000L) {
                        lastCheckpointTimeMs = now
                        mc.currentMediaItem?.mediaId?.let { id ->
                            onPositionCheckpoint(id, pos)
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun saveCheckpointNow() {
        val mc = controller ?: return
        val id = mc.currentMediaItem?.mediaId ?: return
        onPositionCheckpoint(id, mc.currentPosition.coerceAtLeast(0L))
    }

    fun releaseController() {
        saveCheckpointNow()
        stopProgressPolling()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}
