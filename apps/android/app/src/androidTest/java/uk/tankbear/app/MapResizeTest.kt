package uk.tankbear.app

import android.graphics.Color
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.*
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme

/** Synthetic-style renderer regression: the bottom of the resized map must contain map pixels. */
@RunWith(AndroidJUnit4::class)
class MapResizeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun repeatedExpansionAndFooterChangesKeepRendererAspectAndFillBounds() {
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        val hostHeight = mutableStateOf(650.dp)
        compose.runOnIdle { vm.mapJourney = true }
        compose.runOnUiThread {
            compose.activity.setContent { TankBearTheme { Surface { Box(Modifier.fillMaxWidth().height(hostHeight.value)) { MapScreen(vm) } } } }
        }
        var view: MapView? = null
        var map: MapLibreMap? = null
        compose.waitUntil(10_000) {
            compose.runOnIdle { view = findMap(compose.activity.window.decorView); view?.getMapAsync { map = it } }
            map != null
        }
        // Wait for the app's initial style setup before replacing it, so a late
        // startup effect cannot overwrite the synthetic test style.
        compose.waitUntil(30_000) {
            var initialized = false
            compose.runOnIdle { initialized = map?.style?.getSource("pins-src") != null }
            initialized
        }
        // No remote tiles or credentials in this style: a coloured background and a 30px circle
        // expose black strips and stretching, including at the bottom of the map.
        val style = """{"version":8,"sources":{"point":{"type":"geojson","data":{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-0.9781,51.4543]}}]}}},"layers":[{"id":"background","type":"background","paint":{"background-color":"#146c43"}},{"id":"circle","type":"circle","source":"point","paint":{"circle-color":"#ffffff","circle-radius":30}}]}"""
        var ready = false
        compose.runOnIdle {
            map!!.cameraPosition = CameraPosition.Builder().target(LatLng(51.4543, -0.9781)).zoom(15.0).build()
            map!!.setStyle(Style.Builder().fromJson(style)) { ready = true }
        }
        compose.waitUntil(10_000) { ready }
        repeat(30) { step ->
            compose.runOnIdle {
                vm.mapExpanded = step % 3 != 0
                vm.choosingMapLocation = step % 3 == 2
                hostHeight.value = if (step % 4 == 0) 300.dp else if (vm.mapExpanded) 760.dp else 650.dp
            }
            compose.waitForIdle()
            var diagnostic = "No bitmap"
            try { compose.waitUntil(10_000) {
                var correct = false
                compose.runOnIdle {
                    assertSame(view, findMap(compose.activity.window.decorView))
                    val texture = view!!.renderView as TextureView
                    val bitmap = texture.bitmap
                    if (bitmap != null && bitmap.width == view!!.width && bitmap.height == view!!.height) {
                        val bottom = bitmap.height * 95 / 100
                        val filled = listOf(1, 2, 3).all { bitmap.getPixel(bitmap.width * it / 4, bottom) == Color.rgb(20, 108, 67) }
                        var minX = bitmap.width; var maxX = -1; var minY = bitmap.height; var maxY = -1
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                            if (pixels[y * bitmap.width + x] == Color.WHITE) {
                                minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
                            }
                        }
                        diagnostic = "step=$step view=${view!!.width}x${view!!.height} bitmap=${bitmap.width}x${bitmap.height} bottom=${Integer.toHexString(bitmap.getPixel(bitmap.width / 2, bottom))} bounds=$minX,$minY,$maxX,$maxY camera=${map!!.cameraPosition}"
                        val circleWidth = maxX - minX + 1
                        val circleHeight = maxY - minY + 1
                        // SDF/texture rasterisation can differ by a few edge pixels
                        // at fractional densities; reject visible stretch (>3%).
                        val edgeTolerance = maxOf(2, kotlin.math.ceil(minOf(circleWidth, circleHeight) * 0.03).toInt())
                        correct = filled && circleWidth > 0 && kotlin.math.abs(circleWidth - circleHeight) <= edgeTolerance &&
                            kotlin.math.abs((minX + maxX) / 2f - bitmap.width / 2f) <= 2 &&
                            kotlin.math.abs((minY + maxY) / 2f - bitmap.height / 2f) <= 2
                        bitmap.recycle()
                    }
                }
                correct
            } } catch (failure: Throwable) {
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/tankbear-resize-failure.png").use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
                }
                throw AssertionError(diagnostic, failure)
            }
        }
    }
}
