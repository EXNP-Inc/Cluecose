package com.dsikic.cluecose.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dsikic.cluecose.core.nfc.Libre3NFCActivationResponse
import com.dsikic.cluecose.core.nfc.Libre3NFCPatchInfo
import com.dsikic.cluecose.core.nfc.Libre3NFCError
import com.dsikic.cluecose.core.nfc.NFCActivationCommand
import java.io.IOException

/**
 * ISO 15693 (NfcV) reader for the Libre 3 pairing exchange.
 *
 * Android has no CoreNFC-equivalent system sheet: reader mode is bound to the
 * [Activity], so the in-app [com.dsikic.cluecose.ui.UiState.nfcAwaiting]
 * prompt plays the role iOS's NFC sheet plays. MainActivity creates this
 * reader and hands it to the repository, which calls [start] when the user
 * taps "Pair with NFC tap" and [stop] when the exchange finishes or is
 * cancelled.
 *
 * One exchange per armed reader: the first tag seen triggers, on a worker
 * thread,
 *   1. `READ_PATCH_INFO` (`02 A1 7A`) → [Libre3NFCPatchInfo] (serial, state,
 *      wear/warmup durations),
 *   2. the activation / switch-receiver command
 *      (`02 (A0|A8) 7A ‖ metcrc(time, receiverId)`, per the sensor's
 *      [Libre3NFCPatchInfo.recommendedCommandCode]) →
 *      [Libre3NFCActivationResponse] (BLE address, 4-byte PIN, time),
 * and then [onComplete] fires exactly once on the main thread.
 *
 * The reader stays armed after a FAILED exchange so a re-hold retries
 * automatically (the ISO 15693 anti-collision repolls the tag while it
 * remains in the field); [stop] tears it down.
 *
 * Frame handling (field-verified, same as Juggluco `AlgNfcV`): the tag
 * answers with `[flag 1B][body …][CRC16 2B]`; bit 0 of the flag is the ISO
 * 15693 error bit. A receiver mismatch arrives as the short error frame
 * `[0x01][0xB1]` — surfaced as [Failure.ReceiverMismatch].
 */
class NfcActivationReader(activity: Activity) {

    /** A completed NFC pairing exchange (patch info + activation response). */
    data class PairingResult(
        val patchInfo: Libre3NFCPatchInfo,
        val activation: Libre3NFCActivationResponse,
    )

    /** Thrown inside the worker thread; caught and delivered via [onComplete]. */
    sealed class Failure(message: String = "NFC failure") : Exception(message) {
        /** No NFC adapter, or the adapter is disabled / the activity is not usable. */
        object ReaderUnavailable : Failure("NFC is not available on this device.")
        /** The tag was not an ISO 15693 (NfcV) tag. */
        object TagNotIso15693 : Failure("tag is not a supported sensor")
        /** The RF exchange itself failed (tag left the field, controller error, …). */
        data class TransceiveFailed(val detail: String) : Failure("could not talk to the sensor: $detail")
        /** The sensor stored a different receiver ID (ISO 15693 error 0xB1). */
        object ReceiverMismatch : Failure("the sensor is bound to a different receiver")
        /** Some other ISO 15693 error code from the sensor. */
        data class SensorRejected(val code: Int) :
            Failure("sensor rejected the activation (0x${code.toString(16)})")

        /** The patch-info frame did not parse as a Libre 3 response. */
        data class InvalidPatchInfo(val raw: ByteArray) :
            Failure("couldn't read the sensor tag (${raw.size} bytes)") {
            override fun toString() = "invalidPatchInfo(${raw.size} bytes)"
        }

        /** The activation frame did not parse as a 19-byte pairing response. */
        data class InvalidActivationResponse(val raw: ByteArray) :
            Failure("sensor returned an unexpected pairing response (${raw.size} bytes)") {
            override fun toString() = "invalidActivationResponse(${raw.size} bytes)"
        }
    }

    /** Set by the repository before [start]; invoked once on the main thread. */
    @Volatile
    var onComplete: ((result: PairingResult?, failure: Failure?) -> Unit)? = null

