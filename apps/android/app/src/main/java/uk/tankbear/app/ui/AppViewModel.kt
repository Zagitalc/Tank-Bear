package uk.tankbear.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import uk.tankbear.app.data.PriceHistory
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
    data class Done(val outcome: ApiOutcome.Ok, val at: java.time.Instant, val inputs: SearchInputs) : SearchState
    data class Failed(val failure: ApiOutcome.Failure) : SearchState
}

sealed interface NearbyState {
    data object Idle : NearbyState
    data object Loading : NearbyState
    data class Done(val result: NearbyResult, val at: java.time.Instant, val inputs: SearchInputs) : NearbyState
    data class Failed(val failure: ApiOutcome.Failure) : NearbyState
}

enum class NearbySort { Closest, Cheapest }

sealed interface HistoryState {
    data object Loading : HistoryState
    data class Done(val history: PriceHistory) : HistoryState
    data class Failed(val message: String) : HistoryState
}

/** In-memory search identity; never persisted or logged. */
data class SearchInputs(val mode: Mode, val origin: LatLon?, val destination: LatLon?,
    val fuel: FuelType, val mpg: String?, val litres: String)

internal fun searchInputs(mode: Mode, origin: LatLon?, destination: LatLon?, saved: Saved) =
    SearchInputs(mode, origin, if (mode == Mode.AlongJourney) destination else null,
        saved.fuel, if (mode == Mode.Nearby) null else saved.mpg.trim(), saved.litres.trim())

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var saved by mutableStateOf(Saved())
        private set
    var loaded by mutableStateOf(false)
        private set

    private var currentMode by mutableStateOf(Mode.AlongJourney)
    var mode: Mode
        get() = currentMode
        set(value) {
            if (currentMode == value) return
            currentMode = value
            cancel()
            formError = null
            choosingMapLocation = false
            mapTarget = MapTarget.Start
            selectedStationId = null
        }
    var originLat by mutableStateOf("")
    var originLon by mutableStateOf("")
    var destLat by mutableStateOf("")
    var destLon by mutableStateOf("")
    private var startDescription by mutableStateOf<Pair<LatLon, String>?>(null)
    private var destinationDescription by mutableStateOf<Pair<LatLon, String>?>(null)
    fun locationDescription(target: MapTarget): String {
        val point = if (target == MapTarget.Start) Validation.point(originLat, originLon) else Validation.point(destLat, destLon)
        if (point == null) return "Not set"
        val description = if (target == MapTarget.Start) startDescription else destinationDescription
        return description?.takeIf { it.first == point }?.second ?: "Location selected"
    }
    private fun describeLocation(target: MapTarget, label: String) {
        val point = if (target == MapTarget.Start) Validation.point(originLat, originLon) else Validation.point(destLat, destLon)
        if (point != null) {
            if (target == MapTarget.Start) startDescription = point to label else destinationDescription = point to label
        }
    }
    private var errorMessage by mutableStateOf<String?>(null)
    private var errorInputs by mutableStateOf<SearchInputs?>(null)
    var formError: String?
        get() = errorMessage.takeIf { errorInputs == inputs }
        private set(value) { errorMessage = value; errorInputs = inputs }
    var findResultsVersion by mutableStateOf(0)
        private set
    var search by mutableStateOf<SearchState>(SearchState.Idle)
        private set
    private var job: Job? = null
    internal var journeyLookup: suspend (String, String, JourneyRequest) -> ApiOutcome = { url, appKey, request ->
        OptimiseClient(url, appKey).optimise(request)
    }
    internal var nearbyLookup: suspend (String, String, LatLon, FuelType, String) -> ApiOutcome = { url, appKey, point, fuel, litres ->
        OptimiseClient(url, appKey).nearby(point, fuel, litres)
    }
    internal var placeLookup: suspend (String, String, String) -> uk.tankbear.app.data.PlaceSearchResult = { url, appKey, query ->
        uk.tankbear.app.data.GeocodingClient(url, appKey).search(query)
    }
    val findResultCount: Int? get() = if (resultsOutdated) null else if (mode == Mode.Nearby)
        (nearby as? NearbyState.Done)?.result?.stations?.size
        else (search as? SearchState.Done)?.outcome?.result?.candidates?.size
    fun revealFindResults() { if (findResultCount != null) findResultsVersion++ }

    /** History panels the person has opened, by station id. Held in memory only. */
    val histories = mutableStateMapOf<String, HistoryState>()

    fun toggleHistory(stationId: String) {
        if (histories.containsKey(stationId)) { histories.remove(stationId); return }
        histories[stationId] = HistoryState.Loading
        viewModelScope.launch {
            histories[stationId] = when (val o = OptimiseClient(saved.baseUrl, saved.appKey).history(stationId, saved.fuel)) {
                is ApiOutcome.HistoryOk -> HistoryState.Done(o.history)
                is ApiOutcome.Failure -> HistoryState.Failed(o.message)
                else -> HistoryState.Failed("Couldn't load history.")
            }
        }
    }

    // The map's nearby lookup is independent of Find's selected mode and jobs.
    var mapJourney by mutableStateOf(false)
    var mapNearbyOpenOnly by mutableStateOf(false)
    var mapNearbySort by mutableStateOf(NearbySort.Closest)
    var mapNearby by mutableStateOf<NearbyState>(NearbyState.Idle)
        private set
    var mapSelectedStationId by mutableStateOf<String?>(null)
        private set
    private var mapJob: Job? = null
    private var mapGeneration = 0
    private var mapRequestKey: SearchInputs? = null
    private var mapConnection: Pair<String, String>? = null
    internal var retryUntil by mutableStateOf(java.time.Instant.EPOCH)
        private set
    private var retryConnection by mutableStateOf<Pair<String, String>?>(null)
    var mapNearbyCancelled by mutableStateOf(false)
        private set
    internal var mapNow: () -> java.time.Instant = { java.time.Instant.now() }
    internal var mapNearbyLookup: suspend (String, String, LatLon, FuelType, String) -> ApiOutcome = { url, appKey, origin, fuel, litres ->
        OptimiseClient(url, appKey).nearby(origin, fuel, litres)
    }
    val retrySecondsRemaining: Long get() {
        if (retryConnection != (saved.baseUrl to saved.appKey)) return 0
        val remaining = java.time.Duration.between(mapNow(), retryUntil)
        return if (remaining.isNegative || remaining.isZero) 0 else remaining.seconds + if (remaining.nano > 0) 1 else 0
    }
    val mapRetryAvailable: Boolean get() = retrySecondsRemaining == 0L
    private fun respectRetryAfter(failure: ApiOutcome.Failure, connection: Pair<String, String>) {
        if (connection != (saved.baseUrl to saved.appKey)) return
        val seconds = failure.retryAfterSeconds?.takeIf { it > 0 } ?: return
        val until = mapNow().plusSeconds(seconds.toLong())
        if (retryConnection != connection || until > retryUntil) retryUntil = until
        retryConnection = connection
    }
    fun cancelMapNearby() {
        mapGeneration++
        mapJob?.cancel()
        mapNearby = NearbyState.Idle
        mapNearbyCancelled = true
    }
    val mapInputs: SearchInputs get() = searchInputs(Mode.Nearby, Validation.point(originLat, originLon), null, saved)
    val currentMapNearby: NearbyState get() {
        val done = mapNearby as? NearbyState.Done
        return if (done != null && (done.inputs != mapInputs || mapConnection != (saved.baseUrl to saved.appKey))) NearbyState.Idle else mapNearby
    }
    fun selectMapStation(id: String) { if (mapJourney) selectStation(id) else mapSelectedStationId = id }
    fun loadMapNearby(force: Boolean = false) {
        if (!loaded || mapJourney || choosingMapLocation) return
        val key = mapInputs
        val origin = key.origin ?: run { mapGeneration++; mapJob?.cancel(); mapNearby = NearbyState.Idle; return }
        val litres = Validation.litres(saved.litres) ?: run { mapGeneration++; mapJob?.cancel(); mapNearby = NearbyState.Idle; return }
        val connection = saved.baseUrl to saved.appKey
        val existing = mapNearby as? NearbyState.Done
        if (!force && existing != null && existing.inputs == key && mapConnection == connection &&
            java.time.Duration.between(existing.at, mapNow()).seconds in 0 until 300) return
        if (mapNearby is NearbyState.Loading && mapRequestKey == key && mapConnection == connection) return
        if (!mapRetryAvailable) return
        mapJob?.cancel()
        val generation = ++mapGeneration
        mapRequestKey = key
        mapConnection = connection
        mapNearbyCancelled = false
        mapNearby = NearbyState.Loading
        mapJob = viewModelScope.launch {
            val outcome = mapNearbyLookup(connection.first, connection.second, origin, key.fuel, litres)
            if (generation != mapGeneration || key != mapInputs || connection != (saved.baseUrl to saved.appKey)) return@launch
            mapNearby = when (outcome) {
                is ApiOutcome.NearbyOk -> {
                    mapSelectedStationId = (outcome.result.stations.firstOrNull { !it.temporaryClosure && it.opening.state != "closed" && it.fill != null }
                        ?: outcome.result.stations.firstOrNull { !it.temporaryClosure && it.fill != null }
                        ?: outcome.result.stations.firstOrNull())?.id
                    fitCount++
                    NearbyState.Done(outcome.result, mapNow(), key)
                }
                is ApiOutcome.Failure -> {
                    respectRetryAfter(outcome, connection)
                    NearbyState.Failed(outcome)
                }
                else -> NearbyState.Idle
            }
        }
    }

    var nearby by mutableStateOf<NearbyState>(NearbyState.Idle)
        private set
    var nearbySort by mutableStateOf(NearbySort.Closest)
    var routeSort by mutableStateOf(RouteSort.Total)
        private set
    var routeSelectionVersion by mutableStateOf(0)
        private set
    fun chooseRouteSort(sort: RouteSort, fromFind: Boolean = false) {
        routeSort = sort
        if (resultsOutdated) return
        val first = orderedCandidates(result?.candidates.orEmpty(), sort)
            .firstOrNull { !mapOpenOnly || it.opening.state == "open" }
        selectedStationId = first?.stationId
        routeSelectionVersion++ // Re-tapping an active sort must reveal its first result too.
        if (first != null) fitCount++
        if (fromFind) revealFindResults()
    }
    var mapOpenOnly by mutableStateOf(false)
    var locationMessage by mutableStateOf<String?>(null)
    var placeMessage by mutableStateOf<String?>(null)
        private set
    var placeMessageIsError by mutableStateOf(false)
        private set
    private var removedPlace by mutableStateOf<Pair<Int, SavedPlace>?>(null)
    val removedPlaceName: String? get() = removedPlace?.second?.name

    /** Which endpoint the explicit map picker is choosing. */
    var mapTarget by mutableStateOf(MapTarget.Start)
    var selectedStationId by mutableStateOf<String?>(null)
        private set
    /** Bumped whenever a new result arrives, so the map re-fits to it once. */
    var fitCount by mutableStateOf(0)
        private set

    var mapExpanded by mutableStateOf(false)
    var mapChoiceReturnsToFind by mutableStateOf(false)
    var choosingMapLocation by mutableStateOf(false)
    var placePreview by mutableStateOf<LatLon?>(null)
        private set
    private var placePreviewLabel: String? = null
    fun beginMapChoice(target: MapTarget, preview: LatLon? = null, label: String? = null) {
        placePreviewLabel = label
        placePreview = preview
        mapTarget = target
        choosingMapLocation = true
        mapExpanded = true
    }

    val inputs: SearchInputs get() = searchInputs(mode, Validation.point(originLat, originLon),
        Validation.point(destLat, destLon), saved)
    val resultsOutdated: Boolean get() {
        val previous = if (mode == Mode.Nearby) (nearby as? NearbyState.Done)?.inputs else (search as? SearchState.Done)?.inputs
        return previous != null && previous != inputs
    }
    val loading: Boolean get() = if (mode == Mode.Nearby) nearby is NearbyState.Loading else search is SearchState.Loading

    val result: JourneyResult? get() = (search as? SearchState.Done)?.outcome?.result

    fun setFromMap(point: LatLon) {
        if (Validation.point(point.lat.toString(), point.lon.toString()) == null) {
            formError = "Choose a location inside the UK."; return
        }
        formError = null
        choosingMapLocation = false
        val lat = "%.5f".format(java.util.Locale.UK, point.lat)
        val lon = "%.5f".format(java.util.Locale.UK, point.lon)
        if (mapTarget == MapTarget.Start || mode != Mode.AlongJourney) { originLat = lat; originLon = lon } else { destLat = lat; destLon = lon }
        val actualTarget = if (mapTarget == MapTarget.Start || mode != Mode.AlongJourney) MapTarget.Start else MapTarget.Destination
        val preview = placePreview
        val unchangedPreview = preview != null &&
            "%.5f".format(java.util.Locale.UK, preview.lat) == lat && "%.5f".format(java.util.Locale.UK, preview.lon) == lon
        describeLocation(actualTarget, placePreviewLabel?.takeIf { unchangedPreview } ?: "Selected on map")
    }

    fun selectStation(id: String) { selectedStationId = id }

    init {
        viewModelScope.launch {
            saved = prefs.load()
            loaded = true
        }
    }

    fun update(change: (Saved) -> Saved) {
        val previousFuel = saved.fuel
        saved = change(saved)
        if (saved.fuel != previousFuel) histories.clear()
        viewModelScope.launch { prefs.save(saved) }
    }

    fun useExample() {
        originLat = "51.4543"; originLon = "-0.9781"
        destLat = "51.7520"; destLon = "-1.2577"
        describeLocation(MapTarget.Start, "Reading (example)")
        describeLocation(MapTarget.Destination, "Oxford (example)")
    }

    fun useLocation(point: LatLon) {
        originLat = "%.5f".format(java.util.Locale.UK, point.lat)
        originLon = "%.5f".format(java.util.Locale.UK, point.lon)
        locationMessage = null
        describeLocation(MapTarget.Start, "Current location")
    }

    fun savePlace(name: String, forDestination: Boolean): Boolean {
        placeMessageIsError = true
        val point = if (forDestination) Validation.point(destLat, destLon) else Validation.point(originLat, originLon)
        if (point == null) { placeMessage = "Set the ${if (forDestination) "destination" else "start"} first."; return false }
        val updated = Places.add(saved.places, name, point)
        if (updated == null) {
            placeMessage = if (name.isBlank()) "Give the place a name." else "You can keep up to ${Places.MAX} places. Remove one first."
            return false
        }
        val replaced = saved.places.any { it.name.equals(name.trim().take(40), ignoreCase = true) }
        update { it.copy(places = updated) }
        removedPlace = null
        placeMessageIsError = false
        placeMessage = "${if (replaced) "Updated" else "Saved"} ${updated.last().name} on this phone."
        return true
    }

    fun removePlace(place: SavedPlace) {
        val index = saved.places.indexOf(place)
        if (index < 0) return
        removedPlace = index to place
        placeMessage = null
        update { it.copy(places = it.places - place) }
    }

    fun undoRemovePlace() {
        val (index, place) = removedPlace ?: return
        if (saved.places.size >= Places.MAX || saved.places.any { it.name.equals(place.name, ignoreCase = true) }) return
        update { current -> current.copy(places = current.places.toMutableList().apply { add(index.coerceAtMost(size), place) }) }
        removedPlace = null
        placeMessageIsError = false
        placeMessage = "Restored ${place.name} on this phone."
    }

    fun usePlace(place: SavedPlace, asDestination: Boolean) {
        val lat = "%.5f".format(java.util.Locale.UK, place.point.lat)
        val lon = "%.5f".format(java.util.Locale.UK, place.point.lon)
        if (asDestination) { destLat = lat; destLon = lon } else { originLat = lat; originLon = lon }
        describeLocation(if (asDestination) MapTarget.Destination else MapTarget.Start, place.name)
    }

    fun sortedNearby(result: NearbyResult) = when (nearbySort) {
        NearbySort.Closest -> result.stations
        NearbySort.Cheapest -> result.stations.sortedWith(compareBy({ it.fill == null }, { it.fill?.fillCostPence ?: Long.MAX_VALUE }, { it.distanceMetres }))
    }

    fun sortedMapNearby(result: NearbyResult) = when (mapNearbySort) {
        NearbySort.Closest -> result.stations
        NearbySort.Cheapest -> result.stations.sortedWith(compareBy({ it.fill == null }, { it.fill?.fillCostPence ?: Long.MAX_VALUE }, { it.distanceMetres }))
    }

    private fun searchNearby(fromFind: Boolean) {
        val origin = Validation.point(originLat, originLon) ?: return fail("Choose your start on the map or use your location.")
        val litres = Validation.litres(saved.litres) ?: return fail("Set how many litres to buy (1 to 500) on the Car tab.")
        job?.cancel()
        val submittedInputs = inputs
        val connection = saved
        histories.clear()
        nearby = NearbyState.Loading
        job = viewModelScope.launch {
            nearby = when (val outcome = nearbyLookup(connection.baseUrl, connection.appKey, origin, submittedInputs.fuel, litres)) {
                is ApiOutcome.NearbyOk -> {
                    selectedStationId = outcome.result.stations.firstOrNull()?.id
                    fitCount++
                    if (fromFind && submittedInputs == inputs) findResultsVersion++
                    NearbyState.Done(outcome.result, java.time.Instant.now(), submittedInputs)
                }
                is ApiOutcome.Failure -> { respectRetryAfter(outcome, connection.baseUrl to connection.appKey); NearbyState.Failed(outcome) }
                is ApiOutcome.Ok, is ApiOutcome.HistoryOk -> NearbyState.Idle
            }
        }
    }

    fun submit(fromFind: Boolean = false) {
        if (retrySecondsRemaining > 0) return
        formError = null
        if (mode == Mode.Nearby) return searchNearby(fromFind)
        val origin = Validation.point(originLat, originLon)
            ?: return fail("Choose your start on the map or use your location.")
        val destination = if (mode == Mode.AlongJourney) {
            Validation.point(destLat, destLon) ?: return fail("Choose your destination on the map.")
        } else null
        val mpg = Validation.mpg(saved.mpg) ?: return fail("Set your car's Imperial MPG (5 to 200) on the Car tab.")
        val litres = Validation.litres(saved.litres) ?: return fail("Set how many litres to buy (1 to 500) on the Car tab.")
        val request = JourneyRequest(mode, origin, destination, saved.fuel, mpg, litres)
        job?.cancel()
        val submittedInputs = inputs
        val connection = saved
        histories.clear()
        search = SearchState.Loading
        job = viewModelScope.launch {
            search = when (val outcome = journeyLookup(connection.baseUrl, connection.appKey, request)) {
                is ApiOutcome.Ok -> {
                    selectedStationId = orderedCandidates(outcome.result.candidates, routeSort)
                        .firstOrNull { !mapOpenOnly || it.opening.state == "open" }?.stationId
                    fitCount++
                    if (fromFind && submittedInputs == inputs) findResultsVersion++
                    SearchState.Done(outcome, java.time.Instant.now(), submittedInputs)
                }
                is ApiOutcome.Failure -> { respectRetryAfter(outcome, connection.baseUrl to connection.appKey); SearchState.Failed(outcome) }
                is ApiOutcome.NearbyOk, is ApiOutcome.HistoryOk -> SearchState.Idle
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
