package com.matedroid.domain

import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.RecurringRouteMetadata
import com.matedroid.data.local.entity.SmartPlace
import java.time.Instant
import kotlin.math.floor

/** A route is intentionally endpoint-based in Phase 7A; no route-shape inference is performed. */
data class RouteEndpoint(
    val key: String,
    val label: String,
    val point: GeoPoint,
    val smartPlace: SmartPlace? = null
)

data class RouteEndpointPair(val start: RouteEndpoint, val end: RouteEndpoint)
data class RouteCoverage(val knownEndpoints: Int, val totalDrives: Int)

data class RecurringRoute(
    val key: String,
    val origin: RouteEndpoint,
    val destination: RouteEndpoint,
    val driveIds: List<Int>,
    val occurrenceCount: Int,
    val totalDistance: Double,
    val averageDistance: Double,
    val averageDurationMinutes: Double,
    val weightedEfficiencyWhPerDistance: Double?,
    val mostRecent: String,
    val commute: Boolean,
    val metadata: RecurringRouteMetadata?
) {
    val displayName: String get() = metadata?.name?.takeIf { it.isNotBlank() } ?: "${origin.label} → ${destination.label}"
    val endpoints: String get() = "${origin.label} → ${destination.label}"
    val isSuggested: Boolean get() = metadata?.confirmed != true
}

data class RecurringRoutesSnapshot(val routes: List<RecurringRoute>, val coverage: RouteCoverage)

private data class EndpointOccurrence(val driveId: Int, val isStart: Boolean, val point: GeoPoint)

/**
 * A small, fixed radius for unknown endpoints. Smart Place radii deliberately do not apply here:
 * a broad Home geofence is useful for place matching but would collapse unrelated destinations.
 */
object EndpointClusterer {
    const val CLUSTER_RADIUS_METERS = 200.0
    private const val METERS_PER_LAT_DEGREE = 111_320.0

    fun clusterKeys(occurrences: List<Triple<Int, Boolean, GeoPoint>>): Map<Pair<Int, Boolean>, String> {
        data class Cluster(val key: String, val anchor: GeoPoint)
        val cellDegrees = CLUSTER_RADIUS_METERS / METERS_PER_LAT_DEGREE
        fun cell(point: GeoPoint): Pair<Int, Int> = floor(point.latitude / cellDegrees).toInt() to floor(point.longitude / cellDegrees).toInt()
        fun key(point: GeoPoint) = "geo:${"%.5f".format(java.util.Locale.ROOT, point.latitude)}:${"%.5f".format(java.util.Locale.ROOT, point.longitude)}"
        val clustersByCell = mutableMapOf<Pair<Int, Int>, MutableList<Cluster>>()
        val output = mutableMapOf<Pair<Int, Boolean>, String>()

        occurrences.sortedWith(compareBy<Triple<Int, Boolean, GeoPoint>> { it.third.latitude }.thenBy { it.third.longitude }.thenBy { it.first }.thenBy { it.second })
            .forEach { occurrence ->
                val point = occurrence.third
                val bucket = cell(point)
                val candidates = buildList {
                    for (lat in bucket.first - 1..bucket.first + 1) for (lon in bucket.second - 1..bucket.second + 1) {
                        addAll(clustersByCell[lat to lon].orEmpty())
                    }
                }
                val existing = candidates
                    .filter { SmartPlaceMatcher.distanceMeters(point, it.anchor) <= CLUSTER_RADIUS_METERS }
                    .minWithOrNull(compareBy<Cluster> { SmartPlaceMatcher.distanceMeters(point, it.anchor) }.thenBy { it.key })
                val cluster = existing ?: Cluster(key(point), point).also { clustersByCell.getOrPut(bucket) { mutableListOf() }.add(it) }
                output[occurrence.first to occurrence.second] = cluster.key
            }
        return output
    }
}

object RecurringRouteDetector {
    const val MIN_OCCURRENCES = 3
    private const val METADATA_MATCH_METERS = 300.0

