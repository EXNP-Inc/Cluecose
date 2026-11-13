// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.RuntimeTable
import com.dsikic.cluecose.core.tables.SourceTables

internal fun foldTableU32Word63c278(absoluteOffset: Int, tables: SourceTables): UInt {
    val relative = absoluteOffset - table63c278FoldBase
    if (relative < 0 || relative + 4 > tables.foldTables63c278.size) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(
            RuntimeTable.FIRST_PAIR_63C278_FOLD_TABLES.fileName,
            absoluteOffset
        )
    }
    return readUInt32LE(tables.foldTables63c278, relative)
}

internal fun foldTableU64Word63c278(absoluteOffset: Int, tables: SourceTables): ULong {
    val relative = absoluteOffset - table63c278FoldBase
    if (relative < 0 || relative + 8 > tables.foldTables63c278.size) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(
            RuntimeTable.FIRST_PAIR_63C278_FOLD_TABLES.fileName,
            absoluteOffset
        )
    }
    return readUInt64LE(tables.foldTables63c278, relative)
}

internal fun u32WordsFromTableSegments(segments: List<TableSegment>, tables: SourceTables): List<UInt> {
    val out = mutableListOf<UInt>()
    for (segment in segments) {
        require(segment.byteCount % 4 == 0)
        for (offset in 0 until segment.byteCount step 4) {
            out.add(u32TableWord63c278(segment.offset + offset, tables))
        }
    }
    return out
}

internal fun fold63c278(value: ULong, tableOffset: Int, rounds: Int, tables: SourceTables): ULong {
    var folded = value
    for (i in 0 until rounds) {
        val relative = tableOffset - table63c278FoldBase + (folded.toInt() and 0x0f) * 8
        if (relative < 0 || relative + 8 > tables.foldTables63c278.size) {
            throw FirstPairSourceSliceError.tableReadOutOfBounds(
                RuntimeTable.FIRST_PAIR_63C278_FOLD_TABLES.fileName,
                tableOffset
            )
        }
        folded = readUInt64LE(tables.foldTables63c278, relative) + (folded shr 4)
    }
    return folded
}

internal fun fold633fa8Tail(value: ULong, tableOffset: Int, rounds: Int, tables: SourceTables): ULong {
    var folded = value
    for (i in 0 until rounds) {
        val relative = tableOffset - table633fa8TailFoldBase + (folded.toInt() and 0x0f) * 8
        if (relative < 0 || relative + 8 > tables.tailFoldTables633fa8.size) {
            throw FirstPairSourceSliceError.tableReadOutOfBounds(
                RuntimeTable.FIRST_PAIR_633FA8_TAIL_FOLD_TABLES.fileName,
                tableOffset
            )
        }
        folded = readUInt64LE(tables.tailFoldTables633fa8, relative) + (folded shr 4)
    }
    return folded
}

internal fun builderProcess2P5PublicScalarQwordsFromEntropy(entropy11A: ByteArray): List<ULong> {
    val x1Source = builder633fa8NullPublicEntrySourceFromEntropy(entropy11A)
    val aSourceWords = builderProcess2P5PublicASourceWordsFromEntryArgSource(x1Source)
    val bSourceWords = process2P5PublicBSourceStaticWords
    val initialWorkspace = builderProcess2P5PublicInitialWorkspaceFromSourceWords(
        aSourceWords,
        bSourceWords
    )
    val table35d8 = builderProcess2P5PublicTable35d8FromSourceWords(
        builder633fa8InvariantWords2dfc
    )
    val lowPrefix = builderProcess2P5PublicLowPrefixFromTable(
        table35d8,
        builder633fa8InvariantSeed3110
    )

    val highStack = ByteArray(0x360 + 42 * 8)
    val repeated = table35d8[7]
    for (offset in listOf(0x140, 0x148, 0x150, 0x158, 0x160, 0x168, 0x170)) {
        writeUInt64LE(repeated, highStack, offset)
    }
    for (index in initialWorkspace.indices) {
        writeUInt64LE(initialWorkspace[index], highStack, 0x360 + index * 8)
    }

    return builderProcess2P5PublicQwordsFromPreframe(
        lowPrefix,
        highStack
    )
}

