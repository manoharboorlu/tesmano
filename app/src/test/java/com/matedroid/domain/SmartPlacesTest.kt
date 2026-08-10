package com.matedroid.domain

import com.matedroid.data.local.entity.DriveTagOverrideAction
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.data.local.entity.SmartPlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlacesTest {
    private fun place(id: Long, latitude: Double, radius: Int = 300, enabled: Boolean = true, type: String = SmartPlaceType.CUSTOM) =
        SmartPlace(id, "Place $id", type, latitude, 0.0, radius, enabled, 0, 0)

    @Test fun `matches inside radius`() {
        assertEquals(1L, SmartPlaceMatcher.match(GeoPoint(0.001, 0.0), listOf(place(1, 0.0)))?.id)
    }

    @Test fun `does not match outside radius`() {
        assertNull(SmartPlaceMatcher.match(GeoPoint(0.004, 0.0), listOf(place(1, 0.0))))
    }

    @Test fun `matches radius boundary`() {
        val point = GeoPoint(0.001, 0.0)
        val radius = SmartPlaceMatcher.distanceMeters(point, GeoPoint(0.0, 0.0)).toInt() + 1
        assertEquals(1L, SmartPlaceMatcher.match(point, listOf(place(1, 0.0, radius)))?.id)
    }

    @Test fun `selects closest overlapping place deterministically`() {
        assertEquals(2L, SmartPlaceMatcher.match(GeoPoint(0.001, 0.0), listOf(place(1, 0.0, 500), place(2, 0.0011, 500)))?.id)
    }

    @Test fun `ignores disabled places`() {
        assertNull(SmartPlaceMatcher.match(GeoPoint(0.0, 0.0), listOf(place(1, 0.0, enabled = false))))
    }

    @Test fun `commute is only home work relationship and manual override wins`() {
        val home = place(1, 0.0, type = SmartPlaceType.HOME)
        val work = place(2, 1.0, type = SmartPlaceType.WORK)
        assertTrue(CommuteRuleEngine.isCommute(home, work))
        assertTrue(CommuteRuleEngine.isCommute(work, home))
        assertFalse(CommuteRuleEngine.isCommute(home, place(3, 2.0)))
        val removed = DrivePlaceContext(home, work, true, DriveTagOverrideAction.REMOVE)
        assertFalse(removed.commute)
        val assigned = DrivePlaceContext(home, place(3, 2.0), false, DriveTagOverrideAction.ASSIGN)
        assertTrue(assigned.commute)
        assertTrue(DrivePlaceContext(home, work, true, null).commute)
    }

    @Test fun `only active home and work replace their active role`() {
        assertTrue(SmartPlacePolicy.replacesExistingActivePlace(place(1, 0.0, type = SmartPlaceType.HOME)))
        assertTrue(SmartPlacePolicy.replacesExistingActivePlace(place(2, 0.0, type = SmartPlaceType.WORK)))
        assertFalse(SmartPlacePolicy.replacesExistingActivePlace(place(3, 0.0)))
        assertFalse(SmartPlacePolicy.replacesExistingActivePlace(place(4, 0.0, enabled = false, type = SmartPlaceType.HOME)))
    }
}
