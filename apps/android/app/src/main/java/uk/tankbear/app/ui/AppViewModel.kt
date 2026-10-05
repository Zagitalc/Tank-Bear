package uk.tankbear.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uk.tankbear.app.data.ApiOutcome
import uk.tankbear.app.data.FuelType
import uk.tankbear.app.data.JourneyRequest
import uk.tankbear.app.data.JourneyResult
import uk.tankbear.app.data.LatLon
import uk.tankbear.app.data.NearbyResult
import uk.tankbear.app.data.Places
import uk.tankbear.app.data.SavedPlace
import uk.tankbear.app.data.Mode
import uk.tankbear.app.data.OptimiseClient
import uk.tankbear.app.data.Prefs
import uk.tankbear.app.data.Saved
import uk.tankbear.app.data.Validation

enum class MapTarget { Start, Destination }

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Done(val outcome: ApiOutcome.Ok, val at: java.time.Instant) : SearchState
    data class Failed(val failure: ApiOutcome.Failure) : SearchState
}

sealed interface NearbyState {
    data object Idle : NearbyState
    data object Loading : NearbyState
    data class Done(val result: NearbyResult, val at: java.time.Instant) : NearbyState
    data class Failed(val failure: ApiOutcome.Failure) : NearbyState
}

enum class NearbySort { Closest, Cheapest }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var saved by mutableStateOf(Saved())
        private set
    var loaded by mutableStateOf(false)
        private set

    var mode by mutableStateOf(Mode.AlongJourney)
    var originLat by mutableStateOf("")
    var originLon by mutableStateOf("")
    var destLat by mutableStateOf("")
    var destLon by mutableStateOf("")
    var formError by mutableStateOf<String?>(null)
        private set
    var search by mutableStateOf<SearchState>(SearchState.Idle)
        private set
    private var job: Job? = null

    var nearby by mutableStateOf<NearbyState>(NearbyState.Idle)
        private set
    var nearbySort by mutableStateOf(NearbySort.Closest)
    var locationMessage by mutableStateOf<String?>(null)
    var placeMessage by mutableStateOf<String?>(null)
        private set

    /** What a long-press on the map sets. */
    var mapTarget by mutableStateOf(MapTarget.Start)
    var selectedStationId by mutableStateOf<String?>(null)
        private set
    /** Bumped whenever a new result arrives, so the map re-fits to it once. */
    var fitCount by mutableStateOf(0)
        private set

    val result: JourneyResult? get() = (search as? SearchState.Done)?.outcome?.result

    fun setFromMap(point: LatLon) {
        val lat = "%.5f".format(java.util.Locale.UK, point.lat)
        val lon = "%.5f".format(java.util.Locale.UK, point.lon)
        if (mapTarget == MapTarget.Start || mode != Mode.AlongJourney) { originLat = lat; originLon = lon } else { destLat = lat; destLon = lon }
    }

    fun selectStation(id: String) { selectedStationId = id }

    init {
        viewModelScope.launch {
            saved = prefs.load()
            loaded = true
        }
    }

    fun update(change: (Saved) -> Saved) {
        saved = change(saved)
        viewModelScope.launch { prefs.save(saved) }
    }

    fun useExample() {
        originLat = "51.4543"; originLon = "-0.9781"
        destLat = "51.7520"; destLon = "-1.2577"
    }

    fun useLocation(point: LatLon) {
        originLat = "%.5f".format(java.util.Locale.UK, point.lat)
        originLon = "%.5f".format(java.util.Locale.UK, point.lon)
        locationMessage = null
    }

    fun savePlace(name: String, forDestination: Boolean) {
        val point = if (forDestination) Validation.point(destLat, destLon) else Validation.point(originLat, originLon)
        if (point == null) { placeMessage = "Set the ${if (forDestination) "destination" else "start"} first."; return }
        val updated = Places.add(saved.places, name, point)
        if (updated == null) {
            placeMessage = if (name.isBlank()) "Give the place a name." else "You can keep up to ${Places.MAX} places. Remove one first."
            return
        }
        placeMessage = null
        update { it.copy(places = updated) }
    }

    fun removePlace(place: SavedPlace) { update { it.copy(places = it.places - place) } }

    fun usePlace(place: SavedPlace, asDestination: Boolean) {
        val lat = "%.5f".format(java.util.Locale.UK, place.point.lat)
        val lon = "%.5f".format(java.util.Locale.UK, place.point.lon)
        if (asDestination) { destLat = lat; destLon = lon } else { originLat = lat; originLon = lon }
    }

    fun sortedNearby(result: NearbyResult) = when (nearbySort) {
        NearbySort.Closest -> result.stations
        NearbySort.Cheapest -> result.stations.sortedWith(compareBy({ it.fill == null }, { it.fill?.fillCostPence ?: Long.MAX_VALUE }, { it.distanceMetres }))
    }

    private fun searchNearby() {
        val origin = Validation.point(originLat, originLon) ?: return fail("Enter the start as latitude and longitude inside the UK, or use your location.")
        val litres = Validation.litres(saved.litres) ?: return fail("Set how many litres to buy (1 to 500) on the Car tab.")
        if (saved.appKey.isBlank()) return fail("Add the app key in Settings.")
        job?.cancel()
        nearby = NearbyState.Loading
        job = viewModelScope.launch {
            nearby = when (val outcome = OptimiseClient(saved.baseUrl, saved.appKey).nearby(origin, saved.fuel, litres)) {
                is ApiOutcome.NearbyOk -> NearbyState.Done(outcome.result, java.time.Instant.now())
                is ApiOutcome.Failure -> NearbyState.Failed(outcome)
                is ApiOutcome.Ok -> NearbyState.Idle
            }
        }
    }

    fun submit() {
        formError = null
        if (mode == Mode.Nearby) return searchNearby()
        val origin = Validation.point(originLat, originLon)
            ?: return fail("Enter the start as latitude and longitude inside the UK.")
        val destination = if (mode == Mode.AlongJourney) {
            Validation.point(destLat, destLon) ?: return fail("Enter the destination as latitude and longitude inside the UK.")
        } else null
        val mpg = Validation.mpg(saved.mpg) ?: return fail("Set your car's Imperial MPG (5 to 200) on the Car tab.")
        val litres = Validation.litres(saved.litres) ?: return fail("Set how many litres to buy (1 to 500) on the Car tab.")
        if (saved.appKey.isBlank()) return fail("Add the app key in Settings.")
        val request = JourneyRequest(mode, origin, destination, saved.fuel, mpg, litres)
        job?.cancel()
        search = SearchState.Loading
        job = viewModelScope.launch {
            search = when (val outcome = OptimiseClient(saved.baseUrl, saved.appKey).optimise(request)) {
                is ApiOutcome.Ok -> {
                    selectedStationId = outcome.result.candidates.firstOrNull()?.stationId
                    fitCount++
                    SearchState.Done(outcome, java.time.Instant.now())
                }
                is ApiOutcome.Failure -> SearchState.Failed(outcome)
                is ApiOutcome.NearbyOk -> SearchState.Idle
            }
        }
    }

    fun cancel() {
        job?.cancel()
        nearby = NearbyState.Idle
        search = SearchState.Idle
    }

    private fun fail(message: String) {
        formError = message
    }

    fun fuelOptions() = FuelType.entries
}
