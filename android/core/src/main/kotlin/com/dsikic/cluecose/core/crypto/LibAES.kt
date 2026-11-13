package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.RuntimeTable
import com.dsikic.cluecose.core.tables.TablesSource
import com.dsikic.cluecose.core.tables.TablesSourceError

/**
 * Clean-room port of Abbott's "lib AES" table-driven block primitive — 1:1
 * port of LibreCRKit LibAES.swift, itself a clean-room port of:
 *
 * - key setup:     lib+0x5dec48..0x5defb0
 * - block encrypt: lib+0x5e41b4..0x5e45e4
 * - wire block:    lib+0x5defec..0x5df414
 *
 * This is a table-driven AES-like primitive, not CommonCrypto AES. The
 * static tables are the vendored `libaes_*.bin` files under RuntimeTables
 * (the same 9 files LibreCRKit bundles; see [LibAESTables]).
 *
 * Two block ciphers share the key setup:
 * - [blockEncryptor]: the generic wire primitive, e.g. the CCM block
 *   encryptor for wrapping persistent SKB blobs.
 * - [phase5BlockEncryptor]: the Phase 5 wire block (lib+0x5defec) — the
 *   16-byte-block encryptor the live Phase 5 data plane uses.
 *
 * Pure Kotlin/JVM — no Android imports; tables are injected via
 * [TablesSource] (JVM tests: classpath, app: assets).
 */

/** Key-schedule context size (`LibAES.contextSize` = 0x10b0). */
const val LIBAES_CONTEXT_SIZE = 0x10B0

/**
 * The 9 static tables the lib AES primitive reads (1:1 with LibreCRKit
 * `LibAESTables`), loaded through [TablesSource] so `:core` stays pure
 * Kotlin/JVM. Sizes are the vendored file sizes, validated on load.
 *
 * Word tables ([round2Tables], [finalTableWords]) are reinterpreted as
 * little-endian words exactly as the Swift does after loading the `.bin`
 * files; [phase5RoundTables] is the Swift `phase5RoundTables` alias of
 * [finalTableWords].
 */
class LibAESTables(
    /** `libaes_round1_tables_278dc2.bin` — round-1 T tables (generic primitive). */
    val round1Tables: ByteArray,
    /** `libaes_round2_9_tables_279dc4.bin` as little-endian words — 16 × 256. */
    val round2Tables: IntArray,
    /** `libaes_5defec_round1_tables_26f621.bin` — Phase 5 round-1 tables. */
    val phase5Round1Tables: ByteArray,
    /** `libaes_keyexp_tables_275bbb.bin` — key-schedule SubWord tables. */
    val keyexpTables: ByteArray,
    /** `libaes_keyexp_consts_276bbc.bin` — key-schedule constants. */
    val keyexpConsts: ByteArray,
    /** `libaes_final_key_tables_276cfc.bin` — final round-key whitening. */
    val finalKeyTables: ByteArray,
    /** `libaes_final_table_index_277cfc.bin` — 16 LE words selecting the output tables. */
    val finalTableIndex: ByteArray,
    /** `libaes_final_table_map_277d3c.bin` — 16 × 256 output byte maps. */
    val finalTableMap: ByteArray,
    /** `libaes_final_table_words_270624.bin` as little-endian words — 16 × 256. */
    val finalTableWords: IntArray,
) {

    /** Phase 5 round tables = [finalTableWords] (Swift `phase5RoundTables`). */
    val phase5RoundTables: IntArray get() = finalTableWords

    companion object {
        const val SIZE_ROUND1 = 0x1000
        const val SIZE_ROUND2_9 = 0x4000
        const val SIZE_5DEFEC_ROUND1 = 0x1000
        const val SIZE_KEYEXP_TABLES = 0x1000
        const val SIZE_KEYEXP_CONSTS = 0x140
        const val SIZE_FINAL_KEY_TABLES = 0x1000
        const val SIZE_FINAL_TABLE_INDEX = 0x40
        const val SIZE_FINAL_TABLE_MAP = 0x1000
        const val SIZE_FINAL_TABLE_WORDS = 0x4000

        /** Load and validate all 9 tables (Swift `LibAESTables.shared`). */
        @JvmStatic
        fun load(source: TablesSource): LibAESTables {
            return LibAESTables(
                round1Tables = loadExact(RuntimeTable.LIBAES_ROUND1_TABLES, source, SIZE_ROUND1),
                round2Tables = loadWords(
                    loadExact(RuntimeTable.LIBAES_ROUND2_9_TABLES, source, SIZE_ROUND2_9),
                ),
                phase5Round1Tables = loadExact(
                    RuntimeTable.LIBAES_5DEFEC_ROUND1_TABLES,
                    source,
                    SIZE_5DEFEC_ROUND1,
                ),
                keyexpTables = loadExact(RuntimeTable.LIBAES_KEYEXP_TABLES, source, SIZE_KEYEXP_TABLES),
                keyexpConsts = loadExact(RuntimeTable.LIBAES_KEYEXP_CONSTS, source, SIZE_KEYEXP_CONSTS),
                finalKeyTables = loadExact(
                    RuntimeTable.LIBAES_FINAL_KEY_TABLES,
                    source,
                    SIZE_FINAL_KEY_TABLES,
                ),
                finalTableIndex = loadExact(
                    RuntimeTable.LIBAES_FINAL_TABLE_INDEX,
                    source,
                    SIZE_FINAL_TABLE_INDEX,
                ),
                finalTableMap = loadExact(RuntimeTable.LIBAES_FINAL_TABLE_MAP, source, SIZE_FINAL_TABLE_MAP),
                finalTableWords = loadWords(
                    loadExact(RuntimeTable.LIBAES_FINAL_TABLE_WORDS, source, SIZE_FINAL_TABLE_WORDS),
                ),
            )
        }

        private fun loadExact(table: RuntimeTable, source: TablesSource, expected: Int): ByteArray {
            val bytes = table.load(source)
            if (bytes.size != expected) {
                throw TablesSourceError.InvalidTableSize(table.fileName, "exact $expected", bytes.size)
            }
            return bytes
        }

        private fun loadWords(bytes: ByteArray): IntArray {
            val out = IntArray(bytes.size / 4)
            for (i in out.indices) {
                val o = i * 4
                out[i] = (bytes[o].toInt() and 0xFF) or
                    ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[o + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[o + 3].toInt() and 0xFF) shl 24)
            }
            return out
        }
    }
}

