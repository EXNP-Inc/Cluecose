package com.dsikic.cluecose.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.dsikic.cluecose.core.ble.BleFraming
import com.dsikic.cluecose.core.ble.BleFramingError
import com.dsikic.cluecose.core.pairing.BleCharRef
import com.dsikic.cluecose.core.pairing.CommandPairingTransport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The seven Libre data-plane characteristics (suffix of the full 128-bit
 * UUID, base `…-EF89-11E9-81B4-2A2AE2DBCCE4`; iOS `LibreSensorGATT.Char`).
 * Suffix match at runtime because [GattSession] resolves characteristics
 * from the discovered service table.
 */
enum class DataCharRef(val uuidSuffix: String) {
    PATCH_CONTROL("08981338"),
    PATCH_STATUS("08981482"),
    GLUCOSE_DATA("0898177A"),
    HISTORIC_DATA("0898195A"),
    EVENT_LOG("08981BEE"),
    CLINICAL_DATA("08981AB8"),
    FACTORY_DATA("08981D24"),
}

/**
 * Synchronous [BluetoothGatt] wrapper for the Libre 3. Mirrors the iOS
 * `SensorSession` / `SensorSessionTransport` pair:
 *
 * - implements [CommandPairingTransport] so the `:core`
 *   [com.dsikic.cluecose.core.pairing.PairingFlow] can drive the security
 *   handshake on a background thread;
 * - framing per `:core` [BleFraming] — writes: 2-byte LE offset prefix +
 *   18-byte chunks; security-channel notifies: 1-byte sequence prefix +
 *   19-byte chunks (the seq-prefix reassembly is the pairing/security
 *   channel ONLY — post-auth data-plane notifies are dispatched raw to
 *   [onDataNotify] for
 *   [com.dsikic.cluecose.core.dataplane.DataPlaneNotificationAssembler]);
 * - blocking semantics: each call parks the calling (worker) thread on a
 *   CountDownLatch; every GATT callback is marshalled through the single
 *   [gattExecutor] thread, so all waiter state is mutated on exactly one
 *   thread (the latch's happens-before edge makes the results visible to
 *   the waiting caller);
 * - data-plane notifications (post-authorization) are delivered to
 *   [onDataNotify] — the repository installs a
 *   [com.dsikic.cluecose.ble.DataPlaneListener] there;
 * - disconnect / GATT errors surface through [onDisconnected] (the
 *   repository tears the link down and schedules a reconnect).
 *
 * Characteristic identity is UUID-based. The two security-channel
 * characteristics resolve by UUID suffix on the security service (found by
 * the `0898203A-…` prefix); the APP_WIRING_SPEC handle mapping (0x002d /
 * 0x002a on the data service) is superseded — both live on the security
 * service.
 */
