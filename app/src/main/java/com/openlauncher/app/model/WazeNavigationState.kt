package com.openlauncher.app.model

data class WazeNavigationState(
    val active: Boolean = false,
    val instruction: String = "",
    val distance: String = "",
    val eta: String = ""
)
