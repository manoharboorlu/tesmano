package com.matedroid.domain

import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.RecurringRouteMetadata
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.data.local.entity.SmartPlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringRoutesTest {
    private fun drive(id: Int, distance: Double = 10.0, energy: Double? = 2.0, date: String = "2026-08-09T12:00:00Z") =
        DriveSummary(id, 1, date, date, 20, "", "", distance, 0, 0, 0, 0, 50, 49, null, null, energy, null)
    private fun place(id: Long, type: String, point: GeoPoint, enabled: Boolean = true) =
        SmartPlace(id, type.lowercase().replaceFirstChar { it.uppercase() }, type, point.latitude, point.longitude, 500, enabled, 1, 1)
    private fun pair(start: GeoPoint, end: GeoPoint) = RouteEndpointPair(RouteEndpoint("a", "A", start), RouteEndpoint("b", "B", end))

    @Test fun `Smart Place endpoint identity takes priority`() {
        val home = GeoPoint(40.0, -74.0)
        val work = GeoPoint(40.01, -74.01)
        val resolved = RecurringRouteDetector.resolveEndpointPairs(mapOf(1 to (home to work)), listOf(place(1, SmartPlaceType.HOME, home), place(2, SmartPlaceType.WORK, work)))
        assertEquals("place:1", resolved.getValue(1).start.key)
        assertEquals("place:2", resolved.getValue(1).end.key)
    }

    @Test fun `unknown endpoints cluster within tolerance and stay distinct outside it`() {
        val near = RecurringRouteDetector.resolveEndpointPairs(mapOf(1 to (GeoPoint(0.0, 0.0) to GeoPoint(1.0, 1.0)), 2 to (GeoPoint(0.001, 0.0) to GeoPoint(1.001, 1.0))), emptyList())
        assertEquals(near.getValue(1).start.key, near.getValue(2).start.key)
        val far = RecurringRouteDetector.resolveEndpointPairs(mapOf(1 to (GeoPoint(0.0, 0.0) to GeoPoint(1.0, 1.0)), 2 to (GeoPoint(0.004, 0.0) to GeoPoint(1.004, 1.0))), emptyList())
        assertNotEquals(far.getValue(1).start.key, far.getValue(2).start.key)
    }

    @Test fun `direction and threshold remain explicit`() {
        val origin = GeoPoint(0.0, 0.0); val destination = GeoPoint(1.0, 1.0)
        val endpoints = RecurringRouteDetector.resolveEndpointPairs(mapOf(1 to (origin to destination), 2 to (origin to destination), 3 to (origin to destination), 4 to (destination to origin), 5 to (destination to origin), 6 to (destination to origin)), emptyList())
        val result = RecurringRouteDetector.discover((1..6).map(::drive), endpoints, emptyList())
        assertEquals(2, result.routes.size)
        assertNotEquals(result.routes[0].key, result.routes[1].key)
        assertTrue(RecurringRouteDetector.discover((1..2).map(::drive), endpoints, emptyList()).routes.isEmpty())
    }

    @Test fun `disabled places are ignored and an edit naturally rederives endpoint identity`() {
        val point = GeoPoint(40.0, -74.0)
        val disabled = RecurringRouteDetector.resolveEndpointPairs(mapOf(1 to (point to GeoPoint(41.0, -75.0))), listOf(place(1, SmartPlaceType.HOME, point, enabled = false)))
        val enabled = RecurringRouteDetector.resolveEndpointPairs(mapOf(1 to (point to GeoPoint(41.0, -75.0))), listOf(place(1, SmartPlaceType.HOME, point)))
        assertFalse(disabled.getValue(1).start.key.startsWith("place:"))
        assertEquals("place:1", enabled.getValue(1).start.key)
    }

    @Test fun `confirmed route name follows a nearby rederived endpoint`() {
        val start = GeoPoint(40.0, -74.0); val end = GeoPoint(40.01, -74.01)
        val oldPairs = RecurringRouteDetector.resolveEndpointPairs((1..3).associateWith { start to end }, emptyList())
        val old = RecurringRouteDetector.discover((1..3).map(::drive), oldPairs, emptyList()).routes.single()
        val metadata = RecurringRouteMetadata(old.key, "Gym", true, false, true, start.latitude, start.longitude, end.latitude, end.longitude, 1, 1)
        val newPairs = RecurringRouteDetector.resolveEndpointPairs((1..3).associateWith { start to end }, listOf(place(1, SmartPlaceType.HOME, start)))
        assertEquals("Gym", RecurringRouteDetector.discover((1..3).map(::drive), newPairs, listOf(metadata)).routes.single().displayName)
    }

    @Test fun `weighted efficiency and endpoint coverage use summary data only`() {
        val endpoints = mapOf(1 to pair(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)), 2 to pair(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)), 3 to pair(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)))
        val routes = RecurringRouteDetector.discover(listOf(drive(1, 10.0, 2.0), drive(2, 20.0, 8.0), drive(3, 5.0, null), drive(4)), endpoints, emptyList())
        assertEquals(333.333, routes.routes.single().weightedEfficiencyWhPerDistance!!, .01)
        assertEquals(3, routes.coverage.knownEndpoints)
        assertEquals(4, routes.coverage.totalDrives)
    }
}
