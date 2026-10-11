package com.openlauncher.app.model

data class VehicleDoorState(
    val frontLeftOpen: Boolean = false,
    val frontRightOpen: Boolean = false,
    val rearLeftOpen: Boolean = false,
    val rearRightOpen: Boolean = false,
    val trunkOpen: Boolean = false,
    val hoodOpen: Boolean = false
) {
    val anyOpen: Boolean
        get() = frontLeftOpen || frontRightOpen || rearLeftOpen ||
            rearRightOpen || trunkOpen || hoodOpen
}

/**
 * Vehicle values are nullable on purpose. Aftermarket CAN boxes expose different subsets of
 * data; the dashboard must never turn a missing value into a plausible-looking fake reading.
 */
data class VehicleData(
    val fuelPercent: Float? = null,
    val rangeKm: Float? = null,
    val doors: VehicleDoorState? = null,
    val source: String? = null,
    val updatedAtMs: Long = 0L
)
