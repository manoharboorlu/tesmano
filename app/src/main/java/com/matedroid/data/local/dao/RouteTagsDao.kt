package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matedroid.data.local.entity.DriveManualTag
import com.matedroid.data.local.entity.RecurringRouteMetadata
import com.matedroid.data.local.entity.UserDriveTag
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteTagsDao {
    @Query("SELECT * FROM user_drive_tags WHERE enabled = 1 ORDER BY name COLLATE NOCASE")
    fun observeEnabledTags(): Flow<List<UserDriveTag>>

    @Query("SELECT * FROM user_drive_tags WHERE enabled = 1 ORDER BY name COLLATE NOCASE")
    suspend fun enabledTags(): List<UserDriveTag>

    @Query("SELECT * FROM user_drive_tags WHERE lower(name) = lower(:name) LIMIT 1")
    suspend fun tagNamed(name: String): UserDriveTag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: UserDriveTag): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addManualTag(assignment: DriveManualTag)

    @Query("DELETE FROM drive_manual_tags WHERE driveId = :driveId AND tagId = :tagId")
    suspend fun removeManualTag(driveId: Int, tagId: Long)

    @Query("SELECT * FROM drive_manual_tags WHERE driveId IN (:driveIds)")
    suspend fun manualTagsForDrives(driveIds: List<Int>): List<DriveManualTag>

    @Query("SELECT * FROM drive_manual_tags WHERE driveId = :driveId")
    suspend fun manualTagsForDrive(driveId: Int): List<DriveManualTag>

    @Query("SELECT * FROM recurring_route_metadata")
    suspend fun routeMetadata(): List<RecurringRouteMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRouteMetadata(metadata: RecurringRouteMetadata)

    @Query("DELETE FROM recurring_route_metadata WHERE routeKey = :routeKey")
    suspend fun deleteRouteMetadata(routeKey: String)
}
