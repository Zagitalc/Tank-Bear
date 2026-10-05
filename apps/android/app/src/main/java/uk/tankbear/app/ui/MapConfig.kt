package uk.tankbear.app.ui

/** Where the map comes from and where it first looks. Nothing else in the app knows the style address. */
object MapConfig {
    // OpenFreeMap: no key, commercial use allowed, attribution required (MapLibre draws it), no uptime
    // guarantee (terms read 5 October 2026). Replace this one address with a supported or self-hosted
    // style before a public release. Check https://openfreemap.org/quick_start/ if the map comes up blank.
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    const val INITIAL_LAT = 54.0
    const val INITIAL_LON = -2.5
    const val INITIAL_ZOOM = 5.0
}