internal fun builderProcess2P5PublicASourceWordsFromEntryArgSource(source11A: ByteArray): List<UInt> {
    if (source11A.size != 0x11a) {
        throw FirstPairSourceSliceError.invalidProcess2P5PublicSourceLength(source11A.size)
    }
    val tables = FirstPairConfig.tables
    val source = source11A
    val prelude = vm6420d8(
        process2P5PublicASourceInitialMagic,
        source,
        source,
        tables
    )
    require(prelude, 0x10a, "process2(5) public prelude")

    val seedInputs = mutableListOf<Byte>()
    for (index in 0 until 19) {
        val start = index * 0x0e
        seedInputs.addAll(prelude.copyOfRange(start, start + 0x10).toList())
    }
    seedInputs.addAll(process2P5PublicTableBlock(
        process2P5PublicASourceStaticTailBlock,
        0x10,
        tables
    ).toList())

    val seedBlocks = mutableListOf<Byte>()
    for (index in 0 until builder633fa8ScalarWordCount) {
        val start = index * 0x10
        val block = seedInputs.subList(start, start + 0x10).toByteArray()
        seedBlocks.addAll(vm638840(
            process2P5PublicASourceBlockMagic,
            block,
            block,
            tables
        ).toList())
    }

    val initCLane = process2P5PublicTableBlock(
        process2P5PublicASourceStaticCTable,
        0x10,
        tables
    ).concat(byteArrayOf(0x06.toByte(), 0x06.toByte()))
    val staticALane = process2P5PublicTableBlock(
        process2P5PublicASourceStaticATable,
        0x12,
        tables
    )

    val out = mutableListOf<UInt>()
    for (outerIndex in 0 until builder633fa8ScalarWordCount) {
        val lane = outerIndex and 7
        val eSource = process2P5PublicTableBlock(
            process2P5PublicASourceStaticETable + 0x12 * lane,
            0x12,
            tables
        )
        val dSource = process2P5PublicTableBlock(
            process2P5PublicASourceStaticDTable + 0x12 * lane,
            0x12,
            tables
        )
        val blockOffset = outerIndex * 0x10
        val block = seedBlocks.subList(blockOffset, blockOffset + 0x10).toByteArray()

        val bLane0 = vm638840(
            process2P5PublicASourceBInitMagic,
            eSource,
            eSource,
            tables
        )
        val dLaneInitial = vm6420d8(
            process2P5PublicASourceDInitMagic,
            block,
            block,
            tables
        )
        val eLane0 = vm638840(
            process2P5PublicASourcePrebridgeMagic,
            bLane0,
            bLane0,
            tables
        )
        var cLane = initCLane
        var bLane = bLane0
        var eLane = eLane0

        for (i in 0 until 28) {
            val fLane = vm638840(
                process2P5PublicASourceFMagic,
                dLaneInitial,
                cLane,
                tables
            )
            val aLane = vm638840(
                process2P5PublicASourceAMagic,
                staticALane,
                fLane,
                tables
            )
            val tLane = vm638840(
                process2P5PublicASourceTMagic,
                bLane,
                aLane,
                tables
            )
            eLane = vm638840(
                process2P5PublicASourceBMixMagic,
                eLane,
                tLane,
                tables
            )
            bLane = vm638840(
                process2P5PublicASourceEAdvanceMagic,
                bLane,
                bLane,
                tables
            )
            cLane = vm638840(
                process2P5PublicASourceCAdvanceMagic,
                cLane,
                cLane,
                tables
            )
        }

        val fLane = vm638840(
            process2P5PublicASourcePostFMagic,
            eLane,
            bLane,
            tables
        )
        val dLane = vm638840(
            process2P5PublicASourcePostDMagic,
            fLane,
            dSource,
            tables
        )
        val packELane0 = vm641fcc(
            process2P5PublicASourcePostEMagic,
            dLane,
            tables
        )
        var packELane = packELane0

        val packedLane = ByteArray(4)
        var shift = 32
        for (packIndex in 0 until 8) {
            val cWord = vm638840(
                process2P5PublicASourcePackCMagic,
                packELane,
                packELane,
                tables
            )
            if (shift >= 5) {
                packELane = vm6420d8(
                    process2P5PublicASourcePackEMagic,
                    packELane,
                    packELane,
                    tables
                )
            }
            val bWord = vm638840(
                process2P5PublicASourcePackBMagic,
                cWord,
                cWord,
                tables
            )

            val selected = (bWord[2].toInt() and 0xff) xor ((bWord[3].toInt() and 0xff) shl 3)
            val packed = process2P5PublicTableByte(
                process2P5PublicASourceNibbleTable + selected,
                tables
            )
            val nibble0 = if ((packIndex and 1) == 0) (packed.toInt() and 0xff and 0x0f) else ((packed.toInt() and 0xff) shr 4)
            var nibble = nibble0
            if (shift < 4) {
                val mask = if (shift == 0) 0 else (1 shl shift) - 1
                nibble = nibble and mask
            }

            val byteIndex = packIndex shr 1
            if ((packIndex and 1) == 0) {
                packedLane[byteIndex] = nibble.toByte()
            } else {
                packedLane[byteIndex] = (packedLane[byteIndex].toInt() xor (nibble shl 4)).toByte()
            }
            shift = maxOf(shift - 4, 0)
        }

        out.add(readUInt32LE(packedLane, 0))
    }
    return out
}

internal fun builderProcess2P5PublicInitialWorkspaceFromSourceWords(aSourceWords: List<UInt>, bSourceWords: List<UInt>): List<ULong> {
    val prefixes = builderProcess2P5PublicInitialPrefixesFromSourceWords(
        aSourceWords,
        bSourceWords
    )
    return builderProcess2P5PublicInitialWorkspaceFromPrefixes(
        prefixes.first,
        prefixes.second
    )
}

