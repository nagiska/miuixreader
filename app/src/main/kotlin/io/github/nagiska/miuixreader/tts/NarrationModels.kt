package io.github.nagiska.miuixreader.tts

import io.github.nagiska.miuixreader.data.NarrationEngine

sealed interface NarrationAnchor {
    data class Publication(val locatorJson: String) : NarrationAnchor

    data class Txt(
        val itemIndex: Int,
        val offsetFraction: Float,
        val totalFraction: Float,
    ) : NarrationAnchor
}

data class NarrationSegment(
    val text: String,
    val anchor: NarrationAnchor,
)

data class NarrationSession(
    val bookId: Long,
    val title: String,
    val engine: NarrationEngine,
    val rate: Float,
    val gsvPort: Int,
    val segments: List<NarrationSegment>,
)

enum class NarrationPhase {
    IDLE,
    PREPARING,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR,
}

data class NarrationPlaybackState(
    val bookId: Long? = null,
    val title: String = "",
    val phase: NarrationPhase = NarrationPhase.IDLE,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0,
    val anchor: NarrationAnchor? = null,
    val backendName: String = "",
    val errorMessage: String? = null,
) {
    val isActive: Boolean
        get() = bookId != null && phase !in setOf(NarrationPhase.IDLE, NarrationPhase.ERROR)
}

data class GsvEndpointStatus(
    val reachable: Boolean,
    val ready: Boolean,
    val backendName: String = "",
    val errorMessage: String? = null,
)
