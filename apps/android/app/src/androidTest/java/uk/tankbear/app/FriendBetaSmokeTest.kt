package uk.tankbear.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import uk.tankbear.app.data.*

/** Public sample points only; no phone location or credentials. */
class FriendBetaSmokeTest {
    @Test fun nearbyReturnHistoryAndGeocodingRemainCompatible() = runBlocking {
        val client = OptimiseClient("https://tankbear.duckdns.org", "")
        val reading = LatLon(51.4543, -0.9781)
        val nearby = client.nearby(reading, FuelType.E10, "30")
        assertTrue("Nearby response", nearby is ApiOutcome.NearbyOk)
        val stations = (nearby as ApiOutcome.NearbyOk).result.stations
        assertTrue(stations.isNotEmpty())
        assertTrue("History response", client.history(stations.first().id, FuelType.E10) is ApiOutcome.HistoryOk)
        val trip = client.optimise(JourneyRequest(Mode.FuelTrip, reading, null, FuelType.E10, "40", "30"))
        assertTrue("Return response", trip is ApiOutcome.Ok)
        assertTrue((trip as ApiOutcome.Ok).result.candidates.isNotEmpty())
        val places = GeocodingClient("https://tankbear.duckdns.org", "").search("Reading")
        assertNull(places.error)
        assertTrue(places.places.isNotEmpty())
        val offline = OptimiseClient("http://127.0.0.1:1", "").nearby(reading, FuelType.E10, "30")
        assertTrue("Unavailable server handled", offline is ApiOutcome.Failure)
    }
}
