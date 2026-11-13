// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.RuntimeTable

internal fun builder633fa8NullPostAcceptBlocks(scheduleWords: List<UInt>): Builder633fa8NullPostAcceptResult {
    if (scheduleWords.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalid633fa8WordCount(scheduleWords.size)
    }
    val tables = FirstPairConfig.tables
    val initCF0 = builder633fa8NullTableBlock(
        builder633fa8NullPostInitCF0Static,
        builder633fa8NullSeedBlockBytes,
        tables
    )
    val initBD0 = builder633fa8NullTableBlock(
        builder633fa8NullPostInitBD0Static,
        builder633fa8NullSeedBlockBytes,
        tables
    )
    val finalCF0Static = builder633fa8NullTableBlock(
        builder633fa8NullPostFinalCF0Static,
        builder633fa8NullLoopLaneBytes,
        tables
    )
    val finalBD0Static = builder633fa8NullTableBlock(
        builder633fa8NullPostFinalBD0Static,
        builder633fa8NullLoopLaneBytes,
        tables
    )

    val blocks4080 = ArrayList<Byte>()
    val blocks3f40 = ArrayList<Byte>()

    for (index in scheduleWords.indices) {
        val scheduleWord = scheduleWords[index]
        val tableOffset = (index * 4) and 0x1c
        val selector = scheduleWord *
            u32TableWord63c278(builder633fa8NullPostKeyMulTable + tableOffset, tables) +
            u32TableWord63c278(builder633fa8NullPostKeyAddTable + tableOffset, tables)
        var cf0State = initCF0
        var bd0State = initBD0

        for (shift in intArrayOf(24, 16, 8, 0)) {
            // Built by append rather than chained `+`: the operator chain
            // blows the type-checker budget in this file's overload scope.
            var cf0Source = ByteArray(3)
            cf0Source = cf0Source.concat(byteArrayOf(0x05.toByte()))
            cf0Source = cf0Source.concat(cf0State.copyOfRange(0, 8))
            cf0Source = cf0Source.concat(cf0State.copyOfRange(8, 12))
            cf0Source = cf0Source.concat(cf0State.copyOfRange(12, 14))
            var bd0Source = ByteArray(3)
            bd0Source = bd0Source.concat(byteArrayOf(0x03.toByte()))
            bd0Source = bd0Source.concat(bd0State.copyOfRange(0, 8))
            bd0Source = bd0Source.concat(bd0State.copyOfRange(8, 12))
            bd0Source = bd0Source.concat(bd0State.copyOfRange(12, 14))
            val cf0 = vm638840(
                builder633fa8NullPostInitCF0Magic,
                cf0Source,
                cf0Source,
                tables
            )
            val bd0 = vm638840(
                builder633fa8NullPostInitBD0Magic,
                bd0Source,
                bd0Source,
                tables
            )
            val byteValue = ((selector shr shift) and 0xffU).toInt()
            val cf0Row = expand3BitPairTableRow633fa8Null(
                builder633fa8NullTableBlock(
                    builder633fa8NullPostTableCF0 + byteValue * 9,
                    9,
                    tables
                )
            )
            val bd0Row = expand3BitPairTableRow633fa8Null(
                builder633fa8NullTableBlock(
                    builder633fa8NullPostTableBD0 + byteValue * 9,
                    9,
                    tables
                )
            )
            cf0State = vm638840(
                builder633fa8NullPostMixCF0Magic,
                cf0,
                cf0Row,
                tables
            )
            bd0State = vm638840(
                builder633fa8NullPostMixBD0Magic,
                bd0,
                bd0Row,
                tables
            )
        }

        val e10 = vm638840(
            builder633fa8NullPostFinalCF0Magic,
            cf0State,
            finalCF0Static,
            tables
        )
        val ab0 = vm638840(
            builder633fa8NullPostFinalBD0Magic,
            bd0State,
            finalBD0Static,
            tables
        )
        blocks4080.addAll(vm638840(
            builder633fa8NullPostBlock4080Magic,
            e10,
            e10,
            tables
        ).toList())
        blocks3f40.addAll(vm638840(
            builder633fa8NullPostBlock3f40Magic,
            ab0,
            ab0,
            tables
        ).toList())
    }

    return Builder633fa8NullPostAcceptResult(
        blocks4080 = blocks4080.toByteArray(),
        blocks3f40 = blocks3f40.toByteArray()
    )
}

