package com.dsikic.cluecose.core.ble

import com.dsikic.cluecose.core.util.Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 1:1 port of LibreCRKit `Tests/LibreCRKitTests/BleFramingTests.swift`, plus
 * the round-trip / reset coverage the Swift suite implies: small, exact
 * multiple, and large message sizes for the write fragmenter, offset
 * discontinuity rejection, and reassembler reset.
 */
class BleFramingTest {

    // Phase 1 from findings/generateKAuth/BLE_PAIRING_TRANSCRIPT.md — phone
    // writes a 162-byte cert across 9 fragments to the auth characteristic.
    private val phase1Phone162 = Hex.decode(
        "03030102030405060708090a0b0c0d0e0f10000161897655010000000000000000048242be33f1a330880112fa62cc4842a43d1204922ad201d8775bb226f611f75b0ef3d5bc6cc4317caa457584ab003f1712336089d3a4f29838ed0dc666deaea2d65a00dfff5d7bcae21655e302e3458e774daaaaca87af75f1b87884b18d4ce875d0d108c903a834471a4ff674b2d30bcba062373014b7786e4437b177aec3c8",
    )

    // Phase 2 — 140-byte sensor cert reassembled from 8 notify fragments.
    private val phase2Sensor140 = Hex.decode(
        "0157259416c6007ae00550043e1f46f25d44b3d72a8c37dcfebc7c339ed01fc5668a6387458084ac9cafebe7438b649f76b81eeca9343287da162b07c5c07362997e40e13035df14cdf3d5d81a5fe9ffc84de8c8ecc33be609bf0d8ebf97bed6bae899e181bb32960c81e99d5aab30b2fc7a77b0d45eff9885d46722371cd9375e14352d890f60fa1e7159e6",
    )

    // MARK: - 1:1 ports of BleFramingTests.swift

    @Test
    fun `write fragmenter 162 cert`() {
        val frags = BleFraming.fragmentForWrite(phase1Phone162)
        assertEquals(9, frags.size)
        // Each fragment: 2-byte LE offset + up to 18 bytes
        frags.forEachIndexed { i, frag ->
            val claimedOffset = (frag[0].toInt() and 0xFF) or ((frag[1].toInt() and 0xFF) shl 8)
            assertEquals(i * 18, claimedOffset, "fragment $i offset")
            assertTrue(frag.size - 2 <= 18)
        }
        // First fragment carries the test-pattern header bytes.
        assertContentEquals(
            Hex.decode("03030102030405060708090a0b0c0d0e0f10"),
            frags[0].copyOfRange(2, 20),
        )
        // Reassembling fragments gives the original message back.
        val roundTrip = BleFraming.reassembleWrite(frags)
        assertContentEquals(phase1Phone162, roundTrip)
    }

    @Test
    fun `notify reassembler sensor cert`() {
        // Build wire-shape notifies: 1B seq (starting 0) + 19B chunk.
        val asm = BleFraming.NotifyReassembler()
        var seq = 0
        var idx = 0
        while (idx < phase2Sensor140.size) {
            val end = minOf(idx + 19, phase2Sensor140.size)
            val frag = ByteArray(1 + (end - idx))
            frag[0] = seq.toByte()
            System.arraycopy(phase2Sensor140, idx, frag, 1, end - idx)
            asm.feed(frag)
            seq += 1
            idx = end
        }
        assertEquals(140, asm.availableBytes)
        val assembled = asm.take(140)
        assertContentEquals(phase2Sensor140, assembled)
    }

    @Test
    fun `notify reassembler sequence gap`() {
        val asm = BleFraming.NotifyReassembler()
        asm.feed(byteArrayOf(0x00, 0xaa.toByte()))
        val e = assertFailsWith<BleFramingError.SequenceGap> {
            asm.feed(byteArrayOf(0x02, 0xbb.toByte()))
        }
        assertEquals(1, e.expected)
        assertEquals(2, e.got)
    }

    @Test
    fun `take more than available`() {
        val asm = BleFraming.NotifyReassembler()
        asm.feed(byteArrayOf(0x00, 0xaa.toByte(), 0xbb.toByte()))
        val e = assertFailsWith<BleFramingError.NotEnoughBytes> { asm.take(5) }
        assertEquals(2, e.have)
        assertEquals(5, e.want)
    }

