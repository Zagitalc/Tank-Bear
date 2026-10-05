package uk.tankbear.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchesTabsAndRestoresSelectionAfterActivityRecreation() {
        compose.onNodeWithText("Find").assertIsSelected()
        compose.onNodeWithText("Make your fuel stop count").assertIsDisplayed()

        compose.onNodeWithText("Car").performClick()
        compose.onNodeWithText("Your car, your fuel costs").assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Car").assertIsSelected()
        compose.onNodeWithText("Your car, your fuel costs").assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("You’re in control").assertIsDisplayed()
        compose.onNodeWithText("Find").performClick()
        compose.onNodeWithText("Make your fuel stop count").assertIsDisplayed()
    }
}
