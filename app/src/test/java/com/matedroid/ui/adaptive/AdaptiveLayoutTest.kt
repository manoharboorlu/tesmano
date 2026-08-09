package com.matedroid.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun `Fold8 cover-width window uses the compact cover mode`() {
        assertEquals(TesManoLayoutMode.COMPACT_COVER, TesManoLayoutClassifier.fromWidthDp(411))
    }

    @Test
    fun `Fold8 main-width window uses the expanded main mode`() {
        assertEquals(TesManoLayoutMode.EXPANDED_MAIN, TesManoLayoutClassifier.fromWidthDp(859))
    }
}
