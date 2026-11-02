package com.srik.shortstracker.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Runs the parser over real screens captured from a phone (OnePlus, Android 16, YouTube 21.41,
 * Instagram 451) by the tracker's debug build. See src/test/resources/screens.
 */
class ScreenParserTest {
    private val height = 3168

    private fun parse(app: ShortsApp, screen: String) = ScreenParser.parse(app, Screens.load(screen), height)
    private fun yt(screen: String) = parse(ShortsApp.YOUTUBE, screen)
    private fun ig(screen: String) = parse(ShortsApp.INSTAGRAM, screen)
    private fun snap(screen: String) = parse(ShortsApp.SNAPCHAT, screen)

    @Test
    fun `youtube short reads channel and title, not the music line`() {
        val a = yt("yt_short_a")
        assertEquals(ViewKind.SHORT, a.kind)
        assertEquals("@ArtemYashin", a.creator)
        assertEquals("Did I steal my game?", a.caption)
    }

    @Test
    fun `each swipe is a different youtube short`() {
        val keys = listOf("yt_short_a", "yt_short_b", "yt_short_c").map { yt(it).key }
        assertEquals(3, keys.toSet().size)
        assertNull(keys.firstOrNull { it == null })
    }

    @Test
    fun `youtube short comments keep the same short playing`() {
        listOf("yt_short_comments", "yt_short_comments_scrolled").forEach { screen ->
            val info = yt(screen)
            assertEquals(screen, ViewKind.SHORT, info.kind)
            assertNull(screen, info.key)
        }
    }

    @Test
    fun `youtube search and home are not tracked, even with the mini player`() {
        assertNull(yt("yt_search_typing").kind)
        assertNull(yt("yt_home").kind)
    }

    @Test
    fun `youtube watch page is a regular video`() {
        val info = yt("yt_watch")
        assertEquals(ViewKind.VIDEO, info.kind)
        assertEquals("Me at the zoo", info.caption)
        assertEquals("jawed", info.creator)
    }

    @Test
    fun `scrolling the watch page keeps the same video`() {
        val info = yt("yt_watch_scrolled")
        assertEquals(ViewKind.VIDEO, info.kind)
        val first = yt("yt_watch").key
        assertEquals(true, info.key == null || info.key == first)
    }

    @Test
    fun `instagram reel reads author and caption, not the counts`() {
        val info = ig("ig_reel")
        assertEquals(ViewKind.SHORT, info.kind)
        assertEquals("lord.farquaad.sol", info.creator)
        assertEquals("Drippy necklace …", info.caption)
    }

    @Test
    fun `instagram comments keep the same reel`() {
        val info = ig("ig_reel_comments")
        assertEquals(ViewKind.SHORT, info.kind)
        assertEquals(true, info.key == null || info.key == ig("ig_reel").key)
    }

    @Test
    fun `instagram feed video counts after settling`() {
        val a = ig("ig_feed_a")
        assertEquals(ViewKind.SHORT, a.kind)
        assertEquals("swetaandankit", a.creator)
        assertEquals(2_000L, a.settleMs)
        // Scrolled down the same post: its header is gone, so nothing new.
        assertNull(ig("ig_feed_b").key)
        // The next post is only peeking in from the bottom.
        val c = ig("ig_feed_c")
        assertNotEquals(a.key, c.key)
    }

    @Test
    fun `snapchat spotlight reads the creator and sound line`() {
        val d = snap("snap_spot_d")
        assertEquals(ViewKind.SHORT, d.kind)
        assertEquals("James Condy", d.creator)
        assertEquals("Original sound · james_condy", d.caption)
        assertEquals("Gym Crush", snap("snap_spot_c").creator)
        assertNotEquals(d.key, snap("snap_spot_e").key)
    }

    @Test
    fun `snapchat ads are not counted`() {
        assertNull(snap("snap_spot_b").key)
    }
}
