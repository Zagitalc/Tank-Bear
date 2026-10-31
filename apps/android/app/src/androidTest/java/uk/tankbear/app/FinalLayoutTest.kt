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
        val savedBefore = vm.saved
        val placeName = "Long name for a saved public location"
        compose.runOnIdle { vm.update { it.copy(mpg = "40", litres = "30", places = emptyList()) }; vm.useExample(); vm.savePlace(placeName, false) }
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
                compose.onNodeWithText("Car").performClick()
                compose.onNodeWithText("Use UK Imperial MPG.", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Sets the fill estimate on Find and Map.", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Settings").performClick()
                for (label in listOf("Location", "Privacy", "Data", "App version")) {
                    compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                }
                compose.onNodeWithText("${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Find").performClick()
                compose.onNodeWithText("Manage saved places").performScrollTo().performClick()
                compose.onNodeWithText(placeName).performScrollTo().assertIsDisplayed()
                for (action in listOf("Start", "Destination", "Remove", "Save start", "Save destination")) {
                    val selector = hasText(action) and hasAnyAncestor(hasTestTag("saved-places"))
                    compose.onNode(selector).performScrollTo().assertIsDisplayed()
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    compose.onNode(selector, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertFalse("$action must wrap without clipping", layouts.single().hasVisualOverflow)
                }
                compose.onNodeWithText("Hide saved places").performScrollTo().performClick()
                compose.onNodeWithText("Find the best stop").assertIsDisplayed()
                compose.onNodeWithTag("choose-start").performScrollTo().performClick()
                compose.onNodeWithText("Choose start on map").performScrollTo().performClick()
                compose.onNodeWithText("Use this start").performScrollTo().assertIsDisplayed()
                val bounds = compose.onNodeWithTag("route-map").fetchSemanticsNode().boundsInRoot
                assertTrue("Map must retain usable area", bounds.width > 100 && bounds.height > 100)
                compose.onNodeWithText("Cancel").performScrollTo().performClick()
                compose.onNodeWithText("Find the best stop").assertIsDisplayed()
            }
        } finally {
            compose.runOnIdle { vm.update { savedBefore } }
            kotlinx.coroutines.runBlocking { uk.tankbear.app.data.Prefs(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext).save(savedBefore) }
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
