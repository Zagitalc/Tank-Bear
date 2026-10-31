package uk.tankbear.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

/** Nearby fill estimates are supplied by the server and exclude driving costs. */
@Composable
internal fun NearbyStationCard(
    station: NearbyStation,
    cheapest: Boolean,
    litres: String?,
    feedLastSuccessfulRefresh: String?,
    now: Instant,
    historyVisible: Boolean,
    onToggleHistory: () -> Unit,
    modifier: Modifier = Modifier,
    history: @Composable () -> Unit,
) {
    var details by rememberSaveable(station.id, feedLastSuccessfulRefresh, station.fill) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth().testTag("nearby-result-${station.id}"),
        colors = CardDefaults.cardColors(containerColor = TankBearTokens.colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cheapest) Text("Cheapest among stations checked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(station.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            station.fill?.let { fill ->
                val pump: @Composable () -> Unit = {
                    Column {
                        Text("Pump price", style = MaterialTheme.typography.bodySmall, color = TankBearTokens.colors.muted)
                        Text(Formatting.pricePerLitre(fill.pencePerLitre), modifier = Modifier.fillMaxWidth(), style = TankBearType.price)
                    }
                }
                val cost: @Composable () -> Unit = {
                    Column {
                        Text(litres?.let { "Fill for $it L" } ?: "Fill estimate", style = MaterialTheme.typography.bodySmall, color = TankBearTokens.colors.muted)
                        Text(Formatting.pence(fill.fillCostPence), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge)
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { pump(); cost() }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { pump() }
                        Box(Modifier.weight(1f)) { cost() }
                    }
                }
                Text("Fill estimate excludes driving costs.", style = MaterialTheme.typography.bodySmall, color = TankBearTokens.colors.muted)
            } ?: Text("No price for your fuel here.", style = MaterialTheme.typography.bodyMedium)
            Text(if (station.temporaryClosure) "Reported temporarily closed" else Formatting.opening(station.opening), style = MaterialTheme.typography.bodyMedium)
            Text("${Formatting.metresAsMiles(station.distanceMetres)} away · straight line", style = MaterialTheme.typography.bodySmall)
            station.fill?.let { Text("Price changed ${Formatting.age(it.priceLastUpdated, now)}", style = MaterialTheme.typography.bodySmall, color = TankBearTokens.colors.muted) }
            TextButton(onClick = { details = !details }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (details) "Hide details" else "Details", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            if (details) Column(Modifier.testTag("nearby-result-details-${station.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                Text(station.name, style = MaterialTheme.typography.titleMedium)
                station.brand?.takeIf { it.isNotBlank() && !station.name.contains(it, ignoreCase = true) }?.let { Text(it) }
                station.fill?.let { fill ->
                    Text(Formatting.versusMedian(fill.vsLocalMedianPencePerLitre) ?: "Local price comparison unavailable.", style = MaterialTheme.typography.bodyMedium)
                    if (cheapest) Text("Based on stations with a price that aren't reported closed.", style = MaterialTheme.typography.bodySmall)
                }
                feedLastSuccessfulRefresh?.let { Text("Prices checked ${Formatting.age(it, now)}. Price changes are shown separately.", style = MaterialTheme.typography.bodySmall) }
                DataAttribution()
                if (station.fill != null) {
                    FindPill(if (historyVisible) "Hide history" else "Price history", onClick = onToggleHistory)
                    history()
                }
            }
        }
    }
}
