package com.waha.domain

/**
 * A chosen video quality, expressed as the maximum height the extractor may
 * resolve — or null for "auto", i.e. whatever is best.
 *
 * The encoding is here (rather than inside the preferences store) so the
 * persisted format is pure and testable: `"auto"` or a plain height like
 * `"720"`, which also stays readable if the file is opened by hand.
 */
object PlayerQuality {

    /** Stored value for the automatic choice. */
    const val AUTO_KEY = "auto"

    fun encode(height: Int?): String = height?.toString() ?: AUTO_KEY

    /** Reads a stored value back; anything unparsable falls back to auto. */
    fun decode(raw: String?): Int? =
        raw?.trim()?.takeIf { it.isNotEmpty() && it != AUTO_KEY }?.toIntOrNull()

    /** Arabic label for a quality choice. */
    fun label(height: Int?, autoLabel: String = "تلقائي"): String =
        height?.let { "${it}p" } ?: autoLabel
}
