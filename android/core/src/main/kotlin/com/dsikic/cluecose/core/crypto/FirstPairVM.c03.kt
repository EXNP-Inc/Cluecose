// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

internal fun builder642f60SixthStreamsFromSP1A0(sp1a0Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (sp1a0Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp1a0Words.size)
    }
    val tables = FirstPairConfig.tables
    val spa90Words = (0 until builder63c278VectorWords).map { index ->
        builder642f60SixthAWord(sp1a0Words[index], index, tables)
    }
    val sp880Words = (0 until builder63c278VectorWords).map { index ->
        builder642f60SixthBWord(sp1a0Words[index], index, tables)
    }
    return FpQuad(
        spa90Words,
        prefixSumsU64(spa90Words),
        sp880Words,
        prefixSumsU64(sp880Words)
    )
}

internal fun builder642f60Sixth64bd0cWorkspace(spa90Words: List<ULong>, sp670Prefix: List<ULong>, sp880Words: List<ULong>, sp510Prefix: List<ULong>): ByteArray {
    if (spa90Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(spa90Words.size)
    }
    if (sp670Prefix.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp670Prefix.size)
    }
    if (sp880Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp880Words.size)
    }
    if (sp510Prefix.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp510Prefix.size)
    }

    val out = ByteArray(builder64bd0cWorkspaceBytes)
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - 21)
        val end = minOf(index, 21)
        var productSum: ULong = 0UL
        if (start <= end) {
            for (position in start..end) {
                productSum = productSum + spa90Words[position] * sp880Words[index - position]
            }
        }
        val span = (if (start <= end) end - start + 1 else 0).toULong()
        val sumA = rangeSumFromPrefix(sp670Prefix, start, end)
        val sumB = rangeSumFromPrefix(sp510Prefix, index - end, index - start)
        val mixed = span * 0xc9579b83c731c3c0UL +
            0x5c81c51b07a75dd5UL +
            productSum * 0x5af5ce9c3c24da93UL +
            sumA * 0x22758d71fea188c0UL +
            sumB * 0xf1d0ed7a635c3b3fUL
        u64LEBytes(mixed * 0xa731aa4721be8565UL + 0x25f1b6bafa949dffUL)
            .copyInto(out, index * 8)
    }
    return out
}

internal fun builder642f60Out0SourceWords(sixthOutput: ByteArray, contextSource: ByteArray, sp250Words: List<UInt>): List<UInt> {
    if (sixthOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 sixth output", sixthOutput.size, builder63c278VectorBytes)
    }
    if (contextSource.size < 0x208) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 context source", contextSource.size, 0x208)
    }
    if (sp250Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp250Words.size)
    }
    val tables = FirstPairConfig.tables
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder642f60Out0StaticQ0, 16),
            TableSegment(builder642f60Out0StaticQ1, 16),
            TableSegment(builder642f60Out0StaticQ0, 16),
            TableSegment(builder642f60Out0StaticQ1, 16),
            TableSegment(builder642f60Out0StaticQ0, 16),
            TableSegment(builder642f60Out0StaticD1, 8),
        ),
        tables
    )
    val sixthBytes = sixthOutput
    val contextBytes = contextSource
    return (0 until builder63c278VectorWords).map { index ->
        val tableOffset = (index * 4) and 0x1c
        val sixthWord = readUInt32LE(sixthBytes, index * 4)
        val contextWord = readUInt32LE(contextBytes, 0x1b0 + index * 4)
        var word = baseWords[index] +
            sixthWord * u32TableWord63c278(builder642f60Out0SixthMulTable + tableOffset, tables)
        word = word +
            contextWord * u32TableWord63c278(builder642f60Out0ContextMulTable + tableOffset, tables)
        val sp250Delta = sp250Words[index] * u32TableWord63c278(
            builder642f60Out0SP250MulTable + tableOffset,
            tables
        )
        word + sp250Delta + sp250Delta
    }
}

internal fun builder642f60Out0WordsFromSource(sourceWords: List<UInt>): List<UInt> {
    if (sourceWords.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sourceWords.size)
    }
    val tables = FirstPairConfig.tables
    var state: UInt = 0xb326b224U
    val out = ArrayList<UInt>()
    for (index in sourceWords.indices) {
        val word = sourceWords[index]
        val tableOffset = (index * 4) and 0x1c
        state = state * 0x3d98bc67U + word
        var folded7 = state * 0xe98a6e39U + 0xa9ce435cU
        folded7 = fold32ByNibbles63c278(
            folded7,
            builder642f60Out0FoldTable,
            7,
            tables
        )
        val side = state * 0x88625dcfU +
            folded7 * 0x90000000U +
            0x647eea94U
        val folded8 = fold32ByNibbles63c278(
            folded7,
            builder642f60Out0FoldTable,
            1,
            tables
        )
        out.add(
            u32TableAffine63c278(
                side,
                builder642f60Out0OutMulTable + tableOffset,
                builder642f60Out0OutAddTable + tableOffset,
                tables
            )
        )
        state = folded7 * 0x50717a0fU +
            folded8 * 0xf8e85f10U +
            0x119b9786U
    }
    return out
}