/**
 * Phase 5 wire block encryptor (lib+0x5defec) under a 16-byte raw key — the
 * block primitive the live Phase 5 data plane uses (Swift
 * `LibAES.phase5BlockEncryptor(rawKey:)`). Tables are loaded once; the
 * returned [AesBlockEncrypt] reuses the key-schedule context.
 */
fun phase5BlockEncryptor(rawKey: ByteArray, tables: TablesSource): AesBlockEncrypt {
    val t = LibAESTables.load(tables)
    val ctx = keySetup(rawKey, t)
    return AesBlockEncrypt { block -> phase5BlockEncrypt(block, ctx, t) }
}

/** Generic lib AES wire block encryptor under a 16-byte raw key (Swift `LibAES.blockEncryptor`). */
fun blockEncryptor(rawKey: ByteArray, tables: TablesSource): AesBlockEncrypt {
    val t = LibAESTables.load(tables)
    val ctx = keySetup(rawKey, t)
    return AesBlockEncrypt { block -> blockEncrypt(block, ctx, t) }
}

/** Convenience overload of [keySetup] taking a [TablesSource] instead of a loaded [LibAESTables]. */
fun keySetup(rawKey: ByteArray, tables: TablesSource): ByteArray = keySetup(rawKey, LibAESTables.load(tables))

/** Convenience overload of [blockEncrypt] taking a [TablesSource]. */
fun blockEncrypt(plaintext: ByteArray, ctx: ByteArray, tables: TablesSource): ByteArray =
    blockEncrypt(plaintext, ctx, LibAESTables.load(tables))

/** Convenience overload of [phase5BlockEncrypt] taking a [TablesSource]. */
fun phase5BlockEncrypt(plaintext: ByteArray, ctx: ByteArray, tables: TablesSource): ByteArray =
    phase5BlockEncrypt(plaintext, ctx, LibAESTables.load(tables))

/**
 * Key setup (lib+0x5dec48..0x5defb0): expands a 16-byte raw key into the
 * 0x10b0 context — raw key at 0x00, ten round-key word-groups at
 * 0x10 + k*0x10, whitened final key words at 0xa0, and 16 per-key output
 * byte tables at 0xb0 + i*0x100.
 */
