package com.waha.data

import com.waha.domain.PlayerQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the persisted formats that keep the playback position and the
 * chosen quality across sessions. The stores themselves need Android
 * (DataStore / SharedPreferences), so the formats they share with them are
 * tested here on the JVM.
 */
class PlayerPreferencesTest {

    @Test
    fun `ids round trip in order`() {
        val ids = listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc")

        assertEquals(ids, WatchHistoryCodec.decodeIds(WatchHistoryCodec.encodeIds(ids)))
    }

    @Test
    fun `ids drop blanks and decode a missing value as empty`() {
        assertEquals(listOf("a", "b"), WatchHistoryCodec.decodeIds("a\n\nb\n"))
        assertTrue(WatchHistoryCodec.decodeIds(null).isEmpty())
        assertTrue(WatchHistoryCodec.decodeIds("").isEmpty())
    }

    @Test
    fun `millis round trip`() {
        val values = mapOf("a" to 120_000L, "b" to 0L, "c" to 5_400_000L)

        assertEquals(values, WatchHistoryCodec.decodeMillis(WatchHistoryCodec.encodeMillis(values)))
    }

    @Test
    fun `malformed entries are skipped instead of failing the load`() {
        val raw = "a=100\nbroken\nb=notanumber\n=42\nc=250\n"

        assertEquals(mapOf("a" to 100L, "c" to 250L), WatchHistoryCodec.decodeMillis(raw))
        assertTrue(WatchHistoryCodec.decodeMillis(null).isEmpty())
    }

    @Test
    fun `quality round trips and auto is the stored spelling of null`() {
        assertEquals("auto", PlayerQuality.encode(null))
        assertNull(PlayerQuality.decode(PlayerQuality.encode(null)))
        assertEquals(1080, PlayerQuality.decode(PlayerQuality.encode(1080)))
        assertEquals(360, PlayerQuality.decode(PlayerQuality.encode(360)))
    }

    @Test
    fun `an unreadable quality falls back to auto`() {
        assertNull(PlayerQuality.decode(null))
        assertNull(PlayerQuality.decode(""))
        assertNull(PlayerQuality.decode("   "))
        assertNull(PlayerQuality.decode("auto"))
        assertNull(PlayerQuality.decode("hd"))
        assertNull(PlayerQuality.decode("1080P"))
        assertEquals("auto", PlayerQuality.encode(PlayerQuality.decode("hd")))
    }

    @Test
    fun `quality labels match the players' wording`() {
        assertEquals("تلقائي", PlayerQuality.label(null))
        assertEquals("720p", PlayerQuality.label(720))
        assertEquals("480p", PlayerQuality.label(480))
    }
}
