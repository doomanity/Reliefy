package com.maxrave.kotlinytmusicscraper.video

import com.maxrave.domain.data.model.video.VideoChannelItem
import com.maxrave.domain.data.model.video.VideoFeedItem
import com.maxrave.domain.data.model.video.VideoItem
import com.maxrave.domain.data.model.video.VideoPage
import com.maxrave.domain.data.model.video.VideoPlaylistItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json

/**
 * Turns YouTube's web responses (home, search, channel tabs, playlists, related videos and every
 * continuation of those) into a flat list of cards.
 *
 * It does not follow the page layout. It walks the response in reading order and picks up every
 * item it knows by name, which is why one parser serves every page: YouTube draws a video with the
 * same renderer wherever it shows it. Ads are skipped by name, so nothing promoted ever reaches
 * the list.
 */
object YtVideoFeedParser {
    /** Home, search, channel tab, playlist and their continuations. */
    fun parsePage(response: JsonElement?): VideoPage {
        val roots =
            PAGE_ROOT_KEYS.mapNotNull { response.child(it) }
        return parseRoots(roots)
    }

    /** The "up next" list beside a video and its continuations. */
    fun parseRelated(response: JsonElement?): VideoPage {
        val secondary =
            response.at("contents", "twoColumnWatchNextResults", "secondaryResults")
        val roots =
            if (secondary != null) {
                listOf(secondary)
            } else {
                // A continuation of the list has no watch page around it.
                listOfNotNull(
                    response.child("onResponseReceivedEndpoints"),
                    response.child("onResponseReceivedActions"),
                )
            }
        return parseRoots(roots)
    }

    /** The suggestions Google's autocomplete answers with: `["query", ["a", "b", ...]]`. */
    fun parseSuggestions(raw: String): List<String> {
        val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonArray ?: return emptyList()
        val list = root.getOrNull(1) as? JsonArray ?: return emptyList()
        return list.mapNotNull { it.asStr()?.trim()?.takeIf { s -> s.isNotEmpty() } }
    }

    private fun parseRoots(roots: List<JsonElement>): VideoPage {
        val found = mutableListOf<VideoFeedItem>()
        for (root in roots) collect(root, found)
        return VideoPage(
            items = dedupe(found),
            continuation = findContinuation(roots),
        )
    }

    private fun dedupe(items: List<VideoFeedItem>): List<VideoFeedItem> {
        val seen = HashSet<String>()
        val out = ArrayList<VideoFeedItem>(items.size)
        for (item in items) {
            val key =
                when (item) {
                    is VideoFeedItem.Video -> "v:" + item.video.videoId
                    is VideoFeedItem.Channel -> "c:" + item.channel.channelId
                    is VideoFeedItem.Playlist -> "p:" + item.playlist.playlistId
                }
            if (seen.add(key)) out.add(item)
        }
        return out
    }

    // ----- walking -----

    private fun collect(
        el: JsonElement?,
        out: MutableList<VideoFeedItem>,
    ) {
        when (el) {
            is JsonObject -> {
                for ((key, value) in el) {
                    if (key in SKIP_KEYS) continue
                    when (key) {
                        "videoRenderer", "compactVideoRenderer", "gridVideoRenderer", "playlistVideoRenderer" ->
                            parseVideoRenderer(value)?.let { out.add(VideoFeedItem.Video(it)) }

                        "lockupViewModel" -> parseLockup(value)?.let { out.add(it) }

                        "shortsLockupViewModel" ->
                            parseShortsLockup(value)?.let { out.add(VideoFeedItem.Video(it)) }

                        "reelItemRenderer" ->
                            parseReelItem(value)?.let { out.add(VideoFeedItem.Video(it)) }

                        "channelRenderer", "gridChannelRenderer" ->
                            parseChannelRenderer(value)?.let { out.add(VideoFeedItem.Channel(it)) }

                        "playlistRenderer", "gridPlaylistRenderer" ->
                            parsePlaylistRenderer(value)?.let { out.add(VideoFeedItem.Playlist(it)) }

                        else -> collect(value, out)
                    }
                }
            }

            is JsonArray -> el.forEach { collect(it, out) }
            else -> {}
        }
    }

    private fun findContinuation(roots: List<JsonElement>): String? {
        var token: String? = null
        for (root in roots) {
            for (key in CONTINUATION_KEYS) {
                for (item in root.findAll(key, SKIP_KEYS)) {
                    item.findFirst("token")?.asStr()?.let { token = it }
                }
            }
        }
        return token
    }

    // ----- video renderers (search, channel grid, playlists on older layouts) -----