fun keySetup(rawKey: ByteArray, t: LibAESTables): ByteArray {
    require(rawKey.size == 16) { "lib AES key must be 16 bytes, got ${rawKey.size}" }
    val ctx = ByteArray(LIBAES_CONTEXT_SIZE)
    rawKey.copyInto(ctx, 0)

    var outOff = 0x10
    var constA = 0x08     // 0x276bc4 - 0x276bbc
    var constB = 0xa8     // 0x276c64 - 0x276bbc
    var loopCtr = -4
    var w13 = readU32(ctx, 0x0c)

    while (true) {
        val w14 = readU32(t.keyexpConsts, constA - 8)
        loopCtr += 4
        val keepGoing = loopCtr < 0x24

        w13 = w14 xor ror32(w13, 24)
        var sub = subword(t.keyexpTables, w13, 0)
        val prev0 = readU32(ctx, outOff - 0x10)
        val prev1 = readU32(ctx, outOff - 0x0c)
        var w15 = prev0 xor readU32(t.keyexpConsts, constB - 8)
        w13 = w15 xor sub
        putU32(ctx, outOff, w13)

        w13 = readU32(t.keyexpConsts, constA - 4) xor w13
        sub = subword(t.keyexpTables, w13, 1)
        var w14Mix = prev1 xor readU32(t.keyexpConsts, constB - 4)
        w13 = w14Mix xor sub
        putU32(ctx, outOff + 0x04, w13)

        w13 = readU32(t.keyexpConsts, constA) xor w13
        sub = subword(t.keyexpTables, w13, 2)
        val prev2 = readU32(ctx, outOff - 0x08)
        val prev3 = readU32(ctx, outOff - 0x04)
        w15 = prev2 xor readU32(t.keyexpConsts, constB)
        w13 = w15 xor sub
        putU32(ctx, outOff + 0x08, w13)

        w13 = readU32(t.keyexpConsts, constA + 0x04) xor w13
        constA += 0x10
        sub = subword(t.keyexpTables, w13, 3)
        w14Mix = prev3 xor readU32(t.keyexpConsts, constB + 0x04)
        constB += 0x10
        w13 = w14Mix xor sub
        putU32(ctx, outOff + 0x0c, w13)

        outOff += 0x10
        if (!keepGoing) break
    }

    for (group in 0 until 4) {
        val off = 0xa0 + group * 4
        putU32(ctx, off, subword(t.finalKeyTables, readU32(ctx, off), group))
    }

    for (tableIdx in 0 until 16) {
        val wordTableIdx = readU32(t.finalTableIndex, tableIdx * 4)
        val wordTableBase = wordTableIdx * 0x100 // Swift: wordTableIdx * 0x400 bytes
        val mapBase = tableIdx * 0x100
        val keyWord = readU32(ctx, 0xa0 + (tableIdx ushr 2) * 4)
        val shift = 24 - 8 * (tableIdx and 3)
        val dst = 0xb0 + tableIdx * 0x100

        for (i in 0 until 256) {
            val mixed = keyWord xor t.finalTableWords[wordTableBase + i]
            ctx[dst + i] = t.finalTableMap[mapBase + ((mixed ushr shift) and 0xFF)]
        }
    }

    return ctx
}

/**
 * Generic wire block encrypt (lib+0x5e41b4..0x5e45e4) over a context built
 * by [keySetup]. 16-byte block in, 16-byte block out.
 */
