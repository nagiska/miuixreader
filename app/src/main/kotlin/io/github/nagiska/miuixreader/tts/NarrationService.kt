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
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import io.github.nagiska.miuixreader.R
import io.github.nagiska.miuixreader.ReaderActivity
import io.github.nagiska.miuixreader.ReaderApplication
import io.github.nagiska.miuixreader.data.NarrationEngine
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NarrationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSession
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var wakeLock: PowerManager.WakeLock? = null
    private var playbackJob: Job? = null
    private var synthesizer: NarrationSynthesizer? = null
    private var player: MediaPlayer? = null
    private var playerPrepared = false
    private var activeSession: NarrationSession? = null
    private var activeGeneration = 0L
    private val paused = MutableStateFlow(false)
    private var resumeOnFocusGain = false

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
        cleanupNarrationCache()
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
        playbackJob?.cancel()
        releaseSessionResources()
        activeSession = session
        paused.value = false
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
        playbackJob = serviceScope.launch {
            try {
                playSession(session, generation)
                if (generation == activeGeneration) finishSession()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == activeGeneration) {
                    failSession(
                        session,
                        error.message ?: getString(R.string.narration_synthesis_failed),
                    )
                }
            } finally {
                if (generation == activeGeneration) releaseSessionResources()
            }
        }
    }

    private suspend fun playSession(session: NarrationSession, generation: Long) = coroutineScope {
        require(session.segments.isNotEmpty()) { getString(R.string.narration_no_text) }
        val engine = when (session.engine) {
            NarrationEngine.SYSTEM -> SystemTtsSynthesizer(this@NarrationService, session.rate)
            NarrationEngine.GSV_LOCAL -> GsvLocalClient(session.gsvPort, session.rate).also { client ->
                val status = client.checkStatus()
                require(status.reachable) {
                    status.errorMessage ?: getString(R.string.narration_gsv_unreachable)
                }
                require(status.ready) {
                    status.errorMessage ?: getString(R.string.narration_gsv_not_ready)
                }
                updateState(currentState.value.copy(backendName = status.backendName))
            }
        }
        synthesizer = engine
        val sessionDirectory = File(cacheDir, "narration/${UUID.randomUUID()}").apply { mkdirs() }
        var index = 0
        var prepared = async {
            synthesizeSegment(engine, session.segments[index], sessionDirectory, index)
        }

        while (index < session.segments.size && generation == activeGeneration) {
            val segment = session.segments[index]
            updateState(
                currentState.value.copy(
                    phase = if (paused.value) {
                        NarrationPhase.PAUSED
                    } else if (index == 0) {
                        NarrationPhase.PREPARING
                    } else {
                        NarrationPhase.BUFFERING
                    },
                    segmentIndex = index,
                    anchor = segment.anchor,
                    errorMessage = null,
                ),
            )
            val audio = prepared.await()
            paused.first { !it }
            if (generation != activeGeneration) break
            val next = if (index + 1 < session.segments.size) {
                async {
                    synthesizeSegment(
                        engine,
                        session.segments[index + 1],
                        sessionDirectory,
                        index + 1,
                    )
                }
            } else {
                null
            }
            updateState(
                currentState.value.copy(
                    phase = NarrationPhase.PLAYING,
                    segmentIndex = index,
                    anchor = segment.anchor,
                    backendName = audio.backendName,
                ),
            )
            persistProgress(session.bookId, segment.anchor)
            try {
                playAudio(audio.file)
            } finally {
                audio.file.delete()
            }
            index++
            if (next != null) prepared = next
        }
        sessionDirectory.deleteRecursively()
    }

    private suspend fun synthesizeSegment(
        engine: NarrationSynthesizer,
        segment: NarrationSegment,
        directory: File,
        index: Int,
    ): SynthesizedNarrationAudio = engine.synthesize(
        segment.text,
        File(directory, "segment-$index.wav"),
    )

    private suspend fun playAudio(file: File) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val mediaPlayer = MediaPlayer()
            try {
                mediaPlayer.setAudioAttributes(playbackAudioAttributes)
                mediaPlayer.setWakeMode(this@NarrationService, PowerManager.PARTIAL_WAKE_LOCK)
                mediaPlayer.setDataSource(file.absolutePath)
                mediaPlayer.setOnPreparedListener { preparedPlayer ->
                    playerPrepared = true
                    if (!paused.value) preparedPlayer.start()
                }
                mediaPlayer.setOnCompletionListener {
                    releasePlayer()
                    if (continuation.isActive) continuation.resume(Unit)
                }
                mediaPlayer.setOnErrorListener { _, what, extra ->
                    releasePlayer()
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Audio playback failed ($what/$extra)"),
                        )
                    }
                    true
                }
                player = mediaPlayer
                continuation.invokeOnCancellation { releasePlayer() }
                mediaPlayer.prepareAsync()
            } catch (error: Exception) {
                if (player === mediaPlayer) {
                    player = null
                    playerPrepared = false
                }
                runCatching { mediaPlayer.release() }
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    private fun pausePlayback() {
        val state = currentState.value
        if (!state.isActive || state.phase == NarrationPhase.PAUSED) return
        paused.value = true
        if (playerPrepared) runCatching { player?.pause() }
        updateState(state.copy(phase = NarrationPhase.PAUSED))
    }

    private fun resumePlayback() {
        val state = currentState.value
        if (state.phase != NarrationPhase.PAUSED) return
        paused.value = false
        if (playerPrepared) runCatching { player?.start() }
        updateState(
            state.copy(phase = if (playerPrepared) NarrationPhase.PLAYING else NarrationPhase.BUFFERING),
        )
    }

    private fun stopSession() {
        activeGeneration++
        playbackJob?.cancel()
        playbackJob = null
        releaseSessionResources()
        activeSession = null
        updateState(NarrationPlaybackState())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishSession() {
        activeGeneration++
        activeSession = null
        updateState(NarrationPlaybackState())
        releaseSessionResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun failSession(session: NarrationSession, message: String) {
        activeGeneration++
        playbackJob?.cancel()
        playbackJob = null
        updateState(
            NarrationPlaybackState(
                bookId = session.bookId,
                title = session.title,
                phase = NarrationPhase.ERROR,
                segmentCount = session.segments.size,
                errorMessage = message,
            ),
        )
        releaseSessionResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseSessionResources() {
        synthesizer?.cancel()
        synthesizer?.close()
        synthesizer = null
        releasePlayer()
        runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
        mediaSession.isActive = false
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        cleanupNarrationCache()
    }

    private fun releasePlayer() {
        playerPrepared = false
        player?.apply {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            runCatching { stop() }
            release()
        }
        player = null
    }

    private fun persistProgress(bookId: Long, anchor: NarrationAnchor) {
        serviceScope.launch(Dispatchers.IO) {
            val progression = when (anchor) {
                is NarrationAnchor.Publication -> anchor.locatorJson
                is NarrationAnchor.Txt -> "txt2:${anchor.itemIndex}:${anchor.offsetFraction.coerceIn(0f, 1f)}"
            }
            (application as ReaderApplication).books.saveProgression(bookId, progression)
        }
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:narration")
            .apply {
                setReferenceCounted(false)
                acquire(MAX_WAKE_LOCK_MILLIS)
            }
    }

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                stopSession()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = currentState.value.phase == NarrationPhase.PLAYING
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                runCatching { player?.setVolume(0.2f, 0.2f) }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                runCatching { player?.setVolume(1f, 1f) }
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resumePlayback()
                }
            }
        }
    }

    private fun updateState(state: NarrationPlaybackState) {
        _state.value = state
        updateMediaSessionState(state)
        if (activeSession != null) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(state.title, state.phase))
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

    private fun cleanupNarrationCache() {
        File(cacheDir, "narration").deleteRecursively()
    }

    override fun onDestroy() {
        activeGeneration++
        playbackJob?.cancel()
        synchronized(pendingLock) { pendingSession = null }
        activeSession = null
        releaseSessionResources()
        mediaSession.release()
        _state.value = NarrationPlaybackState()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "io.github.nagiska.miuixreader.tts.START"
        private const val ACTION_PAUSE = "io.github.nagiska.miuixreader.tts.PAUSE"
        private const val ACTION_RESUME = "io.github.nagiska.miuixreader.tts.RESUME"
        private const val ACTION_STOP = "io.github.nagiska.miuixreader.tts.STOP"
        private const val NOTIFICATION_CHANNEL = "narration-playback"
        private const val NOTIFICATION_ID = 2107
        private const val MEDIA_SESSION_TAG = "MiuixReaderNarration"
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