internal fun builder633fa8NullPreludeSourceFromPostAccept(blocks4080: ByteArray, blocks3f40: ByteArray): ByteArray {
    if (blocks4080.size != builder633fa8NullSeedBlocksBytes) {
        throw FirstPairSourceSliceError.invalid633fa8NullPostAcceptBlocksLength("4080", blocks4080.size)
    }
    if (blocks3f40.size != builder633fa8NullSeedBlocksBytes) {
        throw FirstPairSourceSliceError.invalid633fa8NullPostAcceptBlocksLength("3f40", blocks3f40.size)
    }

    val tables = FirstPairConfig.tables
    val bytes4080 = blocks4080
    val first4080Block = bytes4080.copyOfRange(0, builder633fa8NullSeedBlockBytes)
    val first4080 = vm638840(
        builder633fa8NullPreludeFirst4080Magic,
        first4080Block,
        first4080Block,
        tables
    )
    val rest4080 = ArrayList<Byte>()
    for (index in 1 until builder633fa8ScalarWordCount) {
        val start = index * builder633fa8NullSeedBlockBytes
        val block = bytes4080.copyOfRange(start, start + builder633fa8NullSeedBlockBytes)
        rest4080.addAll(vm638840(
            builder633fa8NullPreludeRest4080Magic,
            block,
            block,
            tables
        ).toList())
    }
    val stitched4080 = stitch633fa8NullPrelude11A(
        first4080,
        rest4080.toByteArray()
    )
    val bd0 = vm638840(
        builder633fa8NullPreludeBD0Magic,
        stitched4080,
        stitched4080,
        tables
    )

    val bytes3f40 = blocks3f40
    val first3f40Block = bytes3f40.copyOfRange(0, builder633fa8NullSeedBlockBytes)
    val first3f40 = vm638840(
        builder633fa8NullPreludeFirst3f40Magic,
        first3f40Block,
        first3f40Block,
        tables
    )
    val rest3f40 = ArrayList<Byte>()
    for (index in 1 until builder633fa8ScalarWordCount) {
        val start = index * builder633fa8NullSeedBlockBytes
        val block = bytes3f40.copyOfRange(start, start + builder633fa8NullSeedBlockBytes)
        rest3f40.addAll(vm638840(
            builder633fa8NullPreludeRest3f40Magic,
            block,
            block,
            tables
        ).toList())
    }
    val stitched3f40 = stitch633fa8NullPrelude11A(
        first3f40,
        rest3f40.toByteArray()
    )
    val ab0 = vm638840(
        builder633fa8NullPreludeAB0Magic,
        stitched3f40,
        stitched3f40,
        tables
    )
    val stage4080 = vm638840(
        builder633fa8NullPreludeStage4080Magic,
        bd0,
        ab0,
        tables
    )
    val f40 = vm638840(
        builder633fa8NullPreludeF40Magic,
        stage4080,
        ab0,
        tables
    )
    return vm638840(
        builder633fa8NullPreludeSourceMagic,
        f40,
        f40,
        tables
    )
}

internal fun builder633fa8NullPreludeSourceFromEntropy(entropy11A: ByteArray): ByteArray {
    val sources = builder633fa8NullEntrySourcesFromInvariantEntry()
    val initial = builder633fa8NullInitialFromEntropy(
        entropy11A,
        sources.prologueSource
    )
    val loop = builder633fa8NullFirstLoopFromBlocks(initial.seedBlocks)
    val acceptance = builder633fa8NullScheduleAcceptance(
        loop.scheduleWords,
        sources.check1SourceWords,
        sources.check2SourceWords
    )
    if (!(acceptance.firstOK && acceptance.secondOK)) {
        throw FirstPairSourceSliceError.rejected633fa8NullEntropy
    }

    val postAccept = builder633fa8NullPostAcceptBlocks(loop.scheduleWords)
    return builder633fa8NullPreludeSourceFromPostAccept(
        postAccept.blocks4080,
        postAccept.blocks3f40
    )
}

internal fun builder633fa8NullScalarWindowFromEntropy(entropy11A: ByteArray): ByteArray {
    val preludeSource = builder633fa8NullPreludeSourceFromEntropy(entropy11A)
    return builder633fa8ScalarWindowFromPreludeSource(preludeSource)
}

internal fun builder633fa8NullPublicEntrySourceFromEntropy(entropy11A: ByteArray): ByteArray {
    val preludeSource = builder633fa8NullPreludeSourceFromEntropy(entropy11A)
    val scalar = builder633fa8ScalarWindowFromPreludeSource(preludeSource)
    return preludeSource.concat(scalar.copyOfRange(0, 0x10))
}

internal fun builderProcess2P5PublicScalarWindowFromEntropy(entropy11A: ByteArray): ByteArray {
    val qwords = builderProcess2P5PublicScalarQwordsFromEntropy(entropy11A)
    val words = builderProcess2P5PublicScalarWordsFromQwords(qwords)
    return builderProcess2P5PublicScalarWindowFromWords(words)
}

internal fun builderProcess2P5PublicKey65FromEntropy(entropy11A: ByteArray): ByteArray {
    val scalarWindow = builderProcess2P5PublicScalarWindowFromEntropy(entropy11A)
    val fixedPoint = process2P5PublicFixedPointBE.copyOfRange(1, process2P5PublicFixedPointBE.size)
    val outputs = P256ScalarMultiplier.builder5bcf98P256Outputs(
        scalarWindow,
        fixedPoint
    )
    val xBE = outputs.xOutput70.copyOfRange(0, 32).reversedArray()
    val yBE = outputs.yOutput70.copyOfRange(0, 32).reversedArray()
    return byteArrayOf(0x04).concat(xBE).concat(yBE)
}

internal fun builder633fa8NullScalarWindowFromEntropySource(maxAttempts: Int = 64, entropySource: (Int) -> ByteArray): Builder633fa8NullScalarResult {
    if (!(maxAttempts > 0)) {
        throw FirstPairSourceSliceError.invalid633fa8NullMaxAttempts(maxAttempts)
    }

    for (attempt in 1..maxAttempts) {
        val entropy = entropySource(builder633fa8NullEntropyBytes)
        try {
            val scalar = builder633fa8NullScalarWindowFromEntropy(entropy)
            return Builder633fa8NullScalarResult(
                scalar,
                entropy,
                attempt
            )
        } catch (error: FirstPairSourceSliceError) {
            if (error == FirstPairSourceSliceError.rejected633fa8NullEntropy) {
                continue
            }
            throw error
        }
    }
    throw FirstPairSourceSliceError.rejected633fa8NullEntropyAfterAttempts(maxAttempts)
}

