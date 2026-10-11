package uk.tankbear.app.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import uk.tankbear.app.R
import uk.tankbear.app.data.LatLon
import uk.tankbear.app.data.Mode
import uk.tankbear.app.data.Polyline
import uk.tankbear.app.data.Validation

private const val BASE_SRC = "baseline-src"
private const val BASE_LINE = "baseline-line"
private const val ROUTE_SRC = "route-src"
private const val ROUTE_CASING = "route-casing"
private const val ROUTE_LINE = "route-line"
private const val STATION_SRC = "station-src"
private const val STATION_LAYER = "station-layer"
private const val SELECTED_LAYER = "station-selected"
private const val PINS_SRC = "pins-src"
private const val PINS_LAYER = "pins-layer"
private const val ID = "id"
private const val SELECTED = "selected"
private const val ROLE = "role"

private val CameraSaver = listSaver<CameraPosition?, Double>(
    save = { c -> if (c == null) emptyList() else listOf(c.target!!.latitude, c.target!!.longitude, c.zoom) },
    restore = { l ->
        if (l.size == 3) CameraPosition.Builder().target(LatLng(l[0], l[1])).zoom(l[2]).build() else null
    },
)

/** A collection holding one line, or nothing when there are fewer than two points. */
private fun lineOf(points: List<LatLon>): FeatureCollection =
    FeatureCollection.fromFeatures(
        if (points.size < 2) emptyList()
        else listOf(Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) }))),
    )

/**
 * The map under the same rules as the list: it explains the chosen route, it never decides it. It sits above
 * the info panel, not behind it, so the attribution MapLibre draws stays visible (the map data licence needs it).
 */
