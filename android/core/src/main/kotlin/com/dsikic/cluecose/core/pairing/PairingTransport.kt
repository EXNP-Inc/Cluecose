package com.dsikic.cluecose.core.pairing

/**
 * Transport abstraction for the pairing state machine. 1:1 port of LibreCRKit
 * `Pairing/PairingTransport.swift` (minus `Sendable` — pure JVM core).
 *
 * Decouples [PairingFlow] from the concrete BLE stack so the same code path is
 * driven by a scripted in-memory transport in unit tests AND by a real GATT
 * session on device (the Android adapter blocks on the GATT callbacks and
 * fulfils these calls synchronously on a background thread).
 *
 * Wire-level fragmentation (write byte-offset prefix, notify seq prefix) lives
 * in `ble.BleFraming`; the transport does the framing.
 */
interface PairingTransport {
    /** Write a logical message (framed into 18B chunks per `BleFraming`). */
    fun write(message: ByteArray, to: BleCharRef)

    /** Reassemble notify fragments and return the first [exactly] bytes. */
    fun awaitNotify(on: BleCharRef, exactly: Int): ByteArray
}

/** Optional extension for the command-gated security state machine. */
interface CommandPairingTransport : PairingTransport {
    /** Write one command byte to the single-byte command/response characteristic. */
    fun writeCommand(command: Int)

    /** Read the next single-byte command/response notify (prefix-checked by caller). */
    fun awaitCommandResponse(timeoutSeconds: Int): ByteArray
}

/** Opaque GATT characteristic identity from the pairing flow's perspective. */
enum class BleCharRef {
    /** Cert + ephemeral exchange (handle 0x002d in observed captures). */
    CERT_HANDSHAKE,

    /** Challenge / response (handle 0x002a in observed captures). */
    CHALLENGE,
}