internal fun builder642f60SeventhSourceWords(sp250Words: List<UInt>, contextSource: ByteArray, out0Words: List<UInt>): List<UInt> {
    if (sp250Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp250Words.size)
    }
    if (contextSource.size < 0x260) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 context source", contextSource.size, 0x260)
    }
    if (out0Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(out0Words.size)
    }
    val tables = FirstPairConfig.tables
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder642f60SeventhStaticQ0, 16),
            TableSegment(builder642f60SeventhStaticQ1, 16),
            TableSegment(builder642f60SeventhStaticQ0, 16),
            TableSegment(builder642f60SeventhStaticQ1, 16),
            TableSegment(builder642f60SeventhStaticQ0, 16),
            TableSegment(builder642f60SeventhStaticD1, 8),
        ),
        tables
    )
    val contextBytes = contextSource
    return (0 until builder63c278VectorWords).map { index ->
        val tableOffset = (index * 4) and 0x1c
        val contextWord = readUInt32LE(contextBytes, 0x208 + index * 4)
        baseWords[index] +
            sp250Words[index] * u32TableWord63c278(builder642f60SeventhSP250MulTable + tableOffset, tables) +
            contextWord * u32TableWord63c278(builder642f60SeventhContextMulTable + tableOffset, tables) +
            out0Words[index] * u32TableWord63c278(builder642f60SeventhOut0MulTable + tableOffset, tables)
    }
}

internal fun builder642f60SeventhStageSP148WordsFromSource(sourceWords: List<UInt>): List<UInt> {
    if (sourceWords.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sourceWords.size)
    }
    val tables = FirstPairConfig.tables
    var state: UInt = 0xf92a7de1U
    val out = ArrayList<UInt>()
    for (index in sourceWords.indices) {
        val word = sourceWords[index]
        val tableOffset = (index * 4) and 0x1c
        state = state * 0x2bd72421U + word
        var folded7 = state * 0x79766d05U + 0x22dc5eefU
        folded7 = fold32ByNibbles63c278(
            folded7,
            builder642f60SeventhSP148FoldTable,
            7,
            tables
        )
        val side = state * 0x072b272dU +
            folded7 * 0x70000000U +
            0x63742f4bU
        val folded8 = fold32ByNibbles63c278(
            folded7,
            builder642f60SeventhSP148FoldTable,
            1,
            tables
        )
        out.add(
            u32TableAffine63c278(
                side,
                builder642f60SeventhSP148OutMulTable + tableOffset,
                builder642f60SeventhSP148OutAddTable + tableOffset,
                tables
            )
        )
        state = folded7 * 0x04d53e2dU +
            folded8 * 0xb2ac1d30U +
            0x1cf006ebU
    }
    return out
}

internal fun builder642f60SeventhStreams(sp1a0Words: List<UInt>, sp148Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (sp1a0Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp1a0Words.size)
    }
    if (sp148Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp148Words.size)
    }
    val tables = FirstPairConfig.tables
    val sp670Words = (0 until builder63c278VectorWords).map { index ->
        u64StreamWordFromU32Affine(
            sp1a0Words[index],
            index,
            builder642f60SeventhAMulTable,
            builder642f60SeventhAAddTable,
            0xf36a661dU,
            0x55308919U,
            0xce5ac3ad5b5dac97UL,
            0x48dc073b21398a79UL,
            builder642f60SeventhAFoldTable,
            0xfa62b370c3eadc41UL,
            0xf45f1f1900000000UL,
            0x6fc778fe52193dd5UL,
            tables
        )
    }
    val sp510Words = (0 until builder63c278VectorWords).map { index ->
        u64StreamWordFromU32Affine(
            sp148Words[index],
            index,
            builder642f60SeventhBMulTable,
            builder642f60SeventhBAddTable,
            0x84c35e4fU,
            0xea2fdd20U,
            0x1f41e4ec093ed9f7UL,
            0x27d1733855de4d16UL,
            builder642f60SeventhBFoldTable,
            0x9fac6b22392e3497UL,
            0x09482d9f00000000UL,
            0xf6042c7612dc729eUL,
            tables
        )
    }
    return FpQuad(
        sp670Words,
        listOf(0UL) + prefixSumsU64(sp670Words),
        sp510Words,
        listOf(0UL) + prefixSumsU64(sp510Words)
    )
}

