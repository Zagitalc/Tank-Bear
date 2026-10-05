package uk.tankbear.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Saved places live on the phone only. Coordinates are stored as typed, never sent anywhere except in a search. */
object Places {
    const val MAX = 8

    fun encode(places: List<SavedPlace>): String =
        JSONArray(places.map { JSONObject().put("n", it.name).put("lat", it.point.lat).put("lon", it.point.lon) }).toString()

    fun decode(text: String): List<SavedPlace> = try {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val point = Validation.point(o.getDouble("lat").toString(), o.getDouble("lon").toString())
            val name = o.getString("n").trim()
            if (point != null && name.isNotEmpty()) SavedPlace(name.take(40), point) else null
        }.take(MAX)
    } catch (_: Exception) {
        emptyList()
    }

    /** Adds or replaces by name (ignoring case). Returns null when the name is empty or the list is full. */
    fun add(places: List<SavedPlace>, name: String, point: LatLon): List<SavedPlace>? {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return null
        val others = places.filterNot { it.name.equals(clean, ignoreCase = true) }
        if (others.size >= MAX) return null
        return others + SavedPlace(clean, point)
    }
}
