package com.maxrave.simpmusic.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.PaletteStyle
import com.maxrave.domain.manager.DataStoreManager
import org.koin.compose.koinInject

/*
 * Reliefy's "Customize" settings: how the app looks and moves, on top of SimpMusic's own
 * theme mode (dark / light / system) and theme colour (default / wallpaper / custom).
 *
 * Everything here is stored through DataStoreManager's generic string keys, so no change to the
 * DataStore interface (and no migration) was needed. An unknown or missing value always falls
 * back to the default, which is SimpMusic's original look.
 */

/** How the Material colour scheme is generated from the seed colour. */
enum class ColorStyle(
    val key: String,
    val paletteStyle: PaletteStyle,
) {
    DEFAULT("default", PaletteStyle.TonalSpot),
    VIBRANT("vibrant", PaletteStyle.Vibrant),
    EXPRESSIVE("expressive", PaletteStyle.Expressive),
    MUTED("muted", PaletteStyle.Neutral),
    TRUE_COLOR("true_color", PaletteStyle.Fidelity),
    RAINBOW("rainbow", PaletteStyle.Rainbow),
    FRUIT_SALAD("fruit_salad", PaletteStyle.FruitSalad),
    MONOCHROME("monochrome", PaletteStyle.Monochrome),
    ;

    companion object {
        fun fromKey(key: String?): ColorStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** How round corners are. [scale] multiplies every rounded corner the app draws. */
enum class CornerStyle(
    val key: String,
    val scale: Float,
) {
    SHARP("sharp", 0.25f),
    SOFT("soft", 0.6f),
    ROUND("round", 1f),
    EXTRA_ROUND("extra_round", 1.6f),
    ;

    companion object {
        fun fromKey(key: String?): CornerStyle = entries.firstOrNull { it.key == key } ?: ROUND
    }
}

/** How screens and Material components move. */
enum class MotionStyle(
    val key: String,
) {
    SMOOTH("smooth"),
    SNAPPY("snappy"),
    BOUNCY("bouncy"),
    REDUCED("reduced"),
    ;

    companion object {
        fun fromKey(key: String?): MotionStyle = entries.firstOrNull { it.key == key } ?: SMOOTH
    }
}

/** What the dark theme's background is. Light theme is not affected. */
enum class BackgroundStyle(
    val key: String,
) {
    PURE_BLACK("pure_black"),
    SOFT_DARK("soft_dark"),
    ;

    companion object {
        fun fromKey(key: String?): BackgroundStyle = entries.firstOrNull { it.key == key } ?: PURE_BLACK
    }
}

@Immutable
data class ReliefyStyle(
    val colorStyle: ColorStyle = ColorStyle.DEFAULT,
    val corners: CornerStyle = CornerStyle.ROUND,
    val motion: MotionStyle = MotionStyle.SMOOTH,
    val background: BackgroundStyle = BackgroundStyle.PURE_BLACK,
) {
    companion object {
        val Default = ReliefyStyle()
    }
}

object ReliefyStyleKeys {
    const val COLOR_STYLE = "reliefy_color_style"
    const val CORNERS = "reliefy_corner_style"
    const val MOTION = "reliefy_motion_style"
    const val BACKGROUND = "reliefy_background_style"
    const val PRESET = "reliefy_theme_preset"
}

/** Provided by [AppTheme]. */
val LocalReliefyStyle = staticCompositionLocalOf { ReliefyStyle.Default }

/** Reads the stored style. Values that have not loaded yet read as the defaults. */
@Composable
fun rememberReliefyStyle(dataStoreManager: DataStoreManager = koinInject()): ReliefyStyle {
    val color by dataStoreManager.getString(ReliefyStyleKeys.COLOR_STYLE).collectAsStateWithLifecycle(null)
    val corners by dataStoreManager.getString(ReliefyStyleKeys.CORNERS).collectAsStateWithLifecycle(null)
    val motion by dataStoreManager.getString(ReliefyStyleKeys.MOTION).collectAsStateWithLifecycle(null)
    val background by dataStoreManager.getString(ReliefyStyleKeys.BACKGROUND).collectAsStateWithLifecycle(null)
    return ReliefyStyle(
        colorStyle = ColorStyle.fromKey(color),
        corners = CornerStyle.fromKey(corners),
        motion = MotionStyle.fromKey(motion),
        background = BackgroundStyle.fromKey(background),
    )
}

// ===== Corners =====

/**
 * The corner scale the whole app reads. It is snapshot state rather than a CompositionLocal on
 * purpose: [roundedShape] is called from ~200 places, some of them outside composition (default
 * parameters, top-level values), and a plain function works in all of them. Inside composition the
 * read is tracked, so changing the setting recomposes whatever drew a rounded corner.
 */
private val cornerScaleState = mutableFloatStateOf(1f)

internal fun setCornerScale(scale: Float) {
    if (cornerScaleState.floatValue != scale) cornerScaleState.floatValue = scale
}

/** A [RoundedCornerShape] whose radius follows the user's corner style. Same type, drop-in. */
fun roundedShape(size: Dp): RoundedCornerShape = RoundedCornerShape(size * cornerScaleState.floatValue)

/** Material's own shape scale (cards, sheets, dialogs, menus, text fields), scaled the same way. */
fun reliefyShapes(scale: Float): Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp * scale),
        small = RoundedCornerShape(8.dp * scale),
        medium = RoundedCornerShape(12.dp * scale),
        large = RoundedCornerShape(16.dp * scale),
        extraLarge = RoundedCornerShape(28.dp * scale),
    )

