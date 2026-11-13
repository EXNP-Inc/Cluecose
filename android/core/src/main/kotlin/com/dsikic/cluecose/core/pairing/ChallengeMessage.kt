package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.aesBlock128

/**
 * Phase 5 challenge wire format — 1:1 port of the *error surface* and the
 * standard-AES convenience overloads of LibreCRKit `Pairing/ChallengeMessage.swift`.
 *
 * The wire-format class itself ([Phase5Challenge], already ported) provides the
 * [AesBlockEncrypt]-level API; this file adds:
 *
 * - [ChallengeError], mirroring Swift `ChallengeError` (same cases, so error
 *   equality matches),
 * - the `sessionKey`-based convenience overloads
 *   ([Phase5Challenge.Companion.encryptSessionKey] = Swift
 *   `Phase5Challenge.encrypt(plaintext:sessionKey:nonce:aad:)`, and
 *   [Phase5Challenge.decryptSessionKey] = Swift
 *   `Phase5Challenge.decrypt(sessionKey:nonce:aad:)`), which build the
 *   standard AES-128 block closure (Swift `AESCCM.commonCryptoBlockEncrypt`)
 *   and forward to the [AesBlockEncrypt] overloads.
 *
 * The Phase-5 wire data plane uses `phase5BlockEncryptor` (white-box AES)
 * instead; these overloads exist for the standard-AES test/offline paths,
 * exactly as in the Swift file.
 */

/**
 * 1:1 port of Swift `ChallengeError` (same cases, so error equality matches).
 *
 * NOTE: the existing [Phase5Challenge] validates its invariants with `require`
 * (IllegalArgumentException) to match the style of its port — those checks
 * throw `IllegalArgumentException` with the same conditions; the sealed
 * hierarchy below is the Swift-mirrored error type for callers that match on
 * error identity.
 */
sealed class ChallengeError(message: String = "phase 5 challenge failure") : Exception(message) {

    /** `nonce.count != 7` (Swift `.wrongNonceSize`). */
    data class WrongNonceSize(val size: Int) : ChallengeError("wrong nonce size: $size")

    /** `ciphertext.count != 36` (Swift `.wrongCiphertextSize`). */
    data class WrongCiphertextSize(val size: Int) : ChallengeError("wrong ciphertext size: $size")

    /** `tag.count != 4` (Swift `.wrongTagSize`). */
    data class WrongTagSize(val size: Int) : ChallengeError("wrong tag size: $size")

    /** `plaintext.count != 36` (Swift `.wrongPlaintextSize`). */
    data class WrongPlaintextSize(val size: Int) : ChallengeError("wrong plaintext size: $size")

    /** `sessionKey.count != 16` (Swift `.wrongKeySize`). */
    data class WrongKeySize(val size: Int) : ChallengeError("wrong key size: $size")

    /** `raw.count != 54 && raw.count != 40` (Swift `.wrongWireSize`). */
    data class WrongWireSize(val size: Int) : ChallengeError("wrong wire size: $size")
}

/**
 * Swift `Phase5Challenge.encrypt(plaintext:sessionKey:nonce:aad:)` — build by
 * AES-CCM encrypting the 36B `R1 || R2 || tail4` plaintext under a standard
 * AES-128 [sessionKey].
 */
fun Phase5Challenge.Companion.encryptSessionKey(
    plaintext: ByteArray,
    sessionKey: ByteArray,
    nonce: ByteArray,
    aad: ByteArray = ByteArray(0),
): Phase5Challenge {
    if (sessionKey.size != 16) throw ChallengeError.WrongKeySize(sessionKey.size)
    return encrypt(plaintext = plaintext, aes = aesBlock128(sessionKey), nonce = nonce, aad = aad)
}

/**
 * Swift `Phase5Challenge.decrypt(sessionKey:nonce:aad:)` — decrypt with a
 * standard AES-128 [sessionKey].
 */
fun Phase5Challenge.decryptSessionKey(sessionKey: ByteArray, nonce: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
    if (sessionKey.size != 16) throw ChallengeError.WrongKeySize(sessionKey.size)
    return decrypt(aes = aesBlock128(sessionKey), nonce = nonce, aad = aad)
}
