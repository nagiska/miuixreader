package io.github.nagiska.miuixreader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SystemTtsSynthesizer(
    context: Context,
    private val rate: Float,
) : NarrationSynthesizer {
    private val ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<String, PendingSynthesis>()
    private val engine = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready.complete(Unit)
        } else {
            ready.completeExceptionally(IllegalStateException("System text-to-speech is unavailable"))
        }
    }

    init {
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    pending.remove(utteranceId)?.let { synthesis ->
                        if (synthesis.file.isFile && synthesis.file.length() > 0L) {
                            synthesis.result.complete(
                                SynthesizedNarrationAudio(synthesis.file, "Android system TTS"),
                            )
                        } else {
                            synthesis.result.completeExceptionally(
                                IllegalStateException("System text-to-speech returned no audio"),
                            )
                        }
                    }
                }

                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String) {
                    fail(utteranceId, TextToSpeech.ERROR)
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    fail(utteranceId, errorCode)
                }

                override fun onStop(utteranceId: String, interrupted: Boolean) {
                    fail(utteranceId, TextToSpeech.ERROR)
                }

                private fun fail(utteranceId: String, errorCode: Int) {
                    pending.remove(utteranceId)?.let { synthesis ->
                        synthesis.file.delete()
                        synthesis.result.completeExceptionally(
                            IllegalStateException("System text-to-speech failed ($errorCode)"),
                        )
                    }
                }
            },
        )
    }

    override suspend fun synthesize(text: String, output: File): SynthesizedNarrationAudio {
        require(text.isNotBlank()) { "Narration text must not be blank" }
        ready.await()
        output.parentFile?.mkdirs()
        output.delete()
        val utteranceId = UUID.randomUUID().toString()
        val result = CompletableDeferred<SynthesizedNarrationAudio>()
        pending[utteranceId] = PendingSynthesis(output, result)

        val status = withContext(Dispatchers.Main.immediate) {
            val language = when {
                containsCjk(text) -> Locale.SIMPLIFIED_CHINESE
                containsLatin(text) -> Locale.ENGLISH
                else -> Locale.getDefault()
            }
            val languageStatus = engine.setLanguage(language)
            if (
                languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                languageStatus == TextToSpeech.LANG_NOT_SUPPORTED ||
                engine.setSpeechRate(rate) == TextToSpeech.ERROR
            ) {
                TextToSpeech.ERROR
            } else {
                engine.synthesizeToFile(text, Bundle(), output, utteranceId)
            }
        }
        if (status == TextToSpeech.ERROR) {
            pending.remove(utteranceId)
            output.delete()
            error("System text-to-speech rejected the request")
        }
        return try {
            result.await()
        } finally {
            pending.remove(utteranceId)
        }
    }

    override fun cancel() {
        engine.stop()
        val error = IllegalStateException("System text-to-speech was stopped")
        pending.values.forEach { synthesis ->
            synthesis.file.delete()
            synthesis.result.completeExceptionally(error)
        }
        pending.clear()
    }

    override fun close() {
        cancel()
        engine.shutdown()
    }

    private data class PendingSynthesis(
        val file: File,
        val result: CompletableDeferred<SynthesizedNarrationAudio>,
    )

    private fun containsCjk(text: String): Boolean = text.any { character ->
        character in '\u3400'..'\u4DBF' || character in '\u4E00'..'\u9FFF'
    }

    private fun containsLatin(text: String): Boolean = text.any { character ->
        character in 'A'..'Z' || character in 'a'..'z'
    }
}