// ===== Motion =====

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MotionStyle.motionScheme(): MotionScheme =
    when (this) {
        MotionStyle.SMOOTH, MotionStyle.BOUNCY -> MotionScheme.expressive()
        MotionStyle.SNAPPY, MotionStyle.REDUCED -> MotionScheme.standard()
    }

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun bouncySlide() = spring<IntOffset>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

/** A screen being opened slides in. */
fun MotionStyle.navEnter(): EnterTransition =
    when (this) {
        MotionStyle.SMOOTH ->
            slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { it / 4 } +
                fadeIn(tween(260, delayMillis = 60, easing = Standard))
        MotionStyle.SNAPPY ->
            scaleIn(tween(200, easing = Standard), initialScale = 0.94f) + fadeIn(tween(160))
        MotionStyle.BOUNCY ->
            slideInHorizontally(bouncySlide()) { it / 3 } + fadeIn(tween(220))
        MotionStyle.REDUCED -> fadeIn(tween(150))
    }

/** The screen underneath, when another opens on top of it. */
fun MotionStyle.navExit(): ExitTransition =
    when (this) {
        MotionStyle.SMOOTH ->
            slideOutHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / 10 } +
                fadeOut(tween(180, easing = EmphasizedAccelerate))
        MotionStyle.SNAPPY ->
            scaleOut(tween(200, easing = Standard), targetScale = 1.04f) + fadeOut(tween(120))
        MotionStyle.BOUNCY ->
            slideOutHorizontally(bouncySlide()) { -it / 6 } + fadeOut(tween(200))
        MotionStyle.REDUCED -> fadeOut(tween(150))
    }

/** The screen underneath, coming back when the top one is closed. */
fun MotionStyle.navPopEnter(): EnterTransition =
    when (this) {
        MotionStyle.SMOOTH ->
            slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / 10 } +
                fadeIn(tween(260, delayMillis = 60, easing = Standard))
        MotionStyle.SNAPPY ->
            scaleIn(tween(200, easing = Standard), initialScale = 1.04f) + fadeIn(tween(160))
        MotionStyle.BOUNCY ->
            slideInHorizontally(bouncySlide()) { -it / 6 } + fadeIn(tween(220))
        MotionStyle.REDUCED -> fadeIn(tween(150))
    }

