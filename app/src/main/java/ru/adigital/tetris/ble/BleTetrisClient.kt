package ru.adigital.tetris.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object BleTetrisConfig {
    const val LOG_TAG = "TetrisBLE"
    val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    /** Phone -> ESP32: one blink cycle on onboard LED (see TetrisBLE_Step1_Connect.ino). */
    const val CMD_BLINK: Byte = 0x01
}

fun bleConnectPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}

fun Context.hasBleConnectPermissions(): Boolean {
    return bleConnectPermissions().all { perm ->
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }
}

class BleTetrisClient(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun postUi(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun notifyBusy(onBusy: (Boolean) -> Unit, value: Boolean) {
        postUi { onBusy(value) }
    }

    private fun notifyMessage(onUserMessage: (String) -> Unit, code: String) {
        postUi { onUserMessage(code) }
    }
    private var scanner: BluetoothLeScanner? = null
    private var activeScanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private val scanStarted = AtomicBoolean(false)
    private var scanTimeoutRunnable: Runnable? = null
    /** Cleared in [release]; used from GATT callbacks for UI messages. */
    private var sessionUserMessage: ((String) -> Unit)? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val stateStr = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING"
                BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
                BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
                else -> "state=$newState"
            }
            Log.d(BleTetrisConfig.LOG_TAG, "onConnectionStateChange status=$status $stateStr")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(BleTetrisConfig.LOG_TAG, "GATT connected, discoverServices()")
                        gatt.discoverServices()
                    } else {
                        Log.e(
                            BleTetrisConfig.LOG_TAG,
                            "GATT connected callback with non-success status=$status",
                        )
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(BleTetrisConfig.LOG_TAG, "GATT disconnected (status=$status)")
                    commandCharacteristic = null
                    gatt.close()
                    if (this@BleTetrisClient.gatt == gatt) {
                        this@BleTetrisClient.gatt = null
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(BleTetrisConfig.LOG_TAG, "onServicesDiscovered status=$status")
            val notify = sessionUserMessage
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val svc: BluetoothGattService? = gatt.getService(BleTetrisConfig.SERVICE_UUID)
                if (svc != null) {
                    commandCharacteristic = svc.getCharacteristic(BleTetrisConfig.CHARACTERISTIC_UUID)
                    if (commandCharacteristic == null) {
                        Log.e(BleTetrisConfig.LOG_TAG, "Characteristic ${BleTetrisConfig.CHARACTERISTIC_UUID} missing")
                        notify?.let { notifyMessage(it, "SERVICE_MISSING") }
                    } else {
                        Log.i(BleTetrisConfig.LOG_TAG, "Service ${BleTetrisConfig.SERVICE_UUID} found — handshake OK")
                        Log.i(BleTetrisConfig.LOG_TAG, "BLE session ready (UI may show connected)")
                        notify?.let { notifyMessage(it, "CONNECTED") }
                    }
                } else {
                    Log.e(BleTetrisConfig.LOG_TAG, "Expected service UUID not found after discovery")
                    notify?.let { notifyMessage(it, "SERVICE_MISSING") }
                }
            } else {
                Log.e(BleTetrisConfig.LOG_TAG, "discoverServices failed, status=$status")
                notify?.let { notifyMessage(it, "DISCOVER_FAILED") }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.d(
                BleTetrisConfig.LOG_TAG,
                "onCharacteristicWrite uuid=${characteristic.uuid} status=$status",
            )
        }
    }

    fun release() {
        cancelScanTimeout()
        scanStarted.set(false)
        stopScanInternal()
        sessionUserMessage = null
        commandCharacteristic = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    /**
     * @param onUserMessage localized short message for the UI
     * @param onBusy scanning / connecting in progress
     */
    fun beginConnect(onUserMessage: (String) -> Unit, onBusy: (Boolean) -> Unit) {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null) {
            Log.e(BleTetrisConfig.LOG_TAG, "BluetoothAdapter is null")
            notifyMessage(onUserMessage, "NO_ADAPTER")
            return
        }
        if (!adapter.isEnabled) {
            Log.e(BleTetrisConfig.LOG_TAG, "Bluetooth is disabled")
            notifyMessage(onUserMessage, "BT_OFF")
            return
        }

        release()
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(BleTetrisConfig.LOG_TAG, "BluetoothLeScanner is null")
            notifyMessage(onUserMessage, "NO_ADAPTER")
            return
        }

        sessionUserMessage = onUserMessage
        notifyBusy(onBusy, true)
        Log.i(BleTetrisConfig.LOG_TAG, "BLE scan started (filter service=${BleTetrisConfig.SERVICE_UUID})")

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleTetrisConfig.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                Log.i(
                    BleTetrisConfig.LOG_TAG,
                    "onScanResult name=${device.name} address=${device.address}",
                )
                postUi {
                    if (!scanStarted.compareAndSet(true, false)) {
                        return@postUi
                    }
                    stopScanInternal()
                    cancelScanTimeout()
                    connectToDevice(device, onBusy)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(BleTetrisConfig.LOG_TAG, "onScanFailed errorCode=$errorCode")
                scanStarted.set(false)
                sessionUserMessage = null
                notifyBusy(onBusy, false)
            }
        }

        activeScanCallback = callback
        scanStarted.set(true)
        try {
            scanner?.startScan(listOf(filter), settings, callback)
        } catch (e: SecurityException) {
            Log.e(BleTetrisConfig.LOG_TAG, "startScan SecurityException", e)
            scanStarted.set(false)
            sessionUserMessage = null
            notifyBusy(onBusy, false)
            return
        }

        scanTimeoutRunnable = Runnable {
            if (!scanStarted.get()) {
                return@Runnable
            }
            Log.e(BleTetrisConfig.LOG_TAG, "Scan timeout — no device with service UUID in advertisement")
            scanStarted.set(false)
            stopScanInternal()
            notifyBusy(onBusy, false)
            notifyMessage(onUserMessage, "TIMEOUT")
        }
        mainHandler.postDelayed(scanTimeoutRunnable!!, SCAN_TIMEOUT_MS)
    }

    /**
     * Queues a write of [BleTetrisConfig.CMD_BLINK] to the controller. Returns false if not connected
     * or characteristic is not ready.
     */
    fun requestBlink(): Boolean {
        val g = gatt ?: run {
            Log.w(BleTetrisConfig.LOG_TAG, "requestBlink: GATT not connected")
            return false
        }
        val ch = commandCharacteristic ?: run {
            Log.w(BleTetrisConfig.LOG_TAG, "requestBlink: characteristic not cached")
            return false
        }
        val payload = byteArrayOf(BleTetrisConfig.CMD_BLINK)
        return try {
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = g.writeCharacteristic(
                    ch,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )
                result == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.setValue(payload)
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
            if (ok) {
                Log.i(BleTetrisConfig.LOG_TAG, "requestBlink: write queued (CMD_BLINK)")
            } else {
                Log.e(BleTetrisConfig.LOG_TAG, "requestBlink: writeCharacteristic returned false / non-success")
            }
            ok
        } catch (e: SecurityException) {
            Log.e(BleTetrisConfig.LOG_TAG, "requestBlink: SecurityException", e)
            false
        }
    }

    private fun connectToDevice(device: BluetoothDevice, onBusy: (Boolean) -> Unit) {
        Log.i(BleTetrisConfig.LOG_TAG, "connectGatt(${device.address})")
        try {
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e(BleTetrisConfig.LOG_TAG, "connectGatt SecurityException", e)
        }
        notifyBusy(onBusy, false)
    }

    private fun stopScanInternal() {
        val cb = activeScanCallback ?: return
        try {
            scanner?.stopScan(cb)
        } catch (e: SecurityException) {
            Log.e(BleTetrisConfig.LOG_TAG, "stopScan SecurityException", e)
        }
        activeScanCallback = null
    }

    private fun cancelScanTimeout() {
        scanTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        scanTimeoutRunnable = null
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 15_000L
    }
}
