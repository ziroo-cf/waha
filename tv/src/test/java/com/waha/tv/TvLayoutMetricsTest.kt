package com.waha.tv

import androidx.compose.ui.unit.dp
import com.waha.tv.ui.screens.WahaTvDimens
import com.waha.tv.ui.screens.tvFocusScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The look of the TV layout is mostly numbers, and the numbers are the design:
 * wide 16:9 cards, a highlight that never moves anything, and a keyboard small
 * enough to share a 720p screen with the results. Each of those is a promise the
 * build has to keep, so each is asserted here rather than left to a comment.
 */
class TvLayoutMetricsTest {

    @Test
    fun `cards are wide 16 by 9 landscape`() {
        assertEquals(16f / 9f, WahaTvDimens.CardAspectRatio, 0.0001f)

        // Wide enough to read from a sofa, narrow enough that a rail shows about
        // three and a half of them on a 720p screen (about 960dp across).
        assertTrue(WahaTvDimens.CardWidth >= 200.dp)
        assertTrue(WahaTvDimens.CardWidth <= 320.dp)
    }

    @Test
    fun `focus never enlarges anything`() {
        assertEquals(1f, WahaTvDimens.FocusScale, 0f)
        assertEquals(1f, tvFocusScale(), 0f)
    }

    @Test
    fun `focus is a thick accent border`() {
        assertTrue(
            "the focus border must read across a room",
            WahaTvDimens.FocusBorderWidth >= 3.dp
        )
    }

    @Test
    fun `the rail is an icon strip at rest and opens wide enough for its names`() {
        val resting = WahaTvDimens.railWidth(expanded = false)
        val focused = WahaTvDimens.railWidth(expanded = true)

        // Folded, the rail is icons only: a strip, not a panel of the screen.
        assertTrue("the resting rail should be an icon wide", resting <= 80.dp)
        // Open, it has to hold an icon and a destination name side by side.
        assertTrue("the open rail must have room for names", focused >= 200.dp)
        assertTrue("opening the rail must widen it", resting < focused)
    }

    @Test
    fun `the folded rail is exactly one icon wide`() {
        // Gutter + icon + gutter is the whole resting width, which is what centres the icon
        // in the strip instead of leaving it off to one side.
        assertEquals(
            WahaTvDimens.RailWidthResting,
            WahaTvDimens.RailEdgePadding * 2 + WahaTvDimens.RailIconSize
        )
        assertTrue(WahaTvDimens.RailItemHeight < WahaTvDimens.RailHeaderHeight)
        assertTrue(WahaTvDimens.RailHeaderHeight >= 60.dp)
    }

    @Test
    fun `the duration badge sits inside the artwork's corner`() {
        assertTrue(WahaTvDimens.BadgeInset >= 6.dp)
        assertTrue(WahaTvDimens.BadgeCornerRadius <= WahaTvDimens.CornerRadius)
        assertTrue(WahaTvDimens.BadgePaddingHorizontal > WahaTvDimens.BadgePaddingVertical)
    }

    @Test
    fun `a card's detail block uses one gutter and one line gap`() {
        assertTrue(WahaTvDimens.CardMetaPaddingHorizontal >= 8.dp)
        assertTrue(WahaTvDimens.CardMetaLineSpacing < WahaTvDimens.CardMetaPaddingTop)
    }

    @Test
    fun `titles get two lines so Arabic phrasing is not cut mid-phrase`() {
        assertEquals(2, WahaTvDimens.CardTitleMaxLines)
    }

    @Test
    fun `the keyboard stays short enough to leave the results visible`() {
        assertEquals(36.dp, WahaTvDimens.KeyboardKeyHeight)
        assertEquals(4.dp, WahaTvDimens.KeyboardKeyGap)
    }
}
