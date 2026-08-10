package com.matedroid.domain

import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.local.entity.ChargeSummary
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

/** The common local input for both a detail screen and cached charge-summary analytics. */
data class ChargeCostInput(
    val chargeId: Int,
    val startDate: String?,
    val latitude: Double?,
    val longitude: Double?,
    val energyAdded: Double?,
    val energyUsed: Double?
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

    fun presentation(
        input: ChargeCostInput,
        rules: List<ChargingRateRule>,
        places: List<SmartPlace>,
        override: ChargeCostOverride?
    ): ChargeCostPresentation {
        val basis = energyBasis(input.energyAdded, input.energyUsed)
        val point = input.latitude?.let { lat -> input.longitude?.let { lon -> GeoPoint(lat, lon) } }
        val place = SmartPlaceMatcher.match(point, places)
        // Override pricing remains authoritative, while current place matching still supplies useful provenance.
        if (override != null) return override.toPresentation(basis, place)
        val sessionTime = input.startDate?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(Long.MAX_VALUE) }
            ?: Long.MAX_VALUE
        val rule = ChargingRateResolver.resolve(rules, place?.id, sessionTime)
        if (rule == null) return ChargeCostPresentation(null, "USD", place = place, energyBasis = basis)
        // An explicit free source proves zero cost even when telemetry is unavailable.
        if (rule.freeCharging) return ChargeCostPresentation(0, rule.currencyCode, basis, rule, place, isFree = true)
        if (basis == null) return ChargeCostPresentation(null, rule.currencyCode, rate = rule, place = place)
        return ChargeCostPresentation(estimatedMinorUnits(basis, rule), rule.currencyCode, basis, rule, place)
    }

    private fun ChargeCostOverride.toPresentation(basis: ChargeCostEnergyBasis?, place: SmartPlace?) = when (mode) {
        ChargeCostOverrideMode.FREE -> ChargeCostPresentation(0, currencyCode, basis, place = place, isManual = true, isFree = true)
        else -> ChargeCostPresentation(costMinorUnits, currencyCode, basis, place = place, isManual = true)
    }
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

    fun observeOverrides(): Flow<List<ChargeCostOverride>> = costDao.observeOverrides()

    suspend fun costForCharge(detail: ChargeDetail): ChargeCostPresentation {
        return costForInput(
            ChargeCostInput(detail.chargeId, detail.startDate, detail.latitude, detail.longitude, detail.chargeEnergyAdded, detail.chargeEnergyUsed)
        )
    }

    suspend fun costForCharge(summary: ChargeSummary): ChargeCostPresentation = costForInput(summary.toCostInput())

    suspend fun costForInput(input: ChargeCostInput): ChargeCostPresentation = ChargeCostEngine.presentation(
        input = input,
        rules = costDao.activeRules(),
        places = placesDao.activePlaces(),
        override = costDao.overrideForCharge(input.chargeId)
    )

    suspend fun saveRate(rule: ChargingRateRule): Long {
        val now = System.currentTimeMillis()
        return costDao.saveRule(rule.copy(createdAt = if (rule.id == 0L) now else rule.createdAt, updatedAt = now))
    }

    suspend fun setManualCost(chargeId: Int, minor: Long, currencyCode: String) =
        costDao.saveOverride(ChargeCostOverride(chargeId, ChargeCostOverrideMode.COST, minor, currencyCode, System.currentTimeMillis()))
    suspend fun setManualFree(chargeId: Int, currencyCode: String) =
        costDao.saveOverride(ChargeCostOverride(chargeId, ChargeCostOverrideMode.FREE, null, currencyCode, System.currentTimeMillis()))
    suspend fun useAutomatic(chargeId: Int) = costDao.deleteOverride(chargeId)

}

fun ChargeSummary.toCostInput() = ChargeCostInput(
    chargeId = chargeId,
    startDate = startDate,
    latitude = latitude,
    longitude = longitude,
    energyAdded = energyAdded,
    energyUsed = energyUsed
)