internal fun builderProcess2P5PublicInitialPrefixesFromSourceWords(aSourceWords: List<UInt>, bSourceWords: List<UInt>): FpPair<List<ULong>, List<ULong>> {
    if (aSourceWords.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalidProcess2P5PublicWordCount("A-prefix", aSourceWords.size)
    }
    if (bSourceWords.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalidProcess2P5PublicWordCount("B-prefix", bSourceWords.size)
    }
    val tables = FirstPairConfig.tables

    val aValues = mutableListOf<ULong>()
    var word = aSourceWords[0] * process2P5PublicPrefixAInitWordMul + process2P5PublicPrefixAInitWordAdd
    aValues.add(builderProcess2P5PublicPrefixQword(
        word,
        process2P5PublicPrefixAFoldTable,
        process2P5PublicPrefixAQwordMul,
        process2P5PublicPrefixAQwordAdd,
        process2P5PublicPrefixAFoldMul,
        process2P5PublicPrefixAFinalMul,
        process2P5PublicPrefixAFinalAdd,
        tables
    ))
    for (index in 1 until builder633fa8ScalarWordCount) {
        val tableOffset = (index shl 2) and 0x1c
        word = aSourceWords[index] * u32TableWord63c278(process2P5PublicPrefixAWordMulTable + tableOffset, tables) + u32TableWord63c278(process2P5PublicPrefixAWordAddTable + tableOffset, tables)
        word = word * process2P5PublicPrefixAWordMul + process2P5PublicPrefixAWordAdd
        aValues.add(builderProcess2P5PublicPrefixQword(
            word,
            process2P5PublicPrefixAFoldTable,
            process2P5PublicPrefixAQwordMul,
            process2P5PublicPrefixAQwordAdd,
            process2P5PublicPrefixAFoldMul,
            process2P5PublicPrefixAFinalMul,
            process2P5PublicPrefixAFinalAdd,
            tables
        ))
    }

    val bValues = mutableListOf<ULong>()
    word = bSourceWords[0] * process2P5PublicPrefixBInitWordMul + process2P5PublicPrefixBInitWordAdd
    bValues.add(builderProcess2P5PublicPrefixQword(
        word,
        process2P5PublicPrefixBFoldTable,
        process2P5PublicPrefixBQwordMul,
        process2P5PublicPrefixBQwordAdd,
        process2P5PublicPrefixBFoldMul,
        process2P5PublicPrefixBFinalMul,
        process2P5PublicPrefixBFinalAdd,
        tables
    ))
    for (index in 1 until builder633fa8ScalarWordCount) {
        val tableOffset = (index and 7) shl 2
        word = bSourceWords[index] * u32TableWord63c278(process2P5PublicPrefixBWordMulTable + tableOffset, tables) + u32TableWord63c278(process2P5PublicPrefixBWordAddTable + tableOffset, tables)
        word = word * process2P5PublicPrefixBWordMul + process2P5PublicPrefixBWordAdd
        bValues.add(builderProcess2P5PublicPrefixQword(
            word,
            process2P5PublicPrefixBFoldTable,
            process2P5PublicPrefixBQwordMul,
            process2P5PublicPrefixBQwordAdd,
            process2P5PublicPrefixBFoldMul,
            process2P5PublicPrefixBFinalMul,
            process2P5PublicPrefixBFinalAdd,
            tables
        ))
    }

    return FpPair(cumulativeQwords(aValues), cumulativeQwords(bValues))
}

internal fun builderProcess2P5PublicInitialWorkspaceFromPrefixes(aPrefix: List<ULong>, bPrefix: List<ULong>): List<ULong> {
    if (aPrefix.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalidProcess2P5PublicQwordCount("A-prefix", aPrefix.size)
    }
    if (bPrefix.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalidProcess2P5PublicQwordCount("B-prefix", bPrefix.size)
    }

    val aVec = diffCumulativeQwords(aPrefix)
    val bVec = diffCumulativeQwords(bPrefix)
    val constants = process2P5PublicInitWorkspaceConstants
    val out = mutableListOf<ULong>()
    for (index in 0 until 42) {
        val low = maxOf(0, index - 19)
        val high = minOf(index, 19)
        val productSum: ULong
        val aSum: ULong
        val bSum: ULong
        val count: Int
        if (low <= high) {
            var product: ULong = 0UL
            for (bIndex in low..high) {
                product = product + aVec[index - bIndex] * bVec[bIndex]
            }
            productSum = product

            var aWindow = aPrefix[index - low]
            if (index - high - 1 >= 0) {
                aWindow = aWindow - aPrefix[index - high - 1]
            }
            aSum = aWindow

            var bWindow = bPrefix[high]
            if (low != 0) {
                bWindow = bWindow - bPrefix[low - 1]
            }
            bSum = bWindow
            count = high - low + 1
        } else {
            productSum = 0UL
            aSum = 0UL
            bSum = 0UL
            count = 0
        }

        var value = count.toULong() * constants.countMul + constants.countAdd
        value = value + productSum * constants.productMul
        value = value + bSum * constants.bPrefixMul
        value = value + aSum * constants.aPrefixMul
        value = value * constants.finalMul + constants.finalAdd
        out.add(value)
    }
    return out
}

internal fun builderProcess2P5PublicTable35d8FromSourceWords(sourceWords: List<UInt>): List<ULong> {
    if (sourceWords.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalidProcess2P5PublicWordCount("table35d8", sourceWords.size)
    }
    val tables = FirstPairConfig.tables
    val out = mutableListOf<ULong>()
    for (index in sourceWords.indices) {
        val sourceWord = sourceWords[index]
        val tableOffset = (index shl 2) and 0x1c
        var word = sourceWord * u32TableWord63c278(process2P5PublicTableU32MulTable + tableOffset, tables) + u32TableWord63c278(process2P5PublicTableU32AddTable + tableOffset, tables)
        word = word * process2P5PublicTableWordMul + process2P5PublicTableWordAdd

        var qword = word.toULong() * process2P5PublicTableQwordMul + process2P5PublicTableQwordAdd
        qword = foldProcess2P5Public(
            qword,
            process2P5PublicTableFoldTable,
            8,
            tables
        )
        val folded = qword * process2P5PublicTableFoldMul
        out.add(word.toULong() * process2P5PublicTableFinalMul + folded + process2P5PublicTableFinalAdd)
    }
    return out
}

