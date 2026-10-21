package com.doomanity.cooley.video

import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Process-wide bits the ported LibreTube code needs: the application context (for the PoToken
 * WebView, the bandwidth meter and the display's HDR support) and a few constants.
 */
object VideoContext {
    /** Verbose logs from the ported PoToken code. Off in shipped builds. */
    const val DEBUG_LOGS = false

    const val YOUTUBE_URL = "https://www.youtube.com"

    lateinit var app: Context
        private set

    fun init(context: Context) {
        if (!this::app.isInitialized) app = context.applicationContext
    }

    fun supportsHdr(context: Context): Boolean =
        runCatching { ContextCompat.getDisplayOrDefault(context).isHdr }.getOrDefault(false)
}
