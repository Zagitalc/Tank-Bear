package uk.tankbear.app.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
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
                if (appKey.isNotBlank()) setRequestProperty("X-Tank-Bear-Key", appKey)
            }
            try {
                connection.outputStream.use { it.write(body(request).toByteArray()) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status == 200) {
                    ApiOutcome.Ok(ResultParser.parse(text))
                } else {
                    responseFailure(status, text, connection.getHeaderField("Retry-After")?.toIntOrNull(), request.fuelType, request.mode)
                }
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ApiOutcome.Failure(FailureKind.Network, "Couldn't reach Tank Bear. Check your internet connection and try again.")
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
                if (appKey.isNotBlank()) setRequestProperty("X-Tank-Bear-Key", appKey)
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status == 200) ApiOutcome.NearbyOk(ResultParser.parseNearby(text))
                else responseFailure(status, text, connection.getHeaderField("Retry-After")?.toIntOrNull(), fuel)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ApiOutcome.Failure(FailureKind.Network, "Couldn't reach Tank Bear. Check your internet connection and try again.")
        }
    }

    /** Recorded price history for one station and grade over `days`. */
    suspend fun history(stationId: String, fuel: FuelType, days: Int = 7): ApiOutcome = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(baseUrl.trimEnd('/') + "/v1/stations/$stationId/history?fuelType=${fuel.api}&days=$days").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                if (appKey.isNotBlank()) setRequestProperty("X-Tank-Bear-Key", appKey)
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status == 200) ApiOutcome.HistoryOk(ResultParser.parseHistory(text))
                else responseFailure(status, text, connection.getHeaderField("Retry-After")?.toIntOrNull(), fuel)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ApiOutcome.Failure(FailureKind.Network, "Couldn't reach Tank Bear. Check your internet connection and try again.")
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

        /** Recognise only known legacy validation messages, never display arbitrary server text. */
        fun responseFailure(status: Int, text: String, retryAfter: Int?, fuel: FuelType, mode: Mode? = null): ApiOutcome.Failure {
            val error = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
            if (status == 400 && error?.optString("code") == "INVALID_REQUEST" && mode == Mode.FuelTripOneWay &&
                error.optString("message") == "mode must be \"along_journey\" or \"fuel_trip\".") {
                return ApiOutcome.Failure(FailureKind.Server,
                    "One-way fuel trips need a Tank Bear server update. A return trip will not be substituted.")
            }
            val legacyGradeMessages = setOf(
                "fuelType must be \"E10\" or \"B7\" (standard diesel).",
                "fuelType must be E10 or B7.",
                "fuelType (E10 or B7) and litres (1 to 500) go together.",
            )
            if (status == 400 && error?.optString("code") == "INVALID_REQUEST" &&
                fuel in setOf(FuelType.E5, FuelType.B7_PREMIUM) && error.optString("message") in legacyGradeMessages) {
                return ApiOutcome.Failure(FailureKind.Server,
                    "${fuel.label} isn't supported by this server version yet. Tank Bear needs a server update. Your location and MPG aren't the cause.")
            }
            return failure(status, ResultParser.errorCode(text), retryAfter)
        }

        fun failure(status: Int, code: String?, retryAfter: Int?): ApiOutcome.Failure = when {
            status == 401 || code == "UNAUTHORISED" ->
                ApiOutcome.Failure(FailureKind.Unauthorised, "Tank Bear access is not enabled for this installation yet. Please contact the person who shared the app.")
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
            status == 400 -> ApiOutcome.Failure(FailureKind.Invalid, "The server rejected this search. Try again; if it keeps happening, report it as an app/server compatibility problem.")
            else -> ApiOutcome.Failure(FailureKind.Server, "Tank Bear's server couldn't answer. Try again later.")
        }
    }
}