internal fun builder642f60SeventhSP9E0Words(sp670Words: List<ULong>, spa90Prefix: List<ULong>, sp510Words: List<ULong>, sp880Prefix: List<ULong>): List<UInt> {
    if (sp670Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp670Words.size)
    }
    if (spa90Prefix.size != builder63c278VectorWords + 1) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(spa90Prefix.size)
    }
    if (sp510Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp510Words.size)
    }
    if (sp880Prefix.size != builder63c278VectorWords + 1) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp880Prefix.size)
    }
    val tables = FirstPairConfig.tables
    var state: ULong = 0x8360a2c993f75737UL
    val out = ArrayList<UInt>()

    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - (builder63c278VectorWords - 1))
        val end = minOf(index, builder63c278VectorWords - 1)
        val mixed: ULong
        if (start <= end) {
            var productSum: ULong = 0UL
            for (position in start..end) {
                productSum = productSum + sp670Words[position] * sp510Words[index - position]
            }
            val span = (end - start + 1).toULong()
            val sumA = spa90Prefix[end + 1] - spa90Prefix[start]
            val sumB = sp880Prefix[index - start + 1] - sp880Prefix[index - end]
            mixed = state +
                span * 0x005dbd39bbb74611UL +
                productSum * 0x376b7bf8523b310fUL +
                sumA * 0x05844a4f0ab6c52bUL +
                sumB * 0xbcb254e552fa427dUL
        } else {
            mixed = state
        }

        val folded7 = fold63c278(
            mixed * 0x8db6469e177ed14bUL + 0x0980afdda9144775UL,
            builder642f60SeventhSP9E0FoldTable,
            7,
            tables
        )
        val folded8 = fold63c278(
            folded7,
            builder642f60SeventhSP9E0FoldTable,
            1,
            tables
        )
        val folded16 = fold63c278(
            folded8,
            builder642f60SeventhSP9E0FoldTable,
            8,
            tables
        )
        val side = mixed.toUInt() * 0xe15d12adU +
            folded7.toUInt() * 0x90000000U +
            0x07600fb6U
        val tableOffset = (index * 4) and 0x1c
        out.add(
            u32TableAffine63c278(
                side,
                builder642f60SeventhSP9E0OutMulTable + tableOffset,
                builder642f60SeventhSP9E0OutAddTable + tableOffset,
                tables
            )
        )
        state = folded7 * 0xfac2e2a1bcc53063UL +
            folded16 * 0x33acf9d000000000UL +
            0x42e2c949e6b96dc1UL
    }
    return out
}

internal fun builder642f60SeventhSPA90WordsFromSP300(sp300Words: List<UInt>): List<ULong> {
    if (sp300Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp300Words.size)
    }
    val tables = FirstPairConfig.tables
    return (0 until builder63c278VectorWords).map { index ->
        u64StreamWordFromU32Affine(
            sp300Words[index],
            index,
            builder642f60SeventhSP300MulTable,
            builder642f60SeventhSP300AddTable,
            0x923b2603U,
            0x0d7c3c6dU,
            0x461236e7241ea4afUL,
            0xc0bb06ebd489d8f1UL,
            builder642f60SeventhSP300FoldTable,
            0x1925dd7dc803ae75UL,
            0x6a8f4fe500000000UL,
            0x52f0304276b65fdeUL,
            tables
        )
    }
}

internal fun builder642f60SeventhSP7D0WordsFromSPA90(spa90Words: List<ULong>): List<UInt> {
    if (spa90Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(spa90Words.size)
    }
    val tables = FirstPairConfig.tables
    var state: ULong = 0xd71b81e668a07680UL
    val out = ArrayList<UInt>()

    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - (builder63c278VectorWords - 1))
        val end = minOf(index, builder63c278VectorWords - 1)
        var pairAccumulator: ULong = 0xb616243568409e12UL
        var left = start
        var right = end

        if (end > start) {
            while (true) {
                val endWord = spa90Words[right]
                right -= 1
                val product = endWord * 0x3a825182ec92a9efUL + 0x975bbf5d33a0b7f4UL
                var mixedPair = endWord * 0x975bbf5d33a0b7f4UL + pairAccumulator
                val startWord = spa90Words[left]
                left += 1
                mixedPair = product * startWord + mixedPair
                pairAccumulator = mixedPair + 0x4657dd9b924a1870UL
                if (right <= left) {
                    break
                }
            }
        }

        pairAccumulator = pairAccumulator * 0xc3c1f54f3c2cd4a6UL
        val scaledState = state * 0x7f0a8f747ca98163UL
        val mixed: ULong
        if (right == left) {
            val center = spa90Words[right]
            val centerMixed = center * 0x8b03bdcc8a740e7dUL + 0x25af1839607d5838UL
            mixed = centerMixed * center + pairAccumulator + scaledState + 0xe804eb7226c5f391UL
        } else {
            mixed = pairAccumulator + scaledState + 0x0bea08ebd101a741UL
        }

        val product = mixed * 0x416e14010d9d6b21UL
        val firstFold = foldTableU64Word63c278(
            builder642f60SeventhSP7D0FoldTable + (product and 0x0fUL).toInt() * 8,
            tables
        ) + ((product + 0xe4602986bf1f9a80UL) shr 4)
        val folded7 = fold63c278(
            firstFold,
            builder642f60SeventhSP7D0FoldTable,
            6,
            tables
        )
        val folded8 = fold63c278(
            folded7,
            builder642f60SeventhSP7D0FoldTable,
            1,
            tables
        )
        val side = mixed.toUInt() * 0x3e3dcae5U +
            folded7.toUInt() * 0xb0000000U +
            0x8a63e3dcU
        val folded16 = fold63c278(
            folded8,
            builder642f60SeventhSP7D0FoldTable,
            8,
            tables
        )
        val tableOffset = (index * 4) and 0x1c
        out.add(
            u32TableAffine63c278(
                side,
                builder642f60SeventhSP7D0OutMulTable + tableOffset,
                builder642f60SeventhSP7D0OutAddTable + tableOffset,
                tables
            )
        )
        state = folded7 * 0x38c35e2d317591ebUL +
            folded16 * 0xe8a6e15000000000UL +
            0x7cfc7c8b77dde511UL
    }
    return out
}

