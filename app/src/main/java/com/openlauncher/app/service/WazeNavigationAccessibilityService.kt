package com.openlauncher.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
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
 * Reads Waze's visible guidance banner because its ongoing notification does not expose the
 * next turn on this head unit. The accessibility configuration restricts events to Waze only.
 * This follows the screen-reading approach demonstrated by the GPL-licensed Dashwheel project,
 * with an implementation specific to Open Launcher's much smaller navigation model.
 */
class WazeNavigationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        isConnected.value = true
        Log.i(TAG, "Waze navigation accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != WazeNavigationFeed.WAZE_PACKAGE) return
        WazeNavigationFeed.readScreen(event)
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
        if (sbn.packageName == WAZE_PACKAGE && sbn.isOngoing) {
            runningNotificationKey = sbn.key
        }
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

    internal fun readScreen(event: AccessibilityEvent) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastReadAt < READ_INTERVAL_MS) return
        lastReadAt = now

        val root = runCatching { event.source?.let(::rootOf) }.getOrNull() ?: return
        val bounds = Rect().also(root::getBoundsInScreen)
        if (bounds.height() <= 0) return

        val pieces = ArrayList<ScreenPiece>()
        runCatching { collectVisibleText(root, pieces, depth = 0) }
            .onFailure { Log.w(TAG, "Unable to read Waze guidance screen", it) }
        val parsed = parseScreen(pieces, bounds.top, bounds.height()) ?: return
        if (parsed != _state.value) {
            _state.value = parsed
            Log.i(TAG, "Waze turn: ${parsed.distance} ${parsed.instruction} (${parsed.eta})")
        }
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
            !it.described && DISTANCE.matches(it.text)
        } ?: return null
        val street = top.firstOrNull {
            !it.described && it !== distancePiece && !DISTANCE.matches(it.text) &&
                it.text.lowercase() !in THEN_WORDS
        }?.text
        val maneuver = top.firstOrNull {
            it.described && !DISTANCE.matches(it.text) && it.text != street &&
                it.text.lowercase() !in THEN_WORDS
        }?.text
        val instruction = listOfNotNull(maneuver, street).distinct().joinToString(", ")
            .ifBlank { "Continue" }

        val bottomStart = screenTop + screenHeight * 2 / 3
        val bottom = pieces.filter { !it.described && it.top >= bottomStart }
        val tripDetails = bottom
            .flatMap { it.text.split('•', '·') }
            .map(String::trim)
            .filter { DURATION.containsMatchIn(it) || DISTANCE.matches(it) }
        val arrival = bottom.firstOrNull { CLOCK.matches(it.text) }?.text

        return WazeNavigationState(
            active = true,
            instruction = instruction,
            distance = distancePiece.text,
            eta = (tripDetails + listOfNotNull(arrival)).distinct().joinToString(" · ")
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
    private val THEN_WORDS = setOf("then", "puis", "dann", "luego", "poi", "depois", "daarna", "potem")
}
