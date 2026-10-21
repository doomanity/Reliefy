package com.maxrave.simpmusic.expect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun VideoPlayerSurface(
    modifier: Modifier,
    crop: Boolean,
    small: Boolean,
) {
    // The video player is Android only for now.
    Box(modifier.background(Color.Black))
}

@Composable
actual fun rememberIsInPictureInPicture(): Boolean = false

@Composable
actual fun VideoPictureInPictureEffect(
    enabled: Boolean,
    aspectRatio: Float,
) {}

actual fun enterVideoPictureInPicture(aspectRatio: Float): Boolean = false

actual fun isVideoPictureInPictureSupported(): Boolean = false

@Composable
actual fun VideoWindowEffect(
    fullscreen: Boolean,
    keepScreenOn: Boolean,
) {}
