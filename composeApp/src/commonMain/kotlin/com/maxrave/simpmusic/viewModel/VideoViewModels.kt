package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.data.model.video.VideoChannelTab
import com.maxrave.domain.data.model.video.VideoFeedItem
import com.maxrave.domain.data.model.video.VideoItem
import com.maxrave.domain.data.model.video.VideoPage
import com.maxrave.domain.data.model.video.VideoSearchFilter
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.VideoRepository
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject

/** What a video list screen draws. */
data class VideoFeedState(
    val items: List<VideoFeedItem> = emptyList(),
    val continuation: String? = null,
    /** The first page (or a refresh) is on its way. */
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    /** The first page failed and there is nothing to show. */
    val hasError: Boolean = false,
    /** A later page failed. The list stays, and the user is offered a retry. */
    val moreFailed: Boolean = false,
)

/**
 * One scrolling list of videos: loads the first page, then the following pages on request, and
 * keeps the paging rules in a single place so home, search, channels and playlists behave alike.
 */
class VideoFeed(
    private val scope: CoroutineScope,
    private val repository: VideoRepository,
) {
    private val _state = MutableStateFlow(VideoFeedState())
    val state: StateFlow<VideoFeedState> = _state.asStateFlow()

    private var loader: (suspend () -> Result<VideoPage>)? = null
    private var firstJob: Job? = null
    private var moreJob: Job? = null

    /** Starts over with [newLoader], or with the same one when null. */
    fun refresh(newLoader: (suspend () -> Result<VideoPage>)? = null) {
        if (newLoader != null) loader = newLoader
        val load = loader ?: return
        firstJob?.cancel()
        moreJob?.cancel()
        firstJob =
            scope.launch {
                _state.update { it.copy(isLoading = true, hasError = false, moreFailed = false, isLoadingMore = false) }
                load().fold(
                    onSuccess = { page ->
                        _state.value =
                            VideoFeedState(
                                items = page.items,
                                continuation = page.continuation,
                                isLoading = false,
                            )
                    },
                    onFailure = {
                        _state.update { cur -> cur.copy(isLoading = false, hasError = cur.items.isEmpty()) }
                    },
                )
            }
    }

    fun loadMore() {
        val current = _state.value
        val token = current.continuation ?: return
        if (current.isLoading || current.isLoadingMore) return
        moreJob =
            scope.launch {
                _state.update { it.copy(isLoadingMore = true, moreFailed = false) }
                repository.more(token).fold(
                    onSuccess = { page ->
                        _state.update { cur ->
                            val seen = HashSet(cur.items.map { it.key() })
                            val fresh = page.items.filter { seen.add(it.key()) }
                            cur.copy(
                                items = cur.items + fresh,
                                continuation = page.continuation,
                                isLoadingMore = false,
                            )
                        }
                    },
                    onFailure = { _state.update { it.copy(isLoadingMore = false, moreFailed = true) } },
                )
            }
    }

    fun clear() {
        firstJob?.cancel()
        moreJob?.cancel()
        _state.value = VideoFeedState(isLoading = false)
    }
}

private fun VideoFeedItem.key(): String =
    when (this) {
        is VideoFeedItem.Video -> "v:" + video.videoId
        is VideoFeedItem.Channel -> "c:" + channel.channelId
        is VideoFeedItem.Playlist -> "p:" + playlist.playlistId
    }

/** The part every video screen shares: starting playback of a tapped video. */
abstract class VideoViewModel : BaseViewModel() {
    protected val videoRepository: VideoRepository by inject<VideoRepository>()
    private val dataStoreManager: DataStoreManager by inject<DataStoreManager>()

    /** True while a YouTube account is signed in (the home feed is then made for the user). */
    val isLoggedIn: Flow<Boolean> by lazy { dataStoreManager.loggedIn.map { it == DataStoreManager.TRUE } }