internal fun builder633fa8TailQwordsFromSources(words3ab0: List<UInt>, words3120: List<UInt>, words2dfc: List<UInt>, seed3110: ULong): List<ULong> {
    if (words3ab0.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalid633fa8TailWordCount("3ab0", words3ab0.size)
    }
    if (words3120.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalid633fa8TailWordCount("3120", words3120.size)
    }
    if (words2dfc.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalid633fa8TailWordCount("2dfc", words2dfc.size)
    }

    val tables = FirstPairConfig.tables
    val stack = ByteArray(builder633fa8TailStackBytes)

    var word: UInt = words3ab0[0] * 0xc365675bU + 0xe8f087b3U
    var value: ULong = builder633fa8TailStreamU64(
        word,
        0x39629fb00ae1a583UL,
        0xc87e38ff2ae3bb2dUL,
        builder633fa8TailAFoldTable,
        0xd0779b0b00000000UL,
        0x7168c55d8932925fUL,
        0x9f10057a9662ab2dUL,
        tables
    )
    writeUInt64LE(value, stack, 0x3f40)
    writeUInt64LE(value, stack, 0x3cf0)
    var prefix: ULong = value
    for (index in 1 until builder633fa8ScalarWordCount) {
        word = u32Affine633fa8Tail(words3ab0[index], index, 0x11fd08, 0x11fd28, tables)
        word = word * 0x6ebad499U + 0x8b060038U
        value = builder633fa8TailStreamU64(
            word,
            0x39629fb00ae1a583UL,
            0xc87e38ff2ae3bb2dUL,
            builder633fa8TailAFoldTable,
            0xd0779b0b00000000UL,
            0x7168c55d8932925fUL,
            0x9f10057a9662ab2dUL,
            tables
        )
        writeUInt64LE(value, stack, 0x3f40 + index * 8)
        prefix = prefix + value
        writeUInt64LE(prefix, stack, 0x3cf0 + index * 8)
    }

    word = words3120[0] * 0x21753b73U + 0x9f972fa4U
    value = builder633fa8TailStreamU64(
        word,
        0xc16bd9358bd641f1UL,
        0xdbd59c6303e46229UL,
        builder633fa8TailBFoldTable,
        0xe919ac4d00000000UL,
        0x0eb018d832b73e83UL,
        0x1f4e35decd254a8bUL,
        tables
    )
    writeUInt64LE(value, stack, 0x3e10)
    writeUInt64LE(value, stack, 0x3bd0)
    prefix = value
    for (index in 1 until builder633fa8ScalarWordCount) {
        word = u32Affine633fa8Tail(words3120[index], index, 0x112528, 0x112548, tables)
        word = word * 0x740d5673U + 0xf3b4a3bcU
        value = builder633fa8TailStreamU64(
            word,
            0xc16bd9358bd641f1UL,
            0xdbd59c6303e46229UL,
            builder633fa8TailBFoldTable,
            0xe919ac4d00000000UL,
            0x0eb018d832b73e83UL,
            0x1f4e35decd254a8bUL,
            tables
        )
        writeUInt64LE(value, stack, 0x3e10 + index * 8)
        prefix = prefix + value
        writeUInt64LE(prefix, stack, 0x3bd0 + index * 8)
    }

    convolutionWorkspace633fa8Tail(
        stack,
        0x3e10,
        0x3bd0,
        0x3f40,
        0x3cf0,
        0x4080,
        builder633fa8ScalarWordCount,
        42,
        0x88edcb9fcc5a504fUL,
        0xc50c4cfe6b90cc32UL,
        0xb280f1fcde620b25UL,
        0x24cc8b7736fa66cfUL,
        0x0512ce98be108b3a5UL,
        0xe8cb5b6d2f40c331UL,
        0xeb47abb56d203e7dUL
    )

    for (index in words2dfc.indices) {
        val sourceWord = words2dfc[index]
        word = u32Affine633fa8Tail(sourceWord, index, 0x11b268, 0x120e48, tables)
        word = word * 0x4890e04fU + 0xc2cec971U
        value = builder633fa8TailStreamU64(
            word,
            0xe2d3ea4512d167e7UL,
            0x00a7c876b324afde01UL,
            builder633fa8TailCFoldTable,
            0xc4e79ba300000000UL,
            0xf5ea48539d50faebUL,
            0x37ffe0ce46814927UL,
            tables
        )
        writeUInt64LE(value, stack, 0x3f40 + index * 8)
    }

    val q = (0 until builder633fa8ScalarWordCount).map { index: Int -> readUInt64LE(stack, 0x3f40 + index * 8) }
    var x30: ULong = readUInt64LE(stack, 0x4080)
    val seedA: ULong = seed3110 * 0xac33be2f37df9899UL + 0xf586b9c725fc2655UL
    val seedB: ULong = seed3110 * 0xc460e481253db509UL + 0x5642aeb8585a52ebUL

    for (byteOffset in 0 until 0xb0 step 8) {
        val position = 0x4080 + byteOffset
        var x10: ULong = x30 * seedA + seedB
        var folded: ULong = fold633fa8Tail(
            x10 * 0x09883223fa4660edUL + 0x2d97cba42b4b302fUL,
            builder633fa8TailDFoldTable,
            7,
            tables
        )
        x10 = x10 * 0x9c92333d4c3638c9UL +
            folded * 0x718df58330000000UL +
            0xdb9c322f9570a279UL

        val x7: ULong = x10 * 0x06192264fc57feafUL + 0xe244b4f2265375bfUL
        val x21: ULong = x10 * 0x3fd0fde8a99b1d3cUL + 0x7bd7d5be27b1d17cUL
        val x3: ULong = x7 * q[8] + x21
        x30 = x7 * q[0] + x21 + x30
        val x4: ULong = x7 * q[9] + x21

        folded = fold633fa8Tail(
            x30 * 0xa5ba351ba23facf5UL + 0x0720c6fcb580eff2UL,
            builder633fa8TailEFoldTable,
            7,
            tables
        )
        var x12: ULong = folded * 0x9549f71510a8f0e7UL + 0x2d3bf7a5dd39f0abUL
        folded = fold633fa8Tail(
            x12 * 0xead4735c0bc5924dUL + 0x73beb11d9159837cUL,
            builder633fa8TailFFoldTable,
            9,
            tables
        )
        var x8Mix: ULong = x12 * 0x7794ebcd6781608dUL + folded * 0xafc58bf000000000UL

        val old08 = readUInt64LE(stack, position + 0x08)
        val old10 = readUInt64LE(stack, position + 0x10)
        var x13: ULong = x7 * q[1] + x21 + old08
        val x11: ULong = x7 * q[2] + x21 + old10
        writeUInt64LE(x30, stack, position)
        writeUInt64LE(x13, stack, position + 0x08)

        val acc13: ULong = x7 * q[13] + x21
        val old18 = readUInt64LE(stack, position + 0x18)
        val old20 = readUInt64LE(stack, position + 0x20)
        var x14: ULong = x7 * q[3] + x21 + old18
        val x15: ULong = x7 * q[4] + x21 + old20

        val old28 = readUInt64LE(stack, position + 0x28)
        val old30 = readUInt64LE(stack, position + 0x30)
        val x16: ULong = x7 * q[5] + x21 + old28
        var x17: ULong = x7 * q[6] + x21 + old30
        writeUInt64LE(x14, stack, position + 0x18)
        writeUInt64LE(x15, stack, position + 0x20)

        val old38 = readUInt64LE(stack, position + 0x38)
        val old40 = readUInt64LE(stack, position + 0x40)
        var acc15: ULong = x7 * q[14] + x21
        val x1: ULong = x7 * q[7] + x21 + old38
        val x16b: ULong = x3 + old40
        writeUInt64LE(x16, stack, position + 0x28)
        writeUInt64LE(x17, stack, position + 0x30)

        val old48 = readUInt64LE(stack, position + 0x48)
        val old50 = readUInt64LE(stack, position + 0x50)
        val acc0: ULong = x7 * q[15] + x21
        x14 = x4 + old48
        val acc2: ULong = x7 * q[10] + x21
        x17 = acc2 + old50
        writeUInt64LE(x1, stack, position + 0x38)
        writeUInt64LE(x16b, stack, position + 0x40)
        writeUInt64LE(x14, stack, position + 0x48)
        writeUInt64LE(x17, stack, position + 0x50)

        val old58 = readUInt64LE(stack, position + 0x58)
        val old60 = readUInt64LE(stack, position + 0x60)
        val acc12: ULong = x7 * q[11] + x21
        val acc30: ULong = x7 * q[12] + x21
        val acc14: ULong = x7 * q[16] + x21
        x12 = acc12 + old58
        x17 = acc30 + old60
        val acc1: ULong = x7 * q[17] + x21
        writeUInt64LE(x12, stack, position + 0x58)
        writeUInt64LE(x17, stack, position + 0x60)

        val old68 = readUInt64LE(stack, position + 0x68)
        val old70 = readUInt64LE(stack, position + 0x70)
        x13 = acc13 + old68
        x12 = acc15 + old70
        val acc16: ULong = x7 * q[18] + x21
        writeUInt64LE(x13, stack, position + 0x68)
        writeUInt64LE(x12, stack, position + 0x70)

        val old78 = readUInt64LE(stack, position + 0x78)
        val old80 = readUInt64LE(stack, position + 0x80)
        acc15 = x7 * q[19] + x21
        x12 = acc0 + old78
        x13 = acc14 + old80
        writeUInt64LE(x12, stack, position + 0x78)
        writeUInt64LE(x13, stack, position + 0x80)

        x8Mix = x8Mix * 0x56c495ec086d9247UL + readUInt64LE(stack, position + 0x08)

        val old88 = readUInt64LE(stack, position + 0x88)
        val old90 = readUInt64LE(stack, position + 0x90)
        x12 = acc1 + old88
        x14 = acc16 + old90
        writeUInt64LE(x12, stack, position + 0x88)
        writeUInt64LE(x14, stack, position + 0x90)

        val old98 = readUInt64LE(stack, position + 0x98)
        x12 = acc15 + old98
        writeUInt64LE(x12, stack, position + 0x98)

        x30 = x8Mix + 0xc387faf5615fb2e3UL
        writeUInt64LE(x30, stack, position + 0x08)
        writeUInt64LE(x11, stack, position + 0x10)
    }

    return (0 until builder633fa8ScalarWordCount).map { index: Int -> readUInt64LE(stack, 0x4130 + index * 8) }
}

