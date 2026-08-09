package com.matedroid.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAuthenticationTest {
    @Test fun `uses no Authorization header without credentials`() {
        val authentication = resolveReadAuthentication("", "", "")
        assertEquals(ApiAuthMode.NONE, authentication.mode)
        assertNull(authentication.value)
    }

    @Test fun `uses Basic when only complete Basic credentials are configured`() {
        assertEquals(ApiAuthMode.BASIC, resolveReadAuthentication("", "user", "password").mode)
    }

    @Test fun `uses Bearer when a token is configured`() {
        val authentication = resolveReadAuthentication("sanitized-token", "", "")
        assertEquals(ApiAuthMode.BEARER, authentication.mode)
        assertEquals("sanitized-token", authentication.value)
    }

    @Test fun `Bearer takes precedence when old settings contain both modes`() {
        val authentication = resolveReadAuthentication("sanitized-token", "user", "password")
        assertEquals(ApiAuthMode.BEARER, authentication.mode)
        assertEquals("sanitized-token", authentication.value)
    }
}
