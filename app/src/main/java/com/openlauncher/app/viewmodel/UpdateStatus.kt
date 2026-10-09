package com.openlauncher.app.viewmodel

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val version: String) : UpdateStatus
    data class Downloading(val version: String, val progressPercent: Int?) : UpdateStatus
    data class AwaitingInstallPermission(val version: String, val apkUri: String) : UpdateStatus
    data class AwaitingInstallConfirmation(val version: String, val apkUri: String) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}
