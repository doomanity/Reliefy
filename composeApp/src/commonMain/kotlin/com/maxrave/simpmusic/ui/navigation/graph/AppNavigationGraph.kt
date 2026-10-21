package com.maxrave.simpmusic.ui.navigation.graph

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.WrappedDestination
import com.maxrave.simpmusic.ui.theme.ForceDarkContent
import com.maxrave.simpmusic.ui.theme.LocalReliefyStyle
import com.maxrave.simpmusic.ui.theme.navEnter
import com.maxrave.simpmusic.ui.theme.navExit
import com.maxrave.simpmusic.ui.theme.navPopEnter
import com.maxrave.simpmusic.ui.theme.navPopExit
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import com.maxrave.simpmusic.ui.navigation.destination.player.FullscreenDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination
import com.maxrave.simpmusic.ui.screen.home.HomeScreen
import com.maxrave.simpmusic.ui.screen.home.analytics.AnalyticsScreen
import com.maxrave.simpmusic.ui.screen.home.wrapped.WrappedScreen
import com.maxrave.simpmusic.ui.screen.library.LibraryScreen
import com.maxrave.simpmusic.ui.screen.library.MixForYouScreen
import com.maxrave.simpmusic.ui.screen.other.SearchScreen
import com.maxrave.simpmusic.ui.screen.player.FullscreenPlayer
import com.maxrave.simpmusic.ui.navigation.destination.video.VideoChannelDestination
import com.maxrave.simpmusic.ui.navigation.destination.video.VideoPlaylistDestination
import com.maxrave.simpmusic.ui.screen.video.VideoHomeScreen
import com.maxrave.simpmusic.ui.screen.video.VideoListScreen
import com.maxrave.simpmusic.ui.screen.video.VideoSearchScreen
import com.maxrave.simpmusic.ui.screen.video.VideoWatchUi
import com.maxrave.simpmusic.utils.AppMode
import com.maxrave.simpmusic.utils.AppModeController
import org.koin.compose.koinInject

@Composable
@ExperimentalMaterial3Api
@ExperimentalFoundationApi
fun AppNavigationGraph(
    innerPadding: PaddingValues,
    navController: NavHostController,
    startDestination: Any = HomeDestination,
    hideNavBar: () -> Unit = { },
    showNavBar: (shouldShowNowPlayingSheet: Boolean) -> Unit = { },
    showNowPlayingSheet: () -> Unit = {},
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    // Reliefy: screen transitions follow Customize > Animation feel.
    val motion = LocalReliefyStyle.current.motion
    // Music or Video. The tabs are the same routes in both halves; this decides what they draw.
    val appModeController: AppModeController = koinInject()
    val appMode by appModeController.mode.collectAsState()
    val isVideoMode = appMode == AppMode.VIDEO
    NavHost(
        navController,
        startDestination = startDestination,
        enterTransition = { motion.navEnter() },
        exitTransition = { motion.navExit() },
        popEnterTransition = { motion.navPopEnter() },
        popExitTransition = { motion.navPopExit() },
    ) {
        // Bottom bar destinations
        composable<HomeDestination> {
            if (isVideoMode) {
                VideoHomeScreen(
                    navController = navController,
                    innerPadding = innerPadding,
                    onOpenPlayer = { VideoWatchUi.open() },
                )
            } else {
                HomeScreen(
                    onScrolling = onScrolling,
                    navController = navController,
                )
            }
        }
        composable<SearchDestination> {
            if (isVideoMode) {
                VideoSearchScreen(
                    navController = navController,
                    innerPadding = innerPadding,
                    onOpenPlayer = { VideoWatchUi.open() },
                )
            } else {
                SearchScreen(
                    navController = navController,
                )
            }
        }
        // Video half: a channel's videos, and a playlist's videos.
        composable<VideoChannelDestination> { entry ->
            val route = entry.toRoute<VideoChannelDestination>()
            VideoListScreen(
                navController = navController,
                innerPadding = innerPadding,
                onOpenPlayer = { VideoWatchUi.open() },
                title = route.name,
                channelId = route.channelId,
            )
        }
        composable<VideoPlaylistDestination> { entry ->
            val route = entry.toRoute<VideoPlaylistDestination>()
            VideoListScreen(
                navController = navController,
                innerPadding = innerPadding,
                onOpenPlayer = { VideoWatchUi.open() },
                title = route.title,
                playlistId = route.playlistId,
            )
        }
        composable<LibraryDestination> {
            LibraryScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
            )
        }
        // Only reachable as a tab while signed in to YouTube
        composable<MixForYouDestination> {
            MixForYouScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
            )
        }
        // Only reachable as a tab while local tracking is enabled.
        // ForceDarkContent for the same reason as album/playlist/artist: the page background comes
        // from the artwork via toImmersiveBackground(), which always lands dark, so the light
        // theme's dark-on-light text and icons would be unreadable on it.
        composable<AnalyticsDestination> {
            ForceDarkContent {
                AnalyticsScreen(
                    navController = navController,
                    innerPadding = innerPadding,
                )
            }
        }
        // Reached only from the Analytics screen's entry banner, so it inherits that screen's
        // gate on local tracking. ForceDarkContent for a different reason than Analytics: the reel
        // is drawn on its own near-black ground whatever the user's theme is, because it is an
        // event and because every card is also a share image that has to survive leaving the app.
        composable<WrappedDestination> {
            ForceDarkContent {
                WrappedScreen(
                    navController = navController,
                    hideNavBar = hideNavBar,
                    // Deliberately not the fullscreen player's `showNavBar(true)` + open sheet:
                    // leaving the reel goes back to Analytics, and raising the Now Playing sheet
                    // over it would be a screen the user never asked for.
                    showNavBar = { showNavBar(false) },
                )
            }
        }
        composable<FullscreenDestination> {
            ForceDarkContent {
                FullscreenPlayer(
                    navController,
                    hideNavBar = hideNavBar,
                    showNavBar = {
                        showNavBar.invoke(true)
                        showNowPlayingSheet.invoke()
                    },
                )
            }
        }
        // Home screen graph
        homeScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
        )
        // Library screen graph
        libraryScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
        )
        // List screen graph
        listScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
        )
        // Login screen graph
        loginScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomBar = hideNavBar,
            showBottomBar = {
                showNavBar(false)
            },
        )
    }
}