fun blockEncrypt(plaintext: ByteArray, ctx: ByteArray, t: LibAESTables): ByteArray {
    require(plaintext.size == 16) { "lib AES block must be 16 bytes, got ${plaintext.size}" }
    require(ctx.size >= LIBAES_CONTEXT_SIZE) { "lib AES context too small: ${ctx.size} < $LIBAES_CONTEXT_SIZE" }

    // Round 1: T-table mix XOR raw-key context bytes (fused initial AddRoundKey).
    val state = ByteArray(16)
    for (c in 0 until 4) {
        for (r in 0 until 4) {
            val ti = c * 4 + (3 - r)
            state[c * 4 + r] = (
                (t.round1Tables[ti * 0x100 + (plaintext[ti].toInt() and 0xFF)].toInt() and 0xFF) xor
                    (ctx[c * 4 + r].toInt() and 0xFF)
                ).toByte()
        }
    }

    var w16 = readU32(state, 0)
    var w17 = readU32(state, 4)
    var w14 = readU32(state, 8)
    var w13 = readU32(state, 12)
    var x8 = 0
    var w11 = 0
    var w15 = 0

    while (true) {
        w11 = w17 and 0xFF
        w15 = ubfx(w13, 16, 8)
        var w0 = ubfx(w14, 8, 8)
        var x11i = w11
        var w3 = w16 ushr 24
        var w6 = readU32(ctx, 0x10 + x8)
        var w7 = readU32(ctx, 0x14 + x8)
        var x15i = w15
        var x0i = w0
        w11 = tableWord(t.round2Tables, 7, x11i)
        var w5 = w14 and 0xFF
        var w4 = ubfx(w13, 8, 8)
        w3 = tableWord(t.round2Tables, 0, w3)
        var x5i = w5
        w15 = tableWord(t.round2Tables, 13, x15i)
        w0 = tableWord(t.round2Tables, 10, x0i)
        w11 = w11 xor w6
        w6 = ubfx(w16, 16, 8)
        var w19 = w17 ushr 24
        w11 = w11 xor w15
        w15 = w0 xor w3
        x0i = w4
        w3 = tableWord(t.round2Tables, 11, x5i)
        var x4i = w6
        x5i = w19
        w6 = w13 and 0xFF
        w0 = tableWord(t.round2Tables, 14, x0i)
        w19 = w14 ushr 24
        w4 = tableWord(t.round2Tables, 1, x4i)
        w5 = tableWord(t.round2Tables, 4, x5i)
        var x6i = w6
        w3 = w3 xor w7
        val x7i = w19
        w11 = w11 xor w15
        w15 = w3 xor w0
        w0 = w4 xor w5
        w6 = tableWord(t.round2Tables, 15, x6i)
        w19 = readU32(ctx, 0x18 + x8)
        w4 = readU32(ctx, 0x1c + x8)
        w3 = tableWord(t.round2Tables, 8, x7i)
        val w7i = ubfx(w17, 16, 8)
        val w14i = ubfx(w14, 16, 8)
        val w17i = ubfx(w17, 8, 8)
        val w13i = w13 ushr 24
        w5 = w19 xor w6
        val w6i = ubfx(w16, 8, 8)
        val w16i = w16 and 0xFF
        val w16t = tableWord(t.round2Tables, 3, w16i)
        val w7t = tableWord(t.round2Tables, 5, w7i)
        val w14t = tableWord(t.round2Tables, 9, w14i)
        val w6t = tableWord(t.round2Tables, 2, w6i)
        val w17t = tableWord(t.round2Tables, 6, w17i)
        val w13t = tableWord(t.round2Tables, 12, w13i)
        w16 = w4 xor w16t
        w3 = w5 xor w3
        w5 = w6t xor w7t
        w14 = w17t xor w14t
        w16 = w16 xor w13t
        w13 = w15 xor w0
        w15 = w3 xor w5
        w14 = w14 xor w16

        if (x8 == 0x80) break

        w17 = w13 and 0xFF
        w16 = w11 ushr 24
        w0 = ubfx(w15, 8, 8)
        w4 = ubfx(w11, 16, 8)
        val x17i = w17
        w5 = w15 and 0xFF
        w3 = ubfx(w14, 16, 8)
        w6 = ubfx(w14, 8, 8)
        w16 = tableWord(t.round2Tables, 0, w16)
        x0i = w0
        x4i = w4
        w17 = tableWord(t.round2Tables, 7, x17i)
        w7 = w13 ushr 24
        x5i = w5
        val x3iFirst = w3
        w0 = tableWord(t.round2Tables, 10, x0i)
        w4 = tableWord(t.round2Tables, 1, x4i)
        w16 = w17 xor w16
        w17 = tableWord(t.round2Tables, 11, x5i)
        x5i = w6
        x6i = w7
        w16 = w16 xor w0
        w0 = tableWord(t.round2Tables, 13, x3iFirst)
        val oldX8 = x8
        x8 += 0x20
        w17 = w17 xor w4
        w3 = tableWord(t.round2Tables, 14, x5i)
        w4 = tableWord(t.round2Tables, 4, x6i)
        w5 = w14 and 0xFF
        val w14tmp = w14 ushr 24
        w6 = ubfx(w11, 8, 8)
        w3 = w3 xor w4
        x5i = w5
        val w11tmp = w11 and 0xFF
        val w7rk = readU32(ctx, 0x20 + oldX8)
        val w4rk = readU32(ctx, 0x24 + oldX8)
        w17 = w17 xor w3
        val x14i = w14tmp
        x11i = w11tmp
        val x3i = w6
        w0 = w0 xor w7rk
        w17 = w17 xor w4rk
        val w4idx = ubfx(w15, 16, 8)
        w16 = w16 xor w0
        w0 = tableWord(t.round2Tables, 15, x5i)
        val w5idx = ubfx(w13, 8, 8)
        val w15tmp = w15 ushr 24
        val w13tmp = ubfx(w13, 16, 8)
        x4i = w4idx
        x5i = w5idx
        val w14v = tableWord(t.round2Tables, 12, x14i)
        val w11v = tableWord(t.round2Tables, 3, x11i)
        x15i = w15tmp
        val x13i = w13tmp
        val w4v = tableWord(t.round2Tables, 9, x4i)
        val w6rk = readU32(ctx, 0x28 + oldX8)
        val w12rk = readU32(ctx, 0x2c + oldX8)
        val w5v = tableWord(t.round2Tables, 6, x5i)
        val w3v = tableWord(t.round2Tables, 2, x3i)
        val w15v = tableWord(t.round2Tables, 8, x15i)
        val w13v = tableWord(t.round2Tables, 5, x13i)
        w11 = w14v xor w11v
        w0 = w0 xor w6rk
        w4 = w5v xor w4v
        w14 = w0 xor w3v
        w13 = w15v xor w13v
        w11 = w4 xor w11
        w14 = w14 xor w13
        w13 = w11 xor w12rk
    }

    // Final round through the per-key output tables at ctx 0xb0..0x10b0.
    val out = ByteArray(16)
    out[0] = dyn(ctx, 0x0b0, w11 ushr 24)
    out[1] = dyn(ctx, 0x1b0, w14 ushr 16)
    out[2] = dyn(ctx, 0x2b0, w15 ushr 8)
    out[3] = dyn(ctx, 0x3b0, w13)
    out[4] = dyn(ctx, 0x4b0, w13 ushr 24)
    out[5] = dyn(ctx, 0x5b0, w11 ushr 16)
    out[6] = dyn(ctx, 0x6b0, w14 ushr 8)
    out[7] = dyn(ctx, 0x7b0, w15)
    out[8] = dyn(ctx, 0x8b0, w15 ushr 24)
    out[9] = dyn(ctx, 0x9b0, w13 ushr 16)
    out[10] = dyn(ctx, 0xab0, w11 ushr 8)
    out[11] = dyn(ctx, 0xbb0, w14)
    out[12] = dyn(ctx, 0xcb0, w14 ushr 24)
    out[13] = dyn(ctx, 0xdb0, w15 ushr 16)
    out[14] = dyn(ctx, 0xeb0, w13 ushr 8)
    out[15] = dyn(ctx, 0xfb0, w11)
    return out
}

