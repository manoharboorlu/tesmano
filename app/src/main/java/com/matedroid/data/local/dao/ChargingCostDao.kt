package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.ChargeCostOverride
import com.matedroid.data.local.entity.ChargingRateRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingCostDao {
    @Query("SELECT * FROM charging_rate_rules WHERE enabled = 1 ORDER BY effectiveFrom DESC, id DESC")
    suspend fun activeRules(): List<ChargingRateRule>

    @Query("SELECT * FROM charging_rate_rules ORDER BY scope, smartPlaceId, effectiveFrom DESC, id DESC")
    fun observeRules(): Flow<List<ChargingRateRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(rule: ChargingRateRule): Long

    @Query("UPDATE charging_rate_rules SET enabled = 0, updatedAt = :updatedAt WHERE scope = :scope AND smartPlaceId IS :smartPlaceId AND enabled = 1")
    suspend fun disableCurrent(scope: String, smartPlaceId: Long?, updatedAt: Long)

    @Query("SELECT * FROM charge_cost_overrides WHERE chargeId = :chargeId")
    suspend fun overrideForCharge(chargeId: Int): ChargeCostOverride?

    @Query("SELECT * FROM charge_cost_overrides")
    fun observeOverrides(): Flow<List<ChargeCostOverride>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOverride(override: ChargeCostOverride)

    @Query("DELETE FROM charge_cost_overrides WHERE chargeId = :chargeId")
    suspend fun deleteOverride(chargeId: Int)
}
