package com.matedroid.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CarImageResolverTest {

    @Test
    fun `missing custom legacy Model Y asset keeps the dark Gemini fallback`() {
        assertEquals(
            "car_images/my_PMNG_WY19B.png",
            CarImageResolver.getCustomAssetOrFallback(
                CarImageResolver.LEGACY_MODEL_Y_PERFORMANCE_DARK_GEMINI
            ) { false }
        )
    }

    @Test
    fun `DiamondBlack maps to PX02 color code`() {
        // TeslamateAPI reports the Juniper/Highland black as "DiamondBlack" (one word, no space).
        // Regression: this used to be unmapped, falling back to white (PPSW).
        assertEquals("PX02", CarImageResolver.mapColor("DiamondBlack"))
    }

    @Test
    fun `reversed and spaced spellings of Diamond Black also map to PX02`() {
        assertEquals("PX02", CarImageResolver.mapColor("Diamond Black"))
        assertEquals("PX02", CarImageResolver.mapColor("BlackDiamond"))
    }

    @Test
    fun `black Model Y Juniper Premium resolves to the black asset, not white`() {
        // Real user config: Model Y, DiamondBlack, 19" Crossflow, trim "74" (Long Range/Premium).
        val path = CarImageResolver.getAssetPath(
            model = "Y",
            exteriorColor = "DiamondBlack",
            wheelType = "Crossflow19",
            trimBadging = "74",
        )
        assertEquals("car_images/myj_PX02_WY19P.png", path)
    }

    @Test
    fun `exact legacy PN01 Performance dark Gemini configuration prefers the custom asset`() {
        assertEquals(
            "car_images/custom/vehicle_custom_legacy_model_y_pn01_dark_gemini.png",
            CarImageResolver.getAssetPath("Y", "StealthGrey", "Gemini19", "P74D")
        )
    }

    @Test
    fun `legacy PMNG proxy resolves the custom asset only with Performance and Gemini evidence`() {
        assertEquals(
            "car_images/custom/vehicle_custom_legacy_model_y_pn01_dark_gemini.png",
            CarImageResolver.getAssetPath("Y", "MidnightSilver", "Gemini19", "P74D")
        )
        assertEquals(
            "car_images/my_PMNG_WY19B.png",
            CarImageResolver.getAssetPath("Y", "MidnightSilver", "Gemini19", "74D")
        )
        assertEquals(
            "car_images/custom/vehicle_custom_legacy_model_y_pn01_dark_gemini.png",
            CarImageResolver.getAssetPath("Y", "MidnightSilver", "Gemini", "P74D")
        )
    }

    @Test
    fun `missing preferred custom asset retains the correct legacy dark Gemini fallback`() {
        assertEquals(
            "car_images/my_PMNG_WY19B.png",
            CarImageResolver.getFallbackAssetPath("Y", "StealthGrey", "Gemini19", "P74D") { false }
        )
    }

    @Test
    fun `PN01 P74D and Uberturbine do not prove Juniper`() {
        assertEquals(
            "car_images/my_PMNG_WY20P.png",
            CarImageResolver.getAssetPath("Y", "StealthGrey", "Uberturbine21", "P74D")
        )
    }

    @Test
    fun `legacy P74D Uberturbine remains legacy`() {
        assertEquals(
            "car_images/my_PPSW_WY20P.png",
            CarImageResolver.getAssetPath("Y", "PearlWhite", "Uberturbine21", "P74D")
        )
    }

    @Test
    fun `legacy deep blue Gemini remains legacy`() {
        assertEquals(
            "car_images/my_PPSB_WY19B.png",
            CarImageResolver.getAssetPath("Y", "DeepBlue", "Gemini19")
        )
    }

    @Test
    fun `Juniper exclusive wheel establishes Juniper independently of PN01`() {
        assertEquals(
            "car_images/myj_PN01_WY19P.png",
            CarImageResolver.getAssetPath("Y", "StealthGrey", "Crossflow19", "74")
        )
    }

    @Test
    fun `Juniper specific evidence and Performance trim select Juniper Performance`() {
        assertEquals(
            "car_images/myjp_PB02_WY21A.png",
            CarImageResolver.getAssetPath("Y", "MarineBlue", "Uberturbine21", "P74D")
        )
    }

    @Test
    fun `manual legacy PN01 dark Gemini override uses the preferred custom asset`() {
        assertEquals(
            "car_images/custom/vehicle_custom_legacy_model_y_pn01_dark_gemini.png",
            CarImageResolver.getAssetPathForOverride("my", "PN01", "WY19B", "P74D")
        )
        assertEquals(
            "car_images/my_PMNG_WY19B.png",
            CarImageResolver.getAssetPathForOverride("my", "PMNG", "WY19B", "74D")
        )
    }

    @Test
    fun `appearance dimensions remain independent under manual wheel override`() {
        val appearance = VehicleAppearanceResolver.resolve(
            model = "Y",
            exteriorColorCode = "PN01",
            wheelType = "Gemini19",
            trimBadging = "P74D",
            override = VehicleAppearanceOverride(wheels = WheelConfiguration.GEMINI_19_DARK)
        )
        assertEquals(VehicleGeneration.UNKNOWN, appearance.generation)
        assertEquals(VehicleTrim.PERFORMANCE, appearance.trim)
        assertEquals(WheelConfiguration.GEMINI_19_DARK, appearance.wheels)
        assertEquals(BrakeConfiguration.RED_PERFORMANCE, appearance.brakes)
    }
}
