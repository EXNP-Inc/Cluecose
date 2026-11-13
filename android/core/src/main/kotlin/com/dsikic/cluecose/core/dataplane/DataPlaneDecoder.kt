package com.dsikic.cluecose.core.dataplane

import com.dsikic.cluecose.core.util.ByteUtil

/**
 * Data-plane channel identification by GATT characteristic UUID prefix
 * (1:1 port of LibreCRKit DataPlaneDecoder.swift).
 */
enum class DataPlaneChannel {
    PATCH_CONTROL,
    PATCH_STATUS,
    GLUCOSE_DATA,
    HISTORIC_DATA,
    EVENT_LOG,
    CLINICAL_DATA,
    FACTORY_DATA;

    companion object {
        /** Resolve a channel from the leading 8 hex chars of a characteristic UUID. */
        fun fromUuidString(uuidString: String): DataPlaneChannel? {
            val lower = uuidString.lowercase()
            return when {
                lower.startsWith("08981338") -> PATCH_CONTROL
                lower.startsWith("08981482") -> PATCH_STATUS
                lower.startsWith("0898177a") -> GLUCOSE_DATA
                lower.startsWith("0898195a") -> HISTORIC_DATA
                lower.startsWith("08981bee") -> EVENT_LOG
                lower.startsWith("08981ab8") -> CLINICAL_DATA
                lower.startsWith("08981d24") -> FACTORY_DATA
                else -> null
            }
        }
    }

    /** Preferred inbound packet kind per channel; `null` = try all kinds. */
    val preferredInboundKind: DataPlanePacketKind?
        get() = when (this) {
            PATCH_STATUS -> DataPlanePacketKind.KIND2
            GLUCOSE_DATA -> DataPlanePacketKind.KIND3
            HISTORIC_DATA -> DataPlanePacketKind.KIND4
            else -> null
        }
}

/** Decoded payload for a data-plane notification. */
sealed class DataPlaneDecodedPayload {
    data class RealtimeGlucose(val reading: RealtimeGlucoseReading) : DataPlaneDecodedPayload()
    data class PatchStatusPayload(val status: PatchStatus) : DataPlaneDecodedPayload()
    data class HistoricalPagePayload(val page: HistoricalReadingPage) : DataPlaneDecodedPayload()
    data class ClinicalRecordPayload(val record: ClinicalReadingRecord) : DataPlaneDecodedPayload()
    data class Raw(val bytes: ByteArray) : DataPlaneDecodedPayload() {
        override fun equals(other: Any?): Boolean =
            other is Raw && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

data class DataPlaneDecodedPacket(
    val channel: DataPlaneChannel,
    val frame: DataFrame,
    val kind: DataPlanePacketKind,
    val preferredKind: DataPlanePacketKind?,
    val plaintext: ByteArray,
    val payload: DataPlaneDecodedPayload,
) {
    val usedPreferredKind: Boolean
        get() = preferredKind == null || preferredKind == kind
}

/**
 * Decrypts data-plane frames and dispatches to the per-channel payload
 * decoders (1:1 port of LibreCRKit DataPlaneDecoder).
 */
class DataPlaneDecoder(private val crypto: DataPlaneCrypto) {

    fun decrypt(frame: DataFrame, channel: DataPlaneChannel): DataPlaneDecodedPacket {
        val preferredKind = channel.preferredInboundKind
        val result: DataPlaneDecryptResult =
            if (preferredKind != null) {
                val p = try {
                    crypto.decrypt(frame, preferredKind)
                } catch (e: Exception) {
                    null
                }
                if (p != null) DataPlaneDecryptResult(preferredKind, p)
                else crypto.decryptTryingAllKinds(frame)
            } else {
                crypto.decryptTryingAllKinds(frame)
            }

        return DataPlaneDecodedPacket(
            channel = channel,
            frame = frame,
            kind = result.kind,
            preferredKind = preferredKind,
            plaintext = result.plaintext,
            payload = decodePayload(channel, result.plaintext),
        )
    }

    companion object {
        private fun decodePayload(channel: DataPlaneChannel, plaintext: ByteArray): DataPlaneDecodedPayload {
            return when (channel) {
                DataPlaneChannel.GLUCOSE_DATA -> try {
                    DataPlaneDecodedPayload.RealtimeGlucose(RealtimeGlucoseReading.parse(plaintext))
                } catch (e: Exception) {
                    DataPlaneDecodedPayload.Raw(plaintext)
                }
                DataPlaneChannel.PATCH_STATUS -> try {
                    DataPlaneDecodedPayload.PatchStatusPayload(PatchStatus.parse(plaintext))
                } catch (e: Exception) {
                    DataPlaneDecodedPayload.Raw(plaintext)
                }
                DataPlaneChannel.HISTORIC_DATA -> try {
                    DataPlaneDecodedPayload.HistoricalPagePayload(HistoricalReadingPage.parse(plaintext))
                } catch (e: Exception) {
                    DataPlaneDecodedPayload.Raw(plaintext)
                }
                DataPlaneChannel.CLINICAL_DATA -> try {
                    DataPlaneDecodedPayload.ClinicalRecordPayload(ClinicalReadingRecord.parse(plaintext))
                } catch (e: Exception) {
                    DataPlaneDecodedPayload.Raw(plaintext)
                }
                else -> DataPlaneDecodedPayload.Raw(plaintext)
            }
        }
    }
}

/**
 * Reassembles post-auth notification bodies before [DataFrame.parse].
 *
 * `glucoseData` readings arrive as a 15-byte prefix followed by a 20-byte
 * suffix. The suffix contains the normal 2-byte sequence trailer, so the two
 * notifications must be concatenated before CCM tag verification.
 */
class DataPlaneNotificationAssembler {

    private var glucosePrefix: ByteArray? = null

    /**
     * Feed one notification fragment. Returns the complete frame body when a
     * frame is ready (glucose prefix+suffix combined, or any non-glucose
     * fragment as-is), or `null` when the glucose prefix is buffered.
     */
    @Synchronized
    fun feed(fragment: ByteArray, channel: DataPlaneChannel): ByteArray? {
        if (channel != DataPlaneChannel.GLUCOSE_DATA) return fragment

        val prefix = glucosePrefix
        if (prefix != null) {
            glucosePrefix = null
            return ByteUtil.concat(prefix, fragment)
        }
        if (fragment.size == 15) {
            glucosePrefix = fragment
            return null
        }
        return fragment
    }

    @Synchronized
    fun reset() {
        glucosePrefix = null
    }
}
