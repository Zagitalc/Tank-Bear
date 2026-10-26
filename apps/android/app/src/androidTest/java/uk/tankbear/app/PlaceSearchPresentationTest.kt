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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme

class PlaceSearchPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun largeTextResultsAndAttributionRemainReachable() {
        val dark = mutableStateOf(false)
        var selected: GeocodedPlace? = null
        var link = ""
        val place = GeocodedPlace("Newport, Telford and Wrekin", LatLon(52.77, -2.38))
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides config,
                    LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TankBearTheme {
                        Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            Column(Modifier.width(360.dp).fillMaxHeight().testTag("places").verticalScroll(rememberScrollState()).padding(16.dp)) {
                                PlaceSearchContent("Newport", MapTarget.Start, false, PlaceSearchResult(listOf(place)), {}, {},
                                    { selected = it }, { link = it })
                            }
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithText(place.label).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(place, selected) }
            compose.onNodeWithText("© OpenStreetMap contributors").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("https://www.openstreetmap.org/copyright", link) }
            compose.onNodeWithText("Search start by name or postcode").performScrollTo()
            val bitmap = compose.onNodeWithTag("places").captureToImage().asAndroidBitmap()
            java.io.File(compose.activity.cacheDir, if (night) "places-dark.png" else "places-light.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun loadingEmptyAndProviderErrorKeepSearchAction() {
        val loading = mutableStateOf(true)
        val result = mutableStateOf<PlaceSearchResult?>(null)
        var searched = false
        compose.runOnUiThread {
            compose.activity.setContent {
                TankBearTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        PlaceSearchContent("Oxford", MapTarget.Destination, loading.value, result.value,
                            {}, { searched = true }, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Searching places…").assertIsNotEnabled()
        compose.runOnIdle { loading.value = false; result.value = PlaceSearchResult() }
        compose.onNodeWithText("No places found. Try a fuller name or postcode, or choose on the map.").assertExists()
        val error = "Place search limit reached. Try later or choose on the map."
        compose.runOnIdle { result.value = PlaceSearchResult(error = error) }
        compose.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Search places").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(searched) }
    }
}
