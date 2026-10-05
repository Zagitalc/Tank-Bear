package uk.tankbear.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import uk.tankbear.app.data.NearbyStation
import uk.tankbear.app.data.NearbyResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uk.tankbear.app.data.Candidate
import uk.tankbear.app.data.Formatting
import uk.tankbear.app.data.JourneyResult
import uk.tankbear.app.data.Mode

@Composable
private fun <T> Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun FindScreen(vm: AppViewModel, onShowOnMap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Make your fuel stop count", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Compare pump prices with the extra driving needed to reach each station.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Column(Modifier.selectableGroup()) {
            Choice<Mode>("Along my journey", vm.mode == Mode.AlongJourney) { vm.mode = Mode.AlongJourney }
            Choice<Mode>("Fuel trip (there and back from here)", vm.mode == Mode.FuelTrip) { vm.mode = Mode.FuelTrip }
            Choice<Mode>("Nearby stations (no journey)", vm.mode == Mode.Nearby) { vm.mode = Mode.Nearby }
        }
        Text("Start", style = MaterialTheme.typography.titleMedium)
        CoordinateRow(vm.originLat, vm.originLon, { vm.originLat = it }, { vm.originLon = it }, "Start")
        LocationButton(vm)
        if (vm.mode == Mode.AlongJourney) {
            Text("Destination", style = MaterialTheme.typography.titleMedium)
            CoordinateRow(vm.destLat, vm.destLon, { vm.destLat = it }, { vm.destLon = it }, "Destination")
        }
        SavedPlaces(vm)
        OutlinedButton(onClick = vm::useExample) { Text("Fill in an example (Reading to Oxford)") }
        Text(
            "Enter coordinates in decimal degrees, use your location, pick a saved place, or long-press the Map tab.",
            style = MaterialTheme.typography.bodyMedium,
        )
        vm.formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
        val loading = vm.search is SearchState.Loading
        Button(onClick = vm::submit, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (vm.mode == Mode.Nearby) "Find nearby stations" else "Find the best stop") }
        if (vm.mode == Mode.Nearby) {
            NearbySection(vm)
            return@Column
        }
        when (val s = vm.search) {
            SearchState.Idle -> Unit
            SearchState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Checking stations and routes…")
                OutlinedButton(onClick = vm::cancel) { Text("Cancel") }
            }
            is SearchState.Failed -> Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.failure.message, style = MaterialTheme.typography.bodyLarge)
                    s.failure.retryAfterSeconds?.let { Text("Try again in about $it seconds.") }
                }
            }
            is SearchState.Done -> Results(s.outcome.result, s.at, vm, onShowOnMap)
        }
    }
}

