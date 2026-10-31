package uk.tankbear.app.ui

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*

/** Scoped overrides of OpenFreeMap Dark. Preserve widths, filters, zoom rules and overlays. */
internal object MapContrast {
    fun apply(style: Style) {
        (style.getLayer("background") as? BackgroundLayer)?.setProperties(backgroundColor("#242B33"))
        mapOf("water" to "#183747", "landuse_residential" to "#2B333D",
            "landcover_wood" to "#293C36", "landuse_park" to "#30433B").forEach { (id, color) ->
            (style.getLayer(id) as? FillLayer)?.setProperties(fillColor(color))
        }
        mapOf("highway_path" to "#82909D", "highway_minor" to "#96A3AF",
            "highway_major_inner" to "#C1CBD5", "highway_motorway_inner" to "#D3C39C",
            "highway_major_casing" to "#394450", "highway_motorway_casing" to "#394450",
            "highway_major_subtle" to "#96A3AF", "highway_motorway_subtle" to "#B0A585",
            "road_pier" to "#96A3AF").forEach { (id, color) ->
            (style.getLayer(id) as? LineLayer)?.setProperties(lineColor(color))
        }
        style.layers.filterIsInstance<SymbolLayer>().filter {
            it.id.startsWith("highway_name_") || it.id.startsWith("place_")
        }.forEach {
            it.setProperties(textColor("#F2F5F7"), textHaloColor("#242B33"), textHaloWidth(1.5f))
        }
    }
}
