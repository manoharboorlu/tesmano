package com.matedroid.domain

import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.util.formatMonthYear
import com.matedroid.util.parseInstantAware
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exploratory personal driving-efficiency analytics, built entirely from cached [DriveSummary]
 * rows. Never fetches a Drive Detail. Every aggregate here is a *weighted* ratio
 * (`SUM(valid energy) / SUM(valid distance)`) — individual drive-level Wh/unit values are never
 * averaged together, per the project's efficiency-math rule.
 *
 * These are associations for personal exploration, not a causal model — see the *By*
 * naming below (`efficiencyBySpeedBand`, not "impact of speed").
 */
enum class EfficiencyPeriod {
    Last7Days,
    Last30Days,
    Last90Days,
    AllTime
}

private fun EfficiencyPeriod.windowDays(): Int? = when (this) {
    EfficiencyPeriod.Last7Days -> 7
    EfficiencyPeriod.Last30Days -> 30
    EfficiencyPeriod.Last90Days -> 90
    EfficiencyPeriod.AllTime -> null
}

/** The drive's local calendar date, honoring the stored timestamp's actual offset. */
fun driveLocalDate(drive: DriveSummary, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    parseInstantAware(drive.startDate, zone)?.toLocalDate()

fun filterByPeriod(
    drives: List<DriveSummary>,
    period: EfficiencyPeriod,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): List<DriveSummary> {
    val days = period.windowDays() ?: return drives
    val start = today.minusDays((days - 1).toLong())
    return drives.filter { d -> driveLocalDate(d, zone)?.let { it in start..today } == true }
}

/**
 * [driveCount]/[totalDistance] cover every drive in scope (an honest "miles driven" total);
 * [weightedEfficiency] and [totalEnergyKwh] are restricted to drives with valid distance+energy,
 * exactly matching [validDriveCount] — the pair doubles as the data-quality coverage stat
 * ("94 of 101 drives with valid efficiency").
 */
data class EfficiencySummary(
    val weightedEfficiency: Double?,
    val totalDistance: Double,
    val totalEnergyKwh: Double,
    val driveCount: Int,
    val validDriveCount: Int
)

private fun DriveSummary.hasValidEfficiencyInputs(): Boolean =
    energyConsumed != null && energyConsumed > 0.0 && distance > 0.0

private fun DriveSummary.efficiencyOrNull(): Double? =
    if (hasValidEfficiencyInputs()) energyConsumed!! * 1000.0 / distance else null

fun computeEfficiencySummary(drives: List<DriveSummary>): EfficiencySummary {
    val valid = drives.filter { it.hasValidEfficiencyInputs() }
    val sumEnergy = valid.sumOf { it.energyConsumed!! }
    val sumValidDistance = valid.sumOf { it.distance }
    return EfficiencySummary(
        weightedEfficiency = if (sumValidDistance > 0) sumEnergy * 1000.0 / sumValidDistance else null,
        totalDistance = drives.sumOf { it.distance },
        totalEnergyKwh = sumEnergy,
        driveCount = drives.size,
        validDriveCount = valid.size
    )
}

/** How the period's weighted efficiency compares to the all-time personal baseline. Lower Wh/unit is more efficient. */
data class BaselineComparison(val periodWhPerUnit: Double, val baselineWhPerUnit: Double) {
    val percentBetter: Double get() = (baselineWhPerUnit - periodWhPerUnit) / baselineWhPerUnit * 100.0

    /** True when the difference rounds to 0.0% at the display precision used for [percentBetter]. */
    val isEffectivelyBaseline: Boolean get() = kotlin.math.abs(percentBetter) < 0.05
}

fun compareToBaseline(period: EfficiencySummary, baseline: EfficiencySummary): BaselineComparison? {
    val periodEff = period.weightedEfficiency ?: return null
    val baselineEff = baseline.weightedEfficiency?.takeIf { it > 0.0 } ?: return null
    return BaselineComparison(periodEff, baselineEff)
}

/** One banded group's weighted efficiency — a generic result shared by every "efficiency by X" view. */
data class BandResult<T>(val band: T, val summary: EfficiencySummary)

private fun <T> bandedEfficiency(drives: List<DriveSummary>, bands: List<T>, bandOf: (DriveSummary) -> T?): List<BandResult<T>> {
    val grouped = drives.filter { it.hasValidEfficiencyInputs() }.groupBy(bandOf)
    return bands.map { band -> BandResult(band, computeEfficiencySummary(grouped[band].orEmpty())) }
}

// ---- Associated with average speed (distance / duration) ----

enum class SpeedBand(val label: String, val maxExclusive: Double?) {
    Under25("< 25", 25.0),
    B25to45("25–45", 45.0),
    B45to60("45–60", 60.0),
    Over60("60+", null)
}

private fun DriveSummary.averageSpeed(): Double? = if (durationMin > 0) distance / (durationMin / 60.0) else null

fun speedBandOf(speed: Double): SpeedBand = SpeedBand.entries.first { speed < (it.maxExclusive ?: Double.MAX_VALUE) }

fun efficiencyBySpeedBand(drives: List<DriveSummary>): List<BandResult<SpeedBand>> =
    bandedEfficiency(drives, SpeedBand.entries) { d -> d.averageSpeed()?.let(::speedBandOf) }

// ---- Associated with trip length ----

enum class TripLengthBand(val label: String, val maxExclusive: Double?) {
    B0to5("0–5", 5.0),
    B5to15("5–15", 15.0),
    B15to40("15–40", 40.0),
    Over40("40+", null)
}

fun tripLengthBandOf(distance: Double): TripLengthBand = TripLengthBand.entries.first { distance < (it.maxExclusive ?: Double.MAX_VALUE) }

fun efficiencyByTripLength(drives: List<DriveSummary>): List<BandResult<TripLengthBand>> =
    bandedEfficiency(drives, TripLengthBand.entries) { d -> tripLengthBandOf(d.distance) }

// ---- Associated with outside temperature (measured, never substituted with cabin temp) ----

data class TempBand(val label: String, val maxExclusive: Double?)

private val tempBandsFahrenheit = listOf(
    TempBand("< 32°F", 32.0),
    TempBand("32–50°F", 50.0),
    TempBand("50–70°F", 70.0),
    TempBand("70°F+", null)
)
private val tempBandsCelsius = listOf(
    TempBand("< 0°C", 0.0),
    TempBand("0–10°C", 10.0),
    TempBand("10–21°C", 21.0),
    TempBand("21°C+", null)
)

fun temperatureBands(isFahrenheit: Boolean): List<TempBand> = if (isFahrenheit) tempBandsFahrenheit else tempBandsCelsius

fun efficiencyByOutsideTemp(drives: List<DriveSummary>, isFahrenheit: Boolean): List<BandResult<TempBand>> {
    val bands = temperatureBands(isFahrenheit)
    return bandedEfficiency(drives.filter { it.outsideTempAvg != null }, bands) { d ->
        bands.first { d.outsideTempAvg!! < (it.maxExclusive ?: Double.MAX_VALUE) }
    }
}

// ---- Best / worst individual drives ----

data class RankedDrive(val summary: DriveSummary, val efficiency: Double)

/** [minDistance] is a user-facing display filter (e.g. "Drives ≥ 10 mi"), never a data-validity cutoff. */
fun bestWorstDrives(drives: List<DriveSummary>, minDistance: Double, limit: Int = 5): Pair<List<RankedDrive>, List<RankedDrive>> {
    val ranked = drives.filter { it.hasValidEfficiencyInputs() && it.distance >= minDistance }
        .mapNotNull { d -> d.efficiencyOrNull()?.let { RankedDrive(d, it) } }
    return ranked.sortedBy { it.efficiency }.take(limit) to ranked.sortedByDescending { it.efficiency }.take(limit)
}

// ---- Trend: daily for 7d/30d, weekly for 90d, monthly for All ----

data class TrendPoint(val label: String, val weightedEfficiency: Double?, val distance: Double)

private val trendDayFormatter = DateTimeFormatter.ofPattern("MMM d")

fun efficiencyTrend(drives: List<DriveSummary>, period: EfficiencyPeriod, zone: ZoneId = ZoneId.systemDefault()): List<TrendPoint> {
    val valid = drives.filter { it.hasValidEfficiencyInputs() }
    return when (period) {
        EfficiencyPeriod.Last7Days, EfficiencyPeriod.Last30Days ->
            valid.groupBy { driveLocalDate(it, zone) }
                .filterKeys { it != null }
                .toSortedMap(compareBy { it })
                .map { (date, group) -> group.toTrendPoint(date!!.format(trendDayFormatter)) }
        EfficiencyPeriod.Last90Days ->
            valid.groupBy { driveLocalDate(it, zone)?.with(DayOfWeek.MONDAY) }
                .filterKeys { it != null }
                .toSortedMap(compareBy { it })
                .map { (weekStart, group) -> group.toTrendPoint(weekStart!!.format(trendDayFormatter)) }
        EfficiencyPeriod.AllTime ->
            valid.groupBy { driveLocalDate(it, zone)?.let { d -> YearMonth.from(d) } }
                .filterKeys { it != null }
                .toSortedMap(compareBy { it })
                .map { (month, group) -> group.toTrendPoint(month!!.atDay(1).formatMonthYear()) }
    }
}

private fun List<DriveSummary>.toTrendPoint(label: String): TrendPoint {
    val summary = computeEfficiencySummary(this)
    return TrendPoint(label, summary.weightedEfficiency, summary.totalDistance)
}
