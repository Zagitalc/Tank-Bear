package uk.tankbear.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import uk.tankbear.app.ui.theme.TankBearType
import uk.tankbear.app.ui.theme.TankBearTokens
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.tankbear.app.data.Formatting
import uk.tankbear.app.data.NearbyResult
import uk.tankbear.app.data.Validation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NearbyMapPanel(vm: AppViewModel, result: NearbyResult?, onCar: () -> Unit) {
    var showStations by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val panelScroll = rememberScrollState()
    LaunchedEffect(vm.mapSelectedStationId) { panelScroll.animateScrollTo(0) }
    val filtered = result?.let(vm::sortedMapNearby).orEmpty().filter {
        !vm.mapNearbyOpenOnly || (!it.temporaryClosure && it.opening.state == "open")
    }
    val selected = filtered.firstOrNull { it.id == vm.mapSelectedStationId }
    val state = vm.currentMapNearby
    val hasStart = Validation.point(vm.originLat, vm.originLon) != null
    val validLitres = Validation.litres(vm.saved.litres) != null
    var retryAvailable by remember(state) { mutableStateOf(vm.mapRetryAvailable) }
    LaunchedEffect(state) {
        while (state is NearbyState.Failed && !retryAvailable) { delay(1000); retryAvailable = vm.mapRetryAvailable }
    }
    Column(Modifier.fillMaxWidth().testTag("nearby-panel"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Only the summary scrolls. Source credit and the action row keep their space.
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(panelScroll), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${vm.saved.fuel.label} · p/L", style = MaterialTheme.typography.labelMedium, color = TankBearTokens.colors.muted)
            if (!hasStart) {
                Text("Choose a start to see nearby stations.")
                FindPill("Choose on map", onClick = { vm.mapChoiceReturnsToFind = false; vm.beginMapChoice(MapTarget.Start) })
                LocationButton(vm)
            } else if (!validLitres) {
                Text("Set how many litres to buy on the Car tab.")
                FindPill("Check car details", onClick = onCar)
            } else {
                when (state) {
                    NearbyState.Idle, NearbyState.Loading -> Text("Checking nearby stations…")
                    is NearbyState.Failed -> {
                        Text(state.failure.message, color = MaterialTheme.colorScheme.error)
                        if (!retryAvailable) Text("Please wait before trying again.")
                    }
                    is NearbyState.Done -> {
                        if (result?.stations.isNullOrEmpty()) Text("No stations found within 5 km. Try another start.")
                        else if (filtered.isEmpty()) Text("No stations match Open now. Turn it off to see all stations.")
                        selected?.let { station ->
                            Column(Modifier.fillMaxWidth().clickable(onClickLabel = "View station details") { showDetails = true; showStations = true }
                                .testTag("nearby-station-summary"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(station.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val price = station.fill?.let { Formatting.pricePerLitre(it.pencePerLitre) } ?: "No price for your fuel"
                                val opening = if (station.temporaryClosure) "Reported temporarily closed" else Formatting.opening(station.opening)
                                if (LocalDensity.current.fontScale >= 1.5f) {
                                    Text(price, style = TankBearType.price)
                                    Text(opening, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(price, style = TankBearType.price, modifier = Modifier.weight(1f))
                                        Text(opening, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                                    }
                                }
                                station.fill?.let {
                                    Text("Filling ${vm.mapInputs.litres} L would cost ${Formatting.pence(it.fillCostPence)}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("${Formatting.metresAsMiles(station.distanceMetres)} away · straight line · excludes driving costs", style = MaterialTheme.typography.bodySmall, color = TankBearTokens.colors.muted)
                            }
                        }
                    }
                }
            }
        }
        if (result != null && state is NearbyState.Done) DataAttribution()
        if (hasStart && validLitres) {
            Row(Modifier.fillMaxWidth().testTag("nearby-map-actions"), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = result != null, onClick = { showDetails = false; showStations = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                    Text("Stations", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                TextButton(enabled = state is NearbyState.Done || (state is NearbyState.Failed && retryAvailable),
                    onClick = { vm.loadMapNearby(force = true) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                    Text(if (state is NearbyState.Failed) "Try again" else "Refresh", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                TextButton(onClick = { vm.beginMapChoice(MapTarget.Start) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                    Text("Change start", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (showStations && result != null) {
        ModalBottomSheet(onDismissRequest = { showStations = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.8f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Nearby stations", style = MaterialTheme.typography.headlineSmall) }
                if (selected != null) item {
                    TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Hide station details" else "Show station details") }
                }
                selected?.takeIf { showDetails }?.let { station ->
                    item {
                        Column(Modifier.testTag("nearby-station-details"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Selected station", style = MaterialTheme.typography.labelMedium, color = TankBearTokens.colors.muted)
                            Text(station.name, style = MaterialTheme.typography.titleMedium)
                            station.brand?.takeIf { it.isNotBlank() && !station.name.contains(it, ignoreCase = true) }?.let { Text(it) }
                            Text(station.fill?.let { Formatting.pricePerLitre(it.pencePerLitre) } ?: "No price for your fuel", style = TankBearType.price)
                            station.fill?.let {
                                Text("Filling ${vm.mapInputs.litres} L would cost ${Formatting.pence(it.fillCostPence)}", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(if (station.temporaryClosure) "Reported temporarily closed" else Formatting.opening(station.opening))
                            Text("${Formatting.metresAsMiles(station.distanceMetres)} away (straight line). Driving costs not calculated.")
                            station.fill?.let { Text("Price changed ${Formatting.age(it.priceLastUpdated, java.time.Instant.now())}") }
                            result.feedLastSuccessfulRefresh?.let { Text("Prices checked ${Formatting.age(it, java.time.Instant.now())}.") }
                            Text("Within 5 km of your start · up to 20 stations", style = MaterialTheme.typography.bodySmall)
                            DataAttribution()
                        }
                    }
                    item {
                        TextButton(onClick = { vm.toggleHistory(station.id) }) { Text(if (vm.histories.containsKey(station.id)) "Hide history for ${station.name}" else "Price history for ${station.name}") }
                    }
                    item { HistoryPanel(vm, station.id, java.time.Instant.now()) }
                }
                item {
                    val median = MapPrices.price(result.localMedianPencePerLitre)
                    Text(if (median == null) "Grey: local comparison unavailable. Prices exclude driving costs."
                    else "Local median: ${MapPrices.label(median.toPlainString())}/L. Green: more than 1p below; amber: within 1p; red: more than 1p above. Grey: comparison unavailable. Prices exclude driving costs.")
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = vm.mapNearbySort == NearbySort.Closest, onClick = { vm.mapNearbySort = NearbySort.Closest }, label = { Text("Closest") })
                        FilterChip(selected = vm.mapNearbySort == NearbySort.Cheapest, onClick = { vm.mapNearbySort = NearbySort.Cheapest }, label = { Text("Cheapest") })
                        FilterChip(modifier = Modifier.testTag("nearby-open-now"), selected = vm.mapNearbyOpenOnly, onClick = { vm.mapNearbyOpenOnly = !vm.mapNearbyOpenOnly }, label = { Text("Open now") })
                    }
                }
                if (filtered.isEmpty()) item { Text("No stations match this filter.") }
                items(filtered, key = { it.id }) { station ->
                    TextButton(onClick = { vm.selectMapStation(station.id); showStations = false }, modifier = Modifier.fillMaxWidth().testTag("station-list-${station.id}")) {
                        Text("${station.name} · ${station.fill?.let { Formatting.pricePerLitre(it.pencePerLitre) } ?: "No price"}")
                    }
                }
                item { DataAttribution() }
            }
        }
    }
}
