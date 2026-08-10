package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeCostOverride
import com.matedroid.data.local.entity.ChargeCurveAggregate
import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.ChargingRateRule
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.util.parseInstantAware
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exploratory personal charging analytics, built entirely from cached [ChargeSummary] rows plus
 * the already-computed [ChargeDetailAggregate] (AC/DC, peak power — populated by the existing
 * bulk charge-detail sync, never fetched eagerly here). Every history-wide efficiency figure is a
 * *weighted* ratio (`SUM(battery energy) / SUM(grid energy)` over coherent sessions only) —
 * per-session efficiency percentages are never averaged together. Cost math always delegates to
 * [ChargeCostEngine] / [ChargingRateResolver] — this file never re-derives pricing.
 */
enum class ChargingPeriod { Last7Days, Last30Days, Last90Days, AllTime }

private fun ChargingPeriod.windowDays(): Int? = when (this) {
    ChargingPeriod.Last7Days -> 7
    ChargingPeriod.Last30Days -> 30
    ChargingPeriod.Last90Days -> 90
    ChargingPeriod.AllTime -> null
}

fun chargeLocalDate(charge: ChargeSummary, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    parseInstantAware(charge.startDate, zone)?.toLocalDate()

fun filterChargesByPeriod(
    charges: List<ChargeSummary>,
    period: ChargingPeriod,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): List<ChargeSummary> {
    val days = period.windowDays() ?: return charges
    val start = today.minusDays((days - 1).toLong())
    return charges.filter { c -> chargeLocalDate(c, zone)?.let { it in start..today } == true }
}

/** A session's grid energy is "coherent" only when reported and no smaller than battery energy added. */
private fun ChargeSummary.isCoherentGridSession(): Boolean =
    ChargeCostEngine.energyBasis(energyAdded, energyUsed) is ChargeCostEnergyBasis.GridEnergyReported

/**
 * [sessionCount]/[batteryEnergyKwh] cover every session in scope (an honest "energy added" total);
 * [gridEnergyKwh]/[weightedEfficiencyPercent] are restricted to sessions with coherent wall/grid
 * energy, matching [coherentSessionCount] — doubles as the coverage stat ("9/14 sessions with
 * wall-energy efficiency").
 */
data class ChargingHeadline(
    val sessionCount: Int,
    val batteryEnergyKwh: Double,
    val gridEnergyKwh: Double?,
    val coherentSessionCount: Int,
    val weightedEfficiencyPercent: Double?,
    val avgDurationMin: Double?,
    /** SUM(energy added) / SUM(duration) for sessions with a valid duration — a weighted rate, not an averaged one. */
    val avgChargingRateKw: Double?,
    val peakPowerKw: Int?
)

fun computeChargingHeadline(sessions: List<ChargeSummary>, aggregatesByChargeId: Map<Int, ChargeDetailAggregate>): ChargingHeadline {
    val coherent = sessions.filter { it.isCoherentGridSession() }
    val gridEnergy = coherent.sumOf { it.energyUsed!! }.takeIf { coherent.isNotEmpty() }
    val coherentBatteryEnergy = coherent.sumOf { it.energyAdded }
    val weightedEfficiency = if (gridEnergy != null && gridEnergy > 0.0) coherentBatteryEnergy / gridEnergy * 100.0 else null
    val withDuration = sessions.filter { it.durationMin > 0 }
    val totalDurationHours = withDuration.sumOf { it.durationMin / 60.0 }
    val avgRate = if (totalDurationHours > 0.0) withDuration.sumOf { it.energyAdded } / totalDurationHours else null
    val avgDuration = if (sessions.isNotEmpty()) sessions.map { it.durationMin }.average() else null
    val peakPower = sessions.mapNotNull { aggregatesByChargeId[it.chargeId]?.maxChargerPower }.maxOrNull()
    return ChargingHeadline(
        sessionCount = sessions.size,
        batteryEnergyKwh = sessions.sumOf { it.energyAdded },
        gridEnergyKwh = gridEnergy,
        coherentSessionCount = coherent.size,
        weightedEfficiencyPercent = weightedEfficiency,
        avgDurationMin = avgDuration,
        avgChargingRateKw = avgRate,
        peakPowerKw = peakPower
    )
}

// ---- Place comparison (Home / Work / Custom / Other) — reuses ChargeCostEngine, never re-prices ----

data class PlaceChargingBreakdown(
    val category: String,
    val sessionCount: Int,
    val batteryEnergyKwh: Double,
    val gridEnergyKwh: Double?,
    val weightedEfficiencyPercent: Double?,
    val totalDurationMin: Int,
    val costMinorUnits: Long?,
    val currencyCode: String?,
    val effectiveCostPerKwh: BigDecimal?
)

private data class PricedSession(val summary: ChargeSummary, val cost: ChargeCostPresentation, val category: String)

private fun priceSessions(
    sessions: List<ChargeSummary>,
    rules: List<ChargingRateRule>,
    places: List<SmartPlace>,
    overrides: List<ChargeCostOverride>
): List<PricedSession> {
    val overridesByCharge = overrides.associateBy { it.chargeId }
    return sessions.map { s ->
        val cost = ChargeCostEngine.presentation(s.toCostInput(), rules, places, overridesByCharge[s.chargeId])
        PricedSession(s, cost, categoryFor(cost.place))
    }
}

private fun groupCostAndEfficiency(group: List<PricedSession>): Pair<Long?, String?> {
    val priced = group.filter { it.cost.costMinorUnits != null }
    val currencies = priced.map { it.cost.currencyCode }.toSet()
    val total = if (currencies.size == 1) priced.sumOf { it.cost.costMinorUnits ?: 0L } else null
    return total to currencies.singleOrNull()
}

private fun effectiveRate(totalCostMinorUnits: Long?, priced: List<PricedSession>): BigDecimal? {
    if (totalCostMinorUnits == null) return null
    val basisKwh = priced.mapNotNull { it.cost.energyBasis?.kwh }.fold(BigDecimal.ZERO) { acc, kwh -> acc + kwh }
    if (basisKwh <= BigDecimal.ZERO) return null
    return BigDecimal(totalCostMinorUnits).divide(BigDecimal(100), 8, RoundingMode.HALF_UP).divide(basisKwh, 4, RoundingMode.HALF_UP)
}

fun computePlaceBreakdown(
    sessions: List<ChargeSummary>,
    rules: List<ChargingRateRule>,
    places: List<SmartPlace>,
    overrides: List<ChargeCostOverride>
): List<PlaceChargingBreakdown> {
    val priced = priceSessions(sessions, rules, places, overrides)
    return priced.groupBy { it.category }.map { (category, group) ->
        val summaries = group.map { it.summary }
        val coherent = summaries.filter { it.isCoherentGridSession() }
        val gridEnergy = coherent.sumOf { it.energyUsed!! }.takeIf { coherent.isNotEmpty() }
        val weighted = if (gridEnergy != null && gridEnergy > 0.0) coherent.sumOf { it.energyAdded } / gridEnergy * 100.0 else null
        val (totalCost, currency) = groupCostAndEfficiency(group)
        PlaceChargingBreakdown(
            category = category,
            sessionCount = group.size,
            batteryEnergyKwh = summaries.sumOf { it.energyAdded },
            gridEnergyKwh = gridEnergy,
            weightedEfficiencyPercent = weighted,
            totalDurationMin = summaries.sumOf { it.durationMin },
            costMinorUnits = totalCost,
            currencyCode = currency,
            effectiveCostPerKwh = effectiveRate(totalCost, group.filter { it.cost.costMinorUnits != null })
        )
    }.sortedByDescending { it.sessionCount }
}

data class ChargingCostSummary(
    val totalCostMinorUnits: Long?,
    val currencyCode: String?,
    val effectiveCostPerKwh: BigDecimal?,
    val pricedSessions: Int,
    val freeSessions: Int,
    val unavailableSessions: Int
)

/** Total spend and effective $/kWh for a set of sessions — delegates entirely to [ChargeCostEngine], never re-prices. */
fun computeChargingCostSummary(
    sessions: List<ChargeSummary>,
    rules: List<ChargingRateRule>,
    places: List<SmartPlace>,
    overrides: List<ChargeCostOverride>
): ChargingCostSummary {
    val priced = priceSessions(sessions, rules, places, overrides)
    val (total, currency) = groupCostAndEfficiency(priced)
    val withCost = priced.filter { it.cost.costMinorUnits != null }
    return ChargingCostSummary(
        totalCostMinorUnits = total,
        currencyCode = currency,
        effectiveCostPerKwh = effectiveRate(total, withCost),
        pricedSessions = withCost.size,
        freeSessions = withCost.count { it.cost.isFree },
        unavailableSessions = priced.size - withCost.size
    )
}

// ---- AC vs DC — only where ChargeDetailAggregate.isFastCharger classification exists ----

data class AcDcBreakdown(
    val isFastCharger: Boolean,
    val sessionCount: Int,
    val batteryEnergyKwh: Double,
    val gridEnergyKwh: Double?,
    val weightedEfficiencyPercent: Double?,
    val medianPeakPowerKw: Int?
)

fun acDcClassifiedCount(sessions: List<ChargeSummary>, aggregatesByChargeId: Map<Int, ChargeDetailAggregate>): Int =
    sessions.count { aggregatesByChargeId[it.chargeId]?.isFastCharger != null }

fun computeAcDcBreakdown(sessions: List<ChargeSummary>, aggregatesByChargeId: Map<Int, ChargeDetailAggregate>): List<AcDcBreakdown> {
    val classified = sessions.mapNotNull { s -> aggregatesByChargeId[s.chargeId]?.isFastCharger?.let { s to it } }
    return classified.groupBy { it.second }.map { (isFast, pairs) ->
        val summaries = pairs.map { it.first }
        val coherent = summaries.filter { it.isCoherentGridSession() }
        val gridEnergy = coherent.sumOf { it.energyUsed!! }.takeIf { coherent.isNotEmpty() }
        val weighted = if (gridEnergy != null && gridEnergy > 0.0) coherent.sumOf { it.energyAdded } / gridEnergy * 100.0 else null
        val powers = summaries.mapNotNull { aggregatesByChargeId[it.chargeId]?.maxChargerPower }.sorted()
        val median = powers.takeIf { it.isNotEmpty() }?.get(powers.size / 2)
        AcDcBreakdown(isFast, summaries.size, summaries.sumOf { it.energyAdded }, gridEnergy, weighted, median)
    }.sortedByDescending { it.sessionCount }
}

// ---- Charging by starting SOC (simple energy/session distribution, no invented behavioral score) ----

enum class StartSocBand(val label: String, val maxExclusive: Int?) {
    Under20("< 20%", 20),
    B20to40("20–40%", 40),
    B40to60("40–60%", 60),
    B60to80("60–80%", 80),
    Over80("80%+", null)
}

fun startSocBandOf(soc: Int): StartSocBand = StartSocBand.entries.first { soc < (it.maxExclusive ?: Int.MAX_VALUE) }

data class SocBandResult(val band: StartSocBand, val sessionCount: Int, val batteryEnergyKwh: Double)

fun chargingByStartSoc(sessions: List<ChargeSummary>): List<SocBandResult> =
    StartSocBand.entries.map { band ->
        val group = sessions.filter { startSocBandOf(it.startBatteryLevel) == band }
        SocBandResult(band, group.size, group.sumOf { it.energyAdded })
    }

// ---- Best / worst sessions — only trustworthy per-session metrics are ranked ----

data class RankedCharge(val summary: ChargeSummary, val metricValue: Double)

fun fastestChargingSessions(sessions: List<ChargeSummary>, limit: Int = 5): List<RankedCharge> =
    sessions.filter { it.durationMin > 0 && it.energyAdded > 0.0 }
        .map { RankedCharge(it, it.energyAdded / (it.durationMin / 60.0)) }
        .sortedByDescending { it.metricValue }.take(limit)

fun mostEfficientChargingSessions(sessions: List<ChargeSummary>, limit: Int = 5): List<RankedCharge> =
    sessions.mapNotNull { s ->
        val used = s.energyUsed?.takeIf { it > 0.0 && it >= s.energyAdded && s.energyAdded > 0.0 } ?: return@mapNotNull null
        RankedCharge(s, s.energyAdded / used * 100.0)
    }.sortedByDescending { it.metricValue }.take(limit)

fun largestEnergyAddedSessions(sessions: List<ChargeSummary>, limit: Int = 5): List<RankedCharge> =
    sessions.filter { it.energyAdded > 0.0 }.map { RankedCharge(it, it.energyAdded) }.sortedByDescending { it.metricValue }.take(limit)

fun longestChargingSessions(sessions: List<ChargeSummary>, limit: Int = 5): List<RankedCharge> =
    sessions.filter { it.durationMin > 0 }.map { RankedCharge(it, it.durationMin.toDouble()) }.sortedByDescending { it.metricValue }.take(limit)

// ---- Trend: daily for 7d/30d, weekly for 90d, monthly for All ----

data class ChargingLabTrendPoint(val label: String, val sessionCount: Int, val batteryEnergyKwh: Double, val weightedEfficiencyPercent: Double?)

private val trendDayFormatter = DateTimeFormatter.ofPattern("MMM d")

private fun List<ChargeSummary>.toChargingLabTrendPoint(label: String): ChargingLabTrendPoint {
    val coherent = filter { it.isCoherentGridSession() }
    val gridEnergy = coherent.sumOf { it.energyUsed!! }.takeIf { coherent.isNotEmpty() }
    val weighted = if (gridEnergy != null && gridEnergy > 0.0) coherent.sumOf { it.energyAdded } / gridEnergy * 100.0 else null
    return ChargingLabTrendPoint(label, size, sumOf { it.energyAdded }, weighted)
}

fun chargingTrend(sessions: List<ChargeSummary>, period: ChargingPeriod, zone: ZoneId = ZoneId.systemDefault()): List<ChargingLabTrendPoint> =
    when (period) {
        ChargingPeriod.Last7Days, ChargingPeriod.Last30Days ->
            sessions.groupBy { chargeLocalDate(it, zone) }
                .filterKeys { it != null }
                .toSortedMap(compareBy { it })
                .map { (date, group) -> group.toChargingLabTrendPoint(date!!.format(trendDayFormatter)) }
        ChargingPeriod.Last90Days ->
            sessions.groupBy { chargeLocalDate(it, zone)?.with(DayOfWeek.MONDAY) }
                .filterKeys { it != null }
                .toSortedMap(compareBy { it })
                .map { (weekStart, group) -> group.toChargingLabTrendPoint(weekStart!!.format(trendDayFormatter)) }
        ChargingPeriod.AllTime ->
            sessions.groupBy { chargeLocalDate(it, zone)?.let { d -> YearMonth.from(d) } }
                .filterKeys { it != null }
                .toSortedMap(compareBy { it })
                .map { (month, group) -> group.toChargingLabTrendPoint(month!!.atDay(1).let { DateTimeFormatter.ofPattern("MMM yyyy").format(it) }) }
    }

// ---- Charging curve (power vs SOC) and observed taper — computed only from an already-fetched detail ----

data class ChargeCurveSample(val socPercent: Int, val powerKw: Double)
data class ChargeCurveBand(val label: String, val avgPowerKw: Double)
data class ChargeTaperInfo(val peakPowerKw: Double, val peakSoc: Int, val taperStartSoc: Int?)
data class ChargeCurveResult(val bands: List<ChargeCurveBand?>, val taper: ChargeTaperInfo?, val sampleCount: Int)

/** 10 fixed 10%-wide SOC bands, in original sample order for taper detection (chronological). */
fun computeChargeCurve(samples: List<ChargeCurveSample>): ChargeCurveResult? {
    val valid = samples.filter { it.socPercent in 0..100 && it.powerKw > 0.0 }
    if (valid.isEmpty()) return null
    val bands = (0 until 10).map { i ->
        val lower = i * 10
        val upper = lower + 10
        val inBand = valid.filter { it.socPercent >= lower && if (upper == 100) it.socPercent <= upper else it.socPercent < upper }
        if (inBand.isEmpty()) null else ChargeCurveBand("$lower–$upper%", inBand.map { it.powerKw }.average())
    }
    val peakIndex = valid.indices.maxBy { valid[it].powerKw }
    val peak = valid[peakIndex]
    // "Observed taper": the first later sample whose power meaningfully falls below the observed peak.
    val taperStartSoc = valid.drop(peakIndex + 1).firstOrNull { it.powerKw < peak.powerKw * 0.9 }?.socPercent
    return ChargeCurveResult(bands, ChargeTaperInfo(peak.powerKw, peak.socPercent, taperStartSoc), valid.size)
}

const val CHARGE_CURVE_SCHEMA_VERSION = 1

fun ChargeCurveResult.toEntity(chargeId: Int, carId: Int, computedAt: Long): ChargeCurveAggregate = ChargeCurveAggregate(
    chargeId = chargeId,
    carId = carId,
    schemaVersion = CHARGE_CURVE_SCHEMA_VERSION,
    computedAt = computedAt,
    sampleCount = sampleCount,
    peakPowerKw = taper?.peakPowerKw,
    peakPowerSoc = taper?.peakSoc,
    taperStartSoc = taper?.taperStartSoc,
    band0to10 = bands.getOrNull(0)?.avgPowerKw,
    band10to20 = bands.getOrNull(1)?.avgPowerKw,
    band20to30 = bands.getOrNull(2)?.avgPowerKw,
    band30to40 = bands.getOrNull(3)?.avgPowerKw,
    band40to50 = bands.getOrNull(4)?.avgPowerKw,
    band50to60 = bands.getOrNull(5)?.avgPowerKw,
    band60to70 = bands.getOrNull(6)?.avgPowerKw,
    band70to80 = bands.getOrNull(7)?.avgPowerKw,
    band80to90 = bands.getOrNull(8)?.avgPowerKw,
    band90to100 = bands.getOrNull(9)?.avgPowerKw
)

private val curveBandLabels = listOf("0–10%", "10–20%", "20–30%", "30–40%", "40–50%", "50–60%", "60–70%", "70–80%", "80–90%", "90–100%")

fun ChargeCurveAggregate.toBands(): List<ChargeCurveBand?> = listOf(
    band0to10, band10to20, band20to30, band30to40, band40to50, band50to60, band60to70, band70to80, band80to90, band90to100
).mapIndexed { i, power -> power?.let { ChargeCurveBand(curveBandLabels[i], it) } }

fun ChargeCurveAggregate.toTaper(): ChargeTaperInfo? =
    peakPowerKw?.let { power -> peakPowerSoc?.let { soc -> ChargeTaperInfo(power, soc, taperStartSoc) } }

/** A simple cross-session average per SOC band — a descriptive shape, not an energy-weighted ratio. */
fun mergeCurveBands(perSessionBands: List<List<ChargeCurveBand?>>): List<ChargeCurveBand?> =
    (0 until 10).map { i ->
        val values = perSessionBands.mapNotNull { it.getOrNull(i)?.avgPowerKw }
        if (values.isEmpty()) null else ChargeCurveBand(curveBandLabels[i], values.average())
    }
