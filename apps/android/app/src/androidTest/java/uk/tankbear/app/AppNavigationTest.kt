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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Saved places persist on the device between runs; start every test with none so labels are unambiguous. */
    @Before
    fun removeAnySavedPlaces() {
        compose.onNodeWithText("Find").performClick()
        while (compose.onAllNodesWithText("Remove").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithText("Remove")[0].performScrollTo().performClick()
            compose.waitForIdle()
        }
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
        compose.onNodeWithText("Fuel price data: Fuel Finder/ICS").performScrollTo().assertIsDisplayed()
        tab("Map").performClick()
        tab("Map").assertIsSelected()
        tab("Find").performClick()
        compose.onNodeWithText("Make your fuel stop count").assertIsDisplayed()
    }

    @Test
    fun mapChoiceIsExplicitAndSearchStaysReachable() {
        compose.onNodeWithText("Find the best stop").assertIsDisplayed()
        compose.onNodeWithText("Start latitude").assertDoesNotExist()
        compose.onNodeWithText("Choose start on map").performScrollTo().performClick()
        compose.onNodeWithText("Use this location").assertIsDisplayed()
        compose.onNodeWithText("Confirm your map location first").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Choose start").assertIsDisplayed()
        compose.onNodeWithText("Find the best stop").assertIsDisplayed()
        tab("Find").performClick()
        compose.onNodeWithText("Enter coordinates (optional)").performScrollTo().performClick()
        compose.onNodeWithText("Start latitude").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Find the best stop").assertIsDisplayed()
    }

    @Test
    fun eachModeShowsOnlyTheFieldsItNeeds() {
        compose.onNodeWithText("Choose destination on map").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fuel trip (there and back from here)").performScrollTo().performClick()
        compose.onNodeWithText("Choose destination on map").assertDoesNotExist()
        compose.onNodeWithText("Find the best stop").assertIsDisplayed()
        compose.onNodeWithText("Nearby stations (no journey)").performScrollTo().performClick()
        compose.onNodeWithText("Choose destination on map").assertDoesNotExist()
        compose.onNodeWithText("Find nearby stations").assertIsDisplayed()
        compose.onNodeWithText("Along my journey").performScrollTo().performClick()
        compose.onNodeWithText("Choose destination on map").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMissingCarSettingGivesAClearMessageInsteadOfASearch() {
        tab("Car").performClick()
        compose.onNodeWithText("Miles per Imperial gallon (MPG)").performTextReplacement("")
        tab("Find").performClick()
        compose.onNodeWithText("Fill in an example (Reading to Oxford)").performScrollTo().performClick()
        compose.onNodeWithText("Find the best stop").performClick()
        compose.onNodeWithText("Set your car's Imperial MPG", substring = true).assertIsDisplayed()
    }

    @Test
    fun savedPlaceNeedsAStartThenCanBeSavedAndRemoved() {
        compose.onNodeWithText("Name for a new place (Home, Work…)").performScrollTo().performTextInput("Test place")
        compose.onNodeWithText("Save start").performScrollTo().performClick()
        compose.onNodeWithText("Set the start first.").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Fill in an example (Reading to Oxford)").performScrollTo().performClick()
        compose.onNodeWithText("Save start").performScrollTo().performClick()
        compose.onNodeWithText("Test place").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove").performScrollTo().performClick()
        compose.onNodeWithText("Test place").assertDoesNotExist()
    }

    @Test
    fun theMapTabExplainsItselfBeforeAnySearch() {
        tab("Map").performClick()
        compose.onNodeWithText("Long-press the map to set the start").assertIsDisplayed()
        compose.onNodeWithText("Choose destination on map").assertIsDisplayed()
        compose.onNodeWithText("Run a search on the Find tab", substring = true).assertIsDisplayed()
    }
}
