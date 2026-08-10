package com.matedroid.domain

import com.matedroid.data.local.entity.ChargingRateRule
import com.matedroid.data.local.entity.ChargingRateScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingCostEngineTest {
    private fun rate(scope: String, placeId: Long? = null, micros: Long = 258_000, from: Long = 0, free: Boolean = false) =
        ChargingRateRule(scope = scope, smartPlaceId = placeId, name = "rate", priceMicrosPerKwh = micros, currencyCode = "USD", freeCharging = free, effectiveFrom = from, createdAt = 0, updatedAt = 0)

    @Test fun `place rate wins over default and rate history respects session time`() {
        val old = rate(ChargingRateScope.PLACE, 1, 220_000, from = 0)
        val current = rate(ChargingRateScope.PLACE, 1, 260_000, from = 100)
        val default = rate(ChargingRateScope.DEFAULT, micros = 300_000)
        assertEquals(220_000L, ChargingRateResolver.resolve(listOf(old, current, default), 1, 50)?.priceMicrosPerKwh)
        assertEquals(260_000L, ChargingRateResolver.resolve(listOf(old, current, default), 1, 150)?.priceMicrosPerKwh)
        assertEquals(300_000L, ChargingRateResolver.resolve(listOf(old, current, default), 99, 150)?.priceMicrosPerKwh)
        assertNull(ChargingRateResolver.resolve(emptyList(), 1, 1))
    }

    @Test fun `uses coherent grid energy then battery fallback and exact minor units`() {
        val grid = ChargeCostEngine.energyBasis(16.3, 18.7)!!
        assertEquals("GridEnergyReported", grid::class.simpleName)
        assertEquals(482L, ChargeCostEngine.estimatedMinorUnits(grid, rate(ChargingRateScope.DEFAULT)))
        assertEquals("BatteryEnergyAdded", ChargeCostEngine.energyBasis(16.3, null)!!::class.simpleName)
        assertNull(ChargeCostEngine.energyBasis(-1.0, 2.0))
        assertNull(ChargeCostEngine.energyBasis(0.0, 2.0))
    }

    @Test fun `free rate remains a zero cost rule`() {
        assertEquals(true, rate(ChargingRateScope.DEFAULT, free = true).freeCharging)
    }

    @Test fun `first open-ended rate prices earlier sessions while later changes are forward only`() {
        val first = rate(ChargingRateScope.PLACE, 1, 220_000, from = 0)
        val changed = rate(ChargingRateScope.PLACE, 1, 260_000, from = 1_000)
        assertEquals(220_000L, ChargingRateResolver.resolve(listOf(first, changed), 1, 999)?.priceMicrosPerKwh)
        assertEquals(260_000L, ChargingRateResolver.resolve(listOf(first, changed), 1, 1_000)?.priceMicrosPerKwh)
    }
}
