package com.dsikic.cluecose.ble

import android.util.Log
import com.dsikic.cluecose.core.dataplane.DataFrame
import com.dsikic.cluecose.core.dataplane.DataPlaneChannel
import com.dsikic.cluecose.core.dataplane.DataPlaneCrypto
import com.dsikic.cluecose.core.dataplane.DataPlaneDecodedPacket
import com.dsikic.cluecose.core.dataplane.DataPlaneDecodedPayload
import com.dsikic.cluecose.core.dataplane.DataPlaneDecoder
import com.dsikic.cluecose.core.dataplane.DataPlaneNotificationAssembler

/**
 * Consumes the encrypted data plane on a live [GattSession]. Mirrors the iOS
 * `startListener` task:
 *
 *   notify → [DataPlaneNotificationAssembler] (glucoseData 15 B prefix +
 *   20 B suffix) → [DataFrame.parse] → [DataPlaneDecoder] → packet callback.
 *
 * Security-channel notifies (seq-prefixed) are consumed by the
 * [com.dsikic.cluecose.core.pairing.PairingFlow] during the handshake, so
 * they never reach this listener; after authorization the listener is the
 * only consumer and [GattSession.onDataNotify] hands every data-plane
 * characteristic's raw notify value straight to [feed].
 *
 * Threading: [feed] is invoked on the GATT executor thread (one at a time),
 * so the assembler + counters are race-free without locks. The packet
 * callback runs on that same thread — the repository's callback only mutates
 * repository state + posts events, which is safe from a background thread.
 */
class DataPlaneListener(private val crypto: DataPlaneCrypto) {

    /** Repository sink (runs on the GATT executor thread). */
    fun interface PacketSink {
        fun onPacket(packet: DataPlaneDecodedPacket, receivedAt: Long)
    }

    private val assembler = DataPlaneNotificationAssembler()
    private val decoder = DataPlaneDecoder(crypto)

    private var firstNotifyLogged = false
    private var firstFrameLogged = false
    private var decryptFailures = 0

    // Backfill bookkeeping (the realtime stream shares the notify pipeline,
    // so backfill completion must track pages / clinical records specifically).
    private var lastHistoricalPageAt = 0L
    private var historicalPageCount = 0
    private var lastClinicalRecordAt = 0L
    private var clinicalRecordCount = 0

    /**
     * Route one raw notify fragment. Call from the GATT executor thread
     * (install as [GattSession.onDataNotify]).
     */
    fun feed(ref: DataCharRef, fragment: ByteArray, sink: PacketSink?, receivedAt: Long = System.currentTimeMillis()) {
        val channel = DataPlaneChannel.fromUuidString(ref.uuidSuffix) ?: return
        if (!firstNotifyLogged) {
            firstNotifyLogged = true
            Log.i(TAG, "LISTENER first notify on ${ref.uuidSuffix} (${fragment.size}B)")
        }
        val frameRaw = assembler.feed(fragment, channel) ?: return // buffered glucose prefix
        try {
            val frame = DataFrame.parse(frameRaw)
            val packet = decoder.decrypt(frame, channel)
            if (!firstFrameLogged) {
                firstFrameLogged = true
                Log.i(TAG, "LISTENER first decrypted frame: channel=$channel kind=${packet.kind} payload=${payloadName(packet.payload)}")
            }
            trackBackfillCounters(packet, receivedAt)
            sink?.onPacket(packet, receivedAt)
        } catch (e: Exception) {
            decryptFailures += 1
            if (decryptFailures == 1 || decryptFailures % 25 == 0) {
                Log.w(TAG, "LISTENER decrypt failure #$decryptFailures on ${ref.uuidSuffix}: ${e.message}")
            }
        }
    }

    /** Drop buffered partial frames (fresh session). */
    fun reset() {
        assembler.reset()
    }

    val historicalPageCountValue: Int get() = historicalPageCount
    val lastHistoricalPageAtValue: Long get() = lastHistoricalPageAt
    val clinicalRecordCountValue: Int get() = clinicalRecordCount
    val lastClinicalRecordAtValue: Long get() = lastClinicalRecordAt

    private fun trackBackfillCounters(packet: DataPlaneDecodedPacket, receivedAt: Long) {
        when (packet.payload) {
            is DataPlaneDecodedPayload.HistoricalPagePayload -> {
                lastHistoricalPageAt = receivedAt
                historicalPageCount += 1
            }
            is DataPlaneDecodedPayload.ClinicalRecordPayload -> {
                lastClinicalRecordAt = receivedAt
                clinicalRecordCount += 1
            }
            else -> Unit
        }
    }

    private fun payloadName(payload: DataPlaneDecodedPayload): String = when (payload) {
        is DataPlaneDecodedPayload.RealtimeGlucose -> "realtimeGlucose"
        is DataPlaneDecodedPayload.PatchStatusPayload -> "patchStatus"
        is DataPlaneDecodedPayload.HistoricalPagePayload -> "historicalReadingPage"
        is DataPlaneDecodedPayload.ClinicalRecordPayload -> "clinicalReadingRecord"
        is DataPlaneDecodedPayload.Raw -> "raw"
    }

    companion object {
        private const val TAG = "CluecoseDataPlane"
    }
}
