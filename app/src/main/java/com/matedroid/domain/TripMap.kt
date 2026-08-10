package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** A single row in a day's chronological Home → ... → Home journey. */
sealed interface TripEntry {
    val startDate: String

    data class Drive(val summary: DriveSummary) : TripEntry {
        override val startDate get() = summary.startDate
    }

    data class Charge(val summary: ChargeSummary) : TripEntry {
        override val startDate get() = summary.startDate
    }
}

/**
 * Chronological (earliest first) merge of a day's drives and charges — the journey order.
 *
 * Sorts by the true instant (honoring whatever offset the stored timestamp carries), not the
 * offset-discarded naive wall-clock time — the backend's configured timezone offset is not
 * necessarily the device's, so a naive-time sort can silently misorder entries.
 */
fun mergeChronological(drives: List<DriveSummary>, charges: List<ChargeSummary>): List<TripEntry> =
    (drives.map(TripEntry::Drive) + charges.map(TripEntry::Charge))
        .sortedBy { instantOf(it.startDate) }

private fun instantOf(value: String): Instant = try {
    Instant.parse(value)
} catch (_: Exception) {
    try {
        LocalDateTime.parse(value.replace("Z", "")).atZone(ZoneId.systemDefault()).toInstant()
    } catch (_: Exception) {
        Instant.MIN
    }
}

/**
 * Totals for a selected day, computed purely from cached summaries and already-resolved charge
 * costs. Unknown stays unavailable: a field is null only when nothing valid backs it, never
 * fabricated as zero.
 */
data class TripDayTotals(
    val driveCount: Int,
    val chargeCount: Int,
    val totalDistanceKm: Double,
    val driveDurationMin: Int,
    val chargeDurationMin: Int,
    val driveEnergyKwh: Double?,
    val weightedEfficiencyWhKm: Double?,
    val chargeEnergyKwh: Double,
    val chargeCostMinorUnits: Long?,
    val chargeCostCurrency: String?,
    val chargeCostMixedCurrencies: Boolean
)

/**
 * [chargeCosts] must already be resolved (one [ChargeCostPresentation] per charge, keyed by
 * chargeId) via [ChargingCostRepository.costForCharge] — this function never re-derives cost.
 */
fun computeDayTotals(
    drives: List<DriveSummary>,
    charges: List<ChargeSummary>,
    chargeCosts: Map<Int, ChargeCostPresentation>
): TripDayTotals {
    val validEnergyDrives = drives.filter { it.energyConsumed != null }
    val sumValidEnergy = validEnergyDrives.sumOf { it.energyConsumed!! }
    val sumValidDistance = validEnergyDrives.sumOf { it.distance }

    val costs = charges.mapNotNull { chargeCosts[it.chargeId] }
    val pricedCosts = costs.filter { it.costMinorUnits != null }
    val currencies = pricedCosts.map { it.currencyCode }.toSet()
    val mixedCurrencies = currencies.size > 1

    return TripDayTotals(
        driveCount = drives.size,
        chargeCount = charges.size,
        totalDistanceKm = drives.sumOf { it.distance },
        driveDurationMin = drives.sumOf { it.durationMin },
        chargeDurationMin = charges.sumOf { it.durationMin },
        driveEnergyKwh = validEnergyDrives.takeIf { it.isNotEmpty() }?.let { sumValidEnergy },
        weightedEfficiencyWhKm = if (sumValidDistance > 0) sumValidEnergy * 1000.0 / sumValidDistance else null,
        chargeEnergyKwh = charges.sumOf { it.energyAdded },
        chargeCostMinorUnits = if (!mixedCurrencies) pricedCosts.sumOf { it.costMinorUnits ?: 0L }.takeIf { pricedCosts.isNotEmpty() } else null,
        chargeCostCurrency = currencies.singleOrNull(),
        chargeCostMixedCurrencies = mixedCurrencies
    )
}