/**
 * Phase 5 wire block encrypt (lib+0x5defec..0x5df414) over a context built
 * by [keySetup]. 16-byte block in, 16-byte block out — the primitive the
 * live Phase 5 data plane drives CCM with.
 */
fun phase5BlockEncrypt(plaintext: ByteArray, ctx: ByteArray, t: LibAESTables): ByteArray {
    require(plaintext.size == 16) { "lib AES block must be 16 bytes, got ${plaintext.size}" }
    require(ctx.size >= LIBAES_CONTEXT_SIZE) { "lib AES context too small: ${ctx.size} < $LIBAES_CONTEXT_SIZE" }

    val r1 = t.phase5Round1Tables
    val p = IntArray(16) { plaintext[it].toInt() and 0xFF }

    var w16 = readU32(ctx, 0x00) xor
        ((r1[0x000 + p[0]].toInt() and 0xFF) shl 24) xor
        ((r1[0x100 + p[1]].toInt() and 0xFF) shl 16) xor
        ((r1[0x200 + p[2]].toInt() and 0xFF) shl 8) xor
        (r1[0x300 + p[3]].toInt() and 0xFF)
    var w14 = readU32(ctx, 0x04) xor
        ((r1[0x400 + p[4]].toInt() and 0xFF) shl 24) xor
        ((r1[0x500 + p[5]].toInt() and 0xFF) shl 16) xor
        ((r1[0x600 + p[6]].toInt() and 0xFF) shl 8) xor
        (r1[0x700 + p[7]].toInt() and 0xFF)
    var w13 = readU32(ctx, 0x08) xor
        ((r1[0x800 + p[8]].toInt() and 0xFF) shl 24) xor
        ((r1[0x900 + p[9]].toInt() and 0xFF) shl 16) xor
        ((r1[0xa00 + p[10]].toInt() and 0xFF) shl 8) xor
        (r1[0xb00 + p[11]].toInt() and 0xFF)
    var w15 = readU32(ctx, 0x0c) xor
        ((r1[0xc00 + p[12]].toInt() and 0xFF) shl 24) xor
        ((r1[0xd00 + p[13]].toInt() and 0xFF) shl 16) xor
        ((r1[0xe00 + p[14]].toInt() and 0xFF) shl 8) xor
        (r1[0xf00 + p[15]].toInt() and 0xFF)

    var x8 = 0
    var w11 = 0
    while (true) {
        val f = phase5FirstHalf(t, w16, w14, w13, w15, ctx, x8)
        w11 = f.a
        w14 = f.b
        w13 = f.c
        w15 = f.d
        if (x8 == 0x80) break
        val s = phase5SecondHalf(t, w11, w14, w13, w15, ctx, x8)
        w16 = s.a
        w14 = s.b
        w13 = s.c
        w15 = s.d
        x8 += 0x20
    }

    // Final round through the per-key output tables at ctx 0xb0..0x10b0.
    val out = ByteArray(16)
    out[0] = dyn(ctx, 0x0b0, w11 ushr 24)
    out[1] = dyn(ctx, 0x1b0, w14 ushr 16)
    out[2] = dyn(ctx, 0x2b0, w13 ushr 8)
    out[3] = dyn(ctx, 0x3b0, w15)
    out[4] = dyn(ctx, 0x4b0, w14 ushr 24)
    out[5] = dyn(ctx, 0x5b0, w13 ushr 16)
    out[6] = dyn(ctx, 0x6b0, w15 ushr 8)
    out[7] = dyn(ctx, 0x7b0, w11)
    out[8] = dyn(ctx, 0x8b0, w13 ushr 24)
    out[9] = dyn(ctx, 0x9b0, w15 ushr 16)
    out[10] = dyn(ctx, 0xab0, w11 ushr 8)
    out[11] = dyn(ctx, 0xbb0, w14)
    out[12] = dyn(ctx, 0xcb0, w15 ushr 24)
    out[13] = dyn(ctx, 0xdb0, w11 ushr 16)
    out[14] = dyn(ctx, 0xeb0, w14 ushr 8)
    out[15] = dyn(ctx, 0xfb0, w13)
    return out
}

