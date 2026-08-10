package com.matedroid.domain

import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.local.dao.ChargingCostDao
import com.matedroid.data.local.dao.SmartPlacesDao
import com.matedroid.data.local.entity.ChargeCostOverride
import com.matedroid.data.local.entity.ChargeCostOverrideMode
import com.matedroid.data.local.entity.ChargingRateRule
import com.matedroid.data.local.entity.ChargingRateScope
import com.matedroid.data.local.entity.SmartPlace
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ChargeCostEnergyBasis {
    val kwh: BigDecimal
    data class GridEnergyReported(override val kwh: BigDecimal) : ChargeCostEnergyBasis
    data class BatteryEnergyAdded(override val kwh: BigDecimal) : ChargeCostEnergyBasis
}

data class ChargeCostPresentation(
    val costMinorUnits: Long?,
    val currencyCode: String,
    val energyBasis: ChargeCostEnergyBasis? = null,
    val rate: ChargingRateRule? = null,
    val place: SmartPlace? = null,
    val isManual: Boolean = false,
    val isFree: Boolean = false
)

object ChargeCostEngine {
    private val MICRO = BigDecimal(1_000_000)

    /** `charge_energy_used` is used only when it is a coherent wall/grid value. */
    fun energyBasis(energyAdded: Double?, energyUsed: Double?): ChargeCostEnergyBasis? {
        val added = energyAdded?.takeIf { it > 0.0 && it.isFinite() }?.let(::BigDecimal) ?: return null
        val used = energyUsed?.takeIf { it > 0.0 && it.isFinite() }?.let(::BigDecimal)
        return if (used != null && used >= added) ChargeCostEnergyBasis.GridEnergyReported(used)
        else ChargeCostEnergyBasis.BatteryEnergyAdded(added)
    }

    fun estimatedMinorUnits(basis: ChargeCostEnergyBasis, rule: ChargingRateRule): Long =
        basis.kwh.multiply(BigDecimal(rule.priceMicrosPerKwh))
            .multiply(BigDecimal(100))
            .divide(MICRO, 0, RoundingMode.HALF_UP)
            .longValueExact()
}

object ChargingRateResolver {
    fun resolve(rules: List<ChargingRateRule>, placeId: Long?, timestamp: Long): ChargingRateRule? =
        rules.asSequence().filter { it.enabled && it.effectiveFrom <= timestamp }
            .sortedWith(compareByDescending<ChargingRateRule> { it.effectiveFrom }.thenByDescending { it.id })
            .firstOrNull { it.scope == ChargingRateScope.PLACE && it.smartPlaceId == placeId }
            ?: rules.asSequence().filter { it.enabled && it.effectiveFrom <= timestamp }
                .sortedWith(compareByDescending<ChargingRateRule> { it.effectiveFrom }.thenByDescending { it.id })
                .firstOrNull { it.scope == ChargingRateScope.DEFAULT }
}

@Singleton
class ChargingCostRepository @Inject constructor(
    private val costDao: ChargingCostDao,
    private val placesDao: SmartPlacesDao
) {
    fun observeRules(): Flow<List<ChargingRateRule>> = costDao.observeRules()

    suspend fun costForCharge(detail: ChargeDetail): ChargeCostPresentation {
        val override = costDao.overrideForCharge(detail.chargeId)
        if (override != null) return override.toPresentation()
        val point = detail.latitude?.let { lat -> detail.longitude?.let { lon -> GeoPoint(lat, lon) } }
        val place = SmartPlaceMatcher.match(point, placesDao.activePlaces())
        val sessionTime = detail.startDate?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(Long.MAX_VALUE) } ?: Long.MAX_VALUE
        val rule = resolveRule(place?.id, sessionTime)
        if (rule == null) return ChargeCostPresentation(null, "USD", place = place)
        if (rule.freeCharging) return ChargeCostPresentation(0, rule.currencyCode, rate = rule, place = place, isFree = true)
        val basis = ChargeCostEngine.energyBasis(detail.chargeEnergyAdded, detail.chargeEnergyUsed)
            ?: return ChargeCostPresentation(null, rule.currencyCode, rate = rule, place = place)
        return ChargeCostPresentation(ChargeCostEngine.estimatedMinorUnits(basis, rule), rule.currencyCode, basis, rule, place)
    }

    private suspend fun resolveRule(placeId: Long?, timestamp: Long): ChargingRateRule? {
        return ChargingRateResolver.resolve(costDao.activeRules(), placeId, timestamp)
    }

    suspend fun saveRate(rule: ChargingRateRule): Long {
        val now = System.currentTimeMillis()
        return costDao.saveRule(rule.copy(createdAt = if (rule.id == 0L) now else rule.createdAt, updatedAt = now))
    }

    suspend fun setManualCost(chargeId: Int, minor: Long, currencyCode: String) =
        costDao.saveOverride(ChargeCostOverride(chargeId, ChargeCostOverrideMode.COST, minor, currencyCode, System.currentTimeMillis()))
    suspend fun setManualFree(chargeId: Int, currencyCode: String) =
        costDao.saveOverride(ChargeCostOverride(chargeId, ChargeCostOverrideMode.FREE, null, currencyCode, System.currentTimeMillis()))
    suspend fun useAutomatic(chargeId: Int) = costDao.deleteOverride(chargeId)

    private fun ChargeCostOverride.toPresentation() = when (mode) {
        ChargeCostOverrideMode.FREE -> ChargeCostPresentation(0, currencyCode, isManual = true, isFree = true)
        else -> ChargeCostPresentation(costMinorUnits, currencyCode, isManual = true)
    }
}
