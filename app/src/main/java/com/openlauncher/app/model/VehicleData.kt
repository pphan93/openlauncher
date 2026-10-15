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

enum class ClimateTemperatureState {
    VALUE,
    LOW,
    HIGH,
    UNKNOWN
}

data class VehicleClimateState(
    val acOn: Boolean = false,
    val automatic: Boolean = false,
    val fanLevel: Int = 0,
    val fanLevelMax: Int? = null,
    val airUp: Boolean = false,
    val airFace: Boolean = false,
    val airDown: Boolean = false,
    val driverTemperatureCelsius: Float? = null,
    val driverTemperatureState: ClimateTemperatureState = ClimateTemperatureState.UNKNOWN,
    val passengerTemperatureCelsius: Float? = null,
    val passengerTemperatureState: ClimateTemperatureState = ClimateTemperatureState.UNKNOWN,
    val driverSeatHeatLevel: Int = 0,
    val passengerSeatHeatLevel: Int = 0,
    val driverSeatCoolLevel: Int = 0,
    val passengerSeatCoolLevel: Int = 0,
    val frontDefrostOn: Boolean = false,
    val rearDefrostOn: Boolean = false
)

/**
 * Vehicle values are nullable on purpose. Aftermarket CAN boxes expose different subsets of
 * data; the dashboard must never turn a missing value into a plausible-looking fake reading.
 */
data class VehicleData(
    val fuelPercent: Float? = null,
    val rangeKm: Float? = null,
    val doors: VehicleDoorState? = null,
    val climate: VehicleClimateState? = null,
    val source: String? = null,
    val updatedAtMs: Long = 0L
)
