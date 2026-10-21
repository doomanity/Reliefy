package com.maxrave.simpmusic.ui.screen.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maxrave.domain.mediaservice.video.VideoLoadStatus
import com.maxrave.domain.mediaservice.video.VideoPlayback
import com.maxrave.simpmusic.expect.ui.VideoPlayerSurface
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.SimpIcons
import org.koin.compose.koinInject

/**
 * The shrunk watch page, like YouTube's: a small floating video in the corner with pause and
 * close on it. Tapping it opens the watch page again.
 */
@Composable
fun VideoMiniPlayer(
    modifier: Modifier = Modifier,
    playback: VideoPlayback = koinInject(),
) {
    val state by playback.state.collectAsState()
    val position by playback.positionMs.collectAsState()
    if (state.current == null) return
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Color.Black)
            .clickable { VideoWatchUi.open() },
    ) {
        VideoPlayerSurface(Modifier.fillMaxSize(), small = true)
        if (state.status == VideoLoadStatus.LOADING || state.isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(22.dp),
            )
        }
        MiniButton(
            onClick = { playback.togglePlayPause() },
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        ) {
            Icon(if (state.isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        MiniButton(
            onClick = { playback.stop() },
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        ) {
            Icon(SimpIcons.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        if (state.durationMs > 0) {
            Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp)) {
                drawRect(Color.White.copy(alpha = 0.3f))
                drawRect(
                    Color(0xFFFF0033),
                    size = Size(size.width * (position.toFloat() / state.durationMs).coerceIn(0f, 1f), size.height),
                )
            }
        }
    }
}

@Composable
private fun MiniButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
