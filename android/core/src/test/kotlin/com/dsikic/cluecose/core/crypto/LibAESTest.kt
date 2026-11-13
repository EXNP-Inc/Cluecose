package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.ClasspathTablesSource
import com.dsikic.cluecose.core.util.Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 1:1 port of LibreCRKitTests/LibAESTests.swift. */
class LibAESTest {

    private data class BlockVector(val name: String, val plaintext: ByteArray, val expectedHex: String)
    private data class Phase5Vector(
        val name: String,
        val key: ByteArray,
        val plaintext: ByteArray,
        val expectedHex: String,
    )

    private val tables = ClasspathTablesSource()

    private val rawKey = Hex.decode("3bb02ee4fdefe1737312a4668e7f8604")

    @Test
    fun keySetupAndBlockVectorsMatchPython() {
        val ctx = keySetup(rawKey, tables)
        assertEquals(LIBAES_CONTEXT_SIZE, ctx.size)

        val vectors = listOf(
            BlockVector("zero block", ByteArray(16), "bf7a0358dc26e61faeb3d310b30f0826"),
            BlockVector("counter block", ByteArray(16) { it.toByte() }, "d388d17cc37abcfa3354656979c5ac77"),
            BlockVector("apr26 R1", Hex.decode("db94448c6abde8bc183df11cf5cf197f"), "ef66b86917499e796a66a9edf2b6f8d2"),
            BlockVector("ff block", ByteArray(16) { 0xFF.toByte() }, "a87e6d65ec16f3669541ccebbb585758"),
            BlockVector("10..1f block", ByteArray(16) { (0x10 + it).toByte() }, "2c534187108ce4af212a12cfc4cf52fb"),
        )

        for (vector in vectors) {
            val actual = blockEncrypt(vector.plaintext, ctx, tables)
            assertEquals(vector.expectedHex, Hex.encode(actual), "${vector.name} diverged from Python lib_aes")
        }
    }

    @Test
    fun ccmOverLibAESMatchesPython() {
        val aes = blockEncryptor(rawKey, tables)
        val nonce = Hex.decode("010000007500dc")
        val plaintext = ByteArray(36) { it.toByte() }

        val ciphertextWithTag = AesCcm.encrypt(nonce, plaintext, tagLength = 8, aes = aes)
        assertEquals(
            "2be74afb317295bdc5eb664a38780ea660e8660fdb743dd487591762ee7d115657918fc4294fa2abd83c575a",
            Hex.encode(ciphertextWithTag),
        )

        val recovered = AesCcm.decrypt(nonce, ciphertextWithTag, tagLength = 8, aes = aes)
        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun phase5WireBlockMatchesPython5defecPort() {
        val vectors = listOf(
            Phase5Vector(
                "zero key zero block",
                ByteArray(16),
                ByteArray(16),
                "6b9bddb402786cba9adac3304b86028b",
            ),
            Phase5Vector(
                "range key range block",
                ByteArray(16) { it.toByte() },
                ByteArray(16) { it.toByte() },
                "aa4454e26d649350498357b4ce2596ed",
            ),
            Phase5Vector(
                "live 2026-05-06 A1",
                Hex.decode("3b16168843c299ad7fa311ba2440d58a"),
                Hex.decode("07210400008f8c4b0000000000000001"),
                "c4ccfb387363f51bf61df08fc6d39304",
            ),
        )

        for (vector in vectors) {
            val ctx = keySetup(vector.key, tables)
            val actual = phase5BlockEncrypt(vector.plaintext, ctx, tables)
            assertEquals(vector.expectedHex, Hex.encode(actual), "${vector.name} diverged from Python block_5defec")
        }
    }

    @Test
    fun phase5CCMOver5defecMatchesLiveWireTuple() {
        val key = Hex.decode("3b16168843c299ad7fa311ba2440d58a")
        val aes = phase5BlockEncryptor(key, tables)
        val nonce = Hex.decode("210400008f8c4b")
        val plaintext = Hex.decode(
            "8d2f296f882c1c0991d0e38c097892288c5b0b7441a7486d930806db08acdf1e3225ec72",
        )

        val ciphertextWithTag = AesCcm.encrypt(nonce, plaintext, tagLength = 4, aes = aes)
        assertEquals(
            "49e3d257fb4fe91267cd1303cfab012ca215375f94040f8e9340a139de69720a88dc15dd50d3931a",
            Hex.encode(ciphertextWithTag),
        )

        val recovered = AesCcm.decrypt(nonce, ciphertextWithTag, tagLength = 4, aes = aes)
        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun rejectsInvalidSizes() {
        assertFailsWith<IllegalArgumentException> { keySetup(ByteArray(15), tables) }
        assertFailsWith<IllegalArgumentException> { phase5BlockEncryptor(ByteArray(15), tables) }

        val ctx = keySetup(rawKey, tables)
        assertFailsWith<IllegalArgumentException> { blockEncrypt(ByteArray(15), ctx, tables) }
        assertFailsWith<IllegalArgumentException> { phase5BlockEncrypt(ByteArray(15), ctx, tables) }
    }
}