internal fun builder642f60SeventhSource44Words(sp9e0Words: List<UInt>, contextSource: ByteArray, sp7d0Words: List<UInt>): List<UInt> {
    if (sp9e0Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp9e0Words.size)
    }
    if (contextSource.size < 0x418) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 context source", contextSource.size, 0x418)
    }
    if (sp7d0Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp7d0Words.size)
    }
    val tables = FirstPairConfig.tables
    val contextBytes = contextSource
    val out = ArrayList<UInt>()
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val tableOffset = (index * 4) and 0x1c
        val contextWord = readUInt32LE(contextBytes, 0x368 + index * 4)
        val sp7d0Delta = sp7d0Words[index] * u32TableWord63c278(
            builder642f60SeventhSourceSP7D0MulTable + tableOffset,
            tables
        )
        out.add(
            u32TableWord63c278(builder642f60SeventhSourceStaticTable + tableOffset, tables) +
                sp9e0Words[index] * u32TableWord63c278(
                    builder642f60SeventhSourceSP9E0MulTable + tableOffset,
                    tables
                ) +
                contextWord * u32TableWord63c278(
                    builder642f60SeventhSourceContext368MulTable + tableOffset,
                    tables
                ) +
                sp7d0Delta +
                sp7d0Delta
        )
    }
    return out
}

internal fun builder642f60SeventhSP40WordsFromSource44(sourceWords: List<UInt>): List<UInt> {
    if (sourceWords.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sourceWords.size)
    }
    val tables = FirstPairConfig.tables
    var state: UInt = 0xcfda05baU
    val out = ArrayList<UInt>()
    for (index in sourceWords.indices) {
        val word = sourceWords[index]
        val tableOffset = (index * 4) and 0x1c
        state = state * 0x0862c569U + word
        var folded7 = state * 0x5e8a87f3U + 0x54d7c56fU
        folded7 = fold32ByNibbles63c278(
            folded7,
            builder642f60SeventhSP40FoldTable,
            7,
            tables
        )
        val side = state * 0x12f83eedU +
            (folded7 shl 28) +
            0x51f93a0aU
        val folded8 = foldTableU32Word63c278(
            builder642f60SeventhSP40FoldTable + (folded7 and 0x0fU).toInt() * 4,
            tables
        ) + (folded7 shr 4)
        out.add(
            u32TableAffine63c278(
                side,
                builder642f60SeventhSP40OutMulTable + tableOffset,
                builder642f60SeventhSP40OutAddTable + tableOffset,
                tables
            )
        )
        state = folded7 * 0x36a73103U +
            folded8 * 0x958cefd0U +
            0x9e56fff6U
    }
    return out
}

internal fun builder642f60Seventh64bd0cWorkspace(sp40Words: List<UInt>): ByteArray {
    if (sp40Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp40Words.size)
    }
    val tables = FirstPairConfig.tables
    val out = ByteArray(builder64bd0cWorkspaceBytes)
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val word = u64StreamWordFromU32Affine(
            sp40Words[index],
            index,
            builder642f60SeventhWorkspaceMulTable,
            builder642f60SeventhWorkspaceAddTable,
            0x2e6bbea3U,
            0xe3db739aU,
            0x40c95ec2845e4b0bUL,
            0xb5edeaa67030b38dUL,
            builder642f60SeventhWorkspaceFoldTable,
            0xa2d77df3e3f51135UL,
            0x7122434100000000UL,
            0xb3aefd596d371f14UL,
            tables
        )
        u64LEBytes(word).copyInto(out, index * 8)
    }
    return out
}

internal fun builder642f60Out1WordsFrom64bd0cOutput(output: ByteArray): List<UInt> {
    return builder642f60AffineWordsFrom64bd0cOutput(
        output,
        builder642f60Out1MulTable,
        builder642f60Out1AddTable,
        "642f60 output #1"
    )
}

internal fun builder642f60EighthStreams(sp2a8Words: List<UInt>, x2Source: ByteArray): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (sp2a8Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp2a8Words.size)
    }
    if (x2Source.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 x2 source", x2Source.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val x2Bytes = x2Source
    val x2Words = (0 until builder63c278VectorWords).map { index ->
        readUInt32LE(x2Bytes, index * 4)
    }
    val spa90Words = (0 until builder63c278VectorWords).map { index ->
        builder642f60EighthAWord(sp2a8Words[index], index, tables)
    }
    val sp880Words = (0 until builder63c278VectorWords).map { index ->
        builder642f60EighthBWord(x2Words[index], index, tables)
    }
    return FpQuad(
        spa90Words,
        prefixSumsU64(spa90Words),
        sp880Words,
        prefixSumsU64(sp880Words)
    )
}

