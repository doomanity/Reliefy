package com.maxrave.domain.data.model.video

import kotlinx.serialization.Serializable

/**
 * Models for Reliefy's YouTube-style video side. They describe what a card on screen needs and
 * nothing else, so the scraper can fill them from any of YouTube's renderers.
 */

/** One video card. Shorts and live streams are videos with a flag, they play the same way. */
@Serializable
data class VideoItem(
    val videoId: String,
    val title: String,
    val channelName: String? = null,
    val channelId: String? = null,
    val channelAvatarUrl: String? = null,
    val thumbnailUrl: String? = null,
    /** "5:38". Null for live streams and for cards that do not show a length. */
    val durationText: String? = null,
    /** Already localized by YouTube, for example "430M views" or "12K watching". */
    val viewsText: String? = null,
    /** Already localized by YouTube, for example "2 years ago". */
    val publishedText: String? = null,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val isUpcoming: Boolean = false,
)

@Serializable
data class VideoChannelItem(
    val channelId: String,
    val name: String,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val subscribersText: String? = null,
    val description: String? = null,
    val isVerified: Boolean = false,
)

@Serializable
data class VideoPlaylistItem(
    val playlistId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    /** "182 videos". */
    val videoCountText: String? = null,
    val channelName: String? = null,
)

/** Everything a feed (home, search, channel, related) can hold. */
sealed interface VideoFeedItem {
    data class Video(
        val video: VideoItem,
    ) : VideoFeedItem

    data class Channel(
        val channel: VideoChannelItem,
    ) : VideoFeedItem

    data class Playlist(
        val playlist: VideoPlaylistItem,
    ) : VideoFeedItem
}

/** One page of a feed. [continuation] is the token for the next page, null at the end. */
data class VideoPage(
    val items: List<VideoFeedItem>,
    val continuation: String? = null,
)

/** The filters the search page offers. [params] is YouTube's own encoded filter. */
enum class VideoSearchFilter(
    val params: String?,
) {
    ALL(null),
    VIDEOS("EgIQAQ%3D%3D"),
    CHANNELS("EgIQAg%3D%3D"),
    PLAYLISTS("EgIQAw%3D%3D"),
    LIVE("EgJAAQ%3D%3D"),
}

/** The tabs of a channel page that list videos. [params] is YouTube's own encoded tab. */
enum class VideoChannelTab(
    val params: String?,
) {
    HOME(null),
    VIDEOS("EgZ2aWRlb3PyBgQKAjoA"),
    SHORTS("EgZzaG9ydHPyBgUKA5oBAA%3D%3D"),
    PLAYLISTS("EglwbGF5bGlzdHPyBgQKAkIA"),
}
