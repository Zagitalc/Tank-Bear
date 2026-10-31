package uk.tankbear.app

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme

/** Public Reading coordinates only; no fuel-server access or location permission needed. */
@RunWith(AndroidJUnit4::class)
class MapExpansionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun expandChooseCancelConfirmAndThemeReloadPreserveMap() {
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        val dark = mutableStateOf(false)
        compose.runOnIdle { vm.useExample() }
        compose.runOnUiThread {
            compose.activity.setContent {
                val config = Configuration(LocalConfiguration.current)
                config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                CompositionLocalProvider(LocalConfiguration provides config) { TankBearTheme { Surface { MapScreen(vm, Modifier.safeDrawingPadding()) } } }
            }
        }
        var view: MapView? = null
        var map: MapLibreMap? = null
        compose.waitUntil(10_000) {
            compose.runOnIdle { view = findMap(compose.activity.window.decorView); view?.getMapAsync { map = it } }
            map != null
        }
        compose.waitUntil(30_000) { var ready = false; compose.runOnIdle { ready = map?.style?.isFullyLoaded == true }; ready }
        val viewpoint = CameraPosition.Builder().target(LatLng(51.4543, -0.9781)).zoom(15.0).bearing(13.0).build()
        var height = 0
        compose.runOnIdle { map!!.cameraPosition = viewpoint; height = view!!.height }
        compose.onNodeWithContentDescription("Expand map").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(view, findMap(compose.activity.window.decorView))
            assertTrue(view!!.height > height)
            assertEquals(15.0, map!!.cameraPosition.zoom, 0.01)
            assertEquals(13.0, map!!.cameraPosition.bearing, 0.01)
        }
        waitForTiles(map!!)
        capture("stage11-expanded-light.png")
        val start = vm.originLat to vm.originLon
        compose.onNodeWithTag("route-map").performTouchInput { click(Offset(width * .6f, height * .55f)) }
        compose.waitUntil(10_000) { vm.choosingMapLocation }
        compose.waitForIdle()
        capture("stage11-picker-check.png")
        compose.onNodeWithText("Use this start").assertIsDisplayed()
        compose.runOnIdle { assertEquals(start, vm.originLat to vm.originLon) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(vm.mapExpanded); assertEquals(start, vm.originLat to vm.originLon) }
        compose.onNodeWithTag("route-map").performTouchInput { click(center) }
        compose.waitUntil(10_000) { vm.mapExpanded }
        compose.runOnIdle { assertFalse(vm.choosingMapLocation) }
        compose.onNodeWithTag("route-map").performTouchInput { click(Offset(width * .7f, height * .6f)) }
        compose.waitUntil(10_000) { vm.choosingMapLocation }
        compose.onNodeWithText("Use this start").performClick()
        compose.runOnIdle { assertFalse(vm.mapExpanded); assertNotEquals(start, vm.originLat to vm.originLon) }
        compose.onNodeWithContentDescription("Expand map").performClick()
        compose.runOnIdle { dark.value = true }
        compose.waitUntil(30_000) {
            var ready = false
            compose.runOnIdle {
                val road = map!!.style?.getLayer("highway_minor") as? LineLayer
                ready = road?.lineColorAsInt == android.graphics.Color.rgb(150, 163, 175)
            }
            ready
        }
        waitForTiles(map!!)
        capture("stage11-expanded-dark.png")
        compose.runOnIdle {
            assertSame(view, findMap(compose.activity.window.decorView))
            assertTrue(map!!.uiSettings.isAttributionEnabled)
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithContentDescription("Expand map").assertIsDisplayed()
        capture("stage11-small-dark.png")
    }

    private fun waitForTiles(map: MapLibreMap) {
        compose.waitUntil(45_000) {
            var ready = false
            compose.runOnIdle {
                val view = findMap(compose.activity.window.decorView)!!
                ready = map.queryRenderedFeatures(android.graphics.RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()))
                    .any { it.hasProperty("name") }
            }
            ready
        }
    }

    private fun capture(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        i.uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(i.targetContext.cacheDir, name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
