package io.github.nagiska.miuixreader.data

import org.json.JSONObject

/** A restorable TXT position, with whole-book progress when the saved format supports it. */
internal data class TxtReadingProgress(
    val itemIndex: Int = 0,
    val scrollOffset: Int = 0,
    val offsetFraction: Float = 0f,
    val totalFraction: Float? = null,
)

/** Reads legacy pixel offsets, relative offsets, and relative offsets with whole-book progress. */
internal fun decodeTxtReadingProgress(value: String?): TxtReadingProgress? {
    val parts = value?.trim()?.split(':') ?: return null
    val expectedSize = when (parts.firstOrNull()) {
        "txt", "txt2" -> 3
        "txt3" -> 4
        else -> return null
    }
    if (parts.size != expectedSize) return null
    val itemIndex = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: return null
    return when (parts[0]) {
        "txt" -> TxtReadingProgress(
            itemIndex = itemIndex,
            scrollOffset = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: return null,
        )
        "txt2" -> TxtReadingProgress(
            itemIndex = itemIndex,
            offsetFraction = parts[2].readingFractionOrNull() ?: return null,
        )
        else -> TxtReadingProgress(
            itemIndex = itemIndex,
            offsetFraction = parts[2].readingFractionOrNull() ?: return null,
            totalFraction = parts[3].readingFractionOrNull() ?: return null,
        )
    }
}

/** Writes finite, bounded txt3 values; invalid floating-point inputs safely become zero. */
internal fun encodeTxtReadingProgress(
    itemIndex: Int,
    offsetFraction: Float,
    totalFraction: Float,
): String {
    val offset = offsetFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val total = totalFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return "txt3:${itemIndex.coerceAtLeast(0)}:$offset:$total"
}

/**
 * Whole-book progress only. Legacy TXT positions and chapter-local publication progression
 * cannot establish a percentage for the entire book and are deliberately left unknown.
 */
internal fun BookEntity.overallReadingProgress(): Float? {
    if (lastOpenedAt == null) return 0f
    val saved = progression?.takeIf { it.isNotBlank() } ?: return null
    if (bookFormat == BookFormat.TXT) return decodeTxtReadingProgress(saved)?.totalFraction
    return runCatching {
        val locations = JSONObject(saved).optJSONObject("locations") ?: return@runCatching null
        val total = (locations.opt("totalProgression") as? Number)?.toDouble()
            ?: return@runCatching null
        total.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat()
    }.getOrNull()
}

private fun String.readingFractionOrNull(): Float? =
    toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