internal fun builderProcess2P5PublicLowPrefixFromTable(qwords35d8: List<ULong>, seed80: ULong): ByteArray {
    if (qwords35d8.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalidProcess2P5PublicQwordCount("low-prefix table", qwords35d8.size)
    }
    val out = ByteArray(0xb0)
    writeUInt64LE(
        seed80 * process2P5PublicLowA8Mul + process2P5PublicLowA8Add,
        out,
        0xa8
    )
    writeUInt64LE(
        seed80 * process2P5PublicLow80Mul + process2P5PublicLow80Add,
        out,
        0x80
    )
    for (copyOffset in process2P5PublicLowCopyOffsets) {
        writeUInt64LE(qwords35d8[copyOffset.tableIndex], out, copyOffset.offset)
    }
    return out
}

internal fun builderProcess2P5PublicQwordsFromPreframe(lowPrefix: ByteArray, highStack: ByteArray): List<ULong> {
    val workspaces = builderProcess2P5PublicQwordWorkspacesFromPreframe(
        lowPrefix,
        highStack
    )
    return workspaces[workspaces.size - 1].subList(22, 42)
}

internal fun builderProcess2P5PublicQwordWorkspacesFromPreframe(lowPrefix: ByteArray, highStack: ByteArray): List<List<ULong>> {
    require(lowPrefix, 0xb0, "process2(5) qword low prefix")
    require(highStack, 0x360 + 42 * 8, "process2(5) qword high stack")
    val tables = FirstPairConfig.tables

    val low28 = readUInt64LE(lowPrefix, 0x28)
    val low30 = readUInt64LE(lowPrefix, 0x30)
    val low38 = readUInt64LE(lowPrefix, 0x38)
    val low40 = readUInt64LE(lowPrefix, 0x40)
    val low48 = readUInt64LE(lowPrefix, 0x48)
    val low50 = readUInt64LE(lowPrefix, 0x50)
    val low58 = readUInt64LE(lowPrefix, 0x58)
    val low60 = readUInt64LE(lowPrefix, 0x60)
    val low68 = readUInt64LE(lowPrefix, 0x68)
    val low70 = readUInt64LE(lowPrefix, 0x70)
    val low78 = readUInt64LE(lowPrefix, 0x78)
    val low80 = readUInt64LE(lowPrefix, 0x80)
    val low88 = readUInt64LE(lowPrefix, 0x88)
    val low90 = readUInt64LE(lowPrefix, 0x90)
    val lowA8 = readUInt64LE(lowPrefix, 0xa8)

    val workspace = (0 until 42).map { readUInt64LE(highStack, 0x360 + it * 8) }.toMutableList()
    var x22 = workspace[0]
    val x6 = readUInt64LE(highStack, 0x140)
    val x19 = readUInt64LE(highStack, 0x148)
    val x21 = readUInt64LE(highStack, 0x150)
    val x23 = readUInt64LE(highStack, 0x158)
    val x24 = readUInt64LE(highStack, 0x160)
    val x26 = readUInt64LE(highStack, 0x168)
    val x28 = readUInt64LE(highStack, 0x170)

    val workspaces = mutableListOf(workspace.toList())
    for (index in 0 until 22) {
        val reg15 = low88
        val reg2 = low50
        val reg1 = low58
        var reg4 = low40
        var reg3 = low48

        val state = x22 * lowA8 + low80
        var folded = foldProcess2P5Public(
            state * 0x87d6a191657cf88bUL + 0x55ab3c8b3f81c5eaUL,
            process2P5PublicQwordFoldTableA,
            7,
            tables
        )
        val stateShifted = state * 0x5513e20130c294ffUL + folded * 0x097f450230000000UL + 0x65416d6b1d6e1cbcUL
        val x27 = stateShifted * 0xde9a0217389253bbUL + 0x7368784697fb3dc5UL
        val x20 = stateShifted * 0x0421be0fdc09a97cfUL + 0x492946def7da33b1UL

        reg3 = x27 * reg3 + x20
        val x22Head = x27 * low90 + x20 + x22
        reg4 = x27 * reg4 + x20

        folded = foldProcess2P5Public(
            x22Head * 0xe991db2a5d2a7fadUL + 0xddaca38024dd36cdUL,
            process2P5PublicQwordFoldTableB,
            7,
            tables
        )
        val x12 = folded * 0xcf053a359e1d9b81UL + 0xcfa9a29b5752d274UL
        folded = foldProcess2P5Public(
            x12 * 0x71795e15d000819bUL + 0xf0c1332200ddc903UL,
            process2P5PublicQwordFoldTableC,
            9,
            tables
        )

        val old = workspace.subList(index, index + 20).toList()
        val out = MutableList<ULong>(20) { 0UL }
        out[0] = x22Head
        out[1] = x27 * reg15 + x20 + old[1]
        out[2] = x27 * low78 + x20 + old[2]
        val acc13 = x27 * x6 + x20
        out[3] = x27 * low70 + x20 + old[3]
        out[4] = x27 * low68 + x20 + old[4]
        out[5] = x27 * low60 + x20 + old[5]
        out[6] = x27 * reg1 + x20 + old[6]
        val acc15 = x27 * x19 + x20
        out[7] = x27 * reg2 + x20 + old[7]
        out[8] = reg3 + old[8]
        val acc0 = x27 * x21 + x20
        out[9] = reg4 + old[9]
        val acc2 = x27 * low38 + x20
        out[10] = acc2 + old[10]
        val acc12 = x27 * low30 + x20
        out[11] = acc12 + old[11]
        val acc22 = x27 * low28 + x20
        out[12] = acc22 + old[12]
        val acc14 = x27 * x23 + x20
        val acc1 = x27 * x24 + x20
        out[13] = acc13 + old[13]
        out[14] = acc15 + old[14]
        val acc16 = x27 * x26 + x20
        val acc15Tail = x27 * x28 + x20
        out[15] = acc0 + old[15]
        out[16] = acc14 + old[16]

        val finalAcc0 = folded * 0x7a1cf7b000000000UL + x12 * 0x85a6c1a6777a6587UL
        val finalAcc = finalAcc0 * 0xbf59bd30f12b2173UL + out[1]
        out[17] = acc1 + old[17]
        out[18] = acc16 + old[18]
        out[19] = acc15Tail + old[19]
        x22 = finalAcc + 0x0ba9328bc380f3f5UL
        out[1] = x22

        for (outIndex in out.indices) {
            workspace[index + outIndex] = out[outIndex]
        }
        workspaces.add(workspace.toList())
    }
    return workspaces
}

