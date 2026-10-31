package uk.tankbear.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.*

class FindResultsVisibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: AppViewModel
    private var savedBefore: Saved? = null
    @Before fun setup() {
        vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        compose.runOnIdle { savedBefore = vm.saved; vm.update { it.copy(mpg = "40", litres = "30") }; vm.useExample() }
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setContent { TankBearApp(vm) }
        }
    }
    @After fun restore() { savedBefore?.let { saved -> runBlocking { Prefs(InstrumentationRegistry.getInstrumentation().targetContext).save(saved) } } }
    private fun verify(mode: Mode) {
        val pending = CompletableDeferred<ApiOutcome>()
        val point = LatLon(51.4543, -0.9781)
        val name = "Public visibility sample"
        val time = "2026-10-31T12:00:00Z"
        val candidate = Candidate("sample", name, 1, "173.9", time, "300", "60", 5217, 10, 5227, 0, 10, 0, 0, point, "sample", OpeningInfo("open"))
        val result = if (mode == Mode.Nearby) ApiOutcome.NearbyOk(NearbyResult(time,
            listOf(NearbyStation("sample", name, null, point, 300, false, OpeningInfo("open"), NearbyFill("173.9", time, 5217, null)))))
            else ApiOutcome.Ok(JourneyResult(true, "Sample journey", 1, 1, 0, false, time, "sample", "sample", "sample", "sample", listOf(candidate), "sample baseline"))
        compose.runOnIdle { vm.mode = mode; vm.nearbyLookup = { _, _, _, _, _ -> pending.await() }; vm.journeyLookup = { _, _, _ -> pending.await() } }
        compose.onNodeWithText(if (mode == Mode.Nearby) "Find nearby stations" else "Find the best stop").performClick()
        compose.runOnIdle { pending.complete(result) }
        compose.waitUntil(5000) { vm.findResultCount == 1 }
        compose.waitForIdle()
        // No performScrollTo: completion must reveal an actual station, not just a heading.
        compose.onNodeWithText(if (mode == Mode.Nearby) name else "1. $name").assertIsDisplayed()
        compose.onNodeWithText("Make your fuel stop count").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("View 1 station result").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(if (mode == Mode.Nearby) name else "1. $name").assertIsDisplayed()
        compose.runOnIdle { vm.update { it.copy(litres = "31") } }
        compose.onNodeWithText("View 1 station result").assertDoesNotExist()
        compose.onNodeWithText("Update results").assertIsDisplayed()
    }
    @Test fun nearbyCompletionAndViewResultsRevealTheFirstStation() = verify(Mode.Nearby)
    @Test fun journeyCompletionAndViewResultsRevealTheFirstStation() = verify(Mode.AlongJourney)
}
