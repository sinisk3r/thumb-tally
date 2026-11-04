package com.srik.shortstracker.tracker

data class ScreenInfo(
    /** Null when the screen isn't something we track (home feed, search, profile, mini player...). */
    val kind: ViewKind? = null,
    val creator: String? = null,
    val caption: String? = null,
    /**
     * Identifies the video on screen. Null when the player is there but covered (comments, description,
     * full screen...), in which case whatever was playing is still playing.
     */
    val key: String? = null,
    /** How long a video must stay on screen before it counts. Used for autoplaying feed videos. */
    val settleMs: Long = 0,
    /** "Artist · Title" from the app's audio line, when it shows one (see [Songs]). Not part of [key]. */
    val song: String? = null,
)

/**
 * Works out whether a YouTube/Instagram screen is a Short/Reel or a regular YouTube video, and which one.
 *
 * Only labels that belong to the playing video are trusted ("Go to channel @x" on a Short, the author row
 * on a Reel). Comments, search suggestions and the home feed are full of @handles and text that used to be
 * mistaken for new videos. Real screens from the phone live in src/test/resources/screens; when an app
 * update breaks detection, capture a new one with "Save screen dumps" and add a test.
 */
object ScreenParser {
    private val GO_TO_CHANNEL = Regex("^Go to channel (@\\S+)")
    private val SUBSCRIBE_TO = Regex("^Subscribe to (.+?)\\.?$")
    private val HANDLE = Regex("^@[\\w.\\-]{2,}$")
    private val STATS = Regex("\\bviews?\\b.*\\bago\\b", RegexOption.IGNORE_CASE)
    private val REEL_BY = Regex("^Reels? by ([A-Za-z0-9._]{2,30})", RegexOption.IGNORE_CASE)
    private val POSTED = Regex("^(\\S+?)(?: and .+?)? posted a (?:video|reel)\\b", RegexOption.IGNORE_CASE)
    private val COUNT = Regex("^[\\d.,]+\\s*[KMB]?$", RegexOption.IGNORE_CASE)
    private val SKIP_LABELS = setOf("ai content", "follow", "following", "subscribe", "subscribed", "sponsored", "paid partnership")

    fun parse(app: ShortsApp, root: NodeSnap, screenHeight: Int): ScreenInfo {
        val all = root.flatten()
        val nodes = all.filter { it.visible }
        val width = root.right.takeIf { it > 0 } ?: nodes.maxOfOrNull { it.right } ?: 0
        return when (app) {
            ShortsApp.YOUTUBE -> parseYouTube(nodes, width, screenHeight)
            ShortsApp.INSTAGRAM -> parseInstagram(nodes, all, width, screenHeight)
            ShortsApp.SNAPCHAT -> parseSnapchat(nodes, width, screenHeight)
        }
    }

    private fun parseYouTube(nodes: List<NodeSnap>, width: Int, height: Int): ScreenInfo {
        val shortsPlayer = nodes.any {
            it.idName in setOf("reel_watch_player", "reel_player_page_container") && it.height >= height * 0.5
        }
        if (shortsPlayer) return parseYouTubeShort(nodes, height)
        // The watch page; the mini player (a small box over the home feed) doesn't count.
        val watchPlayer = nodes.any { it.idName == "watch_player" && it.width >= width * 0.9 }
        if (watchPlayer) return parseYouTubeVideo(nodes)
        return ScreenInfo()
    }

    private fun parseYouTubeShort(nodes: List<NodeSnap>, height: Int): ScreenInfo {
        // The music pill sits in the bottom row next to the title, only when the sound uses real music.
        val song = nodes.firstNotNullOfOrNull { node -> if (node.top >= height * 0.75) Songs.fromLabel(node.desc) else null }
        // The channel row only shows on the plain player, not when comments or the description cover it.
        val anchorAt = nodes.indexOfFirst { node ->
            node.desc?.let { GO_TO_CHANNEL.containsMatchIn(it) || it == "Collaboration channels" } == true
        }
        if (anchorAt < 0) return ScreenInfo(ViewKind.SHORT, song = song)
        val anchor = nodes[anchorAt]
        val after = nodes.drop(anchorAt + 1)
        val creator = anchor.desc?.let { GO_TO_CHANNEL.find(it)?.groupValues?.get(1) }
            ?: after.firstNotNullOfOrNull { node -> node.desc?.let { SUBSCRIBE_TO.find(it)?.groupValues?.get(1) } }
        // The title is the first line under the channel row; the music/related-video pill comes after it.
        val title = after.firstOrNull { node ->
            node.top >= anchor.bottom && node.label?.trim()?.let(::isContent) == true
        }?.label?.let(::cleanCaption)
        return ScreenInfo(ViewKind.SHORT, creator, title, key = "yt-short|${creator.orEmpty()}|${title.orEmpty()}", song = song)
    }

    private fun parseYouTubeVideo(nodes: List<NodeSnap>): ScreenInfo {
        // "20 million likes. 445 million views. 21 years ago" sits right under the title.
        val statsAt = nodes.indexOfFirst { node -> node.text?.let { STATS.containsMatchIn(it) } == true }
        if (statsAt < 0) return ScreenInfo(ViewKind.VIDEO)
        val title = nodes.take(statsAt).lastOrNull { node ->
            node.text?.trim()?.let { isContent(it) && !HANDLE.matches(it) } == true
        }?.text?.let(::cleanCaption)
        val channel = nodes.firstNotNullOfOrNull { node -> node.desc?.let { SUBSCRIBE_TO.find(it)?.groupValues?.get(1) } }
            ?: nodes.firstNotNullOfOrNull { node -> node.text?.trim()?.takeIf { HANDLE.matches(it) } }
        if (title == null) return ScreenInfo(ViewKind.VIDEO)
        return ScreenInfo(ViewKind.VIDEO, channel, title, key = "yt-video|${channel.orEmpty()}|$title")
    }

