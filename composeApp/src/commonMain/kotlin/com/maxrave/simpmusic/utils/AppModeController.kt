package com.maxrave.simpmusic.utils

import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The two halves of Reliefy. The app opens in the one it was last left in. */
enum class AppMode {
    MUSIC,
    VIDEO,
}

/**
 * Holds which half of the app is showing, and keeps the one setting the video half depends on in
 * step with it.
 *
 * Video mode plays every item as video ("watch the video instead of playing audio"). The user may
 * have chosen that setting for the music half themselves, so entering Video mode remembers what it
 * was and returning to Music mode puts it back, instead of leaving it switched on for good.
 */
class AppModeController(
    private val dataStoreManager: DataStoreManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writeLock = Mutex()

    private val _mode = MutableStateFlow(readSavedMode())
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    private fun readSavedMode(): AppMode =
        try {
            val saved = runBlocking { dataStoreManager.getString(MODE_KEY).first() }
            if (saved == AppMode.VIDEO.name) AppMode.VIDEO else AppMode.MUSIC
        } catch (e: Exception) {
            Logger.w(TAG, "could not read the saved mode: ${e.message}")
            AppMode.MUSIC
        }

    fun setMode(target: AppMode) {
        if (_mode.value == target) return
        // The screen flips at once; the setting follows right behind it.
        _mode.value = target
        scope.launch {
            writeLock.withLock {
                try {
                    if (target == AppMode.VIDEO) {
                        // Remember the user's own choice once. A quick Music -> Video -> Music ->
                        // Video must not overwrite it with the value Video mode itself set.
                        val remembered = dataStoreManager.getString(PREVIOUS_WATCH_KEY).first()
                        if (remembered.isNullOrEmpty()) {
                            dataStoreManager.putString(
                                PREVIOUS_WATCH_KEY,
                                dataStoreManager.watchVideoInsteadOfPlayingAudio.first(),
                            )
                        }
                        dataStoreManager.setWatchVideoInsteadOfPlayingAudio(true)
                    } else {
                        val remembered = dataStoreManager.getString(PREVIOUS_WATCH_KEY).first()
                        if (!remembered.isNullOrEmpty()) {
                            dataStoreManager.setWatchVideoInsteadOfPlayingAudio(remembered == DataStoreManager.TRUE)
                            dataStoreManager.putString(PREVIOUS_WATCH_KEY, "")
                        }
                    }
                    dataStoreManager.putString(MODE_KEY, target.name)
                    Logger.i(TAG, "mode is now ${target.name}")
                } catch (e: Exception) {
                    Logger.w(TAG, "could not save the mode: ${e.message}")
                }
            }
        }
    }

    private companion object {
        const val TAG = "AppMode"
        const val MODE_KEY = "reliefy_app_mode"
        const val PREVIOUS_WATCH_KEY = "reliefy_prev_watch_video"
    }
}
