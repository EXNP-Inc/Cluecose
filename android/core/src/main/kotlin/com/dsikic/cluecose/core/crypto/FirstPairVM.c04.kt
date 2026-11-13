// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

internal fun builder6473d0ThirdStreams(in2: ByteArray, sp488Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (in2.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in2 source", in2.size, builder63c278VectorBytes)
    }
    if (sp488Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp488Words.size)
    }
    val tables = FirstPairConfig.tables
    val bytes = in2
    val aWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0ThirdAWord(readUInt32LE(bytes, index * 4), index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0ThirdBWord(sp488Words[index], index, tables)
    }
    return FpQuad(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Third64c524Workspace(in2: ByteArray, sp488Words: List<UInt>): ByteArray {
    val streams = builder6473d0ThirdStreams(in2, sp488Words)
    return convolutionWorkspaceU64(
        streams.first,
        streams.second,
        builder6473d0ThirdConvBaseAdd,
        builder6473d0ThirdConvCountMul,
        builder6473d0ThirdConvProductMul,
        builder6473d0ThirdConvSumAMul,
        builder6473d0ThirdConvSumBMul,
        builder6473d0ThirdConvFinalMul,
        builder6473d0ThirdConvFinalAdd
    )
}

internal fun builder6473d0FourthStreams(out1Seed: ByteArray, thirdOutput: ByteArray): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (out1Seed.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 out1 seed", out1Seed.size, builder63c278VectorBytes)
    }
    if (thirdOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 third output", thirdOutput.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val out1Bytes = out1Seed
    val thirdBytes = thirdOutput
    val aWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0FourthAWord(readUInt32LE(out1Bytes, index * 4), index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0FourthBWord(readUInt32LE(thirdBytes, index * 4), index, tables)
    }
    return FpQuad(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Fourth64c524Workspace(out1Seed: ByteArray, thirdOutput: ByteArray): ByteArray {
    val streams = builder6473d0FourthStreams(out1Seed, thirdOutput)
    return convolutionWorkspaceU64(
        streams.first,
        streams.second,
        builder6473d0FourthConvBaseAdd,
        builder6473d0FourthConvCountMul,
        builder6473d0FourthConvProductMul,
        builder6473d0FourthConvSumAMul,
        builder6473d0FourthConvSumBMul,
        builder6473d0FourthConvFinalMul,
        builder6473d0FourthConvFinalAdd
    )
}

internal fun builder6473d0FifthSourceWords(fourthOutput: ByteArray, contextSource: ByteArray, in1: ByteArray): List<UInt> {
    if (fourthOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 fourth output", fourthOutput.size, builder63c278VectorBytes)
    }
    if (contextSource.size < 0x1b0) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 context source", contextSource.size, 0x1b0)
    }
    if (in1.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in1 source", in1.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder6473d0FifthStaticQ0, 16),
            TableSegment(builder6473d0FifthStaticQ1, 16),
            TableSegment(builder6473d0FifthStaticQ0, 16),
            TableSegment(builder6473d0FifthStaticQ1, 16),
            TableSegment(builder6473d0FifthStaticQ0, 16),
            TableSegment(builder6473d0FifthStaticD1, 8),
        ),
        tables
    )
    val fourthBytes = fourthOutput
    val contextBytes = contextSource
    val in1Bytes = in1
    return (0 until builder63c278VectorWords).map { index ->
        val tableOffset = (index * 4) and 0x1c
        val fourthWord = readUInt32LE(fourthBytes, index * 4)
        val contextWord = readUInt32LE(contextBytes, 0x158 + index * 4)
        val in1Word = readUInt32LE(in1Bytes, index * 4)
        var word = baseWords[index] +
            fourthWord * u32TableWord63c278(builder6473d0FifthFourthMulTable + tableOffset, tables)
        word = word +
            contextWord * u32TableWord63c278(builder6473d0FifthContext158MulTable + tableOffset, tables)
        word +
            in1Word * u32TableWord63c278(builder6473d0FifthIn1MulTable + tableOffset, tables)
    }
}

internal fun builder6473d0FifthSP3D8Words(sourceWords: List<UInt>): List<UInt> {
    if (sourceWords.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sourceWords.size)
    }
    val tables = FirstPairConfig.tables
    var state: UInt = builder6473d0FifthSP3D8StateInit
    val out = ArrayList<UInt>()
    for (index in sourceWords.indices) {
        val word = sourceWords[index]
        val tableOffset = (index * 4) and 0x1c
        state = state * builder6473d0FifthSP3D8StateMul + word
        var folded7 = state * builder6473d0FifthSP3D8FoldPreMul + builder6473d0FifthSP3D8FoldPreAdd
        folded7 = fold32ByNibbles63c278(
            folded7,
            builder6473d0FifthSP3D8FoldTable,
            7,
            tables
        )
        val side = state * builder6473d0FifthSP3D8SideMul +
            folded7 * builder6473d0FifthSP3D8SideFoldedMul +
            builder6473d0FifthSP3D8SideAdd
        val folded8 = fold32ByNibbles63c278(
            folded7,
            builder6473d0FifthSP3D8FoldTable,
            1,
            tables
        )
        out.add(u32TableAffine63c278(
            side,
            builder6473d0FifthSP3D8OutMulTable + tableOffset,
            builder6473d0FifthSP3D8OutAddTable + tableOffset,
            tables
        ))
        state = folded7 * builder6473d0FifthSP3D8NextFolded7Mul +
            folded8 * builder6473d0FifthSP3D8NextFolded8Mul +
            builder6473d0FifthSP3D8NextAdd
    }
    return out
}

