package com.webstudio.lumagallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object LiquidGlassTokens {
    val PanelShape = RoundedCornerShape(24.dp)
    val CompactShape = RoundedCornerShape(18.dp)
    val TileShape = RoundedCornerShape(14.dp)
    val PillShape = RoundedCornerShape(28.dp)
    val GlassElevation = 10.dp
    const val LightAlpha = 0.72f
    const val DarkAlpha = 0.46f
}

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colors = if (isDark) {
        listOf(
            Color(0xFF091116),
            MaterialTheme.colorScheme.surface,
            Color(0xFF15343A),
            Color(0xFF0B1D24)
        )
    } else {
        listOf(
            Color(0xFFF8FCFF),
            Color(0xFFE8F6FA),
            Color(0xFFFFF1E5),
            Color(0xFFF5FBFF)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = colors,
                    start = Offset.Zero,
                    end = Offset(1200f, 1600f)
                )
            ),
        content = content
    )
}

@Composable
fun Modifier.liquidGlass(
    shape: Shape = LiquidGlassTokens.PanelShape,
    alpha: Float = if (isSystemInDarkTheme()) LiquidGlassTokens.DarkAlpha else LiquidGlassTokens.LightAlpha,
    elevation: Dp = LiquidGlassTokens.GlassElevation
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val fill = Brush.linearGradient(
        colors = listOf(
            scheme.surface.copy(alpha = alpha + 0.08f),
            scheme.surfaceContainerLow.copy(alpha = alpha),
            scheme.primaryContainer.copy(alpha = alpha * 0.22f)
        ),
        start = Offset.Zero,
        end = Offset(500f, 700f)
    )
    val stroke = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.24f else 0.78f),
            scheme.outlineVariant.copy(alpha = 0.34f),
            scheme.primary.copy(alpha = 0.22f)
        )
    )

    return this
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)
        .background(fill)
        .border(width = 1.dp, brush = stroke, shape = shape)
}
