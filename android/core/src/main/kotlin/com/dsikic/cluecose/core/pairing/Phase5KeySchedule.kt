package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.tables.RuntimeTable
import com.dsikic.cluecose.core.tables.TablesSource

/**
 * Phase 5 wire-cipher key schedule — 1:1 port of LibreCRKit
 * Crypto/Phase5KeySchedule.swift (clean-room port of lib+0x5de1e4).
 *
 * Input is the 66-byte stage-1 source (3 bits per byte); output is the raw
 * 16-byte Phase 5 key consumed by the data-plane cipher. Reads two vendored
 * tables (sbox19 + phase5_keysched_region) through the injectable
 * [TablesSource].
 */
class Phase5KeySchedule(private val tablesSource: TablesSource) {

    private class ScheduleTables(val sboxMaster: ByteArray, val region274000: ByteArray)

    private val tables: ScheduleTables by lazy {
        val sbox = RuntimeTable.SBOX19.load(tablesSource)
        val region = RuntimeTable.PHASE5_KEYSCHED_REGION.load(tablesSource)
        if (sbox.size != 0x80000) throw Phase5KeyScheduleError.InvalidTableSize(RuntimeTable.SBOX19.fileName, sbox.size)
        if (region.size != REGION_LENGTH) throw Phase5KeyScheduleError.InvalidTableSize(RuntimeTable.PHASE5_KEYSCHED_REGION.fileName, region.size)
        ScheduleTables(sbox, region)
    }

    /** Derive the 16-byte raw Phase 5 key from a 66-byte 3-bit-per-byte source. */
    fun deriveRawKey(input66: ByteArray): ByteArray {
        if (input66.size != STAGE1_COUNT) throw Phase5KeyScheduleError.InvalidInputLength(input66.size)
        val sbox = tables.sboxMaster
        val region = tables.region274000
        // prog = region slice at progDataBase - regionBase (0x624), length 0x1000
        val prog = region.slice(0x624, 0x624 + 0x1000)

        val inBuf = ByteArray(0x90)
        val stage1Prog = prog.slice(STAGE1_TABLE_OFFSET, STAGE1_TABLE_OFFSET + STAGE1_COUNT)
        val stage1 = vmA(sbox, input66, input66, stage1Prog, STAGE1_COUNT)
        stage1.copyInto(inBuf, 0)

        val stack = ByteArray(0x180)
        for (call in PHASE1_CALLS) {
            val totalRead = when (call.op) {
                Op.VM_D -> 6
                Op.VM_B -> call.countA + call.countB
                Op.VM_A -> call.countB
            }
            val src1 = source(call.src1, inBuf, stack, totalRead)
            val src2 = source(call.src2, inBuf, stack, totalRead)
            val progSeg = prog.slice(call.progOffset, call.progOffset + totalRead)
            val out = when (call.op) {
                Op.VM_A -> vmA(sbox, src1, src2, progSeg, call.countB)
                Op.VM_B -> vmB(sbox, src1, src2, progSeg, call.countA, call.countB, 0)
                Op.VM_D -> vmD(sbox, src1, src2, progSeg)
            }
            out.copyInto(stack, call.dst)
        }

        val subOrcBytes = ByteArray(16)
        for (iter in 0 until 16) {
            val chunkInOff = PHASE2_CHUNK_OFFSETS_SP[iter]
            for (round in 0 until 4) {
                val src1Off = if (round == 0) chunkInOff else PHASE2_SCRATCH_SRCS[round]
                val src1 = stack.slice(src1Off, src1Off + 6)
                val src2 = regionSlice(region, PHASE2_SRC2_LIB_OFFSETS[iter][round], 6)
                val progSeg = prog.slice(PHASE2_PROG_OFFSETS[iter][round], PHASE2_PROG_OFFSETS[iter][round] + 6)
                val out = vmD(sbox, src1, src2, progSeg)
                out.copyInto(stack, PHASE2_SCRATCH_DSTS[round])
            }

            val compressed = subOrc(
                stack.slice(SUB_ORC_ARG_SP, SUB_ORC_ARG_SP + 6),
                sbox,
                prog,
                region,
            )
            subOrcBytes[iter] = compressed.toByte()
            stack[SUB_ORC_ACCUM_SP] = compressed.toByte()
        }

        val aesKey = ByteArray(16)
        for (iter in 0 until 16) {
            val tableOffset = if (iter == 0) 0 else iter * 0x100 + 0x40
            val regionOff = (POST_LOOP_BASE - REGION_BASE) + tableOffset + (subOrcBytes[iter].toInt() and 0xFF)
            aesKey[KEY_POS_BY_ITER[iter]] = read(region, regionOff, RuntimeTable.PHASE5_KEYSCHED_REGION.fileName)
        }
        return aesKey
    }

