package uk.tankbear.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.tankbear.app.data.*
import uk.tankbear.app.ui.theme.TankBearTokens
import uk.tankbear.app.ui.theme.TankBearType
import java.time.Instant

/** Presents backend-owned money and ranking unchanged in Find and the route map. */
@Composable
internal fun JourneyStationCard(
    candidate: Candidate,
    result: JourneyResult,
    now: Instant,
    modifier: Modifier = Modifier,
    litres: String? = null,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onMap: (() -> Unit)? = null,
    historyVisible: Boolean = false,
    onToggleHistory: () -> Unit,
    history: @Composable () -> Unit,
) {
    var details by rememberSaveable(candidate.stationId, result.feedLastSuccessfulRefresh, candidate.comparisonCostPence) { mutableStateOf(false) }
    val referenceName = result.candidates.firstOrNull { it.stationId == result.referenceStationId }?.name
    val isReference = candidate.stationId == result.referenceStationId
    val drivingLabel = if (result.baselineGeometry != null) "Extra driving" else if (result.oneWay) "One-way driving" else "There and back"
    val labels = buildList {
        if (candidate.stationId == result.bestOverallId) add("Best total among stations checked")
        if (candidate.stationId == result.cheapestPumpId) add("Cheapest pump")
        if (candidate.stationId == result.smallestDetourId) add("Least driving")
    }
    val content: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected) Text("Selected on map", style = MaterialTheme.typography.labelMedium)
            labels.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            Text("${candidate.rank}. ${candidate.name}", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val total: @Composable () -> Unit = {
                Column {
                    Text(litres?.let { "Estimated total for $it L" } ?: "Estimated total", style = MaterialTheme.typography.bodySmall, color = TankBearTokens.colors.muted)
                    Text(Formatting.pence(candidate.comparisonCostPence), style = TankBearType.price,
                        modifier = Modifier.testTag("journey-total-${candidate.stationId}"))
                }
            }
            val pump: @Composable () -> Unit = {
                Column {
                    Text("Pump price", style = MaterialTheme.typography.bodySmall, color = TankBearTokens.colors.muted)
                    Text(Formatting.pricePerLitre(candidate.pencePerLitre), style = MaterialTheme.typography.titleMedium)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { total(); pump() }
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { total() }
                    Box(Modifier.weight(1f)) { pump() }
                }
            }
            Text("$drivingLabel: ${Formatting.minutes(candidate.detourDurationSeconds)} · ${Formatting.miles(candidate.detourDistanceMetres)}",
                style = MaterialTheme.typography.bodyMedium)
            Text(if (isReference) "This is the comparison station" else Formatting.savingHeadline(candidate.trueSavingPence, referenceName ?: "comparison station"),
                style = MaterialTheme.typography.labelLarge)
            if (!isReference && Formatting.isSmallDifference(candidate.trueSavingPence)) {
                Text("A difference this small is within the estimate's margin.", style = MaterialTheme.typography.bodySmall)
            }
            Text(Formatting.opening(candidate.opening), style = MaterialTheme.typography.bodySmall)
            Text("Price changed ${Formatting.age(candidate.priceLastUpdated, now)}", style = MaterialTheme.typography.bodySmall, color = TankBearTokens.colors.muted)
            DataAttribution()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { details = !details }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(if (details) "Hide details" else "Details", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                if (onMap != null) FilledTonalButton(onClick = onMap, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    Text("View route", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
            if (details) {
                HorizontalDivider()
                Text(candidate.name, style = MaterialTheme.typography.titleMedium)
                if (labels.size > 1) Text(labels.drop(1).joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                Text("Fill: ${Formatting.pence(candidate.fillCostPence)}")
                Text("$drivingLabel fuel: ${Formatting.pence(candidate.detourFuelCostPence)}")
                Text("Estimated total: ${Formatting.pence(candidate.comparisonCostPence)} (rounded)")
                Text("Using the MPG and fuel price from this search. Line items and totals are rounded separately and can differ by a penny.", style = MaterialTheme.typography.bodySmall)
                if (!isReference) {
                    Text(referenceName?.let { "Comparison station: $it" } ?: "Comparison station name unavailable in these results.")
                    Text("Pump-price saving: ${Formatting.pence(candidate.pumpSavingPence)}")
                    Text("Comparison station's driving fuel: ${Formatting.pence(candidate.referenceDetourFuelCostPence)}")
                    Text("Savings compare both stations' fill and driving costs.", style = MaterialTheme.typography.bodySmall)
                }
                val median = remember(result.candidates) { MapPrices.median(result.candidates.map { it.pencePerLitre }) }
                Text(MapPrices.comparison(candidate.pencePerLitre, median, "the median among routed stations checked"), style = MaterialTheme.typography.bodySmall)
                Text("Prices checked ${Formatting.age(result.feedLastSuccessfulRefresh, now)}. Price changes are shown separately.", style = MaterialTheme.typography.bodySmall)
                FindPill(if (historyVisible) "Hide history" else "Price history", onClick = onToggleHistory)
                history()
            }
        }
    }
    val cardModifier = modifier.fillMaxWidth().testTag("journey-station-${candidate.stationId}")
    val colors = CardDefaults.cardColors(containerColor = TankBearTokens.colors.surface)
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    if (onSelect != null) Card(onClick = onSelect, modifier = cardModifier, colors = colors, border = border, content = content)
    else Card(modifier = cardModifier, colors = colors, border = border, content = content)
}
