package io.github.nagiska.miuixreader.tts

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
    val queueDepth: Int = 0,
    val lastStartDelayMillis: Long? = null,
    val lastGapMillis: Long? = null,
    val maxQueueDepth: Int = 0,
    val errorMessage: String? = null,
) {
    val isActive: Boolean
        get() = bookId != null && phase !in setOf(NarrationPhase.IDLE, NarrationPhase.ERROR)
}
