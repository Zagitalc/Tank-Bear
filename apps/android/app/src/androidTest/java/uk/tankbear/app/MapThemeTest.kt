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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.sources.GeoJsonSource
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme

/** Live map-provider compatibility check using only the public Reading/Oxford example. */
@RunWith(AndroidJUnit4::class)
class MapThemeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
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
        compose.runOnIdle {
            assertEquals(setOf("start", "destination"), map!!.style!!.getSourceAs<GeoJsonSource>("pins-src")!!.querySourceFeatures(null).map { it.getStringProperty("role") }.toSet())
        }
        val selected = vm.selectedStationId
        compose.runOnIdle {
            assertNotNull(selected)
            vm.beginMapChoice(MapTarget.Destination)
        }
        compose.waitForIdle()
        // Move to a public sample viewpoint after picker entry has positioned the camera.
        Thread.sleep(600)
        val viewpoint = CameraPosition.Builder().target(LatLng(51.6, -1.1)).zoom(11.0).bearing(23.0).tilt(15.0).build()
        compose.runOnIdle { map!!.cancelTransitions(); map!!.cameraPosition = viewpoint; dark.value = true }
        waitForStyle(MapConfig.DARK_STYLE_URL)
        compose.runOnIdle {
            assertEquals(selected, vm.selectedStationId)
            assertTrue(vm.choosingMapLocation)
            assertEquals(MapTarget.Destination, vm.mapTarget)
            assertEquals(viewpoint.zoom, map!!.cameraPosition.zoom, 0.01)
            assertEquals(viewpoint.bearing, map!!.cameraPosition.bearing, 0.01)
            assertEquals(viewpoint.tilt, map!!.cameraPosition.tilt, 0.01)
            assertEquals(viewpoint.target!!.latitude, map!!.cameraPosition.target!!.latitude, 0.00001)
            assertTrue(map!!.uiSettings.isAttributionEnabled)
            assertNotNull(map!!.style!!.getImage("pump-selected"))
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(instrumentation.targetContext.cacheDir, "map-dark.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.runOnIdle { vm.choosingMapLocation = false; dark.value = false }
        waitForStyle(MapConfig.STYLE_URL)
        compose.runOnIdle {
            assertEquals(selected, vm.selectedStationId)
            assertEquals(viewpoint.zoom, map!!.cameraPosition.zoom, 0.01)
            assertTrue(map!!.style!!.getSourceAs<GeoJsonSource>("station-src")!!.querySourceFeatures(null).isNotEmpty())
        }
    }
}