    // MARK: - Round-trip coverage (small, exact multiple, large)

    @Test
    fun `fragment reassemble round trip small message`() {
        val msg = Hex.decode("03030102deadbeefcafe")
        val roundTrip = BleFraming.reassembleWrite(BleFraming.fragmentForWrite(msg))
        assertContentEquals(msg, roundTrip)
    }

    @Test
    fun `fragment reassemble round trip exact multiple of chunk`() {
        val msg = ByteArray(BleFraming.WRITE_CHUNK_PAYLOAD * 4) { (it * 7).toByte() }
        val frags = BleFraming.fragmentForWrite(msg)
        assertEquals(4, frags.size)
        frags.forEach { assertEquals(BleFraming.WRITE_CHUNK_PAYLOAD + 2, it.size) }
        assertContentEquals(msg, BleFraming.reassembleWrite(frags))
    }

    @Test
    fun `fragment reassemble round trip large message with 2 byte offsets`() {
        val msg = ByteArray(2048) { it.toByte() }
        val roundTrip = BleFraming.reassembleWrite(BleFraming.fragmentForWrite(msg))
        assertContentEquals(msg, roundTrip)
    }

    @Test
    fun `fragmenter with empty message yields no fragments`() {
        assertTrue(BleFraming.fragmentForWrite(ByteArray(0)).isEmpty())
    }

    // MARK: - Error paths + reset

    @Test
    fun `discontinuous write offsets throw`() {
        // 40 bytes → 3 fragments (18 + 18 + 4) at offsets 0, 18, 36.
        val frags = BleFraming.fragmentForWrite(ByteArray(40) { (it * 3).toByte() })
        assertEquals(3, frags.size)
        // Drop the offset-0 fragment: reassembly sees a gap at offset 0.
        assertFailsWith<BleFramingError.DiscontinuousOffsets> {
            BleFraming.reassembleWrite(frags.drop(1))
        }
        // Duplicate offset also breaks continuity.
        assertFailsWith<BleFramingError.DiscontinuousOffsets> {
            BleFraming.reassembleWrite(frags + frags[0])
        }
    }

    @Test
    fun `fragment too short throw`() {
        assertFailsWith<BleFramingError.FragmentTooShort> {
            BleFraming.reassembleWrite(listOf(byteArrayOf(0x00)))
        }
        assertFailsWith<BleFramingError.FragmentTooShort> {
            BleFraming.NotifyReassembler().feed(ByteArray(0))
        }
    }

    @Test
    fun `take consumes head and leaves tail`() {
        val asm = BleFraming.NotifyReassembler()
        asm.feed(byteArrayOf(0x00, 0x11, 0x22))
        asm.feed(byteArrayOf(0x01, 0x33, 0x44))
        assertContentEquals(byteArrayOf(0x11, 0x22, 0x33), asm.take(3))
        assertEquals(1, asm.availableBytes)
        assertContentEquals(byteArrayOf(0x44), asm.take(1))
        assertEquals(0, asm.availableBytes)
    }

    @Test
    fun `reset clears buffer and sequence counter`() {
        val asm = BleFraming.NotifyReassembler()
        asm.feed(byteArrayOf(0x00, 0x11))
        asm.feed(byteArrayOf(0x01, 0x22))
        asm.reset()
        assertEquals(0, asm.availableBytes)
        // Seq counter is back to "unset": the first fragment after reset
        // starts the sequence again from 0.
        asm.feed(byteArrayOf(0x00, 0x33))
        assertContentEquals(byteArrayOf(0x33), asm.take(1))
        // And the sequence continues from where the reset stream is.
        assertFailsWith<BleFramingError.SequenceGap> { asm.feed(byteArrayOf(0x00, 0x44)) }
    }

    @Test
    fun `notify seq wraps 255 to 0`() {
        // Mirrors Swift's `seq &+ 1` (UInt8 wraparound).
        val asm = BleFraming.NotifyReassembler()
        var seq = 255
        repeat(3) {
            asm.feed(byteArrayOf(seq.toByte(), 0x42))
            seq = (seq + 1) and 0xFF
        }
        assertEquals(3, asm.availableBytes)
        assertContentEquals(byteArrayOf(0x42, 0x42, 0x42), asm.take(3))
    }
}
