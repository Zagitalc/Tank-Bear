package uk.tankbear.app

import org.junit.Assert.*
import org.junit.Test
import uk.tankbear.app.ui.MapPrices
import uk.tankbear.app.ui.PumpPriceBand
import java.math.BigDecimal

class MapPricesTest {
    @Test fun medianUsesNumericOrderAndExactEvenMidpointWithoutMutatingInput() {
        val prices = listOf("100", "99.9", "101.1", "110")
        assertEquals(0, MapPrices.median(prices)!!.compareTo(BigDecimal("100.55")))
        assertEquals(0, MapPrices.median(listOf("100", "9", "99.9"))!!.compareTo(BigDecimal("99.9")))
        assertEquals(listOf("100", "99.9", "101.1", "110"), prices)
    }

    @Test fun amberIncludesBothOnePennyBoundariesButNotLargerDifferences() {
        val median = BigDecimal("172.9")
        assertEquals(PumpPriceBand.Around, MapPrices.band("171.9", median))
        assertEquals(PumpPriceBand.Around, MapPrices.band("173.900", median))
        assertEquals(PumpPriceBand.Below, MapPrices.band("171.899", median))
        assertEquals(PumpPriceBand.Above, MapPrices.band("173.901", median))
        assertEquals(PumpPriceBand.Around, MapPrices.band("172.9", median))
    }

    @Test fun missingOrInvalidPricesNeverGetInventedOrColouredCheap() {
        for (value in listOf(null, "", "NaN", "0", "-1")) {
            assertEquals(PumpPriceBand.Unknown, MapPrices.band(value, BigDecimal("172.9")))
            assertEquals("—", MapPrices.label(value))
        }
        assertNull(MapPrices.median(listOf("172.9", "invalid", "0")))
        assertEquals(PumpPriceBand.Unknown, MapPrices.band("172.9", null))
    }

    @Test fun labelsPreserveSuppliedPriceAndComparisonNamesItsScope() {
        assertEquals("172.9p", MapPrices.label("172.900"))
        assertEquals("172.95p", MapPrices.label("172.95"))
        assertTrue(MapPrices.comparison("170.9", BigDecimal("172.9"), "the local median").contains("2 p/L below the local median"))
        assertTrue(MapPrices.comparison("175.9", BigDecimal("172.9"), "the median among routed stations checked").contains("above the median among routed stations checked"))
        assertEquals("No price for your selected fuel.", MapPrices.comparison(null, BigDecimal.ONE, "median"))
        assertEquals("Price comparison unavailable.", MapPrices.comparison("172.9", null, "median"))
    }
}