internal fun builderProcess2P5PublicScalarWordsFromQwords(qwords: List<ULong>): List<UInt> {
    if (qwords.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalidProcess2P5PublicQwordCount("scalar words", qwords.size)
    }
    val tables = FirstPairConfig.tables
    var state: ULong = 0x8e047df005b7774bUL
    val out = mutableListOf<UInt>()
    for (index in qwords.indices) {
        val qword = qwords[index]
        state = state * 0x4f9b1e335b5175b1UL + qword * 0xddc0126ec4f0da8bUL + 0x807a205bcf09b957UL
        val foldedSeed = state * 0x0cc6d1cb7a71ea27UL + 0x75f17a53af690cbcUL
        val folded7 = foldProcess2P5Public(
            foldedSeed,
            process2P5PublicScalarQwordFoldTable,
            7,
            tables
        )
        var word = (state.toInt().toUInt() * 0xb904cc8bU + folded7.toInt().toUInt() * 0x30000000U + 0x7733dbc5U).toInt().toUInt()
        val folded16 = foldProcess2P5Public(
            folded7,
            process2P5PublicScalarQwordFoldTable,
            9,
            tables
        )
        val tableOffset = (index * 4) and 0x1c
        word = word * u32TableWord63c278(process2P5PublicScalarQwordMulTable + tableOffset, tables) + u32TableWord63c278(process2P5PublicScalarQwordAddTable + tableOffset, tables)
        out.add(word)

        state = folded7 * 0xf5b69300c49039c7UL + folded16 * 0xb6fc639000000000UL + 0x5c589cf77e794af2UL
    }
    return out
}

internal fun builderProcess2P5PublicScalarWindowFromWords(words: List<UInt>): ByteArray {
    if (words.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalidProcess2P5PublicWordCount("scalar pack", words.size)
    }
    val tables = FirstPairConfig.tables
    val out = ByteArray(builder633fa8ScalarWindowBytes)
    var acc: ULong = 0UL
    var bits = 0
    var outIndex = 0
    for (index in words.indices) {
        val word = words[index]
        val tableOffset = (index * 4) and 0x1c
        var value = word * u32TableWord63c278(process2P5PublicScalarPackMulTable + tableOffset, tables) + u32TableWord63c278(process2P5PublicScalarPackAddTable + tableOffset, tables)
        value = value * 0x0b6afc2fU + 0x4608a396U

        acc = acc xor (value.toULong() shl bits)
        bits += 28
        while (bits > 16 && outIndex < 69) {
            out[outIndex] = (acc and 0xffUL).toByte()
            acc = acc shr 8
            outIndex += 1
            bits -= 8
        }
    }
    if (bits >= 1 && outIndex < 69) {
        out[outIndex] = (acc and 0xffUL).toByte()
    }
    return out
}

internal fun builderProcess2P5PublicPrefixQword(word: UInt, foldTable: Int, qwordMul: ULong, qwordAdd: ULong, foldMul: ULong, finalMul: ULong, finalAdd: ULong, tables: SourceTables): ULong {
    val folded = foldProcess2P5Public(
        word.toULong() * qwordMul + qwordAdd,
        foldTable,
        8,
        tables
    )
    return folded * foldMul + word.toULong() * finalMul + finalAdd
}

internal fun diffCumulativeQwords(prefixes: List<ULong>): List<ULong> {
    if (prefixes.isEmpty()) return listOf()
    val out = mutableListOf<ULong>(prefixes[0])
    for (index in 1 until prefixes.size) {
        out.add(prefixes[index] - prefixes[index - 1])
    }
    return out
}

internal fun cumulativeQwords(values: List<ULong>): List<ULong> {
    var total: ULong = 0UL
    val out = mutableListOf<ULong>()
    for (value in values) {
        total = total + value
        out.add(total)
    }
    return out
}

internal fun process2P5PublicTableBlock(libOffset: Int, byteCount: Int, tables: SourceTables): ByteArray {
    val relative = libOffset - process2P5PublicTableBase
    if (relative < 0 || relative + byteCount > tables.process2PublicTables.size) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(
            RuntimeTable.FIRST_PAIR_PROCESS2_PUBLIC_TABLES.fileName,
            libOffset
        )
    }
    return tables.process2PublicTables.copyOfRange(relative, relative + byteCount)
}