    // MARK: - VM primitives

    private fun vmA(sbox: ByteArray, src1: ByteArray, src2: ByteArray, prog: ByteArray, length: Int): ByteArray {
        var state = 0
        val out = ByteArray(length)
        for (i in 0 until length) {
            state = vmOpNoWrite(state, src1[i].toInt() and 0xFF, src2[i].toInt() and 0xFF, prog[i].toInt() and 0xFF, sbox)
            out[i] = (state and 7).toByte()
        }
        return out
    }

    private fun vmB(
        sbox: ByteArray,
        src1: ByteArray,
        src2: ByteArray,
        prog: ByteArray,
        lengthA: Int,
        lengthB: Int,
        lengthC: Int = 0,
    ): ByteArray {
        var state = 0
        for (i in 0 until lengthA) {
            state = vmOpNoWrite(state, src1[i].toInt() and 0xFF, src2[i].toInt() and 0xFF, prog[i].toInt() and 0xFF, sbox)
        }
        val out = ByteArray(lengthB + lengthC)
        for (i in 0 until lengthB) {
            val idx = lengthA + i
            state = vmOpNoWrite(state, src1[idx].toInt() and 0xFF, src2[idx].toInt() and 0xFF, prog[idx].toInt() and 0xFF, sbox)
            out[i] = (state and 7).toByte()
        }
        for (i in 0 until lengthC) {
            val idx = lengthA + lengthB + i
            val sboxIdx = ((state and 0xF8) or (prog[idx].toInt() shl 11)) and 0x7FFFF
            state = sbox[sboxIdx].toInt()
            out[lengthB + i] = (state and 7).toByte()
        }
        return out
    }

    private fun vmD(sbox: ByteArray, src1: ByteArray, src2: ByteArray, prog: ByteArray): ByteArray =
        vmA(sbox, src1, src2, prog, 6)

    private fun vmOpNoWrite(state: Int, src1: Int, src2: Int, prog: Int, sbox: ByteArray): Int {
        val idx = (((state and 0xF8) xor src1) or ((src2 shl 8) xor (prog shl 11))) and 0x7FFFF
        return sbox[idx].toInt()
    }

    private fun subOrc(
        arg6B: ByteArray,
        sboxMaster: ByteArray,
        progData: ByteArray,
        region: ByteArray,
    ): Int {
        val scratch = transform6B(arg6B, sboxMaster)
        val vma1Prog = progData.slice(SUB_ORC_VMA1_OFFSET, SUB_ORC_VMA1_OFFSET + 4)
        val vma2Prog = progData.slice(SUB_ORC_VMA2_OFFSET, SUB_ORC_VMA2_OFFSET + 4)

        val keep = vmA(sboxMaster, scratch.slice(0, 4), scratch.slice(0, 4), vma1Prog, 4)

        val scratchMut = scratch.copyOf()
        val vmbProg = progData.slice(SUB_ORC_VMB_OFFSET, SUB_ORC_VMB_OFFSET + SUB_ORC_VMB_LENGTH_A + SUB_ORC_VMB_LENGTH_B + SUB_ORC_VMB_LENGTH_C)
        val vmbOut = vmB(sboxMaster, scratchMut, scratchMut, vmbProg, SUB_ORC_VMB_LENGTH_A, SUB_ORC_VMB_LENGTH_B, SUB_ORC_VMB_LENGTH_C)
        vmbOut.copyInto(scratchMut, 0)

        val step5 = vmA(sboxMaster, keep, keep, vma2Prog, 4)
        val idx5 = (step5[2].toInt() and 0xFF) xor ((step5[3].toInt() and 0xFF) shl 3)
        val lookup5 = read(region, PHASE5_SBOX_OFF + idx5, RuntimeTable.PHASE5_KEYSCHED_REGION.fileName).toInt() and 0xFF

        val postVMB = scratchMut.slice(0, 4)
        val keep2 = vmA(sboxMaster, postVMB, postVMB, vma1Prog, 4)
        val step7 = vmA(sboxMaster, keep2, keep2, vma2Prog, 4)
        val idx7 = (step7[2].toInt() and 0xFF) xor ((step7[3].toInt() and 0xFF) shl 3)
        val lookup7 = read(region, PHASE5_SBOX_OFF + idx7, RuntimeTable.PHASE5_KEYSCHED_REGION.fileName).toInt() and 0xFF

        return (lookup7 and 0xF0) or (lookup5 and 0x0F)
    }

