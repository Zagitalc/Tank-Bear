package uk.tankbear.app.data

import org.json.JSONObject

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
            )
        }
        return JourneyResult(
            ranked = o.getString("status") == "ranked",
            scope = o.getString("scope"),
            stationsInSearchArea = coverage.getInt("stationsInSearchArea"),
            stationsRouted = coverage.getInt("stationsRouted"),
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

    fun errorCode(json: String): String? = try {
        JSONObject(json).getJSONObject("error").getString("code")
    } catch (_: Exception) {
        null
    }
}
