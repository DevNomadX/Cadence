package com.example.ui.theme

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Provides the active Performance Mode / Reduced Animations flag across the entire Compose tree.
 * Auto-enabled on low-RAM Android devices and manually toggleable in Settings.
 */
val LocalPerformanceMode = staticCompositionLocalOf { false }

/**
 * Computes an opaque pre-blended RGB color equivalent to alpha-blending [this] over [background],
 * eliminating expensive translucent offscreen compositing passes on low-end GPUs while preserving
 * Cadence's exact tonal aesthetic.
 */
fun Color.opaqueOver(background: Color, alpha: Float): Color =
    this.copy(alpha = alpha).compositeOver(background)

val ExpressiveM3Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
private fun animateColor(target: Color): Color =
    animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 360),
        label = "theme_color"
    ).value

@Composable
private fun ColorScheme.animated(): ColorScheme = copy(
    primary = animateColor(primary),
    onPrimary = animateColor(onPrimary),
    primaryContainer = animateColor(primaryContainer),
    onPrimaryContainer = animateColor(onPrimaryContainer),
    secondary = animateColor(secondary),
    onSecondary = animateColor(onSecondary),
    secondaryContainer = animateColor(secondaryContainer),
    onSecondaryContainer = animateColor(onSecondaryContainer),
    tertiary = animateColor(tertiary),
    onTertiary = animateColor(onTertiary),
    tertiaryContainer = animateColor(tertiaryContainer),
    onTertiaryContainer = animateColor(onTertiaryContainer),
    background = animateColor(background),
    onBackground = animateColor(onBackground),
    surface = animateColor(surface),
    onSurface = animateColor(onSurface),
    surfaceVariant = animateColor(surfaceVariant),
    onSurfaceVariant = animateColor(onSurfaceVariant),
    outline = animateColor(outline),
    outlineVariant = animateColor(outlineVariant),
    surfaceContainerLowest = animateColor(surfaceContainerLowest),
    surfaceContainerLow = animateColor(surfaceContainerLow),
    surfaceContainer = animateColor(surfaceContainer),
    surfaceContainerHigh = animateColor(surfaceContainerHigh),
    surfaceContainerHighest = animateColor(surfaceContainerHighest)
)

private fun shouldSkipThemeColorAnimation(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    if (am?.isLowRamDevice == true) return true
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale == 0f
}

@Composable
fun CadenceTheme(
    palette: ExpressivePalette = ExpressivePalette.TERRACOTTA,
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    performanceMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearanceMode) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }

    val context = LocalContext.current
    val baseScheme = if (palette == ExpressivePalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        palette.colorScheme(darkTheme)
    }

    val hardwareSkipAnim = remember(context) { shouldSkipThemeColorAnimation(context) }
    val skipColorAnimation = performanceMode || hardwareSkipAnim
    val resolvedScheme = if (skipColorAnimation) baseScheme else baseScheme.animated()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalPerformanceMode provides performanceMode) {
        MaterialTheme(
            colorScheme = resolvedScheme,
            typography = CadenceTypography,
            shapes = ExpressiveM3Shapes,
            content = content
        )
    }
}