    private fun transform6B(arg6B: ByteArray, sboxMaster: ByteArray): ByteArray {
        val out = ByteArray(6)
        var x = scramblerHalfword(sboxMaster, (arg6B[0].toInt() and 0xFF) + 0x2000)
        x = scramblerHalfword(sboxMaster, ((x and 0xFF8) xor (arg6B[1].toInt() and 0xFF)) or 0x2000)
        x = scramblerHalfword(sboxMaster, ((x and 0xFF8) xor (arg6B[2].toInt() and 0xFF)) or 0x2000)
        x = scramblerHalfword(sboxMaster, ((x and 0xFF8) xor (arg6B[3].toInt() and 0xFF)) or 0x4000)
        out[0] = (x and 7).toByte()

        x = scramblerHalfword(sboxMaster, (((x and 0xFF8) xor (arg6B[4].toInt() and 0xFF)) or 0x21000) + 0xD000)
        out[1] = (x and 7).toByte()
        x = scramblerHalfword(sboxMaster, ((x and 0xFF8) xor (arg6B[5].toInt() and 0xFF)) or 0x21000)
        out[2] = (x and 7).toByte()

        x = scramblerHalfword(sboxMaster, (x xor (arg6B[2].toInt() and 0xFF)) xor 0x6000)
        out[3] = (x and 7).toByte()
        x = scramblerHalfword(sboxMaster, (x xor (arg6B[3].toInt() and 0xFF)) xor 0x2000)
        out[4] = (x and 7).toByte()
        x = scramblerHalfword(sboxMaster, (x xor (arg6B[4].toInt() and 0xFF)) xor 0x4000)
        out[5] = (x and 7).toByte()
        return out
    }

    private fun scramblerHalfword(sboxMaster: ByteArray, index: Int): Int {
        val byteOffset = SCRAMBLER_OFFSET_IN_SBOX_MASTER + index * 2
        val lo = read(sboxMaster, byteOffset, RuntimeTable.SBOX19.fileName).toInt() and 0xFF
        val hi = read(sboxMaster, byteOffset + 1, RuntimeTable.SBOX19.fileName).toInt() and 0xFF
        return lo or (hi shl 8)
    }

    // MARK: - Byte helpers

    private fun source(src: Source, inBuf: ByteArray, stack: ByteArray, count: Int): ByteArray = when (src) {
        Source.Input -> inBuf.slice(0, count)
        is Source.Stack -> stack.slice(src.offset, src.offset + count)
    }

    private fun regionSlice(region: ByteArray, libOffset: Int, count: Int): ByteArray {
        val offset = libOffset - REGION_BASE
        if (offset < 0 || offset + count > region.size) {
            throw Phase5KeyScheduleError.TableReadOutOfBounds(RuntimeTable.PHASE5_KEYSCHED_REGION.fileName, offset)
        }
        return region.slice(offset, offset + count)
    }

    private fun read(bytes: ByteArray, offset: Int, name: String): Byte {
        if (offset < 0 || offset >= bytes.size) {
            throw Phase5KeyScheduleError.TableReadOutOfBounds(name, offset)
        }
        return bytes[offset]
    }

    private fun ByteArray.slice(offset: Int, endExclusive: Int): ByteArray = copyOfRange(offset, endExclusive)

    private sealed class Source {
        object Input : Source()
        data class Stack(val offset: Int) : Source()
    }

    private enum class Op { VM_A, VM_B, VM_D }

    private data class Phase1Call(
        val op: Op,
        val progOffset: Int,
        val countA: Int,
        val countB: Int,
        val src1: Source,
        val src2: Source,
        val dst: Int,
    )

