package uk.tankbear.app.data

enum class FuelType(val api: String, val label: String) {
    E10("E10", "Petrol (E10)"),
    B7("B7", "Diesel (standard)"),
}

enum class Mode(val api: String) { AlongJourney("along_journey"), FuelTrip("fuel_trip"), Nearby("nearby") }

data class LatLon(val lat: Double, val lon: Double)

data class JourneyRequest(
    val mode: Mode,
    val origin: LatLon,
    val destination: LatLon?,
    val fuelType: FuelType,
    val mpgImperial: String,
    val litresToBuy: String,
)

/** Usual-hours opening status from the backend. Bank holidays are not applied. */
data class OpeningInfo(
    val state: String,
    val is24Hours: Boolean = false,
    val closesAt: String? = null,
    val opensAt: String? = null,
    val closesInMinutes: Int? = null,
) {
    companion object { val UNKNOWN = OpeningInfo("unknown") }
}

data class NearbyFill(val pencePerLitre: String, val priceLastUpdated: String, val fillCostPence: Long, val vsLocalMedianPencePerLitre: String?)

data class NearbyStation(
    val id: String,
    val name: String,
    val brand: String?,
    val position: LatLon,
    val distanceMetres: Int,
    val temporaryClosure: Boolean,
    val opening: OpeningInfo,
    /** Null when the station has no price for the chosen grade. */
    val fill: NearbyFill?,
)

data class NearbyResult(val feedLastSuccessfulRefresh: String?, val stations: List<NearbyStation>, val localMedianPencePerLitre: String? = null)

data class SavedPlace(val name: String, val point: LatLon)

data class Candidate(
    val stationId: String,
    val name: String,
    val rank: Int,
    val pencePerLitre: String,
    val priceLastUpdated: String,
    /** Decimal strings from the API; metres and seconds. */
    val detourDistanceMetres: String,
    val detourDurationSeconds: String,
    val fillCostPence: Long,
    val detourFuelCostPence: Long,
    val comparisonCostPence: Long,
    val pumpSavingPence: Long,
    val referenceDetourFuelCostPence: Long,
    val trueSavingPence: Long,
    val worseThanBestPence: Long,
    val position: LatLon,
    /** Encoded polyline (6 digits) of the whole origin-station-destination route. */
    val routeGeometry: String,
    val opening: OpeningInfo,
)

data class JourneyResult(
    val ranked: Boolean,
    val scope: String,
    val stationsInSearchArea: Int,
    val stationsRouted: Int,
    val closedNowExcluded: Int,
    val partial: Boolean,
    val feedLastSuccessfulRefresh: String,
    val referenceStationId: String?,
    val bestOverallId: String?,
    val cheapestPumpId: String?,
    val smallestDetourId: String?,
    val candidates: List<Candidate>,
    /** Encoded polyline (6 digits) of the baseline route; null in fuel-trip mode. */
    val baselineGeometry: String?,
)

enum class FailureKind { Network, Unauthorised, RateLimited, NoRoute, NoData, Invalid, Server }

data class HistoryCoverage(
    val windowDays: Int,
    val watchingSince: String?,
    val sufficientForTrend: Boolean,
    val reason: String?,
)

data class PricePoint(val at: String, val pencePerLitre: String)

data class HistoryTrend(val changePencePerLitre: String, val direction: String, val sincePencePerLitre: String)

data class PriceHistory(
    val stationName: String,
    val coverage: HistoryCoverage,
    val points: List<PricePoint>,
    val currentPencePerLitre: String?,
    val trend: HistoryTrend?,
    val windowFrom: String,
    val windowTo: String,
)

sealed interface ApiOutcome {
    data class HistoryOk(val history: PriceHistory) : ApiOutcome
    data class Ok(val result: JourneyResult) : ApiOutcome
    data class NearbyOk(val result: NearbyResult) : ApiOutcome
    data class Failure(val kind: FailureKind, val message: String, val retryAfterSeconds: Int? = null) : ApiOutcome
}
