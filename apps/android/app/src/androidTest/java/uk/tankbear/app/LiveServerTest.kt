package uk.tankbear.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.tankbear.app.data.*

/** Opt-in deployed smoke test. The key is provisioned into app-private storage, never test arguments. */
@RunWith(AndroidJUnit4::class)
class LiveServerTest {
    @Test
    fun liveNearbyAndOptionalJourney() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveServer") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyFile = File(context.filesDir, "live-test-key")
        check(keyFile.isFile) { "Provision the app-private live-test-key file first" }
        val key = keyFile.readText().trim()
        keyFile.delete()
        check(key.length >= 24) { "Live test key is missing" }
        val client = OptimiseClient("https://tankbear.duckdns.org", key)
        // Public demonstration points, not the phone's location or a user's journey.
        val reading = LatLon(51.4543, -0.9781)
        val nearby = client.nearby(reading, FuelType.E10, "30")
        assertTrue("Live Nearby must succeed", nearby is ApiOutcome.NearbyOk)
        assertTrue("Live Nearby must contain stations", (nearby as ApiOutcome.NearbyOk).result.stations.isNotEmpty())
        if (InstrumentationRegistry.getArguments().getString("liveJourney") == "false") return@runBlocking
        val journey = client.optimise(JourneyRequest(Mode.AlongJourney, reading,
            LatLon(51.752, -1.2577), FuelType.E10, "45", "30"))
        assertTrue("Live journey must succeed", journey is ApiOutcome.Ok)
        val result = (journey as ApiOutcome.Ok).result
        assertTrue("Live journey must contain a real baseline", !result.baselineGeometry.isNullOrEmpty())
        assertTrue("Live journey must route stations", result.stationsRouted > 0)
        // Optional development-phone setup, only after the complete live check passes.
        if (InstrumentationRegistry.getArguments().getString("provisionApp") == "true") {
            val prefs = Prefs(context)
            prefs.save(prefs.load().copy(baseUrl = "https://tankbear.duckdns.org", appKey = key))
        }
    }
}
