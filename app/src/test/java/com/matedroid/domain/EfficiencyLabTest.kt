package com.matedroid.domain

import com.matedroid.data.local.entity.DriveSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EfficiencyLabTest {

    private fun drive(
        id: Int,
        start: String,
        distance: Double,
        durationMin: Int = 20,
        energyConsumed: Double? = 5.0,
        outsideTempAvg: Double? = null
    ) = DriveSummary(
        driveId = id, carId = 1, startDate = start, endDate = start, durationMin = durationMin,
        startAddress = "A", endAddress = "B", distance = distance, speedMax = 60, speedAvg = 40,
        powerMax = 100, powerMin = -20, startBatteryLevel = 80, endBatteryLevel = 70,
        outsideTempAvg = outsideTempAvg, insideTempAvg = null, energyConsumed = energyConsumed, efficiency = null
    )

    // ---- Weighted efficiency ----

    @Test fun `weighted efficiency is SUM energy over SUM distance, not an average of per-drive ratios`() {
        // Drive A: 10 mi / 2 kWh = 200 Wh/mi. Drive B: 100 mi / 30 kWh = 300 Wh/mi.
        // A naive average would be 250 Wh/mi; the weighted ratio is 32/110 * 1000 = ~290.9.
        val drives = listOf(
            drive(1, "2026-08-01T09:00:00-04:00", distance = 10.0, energyConsumed = 2.0),
            drive(2, "2026-08-01T10:00:00-04:00", distance = 100.0, energyConsumed = 30.0)
        )
        val summary = computeEfficiencySummary(drives)
        assertEquals(290.909, summary.weightedEfficiency!!, 0.01)
    }

    @Test fun `drives without valid energy are excluded from efficiency but still counted in distance and drive count`() {
        val drives = listOf(
            drive(1, "2026-08-01T09:00:00-04:00", distance = 10.0, energyConsumed = 2.0),
            drive(2, "2026-08-01T10:00:00-04:00", distance = 50.0, energyConsumed = null)
        )
        val summary = computeEfficiencySummary(drives)
        assertEquals(200.0, summary.weightedEfficiency!!, 0.01)
        assertEquals(60.0, summary.totalDistance, 0.01) // both drives' distance counted
        assertEquals(2, summary.driveCount)
        assertEquals(1, summary.validDriveCount)
    }

    @Test fun `no valid drives leaves weighted efficiency unavailable rather than zero`() {
        val summary = computeEfficiencySummary(listOf(drive(1, "2026-08-01T09:00:00-04:00", distance = 10.0, energyConsumed = null)))
        assertNull(summary.weightedEfficiency)
    }

    // ---- Period filtering ----

    @Test fun `period filtering keeps drives within the rolling window inclusive of both ends`() {
        val today = LocalDate.of(2026, 8, 10)
        val drives = listOf(
            drive(1, "2026-08-10T08:00:00-04:00", distance = 1.0), // today
            drive(2, "2026-08-04T08:00:00-04:00", distance = 1.0), // exactly 7 days back
            drive(3, "2026-08-03T08:00:00-04:00", distance = 1.0)  // 8 days back — outside 7d window
        )
        val filtered = filterByPeriod(drives, EfficiencyPeriod.Last7Days, today)
        assertEquals(setOf(1, 2), filtered.map { it.driveId }.toSet())
    }

    @Test fun `AllTime period applies no filtering`() {
        val today = LocalDate.of(2026, 8, 10)
        val drives = listOf(drive(1, "2020-01-01T08:00:00-04:00", distance = 1.0))
        assertEquals(1, filterByPeriod(drives, EfficiencyPeriod.AllTime, today).size)
    }

    // ---- Personal baseline comparison ----

    @Test fun `baseline comparison reports positive percent when period is more efficient than baseline`() {
        val period = EfficiencySummary(weightedEfficiency = 261.0, totalDistance = 300.0, totalEnergyKwh = 78.3, driveCount = 10, validDriveCount = 10)
        val baseline = EfficiencySummary(weightedEfficiency = 268.0, totalDistance = 3000.0, totalEnergyKwh = 804.0, driveCount = 100, validDriveCount = 94)
        val comparison = compareToBaseline(period, baseline)!!
        assertEquals(2.6119, comparison.percentBetter, 0.01)
    }

    @Test fun `baseline comparison is null when either side lacks a valid weighted efficiency`() {
        val unavailable = EfficiencySummary(weightedEfficiency = null, totalDistance = 0.0, totalEnergyKwh = 0.0, driveCount = 0, validDriveCount = 0)
        val available = EfficiencySummary(weightedEfficiency = 250.0, totalDistance = 10.0, totalEnergyKwh = 2.5, driveCount = 1, validDriveCount = 1)
        assertNull(compareToBaseline(unavailable, available))
        assertNull(compareToBaseline(available, unavailable))
    }

    @Test fun `baseline comparison is effectively equal when period exactly matches baseline, but not for a real difference`() {
        val period = EfficiencySummary(weightedEfficiency = 261.5, totalDistance = 300.0, totalEnergyKwh = 78.45, driveCount = 10, validDriveCount = 10)
        val baseline = EfficiencySummary(weightedEfficiency = 261.5, totalDistance = 3000.0, totalEnergyKwh = 784.5, driveCount = 100, validDriveCount = 94)
        assertTrue(compareToBaseline(period, baseline)!!.isEffectivelyBaseline)

        val meaningfullyDifferent = EfficiencySummary(weightedEfficiency = 268.0, totalDistance = 300.0, totalEnergyKwh = 80.4, driveCount = 10, validDriveCount = 10)
        assertFalse(compareToBaseline(meaningfullyDifferent, baseline)!!.isEffectivelyBaseline)
    }

    // ---- Speed-band weighted aggregation ----

    @Test fun `speed bands weight by SUM energy over SUM distance within each band, not per-drive average`() {
        // Both drives average 40 mph (25-45 band): 20mi/0.5h and 40mi/1h.
        val drives = listOf(
            drive(1, "2026-08-01T09:00:00-04:00", distance = 20.0, durationMin = 30, energyConsumed = 4.0),  // 200 Wh/mi
            drive(2, "2026-08-01T11:00:00-04:00", distance = 40.0, durationMin = 60, energyConsumed = 12.0)  // 300 Wh/mi
        )
        val bands = efficiencyBySpeedBand(drives)
        val midBand = bands.first { it.band == SpeedBand.B25to45 }
        // Weighted: (4+12)/(20+40)*1000 = 266.67, not the naive average of 250.
        assertEquals(266.667, midBand.summary.weightedEfficiency!!, 0.01)
        assertEquals(2, midBand.summary.validDriveCount)
        bands.filter { it.band != SpeedBand.B25to45 }.forEach {
            assertNull(it.summary.weightedEfficiency)
        }
    }

    @Test fun `speed band boundaries place a drive at exactly the boundary in the lower band`() {
        // Exactly 25 mph in 1 hour -> 25 mi. Boundary is "< 25" exclusive, so 25.0 falls in 25-45.
        assertEquals(SpeedBand.B25to45, speedBandOf(25.0))
        assertEquals(SpeedBand.Under25, speedBandOf(24.999))
    }

    // ---- Trip-length weighted aggregation ----

    @Test fun `trip length bands weight by SUM energy over SUM distance within each band`() {
        val drives = listOf(
            drive(1, "2026-08-01T09:00:00-04:00", distance = 2.0, energyConsumed = 1.0),   // 500 Wh/mi, 0-5 band
            drive(2, "2026-08-01T10:00:00-04:00", distance = 3.0, energyConsumed = 0.6),   // 200 Wh/mi, 0-5 band
            drive(3, "2026-08-01T11:00:00-04:00", distance = 50.0, energyConsumed = 12.5)  // 250 Wh/mi, 40+ band
        )
        val bands = efficiencyByTripLength(drives)
        val shortBand = bands.first { it.band == TripLengthBand.B0to5 }
        assertEquals(320.0, shortBand.summary.weightedEfficiency!!, 0.01) // (1+0.6)/(2+3)*1000
        val longBand = bands.first { it.band == TripLengthBand.Over40 }
        assertEquals(250.0, longBand.summary.weightedEfficiency!!, 0.01)
    }

    @Test fun `short drives remain valid and are never excluded from trip-length bands`() {
        val drives = listOf(drive(1, "2026-08-01T09:00:00-04:00", distance = 0.8, energyConsumed = 0.3))
        val bands = efficiencyByTripLength(drives)
        val shortBand = bands.first { it.band == TripLengthBand.B0to5 }
        assertTrue(shortBand.summary.validDriveCount == 1)
    }

    // ---- Best/worst drives ----

    @Test fun `best and worst drives are ranked by per-drive efficiency and respect the minimum-distance display filter`() {
        val drives = listOf(
            drive(1, "2026-08-01T09:00:00-04:00", distance = 1.0, energyConsumed = 0.1),  // 100 Wh/mi but under minDistance
            drive(2, "2026-08-01T10:00:00-04:00", distance = 20.0, energyConsumed = 4.0), // 200 Wh/mi
            drive(3, "2026-08-01T11:00:00-04:00", distance = 20.0, energyConsumed = 8.0)  // 400 Wh/mi
        )
        val (best, worst) = bestWorstDrives(drives, minDistance = 10.0)
        assertEquals(listOf(2, 3), best.map { it.summary.driveId })
        assertEquals(listOf(3, 2), worst.map { it.summary.driveId })
    }
}
