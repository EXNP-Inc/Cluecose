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
        object TagNotIso15693 : Failure("tag is not a supported CGM sensor")
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
            if (stopped || inFlight) return
            inFlight = true
            Log.d(TAG, "tag discovered — running patch-info + activation exchange")
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

    /** True when the device has an enabled NFC adapter (reader mode usable). */
    fun isAvailable(): Boolean = adapter?.isEnabled == true

    /**
     * Arm reader mode for one exchange with this receiver ID. Safe to call
     * from the repository worker thread (main-thread work is posted).
     * [onComplete] must be set beforehand.
     */
    fun start(receiverId: Int) {
        val a = adapter ?: throw Failure.ReaderUnavailable
        if (!a.isEnabled) throw Failure.ReaderUnavailable
        session += 1
        this.receiverId = receiverId
        stopped = false
        inFlight = false
        armed = true
        main.post {
            try {
                a.enableReaderMode(activity, readerCallback, READER_FLAGS, null)
                Log.d(TAG, "reader mode armed receiverId=0x${receiverId.toString(16)}")
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
        main.post { runCatching { adapter?.disableReaderMode(activity) } }
    }

    /**
     * Re-arm after the activity resumed (the system suspends reader mode
     * while the activity is paused; this makes the armed state explicit).
     */
    fun resume() {
        if (!armed || stopped) return
        main.post {
            runCatching { adapter?.enableReaderMode(activity, readerCallback, READER_FLAGS, null) }
        }
    }

    /** Runs the two-command exchange; called on the "cluecose-nfc" thread. */
    private fun performExchange(tag: Tag, mySession: Int) {
        val nfcV = NfcV.get(tag) ?: throw Failure.TagNotIso15693
        val patchInfo = readPatchInfo(nfcV)
        Log.d(TAG, "patch info ok: serial=${patchInfo.serialNumber} state=0x${patchInfo.stateByte.toString(16)} cmd=${patchInfo.recommendedCommandCode}")
        val activation = activateOrSwitch(nfcV, patchInfo, receiverId)
        Log.d(TAG, "activation ok: ble=${activation.bleAddressDisplay} timeRaw=${activation.activationTimeRaw.size}B")
        inFlight = false
        report(PairingResult(patchInfo, activation), null, mySession)
    }

    private fun readPatchInfo(nfcV: NfcV): Libre3NFCPatchInfo {
        val resp = transceive(nfcV, NFCActivationCommand.READ_PATCH_INFO)
        val flag = resp[0].toInt() and 0xFF
        if (flag and 1 != 0) throw errorFrameFailure(resp)
        // Strip the leading ISO 15693 flag byte only. The remaining body is the
        // `00 A5 …` frame (its own trailing CRC stays; the core parser tolerates
        // extra trailing bytes for the variable-length patch info).
        val body = resp.copyOfRange(1, resp.size)
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
        val flag = resp[0].toInt() and 0xFF
        if (flag and 1 != 0) throw errorFrameFailure(resp)
        // Strip the leading flag byte only: the core `Libre3NFCActivationResponse`
        // expects the 19-byte `00 A5 00 … ‖ CRC2` frame, whose last 2 bytes ARE
        // the ISO 15693 CRC.
        val body = resp.copyOfRange(1, resp.size)
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
        return resp
    }

    /**
     * ISO 15693 error frame: `[flag=0x01][code][optional CRC16]`. The
     * receiver-mismatch code 0xB1 sits right after the flag byte.
     */
    private fun errorFrameFailure(resp: ByteArray): Failure {
        val code = if (resp.size >= 2) resp[1].toInt() and 0xFF else 0
        Log.w(TAG, "sensor error frame: flag=0x${(resp[0].toInt() and 0xFF).toString(16)} code=0x${code.toString(16)}")
        return if (code == 0xB1) Failure.ReceiverMismatch else Failure.SensorRejected(code)
    }

    private fun report(result: PairingResult?, failure: Failure?, mySession: Int) {
        if (stopped || mySession != session) return
        main.post { onComplete?.invoke(result, failure) }
    }

    companion object {
        private const val TAG = "CluecoseNfc"

        // ISO 15693 lives on the NFC-A physical layer; skip NDEF discovery —
        // the sensor is a raw transceive target, not an NDEF tag.
        private const val READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    }
}
