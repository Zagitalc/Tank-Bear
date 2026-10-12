package uk.tankbear.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.tankbear.app.ui.FindPill
import uk.tankbear.app.ui.JourneyChoice
import uk.tankbear.app.ui.theme.TankBearTheme

@RunWith(AndroidJUnit4::class)
class FindPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent(content = content) }
    }

    @Test fun largeTextPillsStayEqualAndClickable() {
        var clicked = false
        show {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                TankBearTheme {
                    Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        Column(Modifier.width(328.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            FindPill("Price history", onClick = {}, modifier = Modifier.testTag("history"))
                            FindPill("Show route on map", onClick = { clicked = true }, modifier = Modifier.testTag("route"))
                        }
                    }
                }
            }
        }
        val history = compose.onNodeWithTag("history").fetchSemanticsNode().boundsInRoot
        val route = compose.onNodeWithTag("route").fetchSemanticsNode().boundsInRoot
        assertEquals(history.width, route.width, 0.5f)
        compose.onNodeWithTag("route").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(clicked) }
        val label = compose.onNodeWithText("Show route on map", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(label.left >= route.left && label.right <= route.right)
        assertEquals(route.center.y, label.center.y, 1f)
    }

    @Test fun wrappedJourneyLabelSelectsWholeRow() {
        show {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                TankBearTheme {
                    var trip by remember { mutableStateOf(false) }
                    Column(Modifier.width(328.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                        JourneyChoice("Along my journey", !trip) { trip = false }
                        JourneyChoice("Fuel trip (there and back from here)", trip) { trip = true }
                    }
                }
            }
        }
        compose.onNodeWithText("Along my journey").assertIsSelected()
        compose.onNodeWithText("Fuel trip (there and back from here)").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Along my journey").assertIsNotSelected()
    }
    @Test fun findRendersBothThemesWithReachableAction() {
        val dark = mutableStateOf(false)
        show {
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
            configuration.uiMode = (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark.value) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
            CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                TankBearTheme {
                    val vm: uk.tankbear.app.ui.AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        Column(Modifier.width(360.dp).height(740.dp).testTag("find-preview")) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                uk.tankbear.app.ui.FindBrand()
                                uk.tankbear.app.ui.FindScreen(vm, {})
                            }
                            uk.tankbear.app.ui.SearchActionBar(vm, true, {}, {})
                        }
                    }
                }
            }
        }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark.value = theme == "dark" }
            compose.onNodeWithText("Make your fuel stop count").assertIsDisplayed()
            compose.onNodeWithText("Find the best stop").assertIsDisplayed()
            val bitmap = compose.onNodeWithTag("find-preview").captureToImage().asAndroidBitmap()
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(context.cacheDir, "find-$theme.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }

        }
    }

}
