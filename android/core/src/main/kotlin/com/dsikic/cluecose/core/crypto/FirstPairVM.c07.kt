// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.SourceTables
import com.dsikic.cluecose.core.tables.RuntimeTable

internal fun reducer67ea28Word(src: ByteArray, tables: SourceTables): ByteArray {
    val tmp18 = vm67cc18(0x120000048e2UL, src, src, tables)
    var state18 = vm67d524(0xc00f000c0578eUL, tmp18, tables)

    var packed: UInt = 0U
    var outShift: UInt = 0U
    var bitBudget = 0x20
    for (roundIndex in 0 until 8) {
        val scratch4 = vm67cc18(0x40000033d7UL, state18, state18, tables)
        if (bitBudget >= 5) {
            state18 = vm67cecc(0x8010000805f94UL, state18, state18, tables)
        }

        val tmp4 = vm67cc18(0x4000004513UL, scratch4, scratch4, tables)
        val tableIndex = (tmp4[2].toInt() and 0xff) xor ((tmp4[3].toInt() and 0xff) shl 3)
        if (tableIndex >= tables.reducer67ea28Nibble.size) {
            throw FirstPairSourceSliceError.tableReadOutOfBounds(
                RuntimeTable.FIRST_PAIR_REDUCER_67EA28_NIBBLE.fileName,
                tableIndex
            )
        }
        val tableByte = tables.reducer67ea28Nibble[tableIndex]
        val nibble = if (roundIndex and 1 == 0) (tableByte.toInt() and 0x0f).toUInt() else ((tableByte.toInt() and 0xff) shr 4).toUInt()

        val mask: UInt
        if (bitBudget >= 4) {
            mask = UInt.MAX_VALUE
            bitBudget -= 4
        } else {
            mask = if (bitBudget == 0) 0U else ((1U shl bitBudget) - 1U)
            bitBudget = 0
        }

        packed = packed or ((nibble and mask) shl outShift.toInt())
        outShift += 4U
    }

    return byteArrayOf(
        (packed and 0xffU).toByte(),
        ((packed shr 8) and 0xffU).toByte(),
        ((packed shr 16) and 0xffU).toByte(),
        ((packed shr 24) and 0xffU).toByte(),
    )
}

internal fun update67eb94Blocks(wordsLE: List<ByteArray>, tables: SourceTables): ByteArray {
    if (wordsLE.size != 8) {
        throw FirstPairSourceSliceError.sourceTooShort("67eb94 words", wordsLE.size, 8)
    }

    var blocks = ByteArray(0)
    for (index in 0 until wordsLE.size) {
        val word = wordsLE[index]
        val magic = eb94UpdateMagics[index]
        val expanded = expand67ed24(word, tables)
        blocks = blocks.concat(vm67cc18(magic, expanded, expanded, tables))
    }
    return blocks
}

internal fun constructor67076cBlocks(rawDescriptorBlocks: ByteArray, magic: ULong): ByteArray {
    if (rawDescriptorBlocks.size % block66Size != 0) {
        throw FirstPairSourceSliceError.invalidEncodedBlockLength(rawDescriptorBlocks.size)
    }
    val tables = FirstPairConfig.tables
    val bytes = rawDescriptorBlocks
    var out = ByteArray(0)
    for (start in 0 until bytes.size step block66Size) {
        val block = bytes.copyOfRange(start, start + block66Size)
        out = out.concat(vm67076c(magic, block, block, tables))
    }
    return out
}

internal fun expand67ed24(wordLE: ByteArray, tables: SourceTables): ByteArray {
    require(wordLE, 4, "67ed24 word")
    val sideA = expandWordTrits(wordLE, expand67ed24AOffset, tables)
    val sideB = expandWordTrits(wordLE, expand67ed24BOffset, tables)

    val foldedA = fold24To18(0x600000133bUL, 0x6000003479UL, sideA, tables)
    val wideA = vm67cecc(0x40012000000028UL, foldedA, foldedA, tables)

    val foldedB = fold24To18(0x6000004936UL, 0x6000000000UL, sideB, tables)
    val wideB = vm67cecc(0x40012000004683UL, foldedB, foldedB, tables)

    val mixed = vm67cc18(0x22000004d74UL, wideA, wideB, tables)
    return vm67d524(0xc01f000c05d34UL, mixed, tables)
}

internal fun expandWordTrits(wordLE: ByteArray, tableOffset: Int, tables: SourceTables): ByteArray {
    require(wordLE, 4, "67ed24 word")
    var out = ByteArray(0)
    for (i in 0 until 4) {
        val byte = wordLE[i]
        val index = tableOffset + (byte.toInt() and 0xff) * 3
        val packed = checkedSlice(
            tables.finalizerTables,
            index,
            3,
            RuntimeTable.FIRST_PAIR_FINALIZER_TABLES.fileName
        )
        for (value in packed) {
            out = out.concat(byteArrayOf((value.toInt() and 7).toByte()))
            out = out.concat(byteArrayOf(((value.toInt() and 0xff) shr 3).toByte()))
        }
    }
    return out
}

internal fun fold24To18(firstMagic: ULong, tailMagic: ULong, src24: ByteArray, tables: SourceTables): ByteArray {
    require(src24, 24, "67ed24 fold source")
    var out = vm67cc18(firstMagic, src24, src24, tables)
    var tail = ByteArray(0)
    for (offset in 6..18 step 6) {
        val src = src24.copyOfRange(offset, src24.size)
        tail = tail.concat(vm67cc18(tailMagic, src, src, tables))
    }
    out = out.concat(tail.copyOfRange(2, 6))
    out = out.concat(tail.copyOfRange(8, 12))
    out = out.concat(tail.copyOfRange(14, 18))
    return out
}

internal fun expandRawByte67d630(byte: Byte, tableOffset: Int, tables: SourceTables): ByteArray {
    val index = tableOffset + (byte.toInt() and 0xff) * 3
    val packed = checkedSlice(
        tables.seedTables679f48,
        index,
        3,
        RuntimeTable.FIRST_PAIR_679F48_SEED_TABLES.fileName
    )
    var out = ByteArray(0)
    for (value in packed) {
        out = out.concat(byteArrayOf((value.toInt() and 7).toByte()))
        out = out.concat(byteArrayOf(((value.toInt() and 0xff) shr 3).toByte()))
    }
    return out
}

internal fun fold96To66(firstMagic: ULong, tailMagic: ULong, src96: ByteArray, tables: SourceTables): ByteArray {
    require(src96, 0x60, "67d630 fold source")
    val padded = src96.concat(ByteArray(0x60))
    val first = vm67cc18(
        firstMagic,
        padded.copyOfRange(0, 0x60),
        padded.copyOfRange(0, 0x60),
        tables
    )
    var out = first.copyOfRange(0, 6)
    for (offset in 6 until 0x60 step 6) {
        val src = padded.copyOfRange(offset, offset + 0x60)
        val chunk = vm67cc18(tailMagic, src, src, tables)
        out = out.concat(chunk.copyOfRange(2, 6))
    }
    return out
}

