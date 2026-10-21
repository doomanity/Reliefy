package com.maxrave.domain.mediaservice.video

import com.maxrave.domain.data.model.video.VideoItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** A chapter of a video, from its description timestamps. */
data class VideoChapter(
    val title: String,
    val startMs: Long,
)

/** A SponsorBlock segment that playback skips over. */
data class SkipSegment(
    val startMs: Long,
    val endMs: Long,
    /** SponsorBlock's category, for example "sponsor", "selfpromo", "interaction", "intro". */
    val category: String,
)

/** What the watch page shows under the player. Filled once the video's page has been read. */
data class VideoDetails(
    val videoId: String,
    val title: String,
    val description: String = "",
    val channelId: String? = null,
    val channelName: String? = null,
    val channelAvatarUrl: String? = null,
    val channelVerified: Boolean = false,
    /** -1 when YouTube hides it. */
    val subscriberCount: Long = -1,
    val viewCount: Long = -1,
    val likeCount: Long = -1,
    /** From Return YouTube Dislike. -1 when unknown. */
    val dislikeCount: Long = -1,
    /** "3 weeks ago", already localized by YouTube. */
    val uploadedText: String? = null,
    val durationSeconds: Long = 0,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val thumbnailUrl: String? = null,
    val related: List<VideoItem> = emptyList(),
    val chapters: List<VideoChapter> = emptyList(),
)

/** One comment under a video. */
data class VideoComment(
    val id: String,
    val author: String,
    val authorAvatarUrl: String? = null,
    val text: String,
    /** "2 weeks ago", already localized by YouTube. */
    val publishedText: String? = null,
    val likeCount: Long = 0,
    val replyCount: Int = 0,
    val isPinned: Boolean = false,
    val isHeartedByUploader: Boolean = false,
    val isByUploader: Boolean = false,
)

/** A page of comments. Pass [nextPage] back to load the next one; null at the end. */
data class VideoCommentsPage(
    val comments: List<VideoComment>,
    val nextPage: String? = null,
    /** Total count shown on YouTube, or -1 when unknown. */
    val totalCount: Long = -1,
    val disabled: Boolean = false,
)

enum class VideoLoadStatus { IDLE, LOADING, READY, ERROR }

data class VideoPlaybackState(
    /** The video being played, as it was tapped. Lets the watch page draw before details load. */
    val current: VideoItem? = null,
    val details: VideoDetails? = null,
    val status: VideoLoadStatus = VideoLoadStatus.IDLE,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    /** Available video heights, highest first. Empty until the tracks are known. */
    val qualities: List<Int> = emptyList(),
    /** The chosen height, or null for automatic. */
    val selectedQuality: Int? = null,
    /** The height actually playing right now. */
    val playingQuality: Int? = null,
    val videoAspectRatio: Float = 16f / 9f,
    val queue: List<VideoItem> = emptyList(),
    val queueIndex: Int = -1,
    val autoplay: Boolean = true,
    val segments: List<SkipSegment> = emptyList(),
    /** Which way the stream is being loaded ("SABR", "DASH", "HLS"), for the info row. */
    val streamKind: String? = null,
) {
    val hasNext: Boolean get() = queueIndex in 0 until queue.size - 1 || (autoplay && details?.related?.isNotEmpty() == true)
    val hasPrevious: Boolean get() = queueIndex > 0
    val isActive: Boolean get() = current != null
}

/**
 * The video side's player. It is separate from the music player on purpose: videos are loaded the
 * LibreTube way (PoToken, SABR streaming) and need their own surface, quality list and queue.
 */
interface VideoPlayback {
    /** False where no video player exists (desktop for now). */
    val isSupported: Boolean
    val state: StateFlow<VideoPlaybackState>

    /** Playback position, refreshed a few times a second while a video is loaded. */
    val positionMs: StateFlow<Long>
    val bufferedMs: StateFlow<Long>

    /** One event per SponsorBlock segment skipped, so the screen can say what was skipped. */
    val skipEvents: SharedFlow<SkipSegment>

    /** Plays [video]. [queue] is what plays after it (the list it was tapped in). */
    fun play(
        video: VideoItem,
        queue: List<VideoItem> = emptyList(),
    )

    fun playAt(index: Int)

    fun togglePlayPause()

    fun pause()

    fun resume()

    fun seekTo(positionMs: Long)

    fun next()

    fun previous()

    fun setSpeed(speed: Float)

    fun setQuality(height: Int?)

    fun setAutoplay(enabled: Boolean)

    fun retry()

    /** Stops playback and forgets the video (closes the mini player). */
    fun stop()

    /** "Up next" for [videoId] read elsewhere; autoplay picks from it when the video ends. */
    fun offerUpNext(
        videoId: String,
        videos: List<VideoItem>,
    ) {}

    /** Comments of [videoId]. [page] is null for the first page, then a page's [VideoCommentsPage.nextPage]. */
    suspend fun comments(
        videoId: String,
        page: String? = null,
    ): Result<VideoCommentsPage>
}

/** Used where there is no video player. Every call does nothing. */
object UnsupportedVideoPlayback : VideoPlayback {
    override val isSupported: Boolean = false
    private val _state = MutableStateFlow(VideoPlaybackState())
    override val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()
    override val positionMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    override val bufferedMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    override val skipEvents: SharedFlow<SkipSegment> = MutableSharedFlow<SkipSegment>().asSharedFlow()

    override fun play(
        video: VideoItem,
        queue: List<VideoItem>,
    ) {
        _state.value =
            VideoPlaybackState(
                current = video,
                status = VideoLoadStatus.ERROR,
                error = "Video playback is not available on this device yet.",
            )
    }

    override fun playAt(index: Int) {}

    override fun togglePlayPause() {}

    override fun pause() {}

    override fun resume() {}

    override fun seekTo(positionMs: Long) {}

    override fun next() {}

    override fun previous() {}

    override fun setSpeed(speed: Float) {}

    override fun setQuality(height: Int?) {}

    override fun setAutoplay(enabled: Boolean) {}

    override fun retry() {}

    override fun stop() {
        _state.value = VideoPlaybackState()
    }

    override suspend fun comments(
        videoId: String,
        page: String?,
    ): Result<VideoCommentsPage> = Result.success(VideoCommentsPage(emptyList(), disabled = true))
}
