package com.doomanity.cooley.video

import android.content.ComponentName
import android.content.Context
import android.util.Base64
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.StreamRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.doomanity.cooley.video.sabr.SabrMediaSource
import com.doomanity.cooley.video.sabr.manifest.SabrManifest
import com.doomanity.cooley.video.stream.CommentsFetcher
import com.doomanity.cooley.video.stream.DashHelper
import com.doomanity.cooley.video.stream.LoadedVideo
import com.doomanity.cooley.video.stream.PlainClientLoader
import com.doomanity.cooley.video.stream.PlainStream
import com.doomanity.cooley.video.stream.StreamFetcher
import com.doomanity.cooley.video.stream.Streams
import com.doomanity.cooley.video.stream.VideoExtras
import com.google.common.util.concurrent.ListenableFuture
import com.maxrave.domain.data.model.video.VideoItem
import com.maxrave.domain.mediaservice.video.SkipSegment
import com.maxrave.domain.mediaservice.video.VideoCommentsPage
import com.maxrave.domain.mediaservice.video.VideoLoadStatus
import com.maxrave.domain.mediaservice.video.VideoPlayback
import com.maxrave.domain.mediaservice.video.VideoPlaybackState
import com.maxrave.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

private const val TAG = "VideoEngine"

/** How a video's media is being fetched. Tried in this order; the next one is the fallback. */
private enum class SourceKind(
    val label: String,
) {
    SABR("SABR"),
    DASH("DASH"),
    HLS("HLS"),
}

/**
 * The video side's player, built the LibreTube way: streams come from LibreTube's NewPipe
 * Extractor fork with a BotGuard PoToken, and play over SABR (YouTube's own streaming protocol),
 * falling back to a DASH manifest built from the stream list, then to HLS.
 *
 * One ExoPlayer for the whole app. [VideoPlaybackService] wraps it in a media session so the video
 * keeps playing with the screen off and shows its own notification, apart from the music player.
 * Everything here runs on the main thread except the network calls.
 */
