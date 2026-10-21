package com.maxrave.simpmusic.ui.screen.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.maxrave.domain.data.model.video.VideoItem
import com.maxrave.domain.mediaservice.video.SkipSegment
import com.maxrave.domain.mediaservice.video.VideoLoadStatus
import com.maxrave.domain.mediaservice.video.VideoPlayback
import com.maxrave.domain.mediaservice.video.VideoPlaybackState
import com.maxrave.simpmusic.expect.shareUrl
import com.maxrave.simpmusic.expect.ui.PlatformBackHandler
import com.maxrave.simpmusic.expect.ui.VideoPictureInPictureEffect
import com.maxrave.simpmusic.expect.ui.VideoPlayerSurface
import com.maxrave.simpmusic.expect.ui.VideoWindowEffect
import com.maxrave.simpmusic.expect.ui.enterVideoPictureInPicture
import com.maxrave.simpmusic.expect.ui.isVideoPictureInPictureSupported
import com.maxrave.simpmusic.expect.ui.rememberIsInPictureInPicture
import com.maxrave.simpmusic.ui.component.RemoteImage
import com.maxrave.simpmusic.ui.component.VideoCard
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.Fullscreen
import com.maxrave.simpmusic.ui.icon.FullscreenExit
import com.maxrave.simpmusic.ui.icon.KeyboardArrowDown
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PictureInPictureAlt
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.Settings
import com.maxrave.simpmusic.ui.icon.Share
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.SkipNext
import com.maxrave.simpmusic.ui.icon.SkipPrevious
import com.maxrave.simpmusic.ui.icon.ThumbDown
import com.maxrave.simpmusic.ui.icon.ThumbUp
import com.maxrave.simpmusic.ui.navigation.destination.video.VideoChannelDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.VideoWatchViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.retry
import simpmusic.composeapp.generated.resources.video_autoplay
import simpmusic.composeapp.generated.resources.video_autoplay_hint
import simpmusic.composeapp.generated.resources.video_badge_live
import simpmusic.composeapp.generated.resources.video_chip_all
import simpmusic.composeapp.generated.resources.video_chip_from
import simpmusic.composeapp.generated.resources.video_chip_related
import simpmusic.composeapp.generated.resources.video_more
import simpmusic.composeapp.generated.resources.video_play_error
import simpmusic.composeapp.generated.resources.video_quality
import simpmusic.composeapp.generated.resources.video_quality_auto
import simpmusic.composeapp.generated.resources.video_quality_auto_playing
import simpmusic.composeapp.generated.resources.video_segment_interaction
import simpmusic.composeapp.generated.resources.video_segment_intro
import simpmusic.composeapp.generated.resources.video_segment_offtopic
import simpmusic.composeapp.generated.resources.video_segment_outro
import simpmusic.composeapp.generated.resources.video_segment_selfpromo
import simpmusic.composeapp.generated.resources.video_segment_sponsor
import simpmusic.composeapp.generated.resources.video_share
import simpmusic.composeapp.generated.resources.video_show_less
import simpmusic.composeapp.generated.resources.video_skipped
import simpmusic.composeapp.generated.resources.video_speed
import simpmusic.composeapp.generated.resources.video_speed_normal
import simpmusic.composeapp.generated.resources.video_streaming_via
import simpmusic.composeapp.generated.resources.video_subscribers_count
import simpmusic.composeapp.generated.resources.video_views_count

/** YouTube's progress red. */
private val ProgressRed = Color(0xFFFF0033)

/** SponsorBlock's green, for the skipped parts on the seek bar. */
private val SegmentGreen = Color(0xFF00D400)

private val Speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * The watch page: the player on top, then the title, channel, likes and dislikes, description and
 * a list of videos to play next. Like YouTube, back shrinks it to the mini player instead of
 * stopping the video.
 */
