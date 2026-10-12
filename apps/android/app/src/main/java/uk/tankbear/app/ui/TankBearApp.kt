package uk.tankbear.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import uk.tankbear.app.data.Validation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.tankbear.app.R
import uk.tankbear.app.ui.theme.TankBearTheme

private enum class Destination(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Find(R.string.find, R.drawable.ic_find),
    Map(R.string.map, R.drawable.ic_map),
    Car(R.string.car, R.drawable.ic_car),
    Settings(R.string.settings, R.drawable.ic_settings),
}

@Composable
fun TankBearApp(vm: AppViewModel = viewModel()) {
    var selected by rememberSaveable { mutableStateOf(Destination.Find) }

    LaunchedEffect(vm.loaded) {
        if (vm.loaded && Validation.mpg(vm.saved.mpg) == null) selected = Destination.Car
    }

    TankBearTheme {
        Scaffold(
            bottomBar = {
                Column {
                  if (selected == Destination.Find || selected == Destination.Map) SearchActionBar(vm, fromFind = selected == Destination.Find,
                      onChoose = { target -> vm.beginMapChoice(target); selected = Destination.Map },
                      onCar = { selected = Destination.Car })
                  NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selected == destination,
                            onClick = { selected = destination; if (destination != Destination.Map) vm.choosingMapLocation = false },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
                }
            },
        ) { padding ->
            if (selected == Destination.Map) {
                MapScreen(vm, Modifier.padding(padding))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    FindBrand()
                    when (selected) {
                        Destination.Find -> FindScreen(vm, onShowOnMap = { selected = Destination.Map })
                        Destination.Car -> {
                            CarScreen(vm)
                            androidx.compose.material3.Button(
                                enabled = Validation.mpg(vm.saved.mpg) != null && Validation.litres(vm.saved.litres) != null,
                                onClick = { selected = Destination.Find },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            ) { Text("Find fuel") }
                        }
                        else -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}

/** Shared fixed action: search is always reachable without scrolling on Find and Map. */
@Composable
internal fun SearchActionBar(vm: AppViewModel, fromFind: Boolean, onChoose: (MapTarget) -> Unit, onCar: () -> Unit) {
    val missingStart = Validation.point(vm.originLat, vm.originLon) == null
    val missingDestination = vm.mode == uk.tankbear.app.data.Mode.AlongJourney && Validation.point(vm.destLat, vm.destLon) == null
    val correction = if (vm.formError == null) null else if (missingStart) "Choose start" else if (missingDestination) "Choose destination" else if (Validation.litres(vm.saved.litres) == null || (vm.mode != uk.tankbear.app.data.Mode.Nearby && Validation.mpg(vm.saved.mpg) == null)) "Check car details" else null
    val failure = if (vm.mode == uk.tankbear.app.data.Mode.Nearby) (vm.nearby as? NearbyState.Failed)?.failure else (vm.search as? SearchState.Failed)?.failure
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            vm.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            failure?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            if (vm.resultsOutdated) Text("Inputs changed — previous results are outdated.", style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.Button(
                onClick = {
                    when {
                        vm.loading -> vm.cancel()
                        correction != null && missingStart -> onChoose(MapTarget.Start)
                        correction != null && missingDestination -> onChoose(MapTarget.Destination)
                        correction != null -> onCar()
                        else -> vm.submit(fromFind)
                    }
                },
                enabled = vm.loaded && !vm.choosingMapLocation,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(when {
                    vm.choosingMapLocation -> "Confirm your map location first"
                    vm.loading -> "Cancel search"
                    correction != null -> correction
                    failure != null -> "Try search again"
                    vm.resultsOutdated -> "Update results"
                    vm.mode == uk.tankbear.app.data.Mode.Nearby -> "Find nearby stations"
                    else -> "Find the best stop"
                }, textAlign = TextAlign.Center)
            }
        }
    }
}
