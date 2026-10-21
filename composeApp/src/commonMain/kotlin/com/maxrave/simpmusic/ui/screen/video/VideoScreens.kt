package com.maxrave.simpmusic.ui.screen.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maxrave.domain.data.model.video.VideoChannelTab
import com.maxrave.domain.data.model.video.VideoSearchFilter
import com.maxrave.simpmusic.expect.ui.PlatformBackHandler
import com.maxrave.simpmusic.ui.component.AppModeTitle
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.VideoFeedGrid
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.Settings
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.home.SettingsDestination
import com.maxrave.simpmusic.ui.navigation.destination.video.VideoChannelDestination
import com.maxrave.simpmusic.ui.navigation.destination.video.VideoPlaylistDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.VideoHomeViewModel
import com.maxrave.simpmusic.viewModel.VideoListViewModel
import com.maxrave.simpmusic.viewModel.VideoSearchViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.settings
import simpmusic.composeapp.generated.resources.video_filter_all
import simpmusic.composeapp.generated.resources.video_filter_channels
import simpmusic.composeapp.generated.resources.video_filter_live
import simpmusic.composeapp.generated.resources.video_filter_playlists
import simpmusic.composeapp.generated.resources.video_filter_videos
import simpmusic.composeapp.generated.resources.video_home_subtitle
import simpmusic.composeapp.generated.resources.video_search_hint
import simpmusic.composeapp.generated.resources.video_sign_in_hint
import simpmusic.composeapp.generated.resources.video_tab_playlists
import simpmusic.composeapp.generated.resources.video_tab_shorts
import simpmusic.composeapp.generated.resources.video_tab_videos

private val ScreenGutter = 16.dp

private fun feedPadding(innerPadding: PaddingValues) =
    PaddingValues(
        start = ScreenGutter,
        end = ScreenGutter,
        top = 4.dp,
        bottom = innerPadding.calculateBottomPadding() + 16.dp,
    )

/** The video side's home: a feed of videos, no ads. The app name up top switches back to Music. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoHomeScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    onOpenPlayer: () -> Unit,
    viewModel: VideoHomeViewModel = koinViewModel(),
) {
    val state by viewModel.feed.state.collectAsState()
    val loggedIn by viewModel.isLoggedIn.collectAsState(initial = false)
    val signInHint: @Composable () -> Unit = { SignInHint { navController.navigate(SettingsDestination) } }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets =
                TopAppBarDefaults.windowInsets.exclude(
                    TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Start),
                ),
            title = { AppModeTitle(subtitle = stringResource(Res.string.video_home_subtitle)) },
            actions = {
                RippleIconButton(imageVector = SimpIcons.Settings, tint = MaterialTheme.colorScheme.onBackground) {
                    navController.navigate(SettingsDestination)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        VideoFeedGrid(
            state = state,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel.feed::loadMore,
            onVideoClick = { video ->
                viewModel.playVideo(video, state.items)
                onOpenPlayer()
            },
            onChannelClick = { id, name -> navController.navigate(VideoChannelDestination(id, name)) },
            onPlaylistClick = { navController.navigate(VideoPlaylistDestination(it.playlistId, it.title)) },
            contentPadding = feedPadding(innerPadding),
            header = if (loggedIn) null else signInHint,
        )
    }
}

@Composable
private fun SignInHint(onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.video_sign_in_hint),
            style = typo().bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenSettings) { Text(stringResource(Res.string.settings)) }
    }
}

/** Search: type, pick a suggestion or press search, then filter the results. */
@Composable
fun VideoSearchScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    onOpenPlayer: () -> Unit,
    viewModel: VideoSearchViewModel = koinViewModel(),
) {
    val state by viewModel.feed.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val submitted by viewModel.submitted.collectAsState()
    val focusManager = LocalFocusManager.current

    // Back from results returns to the search box instead of leaving the tab.
    PlatformBackHandler(enabled = submitted != null) { viewModel.clearResults() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(stringResource(Res.string.video_search_hint)) },
            leadingIcon = { Icon(SimpIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    RippleIconButton(
                        imageVector = SimpIcons.Close,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        viewModel.onQueryChange("")
                        viewModel.clearResults()
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        viewModel.submit()
                        focusManager.clearFocus()
                    },
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = ScreenGutter, vertical = 8.dp),
        )

        if (submitted == null) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
            ) {
                items(suggestions, key = { it }) { suggestion ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.submit(suggestion)
                                focusManager.clearFocus()
                            }.padding(horizontal = ScreenGutter, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            SimpIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            suggestion,
                            style = typo().bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenGutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(VideoSearchFilter.entries.toList(), key = { it.name }) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(stringResource(f.label())) },
                    )
                }
            }
            VideoFeedGrid(
                state = state,
                onRefresh = { viewModel.submit() },
                onLoadMore = viewModel.feed::loadMore,
                onVideoClick = { video ->
                    viewModel.playVideo(video, state.items)
                    onOpenPlayer()
                },
                onChannelClick = { id, name -> navController.navigate(VideoChannelDestination(id, name)) },
                onPlaylistClick = { navController.navigate(VideoPlaylistDestination(it.playlistId, it.title)) },
                contentPadding = feedPadding(innerPadding),
            )
        }
    }
}

