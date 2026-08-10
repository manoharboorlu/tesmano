package com.matedroid.data.local.entity

import androidx.room.Entity

/**
 * Local user intent for one tag.  The composite key deliberately supports many tags per drive;
 * automatic tags are derived from places and rules and are never overwritten here.
 */
@Entity(tableName = "drive_tag_overrides", primaryKeys = ["driveId", "tag"])
data class DriveTagOverride(
    val driveId: Int,
    val tag: String,
    val action: String,
    val updatedAt: Long
)

object DriveTag {
    const val COMMUTE = "COMMUTE"
}

object DriveTagOverrideAction {
    const val ASSIGN = "ASSIGN"
    const val REMOVE = "REMOVE"
}
