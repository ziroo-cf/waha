package com.waha.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvKeyboardTest {

    @Test
    fun `typing appends characters`() {
        var query = ""
        listOf("و", "ا", "ح", "ة").forEach { query = TvKeyboard.append(query, it) }

        assertEquals("واحة", query)
    }

    @Test
    fun `append ignores empty input and stops at the cap`() {
        assertEquals("ا", TvKeyboard.append("ا", ""))
        assertEquals("abc", TvKeyboard.append("abc", "د", maxLength = 3))
    }

    @Test
    fun `backspace removes one whole character`() {
        assertEquals("واح", TvKeyboard.backspace("واحة"))
        assertEquals("", TvKeyboard.backspace(""))
    }

    @Test
    fun `backspace keeps a supplementary character intact`() {
        // A surrogate pair (emoji) is one code point and must not split.
        val emoji = String(Character.toChars(0x1F600))
        assertEquals("ا", TvKeyboard.backspace("ا$emoji"))
    }

    @Test
    fun `matching is case-insensitive and ignores surrounding spaces`() {
        assertTrue(TvKeyboard.matches("Blue Safari EP 2", "blue"))
        assertTrue(TvKeyboard.matches("حلقة الأصدقاء", "الأصدقاء"))
        assertTrue(TvKeyboard.matches("واحة", " واحة "))
        assertFalse(TvKeyboard.matches("واحة", "طبخ"))
    }

    @Test
    fun `a blank query matches nothing`() {
        assertFalse(TvKeyboard.matches("واحة", "   "))
        assertFalse(TvKeyboard.matches("واحة", ""))
    }

    @Test
    fun `the page switches and the four actions fill exactly one keyboard row`() {
        // The keyboard's D-pad navigation depends on uniform columns: the top row
        // holds the three page switches plus فراغ/حذف/مسح/ابحث, so every letter has
        // a key directly above and below it.
        val topRowKeys = TvKeyboard.Page.entries.size + 4

        assertEquals(TvKeyboard.KEYS_PER_ROW, topRowKeys)
    }

    @Test
    fun `every page exposes enough keys to search with and fits the row grid`() {
        TvKeyboard.Page.entries.forEach { page ->
            val keys = TvKeyboard.keyRows(page)
            assertTrue("page $page is empty", keys.isNotEmpty())
            assertTrue("page $page has duplicates", keys.distinct().size == keys.size)
        }

        assertEquals(28, TvKeyboard.arabicLetters.size)
        assertEquals(26, TvKeyboard.latinLetters.size)
        assertEquals(10, TvKeyboard.digits.size)
        assertTrue(TvKeyboard.keyRows(TvKeyboard.Page.Arabic).size <= TvKeyboard.KEYS_PER_ROW * 4)
    }
}