internal fun process2P5PublicTableByte(libOffset: Int, tables: SourceTables): Byte {
    return process2P5PublicTableBlock(libOffset, 1, tables)[0]
}

internal fun process2P5PublicTableUInt64(libOffset: Int, tables: SourceTables): ULong {
    val bytes = process2P5PublicTableBlock(libOffset, 8, tables)
    return readUInt64LE(bytes, 0)
}

internal fun foldProcess2P5Public(value: ULong, tableOffset: Int, rounds: Int, tables: SourceTables): ULong {
    var folded = value
    for (i in 0 until rounds) {
        folded = process2P5PublicTableUInt64(
            tableOffset + (folded.toInt() and 0x0f) * 8,
            tables
        ) + (folded shr 4)
    }
    return folded
}

internal fun builder633fa8TailStreamU64(word: UInt, wordMul: ULong, wordAdd: ULong, foldTable: Int, foldMul: ULong, mixMul: ULong, mixAdd: ULong, tables: SourceTables): ULong {
    val folded = fold633fa8Tail(
        word.toULong() * wordMul + wordAdd,
        foldTable,
        8,
        tables
    )
    return folded * foldMul + word.toULong() * mixMul + mixAdd
}

internal fun convolutionWorkspace633fa8Tail(stack: ByteArray, aVectorOffset: Int, aPrefixOffset: Int, bVectorOffset: Int, bPrefixOffset: Int, outOffset: Int, length: Int, outputCount: Int, countMul: ULong, countAdd: ULong, productMul: ULong, bPrefixMul: ULong, aPrefixMul: ULong, finalMul: ULong, finalAdd: ULong): Unit {
    val aVector = (0 until length).map { readUInt64LE(stack, aVectorOffset + it * 8) }
    val aPrefix = (0 until length).map { readUInt64LE(stack, aPrefixOffset + it * 8) }
    val bVector = (0 until length).map { readUInt64LE(stack, bVectorOffset + it * 8) }
    val bPrefix = (0 until length).map { readUInt64LE(stack, bPrefixOffset + it * 8) }

    for (index in 0 until outputCount) {
        val low = maxOf(0, index - (length - 1))
        val high = minOf(index, length - 1)
        val productSum: ULong
        val aSum: ULong
        val bSum: ULong
        val count: Int
        if (low <= high) {
            var runningProductSum: ULong = 0UL
            for (bIndex in low..high) {
                runningProductSum = runningProductSum + aVector[index - bIndex] * bVector[bIndex]
            }
            productSum = runningProductSum

            var runningASum = aPrefix[index - low]
            if (index - high - 1 >= 0) {
                runningASum = runningASum - aPrefix[index - high - 1]
            }
            aSum = runningASum

            var runningBSum = bPrefix[high]
            if (low > 0) {
                runningBSum = runningBSum - bPrefix[low - 1]
            }
            bSum = runningBSum
            count = high - low + 1
        } else {
            productSum = 0UL
            aSum = 0UL
            bSum = 0UL
            count = 0
        }

        var out = count.toULong() * countMul + countAdd
        out = out + productSum * productMul
        out = out + bSum * bPrefixMul
        out = out + aSum * aPrefixMul
        out = out * finalMul + finalAdd
        writeUInt64LE(out, stack, outOffset + index * 8)
    }
}

internal fun unpack3BitStream5bdd14(source: ByteArray, offset: Int, count: Int): FpPair<ByteArray, Int> {
    if (count < 0) {
        throw FirstPairSourceSliceError.sourceTooShort("3-bit unpack", count, 0)
    }
    if (offset < 0 || offset >= source.size) {
        throw FirstPairSourceSliceError.sourceTooShort("3-bit unpack source", source.size, offset + 1)
    }

    var pointer = offset
    var bitOffset = 0
    val out = mutableListOf<Byte>()
    var remaining = count
    while (remaining > 0) {
        val currentBit = bitOffset and 0xff
        if (currentBit == 8) {
            pointer += 1
            if (pointer >= source.size) {
                throw FirstPairSourceSliceError.sourceTooShort("3-bit unpack source", source.size, pointer + 1)
            }
            out.add((source[pointer].toInt() and 7).toByte())
            bitOffset = 3
        } else if (currentBit == 0) {
            out.add((source[pointer].toInt() and 7).toByte())
            bitOffset = 3
        } else if (currentBit <= 5) {
            out.add(((source[pointer].toInt() and 0xff) shr currentBit and 7).toByte())
            bitOffset = currentBit + 3
        } else {
            val spanBits = currentBit - 5
            if (pointer + 1 >= source.size) {
                throw FirstPairSourceSliceError.sourceTooShort("3-bit unpack source", source.size, pointer + 2)
            }
            val low = (source[pointer].toInt() and 0xff) shr currentBit
            val high = source[pointer + 1].toInt() and 0xff and ((1 shl spanBits) - 1)
            out.add(((low or (high shl (8 - currentBit))) and 7).toByte())
            pointer += 1
            bitOffset = spanBits
        }
        remaining -= 1
    }

    pointer += 1
    if (pointer > source.size) {
        throw FirstPairSourceSliceError.sourceTooShort("3-bit unpack source", source.size, pointer)
    }
    return FpPair(out.toByteArray(), pointer)
}

