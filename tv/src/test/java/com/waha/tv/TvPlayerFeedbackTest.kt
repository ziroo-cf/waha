package com.waha.tv

import com.waha.tv.ui.screens.TvPlayerFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glyph the middle of the player shows for each press.
 *
 * These are the player's only on-screen words, so the mapping is asserted here:
 * a play/pause press must land on the play/pause glyphs, and a seek must say
 * "10 seconds" with an arrow rather than a caption.
 */
class TvPlayerFeedbackTest {

    @Test
    fun `play and pause map to the playback glyphs`() {
        assertEquals("Filled.PlayArrow", TvPlayerFeedback.Play.icon.name)
        assertEquals("Filled.Pause", TvPlayerFeedback.Pause.icon.name)
    }

    @Test
    fun `seeking maps to the ten second arrows`() {
        assertEquals("Filled.Replay10", TvPlayerFeedback.Rewind.icon.name)
        assertEquals("Filled.Forward10", TvPlayerFeedback.Forward.icon.name)
    }

    @Test
    fun `every glyph carries a spoken description for the seek arrows`() {
        assertEquals("تشغيل", TvPlayerFeedback.Play.description)
        assertEquals("إيقاف مؤقت", TvPlayerFeedback.Pause.description)
        assertTrue(TvPlayerFeedback.Rewind.description.contains("١٠ ثوانٍ"))
        assertTrue(TvPlayerFeedback.Forward.description.contains("١٠ ثوانٍ"))
    }
}