/** The screen being closed. */
fun MotionStyle.navPopExit(): ExitTransition =
    when (this) {
        MotionStyle.SMOOTH ->
            slideOutHorizontally(tween(320, easing = EmphasizedAccelerate)) { it / 4 } +
                fadeOut(tween(200, easing = EmphasizedAccelerate))
        MotionStyle.SNAPPY ->
            scaleOut(tween(180, easing = Standard), targetScale = 0.94f) + fadeOut(tween(120))
        MotionStyle.BOUNCY ->
            slideOutHorizontally(bouncySlide()) { it / 3 } + fadeOut(tween(200))
        MotionStyle.REDUCED -> fadeOut(tween(150))
    }

// ===== Theme presets =====

/**
 * A preset sets every Customize option at once, plus the theme colour. It does not touch dark /
 * light mode: that is a comfort choice, not a look. [seedHex] null means "Reliefy's default
 * colour" (theme colour source DEFAULT); otherwise the theme colour becomes that custom colour.
 */
@Immutable
data class ThemePreset(
    val id: String,
    val name: String,
    val seedHex: String?,
    val style: ReliefyStyle,
) {
    val previewColor: Color get() = seedHex?.let { parseThemeColorHex(it) } ?: seed
}

val ThemePresets: List<ThemePreset> =
    listOf(
        ThemePreset(
            id = "reliefy",
            name = "Reliefy",
            seedHex = null,
            style = ReliefyStyle(ColorStyle.DEFAULT, CornerStyle.ROUND, MotionStyle.SMOOTH, BackgroundStyle.PURE_BLACK),
        ),
        ThemePreset(
            id = "classic",
            name = "SimpMusic Classic",
            seedHex = "FF8ECAE6",
            style = ReliefyStyle(ColorStyle.DEFAULT, CornerStyle.ROUND, MotionStyle.SMOOTH, BackgroundStyle.PURE_BLACK),
        ),
        ThemePreset(
            id = "midnight",
            name = "Midnight",
            seedHex = "FF6C7BFF",
            style = ReliefyStyle(ColorStyle.EXPRESSIVE, CornerStyle.ROUND, MotionStyle.SMOOTH, BackgroundStyle.SOFT_DARK),
        ),
        ThemePreset(
            id = "sakura",
            name = "Sakura",
            seedHex = "FFFF8FB1",
            style = ReliefyStyle(ColorStyle.DEFAULT, CornerStyle.EXTRA_ROUND, MotionStyle.BOUNCY, BackgroundStyle.SOFT_DARK),
        ),
        ThemePreset(
            id = "forest",
            name = "Forest",
            seedHex = "FF5FB36B",
            style = ReliefyStyle(ColorStyle.TRUE_COLOR, CornerStyle.SOFT, MotionStyle.SMOOTH, BackgroundStyle.PURE_BLACK),
        ),
        ThemePreset(
            id = "sunset",
            name = "Sunset",
            seedHex = "FFFF8A5B",
            style = ReliefyStyle(ColorStyle.VIBRANT, CornerStyle.ROUND, MotionStyle.BOUNCY, BackgroundStyle.SOFT_DARK),
        ),
        ThemePreset(
            id = "neon",
            name = "Neon",
            seedHex = "FF00E5FF",
            style = ReliefyStyle(ColorStyle.RAINBOW, CornerStyle.SHARP, MotionStyle.SNAPPY, BackgroundStyle.PURE_BLACK),
        ),
        ThemePreset(
            id = "mono",
            name = "Mono",
            seedHex = "FF9E9E9E",
            style = ReliefyStyle(ColorStyle.MONOCHROME, CornerStyle.SHARP, MotionStyle.SNAPPY, BackgroundStyle.PURE_BLACK),
        ),
    )

/** Accent swatches offered on the Customize screen. */
val AccentSwatches: List<String> =
    listOf(
        "FF7FD8BE",
        "FF8ECAE6",
        "FF4C82EF",
        "FF6C7BFF",
        "FF9B72CF",
        "FFFF8FB1",
        "FFEF5350",
        "FFFF8A5B",
        "FFFFCA28",
        "FF5FB36B",
        "FF00E5FF",
        "FF9E9E9E",
    )
