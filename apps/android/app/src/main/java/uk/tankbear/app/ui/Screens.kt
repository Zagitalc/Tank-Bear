package uk.tankbear.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import uk.tankbear.app.ui.theme.TankBearTokens
import uk.tankbear.app.ui.theme.TankBearType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import uk.tankbear.app.data.Validation
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.tankbear.app.BuildConfig
import uk.tankbear.app.R
import androidx.core.content.ContextCompat
import uk.tankbear.app.data.NearbyStation
import uk.tankbear.app.data.NearbyResult
import androidx.compose.material3.Text
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uk.tankbear.app.data.Candidate
import uk.tankbear.app.data.PriceHistory
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FindScreen(vm: AppViewModel, onShowOnMap: () -> Unit) {
    val resultsAnchor = remember { BringIntoViewRequester() }
    var seenVersion by remember { mutableStateOf(vm.findResultsVersion) }
    LaunchedEffect(vm.findResultsVersion) {
        if (vm.findResultsVersion != seenVersion) {
            seenVersion = vm.findResultsVersion
            withFrameNanos { }
            resultsAnchor.bringIntoView()
        }
    }
    val resultsModifier = Modifier.bringIntoViewRequester(resultsAnchor)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Make your fuel stop count", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Compare pump prices with the extra driving needed to reach each station.",
            style = MaterialTheme.typography.bodyLarge, color = TankBearTokens.colors.muted,
        )
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TankBearTokens.colors.surface)) {
          Column(Modifier.selectableGroup().padding(horizontal = 16.dp, vertical = 8.dp)) {
            JourneyChoice("Along my journey", vm.mode == Mode.AlongJourney) { vm.mode = Mode.AlongJourney }
            JourneyChoice("Fuel trip (there and back from here)", vm.mode == Mode.FuelTrip) { vm.mode = Mode.FuelTrip }
            JourneyChoice("Nearby stations (no journey)", vm.mode == Mode.Nearby) { vm.mode = Mode.Nearby }
        }
        }
        var choosingLocation by rememberSaveable { mutableStateOf<MapTarget?>(null) }
        FindLocationRow(vm, MapTarget.Start) { choosingLocation = MapTarget.Start }
        if (vm.mode == Mode.AlongJourney) FindLocationRow(vm, MapTarget.Destination) { choosingLocation = MapTarget.Destination }
        choosingLocation?.let { target ->
            FindLocationPicker(vm, target, onDismiss = { choosingLocation = null }, onShowOnMap = onShowOnMap)
        }
        var managePlaces by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { managePlaces = !managePlaces }) { Text(if (managePlaces) "Hide saved places" else "Manage saved places") }
        if (managePlaces) SavedPlaces(vm)
        var moreOptions by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { moreOptions = !moreOptions }) { Text(if (moreOptions) "Fewer options" else "More options") }
        if (moreOptions) {
            Text("Coordinates (optional)", style = MaterialTheme.typography.titleMedium)
            CoordinateRow(vm.originLat, vm.originLon, { vm.originLat = it }, { vm.originLon = it }, "Start")
            if (vm.mode == Mode.AlongJourney) CoordinateRow(vm.destLat, vm.destLon, { vm.destLat = it }, { vm.destLon = it }, "Destination")
            FindPill("Fill in an example (Reading to Oxford)", onClick = vm::useExample)
        }
        if (vm.resultsOutdated) {
            Text("Previous results are outdated. Update results for your new locations or car settings.")
            return@Column
        }
        if (vm.mode == Mode.Nearby) {
            if (vm.nearby is NearbyState.Done) FindPill("Map and results", onClick = onShowOnMap)
            NearbySection(vm, resultsModifier, onChangeStart = { choosingLocation = MapTarget.Start })
            return@Column
        }
        when (val s = vm.search) {
            SearchState.Idle -> Unit
            SearchState.Loading, is SearchState.Failed -> Unit // Fixed action bar owns request feedback.
            is SearchState.Done -> Results(s.outcome.result, s.at, vm, onShowOnMap, resultsModifier)
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

/** Fuel Finder requires this credit wherever prices are shown. */
@Composable
fun DataAttribution() {
    Text(stringResource(R.string.data_attribution), style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun Results(result: JourneyResult, now: java.time.Instant, vm: AppViewModel, onShowOnMap: () -> Unit, resultsModifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(result.scope, style = TankBearType.heading)
        DataAttribution()
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
            Text("No suitable stations could be compared for this trip. Try a different start or route, and check your selected fuel. Closed stations or missing prices can limit results.", resultsModifier, style = MaterialTheme.typography.bodyLarge)
        }
        if (result.candidates.isNotEmpty()) Text("Station results", resultsModifier, style = MaterialTheme.typography.titleMedium)
        val shown = orderedCandidates(result.candidates, vm.routeSort).filter { !vm.mapOpenOnly || it.opening.state == "open" }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RouteSort.entries.forEach { sort -> ResultSortPill(sort.label, vm.routeSort == sort) { vm.routeSort = sort } }
            ResultSortPill("Open now", vm.mapOpenOnly) { vm.mapOpenOnly = !vm.mapOpenOnly }
        }
        if (shown.isEmpty() && result.candidates.isNotEmpty()) {
            Text("No stations match the Open now filter. Turn it off to see all results.")
            FindPill("Show all results", onClick = { vm.mapOpenOnly = false })
        }
        shown.forEach { candidate ->
            key(candidate.stationId) { StationCard(candidate, result, now, vm) { vm.selectStation(candidate.stationId); onShowOnMap() } }
        }
    }
}