internal fun builder633fa8E10WordsFromTailQwords(tailQwords: List<ULong>): List<UInt> {
    if (tailQwords.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalid633fa8QwordCount(tailQwords.size)
    }
    val tables = FirstPairConfig.tables
    var carry: ULong = builder633fa8E10InitialCarry
    val out = ArrayList<UInt>()
    for (index in tailQwords.indices) {
        val qword = tailQwords[index]
        val source: ULong = qword * builder633fa8E10QwordMul
        carry = carry * builder633fa8E10CarryMul + source
        carry = carry + builder633fa8E10CarryAdd

        val foldedSeed: ULong = carry * builder633fa8E10FoldSeedMul + builder633fa8E10FoldSeedAdd
        var word: UInt = (carry and 0xffff_ffffUL).toUInt() * builder633fa8E10WordMul
        val folded7: ULong = fold633fa8Tail(
            foldedSeed,
            builder633fa8E10TailFoldTable,
            7,
            tables
        )
        word = word + ((folded7 and 0x0fUL).toUInt() shl 28) + builder633fa8E10WordAdd
        val folded16: ULong = fold633fa8Tail(
            folded7,
            builder633fa8E10TailFoldTable,
            9,
            tables
        )

        val tableOffset = (index * 4) and 0x1c
        word = word * u32TableWord63c278(builder633fa8E10TailMulTable + tableOffset, tables) +
            u32TableWord63c278(builder633fa8E10TailAddTable + tableOffset, tables)
        out.add(word)

        carry = folded7 * builder633fa8E10NextCarryFolded7Mul +
            folded16 * builder633fa8E10NextCarryFolded16Mul +
            builder633fa8E10NextCarryAdd
    }
    return out
}

