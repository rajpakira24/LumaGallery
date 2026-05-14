package com.webstudio.lumagallery.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.webstudio.lumagallery.ui.components.LiquidGlassBackground

private val LightColorScheme = lightColorScheme(
    primary = LumaPrimaryLight,
    onPrimary = LumaOnPrimaryLight,
    primaryContainer = LumaPrimaryContainerLight,
    onPrimaryContainer = LumaOnPrimaryContainerLight,
    secondary = LumaSecondaryLight,
    onSecondary = LumaOnSecondaryLight,
    secondaryContainer = LumaSecondaryContainerLight,
    onSecondaryContainer = LumaOnSecondaryContainerLight,
    tertiary = LumaTertiaryLight,
    onTertiary = LumaOnTertiaryLight,
    tertiaryContainer = LumaTertiaryContainerLight,
    onTertiaryContainer = LumaOnTertiaryContainerLight,
    error = LumaErrorLight,
    onError = LumaOnErrorLight,
    errorContainer = LumaErrorContainerLight,
    onErrorContainer = LumaOnErrorContainerLight,
    background = LumaBackgroundLight,
    onBackground = LumaOnBackgroundLight,
    surface = LumaSurfaceLight,
    onSurface = LumaOnSurfaceLight,
    surfaceVariant = LumaSurfaceVariantLight,
    onSurfaceVariant = LumaOnSurfaceVariantLight,
    surfaceTint = LumaSurfaceTintLight,
    inverseSurface = LumaInverseSurfaceLight,
    inverseOnSurface = LumaInverseOnSurfaceLight,
    inversePrimary = LumaInversePrimaryLight,
    surfaceContainerLowest = LumaSurfaceContainerLowestLight,
    surfaceContainerLow = LumaSurfaceContainerLowLight,
    surfaceContainer = LumaSurfaceContainerLight,
    surfaceContainerHigh = LumaSurfaceContainerHighLight,
    surfaceContainerHighest = LumaSurfaceContainerHighestLight,
    outline = LumaOutlineLight,
    outlineVariant = LumaOutlineVariantLight,
    scrim = LumaScrimLight
)

private val DarkColorScheme = darkColorScheme(
    primary = LumaPrimaryDark,
    onPrimary = LumaOnPrimaryDark,
    primaryContainer = LumaPrimaryContainerDark,
    onPrimaryContainer = LumaOnPrimaryContainerDark,
    secondary = LumaSecondaryDark,
    onSecondary = LumaOnSecondaryDark,
    secondaryContainer = LumaSecondaryContainerDark,
    onSecondaryContainer = LumaOnSecondaryContainerDark,
    tertiary = LumaTertiaryDark,
    onTertiary = LumaOnTertiaryDark,
    tertiaryContainer = LumaTertiaryContainerDark,
    onTertiaryContainer = LumaOnTertiaryContainerDark,
    error = LumaErrorDark,
    onError = LumaOnErrorDark,
    errorContainer = LumaErrorContainerDark,
    onErrorContainer = LumaOnErrorContainerDark,
    background = LumaBackgroundDark,
    onBackground = LumaOnBackgroundDark,
    surface = LumaSurfaceDark,
    onSurface = LumaOnSurfaceDark,
    surfaceVariant = LumaSurfaceVariantDark,
    onSurfaceVariant = LumaOnSurfaceVariantDark,
    surfaceTint = LumaSurfaceTintDark,
    inverseSurface = LumaInverseSurfaceDark,
    inverseOnSurface = LumaInverseOnSurfaceDark,
    inversePrimary = LumaInversePrimaryDark,
    surfaceContainerLowest = LumaSurfaceContainerLowestDark,
    surfaceContainerLow = LumaSurfaceContainerLowDark,
    surfaceContainer = LumaSurfaceContainerDark,
    surfaceContainerHigh = LumaSurfaceContainerHighDark,
    surfaceContainerHighest = LumaSurfaceContainerHighestDark,
    outline = LumaOutlineDark,
    outlineVariant = LumaOutlineVariantDark,
    scrim = LumaScrimDark
)

@Composable
fun LumaGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = {
            LiquidGlassBackground {
                content()
            }
        }
    )
}
