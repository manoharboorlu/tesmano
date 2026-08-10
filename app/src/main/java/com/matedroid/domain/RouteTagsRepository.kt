package com.matedroid.domain

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.dao.RouteTagsDao
import com.matedroid.data.local.dao.SmartPlacesDao
import com.matedroid.data.local.entity.DriveManualTag
import com.matedroid.data.local.entity.RecurringRouteMetadata
import com.matedroid.data.local.entity.UserDriveTag
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

data class DriveTagSet(val commute: Boolean, val manual: List<UserDriveTag>)

@Singleton
class RouteTagsRepository @Inject constructor(
    private val drives: DriveSummaryDao,
    private val aggregates: AggregateDao,
    private val places: SmartPlacesDao,
    private val dao: RouteTagsDao,
    private val smartPlaces: SmartPlacesRepository
) {
    fun observeEnabledTags(): Flow<List<UserDriveTag>> = dao.observeEnabledTags()
    suspend fun enabledTags(): List<UserDriveTag> = dao.enabledTags()

    suspend fun tagsForDrives(carId: Int, driveIds: List<Int>): Map<Int, DriveTagSet> {
        if (driveIds.isEmpty()) return emptyMap()
        val tagsById = dao.enabledTags().associateBy { it.id }
        val manual = dao.manualTagsForDrives(driveIds).groupBy { it.driveId }
        val contexts = smartPlaces.contextsForDrives(carId, driveIds)
        return driveIds.associateWith { driveId -> DriveTagSet(contexts[driveId]?.commute == true, manual[driveId].orEmpty().mapNotNull { tagsById[it.tagId] }) }
    }

    suspend fun tagsForDrive(carId: Int, driveId: Int): DriveTagSet = tagsForDrives(carId, listOf(driveId))[driveId] ?: DriveTagSet(false, emptyList())

    suspend fun createTag(name: String): UserDriveTag {
        val clean = name.trim().take(40)
        require(clean.isNotEmpty())
        dao.tagNamed(clean)?.let { return it }
        val now = System.currentTimeMillis()
        val id = dao.upsertTag(UserDriveTag(name = clean, createdAt = now, updatedAt = now))
        return dao.enabledTags().first { it.id == id }
    }

    suspend fun addManualTag(driveId: Int, tagId: Long) = dao.addManualTag(DriveManualTag(driveId, tagId, System.currentTimeMillis()))
    suspend fun removeManualTag(driveId: Int, tagId: Long) = dao.removeManualTag(driveId, tagId)

    suspend fun recurringRoutes(carId: Int): RecurringRoutesSnapshot {
        val summaries = drives.getAllForCar(carId)
        val cached = places.endpointsForCar(carId).associateBy { it.driveId }
        val aggregate = aggregates.getDriveAggregatesForCar(carId).associateBy { it.driveId }
        val points = summaries.mapNotNull { drive ->
            val cachedEndpoint = cached[drive.driveId]
            val detail = aggregate[drive.driveId]
            val startLat = cachedEndpoint?.startLatitude ?: detail?.startLatitude
            val startLon = cachedEndpoint?.startLongitude ?: detail?.startLongitude
            val endLat = cachedEndpoint?.endLatitude ?: detail?.endLatitude
            val endLon = cachedEndpoint?.endLongitude ?: detail?.endLongitude
            if (startLat != null && startLon != null && endLat != null && endLon != null) drive.driveId to (GeoPoint(startLat, startLon) to GeoPoint(endLat, endLon)) else null
        }.toMap()
        val endpointPairs = RecurringRouteDetector.resolveEndpointPairs(points, places.activePlaces())
        return RecurringRouteDetector.discover(summaries, endpointPairs, dao.routeMetadata())
    }

    suspend fun saveRouteIntent(route: RecurringRoute, name: String?, confirmed: Boolean, dismissed: Boolean) {
        val prior = route.metadata
        val now = System.currentTimeMillis()
        if (prior != null && prior.routeKey != route.key) dao.deleteRouteMetadata(prior.routeKey)
        dao.upsertRouteMetadata(
            RecurringRouteMetadata(
                routeKey = route.key,
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                confirmed = confirmed,
                dismissed = dismissed,
                enabled = true,
                originLatitude = route.origin.point.latitude,
                originLongitude = route.origin.point.longitude,
                destinationLatitude = route.destination.point.latitude,
                destinationLongitude = route.destination.point.longitude,
                createdAt = prior?.createdAt ?: now,
                updatedAt = now
            )
        )
    }
}
