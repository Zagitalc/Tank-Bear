package uk.tankbear.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Android's sans-serif resolves to Roboto; no network font loading.
private fun type(size: Int, height: Int, bold: Boolean = false) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp,
)

object TankBearType {
    val title = type(32, 40, true)
    val heading = type(24, 32, true)
    val price = type(28, 36, true)
    val body = type(16, 24)
    val label = type(14, 20, true)
    val caption = type(12, 18)
}

internal val TankBearTypography = Typography(
    displayLarge = TankBearType.title,
    displayMedium = TankBearType.title,
    displaySmall = TankBearType.title,
    headlineLarge = TankBearType.title,
    headlineMedium = TankBearType.price,
    headlineSmall = TankBearType.heading,
    titleLarge = TankBearType.heading,
    titleMedium = TankBearType.body.copy(fontWeight = FontWeight.Bold),
    titleSmall = TankBearType.label,
    bodyLarge = TankBearType.body,
    bodyMedium = TankBearType.body,
    bodySmall = TankBearType.caption,
    labelLarge = TankBearType.label,
    labelMedium = TankBearType.label,
    labelSmall = TankBearType.caption,
)
