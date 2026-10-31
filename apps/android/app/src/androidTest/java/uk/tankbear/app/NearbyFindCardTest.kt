package uk.tankbear.app

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme
import java.time.Instant

class NearbyFindCardTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val name = "Long supplied trading name for the public sample service station"
    private val initial = NearbyStation("sample", name, "Sample brand", LatLon(51.4543, -0.9781), 1400, false,
        OpeningInfo("open", is24Hours = true), NearbyFill("173.9", "2026-10-30T12:00:00Z", 5217, "-2"))
    private val station = mutableStateOf(initial)
    private val dark = mutableStateOf(false)
    private val width = mutableStateOf(360)
    private val scale = mutableStateOf(1f)
    private val history = mutableStateOf(false)
    private var savedBefore: Saved? = null

    @After fun restore() { savedBefore?.let { saved -> runBlocking { Prefs(InstrumentationRegistry.getInstrumentation().targetContext).save(saved) } } }

    private fun show(cheapest: Boolean = true, content: @Composable () -> Unit = {
        NearbyStationCard(station.value, cheapest, "30", "2026-10-31T11:00:00Z", now, history.value, { history.value = !history.value }) {
            if (history.value) Text("Sample history content")
        }
    }) {
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides configuration,
                    LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                    TankBearTheme { Surface {
                        Column(Modifier.width(width.value.dp).fillMaxHeight().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp)) { content() }
                    } }
                }
            }
        }
    }

    @Test fun summaryKeepsPricesAndCaveatsWhileDetailsRevealNameComparisonAndHistory() {
        show()
        for (label in listOf("Cheapest among stations checked", "173.9p/L", "Fill for 30 L", "£52.17", "Open 24 hours", "0.9 mi away · straight line", "Fill estimate excludes driving costs.", "Price changed yesterday")) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithText("2p/L below the local median").assertDoesNotExist()
        compose.onNodeWithText("Price history").assertDoesNotExist()
        compose.onNodeWithText("Details").performClick()
        compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag("nearby-result-details-sample"))).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sample brand").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2p/L below the local median").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Prices checked 1 h ago. Price changes are shown separately.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Price history").performScrollTo().performClick()
        compose.onNodeWithText("Sample history content").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hide details").performScrollTo().performClick()
        compose.onNodeWithText("Sample history content").assertDoesNotExist()
    }

    @Test fun missingPriceAndTemporaryClosureNeverInventAnEstimateOrOpenStatus() {
        station.value = initial.copy(fill = null, temporaryClosure = true)
        show(cheapest = false)
        compose.onNodeWithText("Cheapest among stations checked").assertDoesNotExist()
        compose.onNodeWithText("No price for your fuel here.").assertIsDisplayed()
        compose.onNodeWithText("Reported temporarily closed").assertIsDisplayed()
        compose.onNodeWithText("Open 24 hours").assertDoesNotExist()
        compose.onNodeWithText("£52.17").assertDoesNotExist()
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithText("Price history").assertDoesNotExist()
        compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag("nearby-result-details-sample"))).performScrollTo().assertIsDisplayed()
    }

    @Test fun amountsAndDetailsStayReadableAcrossThemesWidthsAndLargeText() {
        show()
        for (night in listOf(false, true)) for (panelWidth in listOf(360, 412)) for (font in listOf(1f, 2f)) {
            compose.runOnIdle { dark.value = night; width.value = panelWidth; scale.value = font }
            compose.onNodeWithText("173.9p/L").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("£52.17").performScrollTo().assertIsDisplayed()
            val action = compose.onNodeWithText("Details").performScrollTo().assertIsDisplayed()
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top >= 48.dp)
            for (label in listOf("Details", "173.9p/L", "£52.17")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse("$label clips at $panelWidth dp / $font", layouts.single().hasVisualOverflow)
            }
        }
    }

    @Test fun sortingKeepsExpandedDetailsWithTheirStationAndExcludesClosedFromCheapest() {
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        val cheap = initial.copy(id = "cheap", name = "Cheaper sample", distanceMetres = 2000, fill = initial.fill!!.copy(pencePerLitre = "168.9", fillCostPence = 5067))
        val closed = initial.copy(id = "closed", name = "Closed sample", distanceMetres = 3000, temporaryClosure = true, fill = initial.fill!!.copy(pencePerLitre = "100.0", fillCostPence = 3000))
        compose.runOnIdle {
            savedBefore = vm.saved
            vm.update { it.copy(mpg = "40", litres = "30") }
            vm.mode = Mode.Nearby
            vm.useExample()
            vm.nearbyLookup = { _, _, _, _, _ -> ApiOutcome.NearbyOk(NearbyResult("2026-10-31T11:00:00Z", listOf(initial, cheap, closed))) }
            vm.submit()
        }
        compose.waitUntil(5000) { vm.nearby is NearbyState.Done }
        show { FindScreen(vm, {}) }
        compose.onNode(hasText("Details") and hasAnyAncestor(hasTestTag("nearby-result-sample"))).performScrollTo().performClick()
        compose.onNodeWithTag("nearby-result-details-sample").assertExists()
        compose.onNodeWithText("Cheapest").performScrollTo().performClick()
        compose.onNodeWithTag("nearby-result-details-sample").assertExists()
        compose.onNode(hasText("Cheapest among stations checked") and hasAnyAncestor(hasTestTag("nearby-result-cheap"))).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Cheapest among stations checked") and hasAnyAncestor(hasTestTag("nearby-result-closed"))).assertDoesNotExist()
    }
}
