package com.waha.tv

import com.waha.tv.ui.screens.TvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The TV navigation is the rail down the screen's right edge: the search action at
 * the top, then the three sections. That order is part of the design (البحث
 * first because it is an action, الإعدادات last) and the D-pad walks it, so it is
 * asserted here instead of being left to the enum's declaration order.
 *
 * المحفوظات and السجل are no longer destinations: both are rows inside المكتبة.
 */
class TvTopNavTest {

    @Test
    fun `the rail lists the search action first and then the three sections`() {
        assertEquals(
            listOf("البحث", "الرئيسية", "المكتبة", "الإعدادات"),
            TvDestination.railOrder.map { it.label }
        )
    }

    @Test
    fun `the sections keep the order the design asks for`() {
        assertEquals(
            listOf("الرئيسية", "المكتبة", "الإعدادات"),
            TvDestination.sections.map { it.label }
        )
    }

    @Test
    fun `saved and history are not separate destinations any more`() {
        val labels = TvDestination.entries.map { it.label }

        assertTrue("المحفوظات should live inside المكتبة", "المحفوظات" !in labels)
        assertTrue("السجل should live inside المكتبة", "السجل" !in labels)
        assertEquals(listOf("الرئيسية", "البحث", "المكتبة", "الإعدادات"), labels)
    }

    @Test
    fun `search is the rail's own action, not one of the sections`() {
        assertTrue(TvDestination.Search !in TvDestination.sections)
        assertEquals(TvDestination.railOrder.first(), TvDestination.Search)
    }

    @Test
    fun `the rail shows every destination exactly once`() {
        assertEquals(TvDestination.entries.toSet(), TvDestination.railOrder.toSet())
        assertEquals(TvDestination.railOrder.size, TvDestination.railOrder.distinct().size)
    }

    @Test
    fun `the app opens on the home destination`() {
        assertEquals(TvDestination.Home, TvDestination.entries.first())
    }

    @Test
    fun `every destination is labelled and has an icon`() {
        TvDestination.entries.forEach { destination ->
            assertTrue("blank label for $destination", destination.label.isNotBlank())
            // Resolving the icon here also guards against a missing branch in the
            // when-expression that maps destinations to artwork.
            assertTrue(destination.icon.name.isNotBlank())
        }
    }

    @Test
    fun `a destination swaps its outlined glyph for the filled one while focused`() {
        TvDestination.entries.forEach { destination ->
            assertTrue(
                "${destination.name} must rest as an outlined icon, was ${destination.icon.name}",
                destination.icon.name.startsWith("Outlined.")
            )
            assertTrue(
                "${destination.name} must fill in while focused, was ${destination.focusedIcon.name}",
                destination.focusedIcon.name.startsWith("Filled.")
            )
        }
    }

    @Test
    fun `each destination has its own stable test tag for its item and its name`() {
        val tags = TvDestination.entries.map { TvTestTags.nav(it) }
        assertEquals(tags.size, tags.distinct().size)
        assertEquals("tv_nav_home", TvTestTags.nav(TvDestination.Home))
        assertEquals("tv_nav_settings", TvTestTags.nav(TvDestination.Settings))

        // The label is a separate node from the item (the rail hides names while it is
        // folded), so it needs its own distinct tag.
        val labelTags = TvDestination.entries.map { TvTestTags.navLabel(it) }
        assertEquals(labelTags.size, labelTags.distinct().size)
        assertTrue(labelTags.none { it in tags })
    }
}
