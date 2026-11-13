package com.dsikic.cluecose.core.tables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies the vendored Abbott runtime tables in
 * `core/src/test/resources/RuntimeTables/` are complete and sized exactly as
 * the LibreCRKit `cachedTables` guards require. This is the pre-flight check
 * for the Phase-5 VM port: if this passes, the tables are valid input.
 */
class SourceTablesLoadTest {

    private val source = ClasspathTablesSource()

    @Test
    fun allVendoredTablesPassSizeGuards() {
        val tables = SourceTables.load(source)

        assertEquals(SourceTables.SBOX19_SIZE, tables.sbox19.size)
        assertTrue(tables.prog64e2b8.size >= SourceTables.PROG_64E2B8_MIN)
        assertTrue(tables.prog638840.size >= SourceTables.PROG_638840_MIN)
        assertEquals(SourceTables.LOW_SEED_STATICS_6388F0, tables.lowSeedStatics6388f0.size)
        assertEquals(SourceTables.LOW_LOOP_STATICS_6388F0, tables.lowLoopStatics6388f0.size)
        assertEquals(SourceTables.LANE_TABLES_6388F0, tables.laneTables6388f0.size)
        assertEquals(SourceTables.SELECTOR_TABLES_6388F0, tables.selectorMul6388f0.size)
        assertEquals(SourceTables.SELECTOR_TABLES_6388F0, tables.selectorAdd6388f0.size)
        assertEquals(SourceTables.U32_TABLES_63C278, tables.u32Tables63c278.size)
        assertEquals(SourceTables.FOLD_TABLES_63C278, tables.foldTables63c278.size)
        assertEquals(SourceTables.TAIL_FOLD_TABLES_633FA8, tables.tailFoldTables633fa8.size)
        assertEquals(SourceTables.TAIL_U32_LOW_TABLES_633FA8, tables.tailU32LowTables633fa8.size)
        assertEquals(SourceTables.NULL_TABLES_633FA8, tables.nullTables633fa8.size)
        assertEquals(SourceTables.NULL_NIBBLE_633FA8, tables.nullNibble633fa8.size)
        assertEquals(SourceTables.PROCESS2_PUBLIC_TABLE, tables.process2PublicTables.size)
        assertTrue(tables.prog67cc18.size >= SourceTables.PROG_67CC18_MIN)
        assertTrue(tables.ttableBExt.size >= SourceTables.TTABLE_B_EXT_MIN)
        assertEquals(SourceTables.FINAL_LEN_TABLES, tables.finalLenTables.size)
        assertEquals(SourceTables.DF80_ROUND_TABLES, tables.df80RoundTables.size)
        assertEquals(SourceTables.FINALIZER_TABLES, tables.finalizerTables.size)
        assertEquals(SourceTables.SEED_TABLES_679F48, tables.seedTables679f48.size)
        assertEquals(SourceTables.REDUCER_67EA28_NIBBLE, tables.reducer67ea28Nibble.size)
        assertEquals(SourceTables.PROG_67076C, tables.prog67076c.size)
    }

    @Test
    fun missingTableIsReported() {
        val source = TablesSource { _ ->
            throw TablesSourceError.MissingTable("nope.bin")
        }
        assertFailsWith<TablesSourceError.MissingTable> {
            SourceTables.load(source)
        }
    }
}
