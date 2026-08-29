package io.github.nagiska.miuixreader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.Closeable
import kotlinx.coroutines.CompletableDeferred

/**
 * Thin adapter around the selected Android TTS engine.
 *
 * It deliberately leaves language, voice and speech-rate selection untouched. The selected engine
 * owns its voice and per-voice speed settings; overriding them here can undo the user's engine
 * configuration. The engine therefore receives an empty request bundle.
 */
internal class SystemTtsSynthesizer(
    context: Context,
    private val listener: Listener,
) : Closeable {
    interface Listener {
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onStop(utteranceId: String, interrupted: Boolean)
        fun onError(utteranceId: String, errorCode: Int)
    }

    private val ready = CompletableDeferred<Unit>()
    private val engine = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready.complete(Unit)
        } else {
            ready.completeExceptionally(
                IllegalStateException("System text-to-speech is unavailable"),
            )
        }
    }

    @Volatile
    private var closed = false

    init {
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    listener.onStart(utteranceId)
                }

                override fun onDone(utteranceId: String) {
                    listener.onDone(utteranceId)
                }

                override fun onStop(utteranceId: String, interrupted: Boolean) {
                    listener.onStop(utteranceId, interrupted)
                }

                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String) {
                    listener.onError(utteranceId, TextToSpeech.ERROR)
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    listener.onError(utteranceId, errorCode)
                }
            },
        )
    }

    suspend fun awaitReady() {
        ready.await()
    }

    fun enqueue(utteranceId: String, text: String, flush: Boolean): Boolean {
        if (closed || text.isBlank()) return false
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        return engine.speak(text, queueMode, Bundle(), utteranceId) == TextToSpeech.SUCCESS
    }

    fun stop() {
        if (!closed) engine.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        engine.setOnUtteranceProgressListener(null)
        engine.stop()
        engine.shutdown()
    }
}