// MARK: - Private helpers (1:1 with LibAES.swift)

/** Four lib AES state words (Swift returns 4-tuples from the phase-5 half rounds). */
private data class W4(val a: Int, val b: Int, val c: Int, val d: Int)

/** Extract `width` bits starting at `lsb` (ARM UBFX, width always 8 here). */
private fun ubfx(x: Int, lsb: Int, width: Int): Int =
    (x ushr lsb) and ((1 shl width) - 1)

/** Rotate right by `shift` bits (shift != 0 here). */
private fun ror32(x: Int, shift: Int): Int =
    (x ushr shift) or (x shl (32 - shift))

/** Little-endian u32 read (Swift `libAESReadU32`). */
private fun readU32(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)

/** Little-endian u32 write (Swift `putU32`). */
private fun putU32(b: ByteArray, off: Int, v: Int) {
    b[off] = (v and 0xFF).toByte()
    b[off + 1] = ((v ushr 8) and 0xFF).toByte()
    b[off + 2] = ((v ushr 16) and 0xFF).toByte()
    b[off + 3] = ((v ushr 24) and 0xFF).toByte()
}

/**
 * Key-schedule SubWord through 4 byte tables (Swift `subword`): byte i of
 * `value` maps through the group table at 0x{i*0x100} with a fixed
 * permutation — b0 -> low byte, b2 -> 0x10, b1 -> 0x02, b3 -> high byte.
 */
private fun subword(table: ByteArray, value: Int, group: Int): Int {
    val base = group * 0x400
    val b0 = value and 0xFF
    val b1 = (value ushr 8) and 0xFF
    val b2 = (value ushr 16) and 0xFF
    val b3 = (value ushr 24) and 0xFF
    return (table[base + 0x300 + b0].toInt() and 0xFF) or
        ((table[base + 0x100 + b2].toInt() and 0xFF) shl 16) or
        ((table[base + 0x200 + b1].toInt() and 0xFF) shl 8) or
        ((table[base + b3].toInt() and 0xFF) shl 24)
}