internal fun builder633fa8ScalarWindowFromE10Words(words: List<UInt>): ByteArray {
    if (words.size != builder633fa8ScalarWordCount) {
        throw FirstPairSourceSliceError.invalid633fa8WordCount(words.size)
    }
    val tables = FirstPairConfig.tables
    val out = ByteArray(builder633fa8ScalarWindowBytes)
    var accumulator: ULong = 0UL
    var bitCount = 0
    var outIndex = 0
    for (index in words.indices) {
        val word = words[index]
        val tableOffset = (index * 4) and 0x1c
        val packed: ULong = word.toULong() *
            u32TableWord63c278(
                builder633fa8ScalarPackMulTable + tableOffset,
                tables
            ).toULong() +
            u32TableWord63c278(
                builder633fa8ScalarPackAddTable + tableOffset,
                tables
            ).toULong()
        var value = (packed and 0xffff_ffffUL).toUInt()
        value = value * builder633fa8ScalarPackMul + builder633fa8ScalarPackAdd
        accumulator = accumulator xor (value.toULong() shl bitCount)
        bitCount += 28
        while (bitCount > 16 && outIndex < builder633fa8ScalarWindowBytes - 1) {
            out[outIndex] = (accumulator and 0xffUL).toByte()
            accumulator = accumulator shr 8
            outIndex += 1
            bitCount -= 8
        }
    }
    if (bitCount >= 1 && outIndex < builder633fa8ScalarWindowBytes - 1) {
        out[outIndex] = (accumulator and 0xffUL).toByte()
    }
    return out
}

internal fun builder6388f0StreamStart642f60Inputs(out0Seed: ByteArray, out1Seed: ByteArray, x2Source: ByteArray? = null): Builder6388f0Next642f60Inputs {
    val resolvedX2 = x2Source ?: streamStart642f60X2Source
    if (resolvedX2.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort(
            "6388f0 stream-start 642f60 x2 source",
            resolvedX2.size,
            builder63c278VectorBytes
        )
    }
    return Builder6388f0Next642f60Inputs(
        builder6388f0StreamStart642f60X0FromOut0Seed(out0Seed),
        builder6388f0StreamStart642f60X1FromOut1Seed(out1Seed),
        resolvedX2.copyOfRange(0, builder63c278VectorBytes)
    )
}

internal fun builder6388f0FirstPair642f60StreamStarts(row0Out0Seed: ByteArray, row0Out1Seed: ByteArray, row59Out0Seed: ByteArray, row59Out1Seed: ByteArray, x2Source: ByteArray? = null): Builder6388f0FirstPair642f60Starts {
    return Builder6388f0FirstPair642f60Starts(
        builder6388f0StreamStart642f60Inputs(
            row0Out0Seed,
            row0Out1Seed,
            x2Source
        ),
        builder6388f0StreamStart642f60Inputs(
            row59Out0Seed,
            row59Out1Seed,
            x2Source
        )
    )
}

internal fun builder6388f0FirstPair642f60StreamStarts(seeds: Builder6388f0FirstPairStreamSeeds, x2Source: ByteArray? = null): Builder6388f0FirstPair642f60Starts {
    return builder6388f0FirstPair642f60StreamStarts(
        seeds.row0Out0,
        seeds.row0Out1,
        seeds.row59Out0,
        seeds.row59Out1,
        x2Source
    )
}

internal fun builder6388f0SharedContextFromBundle(): ByteArray {
    val context = RuntimeTable.FIRST_PAIR_6388F0_SHARED_CONTEXT.load(
        FirstPairConfig.tablesSource ?: error(
            "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
        )
    )
    if (context.size != builder6388f0SharedContextLength) {
        throw FirstPairSourceSliceError.invalidTableSize(
            RuntimeTable.FIRST_PAIR_6388F0_SHARED_CONTEXT.fileName,
            context.size
        )
    }
    return context
}

internal fun builder6388f0CallerLoopTablesFromBundle(): Builder6388f0CallerLoopTables {
    val interleaved = RuntimeTable.FIRST_PAIR_6388F0_CALLER_LOOP_INTERLEAVED.load(
        FirstPairConfig.tablesSource ?: error(
            "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
        )
    )
    if (interleaved.size != builder6388f0CallerLoopInterleavedLength) {
        throw FirstPairSourceSliceError.invalidTableSize(
            RuntimeTable.FIRST_PAIR_6388F0_CALLER_LOOP_INTERLEAVED.fileName,
            interleaved.size
        )
    }

    val first = ArrayList<Byte>()
    val second = ArrayList<Byte>()
    for (row in 0 until builder6388f0CallerLoopTableRows) {
        val rowOffset = row * builder6388f0CallerLoopInterleavedRowBytes
        first.addAll(interleaved.copyOfRange(rowOffset, rowOffset + builder6388f0CallerLoopRowBytes).toList())
        second.addAll(
            interleaved.copyOfRange(
                rowOffset + builder6388f0CallerLoopRowBytes,
                rowOffset + builder6388f0CallerLoopInterleavedRowBytes
            ).toList()
        )
    }
    return Builder6388f0CallerLoopTables(first = first.toByteArray(), second = second.toByteArray())
}

