package com.dsikic.cluecose.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Libre 3 scan filter: the data service UUID
 * (`089810CC-EF89-11E9-81B4-2A2AE2DBCCE4`, iOS `LibreSensorGATT.serviceUUID`).
 * The scanner only ever reports devices advertising this service.
 */
object LibreGattUuids {
    const val DATA_SERVICE = "089810CC-EF89-11E9-81B4-2A2AE2DBCCE4"

    /**
     * Security-service UUID PREFIX (`0898203A-…`, iOS `LibreSensorGATT`).
     * The full suffix is not in this checkout, so [GattSession] resolves the
     * security service (and its cert/challenge/command characteristics) by
     * this prefix at runtime. The handle-based mapping in APP_WIRING_SPEC.md
     * (CERT_HANDSHAKE = 0x002d / CHALLENGE = 0x002a) is superseded: both
     * characteristics live on the security service, not the data service.
     */
    const val SECURITY_SERVICE_PREFIX = "0898203A"
}

/**
 * One scan hit: advertised name + MAC + RSSI. Mirrors iOS `DiscoveredSensor`.
 * Libre advertises its BLE address in the local name (that is the match key
 * for NFC-paired targets).
 */
data class DiscoveredSensor(
    val name: String,
    val address: String,
    val rssi: Int,
)

/**
 * Wraps [BluetoothLeScanner] with a synchronous, blocking scan API:
 * "scan up to [timeout] ms, keep the strongest advert per device, prefer the
 * target, fall back to the best Libre advert". Runs on a background thread
 * (the caller never blocks the main thread) and returns [null] when nothing
 * Libre-like was seen in the window.
 *
 * The sensor opens a connection window only about once a minute, so a real
 * discovery pass is long (the iOS reader uses 75 s); the timeout is a
 * parameter for testability.
 */
class SensorScanner(context: Context) {

    private val appContext: Context = context.applicationContext

    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Throws when no BLE adapter is present (device without hardware). */
    private fun checkAdapter(): BluetoothAdapter {
        return adapter ?: throw SensorScanError.BleNotAvailable
    }

    private fun granted(vararg permissions: String): Boolean =
        permissions.all { appContext.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }

    private fun checkPermissions() {
        if (adapter?.isEnabled != true) throw SensorScanError.BleOff
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            // API 31+: BLUETOOTH_SCAN (neverForLocation) + BLUETOOTH_CONNECT.
            if (!granted(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)) {
                throw SensorScanError.MissingPermission
            }
        } else {
            // API ≤ 30: location permission is required for scan results.
            if (!granted(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                throw SensorScanError.MissingPermission
            }
        }
    }

    /**
     * @return the target advert when seen (hex-normalized name/address match),
     *   otherwise the strongest advert seen, otherwise `null`.
     */
    @SuppressLint("MissingPermission") // guarded by checkPermissions()
    fun scanOnce(
        targetName: String? = null,
        timeoutMs: Long = 75_000,
    ): DiscoveredSensor? {
        val btAdapter = checkAdapter()
        checkPermissions()
        val scanner: BluetoothLeScanner = btAdapter.bluetoothLeScanner ?: throw SensorScanError.BleNotAvailable

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(LibreGattUuids.DATA_SERVICE))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val best = java.util.concurrent.ConcurrentHashMap<String, DiscoveredSensor>()
        val latch = CountDownLatch(1)

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val r = result ?: return
                val name = r.device.name ?: r.scanRecord?.deviceName ?: ""
                val hit = DiscoveredSensor(name, r.device.address, r.rssi)
                // Dedupe by address, keep the strongest RSSI.
                val prev = best[r.device.address]
                if (prev == null || hit.rssi > prev.rssi) {
                    best[r.device.address] = hit
                    Log.d(TAG, "scan hit: $name @ ${r.device.address} rssi=${r.rssi}")
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>?) {
                results?.forEach { onScanResult(0, it) }
            }
        }

        Log.d(TAG, "scan start target=${targetName ?: "any"} for ${timeoutMs} ms")
        try {
            scanner.startScan(listOf(filter), settings, callback)
        } catch (e: SecurityException) {
            throw SensorScanError.MissingPermission
        }
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {
                Log.w(TAG, "stopScan: ${e.message}")
            }
        }

        if (best.isEmpty()) return null
        // Prefer the NFC-reported target (hex-only, case-insensitive compare,
        // iOS `normalizedBLEAddress`); the sensor advertises its address as
        // the name, so compare against both name and MAC.
        val targetNorm = normalizedAddress(targetName)
        if (targetNorm != null) {
            for (hit in best.values) {
                if (normalizedAddress(hit.name) == targetNorm || normalizedAddress(hit.address) == targetNorm) {
                    Log.d(TAG, "scan matched target ${hit.address}")
                    return hit
                }
            }
            Log.d(TAG, "target $targetNorm never advertised — falling back to strongest advert")
        }
        return best.values.maxByOrNull { it.rssi }
    }

    /** Strip everything but hex digits, uppercase (iOS `normalizedBLEAddress`). */
    fun normalizedAddress(value: String?): String? {
        val normalized = (value ?: "").filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.uppercase()
        return normalized.ifEmpty { null }
    }

    companion object {
        private const val TAG = "CluecoseBle"
    }
}

sealed class SensorScanError(message: String) : Exception(message) {
    object BleNotAvailable : SensorScanError("no Bluetooth adapter on this device")
    object BleOff : SensorScanError("Bluetooth is turned off. Enable it in Settings.")
    object MissingPermission : SensorScanError("Bluetooth permission is missing")
}
