package com.matedroid.domain

import com.matedroid.data.api.models.DriveDetail
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.SmartPlacesDao
import com.matedroid.data.local.entity.DriveEndpointCache
import com.matedroid.data.local.entity.DriveTag
import com.matedroid.data.local.entity.DriveTagOverride
import com.matedroid.data.local.entity.DriveTagOverrideAction
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.data.local.entity.SmartPlaceType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val latitude: Double, val longitude: Double)

data class DrivePlaceContext(
    val start: SmartPlace?,
    val end: SmartPlace?,
    val automaticCommute: Boolean,
    val manualCommuteAction: String?
) {
    val commute: Boolean get() = when (manualCommuteAction) {
        DriveTagOverrideAction.ASSIGN -> true
        DriveTagOverrideAction.REMOVE -> false
        else -> automaticCommute
    }
    val commuteIsManual: Boolean get() = manualCommuteAction != null
}

/** Deterministic geographic matcher shared by every Smart Place surface. */
object SmartPlaceMatcher {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val latDelta = Math.toRadians(to.latitude - from.latitude)
        val lonDelta = Math.toRadians(to.longitude - from.longitude)
        val a = sin(latDelta / 2).pow(2) +
            cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) * sin(lonDelta / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    fun match(point: GeoPoint?, places: List<SmartPlace>): SmartPlace? = point?.let { candidate ->
        places.asSequence()
            .filter { it.enabled && distanceMeters(candidate, GeoPoint(it.latitude, it.longitude)) <= it.radiusMeters }
            .sortedWith(compareBy<SmartPlace> { distanceMeters(candidate, GeoPoint(it.latitude, it.longitude)) }.thenBy { it.id })
            .firstOrNull()
    }
}

object CommuteRuleEngine {
    fun isCommute(start: SmartPlace?, end: SmartPlace?): Boolean {
        val types = setOf(start?.type, end?.type)
        return types == setOf(SmartPlaceType.HOME, SmartPlaceType.WORK)
    }
}

object SmartPlacePolicy {
    /** Home and Work are singleton active roles; Custom locations intentionally are not. */
    fun replacesExistingActivePlace(place: SmartPlace): Boolean =
        place.enabled && place.type != SmartPlaceType.CUSTOM
}

@Singleton
class SmartPlacesRepository @Inject constructor(
    private val dao: SmartPlacesDao,
    private val aggregateDao: AggregateDao
) {
    fun observePlaces(): Flow<List<SmartPlace>> = dao.observePlaces()

    suspend fun savePlace(place: SmartPlace): Long {
        require(place.type in SmartPlaceType.all)
        require(place.radiusMeters in 100..2_000)
        val now = System.currentTimeMillis()
        if (SmartPlacePolicy.replacesExistingActivePlace(place)) {
            dao.disableOtherActiveOfType(place.type, place.id, now)
        }
        val normalized = place.copy(
            name = place.name.trim(),
            createdAt = if (place.id == 0L) now else place.createdAt,
            updatedAt = now
        )
        return if (normalized.id == 0L) dao.insertPlace(normalized) else {
            dao.updatePlace(normalized)
            normalized.id
        }
    }

    suspend fun deletePlace(place: SmartPlace) = dao.deletePlace(place)

    suspend fun cacheLoadedDetail(carId: Int, driveId: Int, detail: DriveDetail) {
        val points = detail.positions.orEmpty().mapNotNull { position ->
            position.latitude?.let { latitude -> position.longitude?.let { longitude -> GeoPoint(latitude, longitude) } }
        }
        if (points.isEmpty()) return
        dao.upsertEndpoint(
            DriveEndpointCache(
                driveId = driveId,
                carId = carId,
                startLatitude = points.first().latitude,
                startLongitude = points.first().longitude,
                endLatitude = points.last().latitude,
                endLongitude = points.last().longitude,
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun contextForDrive(carId: Int, driveId: Int): DrivePlaceContext {
        val endpoint = dao.endpointForDrive(driveId) ?: aggregateDao
            .getDriveAggregate(carId, driveId)
            ?.let {
                DriveEndpointCache(it.driveId, it.carId, it.startLatitude, it.startLongitude, it.endLatitude, it.endLongitude, it.computedAt)
            }
        val places = dao.activePlaces()
        val start = endpoint?.let { SmartPlaceMatcher.match(it.startLatitude?.let { lat -> it.startLongitude?.let { lon -> GeoPoint(lat, lon) } }, places) }
        val end = endpoint?.let { SmartPlaceMatcher.match(it.endLatitude?.let { lat -> it.endLongitude?.let { lon -> GeoPoint(lat, lon) } }, places) }
        val override = dao.overrideForTag(driveId, DriveTag.COMMUTE)
        return DrivePlaceContext(start, end, CommuteRuleEngine.isCommute(start, end), override?.action)
    }

    suspend fun contextsForDrives(carId: Int, driveIds: List<Int>): Map<Int, DrivePlaceContext> {
        if (driveIds.isEmpty()) return emptyMap()
        val places = dao.activePlaces()
        val cached = dao.endpointsForDrives(driveIds).associateBy { it.driveId }
        val aggregates = aggregateDao.getDriveAggregates(carId, driveIds).associateBy { it.driveId }
        val overrides = dao.overridesForTag(driveIds, DriveTag.COMMUTE).associateBy { it.driveId }
        return driveIds.associateWith { id ->
            val endpoint = cached[id] ?: aggregates[id]?.let { DriveEndpointCache(it.driveId, it.carId, it.startLatitude, it.startLongitude, it.endLatitude, it.endLongitude, it.computedAt) }
            val start = endpoint?.let { SmartPlaceMatcher.match(it.startLatitude?.let { lat -> it.startLongitude?.let { lon -> GeoPoint(lat, lon) } }, places) }
            val end = endpoint?.let { SmartPlaceMatcher.match(it.endLatitude?.let { lat -> it.endLongitude?.let { lon -> GeoPoint(lat, lon) } }, places) }
            DrivePlaceContext(start, end, CommuteRuleEngine.isCommute(start, end), overrides[id]?.action)
        }
    }

    suspend fun setCommuteOverride(driveId: Int, assigned: Boolean) {
        dao.upsertOverride(DriveTagOverride(driveId, DriveTag.COMMUTE, if (assigned) DriveTagOverrideAction.ASSIGN else DriveTagOverrideAction.REMOVE, System.currentTimeMillis()))
    }

    suspend fun revertCommuteToAutomatic(driveId: Int) = dao.deleteOverride(driveId, DriveTag.COMMUTE)
}
