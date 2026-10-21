package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The video side's picture. [small] is for the mini player: its rounded corners need a surface
 * that can be clipped. [crop] fills the box instead of showing black bars.
 */
@Composable
expect fun VideoPlayerSurface(
    modifier: Modifier = Modifier,
    crop: Boolean = false,
    small: Boolean = false,
)

/** True while the app is shrunk to a picture-in-picture window. */
@Composable
expect fun rememberIsInPictureInPicture(): Boolean

/** While [enabled], leaving the app (home button, gesture) shrinks the video to a floating window. */
@Composable
expect fun VideoPictureInPictureEffect(
    enabled: Boolean,
    aspectRatio: Float,
)

/** Shrinks the video to a floating window right now. False when the device cannot. */
expect fun enterVideoPictureInPicture(aspectRatio: Float): Boolean

expect fun isVideoPictureInPictureSupported(): Boolean

/**
 * The watch page's screen handling: landscape with the system bars hidden in [fullscreen],
 * portrait otherwise, and the screen kept on while [keepScreenOn].
 */
@Composable
expect fun VideoWindowEffect(
    fullscreen: Boolean,
    keepScreenOn: Boolean,
)