@OptIn(UnstableApi::class)
class VideoEngine(
    context: Context,
    /** The music side's stream loader, used as a backup when LibreTube's way cannot load a video. */
    private val streamRepository: StreamRepository? = null,
    private val dataStoreManager: DataStoreManager? = null,
) : VideoPlayback {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val isSupported: Boolean = true

    private val _state = MutableStateFlow(VideoPlaybackState())
    override val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _position.asStateFlow()

    private val _buffered = MutableStateFlow(0L)
    override val bufferedMs: StateFlow<Long> = _buffered.asStateFlow()

    private val _skipEvents = MutableSharedFlow<SkipSegment>(extraBufferCapacity = 4)
    override val skipEvents: SharedFlow<SkipSegment> = _skipEvents.asSharedFlow()

    private var loadJob: Job? = null
    private var tickJob: Job? = null
    private var loaded: LoadedVideo? = null
    private val triedKinds = mutableSetOf<SourceKind>()
    private val skipped = mutableSetOf<SkipSegment>()

    /** Videos already played in this run, so autoplay never loops back to one. */
    private val played = LinkedHashSet<String>()

    /** The height the user picked; applied again to each new video. Null means automatic. */
    private var preferredQuality: Int? = null
    private var qualityAppliedFor: String? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null

    init {
        VideoContext.init(context)
        instance = this
    }

    /** The real player, for the video surface. Created on first use, on the main thread. */
    val player: ExoPlayer by lazy { buildPlayer() }

    /** What the media session sees: next and previous go through this engine's queue. */
    val sessionPlayer: Player by lazy {
        object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands =
                super
                    .getAvailableCommands()
                    .buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

            override fun seekToNext() = next()

            override fun seekToNextMediaItem() = next()

            override fun seekToPrevious() = previous()

            override fun seekToPreviousMediaItem() = previous()
        }
    }

    private fun buildPlayer(): ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                // Taking audio focus is what pauses the music side when a video starts.
                true,
            ).setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.addListener(listener) }

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> _state.update { it.copy(isBuffering = true) }
                    Player.STATE_READY ->
                        _state.update {
                            it.copy(
                                isBuffering = false,
                                isEnded = false,
                                status = VideoLoadStatus.READY,
                                durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                            )
                        }
                    Player.STATE_ENDED -> {
                        _state.update { it.copy(isBuffering = false, isEnded = true) }
                        if (_state.value.autoplay) next()
                    }
                    else -> _state.update { it.copy(isBuffering = false) }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _state.update { it.copy(speed = playbackParameters.speed) }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    _state.update { it.copy(videoAspectRatio = ratio) }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val heights =
                    tracks.groups
                        .filter { it.type == C.TRACK_TYPE_VIDEO }
                        .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group.getTrackFormat(it) } }
                        .mapNotNull { it.qualityValue() }
                        .distinct()
                        .sortedDescending()
                val playing =
                    tracks.groups
                        .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                        ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) } }
                        ?.qualityValue()
                _state.update { it.copy(qualities = heights, playingQuality = playing) }
                val id = _state.value.current?.videoId
                if (heights.isNotEmpty() && id != null && qualityAppliedFor != id) {
                    qualityAppliedFor = id
                    preferredQuality?.let { wanted ->
                        // the closest height not above the one picked, or the smallest there is
                        val pick = heights.firstOrNull { it <= wanted } ?: heights.last()
                        applyQuality(pick)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Logger.w(TAG, "player error on ${_state.value.streamKind}: ${error.errorCodeName} ${error.message}")
                val video = loaded
                if (video != null && setSource(video, startPositionMs = player.currentPosition)) {
                    Logger.i(TAG, "falling back to ${_state.value.streamKind}")
                    return
                }
                val id = _state.value.current?.videoId
                if (id != null && tryBackup(id, player.currentPosition, error.localizedMessage ?: error.errorCodeName)) return
                _state.update {
                    it.copy(
                        status = VideoLoadStatus.ERROR,
                        error = error.localizedMessage ?: error.errorCodeName,
                        isBuffering = false,
                        isPlaying = false,
                    )
                }
            }
        }

    // region Commands

    override fun play(
        video: VideoItem,
        queue: List<VideoItem>,
    ) {
        val rest = queue.filter { it.videoId != video.videoId && !it.isUpcoming }
        _state.update { it.copy(queue = listOf(video) + rest, queueIndex = 0) }
        load(video)
    }

    override fun playAt(index: Int) {
        val video = _state.value.queue.getOrNull(index) ?: return
        _state.update { it.copy(queueIndex = index) }
        load(video)
    }

    override fun togglePlayPause() {
        if (player.isPlaying) pause() else resume()
    }

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        val s = _state.value
        when {
            s.status == VideoLoadStatus.ERROR -> retry()
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            player.playbackState == Player.STATE_IDLE && loaded != null -> {
                player.prepare()
                player.play()
            }
            else -> player.play()
        }
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        _position.value = positionMs.coerceAtLeast(0)
    }

    override fun next() {
        val s = _state.value
        if (s.queueIndex + 1 in s.queue.indices) {
            playAt(s.queueIndex + 1)
            return
        }
        // Like YouTube: when the list runs out, carry on with a related video not played yet.
        val related =
            s.details
                ?.related
                ?.firstOrNull { !it.isShort && !it.isLive && !it.isUpcoming && it.videoId !in played }
                ?: return
        _state.update { it.copy(queue = it.queue + related, queueIndex = it.queue.size) }
        load(related)
    }

    override fun previous() {
        val s = _state.value
        if (player.currentPosition > 3_000 || s.queueIndex <= 0) {
            seekTo(0)
        } else {
            playAt(s.queueIndex - 1)
        }
    }

    override fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    override fun setQuality(height: Int?) {
        preferredQuality = height
        applyQuality(height)
        _state.update { it.copy(selectedQuality = height) }
    }

    override fun setAutoplay(enabled: Boolean) {
        _state.update { it.copy(autoplay = enabled) }
    }

    override fun retry() {
        val video = _state.value.current ?: return
        load(video, resumeAtMs = _position.value)
    }

    override fun stop() {
        loadJob?.cancel()
        tickJob?.cancel()
        loaded = null
        player.stop()
        player.clearMediaItems()
        _position.value = 0
        _buffered.value = 0
        _state.value = VideoPlaybackState(speed = _state.value.speed, autoplay = _state.value.autoplay, selectedQuality = preferredQuality)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private var upNext: Pair<String, List<VideoItem>>? = null

    override fun offerUpNext(
        videoId: String,
        videos: List<VideoItem>,
    ) {
        upNext = videoId to videos
        _state.update { s ->
            val d = s.details
            if (d != null && d.videoId == videoId && d.related.isEmpty()) s.copy(details = d.copy(related = videos)) else s
        }
    }

    override suspend fun comments(
        videoId: String,
        page: String?,
    ): Result<VideoCommentsPage> =
        try {
            Result.success(CommentsFetcher.load(videoId, page))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.w(TAG, "comments for $videoId failed: ${e.message}")
            Result.failure(e)
        }

    // endregion

    private fun load(
        video: VideoItem,
        resumeAtMs: Long = 0,
    ) {
        loadJob?.cancel()
        loaded = null
        triedKinds.clear()
        backupStage = 0
        backupReason = null
        skipped.clear()
        qualityAppliedFor = null
        played += video.videoId
        _position.value = resumeAtMs
        _buffered.value = 0
        _state.update {
            it.copy(
                current = video,
                details = it.details?.takeIf { d -> d.videoId == video.videoId },
                status = VideoLoadStatus.LOADING,
                error = null,
                isEnded = false,
                isPlaying = false,
                isBuffering = true,
                durationMs = 0,
                qualities = emptyList(),
                playingQuality = null,
                segments = emptyList(),
                streamKind = null,
                videoAspectRatio = 16f / 9f,
            )
        }
        player.stop()
        player.clearMediaItems()
        ensureSession()
        startTicking()

        loadJob =
            scope.launch {
                val result =
                    try {
                        StreamFetcher.load(video.videoId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Logger.e(TAG, "could not load ${video.videoId}: ${e.message}")
                        if (tryBackup(video.videoId, resumeAtMs, e.message ?: e::class.simpleName.orEmpty())) return@launch
                        _state.update {
                            it.copy(
                                status = VideoLoadStatus.ERROR,
                                error = e.message ?: e::class.simpleName,
                                isBuffering = false,
                            )
                        }
                        return@launch
                    }
                loaded = result
                val offered = upNext?.takeIf { it.first == video.videoId }?.second.orEmpty()
                val details =
                    if (result.details.related.isEmpty() && offered.isNotEmpty()) result.details.copy(related = offered) else result.details
                _state.update {
                    it.copy(
                        details = details,
                        durationMs = result.details.durationSeconds * 1000,
                    )
                }
                launch {
                    val dislikes = VideoExtras.dislikes(video.videoId)
                    if (dislikes >= 0) {
                        _state.update { s ->
                            val d = s.details
                            if (d != null && d.videoId == video.videoId) s.copy(details = d.copy(dislikeCount = dislikes)) else s
                        }
                    }
                }
                if (!result.details.isLive) {
                    launch {
                        val segments = VideoExtras.sponsorSegments(video.videoId)
                        _state.update { s -> if (s.current?.videoId == video.videoId) s.copy(segments = segments) else s }
                    }
                }
                if (!setSource(result, startPositionMs = resumeAtMs)) {
                    if (tryBackup(video.videoId, resumeAtMs, "No playable stream found")) return@launch
                    _state.update {
                        it.copy(status = VideoLoadStatus.ERROR, error = "No playable stream found", isBuffering = false)
                    }
                }
            }
    }

    /** How many backup loaders have been tried for the current video (see [tryBackup]). */
    private var backupStage = 0

    /** Why the main loader failed, shown if every backup fails too. */
    private var backupReason: String? = null

    /**
     * Last resorts, in order, each tried once per video:
     *  1. YouTube's own phone apps (iPhone HLS, then an Android mp4), see [PlainClientLoader].
     *     LibreTube's client is refused every "made for kids" video; these are not.
     *  2. The music side's loader (other clients, other extractors), as one video stream plus one
     *     audio stream. Fixes many region locked videos.
     * Returns false when there is nothing left to try; [reason] is shown then.
     */
    private fun tryBackup(
        videoId: String,
        startPositionMs: Long,
        reason: String,
    ): Boolean {
        if (backupReason == null) backupReason = reason
        while (backupStage < 2) {
            val stage = backupStage++
            when (stage) {
                0 -> {
                    startPlainBackup(videoId, startPositionMs)
                    return true
                }
                1 -> if (streamRepository != null && dataStoreManager != null) {
                    startMusicBackup(videoId, startPositionMs)
                    return true
                }
            }
        }
        return false
    }

    private fun failBackups() {
        _state.update { it.copy(status = VideoLoadStatus.ERROR, error = backupReason, isBuffering = false) }
    }

    private fun backupMetadata(): MediaMetadata {
        val current = _state.value
        return MediaMetadata
            .Builder()
            .setTitle(current.details?.title ?: current.current?.title)
            .setArtist(current.details?.channelName ?: current.current?.channelName)
            .setArtworkUri((current.details?.thumbnailUrl ?: current.current?.thumbnailUrl)?.toUri())
            .build()
    }

    private fun startPlainBackup(
        videoId: String,
        startPositionMs: Long,
    ) {
        Logger.i(TAG, "trying the phone app clients for $videoId ($backupReason)")
        _state.update { it.copy(status = VideoLoadStatus.LOADING, error = null, isBuffering = true, streamKind = "Backup") }
        loadJob =
            scope.launch {
                val result = PlainClientLoader.load(videoId)
                if (result == null) {
                    if (!tryBackup(videoId, startPositionMs, backupReason.orEmpty())) failBackups()
                    return@launch
                }
                // The main loader may have failed before reading anything; fill the watch page.
                result.details?.let { found ->
                    _state.update { s ->
                        if (s.current?.videoId != videoId) return@update s
                        val old = s.details
                        val merged =
                            if (old == null || old.videoId != videoId) {
                                found.copy(related = upNext?.takeIf { it.first == videoId }?.second.orEmpty())
                            } else {
                                old
                            }
                        s.copy(details = merged, durationMs = if (s.durationMs > 0) s.durationMs else found.durationSeconds * 1000)
                    }
                    launch {
                        val dislikes = VideoExtras.dislikes(videoId)
                        if (dislikes >= 0) {
                            _state.update { s ->
                                val d = s.details
                                if (d != null && d.videoId == videoId) s.copy(details = d.copy(dislikeCount = dislikes)) else s
                            }
                        }
                    }
                }
                val stream = result.stream
                val http = DefaultHttpDataSource.Factory().setUserAgent(stream.userAgent).setAllowCrossProtocolRedirects(true)
                val metadata = backupMetadata()
                fun item(
                    url: String,
                    mime: String? = null,
                ) = MediaItem
                    .Builder()
                    .setUri(url)
                    .apply { if (mime != null) setMimeType(mime) }
                    .setMediaId(videoId)
                    .setMediaMetadata(metadata)
                    .build()
                val source =
                    when (stream) {
                        is PlainStream.Hls -> HlsMediaSource.Factory(http).createMediaSource(item(stream.url, MimeTypes.APPLICATION_M3U8))
                        is PlainStream.Files -> {
                            val factory = ProgressiveMediaSource.Factory(http)
                            val parts = listOfNotNull(stream.videoUrl, stream.audioUrl).map { factory.createMediaSource(item(it)) }
                            if (parts.size == 1) parts[0] else MergingMediaSource(*parts.toTypedArray())
                        }
                    }
                _state.update { it.copy(streamKind = "${stream.clientLabel} app") }
                player.setMediaSource(source)
                player.setPlaybackSpeed(_state.value.speed)
                if (startPositionMs > 0) player.seekTo(startPositionMs)
                player.prepare()
                player.playWhenReady = true
            }
    }

    private fun startMusicBackup(
        videoId: String,
        startPositionMs: Long,
    ) {
        val repo = streamRepository ?: return
        val store = dataStoreManager ?: return
        Logger.i(TAG, "trying the music loader for $videoId ($backupReason)")
        _state.update { it.copy(status = VideoLoadStatus.LOADING, error = null, isBuffering = true, streamKind = "Backup") }
        loadJob =
            scope.launch {
                val (videoUrl, audioUrl) =
                    withContext(Dispatchers.IO) {
                        val v = runCatching { withTimeout(40_000) { repo.getStream(store, videoId, false, true).first() } }.getOrNull()
                        val a = runCatching { withTimeout(40_000) { repo.getStream(store, videoId, false, false).first() } }.getOrNull()
                        v to a
                    }
                if (videoUrl.isNullOrBlank() && audioUrl.isNullOrBlank()) {
                    failBackups()
                    return@launch
                }
                val metadata = backupMetadata()
                val factory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
                fun source(url: String) =
                    factory.createMediaSource(MediaItem.Builder().setUri(url).setMediaId(videoId).setMediaMetadata(metadata).build())
                val sources = listOfNotNull(videoUrl?.takeIf { it.isNotBlank() }, audioUrl?.takeIf { it.isNotBlank() }).map(::source)
                _state.update { it.copy(streamKind = "Music loader") }
                player.setMediaSource(if (sources.size == 1) sources[0] else MergingMediaSource(*sources.toTypedArray()))
                player.setPlaybackSpeed(_state.value.speed)
                if (startPositionMs > 0) player.seekTo(startPositionMs)
                player.prepare()
                player.playWhenReady = true
            }
    }

    /**
     * Hands the player the next way of loading [video] not tried yet. Returns false when every
     * way has been tried.
     */
    private fun setSource(
        video: LoadedVideo,
        startPositionMs: Long = 0,
    ): Boolean {
        val streams = video.streams
        val kind =
            SourceKind.entries.firstOrNull { it !in triedKinds && canUse(it, streams) }
                ?: return false
        triedKinds += kind
        val details = video.details
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(details.title)
                .setArtist(details.channelName)
                .setArtworkUri(details.thumbnailUrl?.toUri())
                .build()
        fun item(
            uri: String,
            mime: String,
        ) = MediaItem
            .Builder()
            .setUri(uri)
            .setMimeType(mime)
            .setMediaId(details.videoId)
            .setMediaMetadata(metadata)
            .build()

        try {
            when (kind) {
                SourceKind.SABR -> {
                    val manifest = SabrManifest(details.videoId, streams)
                    val source =
                        SabrMediaSource
                            .Factory(manifest)
                            .createMediaSource(item(streams.serverAbrStreamingUrl!!, "application/vnd.yt-ump"))
                    player.setMediaSource(source)
                }
                SourceKind.DASH -> {
                    val manifest =
                        DashHelper.createManifest(
                            streams.copy(
                                videoStreams = streams.videoStreams.filter { it.url?.startsWith("sabr://") != true },
                                audioStreams = streams.audioStreams.filter { it.url?.startsWith("sabr://") != true },
                            ),
                            VideoContext.supportsHdr(context),
                        )
                    val encoded = Base64.encodeToString(manifest.toByteArray(), Base64.NO_WRAP)
                    player.setMediaItem(item("data:application/dash+xml;charset=utf-8;base64,$encoded", MimeTypes.APPLICATION_MPD))
                }
                SourceKind.HLS -> {
                    val source =
                        HlsMediaSource
                            .Factory(DefaultDataSource.Factory(context))
                            .createMediaSource(item(streams.hls!!, MimeTypes.APPLICATION_M3U8))
                    player.setMediaSource(source)
                }
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "${kind.label} source failed to build: ${e.message}")
            return setSource(video, startPositionMs)
        }
        _state.update { it.copy(streamKind = kind.label, status = VideoLoadStatus.LOADING, error = null) }
        player.setPlaybackSpeed(_state.value.speed)
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.prepare()
        player.playWhenReady = true
        Logger.i(TAG, "playing ${details.videoId} over ${kind.label}")
        return true
    }

    private fun canUse(
        kind: SourceKind,
        streams: Streams,
    ): Boolean =
        when (kind) {
            SourceKind.SABR ->
                !streams.isLive && streams.serverAbrStreamingUrl != null && streams.videoPlaybackUstreamerConfig != null &&
                    streams.videoStreams.isNotEmpty() && streams.audioStreams.isNotEmpty()
            SourceKind.DASH ->
                !streams.isLive &&
                    streams.videoStreams.any { it.url?.startsWith("sabr://") == false && (it.indexEnd ?: 0) > 0 } &&
                    streams.audioStreams.any { it.url?.startsWith("sabr://") == false && (it.indexEnd ?: 0) > 0 }
            SourceKind.HLS -> streams.hls != null
        }

    private fun applyQuality(height: Int?) {
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (height != null) {
            val match =
                player.currentTracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .firstNotNullOfOrNull { group ->
                        (0 until group.length)
                            .firstOrNull { group.isTrackSupported(it) && group.getTrackFormat(it).qualityValue() == height }
                            ?.let { index -> group to index }
                    }
            if (match != null) builder.addOverride(TrackSelectionOverride(match.first.mediaTrackGroup, match.second))
        }
        player.trackSelectionParameters = builder.build()
    }

    /** Position updates and SponsorBlock skipping, a few times a second while a video is loaded. */
    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob =
            scope.launch {
                while (isActive) {
                    if (player.mediaItemCount > 0) {
                        val position = player.currentPosition
                        _position.value = position
                        _buffered.value = player.bufferedPosition
                        val segment =
                            _state.value.segments.firstOrNull {
                                position >= it.startMs && position < it.endMs - 500 && it !in skipped
                            }
                        if (segment != null && player.playbackState == Player.STATE_READY) {
                            skipped += segment
                            player.seekTo(segment.endMs)
                            _skipEvents.tryEmit(segment)
                        }
                    }
                    delay(250)
                }
            }
    }

    /**
     * Connects a controller to [VideoPlaybackService]. Media3 creates the service (and its
     * media session) for the first controller and keeps it in the foreground while playing.
     */
    private fun ensureSession() {
        if (controllerFuture != null) return
        controllerFuture =
            runCatching {
                MediaController
                    .Builder(context, SessionToken(context, ComponentName(context, VideoPlaybackService::class.java)))
                    .buildAsync()
            }.onFailure { Logger.w(TAG, "could not start the video session: ${it.message}") }
                .getOrNull()
    }

    companion object {
        @Volatile
        var instance: VideoEngine? = null
            private set
    }
}

/** The number a quality is shown by: the short side, so a vertical 1080x1920 short is "1080p". */
private fun androidx.media3.common.Format.qualityValue(): Int? {
    val w = width
    val h = height
    return when {
        w > 0 && h > 0 -> min(w, h)
        h > 0 -> h
        else -> null
    }
}
