package uk.tankbear.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import uk.tankbear.app.ui.theme.TankBearTokens
import uk.tankbear.app.ui.theme.TankBearType
import uk.tankbear.app.data.GeocodedPlace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uk.tankbear.app.data.GeocodingClient
import uk.tankbear.app.data.PlaceSearchResult

@Composable
internal fun PlaceSearch(vm: AppViewModel, target: MapTarget, onShowOnMap: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PlaceSearchResult?>(null) }
    var generation by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    PlaceSearchContent(query, target, loading, result,
        onQueryChange = {
            generation++; job?.cancel(); loading = false; result = null; query = it.take(200)
        }, onSearch = {
            loading = true
            result = null
            val submitted = query
            val revision = ++generation
            job = scope.launch {
                try {
                    val response = GeocodingClient(vm.saved.baseUrl, vm.saved.appKey).search(submitted)
                    if (generation == revision) result = response
                } finally { if (generation == revision) loading = false }
            }
        }, onChoose = { place ->
            vm.beginMapChoice(target, place.point)
            onShowOnMap()
        }, onAttribution = { uri.openUri(it) })
}

/** Presentation only; request cancellation and map confirmation stay in the owner. */
@Composable
internal fun PlaceSearchContent(
    query: String, target: MapTarget, loading: Boolean, result: PlaceSearchResult?,
    onQueryChange: (String) -> Unit, onSearch: () -> Unit,
    onChoose: (GeocodedPlace) -> Unit, onAttribution: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TankBearTokens.space.dp16)) {
        Text("Search ${target.name.lowercase()} by name or postcode", style = TankBearType.heading,
            modifier = Modifier.semantics { heading() })
        OutlinedTextField(value = query, onValueChange = onQueryChange,
            label = { Text("Place or postcode", style = TankBearType.caption) },
            singleLine = true, textStyle = TankBearType.body,
            shape = RoundedCornerShape(TankBearTokens.radius.card),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TankBearTokens.colors.surface,
                unfocusedContainerColor = TankBearTokens.colors.surface),
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
        Button(enabled = query.trim().length >= 3 && !loading, onClick = onSearch,
            modifier = Modifier.fillMaxWidth().heightIn(min = TankBearTokens.touch.min),
            shape = RoundedCornerShape(TankBearTokens.radius.pill),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
            Text(if (loading) "Searching places…" else "Search places", style = TankBearType.label,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
        }
        Text("Search sends this text to Geoapify. Choose a result, then confirm on the map.",
            style = TankBearType.caption, color = TankBearTokens.colors.muted)
        result?.let { response ->
            val message = response.error ?: if (response.places.isEmpty())
                "No places found. Try a fuller name or postcode, or choose on the map." else null
            message?.let {
                Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    color = TankBearTokens.colors.surface, shape = RoundedCornerShape(TankBearTokens.radius.card)) {
                    Text(it, Modifier.padding(TankBearTokens.space.dp16), style = TankBearType.body,
                        color = if (response.error != null) MaterialTheme.colorScheme.error else TankBearTokens.colors.text)
                }
            }
            response.places.forEach { place ->
                FindPill(place.label, onClick = { onChoose(place) })
            }
            Column {
                TextButton(onClick = { onAttribution("https://www.geoapify.com/") }, modifier = Modifier.heightIn(min = TankBearTokens.touch.min)) {
                    Text("Powered by Geoapify", style = TankBearType.caption)
                }
                TextButton(onClick = { onAttribution("https://www.openstreetmap.org/copyright") }, modifier = Modifier.heightIn(min = TankBearTokens.touch.min)) {
                    Text("© OpenStreetMap contributors", style = TankBearType.caption)
                }
            }
        }
    }
}