internal fun shift67dd7cRemainder(block66: ByteArray, tables: SourceTables): ByteArray {
    require(block66, block66Size, "67dd7c remainder block")
    val shifted = byteArrayOf(0, 0, 0, 3).concat(block66.copyOfRange(0, 0x3e))
    return vm67cc18(0x42000001974UL, shifted, shifted, tables)
}

internal fun packDF80Zeros6Marker(marker: Byte, src: ByteArray): ByteArray {
    require(src, 11, "67df80 pack")
    return ByteArray(6).concat(byteArrayOf(marker)).concat(src.copyOfRange(0, 11))
}

internal fun packDF80Zeros5Marker6(src: ByteArray): ByteArray {
    require(src, 12, "67df80 pack")
    return ByteArray(5).concat(byteArrayOf(6)).concat(src.copyOfRange(0, 12))
}

internal fun packDF80Zeros11Marker5(src: ByteArray): ByteArray {
    require(src, 6, "67df80 pack")
    return ByteArray(11).concat(byteArrayOf(5)).concat(src.copyOfRange(0, 6))
}

internal fun packDF80Zeros12Marker3(src: ByteArray): ByteArray {
    require(src, 5, "67df80 pack")
    return ByteArray(12).concat(byteArrayOf(3)).concat(src.copyOfRange(0, 5))
}

internal fun packDF80Zeros8Zero6(src: ByteArray): ByteArray {
    require(src, 8, "67df80 pack")
    return ByteArray(9).concat(byteArrayOf(6)).concat(src.copyOfRange(0, 8))
}

internal fun packDF80Zeros2Marker6(src: ByteArray): ByteArray {
    require(src, 15, "67df80 pack")
    return byteArrayOf(0, 0, 6).concat(src.copyOfRange(0, 15))
}

internal fun packDF80Zeros14Marker1(src: ByteArray): ByteArray {
    require(src, 3, "67df80 pack")
    return ByteArray(14).concat(byteArrayOf(1)).concat(src.copyOfRange(0, 3))
}

internal fun packDF80Zeros9(src: ByteArray): ByteArray {
    require(src, 9, "67df80 pack")
    return ByteArray(9).concat(src.copyOfRange(0, 9))
}

internal fun packDF80Zeros4Marker3(src: ByteArray): ByteArray {
    require(src, 13, "67df80 pack")
    return ByteArray(4).concat(byteArrayOf(3)).concat(src.copyOfRange(0, 13))
}

internal fun builder63c278X1Word(word: UInt, index: Int, tables: SourceTables): ULong {
    val w0 = u32Affine63c278(
        word,
        index,
        0x115488,
        0x121908,
        tables
    )
    val w = (w0 * 0x30316c9dU) + 0xe533e221U
    var folded = (w.toULong() * 0x74ddf8a53c239debUL) + 0xc98ef94d2aa6d2f9UL
    folded = fold63c278(folded, 0x301770, 8, tables)
    folded = folded * 0xff444fcf00000000UL
    return (w.toULong() * 0xdda5a8135a0bc9fbUL) + folded + 0x8031c96ed30bf85eUL
}

internal fun builder63c278X0Word(word: UInt, index: Int, tables: SourceTables): ULong {
    val w0 = u32Affine63c278(
        word,
        index,
        0x11a9a8,
        0x122aa8,
        tables
    )
    val w = (w0 * 0x707fe555U) + 0x1d759ee3U
    var folded = (w.toULong() * 0xc7e623dc4156435dUL) + 0xa7268272249650e4UL
    folded = fold63c278(folded, 0x3017f0, 8, tables)
    folded = folded * 0xd70f3ef300000000UL
    return (w.toULong() * 0x1defa278095a88b9UL) + folded + 0xc8066dafe659e3ddUL
}

internal fun builder63c278X2Word(word: UInt, index: Int, tables: SourceTables): ULong {
    val w0 = u32Affine63c278(
        word,
        index,
        0x11f588,
        0x123488,
        tables
    )
    val w = (w0 * 0xe41d161fU) + 0xb12fcee1U
    var folded = (w.toULong() * 0xf3402af2c5c78103UL) + 0x81b5a02882be6230UL
    folded = fold63c278(folded, 0x301a70, 8, tables)
    folded = folded * 0xc69af5ab00000000UL
    return (w.toULong() * 0x057da4120776f3ffUL) + folded + 0x7d7d6bb0e7cd07d3UL
}

internal fun builder63c278X0BWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w0 = u32Affine63c278(
        word,
        index,
        0x118f28,
        0x118548,
        tables
    )
    val w = (w0 * 0x4dce977fU) + 0x7275db64U
    var folded = (w.toULong() * 0x3125dbf4f55c0c6dUL) + 0x1167036e8591663cUL
    folded = fold63c278(folded, 0x301af0, 8, tables)
    folded = folded * 0xee1902df00000000UL
    return (w.toULong() * 0x41108caa0013530dUL) + folded + 0x2ce8cc914f903207UL
}

internal fun builder63c278MixSeed(carry: ULong, scalarMul: ULong, scalarAdd: ULong, tables: SourceTables): FpPair<ULong, ULong> {
    val mixed = (carry * scalarMul) + scalarAdd
    var folded = (mixed * 0xe56ee0d2dabe3103UL) + 0xe1a57f65c01b39acUL
    folded = fold63c278(folded, 0x301870, 7, tables)
    folded = folded * 0x43cf3bc9b0000000UL
    val seed = (mixed * 0x47b2ca50a9011f2fUL) + folded + 0xa9ccf36f06c69525UL
    return FpPair(
        (seed * 0x707f1c911d72472dUL) + 0x20d7bce79675ce2eUL,
        (seed * 0x62d17dd555b3e7b5UL) + 0xa95e929c3eca7e5eUL
    )
}

internal fun builder63c278NextCarry(updatedFirst: ULong, updatedSecond: ULong, tables: SourceTables): ULong {
    var folded = (updatedFirst * 0x500a38540d22b25bUL) + 0xae9b83bb74900f1eUL
    folded = fold63c278(folded, 0x3018f0, 7, tables)
    val carryMix = (folded * 0x6e12b4b0721da33bUL) + 0x15fb45ff71081e4eUL
    var folded2 = (carryMix * 0xb926d0a2f88df903UL) + 0x0931eca912f88a4c7UL
    folded2 = fold63c278(folded2, 0x301970, 9, tables)
    folded2 = folded2 * 0x30cbc3f000000000UL
    val mixed2 = (carryMix * 0x025241c2cd0d8443UL) + folded2
    return (mixed2 * 0x8074fb50d5400883UL) + updatedSecond + 0x9a2a45734b3e5fb0UL
}