internal fun builder6473d0FifthStreams(sp430Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (sp430Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp430Words.size)
    }
    val tables = FirstPairConfig.tables
    val aWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0FifthAWord(sp430Words[index], index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0FifthBWord(sp430Words[index], index, tables)
    }
    return FpQuad(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Fifth64c524Workspace(sp430Words: List<UInt>): ByteArray {
    val streams = builder6473d0FifthStreams(sp430Words)
    return convolutionWorkspaceU64(
        streams.first,
        streams.second,
        builder6473d0FifthConvBaseAdd,
        builder6473d0FifthConvCountMul,
        builder6473d0FifthConvProductMul,
        builder6473d0FifthConvSumAMul,
        builder6473d0FifthConvSumBMul,
        builder6473d0FifthConvFinalMul,
        builder6473d0FifthConvFinalAdd
    )
}

internal fun builder6473d0SixthSP380Words(fifthOutput: ByteArray): List<UInt> {
    if (fifthOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 fifth output", fifthOutput.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = fifthOutput
    return (0 until builder63c278VectorWords).map { index ->
        u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder6473d0SixthSP380MulTable,
            builder6473d0SixthSP380AddTable,
            tables
        )
    }
}

internal fun builder6473d0SixthStreams(sp430Words: List<UInt>, sp380Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (sp430Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp430Words.size)
    }
    if (sp380Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp380Words.size)
    }
    val tables = FirstPairConfig.tables
    val aWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0SixthSP750Word(sp380Words[index], index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0SixthSP698Word(sp430Words[index], index, tables)
    }
    return FpQuad(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Sixth64c524Workspace(sp430Words: List<UInt>, sp380Words: List<UInt>): ByteArray {
    val streams = builder6473d0SixthStreams(sp430Words, sp380Words)
    return convolutionWorkspaceU64(
        streams.first,
        streams.second,
        builder6473d0SixthConvBaseAdd,
        builder6473d0SixthConvCountMul,
        builder6473d0SixthConvProductMul,
        builder6473d0SixthConvSumAMul,
        builder6473d0SixthConvSumBMul,
        builder6473d0SixthConvFinalMul,
        builder6473d0SixthConvFinalAdd
    )
}

internal fun builder6473d0SeventhSP328Words(sixthOutput: ByteArray): List<UInt> {
    if (sixthOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 sixth output", sixthOutput.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = sixthOutput
    return (0 until builder63c278VectorWords).map { index ->
        u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder6473d0SeventhSP328MulTable,
            builder6473d0SeventhSP328AddTable,
            tables
        )
    }
}

internal fun builder6473d0SeventhStreams(in0: ByteArray, sp380Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (in0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in0", in0.size, builder63c278VectorBytes)
    }
    if (sp380Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp380Words.size)
    }
    val tables = FirstPairConfig.tables
    val in0Bytes = in0
    val aWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0SeventhSP750Word(readUInt32LE(in0Bytes, index * 4), index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0SeventhSP698Word(sp380Words[index], index, tables)
    }
    return FpQuad(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Seventh64c524Workspace(in0: ByteArray, sp380Words: List<UInt>): ByteArray {
    val streams = builder6473d0SeventhStreams(in0, sp380Words)
    return convolutionWorkspaceU64(
        streams.first,
        streams.second,
        builder6473d0SeventhConvBaseAdd,
        builder6473d0SeventhConvCountMul,
        builder6473d0SeventhConvProductMul,
        builder6473d0SeventhConvSumAMul,
        builder6473d0SeventhConvSumBMul,
        builder6473d0SeventhConvFinalMul,
        builder6473d0SeventhConvFinalAdd
    )
}

internal fun builder6473d0EighthSP2D0Words(seventhOutput: ByteArray): List<UInt> {
    if (seventhOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 seventh output", seventhOutput.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = seventhOutput
    return (0 until builder63c278VectorWords).map { index ->
        u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder6473d0EighthSP2D0MulTable,
            builder6473d0EighthSP2D0AddTable,
            tables
        )
    }
}

internal fun builder6473d0EighthStreams(sp3d8Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (sp3d8Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp3d8Words.size)
    }
    val tables = FirstPairConfig.tables
    val aWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0EighthAWord(sp3d8Words[index], index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0EighthBWord(sp3d8Words[index], index, tables)
    }
    return FpQuad(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Eighth64c524Workspace(sp3d8Words: List<UInt>): ByteArray {
    val streams = builder6473d0EighthStreams(sp3d8Words)
    return convolutionWorkspaceU64(
        streams.first,
        streams.second,
        builder6473d0EighthConvBaseAdd,
        builder6473d0EighthConvCountMul,
        builder6473d0EighthConvProductMul,
        builder6473d0EighthConvSumAMul,
        builder6473d0EighthConvSumBMul,
        builder6473d0EighthConvFinalMul,
        builder6473d0EighthConvFinalAdd
    )
}

internal fun builder6473d0NinthFirstSourceWords(eighthOutput: ByteArray, contextSource: ByteArray, sp328Words: List<UInt>, sp2d0Words: List<UInt>): List<UInt> {
    if (eighthOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 eighth output", eighthOutput.size, builder63c278VectorBytes)
    }
    if (contextSource.size < 0x260) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 context source", contextSource.size, 0x260)
    }
    if (sp328Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp328Words.size)
    }
    if (sp2d0Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp2d0Words.size)
    }
    val tables = FirstPairConfig.tables
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder6473d0Ninth1StaticQ0, 16),
            TableSegment(builder6473d0Ninth1StaticQ1, 16),
            TableSegment(builder6473d0Ninth1StaticQ0, 16),
            TableSegment(builder6473d0Ninth1StaticQ1, 16),
            TableSegment(builder6473d0Ninth1StaticQ0, 16),
            TableSegment(builder6473d0Ninth1StaticD1, 8),
        ),
        tables
    )
    val eighthBytes = eighthOutput
    val contextBytes = contextSource
    return (0 until builder63c278VectorWords).map { index ->
        val tableOffset = (index * 4) and 0x1c
        val eighthWord = readUInt32LE(eighthBytes, index * 4)
        val contextWord = readUInt32LE(contextBytes, 0x208 + index * 4)
        val sp2d0Delta = sp2d0Words[index] * u32TableWord63c278(
            builder6473d0Ninth1SP2D0MulTable + tableOffset,
            tables
        )
        baseWords[index] +
            eighthWord * u32TableWord63c278(builder6473d0Ninth1EighthMulTable + tableOffset, tables) +
            contextWord * u32TableWord63c278(builder6473d0Ninth1Context208MulTable + tableOffset, tables) +
            sp328Words[index] * u32TableWord63c278(builder6473d0Ninth1SP328MulTable + tableOffset, tables) +
            sp2d0Delta +
            sp2d0Delta
    }
}

