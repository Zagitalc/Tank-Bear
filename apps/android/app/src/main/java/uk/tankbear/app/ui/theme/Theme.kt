package uk.tankbear.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun TankBearColors.materialColors(dark: Boolean) =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = soft, onPrimaryContainer = text,
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = soft, onSecondaryContainer = text,
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = soft, onTertiaryContainer = text,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = soft, onSurfaceVariant = muted,
        surfaceTint = primary,
        surfaceBright = surface, surfaceDim = background,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = soft,
        surfaceContainerHighest = soft,
        outline = outline, outlineVariant = outline,
        error = error, onError = background,
        errorContainer = errorSurface, onErrorContainer = error,
        inverseSurface = text, inverseOnSurface = background,
        inversePrimary = if (dark) LightTankBearColors.primary else DarkTankBearColors.primary,
    )

private val TankBearShapes = Shapes(
    extraSmall = RoundedCornerShape(TankBearRadius.small),
    small = RoundedCornerShape(TankBearRadius.small),
    medium = RoundedCornerShape(TankBearRadius.card),
    large = RoundedCornerShape(TankBearRadius.card),
    extraLarge = RoundedCornerShape(TankBearRadius.pill),
)

@Composable
fun TankBearTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkTankBearColors else LightTankBearColors
    CompositionLocalProvider(LocalTankBearColors provides colors) {
        MaterialTheme(
            colorScheme = colors.materialColors(dark),
            typography = TankBearTypography,
            shapes = TankBearShapes,
            content = content,
        )
    }
}
