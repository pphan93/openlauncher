package com.openlauncher.app.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val isSystemApp: Boolean = false,
    val isLaunchable: Boolean = false
)