    private fun parseInstagram(nodes: List<NodeSnap>, all: List<NodeSnap>, width: Int, height: Int): ScreenInfo {
        val reelsViewer = nodes.any {
            it.idName in setOf("clips_viewer_view_pager", "clips_video_container") && it.height >= height * 0.6
        }
        if (reelsViewer) return parseInstagramReel(nodes, all, width, height)
        return parseInstagramFeed(nodes, height)
    }

    private fun parseInstagramReel(nodes: List<NodeSnap>, all: List<NodeSnap>, width: Int, height: Int): ScreenInfo {
        val authorAt = nodes.indexOfFirst { it.idName == "clips_author_username" }
        val author = nodes.getOrNull(authorAt)
        val creator = author?.label?.trim()
            ?: nodes.firstNotNullOfOrNull { node -> node.desc?.let { REEL_BY.find(it.trim())?.groupValues?.get(1)?.trimEnd('.') } }
            ?: return ScreenInfo(ViewKind.SHORT)
        // The keyboard (typing a comment) hides the author and caption rows; the same Reel is still playing.
        if (author == null && all.any { it.idName == "clips_author_username" && !it.visible }) {
            return ScreenInfo(ViewKind.SHORT, creator)
        }
        // The caption is the line just under the author row, starting at the left edge (not the tab bar).
        val caption = author?.let { a ->
            nodes.drop(authorAt + 1).firstOrNull { node ->
                node.top >= a.bottom && node.top < a.bottom + height * 0.08 && node.left < width * 0.2 &&
                    node.label?.trim()?.let(::isContent) == true
            }?.label?.let(::cleanCaption)
        }
        // The audio line sits in the same band, just under the author's name.
        val song = author?.let { a ->
            nodes.drop(authorAt + 1).firstNotNullOfOrNull { node ->
                if (node.top >= a.top && node.top < a.bottom + height * 0.08 && node.left < width * 0.3) {
                    Songs.fromLabel(node.label)
                } else {
                    null
                }
            }
        }
        return ScreenInfo(ViewKind.SHORT, creator, caption, key = "ig-reel|$creator|${caption.orEmpty()}", song = song)
    }

    /** A video post in the home feed counts as a Reel once it fills most of the screen for a moment. */
    private fun parseInstagramFeed(nodes: List<NodeSnap>, height: Int): ScreenInfo {
        val video = nodes.filter { it.idName == "video_container" }.maxByOrNull { it.height } ?: return ScreenInfo()
        if (video.height < height * 0.45) return ScreenInfo()
        val headerNode = nodes.firstOrNull { node ->
            node.idName == "row_feed_profile_header" && node.top in (video.top - height / 10)..(video.top + height / 10)
        }
        val header = headerNode?.desc ?: return ScreenInfo(ViewKind.SHORT, settleMs = FEED_SETTLE_MS)
        val creator = POSTED.find(header)?.groupValues?.get(1)
        // The line under the name is the song or a place; only a song has "Artist · Title".
        val song = nodes.firstNotNullOfOrNull { node ->
            if (node.idName == "secondary_label" && node.top in headerNode.top..headerNode.bottom) Songs.fromLabel(node.label) else null
        }
        return ScreenInfo(ViewKind.SHORT, creator, null, key = "ig-feed|$header", settleMs = FEED_SETTLE_MS, song = song)
    }

    /**
     * Spotlight, Snapchat's Shorts. Its view IDs are scrambled, but the creator's name and the sound line
     * always sit at the bottom left. Ads aren't counted.
     */
    private fun parseSnapchat(nodes: List<NodeSnap>, width: Int, height: Int): ScreenInfo {
        val spotlight = nodes.any { it.idName == "spotlight_container" && it.height >= height * 0.8 } &&
            nodes.any { it.idName == "opera_viewer" }
        if (!spotlight) return ScreenInfo()
        if (nodes.any { it.idName?.startsWith("sc_composer_ad") == true || it.text == "Ad" }) return ScreenInfo(ViewKind.SHORT)
        val lines = nodes.asSequence()
            .filter { it.left < width * 0.3 && it.top >= height * 0.7 && it.bottom <= height * 0.9 }
            .mapNotNull { node -> node.text?.trim()?.takeIf(::isContent) }
            .toList()
        val creator = lines.firstOrNull() ?: return ScreenInfo(ViewKind.SHORT)
        val caption = lines.getOrNull(1)?.let(::cleanCaption)
        return ScreenInfo(ViewKind.SHORT, creator, caption, key = "snap|$creator|${caption.orEmpty()}", song = Songs.fromLabel(caption))
    }

    private fun isContent(text: String): Boolean =
        text.length >= 2 && text.lowercase() !in SKIP_LABELS && !text.startsWith("Subscribe", ignoreCase = true) &&
            !text.startsWith("Follow", ignoreCase = true) && !COUNT.matches(text)

    private fun cleanCaption(raw: String): String? =
        raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(200)

    private val NodeSnap.height get() = bottom - top
    private val NodeSnap.width get() = right - left

    private const val FEED_SETTLE_MS = 2_000L
}