@Composable
private fun CoordinateRow(lat: String, lon: String, onLat: (String) -> Unit, onLon: (String) -> Unit, name: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            lat, onLat, label = { Text("$name latitude") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            lon, onLon, label = { Text("$name longitude") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Results(result: JourneyResult, now: java.time.Instant, vm: AppViewModel, onShowOnMap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(result.scope, style = MaterialTheme.typography.titleMedium)
        Text(
            "Routed ${result.stationsRouted} of ${result.stationsInSearchArea} stations near your route" +
                (if (result.partial) ". Some couldn't be checked, so this may miss a better stop." else "."),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (result.closedNowExcluded > 0) {
            Text(
                "${result.closedNowExcluded} station${if (result.closedNowExcluded == 1) "" else "s"} on your route ${if (result.closedNowExcluded == 1) "is" else "are"} closed right now and left out. Opening hours are usual hours and may differ on bank holidays.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "Prices checked ${Formatting.age(result.feedLastSuccessfulRefresh, now)}. Each price shows when it last changed.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!result.ranked || result.candidates.isEmpty()) {
            Text("No station could be compared for this trip.", style = MaterialTheme.typography.bodyLarge)
        }
        val referenceName = result.candidates.firstOrNull { it.stationId == result.referenceStationId }?.name
        result.candidates.forEach { StationCard(it, result, referenceName, now) { vm.selectStation(it.stationId); onShowOnMap() } }
    }
}

@Composable
private fun StationCard(c: Candidate, r: JourneyResult, referenceName: String?, now: java.time.Instant, onMap: () -> Unit) {
    val labels = buildList {
        if (c.stationId == r.bestOverallId) add("Best overall")
        if (c.stationId == r.cheapestPumpId) add("Cheapest pump")
        if (c.stationId == r.smallestDetourId) add("Smallest detour")
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (labels.isNotEmpty()) Text(labels.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("${c.rank}. ${c.name}", style = MaterialTheme.typography.titleMedium)
            Text("${Formatting.pricePerLitre(c.pencePerLitre)} · price changed ${Formatting.age(c.priceLastUpdated, now)}")
            Text(Formatting.opening(c.opening), style = MaterialTheme.typography.bodyMedium)
            Text("Extra driving: ${Formatting.miles(c.detourDistanceMetres)}, ${Formatting.minutes(c.detourDurationSeconds)}")
            Text("Pump saving ${Formatting.pence(c.pumpSavingPence)}, extra fuel to get there ${Formatting.pence(c.detourFuelCostPence)}")
            val headline = if (c.stationId == r.referenceStationId) "This is the comparison station" else Formatting.savingHeadline(c.trueSavingPence, referenceName)
            Text(headline, style = MaterialTheme.typography.titleSmall)
            if (c.stationId != r.referenceStationId && Formatting.isSmallDifference(c.trueSavingPence)) {
                Text("A difference this small is within the estimate's margin.", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Total for this stop ${Formatting.pence(c.comparisonCostPence)}", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onMap) { Text("Show route on map") }
        }
    }
}

@Composable
fun CarScreen(vm: AppViewModel) {
    val s = vm.saved
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your car, your fuel costs", style = MaterialTheme.typography.headlineSmall)
        Text("Use your car's fuel type and Imperial MPG to estimate what a detour costs.", style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.selectableGroup()) {
            vm.fuelOptions().forEach { f -> Choice<Unit>(f.label, s.fuel == f) { vm.update { it.copy(fuel = f) } } }
        }
        OutlinedTextField(
            s.mpg, { v -> vm.update { it.copy(mpg = v) } }, label = { Text("Miles per Imperial gallon (MPG)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            s.litres, { v -> vm.update { it.copy(litres = v) } }, label = { Text("Litres to buy") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        Text("Saved on this phone only. No account, no registration lookup.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.saved
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("You’re in control", style = MaterialTheme.typography.headlineSmall)
        Text("No account needed. Tank Bear will ask before using your location.", style = MaterialTheme.typography.bodyLarge)
        Text("Connection (development)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            s.baseUrl, { v -> vm.update { it.copy(baseUrl = v) } }, label = { Text("Server address") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            s.appKey, { v -> vm.update { it.copy(appKey = v) } }, label = { Text("App key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Your journey is sent to this server only when you tap Find. Nothing is stored there, and no location is collected in the background. " +
                "10.0.2.2 reaches a server on your computer from the Android emulator.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}


@Composable
private fun LocationButton(vm: AppViewModel) {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }

    fun locate() {
        locating = true
        vm.locationMessage = "Finding your location…"
        LocationFetcher.getOnce(context) { result ->
            locating = false
            when (result) {
                is LocationFetcher.Result.Found -> { blocked = false; vm.useLocation(result.point) }
                LocationFetcher.Result.Unavailable -> vm.locationMessage = "Location is switched off in your phone's settings. Turn it on, or enter coordinates."
                LocationFetcher.Result.NoFix -> vm.locationMessage = "Couldn't get a location fix. Try again, or enter coordinates."
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        // Approximate location is enough, so either grant works.
        if (granted.values.any { it }) { blocked = false; locate() } else {
            blocked = true
            vm.locationMessage = "Location permission is off. Allow it in the app's settings, or enter coordinates."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(enabled = !locating, onClick = {
            val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) locate()
            else launcher.launch(permissions)
        }) { Text("Use my location as the start") }
        vm.locationMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (blocked) {
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", context.packageName, null)))
            }) { Text("Open app settings") }
        }
        Text("Read once when you tap, never in the background, and never stored.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SavedPlaces(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Saved places", style = MaterialTheme.typography.titleMedium)
        if (vm.saved.places.isEmpty()) Text("None yet. Saved on this phone only.", style = MaterialTheme.typography.bodyMedium)
        vm.saved.places.forEach { place ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { vm.usePlace(place, asDestination = false) }) { Text("Start") }
                if (vm.mode == Mode.AlongJourney) TextButton(onClick = { vm.usePlace(place, asDestination = true) }) { Text("Destination") }
                TextButton(onClick = { vm.removePlace(place) }) { Text("Remove") }
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Name for a new place (Home, Work…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.savePlace(name, forDestination = false); if (vm.placeMessage == null) name = "" }) { Text("Save start") }
            if (vm.mode == Mode.AlongJourney) {
                OutlinedButton(onClick = { vm.savePlace(name, forDestination = true); if (vm.placeMessage == null) name = "" }) { Text("Save destination") }
            }
        }
        vm.placeMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun NearbySection(vm: AppViewModel) {
    when (val n = vm.nearby) {
        NearbyState.Idle -> Text(
            "Shows the closest stations to your start with today's price for your fuel and what filling up would cost. It compares nothing about a journey.",
            style = MaterialTheme.typography.bodyMedium,
        )
        NearbyState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Looking for stations…")
            OutlinedButton(onClick = vm::cancel) { Text("Cancel") }
        }
        is NearbyState.Failed -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp)) { Text(n.failure.message, style = MaterialTheme.typography.bodyLarge) }
        }
        is NearbyState.Done -> NearbyResults(vm, n.result, n.at)
    }
}

@Composable
private fun NearbyResults(vm: AppViewModel, result: NearbyResult, now: java.time.Instant) {
    val stations = vm.sortedNearby(result)
    // "Cheapest here" only among stations that look available and have a price.
    val cheapest = stations.filter { it.fill != null && !it.temporaryClosure && it.opening.state != "closed" }.minByOrNull { it.fill!!.fillCostPence }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Within 3 miles of your start, ${stations.size} found", style = MaterialTheme.typography.titleMedium)
        result.feedLastSuccessfulRefresh?.let {
            Text("Prices checked ${Formatting.age(it, now)}. Each price shows when it last changed.", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NearbySort.entries.forEach { sort -> FilterChip(selected = vm.nearbySort == sort, onClick = { vm.nearbySort = sort }, label = { Text(sort.name) }) }
        }
        if (stations.isEmpty()) Text("No stations with data near that point.", style = MaterialTheme.typography.bodyLarge)
        stations.forEach { NearbyCard(it, it.id == cheapest?.id, now) }
    }
}

@Composable
private fun NearbyCard(s: NearbyStation, cheapest: Boolean, now: java.time.Instant) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (cheapest) Text("Cheapest here", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(s.name, style = MaterialTheme.typography.titleMedium)
            Text("${Formatting.metresAsMiles(s.distanceMetres)} away · ${Formatting.opening(s.opening)}")
            if (s.temporaryClosure) Text("Reported temporarily closed for three days or more.", style = MaterialTheme.typography.bodyMedium)
            s.fill?.let {
                Text("${Formatting.pricePerLitre(it.pencePerLitre)} · price changed ${Formatting.age(it.priceLastUpdated, now)}")
                Text("Filling up would cost ${Formatting.pence(it.fillCostPence)}", style = MaterialTheme.typography.titleSmall)
            } ?: Text("No price for your fuel here.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
