package com.matedroid.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.matedroid.data.local.entity.DriveEndpointCache
import com.matedroid.data.local.entity.DriveTagOverride
import com.matedroid.data.local.entity.SmartPlace
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartPlacesDao {
    @Query("SELECT * FROM smart_places ORDER BY enabled DESC, type ASC, name COLLATE NOCASE ASC")
    fun observePlaces(): Flow<List<SmartPlace>>

    @Query("SELECT * FROM smart_places WHERE enabled = 1")
    suspend fun activePlaces(): List<SmartPlace>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: SmartPlace): Long

    @Update
    suspend fun updatePlace(place: SmartPlace)

    @Delete
    suspend fun deletePlace(place: SmartPlace)

    @Query("UPDATE smart_places SET enabled = 0, updatedAt = :updatedAt WHERE type = :type AND enabled = 1 AND id != :exceptId")
    suspend fun disableOtherActiveOfType(type: String, exceptId: Long, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEndpoint(endpoint: DriveEndpointCache)

    @Query("SELECT * FROM drive_endpoint_cache WHERE driveId = :driveId")
    suspend fun endpointForDrive(driveId: Int): DriveEndpointCache?

    @Query("SELECT * FROM drive_endpoint_cache WHERE driveId IN (:driveIds)")
    suspend fun endpointsForDrives(driveIds: List<Int>): List<DriveEndpointCache>

    @Query("SELECT * FROM drive_endpoint_cache WHERE carId = :carId")
    suspend fun endpointsForCar(carId: Int): List<DriveEndpointCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: DriveTagOverride)

    @Query("SELECT * FROM drive_tag_overrides WHERE driveId = :driveId AND tag = :tag")
    suspend fun overrideForTag(driveId: Int, tag: String): DriveTagOverride?

    @Query("SELECT * FROM drive_tag_overrides WHERE driveId IN (:driveIds) AND tag = :tag")
    suspend fun overridesForTag(driveIds: List<Int>, tag: String): List<DriveTagOverride>

    @Query("DELETE FROM drive_tag_overrides WHERE driveId = :driveId AND tag = :tag")
    suspend fun deleteOverride(driveId: Int, tag: String)
}
