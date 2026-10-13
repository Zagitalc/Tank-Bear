package uk.tankbear.app.ui

import java.math.BigDecimal

internal enum class PumpPriceBand { Below, Around, Above, Unknown }

/** Display comparison only. Never changes backend costs, ranking or eligibility. */
internal object MapPrices {
    private val margin = BigDecimal.ONE

    fun price(value: String?): BigDecimal? = value?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    fun median(values: List<String>): BigDecimal? {
        val prices = values.mapNotNull(::price).sorted()
        if (prices.size < 2) return null
        val middle = prices.size / 2
        return if (prices.size % 2 == 1) prices[middle]
        else (prices[middle - 1] + prices[middle]).divide(BigDecimal(2))
    }

    fun band(value: String?, median: BigDecimal?): PumpPriceBand {
        val price = price(value) ?: return PumpPriceBand.Unknown
        if (median == null) return PumpPriceBand.Unknown
        val difference = price - median
        return when {
            difference < -margin -> PumpPriceBand.Below
            difference > margin -> PumpPriceBand.Above
            else -> PumpPriceBand.Around
        }
    }

    fun label(value: String?): String = price(value)?.stripTrailingZeros()?.toPlainString()?.let { "${it}p" } ?: "—"

    fun comparison(value: String?, median: BigDecimal?, scope: String): String {
        val price = price(value) ?: return "No price for your selected fuel."
        if (median == null) return "Price comparison unavailable."
        val difference = price - median
        val amount = difference.abs().stripTrailingZeros().toPlainString()
        return when (band(value, median)) {
            PumpPriceBand.Below -> "$amount p/L below $scope."
            PumpPriceBand.Above -> "$amount p/L above $scope."
            else -> "Within 1p/L of $scope."
        }
    }
}
