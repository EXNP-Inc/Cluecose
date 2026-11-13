package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.tables.ClasspathTablesSource
import com.dsikic.cluecose.core.util.Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 1:1 port of LibreCRKitTests/Phase5KeyScheduleTests.swift. */
class Phase5KeyScheduleGoldenTest {

    private val schedule = Phase5KeySchedule(ClasspathTablesSource())

    private fun hex(s: String) = Hex.decode(s)

    @Test
    fun pythonReferenceVectors() {
        val vectors = listOf(
            "00".repeat(66) to "4facb8db3692f2714ebaea5f9ff22de6",
            (
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
                    "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f4041"
                ) to "56120a7e63561935008ef24e76f45d2a",
            (
                "050404000106040200060403000402060006030600010305070302070500030500020" +
                    "605020406000607050606000507060103030207040705040406030200060507"
                ) to "3b16168843c299ad7fa311ba2440d58a",
            (
                "02040702070305000006040006040206030003020605000105000301050000010202" +
                    "0605060500000207040000060507020702060304070705050502060603000407"
                ) to "3b16168843c299ad7fa311ba2440d58a",
            (
                "070705010506010205030100000305050600030300060304010205050201050606000407" +
                    "010102020704030606050705010404060101060507010404000200000204"
                ) to "3e2199e34b872cec7ea8b621542c77ff",
            (
                "040407050401050502030004070502010204000007030103030500070704060304" +
                    "040404000206070605020103020500020404000203010107040404070403050004"
                ) to "83f168a697970f7288c8a0abd0d83fee",
        )
        for ((inputHex, expectedHex) in vectors) {
            val key = schedule.deriveRawKey(hex(inputHex))
            assertEquals(expectedHex, Hex.encode(key), "input $inputHex")
        }
    }

    @Test
    fun rejectsWrongInputLength() {
        val error = assertFailsWith<Phase5KeyScheduleError.InvalidInputLength> {
            schedule.deriveRawKey(ByteArray(65))
        }
        assertEquals(65, error.count)
    }

    @Test
    fun sessionDataRejectsWrongLength() {
        val error = assertFailsWith<Phase5SessionDataError.InvalidLength> {
            Phase5SessionData(ByteArray(65))
        }
        assertEquals(65, error.count)
    }

    @Test
    fun sessionDataRejectsNon3BitBytes() {
        val bytes = ByteArray(Phase5SessionData.BYTE_COUNT)
        bytes[17] = 0x08
        val error = assertFailsWith<Phase5SessionDataError.Non3BitByte> {
            Phase5SessionData(bytes)
        }
        assertEquals(17, error.index)
        assertEquals(0x08, error.value)
    }

    @Test
    fun sessionDataDerivesRawKey() {
        val session = Phase5SessionData(ByteArray(Phase5SessionData.BYTE_COUNT))
        val key = session.phase5RawKey(schedule)
        assertContentEquals(hex("4facb8db3692f2714ebaea5f9ff22de6"), key)
    }

    @Test
    fun randomSessionDataIs66MaskedBytes() {
        val session = Phase5SessionData(ByteArray(Phase5SessionData.BYTE_COUNT)).random()
        assertEquals(Phase5SessionData.BYTE_COUNT, session.bytes.size)
        assertTrue(session.bytes.all { (it.toInt() and 0xFF) <= 0x07 })
        assertEquals(16, session.phase5RawKey(schedule).size)
    }
}