internal fun builder642f60Eighth64bd0cWorkspace(spa90Words: List<ULong>, sp670Prefix: List<ULong>, sp880Words: List<ULong>, sp510Prefix: List<ULong>): ByteArray {
    if (spa90Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(spa90Words.size)
    }
    if (sp670Prefix.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp670Prefix.size)
    }
    if (sp880Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp880Words.size)
    }
    if (sp510Prefix.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp510Prefix.size)
    }

    val out = ByteArray(builder64bd0cWorkspaceBytes)
    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - (builder63c278VectorWords - 1))
        val end = minOf(index, builder63c278VectorWords - 1)
        val mixed: ULong
        if (start <= end) {
            var productSum: ULong = 0UL
            for (position in start..end) {
                productSum = productSum + spa90Words[position] * sp880Words[index - position]
            }
            val span = (end - start + 1).toULong()
            val sumA = rangeSumFromPrefix(sp670Prefix, start, end)
            val sumB = rangeSumFromPrefix(sp510Prefix, index - end, index - start)
            mixed = span * 0x05f89c998f88e9a2UL +
                0xe3449c12b03ff8d9UL +
                productSum * 0xeab93afc6984b71dUL +
                sumA * 0xc2592d51a5992a23UL +
                sumB * 0xf8e4c71d4c7a89deUL
        } else {
            mixed = 0xe3449c12b03ff8d9UL
        }
        u64LEBytes(mixed * 0xca274927c26656e9UL + 0x89706c698c29e887UL)
            .copyInto(out, index * 8)
    }
    return out
}

internal fun builder642f60Out2WordsFrom64bd0cOutput(output: ByteArray): List<UInt> {
    return builder642f60AffineWordsFrom64bd0cOutput(
        output,
        builder642f60Out2MulTable,
        builder642f60Out2AddTable,
        "642f60 output #2"
    )
}

internal fun builder642f60Outputs(in0: ByteArray, in1: ByteArray, in2: ByteArray, contextSource: ByteArray): Builder642f60Result {
    if (in0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 in0", in0.size, builder63c278VectorBytes)
    }
    if (in1.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 in1", in1.size, builder63c278VectorBytes)
    }
    if (in2.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 in2", in2.size, builder63c278VectorBytes)
    }
    if (contextSource.size < 0x420) {
        throw FirstPairSourceSliceError.sourceTooShort("642f60 context source", contextSource.size, 0x420)
    }

    val contextBytes = contextSource
    val arg0 = contextBytes.copyOfRange(0x100, 0x158)
    val scalar = readUInt64LE(contextBytes, 0x418)

    val sp2a8Words = builder642f60StageSP2A8WordsFromX1(in1)
    val firstOutput = packUInt32LE(builder64bd0cOutputWords(
        arg0,
        scalar,
        builder642f60First64bd0cWorkspaceFromX1(
            in1,
            sp2a8Words
        )
    ))
    val sp300Words = builder642f60StageSP300WordsFrom64bd0cOutput(firstOutput)

    val sp1f8Words = builder642f60StageSP1F8WordsFromX0(in0)
    val secondOutput = packUInt32LE(builder64bd0cOutputWords(
        arg0,
        scalar,
        builder642f60Second64bd0cWorkspace(
            sp1f8Words,
            sp300Words
        )
    ))
    val sp250Words = builder642f60StageSP250WordsFrom64bd0cOutput(secondOutput)

    val thirdOutput = packUInt32LE(builder64bd0cOutputWords(
        arg0,
        scalar,
        builder642f60Third64bd0cWorkspaceFromX2(in2)
    ))
    val sp148Words = builder642f60StageSP148WordsFrom64bd0cOutput(thirdOutput)

    val fourthOutput = packUInt32LE(builder64bd0cOutputWords(
        arg0,
        scalar,
        builder642f60Fourth64bd0cWorkspace(sp148Words)
    ))
    val spf0Words = builder642f60StageSPF0WordsFrom64bd0cOutput(fourthOutput)

    val midSPA90Words = builder642f60MidStageSPA90WordsFromX0(in0)
    val midSP40Words = builder642f60MidStageSP40WordsFromSPA90(midSPA90Words)
    val midStreams = builder642f60MidStageStreamsFromContextSPF0(
        contextSource,
        spf0Words
    )
    val midSP670Words = builder642f60MidStageSP670Words(
        midStreams.first,
        midStreams.second,
        midStreams.third,
        midStreams.fourth
    )
    val midSP40B = builder642f60MidStageSPA90SP880FromSP40(midSP40Words)
    val midStatic = builder642f60MidStageStaticSP9E0SP7D0(midSP40B.third)
    val midSP510Words = builder642f60MidStageSP510Words(
        midSP40B.first,
        midSP40B.second,
        midStatic.first,
        midStatic.second
    )

    val fifthOutput = packUInt32LE(builder64bd0cOutputWords(
        arg0,
        scalar,
        builder642f60MidFifth64bd0cWorkspace(
            midSP670Words,
            midSP510Words
        )
    ))
    val sp1a0Words = builder642f60StageSP1A0WordsFrom64bd0cOutput(fifthOutput)

    val sixthStreams = builder642f60SixthStreamsFromSP1A0(sp1a0Words)
    val sixthOutput = packUInt32LE(builder64bd0cOutputWords(
        arg0,
        scalar,
        builder642f60Sixth64bd0cWorkspace(
            sixthStreams.first,
            sixthStreams.second,
            sixthStreams.third,
            sixthStreams.fourth
        )
    ))

    val out0SourceWords = builder642f60Out0SourceWords(
        sixthOutput,
        contextSource,
        sp250Words
    )
    val out0Words = builder642f60Out0WordsFromSource(out0SourceWords)

    val seventhSourceWords = builder642f60SeventhSourceWords(
        sp250Words,
        contextSource,
        out0Words
    )
    val seventhSP148Words = builder642f60SeventhStageSP148WordsFromSource(seventhSourceWords)
    val seventhStreams = builder642f60SeventhStreams(
        sp1a0Words,
        seventhSP148Words
    )
    val seventhSP9E0Words = builder642f60SeventhSP9E0Words(
        seventhStreams.first,
        seventhStreams.second,
        seventhStreams.third,
        seventhStreams.fourth
    )
    val seventhSPA90Words = builder642f60SeventhSPA90WordsFromSP300(sp300Words)
    val seventhSP7D0Words = builder642f60SeventhSP7D0WordsFromSPA90(seventhSPA90Words)
    val seventhSource44Words = builder642f60SeventhSource44Words(
        seventhSP9E0Words,
        contextSource,
        seventhSP7D0Words
    )
    val seventhSP40Words = builder642f60SeventhSP40WordsFromSource44(seventhSource44Words)
    val seventhOutput = packUInt32LE(builder64bd0cOutputWords(
        arg0,
        scalar,
        builder642f60Seventh64bd0cWorkspace(seventhSP40Words)
    ))
    val out1Words = builder642f60Out1WordsFrom64bd0cOutput(seventhOutput)

    val eighthStreams = builder642f60EighthStreams(sp2a8Words, in2)
    val eighthOutput = packUInt32LE(builder64bd0cOutputWords(
        arg0,
        scalar,
        builder642f60Eighth64bd0cWorkspace(
            eighthStreams.first,
            eighthStreams.second,
            eighthStreams.third,
            eighthStreams.fourth
        )
    ))
    val out2Words = builder642f60Out2WordsFrom64bd0cOutput(eighthOutput)

    return Builder642f60Result(
        packUInt32LE(out0Words),
        packUInt32LE(out1Words),
        packUInt32LE(out2Words)
    )
}

