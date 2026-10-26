package uk.tankbear.app.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class GeocodedPlace(val label: String, val point: LatLon)
data class PlaceSearchResult(val places: List<GeocodedPlace> = emptyList(), val error: String? = null)

/** Queries stay in memory and are sent only when Search is pressed. */
class GeocodingClient(private val baseUrl: String, private val appKey: String) {
    suspend fun search(query: String): PlaceSearchResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(baseUrl.trimEnd('/') + "/v1/places/search").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (appKey.isNotBlank()) setRequestProperty("X-Tank-Bear-Key", appKey)
            }
            try {
                connection.outputStream.use { it.write(JSONObject().put("query", query.trim()).toString().toByteArray()) }
                if (connection.responseCode != 200) return@withContext PlaceSearchResult(error = when (connection.responseCode) {
                    429 -> "Place search limit reached. Try later or choose on the map."
                    400 -> "Enter a place name or postcode (3–200 characters)."
                    else -> "Place search is unavailable. You can still choose on the map."
                })
                val rows = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("places")
                PlaceSearchResult(places = (0 until rows.length()).mapNotNull { i ->
                    val row = rows.getJSONObject(i)
                    val point = Validation.point(row.getDouble("lat").toString(), row.getDouble("lon").toString())
                    point?.let { GeocodedPlace(row.getString("label"), it) }
                })
            } finally { connection.disconnect() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { PlaceSearchResult(error = "Couldn't search places. Check your connection or choose on the map.") }
    }
}