    private val activity: Activity = activity

    private val adapter: NfcAdapter? =
        activity.getSystemService(Activity.NFC_SERVICE) as? NfcAdapter

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var armed = false
    @Volatile private var stopped = true
    @Volatile private var inFlight = false
    @Volatile private var session = 0
    @Volatile private var receiverId = 0

    private val readerCallback = object : NfcAdapter.ReaderCallback {
        override fun onTagDiscovered(tag: Tag) {
            val mySession = session
            if (stopped || inFlight) {
                // Diagnostic: the framework can keep routing tags while our
                // `stopped` flag is set (zombie reader — see stop()). A silent
                // return here cost a field session: the tag WAS routed, the
                // app looked dead.
                Log.w(TAG, "tag seen but reader not active (stopped=$stopped inFlight=$inFlight) — dropped")
                return
            }
            inFlight = true
            Log.i(TAG, "tag discovered — running patch-info + activation exchange")
            Thread({
                try {
                    performExchange(tag, mySession)
                } catch (e: Failure) {
                    Log.w(TAG, "NFC exchange failed: ${e.message}")
                    inFlight = false
                    report(null, e, mySession)
                } catch (e: Exception) {
                    Log.w(TAG, "NFC exchange failed unexpectedly: ${e.message}", e)
                    inFlight = false
                    report(null, Failure.TransceiveFailed(e.message ?: "NFC transceive failed"), mySession)
                }
            }, "cluecose-nfc").start()
        }
    }

    /**
     * True when the device has an enabled NFC adapter (reader mode usable).
     *
     * On the Sony G3223 (API 26) the constructor's
     * `getSystemService(NFC_SERVICE)` returned null while the NFC service was
     * clearly up (`dumpsys nfc` → `mState=on`, `mEnableReader` toggleable), so
     * this resolves through [resolveAdapter] (which falls back to the canonical
     * `NfcAdapter.getDefaultAdapter` static) instead of trusting the cached
     * proxy. If even the fallback is null, the diagnostic log records the
     * permission grant + uid + feature flag for the next field report.
     */
    fun isAvailable(): Boolean {
        val a = resolveAdapter()
        if (a == null) {
            // Diagnostic: the Sony G3223 (API 26) returned a NULL adapter from
            // `getSystemService(NFC_SERVICE)` even though `dumpsys package`
            // showed `android.permission.NFC: granted=true` and `dumpsys nfc`
            // showed `mState=on`. Log the permission + a fresh static lookup
            // to learn exactly what the app process can see.
            val perm = activity.checkSelfPermission(android.Manifest.permission.NFC)
            val fresh = NfcAdapter.getDefaultAdapter(activity)
            Log.w(
                TAG,
                "isAvailable: adapter NULL. checkSelfPermission(NFC)=${perm == android.content.pm.PackageManager.PERMISSION_GRANTED} " +
                    "uid=${activity.applicationInfo.uid} getDefaultAdapter()==null=${fresh == null} " +
                    "hasSystemFeatureNfc=${activity.packageManager.hasSystemFeature("android.hardware.nfc")}",
            )
            return false
        }
        return a.isEnabled
    }

    /**
     * The NfcAdapter, preferring the proxy cached at construction but falling
     * back to the canonical `getDefaultAdapter` static — on the Sony the
     * `getSystemService(NFC_SERVICE)` call returned null while the service was
     * clearly up.
     */
    private fun resolveAdapter(): NfcAdapter? =
        adapter?.takeIf { it != null }
            ?: NfcAdapter.getDefaultAdapter(activity)

    /**
     * Arm reader mode for one exchange with this receiver ID. Safe to call
     * from the repository worker thread (main-thread work is posted).
     * [onComplete] must be set beforehand.
     */
    fun start(receiverId: Int) {
        // Prefer the cached proxy, fall back to the canonical static lookup
        // (the Sony G3223 returned null from `getSystemService(NFC_SERVICE)`
        // while the NFC service was up).
        val a = resolveAdapter()?.takeIf { it.isEnabled } ?: throw Failure.ReaderUnavailable
        session += 1
        this.receiverId = receiverId
        stopped = false
        inFlight = false
        armed = true
        main.post {
            try {
                a.enableReaderMode(activity, readerCallback, READER_FLAGS, null)
                Log.i(TAG, "reader mode armed receiverId=0x${receiverId.toString(16)}")
            } catch (e: Exception) {
                Log.w(TAG, "enableReaderMode failed: ${e.message}")
                report(null, Failure.ReaderUnavailable, session)
            }
        }
    }

