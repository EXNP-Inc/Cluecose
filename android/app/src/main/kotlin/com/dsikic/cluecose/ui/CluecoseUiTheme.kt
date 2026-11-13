package com.dsikic.cluecose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Port of iOS `CluecoseTheme.swift`. Backdrop stops are the exact int
 * computations of the iOS float stops (e.g. g = 0.09 * 255 = 22.95 -> 23),
 * glow colors are the exact float values, glass panel spec is the iOS
 * `GlassPanel` modifier with the regularMaterial blur approximated by a
 * semi-transparent white fill (Android has no live blur).
 */
object CluecoseColors {
    // Backdrop gradient, top -> bottom (iOS locations 0, 0.45, 0.8, 1.0).
    val Backdrop0 = Color(0xFF051721) // (0.02, 0.09, 0.13)
    val Backdrop1 = Color(0xFF082930) // (0.03, 0.16, 0.19)
    val Backdrop2 = Color(0xFF0D3D42) // (0.05, 0.24, 0.26)
    val Backdrop3 = Color(0xFF1A4D4F) // (0.10, 0.30, 0.31)

    // Glow colors (iOS `glow(for:)`).
    val GlowLow = Color(0xFF73C7FF) // (0.45, 0.78, 1.00)
    val GlowInRange = Color(0xFF4CEB99) // (0.30, 0.92, 0.60)
    val GlowHigh = Color(0xFFFF9E59) // (1.00, 0.62, 0.35)

    // Text.
    val TextPrimary = Color(0xFFEAF6F9)
    fun white(alpha: Float): Color = Color.White.copy(alpha = alpha)

    // iOS (0.35, 0.95, 0.65) action green (pair / try-again buttons).
    val Action = Color(0xFF59F2A6)

    // Stat-cell tints (iOS AnalyticsView).
    val TintCv = Color(0xFFBF99FF) // (0.75, 0.60, 1.00)
    val TintGmi = Color(0xFFF2BF73) // (0.95, 0.75, 0.45)
    val TintTip = Color(0xFFF2C766) // (0.95, 0.78, 0.40)
    val Warn = GlowHigh
}

/** Glow color for a glucose range category (iOS `CluecoseTheme.glow(for:)`). */
fun glowColor(category: RangeCategory): Color = when (category) {
    RangeCategory.VERY_LOW, RangeCategory.LOW -> CluecoseColors.GlowLow
    RangeCategory.IN_RANGE -> CluecoseColors.GlowInRange
    RangeCategory.HIGH, RangeCategory.VERY_HIGH -> CluecoseColors.GlowHigh
}

/** Fully overridden dark scheme so no Material default leaks through. */
fun cluecoseColorScheme(): ColorScheme = darkColorScheme(
    primary = CluecoseColors.GlowInRange,
    onPrimary = Color(0xFF04150C),
    primaryContainer = Color(0xFF0F3D2A),
    onPrimaryContainer = Color(0xFFC9F7DD),
    secondary = CluecoseColors.GlowLow,
    onSecondary = Color(0xFF06131C),
    secondaryContainer = Color(0xFF123245),
    onSecondaryContainer = Color(0xFFC5E5FF),
    tertiary = CluecoseColors.GlowHigh,
    onTertiary = Color(0xFF1F1004),
    tertiaryContainer = Color(0xFF3D2712),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = CluecoseColors.Backdrop0,
    onBackground = CluecoseColors.TextPrimary,
    surface = CluecoseColors.Backdrop0,
    onSurface = CluecoseColors.TextPrimary,
    surfaceVariant = CluecoseColors.Backdrop2,
    onSurfaceVariant = Color(0xFFB7D2D8),
    surfaceTint = CluecoseColors.GlowInRange,
    outline = CluecoseColors.white(0.30f),
    outlineVariant = CluecoseColors.white(0.12f),
    scrim = Color.Black,
    inverseSurface = CluecoseColors.TextPrimary,
    inverseOnSurface = CluecoseColors.Backdrop2,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun CluecoseUiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = cluecoseColorScheme(),
        content = content,
    )
}

/**
 * Full-screen vertical gradient backdrop — the deep ocean-teal the glass
 * panels sit on (iOS `CluecoseTheme.backdrop()`). Rendered as per-stop
 * 2-color segments so the iOS stop positions (0 / 0.45 / 0.8 / 1.0) are
 * exact — the stable Brush factories take no per-stop fractions.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    val stops = listOf(
        0f to CluecoseColors.Backdrop0,
        0.45f to CluecoseColors.Backdrop1,
        0.8f to CluecoseColors.Backdrop2,
        1f to CluecoseColors.Backdrop3,
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val h = size.height
        for (i in 0 until stops.size - 1) {
            val (f0, c0) = stops[i]
            val (f1, c1) = stops[i + 1]
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(c0, c1),
                    start = Offset(0f, h * f0),
                    end = Offset(0f, h * f1),
                ),
                topLeft = Offset(0f, h * f0),
                size = Size(size.width, h * (f1 - f0)),
            )
        }
    }
}

/**
 * The frosted glass card (iOS `glassPanel(cornerRadius: 28)`):
 *  - 28 dp continuous corners, 20 dp content padding
 *  - semi-transparent white fill (regularMaterial stand-in)
 *  - 1 dp stroke, white 0.45 -> 0.06 vertical gradient
 *  - black 0.28 shadow, ~18 dp radius, 10 dp downward offset
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    contentPadding: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.28f),
                spotColor = Color.Black.copy(alpha = 0.28f),
            )
            .clip(shape)
            .background(CluecoseColors.white(0.07f), shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CluecoseColors.white(0.45f),
                        CluecoseColors.white(0.06f),
                    ),
                ),
                shape = shape,
            )
            .padding(contentPadding),
    ) {
        content()
    }
}
