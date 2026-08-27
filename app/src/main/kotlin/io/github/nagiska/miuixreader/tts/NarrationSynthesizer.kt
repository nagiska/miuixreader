package io.github.nagiska.miuixreader.tts

import java.io.Closeable
import java.io.File

internal data class SynthesizedNarrationAudio(
    val file: File,
    val backendName: String,
)

internal interface NarrationSynthesizer : Closeable {
    suspend fun synthesize(text: String, output: File): SynthesizedNarrationAudio
    fun cancel()
}
