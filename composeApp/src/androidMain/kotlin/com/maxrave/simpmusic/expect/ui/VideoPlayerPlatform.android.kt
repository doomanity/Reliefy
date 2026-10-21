package com.maxrave.simpmusic.expect.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatActivity
import com.doomanity.cooley.video.VideoSurface
import com.maxrave.simpmusic.extension.findActivity
import org.koin.mp.KoinPlatform.getKoin

@Composable
actual fun VideoPlayerSurface(
    modifier: Modifier,
    crop: Boolean,
    small: Boolean,
) {
    VideoSurface(modifier = modifier, crop = crop, textureView = small)
}

@Composable
actual fun rememberIsInPictureInPicture(): Boolean {
    val activity = LocalContext.current.findActivity()
    var inPip by remember(activity) { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

@Composable
actual fun VideoPictureInPictureEffect(
    enabled: Boolean,
    aspectRatio: Float,
) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled, aspectRatio) {
        VideoPip.autoEnter = enabled
        VideoPip.aspectRatio = aspectRatio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { activity.setPictureInPictureParams(VideoPip.params(enabled, aspectRatio)) }
        }
        onDispose {
            VideoPip.autoEnter = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { activity.setPictureInPictureParams(VideoPip.params(false, aspectRatio)) }
            }
        }
    }
}

actual fun enterVideoPictureInPicture(aspectRatio: Float): Boolean {
    val activity: AppCompatActivity = runCatching { getKoin().get<AppCompatActivity>() }.getOrNull() ?: return false
    return VideoPip.enter(activity, aspectRatio)
}

actual fun isVideoPictureInPictureSupported(): Boolean {
    val activity: AppCompatActivity = runCatching { getKoin().get<AppCompatActivity>() }.getOrNull() ?: return false
    return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}

@Composable
actual fun VideoWindowEffect(
    fullscreen: Boolean,
    keepScreenOn: Boolean,
) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, fullscreen) {
        val window = activity.window
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            // Leaving the watch page (or switching modes): give rotation back to the system.
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(activity, keepScreenOn) {
        val window = activity.window
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/**
 * Picture in picture for the video side. On Android 12 and newer the system enters it by itself
 * (auto-enter); older versions need MainActivity to call [onUserLeaveHint].
 */
object VideoPip {
    @Volatile
    var autoEnter: Boolean = false

    @Volatile
    var aspectRatio: Float = 16f / 9f

    /** Called from MainActivity.onUserLeaveHint. */
    fun onUserLeaveHint(activity: Activity) {
        if (autoEnter && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enter(activity, aspectRatio)
    }

    fun enter(
        activity: Activity,
        aspectRatio: Float,
    ): Boolean =
        runCatching {
            if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
            activity.enterPictureInPictureMode(params(autoEnter, aspectRatio))
        }.getOrDefault(false)

    fun params(
        autoEnter: Boolean,
        aspectRatio: Float,
    ): PictureInPictureParams {
        // Android only accepts ratios between 1:2.39 and 2.39:1.
        val ratio = aspectRatio.coerceIn(0.42f, 2.38f)
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }
}