@Composable
fun VideoWatchScreen(
    navController: NavController,
    viewModel: VideoWatchViewModel = koinViewModel(),
) {
    val playback = viewModel.playback
    val state by playback.state.collectAsState()
    val fullscreen by VideoWatchUi.fullscreen.collectAsState()
    val inPip = rememberIsInPictureInPicture()

    VideoWindowEffect(fullscreen = fullscreen, keepScreenOn = state.isPlaying)
    VideoPictureInPictureEffect(enabled = state.isPlaying, aspectRatio = state.videoAspectRatio)
    PlatformBackHandler(enabled = true) {
        if (fullscreen) VideoWatchUi.setFullscreen(false) else VideoWatchUi.minimize()
    }

    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayerSurface(Modifier.fillMaxSize())
        }
        return
    }

    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayerBox(
                state = state,
                playback = playback,
                fullscreen = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // a tap that misses everything must not fall through to the page underneath
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.statusBars),
        )
        VideoPlayerBox(
            state = state,
            playback = playback,
            fullscreen = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(state.videoAspectRatio.coerceIn(1f, 16f / 9f)),
        )
        WatchDetails(
            state = state,
            viewModel = viewModel,
            onOpenChannel = { id, name ->
                VideoWatchUi.minimize()
                navController.navigate(VideoChannelDestination(id, name))
            },
        )
    }
}

