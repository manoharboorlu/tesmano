package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeCostOverride
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.ChargingRateRule
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.data.local.entity.SmartPlaceType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A reusable, local-only period model. The end is exclusive. */
sealed class ChargingAnalyticsPeriod(val label: String) {
    data object CurrentMonth : ChargingAnalyticsPeriod("This month")
    data object PreviousMonth : ChargingAnalyticsPeriod("Previous month")
    data object CurrentYear : ChargingAnalyticsPeriod("This year")
    data object AllTime : ChargingAnalyticsPeriod("All time")

    fun range(now: Instant, zone: ZoneId): ClosedDateRange? = when (this) {
        CurrentMonth -> YearMonth.from(now.atZone(zone)).let { ClosedDateRange(it.atDay(1), it.plusMonths(1).atDay(1)) }
        PreviousMonth -> YearMonth.from(now.atZone(zone)).minusMonths(1).let { ClosedDateRange(it.atDay(1), it.plusMonths(1).atDay(1)) }
        CurrentYear -> now.atZone(zone).year.let { ClosedDateRange(LocalDate.of(it, 1, 1), LocalDate.of(it + 1, 1, 1)) }
        AllTime -> null
    }
}

data class ClosedDateRange(val start: LocalDate, val endExclusive: LocalDate)

data class ChargingAnalyticsSession(
    val summary: ChargeSummary,
    val cost: ChargeCostPresentation,
    val localDate: LocalDate,
    val locationName: String,
    val category: String
)

data class ChargingLocationBreakdown(
    val name: String,
    val category: String,
    val sessions: Int,
    val freeSessions: Int,
    val unavailableSessions: Int,
    val costMinorUnits: Long?,
    val currencyCode: String?,
    val costBasisKwh: BigDecimal
)

data class ChargingTrendPoint(
    val label: String,
    val sessions: Int,
    val costMinorUnits: Long?,
    val currencyCode: String?
)

data class ChargingAnalyticsSnapshot(
    val period: ChargingAnalyticsPeriod,
    val sessions: List<ChargingAnalyticsSession>,
    val totalCostMinorUnits: Long?,
    val currencyCode: String?,
    val mixedCurrencies: Boolean,
    val pricedSessions: Int,
    val unavailableSessions: Int,
    val freeSessions: Int,
    val manualSessions: Int,
    val costBasisKwh: BigDecimal,
    val gridKwh: BigDecimal,
    val batteryFallbackKwh: BigDecimal,
    val weightedCostPerKwh: BigDecimal?,
    val spendPerMile: BigDecimal?,
    val driveMiles: Double,
    val breakdown: List<ChargingLocationBreakdown>,
    val trend: List<ChargingTrendPoint>
) {
    val totalSessions get() = sessions.size
    val coveragePercent get() = if (totalSessions == 0) null else pricedSessions * 100 / totalSessions
}

/** Pure aggregation over cached summaries. Cost evaluation always delegates to [ChargeCostEngine]. */
object ChargingCostAnalyticsCalculator {
    private val milesPerKm = BigDecimal("0.621371")