    private companion object {
        const val STAGE1_COUNT = 66
        const val REGION_BASE = 0x274000
        const val REGION_LENGTH = 0x2000
        const val STAGE1_TABLE_OFFSET = 0x124
        const val PHASE5_SBOX_OFF = 0x9D3
        const val POST_LOOP_BASE = 0x274A13
        const val SCRAMBLER_OFFSET_IN_SBOX_MASTER = 0x20001

        const val SUB_ORC_VMA1_OFFSET = 465
        const val SUB_ORC_VMB_OFFSET = 194
        const val SUB_ORC_VMB_LENGTH_A = 2
        const val SUB_ORC_VMB_LENGTH_B = 4
        const val SUB_ORC_VMB_LENGTH_C = 2
        const val SUB_ORC_VMA2_OFFSET = 833

        val PHASE2_SCRATCH_DSTS = intArrayOf(0xB4, 0x92, 0xE8, 0xD6)
        val PHASE2_SCRATCH_SRCS = intArrayOf(0, 0xB4, 0x92, 0xE8)
        const val SUB_ORC_ARG_SP = 0xD6
        const val SUB_ORC_ACCUM_SP = 0x104

        val PHASE2_CHUNK_OFFSETS_SP = intArrayOf(
            0x8C, 0x86, 0x80, 0x7A, 0x74, 0x6E, 0x68, 0x62,
            0x5C, 0x56, 0x50, 0x4A, 0x44, 0x3E, 0x38, 0x32,
        )

        val PHASE2_PROG_OFFSETS = arrayOf(
            intArrayOf(611, 364, 805, 382),
            intArrayOf(581, 208, 569, 160),
            intArrayOf(124, 136, 765, 737),
            intArrayOf(106, 112, 370, 18),
            intArrayOf(731, 523, 425, 843),
            intArrayOf(553, 895, 675, 715),
            intArrayOf(487, 24, 398, 879),
            intArrayOf(12, 743, 821, 30),
            intArrayOf(669, 657, 873, 118),
            intArrayOf(547, 517, 587, 188),
            intArrayOf(166, 0, 182, 286),
            intArrayOf(837, 36, 651, 867),
            intArrayOf(404, 202, 663, 376),
            intArrayOf(605, 130, 575, 681),
            intArrayOf(6, 599, 749, 499),
            intArrayOf(645, 493, 358, 541),
        )

        val PHASE2_SRC2_LIB_OFFSETS = arrayOf(
            intArrayOf(0x2749BB, 0x2749C1, 0x2749C7, 0x2749CD),
            intArrayOf(0x275A53, 0x275A59, 0x275A5F, 0x275A65),
            intArrayOf(0x275A6B, 0x275A71, 0x275A77, 0x275A7D),
            intArrayOf(0x275A83, 0x275A89, 0x275A8F, 0x275A95),
            intArrayOf(0x275A9B, 0x275AA1, 0x275AA7, 0x275AAD),
            intArrayOf(0x275AB3, 0x275AB9, 0x275ABF, 0x275AC5),
            intArrayOf(0x275ACB, 0x275AD1, 0x275AD7, 0x275ADD),
            intArrayOf(0x275AE3, 0x275AE9, 0x275AEF, 0x275AF5),
            intArrayOf(0x275AFB, 0x275B01, 0x275B07, 0x275B0D),
            intArrayOf(0x275B13, 0x275B19, 0x275B1F, 0x275B25),
            intArrayOf(0x275B2B, 0x275B31, 0x275B37, 0x275B3D),
            intArrayOf(0x275B43, 0x275B49, 0x275B4F, 0x275B55),
            intArrayOf(0x275B5B, 0x275B61, 0x275B67, 0x275B6D),
            intArrayOf(0x275B73, 0x275B79, 0x275B7F, 0x275B85),
            intArrayOf(0x275B8B, 0x275B91, 0x275B97, 0x275B9D),
            intArrayOf(0x275BA3, 0x275BA9, 0x275BAF, 0x275BB5),
        )

        val KEY_POS_BY_ITER = intArrayOf(
            3, 2, 1, 0,
            7, 6, 5, 4,
            11, 10, 9, 8,
            15, 14, 13, 12,
        )

        val PHASE1_CALLS: List<Phase1Call> = listOf(
            Phase1Call(Op.VM_A, 0x1AF, 0, 34, Source.Input, Source.Input, 0xB4),
            Phase1Call(Op.VM_B, 0x0D6, 32, 34, Source.Input, Source.Input, 0x92),
            Phase1Call(Op.VM_A, 0x269, 0, 18, stk(0xB4), stk(0xB4), 0xE8),
            Phase1Call(Op.VM_B, 0x303, 16, 18, stk(0xB4), stk(0xB4), 0xD6),
            Phase1Call(Op.VM_A, 0x02A, 0, 10, stk(0xE8), stk(0xE8), 0x104),
            Phase1Call(Op.VM_B, 0x1D5, 8, 10, stk(0xE8), stk(0xE8), 0xFA),
            Phase1Call(Op.VM_D, 0x211, 0, 6, stk(0x104), stk(0x104), 0x32),
            Phase1Call(Op.VM_B, 0x32B, 4, 6, stk(0x104), stk(0x104), 0x38),
            Phase1Call(Op.VM_D, 0x1F9, 0, 6, stk(0xFA), stk(0xFA), 0x3E),
            Phase1Call(Op.VM_B, 0x2D1, 4, 6, stk(0xFA), stk(0xFA), 0x44),
            Phase1Call(Op.VM_A, 0x0AC, 0, 10, stk(0xD6), stk(0xD6), 0x104),
            Phase1Call(Op.VM_B, 0x2B9, 8, 10, stk(0xD6), stk(0xD6), 0xFA),
            Phase1Call(Op.VM_D, 0x1A3, 0, 6, stk(0x104), stk(0x104), 0x4A),
            Phase1Call(Op.VM_B, 0x034, 4, 6, stk(0x104), stk(0x104), 0x50),
            Phase1Call(Op.VM_D, 0x217, 0, 6, stk(0xFA), stk(0xFA), 0x56),
            Phase1Call(Op.VM_B, 0x27B, 4, 6, stk(0xFA), stk(0xFA), 0x5C),
            Phase1Call(Op.VM_A, 0x08E, 0, 18, stk(0x92), stk(0x92), 0xE8),
            Phase1Call(Op.VM_B, 0x03E, 16, 18, stk(0x92), stk(0x92), 0xD6),
            Phase1Call(Op.VM_A, 0x22F, 0, 10, stk(0xE8), stk(0xE8), 0x104),
            Phase1Call(Op.VM_B, 0x351, 8, 10, stk(0xE8), stk(0xE8), 0xFA),
            Phase1Call(Op.VM_D, 0x1FF, 0, 6, stk(0x104), stk(0x104), 0x62),
            Phase1Call(Op.VM_B, 0x375, 4, 6, stk(0x104), stk(0x104), 0x68),
            Phase1Call(Op.VM_D, 0x33B, 0, 6, stk(0xFA), stk(0xFA), 0x6E),
            Phase1Call(Op.VM_B, 0x2AF, 4, 6, stk(0xFA), stk(0xFA), 0x74),
            Phase1Call(Op.VM_A, 0x184, 0, 10, stk(0xD6), stk(0xD6), 0x104),
            Phase1Call(Op.VM_B, 0x385, 8, 10, stk(0xD6), stk(0xD6), 0xFA),
            Phase1Call(Op.VM_D, 0x118, 0, 6, stk(0x104), stk(0x104), 0x7A),
            Phase1Call(Op.VM_B, 0x2F3, 4, 6, stk(0x104), stk(0x104), 0x80),
            Phase1Call(Op.VM_D, 0x251, 0, 6, stk(0xFA), stk(0xFA), 0x86),
            Phase1Call(Op.VM_B, 0x060, 4, 6, stk(0xFA), stk(0xFA), 0x8C),
        )

        private fun stk(offset: Int) = Source.Stack(offset)
    }
}

sealed class Phase5KeyScheduleError : Exception("phase 5 key schedule failure") {
    data class InvalidInputLength(val count: Int) : Phase5KeyScheduleError()
    data class InvalidTableSize(val file: String, val actual: Int) : Phase5KeyScheduleError()
    data class TableReadOutOfBounds(val file: String, val offset: Int) : Phase5KeyScheduleError()
}
