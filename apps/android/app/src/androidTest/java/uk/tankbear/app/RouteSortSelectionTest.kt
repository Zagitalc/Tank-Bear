package uk.tankbear.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme
import kotlin.math.abs
import kotlin.math.roundToInt

class RouteSortSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: AppViewModel
    private var savedBefore: Saved? = null
    private val start = LatLon(51.4543, -0.9781)
    private val end = LatLon(51.7520, -1.2577)
    private fun candidate(id: String, name: String, rank: Int, price: String, seconds: String, point: LatLon) =
        Candidate(id, name, rank, price, "2026-10-31T12:00:00Z", "1200", seconds,
            5000, 100, 5100, 0, 100, 0, 0, point, encode(listOf(start, point, end)), OpeningInfo("open"))
    private val best = candidate("best", "Best total sample", 1, "166.9", "960", LatLon(51.50, -0.93))
    private val pump = candidate("pump", "Lowest pump sample", 2, "164.9", "600", LatLon(51.46, -0.89))
    private val least = candidate("least", "Least driving sample", 3, "169.9", "60", LatLon(51.49, -0.99))
    private val result = JourneyResult(true, "Synthetic route display test", 3, 3, 0, false,
        "2026-10-31T12:00:00Z", "least", "best", "pump", "least", listOf(least, best, pump), encode(listOf(start, end)))

    @Before fun setup() {
        vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        compose.runOnIdle {
            savedBefore = vm.saved
            vm.update { it.copy(mpg = "40", litres = "30") }
            vm.useExample()
            vm.journeyLookup = { _, _, _ -> ApiOutcome.Ok(result) }
        }
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    @After fun restore() { savedBefore?.let { saved -> runBlocking { Prefs(InstrumentationRegistry.getInstrumentation().targetContext).save(saved) } } }
    private fun load() {
        compose.runOnIdle { vm.submit() }
        compose.waitUntil(5000) { vm.search is SearchState.Done }
    }
    private fun sort(sort: RouteSort) {
        compose.onNode(hasText(sort.label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .performScrollTo().performClick().assertIsSelected()
        compose.waitForIdle()
    }
    private fun assertCard(candidate: Candidate) {
        compose.onNodeWithText("${candidate.rank}. ${candidate.name}", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(candidate.stationId, vm.selectedStationId); assertEquals(result, vm.result) }
    }
    @Test fun mapSortSelectsMatchingRouteAndCardAndRetappingActiveSortRefitsAndScrolls() {
        load()
        compose.runOnIdle { vm.mapJourney = true }
        compose.runOnUiThread { compose.activity.setContent { TankBearTheme { Surface { MapScreen(vm) } } } }
        var map: MapLibreMap? = null
        compose.waitUntil(15_000) {
            compose.runOnIdle { findMap(compose.activity.window.decorView)?.getMapAsync { map = it } }
            map != null
        }
        fun assertNative(candidate: Candidate) {
            compose.waitUntil(30_000) {
                var matches = false
                compose.runOnIdle {
                    val style = map?.style
                    val selected = style?.getSourceAs<GeoJsonSource>("station-src")?.querySourceFeatures(null).orEmpty()
                        .any { it.getStringProperty("id") == candidate.stationId && it.getBooleanProperty("selected") == true }
                    val route = style?.getSourceAs<GeoJsonSource>("route-src")?.querySourceFeatures(null).orEmpty()
                        .flatMap { (it.geometry() as? LineString)?.coordinates().orEmpty() }
                        .any { abs(it.latitude() - candidate.position.lat) < 0.002 && abs(it.longitude() - candidate.position.lon) < 0.002 }
                    val fitted = map?.projection?.visibleRegion?.latLngBounds?.contains(LatLng(candidate.position.lat, candidate.position.lon)) == true
                    matches = selected && route && fitted
                }
                matches
            }
            assertCard(candidate)
        }
        assertNative(best)
        sort(RouteSort.Detour); assertNative(least)
        sort(RouteSort.Pump); assertNative(pump)
        sort(RouteSort.Total); assertNative(best)
        compose.onNodeWithTag("route-results-list").performScrollToNode(hasText("3. ${least.name}"))
        compose.onNodeWithText("3. ${least.name}", useUnmergedTree = true).assertIsDisplayed()
        val version = vm.routeSelectionVersion
        compose.runOnIdle { map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(55.0, -4.0), 9.0)) }
        sort(RouteSort.Total)
        compose.runOnIdle { assertTrue(vm.routeSelectionVersion > version) }
        assertNative(best)
    }

    @Test fun findSortRevealsItsLeadingStationWithoutChangingBackendRanksOrCosts() {
        load()
        compose.runOnUiThread { compose.activity.setContent { TankBearApp(vm) } }
        compose.onNodeWithTag("journey-station-best").performScrollTo()
        sort(RouteSort.Detour); assertCard(least)
        sort(RouteSort.Total); assertCard(best)
        sort(RouteSort.Pump); assertCard(pump)
    }

    @Test fun sortRespectsOpenNowAndClearsSelectionWhenNothingMatches() {
        val unknown = result.copy(candidates = result.candidates.map { if (it.stationId == "least") it.copy(opening = OpeningInfo.UNKNOWN) else it })
        compose.runOnIdle { vm.journeyLookup = { _, _, _ -> ApiOutcome.Ok(unknown) }; vm.submit() }
        compose.waitUntil(5000) { vm.search is SearchState.Done }
        compose.runOnIdle { vm.mapOpenOnly = true }
        compose.runOnUiThread { compose.activity.setContent { TankBearTheme { Surface { MapResults(vm, onSelect = vm::selectStation) } } } }
        sort(RouteSort.Detour)
        compose.runOnIdle { assertEquals("pump", vm.selectedStationId) }
        compose.onNodeWithText("2. ${pump.name}", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle {
            vm.journeyLookup = { _, _, _ -> ApiOutcome.Ok(result.copy(candidates = result.candidates.map { it.copy(opening = OpeningInfo.UNKNOWN) })) }
            vm.submit()
        }
        compose.waitUntil(5000) { vm.search is SearchState.Done && vm.result?.candidates?.all { it.opening.state == "unknown" } == true }
        sort(RouteSort.Total)
        compose.runOnIdle { assertNull(vm.selectedStationId) }
        compose.onNodeWithTag("journey-station-best").assertDoesNotExist()
    }

    @Test fun newSearchSelectionFollowsTheExistingSort() {
        compose.runOnIdle { vm.chooseRouteSort(RouteSort.Detour) }
        load()
        compose.runOnIdle { assertEquals("least", vm.selectedStationId); assertEquals(result, vm.result) }
    }
    private fun findMap(view: android.view.View): MapView? {
        if (view is MapView) return view
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }
    /** Synthetic UI geometry only; no route calculation or real journey is represented. */
    private fun encode(points: List<LatLon>): String = buildString {
        var lat = 0; var lon = 0
        fun delta(value: Int) {
            var remaining = if (value < 0) (value shl 1).inv() else value shl 1
            while (remaining >= 0x20) { append(((remaining and 0x1f) or 0x20).plus(63).toChar()); remaining = remaining shr 5 }
            append((remaining + 63).toChar())
        }
        for (point in points) {
            val nextLat = (point.lat * 1_000_000).roundToInt(); val nextLon = (point.lon * 1_000_000).roundToInt()
            delta(nextLat - lat); delta(nextLon - lon); lat = nextLat; lon = nextLon
        }
    }
}
