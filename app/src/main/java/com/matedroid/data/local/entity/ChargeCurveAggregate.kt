package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Compact power-vs-SOC curve summary computed from an already-fetched Charge Detail response
 * (either the existing bulk charge-detail sync, or a user opening Charge Detail). Never triggers
 * a new network fetch on its own — see [com.matedroid.domain.computeChargeCurve].
 */
@Entity(
    tableName = "charge_curve_aggregates",
    foreignKeys = [
        ForeignKey(
            entity = ChargeSummary::class,
            parentColumns = ["chargeId"],
            childColumns = ["chargeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["carId"])]
)
data class ChargeCurveAggregate(
    @PrimaryKey
    val chargeId: Int,
    val carId: Int,
    val schemaVersion: Int,
    val computedAt: Long,

    val sampleCount: Int,
    val peakPowerKw: Double?,
    val peakPowerSoc: Int?,
    /** First later SOC where power falls meaningfully (< 90%) below the observed peak. Null if never observed. */
    val taperStartSoc: Int?,

    // Average charger power (kW) within each fixed 10%-wide SOC band; null when no sample fell in that band.
    val band0to10: Double?,
    val band10to20: Double?,
    val band20to30: Double?,
    val band30to40: Double?,
    val band40to50: Double?,
    val band50to60: Double?,
    val band60to70: Double?,
    val band70to80: Double?,
    val band80to90: Double?,
    val band90to100: Double?
)
