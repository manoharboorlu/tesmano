package com.matedroid.data.repository

import com.matedroid.data.api.models.DrivesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiEnvelopeTest {
    @Test fun `HTTP 200 error envelope becomes a typed failure`() {
        val result = mapApiEnvelope(DrivesResponse(error = "sanitized server failure"), 200, "drives") { it?.data }

        assertTrue(result is ApiResult.Error)
        result as ApiResult.Error
        assertEquals(ApiFailure.SERVER_ENVELOPE, result.failure)
        assertEquals("sanitized server failure", result.message)
    }

    @Test fun `empty successful envelope becomes typed missing-data failure`() {
        val result = mapApiEnvelope(DrivesResponse(), 200, "drives") { it?.data }

        assertTrue(result is ApiResult.Error)
        result as ApiResult.Error
        assertEquals(ApiFailure.MISSING_DATA, result.failure)
    }
}
