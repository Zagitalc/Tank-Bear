package uk.tankbear.app.data

enum class FuelType(val api: String, val label: String) {
    E10("E10", "Petrol (E10)"),
    B7("B7", "Diesel (standard)"),
}

enum class Mode(val api: String) { AlongJourney("along_journey"), FuelTrip("fuel_trip") }

data class LatLon(val lat: Double, val lon: Double)

data class JourneyRequest(
    val mode: Mode,
    val origin: LatLon,
    val destination: LatLon?,
    val fuelType: FuelType,
    val mpgImperial: String,
    val litresToBuy: String,
)

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
)

data class JourneyResult(
    val ranked: Boolean,
    val scope: String,
    val stationsInSearchArea: Int,
    val stationsRouted: Int,
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

sealed interface ApiOutcome {
    data class Ok(val result: JourneyResult) : ApiOutcome
    data class Failure(val kind: FailureKind, val message: String, val retryAfterSeconds: Int? = null) : ApiOutcome
}
