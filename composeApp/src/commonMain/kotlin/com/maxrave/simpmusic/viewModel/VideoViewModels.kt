package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.data.model.video.VideoChannelTab
import com.maxrave.domain.data.model.video.VideoFeedItem
import com.maxrave.domain.data.model.video.VideoItem
import com.maxrave.domain.data.model.video.VideoPage
import com.maxrave.domain.data.model.video.VideoSearchFilter
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.video.VideoPlayback
import com.maxrave.domain.repository.VideoRepository
import com.maxrave.simpmusic.ui.screen.video.VideoWatchUi
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
    private val videoPlayback: VideoPlayback by inject<VideoPlayback>()

    /** True while a YouTube account is signed in (the home feed is then made for the user). */
    val isLoggedIn: Flow<Boolean> by lazy { dataStoreManager.loggedIn.map { it == DataStoreManager.TRUE } }

    /**
     * Plays [video] in the video player and opens the watch page. The videos after it in
     * [context] (the list it was tapped in) play next, the way YouTube carries on through a list.
     * Shorts and upcoming premieres are left out of that queue.
     */
    fun playVideo(
        video: VideoItem,
        context: List<VideoFeedItem>,
    ) {
        val videos = context.mapNotNull { (it as? VideoFeedItem.Video)?.video }
        val start = videos.indexOfFirst { it.videoId == video.videoId }
        val following =
            if (start >= 0) {
                videos.drop(start + 1).filter { !it.isShort && !it.isUpcoming }.take(QUEUE_LIMIT)
            } else {
                emptyList()
            }
        videoPlayback.play(video, following)
        VideoWatchUi.open()
    }

    private companion object {
        const val QUEUE_LIMIT = 50
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

/** The watch page: the player's state plus the "From <channel>" list. */
class VideoWatchViewModel : BaseViewModel() {
    val playback: VideoPlayback by inject<VideoPlayback>()
    private val videoRepository: VideoRepository by inject<VideoRepository>()

    private val _channelVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val channelVideos: StateFlow<List<VideoItem>> = _channelVideos.asStateFlow()

    private var channelLoadedFor: String? = null

    fun loadChannelVideos(channelId: String) {
        if (channelLoadedFor == channelId) return
        channelLoadedFor = channelId
        _channelVideos.value = emptyList()
        viewModelScope.launch {
            videoRepository.channel(channelId, VideoChannelTab.VIDEOS).onSuccess { page ->
                if (channelLoadedFor == channelId) {
                    _channelVideos.value = page.items.mapNotNull { (it as? VideoFeedItem.Video)?.video }
                }
            }
        }
    }

    /** Plays [video] from [list], queueing the rest of the list after it. */
    fun playFrom(
        video: VideoItem,
        list: List<VideoItem>,
    ) {
        val index = list.indexOfFirst { it.videoId == video.videoId }
        val rest = if (index >= 0) list.drop(index + 1) else emptyList()
        playback.play(video, rest.filter { !it.isShort && !it.isUpcoming })
    }
}
