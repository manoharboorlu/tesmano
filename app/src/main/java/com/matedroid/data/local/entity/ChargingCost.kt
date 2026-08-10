package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local, effective-dated charging price. Price is currency micro-units per kWh. */
@Entity(tableName = "charging_rate_rules", indices = [Index(value = ["smartPlaceId"]), Index(value = ["scope", "enabled"])])
data class ChargingRateRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val smartPlaceId: Long? = null,
    val name: String,
    val priceMicrosPerKwh: Long = 0,
    val currencyCode: String,
    val freeCharging: Boolean = false,
    val enabled: Boolean = true,
    val effectiveFrom: Long = 0,
    val createdAt: Long,
    val updatedAt: Long
)

object ChargingRateScope { const val PLACE = "PLACE"; const val DEFAULT = "DEFAULT" }

/** Manual intent only; automatic estimates remain derived from rate rules and telemetry. */
@Entity(tableName = "charge_cost_overrides")
data class ChargeCostOverride(
    @PrimaryKey val chargeId: Int,
    val mode: String,
    val costMinorUnits: Long? = null,
    val currencyCode: String,
    val updatedAt: Long
)

object ChargeCostOverrideMode { const val COST = "COST"; const val FREE = "FREE" }