@Composable
private fun StationCard(c: Candidate, r: JourneyResult, now: java.time.Instant, vm: AppViewModel, onMap: () -> Unit) {
    JourneyStationCard(c, r, now, litres = (vm.search as? SearchState.Done)?.inputs?.litres,
        onMap = onMap, historyVisible = vm.histories.containsKey(c.stationId), onToggleHistory = { vm.toggleHistory(c.stationId) }) {
        HistoryPanel(vm, c.stationId, now)
    }
}

@Composable
fun CarScreen(vm: AppViewModel) {
    val s = vm.saved
    Column(verticalArrangement = Arrangement.spacedBy(TankBearTokens.space.dp16)) {
        Text("Your car, your fuel costs", style = TankBearType.heading)
        Text("Choose your fuel, driving efficiency and how much you plan to buy.",
            style = TankBearType.body, color = TankBearTokens.colors.muted)
        Text("Fuel type", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(TankBearTokens.space.dp16)) {
            vm.fuelOptions().forEach { f ->
                androidx.compose.material3.Surface(Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(TankBearTokens.radius.pill), color = TankBearTokens.colors.soft) {
                    Column(Modifier.padding(horizontal = TankBearTokens.space.dp16)) {
                        JourneyChoice(f.label, s.fuel == f) { vm.update { it.copy(fuel = f) } }
                    }
                }
            }
        }
        CarNumberField(s.mpg, "Miles per Imperial gallon (MPG)",
            "Use UK Imperial MPG. This estimates driving fuel costs; nearby fill estimates only need fuel type and litres.",
            if (Validation.mpg(s.mpg) == null) "Enter 5–200 Imperial MPG, with up to 3 decimal places." else null
        ) { v -> vm.update { it.copy(mpg = v) } }
        CarNumberField(s.litres, "Litres to buy",
            "The amount you plan to buy. Sets the fill estimate on Find and Map.",
            if (Validation.litres(s.litres) == null) "Enter 1–500 litres, with up to 3 decimal places." else null
        ) { v -> vm.update { it.copy(litres = v) } }
        Text("Saved automatically on this phone. No account, no registration lookup.",
            style = TankBearType.caption, color = TankBearTokens.colors.muted)
    }
}

