package uk.tankbear.app

import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import uk.tankbear.app.ui.theme.TankBearTheme
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.*
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class NearbyMapStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var savedBefore: Saved? = null
    @After fun restorePreferences() {
        savedBefore?.let { saved -> runBlocking { Prefs(InstrumentationRegistry.getInstrumentation().targetContext).save(saved) } }
    }
    private fun model(): AppViewModel {
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        compose.runOnIdle { savedBefore = vm.saved; vm.update { it.copy(mpg = "40", litres = "30") }; vm.useExample() }
        return vm
    }
    private fun result(marker: String) = ApiOutcome.NearbyOk(NearbyResult(marker, emptyList()))

    @Test fun deduplicatesRequestsAndDiscardsOldResponsesEvenAfterReturningToSameStart() {
        val vm = model()
        val requests = mutableListOf<CompletableDeferred<ApiOutcome>>()
        compose.runOnIdle {
            vm.mapNearbyLookup = { _, _, _, _, _ -> CompletableDeferred<ApiOutcome>().also { requests.add(it) }.let { withContext(NonCancellable) { it.await() } } }
            vm.loadMapNearby()
        }
        compose.waitUntil(5000) { requests.size == 1 }
        compose.runOnIdle { vm.loadMapNearby(); vm.loadMapNearby(force = true) }
        assertEquals(1, requests.size)
        compose.runOnIdle { vm.originLat = "51.50000"; vm.loadMapNearby() }
        compose.waitUntil(5000) { requests.size == 2 }
        compose.runOnIdle { vm.originLat = "51.4543"; vm.loadMapNearby() }
        compose.waitUntil(5000) { requests.size == 3 }
        compose.runOnIdle { requests[0].complete(result("old")); requests[1].complete(result("other")) }
        compose.runOnIdle { assertEquals(NearbyState.Loading, vm.mapNearby); requests[2].complete(result("new")) }
        compose.waitUntil(5000) { vm.mapNearby is NearbyState.Done }
        compose.runOnIdle { assertEquals("new", (vm.mapNearby as NearbyState.Done).result.feedLastSuccessfulRefresh); vm.loadMapNearby() }
        assertEquals(3, requests.size)
        compose.runOnIdle { vm.originLat = "52.0"; assertEquals(NearbyState.Idle, vm.currentMapNearby) }
    }

    @Test fun nearbySheetFiltersStationsWithoutChangingFindSelection() {
        val vm = model()
        val point = LatLon(51.4543, -0.9781)
        val price = NearbyFill("145.9", "2026-10-01T00:00:00Z", 4377, "-2")
        val closed = NearbyStation("closed", "Closed sample", null, point, 100, true, OpeningInfo("closed"), price)
        val open = NearbyStation("open", "Open sample", null, point.copy(lon = -0.98), 300, false, OpeningInfo("open"), price)
        compose.runOnIdle {
            vm.selectStation("journey-selection")
            vm.mapNearbyLookup = { _, _, _, _, _ -> ApiOutcome.NearbyOk(NearbyResult("2026-10-01T00:00:00Z", listOf(closed, open), "147.9")) }
        }
        compose.runOnUiThread { compose.activity.setContent { TankBearTheme { Surface { MapScreen(vm) } } } }
        compose.waitUntil(10_000) { vm.currentMapNearby is NearbyState.Done }
        compose.onNodeWithText("Open sample", useUnmergedTree = false).assertIsDisplayed()
        var map: org.maplibre.android.maps.MapLibreMap? = null
        fun findMap(view: android.view.View): org.maplibre.android.maps.MapView? {
            if (view is org.maplibre.android.maps.MapView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.waitUntil(30_000) {
            var rendered = false
            compose.runOnIdle {
                val view = findMap(compose.activity.window.decorView)
                if (view != null && map == null) view.getMapAsync { map = it }
                if (view != null && map?.style?.isFullyLoaded == true) {
                    rendered = map!!.queryRenderedFeatures(android.graphics.RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())).any { it.hasProperty("name") }
                }
            }
            rendered
        }
        capture("stage11-nearby-panel.png")
        compose.onNodeWithText("Stations").assertIsDisplayed().performClick()
        compose.onNodeWithTag("station-list-closed").assertIsDisplayed()
        capture("stage11-nearby-sheet.png")
        compose.onNodeWithTag("nearby-open-now").performScrollTo().performClick()
        compose.onNodeWithTag("station-list-closed").assertDoesNotExist()
        compose.onNodeWithTag("station-list-open").performClick()
        compose.waitUntil(5000) { vm.mapSelectedStationId == "open" }
        compose.onNodeWithText("Open sample").assertIsDisplayed()
        compose.runOnIdle { assertEquals("journey-selection", vm.selectedStationId); assertFalse(vm.mapOpenOnly); assertTrue(vm.mapNearbyOpenOnly) }
    }

    @Test fun findRouteOpensJourneyMapAndMapTabReturnsToNearby() {
        val vm = model()
        val point = LatLon(51.4543, -0.9781)
        val line = "_hgcaB~oez@_}hQ~zaP"
        val station = Candidate("route", "Route sample", 1, "145.9", "2026-10-01T00:00:00Z", "300", "60", 4377, 10, 4387, 0, 10, 0, 0, point, line, OpeningInfo("open"))
        val route = JourneyResult(true, "Sample journey", 1, 1, 0, false, "2026-10-01T00:00:00Z", "route", "route", "route", "route", listOf(station), line)
        var nearbyCalls = 0
        compose.runOnIdle {
            vm.journeyLookup = { _, _, _ -> ApiOutcome.Ok(route) }
            vm.mapNearbyLookup = { _, _, _, _, _ -> nearbyCalls++; result("2026-10-01T00:00:00Z") }
            vm.submit()
        }
        compose.waitUntil(5000) { vm.search is SearchState.Done }
        compose.runOnUiThread { compose.activity.setContent { TankBearApp(vm) } }
        compose.onNodeWithText("Show route on map").performScrollTo().performClick()
        compose.onNodeWithText("Back to Find").assertIsDisplayed()
        compose.onNodeWithText("Fit route").assertIsDisplayed()
        compose.runOnIdle { assertTrue(vm.mapJourney); assertEquals("route", vm.selectedStationId); assertEquals(0, nearbyCalls) }
        compose.onNodeWithText("Map").performClick()
        compose.waitUntil(5000) { vm.currentMapNearby is NearbyState.Done }
        compose.runOnIdle { assertFalse(vm.mapJourney); assertEquals(1, nearbyCalls); assertTrue(vm.search is SearchState.Done); assertEquals("route", vm.selectedStationId) }
    }

    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    @Test fun retryDelayAndMapCancellationDoNotReplaceJourneyState() {
        val vm = model()
        var now = Instant.parse("2026-10-01T00:00:00Z")
        val journey = CompletableDeferred<ApiOutcome>()
        var calls = 0
        compose.runOnIdle {
            vm.mapNow = { now }
            vm.journeyLookup = { _, _, _ -> journey.await() }
            vm.mapNearbyLookup = { _, _, _, _, _ -> calls++; if (calls == 1) ApiOutcome.Failure(FailureKind.RateLimited, "Wait", 30) else result("ok") }
            vm.submit()
            vm.loadMapNearby()
        }
        compose.waitUntil(5000) { vm.mapNearby is NearbyState.Failed }
        compose.runOnIdle {
            assertEquals(SearchState.Loading, vm.search)
            assertFalse(vm.mapRetryAvailable)
            vm.loadMapNearby(force = true)
            assertEquals(1, calls)
            now = now.plusSeconds(30)
            assertTrue(vm.mapRetryAvailable)
            vm.loadMapNearby(force = true)
        }
        compose.waitUntil(5000) { vm.mapNearby is NearbyState.Done }
        compose.runOnIdle {
            assertEquals(SearchState.Loading, vm.search)
            journey.complete(ApiOutcome.Failure(FailureKind.NoRoute, "No route"))
        }
        compose.waitUntil(5000) { vm.search is SearchState.Failed }
        compose.runOnIdle { assertTrue(vm.mapNearby is NearbyState.Done) }
    }
}
