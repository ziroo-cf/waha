package com.waha.tv

/** Longest query the TV search field accepts. */
const val TV_QUERY_MAX_LENGTH = 60

/**
 * The on-screen keyboard's layout and text editing.
 *
 * A TV remote has no hardware keyboard and the leanback IME cannot be relied on,
 * so search types through a D-pad grid. Keeping the layout and the editing rules
 * here (instead of inside the composable) means they are unit tested.
 */
object TvKeyboard {

    /** Seven keys per row: comfortable D-pad travel in both directions. */
    const val KEYS_PER_ROW = 7

    /** The 28 Arabic letters, in the Arabic keyboard's own order. */
    val arabicLetters: List<String> = listOf(
        "ا", "ب", "ت", "ث", "ج", "ح", "خ",
        "د", "ذ", "ر", "ز", "س", "ش", "ص",
        "ض", "ط", "ظ", "ع", "غ", "ف", "ق",
        "ك", "ل", "م", "ن", "ه", "و", "ي"
    )

    /** Latin letters for Latin titles. */
    val latinLetters: List<String> = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ").map { it.toString() }

    val digits: List<String> = (0..9).map { it.toString() }

    /** Which letter set the grid is showing. */
    enum class Page(val label: String) {
        Arabic("عربي"),
        Latin("EN"),
        Digits("١٢٣")
    }

    /** The letter keys of one page, in layout order. */
    fun keyRows(page: Page): List<String> = when (page) {
        Page.Arabic -> arabicLetters
        Page.Latin -> latinLetters
        Page.Digits -> digits
    }

    /** Appends a character, trimmed and capped at [maxLength]. */
    fun append(current: String, character: String, maxLength: Int = TV_QUERY_MAX_LENGTH): String {
        if (character.isEmpty() || current.length >= maxLength) return current
        return (current + character).take(maxLength)
    }

    /** Deletes the last character (a whole code point, so Arabic letters stay intact). */
    fun backspace(current: String): String {
        if (current.isEmpty()) return current
        val lastCodePointLength = Character.charCount(current.codePointBefore(current.length))
        return current.dropLast(lastCodePointLength)
    }

    /** Case-insensitive title match; a blank query matches nothing. */
    fun matches(title: String, query: String): Boolean {
        val trimmed = query.trim()
        return trimmed.isNotEmpty() && title.contains(trimmed, ignoreCase = true)
    }
}
