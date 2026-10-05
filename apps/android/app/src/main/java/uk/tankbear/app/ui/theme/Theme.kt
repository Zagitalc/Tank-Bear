package uk.tankbear.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF775500),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA0),
    onPrimaryContainer = Color(0xFF261900),
    secondaryContainer = Color(0xFFF3DFBA),
    onSecondaryContainer = Color(0xFF292014),
    background = Color(0xFFFFF8EE),
    onBackground = Color(0xFF27231D),
    surface = Color(0xFFFFF8EE),
    onSurface = Color(0xFF27231D),
    surfaceVariant = Color(0xFFEFE3D1),
    onSurfaceVariant = Color(0xFF4C4539),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4B942),
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5B4200),
    onPrimaryContainer = Color(0xFFFFDEA0),
    secondaryContainer = Color(0xFF51452F),
    onSecondaryContainer = Color(0xFFF3DFBA),
    background = Color(0xFF191712),
    onBackground = Color(0xFFEAE2D6),
    surface = Color(0xFF191712),
    onSurface = Color(0xFFEAE2D6),
    surfaceVariant = Color(0xFF4C4539),
    onSurfaceVariant = Color(0xFFD0C5B4),
)

@Composable
fun TankBearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
