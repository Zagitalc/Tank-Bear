package uk.tankbear.app

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import uk.tankbear.app.data.ApiOutcome
import uk.tankbear.app.data.FailureKind
import uk.tankbear.app.data.Formatting
import uk.tankbear.app.data.FuelType
import uk.tankbear.app.data.JourneyRequest
import uk.tankbear.app.data.LatLon
import uk.tankbear.app.data.Mode
import uk.tankbear.app.data.OptimiseClient
import uk.tankbear.app.data.Polyline
import uk.tankbear.app.data.ResultParser
import uk.tankbear.app.data.Validation

class DataTest {
    private val sample = javaClass.classLoader!!.getResource("optimise-response-sample.json")!!.readText()

    @Test fun parsesTheSharedBackendFixture() {
        val r = ResultParser.parse(sample)
        assertTrue(r.ranked)
        assertEquals("Best among the stations checked.", r.scope)
        assertEquals(3, r.candidates.size)
        assertEquals(r.bestOverallId, r.candidates.first().stationId)
        assertEquals("station-a", r.referenceStationId)
        assertEquals("128.9", r.candidates.first().pencePerLitre)
        assertEquals("2026-10-03T08:00:00.000Z", r.candidates.first().priceLastUpdated)
        assertFalse(r.partial)
        assertTrue(r.candidates.first().trueSavingPence > 0)
    }

    @Test fun requestBodyMatchesTheContract() {
        val body = JSONObject(OptimiseClient.body(JourneyRequest(Mode.AlongJourney, LatLon(51.45, -0.97), LatLon(51.75, -1.25), FuelType.B7, "45", "30")))
        assertEquals("along_journey", body.getString("mode"))
        assertEquals("B7", body.getString("fuelType"))
        assertEquals("45", body.getJSONObject("vehicle").getString("mpgImperial"))
        assertEquals("30", body.getString("litresToBuy"))
        val trip = JSONObject(OptimiseClient.body(JourneyRequest(Mode.FuelTrip, LatLon(51.45, -0.97), null, FuelType.E10, "45", "30")))
        assertFalse(trip.has("destination"))
    }

    @Test fun failuresAreExplainedWithoutBackendDetail() {
        assertEquals(FailureKind.Unauthorised, OptimiseClient.failure(401, "UNAUTHORISED", null).kind)
        val limited = OptimiseClient.failure(429, "RATE_LIMITED", 30)
        assertEquals(FailureKind.RateLimited, limited.kind)
        assertEquals(30, limited.retryAfterSeconds)
        assertEquals(FailureKind.NoRoute, OptimiseClient.failure(422, "NO_ROUTE", null).kind)
        assertEquals(FailureKind.NoData, OptimiseClient.failure(503, "FUEL_DATA_STALE", null).kind)
        assertEquals(FailureKind.Server, OptimiseClient.failure(500, null, null).kind)
    }

    @Test fun formatsMoneyDistanceAndTime() {
        assertEquals("52p", Formatting.pence(52))
        assertEquals("£1.05", Formatting.pence(105))
        assertEquals("-£2.00", Formatting.pence(-200))
        assertEquals("174.9p/L", Formatting.pricePerLitre("174.9"))
        assertEquals("1.0 mi", Formatting.miles("1609.344"))
        assertEquals("5 min", Formatting.minutes("300"))
        assertEquals("under 1 min", Formatting.minutes("20"))
    }

    @Test fun priceAgeIsRelativeToAnInjectedClock() {
        val now = Instant.parse("2026-10-05T12:00:00Z")
        assertEquals("just now", Formatting.age("2026-10-05T11:59:00Z", now))
        assertEquals("30 min ago", Formatting.age("2026-10-05T11:30:00Z", now))
        assertEquals("3 h ago", Formatting.age("2026-10-05T09:00:00Z", now))
        assertEquals("yesterday", Formatting.age("2026-10-04T08:00:00Z", now))
        assertEquals("5 days ago", Formatting.age("2026-09-30T08:00:00Z", now))
        assertEquals("unknown", Formatting.age("garbage", now))
    }

    @Test fun savingsAreNotOverclaimed() {
        assertEquals("Saves about 70p vs Shell", Formatting.savingHeadline(70, "Shell"))
        assertEquals("Costs about 36p more vs Shell", Formatting.savingHeadline(-36, "Shell"))
        assertEquals("About the same cost", Formatting.savingHeadline(0, null))
        assertTrue(Formatting.isSmallDifference(19))
        assertFalse(Formatting.isSmallDifference(-20))
    }

    @Test fun validationMatchesTheBackendRules() {
        assertEquals(LatLon(51.45, -0.97), Validation.point(" 51.45", "-0.97 "))
        assertNull(Validation.point("0", "0"))
        assertNull(Validation.point("abc", "1"))
        assertNull(Validation.point("NaN", "1"))
        assertEquals("45", Validation.mpg("45"))
        assertNull(Validation.mpg("0"))
        assertNull(Validation.mpg("1e2"))
        assertNull(Validation.mpg("4.5.1"))
        assertEquals("30", Validation.litres("30"))
        assertNull(Validation.litres("501"))
    }

    @Test fun routeGeometryDecodesToTheRealJourneyPoints() {
        val r = ResultParser.parse(sample)
        val base = Polyline.decode(r.baselineGeometry!!)
        assertEquals(2, base.size)
        assertEquals(51.45, base.first().lat, 1e-6)
        assertEquals(-0.97, base.first().lon, 1e-6)
        assertEquals(51.75, base.last().lat, 1e-6)
        val via = r.candidates.first()
        val route = Polyline.decode(via.routeGeometry)
        assertEquals(3, route.size)
        assertEquals(via.position.lat, route[1].lat, 1e-6)
        assertEquals(via.position.lon, route[1].lon, 1e-6)
    }

    @Test fun badPolylinesAreEmptyNotCrashes() {
        assertTrue(Polyline.decode("").isEmpty())
        assertTrue(Polyline.decode("~").isEmpty())
        assertTrue(Polyline.decode("a b").isEmpty())
    }
}
