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
import com.dsikic.cluecose.core.pairing.BleCharRef
import com.dsikic.cluecose.core.pairing.CommandPairingTransport
import com.dsikic.cluecose.core.util.Hex
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The seven Libre data-plane characteristics (suffix of the full 128-bit
 * UUID, base `…-EF89-11E9-81B4-2A2AE2DBCCE4`; iOS `LibreSensorGATT.Char`).
 * Suffix match at runtime because [GattSession] resolves characteristics
 * from the discovered service table.
 */
enum class DataCharRef(val uuidPrefix: String) {
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

    // In-flight write-ACK wait: the characteristic/descriptor whose device
    // ACK (onCharacteristicWrite/onDescriptorWrite) we are waiting for,
    // plus its latch. Set by the calling (worker) thread right after the
    // write is queued, cleared on completion; the GATT executor invokes
    // ackWrite() on the device ACK, matching by object identity. CCCD
    // writes are strictly serialized in this session, so a stale/late ACK
    // can at worst deliver an identical (success) status to the next wait
    // on the same descriptor — never a false failure.
    @Volatile private var ackTarget: Any? = null
    @Volatile private var ackLatch: CountDownLatch? = null
    @Volatile private var ackStatus = 0

    /**
     * Security-channel (cert/challenge) notify buffer: raw (seq-prefixed)
     * notify values for the fragmented security characteristics that arrive
     * while NO matching waiter is registered. This becomes possible because
     * [connect] now arms the cert CCCD eagerly (iOS parity), so the sensor
     * can stream the 65 B cert during `awaitCommandResponse(0x0a)` — before
     * `awaitNotify(CERT_HANDSHAKE)` registers. The next `awaitNotify` on the
     * same characteristic drains it, in order. Bounded per characteristic;
     * overflow drops the oldest. Only the GATT executor thread touches this
     * ([handleNotify] and the [awaitNotify] drain both run on it).
     */
    private val securityBuffer = mutableMapOf<String, ArrayDeque<ByteArray>>()

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
        // True once the waiter FINISHED (result full or error). NOT the same
        // as result != null — for fragmented waits `result` is non-null from
        // the FIRST chunk onward (mid-accumulation); only `done` marks a
        // waiter that must not receive further fragments.
        @Volatile var done: Boolean = false,
        // Per-call reassembly state (iOS NotifyReassembler, made tolerant
        // of the observed sensor behavior): the sensor streams the 140 B
        // cert as a TIGHT BURST and duplicates frames — 18:50 field run:
        // the SAME seq-1 frame was delivered twice (both buffered before
        // the waiter existed, then the strict +1 counter hit
        // SequenceGap(expected=2, got=1) and the session disconnected 20
        // ms into the stream, dropping the rest in the stack: "indication/
        // notif for unknown device, ignore" ×18). Frames can also arrive
        // OUT OF ORDER (18:49: seq 1 delivered live, seq 0 never reached
        // the app before the close).
        //
        // So: a seq-keyed map of received chunks + a contiguous-advance
        // pointer from seq 0. Duplicates (same seq, same payload) are
        // skipped; a conflicting payload for a taken seq keeps the first
        // and logs. The stream is never "gapped" — genuinely lost frames
        // surface as the 10 s NotifyTimeout with the received-seqs list,
        // which the handshake retries on a fresh link.
        //
        // Kept on the waiter (NOT a shared per-char counter) because the
        // sensor restarts seq at 0 for every stream, and PairingFlow
        // awaits the SAME cert characteristic twice (140 B sensor cert,
        // then 65 B sensor ephemeral). Only the GATT executor thread
        // mutates this (feedFragment, from the live path or the drain).
        //
        // [CERT_PARTIAL_CARRYOVER] seeds a fresh cert waiter with chunks a
        // PREVIOUS attempt's cert stream collected before it lost frames
        // in flight (the sensor streams the 140 B cert in a ~90 ms burst;
        // at the 46.8 ms connection interval only 2-3 connection events
        // carry it, and each attempt loses 2-3 DIFFERENT frames — 20:17/
        // 20:18/20:19 runs: {[0,3,4,5,6,7]}, {[0,1,2,3,4,6,7]},
        // {[0,1,2,3,4,7]}. The cert is a static per-patch blob, so the
        // union of attempts covers all 8 frames within 1-2 tries).
    ) {
        // Per-waiter reassembly state (see the doc above). Plain body
        // properties — a constructor param with an initializer would need an
        // explicit type. Touched only on the GATT executor thread (same as
        // the waiter's other interior state), so no @Volatile needed.
        val receivedChunks = LinkedHashMap<Int, ByteArray>()
        var nextContiguous: Int = 0
    }

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
        securityBuffer.clear()
        closed = false

        val connected = CountDownLatch(1)
        val discovered = CountDownLatch(1)
        val mtuLatch = CountDownLatch(1)
        var discoveryStatus = BluetoothStatusCodes.ERROR_UNKNOWN
        var connectedStatus = BluetoothStatusCodes.ERROR_UNKNOWN
        var mtuResult = 0
        var mtuValue = 0

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

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                gattExecutor.execute {
                    mtuResult = status
                    mtuValue = mtu
                    mtuLatch.countDown()
                }
            }

            // Device-side ACK for a descriptor (CCCD) write. The boolean from
            // writeDescriptor() only means the STACK queued it — 17:13 field
            // run: a CCCD write issued before the connection-parameter update
            // was settled was never ACKed by the sensor and the framework's
            // write permit stayed held for 3+ s, failing every subsequent
            // write. We now wait for THIS callback per CCCD write and fail
            // fast when the sensor does not ACK.
            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                gattExecutor.execute {
                    Log.i(TAG, "onDescriptorWrite status=$status cccd=${descriptor.uuid.toString().equals(CCCD.toString(), ignoreCase = true)}")
                    ackWrite(descriptor, status)
                }
            }
        }

        Log.i(TAG, "connect ${device.address} (name=$name)")
        val g = device.connectGatt(context, false, callback) ?: throw GattError.ConnectFailed("connectGatt returned null")
        gatt = g

        if (!connected.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            disconnect()
            throw GattError.ConnectFailed("connection timed out")
        }
        // connectGatt does NOT start service discovery — the
        // onServicesDiscovered latch above only fires after this call.
        if (!g.discoverServices()) {
            disconnect()
            throw GattError.ServicesDiscovered(256) // ERROR_GATT_FAILURE (AOSP-internal constant)
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

        // 17:13 field run: a CCCD write issued while the MTU exchange was
        // still in flight was never ACKed by the sensor, and the framework
        // held its GATT write permit open (3+ s of false returns) — every
        // later write on that link failed. The 16:36 run, where the first
        // write landed after the connection-parameter update, got ACKs in
        // ~15 ms. Gate the first writes on the link settling: MTU exchange
        // resolved + a short settle for the connection-parameter update
        // (the framework's onConnectionUpdated is not exposed publicly, so
        // it is covered by the timed settle).
        if (mtuLatch.await(2_000, TimeUnit.MILLISECONDS)) {
            Log.i(TAG, "MTU settled: mtu=$mtuValue status=$mtuResult")
        } else {
            Log.w(TAG, "MTU exchange did not settle in 2 s — continuing")
        }
        // The default connection interval on this link is 36 (46.8 ms), and
        // the sensor streams the 140 B cert as a ~90 ms burst — only 2-3
        // connection events carry it, so 2-3 frames are lost in flight per
        // attempt (field runs: different frames lost each try). Ask for a
        // HIGH connection priority: a fast interval (7.5-15 ms) gives the
        // burst 6-12 connection events, enough to carry all 8 frames. The
        // request is non-binding — if the sensor refuses, the cert
        // carryover (cross-attempt chunk union) still completes the stream.
        // (The framework's connection-accept result is not exposed
        // publicly, so the boolean only says "queued".)
        val prioQueued = g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        Log.i(TAG, "requestConnectionPriority(HIGH) queued=$prioQueued")
        try {
            Thread.sleep(LINK_SETTLE_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        Log.i(TAG, "link settled — first writes may proceed")

        resolveCharacteristics(g)
        Log.i(TAG, "connected + discovered")

        // iOS parity (SensorSession.didDiscoverCharacteristicsFor): arm
        // notifications on ALL handshake characteristics immediately, while
        // discovery is still settling — NOT lazily right before each await.
        // The sensor can start streaming (e.g. the 65 B cert right after
        // GetCertificate) before a late CCCD lands; an early subscription is
        // never torn down, so the cert stream is never missed. A later
        // enableNotificationsSync on an already-notifying characteristic is a
        // no-op (same CCCD value).
        val securityChars = listOfNotNull(certChar, challengeChar, commandChar)
        for (char in securityChars) {
            val short = char.uuid.toString().take(8).uppercase()
            runCatching {
                enableNotificationsSync(char, "eagerSub($short)")
            }.onFailure { Log.w(TAG, "eagerSub($short) failed: ${it.message}") }
        }
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
                // characteristics. The Libre short code (first 8 hex digits)
                // is the LEADING prefix of the full 128-bit UUID (all share
                // the EF89-11E9-81B4-2A2AE2DBCCE4 tail), so match startsWith —
                // UUID.toString() ends in the tail, never the short code.
                certChar = chars.firstOrNull { it.uuid.toString().uppercase().startsWith("089823FA") }
                challengeChar = chars.firstOrNull { it.uuid.toString().uppercase().startsWith("089822CE") }
                commandChar = chars.firstOrNull { it.uuid.toString().uppercase().startsWith("08982198") }
            }

            for (ref in DataCharRef.entries) {
                val char = chars.firstOrNull { it.uuid.toString().uppercase().startsWith(ref.uuidPrefix) }
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
        failPendingAck()
        if (g != null) {
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
    }

    /**
     * Drop the cross-attempt sensor-cert chunks for this MAC (static
     * per-patch blob). Call when starting a FRESH NFC pairing: the sensor
     * was just re-activated and the next cert stream is authoritative.
     * Also called on forget. Safe on a fresh session too (no-op).
     */
    fun clearCertCarryover() {
        deviceAddress?.let { clearCertCarryover(it) }
    }

    /** Called on the GATT executor thread. */
    private fun onLinkDropped(status: Int) {
        if (closed) return
        Log.w(TAG, "GATT link dropped status=$status")
        failPendingWaiter()
        failPendingAck()
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

        if (waiter != null && waiter.targetId == targetId && !waiter.done) {
            if (System.currentTimeMillis() > waiter.deadline) return
            if (waiter.exactly < 0) {
                // Command/response: the raw value IS the response (a short
                // 1-byte frame). Zero-length values are skipped by the caller.
                waiter.result = value
                waiter.done = true
                waiter.latch.countDown()
                return
            }
            feedFragment(targetId, value, waiter)
            return
        }

        // No live (incomplete) waiter for this characteristic: either no
        // waiter at all, or one that already completed on a different
        // characteristic / an earlier stream. For the fragmented SECURITY
        // characteristics (cert/challenge — NOT the command char, whose
        // notifies are always consumed by a live command-response waiter)
        // buffer the raw notify for the next awaitNotify on it. Eager
        // subscribe (connect) can make the sensor start streaming the 65 B
        // cert before the cert waiter is registered, and the SAME cert char
        // carries a second 65 B stream (sensor ephemeral) right after the
        // first is consumed — a late chunk for the finished stream must
        // buffer, not poison the next waiter. The seq state lives on the
        // waiter that DRAINS it, so an early stream cannot desync a later
        // one.
        val isSecurity = targetId == certChar?.uuid?.toString() ||
            targetId == challengeChar?.uuid?.toString()
        if (isSecurity) {
            val deque = securityBuffer.getOrPut(targetId) { ArrayDeque() }
            if (deque.size >= SECURITY_BUFFER_MAX) deque.removeFirst()
            deque.addLast(value)
            Log.i(
                TAG,
                "security notify BUFFERED (seq=${value[0].toInt() and 0xFF}) hex=${Hex.encode(value)} buf=${deque.size} (no live waiter)",
            )
            return
        }

        // Post-auth data-plane frame → hand to the listener.
        val ref = dataChars.entries.firstOrNull { it.value.uuid.toString() == targetId }?.key ?: return
        val sink = onDataNotify
        if (sink == null) {
            Log.v(TAG, "data notify dropped (no listener) len=${value.size}")
            return
        }
        sink(ref, value)
    }

    /**
     * Feed one seq-prefixed security-channel fragment ([seq][chunk]) into
     * [waiter]. Tolerant reassembly (see [Waiter.receivedChunks]): store by
     * seq, skip exact duplicates, advance the contiguous prefix from
     * seq 0, complete when the contiguous payload reaches [Waiter.exactly].
     * Never errors on reordering or duplicates — a truly lost frame
     * surfaces as the [FRAGMENT_TIMEOUT_MS] timeout (the handshake
     * retries on a fresh link), never as a mid-stream teardown.
     * Only the GATT executor thread calls this (from [handleNotify] live,
     * or from [drainBuffered]).
     */
    private fun feedFragment(targetId: String, value: ByteArray, waiter: Waiter): Boolean {
        val seq = value[0].toInt() and 0xFF
        val chunk = if (value.size > 1) value.copyOfRange(1, value.size) else ByteArray(0)
        when (val prior = waiter.receivedChunks[seq]) {
            null -> {
                waiter.receivedChunks[seq] = chunk
                Log.i(TAG, "security notify fed (seq=$seq) hex=${Hex.encode(value)} have=${waiter.receivedChunks.keys.toList()}")
            }
            else -> if (prior.contentEquals(chunk)) {
                Log.w(TAG, "security notify DUP skipped (seq=$seq) — sensor re-sent the same frame")
            } else {
                // Conflict: same seq, different payload. The stream already
                // "started" this seq once; keep the first occurrence (the
                // sensor's duplicates are byte-identical in the field runs).
                Log.w(TAG, "security notify CONFLICT at seq=$seq — keeping first occurrence (got ${chunk.size} B vs prior ${prior.size} B)")
            }
        }
        // Advance the contiguous prefix from seq 0.
        while (waiter.receivedChunks.containsKey(waiter.nextContiguous)) {
            waiter.nextContiguous = (waiter.nextContiguous + 1) and 0xFF
        }
        // Rebuild the contiguous payload (small — ≤ 9 chunks at MTU 23).
        val ordered = ArrayList<ByteArray>(waiter.nextContiguous)
        for (s in 0 until waiter.nextContiguous) {
            waiter.receivedChunks[s]?.let { ordered.add(it) }
        }
        val total = ordered.sumOf { it.size }
        val accumulated = ByteArray(total)
        var off = 0
        for (c in ordered) {
            System.arraycopy(c, 0, accumulated, off, c.size)
            off += c.size
        }
        waiter.result = accumulated
        if (accumulated.size >= waiter.exactly) {
            val full = accumulated.copyOfRange(0, waiter.exactly)
            waiter.result = full
            waiter.done = true
            waiter.latch.countDown()
            Log.i(TAG, "security stream COMPLETE (seq 0..${(waiter.nextContiguous - 1) and 0xFF}) ${full.size} B")
            return true
        }
        return false
    }

    /**
     * Drain [targetId]'s buffered security fragments into [waiter], in order,
     * as if they had arrived live. Only the GATT executor thread calls this —
     * the drain loop re-reads [pendingWaiter] each iteration, so a live
     * notify that interleaves on the same single-thread executor (FIFO) is
     * fed live and the buffered remainder stays for the next drain. Returns
     * true when [waiter] is satisfied (or errored).
     */
    private fun drainBuffered(targetId: String, waiter: Waiter): Boolean {
        val deque = securityBuffer[targetId] ?: return false
        while (deque.isNotEmpty()) {
            val cur = pendingWaiter
            if (cur != waiter || cur.done) break
            val value = deque.removeFirst()
            if (feedFragment(targetId, value, waiter)) return true
        }
        return false
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

        val latch = CountDownLatch(1)
        val waiter = Waiter(targetId, exactly, latch, System.currentTimeMillis() + FRAGMENT_TIMEOUT_MS)
        pendingWaiter = waiter

        // Cross-attempt cert carryover: seed chunks a previous attempt's
        // stream collected before it lost frames in flight (see
        // CERT_CARRYOVER_SIZE doc). Gated to the cert stream itself — the
        // 65 B sensor-ephemeral on the SAME characteristic must never see
        // cert chunks. Keyed by MAC, which is unique per patch, so a
        // different patch (different MAC) starts with an empty map and can
        // never be polluted by another patch's cert blob.
        val carryKey: String? = deviceAddress?.uppercase()
        val certSeed: Map<Int, ByteArray> = if (on == BleCharRef.CERT_HANDSHAKE && exactly == CERT_CARRYOVER_SIZE) {
            carryKey?.let { certCarryover[it] }?.toMap() ?: emptyMap()
        } else {
            emptyMap()
        }

        // Enable notifications for the duration of the wait (idempotent — the
        // sensor keeps the CCCD state across re-arms; later calls are no-ops).
        enableNotificationsSync(char, "awaitNotify(${on.name})")

        // Consume fragments that streamed in before the waiter existed
        // (possible because the cert CCCD is armed eagerly in connect).
        // The drain MUST run on the GATT executor thread — it touches
        // securityBuffer and waiter seq/result state, which the executor
        // owns (handleNotify feeds the same state live). Posting it here
        // (after the CCCD write) serializes the drain FIFO with any live
        // notifies: a notify arriving mid-drain is enqueued behind this
        // task and is either fed live (if the waiter is still registered
        // when it runs) or left buffered (if the drain finished the
        // waiter first) — never lost, never double-fed. The carryover
        // seed joins the same task (waiter state is executor-owned).
        gattExecutor.execute {
            for ((seq, chunk) in certSeed) {
                val value = ByteArray(chunk.size + 1)
                value[0] = seq.toByte()
                System.arraycopy(chunk, 0, value, 1, chunk.size)
                feedFragment(targetId, value, waiter)
            }
            if (certSeed.isNotEmpty()) {
                Log.i(TAG, "cert carryover seeded ${certSeed.size} chunks (seqs ${certSeed.keys.sorted()})")
            }
            drainBuffered(targetId, waiter)
        }

        val deadline = waiter.deadline
        while (System.currentTimeMillis() < deadline) {
            if (latch.await(50, TimeUnit.MILLISECONDS)) break
        }
        if (pendingWaiter === waiter) pendingWaiter = null

        // Cert carryover bookkeeping (executor-owned state read after the
        // latch edge): on success the cert is complete — drop the partial
        // map (it is now dead weight for this MAC). On timeout, persist
        // whatever this stream collected so the NEXT attempt (fresh
        // GattSession, same MAC) starts ahead instead of from zero.
        if (on == BleCharRef.CERT_HANDSHAKE && exactly == CERT_CARRYOVER_SIZE && carryKey != null) {
            if (waiter.done) {
                certCarryover.remove(carryKey)
            } else {
                val bucket = certCarryover.getOrPut(carryKey) { java.util.concurrent.ConcurrentHashMap() }
                for ((seq, chunk) in waiter.receivedChunks) {
                    bucket.putIfAbsent(seq, chunk)
                }
                Log.i(TAG, "cert carryover saved: ${bucket.size} chunks (seqs ${bucket.keys.sorted()}) for $carryKey")
            }
        }

        val error = waiter.error
        if (error != null) throw error
        if (!waiter.done) {
            // Timed out. `result` may be PARTIAL (contiguous-from-0 bytes
            // only) — it is NOT a valid frame; the caller treats
            // NotifyTimeout as a retryable transport failure. Report how
            // many bytes we saw AND which seqs landed, so the log can
            // distinguish "the stream never started" (no seqs) from "the
            // stream started but frames were lost in flight" (e.g. seqs
            // 1..7 but no 0).
            val partial = waiter.result?.size ?: 0
            val seqsSeen: List<Int> = waiter.receivedChunks.keys.toList().sorted()
            val seqsShown: String = if (seqsSeen.isEmpty()) "none" else seqsSeen.joinToString(",")
            Log.w(TAG, "awaitNotify(${on.name}) timed out: $partial/$exactly contiguous bytes; seqs seen: [$seqsShown]")
            throw GattError.NotifyTimeout(on.name)
        }
        return waiter.result!!
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
        // iOS parity (SensorSession: `if chr.isNotifying { skip }`): the CCCD
        // is armed exactly ONCE per characteristic — the eager arm in
        // connect(). Re-writing an already-armed CCCD is at best a no-op and
        // at worst a pipeline reset mid-handshake, so when our cached value
        // already equals the desired one, skip the write entirely. (A null
        // cache — the first arm after connect — always writes.)
        val current = cccd.value
        if (current != null && current.contentEquals(value)) {
            Log.i(TAG, "$logLabel already armed — skipping CCCD write")
            return
        }
        cccd.value = value
        val ok = awaitWritePermit(g, "$logLabel CCCD") { g.writeDescriptor(cccd) }
        if (!ok) {
            // Roll back the cache: the write was never sent, so the next
            // caller must not see "already armed" and skip its own write.
            cccd.value = current
            throw GattError.NotifyEnableFailed(char.uuid.toString(), BluetoothStatusCodes.ERROR_UNKNOWN)
        }
        // Descriptors DO carry ATT responses (unlike the NO_RESPONSE
        // security characteristics), so the device ACK here is real and
        // fast (17 ms observed). A missing/rejected ACK means the
        // subscription is NOT live — fail now (the caller retries on a
        // fresh link) instead of letting the framework hold the write
        // permit open and fail every later write (17:13 field run).
        try {
            waitWriteAck(cccd, "$logLabel CCCD")
        } catch (e: GattError) {
            // Same rollback: the device never confirmed, so the cached
            // value is a lie — force the next arm to re-write the CCCD.
            cccd.value = current
            throw e
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

    /**
     * Invoke [ackWrite] for the in-flight write when the device ACKs it —
     * GATT executor thread (onCharacteristicWrite / onDescriptorWrite).
     */
    private fun ackWrite(item: Any, status: Int) {
        if (ackTarget !== item) {
            // Stale/unsolicited ACK (a previous write that was already
            // timeout-failed, or a write we are not tracking) — log, drop.
            Log.w(TAG, "write ACK dropped (no wait) status=$status item=${item.javaClass.simpleName}")
            return
        }
        ackStatus = status
        ackLatch?.countDown()
    }

    /**
     * Wait for the device-side ACK of a write that [write] just queued.
     * The boolean from writeCharacteristic/writeDescriptor is NOT the truth
     * (the framework returns true while the ATT write is still in flight,
     * and holds the permit open if the sensor never ACKs — 17:13 run: 3+ s
     * of false returns killed the whole handshake). A status != GATT_SUCCESS
     * or a missing ACK within [WRITE_ACK_TIMEOUT_MS] is a real transport
     * failure the caller can retry (fresh link).
     */
    private fun waitWriteAck(item: Any, label: String): Int {
        ackTarget = item
        ackStatus = 0
        val latch = CountDownLatch(1)
        ackLatch = latch
        val delivered = latch.await(WRITE_ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        ackTarget = null
        ackLatch = null
        if (!delivered) {
            Log.w(TAG, "$label: no device ACK in ${WRITE_ACK_TIMEOUT_MS} ms — write treated as lost")
            throw GattError.WriteAckTimeout(label)
        }
        val status = ackStatus
        if (status == ACK_FAILED_SENTINEL) throw GattError.Disconnected(19) // link dropped mid-ACK
        if (status != BluetoothGatt.GATT_SUCCESS) {
            throw GattError.WriteFailed(label, status)
        }
        return status
    }

    /** Fail any in-flight write-ACK wait (link dropped / session closed). */
    private fun failPendingAck() {
        if (ackTarget != null || ackLatch != null) {
            ackStatus = ACK_FAILED_SENTINEL
            ackTarget = null
            ackLatch?.countDown()
            ackLatch = null
        }
    }

    /**
     * GATT write-permit retry. The framework returns `false` from
     * `writeCharacteristic`/`writeDescriptor` when it cannot acquire the
     * GATT write permit — most commonly right after connect, while the
     * connection-parameter update is still in flight (Samsung quirk,
     * observed 14:46:53: permit busy 500 ms after discovery). The device
     * did NOT reject the write; the permit frees up in milliseconds, so
     * retry with a short backoff. Bounded — the PairingFlow per-write
     * wall-clock budget is the outer limit.
     */
    private fun awaitWritePermit(g: BluetoothGatt, label: String, write: () -> Boolean): Boolean {
        var attempt = 0
        while (true) {
            if (write()) return true
            attempt += 1
            if (attempt >= WRITE_PERMIT_RETRIES) return false
            Log.w(TAG, "$label: GATT write permit busy (attempt $attempt/$WRITE_PERMIT_RETRIES) — retrying")
            try {
                Thread.sleep(WRITE_PERMIT_RETRY_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharSync(char: BluetoothGattCharacteristic, value: ByteArray, waitForAck: Boolean = false) {
        val g = gatt ?: throw GattError.NotConnected
        char.value = value
        char.writeType = if (char.properties.and(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val ok = awaitWritePermit(g, "write ${char.uuid}") { g.writeCharacteristic(char) }
        if (!ok) {
            throw GattError.WriteFailed(char.uuid.toString(), BluetoothStatusCodes.ERROR_UNKNOWN)
        }
        if (waitForAck) waitWriteAck(char, "write ${char.uuid}")
    }

    companion object {
        private const val TAG = "CluecoseGatt"
        private const val FRAGMENT_TIMEOUT_MS = 10_000L
        private const val STRAY_SKIP_LIMIT = 5
        // Per-characteristic cap on buffered (unwaited) security notifies.
        // The cert stream is 4 chunks at MTU 23; the ephemeral challenge
        // stream is the same shape — 16 is a comfortable 4× headroom.
        private const val SECURITY_BUFFER_MAX = 16
        // GATT write-permit retry: the framework returns false (permit busy)
        // while a prior write's ACK is outstanding. The first post-connect
        // CCCD write opens a window that lasts until the sensor ACKs it —
        // observed 18 ms … 1.5 s (17:06 field run: every write after the
        // cert CCCD was busy for 1.5 s+). 120 x 25 ms = 3 s of bounded
        // retries covers the worst observed window with margin.
        private const val WRITE_PERMIT_RETRIES = 120
        private const val WRITE_PERMIT_RETRY_MS = 25L
        // Device-ACK window for a descriptor (CCCD) write. Observed 17 ms on
        // healthy links; a missing ACK means the write was lost and the
        // framework will hold the GATT write permit open — fail at 500 ms
        // instead of busy-spinning the permit for 3 s (17:13 field run).
        private const val WRITE_ACK_TIMEOUT_MS = 500L
        // Settle delay after MTU before the first write. The framework
        // updates the connection parameters right after connect (observed
        // interval 6 -> 36 at +130 ms); a CCCD write issued before that
        // update lands is not ACKed by the sensor (17:13 run). 300 ms
        // covers the worst observed conn-update latency (~150 ms) with
        // margin without adding real handshake time.
        private const val LINK_SETTLE_MS = 300L
        // Pseudo-status for a write-ACK wait killed by a link drop. Must
        // be a value the sensor can never report (0x0000–0x0127 are GATT
        // status codes; use the int max).
        private const val ACK_FAILED_SENTINEL = Int.MAX_VALUE
        private val CCCD = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        // The 140 B sensor cert (SensorCert.TOTAL_SIZE). The carryover is
        // keyed to this exact size so the 65 B sensor-ephemeral stream on
        // the SAME cert characteristic can never pollute it.
        private const val CERT_CARRYOVER_SIZE = 140
        // Cross-attempt cert chunks: device address → (seq → payload chunk).
        // The sensor streams the 140 B cert in a ~90 ms burst; at the
        // 46.8 ms connection interval only 2-3 connection events carry it
        // and each fresh-link attempt loses 2-3 DIFFERENT frames in flight
        // (20:17/20:18/20:19 runs: {[0,3,4,5,6,7]},
        // {[0,1,2,3,4,6,7]}, {[0,1,2,3,4,7]}) — a per-attempt re-send of
        // GetCertificate cannot fix that alone. The cert is a static
        // per-patch blob, so the union of attempts completes it within
        // 1-2 tries. Lives in the companion because each attempt builds a
        // FRESH GattSession (RealGlucoseRepository.connectAndAuthorize
        // loop). Cleared on cert success, on a new NFC pairing (repo), and
        // on forget.
        private val certCarryover = java.util.concurrent.ConcurrentHashMap<String, MutableMap<Int, ByteArray>>()

        /**
         * Drop any stored partial-cert chunks for a (possibly null) MAC.
         * Called from the repository on forget (the sensor is being
         * abandoned, its partial cert is dead weight). Accepts a raw MAC or
         * the repository's normalized form; both are upper-cased before
         * lookup so a format mismatch can't leave a stale entry behind.
         * Returns true if an entry was removed.
         */
        fun clearCertCarryover(address: String?): Boolean {
            if (address.isNullOrBlank()) return false
            return certCarryover.remove(address.uppercase()) != null
        }
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
    class WriteAckTimeout(val label: String) : GattError("no device ACK for $label — write lost")
    class NoCccd(val uuid: String) : GattError("no CCCD descriptor on $uuid")
    class NotifyTimeout(val name: String) : GattError("timed out waiting for $name notify")
    class CommandResponseTimeout(val seconds: Int) : GattError("timed out waiting for command response after ${seconds}s")
    class Disconnected(val status: Int) : GattError("GATT link dropped (status=$status)")
}
