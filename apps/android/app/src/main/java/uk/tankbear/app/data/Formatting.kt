package uk.tankbear.app.data

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

object Formatting {
    private const val METRES_PER_MILE = 1609.344

    fun pence(value: Long): String {
        val sign = if (value < 0) "-" else ""
        val a = abs(value)
        return if (a >= 100) "$sign£${a / 100}.${(a % 100).toString().padStart(2, '0')}" else "$sign${a}p"
    }

    fun pricePerLitre(pencePerLitre: String): String = "${pencePerLitre}p/L"

    fun miles(metres: String): String {
        val m = metres.toDoubleOrNull() ?: return "?"
        return String.format(java.util.Locale.UK, "%.1f mi", m / METRES_PER_MILE)
    }

    fun minutes(seconds: String): String {
        val s = seconds.toDoubleOrNull() ?: return "?"
        val mins = (s / 60.0).roundToInt()
        return if (mins < 1 && s > 0) "under 1 min" else "$mins min"
    }

    /** "3 days ago" style age of an ISO instant; clock is supplied so tests are deterministic. */
    fun age(iso: String, now: Instant): String {
        val then = try { Instant.parse(iso) } catch (_: Exception) { return "unknown" }
        val d = Duration.between(then, now)
        return when {
            d.isNegative -> "just now"
            d.toMinutes() < 2 -> "just now"
            d.toHours() < 1 -> "${d.toMinutes()} min ago"
            d.toHours() < 24 -> "${d.toHours()} h ago"
            d.toDays() == 1L -> "yesterday"
            else -> "${d.toDays()} days ago"
        }
    }

    /** Savings are estimates: small differences are not presented as decisive. */
    fun savingHeadline(trueSavingPence: Long, referenceName: String?): String {
        val vs = referenceName?.let { " vs $it" } ?: ""
        return when {
            trueSavingPence > 0 -> "Saves about ${pence(trueSavingPence)}$vs"
            trueSavingPence < 0 -> "Costs about ${pence(-trueSavingPence)} more$vs"
            else -> "About the same cost$vs"
        }
    }

    /** Plain wording for usual opening hours; says so when it cannot tell. */
    fun opening(o: OpeningInfo): String = when {
        o.state == "open" && o.is24Hours -> "Open 24 hours"
        o.state == "open" && o.closesInMinutes != null && o.closesInMinutes <= 30 -> "Closes in ${o.closesInMinutes} min"
        o.state == "open" && o.closesAt != null -> "Open until ${o.closesAt}"
        o.state == "open" -> "Open now"
        o.state == "closed" && o.opensAt != null -> "Closed now, opens ${o.opensAt}"
        o.state == "closed" -> "Closed now"
        else -> "Opening hours unknown"
    }

    fun metresAsMiles(metres: Int): String = miles(metres.toString())

    /** "4p/L above the local median", exact decimal difference from the backend. */
    fun versusMedian(diff: String?): String? {
        val d = diff?.toDoubleOrNull() ?: return null
        return when {
            d > 0 -> "${trim(diff)}p/L above the local median"
            d < 0 -> "${trim(diff.removePrefix("-"))}p/L below the local median"
            else -> "At the local median"
        }
    }

    private fun trim(s: String) = s

    fun trendHeadline(t: HistoryTrend, days: Int): String {
        val magnitude = t.changePencePerLitre.removePrefix("-")
        return when (t.direction) {
            "up" -> "Up ${magnitude}p/L over $days days (from ${t.sincePencePerLitre}p/L)"
            "down" -> "Down ${magnitude}p/L over $days days (from ${t.sincePencePerLitre}p/L)"
            else -> "Unchanged over $days days (${t.sincePencePerLitre}p/L)"
        }
    }

    const val SMALL_DIFFERENCE_PENCE = 20L
    fun isSmallDifference(trueSavingPence: Long) = abs(trueSavingPence) < SMALL_DIFFERENCE_PENCE
}
