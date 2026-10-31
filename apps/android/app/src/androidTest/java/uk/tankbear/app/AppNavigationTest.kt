package uk.tankbear.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.After
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private var savedBefore: uk.tankbear.app.data.Saved? = null
    @After fun restorePreferences() {
        savedBefore?.let { saved -> kotlinx.coroutines.runBlocking {
            uk.tankbear.app.data.Prefs(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext).save(saved)
        } }
    }
    /** Isolate saved places for the test, then restore the prior preferences. */
    @Before
    fun removeAnySavedPlaces() {
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[uk.tankbear.app.ui.AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        compose.runOnIdle { savedBefore = vm.saved; vm.update { it.copy(mpg = "40", litres = "30", places = emptyList()) } }
        compose.onNodeWithText("Find").performClick()
    }

    private fun tab(name: String) = compose.onNodeWithText(name)

    @Test
    fun switchesTabsAndRestoresSelectionAfterActivityRecreation() {
        tab("Find").assertIsSelected()
        compose.onNodeWithText("Make your fuel stop count").assertIsDisplayed()

        tab("Car").performClick()
        compose.onNodeWithText("Your car, your fuel costs").assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        tab("Car").assertIsSelected()
        compose.onNodeWithText("Your car, your fuel costs").assertIsDisplayed()

        tab("Settings").performClick()
        compose.onNodeWithText("You’re in control").assertIsDisplayed()
        compose.onNodeWithText("Connected to Tank Bear.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Location").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fuel price data: Fuel Finder/ICS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", substring = true).performScrollTo().assertIsDisplayed()
        tab("Map").performClick()
        tab("Map").assertIsSelected()
        tab("Find").performClick()
        compose.onNodeWithText("Make your fuel stop count").assertIsDisplayed()
    }

    @Test
    fun mapChoiceIsExplicitAndSearchStaysReachable() {
        compose.onNodeWithText("Find the best stop").assertIsDisplayed()
        compose.onNodeWithText("Start latitude").assertDoesNotExist()
        compose.onNodeWithTag("choose-start").performScrollTo().performClick()
        compose.onNodeWithText("Choose start on map").performScrollTo().performClick()
        compose.onNodeWithText("Use this start").assertIsDisplayed()
        compose.onNodeWithText("Find the best stop").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Use this start").assertIsDisplayed()
        compose.onNodeWithText("Find the best stop").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        tab("Find").assertIsSelected()
        compose.onNodeWithText("Find the best stop").assertIsDisplayed()
        tab("Find").performClick()
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.onNodeWithText("Start latitude").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Find the best stop").assertIsDisplayed()
    }

    @Test
    fun eachModeShowsOnlyTheFieldsItNeeds() {
        compose.onNodeWithTag("choose-destination").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fuel trip (there and back from here)").performScrollTo().performClick()
        compose.onNodeWithTag("choose-destination").assertDoesNotExist()
        compose.onNodeWithText("Find the best stop").assertIsDisplayed()
        compose.onNodeWithText("Nearby stations (no journey)").performScrollTo().performClick()
        compose.onNodeWithTag("choose-destination").assertDoesNotExist()
        compose.onNodeWithText("Find nearby stations").assertIsDisplayed()
        compose.onNodeWithText("Along my journey").performScrollTo().performClick()
        compose.onNodeWithTag("choose-destination").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMissingCarSettingGivesAClearMessageInsteadOfASearch() {
        tab("Car").performClick()
        compose.onNodeWithText("Use UK Imperial MPG.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sets the fill estimate on Find and Map.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Litres to buy").performScrollTo().performTextReplacement("501")
        compose.onNodeWithText("Enter 1–500 litres, with up to 3 decimal places.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Litres to buy").performScrollTo().performTextReplacement("30.5")
        compose.onNodeWithText("Enter 1–500 litres, with up to 3 decimal places.").assertDoesNotExist()
        compose.onNodeWithText("Miles per Imperial gallon (MPG)").performScrollTo().performTextReplacement("")
        compose.onNodeWithText("Enter 5–200 Imperial MPG, with up to 3 decimal places.").performScrollTo().assertIsDisplayed()
        tab("Find").performClick()
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.onNodeWithText("Fill in an example (Reading to Oxford)").performScrollTo().performClick()
        compose.onNodeWithText("Find the best stop").performClick()
        compose.onNodeWithText("Set your car's Imperial MPG", substring = true).assertIsDisplayed()
    }

    @Test
    fun savedPlaceNeedsAStartThenCanBeSavedAndRemoved() {
        compose.onNodeWithText("Manage saved places").performScrollTo().performClick()
        compose.onNodeWithText("Name for a new place (Home, Work…)").performScrollTo().performTextInput("Test place")
        compose.onNodeWithText("Save start").performScrollTo().performClick()
        compose.onNodeWithText("Set the start first.").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.onNodeWithText("Fill in an example (Reading to Oxford)").performScrollTo().performClick()
        compose.onNodeWithText("Save start").performScrollTo().performClick()
        compose.onNodeWithText("Test place").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Saved Test place on this phone.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove").performScrollTo().performClick()
        compose.onNodeWithText("Test place").assertDoesNotExist()
        compose.onNodeWithText("Removed Test place from this phone.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.onNodeWithText("Test place").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Restored Test place on this phone.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithText("Name for a new place (Home, Work…)").performScrollTo().performTextReplacement("Test place")
        compose.onNodeWithText("Save destination").performScrollTo().performClick()
        compose.onNodeWithText("Updated Test place on this phone.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove").performScrollTo().performClick()
        compose.onNodeWithText("Test place").assertDoesNotExist()
    }

    @Test
    fun theMapTabExplainsItselfBeforeAnySearch() {
        tab("Map").performClick()
        compose.onNodeWithText("Choose on map").assertIsDisplayed()
        compose.onNodeWithText("Choose a start to see nearby stations.").assertIsDisplayed()
        compose.onNodeWithText("Choose destination").assertDoesNotExist()
    }
}
