package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.materialkolor.rememberDynamicColorScheme
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.simpmusic.expect.ui.isWallpaperDynamicColorSupported
import com.maxrave.simpmusic.expect.ui.layerBackdrop
import com.maxrave.simpmusic.expect.ui.rememberBackdrop
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.LiquidGlassIconButton
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.Favorite
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.RestartAlt
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.SkipNext
import com.maxrave.simpmusic.ui.icon.SkipPrevious
import com.maxrave.simpmusic.ui.theme.AccentSwatches
import com.maxrave.simpmusic.ui.theme.BackgroundStyle
import com.maxrave.simpmusic.ui.theme.ColorStyle
import com.maxrave.simpmusic.ui.theme.CornerStyle
import com.maxrave.simpmusic.ui.theme.LocalReliefyStyle
import com.maxrave.simpmusic.ui.theme.MotionStyle
import com.maxrave.simpmusic.ui.theme.ReliefyStyleKeys
import com.maxrave.simpmusic.ui.theme.ThemePreset
import com.maxrave.simpmusic.ui.theme.ThemePresets
import com.maxrave.simpmusic.ui.theme.parseThemeColorHex
import com.maxrave.simpmusic.ui.theme.roundedShape
import com.maxrave.simpmusic.ui.theme.seed
import com.maxrave.simpmusic.ui.theme.typo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.*

/** Same phone-width column as the other settings pages, centred in a wide window. */
private const val CUSTOMIZE_MAX_WIDTH_DP = 560

private val crossfadeChoices = listOf(DataStoreManager.CROSSFADE_DURATION_AUTO, 2000, 3000, 5000, 8000, 12000)