class GattSession(
    private val context: Context,
) : CommandPairingTransport {

    /** Called (from the GATT executor thread) when the link drops — the owner tears down. */
    @Volatile
    var onDisconnected: ((Int) -> Unit)? = null

    /**
     * Post-auth data-plane notify sink: `(characteristic, raw notify value)`.
     * Installed by the repository before arming data notifications; `null`
     * means drop (pairing phase).
     */
    @Volatile
    var onDataNotify: ((DataCharRef, ByteArray) -> Unit)? = null

    @Volatile var deviceAddress: String? = null
        private set
    @Volatile var advertisedName: String? = null
        private set

    private val gattExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "cluecose-gatt") }

    @Volatile private var gatt: BluetoothGatt? = null
    private var closed = false

    // Resolved characteristics (set once on connect; read-only afterwards).
    private var certChar: BluetoothGattCharacteristic? = null
    private var challengeChar: BluetoothGattCharacteristic? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private val dataChars = mutableMapOf<DataCharRef, BluetoothGattCharacteristic>()

    // Notify-wait plumbing. [pendingWaiter] is touched by the calling thread
    // (register/clear) and by the GATT executor (feed) — hence @Volatile.
    // All of its interior state is only mutated on the GATT executor thread.
    //
    // Characteristic identity is the UUID string: the public SDK exposes no
    // `getCharacteristicId()` (it is AOSP-internal), and Libre's data-plane
    // UUIDs are globally unique 128-bit values.
    @Volatile
    private var pendingWaiter: Waiter? = null

    /** characteristic UUID string -> next expected 1-byte seq (0 = not started). */
    private val notifySeq = mutableMapOf<String, AtomicInteger>()

    /**
     * One blocking notify wait. `exactly > 0`: reassemble seq-prefixed
     * security-channel fragments until [Waiter.exactly] payload bytes
     * accumulate. `exactly == -1`: capture the next raw notify value as-is
     * (command/response characteristic).
     */
    private class Waiter(
        val targetId: String,
        val exactly: Int,
        val latch: CountDownLatch,
        val deadline: Long,
        @Volatile var result: ByteArray? = null,
        @Volatile var error: Exception? = null,
    )

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Connect + MTU + service discovery. Blocking — call from a worker
     * thread. Throws [GattError] on any step failure (the caller should
     * then [disconnect] and retry).
     */
    @SuppressLint("MissingPermission") // caller holds BLUETOOTH_CONNECT
    fun connect(device: BluetoothDevice, name: String?, timeoutMs: Long = 15_000) {
        if (gatt != null) throw GattError.AlreadyConnected
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = manager?.adapter ?: throw GattError.NoAdapter
        if (btAdapter.isEnabled != true) throw GattError.BleOff
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            throw GattError.MissingPermission
        }

        deviceAddress = device.address
        advertisedName = name
        notifySeq.clear()
        closed = false

        val connected = CountDownLatch(1)
        val discovered = CountDownLatch(1)
        var discoveryStatus = BluetoothStatusCodes.ERROR_UNKNOWN
        var connectedStatus = BluetoothStatusCodes.ERROR_UNKNOWN

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                gattExecutor.execute {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connectedStatus = status
                            connected.countDown()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> onLinkDropped(status)
                        else -> Unit
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                gattExecutor.execute {
                    discoveryStatus = status
                    discovered.countDown()
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                gattExecutor.execute { handleNotify(characteristic) }
            }
        }

        Log.i(TAG, "connect ${device.address} (name=$name)")
        val g = device.connectGatt(context, false, callback) ?: throw GattError.ConnectFailed("connectGatt returned null")
        gatt = g

        if (!connected.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            disconnect()
            throw GattError.ConnectFailed("connection timed out")
        }
        if (!discovered.await(timeoutMs, TimeUnit.MILLISECONDS) ||
            discoveryStatus != BluetoothGatt.GATT_SUCCESS
        ) {
            disconnect()
            throw GattError.ServicesDiscovered(discoveryStatus)
        }

        // Observed captures run 20-byte chunks (18 + 2 offset / 19 + 1 seq);
        // request headroom. A refused request is not fatal — the protocol
        // also fits the ATT default MTU 23.
        val mtuOk = g.requestMtu(100)
        if (!mtuOk) {
            Log.w(TAG, "requestMtu(100) refused — continuing with the default MTU")
        }

        resolveCharacteristics(g)
        Log.i(TAG, "connected + discovered")
    }

    private fun resolveCharacteristics(g: BluetoothGatt) {
        certChar = null
        challengeChar = null
        commandChar = null
        dataChars.clear()

        for (service in g.services) {
            val uuid = service.uuid.toString().uppercase()
            val chars = service.characteristics.orEmpty()

            if (uuid.startsWith(LibreGattUuids.SECURITY_SERVICE_PREFIX)) {
                // Security service: the cert / challenge / command
                // characteristics (UUID suffixes observed in the pairing
                // captures; the spec's handle-based mapping is superseded).
                certChar = chars.firstOrNull { it.uuid.toString().uppercase().endsWith("089823FA") }
                challengeChar = chars.firstOrNull { it.uuid.toString().uppercase().endsWith("089822CE") }
                commandChar = chars.firstOrNull { it.uuid.toString().uppercase().endsWith("08982198") }
            }

            for (ref in DataCharRef.entries) {
                val char = chars.firstOrNull { it.uuid.toString().uppercase().endsWith(ref.uuidSuffix) }
                if (char != null) dataChars[ref] = char
            }
        }

        if (certChar == null || challengeChar == null || commandChar == null) {
            throw GattError.SecurityCharacteristicsMissing(
                "security service ${LibreGattUuids.SECURITY_SERVICE_PREFIX}-…: " +
                    "cert=${certChar != null} challenge=${challengeChar != null} command=${commandChar != null}",
            )
        }
        val missing = DataCharRef.entries.filter { dataChars[it] == null }
        if (missing.isNotEmpty()) {
            throw GattError.DataCharacteristicsMissing(missing.joinToString { it.name })
        }
    }

    fun disconnect() {
        closed = true
        val g = gatt
        gatt = null
        failPendingWaiter()
        if (g != null) {
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
    }

    /** Called on the GATT executor thread. */
    private fun onLinkDropped(status: Int) {
        if (closed) return
        Log.w(TAG, "GATT link dropped status=$status")
        failPendingWaiter()
        onDataNotify = null
        onDisconnected?.invoke(status)
    }

    /** Fail the in-flight waiter, if any (executor thread; the latch edge makes it visible). */
    private fun failPendingWaiter() {
        val w = pendingWaiter ?: return
        pendingWaiter = null
        w.error = GattError.Disconnected(19)
        w.latch.countDown()
    }

    /**
     * One [onCharacteristicChanged] delivery. Runs on the GATT executor
     * thread — the only thread that mutates waiter interior state.
     */
    private fun handleNotify(characteristic: BluetoothGattCharacteristic) {
        val value = characteristic.value ?: return
        val targetId = characteristic.uuid.toString()
        val waiter = pendingWaiter

        if (waiter != null && waiter.targetId == targetId) {
            if (System.currentTimeMillis() > waiter.deadline) return
            if (waiter.exactly < 0) {
                // Command/response: the raw value IS the response (a short
                // 1-byte frame). Zero-length values are skipped by the caller.
                waiter.result = value
                waiter.latch.countDown()
                return
            }
            // Security-channel fragment: [seq][chunk].
            val seq = value[0].toInt() and 0xFF
            val counter = notifySeq.getOrPut(targetId) { AtomicInteger(0) }
            val expected = counter.get()
            if (expected != 0 && seq != expected) {
                waiter.error = BleFramingError.SequenceGap(expected, seq)
                waiter.latch.countDown()
                return
            }
            counter.set((seq + 1) and 0xFF)
            val chunk = if (value.size > 1) value.copyOfRange(1, value.size) else ByteArray(0)
            val existing = waiter.result
            val accumulated = if (existing == null) chunk else existing + chunk
            waiter.result = accumulated
            if (accumulated.size >= waiter.exactly) {
                waiter.result = accumulated.copyOfRange(0, waiter.exactly)
                waiter.latch.countDown()
            }
            return
        }

        // No pairing waiter (or a different characteristic): post-auth
        // data-plane frame → hand to the listener.
        val ref = dataChars.entries.firstOrNull { it.value.uuid.toString() == targetId }?.key ?: return
        val sink = onDataNotify
        if (sink == null) {
            Log.v(TAG, "data notify dropped (no listener) len=${value.size}")
            return
        }
        sink(ref, value)
    }

    // ── PairingTransport (security channel) ────────────────────────────────

    /** Logical write, framed into 18-byte offset-prefixed chunks (BleFraming). */
    @SuppressLint("MissingPermission")
    override fun write(message: ByteArray, to: BleCharRef) {
        val char = charFor(to) ?: throw GattError.CharacteristicMissing(to.name)
        for (fragment in BleFraming.fragmentForWrite(message, BleFraming.WRITE_CHUNK_PAYLOAD)) {
            writeCharSync(char, fragment)
        }
    }

    /**
     * Reassemble security-channel notify fragments (1-byte seq prefix +
     * 19-byte payload) until [exactly] bytes are available, then return
     * them. Mirrors iOS `awaitNotify` (per-call reassembler, 10 s
     * per-fragment window).
     */
    override fun awaitNotify(on: BleCharRef, exactly: Int): ByteArray {
        val char = charFor(on) ?: throw GattError.CharacteristicMissing(on.name)
        val targetId = char.uuid.toString()
        notifySeq.getOrPut(targetId) { AtomicInteger(0) }

        val latch = CountDownLatch(1)
        val waiter = Waiter(targetId, exactly, latch, System.currentTimeMillis() + FRAGMENT_TIMEOUT_MS)
        pendingWaiter = waiter

        // Enable notifications for the duration of the wait (idempotent — the
        // sensor keeps the CCCD state across re-arms; later calls are no-ops).
        enableNotificationsSync(char, "awaitNotify(${on.name})")

        val deadline = waiter.deadline
        while (System.currentTimeMillis() < deadline) {
            if (latch.await(50, TimeUnit.MILLISECONDS)) break
        }
        if (pendingWaiter === waiter) pendingWaiter = null

        val error = waiter.error
        if (error != null) throw error
        val result = waiter.result
        if (result == null) throw GattError.NotifyTimeout(on.name)
        return result
    }

    /** One command byte to the command/response characteristic. */
    @SuppressLint("MissingPermission")
    override fun writeCommand(command: Int) {
        val char = commandChar ?: throw GattError.CharacteristicMissing("command")
        writeCharSync(char, byteArrayOf(command.toByte()))
    }

    /**
     * Next command/response notify — a single short raw frame (prefix byte +
     * occasionally an ack byte). Stray empty frames are skipped; a bounded
     * number of them and then the wait fails (the caller's PairingFlow
     * timeout is the outer bound).
     */
    override fun awaitCommandResponse(timeoutSeconds: Int): ByteArray {
        val char = commandChar ?: throw GattError.CharacteristicMissing("command")
        val targetId = char.uuid.toString()
        val timeoutMs = timeoutSeconds * 1000L
        val start = System.currentTimeMillis()
        var strayFrames = 0

        while (System.currentTimeMillis() - start < timeoutMs) {
            val latch = CountDownLatch(1)
            val waiter = Waiter(
                targetId = targetId,
                exactly = -1,
                latch = latch,
                deadline = System.currentTimeMillis() + timeoutMs,
            )
            pendingWaiter = waiter
            enableNotificationsSync(char, "awaitCommandResponse")

            latch.await(timeoutMs.coerceAtMost(4000), TimeUnit.MILLISECONDS)
            if (pendingWaiter === waiter) pendingWaiter = null

            val error = waiter.error
            if (error != null) throw error
            val result = waiter.result
            if (result != null && result.isNotEmpty()) return result
            if (result != null) {
                strayFrames += 1
                if (strayFrames >= STRAY_SKIP_LIMIT) {
                    throw GattError.CommandResponseTimeout(timeoutSeconds)
                }
            }
        }
        throw GattError.CommandResponseTimeout(timeoutSeconds)
    }

    // ── Data-plane helpers (post-authorization) ─────────────────────────────

    /**
     * Raw data-plane write to a data characteristic (already-encrypted frame
     * + 2-byte seq trailer — the frame IS the write, no BleFraming;
     * encrypted data-plane frames are short).
     */
    @SuppressLint("MissingPermission")
    fun writeData(frame: ByteArray, ref: DataCharRef) {
        val char = dataChars[ref] ?: throw GattError.CharacteristicMissing(ref.name)
        writeCharSync(char, frame)
    }

    /**
     * Enable (or disable) notifications on every data-plane characteristic,
     * sequentially off→on with 90 ms settles — the exact re-arm order the
     * iOS backfill bootstrap uses (patchControl, eventLog, factoryData,
     * glucoseData, patchStatus).
     */
    fun rearmDataNotifications(enable: Boolean) {
        val order = if (enable) {
            listOf(
                DataCharRef.PATCH_CONTROL,
                DataCharRef.EVENT_LOG,
                DataCharRef.FACTORY_DATA,
                DataCharRef.GLUCOSE_DATA,
                DataCharRef.PATCH_STATUS,
            )
        } else {
            DataCharRef.entries.toList()
        }
        for (ref in order) {
            val char = dataChars[ref] ?: continue
            runCatching {
                if (enable) {
                    // off → on per channel (the CCCD write is what arms the
                    // sensor; a bare on short-circuits after a reconnect).
                    disableNotificationsSync(char)
                    Thread.sleep(90)
                    enableNotificationsSync(char, "rearm(${ref.name}→on)")
                    Thread.sleep(90)
                } else {
                    disableNotificationsSync(char)
                    Thread.sleep(90)
                }
            }.onFailure { Log.w(TAG, "rearm ${ref.name} failed: ${it.message}") }
        }
    }

    /** Enable or disable one data characteristic's notifications (backfill re-arms). */
    fun setDataNotification(ref: DataCharRef, enable: Boolean) {
        val char = dataChars[ref] ?: throw GattError.CharacteristicMissing(ref.name)
        if (enable) enableNotificationsSync(char, "setDataNotification(${ref.name})")
        else disableNotificationsSync(char)
    }

    // ── GATT plumbing ──────────────────────────────────────────────────────

    private fun charFor(ref: BleCharRef): BluetoothGattCharacteristic? = when (ref) {
        BleCharRef.CERT_HANDSHAKE -> certChar
        BleCharRef.CHALLENGE -> challengeChar
    }

    @SuppressLint("MissingPermission")
    private fun enableNotificationsSync(char: BluetoothGattCharacteristic, logLabel: String) {
        val g = gatt ?: throw GattError.NotConnected
        g.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(CCCD) ?: throw GattError.NoCccd(char.uuid.toString())
        val value = if (char.properties.and(BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        cccd.value = value
        val ok = g.writeDescriptor(cccd)
        if (!ok) {
            throw GattError.NotifyEnableFailed(char.uuid.toString(), BluetoothStatusCodes.ERROR_UNKNOWN)
        }
        Log.i(TAG, "$logLabel ok")
    }

    @SuppressLint("MissingPermission")
    private fun disableNotificationsSync(char: BluetoothGattCharacteristic) {
        val g = gatt ?: return
        runCatching {
            g.setCharacteristicNotification(char, false)
            val cccd = char.getDescriptor(CCCD) ?: return
            cccd.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            g.writeDescriptor(cccd)
        }.onFailure { Log.w(TAG, "disable ${char.uuid} failed: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharSync(char: BluetoothGattCharacteristic, value: ByteArray) {
        val g = gatt ?: throw GattError.NotConnected
        char.value = value
        char.writeType = if (char.properties.and(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val ok = g.writeCharacteristic(char)
        if (!ok) {
            throw GattError.WriteFailed(char.uuid.toString(), BluetoothStatusCodes.ERROR_UNKNOWN)
        }
    }

    companion object {
        private const val TAG = "CluecoseGatt"
        private const val FRAGMENT_TIMEOUT_MS = 10_000L
        private const val STRAY_SKIP_LIMIT = 5
        private val CCCD = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

sealed class GattError(message: String) : Exception(message) {
    object NoAdapter : GattError("no Bluetooth adapter on this device")
    object BleOff : GattError("Bluetooth is turned off. Enable it in Settings.")
    object MissingPermission : GattError("BLUETOOTH_CONNECT permission is missing")
    object NotConnected : GattError("GATT session is not connected")
    object AlreadyConnected : GattError("GATT session already connected")
    class ConnectFailed(val detail: String) : GattError("Bluetooth connect failed: $detail")
    class ServicesDiscovered(val status: Int) : GattError("service discovery failed status=$status")
    class SecurityCharacteristicsMissing(val detail: String) : GattError("security characteristics missing: $detail")
    class DataCharacteristicsMissing(val detail: String) : GattError("data characteristics missing: $detail")
    class CharacteristicMissing(val name: String) : GattError("characteristic not resolved: $name")
    class WriteFailed(val uuid: String, val status: Int) : GattError("GATT write failed on $uuid status=$status")
    class NotifyEnableFailed(val uuid: String, val status: Int) : GattError("enable notifications failed on $uuid status=$status")
    class NoCccd(val uuid: String) : GattError("no CCCD descriptor on $uuid")
    class NotifyTimeout(val name: String) : GattError("timed out waiting for $name notify")
    class CommandResponseTimeout(val seconds: Int) : GattError("timed out waiting for command response after ${seconds}s")
    class Disconnected(val status: Int) : GattError("GATT link dropped (status=$status)")
}