internal fun builder63c278Mix2Seed(carry: ULong, scalarMul: ULong, scalarAdd: ULong, tables: SourceTables): FpPair<ULong, ULong> {
    val mixed = (carry * scalarMul) + scalarAdd
    var folded = (mixed * 0x126e65dcb0b83de1UL) + 0x5454202b530d9481UL
    folded = fold63c278(folded, 0x301b70, 7, tables)
    folded = folded * 0x3d2ffccf90000000UL
    val seed = (mixed * 0x4c89449a165d8427UL) + folded + 0x654ba76b767a427cUL
    return FpPair(
        (seed * 0x564d78f55b5eefabUL) + 0xf24aa781d14548f5UL,
        (seed * 0xeb7cfc7c768d163cUL) + 0x09afd4171a0c7a44UL
    )
}

internal fun builder63c278NextCarry2(updatedFirst: ULong, updatedSecond: ULong, tables: SourceTables): ULong {
    var folded = (updatedFirst * 0xbca4dd7019310b05UL) + 0x088f442397943c2aUL
    folded = fold63c278(folded, 0x301bf0, 7, tables)
    val carryMix = (folded * 0x727c48215454885bUL) + 0xcaf590adfa7e603bUL
    var folded2 = (carryMix * 0xfb3565409b501139UL) + 0x74b39dd74e3ac2edUL
    folded2 = fold63c278(folded2, 0x301c70, 9, tables)
    folded2 = folded2 * 0x0081c49000000000UL
    val mixed2 = (carryMix * 0xf4d598a4fa80dabfUL) + folded2
    return (mixed2 * 0x0d5f48e79ddef1c9UL) + updatedSecond + 0x9506d95873fe6ec8UL
}

internal fun builder63c278AccumAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w0 = u32Affine63c278(
        word,
        index,
        0x11a9c8,
        0x120f28,
        tables
    )
    val w = (w0 * 0x2545ee53U) + 0xf74fe193U
    var folded = (w.toULong() * 0x69289ee9a98801f5UL) + 0x89bfbb0b1b21e854UL
    folded = fold63c278(folded, 0x301d70, 8, tables)
    return (
        (w.toULong() * 0x12f7e0136d4dad87UL)
        + (folded * 0x9917c7f500000000UL)
        + 0xf80d0f670554b0a4UL
    )
}

internal fun builder63c278AccumBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w0 = u32Affine63c278(
        word,
        index,
        0x11bbe8,
        0x121948,
        tables
    )
    val w = (w0 * 0x7dd1ecf7U) + 0xdc8c9daeU
    var folded = (w.toULong() * 0xf13beb213918d361UL) + 0xa1220647c9883100UL
    folded = fold63c278(folded, 0x301df0, 8, tables)
    return (
        (w.toULong() * 0x952e2be9091d60c7UL)
        + (folded * 0xd89eb2d900000000UL)
        + 0x54e3b2cc004948beUL
    )
}

internal fun builder63c278BridgeX0Word(word: UInt, index: Int, tables: SourceTables): ULong {
    val w0 = u32Affine63c278(
        word,
        index,
        0x11cd88,
        0x112e48,
        tables
    )
    val w = (w0 * 0x1c8f15cfU) + 0x05d1107bU
    var folded = (w.toULong() * 0x19d189b1be9d480bUL) + 0xd2bafb34c1909b26UL
    folded = fold63c278(folded, 0x301e70, 8, tables)
    folded = folded * 0x2ece929d00000000UL
    return (w.toULong() * 0x7529d4f2739a8b41UL) + folded + 0xdb7158ce45fcb750UL
}

internal fun builder63c278BridgeMixSeed(carry: ULong, scalarMul: ULong, scalarAdd: ULong, tables: SourceTables): FpPair<ULong, ULong> {
    val mixed = (carry * scalarMul) + scalarAdd
    var folded = (mixed * 0x34af0af1bbce60ddUL) + 0x61b88589a4883d43UL
    folded = fold63c278(folded, 0x301ef0, 7, tables)
    folded = folded * 0x2da4669430000000UL
    val seed = (mixed * 0x8f5055af84d40129UL) + folded + 0x7bf63147a7179819UL
    return FpPair(
        (seed * 0xdb5bb72dd36c07a9UL) + 0x155c3f0a68fbcfe1UL,
        (seed * 0x0ee832c1be220ab1UL) + 0xcc246f1fe68886a9UL
    )
}

internal fun builder63c278BridgeNextCarry(updatedFirst: ULong, updatedSecond: ULong, tables: SourceTables): ULong {
    var folded = (updatedFirst * 0x060c229ff67c02fbUL) + 0xab8d83e0c2b70611UL
    folded = fold63c278(folded, 0x301f70, 7, tables)
    val carryMix = (folded * 0x6a605d1236fbedd7UL) + 0xfd36e0ea31dbe67cUL
    var folded2 = (carryMix * 0x57a20a77f75734e1UL) + 0x4cc594baeecf3ecaUL
    folded2 = fold63c278(folded2, 0x301ff0, 9, tables)
    folded2 = folded2 * 0x9c0c689000000000UL
    val mixed2 = (carryMix * 0x1e0bc5b08daead97UL) + folded2
    return (mixed2 * 0x93bd22efcdeacdc3UL) + updatedSecond + 0xc175492c1e8124acUL
}

internal fun builder63c278PrebranchSP4F0FoldState(state: UInt, tables: SourceTables): UInt {
    var folded = ((state.toULong() * 0x3dbef531UL) + 0x554aacd3UL).toUInt()
    folded = fold32ByNibbles63c278(folded, 0x3021f8, 7, tables)
    val selected = u32TableWord63c278(0x122ac8 + (folded.toInt() and 7) * 4, tables)
    return selected + (folded shr 3)
}

internal fun builder63c278PrebranchSP4F0State(word: UInt, tables: SourceTables): UInt {
    val half = word shr 1
    val bitTable = u32TableWord63c278(0x126850 + (word.toInt() and 1) * 4, tables)
    return ((half.toULong() * 0x0c949fdbUL) + bitTable.toULong()).toUInt()
}

internal fun builder63c278LoopUpdateSP658Odd(words: List<UInt>, tables: SourceTables): List<UInt> {
    return branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x33f71427U, arg0Add = 0x58500b33U, halfMul = 0x2cb60683U,
            bitTable = 0x126a48, preMul = 0xc4fb260bU, preAdd = 0xf348f6f7U,
            wordMul = 0x2b1d86b1U, wordAdd = 0xfa05b11dU,
            foldTable = 0x302378, selectTable = 0x112648,
            argMulTable = 0x117ba8, argAddTable = 0x11cde8,
            carryMul = 0xa822376dU, valueMul = 0xb8000000U, nextMul = 0x30000000U,
            loopAdd = 0x24e24246U, finalAdd = 0x14e24246U,
            outMulTable = 0x1206a8, outAddTable = 0x11b468
        ),
        tables
    )
}

