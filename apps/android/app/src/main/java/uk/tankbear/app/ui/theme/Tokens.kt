package uk.tankbear.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Semantic roles from the approved Tank Bear UI Figma variables. */
@Immutable
data class TankBearColors(
    val background: Color,
    val surface: Color,
    val soft: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val outline: Color,
    val error: Color,
    val errorSurface: Color,
    val start: Color,
    val route: Color,
    val baseline: Color,
    val pumpBelow: Color,
    val pumpAround: Color,
    val pumpAbove: Color,
    val pumpUnknown: Color,
)

internal val LightTankBearColors = TankBearColors(
    background = Color(0xFFFFF8EE),
    surface = Color(0xFFFFFFFF),
    soft = Color(0xFFF3E6CF),
    text = Color(0xFF27231D),
    muted = Color(0xFF625743),
    primary = Color(0xFF775500),
    onPrimary = Color(0xFFFFFFFF),
    outline = Color(0xFF85765F),
    error = Color(0xFFA52A25),
    errorSurface = Color(0xFFFFF0ED),
    start = Color(0xFF21613D),
    route = Color(0xFF875500),
    baseline = Color(0xFF52636F),
    pumpBelow = Color(0xFF21613D),
    pumpAround = Color(0xFF775500),
    pumpAbove = Color(0xFFA52A25),
    pumpUnknown = Color(0xFF625743),
)

internal val DarkTankBearColors = TankBearColors(
    background = Color(0xFF191712),
    surface = Color(0xFF28241D),
    soft = Color(0xFF393125),
    text = Color(0xFFF7EDDC),
    muted = Color(0xFFCEBEA5),
    primary = Color(0xFFF4B942),
    onPrimary = Color(0xFF302000),
    outline = Color(0xFFA39177),
    error = Color(0xFFFFB4AB),
    errorSurface = Color(0xFF482523),
    start = Color(0xFF9FD6AB),
    route = Color(0xFFF4B942),
    baseline = Color(0xFFB8CBD8),
    pumpBelow = Color(0xFF9FD6AB),
    pumpAround = Color(0xFFF4B942),
    pumpAbove = Color(0xFFFFB4AB),
    pumpUnknown = Color(0xFFCEBEA5),
)

internal val LocalTankBearColors = staticCompositionLocalOf { LightTankBearColors }

object TankBearTokens {
    val colors: TankBearColors
        @Composable get() = LocalTankBearColors.current
    val space = TankBearSpacing
    val radius = TankBearRadius
    val touch = TankBearTouch
}

object TankBearSpacing {
    val dp4 = 4.dp
    val dp8 = 8.dp
    val dp12 = 12.dp
    val dp16 = 16.dp
    val dp24 = 24.dp
    val dp32 = 32.dp
}

object TankBearRadius {
    val small = 12.dp
    val card = 24.dp
    val pill = 28.dp
}

object TankBearTouch { val min = 48.dp }
