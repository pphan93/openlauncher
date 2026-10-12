package com.openlauncher.app.util

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.util.Log

internal enum class WazeLaunchResult {
    REQUESTED,
    NOT_INSTALLED,
    FAILED
}

/** Requests Waze as a bounded freeform task on head units that support desktop/freeform windows. */
internal object WazeFreeformLauncher {
    private const val TAG = "WazeFreeformLauncher"
    private const val WAZE_PACKAGE = "com.waze"
    private const val WINDOWING_MODE_FREEFORM = 5

    fun launch(context: Context, bounds: Rect): WazeLaunchResult {
        val intent = context.packageManager.getLaunchIntentForPackage(WAZE_PACKAGE)
            ?: return WazeLaunchResult.NOT_INSTALLED
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        )

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
                // NWD enables freeform window management in its firmware. This method is hidden
                // from normal SDK apps, so use it only as a best-effort hint; launch bounds and
                // LAUNCH_ADJACENT remain as public-API fallbacks if reflection is blocked.
                runCatching {
                    ActivityOptions::class.java
                        .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                        .invoke(options, WINDOWING_MODE_FREEFORM)
                }.onFailure { Log.d(TAG, "Freeform windowing hint unavailable", it) }
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
            WazeLaunchResult.REQUESTED
        }.onFailure {
            Log.w(TAG, "Unable to launch Waze", it)
        }.getOrDefault(WazeLaunchResult.FAILED)
    }
}
