package uk.tankbear.app

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.sources.GeoJsonSource
import androidx.compose.ui.graphics.toArgb
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme

/** Live map-provider compatibility check using only the public Reading/Oxford example. */
@RunWith(AndroidJUnit4::class)
class MapThemeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var savedBefore: uk.tankbear.app.data.Saved? = null
    @After fun restoreDebugPreferences() {
        savedBefore?.let { saved ->
            kotlinx.coroutines.runBlocking {
                uk.tankbear.app.data.Prefs(InstrumentationRegistry.getInstrumentation().targetContext).save(saved)
            }
        }
    }
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun darkStyleRestoresRoutesPinsAndPickerWithoutMovingCamera() {
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val dark = mutableStateOf(false)
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        compose.runOnIdle { savedBefore = vm.saved }
        compose.runOnIdle {
            vm.update { it.copy(mpg = "40", litres = "30", fuel = uk.tankbear.app.data.FuelType.E10) }
            vm.useExample()
            vm.submit()
        }
        compose.waitUntil(60_000) { !vm.loading }
        compose.runOnIdle { assertTrue("Public example journey should load", vm.search is SearchState.Done) }
        compose.runOnUiThread {
            compose.activity.setContent {
                val configuration = Configuration(LocalConfiguration.current)
                configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    TankBearTheme { Surface { MapScreen(vm, Modifier.fillMaxSize()) } }
                }
            }
        }
        var map: MapLibreMap? = null
        compose.waitUntil(10_000) {
            compose.runOnIdle { findMap(compose.activity.window.decorView)?.getMapAsync { map = it } }
            map != null
        }
        fun waitForStyle(url: String) {
            compose.waitUntil(60_000) {
                var ready = false
                compose.runOnIdle {
                    val style = map?.style
                    ready = style?.uri == url && style.getLayer("route-line") != null &&
                        style.getSourceAs<GeoJsonSource>("route-src")?.querySourceFeatures(null)?.isNotEmpty() == true &&
                        style.getSourceAs<GeoJsonSource>("pins-src") != null &&
                        style.getSourceAs<GeoJsonSource>("station-src")?.querySourceFeatures(null)?.isNotEmpty() == true
                }
                ready
            }
        }
        waitForStyle(MapConfig.STYLE_URL)
        compose.waitUntil(10_000) {
            var roles = emptySet<String>()
            compose.runOnIdle {
                roles = map!!.style!!.getSourceAs<GeoJsonSource>("pins-src")!!.querySourceFeatures(null).map { it.getStringProperty("role") }.toSet()
            }
            roles == setOf("start", "destination")
        }
        fun waitForVisibleMarker(id: String, selected: Boolean? = null, theme: String = "light"): org.maplibre.geojson.Feature {
            var feature: org.maplibre.geojson.Feature? = null
            compose.waitUntil(60_000) {
                compose.runOnIdle {
                    val view = findMap(compose.activity.window.decorView)!!
                    feature = map!!.queryRenderedFeatures(android.graphics.RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                        "station-layer", "station-selected").firstOrNull { it.getStringProperty("id") == id &&
                            it.getStringProperty("markerTheme") == theme &&
                            (selected == null || it.getBooleanProperty("selected") == selected) }
                }
                feature != null
            }
            return feature!!
        }
        fun capture(name: String) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        // The fit must contain the whole selected pill, not just its geographic anchor.
        val initiallySelected = vm.result!!.candidates.first { it.stationId == vm.selectedStationId }
        val fittedMarker = waitForVisibleMarker(initiallySelected.stationId, true)
        compose.waitUntil(10_000) {
            var inside = false
            compose.runOnIdle {
                val view = findMap(compose.activity.window.decorView)!!
                val image = map!!.style!!.getImage(fittedMarker.getStringProperty("markerImage"))!!
                val point = map!!.projection.toScreenLocation(LatLng(initiallySelected.position.lat, initiallySelected.position.lon))
                val ratio = image.density / 160f
                val density = compose.activity.resources.displayMetrics.density
                val halfWidth = image.width / ratio * density / 2
                val height = image.height / ratio * density
                inside = point.x - halfWidth >= 0 && point.x + halfWidth <= view.width &&
                    point.y - height >= 0 && point.y <= view.height
            }
            inside
        }
        // Check actual placement, including selection versus nearby labels; checking
        // iconAllowOverlap flags alone misses symbol-layer priority mistakes.
        compose.waitUntil(10_000) {
            var clear = false
            compose.runOnIdle {
                val view = findMap(compose.activity.window.decorView)!!
                val density = compose.activity.resources.displayMetrics.density
                val labels = map!!.queryRenderedFeatures(android.graphics.RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                    "station-layer", "station-selected").distinctBy { it.getStringProperty("id") }
                fun bounds(f: org.maplibre.geojson.Feature): android.graphics.RectF {
                    val point = f.geometry() as org.maplibre.geojson.Point
                    val screen = map!!.projection.toScreenLocation(LatLng(point.latitude(), point.longitude()))
                    val image = map!!.style!!.getImage(f.getStringProperty("markerImage"))!!
                    val scale = density / (image.density / 160f)
                    return android.graphics.RectF(screen.x - image.width * scale / 2, screen.y - image.height * scale,
                        screen.x + image.width * scale / 2, screen.y)
                }
                val selectedLabel = labels.firstOrNull { it.getBooleanProperty("selected") }
                clear = selectedLabel != null && labels.filter { !it.getBooleanProperty("selected") }
                    .none { android.graphics.RectF.intersects(bounds(selectedLabel), bounds(it)) }
            }
            clear
        }
        // A real rendered label must remain tappable; source presence alone is insufficient.
        val alternative = vm.result!!.candidates.first { it.stationId != vm.selectedStationId }
        compose.runOnIdle {
            map!!.cancelTransitions()
            map!!.cameraPosition = CameraPosition.Builder().target(LatLng(alternative.position.lat, alternative.position.lon)).zoom(13.0).build()
        }
        waitForVisibleMarker(alternative.stationId)
        var tap = Offset.Zero
        compose.runOnIdle {
            val point = map!!.projection.toScreenLocation(LatLng(alternative.position.lat, alternative.position.lon))
            tap = Offset(point.x, point.y - 20f * compose.activity.resources.displayMetrics.density)
        }
        compose.onNodeWithTag("route-map").performTouchInput { click(tap) }
        compose.waitUntil(10_000) { vm.selectedStationId == alternative.stationId }
        waitForVisibleMarker(alternative.stationId, true)
        capture("map-prices-light.png")
        val selected = vm.selectedStationId
        compose.runOnIdle {
            assertNotNull(selected)
            vm.beginMapChoice(MapTarget.Destination)
        }
        compose.waitForIdle()
        // Move to a public sample viewpoint after picker entry has positioned the camera.
        Thread.sleep(600)
        val viewpoint = CameraPosition.Builder().target(LatLng(alternative.position.lat, alternative.position.lon)).zoom(12.0).bearing(23.0).tilt(15.0).build()
        compose.runOnIdle { map!!.cancelTransitions(); map!!.cameraPosition = viewpoint; dark.value = true }
        waitForStyle(MapConfig.DARK_STYLE_URL)
        val darkMarker = waitForVisibleMarker(selected!!, true, "dark")
        compose.runOnIdle {
            assertEquals(selected, vm.selectedStationId)
            assertTrue(vm.choosingMapLocation)
            assertEquals(MapTarget.Destination, vm.mapTarget)
            assertEquals(viewpoint.zoom, map!!.cameraPosition.zoom, 0.01)
            assertEquals(viewpoint.bearing, map!!.cameraPosition.bearing, 0.01)
            assertEquals(viewpoint.tilt, map!!.cameraPosition.tilt, 0.01)
            assertEquals(viewpoint.target!!.latitude, map!!.cameraPosition.target!!.latitude, 0.00001)
            assertTrue(map!!.uiSettings.isAttributionEnabled)
            assertEquals(uk.tankbear.app.ui.MapPrices.label(alternative.pencePerLitre), darkMarker.getStringProperty("priceLabel"))
            val image = map!!.style!!.getImage(darkMarker.getStringProperty("markerImage"))!!
            val colors = uk.tankbear.app.ui.theme.DarkTankBearColors
            val expected = when (darkMarker.getStringProperty("priceBand")) {
                "Below" -> colors.pumpBelow
                "Above" -> colors.pumpAbove
                "Around" -> colors.pumpAround
                else -> colors.pumpUnknown
            }
            assertEquals(expected.toArgb(), image.getPixel(image.width / 2, 8))
            assertEquals(false, (map!!.style!!.getLayer("station-layer") as org.maplibre.android.style.layers.SymbolLayer).iconAllowOverlap.value)
            assertEquals(true, (map!!.style!!.getLayer("station-selected") as org.maplibre.android.style.layers.SymbolLayer).iconAllowOverlap.value)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(instrumentation.targetContext.cacheDir, "map-dark.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.runOnIdle { vm.choosingMapLocation = false; dark.value = false }
        waitForStyle(MapConfig.STYLE_URL)
        waitForVisibleMarker(selected, true)
        compose.runOnIdle {
            assertEquals(selected, vm.selectedStationId)
            assertEquals(viewpoint.zoom, map!!.cameraPosition.zoom, 0.01)
            assertNotNull(map!!.style!!.getSourceAs<GeoJsonSource>("station-src"))
        }
    }
}
