package com.my.kizzy

import com.maxrave.domain.data.entities.SongEntity
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DiscordRPC(
    token: String,
) : KizzyRPC(token) {
    @OptIn(ExperimentalTime::class)
    suspend fun updateSong(
        currentPlaybackTimeMillis: Long,
        durationMillis: Long,
        playbackSpeed: Float = 1.0f,
        song: SongEntity,
        // Reliefy: the user can rename what Discord shows after "Listening to". Blank = default.
        appName: String = APP_NAME,
    ) = runCatching {
        val currentTime = Clock.System.now().toEpochMilliseconds()

        val adjustedPlaybackTime = (currentPlaybackTimeMillis / playbackSpeed).toLong()
        val calculatedStartTime = currentTime - adjustedPlaybackTime

        val remainingDuration = durationMillis - currentPlaybackTimeMillis
        val adjustedRemainingDuration = (remainingDuration / playbackSpeed).toLong()

        setActivity(
            name = appName.trim().take(MAX_NAME_LENGTH).ifBlank { APP_NAME },
            details = song.title,
            state = song.artistName?.joinToString(", "),
            largeImage = song.thumbnails?.let { RpcImage.ExternalImage(it) },
            smallImage = RpcImage.ExternalImage(APP_ICON),
            largeText = song.albumName,
            smallText = song.artistName?.firstOrNull(),
            buttons =
                listOf(
                    "Listen on YouTube Music" to "https://music.youtube.com/watch?v=${song.videoId}",
                    "Get Cooley" to "https://github.com/doomanity/Reliefy",
                ),
            type = Type.LISTENING,
            since = currentTime,
            startTime = calculatedStartTime,
            endTime = currentTime + adjustedRemainingDuration,
            applicationId = APPLICATION_ID,
        )
    }

    companion object {
        private const val APPLICATION_ID = "1271273225120125040"
        private const val APP_NAME: String = "Cooley"
        private const val MAX_NAME_LENGTH = 64
        private const val APP_ICON: String =
            "https://raw.githubusercontent.com/doomanity/Reliefy/main/composeApp/src/commonMain/composeResources/drawable/app_icon.png"
    }
}