    fun calculate(
        summaries: List<ChargeSummary>,
        rules: List<ChargingRateRule>,
        places: List<SmartPlace>,
        overrides: List<ChargeCostOverride>,
        period: ChargingAnalyticsPeriod,
        driveDistanceKm: Double,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ChargingAnalyticsSnapshot {
        val range = period.range(now, zone)
        val overridesByCharge = overrides.associateBy { it.chargeId }
        val sessions = summaries.asSequence().mapNotNull { summary ->
            val date = parseDate(summary.startDate, zone) ?: return@mapNotNull null
            if (range != null && (date < range.start || date >= range.endExclusive)) return@mapNotNull null
            val cost = ChargeCostEngine.presentation(summary.toCostInput(), rules, places, overridesByCharge[summary.chargeId])
            val place = cost.place
            ChargingAnalyticsSession(
                summary, cost, date,
                place?.name ?: summary.address.takeIf { it.isNotBlank() } ?: "Other",
                categoryFor(place)
            )
        }.toList()
        val priced = sessions.filter { it.cost.costMinorUnits != null }
        val currencies = priced.map { it.cost.currencyCode }.toSet()
        val singleCurrency = currencies.singleOrNull()
        val total = if (singleCurrency != null) priced.sumOf { it.cost.costMinorUnits ?: 0L } else null
        val withEnergy = priced.filter { it.cost.energyBasis != null }
        val basisKwh = withEnergy.fold(BigDecimal.ZERO) { totalKwh, session -> totalKwh + session.cost.energyBasis!!.kwh }
        val gridKwh = withEnergy.filter { it.cost.energyBasis is ChargeCostEnergyBasis.GridEnergyReported }
            .fold(BigDecimal.ZERO) { totalKwh, session -> totalKwh + session.cost.energyBasis!!.kwh }
        val batteryKwh = withEnergy.filter { it.cost.energyBasis is ChargeCostEnergyBasis.BatteryEnergyAdded }
            .fold(BigDecimal.ZERO) { totalKwh, session -> totalKwh + session.cost.energyBasis!!.kwh }
        val average = if (total != null && basisKwh > BigDecimal.ZERO) {
            BigDecimal(total).divide(BigDecimal(100), 8, RoundingMode.HALF_UP).divide(basisKwh, 4, RoundingMode.HALF_UP)
        } else null
        val miles = BigDecimal.valueOf(driveDistanceKm.coerceAtLeast(0.0)).multiply(milesPerKm)
        val perMile = if (total != null && miles > BigDecimal.ZERO) {
            BigDecimal(total).divide(BigDecimal(100), 8, RoundingMode.HALF_UP).divide(miles, 4, RoundingMode.HALF_UP)
        } else null
        return ChargingAnalyticsSnapshot(
            period, sessions, total, singleCurrency, currencies.size > 1,
            priced.size, sessions.size - priced.size, priced.count { it.cost.isFree }, priced.count { it.cost.isManual },
            basisKwh, gridKwh, batteryKwh, average, perMile, miles.toDouble(),
            breakdown(sessions), trend(sessions, period),
        )
    }

    private fun breakdown(sessions: List<ChargingAnalyticsSession>): List<ChargingLocationBreakdown> =
        sessions.groupBy { it.locationName to it.category }.map { (key, group) ->
            val priced = group.filter { it.cost.costMinorUnits != null }
            val currencies = priced.map { it.cost.currencyCode }.toSet()
            ChargingLocationBreakdown(
                key.first, key.second, group.size, priced.count { it.cost.isFree }, group.size - priced.size,
                if (currencies.size == 1) priced.sumOf { it.cost.costMinorUnits ?: 0L } else null,
                currencies.singleOrNull(),
                priced.fold(BigDecimal.ZERO) { sum, session -> sum + (session.cost.energyBasis?.kwh ?: BigDecimal.ZERO) }
            )
        }.sortedWith(compareByDescending<ChargingLocationBreakdown> { it.costMinorUnits ?: Long.MIN_VALUE }.thenByDescending { it.sessions })

    private fun trend(sessions: List<ChargingAnalyticsSession>, period: ChargingAnalyticsPeriod): List<ChargingTrendPoint> {
        val monthly = period != ChargingAnalyticsPeriod.CurrentMonth && period != ChargingAnalyticsPeriod.PreviousMonth
        return sessions.groupBy { if (monthly) YearMonth.from(it.localDate).toString() else it.localDate.toString() }
            .map { (label, group) ->
                val priced = group.filter { it.cost.costMinorUnits != null }
                val currencies = priced.map { it.cost.currencyCode }.toSet()
                ChargingTrendPoint(label, group.size, if (currencies.size == 1) priced.sumOf { it.cost.costMinorUnits ?: 0L } else null, currencies.singleOrNull())
            }.sortedBy { it.label }
    }

    private fun parseDate(value: String, zone: ZoneId): LocalDate? = runCatching { Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull()
    private fun categoryFor(place: SmartPlace?): String = when (place?.type) {
        SmartPlaceType.HOME -> "Home"
        SmartPlaceType.WORK -> "Work"
        SmartPlaceType.CUSTOM -> "Custom"
        else -> "Other"
    }
}