    private fun parseVideoRenderer(r: JsonElement): VideoItem? {
        val id = r.child("videoId").asStr() ?: return null
        val title = r.child("title").text() ?: return null

        val owner = r.child("ownerText") ?: r.child("longBylineText") ?: r.child("shortBylineText")
        val ownerRun = owner.at("runs", 0)
        val channelName = ownerRun.child("text").asStr() ?: owner.text()
        val channelId = ownerRun.at("navigationEndpoint", "browseEndpoint", "browseId").asStr()
        val avatar =
            bestImage(
                r.at(
                    "channelThumbnailSupportedRenderers",
                    "channelThumbnailWithLinkRenderer",
                    "thumbnail",
                    "thumbnails",
                ),
            ) ?: bestImage(r.child("avatar").findFirst("sources"))

        val statusOverlay =
            r
                .child("thumbnailOverlays")
                .asArr()
                ?.firstNotNullOfOrNull { it.child("thumbnailOverlayTimeStatusRenderer") }
        val overlayStyle = statusOverlay.child("style").asStr()
        val badgeStyles =
            r.child("badges").asArr()?.mapNotNull { it.at("metadataBadgeRenderer", "style").asStr() }.orEmpty()
        val isLive = overlayStyle == "LIVE" || "BADGE_STYLE_TYPE_LIVE_NOW" in badgeStyles
        val isUpcoming = overlayStyle == "UPCOMING" || r.child("upcomingEventData") != null
        val isShort = overlayStyle == "SHORTS" || r.at("navigationEndpoint", "reelWatchEndpoint") != null

        val duration =
            if (isLive || isUpcoming) {
                null
            } else {
                r.child("lengthText").text() ?: statusOverlay.child("text").text()?.takeIf { it.any { c -> c.isDigit() } }
            }

        return VideoItem(
            videoId = id,
            title = title,
            channelName = channelName,
            channelId = channelId,
            channelAvatarUrl = avatar,
            thumbnailUrl = bestImage(r.at("thumbnail", "thumbnails")) ?: fallbackThumbnail(id),
            durationText = duration,
            viewsText = r.child("shortViewCountText").text() ?: r.child("viewCountText").text(),
            publishedText = r.child("publishedTimeText").text(),
            isLive = isLive,
            isShort = isShort,
            isUpcoming = isUpcoming,
        )
    }

    private fun parseReelItem(r: JsonElement): VideoItem? {
        val id = r.child("videoId").asStr() ?: return null
        return VideoItem(
            videoId = id,
            title = r.child("headline").text() ?: return null,
            thumbnailUrl = bestImage(r.at("thumbnail", "thumbnails")) ?: fallbackThumbnail(id),
            viewsText = r.child("viewCountText").text(),
            isShort = true,
        )
    }

    // ----- view-model lockups (related videos, channel tabs, playlists, newer search layouts) -----

    private fun parseLockup(l: JsonElement): VideoFeedItem? {
        val id = l.child("contentId").asStr() ?: return null
        val type = l.child("contentType").asStr().orEmpty()
        val meta = l.at("metadata", "lockupMetadataViewModel")
        val title = meta.child("title").text() ?: return null

        // The rows under the title: channel first when there is one, then views and age.
        val rows =
            meta
                .at("metadata", "contentMetadataViewModel", "metadataRows")
                .asArr()
                ?.map { it.child("metadataParts").asArr() ?: JsonArray(emptyList()) }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        val allParts = rows.flatten()
        val channelPart =
            allParts.firstOrNull { it.at("text", "commandRuns").findFirst("browseId") != null }
                ?: if (rows.size >= 2) rows[0].firstOrNull() else null
        val stats = allParts.filter { it !== channelPart }.mapNotNull { it.child("text").text() }

        val channelName = channelPart.child("text").text()
        val avatarBlock = meta.child("image")
        val channelId = avatarBlock.findFirst("browseId")?.asStr() ?: channelPart.findFirst("browseId")?.asStr()
        val avatar = bestImage(avatarBlock.findFirst("sources"))

        val content = l.child("contentImage")
        val thumb = firstImage(content, setOf("menu"))
        val badges =
            content
                .findAll("thumbnailBadgeViewModel")
                .mapNotNull { b ->
                    val text = b.child("text").text() ?: return@mapNotNull null
                    text to b.child("badgeStyle").asStr().orEmpty()
                }

        return when {
            type.endsWith("VIDEO") -> {
                val live = badges.any { (text, style) -> style.contains("LIVE") || text.equals("LIVE", ignoreCase = true) }
                val upcoming = badges.any { (_, style) -> style.contains("UPCOMING") }
                val duration =
                    if (live || upcoming) {
                        null
                    } else {
                        badges.firstOrNull { (text, _) -> text.any { c -> c.isDigit() } && text.length <= 9 }?.first
                    }
                val short = l.findFirst("reelWatchEndpoint") != null
                VideoFeedItem.Video(
                    VideoItem(
                        videoId = id,
                        title = title,
                        channelName = channelName,
                        channelId = channelId,
                        channelAvatarUrl = avatar,
                        thumbnailUrl = thumb ?: fallbackThumbnail(id),
                        durationText = duration,
                        viewsText = stats.getOrNull(0),
                        publishedText = stats.getOrNull(1),
                        isLive = live,
                        isShort = short,
                        isUpcoming = upcoming,
                    ),
                )
            }

            type.endsWith("PLAYLIST") || type.endsWith("COURSE") || type.endsWith("PODCAST") ->
                VideoFeedItem.Playlist(
                    VideoPlaylistItem(
                        playlistId = id,
                        title = title,
                        thumbnailUrl = thumb,
                        videoCountText = badges.firstOrNull()?.first,
                        channelName = channelName ?: stats.firstOrNull(),
                    ),
                )

            else -> null
        }
    }

