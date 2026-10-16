package com.openlauncher.app.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.openlauncher.app.model.WazeNavigationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads Waze's visible guidance banner when its notification does not expose the next turn.
 * Notification text is preferred by [WazeNavigationFeed], while this Waze-only accessibility
 * service provides the fallback used by head-unit builds with generic navigation notifications.
 */
class WazeNavigationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        isConnected.value = true
        Log.i(TAG, "Waze navigation accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != WazeNavigationFeed.WAZE_PACKAGE) return
        WazeNavigationFeed.readScreen(event, rootInActiveWindow)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WazeNavigation"
        val isConnected = MutableStateFlow(false)
    }
}

object WazeNavigationFeed {
    const val WAZE_PACKAGE = "com.waze"
    private const val TAG = "WazeNavigation"
    private const val READ_INTERVAL_MS = 1_000L

    private val _state = MutableStateFlow(WazeNavigationState())
    val state: StateFlow<WazeNavigationState> = _state.asStateFlow()

    @Volatile private var lastReadAt = 0L
    @Volatile private var runningNotificationKey: String? = null

    fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WAZE_PACKAGE) return

        parseNotification(sbn)?.let { parsed ->
            runningNotificationKey = sbn.key
            if (parsed != _state.value) {
                _state.value = parsed
                Log.i(TAG, "Waze notification turn: ${parsed.distance} ${parsed.instruction} (${parsed.eta})")
            }
            return
        }

        // Some Waze builds expose only a generic ongoing notification. Keep its key so removing
        // it still clears guidance captured from the accessibility fallback when the route ends.
        if (sbn.isOngoing) runningNotificationKey = sbn.key
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == runningNotificationKey) {
            runningNotificationKey = null
            _state.value = WazeNavigationState()
        }
    }

    fun clear() {
        runningNotificationKey = null
        _state.value = WazeNavigationState()
    }

    internal fun readScreen(
        event: AccessibilityEvent,
        activeWindowRoot: AccessibilityNodeInfo? = null
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastReadAt < READ_INTERVAL_MS) return

        val root = runCatching {
            event.source?.let(::rootOf) ?: activeWindowRoot
        }.getOrNull() ?: return
        val bounds = Rect().also(root::getBoundsInScreen)
        if (bounds.height() <= 0) return
        lastReadAt = now

        val pieces = ArrayList<ScreenPiece>()
        runCatching { collectVisibleText(root, pieces, depth = 0) }
            .onFailure { Log.w(TAG, "Unable to read Waze guidance screen", it) }
        val parsed = parseScreen(pieces, bounds.top, bounds.height()) ?: return
        if (parsed != _state.value) {
            _state.value = parsed
            Log.i(TAG, "Waze turn: ${parsed.distance} ${parsed.instruction} (${parsed.eta})")
        }
    }

    private fun parseNotification(sbn: StatusBarNotification): WazeNavigationState? {
        val pieces = ArrayList<String>()
        val extras = sbn.notification.extras
        STANDARD_NOTIFICATION_KEYS.forEach { key ->
            collectNotificationText(extras.get(key), pieces, depth = 0)
        }

        // Android Auto / AndroidX Car App notifications can keep display strings in nested
        // extension bundles rather than the standard title/text fields.
        extras.keySet()
            .filterNot { it in STANDARD_NOTIFICATION_KEYS }
            .forEach { key -> collectNotificationText(extras.get(key), pieces, depth = 0) }

        val lines = pieces
            .flatMap { it.split(NOTIFICATION_SEPARATOR) }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

        val distanceSource = lines.firstOrNull(DISTANCE::containsMatchIn) ?: return null
        val distance = DISTANCE.find(distanceSource)?.value ?: return null
        val inlineInstruction = distanceSource
            .replaceFirst(DISTANCE, "")
            .trim(' ', '-', ':', '\u2022', '\u00B7', '\u2013', '\u2014')
            .takeIf(::isInstructionText)
        val instruction = inlineInstruction ?: lines.firstOrNull(::isInstructionText) ?: "Continue"
        val eta = lines
            .filter { it != distanceSource && (DURATION.containsMatchIn(it) || CLOCK.containsMatchIn(it)) }
            .distinct()
            .joinToString(" \u00B7 ")

        return WazeNavigationState(
            active = true,
            instruction = instruction,
            distance = distance,
            eta = eta
        )
    }

    private fun collectNotificationText(value: Any?, destination: MutableList<String>, depth: Int) {
        if (value == null || depth > 4) return
        when (value) {
            is CharSequence -> value.toString().trim().takeIf(String::isNotEmpty)?.let(destination::add)
            is Array<*> -> value.forEach { collectNotificationText(it, destination, depth + 1) }
            is Bundle -> value.keySet().forEach {
                collectNotificationText(value.get(it), destination, depth + 1)
            }
        }
    }

    private fun isInstructionText(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized in GENERIC_NOTIFICATION_TEXT || normalized in THEN_WORDS) return false
        if (DISTANCE.containsMatchIn(text) || DURATION.containsMatchIn(text) || CLOCK.matches(text)) return false
        return text.length >= 3
    }

    private fun rootOf(start: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var node = start
        while (true) node = node.parent ?: return node
    }

    private fun collectVisibleText(
        node: AccessibilityNodeInfo,
        destination: MutableList<ScreenPiece>,
        depth: Int
    ) {
        if (depth > 40 || !node.isVisibleToUser) return
        val bounds = Rect().also(node::getBoundsInScreen)
        node.text?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let {
            destination += ScreenPiece(it, bounds.top, described = false)
        }
        node.contentDescription?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let {
            destination += ScreenPiece(it, bounds.top, described = true)
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collectVisibleText(it, destination, depth + 1) }
        }
    }

    internal fun parseScreen(
        pieces: List<ScreenPiece>,
        screenTop: Int,
        screenHeight: Int
    ): WazeNavigationState? {
        if (screenHeight <= 0) return null
        val topLimit = screenTop + screenHeight / 4
        val top = pieces.filter { it.top < topLimit }
        val distancePiece = top.firstOrNull {
            !it.described && DISTANCE.containsMatchIn(it.text)
        } ?: return null
        val street = top.firstOrNull {
            !it.described && it !== distancePiece && !DISTANCE.containsMatchIn(it.text) &&
                it.text.lowercase() !in THEN_WORDS
        }?.text
        val maneuver = top.firstOrNull {
            it.described && !DISTANCE.containsMatchIn(it.text) && it.text != street &&
                it.text.lowercase() !in THEN_WORDS
        }?.text
        val instruction = listOfNotNull(maneuver, street).distinct().joinToString(", ")
            .ifBlank { "Continue" }

        val bottomStart = screenTop + screenHeight * 2 / 3
        val bottom = pieces.filter { !it.described && it.top >= bottomStart }
        val tripDetails = bottom
            .flatMap { it.text.split('\u2022', '\u00B7') }
            .map(String::trim)
            .filter { DURATION.containsMatchIn(it) || DISTANCE.containsMatchIn(it) }
        val arrival = bottom.firstOrNull { CLOCK.matches(it.text) }?.text

        return WazeNavigationState(
            active = true,
            instruction = instruction,
            distance = DISTANCE.find(distancePiece.text)?.value ?: distancePiece.text,
            eta = (tripDetails + listOfNotNull(arrival)).distinct().joinToString(" \u00B7 ")
        )
    }

    internal data class ScreenPiece(
        val text: String,
        val top: Int,
        val described: Boolean
    )

    private val DISTANCE = Regex("""\d+(?:[.,]\d+)?\s?(?:km|m|mi|ft|yd)\b""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""\d{1,2}[:h.]\d{2}(?:\s?[AaPp]\.?[Mm]\.?)?""")
    private val DURATION = Regex("""\b\d+\s*(?:min|h|hr)\b""", RegexOption.IGNORE_CASE)
    private val NOTIFICATION_SEPARATOR = Regex("""[\n\r\u2022\u00B7]+""")
    private val STANDARD_NOTIFICATION_KEYS = listOf(
        Notification.EXTRA_TITLE,
        Notification.EXTRA_TITLE_BIG,
        Notification.EXTRA_TEXT,
        Notification.EXTRA_BIG_TEXT,
        Notification.EXTRA_SUB_TEXT,
        Notification.EXTRA_SUMMARY_TEXT,
        Notification.EXTRA_INFO_TEXT,
        Notification.EXTRA_TEXT_LINES
    )
    private val GENERIC_NOTIFICATION_TEXT = setOf(
        "waze",
        "waze is running",
        "driving with waze",
        "tap to open waze",
        "navigation in progress"
    )
    private val THEN_WORDS = setOf("then", "puis", "dann", "luego", "poi", "depois", "daarna", "potem")
}