    /**
     * Plays [video] and queues the videos that follow it in [context] (the list it was tapped in),
     * the way a playlist plays on. Shorts are left out of the queue unless one was tapped.
     */
    fun playVideo(
        video: VideoItem,
        context: List<VideoFeedItem>,
    ) {
        val videos = context.mapNotNull { (it as? VideoFeedItem.Video)?.video }
        val start = videos.indexOfFirst { it.videoId == video.videoId }
        val following =
            if (start >= 0) {
                videos.drop(start + 1).filter { !it.isShort && !it.isUpcoming }
            } else {
                emptyList()
            }
        val tracks = (listOf(video) + following).take(QUEUE_LIMIT).map { it.toTrack() }
        setQueueData(
            QueueData.Data(
                listTracks = ArrayList(tracks),
                firstPlayedTrack = tracks.first(),
                playlistId = null,
                playlistName = "Video",
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        loadMediaItem(tracks.first(), Config.PLAYLIST_CLICK, 0)
    }

    private companion object {
        const val QUEUE_LIMIT = 30
    }
}

class VideoHomeViewModel : VideoViewModel() {
    val feed = VideoFeed(viewModelScope, videoRepository)

    init {
        feed.refresh { videoRepository.home() }
    }

    fun refresh() = feed.refresh()
}

class VideoSearchViewModel : VideoViewModel() {
    val feed = VideoFeed(viewModelScope, videoRepository)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(VideoSearchFilter.ALL)
    val filter: StateFlow<VideoSearchFilter> = _filter.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /** The text the current results answer, or null before the first search. */
    private val _submitted = MutableStateFlow<String?>(null)
    val submitted: StateFlow<String?> = _submitted.asStateFlow()

    private var suggestionJob: Job? = null

    fun onQueryChange(text: String) {
        _query.value = text
        suggestionJob?.cancel()
        if (text.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestionJob =
            viewModelScope.launch {
                delay(200)
                _suggestions.value = videoRepository.suggestions(text.trim())
            }
    }

    fun submit(text: String = _query.value) {
        val q = text.trim()
        if (q.isEmpty()) return
        _query.value = q
        _suggestions.value = emptyList()
        suggestionJob?.cancel()
        _submitted.value = q
        runSearch(q)
    }

    fun setFilter(filter: VideoSearchFilter) {
        if (_filter.value == filter) return
        _filter.value = filter
        _submitted.value?.let { runSearch(it) }
    }

    fun clearResults() {
        _submitted.value = null
        feed.clear()
    }

    private fun runSearch(q: String) {
        val f = _filter.value
        feed.refresh { videoRepository.search(q, f) }
    }
}

/** A channel's videos, or the videos of a playlist. */
class VideoListViewModel : VideoViewModel() {
    val feed = VideoFeed(viewModelScope, videoRepository)

    private val _tab = MutableStateFlow(VideoChannelTab.VIDEOS)
    val tab: StateFlow<VideoChannelTab> = _tab.asStateFlow()

    private var channelId: String? = null
    private var playlistId: String? = null
    private var started = false

    fun loadChannel(id: String) {
        if (started && channelId == id) return
        started = true
        channelId = id
        playlistId = null
        _tab.value = VideoChannelTab.VIDEOS
        feed.refresh { videoRepository.channel(id, VideoChannelTab.VIDEOS) }
    }

    fun loadPlaylist(id: String) {
        if (started && playlistId == id) return
        started = true
        playlistId = id
        channelId = null
        feed.refresh { videoRepository.playlist(id) }
    }

    fun selectTab(tab: VideoChannelTab) {
        val id = channelId ?: return
        if (_tab.value == tab) return
        _tab.value = tab
        feed.refresh { videoRepository.channel(id, tab) }
    }
}

internal fun VideoItem.toTrack(): Track =
    Track(
        album = null,
        artists = listOf(Artist(channelId, channelName ?: "YouTube")),
        duration = durationText,
        durationSeconds = durationText?.let(::durationToSeconds),
        isAvailable = true,
        isExplicit = false,
        likeStatus = "INDIFFERENT",
        // A 4:3 poster. Never square, so the player never mistakes the video for a song.
        thumbnails =
            listOf(
                Thumbnail(
                    height = 360,
                    url = thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    width = 480,
                ),
            ),
        title = title,
        videoId = videoId,
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = "Video",
        views = viewsText,
    )

/** "5:38" -> 338, "1:04:42" -> 3882. Null for anything else ("LIVE", empty). */
internal fun durationToSeconds(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size !in 2..3) return null
    var total = 0
    for (part in parts) {
        val n = part.toIntOrNull() ?: return null
        total = total * 60 + n
    }
    return total
}