internal fun builder63c278LoopSP658EvenUsesSuccessPath(sp658Words: List<UInt>, sp6b0Words: List<UInt>, tables: SourceTables): Boolean {
    for (stream in listOf(sp658Words, sp6b0Words)) {
        if (stream.size != builder63c278VectorWords) {
            throw FirstPairSourceSliceError.invalid63c278VectorWordCount(stream.size)
        }
    }
    for (index in builder63c278VectorWords - 1 downTo 0) {
        val tableOffset = (index * 4) and 0x1c
        val check = ((sp658Words[index].toULong() * u32TableWord63c278(0x1154c8 + tableOffset, tables).toULong())
            + sp6b0Words[index].toULong() * u32TableWord63c278(0x1172c8 + tableOffset, tables).toULong()
            + u32TableWord63c278(0x11c468 + tableOffset, tables).toULong()).toUInt()
        if (check != 0x59262fedU) {
            val folded = fold32ByNibbles63c278(check, 0x302478, 7, tables)
            return (folded and 0x0fU) == 0x0dU
        }
    }
    return true
}

internal fun builder63c278LoopUpdateSP658EvenSuccess(sp658Words: List<UInt>, sp6b0Words: List<UInt>, tables: SourceTables): List<UInt> {
    return stageReducer63c278(
        staticWords = staticPatternWords63c278(0x125a70, 0x125940, 0x126ce0, tables),
        streams = listOf(
            StageReducerStream63c278(sp658Words, 0x11b4c8),
            StageReducerStream63c278(sp6b0Words, 0x122b08),
        ),
        params = StageReducer63c278Params(
            carry = 0x6238179aU, carryMul = 0x2cb31cf5U,
            preMul = 0xeaa360b5U, preAdd = 0x7dcae1fdU,
            foldTable = 0x3025b8, reduceMul = 0x354589c9U,
            sideMul = 0xb0000000U, reduceAdd = 0xb0b43182U,
            folded7Mul = 0x6f16c509U, folded8Mul = 0x0e93af70U,
            nextAdd = 0x29fd0d1cU,
            outMulTable = 0x11d508, outAddTable = 0x123508
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP6B0Even(words: List<UInt>, tables: SourceTables): List<UInt> {
    return branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x96928029U, arg0Add = 0x666d5b3aU, halfMul = 0x27acf74dU,
            bitTable = 0x126a18, preMul = 0x84602417U, preAdd = 0xf95f2c9dU,
            wordMul = 0x4bd20bc9U, wordAdd = 0x3a0734caU,
            foldTable = 0x302278, selectTable = 0x11cda8,
            argMulTable = 0x11c428, argAddTable = 0x119748,
            carryMul = 0x96d2d627U, valueMul = 0x98000000U, nextMul = 0x90000000U,
            loopAdd = 0x2f40aa3dU, finalAdd = 0x3f40aa3dU,
            outMulTable = 0x117b68, outAddTable = 0x121968
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP440Odd(words: List<UInt>, tables: SourceTables): List<UInt> {
    return branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x28f734a3U, arg0Add = 0x7fc88b1cU, halfMul = 0xb00c4591U,
            bitTable = 0x126780, preMul = 0x059e578dU, preAdd = 0x33273af5U,
            wordMul = 0x9d8dd89fU, wordAdd = 0xa52d9347U,
            foldTable = 0x3022b8, selectTable = 0x113708,
            argMulTable = 0x11d4e8, argAddTable = 0x11c448,
            carryMul = 0xd470f3b3U, valueMul = 0xe8000000U, nextMul = 0xd0000000U,
            loopAdd = 0xadcd0df0U, finalAdd = 0x65cd0df0U,
            outMulTable = 0x11cdc8, outAddTable = 0x117b88
        ),
        tables
    )
}

internal fun builder63c278LoopSP440EvenSP4F0Words(words: List<UInt>, tables: SourceTables): List<UInt> {
    return branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x5888f7f5U, arg0Add = 0xbbf0e3d5U, halfMul = 0x642326dbU,
            bitTable = 0x126858, preMul = 0x246654c1U, preAdd = 0x2e782dc3U,
            wordMul = 0x1101c103U, wordAdd = 0x183fafb9U,
            foldTable = 0x3022f8, selectTable = 0x1154a8,
            argMulTable = 0x11a9e8, argAddTable = 0x122268,
            carryMul = 0x4d140725U, valueMul = 0xa8000000U, nextMul = 0xb0000000U,
            loopAdd = 0x6fe563d6U, finalAdd = 0x8fe563d6U,
            outMulTable = 0x11dd28, outAddTable = 0x120f48
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP440Even(sp440Words: List<UInt>, sp5a0Words: List<UInt>, tables: SourceTables): List<UInt> {
    val sp4f0 = builder63c278LoopSP440EvenSP4F0Words(sp440Words, tables)
    return stageReducer63c278(
        staticWords = staticPatternWords63c278(0x124f10, 0x125930, 0x126cd8, tables),
        streams = listOf(
            StageReducerStream63c278(sp4f0, 0x114988),
            StageReducerStream63c278(sp5a0Words, 0x11aa08),
        ),
        params = StageReducer63c278Params(
            carry = 0xd3f16146U, carryMul = 0x84bb8555U,
            preMul = 0xd3dd75bbU, preAdd = 0x4bdc02a1U,
            foldTable = 0x302338, reduceMul = 0x26bbb9ffU,
            sideMul = 0x30000000U, reduceAdd = 0xe8f27692U,
            folded7Mul = 0xef9fd9a7U, folded8Mul = 0x06026590U,
            nextAdd = 0x613a18d6U,
            outMulTable = 0x120688, outAddTable = 0x1185a8
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP440(words: List<UInt>, sp5a0Words: List<UInt>, tables: SourceTables): List<UInt> {
    if ((words[0] and 1U) != 0U) {
        return builder63c278LoopUpdateSP440Odd(words, tables)
    }
    return builder63c278LoopUpdateSP440Even(sp440Words = words, sp5a0Words = sp5a0Words, tables = tables)
}

internal fun builder63c278LoopUpdateSP390Even(words: List<UInt>, tables: SourceTables): List<UInt> {
    return branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x5b7c4419U, arg0Add = 0xd8c9cb43U, halfMul = 0xa30de075U,
            bitTable = 0x1267f0, preMul = 0x936efcedU, preAdd = 0x32c3c0a7U,
            wordMul = 0x88e44053U, wordAdd = 0xc35d94bbU,
            foldTable = 0x3023b8, selectTable = 0x1149a8,
            argMulTable = 0x11b488, argAddTable = 0x119768,
            carryMul = 0x14c37dcdU, valueMul = 0x18000000U, nextMul = 0x30000000U,
            loopAdd = 0xe4da180fU, finalAdd = 0xccda180fU,
            outMulTable = 0x1168e8, outAddTable = 0x11a048
        ),
        tables
    )
}

internal fun builder63c278LoopSP390OddSP4F0Words(words: List<UInt>, tables: SourceTables): List<UInt> {
    return branchAffineUpdate63c278(
        words,
        BranchAffine63c278Params(
            arg0Mul = 0x8e39b739U, arg0Add = 0x7c6d92a6U, halfMul = 0xdca8620dU,
            bitTable = 0x126940, preMul = 0x17c4f57fU, preAdd = 0x5b647db4U,
            wordMul = 0xb0fff815U, wordAdd = 0x831b4fffU,
            foldTable = 0x3023f8, selectTable = 0x123e28,
            argMulTable = 0x11f5a8, argAddTable = 0x11bc08,
            carryMul = 0x19f6ba67U, valueMul = 0xb8000000U, nextMul = 0x90000000U,
            loopAdd = 0x85a9b64dU, finalAdd = 0xb5a9b64dU,
            outMulTable = 0x117bc8, outAddTable = 0x116908
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP390Odd(sp390Words: List<UInt>, sp5a0Words: List<UInt>, tables: SourceTables): List<UInt> {
    val sp4f0 = builder63c278LoopSP390OddSP4F0Words(sp390Words, tables)
    return stageReducer63c278(
        staticWords = staticPatternWords63c278(0x126330, 0x126020, 0x126a10, tables),
        streams = listOf(
            StageReducerStream63c278(sp4f0, 0x11ce08),
            StageReducerStream63c278(sp5a0Words, 0x122ae8),
        ),
        params = StageReducer63c278Params(
            carry = 0x1cd91585U, carryMul = 0x1a4cb35bU,
            preMul = 0x5137a735U, preAdd = 0x3e9907e2U,
            foldTable = 0x302438, reduceMul = 0x38fc5a19U,
            sideMul = 0xb0000000U, reduceAdd = 0x0f3d7c5dU,
            folded7Mul = 0xa81b54e7U, folded8Mul = 0x7e4ab190U,
            nextAdd = 0x767d913cU,
            outMulTable = 0x11a068, outAddTable = 0x11b4a8
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP390(words: List<UInt>, sp5a0Words: List<UInt>, tables: SourceTables): List<UInt> {
    if ((words[0] and 1U) != 0U) {
        return builder63c278LoopUpdateSP390Odd(sp390Words = words, sp5a0Words = sp5a0Words, tables = tables)
    }
    return builder63c278LoopUpdateSP390Even(words, tables)
}

internal fun builder63c278LoopUpdateSP390PredicateFalse(sp390Words: List<UInt>, arg0: ByteArray, tables: SourceTables): List<UInt> {
    val arg0Words = arg0Words63c278(arg0)
    return stageReducer63c278(
        staticWords = staticPatternWords63c278(0x126040, 0x126600, 0x126740, tables),
        streams = listOf(
            StageReducerStream63c278(sp390Words, 0x11b4e8),
            StageReducerStream63c278(arg0Words, 0x11bc28),
        ),
        params = StageReducer63c278Params(
            carry = 0x6306d080U, carryMul = 0x90b4d58bU,
            preMul = 0x323154f1U, preAdd = 0x154382eeU,
            foldTable = 0x3025f8, reduceMul = 0x30b9cbfbU,
            sideMul = 0x50000000U, reduceAdd = 0x61849d3dU,
            folded7Mul = 0x1fb5a053U, folded8Mul = 0x04a5fad0U,
            nextAdd = 0x002fe7efU,
            outMulTable = 0x117be8, outAddTable = 0x1172e8
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP390PredicateJoin(sp390Words: List<UInt>, sp440Words: List<UInt>, tables: SourceTables): List<UInt> {
    return stageReducer63c278(
        staticWords = staticPatternWords63c278(0x124d90, 0x125c10, 0x126998, tables),
        streams = listOf(
            StageReducerStream63c278(sp390Words, 0x11dd48),
            StageReducerStream63c278(sp440Words, 0x122288),
        ),
        params = StageReducer63c278Params(
            carry = 0xd554336dU, carryMul = 0x43e12a11U,
            preMul = 0xfd350b93U, preAdd = 0xc2fdb2e2U,
            foldTable = 0x302638, reduceMul = 0x419ce971U,
            sideMul = 0x50000000U, reduceAdd = 0x967ae928U,
            folded7Mul = 0xcbd75debU, folded8Mul = 0x428a2150U,
            nextAdd = 0x27f798a1U,
            outMulTable = 0x118f48, outAddTable = 0x11ec28
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP6B0Failure(sp6b0Words: List<UInt>, sp658Words: List<UInt>, tables: SourceTables): List<UInt> {
    return stageReducer63c278(
        staticWords = staticPatternWords63c278(0x1256b0, 0x125f40, 0x1268d8, tables),
        streams = listOf(
            StageReducerStream63c278(sp6b0Words, 0x1149c8),
            StageReducerStream63c278(sp658Words, 0x11f5c8),
        ),
        params = StageReducer63c278Params(
            carry = 0x2b0fe6d9U, carryMul = 0x346b3047U,
            preMul = 0xe8d292cbU, preAdd = 0x6376b766U,
            foldTable = 0x3024b8, reduceMul = 0xd98513dbU,
            sideMul = 0xf0000000U, reduceAdd = 0x8af9de6dU,
            folded7Mul = 0x74f0e285U, folded8Mul = 0xb0f1d7b0U,
            nextAdd = 0x6ac588e9U,
            outMulTable = 0x11fdc8, outAddTable = 0x115f68
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP440PredicateTrue(sp440Words: List<UInt>, arg0: ByteArray, tables: SourceTables): List<UInt> {
    val arg0Words = arg0Words63c278(arg0)
    return stageReducer63c278(
        staticWords = staticPatternWords63c278(0x124f20, 0x125a00, 0x126c68, tables),
        streams = listOf(
            StageReducerStream63c278(sp440Words, 0x112668),
            StageReducerStream63c278(arg0Words, 0x116928),
        ),
        params = StageReducer63c278Params(
            carry = 0x43bff476U, carryMul = 0x8123c767U,
            preMul = 0xbc55d64fU, preAdd = 0x3db88f4fU,
            foldTable = 0x302538, reduceMul = 0xb7e919a9U,
            sideMul = 0x90000000U, reduceAdd = 0xd881235bU,
            folded7Mul = 0x239e1779U, folded8Mul = 0xc61e8870U,
            nextAdd = 0xa1d86ec1U,
            outMulTable = 0x113728, outAddTable = 0x116948
        ),
        tables
    )
}

internal fun builder63c278LoopUpdateSP440PredicateJoin(sp440Words: List<UInt>, sp390Words: List<UInt>, tables: SourceTables): List<UInt> {
    return stageReducer63c278(
        staticWords = staticPatternWords63c278(0x125440, 0x126030, 0x1267b8, tables),
        streams = listOf(
            StageReducerStream63c278(sp440Words, 0x114068),
            StageReducerStream63c278(sp390Words, 0x11c488),
        ),
        params = StageReducer63c278Params(
            carry = 0xf35277e4U, carryMul = 0x3ae4bb05U,
            preMul = 0x130a19ebU, preAdd = 0x624d99a5U,
            foldTable = 0x302578, reduceMul = 0x53cea2cfU,
            sideMul = 0x30000000U, reduceAdd = 0x81d9862eU,
            folded7Mul = 0x53c4f527U, folded8Mul = 0xc3b0ad90U,
            nextAdd = 0x59871186U,
            outMulTable = 0x120f68, outAddTable = 0x11a088
        ),
        tables
    )
}

internal fun builder63c278Predicate64D55C(sp440Words: List<UInt>, sp390Words: List<UInt>, tables: SourceTables): Int {
    for (stream in listOf(sp440Words, sp390Words)) {
        if (stream.size < builder63c278VectorWords) {
            throw FirstPairSourceSliceError.invalid63c278VectorWordCount(stream.size)
        }
    }
    for (index in builder63c278VectorWords - 1 downTo 0) {
        val tableOffset = (index * 4) and 0x1c
        val check = ((sp440Words[index].toULong() * u32TableWord63c278(0x11fde8 + tableOffset, tables).toULong())
            + sp390Words[index].toULong() * u32TableWord63c278(0x1206c8 + tableOffset, tables).toULong()
            + u32TableWord63c278(0x1185c8 + tableOffset, tables).toULong()).toUInt()
        if (check != 0x213734c0U) {
            val folded = fold32ByNibbles63c278(check, 0x3024f8, 7, tables)
            return if ((folded and 0x0fU) != 0U) 1 else 0
        }
    }
    return 0
}

internal fun builder63c278TerminalSP658Ready(sp658Words: List<UInt>, tables: SourceTables): Boolean {
    if (sp658Words.size < builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp658Words.size)
    }
    if (((sp658Words[0].toULong() * 0x04dc738dUL) and 0xffff_ffffUL).toUInt() != 0x49f4222fU) {
        return false
    }

    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val check = ((foldTableU32Word63c278(0x3021a0 + index * 4, tables).toULong()
            * u32TableWord63c278(0x1234e8 + tableOffset, tables).toULong())
            + sp658Words[index].toULong()
            * u32TableWord63c278(0x120668 + tableOffset, tables).toULong()
            + u32TableWord63c278(0x11e5a8 + tableOffset, tables).toULong()).toUInt()
        if (check != 0x0a2c3abeU) {
            return false
        }
    }
    return true
}

internal fun branchAffineUpdate63c278(words: List<UInt>, params: BranchAffine63c278Params, tables: SourceTables): List<UInt> {
    if (words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(words.size)
    }
    val first = ((words[0].toULong() * params.arg0Mul.toULong()) + params.arg0Add.toULong()).toUInt()
    val firstState = branchState63c278(first, params.halfMul, params.bitTable, tables)
    var carry = branchWord63c278(firstState, params, tables)

    val out = MutableList<UInt>(builder63c278VectorWords) { 0U }
    for (index in 0 until (builder63c278VectorWords - 1)) {
        val nextIndex = index + 1
        val nextTableOffset = (nextIndex and 7) * 4
        val value = u32TableAffine63c278(
            words[nextIndex],
            params.argMulTable + nextTableOffset,
            params.argAddTable + nextTableOffset,
            tables
        )
        val state = branchState63c278(value, params.halfMul, params.bitTable, tables)
        val word = branchWord63c278(state, params, tables)
        carry = ((carry.toULong() * params.carryMul.toULong())
            + value.toULong() * params.valueMul.toULong()
            + word.toULong() * params.nextMul.toULong()).toUInt()
        val storeValue = carry + params.loopAdd
        val tableOffset = (index * 4) and 0x1c
        out[index] = u32TableAffine63c278(
            storeValue,
            params.outMulTable + tableOffset,
            params.outAddTable + tableOffset,
            tables
        )
        carry = word
    }

    val finalStore = ((carry.toULong() * params.carryMul.toULong()) + params.finalAdd.toULong()).toUInt()
    val finalOffset = ((builder63c278VectorWords - 1) * 4) and 0x1c
    out[builder63c278VectorWords - 1] = u32TableAffine63c278(
        finalStore,
        params.outMulTable + finalOffset,
        params.outAddTable + finalOffset,
        tables
    )
    return out
}

internal fun branchWord63c278(state: UInt, params: BranchAffine63c278Params, tables: SourceTables): UInt {
    val preFold = ((state.toULong() * params.preMul.toULong()) + params.preAdd.toULong()).toUInt()
    val select = branchSelectBit63c278(preFold, params.foldTable, params.selectTable, tables)
    return (
        (state.toULong() * params.wordMul.toULong())
        + params.wordAdd.toULong()
        + select.toULong()
    ).toUInt()
}

internal fun branchSelectBit63c278(value: UInt, foldTable: Int, selectTable: Int, tables: SourceTables): UInt {
    val folded = fold32ByNibbles63c278(value, foldTable, 7, tables)
    val selected = u32TableWord63c278(selectTable + (folded.toInt() and 7) * 4, tables) + (folded shr 3)
    return selected shl 31
}

internal fun branchState63c278(word: UInt, halfMul: UInt, bitTable: Int, tables: SourceTables): UInt {
    val bit = u32TableWord63c278(bitTable + (word.toInt() and 1) * 4, tables)
    return (((word shr 1).toULong() * halfMul.toULong()) + bit.toULong()).toUInt()
}

internal fun staticPatternWords63c278(q0: Int, q1: Int, tail: Int, tables: SourceTables): List<UInt> {
    val q0Words = (0 until 4).map { u32TableWord63c278(q0 + it * 4, tables) }
    val q1Words = (0 until 4).map { u32TableWord63c278(q1 + it * 4, tables) }
    val tailWords = (0 until 2).map { u32TableWord63c278(tail + it * 4, tables) }
    return q0Words + q1Words + q0Words + q1Words + q0Words + tailWords
}

internal fun stageReducer63c278(staticWords: List<UInt>, streams: List<StageReducerStream63c278>, params: StageReducer63c278Params, tables: SourceTables): List<UInt> {
    if (staticWords.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(staticWords.size)
    }
    val sp230 = MutableList<UInt>(staticWords.size) { staticWords[it] }
    for (stream in streams) {
        if (stream.words.size != builder63c278VectorWords) {
            throw FirstPairSourceSliceError.invalid63c278VectorWordCount(stream.words.size)
        }
        for (index in stream.words.indices) {
            val word = stream.words[index]
            val tableOffset = (index and 7) * 4
            val mul = u32TableWord63c278(stream.mulTable + tableOffset, tables)
            sp230[index] = (sp230[index].toULong() + word.toULong() * mul.toULong()).toUInt()
        }
    }

    var carry = params.carry
    val out = mutableListOf<UInt>()
    for (index in sp230.indices) {
        val word = sp230[index]
        carry = ((carry.toULong() * params.carryMul.toULong()) + word.toULong()).toUInt()
        var folded7 = ((carry.toULong() * params.preMul.toULong()) + params.preAdd.toULong()).toUInt()
        folded7 = fold32ByNibbles63c278(folded7, params.foldTable, 7, tables)
        val folded8 = foldTableU32Word63c278(params.foldTable + (folded7.toInt() and 0x0f) * 4, tables) + (folded7 shr 4)
        val stage = ((carry.toULong() * params.reduceMul.toULong())
            + folded7.toULong() * params.sideMul.toULong()
            + params.reduceAdd.toULong()).toUInt()
        val nextCarry = ((folded7.toULong() * params.folded7Mul.toULong())
            + folded8.toULong() * params.folded8Mul.toULong()).toUInt()
        val tableOffset = (index * 4) and 0x1c
        out.add(u32TableAffine63c278(stage, params.outMulTable + tableOffset, params.outAddTable + tableOffset, tables))
        carry = nextCarry + params.nextAdd
    }
    return out
}

internal fun builder6421c0WorkspaceParams(scalar: ULong, firstWord: ULong, tables: SourceTables): FpPair<ULong, ULong> {
    val seedA = (scalar * 0x5509a203390f347fUL) + 0x32f1fb0a9d874bf4UL
    val seedB = (scalar * 0x4c2221c00f3005fbUL) + 0x0ff14ba0b2a5c7baUL
    var mixed = (firstWord * seedA) + seedB
    val folded = fold63c278(
        (mixed * 0x473c6a74e974ae65UL) + 0xadebeda263d28433UL,
        builder6421c0WorkspaceFoldTable,
        7,
        tables
    )
    mixed = (
        (mixed * 0xef65aceeafea45e9UL)
        + (folded * 0x5fe62b0cb0000000UL)
        + 0xd7d1a2ac976837c3UL
    )
    return FpPair(
        (mixed * 0x9c52396943c088f7UL) + 0x8983840ba934a2f1UL,
        (mixed * 0x0fd36815b245b0f2UL) + 0x3aa2f36c3a09d43eUL
    )
}

internal fun builder6421c0RewriteSecondWord(first: ULong, second: ULong, tables: SourceTables): ULong {
    val folded = fold63c278(
        (first * 0x12c340b4b411bb8dUL) + 0xab10f2a46110bcebUL,
        builder6421c0RewriteFold1Table,
        7,
        tables
    )
    var mixed = (folded * 0xcfdc2f8d3b1f41e3UL) + 0x317484327c6f968aUL
    val folded2 = fold63c278(
        (mixed * 0xeefa3d8f20f54f35UL) + 0x483345b5f608f667UL,
        builder6421c0RewriteFold2Table,
        9,
        tables
    )
    mixed = (
        (mixed * 0x6b6283330fe2b923UL)
        + (folded2 * 0x6214609000000000UL)
    )
    return (mixed * 0x8d48d385aeebeb5dUL) + second + 0x71783af05ec8119fUL
}

internal fun builder64bd0cWorkspaceParams(scalar: ULong, firstX2Word: ULong, tables: SourceTables): FpPair<ULong, ULong> {
    val seedA = (scalar * 0x9cbd06d772de1901UL) + 0x34e214bca24f560cUL
    val seedB = (scalar * 0xbe4812554b30ebf8UL) + 0xc770490f6d646597UL
    var mixed = (seedA * firstX2Word) + seedB
    var folded = (mixed * 0x213ec1d8d1bc2d9bUL) + 0x3cda12a384db6d3bUL
    folded = fold63c278(folded, builder64bd0cWorkspaceFold1Table, 7, tables)
    mixed = (
        (mixed * 0x91ab7a47a981923bUL)
        + (folded * 0x0ed61381f0000000UL)
        + 0xab62fec0d215095bUL
    )
    return FpPair(
        (mixed * 0x8493b5edc5e368a1UL) + 0x6f8e182c75ab0bb8UL,
        (mixed * 0x2698148ddd26a50eUL) + 0x740f3b32b62a7210UL
    )
}

internal fun builder64bd0cRewriteSecondWord(first: ULong, second: ULong, tables: SourceTables): ULong {
    var folded = (first * 0xe121bd3e759b23f3UL) + 0x8c105c11c96e758bUL
    folded = fold63c278(folded, builder64bd0cRewriteFold1Table, 7, tables)
    var mixed = (folded * 0x4afc5649aee85307UL) + 0xdc13fe8d315ad1a7UL
    var folded2 = (mixed * 0x7c64ef86eb0d2547UL) + 0x95549ebb3b944abeUL
    folded2 = fold63c278(folded2, builder64bd0cRewriteFold2Table, 9, tables)
    mixed = (
        (mixed * 0xd9ef08a678eb7ba3UL)
        + (folded2 * 0x8e62b3b000000000UL)
    )
    return (mixed * 0x3352cbd4c2b4f2efUL) + second + 0x793cd011929995d8UL
}

internal fun builder64bd0cFinalFirstFold(value: ULong, tables: SourceTables): FpTriple<ULong, UInt, ULong> {
    var folded = (value * 0xbfeaa39c4f3a2fdfUL) + 0xb07328e69628c835UL
    var side = (value.toUInt() * 0x48daeaa5U)
    folded = fold63c278(folded, builder64bd0cFinalFoldTable, 7, tables)
    side = side + (folded.toUInt() * 0x50000000U)
    val nextBase = folded
    folded = fold63c278(folded, builder64bd0cFinalFoldTable, 1, tables)
    return FpTriple(nextBase, side + 0xdfea4892U, folded)
}

internal fun builder64c524WorkspaceParams(scalar: ULong, firstX2Word: ULong, tables: SourceTables): FpPair<ULong, ULong> {
    val seedA = (scalar * 0x37225d56e2d37ae5UL) + 0x3d01a097518f54bcUL
    val seedB = (scalar * 0xd03e88ab453ae68bUL) + 0x5ca5b123c7ddda97UL
    var mixed = (seedA * firstX2Word) + seedB
    var folded = (mixed * 0x4355499b9de8f281UL) + 0xd616e418c4bc0066UL
    folded = fold63c278(folded, builder64c524WorkspaceFold1Table, 7, tables)
    mixed = (
        (mixed * 0x65dd922c973ea261UL)
        + (folded * 0xb2b8c001f0000000UL)
        + 0x457dafb763ad58f5UL
    )
    return FpPair(
        (mixed * 0x9fdf9fbdfb76ed77UL) + 0x14646b0029e6e968UL,
        (mixed * 0x55fb9010c9586c69UL) + 0x63292bd5dae78b98UL
    )
}

internal fun builder64c524RewriteSecondWord(first: ULong, second: ULong, tables: SourceTables): ULong {
    var folded = (first * 0xe191957128574e8dUL) + 0xca735f7e01db7229UL
    folded = fold63c278(folded, builder64c524RewriteFold1Table, 7, tables)
    var mixed = (folded * 0xc0759fa984b4b32bUL) + 0x4e00c393f4ad1417UL
    var folded2 = (mixed * 0x2ca78080bf929d71UL) + 0x489c8bdec6559298UL
    folded2 = fold63c278(folded2, builder64c524RewriteFold2Table, 9, tables)
    mixed = (
        (mixed * 0x21a1db1ac0ca1e41UL)
        + (folded2 * 0x3659a2f000000000UL)
    )
    return (mixed * 0x1685e929cba4a88fUL) + second + 0x56570c70d17acce1UL
}

internal fun builder64c524FinalFirstFold(value: ULong, tables: SourceTables): FpTriple<ULong, UInt, ULong> {
    val product = (value * 0x2d5e3aab4210238bUL)
    var side = (value.toUInt() * 0x6773f057U)
    var folded = foldTableU64Word63c278(
        builder64c524FinalFoldTable + (product.toInt() and 0x0f) * 8,
        tables
    ) + ((product + 0x04d30efa28ce4180UL) shr 4)
    folded = fold63c278(folded, builder64c524FinalFoldTable, 6, tables)
    side = side + (folded.toUInt() * 0xb0000000U)
    val nextBase = folded
    folded = fold63c278(folded, builder64c524FinalFoldTable, 1, tables)
    return FpTriple(nextBase, side + 0xc5960ad5U, folded)
}

internal fun builder64cd40WorkspaceParams(scalar: ULong, firstX2Word: ULong, tables: SourceTables): FpPair<ULong, ULong> {
    val seedA = (scalar * 0x697d0ecbc60d5d0fUL) + 0x937857c2eed8d2b4UL
    val seedB = (scalar * 0xbc235eb940a876ddUL) + 0x18b363a938b968b2UL
    var mixed = (seedA * firstX2Word) + seedB
    var folded = (mixed * 0x10adb81e27dd69a7UL) + 0xe7f726c1fe72a787UL
    folded = fold63c278(folded, 0x301170, 7, tables)
    mixed = (
        (mixed * 0x411310f58c3cbf15UL)
        + (folded * 0xc72468f1d0000000UL)
        + 0x9d21e3104874d274UL
    )
    return FpPair(
        (mixed * 0x0b7281fc87cf2277UL) + 0x719b343dac285e92UL,
        (mixed * 0x71031cfa7b36346eUL) + 0x0c7018d77bac9e24UL
    )
}

internal fun builder64cd40RewriteSecondWord(first: ULong, second: ULong, tables: SourceTables): ULong {
    var folded = (first * 0x4fec946356180ba9UL) + 0xcd7ac1129eab2dd8UL
    folded = fold63c278(folded, 0x3011f0, 7, tables)
    var mixed = (folded * 0x1eb04e8030fffbd7UL) + 0xaee6470479c51db3UL
    var folded2 = (mixed * 0x8d796f74dc90608dUL) + 0x9ac49f51ec349615UL
    folded2 = fold63c278(folded2, 0x301270, 9, tables)
    mixed = (
        (mixed * 0xf9e1d6a0ce988cf5UL)
        + (folded2 * 0x1cc37f7000000000UL)
    )
    return (mixed * 0x87f365d52d3aa373UL) + second + 0x41b4561d4c674238UL
}

internal fun builder64cd40FinalFirstFold(value: ULong, tables: SourceTables): FpTriple<ULong, UInt, ULong> {
    val product = (value * 0x947905173900b973UL)
    var side = ((value.toUInt().toULong() * 0x8f376f21UL) and 0xffff_ffffUL).toUInt()
    var folded = foldTableU64Word63c278(
        0x3012f0 + (product.toInt() and 0x0f) * 8,
        tables
    ) + ((product + 0x0c4fdbc2f625a640UL) shr 4)
    for (i in 0 until 6) {
        folded = foldTableU64Word63c278(
            0x3012f0 + (folded.toInt() and 0x0f) * 8,
            tables
        ) + (folded shr 4)
    }
    side = side + ((folded.toUInt().toULong() * 0x50000000UL) and 0xffff_ffffUL).toUInt()
    val nextBase = folded
    folded = foldTableU64Word63c278(
        0x3012f0 + (folded.toInt() and 0x0f) * 8,
        tables
    ) + (folded shr 4)
    return FpTriple(nextBase, side + 0x06ae2bd7U, folded)
}

internal fun u32AffineBytes63c278(input: ByteArray, mulTable: Int, addTable: Int, label: String, tables: SourceTables): ByteArray {
    if (input.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort(label, input.size, builder63c278VectorBytes)
    }
    val bytes = input
    var out = ByteArray(0)
    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val word = u32TableAffine63c278(
            readUInt32LE(bytes, index * 4),
            mulTable + tableOffset,
            addTable + tableOffset,
            tables
        )
        out = out.concat(u32LEBytes(word))
    }
    return out
}

internal fun packUInt64LE64cd40(words: List<ULong>): ByteArray {
    var out = ByteArray(0)
    for (word in words) {
        out = out.concat(u64LEBytes(word))
    }
    return out
}

internal fun packUInt32LE(words: List<UInt>): ByteArray {
    var out = ByteArray(0)
    for (word in words) {
        out = out.concat(u32LEBytes(word))
    }
    return out
}

internal fun builder642f60AffineWordsFrom64bd0cOutput(output: ByteArray, mulTable: Int, addTable: Int, label: String): List<UInt> {
    if (output.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort(label, output.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = output
    return (0 until builder63c278VectorWords).map { index ->
        u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            mulTable,
            addTable,
            tables
        )
    }
}