internal fun builder6388f0CallerContextFromLoopTables(loopTables: Builder6388f0CallerLoopTables): ByteArray {
    if (loopTables.first.size < builder6388f0CallerLoopTableBytes) {
        throw FirstPairSourceSliceError.sourceTooShort(
            "6388f0 caller loop table 1",
            loopTables.first.size,
            builder6388f0CallerLoopTableBytes
        )
    }
    if (loopTables.second.size < builder6388f0CallerLoopTableBytes) {
        throw FirstPairSourceSliceError.sourceTooShort(
            "6388f0 caller loop table 2",
            loopTables.second.size,
            builder6388f0CallerLoopTableBytes
        )
    }

    val shared = builder6388f0SharedContextFromBundle()
    val context = ByteArray(builder6388f0CallerContextLength)
    replace(context, 0, shared)
    replace(
        context,
        builder6388f0CallerLoopTable1ContextOffset,
        loopTables.first.copyOfRange(0, builder6388f0CallerLoopTableBytes)
    )
    replace(
        context,
        builder6388f0CallerLoopTable2ContextOffset,
        loopTables.second.copyOfRange(0, builder6388f0CallerLoopTableBytes)
    )
    return context
}

internal fun builder6388f0CallerContextFromBundle(): ByteArray {
    return builder6388f0CallerContextFromLoopTables(builder6388f0CallerLoopTablesFromBundle())
}

internal fun builder642f60StageSP2A8WordsFromX1(x1Source: ByteArray): List<UInt> {
    if (x1Source.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 x1 source", x1Source.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x1Source
    var state: UInt = 0x373c5287U
    val out = ArrayList<UInt>()

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val word = readUInt32LE(bytes, index * 4)
        val mixed = u32TableAffine63c278(
            word,
            builder642f60SP2A8X1MulTable + tableOffset,
            builder642f60SP2A8X1AddTable + tableOffset,
            tables
        ) * 0x3c4be1d6U
        state = state * 0x92c1f72bU + mixed + 0xb77cdf91U

        var folded = state * 0x52c0ee2fU + 0xaec98dccU
        val sideBase = state * 0x58fd5601U
        folded = fold32ByNibbles63c278(folded, builder642f60SP2A8FoldTable, 7, tables)
        val side = sideBase + (folded shl 28) + 0x79c97500U
        val folded8 = fold32ByNibbles63c278(folded, builder642f60SP2A8FoldTable, 1, tables)

        val output = u32TableAffine63c278(
            side,
            builder642f60SP2A8OutMulTable + tableOffset,
            builder642f60SP2A8OutAddTable + tableOffset,
            tables
        )
        out.add(output)
        state = folded * 0x01d6d2edU + folded8 * 0xe292d130U + 0x57678c8fU
    }
    return out
}

internal fun builder642f60StageSP300WordsFrom64bd0cOutput(output: ByteArray): List<UInt> {
    return builder642f60AffineWordsFrom64bd0cOutput(
        output,
        builder642f60SP300MulTable,
        builder642f60SP300AddTable,
        "642f60 first 64bd0c output"
    )
}

internal fun builder642f60StageSP250WordsFrom64bd0cOutput(output: ByteArray): List<UInt> {
    return builder642f60AffineWordsFrom64bd0cOutput(
        output,
        builder642f60SP250MulTable,
        builder642f60SP250AddTable,
        "642f60 second 64bd0c output"
    )
}

internal fun builder642f60StageSP148WordsFrom64bd0cOutput(output: ByteArray): List<UInt> {
    return builder642f60AffineWordsFrom64bd0cOutput(
        output,
        builder642f60SP148MulTable,
        builder642f60SP148AddTable,
        "642f60 third 64bd0c output"
    )
}

internal fun builder642f60StageSPF0WordsFrom64bd0cOutput(output: ByteArray): List<UInt> {
    return builder642f60AffineWordsFrom64bd0cOutput(
        output,
        builder642f60SPF0MulTable,
        builder642f60SPF0AddTable,
        "642f60 fourth 64bd0c output"
    )
}

internal fun builder642f60StageSP1A0WordsFrom64bd0cOutput(output: ByteArray): List<UInt> {
    return builder642f60AffineWordsFrom64bd0cOutput(
        output,
        builder642f60SP1A0MulTable,
        builder642f60SP1A0AddTable,
        "642f60 fifth 64bd0c output"
    )
}

internal fun builder642f60StageSP1F8WordsFromX0(x0Source: ByteArray): List<UInt> {
    if (x0Source.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 x0 source", x0Source.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x0Source
    var state: UInt = 0x27b40eb7U
    val out = ArrayList<UInt>()

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val word = readUInt32LE(bytes, index * 4)
        val mixed = u32TableAffine63c278(
            word,
            builder642f60SP1F8X0MulTable + tableOffset,
            builder642f60SP1F8X0AddTable + tableOffset,
            tables
        ) * 0x8c0bfb6eU
        state = state * 0xcfb36435U + mixed + 0x11d2681dU

        var folded = state * 0xc337e20fU + 0x69960635U
        folded = fold32ByNibbles63c278(folded, builder642f60SP1F8FoldTable, 7, tables)
        val side = state * 0x37e76a4dU + folded * 0xd0000000U + 0x0a2a2ce9U
        val folded8 = fold32ByNibbles63c278(folded, builder642f60SP1F8FoldTable, 1, tables)

        val output = u32TableAffine63c278(
            side,
            builder642f60SP1F8OutMulTable + tableOffset,
            builder642f60SP1F8OutAddTable + tableOffset,
            tables
        )
        out.add(output)
        state = folded * 0x01e08913U + folded8 * 0xe1f76ed0U + 0xe153bedeU
    }
    return out
}

internal fun builder642f60First64bd0cWorkspaceFromX1(x1Source: ByteArray, sp2a8Words: List<UInt>): ByteArray {
    if (x1Source.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 x1 source", x1Source.size, builder63c278VectorBytes)
    }
    if (sp2a8Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp2a8Words.size)
    }
    val tables = FirstPairConfig.tables
    val bytes = x1Source
    val aWords = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60FirstAWord(readUInt32LE(bytes, index * 4), index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60FirstBWord(sp2a8Words[index], index, tables)
    }
    return convolutionWorkspaceU64(
        aWords,
        bWords,
        0xc69bed71f29f125aUL,
        0x90f419f6ac783668UL,
        0x4cb8f06bf0049b7dUL,
        0x0f7eac37b6812618UL,
        0xd1388a4d4ecb84f3UL,
        0xdacc0c3ac7084aadUL,
        0x094d3bfe92d4e136UL
    )
}

