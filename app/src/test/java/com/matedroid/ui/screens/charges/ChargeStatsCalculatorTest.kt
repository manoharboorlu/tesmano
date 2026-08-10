package com.matedroid.ui.screens.charges

import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.ChargePoint
import com.matedroid.data.api.models.ChargerDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeStatsCalculatorTest {

    @Test
    fun `does not fabricate energy used or efficiency when TeslaMate omits wall energy`() {
        val stats = ChargeStatsCalculator.calculateStats(
            ChargeDetail(chargeId = 1, chargeEnergyAdded = 12.4, chargeEnergyUsed = null)
        )

        assertNull(stats.energyUsed)
        assertNull(stats.efficiency)
    }

    @Test
    fun `classifies only explicit charger telemetry`() {
        val dc = ChargeDetail(
            chargeId = 1,
            chargePoints = listOf(ChargePoint(chargerDetails = ChargerDetails(fastChargerPresent = true)))
        )
        val ac = ChargeDetail(
            chargeId = 2,
            chargePoints = listOf(ChargePoint(chargerDetails = ChargerDetails(chargerPhases = 3)))
        )
        val incomplete = ChargeDetail(chargeId = 3, chargePoints = listOf(ChargePoint()))

        assertEquals(true, ChargeStatsCalculator.detectDcCharge(dc))
        assertEquals(false, ChargeStatsCalculator.detectDcCharge(ac))
        assertNull(ChargeStatsCalculator.detectDcCharge(incomplete))
    }
}
