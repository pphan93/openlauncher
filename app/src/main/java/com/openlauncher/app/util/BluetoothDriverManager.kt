package com.openlauncher.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothDriverDevice(
    val address: String,
    val name: String,
    val connected: Boolean
)

/** Tracks paired phones through Android's public Bluetooth profile APIs. */
class BluetoothDriverManager(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val _devices = MutableStateFlow<List<BluetoothDriverDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDriverDevice>> = _devices.asStateFlow()
    private val proxies = mutableMapOf<Int, BluetoothProfile>()
    private var receiverRegistered = false
    private var vendorDeviceName: String? = null
    private var vendorConnected = false

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies[profile] = proxy
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
            refresh()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> updateConnection(intent, true)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> updateConnection(intent, false)
                VENDOR_DEVICE_NAME_ACTION -> updateVendorName(
                    intent.getStringExtra(VENDOR_EXTRA_STR)
                )
                VENDOR_HFP_ACTION, VENDOR_HFP_GET_ACTION -> updateVendorConnection(
                    intentNumberExtra(intent, VENDOR_EXTRA_INT)?.toInt()
                )
                else -> refresh()
            }
        }
    }

    fun start() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(VENDOR_DEVICE_NAME_ACTION)
                addAction(VENDOR_HFP_ACTION)
                addAction(VENDOR_HFP_GET_ACTION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        }
        refresh()
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        if (!hasPermission()) {
            _devices.value = emptyList()
            closeProxies()
            return
        }
        val bluetoothAdapter = adapter ?: return
        if (!bluetoothAdapter.isEnabled) {
            _devices.value = emptyList()
            closeProxies()
            return
        }

        if (proxies.isEmpty()) {
            bluetoothAdapter.getProfileProxy(appContext, listener, BluetoothProfile.A2DP)
            bluetoothAdapter.getProfileProxy(appContext, listener, BluetoothProfile.HEADSET)
        }

        val connectedAddresses = buildSet {
            proxies.values.flatMap { runCatching { it.connectedDevices }.getOrDefault(emptyList()) }
                .forEach { add(it.address.uppercase()) }
            runCatching { bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty() }
                .getOrDefault(emptyList())
                .forEach { add(it.address.uppercase()) }
        }
        val standardDevices = bluetoothAdapter.bondedDevices
            .map { device ->
                BluetoothDriverDevice(
                    address = device.address.uppercase(),
                    name = device.name?.takeIf(String::isNotBlank) ?: "Bluetooth device",
                    connected = device.address.uppercase() in connectedAddresses
                )
            }
            .sortedWith(compareByDescending<BluetoothDriverDevice> { it.connected }.thenBy { it.name.lowercase() })
        publishDevices(standardDevices)
    }

    @SuppressLint("MissingPermission")
    private fun updateConnection(intent: Intent, connected: Boolean) {
        if (!hasPermission()) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return
        val address = device.address.uppercase()
        val existing = _devices.value.associateBy { it.address }.toMutableMap()
        existing[address] = BluetoothDriverDevice(
            address = address,
            name = device.name?.takeIf(String::isNotBlank)
                ?: existing[address]?.name
                ?: "Bluetooth device",
            connected = connected
        )
        _devices.value = existing.values
            .sortedWith(compareByDescending<BluetoothDriverDevice> { it.connected }.thenBy { it.name.lowercase() })
    }

    private fun updateVendorName(name: String?) {
        vendorDeviceName = name?.trim()?.takeIf(String::isNotEmpty) ?: return
        vendorConnected = true
        publishDevices(_devices.value.filterNot { it.address.startsWith(VENDOR_ADDRESS_PREFIX) })
    }

    private fun updateVendorConnection(hfpState: Int?) {
        if (hfpState !in 0..6) return
        vendorConnected = hfpState!! >= 3
        publishDevices(_devices.value.filterNot { it.address.startsWith(VENDOR_ADDRESS_PREFIX) })
    }

    private fun publishDevices(standardDevices: List<BluetoothDriverDevice>) {
        val vendor = vendorDeviceName?.let { name ->
            BluetoothDriverDevice(
                address = VENDOR_ADDRESS_PREFIX + name.lowercase().filter(Char::isLetterOrDigit),
                name = name,
                connected = vendorConnected
            )
        }
        _devices.value = (standardDevices + listOfNotNull(vendor))
            .distinctBy { it.address }
            .sortedWith(compareByDescending<BluetoothDriverDevice> { it.connected }.thenBy { it.name.lowercase() })
    }

    private fun intentNumberExtra(intent: Intent, key: String): Number? {
        @Suppress("DEPRECATION")
        return runCatching { intent.extras?.get(key) as? Number }.getOrNull()
    }

    fun stop() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        closeProxies()
    }

    @SuppressLint("MissingPermission")
    private fun closeProxies() {
        val bluetoothAdapter = adapter
        proxies.toMap().forEach { (profile, proxy) ->
            runCatching { bluetoothAdapter?.closeProfileProxy(profile, proxy) }
        }
        proxies.clear()
    }

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    companion object {
        private const val VENDOR_PREFIX = "com.szchoiceway.btsuite.HBCP_EVT_"
        private const val VENDOR_DEVICE_NAME_ACTION = VENDOR_PREFIX + "CUR_CONNECTED_DEVICE_NAME"
        private const val VENDOR_HFP_ACTION = VENDOR_PREFIX + "HSHF_STATUS"
        private const val VENDOR_HFP_GET_ACTION = VENDOR_PREFIX + "HSHF_GET_STATUS"
        private const val VENDOR_EXTRA_INT = "com.szchoiceway.btsuite.DATA_INT"
        private const val VENDOR_EXTRA_STR = "com.szchoiceway.btsuite.DATA_STR"
        private const val VENDOR_ADDRESS_PREFIX = "VENDOR:"
    }
}
