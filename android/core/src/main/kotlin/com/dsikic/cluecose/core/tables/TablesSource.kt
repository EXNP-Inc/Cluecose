package com.dsikic.cluecose.core.tables

/**
 * Injectable source of the vendored Abbott runtime tables.
 *
 * - JVM tests: classpath resources under `RuntimeTables/`
 *   (`core/src/test/resources/RuntimeTables/`).
 * - Android app: `AssetsTablesSource` over
 *   `app/src/main/assets/RuntimeTables/`.
 *
 * This is the seam that keeps `:core` pure Kotlin/JVM — no Android imports.
 */
fun interface TablesSource {
    fun load(table: RuntimeTable): ByteArray
}

sealed class TablesSourceError : Exception("tables source failure") {
    data class MissingTable(val file: String) : TablesSourceError() {
        override fun toString() = "missing runtime table: $file"
    }
    data class InvalidTableSize(val file: String, val expected: String, val actual: Int) : TablesSourceError() {
        override fun toString() =
            "runtime table $file has size $actual, expected $expected"
    }
}

/**
 * The 23 tables the first-pair VM pipeline reads at once
 * (1:1 port of LibreCRKit FirstPairSourceSlice.swift `SourceTables`).
 */
data class SourceTables(
    val sbox19: ByteArray,
    val prog64e2b8: ByteArray,
    val prog638840: ByteArray,
    val lowSeedStatics6388f0: ByteArray,
    val lowLoopStatics6388f0: ByteArray,
    val laneTables6388f0: ByteArray,
    val selectorMul6388f0: ByteArray,
    val selectorAdd6388f0: ByteArray,
    val u32Tables63c278: ByteArray,
    val foldTables63c278: ByteArray,
    val tailFoldTables633fa8: ByteArray,
    val tailU32LowTables633fa8: ByteArray,
    val nullTables633fa8: ByteArray,
    val nullNibble633fa8: ByteArray,
    val process2PublicTables: ByteArray,
    val prog67cc18: ByteArray,
    val ttableBExt: ByteArray,
    val finalLenTables: ByteArray,
    val df80RoundTables: ByteArray,
    val finalizerTables: ByteArray,
    val seedTables679f48: ByteArray,
    val reducer67ea28Nibble: ByteArray,
    val prog67076c: ByteArray,
) {
    companion object {

        // Length constants — verbatim from FirstPairSourceSlice.swift lines 14663-14686.
        const val SBOX19_SIZE = 0x80000
        const val PROG_64E2B8_MIN = 0x250
        const val PROG_638840_MIN = 0x8200
        const val LOW_SEED_STATICS_6388F0 = 0x31E
        const val LOW_LOOP_STATICS_6388F0 = 0x194
        const val LANE_TABLES_6388F0 = 0x1248
        const val SELECTOR_TABLES_6388F0 = 0x20
        const val U32_TABLES_63C278 = 0x14790
        const val FOLD_TABLES_63C278 = 0x3B60
        const val TAIL_FOLD_TABLES_633FA8 = 0x380
        const val TAIL_U32_LOW_TABLES_633FA8 = 0x40
        const val NULL_TABLES_633FA8 = 0x140F
        const val NULL_NIBBLE_633FA8 = 0x40
        const val PROCESS2_PUBLIC_TABLE = 0x518
        const val PROG_67CC18_MIN = 0x6100
        const val TTABLE_B_EXT_MIN = 0x100000
        const val FINAL_LEN_TABLES = 0x600
        const val DF80_ROUND_TABLES = 0x492
        const val FINALIZER_TABLES = 0x12D2
        const val SEED_TABLES_679F48 = 0x6D2
        const val REDUCER_67EA28_NIBBLE = 0x40
        const val PROG_67076C = 0x84

        /** Load all 23 tables and validate sizes (Swift `cachedTables`). */
        @JvmStatic
        fun load(source: TablesSource): SourceTables {
            val sbox = RuntimeTable.SBOX19.load(source)
            val prog = RuntimeTable.FIRST_PAIR_PROG_64E2B8.load(source)
            val prog638840 = RuntimeTable.FIRST_PAIR_PROG_638840.load(source)
            val lowSeedStatics = RuntimeTable.FIRST_PAIR_6388F0_LOW_SEED_STATICS.load(source)
            val lowLoopStatics = RuntimeTable.FIRST_PAIR_6388F0_LOW_LOOP_STATICS.load(source)
            val laneTables = RuntimeTable.FIRST_PAIR_6388F0_LANE_TABLES.load(source)
            val selectorMul = RuntimeTable.FIRST_PAIR_6388F0_SELECTOR_MUL.load(source)
            val selectorAdd = RuntimeTable.FIRST_PAIR_6388F0_SELECTOR_ADD.load(source)
            val u32Tables = RuntimeTable.FIRST_PAIR_63C278_U32_TABLES.load(source)
            val foldTables = RuntimeTable.FIRST_PAIR_63C278_FOLD_TABLES.load(source)
            val tailFoldTables = RuntimeTable.FIRST_PAIR_633FA8_TAIL_FOLD_TABLES.load(source)
            val tailU32LowTables = RuntimeTable.FIRST_PAIR_633FA8_TAIL_U32_LOW_TABLES.load(source)
            val nullTables = RuntimeTable.FIRST_PAIR_633FA8_NULL_TABLES.load(source)
            val nullNibble = RuntimeTable.FIRST_PAIR_633FA8_NULL_NIBBLE.load(source)
            val process2Public = RuntimeTable.FIRST_PAIR_PROCESS2_PUBLIC_TABLES.load(source)
            val prog67 = RuntimeTable.FIRST_PAIR_PROG_67CC18.load(source)
            val ttableBExt = RuntimeTable.CHILD23_TTABLE_B_EXT.load(source)
            val finalLenTables = RuntimeTable.FIRST_PAIR_FINAL_LEN_TABLES.load(source)
            val df80RoundTables = RuntimeTable.FIRST_PAIR_DF80_ROUND_TABLES.load(source)
            val finalizerTables = RuntimeTable.FIRST_PAIR_FINALIZER_TABLES.load(source)
            val seedTables = RuntimeTable.FIRST_PAIR_679F48_SEED_TABLES.load(source)
            val reducerNibble = RuntimeTable.FIRST_PAIR_REDUCER_67EA28_NIBBLE.load(source)
            val prog67076c = RuntimeTable.FIRST_PAIR_PROG_67076C.load(source)

            requireExactSize(RuntimeTable.SBOX19, sbox, SBOX19_SIZE)
            requireMinSize(RuntimeTable.FIRST_PAIR_PROG_64E2B8, prog, PROG_64E2B8_MIN)
            requireMinSize(RuntimeTable.FIRST_PAIR_PROG_638840, prog638840, PROG_638840_MIN)
            requireExactSize(RuntimeTable.FIRST_PAIR_6388F0_LOW_SEED_STATICS, lowSeedStatics, LOW_SEED_STATICS_6388F0)
            requireExactSize(RuntimeTable.FIRST_PAIR_6388F0_LOW_LOOP_STATICS, lowLoopStatics, LOW_LOOP_STATICS_6388F0)
            requireExactSize(RuntimeTable.FIRST_PAIR_6388F0_LANE_TABLES, laneTables, LANE_TABLES_6388F0)
            requireExactSize(RuntimeTable.FIRST_PAIR_6388F0_SELECTOR_MUL, selectorMul, SELECTOR_TABLES_6388F0)
            requireExactSize(RuntimeTable.FIRST_PAIR_6388F0_SELECTOR_ADD, selectorAdd, SELECTOR_TABLES_6388F0)
            requireExactSize(RuntimeTable.FIRST_PAIR_63C278_U32_TABLES, u32Tables, U32_TABLES_63C278)
            requireExactSize(RuntimeTable.FIRST_PAIR_63C278_FOLD_TABLES, foldTables, FOLD_TABLES_63C278)
            requireExactSize(RuntimeTable.FIRST_PAIR_633FA8_TAIL_FOLD_TABLES, tailFoldTables, TAIL_FOLD_TABLES_633FA8)
            requireExactSize(RuntimeTable.FIRST_PAIR_633FA8_TAIL_U32_LOW_TABLES, tailU32LowTables, TAIL_U32_LOW_TABLES_633FA8)
            requireExactSize(RuntimeTable.FIRST_PAIR_633FA8_NULL_TABLES, nullTables, NULL_TABLES_633FA8)
            requireExactSize(RuntimeTable.FIRST_PAIR_633FA8_NULL_NIBBLE, nullNibble, NULL_NIBBLE_633FA8)
            requireExactSize(RuntimeTable.FIRST_PAIR_PROCESS2_PUBLIC_TABLES, process2Public, PROCESS2_PUBLIC_TABLE)
            requireMinSize(RuntimeTable.FIRST_PAIR_PROG_67CC18, prog67, PROG_67CC18_MIN)
            requireMinSize(RuntimeTable.CHILD23_TTABLE_B_EXT, ttableBExt, TTABLE_B_EXT_MIN)
            requireExactSize(RuntimeTable.FIRST_PAIR_FINAL_LEN_TABLES, finalLenTables, FINAL_LEN_TABLES)
            requireExactSize(RuntimeTable.FIRST_PAIR_DF80_ROUND_TABLES, df80RoundTables, DF80_ROUND_TABLES)
            requireExactSize(RuntimeTable.FIRST_PAIR_FINALIZER_TABLES, finalizerTables, FINALIZER_TABLES)
            requireExactSize(RuntimeTable.FIRST_PAIR_679F48_SEED_TABLES, seedTables, SEED_TABLES_679F48)
            requireExactSize(RuntimeTable.FIRST_PAIR_REDUCER_67EA28_NIBBLE, reducerNibble, REDUCER_67EA28_NIBBLE)
            requireExactSize(RuntimeTable.FIRST_PAIR_PROG_67076C, prog67076c, PROG_67076C)

            return SourceTables(
                sbox19 = sbox,
                prog64e2b8 = prog,
                prog638840 = prog638840,
                lowSeedStatics6388f0 = lowSeedStatics,
                lowLoopStatics6388f0 = lowLoopStatics,
                laneTables6388f0 = laneTables,
                selectorMul6388f0 = selectorMul,
                selectorAdd6388f0 = selectorAdd,
                u32Tables63c278 = u32Tables,
                foldTables63c278 = foldTables,
                tailFoldTables633fa8 = tailFoldTables,
                tailU32LowTables633fa8 = tailU32LowTables,
                nullTables633fa8 = nullTables,
                nullNibble633fa8 = nullNibble,
                process2PublicTables = process2Public,
                prog67cc18 = prog67,
                ttableBExt = ttableBExt,
                finalLenTables = finalLenTables,
                df80RoundTables = df80RoundTables,
                finalizerTables = finalizerTables,
                seedTables679f48 = seedTables,
                reducer67ea28Nibble = reducerNibble,
                prog67076c = prog67076c,
            )
        }

        private fun requireExactSize(table: RuntimeTable, bytes: ByteArray, expected: Int) {
            if (bytes.size != expected) {
                throw TablesSourceError.InvalidTableSize(table.fileName, "exact $expected", bytes.size)
            }
        }

        private fun requireMinSize(table: RuntimeTable, bytes: ByteArray, minimum: Int) {
            if (bytes.size < minimum) {
                throw TablesSourceError.InvalidTableSize(table.fileName, ">= $minimum", bytes.size)
            }
        }
    }
}
