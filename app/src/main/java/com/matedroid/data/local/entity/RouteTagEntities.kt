package com.matedroid.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A local user-defined tag. System tags, such as COMMUTE, remain derived. */
@Entity(tableName = "user_drive_tags", indices = [Index(value = ["name"], unique = true)])
data class UserDriveTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

/** Many manual tags may be attached to one drive. This never overwrites a derived system tag. */
@Entity(tableName = "drive_manual_tags", primaryKeys = ["driveId", "tagId"], indices = [Index(value = ["tagId"])])
data class DriveManualTag(
    val driveId: Int,
    val tagId: Long,
    val createdAt: Long
)

/**
 * User intent for a derived route. Route observations stay derived from current places/endpoints;
 * only names, confirmation and dismissals are persisted locally.
 */
@Entity(tableName = "recurring_route_metadata")
data class RecurringRouteMetadata(
    @PrimaryKey val routeKey: String,
    val name: String? = null,
    val confirmed: Boolean = false,
    val dismissed: Boolean = false,
    val enabled: Boolean = true,
    val originLatitude: Double?,
    val originLongitude: Double?,
    val destinationLatitude: Double?,
    val destinationLongitude: Double?,
    val createdAt: Long,
    val updatedAt: Long
)
