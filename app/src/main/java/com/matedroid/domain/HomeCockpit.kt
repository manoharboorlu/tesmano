package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import java.time.LocalDate
import java.time.ZoneId

/**
 * Compact "today" totals for the unfolded Home cockpit. Built entirely from cached summaries
 * already loaded for other Home cards (efficiency baseline, battery estimate) — no separate fetch.
 */
data class TodaySummary(
    val driveCount: Int,
    val milesDriven: Double,
    val driveEnergyKwh: Double,
    val chargeSessionCount: Int,
    /** Null when no charging session started today. */
    val chargeEnergyKwh: Double?
)

fun computeTodaySummary(
    drives: List<DriveSummary>,
    charges: List<ChargeSummary>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): TodaySummary {
    val todaysDrives = drives.filter { driveLocalDate(it, zone) == today }
    val todaysCharges = charges.filter { chargeLocalDate(it, zone) == today }
    return TodaySummary(
        driveCount = todaysDrives.size,
        milesDriven = todaysDrives.sumOf { it.distance },
        driveEnergyKwh = todaysDrives.sumOf { it.energyConsumed ?: 0.0 },
        chargeSessionCount = todaysCharges.size,
        chargeEnergyKwh = todaysCharges.takeIf { it.isNotEmpty() }?.sumOf { it.energyAdded }
    )
}
