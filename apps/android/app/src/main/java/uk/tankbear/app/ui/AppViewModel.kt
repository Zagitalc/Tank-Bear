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
import uk.tankbear.app.data.Mode
import uk.tankbear.app.data.OptimiseClient
import uk.tankbear.app.data.Prefs
import uk.tankbear.app.data.Saved
import uk.tankbear.app.data.Validation

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Done(val outcome: ApiOutcome.Ok, val at: java.time.Instant) : SearchState
    data class Failed(val failure: ApiOutcome.Failure) : SearchState
}

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

    fun submit() {
        formError = null
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
                is ApiOutcome.Ok -> SearchState.Done(outcome, java.time.Instant.now())
                is ApiOutcome.Failure -> SearchState.Failed(outcome)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        search = SearchState.Idle
    }

    private fun fail(message: String) {
        formError = message
    }

    fun fuelOptions() = FuelType.entries
}