internal fun builder6473d0NinthOut2Words(sourceWords: List<UInt>): List<UInt> {
    if (sourceWords.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sourceWords.size)
    }
    return reducerU32Words63c278(
        sourceWords,
        builder6473d0Ninth1Out3StateInit,
        builder6473d0Ninth1Out3StateMul,
        builder6473d0Ninth1Out3FoldPreMul,
        builder6473d0Ninth1Out3FoldPreAdd,
        builder6473d0Ninth1Out3FoldTable,
        builder6473d0Ninth1Out3SideMul,
        builder6473d0Ninth1Out3SideFoldedMul,
        builder6473d0Ninth1Out3SideAdd,
        builder6473d0Ninth1Out3NextFolded7Mul,
        builder6473d0Ninth1Out3NextFolded8Mul,
        builder6473d0Ninth1Out3NextAdd,
        builder6473d0Ninth1Out3OutMulTable,
        builder6473d0Ninth1Out3OutAddTable
    )
}

internal fun builder6473d0NinthSecondSourceWords(sp2d0Words: List<UInt>, contextSource: ByteArray, out2Words: List<UInt>): List<UInt> {
    if (sp2d0Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp2d0Words.size)
    }
    if (contextSource.size < 0x2b8) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 context source", contextSource.size, 0x2b8)
    }
    if (out2Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(out2Words.size)
    }
    val tables = FirstPairConfig.tables
    val baseWords = u32WordsFromTableSegments(
        listOf(
            TableSegment(builder6473d0Ninth2StaticQ0, 16),
            TableSegment(builder6473d0Ninth2StaticQ1, 16),
            TableSegment(builder6473d0Ninth2StaticQ0, 16),
            TableSegment(builder6473d0Ninth2StaticQ1, 16),
            TableSegment(builder6473d0Ninth2StaticQ0, 16),
            TableSegment(builder6473d0Ninth2StaticD1, 8),
        ),
        tables
    )
    val contextBytes = contextSource
    return (0 until builder63c278VectorWords).map { index ->
        val tableOffset = (index * 4) and 0x1c
        val contextWord = readUInt32LE(contextBytes, 0x260 + index * 4)
        baseWords[index] +
            sp2d0Words[index] * u32TableWord63c278(builder6473d0Ninth2SP2D0MulTable + tableOffset, tables) +
            contextWord * u32TableWord63c278(builder6473d0Ninth2Context260MulTable + tableOffset, tables) +
            out2Words[index] * u32TableWord63c278(builder6473d0Ninth2Out2MulTable + tableOffset, tables)
    }
}

