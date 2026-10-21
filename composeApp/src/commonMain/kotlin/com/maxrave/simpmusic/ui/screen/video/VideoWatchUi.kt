package com.maxrave.simpmusic.ui.screen.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the watch page is open (or shrunk to the mini player) and in fullscreen. Kept outside
 * composition on purpose: rotating to fullscreen recreates the activity, and the page must come
 * back the way it was.
 */
object VideoWatchUi {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _fullscreen = MutableStateFlow(false)
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()

    fun open() {
        _open.value = true
    }

    /** Shrinks the watch page to the mini player. */
    fun minimize() {
        _fullscreen.value = false
        _open.value = false
    }

    fun setFullscreen(fullscreen: Boolean) {
        _fullscreen.value = fullscreen
    }
}
