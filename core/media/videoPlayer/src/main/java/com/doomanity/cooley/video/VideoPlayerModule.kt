package com.doomanity.cooley.video

import com.maxrave.domain.mediaservice.video.VideoPlayback
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Binds the Android video player. Desktop binds [com.maxrave.domain.mediaservice.video.UnsupportedVideoPlayback]. */
val videoPlayerModule =
    module {
        single<VideoPlayback> { VideoEngine(androidContext(), getOrNull(), getOrNull()) }
    }
