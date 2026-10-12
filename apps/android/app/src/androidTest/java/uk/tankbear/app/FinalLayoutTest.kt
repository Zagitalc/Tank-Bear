package uk.tankbear.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.tankbear.app.ui.*

class FinalLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun landscapeLargeTextKeepsFindAndMapActionsReachable() {
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        compose.runOnIdle { vm.update { it.copy(mpg = "40", litres = "30") }; vm.useExample() }
        val dark = mutableStateOf(false)
        compose.runOnUiThread {
            compose.activity.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides config,
                    LocalDensity provides Density(LocalDensity.current.density, 2f)) { TankBearApp(vm) }
            }
        }
        try {
            for (night in listOf(false, true)) {
                compose.runOnIdle { dark.value = night }
                compose.onNodeWithText("Find").performClick()
                compose.onNodeWithText("Find the best stop").assertIsDisplayed()
                compose.onNodeWithText("Choose start on map").performScrollTo().performClick()
                compose.onNodeWithText("Use this location").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Cancel").performScrollTo().performClick()
                compose.onNodeWithText("Find the best stop").assertIsDisplayed()
                val bounds = compose.onNodeWithTag("route-map").fetchSemanticsNode().boundsInRoot
                assertTrue("Map must retain usable area", bounds.width > 100 && bounds.height > 100)
            }
        } finally {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
