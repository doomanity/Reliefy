package com.maxrave.simpmusic.ui.screen.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maxrave.domain.mediaservice.video.VideoLoadStatus
import com.maxrave.domain.mediaservice.video.VideoPlayback
import com.maxrave.simpmusic.expect.ui.VideoPlayerSurface
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import org.koin.compose.koinInject

/**
 * The shrunk watch page, above the video side's bottom bar: the playing picture, title and
 * channel, play or pause, and close. Tapping it opens the watch page again.
 */
@Composable
fun VideoMiniPlayer(
    modifier: Modifier = Modifier,
    playback: VideoPlayback = koinInject(),
) {
    val state by playback.state.collectAsState()
    val position by playback.positionMs.collectAsState()
    val video = state.current ?: return
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { VideoWatchUi.open() },
    ) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).background(Color.Black)) {
                VideoPlayerSurface(Modifier.fillMaxHeight().aspectRatio(16f / 9f), small = true)
                if (state.status == VideoLoadStatus.LOADING || state.isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.align(Alignment.Center).size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    state.details?.title ?: video.title,
                    style = typo().bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.details?.channelName ?: video.channelName.orEmpty(),
                    style = typo().bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { playback.togglePlayPause() }) {
                Icon(if (state.isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow, null)
            }
            IconButton(onClick = { playback.stop() }) {
                Icon(SimpIcons.Close, null)
            }
        }
        if (state.durationMs > 0) {
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                drawRect(Color.Gray.copy(alpha = 0.3f))
                drawRect(
                    Color(0xFFFF0033),
                    size = Size(size.width * (position.toFloat() / state.durationMs).coerceIn(0f, 1f), size.height),
                )
            }
        }
    }
}
