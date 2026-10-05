package uk.tankbear.app.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One POST to the Tank Bear backend. The key and coordinates are never logged. */
class OptimiseClient(private val baseUrl: String, private val appKey: String) {
    suspend fun optimise(request: JourneyRequest): ApiOutcome = withContext(Dispatchers.IO) {
        try {
            val url = URL(baseUrl.trimEnd('/') + "/v1/journeys/optimise")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Tank-Bear-Key", appKey)
            }
            try {
                connection.outputStream.use { it.write(body(request).toByteArray()) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status == 200) {
                    ApiOutcome.Ok(ResultParser.parse(text))
                } else {
                    failure(status, ResultParser.errorCode(text), connection.getHeaderField("Retry-After")?.toIntOrNull())
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            ApiOutcome.Failure(FailureKind.Network, "Couldn't reach Tank Bear. Check your connection and the server address in Settings.")
        }
    }

    /** Closest stations to a point with fill cost for one grade. Same key and error handling as optimise. */
    suspend fun nearby(point: LatLon, fuel: FuelType, litres: String, radiusMetres: Int = 5000, limit: Int = 20): ApiOutcome = withContext(Dispatchers.IO) {
        try {
            val query = "lat=%.5f&lon=%.5f&radiusMetres=%d&limit=%d&fuelType=%s&litres=%s"
                .format(java.util.Locale.UK, point.lat, point.lon, radiusMetres, limit, fuel.api, litres)
            val connection = (URL(baseUrl.trimEnd('/') + "/v1/stations/nearby?" + query).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Tank-Bear-Key", appKey)
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status == 200) ApiOutcome.NearbyOk(ResultParser.parseNearby(text))
                else failure(status, ResultParser.errorCode(text), connection.getHeaderField("Retry-After")?.toIntOrNull())
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            ApiOutcome.Failure(FailureKind.Network, "Couldn't reach Tank Bear. Check your connection and the server address in Settings.")
        }
    }

    /** Recorded price history for one station and grade over `days`. */
    suspend fun history(stationId: String, fuel: FuelType, days: Int = 7): ApiOutcome = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(baseUrl.trimEnd('/') + "/v1/stations/$stationId/history?fuelType=${fuel.api}&days=$days").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Tank-Bear-Key", appKey)
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status == 200) ApiOutcome.HistoryOk(ResultParser.parseHistory(text))
                else failure(status, ResultParser.errorCode(text), connection.getHeaderField("Retry-After")?.toIntOrNull())
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            ApiOutcome.Failure(FailureKind.Network, "Couldn't reach Tank Bear. Check your connection and the server address in Settings.")
        }
    }

    companion object {
        fun body(r: JourneyRequest): String = JSONObject().apply {
            put("mode", r.mode.api)
            put("origin", point(r.origin))
            r.destination?.let { put("destination", point(it)) }
            put("fuelType", r.fuelType.api)
            put("vehicle", JSONObject().put("mpgImperial", r.mpgImperial))
            put("litresToBuy", r.litresToBuy)
        }.toString()

        private fun point(p: LatLon) = JSONObject().put("lat", p.lat).put("lon", p.lon)

        fun failure(status: Int, code: String?, retryAfter: Int?): ApiOutcome.Failure = when {
            status == 401 || code == "UNAUTHORISED" ->
                ApiOutcome.Failure(FailureKind.Unauthorised, "The app key was not accepted. Check it in Settings.")
            status == 429 ->
                ApiOutcome.Failure(FailureKind.RateLimited, "Too many requests. Try again shortly.", retryAfter)
            code == "NO_ROUTE" ->
                ApiOutcome.Failure(FailureKind.NoRoute, "No driving route could be found between those points.")
            code == "JOURNEY_TOO_LONG" ->
                ApiOutcome.Failure(FailureKind.Invalid, "Journeys over 400 km are not supported yet.")
            code == "ROUTE_NEEDS_REVIEW" ->
                ApiOutcome.Failure(FailureKind.Invalid, "This route uses a ferry or toll, so costs can't be compared reliably.")
            code == "NO_FUEL_DATA" || code == "FUEL_DATA_STALE" ->
                ApiOutcome.Failure(FailureKind.NoData, "Fuel prices aren't up to date right now, so Tank Bear won't recommend a stop.")
            status == 400 -> ApiOutcome.Failure(FailureKind.Invalid, "Those details weren't accepted. Check the coordinates and car details.")
            else -> ApiOutcome.Failure(FailureKind.Server, "Tank Bear's server couldn't answer. Try again later.")
        }
    }
}
