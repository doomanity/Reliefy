package com.maxrave.domain.repository

import com.maxrave.domain.data.model.video.VideoChannelTab
import com.maxrave.domain.data.model.video.VideoPage
import com.maxrave.domain.data.model.video.VideoSearchFilter

/**
 * The YouTube-style video side of Reliefy: feeds of videos, no ads.
 *
 * Every call answers a [Result], so a screen can show "try again" instead of crashing when the
 * network or YouTube misbehaves. A page with a `continuation` has more; hand that token to [more].
 */
interface VideoRepository {
    /** The home feed. Personal when the user is signed in, a mix of popular topics when not. */
    suspend fun home(): Result<VideoPage>

    suspend fun search(
        query: String,
        filter: VideoSearchFilter = VideoSearchFilter.ALL,
    ): Result<VideoPage>

    /** Autocomplete for the search box. Empty on any failure. */
    suspend fun suggestions(query: String): List<String>

    suspend fun channel(
        channelId: String,
        tab: VideoChannelTab = VideoChannelTab.VIDEOS,
    ): Result<VideoPage>

    suspend fun playlist(playlistId: String): Result<VideoPage>

    /** The "up next" list for a video. */
    suspend fun related(videoId: String): Result<VideoPage>

    /** Comments of [videoId]; [continuation] is null for the first page. */
    suspend fun comments(
        videoId: String,
        continuation: String? = null,
    ): Result<com.maxrave.domain.mediaservice.video.VideoCommentsPage>

    /** The next page of whatever feed produced [continuation]. */
    suspend fun more(continuation: String): Result<VideoPage>
}
