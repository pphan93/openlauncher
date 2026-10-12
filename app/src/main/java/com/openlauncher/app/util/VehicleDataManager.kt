package com.openlauncher.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.openlauncher.app.model.VehicleData
import com.openlauncher.app.model.VehicleDoorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.zip.ZipFile

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
    private var nwdReceiverRegistered = false
    private var nwdDiscoveryStarted = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                ACTION_CHOICEWAY_DOORS -> readChoicewayDoors(intent)
                else -> readCommonPayload(intent)
            }
        }
    }

    private val nwdReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            readCommonPayload(intent, source = "NWD CAN", diagnostic = true)
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
        discoverAndRegisterNwdActions()
    }

    fun stop() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        if (nwdReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(nwdReceiver) }
            nwdReceiverRegistered = false
        }
        registered = false
    }

    private fun readChoicewayDoors(intent: Intent) {
        val raw = numberExtra(intent, EXTRA_CHOICEWAY_DOORS)?.toInt() ?: return
        update(doors = decodeDoorByte(raw), source = "Choiceway CAN")
    }

    private fun readCommonPayload(
        intent: Intent,
        source: String? = null,
        diagnostic: Boolean = false
    ) {
        val extras = intent.extras
        if (extras == null) {
            if (diagnostic) Log.i(TAG, "NWD CAN event action=${intent.action} extras=<none>")
            return
        }
        val flattened = flattenExtras(extras)
        val normalized = flattened.keys.associateBy(::normalizeKey)

        if (diagnostic) {
            Log.i(
                TAG,
                "NWD CAN event action=${intent.action} extras=${describeExtras(flattened, includeValues = true)}"
            )
        }

        val fuel = findNumber(flattened, normalized, FUEL_PERCENT_KEYS)
            ?.toFloat()?.takeIf { it in 0f..100f }
        val range = findNumber(flattened, normalized, RANGE_KM_KEYS)
            ?.toFloat()?.takeIf { it in 0f..3_000f }
        val doors = readDoorState(flattened, normalized)

        if (fuel != null || range != null || doors != null) {
            update(
                fuelPercent = fuel,
                rangeKm = range,
                doors = doors,
                source = source ?: intent.action ?: "Vehicle broadcast"
            )
            Log.d(TAG, "Vehicle update from ${intent.action}: fuel=$fuel range=$range doors=$doors")
        } else if (diagnostic || intent.action?.contains("nwd", ignoreCase = true) == true) {
            Log.i(TAG, "Unmapped NWD payload ${intent.action}: ${describeExtras(flattened)}")
        }

        for ((key, value) in flattened) {
            valueAsBytePayload(key, value)?.let(::decodeRaiseFrames)
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
                        doors = decodeRaiseDoors(bytes, index + 3, length),
                        source = "RAISE CAN via NWD"
                    )
                }
                RAISE_TRIP_COMMAND -> if (length >= 7) {
                    val rawRange = ((bytes[index + 7].toInt() and 0xFF) shl 8) or
                        (bytes[index + 8].toInt() and 0xFF)
                    val unit = bytes[index + 9].toInt() and 0xFF
                    val rangeKm = if (unit == RAISE_DISTANCE_MILES) {
                        rawRange * MILES_TO_KM
                    } else {
                        rawRange.toFloat()
                    }
                    if (rangeKm in 0f..3_000f) {
                        update(rangeKm = rangeKm, source = "RAISE CAN via NWD")
                    }
                }
            }
            index = end + 1
        }
    }

    private fun decodeRaiseDoors(bytes: ByteArray, payloadIndex: Int, length: Int): VehicleDoorState {
        val doors = bytes[payloadIndex].toInt() and 0xFF
        val body = if (length >= 2) bytes[payloadIndex + 1].toInt() and 0xFF else 0
        return VehicleDoorState(
            // Raise boxes differ on whether left/right are mirrored for RHD vehicles. The
            // dashboard currently displays aggregate status, for which the distinction is moot.
            frontLeftOpen = doors and 0x80 != 0,
            frontRightOpen = doors and 0x40 != 0,
            rearRightOpen = doors and 0x20 != 0,
            rearLeftOpen = doors and 0x10 != 0,
            trunkOpen = doors and 0x08 != 0,
            hoodOpen = body and 0x80 != 0
        )
    }

    private fun valueAsBytePayload(key: String, value: Any?): ByteArray? = when (value) {
        is ByteArray -> value
        is IntArray -> value.map(Int::toByte).toByteArray()
        is ShortArray -> value.map(Short::toByte).toByteArray()
        is Array<*> -> value.mapNotNull { (it as? Number)?.toByte() }
            .takeIf { it.size == value.size }?.toByteArray()
        is List<*> -> value.mapNotNull { (it as? Number)?.toByte() }
            .takeIf { it.size == value.size }?.toByteArray()
        is String -> parseHexPayload(key, value)
        else -> null
    }

    private fun parseHexPayload(key: String, value: String): ByteArray? {
        val normalizedKey = normalizeKey(key)
        if (RAW_PAYLOAD_KEY_TERMS.none(normalizedKey::contains)) return null
        val tokens = HEX_BYTE_PATTERN.findAll(value).map(MatchResult::value).toList()
        if (tokens.size < 4) return null
        return tokens.map { it.removePrefix("0x").removePrefix("0X").toInt(16).toByte() }.toByteArray()
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

    private fun findNumber(
        values: Map<String, Any?>,
        keys: Map<String, String>,
        candidates: Set<String>
    ): Number? {
        val actual = matchingKey(keys, candidates) ?: return null
        return valueAsNumber(values[actual])
    }

    private fun readDoorState(
        values: Map<String, Any?>,
        keys: Map<String, String>
    ): VehicleDoorState? {
        findNumber(values, keys, DOOR_MASK_KEYS)?.let { return decodeGenericDoorMask(it.toInt()) }

        fun flag(candidates: Set<String>): Boolean? {
            val actual = matchingKey(keys, candidates) ?: return null
            return valueAsBoolean(values[actual])
        }

        val frontLeft = flag(FRONT_LEFT_DOOR_KEYS)
        val frontRight = flag(FRONT_RIGHT_DOOR_KEYS)
        val rearLeft = flag(REAR_LEFT_DOOR_KEYS)
        val rearRight = flag(REAR_RIGHT_DOOR_KEYS)
        val trunk = flag(TRUNK_KEYS)
        val hood = flag(HOOD_KEYS)
        if (listOf(frontLeft, frontRight, rearLeft, rearRight, trunk, hood).all { it == null }) {
            return null
        }
        return VehicleDoorState(
            frontLeftOpen = frontLeft == true,
            frontRightOpen = frontRight == true,
            rearLeftOpen = rearLeft == true,
            rearRightOpen = rearRight == true,
            trunkOpen = trunk == true,
            hoodOpen = hood == true
        )
    }

    private fun flattenExtras(bundle: Bundle, prefix: String = ""): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val value = runCatching { bundle.get(key) }.getOrNull()
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            if (value is Bundle) result.putAll(flattenExtras(value, fullKey))
            else result[fullKey] = value
        }
        return result
    }

    private fun valueAsNumber(value: Any?): Number? = when (value) {
        is Number -> value
        is String -> NUMBER_PATTERN.find(value.trim())?.value?.toFloatOrNull()
        is CharSequence -> NUMBER_PATTERN.find(value.toString().trim())?.value?.toFloatOrNull()
        else -> null
    }

    private fun valueAsBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "1", "true", "yes", "on", "open", "opened" -> true
            "0", "false", "no", "off", "close", "closed" -> false
            else -> null
        }
        else -> null
    }

    private fun matchingKey(keys: Map<String, String>, candidates: Set<String>): String? {
        candidates.firstNotNullOfOrNull(keys::get)?.let { return it }
        // A vendor may wrap the values in a nested Bundle, producing names such as
        // `carInfo.fuelLevel`. Prefer the longest matching suffix to avoid a short,
        // ambiguous field winning over a unit-specific one.
        return keys.entries
            .filter { (normalized, _) -> candidates.any(normalized::endsWith) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    private fun describeExtras(
        values: Map<String, Any?>,
        includeValues: Boolean = false
    ): String = values.entries.joinToString(limit = MAX_LOGGED_EXTRAS, truncated = "…") {
        val value = it.value
        val type = value?.javaClass?.simpleName ?: "null"
        val shown = if (includeValues) diagnosticValue(value) else null
        "${it.key}=$type${shown?.let { text -> "($text)" }.orEmpty()}"
    }

    private fun diagnosticValue(value: Any?): String? = when (value) {
        null -> null
        is Number, is Boolean -> value.toString()
        is CharSequence -> value.toString().take(MAX_LOGGED_VALUE_LENGTH)
        is ByteArray -> value.toHexPreview()
        is IntArray -> value.take(MAX_LOGGED_BYTES).joinToString(" ") { "%02X".format(it and 0xFF) }
        is ShortArray -> value.take(MAX_LOGGED_BYTES).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        else -> null
    }

    private fun ByteArray.toHexPreview(): String = take(MAX_LOGGED_BYTES)
        .joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } +
        if (size > MAX_LOGGED_BYTES) " …($size bytes)" else ""

    private fun decodeGenericDoorMask(raw: Int): VehicleDoorState {
        val decoded = decodeDoorByte(raw)
        // Some NWD CAN plug-ins use low-order bits instead of the Choiceway high-order layout.
        // The dashboard currently needs open/closed aggregate status, so preserve that signal
        // even when this firmware's per-door bit assignment is not known yet.
        return if (raw != 0 && !decoded.anyOpen) decoded.copy(frontLeftOpen = true) else decoded
    }

    /**
     * NWD/Nowada does not publish a stable CAN API. Its system CAN app does, however, keep the
     * action names as strings in its APK. Discovering those constants makes this work across
     * K2001/K2401/K2501 firmware revisions without hard-coding one vendor build's private API.
     */
    private fun discoverAndRegisterNwdActions() {
        if (nwdDiscoveryStarted) return
        nwdDiscoveryStarted = true
        Thread({
            val actions = linkedSetOf<String>()
            actions += NWD_KNOWN_ACTIONS
            runCatching {
                @Suppress("DEPRECATION")
                val packages = appContext.packageManager.getInstalledPackages(0)
                val nwdG5 = isNwdG5Device()
                Log.i(TAG, "NWD CAN discovery device=${deviceIdentity()} nwdG5=$nwdG5")
                packages.asSequence()
                    .flatMap { info ->
                        val name = info.packageName.lowercase()
                        val label = runCatching {
                            info.applicationInfo?.loadLabel(appContext.packageManager)
                                ?.toString()?.lowercase().orEmpty()
                        }.getOrDefault("")
                        val score = when {
                            name.startsWith("com.nwd") -> 100
                            name.contains("raise") || label.contains("raise") -> 95
                            name.contains("canallinone") || label.contains("canallinone") -> 90
                            name.contains("mycar") || label == "my car" -> 80
                            nwdG5 && (name.contains("can") || label.contains("can")) -> 70
                            nwdG5 && (name.contains("mcu") || label.contains("mcu")) -> 60
                            nwdG5 && (name.contains("vehicle") || label.contains("vehicle")) -> 55
                            nwdG5 && (name.contains("carinfo") || label.contains("car info")) -> 50
                            nwdG5 && (name.contains("car") || label == "car") -> 40
                            else -> 0
                        }
                        val app = info.applicationInfo
                        if (score <= 0 || app == null) emptySequence()
                        else sequenceOf(app.sourceDir, *app.splitSourceDirs.orEmpty())
                            .filterNotNull()
                            .map { Triple(score, info.packageName, it) }
                    }
                    .sortedByDescending { it.first }
                    .distinctBy { it.third }
                    .take(MAX_NWD_PACKAGES)
                    .takeWhile { actions.size < MAX_DISCOVERED_ACTIONS }
                    .forEach { (_, packageName, path) ->
                        val found = runCatching { scanNwdActionStrings(File(path)) }
                            .onFailure { Log.w(TAG, "Unable to inspect $packageName", it) }
                            .getOrDefault(emptySet())
                        Log.i(TAG, "NWD CAN candidate=$packageName actions=${found.joinToString()}")
                        actions += found
                    }
            }.onFailure { Log.w(TAG, "Unable to inspect NWD packages", it) }

            if (actions.isEmpty()) return@Thread
            val registeredActions = actions.take(MAX_DISCOVERED_ACTIONS)
            Handler(Looper.getMainLooper()).post {
                if (!registered || nwdReceiverRegistered) return@post
                val filter = IntentFilter().apply { registeredActions.forEach(::addAction) }
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        appContext.registerReceiver(nwdReceiver, filter, Context.RECEIVER_EXPORTED)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        appContext.registerReceiver(nwdReceiver, filter)
                    }
                    nwdReceiverRegistered = true
                    Log.i(
                        TAG,
                        "NWD CAN receiver started with ${registeredActions.size} actions: " +
                            registeredActions.joinToString()
                    )
                }.onFailure { Log.w(TAG, "Unable to register NWD CAN receiver", it) }
            }
        }, "NwdCanDiscovery").apply { isDaemon = true }.start()
    }

    private fun isNwdG5Device(): Boolean {
        val identity = deviceIdentity().lowercase()
        return identity.contains("nowada") || identity.contains("nwd_g5") ||
            identity.contains("nwd g5") || identity.contains("k2401") ||
            identity.contains("s212851")
    }

    private fun deviceIdentity(): String = listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.DISPLAY
        ).joinToString(" | ")

    private fun scanNwdActionStrings(apk: File): Set<String> {
        if (!apk.isFile) return emptySet()
        return ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(DEX_NAME_PATTERN) }
                .flatMap { entry ->
                    zip.getInputStream(entry).buffered().use { input ->
                        readVehicleActionStrings(input)
                    }.asSequence()
                }
                .take(MAX_DISCOVERED_ACTIONS)
                .toSet()
        }
    }

    private fun readVehicleActionStrings(input: java.io.InputStream): Set<String> {
        val result = linkedSetOf<String>()
        val current = StringBuilder()
        var overflow = false
        while (true) {
            val byte = input.read()
            if (byte < 0) break
            val char = byte.toChar()
            if (char.isAsciiActionCharacter()) {
                if (!overflow) {
                    if (current.length < MAX_ACTION_LENGTH) current.append(char)
                    else {
                        current.setLength(0)
                        overflow = true
                    }
                }
            } else {
                if (!overflow && current.length >= MIN_ACTION_LENGTH) {
                    current.toString().takeIf(::isProbableVehicleAction)?.let(result::add)
                    if (result.size >= MAX_DISCOVERED_ACTIONS) break
                }
                current.setLength(0)
                overflow = false
            }
        }
        if (!overflow && current.length >= MIN_ACTION_LENGTH && result.size < MAX_DISCOVERED_ACTIONS) {
            current.toString().takeIf(::isProbableVehicleAction)?.let(result::add)
        }
        return result
    }

    private fun Char.isAsciiActionCharacter(): Boolean =
        isLetterOrDigit() || this == '_' || this == '.' || this == '$' || this == ':' || this == '-'

    private fun isProbableVehicleAction(value: String): Boolean {
        if (value.length !in MIN_ACTION_LENGTH..MAX_ACTION_LENGTH ||
            !value.first().isLetter() || '.' !in value || '/' in value || '$' in value
        ) return false
        val lower = value.lowercase()
        val vehicle = lower.contains(".can") || lower.contains("_can") ||
            lower.contains("can_") || VEHICLE_ACTION_TERMS.any(lower::contains)
        return vehicle && !value.endsWith(".java", ignoreCase = true)
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
            "fuelpercent", "fuellevelpercent", "remainingfuelpercent", "tankpercent",
            "fuellevel", "fuelrate", "oilpercent", "oillevelpercent", "carrateoil"
        )
        private val RANGE_KM_KEYS = setOf(
            "rangekm", "remainingrangekm", "distancetoemptykm", "cruisingrangekm",
            "range", "remainingrange", "distancetoempty", "cruisingrange", "endurance",
            "endurancemileage", "remainmileage", "surplusmileage"
        )
        private val DOOR_MASK_KEYS = setOf(
            "doormask", "doorstate", "doorstatus", "cardoor", "cardoordata", "doors"
        )
        private val FRONT_LEFT_DOOR_KEYS = setOf(
            "frontleftdoor", "leftfrontdoor", "frontleftdooropen", "lfdoor", "flDoor"
        ).map(::normalizeStatic).toSet()
        private val FRONT_RIGHT_DOOR_KEYS = setOf(
            "frontrightdoor", "rightfrontdoor", "frontrightdooropen", "rfdoor", "frDoor"
        ).map(::normalizeStatic).toSet()
        private val REAR_LEFT_DOOR_KEYS = setOf(
            "rearleftdoor", "leftreardoor", "rearleftdooropen", "lrdoor", "rldoor"
        )
        private val REAR_RIGHT_DOOR_KEYS = setOf(
            "rearrightdoor", "rightreardoor", "rearrightdooropen", "rrdoor"
        )
        private val TRUNK_KEYS = setOf("trunk", "trunkopen", "tailgate", "tailgateopen", "bootopen")
        private val HOOD_KEYS = setOf("hood", "hoodopen", "bonnet", "bonnetopen")

        private val NWD_KNOWN_ACTIONS = setOf(
            "com.nwd.action.CAN_DATA",
            "com.nwd.action.CAN_DATA_CHANGED",
            "com.nwd.action.ACTION_CAN_DATA",
            "com.nwd.action.ACTION_CAN_DATA_CHANGED",
            "com.nwd.action.CAR_INFO",
            "com.nwd.canbus.CAN_DATA",
            "com.nwd.canbus.CAN_INFO",
            "com.nwd.carinfo"
        )
        private val VEHICLE_ACTION_TERMS = setOf("carinfo", "vehicle", "door", "fuel", "range", "mcu")
        private val DEX_NAME_PATTERN = Regex("classes(\\d*)?\\.dex")
        private val NUMBER_PATTERN = Regex("[-+]?\\d+(?:\\.\\d+)?")
        private val HEX_BYTE_PATTERN = Regex("(?i)(?:0x)?[0-9a-f]{2}")
        private val RAW_PAYLOAD_KEY_TERMS = setOf("data", "can", "mcu", "raw", "frame", "packet")
        private const val MAX_DISCOVERED_ACTIONS = 200
        private const val MAX_NWD_PACKAGES = 12
        private const val MIN_ACTION_LENGTH = 8
        private const val MAX_ACTION_LENGTH = 180
        private const val MAX_LOGGED_EXTRAS = 30
        private const val MAX_LOGGED_BYTES = 96
        private const val MAX_LOGGED_VALUE_LENGTH = 160

        private fun normalizeStatic(key: String): String = key.lowercase().filter(Char::isLetterOrDigit)

        private const val RAISE_HEADER = 0x2E
        private const val RAISE_TRIP_COMMAND = 0x21
        private const val RAISE_DOORS_COMMAND = 0x24
        private const val RAISE_DISTANCE_MILES = 0x01
        private const val MILES_TO_KM = 1.609344f
    }
}