    /** Disarm reader mode and drop any pending exchange (idempotent). */
    fun stop() {
        stopped = true
        armed = false
        // Route through resolveAdapter() — the cached getSystemService proxy
        // is NULL on Samsung (and Sony), so a bare `adapter?.disableReaderMode`
        // no-ops and leaves the framework reader ON (mEnableReader stays true)
        // while `stopped` is true → a zombie reader: the OS routes tags to us
        // but onTagDiscovered early-returns. Same fix as start().
        val a = resolveAdapter()
        main.post { runCatching { a?.disableReaderMode(activity) } }
    }

    /**
     * Re-arm after the activity resumed (the system suspends reader mode
     * while the activity is paused; this makes the armed state explicit).
     */
    fun resume() {
        if (!armed || stopped) return
        val a = resolveAdapter()
        main.post {
            runCatching { a?.enableReaderMode(activity, readerCallback, READER_FLAGS, null) }
        }
    }

    /** Runs the two-command exchange; called on the "cluecose-nfc" thread. */
    private fun performExchange(tag: Tag, mySession: Int) {
        val nfcV = NfcV.get(tag) ?: throw Failure.TagNotIso15693
        // Android's BasicTagTechnology requires connect() BEFORE transceive();
        // omitting it makes the first transceive() throw
        // IllegalStateException("Call connect() first!"). The Tag is scoped to
        // this one discovery, so disconnect on the way out — a re-hold yields a
        // fresh Tag object and re-arms the exchange.
        try {
            nfcV.connect()
            val patchInfo = readPatchInfo(nfcV)
            Log.i(TAG, "patch info ok: serial=${patchInfo.serialNumber} state=0x${patchInfo.stateByte.toString(16)} cmd=${patchInfo.recommendedCommandCode}")
            val activation = activateOrSwitch(nfcV, patchInfo, receiverId)
            Log.i(TAG, "activation ok: ble=${activation.bleAddressDisplay} timeRaw=${activation.activationTimeRaw.size}B")
            inFlight = false
            report(PairingResult(patchInfo, activation), null, mySession)
        } finally {
            runCatching { nfcV.close() }
        }
    }

    private fun readPatchInfo(nfcV: NfcV): Libre3NFCPatchInfo {
        val resp = transceive(nfcV, NFCActivationCommand.READ_PATCH_INFO)
        if (isErrorResponse(resp)) throw errorFrameFailure(resp)
        // Strip the optional leading 0x00 status prefix only — keep the 0xA5
        // pad (the core parser normalizes `A5 …` → `00 A5 …`).
        val body = stripResponsePrefix(resp)
        return try {
            Libre3NFCPatchInfo(body)
        } catch (e: Libre3NFCError) {
            Log.w(TAG, "patch info unparseable (${body.size}B): ${e.message}")
            throw Failure.InvalidPatchInfo(body)
        }
    }

    private fun activateOrSwitch(nfcV: NfcV, patchInfo: Libre3NFCPatchInfo, id: Int): Libre3NFCActivationResponse {
        val timeSeconds = (System.currentTimeMillis() / 1000L).toInt()
        val command = NFCActivationCommand.command(patchInfo.recommendedCommandCode, timeSeconds, id)
        val resp = transceive(nfcV, command)
        if (isErrorResponse(resp)) throw errorFrameFailure(resp)
        // The core `Libre3NFCActivationResponse` expects the 19-byte
        // `00 A5 00 … ‖ CRC2` frame, whose last 2 bytes ARE the ISO 15693 CRC.
        val body = stripResponsePrefix(resp)
        return try {
            Libre3NFCActivationResponse(body)
        } catch (e: Libre3NFCError) {
            Log.w(TAG, "activation response unparseable (${body.size}B): ${e.message}")
            throw Failure.InvalidActivationResponse(body)
        }
    }

