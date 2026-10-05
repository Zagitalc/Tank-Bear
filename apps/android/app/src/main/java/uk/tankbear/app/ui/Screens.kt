package uk.tankbear.app.ui

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
fun FindScreen(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Make your fuel stop count", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Compare pump prices with the extra driving needed to reach each station.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Column(Modifier.selectableGroup()) {
            Choice<Mode>("Along my journey", vm.mode == Mode.AlongJourney) { vm.mode = Mode.AlongJourney }
            Choice<Mode>("Fuel trip (there and back from here)", vm.mode == Mode.FuelTrip) { vm.mode = Mode.FuelTrip }
        }
        Text("Start", style = MaterialTheme.typography.titleMedium)
        CoordinateRow(vm.originLat, vm.originLon, { vm.originLat = it }, { vm.originLon = it }, "Start")
        if (vm.mode == Mode.AlongJourney) {
            Text("Destination", style = MaterialTheme.typography.titleMedium)
            CoordinateRow(vm.destLat, vm.destLon, { vm.destLat = it }, { vm.destLon = it }, "Destination")
        }
        OutlinedButton(onClick = vm::useExample) { Text("Fill in an example (Reading to Oxford)") }
        Text(
            "Picking places on a map and using your location come later. For now enter coordinates in decimal degrees.",
            style = MaterialTheme.typography.bodyMedium,
        )
        vm.formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
        val loading = vm.search is SearchState.Loading
        Button(onClick = vm::submit, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Find the best stop") }
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
            is SearchState.Done -> Results(s.outcome.result, s.at)
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
private fun Results(result: JourneyResult, now: java.time.Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(result.scope, style = MaterialTheme.typography.titleMedium)
        Text(
            "Routed ${result.stationsRouted} of ${result.stationsInSearchArea} stations near your route" +
                (if (result.partial) ". Some couldn't be checked, so this may miss a better stop." else "."),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Prices checked ${Formatting.age(result.feedLastSuccessfulRefresh, now)}. Each price shows when it last changed.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!result.ranked || result.candidates.isEmpty()) {
            Text("No station could be compared for this trip.", style = MaterialTheme.typography.bodyLarge)
        }
        val referenceName = result.candidates.firstOrNull { it.stationId == result.referenceStationId }?.name
        result.candidates.forEach { StationCard(it, result, referenceName, now) }
    }
}

@Composable
private fun StationCard(c: Candidate, r: JourneyResult, referenceName: String?, now: java.time.Instant) {
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
            Text("Extra driving: ${Formatting.miles(c.detourDistanceMetres)}, ${Formatting.minutes(c.detourDurationSeconds)}")
            Text("Pump saving ${Formatting.pence(c.pumpSavingPence)}, extra fuel to get there ${Formatting.pence(c.detourFuelCostPence)}")
            val headline = if (c.stationId == r.referenceStationId) "This is the comparison station" else Formatting.savingHeadline(c.trueSavingPence, referenceName)
            Text(headline, style = MaterialTheme.typography.titleSmall)
            if (c.stationId != r.referenceStationId && Formatting.isSmallDifference(c.trueSavingPence)) {
                Text("A difference this small is within the estimate's margin.", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Total for this stop ${Formatting.pence(c.comparisonCostPence)}", style = MaterialTheme.typography.bodyMedium)
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
