package com.openlauncher.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.openlauncher.app.model.VehicleData
import com.openlauncher.app.model.VehicleDoorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Common read-only vehicle data entry point.
 *
 * Choiceway/RaviRS units normally route CAN-box messages through eventcenter. Direct decoded
 * door events are preferred. A validated RAISE (0x2E) frame decoder provides a second path for
 * FT-RZ/RZC boxes, while conservative named-extra support lets another vendor plug into the same
 * UI without changing the widget.
 */
class VehicleDataManager(context: Context) {
    private val appContext = context.applicationContext
    private val _data = MutableStateFlow(VehicleData())
    val data: StateFlow<VehicleData> = _data.asStateFlow()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                ACTION_CHOICEWAY_DOORS -> readChoicewayDoors(intent)
                else -> readCommonPayload(intent)
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            ACTIONS.forEach(::addAction)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }
        registered = true
        Log.i(TAG, "Vehicle data receiver started")
    }

    fun stop() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        registered = false
    }

    private fun readChoicewayDoors(intent: Intent) {
        val raw = numberExtra(intent, EXTRA_CHOICEWAY_DOORS)?.toInt() ?: return
        update(doors = decodeDoorByte(raw), source = "Choiceway CAN")
    }

    private fun readCommonPayload(intent: Intent) {
        val extras = intent.extras ?: return
        val normalized = extras.keySet().associateBy(::normalizeKey)

        val fuel = findNumber(intent, normalized, FUEL_PERCENT_KEYS)
            ?.toFloat()?.takeIf { it in 0f..100f }
        val range = findNumber(intent, normalized, RANGE_KM_KEYS)
            ?.toFloat()?.takeIf { it in 0f..3_000f }

        if (fuel != null || range != null) {
            update(fuelPercent = fuel, rangeKm = range, source = intent.action ?: "Vehicle broadcast")
        }

        for (key in extras.keySet()) {
            @Suppress("DEPRECATION")
            val bytes = runCatching { extras.get(key) as? ByteArray }.getOrNull() ?: continue
            decodeRaiseFrames(bytes)
        }
    }

    /** Decode only checksum-valid RAISE frames: 2E command length payload checksum. */
    private fun decodeRaiseFrames(bytes: ByteArray) {
        var index = 0
        while (index + 3 < bytes.size) {
            if ((bytes[index].toInt() and 0xFF) != RAISE_HEADER) {
                index++
                continue
            }
            val command = bytes[index + 1].toInt() and 0xFF
            val length = bytes[index + 2].toInt() and 0xFF
            val end = index + 3 + length
            if (end >= bytes.size) break
            var sum = command + length
            for (i in index + 3 until end) sum += bytes[i].toInt() and 0xFF
            val expectedChecksum = (sum xor 0xFF) and 0xFF
            if ((bytes[end].toInt() and 0xFF) != expectedChecksum) {
                index++
                continue
            }

            when (command) {
                RAISE_DOORS_COMMAND -> if (length >= 1) {
                    update(
                        doors = decodeDoorByte(bytes[index + 3].toInt() and 0xFF),
                        source = "RAISE CAN"
                    )
                }
                RAISE_TRIP_COMMAND -> if (length >= 7) {
                    val rangeKm = ((bytes[index + 7].toInt() and 0xFF) shl 8) or
                        (bytes[index + 8].toInt() and 0xFF)
                    if (rangeKm in 0..3_000) {
                        update(rangeKm = rangeKm.toFloat(), source = "RAISE CAN")
                    }
                }
            }
            index = end + 1
        }
    }

    private fun decodeDoorByte(raw: Int) = VehicleDoorState(
        frontLeftOpen = raw and 0x80 != 0,
        frontRightOpen = raw and 0x40 != 0,
        rearRightOpen = raw and 0x20 != 0,
        rearLeftOpen = raw and 0x10 != 0,
        trunkOpen = raw and 0x08 != 0,
        hoodOpen = raw and 0x04 != 0
    )

    private fun update(
        fuelPercent: Float? = null,
        rangeKm: Float? = null,
        doors: VehicleDoorState? = null,
        source: String
    ) {
        val old = _data.value
        _data.value = old.copy(
            fuelPercent = fuelPercent ?: old.fuelPercent,
            rangeKm = rangeKm ?: old.rangeKm,
            doors = doors ?: old.doors,
            source = source,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun findNumber(intent: Intent, keys: Map<String, String>, candidates: Set<String>): Number? {
        val actual = candidates.firstNotNullOfOrNull(keys::get) ?: return null
        return numberExtra(intent, actual)
    }

    private fun numberExtra(intent: Intent, key: String): Number? {
        @Suppress("DEPRECATION")
        return runCatching { intent.extras?.get(key) as? Number }.getOrNull()
    }

    private fun normalizeKey(key: String): String = key.lowercase().filter(Char::isLetterOrDigit)

    companion object {
        private const val TAG = "VehicleDataManager"
        private const val ACTION_CHOICEWAY_DOORS =
            "com.szchoiceway.eventcenter.EventUtils.ACCORD_DOOR_INFO"
        private const val EXTRA_CHOICEWAY_DOORS = "EventUtils.CAR_DOOR_DATA"
        private const val ACTION_CHOICEWAY_RAW_CAN =
            "com.choiceway.eventcenter.EventUtils.MCU_MSG_CAN_ALL_INFO"

        // Seen on several vendor integrations. They are harmless when absent and parsed only
        // when an exact, unit-bearing extra name is present.
        private val ACTIONS = arrayOf(
            ACTION_CHOICEWAY_DOORS,
            ACTION_CHOICEWAY_RAW_CAN,
            "com.szchoiceway.eventcenter.EventUtils.CAN_CAR_TIRP_INFO",
            "com.szchoiceway.eventcenter.EventUtils.CAN_FUEL_CONSUMPTION_INFOR",
            "com.syu.carinfo",
            "com.microntek.carinfo"
        )

        private val FUEL_PERCENT_KEYS = setOf(
            "fuelpercent", "fuellevelpercent", "remainingfuelpercent", "tankpercent"
        )
        private val RANGE_KM_KEYS = setOf(
            "rangekm", "remainingrangekm", "distancetoemptykm", "cruisingrangekm"
        )

        private const val RAISE_HEADER = 0x2E
        private const val RAISE_TRIP_COMMAND = 0x21
        private const val RAISE_DOORS_COMMAND = 0x24
    }
}