private fun VideoSearchFilter.label() =
    when (this) {
        VideoSearchFilter.ALL -> Res.string.video_filter_all
        VideoSearchFilter.VIDEOS -> Res.string.video_filter_videos
        VideoSearchFilter.CHANNELS -> Res.string.video_filter_channels
        VideoSearchFilter.PLAYLISTS -> Res.string.video_filter_playlists
        VideoSearchFilter.LIVE -> Res.string.video_filter_live
    }

/** A channel's videos (with Videos / Shorts / Playlists tabs), or the videos of a playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    onOpenPlayer: () -> Unit,
    title: String?,
    channelId: String? = null,
    playlistId: String? = null,
    viewModel: VideoListViewModel = koinViewModel(),
) {
    val state by viewModel.feed.state.collectAsState()
    val tab by viewModel.tab.collectAsState()
    LaunchedEffect(channelId, playlistId) {
        when {
            channelId != null -> viewModel.loadChannel(channelId)
            playlistId != null -> viewModel.loadPlaylist(playlistId)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    title.orEmpty(),
                    style = typo().titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
            },
            navigationIcon = {
                RippleIconButton(
                    imageVector = SimpIcons.ArrowBackIosNew,
                    tint = MaterialTheme.colorScheme.onBackground,
                ) { navController.navigateUp() }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        if (channelId != null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenGutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(CHANNEL_TABS, key = { it.name }) { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { viewModel.selectTab(t) },
                        label = {
                            Text(
                                stringResource(
                                    when (t) {
                                        VideoChannelTab.SHORTS -> Res.string.video_tab_shorts
                                        VideoChannelTab.PLAYLISTS -> Res.string.video_tab_playlists
                                        else -> Res.string.video_tab_videos
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        VideoFeedGrid(
            state = state,
            onRefresh = { viewModel.feed.refresh() },
            onLoadMore = viewModel.feed::loadMore,
            onVideoClick = { video ->
                viewModel.playVideo(video, state.items)
                onOpenPlayer()
            },
            onChannelClick = { id, name -> navController.navigate(VideoChannelDestination(id, name)) },
            onPlaylistClick = { navController.navigate(VideoPlaylistDestination(it.playlistId, it.title)) },
            contentPadding = feedPadding(innerPadding),
            // On a channel page every card is by that channel, so the avatar and name are noise.
            showChannelOnCards = channelId == null,
        )
    }
}

private val CHANNEL_TABS = listOf(VideoChannelTab.VIDEOS, VideoChannelTab.SHORTS, VideoChannelTab.PLAYLISTS)
