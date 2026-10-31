package uk.tankbear.app

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme

@RunWith(AndroidJUnit4::class)
class NearbyMapPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var savedBefore: Saved? = null
    private val longName = "Regal Garages Ltd t/a Loddon Bridge service station with a very long supplied name"
    private val night = mutableStateOf(false)
    private val scale = mutableStateOf(1f)
    private val width = mutableStateOf(360)

    @After fun restorePreferences() {
        savedBefore?.let { saved -> runBlocking { Prefs(InstrumentationRegistry.getInstrumentation().targetContext).save(saved) } }
    }
    private fun showPanel() {
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        val station = NearbyStation("sample", longName, null, LatLon(51.4543, -0.9781), 1400, false,
            OpeningInfo("open", is24Hours = true), NearbyFill("173.9", "2026-10-30T00:00:00Z", 5217, null))
        compose.runOnIdle {
            savedBefore = vm.saved
            vm.update { it.copy(litres = "30", fuel = FuelType.E10) }
            vm.useExample()
            vm.mapNearbyLookup = { _, _, _, _, _ -> ApiOutcome.NearbyOk(NearbyResult("2026-10-31T00:00:00Z", listOf(station))) }
            vm.loadMapNearby()
        }
        compose.waitUntil(5000) { vm.currentMapNearby is NearbyState.Done }
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                    TankBearTheme { Surface {
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            Box(Modifier.width(width.value.dp).height(if (scale.value >= 1.5f) 340.dp else 230.dp)) {
                                NearbyMapPanel(vm, (vm.currentMapNearby as? NearbyState.Done)?.result, {})
                            }
                        }
                    } }
                }
            }
        }
    }
    private fun assertActionsFullyVisible() {
        val panel = compose.onNodeWithTag("nearby-panel").getUnclippedBoundsInRoot()
        for (label in listOf("Stations", "Refresh", "Change start")) {
            val action = compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled()
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue("$label lies inside the panel", bounds.top >= panel.top && bounds.bottom <= panel.bottom && bounds.left >= panel.left && bounds.right <= panel.right)
            assertTrue("$label touch height", bounds.bottom - bounds.top >= 48.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("$label text overflows at ${width.value}dp, scale=${scale.value}, night=${night.value}: size=${layouts.single().size}, constraints=${layouts.single().layoutInput.constraints}, paragraph=${layouts.single().multiParagraph.width}x${layouts.single().multiParagraph.height}, lines=${layouts.single().lineCount}", layouts.single().hasVisualOverflow)
        }
        compose.onNodeWithText("Fuel price data: Fuel Finder/ICS").assertIsDisplayed()
    }
    @Test fun longNamesAndLargeTextKeepActionsVisibleWithoutScrolling() {
        showPanel()
        for (dark in listOf(false, true)) for (panelWidth in listOf(360, 412)) for (fontScale in listOf(1f, 2f)) {
            compose.runOnIdle { night.value = dark; width.value = panelWidth; scale.value = fontScale }
            assertActionsFullyVisible()
            if (fontScale == 1f) {
                compose.onNodeWithText("173.9p/L").assertIsDisplayed()
                compose.onNodeWithText("Open 24 hours").assertIsDisplayed()
                compose.onNodeWithText("Filling 30 L would cost £52.17").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("excludes driving costs", substring = true).performScrollTo().assertIsDisplayed()
            }
            if (panelWidth == 360) {
                val name = "nearby-compact-${if (dark) "dark" else "light"}-${if (fontScale == 1f) "normal" else "large"}.png"
                compose.waitForIdle()
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name").use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
                }
            }
        }
    }
    @Test fun compactCardOpensFullNameAndSeparatePriceAndFeedAges() {
        showPanel()
        compose.onNodeWithTag("nearby-station-summary").performClick()
        compose.onNodeWithTag("nearby-station-details").assertExists()
        compose.onNodeWithTag("nearby-station-details").onChildren().filter(hasText(longName)).onFirst().assertIsDisplayed()
        compose.onNode(hasText("Filling 30 L would cost £52.17") and hasAnyAncestor(hasTestTag("nearby-station-details"))).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Price changed", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Prices checked", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Price history for $longName").performScrollTo().assertIsDisplayed()
    }
}
