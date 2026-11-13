package com.dsikic.cluecose.core.dataplane

import com.dsikic.cluecose.core.util.ByteUtil

/**
 * Patch-status record (0x08981482). 1:1 port of LibreCRKit PatchStatus.swift.
 * 12-byte plaintext.
 */
class PatchStatus(plaintext: ByteArray) {

    companion object {
        const val PLAINTEXT_SIZE = 12

        fun parse(plaintext: ByteArray): PatchStatus {
            if (plaintext.size != PLAINTEXT_SIZE) {
                throw PatchStatusError.WrongPlaintextSize(plaintext.size)
            }
            return PatchStatus(plaintext)
        }
    }

    val lifeCount: Int
    val errorData: Int
    val eventDataRaw: Int
    val eventData: Int
    val index: Int
    val totalEvents: Int
    val patchState: Int
    val currentLifeCount: Int
    val stackDisconnectReason: Int
    val appDisconnectReason: Int

    val patchStateKind: Libre3PatchState
        get() = Libre3PatchState(patchState)

    val isPatchStateActive: Boolean
        get() = patchStateKind.isActive

    val isPatchStateExpiredOrError: Boolean
        get() = patchStateKind.isExpiredOrError

    val isPatchStateTerminated: Boolean
        get() = patchStateKind.isTerminated

    val hasErrorData: Boolean
        get() = errorData != 0

    val sensorError: Libre3SensorError
        get() = Libre3SensorError.fromCode(errorData)

    val sensorAttention: Libre3SensorAttention
        get() = Libre3SensorAttention.from(errorData, patchState)

    val shouldNotifyUser: Boolean
        get() = sensorAttention.shouldNotifyUser

    val shouldNotifyReplaceSensor: Boolean
        get() = sensorAttention.isReplaceSensor

    /** True when `errorData` reports Abbott's SENSOR_TERMINATED condition
     *  (post-shutdown/end-session state). Deliberately false for expired:
     *  end-of-wear state 5 can still advertise over BLE. */
    val isShutdownTerminated: Boolean
        get() = sensorError.isShutdownTerminated

    val isInsertionFailure: Boolean
        get() = sensorError.isInsertionFailure

    @Deprecated("Split into isInsertionFailure and isShutdownTerminated")
    val isTerminalFailure: Boolean
        get() = isInsertionFailure || isShutdownTerminated

    val hasDisconnectReason: Boolean
        get() = stackDisconnectReason != 0 || appDisconnectReason != 0

    val defaultLifecycle: SensorLifecycle
        get() = lifecycle()

    val defaultLifecyclePhase: SensorLifecyclePhase
        get() = defaultLifecycle.phase

    val isInDefaultWarmup: Boolean
        get() = defaultLifecycle.isWarmingUp

    fun lifecycle(
        warmupDurationMinutes: Int = SensorLifecycle.DEFAULT_WARMUP_DURATION_MINUTES,
        wearDurationMinutes: Int? = null,
    ): SensorLifecycle =
        SensorLifecycle(currentLifeCount.toLong(), warmupDurationMinutes, wearDurationMinutes)

    init {
        lifeCount = s16(plaintext, 0)
        errorData = s16(plaintext, 2)
        eventDataRaw = s16(plaintext, 4)
        eventData = 4000 + eventDataRaw
        index = ByteUtil.u8(plaintext, 6).toByte().toInt()
        totalEvents = index + 1
        patchState = ByteUtil.u8(plaintext, 7).toByte().toInt()
        currentLifeCount = s16(plaintext, 8)
        stackDisconnectReason = ByteUtil.u8(plaintext, 10).toByte().toInt()
        appDisconnectReason = ByteUtil.u8(plaintext, 11).toByte().toInt()
    }

    private fun s16(data: ByteArray, offset: Int): Int =
        ByteUtil.u16le(data, offset).toShort().toInt()

    override fun toString(): String =
        "PatchStatus(lc=$lifeCount, err=$errorData, state=$patchState, curLC=$currentLifeCount)"
}

sealed class PatchStatusError : Exception() {
    data class WrongPlaintextSize(val size: Int) : PatchStatusError()
}

/**
 * Sensor error/status code carried in the patch-status `errorData` field.
 * Mapping: 3 → insertionFailure, 5 → expired, 6/8 → terminated,
 * 7 → transmissionError; everything else is none or unknown.
 */
sealed class Libre3SensorError {
    object None : Libre3SensorError()
    object InsertionFailure : Libre3SensorError()
    object Expired : Libre3SensorError()
    object Terminated : Libre3SensorError()
    object TransmissionError : Libre3SensorError()
    data class Unknown(val code: Int) : Libre3SensorError()

    companion object {
        @JvmStatic
        fun fromCode(code: Int): Libre3SensorError = when (code) {
            0 -> None
            3 -> InsertionFailure
            5 -> Expired
            6, 8 -> Terminated
            7 -> TransmissionError
            else -> Unknown(code)
        }
    }

    val isShutdownTerminated: Boolean
        get() = this is Terminated

    val isInsertionFailure: Boolean
        get() = this is InsertionFailure

    override fun toString(): String = when (this) {
        None -> "none"
        InsertionFailure -> "insertionFailure"
        Expired -> "expired"
        Terminated -> "terminated"
        TransmissionError -> "transmissionError"
        is Unknown -> "unknown($code)"
    }
}

/**
 * Product-facing user action inferred from the patch-status record.
 * Abbott-app compatibility: 3 → checkSensor, 5/6 → sensorEnded,
 * 7 → checkSensor (transient), 8 → replaceSensor.
 * When errorData == 0, patch-state fallback: 3 → checkSensor,
 * 5/6 → sensorEnded, 7/8 → replaceSensor.
 */
sealed class Libre3SensorAttention {
    object None : Libre3SensorAttention()
    object CheckSensor : Libre3SensorAttention()
    object SensorEnded : Libre3SensorAttention()
    object ReplaceSensor : Libre3SensorAttention()
    data class Unknown(val code: Int) : Libre3SensorAttention()

    companion object {
        @JvmStatic
        fun from(errorData: Int, patchState: Int? = null): Libre3SensorAttention {
            if (errorData != 0) {
                return when (errorData) {
                    3, 7 -> CheckSensor
                    5, 6 -> SensorEnded
                    8 -> ReplaceSensor
                    else -> Unknown(errorData)
                }
            }
            return when (patchState) {
                3 -> CheckSensor
                7, 8 -> ReplaceSensor
                5, 6 -> SensorEnded
                else -> None
            }
        }
    }

    val shouldNotifyUser: Boolean
        get() = this != None

    val isReplaceSensor: Boolean
        get() = this is ReplaceSensor

    override fun toString(): String = when (this) {
        None -> "none"
        CheckSensor -> "checkSensor"
        SensorEnded -> "sensorEnded"
        ReplaceSensor -> "replaceSensor"
        is Unknown -> "unknown($code)"
    }
}

/** Patch state from patch-status byte 7. 4 = active; 3/5/7 = expired-or-error; 6/8 = terminated. */
class Libre3PatchState(val rawValue: Int) {

    val isActive: Boolean
        get() = rawValue == 4

    val isExpiredOrError: Boolean
        get() = rawValue == 3 || rawValue == 5 || rawValue == 7

    val isTerminated: Boolean
        get() = rawValue == 6 || rawValue == 8

    override fun toString(): String = if (isActive) "active" else "raw($rawValue)"

    override fun equals(other: Any?): Boolean = other is Libre3PatchState && other.rawValue == rawValue

    override fun hashCode(): Int = rawValue
}
