package uk.tankbear.app

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.JourneyStationCard
import uk.tankbear.app.ui.theme.TankBearTheme
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class JourneyCardPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val longName = "Long supplied trading name for the public sample service station"
    // Sample amounts intentionally include independent rounding: 5000 + 127 != 5128.
    private val stop = Candidate("stop", longName, 1, "125.01", "2026-10-30T12:00:00Z", "1609.344", "180",
        5000, 127, 5128, 100, 46, 18, 0, LatLon(51.4543, -0.9781), "sample", OpeningInfo("open"))
    private val reference = stop.copy(stationId = "ref", name = "Reference sample", rank = 2, pencePerLitre = "127.5", comparisonCostPence = 5146)
    private val initial = JourneyResult(true, "Best among stations checked", 2, 2, 0, false,
        "2026-10-31T11:00:00Z", "ref", "stop", "ref", "ref", listOf(stop, reference), "sample baseline")
    private val result = mutableStateOf(initial)
    private val dark = mutableStateOf(false)
    private val scale = mutableStateOf(1f)
    private val width = mutableStateOf(360)
    private val history = mutableStateOf(false)
    private var routeClicks = 0
    private var selectClicks = 0

    private fun snapshot(name: String) {
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureToImage()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/$name.png")
            .use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
    }

    private fun show(mapCard: Boolean = false, candidate: Candidate = stop) {
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                    TankBearTheme { Surface {
                        Column(Modifier.width(width.value.dp).fillMaxHeight().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
                            JourneyStationCard(candidate, result.value, now, litres = "40", selected = mapCard,
                                onSelect = if (mapCard) ({ selectClicks++ }) else null,
                                onMap = if (mapCard) null else ({ routeClicks++ }), historyVisible = history.value,
                                onToggleHistory = { history.value = !history.value }) {
                                if (history.value) androidx.compose.material3.Text("Sample price history")
                            }
                        }
                    } }
                }
            }
        }
    }
    @Test fun totalsSmallDifferenceAndNamedReferenceStayVisibleWhileBreakdownIsExpandable() {
        show()
        compose.onNodeWithText("£51.28").assertIsDisplayed()
        compose.onNodeWithText("Estimated total for 40 L").assertIsDisplayed()
        compose.onNodeWithText("125.01p/L").assertIsDisplayed()
        compose.onNodeWithText("Extra driving: 3 min · 1.0 mi").assertIsDisplayed()
        compose.onNodeWithText("Saves about 18p vs Reference sample").assertIsDisplayed()
        compose.onNodeWithText("A difference this small is within the estimate's margin.").assertIsDisplayed()
        snapshot("journey-card-summary")
        compose.onNodeWithText("Fill: £50.00").assertDoesNotExist()
        compose.onNodeWithText("Price history").assertDoesNotExist()
        compose.onNodeWithText("Details").performScrollTo().performClick()
        compose.onNodeWithText(longName).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fill: £50.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Extra driving fuel: £1.27").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Estimated total: £51.28 (rounded)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Line items and totals", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Comparison station: Reference sample").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Comparison station's driving fuel: 46p").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Prices checked 1 h ago. Price changes are shown separately.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Price history").performScrollTo().performClick()
        compose.onNodeWithText("Sample price history").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hide details").performScrollTo().performClick()
        compose.onNodeWithText("Sample price history").assertDoesNotExist()
        compose.onNodeWithText("View route").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, routeClicks) }
    }
    @Test fun modeWordingNeverSubstitutesExtraDetourForReturnOrOneWayDriving() {
        show()
        compose.runOnIdle { result.value = initial.copy(baselineGeometry = null) }
        compose.onNodeWithText("There and back: 3 min · 1.0 mi").assertIsDisplayed()
        compose.onNodeWithText("Extra driving", substring = true).assertDoesNotExist()
        compose.runOnIdle { result.value = initial.copy(baselineGeometry = null, oneWay = true) }
        compose.onNodeWithText("One-way driving: 3 min · 1.0 mi").assertIsDisplayed()
        compose.onNodeWithText("There and back", substring = true).assertDoesNotExist()
    }
    @Test fun cardActionsAndAmountsRemainReachableInBothThemesAndLargeText() {
        show()
        for (night in listOf(false, true)) for (panelWidth in listOf(360, 412)) for (font in listOf(1f, 2f)) {
            compose.runOnIdle { dark.value = night; width.value = panelWidth; scale.value = font }
            compose.onNodeWithText("£51.28").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("125.01p/L").performScrollTo().assertIsDisplayed()
            if (night && panelWidth == 360 && font == 2f) snapshot("journey-card-large-dark")
            for (label in listOf("Details", "View route")) {
                val button = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                val bounds = button.getUnclippedBoundsInRoot()
                assertTrue(bounds.bottom - bounds.top >= 48.dp)
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse("$label clips at $panelWidth dp / $font", layouts.single().hasVisualOverflow)
            }
        }
    }
    @Test fun selectedReferenceCardKeepsMapSelectionWithoutAdvertisingASaving() {
        show(mapCard = true, candidate = reference)
        compose.onNodeWithText("This is the comparison station").assertIsDisplayed()
        compose.onNodeWithText("Selected on map").assertIsDisplayed()
        compose.onNodeWithText("Saves about", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("journey-station-ref").performClick()
        compose.runOnIdle { assertEquals(1, selectClicks) }
    }
}
