package uk.tankbear.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.*

@RunWith(AndroidJUnit4::class)
class FindLocationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: AppViewModel
    private var savedBefore: Saved? = null
    private val reading = LatLon(51.4543, -0.9781)
    private val oxford = LatLon(51.7520, -1.2577)
    @Before fun setup() {
        vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        compose.runOnIdle {
            savedBefore = vm.saved
            vm.update { it.copy(mpg = "40", litres = "30", places = listOf(SavedPlace("Home sample", reading), SavedPlace("Work sample", oxford))) }
        }
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.onNodeWithText("Find").performClick()
    }
    @After fun restorePreferences() {
        savedBefore?.let { saved -> runBlocking { Prefs(InstrumentationRegistry.getInstrumentation().targetContext).save(saved) } }
    }
    @Test fun findShowsTwoRowsAndChooserSurvivesRecreationWithoutChangingStart() {
        compose.onNodeWithTag("choose-start").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("choose-destination").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Search start by name or postcode").assertDoesNotExist()
        compose.onNodeWithText("Name for a new place (Home, Work…)").assertDoesNotExist()
        compose.onNodeWithText("Start latitude").assertDoesNotExist()
        compose.onNodeWithText("Fill in an example (Reading to Oxford)").assertDoesNotExist()
        compose.onNodeWithText("Find the best stop").assertIsDisplayed()
        compose.onNodeWithText("Make your fuel stop count").performScrollTo()
        capture("find-simple-main.png")
        compose.onNodeWithTag("choose-start").performScrollTo().performClick()
        compose.onNodeWithText("Choose start on map").assertIsDisplayed()
        compose.onNodeWithText("Use my location as the start").assertIsDisplayed()
        capture("find-simple-chooser.png")
        compose.activityRule.scenario.recreate()
        try { compose.waitUntil(5000) { runCatching { compose.onNodeWithText("Choose start on map").assertIsDisplayed(); true }.getOrDefault(false) } }
        catch (failure: Throwable) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/find-chooser-restored.png").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
            throw failure
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.runOnIdle { assertEquals("Not set", vm.locationDescription(MapTarget.Start)); assertEquals("", vm.originLat) }
        compose.onNodeWithTag("choose-destination").performScrollTo().performClick()
        compose.onNodeWithText("Choose destination on map").assertIsDisplayed()
        compose.onNodeWithText("Use my location as the start").assertDoesNotExist()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureToImage()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    @Test fun savedPlacesSelectTheRequestedEndpointAndReturnToFind() {
        compose.onNodeWithTag("choose-start").performScrollTo().performClick()
        compose.onNodeWithText("Home sample").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(reading, Validation.point(vm.originLat, vm.originLon))
            assertEquals("Home sample", vm.locationDescription(MapTarget.Start))
            assertNull(Validation.point(vm.destLat, vm.destLon))
        }
        compose.onNodeWithTag("choose-destination").performScrollTo().performClick()
        compose.onNodeWithText("Work sample").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(oxford, Validation.point(vm.destLat, vm.destLon))
            assertEquals("Work sample", vm.locationDescription(MapTarget.Destination))
            assertEquals(reading, Validation.point(vm.originLat, vm.originLon))
        }
        compose.onNodeWithText("Choose destination on map").assertDoesNotExist()
        compose.onNodeWithTag("choose-start").performScrollTo().assertTextContains("Home sample")
        compose.onNodeWithTag("choose-destination").performScrollTo().assertTextContains("Work sample")
    }
    @Test fun descriptiveLabelsFollowConfirmedPointsAndDoNotMislabelMovedPins() {
        compose.runOnIdle {
            vm.beginMapChoice(MapTarget.Start, reading, "Reading search sample")
            vm.setFromMap(reading)
            assertEquals("Reading search sample", vm.locationDescription(MapTarget.Start))
            vm.beginMapChoice(MapTarget.Start, reading, "Reading search sample")
            vm.setFromMap(oxford)
            assertEquals("Selected on map", vm.locationDescription(MapTarget.Start))
            vm.useLocation(reading)
            assertEquals("Current location", vm.locationDescription(MapTarget.Start))
            vm.originLat = ""
            assertEquals("Not set", vm.locationDescription(MapTarget.Start))
            vm.originLat = "52"
            assertEquals("Location selected", vm.locationDescription(MapTarget.Start))
        }
    }
}
