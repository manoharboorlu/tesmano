package com.matedroid.domain.model

/** Independent vehicle facts. Do not infer one dimension from another. */
enum class VehicleGeneration { LEGACY, HIGHLAND, JUNIPER, UNKNOWN }
enum class VehicleTrim { STANDARD, LONG_RANGE, PERFORMANCE, UNKNOWN }
enum class WheelConfiguration { GEMINI_19_DARK, UBERTURBINE_21, CROSSFLOW_19, PHOTON_18, HELIX_20, UNKNOWN }
enum class BrakeConfiguration { STANDARD, RED_PERFORMANCE, UNKNOWN }
enum class SpoilerConfiguration { NONE, FACTORY_PERFORMANCE, UNKNOWN }

/** Manual values are authoritative only for the dimensions they provide. */
data class VehicleAppearanceOverride(
    val generation: VehicleGeneration? = null,
    val trim: VehicleTrim? = null,
    val wheels: WheelConfiguration? = null,
    val brakes: BrakeConfiguration? = null,
    val spoiler: SpoilerConfiguration? = null
)

data class VehicleAppearance(
    val generation: VehicleGeneration,
    val trim: VehicleTrim,
    val wheels: WheelConfiguration,
    val brakes: BrakeConfiguration,
    val spoiler: SpoilerConfiguration
)

/** Conservative Model Y resolver. PN01, P74D, and Uberturbine21 are not generation evidence. */
object VehicleAppearanceResolver {
    fun resolve(
        model: String?,
        exteriorColorCode: String?,
        wheelType: String?,
        trimBadging: String?,
        spoilerType: String? = null,
        override: VehicleAppearanceOverride? = null
    ): VehicleAppearance {
        val wheels = override?.wheels ?: detectWheels(wheelType)
        val trim = override?.trim ?: detectTrim(trimBadging)
        val generation = override?.generation ?: detectGeneration(model, exteriorColorCode, wheels)
        val brakes = override?.brakes ?: if (trim == VehicleTrim.PERFORMANCE) BrakeConfiguration.RED_PERFORMANCE else BrakeConfiguration.UNKNOWN
        val spoiler = override?.spoiler ?: if (spoilerType.orEmpty().contains("performance", ignoreCase = true)) SpoilerConfiguration.FACTORY_PERFORMANCE else SpoilerConfiguration.UNKNOWN
        return VehicleAppearance(generation, trim, wheels, brakes, spoiler)
    }

    private fun detectGeneration(model: String?, colorCode: String?, wheels: WheelConfiguration): VehicleGeneration = when (model?.uppercase()) {
        "Y" -> when {
            colorCode in setOf("PN00", "PR01", "PX02", "PB01", "PB02") ||
                wheels in setOf(WheelConfiguration.CROSSFLOW_19, WheelConfiguration.PHOTON_18, WheelConfiguration.HELIX_20) -> VehicleGeneration.JUNIPER
            else -> VehicleGeneration.UNKNOWN
        }
        else -> VehicleGeneration.UNKNOWN
    }

    private fun detectTrim(trimBadging: String?): VehicleTrim = when {
        trimBadging.orEmpty().startsWith("P", ignoreCase = true) || trimBadging.orEmpty().contains("performance", ignoreCase = true) -> VehicleTrim.PERFORMANCE
        trimBadging.orEmpty().contains("long", ignoreCase = true) || trimBadging in setOf("74", "74D") -> VehicleTrim.LONG_RANGE
        trimBadging == "50" -> VehicleTrim.STANDARD
        else -> VehicleTrim.UNKNOWN
    }

    private fun detectWheels(wheelType: String?): WheelConfiguration {
        val normalized = wheelType.orEmpty().lowercase().replace(" ", "").replace("-", "").replace("_", "")
        return when {
            normalized.startsWith("gemini19") -> WheelConfiguration.GEMINI_19_DARK
            normalized.startsWith("uberturbine21") -> WheelConfiguration.UBERTURBINE_21
            normalized.startsWith("crossflow19") -> WheelConfiguration.CROSSFLOW_19
            normalized.startsWith("photon18") -> WheelConfiguration.PHOTON_18
            normalized.startsWith("helix20") -> WheelConfiguration.HELIX_20
            else -> WheelConfiguration.UNKNOWN
        }
    }
}