internal fun builder6473d0NinthSP278Words(sourceWords: List<UInt>): List<UInt> {
    if (sourceWords.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sourceWords.size)
    }
    return reducerU32Words63c278(
        sourceWords,
        builder6473d0Ninth2SP278StateInit,
        builder6473d0Ninth2SP278StateMul,
        builder6473d0Ninth2SP278FoldPreMul,
        builder6473d0Ninth2SP278FoldPreAdd,
        builder6473d0Ninth2SP278FoldTable,
        builder6473d0Ninth2SP278SideMul,
        builder6473d0Ninth2SP278SideFoldedMul,
        builder6473d0Ninth2SP278SideAdd,
        builder6473d0Ninth2SP278NextFolded7Mul,
        builder6473d0Ninth2SP278NextFolded8Mul,
        builder6473d0Ninth2SP278NextAdd,
        builder6473d0Ninth2SP278OutMulTable,
        builder6473d0Ninth2SP278OutAddTable
    )
}

internal fun builder6473d0NinthFirstStreams(sp3d8Words: List<UInt>, sp278Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (sp3d8Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp3d8Words.size)
    }
    if (sp278Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp278Words.size)
    }
    val tables = FirstPairConfig.tables
    val aWords = (0 until builder63c278VectorWords).map { index ->
        u64StreamWordFromU32Affine(
            sp3d8Words[index],
            index,
            builder6473d0NinthSP3D8AMulTable,
            builder6473d0NinthSP3D8AAddTable,
            builder6473d0NinthSP3D8AU32Mul,
            builder6473d0NinthSP3D8AU32Add,
            builder6473d0NinthSP3D8AFoldMul,
            builder6473d0NinthSP3D8AFoldAdd,
            builder6473d0NinthSP3D8AFoldTable,
            builder6473d0NinthSP3D8ALinearMul,
            builder6473d0NinthSP3D8AFoldedMul,
            builder6473d0NinthSP3D8ALinearAdd,
            tables
        )
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        u64StreamWordFromU32Affine(
            sp278Words[index],
            index,
            builder6473d0NinthSP278BMulTable,
            builder6473d0NinthSP278BAddTable,
            builder6473d0NinthSP278BU32Mul,
            builder6473d0NinthSP278BU32Add,
            builder6473d0NinthSP278BFoldMul,
            builder6473d0NinthSP278BFoldAdd,
            builder6473d0NinthSP278BFoldTable,
            builder6473d0NinthSP278BLinearMul,
            builder6473d0NinthSP278BFoldedMul,
            builder6473d0NinthSP278BLinearAdd,
            tables
        )
    }
    return FpQuad(aWords, bWords, shiftedPrefixSumsU64(aWords), shiftedPrefixSumsU64(bWords))
}