/**
 * Reliefy's Customize page. Every change is written straight to DataStore and the whole app
 * (this page included) re-themes from it, so the page is its own live preview.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    dataStoreManager: DataStoreManager = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val style = LocalReliefyStyle.current
    val presetId by dataStoreManager.getString(ReliefyStyleKeys.PRESET).collectAsStateWithLifecycle(null)
    val colorSource by dataStoreManager.themeColorSource.collectAsStateWithLifecycle(DataStoreManager.THEME_COLOR_DEFAULT)
    val customHex by dataStoreManager.customThemeColor.collectAsStateWithLifecycle(DataStoreManager.DEFAULT_THEME_COLOR_HEX)
    val crossfadeEnabled by dataStoreManager.crossfadeEnabled.collectAsStateWithLifecycle(DataStoreManager.TRUE)
    val crossfadeDuration by dataStoreManager.crossfadeDuration.collectAsStateWithLifecycle(5000)

    // The colour the current scheme is seeded from, for the small palette previews on this page.
    val currentSeed: Color =
        when (colorSource) {
            DataStoreManager.THEME_COLOR_CUSTOM -> parseThemeColorHex(customHex) ?: seed
            DataStoreManager.THEME_COLOR_WALLPAPER -> MaterialTheme.colorScheme.primary
            else -> seed
        }

    // Changing any single part turns the selection into "Your mix" instead of a named preset.
    fun setPart(
        key: String,
        value: String,
    ) {
        scope.launch {
            dataStoreManager.putString(key, value)
            dataStoreManager.putString(ReliefyStyleKeys.PRESET, "custom")
        }
    }

    fun applyPreset(preset: ThemePreset) {
        scope.launch {
            dataStoreManager.putString(ReliefyStyleKeys.COLOR_STYLE, preset.style.colorStyle.key)
            dataStoreManager.putString(ReliefyStyleKeys.CORNERS, preset.style.corners.key)
            dataStoreManager.putString(ReliefyStyleKeys.MOTION, preset.style.motion.key)
            dataStoreManager.putString(ReliefyStyleKeys.BACKGROUND, preset.style.background.key)
            if (preset.seedHex == null) {
                dataStoreManager.setThemeColorSource(DataStoreManager.THEME_COLOR_DEFAULT)
            } else {
                dataStoreManager.setCustomThemeColor(preset.seedHex)
                dataStoreManager.setThemeColorSource(DataStoreManager.THEME_COLOR_CUSTOM)
            }
            dataStoreManager.putString(ReliefyStyleKeys.PRESET, preset.id)
        }
    }

    fun setAccent(source: String, hex: String?) {
        scope.launch {
            if (hex != null) dataStoreManager.setCustomThemeColor(hex)
            dataStoreManager.setThemeColorSource(source)
            dataStoreManager.putString(ReliefyStyleKeys.PRESET, "custom")
        }
    }

    val backdrop = rememberBackdrop(MaterialTheme.colorScheme.background)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentWidth = minOf(maxWidth.value, CUSTOMIZE_MAX_WIDTH_DP.toFloat()).dp

        // Backdrop source for the glass back button: the page colour with a soft accent glow.
        val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Brush.verticalGradient(listOf(glow, Color.Transparent))),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 8.dp)
                        .height(48.dp)
                        .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(48.dp))
                Text(
                    text = stringResource(Res.string.customize),
                    style = typo().titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(48.dp))
            }

            Column(
                modifier =
                    Modifier
                        .width(contentWidth)
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                StylePreviewCard()

                // ===== Themes =====
                Section(
                    title = stringResource(Res.string.customize_themes),
                    hint = stringResource(Res.string.customize_themes_hint),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ThemePresets.forEach { preset ->
                            PresetCard(
                                preset = preset,
                                selected = presetId == preset.id,
                                onClick = { applyPreset(preset) },
                            )
                        }
                    }
                    if (presetId == "custom") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.customize_custom_mix),
                            style = typo().labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // ===== Accent =====
                Section(
                    title = stringResource(Res.string.customize_accent),
                    hint = stringResource(Res.string.customize_accent_hint),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Reliefy's own default colour.
                        Swatch(
                            color = seed,
                            selected = colorSource == DataStoreManager.THEME_COLOR_DEFAULT,
                            onClick = { setAccent(DataStoreManager.THEME_COLOR_DEFAULT, null) },
                        )
                        AccentSwatches.forEach { hex ->
                            val color = parseThemeColorHex(hex) ?: Color.Gray
                            Swatch(
                                color = color,
                                selected =
                                    colorSource == DataStoreManager.THEME_COLOR_CUSTOM &&
                                        customHex.takeLast(6).equals(hex.takeLast(6), ignoreCase = true),
                                onClick = { setAccent(DataStoreManager.THEME_COLOR_CUSTOM, hex) },
                            )
                        }
                    }
                    if (isWallpaperDynamicColorSupported()) {
                        Spacer(Modifier.height(12.dp))
                        OptionChip(
                            label = stringResource(Res.string.customize_accent_wallpaper),
                            selected = colorSource == DataStoreManager.THEME_COLOR_WALLPAPER,
                            onClick = { setAccent(DataStoreManager.THEME_COLOR_WALLPAPER, null) },
                        )
                    }
                }

                // ===== Color style =====
                Section(title = stringResource(Res.string.customize_color_style)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ColorStyle.entries.forEach { colorStyle ->
                            OptionChip(
                                label = stringResource(colorStyle.label()),
                                selected = style.colorStyle == colorStyle,
                                onClick = { setPart(ReliefyStyleKeys.COLOR_STYLE, colorStyle.key) },
                                leading = { PaletteDots(seedColor = currentSeed, colorStyle = colorStyle) },
                            )
                        }
                    }
                }

                // ===== Corners =====
                Section(title = stringResource(Res.string.customize_corners)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CornerStyle.entries.forEach { corners ->
                            CornerOption(
                                corners = corners,
                                selected = style.corners == corners,
                                onClick = { setPart(ReliefyStyleKeys.CORNERS, corners.key) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ===== Motion =====
                Section(
                    title = stringResource(Res.string.customize_motion),
                    hint = stringResource(Res.string.customize_motion_try),
                ) {
                    MotionDemo(motion = style.motion)
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MotionStyle.entries.forEach { motion ->
                            ChoiceRow(
                                title = stringResource(motion.label()),
                                subtitle = stringResource(motion.description()),
                                selected = style.motion == motion,
                                onClick = { setPart(ReliefyStyleKeys.MOTION, motion.key) },
                            )
                        }
                    }
                }

                // ===== Background =====
                Section(
                    title = stringResource(Res.string.customize_background),
                    hint = stringResource(Res.string.customize_background_note),
                ) {
                    val softSurface =
                        rememberDynamicColorScheme(
                            seedColor = currentSeed,
                            isDark = true,
                            isAmoled = false,
                            style = style.colorStyle.paletteStyle,
                        ).background
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BackgroundOption(
                            title = stringResource(Res.string.bg_pure_black),
                            subtitle = stringResource(Res.string.bg_pure_black_desc),
                            sample = Color.Black,
                            selected = style.background == BackgroundStyle.PURE_BLACK,
                            onClick = { setPart(ReliefyStyleKeys.BACKGROUND, BackgroundStyle.PURE_BLACK.key) },
                            modifier = Modifier.weight(1f),
                        )
                        BackgroundOption(
                            title = stringResource(Res.string.bg_soft_dark),
                            subtitle = stringResource(Res.string.bg_soft_dark_desc),
                            sample = softSurface,
                            selected = style.background == BackgroundStyle.SOFT_DARK,
                            onClick = { setPart(ReliefyStyleKeys.BACKGROUND, BackgroundStyle.SOFT_DARK.key) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ===== Song transitions =====
                Section(
                    title = stringResource(Res.string.customize_transitions),
                    hint = stringResource(Res.string.customize_crossfade_hint),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { dataStoreManager.setCrossfadeEnabled(crossfadeEnabled != DataStoreManager.TRUE) }
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.crossfade),
                            style = typo().bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = crossfadeEnabled == DataStoreManager.TRUE,
                            onCheckedChange = { checked -> scope.launch { dataStoreManager.setCrossfadeEnabled(checked) } },
                        )
                    }
                    AnimatedVisibility(visible = crossfadeEnabled == DataStoreManager.TRUE) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(Res.string.crossfade_duration),
                                style = typo().labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val autoLabel = stringResource(Res.string.crossfade_auto)
                                crossfadeChoices.forEach { ms ->
                                    OptionChip(
                                        label = if (ms == DataStoreManager.CROSSFADE_DURATION_AUTO) autoLabel else "${ms / 1000}s",
                                        selected = crossfadeDuration == ms,
                                        onClick = { scope.launch { dataStoreManager.setCrossfadeDuration(ms) } },
                                    )
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { applyPreset(ThemePresets.first()) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(SimpIcons.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(text = stringResource(Res.string.customize_reset), color = MaterialTheme.colorScheme.primary)
                }

                EndOfPage()
            }
        }

        LiquidGlassIconButton(
            backdrop = backdrop,
            imageVector = SimpIcons.ArrowBackIosNew,
            tint = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(24.dp),
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, top = 8.dp)
                    .size(48.dp),
        ) {
            navController.navigateUp()
        }
    }
}

// ===== Labels =====

private fun ColorStyle.label(): StringResource =
    when (this) {
        ColorStyle.DEFAULT -> Res.string.color_style_default
        ColorStyle.VIBRANT -> Res.string.color_style_vibrant
        ColorStyle.EXPRESSIVE -> Res.string.color_style_expressive
        ColorStyle.MUTED -> Res.string.color_style_muted
        ColorStyle.TRUE_COLOR -> Res.string.color_style_true_color
        ColorStyle.RAINBOW -> Res.string.color_style_rainbow
        ColorStyle.FRUIT_SALAD -> Res.string.color_style_fruit_salad
        ColorStyle.MONOCHROME -> Res.string.color_style_monochrome
    }

private fun CornerStyle.label(): StringResource =
    when (this) {
        CornerStyle.SHARP -> Res.string.corners_sharp
        CornerStyle.SOFT -> Res.string.corners_soft
        CornerStyle.ROUND -> Res.string.corners_round
        CornerStyle.EXTRA_ROUND -> Res.string.corners_extra_round
    }

private fun MotionStyle.label(): StringResource =
    when (this) {
        MotionStyle.SMOOTH -> Res.string.motion_smooth
        MotionStyle.SNAPPY -> Res.string.motion_snappy
        MotionStyle.BOUNCY -> Res.string.motion_bouncy
        MotionStyle.REDUCED -> Res.string.motion_reduced
    }

private fun MotionStyle.description(): StringResource =
    when (this) {
        MotionStyle.SMOOTH -> Res.string.motion_smooth_desc
        MotionStyle.SNAPPY -> Res.string.motion_snappy_desc
        MotionStyle.BOUNCY -> Res.string.motion_bouncy_desc
        MotionStyle.REDUCED -> Res.string.motion_reduced_desc
    }

// ===== Pieces =====

@Composable
private fun Section(
    title: String,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = typo().titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** A tiny "now playing" card drawn with the current theme, so every change shows up here first. */