@Composable
private fun CarNumberField(value: String, label: String, hint: String, error: String?, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label, style = TankBearType.caption) }, singleLine = true,
        textStyle = TankBearType.body, shape = RoundedCornerShape(TankBearTokens.radius.card),
        isError = error != null,
        supportingText = { Text(error ?: hint) },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TankBearTokens.colors.surface,
            unfocusedContainerColor = TankBearTokens.colors.surface),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.saved
    Column(verticalArrangement = Arrangement.spacedBy(TankBearTokens.space.dp16)) {
        Text("You’re in control", style = TankBearType.heading)
        Text("No account or app key is needed for the friend beta.", style = MaterialTheme.typography.bodyMedium)
        SettingsSection("Location", listOf(
            "Choose on map works without location permission. Use my location reads your location once when you tap it and asks for permission when needed.",
            "Find sends your chosen start and destination, when needed, to Tank Bear when you tap Find. Map uses your chosen start to request nearby prices when opened or refreshed.",
        ))
        SettingsSection("Privacy", listOf(
            "Saved places and car settings are saved on this phone. Searches use your selected locations, fuel type, litres and MPG when needed to estimate costs.",
            "No background location, accounts or analytics.",
            "Search places sends your search text through Tank Bear to Geoapify. Viewing the map requests tiles from OpenFreeMap.",
        ))
        SettingsSection("Data", listOf(
            stringResource(R.string.data_attribution),
            "Price changed shows when an individual price last changed. Prices checked shows the last successful feed refresh.",
            "Nearby distances are straight-line distances and fill estimates exclude driving costs. Journey results use routed driving costs.",
            "Map data © OpenStreetMap contributors, shown through MapLibre with OpenFreeMap tiles.",
        ))
        SettingsSection("App version", listOf(
            "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            "Include this version when reporting a problem.",
        ))
        if (BuildConfig.DEBUG) {
            var advanced by remember { mutableStateOf(false) }
            TextButton(onClick = { advanced = !advanced }) { Text("Developer connection settings") }
            if (advanced) {
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
            "Find and nearby Map requests use this server. Place searches also pass through it. " +
                "10.0.2.2 reaches a server on your computer from the Android emulator.",
            style = MaterialTheme.typography.bodyMedium,
        )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, lines: List<String>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TankBearTokens.colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}


@Composable
internal fun LocationButton(vm: AppViewModel, onLocationChosen: () -> Unit = {}) {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    val locationChosen by rememberUpdatedState(onLocationChosen)
    DisposableEffect(Unit) { onDispose { active = false } }

    fun locate() {
        locating = true
        vm.locationMessage = "Finding your location…"
        LocationFetcher.getOnce(context) { result ->
            if (!active) return@getOnce
            locating = false
            when (result) {
                is LocationFetcher.Result.Found -> { blocked = false; vm.useLocation(result.point); locationChosen() }
                LocationFetcher.Result.Unavailable -> vm.locationMessage = "Location is switched off in your phone's settings. Turn it on, or choose a point on the map."
                LocationFetcher.Result.NoFix -> vm.locationMessage = "Couldn't get a location fix. Try again, or choose a point on the map."
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        // Approximate location is enough, so either grant works.
        if (granted.values.any { it }) { blocked = false; locate() } else {
            blocked = true
            vm.locationMessage = "Location permission is off. Allow it in the app's settings, or choose a point on the map."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FindPill("Use my location as the start", enabled = !locating, onClick = {
            val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) locate()
            else launcher.launch(permissions)
        })
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
@OptIn(ExperimentalLayoutApi::class)
private fun SavedPlaces(vm: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    val actionWidth = 112.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val saveWidth = 160.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(Modifier.testTag("saved-places"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Saved places", style = MaterialTheme.typography.titleMedium)
        if (vm.saved.places.isEmpty()) Text("None yet. Saved on this phone only.", style = MaterialTheme.typography.bodyMedium)
        vm.saved.places.forEach { place ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TankBearTokens.colors.surface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.usePlace(place, asDestination = false) }, modifier = Modifier.width(actionWidth).heightIn(min = 48.dp)) { Text("Start", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                        if (vm.mode == Mode.AlongJourney) TextButton(onClick = { vm.usePlace(place, asDestination = true) }, modifier = Modifier.width(actionWidth).heightIn(min = 48.dp)) { Text("Destination", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                        TextButton(onClick = { vm.removePlace(place) }, modifier = Modifier.width(actionWidth).heightIn(min = 48.dp)) { Text("Remove", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    }
                }
            }
        }
        vm.removedPlaceName?.let { removed ->
            Column {
                Text("Removed $removed from this phone.")
                TextButton(onClick = vm::undoRemovePlace, modifier = Modifier.width(actionWidth).heightIn(min = 48.dp)) { Text("Undo", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Name for a new place (Home, Work…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Using an existing name updates that place.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { if (vm.savePlace(name, forDestination = false)) name = "" }, modifier = Modifier.width(saveWidth).heightIn(min = 48.dp)) { Text("Save start", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            if (vm.mode == Mode.AlongJourney) {
                OutlinedButton(onClick = { if (vm.savePlace(name, forDestination = true)) name = "" }, modifier = Modifier.width(saveWidth).heightIn(min = 48.dp)) { Text("Save destination", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            }
        }
        vm.placeMessage?.let { Text(it, color = if (vm.placeMessageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun NearbySection(vm: AppViewModel, resultsModifier: Modifier = Modifier, onChangeStart: () -> Unit) {
    when (val n = vm.nearby) {
        NearbyState.Idle -> Text(
            "Shows the closest stations to your start with today's price for your fuel and what filling up would cost. It compares nothing about a journey.",
            style = MaterialTheme.typography.bodyMedium,
        )
        NearbyState.Loading, is NearbyState.Failed -> Unit // Fixed action bar owns request feedback.
        is NearbyState.Done -> NearbyResults(vm, n.result, n.at, resultsModifier, onChangeStart)
    }
}

@Composable
private fun NearbyResults(vm: AppViewModel, result: NearbyResult, now: java.time.Instant, resultsModifier: Modifier = Modifier, onChangeStart: () -> Unit) {
    val stations = vm.sortedNearby(result)
    // Only badge stations that look available and have a price.
    val cheapest = stations.filter { it.fill != null && !it.temporaryClosure && it.opening.state != "closed" }.minByOrNull { it.fill!!.fillCostPence }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Within 3 miles of your start, ${stations.size} found", style = MaterialTheme.typography.titleMedium)
        DataAttribution()
        result.feedLastSuccessfulRefresh?.let {
            Text("Prices checked ${Formatting.age(it, now)}. Each price shows when it last changed.", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Nearby distances are straight-line distances. Fill prices exclude driving costs.", style = TankBearType.caption, color = TankBearTokens.colors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NearbySort.entries.forEach { sort -> FilterChip(modifier = Modifier.weight(1f).heightIn(min = 48.dp), selected = vm.nearbySort == sort, onClick = { vm.nearbySort = sort }, label = { Text(sort.name, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }) }
        }
        if (stations.isEmpty()) {
            Text("No stations with data near this start. Choose another start on the map and search again.", resultsModifier, style = MaterialTheme.typography.bodyLarge)
            FindPill("Change start", onClick = onChangeStart)
        }
        else Text("Station results", resultsModifier, style = MaterialTheme.typography.titleMedium)
        stations.forEach { station ->
            key(station.id) {
                NearbyStationCard(station, station.id == cheapest?.id, (vm.nearby as? NearbyState.Done)?.inputs?.litres,
                    result.feedLastSuccessfulRefresh, now, vm.histories.containsKey(station.id), { vm.toggleHistory(station.id) }) {
                    HistoryPanel(vm, station.id, now)
                }
            }
        }
    }
}


@Composable
internal fun HistoryPanel(vm: AppViewModel, stationId: String, now: java.time.Instant) {
    when (val h = vm.histories[stationId]) {
        null -> Unit
        HistoryState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.padding(4.dp)); Text("Loading history…")
        }
        is HistoryState.Failed -> Text(h.message, style = MaterialTheme.typography.bodyMedium)
        is HistoryState.Done -> HistoryBody(h.history, now)
    }
}

@Composable
private fun HistoryBody(h: PriceHistory, now: java.time.Instant) {
    val days = h.coverage.windowDays
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TankBearTokens.colors.surface)) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Price history", style = TankBearType.heading)
        Text(
            h.trend?.let { Formatting.trendHeadline(it, days) } ?: "No $days-day trend yet",
            style = MaterialTheme.typography.titleSmall,
        )
        // A trend is shown only when the whole window was watched; otherwise say exactly why not.
        h.coverage.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        PriceStepChart(h)
        DataAttribution()
        Text(
            "Prices are the provider's own change times. Tank Bear has watched since " +
                (h.coverage.watchingSince?.let { Formatting.age(it, now) } ?: "never") + ".",
            style = MaterialTheme.typography.bodySmall,
        )
      }
    }
}

/** Step chart of recorded prices across the window. The summary is also spoken by screen readers. */
@Composable
private fun PriceStepChart(h: PriceHistory) {
    val pts = h.points
    val values = pts.mapNotNull { it.pencePerLitre.toDoubleOrNull() }
    if (values.isEmpty()) { Text("No recorded changes in this window.", style = MaterialTheme.typography.bodyMedium); return }
    val start = java.time.Instant.parse(h.windowFrom).toEpochMilli().toDouble()
    val end = java.time.Instant.parse(h.windowTo).toEpochMilli().toDouble()
    val lo = values.min()
    val hi = values.max()
    val range = (hi - lo).coerceAtLeast(0.5)
    val color = MaterialTheme.colorScheme.primary
    val axis = TankBearTokens.colors.outline
    val summary = "Price history over ${h.coverage.windowDays} days: ${pts.joinToString(", ") { "${it.pencePerLitre}p" }}"
    Canvas(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(112.dp).semantics { contentDescription = summary }) {
        val inset = 4.dp.toPx()
        val plotWidth = size.width - inset * 2
        val plotHeight = size.height - inset * 2
        fun x(iso: String) = inset + (((java.time.Instant.parse(iso).toEpochMilli() - start) / (end - start)).coerceIn(0.0, 1.0) * plotWidth).toFloat()
        fun y(p: String) = inset + (plotHeight - ((p.toDouble() - lo) / range) * plotHeight).toFloat()
        drawLine(axis, Offset(inset, inset), Offset(inset, size.height - inset), 1.dp.toPx())
        drawLine(axis, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), 1.dp.toPx())
        val path = Path()
        pts.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(p.at), y(p.pencePerLitre)) else { path.lineTo(x(p.at), y(pts[i - 1].pencePerLitre)); path.lineTo(x(p.at), y(p.pencePerLitre)) }
        }
        h.currentPencePerLitre?.let { path.lineTo(size.width - inset, y(it)) }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        pts.forEach { drawCircle(color, 3.dp.toPx(), Offset(x(it.at), y(it.pencePerLitre))) }
    }
    Text("Low ${Formatting.pricePerLitre(trimTrailingZero(lo))} · high ${Formatting.pricePerLitre(trimTrailingZero(hi))}", style = MaterialTheme.typography.bodySmall)
}

private fun trimTrailingZero(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
internal fun ResultSortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val width = if (LocalDensity.current.fontScale >= 1.5f) 220.dp else 128.dp
    FilterChip(selected = selected, onClick = onClick,
        modifier = Modifier.width(width).heightIn(min = 48.dp),
        shape = RoundedCornerShape(TankBearTokens.radius.pill),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = TankBearTokens.colors.surface,
            selectedContainerColor = TankBearTokens.colors.soft,
            labelColor = TankBearTokens.colors.text,
            selectedLabelColor = TankBearTokens.colors.text),
        border = null,
        label = { Text(label, Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center, style = TankBearType.label) })
}