internal fun builder6473d0NinthSP1C8Words(aWords: List<ULong>, bWords: List<ULong>): List<UInt> {
    return convolutionReducerU32Words63c278(
        aWords,
        bWords,
        builder6473d0NinthConv1StateInit,
        builder6473d0NinthConv1CountMul,
        builder6473d0NinthConv1ProductMul,
        builder6473d0NinthConv1SumAMul,
        builder6473d0NinthConv1SumBMul,
        builder6473d0NinthConv1FoldPreMul,
        builder6473d0NinthConv1FoldPreAdd,
        builder6473d0NinthConv1FoldTable,
        builder6473d0NinthConv1SideMul,
        builder6473d0NinthConv1SideFoldedMul,
        builder6473d0NinthConv1SideAdd,
        builder6473d0NinthConv1NextFolded8Mul,
        builder6473d0NinthConv1NextFolded16Mul,
        builder6473d0NinthConv1NextAdd,
        builder6473d0NinthConv1OutMulTable,
        builder6473d0NinthConv1OutAddTable
    )
}

internal fun builder6473d0NinthSecondStreams(in1: ByteArray, sp328Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (in1.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in1 source", in1.size, builder63c278VectorBytes)
    }
    if (sp328Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp328Words.size)
    }
    val tables = FirstPairConfig.tables
    val in1Bytes = in1
    val aWords = (0 until builder63c278VectorWords).map { index ->
        u64StreamWordFromU32Affine(
            readUInt32LE(in1Bytes, index * 4),
            index,
            builder6473d0NinthIn1AMulTable,
            builder6473d0NinthIn1AAddTable,
            builder6473d0NinthIn1AU32Mul,
            builder6473d0NinthIn1AU32Add,
            builder6473d0NinthIn1AFoldMul,
            builder6473d0NinthIn1AFoldAdd,
            builder6473d0NinthIn1AFoldTable,
            builder6473d0NinthIn1ALinearMul,
            builder6473d0NinthIn1AFoldedMul,
            builder6473d0NinthIn1ALinearAdd,
            tables
        )
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        u64StreamWordFromU32Affine(
            sp328Words[index],
            index,
            builder6473d0NinthSP328BMulTable,
            builder6473d0NinthSP328BAddTable,
            builder6473d0NinthSP328BU32Mul,
            builder6473d0NinthSP328BU32Add,
            builder6473d0NinthSP328BFoldMul,
            builder6473d0NinthSP328BFoldAdd,
            builder6473d0NinthSP328BFoldTable,
            builder6473d0NinthSP328BLinearMul,
            builder6473d0NinthSP328BFoldedMul,
            builder6473d0NinthSP328BLinearAdd,
            tables
        )
    }
    return FpQuad(aWords, bWords, shiftedPrefixSumsU64(aWords), shiftedPrefixSumsU64(bWords))
}