    fun resolveEndpointPairs(
        endpointCoordinates: Map<Int, Pair<GeoPoint, GeoPoint>>,
        places: List<SmartPlace>
    ): Map<Int, RouteEndpointPair> {
        val unmatched = buildList {
            endpointCoordinates.forEach { (driveId, pair) ->
                if (SmartPlaceMatcher.match(pair.first, places) == null) add(Triple(driveId, true, pair.first))
                if (SmartPlaceMatcher.match(pair.second, places) == null) add(Triple(driveId, false, pair.second))
            }
        }
        val clusterKeys = EndpointClusterer.clusterKeys(unmatched)
        fun endpoint(driveId: Int, start: Boolean, point: GeoPoint): RouteEndpoint {
            val place = SmartPlaceMatcher.match(point, places)
            return if (place != null) RouteEndpoint("place:${place.id}", place.name, point, place)
            else RouteEndpoint(clusterKeys.getValue(driveId to start), "Unmatched location", point)
        }
        return endpointCoordinates.mapValues { (driveId, pair) ->
            RouteEndpointPair(endpoint(driveId, true, pair.first), endpoint(driveId, false, pair.second))
        }
    }

    fun discover(
        drives: List<DriveSummary>,
        endpoints: Map<Int, RouteEndpointPair>,
        metadata: List<RecurringRouteMetadata>
    ): RecurringRoutesSnapshot {
        val groups = drives.mapNotNull { drive -> endpoints[drive.driveId]?.let { (drive to it) } }
            .groupBy { (_, pair) -> "${pair.start.key}→${pair.end.key}" }
        val routes = groups.mapNotNull { (key, observations) ->
            val sample = observations.first().second
            val sampleSummary = observations.first().first
            val displayStart = sample.start.copy(label = sample.start.smartPlace?.name ?: sampleSummary.startAddress.ifBlank { sample.start.label })
            val displayEnd = sample.end.copy(label = sample.end.smartPlace?.name ?: sampleSummary.endAddress.ifBlank { sample.end.label })
            val displayPair = RouteEndpointPair(displayStart, displayEnd)
            val matchedMetadata = metadataFor(key, displayPair, metadata)
            if (matchedMetadata?.dismissed == true || matchedMetadata?.enabled == false) return@mapNotNull null
            if (observations.size < MIN_OCCURRENCES && matchedMetadata?.confirmed != true) return@mapNotNull null
            val validEfficiency = observations.map { it.first }.filter { it.distance > 0 && it.energyConsumed?.let(Double::isFinite) == true && it.energyConsumed > 0 }
            val totalDistance = observations.sumOf { it.first.distance.coerceAtLeast(0.0) }
            val totalEnergy = validEfficiency.sumOf { it.energyConsumed ?: 0.0 }
            val efficiencyDistance = validEfficiency.sumOf { it.distance }
            RecurringRoute(
                key = key,
                origin = displayStart,
                destination = displayEnd,
                driveIds = observations.map { it.first.driveId }.sortedByDescending { id -> observations.first { it.first.driveId == id }.first.startDate },
                occurrenceCount = observations.size,
                totalDistance = totalDistance,
                averageDistance = totalDistance / observations.size,
                averageDurationMinutes = observations.map { it.first.durationMin }.average(),
                weightedEfficiencyWhPerDistance = if (efficiencyDistance > 0) totalEnergy * 1000 / efficiencyDistance else null,
                mostRecent = observations.maxByOrNull { parseEpoch(it.first.startDate) }!!.first.startDate,
                commute = CommuteRuleEngine.isCommute(displayStart.smartPlace, displayEnd.smartPlace),
                metadata = matchedMetadata
            )
        }.sortedWith(compareByDescending<RecurringRoute> { it.occurrenceCount }.thenByDescending { parseEpoch(it.mostRecent) }.thenBy { it.key })
        return RecurringRoutesSnapshot(routes, RouteCoverage(endpoints.size, drives.size))
    }

    private fun metadataFor(key: String, pair: RouteEndpointPair, all: List<RecurringRouteMetadata>): RecurringRouteMetadata? =
        all.firstOrNull { it.routeKey == key } ?: all.firstOrNull { item ->
            item.originLatitude?.let { lat -> item.originLongitude?.let { lon -> SmartPlaceMatcher.distanceMeters(pair.start.point, GeoPoint(lat, lon)) <= METADATA_MATCH_METERS } } == true &&
                item.destinationLatitude?.let { lat -> item.destinationLongitude?.let { lon -> SmartPlaceMatcher.distanceMeters(pair.end.point, GeoPoint(lat, lon)) <= METADATA_MATCH_METERS } } == true
        }

    private fun parseEpoch(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)
}