/** One 32-bit word out of a 16 × 256 word table (Swift `tableWord`). */
private fun tableWord(table: IntArray, tableIdx: Int, index: Int): Int =
    table[tableIdx * 256 + (index and 0xFF)]

/** Byte out of one of the 16 per-key output tables (Swift `dyn`). */
private fun dyn(ctx: ByteArray, off: Int, idx: Int): Byte =
    ctx[off + (idx and 0xFF)]

/**
 * Phase 5 half round A (Swift `phase5FirstHalf`): the first four round
 * words of each round group, using round-key words ctx[0x10 + x8 .. 0x1c + x8].
 */
private fun phase5FirstHalf(
    t: LibAESTables,
    w16: Int,
    w14: Int,
    w13: Int,
    w15: Int,
    ctx: ByteArray,
    x8: Int,
): W4 {
    val tb = t.phase5RoundTables
    val rk0 = readU32(ctx, 0x10 + x8)
    val rk1 = readU32(ctx, 0x14 + x8)
    val rk2 = readU32(ctx, 0x18 + x8)
    val rk3 = readU32(ctx, 0x1c + x8)

    val out11 = tableWord(tb, 0, w16 ushr 24) xor
        rk0 xor
        tableWord(tb, 15, w15) xor
        tableWord(tb, 5, ubfx(w14, 16, 8)) xor
        tableWord(tb, 10, ubfx(w13, 8, 8))

    val out14 = (rk1 xor tableWord(tb, 3, w16) xor tableWord(tb, 9, ubfx(w13, 16, 8))) xor
        (tableWord(tb, 4, w14 ushr 24) xor tableWord(tb, 14, ubfx(w15, 8, 8)))

    val out13 = tableWord(tb, 13, ubfx(w15, 16, 8)) xor
        tableWord(tb, 7, w14) xor
        tableWord(tb, 2, ubfx(w16, 8, 8)) xor
        tableWord(tb, 8, w13 ushr 24) xor
        rk2

    val out15 = (tableWord(tb, 6, ubfx(w14, 8, 8)) xor rk3) xor
        (tableWord(tb, 12, w15 ushr 24) xor tableWord(tb, 11, w13)) xor
        tableWord(tb, 1, ubfx(w16, 16, 8))

    return W4(out11, out14, out13, out15)
}

/**
 * Phase 5 half round B (Swift `phase5SecondHalf`): the second four round
 * words of each round group, using round-key words ctx[0x20 + x8 .. 0x2c + x8].
 */
private fun phase5SecondHalf(
    t: LibAESTables,
    w11: Int,
    w14: Int,
    w13: Int,
    w15: Int,
    ctx: ByteArray,
    x8: Int,
): W4 {
    val tb = t.phase5RoundTables
    val rk0 = readU32(ctx, 0x20 + x8)
    val rk1 = readU32(ctx, 0x24 + x8)
    val rk2 = readU32(ctx, 0x28 + x8)
    val rk3 = readU32(ctx, 0x2c + x8)

    val out16 = tableWord(tb, 15, w15) xor
        tableWord(tb, 0, w11 ushr 24) xor
        tableWord(tb, 10, ubfx(w13, 8, 8)) xor
        tableWord(tb, 5, ubfx(w14, 16, 8)) xor
        rk0

    val out14 = (tableWord(tb, 4, w14 ushr 24) xor
        tableWord(tb, 3, w11) xor
        tableWord(tb, 14, ubfx(w15, 8, 8))) xor
        rk1 xor
        tableWord(tb, 9, ubfx(w13, 16, 8))

    val out13 = (tableWord(tb, 13, ubfx(w15, 16, 8)) xor
        rk2 xor
        tableWord(tb, 2, ubfx(w11, 8, 8))) xor
        tableWord(tb, 8, w13 ushr 24) xor
        tableWord(tb, 7, w14)

    val out15 = (tableWord(tb, 11, w13) xor
        tableWord(tb, 1, ubfx(w11, 16, 8)) xor
        rk3) xor
        (tableWord(tb, 12, w15 ushr 24) xor
            tableWord(tb, 6, ubfx(w14, 8, 8)))

    return W4(out16, out14, out13, out15)
}