internal fun builder6473d0NinthSP118Words(aWords: List<ULong>, bWords: List<ULong>): List<UInt> {
    return convolutionReducerU32Words63c278(
        aWords,
        bWords,
        builder6473d0NinthConv2StateInit,
        builder6473d0NinthConv2CountMul,
        builder6473d0NinthConv2ProductMul,
        builder6473d0NinthConv2SumAMul,
        builder6473d0NinthConv2SumBMul,
        builder6473d0NinthConv2FoldPreMul,
        builder6473d0NinthConv2FoldPreAdd,
        builder6473d0NinthConv2FoldTable,
        builder6473d0NinthConv2SideMul,
        builder6473d0NinthConv2SideFoldedMul,
        builder6473d0NinthConv2SideAdd,
        builder6473d0NinthConv2NextFolded8Mul,
        builder6473d0NinthConv2NextFolded16Mul,
        builder6473d0NinthConv2NextAdd,
        builder6473d0NinthConv2OutMulTable,
        builder6473d0NinthConv2OutAddTable
    )
}

internal fun builder6473d0NinthThirdSourceWords(sp1c8Words: List<UInt>, contextSource: ByteArray, sp118Words: List<UInt>): List<UInt> {
    if (sp1c8Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp1c8Words.size)
    }
    if (contextSource.size < 0x368) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 context source", contextSource.size, 0x368)
    }
    if (sp118Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp118Words.size)
    }
    val tables = FirstPairConfig.tables
    val contextBytes = contextSource
    return (0 until builder64bd0cWorkspaceWords).map { index ->
        val tableOffset = (index * 4) and 0x1c
        val contextWord = readUInt32LE(contextBytes, 0x2b8 + index * 4)
        u32TableWord63c278(builder6473d0Ninth3StaticTable + ((index and 7) * 4), tables) +
            sp1c8Words[index] * u32TableWord63c278(
                builder6473d0Ninth3SP1C8MulTable + tableOffset,
                tables
            ) +
            contextWord * u32TableWord63c278(
                builder6473d0Ninth3Context2B8MulTable + tableOffset,
                tables
            ) +
            sp118Words[index] * u32TableWord63c278(
                builder6473d0Ninth3SP118MulTable + tableOffset,
                tables
            )
    }
}

internal fun builder6473d0NinthSP68Words(sourceWords: List<UInt>): List<UInt> {
    if (sourceWords.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sourceWords.size)
    }
    return reducerU32Words63c278(
        sourceWords,
        builder6473d0Ninth3SP68StateInit,
        builder6473d0Ninth3SP68StateMul,
        builder6473d0Ninth3SP68FoldPreMul,
        builder6473d0Ninth3SP68FoldPreAdd,
        builder6473d0Ninth3SP68FoldTable,
        builder6473d0Ninth3SP68SideMul,
        builder6473d0Ninth3SP68SideFoldedMul,
        builder6473d0Ninth3SP68SideAdd,
        builder6473d0Ninth3SP68NextFolded7Mul,
        builder6473d0Ninth3SP68NextFolded8Mul,
        builder6473d0Ninth3SP68NextAdd,
        builder6473d0Ninth3SP68OutMulTable,
        builder6473d0Ninth3SP68OutAddTable
    )
}

internal fun builder6473d0Ninth64c524Workspace(sp68Words: List<UInt>): ByteArray {
    if (sp68Words.size != builder64bd0cWorkspaceWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp68Words.size)
    }
    val tables = FirstPairConfig.tables
    val words = (0 until builder64bd0cWorkspaceWords).map { index ->
        u64StreamWordFromU32Affine(
            sp68Words[index],
            index,
            builder6473d0NinthWorkspaceMulTable,
            builder6473d0NinthWorkspaceAddTable,
            builder6473d0NinthWorkspaceU32Mul,
            builder6473d0NinthWorkspaceU32Add,
            builder6473d0NinthWorkspaceFoldMul,
            builder6473d0NinthWorkspaceFoldAdd,
            builder6473d0NinthWorkspaceFoldTable,
            builder6473d0NinthWorkspaceLinearMul,
            builder6473d0NinthWorkspaceFoldedMul,
            builder6473d0NinthWorkspaceLinearAdd,
            tables
        )
    }
    return packUInt64LE64cd40(words)
}

internal fun builder6473d0TenthOut3Words(ninthOutput: ByteArray): List<UInt> {
    if (ninthOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 ninth output", ninthOutput.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = ninthOutput
    return (0 until builder63c278VectorWords).map { index ->
        u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder6473d0TenthOut3MulTable,
            builder6473d0TenthOut3AddTable,
            tables
        )
    }
}