    private fun parseShortsLockup(s: JsonElement): VideoItem? {
        val id =
            s.at("onTap", "innertubeCommand", "reelWatchEndpoint", "videoId").asStr()
                ?: s.findFirst("videoId")?.asStr()
                ?: s.child("entityId").asStr()?.removePrefix("shorts-shelf-item-")?.takeIf { it.isNotEmpty() }
                ?: return null
        val title = s.at("overlayMetadata", "primaryText").text() ?: return null
        return VideoItem(
            videoId = id,
            title = title,
            thumbnailUrl = firstImage(s.child("thumbnailViewModel"), setOf("menu")) ?: fallbackThumbnail(id),
            viewsText = s.at("overlayMetadata", "secondaryText").text(),
            isShort = true,
        )
    }

    // ----- channels and playlists -----

    private fun parseChannelRenderer(r: JsonElement): VideoChannelItem? {
        val id = r.child("channelId").asStr() ?: return null
        val name = r.child("title").text() ?: return null
        // YouTube swaps these two on search results: the handle sits in subscriberCountText and the
        // subscriber count in videoCountText.
        val a = r.child("subscriberCountText").text()
        val b = r.child("videoCountText").text()
        val handle = listOfNotNull(a, b).firstOrNull { it.startsWith("@") }
        val subs = listOfNotNull(b, a).firstOrNull { !it.startsWith("@") }
        val verified =
            r.child("ownerBadges").asArr()?.any {
                it.at("metadataBadgeRenderer", "style").asStr()?.contains("VERIFIED") == true
            } == true
        return VideoChannelItem(
            channelId = id,
            name = name,
            handle = handle,
            avatarUrl = bestImage(r.at("thumbnail", "thumbnails")),
            subscribersText = subs,
            description = r.child("descriptionSnippet").text(),
            isVerified = verified,
        )
    }

    private fun parsePlaylistRenderer(r: JsonElement): VideoPlaylistItem? {
        val id = r.child("playlistId").asStr() ?: return null
        val title = r.child("title").text() ?: return null
        val thumbs =
            r.child("thumbnails").asArr()?.firstOrNull().child("thumbnails")
                ?: r.at("thumbnail", "thumbnails")
        val count = r.child("videoCountText").text() ?: r.child("videoCount").asStr()?.let { "$it videos" }
        return VideoPlaylistItem(
            playlistId = id,
            title = title,
            thumbnailUrl = bestImage(thumbs),
            videoCountText = count,
            channelName = (r.child("longBylineText") ?: r.child("shortBylineText")).text(),
        )
    }

    // ----- constants -----

    /** The parts of a response that hold items. Everything else (topbar, tracking, ...) is noise. */
    private val PAGE_ROOT_KEYS =
        listOf(
            "contents",
            "onResponseReceivedActions",
            "onResponseReceivedEndpoints",
            "onResponseReceivedCommands",
            "continuationContents",
        )

    private val CONTINUATION_KEYS = listOf("continuationItemRenderer", "continuationItemViewModel")

    /**
     * Subtrees that never hold a card the user asked for: every kind of advert, plus the menus and
     * overlays that hang off a card and could drag a stray video id in.
     */
    private val SKIP_KEYS =
        setOf(
            "adSlotRenderer",
            "adSlotViewModel",
            "promotedSparklesWebRenderer",
            "promotedSparklesTextSearchRenderer",
            "promotedVideoRenderer",
            "compactPromotedVideoRenderer",
            "searchPyvRenderer",
            "displayAdRenderer",
            "inFeedAdLayoutRenderer",
            "inFeedAdLayoutViewModel",
            "statementBannerRenderer",
            "brandVideoSingleRenderer",
            "brandVideoShelfRenderer",
            "bannerPromoRenderer",
            "menu",
            "menuRenderer",
            "sheetViewModel",
            "frameworkUpdates",
            "expandableMetadataRenderer",
            "macroMarkersListItemRenderer",
        )
}
