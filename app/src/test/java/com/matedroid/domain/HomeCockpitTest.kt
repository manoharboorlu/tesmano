package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HomeCockpitTest {

    private fun drive(id: Int, start: String, distance: Double, energyConsumed: Double? = 2.0) = DriveSummary(
        driveId = id, carId = 1, startDate = start, endDate = start, durationMin = 20,
        startAddress = "A", endAddress = "B", distance = distance, speedMax = 60, speedAvg = 40,
        powerMax = 100, powerMin = -20, startBatteryLevel = 80, endBatteryLevel = 70,
        outsideTempAvg = null, insideTempAvg = null, energyConsumed = energyConsumed, efficiency = null
    )

    private fun charge(id: Int, start: String, energyAdded: Double) = ChargeSummary(
        chargeId = id, carId = 1, startDate = start, endDate = start, durationMin = 60,
        address = "Home", latitude = 0.0, longitude = 0.0,
        energyAdded = energyAdded, energyUsed = null, cost = null,
        startBatteryLevel = 20, endBatteryLevel = 80, outsideTempAvg = null, odometer = 0.0
    )

    @Test fun `today summary includes only today's drives and charges, weighted honestly`() {
        val today = LocalDate.of(2026, 8, 10)
        val drives = listOf(
            drive(1, "2026-08-10T08:00:00-04:00", distance = 10.0, energyConsumed = 3.0),
            drive(2, "2026-08-10T18:00:00-04:00", distance = 5.0, energyConsumed = 1.5),
            drive(3, "2026-08-09T18:00:00-04:00", distance = 100.0, energyConsumed = 30.0) // yesterday, excluded
        )
        val charges = listOf(
            charge(1, "2026-08-10T09:00:00-04:00", energyAdded = 12.0),
            charge(2, "2026-08-01T09:00:00-04:00", energyAdded = 40.0) // earlier this month, excluded
        )
        val summary = computeTodaySummary(drives, charges, today)
        assertEquals(2, summary.driveCount)
        assertEquals(15.0, summary.milesDriven, 0.001)
        assertEquals(4.5, summary.driveEnergyKwh, 0.001)
        assertEquals(1, summary.chargeSessionCount)
        assertEquals(12.0, summary.chargeEnergyKwh!!, 0.001)
    }

    @Test fun `today summary reports no charge energy rather than zero when nothing charged today`() {
        val today = LocalDate.of(2026, 8, 10)
        val drives = listOf(drive(1, "2026-08-10T08:00:00-04:00", distance = 10.0))
        val summary = computeTodaySummary(drives, emptyList(), today)
        assertNull(summary.chargeEnergyKwh)
        assertEquals(0, summary.chargeSessionCount)
    }

    @Test fun `today summary is all-zero-and-null when nothing happened today`() {
        val today = LocalDate.of(2026, 8, 10)
        val summary = computeTodaySummary(emptyList(), emptyList(), today)
        assertEquals(0, summary.driveCount)
        assertEquals(0.0, summary.milesDriven, 0.001)
        assertEquals(0.0, summary.driveEnergyKwh, 0.001)
        assertNull(summary.chargeEnergyKwh)
    }
}