@Composable
fun MapScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    var camera by rememberSaveable(stateSaver = CameraSaver) { mutableStateOf<CameraPosition?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var fitted by rememberSaveable { mutableStateOf(0) }
    var fitRequest by remember { mutableStateOf(0) }
    var fittedRequest by remember { mutableStateOf(0) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // If the activity is going away its own ON_DESTROY has already been delivered.
            if (lifecycle.currentState != Lifecycle.State.DESTROYED) {
                mapView.onPause(); mapView.onStop(); mapView.onDestroy()
            }
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = camera ?: CameraPosition.Builder()
                .target(LatLng(MapConfig.INITIAL_LAT, MapConfig.INITIAL_LON)).zoom(MapConfig.INITIAL_ZOOM).build()
            m.addOnCameraIdleListener { camera = m.cameraPosition }
            m.addOnMapClickListener { p ->
                if (vm.choosingMapLocation) return@addOnMapClickListener false
                val hit = m.queryRenderedFeatures(m.projection.toScreenLocation(p), STATION_LAYER, SELECTED_LAYER).firstOrNull()
                val id = hit?.getStringProperty(ID)
                if (id != null) { vm.selectStation(id); true } else false
            }
            m.setStyle(MapConfig.STYLE_URL) { style ->
                // Bottom to top: baseline, chosen route, stations, start/destination pins.
                style.addSource(GeoJsonSource(BASE_SRC))
                style.addLayer(LineLayer(BASE_LINE, BASE_SRC).withProperties(
                    lineColor("#546E7A"), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                style.addSource(GeoJsonSource(ROUTE_SRC))
                style.addLayer(LineLayer(ROUTE_CASING, ROUTE_SRC).withProperties(
                    lineColor("#ffffff"), lineWidth(9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                style.addLayer(LineLayer(ROUTE_LINE, ROUTE_SRC).withProperties(
                    lineColor("#B26A00"), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                style.addSource(GeoJsonSource(STATION_SRC))
                style.addImage("pump", mapMarker(context, R.drawable.ic_petrol_station, 0xFF665A3A.toInt()))
                style.addImage("pump-selected", mapMarker(context, R.drawable.ic_petrol_station, 0xFFB26A00.toInt(), selected = true))
                style.addImage("car-start", mapMarker(context, R.drawable.ic_car, 0xFF1B5E20.toInt()))
                style.addLayer(SymbolLayer(STATION_LAYER, STATION_SRC).withProperties(
                    iconImage("pump"), iconSize(1.2f), iconAllowOverlap(true), iconIgnorePlacement(true))
                    .also { it.setFilter(Expression.eq(Expression.get(SELECTED), Expression.literal(false))) })
                style.addLayer(SymbolLayer(SELECTED_LAYER, STATION_SRC).withProperties(
                    iconImage("pump-selected"), iconSize(1.2f), iconAllowOverlap(true), iconIgnorePlacement(true))
                    .also { it.setFilter(Expression.eq(Expression.get(SELECTED), Expression.literal(true))) })
                style.addSource(GeoJsonSource(PINS_SRC))
                style.addLayer(CircleLayer(PINS_LAYER, PINS_SRC).withProperties(
                    circleRadius(9f), circleColor("#B71C1C"), circleStrokeColor("#ffffff"), circleStrokeWidth(3f))
                    .also { it.setFilter(Expression.eq(Expression.get(ROLE), Expression.literal("destination"))) })
                style.addLayer(SymbolLayer("start-car", PINS_SRC).withProperties(
                    iconImage("car-start"), iconSize(1.2f), iconAllowOverlap(true), iconIgnorePlacement(true))
                    .also { it.setFilter(Expression.eq(Expression.get(ROLE), Expression.literal("start"))) })
                styleReady = true
            }
            map = m
        }
    }

    val nearby = if (!vm.resultsOutdated && vm.mode == Mode.Nearby) (vm.nearby as? NearbyState.Done)?.result else null
    val result = if (!vm.resultsOutdated && vm.mode != Mode.Nearby) vm.result else null
    val visibleCandidates = result?.candidates.orEmpty().filter { !vm.mapOpenOnly || it.opening.state == "open" }
    val visibleNearby = nearby?.stations.orEmpty().filter { !vm.mapOpenOnly || (!it.temporaryClosure && it.opening.state == "open") }
    val selected = visibleCandidates.firstOrNull { it.stationId == vm.selectedStationId }
    val origin = Validation.point(vm.originLat, vm.originLon)
    val destination = if (vm.mode == Mode.AlongJourney) Validation.point(vm.destLat, vm.destLon) else null

    LaunchedEffect(map, vm.choosingMapLocation, vm.mapTarget) {
        if (vm.choosingMapLocation) {
            val target = if (vm.mapTarget == MapTarget.Destination) destination ?: origin else origin
            target?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 14.0)) }
        }
    }

    // Start and destination pins follow the entered coordinates.
    LaunchedEffect(map, styleReady, origin, destination) {
        val src = map?.style?.getSourceAs<GeoJsonSource>(PINS_SRC) ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val pins = listOfNotNull(origin?.let { "start" to it }, destination?.let { "destination" to it }).map { (role, p) ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also { it.addStringProperty(ROLE, role) }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(pins))
    }

    // Baseline, stations and the chosen route. The camera fits once per new result.
    LaunchedEffect(map, styleReady, result, nearby, vm.mapOpenOnly, vm.selectedStationId, fitRequest) {
        val style = map?.style ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val base = result?.baselineGeometry?.let { Polyline.decode(it) }.orEmpty()
        style.getSourceAs<GeoJsonSource>(BASE_SRC)?.setGeoJson(lineOf(base))
        val route = selected?.let { Polyline.decode(it.routeGeometry) }.orEmpty()
        style.getSourceAs<GeoJsonSource>(ROUTE_SRC)?.setGeoJson(lineOf(route))
        style.getSourceAs<GeoJsonSource>(STATION_SRC)?.setGeoJson(FeatureCollection.fromFeatures(
            (visibleCandidates.map { it.stationId to it.position } +
                visibleNearby.map { it.id to it.position }).map { (id, position) ->
                Feature.fromGeometry(Point.fromLngLat(position.lon, position.lat)).also {
                    it.addStringProperty(ID, id)
                    it.addBooleanProperty(SELECTED, id == vm.selectedStationId)
                }
            }))
        val extent = if (nearby != null) visibleNearby.map { it.position } + listOfNotNull(origin) else if (route.size >= 2) route else base
        if (!vm.choosingMapLocation && extent.size >= 2 && (vm.fitCount != fitted || fitRequest != fittedRequest)) {
            fittedRequest = fitRequest
            fitted = vm.fitCount
            val bounds = LatLngBounds.Builder().includes(extent.map { LatLng(it.lat, it.lon) }).build()
            map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 90))
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
      var panelFraction by rememberSaveable { mutableStateOf(0.48f) }
      val availableHeight = maxHeight
      val density = LocalDensity.current
      val totalPixels = with(density) { maxHeight.toPx() }
      Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            if (vm.choosingMapLocation) {
                Icon(painterResource(if (vm.mapTarget == MapTarget.Start) R.drawable.ic_car else R.drawable.ic_map),
                    contentDescription = "Selected location at map centre",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center).size(36.dp))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                if (vm.choosingMapLocation) {
                    Text("Move the map to choose your ${vm.mapTarget.name.lowercase()}.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = map != null, onClick = {
                            map?.cameraPosition?.target?.let { vm.setFromMap(LatLon(it.latitude, it.longitude)) }
                        }) { Text("Use this location") }
                        TextButton(onClick = { vm.choosingMapLocation = false }) { Text("Cancel") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.beginMapChoice(MapTarget.Start) }) { Text("Choose start") }
                        if (vm.mode == Mode.AlongJourney) TextButton(onClick = { vm.beginMapChoice(MapTarget.Destination) }) { Text("Choose destination") }
                    }
                }
            }
        }
        if (!vm.choosingMapLocation) {
        Column(Modifier.fillMaxWidth().height(availableHeight * panelFraction)) {
            Row(Modifier.fillMaxWidth().draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    panelFraction = (panelFraction - delta / totalPixels).coerceIn(0.25f, 0.55f)
                }), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { panelFraction = if (panelFraction < 0.5f) 0.55f else 0.25f }) {
                    Text(if (panelFraction < 0.5f) "More results ↑" else "More map ↓")
                }
                TextButton(onClick = { fitRequest++ }) { Text(if (nearby != null) "Fit stations" else "Fit route") }
            }
            MapResults(vm, onSelect = { vm.selectStation(it); fitRequest++ })
        }
        }
      }
    }
}