@Composable
private fun StylePreviewCard() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(roundedShape(24.dp))
                .background(scheme.surfaceContainer)
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.4f), roundedShape(24.dp))
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(roundedShape(14.dp))
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SimpIcons.Favorite, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.customize_preview_song),
                style = typo().titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.customize_preview_artist),
                style = typo().bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.42f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SimpIcons.SkipPrevious, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(roundedShape(17.dp))
                            .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SimpIcons.PlayArrow, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Icon(SimpIcons.SkipNext, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(22.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(roundedShape(10.dp))
                        .background(scheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("LIVE", style = typo().labelSmall, color = scheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme =
        rememberDynamicColorScheme(
            seedColor = preset.previewColor,
            isDark = true,
            isAmoled = preset.style.background == BackgroundStyle.PURE_BLACK,
            style = preset.style.colorStyle.paletteStyle,
        )
    val radius: Dp = 16.dp * preset.style.corners.scale
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        label = "presetBorder",
    )
    Column(
        modifier =
            Modifier
                .width(112.dp)
                .clip(roundedShape(18.dp))
                .border(if (selected) 2.dp else 1.dp, borderColor, roundedShape(18.dp))
                .clickable { onClick() }
                .padding(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(radius))
                    .background(scheme.background),
        ) {
            Box(
                Modifier
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(radius * 0.6f))
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp, end = 8.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.35f)),
            )
            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SimpIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.name,
            style = typo().labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = CircleShape,
                ).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(SimpIcons.Check, contentDescription = null, tint = Color.Black.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceContainerHigh.copy(alpha = 0.6f),
        label = "chipBg",
    )
    Row(
        modifier =
            Modifier
                .clip(roundedShape(14.dp))
                .background(bg)
                .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.4f), roundedShape(14.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = typo().labelMedium,
            color = if (selected) scheme.primary else scheme.onSurface,
        )
    }
}

