package com.openlauncher.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.util.Log
import com.openlauncher.app.model.ClimateTemperatureState
import com.openlauncher.app.model.VehicleClimateState
import com.openlauncher.app.model.VehicleDoorState

/**
 * Small, dependency-free client for the public-facing service shipped by com.nwd.can.setting.
 *
 * The NWD SDK classes are deliberately not copied into Open Launcher. The service's AIDL wire
 * contract is stable enough to use through Binder directly. NWD's own client registers both its
 * legacy byte-packet car callback and its newer car-info callback, so this client does the same.
 */
internal class NwdCanServiceClient(
    context: Context,
    private val onVehicleUpdate: (
        fuelPercent: Float?,
        rangeKm: Float?,
        doors: VehicleDoorState?,
        climate: VehicleClimateState?
    ) -> Unit
) {
    private val appContext = context.applicationContext
    private var started = false
    private var bound = false
    private var remote: IBinder? = null

    private val callback = object : Binder(), IInterface {
        init {
            attachInterface(this, CALLBACK_DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int
        ): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK_DESCRIPTOR)
                return true
            }
            if (code == CALLBACK_CAN_DATA) {
                data.enforceInterface(CALLBACK_DESCRIPTOR)
                data.createByteArray()?.let(::handlePacket)
                reply?.writeNoException()
                return true
            }
            if (code == CALLBACK_CAR_INFO) {
                data.enforceInterface(CALLBACK_DESCRIPTOR)
                runCatching { handleCarInfoParcel(data) }
                    .onFailure { Log.w(TAG, "Unable to decode NWD car-info callback", it) }
                reply?.writeNoException()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!started || service == null) return
            remote = service
            runCatching { initializeThirdPartySdk(service) }
                .onFailure { Log.w(TAG, "Unable to initialize NWD third-party SDK", it) }
            val registeredCallbacks = listOf(
                "climate" to FEATURE_ADD_AIR_CALLBACK,
                "door" to FEATURE_ADD_DOOR_CALLBACK,
                "legacy car info" to FEATURE_ADD_CAR_INFO_CALLBACK,
                "car info" to FEATURE_ADD_CAN_CAR_INFO_CALLBACK
            ).count { (label, transaction) ->
                runCatching { registerCallback(service, transaction) }
                    .onFailure { Log.w(TAG, "Unable to register NWD $label callback", it) }
                    .isSuccess
            }
            if (registeredCallbacks > 0) {
                Log.i(TAG, "Connected to NWD CAN service")
            } else {
                Log.w(TAG, "Connected to NWD CAN service, but no callbacks were registered")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            Log.w(TAG, "NWD CAN service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            remote = null
            Log.w(TAG, "NWD CAN service binding died")
        }

        override fun onNullBinding(name: ComponentName?) {
            remote = null
            Log.w(TAG, "NWD CAN service returned a null binding")
        }
    }

    fun start() {
        if (started) return
        started = true
        val intent = Intent().setClassName(NWD_CAN_PACKAGE, NWD_CAN_SERVICE)
        bound = runCatching {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            Log.w(TAG, "Unable to bind NWD CAN service", it)
        }.getOrDefault(false)
        if (!bound) Log.d(TAG, "NWD CAN service is not available")
    }

    fun stop() {
        if (!started) return
        started = false
        remote?.let { service ->
            listOf(
                FEATURE_REMOVE_AIR_CALLBACK,
                FEATURE_REMOVE_DOOR_CALLBACK,
                FEATURE_REMOVE_CAR_INFO_CALLBACK,
                FEATURE_REMOVE_CAN_CAR_INFO_CALLBACK
            ).forEach { transaction ->
                runCatching { registerCallback(service, transaction) }
            }
        }
        remote = null
        if (bound) runCatching { appContext.unbindService(connection) }
        bound = false
    }

    private fun registerCallback(service: IBinder, transaction: Int) {
        call(service, transaction) { it.writeStrongBinder(callback) }
    }

    private fun initializeThirdPartySdk(service: IBinder) {
        call(service, FEATURE_INIT_SDK) { data ->
            data.writeString(THIRD_PARTY_APP)
            data.writeString(THIRD_PARTY_KEY)
            data.writeByte(0)
            data.writeString("Open Launcher vehicle dashboard")
        }
    }

    private fun call(service: IBinder, transaction: Int, write: (Parcel) -> Unit) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(FEATURE_DESCRIPTOR)
            write(data)
            service.transact(transaction, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun handlePacket(packet: ByteArray) {
        Log.d(
            TAG,
            "CAN callback packet header=0x%02X type=0x%02X size=%d".format(
                packet.getOrNull(0)?.toInt()?.and(0xFF) ?: -1,
                packet.getOrNull(1)?.toInt()?.and(0xFF) ?: -1,
                packet.size
            )
        )
        if (isQfSharedPacket(packet)) {
            handleQfSharedPacket(packet)
            return
        }
        if (!isValidPacket(packet)) {
            Log.w(TAG, "Ignored malformed NWD CAN packet (${packet.size} bytes)")
            return
        }
        when (packet[1].toInt() and 0xFF) {
            PACKET_AIR_INFO -> handleAirPacket(packet)

            PACKET_DOOR_INFO -> {
                val bits = packet[3].toInt() and 0xFF
                onVehicleUpdate(
                    null,
                    null,
                    VehicleDoorState(
                        frontLeftOpen = bits and 0x80 != 0,
                        frontRightOpen = bits and 0x40 != 0,
                        rearLeftOpen = bits and 0x20 != 0,
                        rearRightOpen = bits and 0x10 != 0,
                        trunkOpen = bits and 0x08 != 0,
                        hoodOpen = bits and 0x04 != 0
                    ),
                    null
                )
            }

            PACKET_CAR_INFO -> {
                val payloadLength = packet[2].toInt() and 0xFF
                if (payloadLength < CAR_INFO_REQUIRED_LENGTH) return
                val rawRange = ((packet[10].toInt() and 0xFF) shl 8) or
                    (packet[11].toInt() and 0xFF)
                val rawFuel = packet[17].toInt() and 0xFF
                val rangeKm = rawRange.takeUnless { it == UNKNOWN_WORD }
                    ?.toFloat()
                    ?.takeIf { it in 0f..MAX_RANGE_KM }
                // NWD calls this value "oil surplus" and does not carry a unit in this packet.
                // It is useful as a percent only for profiles that already normalize it to 0-100.
                val fuelPercent = rawFuel.takeUnless { it == UNKNOWN_BYTE }
                    ?.toFloat()
                    ?.takeIf { it in 0f..100f }
                if (fuelPercent != null || rangeKm != null) {
                    onVehicleUpdate(fuelPercent, rangeKm, null, null)
                }
            }
        }
    }

    private fun isQfSharedPacket(packet: ByteArray): Boolean =
        packet.size >= 2 && (packet[0].toInt() and 0xFF) == QF_SHARE_HEADER

    private fun handleQfSharedPacket(packet: ByteArray) {
        if ((packet[1].toInt() and 0xFF) != QF_SHARE_CLIMATE ||
            packet.size < QF_CLIMATE_MIN_SIZE
        ) return

        val primaryFlags = packet[2].toInt() and 0xFF
        val secondaryFlags = packet[3].toInt() and 0xFF
        val fanAndMode = packet[4].toInt() and 0xFF
        val driverTemperature = decodeTemperature(packet[7].toInt() and 0xFF, false)
        val passengerTemperature = decodeTemperature(packet[8].toInt() and 0xFF, false)
        val seatHeat = packet[10].toInt() and 0xFF
        onVehicleUpdate(
            null,
            null,
            null,
            VehicleClimateState(
                acOn = primaryFlags and 0x40 != 0,
                automatic = primaryFlags and 0x24 != 0,
                fanLevel = fanAndMode and 0x0F,
                driverTemperatureCelsius = driverTemperature.celsius,
                driverTemperatureState = driverTemperature.state,
                passengerTemperatureCelsius = passengerTemperature.celsius,
                passengerTemperatureState = passengerTemperature.state,
                driverSeatHeatLevel = (seatHeat ushr 4) and 0x0F,
                passengerSeatHeatLevel = seatHeat and 0x0F,
                frontDefrostOn = secondaryFlags and 0x80 != 0,
                rearDefrostOn = secondaryFlags and 0x40 != 0
            )
        )
        Log.d(TAG, "Decoded native QF climate packet from NWD callback")
    }

    private fun handleAirPacket(packet: ByteArray) {
        val payloadLength = packet[2].toInt() and 0xFF
        if (payloadLength < AIR_INFO_REQUIRED_LENGTH) return

        val primaryFlags = packet[3].toInt() and 0xFF
        val secondaryFlags = packet[4].toInt() and 0xFF
        val fanAndMode = packet[5].toInt() and 0xFF
        val temperatureFlags = packet[6].toInt() and 0xFF
        val seatCool = packet[8].toInt() and 0xFF
        val seatHeat = packet[9].toInt() and 0xFF
        val fahrenheit = temperatureFlags and 0x04 != 0
        val driverTemperature = decodeTemperature(packet[10].toInt() and 0xFF, fahrenheit)
        val passengerTemperature = decodeTemperature(packet[11].toInt() and 0xFF, fahrenheit)
        val fanLevelMax = (packet[18].toInt() ushr 4) and 0x0F

        onVehicleUpdate(
            null,
            null,
            null,
            VehicleClimateState(
                acOn = primaryFlags and 0x80 != 0,
                automatic = primaryFlags and 0x24 != 0 || secondaryFlags and 0x04 != 0,
                fanLevel = fanAndMode and 0x0F,
                fanLevelMax = fanLevelMax.takeIf { it > 0 },
                driverTemperatureCelsius = driverTemperature.celsius,
                driverTemperatureState = driverTemperature.state,
                passengerTemperatureCelsius = passengerTemperature.celsius,
                passengerTemperatureState = passengerTemperature.state,
                driverSeatHeatLevel = (seatHeat ushr 4) and 0x0F,
                passengerSeatHeatLevel = seatHeat and 0x0F,
                driverSeatCoolLevel = (seatCool ushr 4) and 0x0F,
                passengerSeatCoolLevel = seatCool and 0x0F,
                frontDefrostOn = secondaryFlags and 0x80 != 0,
                rearDefrostOn = secondaryFlags and 0x40 != 0
            )
        )
    }

    private fun decodeTemperature(raw: Int, fahrenheit: Boolean): DecodedTemperature = when (raw) {
        TEMPERATURE_LOW -> DecodedTemperature(null, ClimateTemperatureState.LOW)
        TEMPERATURE_HIGH -> DecodedTemperature(null, ClimateTemperatureState.HIGH)
        TEMPERATURE_UNKNOWN -> DecodedTemperature(null, ClimateTemperatureState.UNKNOWN)
        else -> {
            val value = raw / 2f
            val celsius = if (fahrenheit) (value - 32f) * 5f / 9f else value
            DecodedTemperature(celsius, ClimateTemperatureState.VALUE)
        }
    }

    /**
     * A second SDK client can globally make the NWD service choose its newer Parcelable path.
     * Decode the stable prefix of that parcel as a defensive fallback, without importing the
     * vendor's very large CarInfo class.
     */
    private fun handleCarInfoParcel(data: Parcel) {
        if (data.readInt() == 0) return
        repeat(4) { data.readInt() } // driving mileage fields
        val rawRange = data.readInt()
        repeat(2) { data.readInt() } // secondary range fields
        repeat(3) { data.readFloat() } // electric power fields
        repeat(3) { data.readInt() } // electric range fields
        repeat(2) { data.readFloat() } // trip A/B
        repeat(4) { data.readInt() } // speed fields
        data.readByte() // speed unit
        repeat(4) { data.readInt() } // three drive times and engine speed
        repeat(5) { data.readFloat() } // coolant and consumption fields
        data.readByte() // consumption unit
        val rawFuel = data.readFloat()

        val rangeKm = rawRange.toFloat().takeIf { rawRange >= 0 && it <= MAX_RANGE_KM }
        val fuelPercent = rawFuel.takeIf { it in 0f..100f }
        if (fuelPercent != null || rangeKm != null) {
            onVehicleUpdate(fuelPercent, rangeKm, null, null)
        }
    }

    private data class DecodedTemperature(
        val celsius: Float?,
        val state: ClimateTemperatureState
    )

    private fun isValidPacket(packet: ByteArray): Boolean {
        if (packet.size < MIN_PACKET_SIZE || (packet[0].toInt() and 0xFF) != PACKET_HEADER) {
            return false
        }
        val payloadLength = packet[2].toInt() and 0xFF
        val packetLength = payloadLength + PACKET_OVERHEAD
        return packetLength <= packet.size &&
            (packet[packetLength - 1].toInt() and 0xFF) == PACKET_TRAILER
    }

    private companion object {
        const val TAG = "NwdCanServiceClient"
        const val NWD_CAN_PACKAGE = "com.nwd.can.setting"
        const val NWD_CAN_SERVICE = "com.nwd.can.service.CanService"

        const val FEATURE_DESCRIPTOR = "com.nwd.can.sdk.outer.adil.ICanRemote4OuterFeature"
        const val CALLBACK_DESCRIPTOR = "com.nwd.can.sdk.outer.adil.ICanRemoteModelCallback"

        const val FEATURE_INIT_SDK = 2
        const val FEATURE_ADD_AIR_CALLBACK = 9
        const val FEATURE_REMOVE_AIR_CALLBACK = 10
        const val FEATURE_ADD_DOOR_CALLBACK = 11
        const val FEATURE_REMOVE_DOOR_CALLBACK = 12
        const val FEATURE_ADD_CAR_INFO_CALLBACK = 17
        const val FEATURE_REMOVE_CAR_INFO_CALLBACK = 18
        const val FEATURE_ADD_CAN_CAR_INFO_CALLBACK = 27
        const val FEATURE_REMOVE_CAN_CAR_INFO_CALLBACK = 28
        const val CALLBACK_CAN_DATA = 1
        const val CALLBACK_CAR_INFO = 2

        const val THIRD_PARTY_APP = "nwdthirdapp"
        const val THIRD_PARTY_KEY = "d39df3d908cf7136227987e37d5b2c7d"

        const val PACKET_HEADER = 0x6E
        const val PACKET_TRAILER = 0xFF
        const val PACKET_OVERHEAD = 4
        const val MIN_PACKET_SIZE = 5
        const val PACKET_AIR_INFO = 1
        const val PACKET_CAR_INFO = 2
        const val PACKET_DOOR_INFO = 3
        const val AIR_INFO_REQUIRED_LENGTH = 15
        const val CAR_INFO_REQUIRED_LENGTH = 15
        const val TEMPERATURE_LOW = 0x00
        const val TEMPERATURE_HIGH = 0xFF
        const val TEMPERATURE_UNKNOWN = 0xFE
        const val UNKNOWN_BYTE = 0xFF
        const val UNKNOWN_WORD = 0xFFFF
        const val MAX_RANGE_KM = 3_000f
        const val QF_SHARE_HEADER = 0x2E
        const val QF_SHARE_CLIMATE = 0x01
        const val QF_CLIMATE_MIN_SIZE = 11
    }
}