internal fun builder642f60OutputsFromBundledContext(in0: ByteArray, in1: ByteArray, in2: ByteArray): Builder642f60Result {
    val context = builder6388f0SharedContextFromBundle()
    return builder642f60Outputs(in0, in1, in2, context)
}

internal fun builder64bd0cArg0U64Words(arg0: ByteArray): List<ULong> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("64bd0c arg0", arg0.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = arg0
    val out = ArrayList<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        val affine = u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder64bd0cArg0MulTable,
            builder64bd0cArg0AddTable,
            tables
        )
        val word = affine * 0x3e251f3fU + 0xc80f68f4U
        val folded = fold63c278(
            word.toULong() * 0xf636dda3668409f3UL + 0xa1898a9b9b0c347bUL,
            builder64bd0cArg0FoldTable,
            8,
            tables
        )
        out.add(
            word.toULong() * 0x57c9f2b4caac6659UL +
                folded * 0xa43bca7d00000000UL +
                0x6de2d7b43700ac09UL
        )
    }
    return out
}

internal fun builder64bd0cWorkspaceAfterUpdate(arg0U64Words: List<ULong>, scalar: ULong, x2Workspace: ByteArray): ByteArray {
    if (arg0U64Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(arg0U64Words.size)
    }
    if (x2Workspace.size < builder64bd0cWorkspaceBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("64bd0c x2 workspace", x2Workspace.size, builder64bd0cWorkspaceBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x2Workspace
    val x2Words = (0 until builder64bd0cWorkspaceWords).map { index ->
        readUInt64LE(bytes, index * 8)
    }.toMutableList()
    var carryWord = x2Words[0]
    for (base in 0 until builder63c278VectorWords) {
        val params = builder64bd0cWorkspaceParams(
            scalar,
            carryWord,
            tables
        )
        for (offset in arg0U64Words.indices) {
            val pos = base + offset
            x2Words[pos] = x2Words[pos] + params.second + (arg0U64Words[offset] * params.first)
        }
        carryWord = builder64bd0cRewriteSecondWord(
            x2Words[base],
            x2Words[base + 1],
            tables
        )
        x2Words[base + 1] = carryWord
    }
    return packUInt64LE64cd40(x2Words)
}

internal fun builder64bd0cFinalU32Words(x2Workspace: ByteArray): List<UInt> {
    if (x2Workspace.size < builder64bd0cWorkspaceBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("64bd0c x2 workspace", x2Workspace.size, builder64bd0cWorkspaceBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x2Workspace
    val x2Words = (0 until builder64bd0cWorkspaceWords).map { index ->
        readUInt64LE(bytes, index * 8)
    }
    var carry: ULong = 0xa8100bf8a7268389UL
    val out = ArrayList<UInt>()

    for (index in 0 until builder63c278VectorWords) {
        val tailWord = x2Words[builder63c278VectorWords + index]
        val mixed = carry * 0xdc6110b4d93c58f7UL + tailWord * 0x29221b50b5648139UL
        val foldedInput = mixed + 0x02ea5a475ff009a0UL
        val firstFold = builder64bd0cFinalFirstFold(foldedInput, tables)
        val folded = fold63c278(
            firstFold.third,
            builder64bd0cFinalFoldTable,
            8,
            tables
        )
        val nextCarry = firstFold.first * 0x1323954bb9644419UL +
            folded * 0x69bbbe7000000000UL
        val tableOffset = (index * 4) and 0x1c
        out.add(u32TableAffine63c278(
            firstFold.second,
            builder64bd0cFinalOutMulTable + tableOffset,
            builder64bd0cFinalOutAddTable + tableOffset,
            tables
        ))
        carry = nextCarry + 0x7a8f00bf503f94fbUL
    }
    return out
}

internal fun builder64bd0cOutputWords(arg0: ByteArray, scalar: ULong, x2Workspace: ByteArray): List<UInt> {
    val arg0Words = builder64bd0cArg0U64Words(arg0)
    val updated = builder64bd0cWorkspaceAfterUpdate(
        arg0Words,
        scalar,
        x2Workspace
    )
    return builder64bd0cFinalU32Words(updated)
}

internal fun builder64c524Arg0U64Words(arg0: ByteArray): List<ULong> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("64c524 arg0", arg0.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = arg0
    val out = ArrayList<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        val affine = u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder64c524Arg0MulTable,
            builder64c524Arg0AddTable,
            tables
        )
        val word = affine * 0xc0134d17U + 0x71ee3738U
        val folded = fold63c278(
            word.toULong() * 0x30f8c406f090e325UL + 0x7a3d4622dcb83626UL,
            builder64c524Arg0FoldTable,
            8,
            tables
        )
        out.add(
            word.toULong() * 0x430cd374007356b5UL +
                folded * 0xc0efe7af00000000UL +
                0xe5faf13619f0e974UL
        )
    }
    return out
}

internal fun builder64c524WorkspaceAfterUpdate(arg0U64Words: List<ULong>, scalar: ULong, x2Workspace: ByteArray): ByteArray {
    if (arg0U64Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(arg0U64Words.size)
    }
    if (x2Workspace.size < builder64c524WorkspaceBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("64c524 x2 workspace", x2Workspace.size, builder64c524WorkspaceBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x2Workspace
    val x2Words = (0 until builder64c524WorkspaceWords).map { index ->
        readUInt64LE(bytes, index * 8)
    }.toMutableList()
    var carryWord = x2Words[0]
    for (base in 0 until builder63c278VectorWords) {
        val params = builder64c524WorkspaceParams(
            scalar,
            carryWord,
            tables
        )
        for (offset in arg0U64Words.indices) {
            val pos = base + offset
            x2Words[pos] = x2Words[pos] + params.second + (arg0U64Words[offset] * params.first)
        }
        carryWord = builder64c524RewriteSecondWord(
            x2Words[base],
            x2Words[base + 1],
            tables
        )
        x2Words[base + 1] = carryWord
    }
    return packUInt64LE64cd40(x2Words)
}

internal fun builder64c524FinalU32Words(x2Workspace: ByteArray): List<UInt> {
    if (x2Workspace.size < builder64c524WorkspaceBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("64c524 x2 workspace", x2Workspace.size, builder64c524WorkspaceBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x2Workspace
    val x2Words = (0 until builder64c524WorkspaceWords).map { index ->
        readUInt64LE(bytes, index * 8)
    }
    var carry: ULong = 0xa231ae9017976cb8UL
    val out = ArrayList<UInt>()

    for (index in 0 until builder63c278VectorWords) {
        val tailWord = x2Words[builder63c278VectorWords + index]
        val foldedInput = carry * 0xcd03684f066c56f1UL +
            tailWord * 0x5691aa6f378a40d3UL +
            0x1c0f700d822da380UL
        val firstFold = builder64c524FinalFirstFold(foldedInput, tables)
        val folded = fold63c278(
            firstFold.third,
            builder64c524FinalFoldTable,
            8,
            tables
        )
        val nextCarry = firstFold.first * 0xe9b2139140497c53UL +
            folded * 0xfb683ad000000000UL
        val tableOffset = (index * 4) and 0x1c
        out.add(u32TableAffine63c278(
            firstFold.second,
            builder64c524FinalOutMulTable + tableOffset,
            builder64c524FinalOutAddTable + tableOffset,
            tables
        ))
        carry = nextCarry + 0xee51a1bedb406a0dUL
    }
    return out
}

internal fun builder64c524OutputWords(arg0: ByteArray, scalar: ULong, x2Workspace: ByteArray): List<UInt> {
    val arg0Words = builder64c524Arg0U64Words(arg0)
    val updated = builder64c524WorkspaceAfterUpdate(
        arg0Words,
        scalar,
        x2Workspace
    )
    return builder64c524FinalU32Words(updated)
}

internal fun builder6473d0FirstStreamsFromIn2(in2: ByteArray): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (in2.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in2 source", in2.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = in2
    val aWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0FirstAWord(readUInt32LE(bytes, index * 4), index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0FirstBWord(readUInt32LE(bytes, index * 4), index, tables)
    }
    return FpQuad(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0First64c524Workspace(in2: ByteArray): ByteArray {
    val streams = builder6473d0FirstStreamsFromIn2(in2)
    return convolutionWorkspaceU64(
        streams.first,
        streams.second,
        builder6473d0FirstConvBaseAdd,
        builder6473d0FirstConvCountMul,
        builder6473d0FirstConvProductMul,
        builder6473d0FirstConvSumAMul,
        builder6473d0FirstConvSumBMul,
        builder6473d0FirstConvFinalMul,
        builder6473d0FirstConvFinalAdd
    )
}

internal fun builder6473d0SP488WordsFrom64c524Output(output: ByteArray): List<UInt> {
    if (output.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 64c524 output", output.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = output
    return (0 until builder63c278VectorWords).map { index ->
        u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder6473d0SP488MulTable,
            builder6473d0SP488AddTable,
            tables
        )
    }
}

internal fun builder6473d0SecondStreams(out0Seed: ByteArray, sp488Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (out0Seed.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 out0 seed", out0Seed.size, builder63c278VectorBytes)
    }
    if (sp488Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp488Words.size)
    }
    val tables = FirstPairConfig.tables
    val bytes = out0Seed
    val aWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0SecondAWord(readUInt32LE(bytes, index * 4), index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0SecondBWord(sp488Words[index], index, tables)
    }
    return FpQuad(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Second64c524Workspace(out0Seed: ByteArray, sp488Words: List<UInt>): ByteArray {
    val streams = builder6473d0SecondStreams(out0Seed, sp488Words)
    return convolutionWorkspaceU64(
        streams.first,
        streams.second,
        builder6473d0SecondConvBaseAdd,
        builder6473d0SecondConvCountMul,
        builder6473d0SecondConvProductMul,
        builder6473d0SecondConvSumAMul,
        builder6473d0SecondConvSumBMul,
        builder6473d0SecondConvFinalMul,
        builder6473d0SecondConvFinalAdd
    )
}

internal fun builder6473d0ThirdSourceWords(secondOutput: ByteArray, contextSource: ByteArray, in0: ByteArray): List<UInt> {
    if (secondOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 second output", secondOutput.size, builder63c278VectorBytes)
    }
    if (contextSource.size < 0x260) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 context source", contextSource.size, 0x260)
    }
    if (in0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in0 source", in0.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder6473d0ThirdStaticQ0, 16),
            TableSegment(builder6473d0ThirdStaticQ1, 16),
            TableSegment(builder6473d0ThirdStaticQ0, 16),
            TableSegment(builder6473d0ThirdStaticQ1, 16),
            TableSegment(builder6473d0ThirdStaticQ0, 16),
            TableSegment(builder6473d0ThirdStaticD1, 8),
        ),
        tables
    )
    val secondBytes = secondOutput
    val contextBytes = contextSource
    val in0Bytes = in0
    return (0 until builder63c278VectorWords).map { index ->
        val tableOffset = (index * 4) and 0x1c
        val secondWord = readUInt32LE(secondBytes, index * 4)
        val contextWord = readUInt32LE(contextBytes, 0x208 + index * 4)
        val in0Word = readUInt32LE(in0Bytes, index * 4)
        var word = baseWords[index] +
            secondWord * u32TableWord63c278(builder6473d0ThirdSecondMulTable + tableOffset, tables)
        word = word +
            contextWord * u32TableWord63c278(builder6473d0ThirdContext208MulTable + tableOffset, tables)
        word +
            in0Word * u32TableWord63c278(builder6473d0ThirdIn0MulTable + tableOffset, tables)
    }
}

internal fun builder6473d0ThirdSP430Words(sourceWords: List<UInt>): List<UInt> {
    if (sourceWords.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sourceWords.size)
    }
    val tables = FirstPairConfig.tables
    var state: UInt = builder6473d0ThirdSP430StateInit
    val out = ArrayList<UInt>()
    for (index in sourceWords.indices) {
        val word = sourceWords[index]
        val tableOffset = (index * 4) and 0x1c
        state = state * builder6473d0ThirdSP430StateMul + word
        var folded7 = state * builder6473d0ThirdSP430FoldPreMul + builder6473d0ThirdSP430FoldPreAdd
        folded7 = fold32ByNibbles63c278(
            folded7,
            builder6473d0ThirdSP430FoldTable,
            7,
            tables
        )
        val side = state * builder6473d0ThirdSP430SideMul -
            (folded7 shl 28) +
            builder6473d0ThirdSP430SideAdd
        val folded8 = fold32ByNibbles63c278(
            folded7,
            builder6473d0ThirdSP430FoldTable,
            1,
            tables
        )
        out.add(u32TableAffine63c278(
            side,
            builder6473d0ThirdSP430OutMulTable + tableOffset,
            builder6473d0ThirdSP430OutAddTable + tableOffset,
            tables
        ))
        state = folded7 * builder6473d0ThirdSP430NextFolded7Mul +
            folded8 * builder6473d0ThirdSP430NextFolded8Mul +
            builder6473d0ThirdSP430NextAdd
    }
    return out
}