internal fun builder6473d0TenthStreams(in2: ByteArray, sp430Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (in2.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in2 source", in2.size, builder63c278VectorBytes)
    }
    if (sp430Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp430Words.size)
    }
    val tables = FirstPairConfig.tables
    val in2Bytes = in2
    val aWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0TenthAWord(readUInt32LE(in2Bytes, index * 4), index, tables)
    }
    val bWords = (0 until builder63c278VectorWords).map { index ->
        builder6473d0TenthBWord(sp430Words[index], index, tables)
    }
    return FpQuad(aWords, bWords, prefixSumsU64(aWords), prefixSumsU64(bWords))
}

internal fun builder6473d0Tenth64c524Workspace(in2: ByteArray, sp430Words: List<UInt>): ByteArray {
    val streams = builder6473d0TenthStreams(in2, sp430Words)
    return convolutionWorkspaceU64(
        streams.first,
        streams.second,
        builder6473d0TenthConvBaseAdd,
        builder6473d0TenthConvCountMul,
        builder6473d0TenthConvProductMul,
        builder6473d0TenthConvSumAMul,
        builder6473d0TenthConvSumBMul,
        builder6473d0TenthConvFinalMul,
        builder6473d0TenthConvFinalAdd
    )
}

internal fun builder6473d0FinalOut4Words(tenthOutput: ByteArray): List<UInt> {
    if (tenthOutput.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 tenth output", tenthOutput.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = tenthOutput
    return (0 until builder63c278VectorWords).map { index ->
        u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder6473d0FinalOut4MulTable,
            builder6473d0FinalOut4AddTable,
            tables
        )
    }
}

internal fun builder6473d0Outputs(in0: ByteArray, in1: ByteArray, in2: ByteArray, contextSource: ByteArray, out0Preimage: ByteArray? = null, out1Preimage: ByteArray? = null): Builder6473d0Result {
    if (in0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in0", in0.size, builder63c278VectorBytes)
    }
    if (in1.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in1", in1.size, builder63c278VectorBytes)
    }
    if (in2.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 in2", in2.size, builder63c278VectorBytes)
    }
    if (contextSource.size < 0x420) {
        throw FirstPairSourceSliceError.sourceTooShort("6473d0 context source", contextSource.size, 0x420)
    }

    fun resolvedPreimage(value: ByteArray?, label: String): ByteArray {
        val v = value ?: return ByteArray(builder63c278VectorBytes)
        if (v.size < builder63c278VectorBytes) {
            throw FirstPairSourceSliceError.sourceTooShort(label, v.size, builder63c278VectorBytes)
        }
        return v.copyOfRange(0, builder63c278VectorBytes)
    }

    val contextBytes = contextSource
    val arg0 = contextBytes.copyOfRange(0x100, 0x158)
    val scalar = readUInt64LE(contextBytes, 0x418)
    val out0Seed = resolvedPreimage(out0Preimage, "6473d0 out0 preimage")
    val out1Seed = resolvedPreimage(out1Preimage, "6473d0 out1 preimage")

    val firstOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0First64c524Workspace(in2)
    ))
    val sp488Words = builder6473d0SP488WordsFrom64c524Output(firstOutput)

    val secondOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0Second64c524Workspace(
            out0Seed,
            sp488Words
        )
    ))
    val thirdSourceWords = builder6473d0ThirdSourceWords(
        secondOutput,
        contextSource,
        in0
    )
    val thirdSP430Words = builder6473d0ThirdSP430Words(thirdSourceWords)

    val thirdOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0Third64c524Workspace(
            in2,
            sp488Words
        )
    ))
    val fourthOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0Fourth64c524Workspace(
            out1Seed,
            thirdOutput
        )
    ))
    val fifthSourceWords = builder6473d0FifthSourceWords(
        fourthOutput,
        contextSource,
        in1
    )
    val fifthSP3D8Words = builder6473d0FifthSP3D8Words(fifthSourceWords)

    val fifthOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0Fifth64c524Workspace(thirdSP430Words)
    ))
    val sixthSP380Words = builder6473d0SixthSP380Words(fifthOutput)
    val sixthOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0Sixth64c524Workspace(
            thirdSP430Words,
            sixthSP380Words
        )
    ))
    val seventhSP328Words = builder6473d0SeventhSP328Words(sixthOutput)
    val seventhOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0Seventh64c524Workspace(
            in0,
            sixthSP380Words
        )
    ))
    val eighthSP2D0Words = builder6473d0EighthSP2D0Words(seventhOutput)
    val eighthOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0Eighth64c524Workspace(fifthSP3D8Words)
    ))

    val out2Words = builder6473d0NinthOut2Words(
        builder6473d0NinthFirstSourceWords(
            eighthOutput,
            contextSource,
            seventhSP328Words,
            eighthSP2D0Words
        )
    )
    val ninthSP278Words = builder6473d0NinthSP278Words(
        builder6473d0NinthSecondSourceWords(
            eighthSP2D0Words,
            contextSource,
            out2Words
        )
    )
    val ninthFirstStreams = builder6473d0NinthFirstStreams(
        fifthSP3D8Words,
        ninthSP278Words
    )
    val ninthSP1C8Words = builder6473d0NinthSP1C8Words(
        ninthFirstStreams.first,
        ninthFirstStreams.second
    )
    val ninthSecondStreams = builder6473d0NinthSecondStreams(
        in1,
        seventhSP328Words
    )
    val ninthSP118Words = builder6473d0NinthSP118Words(
        ninthSecondStreams.first,
        ninthSecondStreams.second
    )
    val ninthSP68Words = builder6473d0NinthSP68Words(
        builder6473d0NinthThirdSourceWords(
            ninthSP1C8Words,
            contextSource,
            ninthSP118Words
        )
    )
    val ninthOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0Ninth64c524Workspace(ninthSP68Words)
    ))
    val out3Words = builder6473d0TenthOut3Words(ninthOutput)
    val tenthOutput = packUInt32LE(builder64c524OutputWords(
        arg0,
        scalar,
        builder6473d0Tenth64c524Workspace(
            in2,
            thirdSP430Words
        )
    ))
    val out4Words = builder6473d0FinalOut4Words(tenthOutput)

    return Builder6473d0Result(
        in0.copyOfRange(0, builder63c278VectorBytes),
        in1.copyOfRange(0, builder63c278VectorBytes),
        in2.copyOfRange(0, builder63c278VectorBytes),
        out0Seed,
        out1Seed,
        packUInt32LE(out2Words),
        packUInt32LE(out3Words),
        packUInt32LE(out4Words)
    )
}

