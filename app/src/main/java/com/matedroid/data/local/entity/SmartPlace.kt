package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-owned, local geofence. No location data is sent to the backend. */
@Entity(
    tableName = "smart_places",
    indices = [Index(value = ["type", "enabled"])]
)
data class SmartPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 300,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    /** Optional display-only address captured from an already-recorded drive endpoint. */
    val address: String? = null
)

object SmartPlaceType {
    const val HOME = "HOME"
    const val WORK = "WORK"
    const val CUSTOM = "CUSTOM"
    val all = listOf(HOME, WORK, CUSTOM)
}
