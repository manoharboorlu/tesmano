package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The first and last recorded coordinates from a detail response already opened by the user.
 * This intentionally stores no route samples and never causes background detail downloads.
 */
@Entity(tableName = "drive_endpoint_cache", indices = [Index(value = ["carId"])])
data class DriveEndpointCache(
    @PrimaryKey val driveId: Int,
    val carId: Int,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val capturedAt: Long
)
