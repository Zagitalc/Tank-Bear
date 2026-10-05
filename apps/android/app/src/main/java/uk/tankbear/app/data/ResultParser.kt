package uk.tankbear.app.data

import org.json.JSONObject

private fun JSONObject.nullableString(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

/** Maps the backend's optimise response. Money arrives as whole pence, prices as decimal strings. */
object ResultParser {
    fun parse(json: String): JourneyResult {
        val o = JSONObject(json)
        val coverage = o.getJSONObject("coverage")
        val labels = o.optJSONObject("labels")
        val reference = o.optJSONObject("reference")
        val list = o.getJSONArray("candidates")
        val candidates = (0 until list.length()).map { i ->
            val c = list.getJSONObject(i)
            Candidate(
                stationId = c.getString("stationId"),
                name = c.getString("name"),
                rank = c.getInt("rank"),
                pencePerLitre = c.getJSONObject("price").getString("pencePerLitre"),
                priceLastUpdated = c.getJSONObject("priceSource").getString("priceLastUpdated"),
                detourDistanceMetres = c.getString("detourDistanceMetres"),
                detourDurationSeconds = c.getString("detourDurationSeconds"),
                fillCostPence = c.getLong("fillCostPence"),
                detourFuelCostPence = c.getLong("detourFuelCostPence"),
                comparisonCostPence = c.getLong("comparisonCostPence"),
                pumpSavingPence = c.getLong("pumpSavingPence"),
                referenceDetourFuelCostPence = c.getLong("referenceDetourFuelCostPence"),
                trueSavingPence = c.getLong("trueSavingPence"),
                worseThanBestPence = c.getLong("worseThanBestPence"),
                position = c.getJSONObject("station").getJSONObject("position").let { LatLon(it.getDouble("lat"), it.getDouble("lon")) },
                routeGeometry = c.getString("routeGeometry"),
                opening = opening(c.optJSONObject("opening")),
            )
        }
        return JourneyResult(
            ranked = o.getString("status") == "ranked",
            scope = o.getString("scope"),
            stationsInSearchArea = coverage.getInt("stationsInSearchArea"),
            stationsRouted = coverage.getInt("stationsRouted"),
            closedNowExcluded = coverage.optInt("closedNowExcluded", 0),
            partial = coverage.getBoolean("partial"),
            feedLastSuccessfulRefresh = o.getJSONObject("feed").getString("lastSuccessfulRefreshAt"),
            referenceStationId = reference?.optString("stationId"),
            bestOverallId = labels?.optString("bestOverallStationId"),
            cheapestPumpId = labels?.optString("cheapestPumpStationId"),
            smallestDetourId = labels?.optString("smallestDetourStationId"),
            candidates = candidates,
            baselineGeometry = o.optJSONObject("baseline")?.optString("geometry")?.takeIf { it.isNotEmpty() },
        )
    }

    fun opening(o: JSONObject?): OpeningInfo =
        if (o == null) OpeningInfo.UNKNOWN
        else OpeningInfo(
            state = o.optString("state", "unknown"),
            is24Hours = o.optBoolean("is24Hours", false),
            closesAt = o.nullableString("closesAt"),
            opensAt = o.nullableString("opensAt"),
            closesInMinutes = if (o.has("closesInMinutes")) o.getInt("closesInMinutes") else null,
        )

    fun parseNearby(json: String): NearbyResult {
        val o = JSONObject(json)
        val list = o.getJSONArray("stations")
        val stations = (0 until list.length()).map { i ->
            val s = list.getJSONObject(i)
            val pos = s.getJSONObject("position")
            val fill = s.optJSONObject("fill")
            NearbyStation(
                id = s.getString("id"),
                name = s.getString("name"),
                brand = s.nullableString("brand"),
                position = LatLon(pos.getDouble("lat"), pos.getDouble("lon")),
                distanceMetres = s.getInt("distanceMetres"),
                temporaryClosure = s.optBoolean("temporaryClosure", false),
                opening = opening(s.optJSONObject("opening")),
                fill = fill?.let { NearbyFill(it.getString("pencePerLitre"), it.getString("priceLastUpdated"), it.getLong("fillCostPence"), it.nullableString("vsLocalMedianPencePerLitre")) },
            )
        }
        return NearbyResult(o.getJSONObject("feed").nullableString("lastSuccessfulRefreshAt"), stations, o.optJSONObject("localSummary")?.nullableString("medianPencePerLitre"))
    }

    fun parseHistory(json: String): PriceHistory {
        val o = JSONObject(json)
        val c = o.getJSONObject("coverage")
        val pts = o.getJSONArray("points")
        val trend = o.optJSONObject("trend")
        val window = o.getJSONObject("window")
        return PriceHistory(
            stationName = o.getJSONObject("station").getString("name"),
            coverage = HistoryCoverage(c.getInt("windowDays"), c.nullableString("watchingSince"), c.getBoolean("sufficientForTrend"), c.nullableString("reason")),
            points = (0 until pts.length()).map { PricePoint(pts.getJSONObject(it).getString("at"), pts.getJSONObject(it).getString("pencePerLitre")) },
            currentPencePerLitre = o.nullableString("currentPencePerLitre"),
            trend = trend?.let { HistoryTrend(it.getString("changePencePerLitre"), it.getString("direction"), it.getString("sincePencePerLitre")) },
            windowFrom = window.getString("from"),
            windowTo = window.getString("to"),
        )
    }

    fun errorCode(json: String): String? = try {
        JSONObject(json).getJSONObject("error").getString("code")
    } catch (_: Exception) {
        null
    }
}
