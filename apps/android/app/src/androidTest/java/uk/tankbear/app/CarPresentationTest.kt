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
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.tankbear.app.ui.*
import uk.tankbear.app.ui.theme.TankBearTheme

class CarPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun fourGradesAndNumericFieldsWorkWithLargeTextInBothThemes() {
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { vm.loaded }
        val original = vm.saved
        val dark = mutableStateOf(false)
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
                            Column(Modifier.width(360.dp).fillMaxHeight().testTag("car").verticalScroll(rememberScrollState()).padding(16.dp)) {
                                FindBrand()
                                CarScreen(vm)
                            }
                        }
                    }
                }
            }
        }
        try {
            for (night in listOf(false, true)) {
                compose.runOnIdle { dark.value = night }
                for (fuel in vm.fuelOptions()) {
                    compose.onNodeWithText(fuel.label).performScrollTo().performClick().assertIsSelected()
                    compose.runOnIdle { assertEquals(fuel, vm.saved.fuel) }
                }
                compose.onNodeWithText("Miles per Imperial gallon (MPG)").performScrollTo().performTextReplacement("26")
                compose.onNodeWithText("Litres to buy").performScrollTo().performTextReplacement("50")
                compose.runOnIdle { assertEquals("26", vm.saved.mpg); assertEquals("50", vm.saved.litres) }
                compose.onNodeWithText("Your car, your fuel costs").performScrollTo()
                val bitmap = compose.onNodeWithTag("car").captureToImage().asAndroidBitmap()
                java.io.File(compose.activity.cacheDir, if (night) "car-dark.png" else "car-light.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally { compose.runOnIdle { vm.update { original } } }
    }
}
