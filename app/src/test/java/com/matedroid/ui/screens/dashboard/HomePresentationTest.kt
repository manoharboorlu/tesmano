package com.matedroid.ui.screens.dashboard

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePresentationTest {
    @Test
    fun `vehicle states retain distinct charging driving asleep and unavailable labels`() {
        assertEquals("Charging", HomePresentation.vehicleStateLabel("charging"))
        assertEquals("Driving", HomePresentation.vehicleStateLabel("driving"))
        assertEquals("Asleep", HomePresentation.vehicleStateLabel("suspended"))
        assertEquals("Connection unavailable", HomePresentation.vehicleStateLabel("offline"))
    }

    @Test
    fun `freshness is derived from the absolute status instant`() {
        val now = Instant.parse("2026-08-09T16:00:00Z")
        assertEquals(
            "Updated 45m ago",
            HomePresentation.freshnessLabel("2026-08-09T15:15:00Z", now)
        )
        assertNull(HomePresentation.freshnessLabel("2026-08-09T16:01:00Z", now))
    }

    @Test
    fun `vehicle identity presents Performance trim without internal badging`() {
        assertEquals("Model Y Performance", HomePresentation.vehicleIdentityDescriptor("Y", "P74D"))
    }
}
