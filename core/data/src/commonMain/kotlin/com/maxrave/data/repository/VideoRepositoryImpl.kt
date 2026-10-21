package com.maxrave.data.repository

import com.maxrave.domain.data.model.video.VideoChannelTab
import com.maxrave.domain.data.model.video.VideoPage
import com.maxrave.domain.data.model.video.VideoSearchFilter
import com.maxrave.domain.repository.VideoRepository
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

private const val TAG = "VideoRepository"

internal class VideoRepositoryImpl(
    private val youTube: YouTube,
) : VideoRepository {
    override suspend fun home(): Result<VideoPage> = call("home") { youTube.video.home() }

    override suspend fun search(
        query: String,
        filter: VideoSearchFilter,
    ): Result<VideoPage> = call("search") { youTube.video.search(query, filter) }

    override suspend fun suggestions(query: String): List<String> =
        withContext(Dispatchers.IO) { youTube.video.suggestions(query) }

    override suspend fun channel(
        channelId: String,
        tab: VideoChannelTab,
    ): Result<VideoPage> = call("channel") { youTube.video.channel(channelId, tab) }

    override suspend fun playlist(playlistId: String): Result<VideoPage> = call("playlist") { youTube.video.playlist(playlistId) }

    override suspend fun related(videoId: String): Result<VideoPage> = call("related") { youTube.video.related(videoId) }

    override suspend fun more(continuation: String): Result<VideoPage> = call("more") { youTube.video.more(continuation) }

    private suspend fun call(
        what: String,
        block: suspend () -> VideoPage,
    ): Result<VideoPage> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "$what failed: ${e.message}")
                Result.failure(e)
            }
        }
}