/** Three dots showing what this colour style makes of the current accent. */
@Composable
private fun PaletteDots(
    seedColor: Color,
    colorStyle: ColorStyle,
) {
    val scheme =
        rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = true,
            style = colorStyle.paletteStyle,
        )
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { c ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

@Composable
private fun CornerOption(
    corners: CornerStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val radius = 12.dp * corners.scale
    Column(
        modifier =
            modifier
                .clip(roundedShape(16.dp))
                .background(if (selected) scheme.primary.copy(alpha = 0.14f) else scheme.surfaceContainerHigh.copy(alpha = 0.6f))
                .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.4f), roundedShape(16.dp))
                .clickable { onClick() }
                .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(radius))
                .background(if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(corners.label()),
            style = typo().labelSmall,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(roundedShape(16.dp))
                .background(if (selected) scheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                .border(1.dp, if (selected) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant.copy(alpha = 0.35f), roundedShape(16.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = typo().bodyMedium, color = if (selected) scheme.primary else scheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = typo().bodySmall, color = scheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(SimpIcons.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun BackgroundOption(
    title: String,
    subtitle: String,
    sample: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .clip(roundedShape(16.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.4f), roundedShape(16.dp))
                .clickable { onClick() }
                .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(roundedShape(10.dp))
                .background(sample)
                .border(1.dp, Color.White.copy(alpha = 0.08f), roundedShape(10.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = typo().bodyMedium, color = if (selected) scheme.primary else scheme.onSurface)
        Text(subtitle, style = typo().bodySmall, color = scheme.onSurfaceVariant)
    }
}

/** A dot that runs along a track with the selected motion, replayed on tap or on change. */
@Composable
private fun MotionDemo(motion: MotionStyle) {
    var atEnd by remember { mutableStateOf(false) }
    LaunchedEffect(motion) { atEnd = !atEnd }
    val spec: AnimationSpec<Float> =
        when (motion) {
            MotionStyle.SMOOTH -> tween(650, easing = FastOutSlowInEasing)
            MotionStyle.SNAPPY -> tween(180)
            MotionStyle.BOUNCY -> spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)
            MotionStyle.REDUCED -> snap()
        }
    val progress by animateFloatAsState(if (atEnd) 1f else 0f, animationSpec = spec, label = "motionDemo")
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(roundedShape(22.dp))
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.6f))
                .clickable { atEnd = !atEnd }
                .padding(6.dp),
    ) {
        val travel = maxWidth - 32.dp
        Box(
            Modifier
                .offset(x = travel * progress)
                .size(32.dp)
                .clip(roundedShape(16.dp))
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
        )
    }
}
