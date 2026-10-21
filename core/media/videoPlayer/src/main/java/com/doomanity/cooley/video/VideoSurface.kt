package com.doomanity.cooley.video

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState

/**
 * Draws the video player's picture, fitted inside the box with black bars, or cropped to fill it
 * when [crop] is set. [textureView] is for small boxes with rounded corners (a SurfaceView cannot
 * be clipped); the full player uses the cheaper SurfaceView.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    crop: Boolean = false,
    textureView: Boolean = false,
) {
    val engine = VideoEngine.instance
    Box(modifier.background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
        if (engine == null) return@Box
        val player = engine.player
        val presentation = rememberPresentationState(player)
        PlayerSurface(
            player = player,
            surfaceType = if (textureView) SURFACE_TYPE_TEXTURE_VIEW else SURFACE_TYPE_SURFACE_VIEW,
            modifier =
                Modifier.resizeWithContentScale(
                    contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                    sourceSizeDp = presentation.videoSizeDp,
                ),
        )
        if (presentation.coverSurface) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}
