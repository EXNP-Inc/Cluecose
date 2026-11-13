package com.dsikic.cluecose.core.pairing

import kotlin.random.Random

/**
 * 66-byte, 3-bits-per-byte Phase 5 session source — 1:1 port of LibreCRKit
 * Pairing/Phase5SessionData.swift.
 */
data class Phase5SessionData(val bytes: ByteArray) {
    init {
        if (bytes.size != BYTE_COUNT) throw Phase5SessionDataError.InvalidLength(bytes.size)
        for ((index, byte) in bytes.withIndex()) {
            val v = byte.toInt() and 0xFF
            if (v > 0x07) throw Phase5SessionDataError.Non3BitByte(index, v)
        }
    }

    /** Diagnostic generator for table/key-schedule tests only. */
    fun random(): Phase5SessionData =
        Phase5SessionData(ByteArray(BYTE_COUNT) { (Random.nextInt(8)).toByte() })

    fun phase5RawKey(schedule: Phase5KeySchedule): ByteArray = schedule.deriveRawKey(bytes)

    override fun equals(other: Any?): Boolean =
        other is Phase5SessionData && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        const val BYTE_COUNT = 66
    }
}

sealed class Phase5SessionDataError : Exception("phase 5 session data failure") {
    data class InvalidLength(val count: Int) : Phase5SessionDataError()
    data class Non3BitByte(val index: Int, val value: Int) : Phase5SessionDataError()
}
