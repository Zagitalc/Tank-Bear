package uk.tankbear.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModeRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test
    fun missingDestinationDoesNotLeakIntoFuelTrip() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[uk.tankbear.app.ui.AppViewModel::class.java]
        compose.runOnIdle {
            vm.mode = uk.tankbear.app.data.Mode.AlongJourney
            vm.useExample()
            vm.destLat = ""
            vm.destLon = ""
            vm.submit()
            org.junit.Assert.assertEquals("Choose your destination on the map.", vm.formError)
            vm.mode = uk.tankbear.app.data.Mode.FuelTrip
            org.junit.Assert.assertNull(vm.formError)
            org.junit.Assert.assertEquals(uk.tankbear.app.ui.SearchState.Idle, vm.search)
            org.junit.Assert.assertNull(vm.inputs.destination)
        }
    }

    @Test
    fun correctingStartClearsValidationError() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[uk.tankbear.app.ui.AppViewModel::class.java]
        compose.runOnIdle {
            vm.mode = uk.tankbear.app.data.Mode.FuelTrip
            vm.originLat = ""
            vm.originLon = ""
            vm.submit(fromFind = true)
            org.junit.Assert.assertEquals("Choose your start on the map or use your location.", vm.formError)
            val version = vm.findResultsVersion
            vm.useExample()
            org.junit.Assert.assertNull(vm.formError)
            org.junit.Assert.assertEquals(version, vm.findResultsVersion)
        }
    }
}
