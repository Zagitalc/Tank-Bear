package uk.tankbear.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme
import java.time.Instant

class RequestRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: AppViewModel
    private var savedBefore: Saved? = null
    private var now = Instant.parse("2026-10-31T12:00:00Z")
    private val empty = ApiOutcome.NearbyOk(NearbyResult("2026-10-31T12:00:00Z", emptyList()))

    @Before fun prepare() {
        vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        compose.runOnIdle {
            savedBefore = vm.saved
            vm.update { it.copy(mpg = "40", litres = "30") }
            vm.useExample()
            vm.mapNow = { now }
            vm.nearbyLookup = { _, _, _, _, _ -> empty }
            vm.mapNearbyLookup = { _, _, _, _, _ -> empty }
        }
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    @After fun restore() {
        savedBefore?.let { saved -> runBlocking { Prefs(InstrumentationRegistry.getInstrumentation().targetContext).save(saved) } }
    }
    private fun showApp() {
        compose.runOnUiThread { compose.activity.setContent { TankBearApp(vm) } }
    }

    @Test fun failureAppearsOnceAndCountdownSurvivesModeChangesWithoutEarlyRequests() {
        var journeyCalls = 0
        var nearbyCalls = 0
        var mapCalls = 0
        compose.runOnIdle {
            vm.journeyLookup = { _, _, _ -> journeyCalls++; ApiOutcome.Failure(FailureKind.RateLimited, "Sample server is busy.", 3) }
            vm.nearbyLookup = { _, _, _, _, _ -> nearbyCalls++; empty }
            vm.mapNearbyLookup = { _, _, _, _, _ -> mapCalls++; empty }
            vm.submit()
        }
        compose.waitUntil(5000) { vm.search is SearchState.Failed }
        showApp()
        compose.onAllNodesWithText("Sample server is busy.").assertCountEquals(1)
        compose.onNodeWithText("Try again in 3s").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle {
            vm.submit()
            vm.loadMapNearby(force = true)
            assertEquals(1, journeyCalls)
            assertEquals(0, mapCalls)
            now = now.plusMillis(2100)
        }
        compose.waitUntil(3000) { compose.onAllNodesWithText("Try again in 1s").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Nearby stations (no journey)").performScrollTo().performClick()
        compose.onNodeWithText("Try again in 1s").assertIsNotEnabled()
        compose.runOnIdle {
            vm.submit()
            assertEquals(0, nearbyCalls)
            now = now.plusMillis(900)
        }
        compose.waitUntil(3000) { compose.onAllNodesWithText("Find nearby stations").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Find nearby stations").assertIsEnabled().performClick()
        compose.waitUntil(5000) { vm.nearby is NearbyState.Done }
        compose.runOnIdle { assertEquals(1, nearbyCalls); vm.loadMapNearby(force = true) }
        compose.waitUntil(5000) { vm.mapNearby is NearbyState.Done }
        compose.runOnIdle { assertEquals(1, mapCalls) }
    }

    @Test fun nearbyFailureBlocksJourneyTooAndNoDelayAllowsImmediateRetry() {
        var nearbyCalls = 0
        var journeyCalls = 0
        compose.runOnIdle {
            vm.mode = Mode.Nearby
            vm.nearbyLookup = { _, _, _, _, _ -> nearbyCalls++; ApiOutcome.Failure(FailureKind.RateLimited, "Wait for nearby.", 2) }
            vm.journeyLookup = { _, _, _ -> journeyCalls++; ApiOutcome.Failure(FailureKind.Network, "Sample offline.") }
            vm.submit()
        }
        compose.waitUntil(5000) { vm.nearby is NearbyState.Failed }
        showApp()
        compose.onAllNodesWithText("Wait for nearby.").assertCountEquals(1)
        compose.onNodeWithText("Try again in 2s").assertIsNotEnabled()
        compose.runOnIdle {
            vm.mode = Mode.AlongJourney
            vm.submit()
            assertEquals(0, journeyCalls)
            assertEquals(1, nearbyCalls)
            now = now.plusSeconds(2)
            vm.submit()
        }
        compose.waitUntil(5000) { vm.search is SearchState.Failed }
        compose.onAllNodesWithText("Sample offline.").assertCountEquals(1)
        compose.waitUntil(3000) { compose.onAllNodesWithText("Try search again").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Try search again").assertIsEnabled().performClick()
        compose.waitUntil(5000) { journeyCalls == 2 && vm.search is SearchState.Failed }
    }

    @Test fun findAndNearbyLoadingHaveOneReachableCancel() {
        val pending = CompletableDeferred<ApiOutcome>()
        compose.runOnIdle { vm.journeyLookup = { _, _, _ -> pending.await() }; vm.submit() }
        showApp()
        compose.onAllNodesWithText("Checking stations and routes…").assertCountEquals(1)
        compose.onAllNodesWithText("Cancel search").assertCountEquals(1)
        compose.onNodeWithText("Cancel search").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(SearchState.Idle, vm.search); pending.complete(ApiOutcome.Failure(FailureKind.Network, "Old result")) }
        val nearbyPending = CompletableDeferred<ApiOutcome>()
        compose.runOnIdle { vm.mode = Mode.Nearby; vm.nearbyLookup = { _, _, _, _, _ -> nearbyPending.await() }; vm.submit() }
        compose.onAllNodesWithText("Checking nearby stations…").assertCountEquals(1)
        compose.onNodeWithText("Cancel search").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(NearbyState.Idle, vm.nearby); nearbyPending.complete(empty) }
        compose.onNodeWithText("Old result").assertDoesNotExist()
    }

    @Test fun mapCancelIgnoresLateResultAndRefreshCanRestart() {
        val pending = CompletableDeferred<ApiOutcome>()
        var calls = 0
        compose.runOnIdle {
            vm.mapNearbyLookup = { _, _, _, _, _ -> calls++; if (calls == 1) withContext(NonCancellable) { pending.await() } else empty }
            vm.loadMapNearby()
        }
        compose.runOnUiThread {
            compose.activity.setContent { TankBearTheme { Surface { Box(Modifier.width(360.dp).height(300.dp)) {
                NearbyMapPanel(vm, (vm.currentMapNearby as? NearbyState.Done)?.result, {})
            } } } }
        }
        compose.onNodeWithText("Checking nearby stations…").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.onNodeWithText("Search cancelled. Tap Refresh to try again.").assertIsDisplayed()
        compose.runOnIdle { pending.complete(empty) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(NearbyState.Idle, vm.mapNearby) }
        compose.onNodeWithText("Refresh").assertIsEnabled().performClick()
        compose.waitUntil(5000) { vm.currentMapNearby is NearbyState.Done }
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun emptyNearbyResultsOfferLocationPickerWithoutRequestingPermission() {
        compose.runOnIdle { vm.mode = Mode.Nearby; vm.submit() }
        compose.waitUntil(5000) { vm.nearby is NearbyState.Done }
        showApp()
        compose.onNodeWithText("Change start").performScrollTo().performClick()
        compose.onNodeWithText("Choose start on map").assertIsDisplayed()
        compose.runOnIdle { assertFalse(vm.choosingMapLocation) }
    }

    @Test fun mapCountdownBlocksFindAndRemainsReadableWithLargeText() {
        var mapCalls = 0
        var journeyCalls = 0
        val dark = mutableStateOf(false)
        val scale = mutableStateOf(1f)
        val width = mutableStateOf(360)
        compose.runOnIdle {
            vm.mapNearbyLookup = { _, _, _, _, _ -> mapCalls++; if (mapCalls == 1) ApiOutcome.Failure(FailureKind.RateLimited, "Sample map is busy.", 3) else empty }
            vm.journeyLookup = { _, _, _ -> journeyCalls++; ApiOutcome.Failure(FailureKind.Network, "Sample offline.") }
            vm.loadMapNearby()
        }
        compose.waitUntil(5000) { vm.currentMapNearby is NearbyState.Failed }
        compose.runOnUiThread {
            compose.activity.setContent {
                val configuration = android.content.res.Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark.value) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides configuration,
                    LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                    TankBearTheme { Surface { Box(Modifier.width(width.value.dp).height(380.dp)) {
                        NearbyMapPanel(vm, (vm.currentMapNearby as? NearbyState.Done)?.result, {})
                    } } }
                }
            }
        }
        for (night in listOf(false, true)) for (panelWidth in listOf(360, 412)) for (font in listOf(1f, 2f)) {
            compose.runOnIdle { dark.value = night; width.value = panelWidth; scale.value = font }
            compose.onAllNodesWithText("Sample map is busy.").assertCountEquals(1)
            compose.onNodeWithText("Try again in 3s").assertIsDisplayed().assertIsNotEnabled()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("Try again in 3s", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Countdown must wrap without clipping", layouts.single().hasVisualOverflow)
        }
        compose.runOnIdle { vm.submit(); vm.loadMapNearby(force = true); assertEquals(0, journeyCalls); assertEquals(1, mapCalls); now = now.plusSeconds(3) }
        compose.waitUntil(3000) { compose.onAllNodesWithText("Try again").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Try again").assertIsEnabled().performClick()
        compose.waitUntil(5000) { vm.mapNearby is NearbyState.Done }
        compose.runOnIdle { assertEquals(2, mapCalls) }
    }
}