internal fun fold63c278FirstNibbleBeforeAdd(product: ULong, addend: ULong, tableOffset: Int, tables: SourceTables): ULong {
    val relative = tableOffset - table63c278FoldBase + (product.toInt() and 0x0f) * 8
    if (relative < 0 || relative + 8 > tables.foldTables63c278.size) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(
            RuntimeTable.FIRST_PAIR_63C278_FOLD_TABLES.fileName,
            tableOffset
        )
    }
    val folded = readUInt64LE(tables.foldTables63c278, relative) + ((product + addend) shr 4)
    return fold63c278(folded, tableOffset, 7, tables)
}

internal fun fold32ByNibbles63c278(value: UInt, tableOffset: Int, rounds: Int, tables: SourceTables): UInt {
    var folded = value
    for (i in 0 until rounds) {
        val word = foldTableU32Word63c278(tableOffset + (folded.toInt() and 0x0f) * 4, tables)
        folded = word + (folded shr 4)
    }
    return folded
}

internal fun laneTable6388f0Expand(tableOffset: Int, selectorByte: Byte, tables: SourceTables): ByteArray {
    val rowOffset = tableOffset + (selectorByte.toInt() and 0xff) * builder6388f0LaneTablePackedRowSize
    val row = checkedSlice(
        tables.laneTables6388f0,
        rowOffset,
        builder6388f0LaneTablePackedRowSize,
        RuntimeTable.FIRST_PAIR_6388F0_LANE_TABLES.fileName
    )
    val out = mutableListOf<Byte>()
    for (packed in row) {
        out.add((packed.toInt() and 7).toByte())
        out.add(((packed.toInt() and 0xff) shr 3 and 7).toByte())
    }
    return out.toByteArray()
}

internal fun lanePrefixed6388f0(prefixWord: UInt, state18: ByteArray): ByteArray {
    require(state18, 14, "6388f0 lane state")
    return u32LEBytes(prefixWord).concat(state18.copyOfRange(0, 14))
}

internal fun selector6388f0(index: Int, scheduleWord: UInt, tables: SourceTables): UInt {
    val tableOffset = (index * 4) and 0x1c
    if (tableOffset + 4 > tables.selectorMul6388f0.size ||
        tableOffset + 4 > tables.selectorAdd6388f0.size
    ) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(
            RuntimeTable.FIRST_PAIR_6388F0_SELECTOR_MUL.fileName,
            tableOffset
        )
    }
    val multiplier = readUInt32LE(tables.selectorMul6388f0, tableOffset).toULong()
    val addend = readUInt32LE(tables.selectorAdd6388f0, tableOffset).toULong()
    return (scheduleWord.toULong() * multiplier + addend).toInt().toUInt()
}

internal fun builder6388f0LowSeedPhaseFromCF0Seed(spec: LowSeedPhaseSpec, seedCF0: ByteArray, tables: SourceTables): LowSeedPhaseResult {
    val count = spec.auxMagics.size
    for (entry in listOf(
        "e10 magics" to spec.e10Magics.size,
        "e10 markers" to spec.e10Markers.size,
        "e10 shifts" to builder6388f0LowSeedE10SourceShifts.size,
        "f40 magics" to spec.f40Magics.size,
    )) {
        val label = entry.first
        val valueCount = entry.second
        if (valueCount != count) {
            throw FirstPairSourceSliceError.invalidLowSeedPhaseInputCount(label, valueCount, count)
        }
    }
    require(seedCF0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed cf0 seed")

    val staticBlock = builder6388f0LowSeedStaticBlock(spec.staticOffset, tables)
    var cf0 = seedCF0
    for (index in 0 until count) {
        val bd0 = vm638840(spec.bd0Magic, cf0, staticBlock, tables)
        val ab0 = vm641fcc(spec.unaryMagic, bd0, tables)
        val e10Source = builder6388f0LowSeedE10SourceFromAB0(
            spec.e10Markers[index].toByte(),
            builder6388f0LowSeedE10SourceShifts[index],
            ab0
        )
        val e10 = vm638840(
            spec.e10Magics[index],
            e10Source,
            e10Source,
            tables
        )
        val f40 = vm6420d8(
            spec.f40Magics[index],
            ab0,
            ab0,
            tables
        )
        val aux = vm638840(
            spec.auxMagics[index],
            e10,
            f40,
            tables
        )
        cf0 = vm638840(spec.phaseMagic, cf0, aux, tables)
    }
    return LowSeedPhaseResult(cf0)
}

internal fun builder6388f0LowSeedE10SourceFromAB0(marker: Byte, shift: Int, ab0: ByteArray): ByteArray {
    if ((marker.toInt() and 0xff) > 7) {
        throw FirstPairSourceSliceError.invalidLowSeedMarker(marker.toInt() and 0xff)
    }
    if (shift < 1 || shift > builder6388f0LowSeedBlockBytes) {
        throw FirstPairSourceSliceError.invalidLowSeedShift(shift)
    }
    require(
        ab0,
        builder6388f0LowSeedBlockBytes - shift,
        "6388f0 low-seed ab0 source"
    )

    val out = ByteArray(builder6388f0LowSeedBlockBytes)
    out[shift - 1] = marker
    val copyCount = builder6388f0LowSeedBlockBytes - shift
    if (copyCount > 0) {
        ab0.copyOfRange(0, copyCount).copyInto(out, shift)
    }
    return out
}

internal fun builder6388f0LowSeedStaticBlock(libOffset: Int, tables: SourceTables): ByteArray {
    val offset = libOffset - builder6388f0LowSeedStaticBase
    return checkedSlice(
        tables.lowSeedStatics6388f0,
        offset,
        builder6388f0LowSeedBlockBytes,
        RuntimeTable.FIRST_PAIR_6388F0_LOW_SEED_STATICS.fileName
    )
}

internal fun builder6388f0LowLoopStaticBlock(libOffset: Int, byteCount: Int, tables: SourceTables): ByteArray {
    val offset = libOffset - builder6388f0LowLoopStaticBase
    return checkedSlice(
        tables.lowLoopStatics6388f0,
        offset,
        byteCount,
        RuntimeTable.FIRST_PAIR_6388F0_LOW_LOOP_STATICS.fileName
    )
}

internal fun builder6388f0LowLoopStaticByte(libOffset: Int, tables: SourceTables): Byte {
    return builder6388f0LowLoopStaticBlock(libOffset, 1, tables)[0]
}

internal fun builder633fa8NullTableBlock(libOffset: Int, byteCount: Int, tables: SourceTables): ByteArray {
    val offset = libOffset - builder633fa8NullTableBase
    return checkedSlice(
        tables.nullTables633fa8,
        offset,
        byteCount,
        RuntimeTable.FIRST_PAIR_633FA8_NULL_TABLES.fileName
    )
}

internal fun builder633fa8NullNibbleByte(libOffset: Int, tables: SourceTables): Byte {
    val offset = libOffset - builder633fa8NullNibbleTableBase
    return checkedSlice(
        tables.nullNibble633fa8,
        offset,
        1,
        RuntimeTable.FIRST_PAIR_633FA8_NULL_NIBBLE.fileName
    )[0]
}

internal fun u32TableWord633fa8Null(absoluteOffset: Int, tables: SourceTables): UInt {
    val relative = absoluteOffset - builder633fa8NullTableBase
    if (relative < 0 || relative + 4 > tables.nullTables633fa8.size) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(
            RuntimeTable.FIRST_PAIR_633FA8_NULL_TABLES.fileName,
            absoluteOffset
        )
    }
    return readUInt32LE(tables.nullTables633fa8, relative)
}