internal fun builder642f60Second64bd0cWorkspace(sp1f8Words: List<UInt>, sp300Words: List<UInt>): ByteArray {
    if (sp1f8Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp1f8Words.size)
    }
    if (sp300Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp300Words.size)
    }
    val tables = FirstPairConfig.tables
    val aWords = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60SecondAWord(sp1f8Words[index], index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60SecondBWord(sp300Words[index], index, tables)
    }
    return convolutionWorkspaceU64(
        aWords,
        bWords,
        0x65de471500bb3121UL,
        0x5b751607ca3bf450UL,
        0xf90d1f20daf847f7UL,
        0x3bd2bf8830ac06c7UL,
        0x8510f1581a89dd50UL,
        0x95f22cc42a8e1323UL,
        0x54b290ac63e72185UL
    )
}

internal fun builder642f60Third64bd0cWorkspaceFromX2(x2Source: ByteArray): ByteArray {
    if (x2Source.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 x2 source", x2Source.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x2Source
    val sourceWords = (0 until builder63c278VectorWords).map { index: Int ->
        readUInt32LE(bytes, index * 4)
    }
    val aWords = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60ThirdAWord(sourceWords[index], index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60ThirdBWord(sourceWords[index], index, tables)
    }
    return convolutionWorkspaceU64(
        aWords,
        bWords,
        0x5b1e432b74fd20f9UL,
        0xd6a9de8138afb1c4UL,
        0x491764cf27f996a7UL,
        0x5f259b9e6d3d894fUL,
        0x0634d81d5a7a1464UL,
        0x81b9bc3ed86899dbUL,
        0x7f3bdcb4320a4605UL
    )
}

internal fun builder642f60Fourth64bd0cWorkspace(sp148Words: List<UInt>): ByteArray {
    if (sp148Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp148Words.size)
    }
    val tables = FirstPairConfig.tables
    val aWords = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60FourthAWord(sp148Words[index], index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60FourthBWord(sp148Words[index], index, tables)
    }
    return convolutionWorkspaceU64(
        aWords,
        bWords,
        0xca87452057c62cf5UL,
        0x33f7ea217636a2b0UL,
        0x25c9902b9655a323UL,
        0x5486edf9ebf09668UL,
        0x9ae2908cd350c4caUL,
        0xc84690dc7332d8bfUL,
        0x2381c41e82ce093dUL
    )
}

internal fun builder642f60MidStageSPA90WordsFromX0(x0Source: ByteArray): List<ULong> {
    if (x0Source.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 x0 source", x0Source.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x0Source
    val out = ArrayList<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        val affine = u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder642f60MidAX0MulTable,
            builder642f60MidAX0AddTable,
            tables
        )
        val word = affine * 0x3c1bc237U + 0xd6718b75U
        val folded = fold63c278(
            word.toULong() * 0x4570116131d5875bUL + 0x4ca880cd5cde550eUL,
            builder642f60MidAFoldTable,
            8,
            tables
        )
        out.add(
            word.toULong() * 0xc7860ccbc266aa3dUL +
                folded * 0xbffb9fb900000000UL +
                0x0bfdc66a47f4cadfUL
        )
    }
    return out
}

internal fun builder642f60MidStageSP40WordsFromSPA90(spa90Words: List<ULong>): List<UInt> {
    if (spa90Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(spa90Words.size)
    }
    val tables = FirstPairConfig.tables
    var carry: ULong = 0xd2263697af87081fUL
    val out = ArrayList<UInt>()

    for (index in 0 until builder64bd0cWorkspaceWords) {
        var low = maxOf(0, index - 21)
        var high = minOf(index, 21)
        var accum: ULong = 0x4ca9f4732c4678daUL
        while (high > low) {
            val highWord = spa90Words[high]
            val lowWord = spa90Words[low]
            val left = highWord * 0xb2358691225cfc35UL + 0xdf9a7386fc929cb6UL
            accum = left * lowWord +
                highWord * 0xdf9a7386fc929cb6UL +
                accum +
                0xe1240ffc79c75054UL
            high -= 1
            low += 1
        }

        val mixed = accum * 0x7047539999fd499eUL
        carry = carry * 0x7d2900791bc15f17UL
        val state: ULong
        if (high == low) {
            val center = spa90Words[high]
            val centerTerm = center * 0xe6b5f1d6d357e2dbUL + 0x7888b2a9570a9e54UL
            state = centerTerm * center +
                mixed +
                carry +
                0xe771da0c03bd224cUL
        } else {
            state = mixed +
                carry +
                0xb928102d38c55e60UL
        }

        val foldedInput = state * 0x93dfdd33afa41fcbUL + 0xb5028820475851e2UL
        val folded7 = fold63c278(
            foldedInput,
            builder642f60MidSP40FoldTable,
            7,
            tables
        )
        val folded16 = fold63c278(
            folded7,
            builder642f60MidSP40FoldTable,
            9,
            tables
        )
        val side = (state.toUInt() * 0x6d4b301fU) +
            (folded7.toUInt() * 0x30000000U) +
            0xb22b53c3U
        carry = folded7 * 0x33b21893aa33e715UL +
            folded16 * 0x5cc18eb000000000UL +
            0xd1af5299bbb3ce82UL

        val tableOffset = (index * 4) and 0x1c
        out.add(
            u32TableAffine63c278(
                side,
                builder642f60MidSP40OutMulTable + tableOffset,
                builder642f60MidSP40OutAddTable + tableOffset,
                tables
            )
        )
    }
    return out
}

