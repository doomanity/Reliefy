package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.domain.data.model.video.VideoChannelItem
import com.maxrave.domain.data.model.video.VideoFeedItem
import com.maxrave.domain.data.model.video.VideoItem
import com.maxrave.domain.data.model.video.VideoPlaylistItem
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.VideoFeedState
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.retry
import simpmusic.composeapp.generated.resources.video_badge_live
import simpmusic.composeapp.generated.resources.video_badge_upcoming
import simpmusic.composeapp.generated.resources.video_empty
import simpmusic.composeapp.generated.resources.video_error_message
import simpmusic.composeapp.generated.resources.video_error_title
import simpmusic.composeapp.generated.resources.video_load_more_failed
import simpmusic.composeapp.generated.resources.video_shorts

/** Everything a video list can hold, with consecutive Shorts folded into one shelf. */
private sealed interface VideoRow {
    val key: String

    class Single(
        val item: VideoFeedItem,
    ) : VideoRow {
        override val key: String =
            when (item) {
                is VideoFeedItem.Video -> "v:" + item.video.videoId
                is VideoFeedItem.Channel -> "c:" + item.channel.channelId
                is VideoFeedItem.Playlist -> "p:" + item.playlist.playlistId
            }
    }

    class Shorts(
        val videos: List<VideoItem>,
    ) : VideoRow {
        override val key: String = "s:" + videos.first().videoId
    }
}

private fun List<VideoFeedItem>.toRows(): List<VideoRow> {
    val rows = ArrayList<VideoRow>(size)
    val pending = ArrayList<VideoItem>()

    fun flush() {
        if (pending.isEmpty()) return
        // One lonely Short reads better as a normal card than as a shelf of one.
        if (pending.size == 1) {
            rows.add(VideoRow.Single(VideoFeedItem.Video(pending.first())))
        } else {
            rows.add(VideoRow.Shorts(pending.toList()))
        }
        pending.clear()
    }
    for (item in this) {
        if (item is VideoFeedItem.Video && item.video.isShort) {
            pending.add(item.video)
        } else {
            flush()
            rows.add(VideoRow.Single(item))
        }
    }
    flush()
    return rows
}

/**
 * The YouTube-style list used by home, search, channels and playlists: one column on a phone, more
 * on a wide window. Loads the next page when the end comes near.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFeedGrid(
    state: VideoFeedState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (channelId: String, name: String?) -> Unit,
    onPlaylistClick: (VideoPlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    gridState: LazyGridState = rememberLazyGridState(),
    showChannelOnCards: Boolean = true,
    header: (@Composable () -> Unit)? = null,
) {
    val rows = remember(state.items) { state.items.toRows() }

    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.items.size, state.continuation, state.moreFailed) {
        if (nearEnd && state.continuation != null && !state.moreFailed) onLoadMore()
    }

    Box(modifier.fillMaxSize()) {
        when {
            state.hasError && rows.isEmpty() ->
                VideoMessage(
                    title = stringResource(Res.string.video_error_title),
                    message = stringResource(Res.string.video_error_message),
                    actionLabel = stringResource(Res.string.retry),
                    onAction = onRefresh,
                )

            state.isLoading && rows.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            else ->
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 300.dp),
                        state = gridState,
                        contentPadding = contentPadding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (header != null) {
                            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
                        }
                        if (rows.isEmpty()) {
                            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(Res.string.video_empty), style = typo().bodyMedium)
                                }
                            }
                        }
                        rows.forEach { row ->
                            when (row) {
                                is VideoRow.Shorts ->
                                    item(key = row.key, span = { GridItemSpan(maxLineSpan) }) {
                                        ShortsShelf(row.videos, onVideoClick)
                                    }

                                is VideoRow.Single ->
                                    when (val entry = row.item) {
                                        is VideoFeedItem.Video ->
                                            item(key = row.key) {
                                                VideoCard(
                                                    video = entry.video,
                                                    onClick = { onVideoClick(entry.video) },
                                                    onChannelClick = {
                                                        entry.video.channelId?.let { id ->
                                                            onChannelClick(id, entry.video.channelName)
                                                        }
                                                    },
                                                    showChannel = showChannelOnCards,
                                                )
                                            }

                                        is VideoFeedItem.Channel ->
                                            item(key = row.key, span = { GridItemSpan(maxLineSpan) }) {
                                                VideoChannelRow(entry.channel) {
                                                    onChannelClick(entry.channel.channelId, entry.channel.name)
                                                }
                                            }

                                        is VideoFeedItem.Playlist ->
                                            item(key = row.key) {
                                                VideoPlaylistCard(entry.playlist) { onPlaylistClick(entry.playlist) }
                                            }
                                    }
                            }
                        }
                        if (state.isLoadingMore) {
                            item(key = "loading-more", span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                }
                            }
                        }
                        if (state.moreFailed) {
                            item(key = "more-failed", span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = onLoadMore) {
                                        Text(stringResource(Res.string.video_load_more_failed), style = typo().bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
fun VideoMessage(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = typo().titleMedium)
            Text(message, style = typo().bodyMedium)
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun RemoteImage(
    url: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model =
            ImageRequest
                .Builder(LocalPlatformContext.current)
                .data(url)
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCacheKey(url)
                .crossfade(true)
                .build(),
        placeholder = rememberHolderPainter(isVideo = true),
        error = rememberHolderPainter(isVideo = true),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
private fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color.Black.copy(alpha = 0.78f),
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** A video: 16:9 picture with its length, then channel picture, title and a line of numbers. */
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChannel: Boolean = true,
) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            RemoteImage(video.thumbnailUrl, Modifier.fillMaxSize())
            when {
                video.isLive ->
                    Badge(
                        stringResource(Res.string.video_badge_live),
                        Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        background = Color(0xFFD50000),
                    )

                video.isUpcoming ->
                    Badge(
                        stringResource(Res.string.video_badge_upcoming),
                        Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    )

                video.durationText != null ->
                    Badge(video.durationText.orEmpty(), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (showChannel) {
                RemoteImage(
                    video.channelAvatarUrl,
                    Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onChannelClick),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = typo().titleSmall.copy(fontSize = 14.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val line =
                    listOfNotNull(
                        video.channelName.takeIf { showChannel },
                        video.viewsText,
                        video.publishedText,
                    ).joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(
                        text = line,
                        style = typo().bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** A row of vertical Shorts under a heading. */
@Composable
fun ShortsShelf(
    videos: List<VideoItem>,
    onClick: (VideoItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.video_shorts),
            style = typo().titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(videos, key = { it.videoId }) { short ->
                Column(Modifier.width(140.dp).clickable { onClick(short) }) {
                    RemoteImage(
                        short.thumbnailUrl,
                        Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)),
                    )
                    Text(
                        short.title,
                        style = typo().titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    short.viewsText?.let {
                        Text(it, style = typo().bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoChannelRow(
    channel: VideoChannelItem,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(channel.avatarUrl, Modifier.size(72.dp).clip(CircleShape))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, style = typo().titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(channel.handle, channel.subscribersText).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = typo().bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            channel.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = typo().bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun VideoPlaylistCard(
    playlist: VideoPlaylistItem,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            RemoteImage(playlist.thumbnailUrl, Modifier.fillMaxSize())
            playlist.videoCountText?.let {
                Badge(it, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        Text(
            playlist.title,
            style = typo().titleSmall.copy(fontSize = 14.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp),
        )
        playlist.channelName?.let {
            Text(
                it,
                style = typo().bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            )
        }
    }
}
