package uk.tankbear.app.ui

import uk.tankbear.app.ui.theme.TankBearTokens
import uk.tankbear.app.ui.theme.TankBearType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.tankbear.app.data.*

enum class RouteSort(val label: String) { Total("Best total"), Pump("Pump price"), Detour("Least driving") }

/** Display ordering only: backend ranks and monetary calculations remain authoritative. */
internal fun orderedCandidates(candidates: List<Candidate>, sort: RouteSort): List<Candidate> = when (sort) {
    RouteSort.Total -> candidates.sortedBy { it.rank }
    RouteSort.Pump -> candidates.sortedWith(compareBy<Candidate> { it.pencePerLitre.toBigDecimal() }.thenBy { it.rank })
    RouteSort.Detour -> candidates.sortedWith(compareBy<Candidate> { it.detourDurationSeconds.toBigDecimal() }
        .thenBy { it.detourDistanceMetres.toBigDecimal() }.thenBy { it.stationId })
}

@Composable
internal fun MapResults(vm: AppViewModel, onSelect: (String) -> Unit, header: (@Composable () -> Unit)? = null) {
    if (vm.resultsOutdated) {
        Text("Update results to compare stations for your new inputs.", Modifier.padding(16.dp))
        return
    }
    val nearbyMode = vm.mode == Mode.Nearby
    val journeyState = if (!nearbyMode) vm.search as? SearchState.Done else null
    val nearbyState = if (nearbyMode) vm.nearby as? NearbyState.Done else null
    val result = journeyState?.outcome?.result
    val nearby = nearbyState?.result
    val median = if (nearbyMode) MapPrices.price(nearby?.localMedianPencePerLitre)
        else MapPrices.median(result?.candidates.orEmpty().map { it.pencePerLitre })
    val medianScope = if (nearbyMode) "the local median" else "the median among routed stations checked"
    val candidates = orderedCandidates(result?.candidates.orEmpty(), vm.routeSort).filter { !vm.mapOpenOnly || it.opening.state == "open" }
    val stations = nearby?.let(vm::sortedNearby).orEmpty().filter { !vm.mapOpenOnly || (!it.temporaryClosure && it.opening.state == "open") }
    val ids = if (nearbyMode) stations.map { it.id } else candidates.map { it.stationId }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.selectedStationId, ids, vm.routeSelectionVersion) {
        val index = ids.indexOf(vm.selectedStationId)
        if (index >= 0) listState.animateScrollToItem(index + if (header == null) 1 else 2)
    }
    val now = java.time.Instant.now()
    val remaining = retrySeconds(vm)
    Column(Modifier.fillMaxSize()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (nearbyMode) NearbySort.entries.forEach { sort ->
                        ResultSortPill(sort.name, vm.nearbySort == sort) { vm.nearbySort = sort }
                    } else RouteSort.entries.forEach { sort ->
                        ResultSortPill(sort.label, vm.routeSort == sort) { vm.chooseRouteSort(sort) }
                    }
                    ResultSortPill("Open now", vm.mapOpenOnly) { vm.mapOpenOnly = !vm.mapOpenOnly }
                }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("route-results-list"), state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (header != null) item(key = "map-controls") { header() }
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result != null || nearby != null) Text("${ids.size} stations", style = MaterialTheme.typography.titleMedium)
                if (result != null || nearby != null) {
                    Text("Map labels: pump price in p/L for your selected fuel.", style = TankBearType.caption)
                    if (median != null) Text("Green: below. Amber: within 1p/L. Red: above. Compared with $medianScope (${MapPrices.label(median.toPlainString())}/L). Grey: comparison unavailable.", style = TankBearType.caption)
                    else Text("Grey: no price comparison available. Zoom in for more labels.", style = TankBearType.caption)
                    if (median != null) Text("Zoom in for more labels. Price colours do not include driving costs.", style = TankBearType.caption)
                }
                DataAttribution()
                val feed = result?.feedLastSuccessfulRefresh ?: nearby?.feedLastSuccessfulRefresh
                feed?.let { Text("Prices checked ${Formatting.age(it, now)}. Price changes are shown separately.") }
                result?.let {
                    Text(it.scope)
                    Text("Routed ${it.stationsRouted} of ${it.stationsInSearchArea} stations." +
                        if (it.partial) " Some could not be checked." else "")
                    if (it.closedNowExcluded > 0) Text("${it.closedNowExcluded} closed stations excluded.")
                    Text(if (it.baselineGeometry != null) "Grey: direct route. Amber: selected stop. Costs show the extra detour."
                        else if (it.oneWay) "Amber: one-way route to the station. Driving cost excludes a return journey."
                        else "Amber: there-and-back route. Driving cost covers the whole return trip.")
                }
                if (nearby != null) Text("Nearby distances are straight-line distances. Fill prices exclude driving costs.")
                val loading = if (nearbyMode) vm.nearby is NearbyState.Loading else vm.search is SearchState.Loading
                val failure = if (nearbyMode) (vm.nearby as? NearbyState.Failed)?.failure else (vm.search as? SearchState.Failed)?.failure
                if (loading) { RequestLoading("Checking stations and routes…"); TextButton(onClick = vm::cancel) { Text("Cancel search") } }
                failure?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error)
                    FindPill(if (remaining > 0) "Try again in ${remaining}s" else "Try search again", enabled = remaining == 0L, onClick = { vm.submit() })
                }
                if (!loading && failure == null && ids.isEmpty()) Text(if (result == null && nearby == null)
                    "Choose your start, then use the search button below." else if (vm.mapOpenOnly) "No stations match the Open now filter. Turn it off to see all results." else "No suitable stations found. Try another start or route, and check your selected fuel.")
                if (ids.isEmpty() && vm.mapOpenOnly && (result != null || nearby != null)) TextButton(onClick = { vm.mapOpenOnly = false }) { Text("Show all results") }
            }
        }
        if (result != null) items(candidates, key = { it.stationId }) { c ->
            JourneyStationCard(c, result, now, litres = journeyState?.inputs?.litres,
                selected = c.stationId == vm.selectedStationId, onSelect = { onSelect(c.stationId) },
                historyVisible = vm.histories.containsKey(c.stationId), onToggleHistory = { vm.toggleHistory(c.stationId) }) {
                HistoryPanel(vm, c.stationId, now)
            }
        }

        items(stations, key = { it.id }) { s ->
            ResultCard(s.id == vm.selectedStationId, { onSelect(s.id) }) {
                Text(s.name, style = TankBearType.heading)
                s.brand?.takeIf { it.isNotBlank() && !s.name.contains(it, ignoreCase = true) }?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
                Text("${Formatting.metresAsMiles(s.distanceMetres)} away (straight line) · ${Formatting.opening(s.opening)}")
                if (s.temporaryClosure) Text("Reported temporarily closed.")
                s.fill?.let {
                    Text(Formatting.pricePerLitre(it.pencePerLitre), style = TankBearType.price)
                    Text(MapPrices.comparison(it.pencePerLitre, median, medianScope), style = TankBearType.caption)
                    Text("Price changed ${Formatting.age(it.priceLastUpdated, now)}", style = TankBearType.caption, color = TankBearTokens.colors.muted)
                    Text("Fill ${Formatting.pence(it.fillCostPence)} · driving not calculated", style = MaterialTheme.typography.titleSmall)
                    HistoryControl(vm, s.id, now)
                } ?: Text("No price for your fuel here.")
            }
        }
    }
    }
}

@Composable
private fun ResultCard(selected: Boolean, onSelect: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TankBearTokens.colors.surface),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected) Text("Selected on map", style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}

@Composable
private fun HistoryControl(vm: AppViewModel, id: String, now: java.time.Instant) {
    FindPill(if (vm.histories.containsKey(id)) "Hide history" else "Price history", onClick = { vm.toggleHistory(id) })
    HistoryPanel(vm, id, now)
}
