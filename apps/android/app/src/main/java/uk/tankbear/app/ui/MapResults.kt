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
import androidx.compose.ui.unit.dp
import uk.tankbear.app.data.*

enum class RouteSort(val label: String) { Total("Best total"), Pump("Pump price"), Detour("Least driving") }

/** Display ordering only: backend ranks and monetary calculations remain authoritative. */
internal fun orderedCandidates(candidates: List<Candidate>, sort: RouteSort): List<Candidate> = when (sort) {
    RouteSort.Total -> candidates.sortedBy { it.rank }
    RouteSort.Pump -> candidates.sortedWith(compareBy<Candidate> { it.pencePerLitre.toBigDecimal() }.thenBy { it.rank })
    RouteSort.Detour -> candidates.sortedWith(compareBy<Candidate> { it.detourDurationSeconds.toBigDecimal() }.thenBy { it.rank })
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
    val candidates = orderedCandidates(result?.candidates.orEmpty(), vm.routeSort).filter { !vm.mapOpenOnly || it.opening.state == "open" }
    val stations = nearby?.let(vm::sortedNearby).orEmpty().filter { !vm.mapOpenOnly || (!it.temporaryClosure && it.opening.state == "open") }
    val ids = if (nearbyMode) stations.map { it.id } else candidates.map { it.stationId }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.selectedStationId, ids) {
        val index = ids.indexOf(vm.selectedStationId)
        if (index >= 0) listState.animateScrollToItem(index + if (header == null) 1 else 2)
    }
    val now = java.time.Instant.now()
    Column(Modifier.fillMaxSize()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (nearbyMode) NearbySort.entries.forEach { sort ->
                        ResultSortPill(sort.name, vm.nearbySort == sort) { vm.nearbySort = sort }
                    } else RouteSort.entries.forEach { sort ->
                        ResultSortPill(sort.label, vm.routeSort == sort) { vm.routeSort = sort }
                    }
                    ResultSortPill("Open now", vm.mapOpenOnly) { vm.mapOpenOnly = !vm.mapOpenOnly }
                }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (header != null) item(key = "map-controls") { header() }
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result != null || nearby != null) Text("${ids.size} stations", style = MaterialTheme.typography.titleMedium)
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
                if (loading) { Text("Checking stations…"); TextButton(onClick = vm::cancel) { Text("Cancel") } }
                failure?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                if (!loading && failure == null && ids.isEmpty()) Text(if (result == null && nearby == null)
                    "Choose your start, then use the search button below." else if (vm.mapOpenOnly) "No stations match the Open now filter. Turn it off to see all results." else "No suitable stations found. Try another start or route, and check your selected fuel.")
                if (ids.isEmpty() && vm.mapOpenOnly && (result != null || nearby != null)) TextButton(onClick = { vm.mapOpenOnly = false }) { Text("Show all results") }
            }
        }
        if (result != null) items(candidates, key = { it.stationId }) { c ->
            ResultCard(c.stationId == vm.selectedStationId, { onSelect(c.stationId) }) {
                Text("${c.rank}. ${c.name}", style = TankBearType.heading)
                if (c.stationId == result.bestOverallId) Text("Best overall among stations checked", color = MaterialTheme.colorScheme.primary)
                Text(Formatting.pricePerLitre(c.pencePerLitre), style = TankBearType.price)
                Text("Price changed ${Formatting.age(c.priceLastUpdated, now)}", style = TankBearType.caption, color = TankBearTokens.colors.muted)
                Text(Formatting.opening(c.opening))
                Text("Fill ${Formatting.pence(c.fillCostPence)} + driving ${Formatting.pence(c.detourFuelCostPence)}")
                Text("Estimated total: ${Formatting.pence(c.comparisonCostPence)} (rounded)", style = TankBearType.heading)
                Text("${if (result.baselineGeometry != null) "Extra detour" else if (result.oneWay) "One-way driving" else "There and back"}: ${Formatting.miles(c.detourDistanceMetres)}, ${Formatting.minutes(c.detourDurationSeconds)}")
                Text("Using the MPG and fuel price from this search.", style = MaterialTheme.typography.bodySmall)
                val reference = result.candidates.firstOrNull { it.stationId == result.referenceStationId }?.name
                Text(if (c.stationId == result.referenceStationId) "Comparison station" else Formatting.savingHeadline(c.trueSavingPence, reference))
                if (c.stationId != result.referenceStationId && Formatting.isSmallDifference(c.trueSavingPence)) Text("A difference this small is within the estimate's margin.")
                HistoryControl(vm, c.stationId, now)
            }
        }
        items(stations, key = { it.id }) { s ->
            ResultCard(s.id == vm.selectedStationId, { onSelect(s.id) }) {
                Text(s.name, style = TankBearType.heading)
                Text("${Formatting.metresAsMiles(s.distanceMetres)} away (straight line) · ${Formatting.opening(s.opening)}")
                if (s.temporaryClosure) Text("Reported temporarily closed.")
                s.fill?.let {
                    Text(Formatting.pricePerLitre(it.pencePerLitre), style = TankBearType.price)
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
