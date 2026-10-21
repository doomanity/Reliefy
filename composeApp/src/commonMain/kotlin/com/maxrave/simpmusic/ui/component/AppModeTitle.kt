package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.KeyboardArrowDown
import com.maxrave.simpmusic.ui.icon.LibraryMusic
import com.maxrave.simpmusic.ui.icon.Movie
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.utils.AppMode
import com.maxrave.simpmusic.utils.AppModeController
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.app_name
import simpmusic.composeapp.generated.resources.mode_music
import simpmusic.composeapp.generated.resources.mode_switch_description
import simpmusic.composeapp.generated.resources.mode_video

/**
 * The app name at the top left of Home, which doubles as the Music / Video switch: tap it and pick
 * the half of the app to be in. [subtitle] is the small line under the name.
 */
@Composable
fun AppModeTitle(
    subtitle: String,
    modifier: Modifier = Modifier,
    controller: AppModeController = koinInject(),
) {
    val mode by controller.mode.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(start = 2.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = typo().titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Icon(
                    imageVector = SimpIcons.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.mode_switch_description),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ModeItem(AppMode.MUSIC, mode) {
                    expanded = false
                    controller.setMode(AppMode.MUSIC)
                }
                ModeItem(AppMode.VIDEO, mode) {
                    expanded = false
                    controller.setMode(AppMode.VIDEO)
                }
            }
        }
        Text(
            text = subtitle,
            style = typo().bodySmall,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun ModeItem(
    target: AppMode,
    current: AppMode,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(if (target == AppMode.MUSIC) Res.string.mode_music else Res.string.mode_video),
                style = typo().titleSmall,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = if (target == AppMode.MUSIC) SimpIcons.LibraryMusic else SimpIcons.Movie,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (target == current) {
                Icon(imageVector = SimpIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        onClick = onClick,
    )
}
