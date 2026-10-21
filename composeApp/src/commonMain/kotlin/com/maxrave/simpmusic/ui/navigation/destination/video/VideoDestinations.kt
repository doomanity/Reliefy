package com.maxrave.simpmusic.ui.navigation.destination.video

import kotlinx.serialization.Serializable

/** A channel's videos. Opened from a video card's channel name or avatar, or a channel result. */
@Serializable
data class VideoChannelDestination(
    val channelId: String,
    val name: String? = null,
)

/** The videos of a YouTube playlist. */
@Serializable
data class VideoPlaylistDestination(
    val playlistId: String,
    val title: String? = null,
)