internal fun builder642f60MidStageStreamsFromContextSPF0(contextSource: ByteArray, spf0Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (contextSource.size < 0x100) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 context source", contextSource.size, 0x100)
    }
    if (spf0Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(spf0Words.size)
    }
    val tables = FirstPairConfig.tables
    val bytes = contextSource
    val contextWords = (0 until builder63c278VectorWords).map { index: Int ->
        readUInt32LE(bytes, 0xa8 + index * 4)
    }
    val spa90Words = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60MidContextStreamWord(contextWords[index], index, tables)
    }
    val sp880Words = (0 until builder63c278VectorWords).map { index: Int ->
        builder642f60MidSPF0StreamWord(spf0Words[index], index, tables)
    }
    return FpQuad(
        spa90Words,
        prefixSumsU64(spa90Words),
        sp880Words,
        prefixSumsU64(sp880Words)
    )
}

internal fun builder642f60MidStageSP670Words(spa90Words: List<ULong>, sp510Prefix: List<ULong>, sp880Words: List<ULong>, sp9e0Prefix: List<ULong>): List<ULong> {
    if (spa90Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(spa90Words.size)
    }
    if (sp510Prefix.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp510Prefix.size)
    }
    if (sp880Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp880Words.size)
    }
    if (sp9e0Prefix.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp9e0Prefix.size)
    }

    val out = ArrayList<ULong>()
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - 21)
        val end = minOf(index, 21)
        var productSum: ULong = 0UL
        if (start <= end) {
            for (position in start..end) {
                productSum = productSum + spa90Words[position] * sp880Words[index - position]
            }
        }
        val span: ULong = if (start <= end) (end - start + 1).toULong() else 0UL
        val sumA = rangeSumFromPrefix(sp510Prefix, start, end)
        val sumB = rangeSumFromPrefix(sp9e0Prefix, index - end, index - start)
        val mixed = span * 0x268d985caf171be0UL +
            0x91891dd268ac7a45UL +
            productSum * 0xbc643695604233c9UL +
            sumA * 0x55d047a51fd1fdd0UL +
            sumB * 0x268318c9a7c7fd06UL
        out.add(mixed * 0xc36e55bcdc7360d9UL + 0x20aeeecb67e4d8eeUL)
    }
    return out
}

internal fun builder642f60MidStageSPA90SP880FromSP40(sp40Words: List<UInt>): FpTriple<List<ULong>, List<ULong>, ULong> {
    if (sp40Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp40Words.size)
    }
    val tables = FirstPairConfig.tables
    val spa90Words = (0 until builder64bd0cWorkspaceWords).map { index: Int ->
        builder642f60MidSP40BWord(sp40Words[index], index, tables)
    }
    return FpTriple(
        spa90Words,
        prefixSumsU64(spa90Words),
        0x9b3fe2a5f2a431c6UL
    )
}

internal fun builder642f60MidStageStaticSP9E0SP7D0(sideInit: ULong): FpPair<List<ULong>, List<ULong>> {
    val tables = FirstPairConfig.tables
    val sp9e0Words = ArrayList<ULong>()
    sp9e0Words.add(sideInit)
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val source = foldTableU32Word63c278(builder642f60MidStaticSrcTable + index * 4, tables)
        val multiplier = u32TableWord63c278(builder642f60MidStaticMulTable + tableOffset, tables)
        val addend = u32TableWord63c278(builder642f60MidStaticAddTable + tableOffset, tables)
        val word = (source * multiplier + addend) * 0x8e3923f3U + 0xdcf87258U
        val folded = fold63c278(
            word.toULong() * 0x76e0c10d644166b9UL + 0x9f48a8b2fd92040dUL,
            builder642f60MidStaticFoldTable,
            8,
            tables
        )
        sp9e0Words.add(
            word.toULong() * 0x5a02cb2433277ab9UL +
                folded * 0xedf34bff00000000UL +
                0x6f59c0117d1d1775UL
        )
    }
    return FpPair(
        sp9e0Words,
        prefixSumsU64(sp9e0Words)
    )
}

internal fun builder642f60MidStageSP510Words(spa90Words: List<ULong>, sp880Prefix: List<ULong>, sp9e0Words: List<ULong>, sp7d0Prefix: List<ULong>): List<ULong> {
    if (spa90Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(spa90Words.size)
    }
    if (sp880Prefix.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp880Prefix.size)
    }
    if (sp9e0Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp9e0Words.size)
    }
    if (sp7d0Prefix.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp7d0Prefix.size)
    }

    val out = ArrayList<ULong>()
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - 21)
        val end = index
        var productSum: ULong = 0UL
        for (position in start..end) {
            productSum = productSum + spa90Words[position] * sp9e0Words[index - position]
        }
        val span = end - start + 1
        val sumA = rangeSumFromPrefix(sp880Prefix, start, end)
        val sumB = sp7d0Prefix[span - 1]
        val mixed = productSum * 0x29f4a886cd96e34dUL +
            span.toULong() * 0xfb27869a34fe306eUL +
            sumA * 0x7228cc7a696bf425UL +
            sumB * 0x4005e2eb6883e7deUL
        out.add(mixed * 0x10af80ba2ba8ff03UL + 0x603f2c10b20e1521UL)
    }
    return out
}

internal fun builder642f60MidFifth64bd0cWorkspace(sp670Words: List<ULong>, sp510Words: List<ULong>): ByteArray {
    if (sp670Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp670Words.size)
    }
    if (sp510Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp510Words.size)
    }
    val out = ArrayList<Byte>()
    for (index in 0 until builder64bd0cWorkspaceWords) {
        out.addAll(
            u64LEBytes(
                sp670Words[index] * 0x311e50313531405dUL +
                    sp510Words[index] * 0xc817dbca0a20eafdUL +
                    0xc254ca1fa792908cUL
            ).toList()
        )
    }
    return out.toByteArray()
}
