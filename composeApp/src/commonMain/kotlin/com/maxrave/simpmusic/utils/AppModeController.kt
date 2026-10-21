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
 * Holds which half of the app is showing. The app opens in the half it was last left in.
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
                    // Cooley 1.1.0 played videos through the music player and switched "watch video
                    // instead of audio" on for that. The video side has its own player now, so
                    // just put back whatever the user had, once.
                    val remembered = dataStoreManager.getString(PREVIOUS_WATCH_KEY).first()
                    if (!remembered.isNullOrEmpty()) {
                        dataStoreManager.setWatchVideoInsteadOfPlayingAudio(remembered == DataStoreManager.TRUE)
                        dataStoreManager.putString(PREVIOUS_WATCH_KEY, "")
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
