package io.github.nagiska.miuixreader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.nagiska.miuixreader.R
import io.github.nagiska.miuixreader.ReaderActivity
import io.github.nagiska.miuixreader.ReaderApplication
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Background playback coordinator for the Android TTS engine selected by the user. */
class NarrationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val progressWriteMutex = Mutex()
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSession
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var wakeLock: PowerManager.WakeLock? = null
    private var startupJob: Job? = null
    private var tts: SystemTtsSynthesizer? = null
    private var activeSession: NarrationSession? = null
    private var activeGeneration = 0L
    private var nextIndex = 0
    private var currentSegmentIndex = -1
    private var resumeIndex = 0
    private var paused = false
    private var resumeOnFocusGain = false
    private var lastDoneAt = 0L
    private var queueWindow = INITIAL_QUEUE_WINDOW
    private val pending = linkedMapOf<String, PendingSegment>()

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        createNotificationChannel()
        mediaSession = MediaSession(this, MEDIA_SESSION_TAG).apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = resumePlayback()
                    override fun onPause() = pausePlayback()
                    override fun onStop() = stopSession()
                },
            )
        }
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAudioAttributes)
            .setOnAudioFocusChangeListener { change -> onAudioFocusChanged(change) }
            .setWillPauseWhenDucked(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val session = consumePendingSession()
                if (session == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification(session.title, NarrationPhase.PREPARING))
                startSession(session)
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    private fun startSession(session: NarrationSession) {
        activeGeneration++
        val generation = activeGeneration
        startupJob?.cancel()
        releaseSessionResources()
        activeSession = session
        nextIndex = 0
        currentSegmentIndex = -1
        resumeIndex = 0
        paused = false
        resumeOnFocusGain = false
        lastDoneAt = 0L
        queueWindow = INITIAL_QUEUE_WINDOW
        mediaSession.isActive = true
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, session.title)
                .build(),
        )
        acquireWakeLock()
        if (audioManager.requestAudioFocus(audioFocusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            failSession(session, getString(R.string.narration_audio_focus_failed))
            return
        }
        updateState(
            NarrationPlaybackState(
                bookId = session.bookId,
                title = session.title,
                phase = NarrationPhase.PREPARING,
                segmentCount = session.segments.size,
            ),
        )
        startupJob = serviceScope.launch {
            try {
                val engine = SystemTtsSynthesizer(this@NarrationService, ttsListener)
                tts = engine
                engine.awaitReady()
                if (generation != activeGeneration) return@launch
                enqueueWindow(generation, flushFirst = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == activeGeneration) {
                    failSession(
                        session,
                        error.message ?: getString(R.string.narration_synthesis_failed),
                    )
                }
            }
        }
    }

    private val ttsListener = object : SystemTtsSynthesizer.Listener {
        override fun onStart(utteranceId: String) {
            serviceScope.launch { handleTtsStart(utteranceId) }
        }

        override fun onDone(utteranceId: String) {
            serviceScope.launch { handleTtsDone(utteranceId) }
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            serviceScope.launch { handleTtsStop(utteranceId, interrupted) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            serviceScope.launch { handleTtsError(utteranceId, errorCode) }
        }
    }

    private fun enqueueWindow(generation: Long, flushFirst: Boolean) {
        val session = activeSession ?: return
        if (generation != activeGeneration || paused) return
        val engine = tts ?: error("TTS engine is not initialized")
        while (nextIndex < session.segments.size && pending.size < queueWindow) {
            val index = nextIndex
            val id = "g$generation-s$index-${UUID.randomUUID()}"
            val shouldFlush = flushFirst && pending.isEmpty()
            pending[id] = PendingSegment(
                index = index,
                submittedAt = SystemClock.elapsedRealtime(),
            )
            val accepted = engine.enqueue(
                utteranceId = id,
                text = session.segments[index].text,
                flush = shouldFlush,
            )
            if (!accepted) pending.remove(id)
            check(accepted) { "System TTS rejected the narration request" }
            nextIndex++
        }
        updateQueueState()
    }

    private fun handleTtsStart(utteranceId: String) {
        val item = pending[utteranceId] ?: return
        val session = activeSession ?: return
        val now = SystemClock.elapsedRealtime()
        currentSegmentIndex = item.index
        val startDelay = (now - item.submittedAt).coerceAtLeast(0L)
        val gap = if (lastDoneAt == 0L) null else (now - lastDoneAt).coerceAtLeast(0L)
        if (gap != null) {
            queueWindow = when {
                gap > GAP_GROW_THRESHOLD_MILLIS -> (queueWindow + 1).coerceAtMost(MAX_QUEUE_WINDOW)
                gap < GAP_SHRINK_THRESHOLD_MILLIS -> (queueWindow - 1).coerceAtLeast(INITIAL_QUEUE_WINDOW)
                else -> queueWindow
            }
        }
        updateState(
            currentState.value.copy(
                bookId = session.bookId,
                title = session.title,
                phase = NarrationPhase.PLAYING,
                segmentIndex = item.index,
                segmentCount = session.segments.size,
                anchor = session.segments[item.index].anchor,
                queueDepth = (pending.size - 1).coerceAtLeast(0),
                lastStartDelayMillis = startDelay,
                lastGapMillis = gap,
                maxQueueDepth = maxOf(currentState.value.maxQueueDepth, pending.size),
                errorMessage = null,
            ),
        )
        persistProgress(session.bookId, session.segments[item.index].anchor)
    }

    private fun handleTtsDone(utteranceId: String) {
        val item = pending.remove(utteranceId) ?: return
        lastDoneAt = SystemClock.elapsedRealtime()
        val session = activeSession ?: return
        if (paused) return
        if (pending.isEmpty() && nextIndex >= session.segments.size) {
            finishSession()
            return
        }
        runCatching { enqueueWindow(activeGeneration, flushFirst = false) }
            .onFailure { error ->
                failSession(
                    session,
                    error.message ?: getString(R.string.narration_synthesis_failed),
                )
            }
        updateQueueState()
    }

    private fun handleTtsStop(utteranceId: String, interrupted: Boolean) {
        if (pending.remove(utteranceId) == null) return
        if (paused) return
        val session = activeSession ?: return
        failSession(
            session,
            getString(R.string.narration_synthesis_failed),
        )
    }

    private fun handleTtsError(utteranceId: String, errorCode: Int) {
        if (pending.remove(utteranceId) == null) return
        val session = activeSession ?: return
        failSession(
            session,
            getString(R.string.narration_tts_error, errorCode),
        )
    }

    private fun pausePlayback(fromAudioFocusLoss: Boolean = false) {
        val session = activeSession ?: return
        val state = currentState.value
        if (!state.isActive || paused) return
        if (!fromAudioFocusLoss) resumeOnFocusGain = false
        resumeIndex = when {
            currentSegmentIndex >= 0 -> currentSegmentIndex
            pending.isNotEmpty() -> pending.values.minOf { it.index }
            else -> nextIndex
        }.coerceIn(0, session.segments.lastIndex.coerceAtLeast(0))
        paused = true
        tts?.stop()
        pending.clear()
        updateState(state.copy(phase = NarrationPhase.PAUSED, queueDepth = 0))
    }

    private fun resumePlayback() {
        val session = activeSession ?: return
        if (currentState.value.phase != NarrationPhase.PAUSED) return
        paused = false
        pending.clear()
        nextIndex = resumeIndex.coerceIn(0, session.segments.size)
        currentSegmentIndex = -1
        lastDoneAt = 0L
        queueWindow = INITIAL_QUEUE_WINDOW
        if (tts == null) {
            startSession(session)
            return
        }
        updateState(currentState.value.copy(phase = NarrationPhase.PREPARING, queueDepth = 0))
        runCatching { enqueueWindow(activeGeneration, flushFirst = true) }
            .onFailure { error ->
                failSession(
                    session,
                    error.message ?: getString(R.string.narration_synthesis_failed),
                )
            }
    }

    private fun stopSession() {
        activeGeneration++
        resumeOnFocusGain = false
        startupJob?.cancel()
        startupJob = null
        releaseSessionResources()
        activeSession = null
        updateState(NarrationPlaybackState())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishSession() {
        activeGeneration++
        resumeOnFocusGain = false
        startupJob?.cancel()
        startupJob = null
        releaseSessionResources()
        activeSession = null
        updateState(NarrationPlaybackState())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun failSession(session: NarrationSession, message: String) {
        if (activeSession?.bookId != session.bookId) return
        activeGeneration++
        resumeOnFocusGain = false
        startupJob?.cancel()
        startupJob = null
        pending.clear()
        updateState(
            NarrationPlaybackState(
                bookId = session.bookId,
                title = session.title,
                phase = NarrationPhase.ERROR,
                segmentIndex = currentSegmentIndex.coerceAtLeast(0),
                segmentCount = session.segments.size,
                queueDepth = 0,
                errorMessage = message,
            ),
        )
        releaseSessionResources()
        activeSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateQueueState() {
        val state = currentState.value
        if (!state.isActive) return
        updateState(
            state.copy(
                queueDepth = (pending.size - 1).coerceAtLeast(0),
                maxQueueDepth = maxOf(state.maxQueueDepth, pending.size),
            ),
        )
    }

    private fun updateState(state: NarrationPlaybackState) {
        _state.value = state
        updateMediaSessionState(state)
        if (activeSession != null) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(state.title, state.phase))
        }
    }

    private fun persistProgress(bookId: Long, anchor: NarrationAnchor) {
        serviceScope.launch(Dispatchers.IO) {
            progressWriteMutex.withLock {
                val progression = when (anchor) {
                    is NarrationAnchor.Publication -> anchor.locatorJson
                    is NarrationAnchor.Txt ->
                        "txt2:${anchor.itemIndex}:${anchor.offsetFraction.coerceIn(0f, 1f)}"
                }
                (application as ReaderApplication).books.saveProgression(bookId, progression)
            }
        }
    }

    private fun updateMediaSessionState(state: NarrationPlaybackState) {
        val playbackState = when (state.phase) {
            NarrationPhase.PLAYING -> PlaybackState.STATE_PLAYING
            NarrationPhase.PAUSED -> PlaybackState.STATE_PAUSED
            NarrationPhase.PREPARING, NarrationPhase.BUFFERING -> PlaybackState.STATE_BUFFERING
            NarrationPhase.ERROR -> PlaybackState.STATE_ERROR
            NarrationPhase.IDLE -> PlaybackState.STATE_STOPPED
        }
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP,
                )
                .setState(playbackState, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stopSession()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = currentState.value.phase in setOf(
                    NarrationPhase.PREPARING,
                    NarrationPhase.BUFFERING,
                    NarrationPhase.PLAYING,
                )
                pausePlayback(fromAudioFocusLoss = true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resumePlayback()
                }
            }
        }
    }

    private fun releaseSessionResources() {
        tts?.close()
        tts = null
        pending.clear()
        runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
        mediaSession.isActive = false
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:narration")
            .apply {
                setReferenceCounted(false)
                acquire(MAX_WAKE_LOCK_MILLIS)
            }
    }

    private fun buildNotification(title: String, phase: NarrationPhase): Notification {
        val playing = phase in setOf(
            NarrationPhase.PREPARING,
            NarrationPhase.BUFFERING,
            NarrationPhase.PLAYING,
        )
        val toggleAction = if (playing) ACTION_PAUSE else ACTION_RESUME
        val toggleLabel = getString(if (playing) R.string.narration_pause else R.string.narration_play)
        val toggleIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val contentIntent = activeSession?.bookId?.let { bookId ->
            PendingIntent.getActivity(
                this,
                bookId.toInt(),
                ReaderActivity.intent(this, bookId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val builder = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title.ifBlank { getString(R.string.app_name) })
            .setContentText(notificationStatus(phase))
            .setOngoing(phase !in setOf(NarrationPhase.IDLE, NarrationPhase.ERROR))
            .setOnlyAlertOnce(true)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
            .addAction(toggleIcon, toggleLabel, servicePendingIntent(toggleAction, 1))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.narration_stop),
                servicePendingIntent(ACTION_STOP, 2),
            )
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, NarrationService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notificationStatus(phase: NarrationPhase): String = getString(
        when (phase) {
            NarrationPhase.PLAYING -> R.string.narration_playing
            NarrationPhase.PAUSED -> R.string.narration_paused
            NarrationPhase.PREPARING, NarrationPhase.BUFFERING -> R.string.narration_preparing
            NarrationPhase.ERROR -> R.string.narration_error
            NarrationPhase.IDLE -> R.string.narration_stopped
        },
    )

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                getString(R.string.narration_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onDestroy() {
        activeGeneration++
        startupJob?.cancel()
        synchronized(pendingLock) { pendingSession = null }
        releaseSessionResources()
        mediaSession.release()
        _state.value = NarrationPlaybackState()
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class PendingSegment(
        val index: Int,
        val submittedAt: Long,
    )

    companion object {
        private const val ACTION_START = "io.github.nagiska.miuixreader.tts.START"
        private const val ACTION_PAUSE = "io.github.nagiska.miuixreader.tts.PAUSE"
        private const val ACTION_RESUME = "io.github.nagiska.miuixreader.tts.RESUME"
        private const val ACTION_STOP = "io.github.nagiska.miuixreader.tts.STOP"
        private const val NOTIFICATION_CHANNEL = "narration-playback"
        private const val NOTIFICATION_ID = 2107
        private const val MEDIA_SESSION_TAG = "MiuixReaderNarration"
        private const val INITIAL_QUEUE_WINDOW = 4
        private const val MAX_QUEUE_WINDOW = 8
        private const val GAP_GROW_THRESHOLD_MILLIS = 250L
        private const val GAP_SHRINK_THRESHOLD_MILLIS = 80L
        private const val MAX_WAKE_LOCK_MILLIS = 6L * 60L * 60L * 1_000L

        private val pendingLock = Any()
        private var pendingSession: NarrationSession? = null
        private val _state = MutableStateFlow(NarrationPlaybackState())
        val currentState: StateFlow<NarrationPlaybackState> = _state.asStateFlow()

        fun start(context: Context, session: NarrationSession) {
            synchronized(pendingLock) { pendingSession = session }
            ContextCompat.startForegroundService(
                context,
                Intent(context, NarrationService::class.java).setAction(ACTION_START),
            )
        }

        fun pause(context: Context) = sendAction(context, ACTION_PAUSE)
        fun resume(context: Context) = sendAction(context, ACTION_RESUME)
        fun stop(context: Context) = sendAction(context, ACTION_STOP)

        private fun sendAction(context: Context, action: String) {
            context.startService(Intent(context, NarrationService::class.java).setAction(action))
        }

        private fun consumePendingSession(): NarrationSession? = synchronized(pendingLock) {
            pendingSession.also { pendingSession = null }
        }
    }
}

private val playbackAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()
