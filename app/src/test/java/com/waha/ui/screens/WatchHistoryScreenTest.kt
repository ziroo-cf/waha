package com.waha.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchHistoryScreenTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `labels cover the time buckets`() {
        assertEquals("الآن", watchedAgoLabel(now - 30_000, now))
        assertEquals("قبل 5 دقيقة", watchedAgoLabel(now - 5 * 60_000, now))
        assertEquals("قبل 3 ساعة", watchedAgoLabel(now - 3 * 3_600_000L, now))
        assertEquals("قبل 2 يوم", watchedAgoLabel(now - 2 * 86_400_000L, now))
        assertEquals("قبل فترة طويلة", watchedAgoLabel(now - 40L * 86_400_000L, now))
    }
}
