package com.matedroid.ui.screens.activity

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTimelineViewModelTest {
    @Test
    fun `timeline ordering uses absolute instants when offsets differ`() {
        val laterLocalButEarlierInstant = drive(startDate = "2026-08-09T10:00:00+02:00")
        val earlierLocalButLaterInstant = charge(startDate = "2026-08-09T09:30:00Z")

        assertTrue(
            ActivityEntry.Charge(earlierLocalButLaterInstant).timelineEpochMillis(ZoneId.of("America/New_York")) >
                ActivityEntry.Drive(laterLocalButEarlierInstant).timelineEpochMillis(ZoneId.of("America/New_York"))
        )
    }

    @Test
    fun `entries retain their stable summary identifiers`() {
        assertEquals(7, ActivityEntry.Drive(drive(id = 7)).id)
        assertEquals(11, ActivityEntry.Charge(charge(id = 11)).id)
    }

    private fun drive(id: Int = 1, startDate: String = "2026-08-09T09:00:00Z") = DriveSummary(
        driveId = id, carId = 1, startDate = startDate, endDate = startDate, durationMin = 20,
        startAddress = "Start", endAddress = "End", distance = 10.0, speedMax = 80, speedAvg = 40,
        powerMax = 100, powerMin = -20, startBatteryLevel = 80, endBatteryLevel = 75,
        outsideTempAvg = null, insideTempAvg = null, energyConsumed = 2.0, efficiency = 200.0
    )

    private fun charge(id: Int = 2, startDate: String = "2026-08-09T09:30:00Z") = ChargeSummary(
        chargeId = id, carId = 1, startDate = startDate, endDate = startDate, durationMin = 30,
        address = "Home", latitude = 0.0, longitude = 0.0, energyAdded = 5.0, energyUsed = null,
        cost = null, startBatteryLevel = 70, endBatteryLevel = 80, outsideTempAvg = null, odometer = 1.0
    )
}