internal fun fold633fa8NullCheck32(value: UInt, tableOffset: Int, rounds: Int, tables: SourceTables): UInt {
    var folded = value
    for (i in 0 until rounds) {
        folded = u32TableWord633fa8Null(
            tableOffset + (folded.toInt() and 0x0f) * 4,
            tables
        ) + (folded shr 4)
    }
    return folded
}

internal fun expand3BitPairTableRow633fa8Null(raw9: ByteArray): ByteArray {
    if (raw9.size != 9) {
        throw FirstPairSourceSliceError.sourceTooShort("633fa8 null 3-bit pair row", raw9.size, 9)
    }
    val out = mutableListOf<Byte>()
    for (value in raw9) {
        out.add((value.toInt() and 7).toByte())
        out.add(((value.toInt() and 0xff) shr 3 and 7).toByte())
    }
    return out.toByteArray()
}

internal fun stitch633fa8NullPrelude11A(firstBlock: ByteArray, restBlocks: ByteArray): ByteArray {
    if (firstBlock.size != builder633fa8NullSeedBlockBytes) {
        throw FirstPairSourceSliceError.sourceTooShort(
            "633fa8 null prelude first stitch block",
            firstBlock.size,
            builder633fa8NullSeedBlockBytes
        )
    }
    val restByteCount = (builder633fa8ScalarWordCount - 1) * builder633fa8NullSeedBlockBytes
    if (restBlocks.size != restByteCount) {
        throw FirstPairSourceSliceError.sourceTooShort(
            "633fa8 null prelude rest stitch blocks",
            restBlocks.size,
            restByteCount
        )
    }

    val out = ByteArray(builder633fa8NullEntropyBytes)
    firstBlock.copyInto(out, 0)
    for (index in 0 until (builder633fa8ScalarWordCount - 1)) {
        val srcStart = index * builder633fa8NullSeedBlockBytes + 2
        val dstStart = builder633fa8NullSeedBlockBytes + index * builder633fa8NullSeedBlockStride
        restBlocks.copyOfRange(srcStart, srcStart + builder633fa8NullSeedBlockStride).copyInto(out, dstStart)
    }
    return out
}

internal fun builder633fa8NullScheduleCheck(scheduleWords: List<UInt>, sourceWords: List<UInt>, scheduleMulTable: Int, sourceMulTable: Int, addTable: Int, foldTable: Int, target: UInt, foldTarget: UInt, tables: SourceTables): Boolean {
    if (scheduleWords.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalid633fa8WordCount(scheduleWords.size)
    }
    if (sourceWords.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalid633fa8WordCount(sourceWords.size)
    }

    for (index in (builder633fa8ScalarWordCount - 1) downTo 0) {
        val tableOffset = (index * 4) and 0x1c
        val word = scheduleWords[index] * u32TableWord63c278(scheduleMulTable + tableOffset, tables) + sourceWords[index] * u32TableWord63c278(sourceMulTable + tableOffset, tables) + u32TableWord63c278(addTable + tableOffset, tables)
        if (word == target) {
            continue
        }
        val folded = fold633fa8NullCheck32(word, foldTable, 7, tables)
        return (folded and 0x0fU) == foldTarget
    }
    return true
}
