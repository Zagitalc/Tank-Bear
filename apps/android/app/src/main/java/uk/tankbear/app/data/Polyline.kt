package uk.tankbear.app.data

/** Decodes the backend's encoded polylines (6-digit precision) into points. Bad input yields an empty list. */
object Polyline {
    fun decode(encoded: String, precision: Int = 6): List<LatLon> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLon>()
        var index = 0
        var lat = 0
        var lon = 0
        fun next(): Int? {
            var result = 0
            var shift = 0
            var byte: Int
            do {
                if (index >= encoded.length) return null
                byte = encoded[index++].code - 63
                if (byte < 0 || byte > 63) return null
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            return if (result and 1 != 0) (result shr 1).inv() else result shr 1
        }
        while (index < encoded.length) {
            val dLat = next() ?: return emptyList()
            val dLon = next() ?: return emptyList()
            lat += dLat
            lon += dLon
            out.add(LatLon(lat / factor, lon / factor))
        }
        return out
    }
}
