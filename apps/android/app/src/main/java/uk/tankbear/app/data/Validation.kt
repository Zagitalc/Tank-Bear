package uk.tankbear.app.data

object Validation {
    /** Loose UK bounds, matching the backend. Returns null when the text is not a usable coordinate. */
    fun point(latText: String, lonText: String): LatLon? {
        val lat = latText.trim().toDoubleOrNull() ?: return null
        val lon = lonText.trim().toDoubleOrNull() ?: return null
        if (!lat.isFinite() || !lon.isFinite()) return null
        return if (lat in 49.0..61.0 && lon in -9.0..2.5) LatLon(lat, lon) else null
    }

    private val decimal = Regex("^\\d{1,4}(\\.\\d{1,3})?$")

    fun mpg(text: String): String? = text.trim().takeIf { decimal.matches(it) && it.toDouble() in 5.0..200.0 }
    fun litres(text: String): String? = text.trim().takeIf { decimal.matches(it) && it.toDouble() in 1.0..500.0 }
}