// region Player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlayerBox(
    state: VideoPlaybackState,
    playback: VideoPlayback,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val position by playback.positionMs.collectAsState()
    val buffered by playback.bufferedMs.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var skipped by remember { mutableStateOf<SkipSegment?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Controls fade out after a few seconds of playing, like YouTube.
    LaunchedEffect(controlsVisible, state.isPlaying, interactions) {
        if (controlsVisible && state.isPlaying) {
            delay(3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(700)
            seekHint = null
        }
    }
    LaunchedEffect(playback) {
        playback.skipEvents.collect { segment ->
            skipped = segment
            delay(2_500)
            skipped = null
        }
    }

    val loading = state.status == VideoLoadStatus.LOADING || state.isBuffering
    val failed = state.status == VideoLoadStatus.ERROR

    Box(modifier.background(Color.Black)) {
        VideoPlayerSurface(Modifier.fillMaxSize())

        // Tap shows or hides the controls; double tap on either half jumps 10 seconds.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(playback) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            interactions++
                        },
                        onDoubleTap = { offset ->
                            val forward = offset.x > size.width / 2
                            val target = playback.positionMs.value + if (forward) 10_000 else -10_000
                            playback.seekTo(target.coerceIn(0, state.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
                            seekHint = if (forward) "+10s" else "-10s"
                            interactions++
                        },
                    )
                },
        )

        AnimatedVisibility(
            visible = controlsVisible && !failed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f))) {
                // top
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (fullscreen) {
                        Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp)) {
                            Text(
                                state.details?.title ?: state.current?.title.orEmpty(),
                                color = Color.White,
                                style = typo().titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                state.details?.channelName ?: state.current?.channelName.orEmpty(),
                                color = Color.White.copy(alpha = 0.8f),
                                style = typo().bodySmall,
                                maxLines = 1,
                            )
                        }
                    } else {
                        IconButton(onClick = { VideoWatchUi.minimize() }) {
                            Icon(SimpIcons.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    if (isVideoPictureInPictureSupported()) {
                        IconButton(onClick = { enterVideoPictureInPicture(state.videoAspectRatio) }) {
                            Icon(SimpIcons.PictureInPictureAlt, null, tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        showSettings = true
                        interactions++
                    }) {
                        Icon(SimpIcons.Settings, null, tint = Color.White)
                    }
                }

                // middle
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            playback.previous()
                            interactions++
                        },
                    ) {
                        Icon(SimpIcons.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        if (!loading) {
                            IconButton(
                                onClick = {
                                    playback.togglePlayPause()
                                    interactions++
                                },
                                modifier =
                                    Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.35f)),
                            ) {
                                Icon(
                                    if (state.isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            playback.next()
                            interactions++
                        },
                        enabled = state.hasNext,
                    ) {
                        Icon(
                            SimpIcons.SkipNext,
                            null,
                            tint = Color.White.copy(alpha = if (state.hasNext) 1f else 0.4f),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                // bottom
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = if (fullscreen) 16.dp else 0.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.details?.isLive == true) {
                            Text(
                                stringResource(Res.string.video_badge_live),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFD50000))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        } else {
                            Text(
                                "${formatClock(position)} / ${formatClock(state.durationMs)}",
                                color = Color.White,
                                style = typo().bodySmall,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { VideoWatchUi.setFullscreen(!fullscreen) }) {
                            Icon(
                                if (fullscreen) SimpIcons.FullscreenExit else SimpIcons.Fullscreen,
                                null,
                                tint = Color.White,
                            )
                        }
                    }
                    if (state.details?.isLive != true) {
                        VideoSeekBar(
                            positionMs = position,
                            bufferedMs = buffered,
                            durationMs = state.durationMs,
                            segments = state.segments,
                            showThumb = true,
                            onSeek = {
                                playback.seekTo(it)
                                interactions++
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = if (fullscreen) 16.dp else 0.dp),
                        )
                    }
                }
            }
        }

        // A thin progress line stays when the controls are hidden.
        if (!controlsVisible && !fullscreen && state.durationMs > 0 && state.details?.isLive != true) {
            Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp)) {
                drawRect(Color.White.copy(alpha = 0.25f))
                drawRect(ProgressRed, size = Size(size.width * (position.toFloat() / state.durationMs).coerceIn(0f, 1f), size.height))
            }
        }

        if (loading && !failed) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(48.dp))
        }

        if (failed) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.video_play_error), color = Color.White, style = typo().titleSmall)
                state.error?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.7f),
                        style = typo().bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TextButton(onClick = { playback.retry() }) {
                    Text(stringResource(Res.string.retry), color = ProgressRed)
                }
            }
            if (!fullscreen) {
                IconButton(onClick = { VideoWatchUi.minimize() }, modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(SimpIcons.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }

        seekHint?.let {
            Text(
                it,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        skipped?.let { segment ->
            Text(
                stringResource(Res.string.video_skipped, stringResource(segment.category.labelRes())),
                color = Color.White,
                style = typo().bodySmall,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    if (showSettings) {
        VideoSettingsSheet(state = state, playback = playback, onDismiss = { showSettings = false })
    }
}

/** YouTube style seek bar: grey track, lighter buffer, red progress, green SponsorBlock parts. */
@Composable
private fun VideoSeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    segments: List<SkipSegment>,
    showThumb: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1)
    val fraction = dragFraction ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Box(
        modifier
            .height(20.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) onSeek(((offset.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                }
            }.pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = if (dragFraction != null) 5.dp.toPx() else 3.dp.toPx()
            val y = size.height - trackHeight - 4.dp.toPx()
            val radius = CornerRadius(trackHeight / 2)
            drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(0f, y), Size(size.width, trackHeight), radius)
            val bufferedFraction = (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
            drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(0f, y), Size(size.width * bufferedFraction, trackHeight), radius)
            segments.forEach { segment ->
                val start = (segment.startMs.toFloat() / duration).coerceIn(0f, 1f) * size.width
                val end = (segment.endMs.toFloat() / duration).coerceIn(0f, 1f) * size.width
                drawRect(SegmentGreen, Offset(start, y), Size((end - start).coerceAtLeast(2f), trackHeight))
            }
            drawRoundRect(ProgressRed, Offset(0f, y), Size(size.width * fraction, trackHeight), radius)
            if (showThumb) {
                val thumb = if (dragFraction != null) 9.dp.toPx() else 6.dp.toPx()
                drawCircle(ProgressRed, thumb, Offset(size.width * fraction, y + trackHeight / 2))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSettingsSheet(
    state: VideoPlaybackState,
    playback: VideoPlayback,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(0) } // 0 = menu, 1 = quality, 2 = speed
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            when (page) {
                1 -> {
                    SettingsOption(
                        text =
                            state.playingQuality
                                ?.takeIf { state.selectedQuality == null }
                                ?.let { stringResource(Res.string.video_quality_auto_playing, it.toString()) }
                                ?: stringResource(Res.string.video_quality_auto),
                        selected = state.selectedQuality == null,
                    ) {
                        playback.setQuality(null)
                        onDismiss()
                    }
                    state.qualities.forEach { height ->
                        SettingsOption(text = "${height}p", selected = state.selectedQuality == height) {
                            playback.setQuality(height)
                            onDismiss()
                        }
                    }
                }
                2 -> {
                    Speeds.forEach { speed ->
                        SettingsOption(
                            text = if (speed == 1f) stringResource(Res.string.video_speed_normal) else "${speed.toSpeedLabel()}x",
                            selected = state.speed == speed,
                        ) {
                            playback.setSpeed(speed)
                            onDismiss()
                        }
                    }
                }
                else -> {
                    SettingsRow(
                        title = stringResource(Res.string.video_quality),
                        value =
                            state.selectedQuality?.let { "${it}p" }
                                ?: state.playingQuality?.let { stringResource(Res.string.video_quality_auto_playing, it.toString()) }
                                ?: stringResource(Res.string.video_quality_auto),
                    ) { page = 1 }
                    SettingsRow(
                        title = stringResource(Res.string.video_speed),
                        value = if (state.speed == 1f) stringResource(Res.string.video_speed_normal) else "${state.speed.toSpeedLabel()}x",
                    ) { page = 2 }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { playback.setAutoplay(!state.autoplay) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.video_autoplay), style = typo().bodyLarge)
                            Text(stringResource(Res.string.video_autoplay_hint), style = typo().bodySmall)
                        }
                        Switch(checked = state.autoplay, onCheckedChange = { playback.setAutoplay(it) })
                    }
                    state.streamKind?.let {
                        Text(
                            stringResource(Res.string.video_streaming_via, it),
                            style = typo().bodySmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = typo().bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = typo().bodyMedium)
    }
}

@Composable
private fun SettingsOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp)) {
            if (selected) Icon(SimpIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = typo().bodyLarge)
    }
}

// endregion

// region Details

@Composable
private fun WatchDetails(
    state: VideoPlaybackState,
    viewModel: VideoWatchViewModel,
    onOpenChannel: (String, String?) -> Unit,
) {
    val details = state.details
    val current = state.current
    var expanded by remember(current?.videoId) { mutableStateOf(false) }
    var chip by remember(current?.videoId) { mutableIntStateOf(0) }
    val channelVideos by viewModel.channelVideos.collectAsState()
    val channelId = details?.channelId ?: current?.channelId
    val channelName = details?.channelName ?: current?.channelName

    LaunchedEffect(chip, channelId) {
        if (chip == 1 && channelId != null) viewModel.loadChannelVideos(channelId)
    }

    val related = details?.related.orEmpty()
    val list: List<VideoItem> =
        when (chip) {
            1 -> channelVideos.filter { it.videoId != current?.videoId }
            2 -> related.filter { it.channelId == null || it.channelId != channelId }
            else -> related
        }.distinctBy { it.videoId }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "title") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            ) {
                Text(
                    details?.title ?: current?.title.orEmpty(),
                    style = typo().titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val views =
                    details?.viewCount?.takeIf { it >= 0 }?.let { stringResource(Res.string.video_views_count, formatCount(it)) }
                        ?: current?.viewsText
                val meta = listOfNotNull(views, details?.uploadedText ?: current?.publishedText).joinToString("  ")
                Row(Modifier.padding(top = 4.dp)) {
                    Text(meta, style = typo().bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (!expanded && details?.description?.isNotBlank() == true) {
                        Text(
                            "  " + stringResource(Res.string.video_more),
                            style = typo().bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }

        item(key = "channel") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = channelId != null) { channelId?.let { onOpenChannel(it, channelName) } }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteImage(
                    details?.channelAvatarUrl ?: current?.channelAvatarUrl,
                    Modifier.size(38.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        channelName.orEmpty(),
                        style = typo().titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    details?.subscriberCount?.takeIf { it >= 0 }?.let {
                        Text(stringResource(Res.string.video_subscribers_count, formatCount(it)), style = typo().bodySmall)
                    }
                }
            }
        }

        item(key = "actions") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                item {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(SimpIcons.ThumbUp, null, modifier = Modifier.size(18.dp))
                            details?.likeCount?.takeIf { it >= 0 }?.let {
                                Text(formatCount(it), style = typo().labelLarge, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(SimpIcons.ThumbDown, null, modifier = Modifier.size(18.dp))
                            details?.dislikeCount?.takeIf { it >= 0 }?.let {
                                Text(formatCount(it), style = typo().labelLarge, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
                item {
                    val title = details?.title ?: current?.title.orEmpty()
                    val id = current?.videoId
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = id != null) { id?.let { shareUrl(title, "https://youtu.be/$it") } }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(SimpIcons.Share, null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.video_share), style = typo().labelLarge, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        if (expanded && details != null) {
            item(key = "description") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { expanded = false }
                        .padding(12.dp),
                ) {
                    Text(details.description, style = typo().bodyMedium)
                    if (details.chapters.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        details.chapters.forEach { chapter ->
                            Text(
                                "${formatClock(chapter.startMs)}  ${chapter.title}",
                                style = typo().bodySmall,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.playback.seekTo(chapter.startMs) }
                                        .padding(vertical = 4.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(Res.string.video_show_less),
                        style = typo().bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item(key = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                item { FilterChip(selected = chip == 0, onClick = { chip = 0 }, label = { Text(stringResource(Res.string.video_chip_all)) }) }
                if (channelId != null && !channelName.isNullOrBlank()) {
                    item {
                        FilterChip(
                            selected = chip == 1,
                            onClick = { chip = 1 },
                            label = { Text(stringResource(Res.string.video_chip_from, channelName)) },
                        )
                    }
                }
                item { FilterChip(selected = chip == 2, onClick = { chip = 2 }, label = { Text(stringResource(Res.string.video_chip_related)) }) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }

        if (details == null && state.status == VideoLoadStatus.LOADING) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                }
            }
        }

        items(list, key = { "v_" + it.videoId }) { video ->
            VideoCard(
                video = video,
                onClick = { viewModel.playFrom(video, list) },
                onChannelClick = { video.channelId?.let { onOpenChannel(it, video.channelName) } },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        item(key = "navbar") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

// endregion

private fun String.labelRes(): StringResource =
    when (this) {
        "selfpromo" -> Res.string.video_segment_selfpromo
        "interaction" -> Res.string.video_segment_interaction
        "intro" -> Res.string.video_segment_intro
        "outro" -> Res.string.video_segment_outro
        "music_offtopic" -> Res.string.video_segment_offtopic
        else -> Res.string.video_segment_sponsor
    }

private fun Float.toSpeedLabel(): String = if (this % 1f == 0f) toInt().toString() else toString().trimEnd('0')

/** 338_000 -> "5:38", 3_882_000 -> "1:04:42". */
internal fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

/** 1234 -> "1.2K", 5_600_000 -> "5.6M". */
internal fun formatCount(n: Long): String {
    fun one(
        value: Double,
        suffix: String,
    ): String {
        val tenths = kotlin.math.round(value * 10).toLong()
        val text = if (value >= 100 || tenths % 10 == 0L) value.toLong().toString() else "${tenths / 10}.${tenths % 10}"
        return text + suffix
    }
    return when {
        n >= 1_000_000_000 -> one(n / 1_000_000_000.0, "B")
        n >= 1_000_000 -> one(n / 1_000_000.0, "M")
        n >= 1_000 -> one(n / 1_000.0, "K")
        else -> n.toString()
    }
}