internal fun builder6473d0OutputsFromBundledContext(in0: ByteArray, in1: ByteArray, in2: ByteArray, out0Preimage: ByteArray? = null, out1Preimage: ByteArray? = null): Builder6473d0Result {
    val context = builder6388f0SharedContextFromBundle()
    return builder6473d0Outputs(
        in0,
        in1,
        in2,
        context,
        out0Preimage,
        out1Preimage
    )
}

internal fun builder6473d0MinimalStack20FromPreimages(preimages: Builder6473d0OutputPreimages): ByteArray {
    val stack20 = ByteArray(builder6473d0CallerStackPreimageBytes)
    val vectors: List<FpTriple<ByteArray, Int, String>> = listOf(
        FpTriple(preimages.out4, 0x000, "out4"),
        FpTriple(preimages.out3, 0x058, "out3"),
        FpTriple(preimages.out2, 0x0b0, "out2"),
        FpTriple(preimages.out1, 0x210, "out1"),
        FpTriple(preimages.out0, 0x268, "out0"),
    )

    for (vector in vectors) {
        if (vector.first.size < builder63c278VectorBytes) {
            throw FirstPairSourceSliceError.sourceTooShort(
                "6473d0 ${vector.third} preimage",
                vector.first.size,
                builder63c278VectorBytes
            )
        }
        replace(
            stack20,
            vector.second,
            vector.first.copyOfRange(0, builder63c278VectorBytes)
        )
    }
    return stack20
}

internal fun builder6473d0PostVectors(result: Builder6473d0Result): Map<Int, ByteArray> {
    return mapOf(
        0x3708 to result.out4,
        0x3760 to result.out3,
        0x37b8 to result.out2,
        0x3810 to result.in2After,
        0x3868 to result.in1After,
        0x38c0 to result.in0After,
        0x3918 to result.out1,
        0x3970 to result.out0
    )
}
