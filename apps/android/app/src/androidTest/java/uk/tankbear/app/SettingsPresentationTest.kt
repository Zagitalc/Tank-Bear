package uk.tankbear.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme

class SettingsPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun attributionRemainsReachableAndAdaptiveIconLoads() {
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        val dark = mutableStateOf(false)
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides config,
                    LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TankBearTheme {
                        Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            Column(Modifier.width(360.dp).fillMaxHeight().testTag("settings").verticalScroll(rememberScrollState()).padding(16.dp)) {
                                FindBrand()
                                SettingsScreen(vm)
                            }
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithText("Map data © OpenStreetMap contributors, shown through MapLibre with OpenFreeMap tiles.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Fuel price data: Fuel Finder/ICS").assertExists()
            compose.onNodeWithText("You’re in control").performScrollTo().assertIsDisplayed()
            val bitmap = compose.onNodeWithTag("settings").captureToImage().asAndroidBitmap()
            java.io.File(compose.activity.cacheDir, if (night) "settings-dark.png" else "settings-light.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val icon = compose.activity.packageManager.getApplicationIcon(compose.activity.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        val adaptive = icon as AdaptiveIconDrawable
        assertNotNull(adaptive.foreground)
        assertNotNull(adaptive.background)
        if (android.os.Build.VERSION.SDK_INT >= 33) assertNotNull(adaptive.monochrome)
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        icon.setBounds(0, 0, 432, 432)
        icon.draw(canvas)
        java.io.File(compose.activity.cacheDir, "launcher.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