    private fun transceive(nfcV: NfcV, command: ByteArray): ByteArray {
        val resp = try {
            nfcV.transceive(command)
        } catch (e: IOException) {
            throw Failure.TransceiveFailed(e.message ?: "transceive failed")
        }
        if (resp == null || resp.isEmpty()) throw Failure.TransceiveFailed("empty response")
        // Log the EXACT command sent and raw response bytes — this is the only
        // way to tell a receiver-mismatch (0xB1) apart from any other short
        // rejection frame, and the difference decides the whole fix.
        Log.i(TAG, "TX ${toHex(command)}")
        Log.i(TAG, "RX ${toHex(resp)} (${resp.size}B) flag=0x${(resp[0].toInt() and 0xFF).toString(16)}")
        return resp
    }

    private fun toHex(b: ByteArray): String =
        b.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }

    /**
     * Response header: an optional leading 0x00 status byte, then the 0xA5 pad.
     * Returns the index of the byte AFTER the pad — where an error frame's
     * flag byte (ISO 15693, bit 0 = error) sits, and where a normal frame's
     * body starts. Field-verified rejection: `00 a5 01 b1` (prefix, pad,
     * flag=0x01, code=0xB1 receiver-mismatch).
     */
    private fun responseHeader(resp: ByteArray): Int = when {
        resp.size >= 2 && (resp[0].toInt() and 0xFF) == 0x00 && (resp[1].toInt() and 0xFF) == 0xA5 -> 2
        resp.isNotEmpty() && (resp[0].toInt() and 0xFF) == 0xA5 -> 1
        else -> 0
    }

    /** True when the sensor answered with an ISO 15693 error frame. */
    private fun isErrorResponse(resp: ByteArray): Boolean {
        val i = responseHeader(resp)
        if (i >= resp.size) return false
        return (resp[i].toInt() and 1) != 0
    }

    /**
     * Strip ONLY the leading 0x00 status prefix (keep the 0xA5 pad — the core
     * parsers normalize `A5 …` → `00 A5 …` and expect the trailing CRC intact).
     */
    private fun stripResponsePrefix(resp: ByteArray): ByteArray =
        if (resp.size >= 2 && (resp[0].toInt() and 0xFF) == 0x00 && (resp[1].toInt() and 0xFF) == 0xA5)
            resp.copyOfRange(1, resp.size)
        else resp

    /**
     * ISO 15693 error frame: `[… 00][A5][flag=0x01][code][optional CRC16]`.
     * The receiver-mismatch code 0xB1 sits right after the flag byte.
     */
    private fun errorFrameFailure(resp: ByteArray): Failure {
        val flagIdx = responseHeader(resp)
        val flag = if (flagIdx < resp.size) resp[flagIdx].toInt() and 0xFF else 0
        val codeIdx = flagIdx + 1
        val code = if (codeIdx < resp.size) resp[codeIdx].toInt() and 0xFF else 0
        Log.w(TAG, "sensor error frame: flag=0x${flag.toString(16)} code=0x${code.toString(16)}")
        return if (code == 0xB1) Failure.ReceiverMismatch else Failure.SensorRejected(code)
    }

    private fun report(result: PairingResult?, failure: Failure?, mySession: Int) {
        if (stopped || mySession != session) return
        main.post { onComplete?.invoke(result, failure) }
    }

    companion object {
        private const val TAG = "CluecoseNfc"

        // ISO 15693 is its OWN physical layer — NfcV in Android terms
        // (FLAG_READER_NFC_V = 0x08), NOT NFC-A (ISO 14443A, 0x01). Arming with
        // NFC-A made the chip poll for the wrong tag family, so the sensor was
        // invisible even on a healthy chip (verified: dumpsys nfc showed
        // mTechMask: 1 = NFC-A only, zero tag-detection events on hold).
        // Skip NDEF discovery — the sensor is a raw transceive target.
        private const val READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    }
}
