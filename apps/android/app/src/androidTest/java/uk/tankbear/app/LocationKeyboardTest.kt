package uk.tankbear.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot as isViewRoot
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.Rule
import org.junit.Test
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.*

class LocationKeyboardTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: AppViewModel
    private var savedBefore: Saved? = null
    private val night = mutableStateOf(false)
    private val place = GeocodedPlace("Public postcode sample", LatLon(51.501, -0.141))
    @Before fun setup() {
        vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        compose.runOnIdle {
            savedBefore = vm.saved
            vm.update { it.copy(mpg = "40", litres = "30", places = List(8) { i -> SavedPlace("Public sample ${i + 1}", place.point) }) }
            vm.useExample()
        }
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setContent { TestApp() }
        }
        compose.onNodeWithText("Find").performClick()
    }
    @After fun restore() {
        savedBefore?.let { saved -> runBlocking { Prefs(InstrumentationRegistry.getInstrumentation().targetContext).save(saved) } }
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    companion object {
        private var originalKeyboard: String? = null
        private var originalHandwriting: String? = null
        private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().toString(Charsets.UTF_8).trim()
        }
        @JvmStatic @BeforeClass fun configureKeyboard() {
            // Keep a full IME for this class; avoid repeatedly toggling Gboard's cached mode.
            originalKeyboard = shell("settings get secure show_ime_with_hard_keyboard")
            originalHandwriting = shell("settings get secure stylus_handwriting_enabled")
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("settings put secure stylus_handwriting_enabled 0")
        }
        @JvmStatic @AfterClass fun restoreKeyboard() {
            fun restoreSetting(key: String, original: String?) {
                if (original != null) shell(if (original == "null") "settings delete secure $key" else "settings put secure $key $original")
            }
            restoreSetting("show_ime_with_hard_keyboard", originalKeyboard)
            restoreSetting("stylus_handwriting_enabled", originalHandwriting)
        }
    }
    @Composable private fun TestApp(fontScale: Float = 1f) {
        val configuration = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides configuration,
            LocalDensity provides Density(LocalDensity.current.density, fontScale)) { TankBearApp(vm) }
    }
    private fun ime(): Pair<Int, Int> {
        var result = 0 to 0
        onView(isViewRoot()).inRoot(isDialog()).check { view, failure ->
            if (failure != null) throw failure
            val insets = ViewCompat.getRootWindowInsets(view)
            val bottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            result = bottom to (view.height - bottom)
        }
        return result
    }
    private fun activityIme(): Pair<Int, Int> {
        var result = 0 to 0
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bottom = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            result = bottom to (view.height - bottom)
        }
        return result
    }
    private fun searchWithKeyboard(target: MapTarget) {
        val pending = CompletableDeferred<PlaceSearchResult>()
        var submitted: String? = null
        compose.runOnIdle { night.value = target == MapTarget.Destination; vm.placeLookup = { _, _, query -> submitted = query; pending.await() } }
        compose.onNodeWithTag("choose-${target.name.lowercase()}").performScrollTo().performClick()
        val field = compose.onNodeWithText("Place or postcode")
        field.performScrollTo().performClick().performTextInput("sw1a1aa")
        // Gboard applies the handwriting test setting asynchronously on cold starts.
        compose.waitUntil(15_000) { ime().first > 0 }
        var previousIme = -1
        var stableSamples = 0
        compose.waitUntil(5000) {
            val current = ime()
            stableSamples = if (current.first == previousIme) stableSamples + 1 else 0
            previousIme = current.first
            current.first > 0 && stableSamples >= 2 && field.fetchSemanticsNode().boundsInWindow.bottom <= current.second + 2
        }
        val bounds = field.fetchSemanticsNode().boundsInWindow
        assertTrue("Query field must stay above the keyboard", bounds.bottom <= ime().second + 2)
        capture("keyboard-${target.name.lowercase()}-typing-${compose.activity.resources.configuration.orientation}.png")
        field.assertIsDisplayed().assertIsFocused().performImeAction()
        compose.waitUntil(5000) { submitted != null }
        compose.runOnIdle { assertEquals("SW1A 1AA", submitted) }
        compose.waitUntil(5000) { ime().first == 0 }
        compose.onNodeWithText("Searching places…").assertIsDisplayed()
        compose.runOnIdle { pending.complete(PlaceSearchResult(listOf(place))) }
        compose.waitUntil(5000) { compose.onAllNodesWithText(place.label).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("1 place found. Choose one to confirm on the map.").assertIsDisplayed()
        compose.onNodeWithText(place.label).assertIsDisplayed()
        capture("keyboard-${target.name.lowercase()}-results.png")
    }
    @Test fun startSearchStaysAboveKeyboardAndImeSearchRevealsResults() = searchWithKeyboard(MapTarget.Start)
    @Test fun destinationSearchStaysAboveKeyboardAndImeSearchRevealsResults() = searchWithKeyboard(MapTarget.Destination)
    @Test fun landscapeLargeTextQueryStaysAboveKeyboard() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.runOnUiThread {
            compose.activity.setContent {
                TestApp(2f)
            }
        }
        searchWithKeyboard(MapTarget.Destination)
    }
    @Test fun openingChooserDismissesKeyboardLeftByAnotherField() {
        compose.onNodeWithText("Manage saved places").performScrollTo().performClick()
        compose.onNodeWithText("Name for a new place (Home, Work…)").performScrollTo().performClick().performTextInput("Draft sample")
        compose.waitUntil(15_000) {
            var visible = false
            compose.runOnIdle { visible = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            visible
        }
        compose.onNodeWithTag("choose-start").performScrollTo().performClick()
        compose.waitUntil(5000) { ime().first == 0 }
        compose.onNodeWithText("Choose start on map").assertIsDisplayed()
        compose.onNodeWithText("Use my location as the start").assertIsDisplayed()
    }
    private fun editCarField(label: String, value: String) {
        compose.onNodeWithText("Car").performClick()
        val field = compose.onNodeWithText(label)
        field.performScrollTo().performClick().performTextReplacement(value)
        compose.waitUntil(15_000) { activityIme().first > 0 }
        var previousIme = -1
        var stableSamples = 0
        compose.waitUntil(5000) {
            val current = activityIme()
            stableSamples = if (current.first == previousIme) stableSamples + 1 else 0
            previousIme = current.first
            current.first > 0 && stableSamples >= 2 && field.fetchSemanticsNode().boundsInWindow.bottom <= current.second + 2
        }
        field.assertIsDisplayed().assertIsFocused().assertTextContains(value)
        capture("car-${if (label == "Litres to buy") "litres" else "mpg"}-${compose.activity.resources.configuration.orientation}-${if (night.value) "dark" else "light"}.png")
        field.performImeAction()
        compose.waitUntil(5000) { activityIme().first == 0 }
        field.assertIsNotFocused()
    }
    @Test fun carLitresAndMpgStayAboveKeyboardAndDoneKeepsEdits() {
        editCarField("Litres to buy", "25.5")
        editCarField("Miles per Imperial gallon (MPG)", "45.2")
        compose.runOnIdle { assertEquals("25.5", vm.saved.litres); assertEquals("45.2", vm.saved.mpg) }
    }
    @Test fun carLitresStayAboveKeyboardAtLargeTextInDarkMode() {
        compose.runOnIdle { night.value = true }
        compose.runOnUiThread { compose.activity.setContent { TestApp(2f) } }
        editCarField("Litres to buy", "25.5")
    }
    @Test fun carLitresStayAboveKeyboardInLandscape() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.runOnUiThread { compose.activity.setContent { TestApp() } }
        editCarField("Litres to buy", "25.5")
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureToImage()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }
}
