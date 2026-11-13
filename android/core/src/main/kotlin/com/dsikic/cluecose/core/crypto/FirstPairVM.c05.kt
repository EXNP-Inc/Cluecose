// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

internal fun builder6388f0First64cd40CallState(contextSource: ByteArray, callerStack20: ByteArray, postVectors: Map<Int, ByteArray>, entryIndex: Int): Builder6388f0Caller64CallState {
    if (contextSource.size < 0x420) {
        throw FirstPairSourceSliceError.sourceTooShort("6388f0 caller context", contextSource.size, 0x420)
    }
    if (callerStack20.size < builder6473d0CallerStackPreimageBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6388f0 caller stack20", callerStack20.size, builder6473d0CallerStackPreimageBytes)
    }
    if (entryIndex < 0) {
        throw FirstPairSourceSliceError.invalid6388f0EntryIndex(entryIndex)
    }

    val tables = FirstPairConfig.tables
    val contextBytes = contextSource
    val stack = ByteArray(builder6388f0CallerStackBytes)
    checkedReplace(stack, 0x230, contextSource)
    checkedReplace(stack, 0x3708, callerStack20)
    for ((offset, raw) in postVectors) {
        checkedReplace(stack, offset, raw)
    }

    val loopSlot = entryIndex % builder6388f0CallerLoopTableRows
    val loopCounter = builder6388f0CallerLoopTableRows - 1 - loopSlot
    val pointerDelta = loopSlot * builder6388f0CallerLoopRowBytes

    val aMul: ULong = 0x5025a2599f75877fUL
    val aAdd: ULong = 0x4d8a8810a4bbc5a3UL
    val bMul: ULong = 0x23c3d48d0602f787UL
    val bAdd: ULong = 0x62917fc875cc9e6bUL
    val firstMixMul: ULong = 0x6f8d70f401079e5bUL
    val firstMixAdd: ULong = 0x31b3e556163432edUL
    val callerMixMul: ULong = 0x30eef2ed3a43a4f9UL
    val callerMixAdd: ULong = 0x92ef60a176c7d6c9UL
    val firstFoldMul: ULong = 0x838e88db00000000UL
    val callerFoldMul: ULong = 0x37fd608100000000UL

    var firstSrcWord = readUInt32LE(stack, 0x38c0) * 0xc938d835U + 0xe6fc451bU
    var callerWord = readUInt32LE(stack, 0x6f8 + loopCounter * 0x58) * 0xc955b06bU + 0x454427dfU
    val firstB = builder6388f0CallerStreamU64(
        firstSrcWord,
        aMul,
        aAdd,
        0x300ef0,
        firstFoldMul,
        firstMixMul,
        firstMixAdd,
        tables
    )
    val firstA = builder6388f0CallerStreamU64(
        callerWord,
        bMul,
        bAdd,
        0x300f70,
        callerFoldMul,
        callerMixMul,
        callerMixAdd,
        tables
    )
    writeUInt64LE(firstB, stack, 0x3b80)
    writeUInt64LE(firstB, stack, 0x4130)
    writeUInt64LE(firstA, stack, 0x39c8)
    writeUInt64LE(firstA, stack, 0x4010)

    var prefixB = firstB
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x38c0 + index * 4)
        word = word * u32TableWord63c278(0x112e28 + tableOffset, tables) +
            u32TableWord63c278(0x117268 + tableOffset, tables)
        word = word * 0x56d9f19bU + 0x64a9155bU
        val value = builder6388f0CallerStreamU64(
            word,
            aMul,
            aAdd,
            0x300ef0,
            firstFoldMul,
            firstMixMul,
            firstMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x3b80 + index * 8)
        prefixB = prefixB + value
        writeUInt64LE(prefixB, stack, 0x4130 + index * 8)
    }

    var prefixA = firstA
    var callerStream = 0x1aec - pointerDelta
    for (index in 0 until (builder63c278VectorWords - 1)) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x117288 + tableOffset, tables) +
            u32TableWord63c278(0x1205e8 + tableOffset, tables)
        word = word * 0x994a2aa3U + 0x7f433349U
        val value = builder6388f0CallerStreamU64(
            word,
            bMul,
            bAdd,
            0x300f70,
            callerFoldMul,
            callerMixMul,
            callerMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x39d0 + index * 8)
        prefixA = prefixA + value
        writeUInt64LE(prefixA, stack, 0x4018 + index * 8)
    }

    builder6388f0Convolution44(
        stack,
        0x39c8,
        0x4010,
        0x3b80,
        0x4130,
        0x3ce0,
        Process2P5PublicConstants(
            0x4079ef92755bf93aUL,
            0xb43c87132a6e84d1UL,
            0x129a56bce90af833UL,
            0x8de93a973ee9c82bUL,
            0xb3c2bc6591a8beaaUL,
            0xe6bf6d3dc98f10f7UL,
            0x632718706bc72397UL
        )
    )

    val cMul: ULong = 0x877a8a4a5f3b0f49UL
    val cAdd: ULong = 0xa24f4a31979cc775UL
    val dMul: ULong = 0xddfbefdc018359d5UL
    val dAdd: ULong = 0x7f589737aa46bdd5UL
    val cMixMul: ULong = 0x5a83f7862436b279UL
    val cMixAdd: ULong = 0x77cf4bf823a845d0UL
    val dMixMul: ULong = 0x080c881a27926eee7UL
    val dMixAdd: ULong = 0xeabaf4ef841c8c86UL
    val cFoldMul: ULong = 0xde34e64f00000000UL
    val dFoldMul: ULong = 0x438d983500000000UL

    firstSrcWord = readUInt32LE(stack, 0x37b8) * 0xff9582fdU + 0xfc52cb23U
    callerWord = readUInt32LE(stack, 0x1b40 + loopCounter * 0x58) * 0xad09fb4bU + 0x4d566e95U
    val firstC = builder6388f0CallerStreamU64(
        firstSrcWord,
        cMul,
        cAdd,
        0x300ff0,
        cFoldMul,
        cMixMul,
        cMixAdd,
        tables
    )
    val firstD = builder6388f0CallerStreamU64(
        callerWord,
        dMul,
        dAdd,
        0x301070,
        dFoldMul,
        dMixMul,
        dMixAdd,
        tables
    )
    writeUInt64LE(firstC, stack, 0x39c8)
    writeUInt64LE(firstC, stack, 0x4010)
    writeUInt64LE(firstD, stack, 0x4130)
    writeUInt64LE(firstD, stack, 0x3ef0)

    var prefixC = firstC
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x37b8 + index * 4)
        word = word * u32TableWord63c278(0x1218c8 + tableOffset, tables) +
            u32TableWord63c278(0x1221e8 + tableOffset, tables)
        word = word * 0x2c6e5d55U + 0x63f5202dU
        val value = builder6388f0CallerStreamU64(
            word,
            cMul,
            cAdd,
            0x300ff0,
            cFoldMul,
            cMixMul,
            cMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x39c8 + index * 8)
        prefixC = prefixC + value
        writeUInt64LE(prefixC, stack, 0x4010 + index * 8)
    }

    var prefixD = firstD
    callerStream = 0x2f34 - pointerDelta
    for (index in 0 until (builder63c278VectorWords - 1)) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x119708 + tableOffset, tables) +
            u32TableWord63c278(0x120608 + tableOffset, tables)
        word = word * 0x206cd1f3U + 0x867e396dU
        val value = builder6388f0CallerStreamU64(
            word,
            dMul,
            dAdd,
            0x301070,
            dFoldMul,
            dMixMul,
            dMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x4138 + index * 8)
        prefixD = prefixD + value
        writeUInt64LE(prefixD, stack, 0x3ef8 + index * 8)
    }

    builder6388f0Convolution44(
        stack,
        0x4130,
        0x3ef0,
        0x39c8,
        0x4010,
        0x3b80,
        Process2P5PublicConstants(
            0xd16513f43f99d2c0UL,
            0x5bdc507e86f7d211UL,
            0x49fd76daa54ce93bUL,
            0x4a15e654a01bea9eUL,
            0xe49b61c39c833ce0UL,
            0x9054b9a41de45a5bUL,
            0x9b016b93e5b24765UL
        )
    )

    for (offset in 0 until 0x160 step 0x10) {
        val firstA = readUInt64LE(stack, 0x3ce0 + offset)
        val secondA = readUInt64LE(stack, 0x3ce0 + offset + 8)
        val firstB = readUInt64LE(stack, 0x3b80 + offset)
        val secondB = readUInt64LE(stack, 0x3b80 + offset + 8)
        writeUInt64LE(
            firstA * 0xb8bc9deccc0ade89UL
                + 0xc46ffd16f1b1756fUL
                + firstB * 0x9c308b62a744c677UL,
            stack,
            0x39c8 + offset
        )
        writeUInt64LE(
            secondA * 0xb8bc9deccc0ade89UL
                + 0xc46ffd16f1b1756fUL
                + secondB * 0x9c308b62a744c677UL,
            stack,
            0x39c8 + offset + 8
        )
    }

    return Builder6388f0Caller64CallState(
        stack.copyOfRange(0x330, 0x388),
        readUInt64LE(contextBytes, 0x418),
        stack.copyOfRange(0x39c8, 0x3b28),
        stack.copyOfRange(0x3b28, 0x3b80),
        stack.copyOfRange(0x3778, 0x42c8)
    )
}

internal fun builder6388f0Second64cd40CallState(contextSource: ByteArray, callerStack20: ByteArray, postVectors: Map<Int, ByteArray>, first64cd40Output: ByteArray, entryIndex: Int): Builder6388f0Caller64CallState {
    if (contextSource.size < 0x420) {
        throw FirstPairSourceSliceError.sourceTooShort("6388f0 caller context", contextSource.size, 0x420)
    }
    if (callerStack20.size < builder6473d0CallerStackPreimageBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6388f0 caller stack20", callerStack20.size, builder6473d0CallerStackPreimageBytes)
    }
    if (first64cd40Output.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("first 64cd40 output", first64cd40Output.size, builder63c278VectorBytes)
    }
    if (entryIndex < 0) {
        throw FirstPairSourceSliceError.invalid6388f0EntryIndex(entryIndex)
    }

    val tables = FirstPairConfig.tables
    val contextBytes = contextSource
    val stack = ByteArray(builder6388f0CallerStackBytes)
    checkedReplace(stack, 0x230, contextSource)
    checkedReplace(stack, 0x3708, callerStack20)
    for ((offset, raw) in postVectors) {
        checkedReplace(stack, offset, raw)
    }
    checkedReplace(stack, 0x3b28, first64cd40Output.copyOfRange(0, builder63c278VectorBytes))

    val loopSlot = entryIndex % builder6388f0CallerLoopTableRows
    val loopCounter = builder6388f0CallerLoopTableRows - 1 - loopSlot
    val pointerDelta = loopSlot * builder6388f0CallerLoopRowBytes
    writeUInt32LE(readUInt32LE(stack, 0x6f8 + loopCounter * 0x58), stack, 0x44)
    writeUInt32LE(readUInt32LE(stack, 0x1b40 + loopCounter * 0x58), stack, 0x40)

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val word = readUInt32LE(stack, 0x3b28 + index * 4) *
            foldTableU32Word63c278(0x300ff0 + tableOffset, tables) +
            u32TableWord63c278(0x1184c8 + tableOffset, tables)
        writeUInt32LE(word, stack, 0x154 + index * 4)
    }

    var callerWord = readUInt32LE(stack, 0x44) * 0xa2e10181U + 0xd0b84b4aU
    var postWord = readUInt32LE(stack, 0x3868) * 0xea9bc62bU + 0x295fb23dU
    var callerWordMul: ULong = 0x67eb8e340bf68eddUL
    var callerWordAdd: ULong = 0x7194bb146d6a6c98UL
    var callerMixMul: ULong = 0x412cb68339b36b19UL
    var callerMixAdd: ULong = 0xe7c0e7165633369bUL
    var callerFoldMul: ULong = 0xf883fc9300000000UL
    var postWordMul: ULong = 0x1e34bf9de310fbcbUL
    var postWordAdd: ULong = 0xe80eb386bd2c7669UL
    var postMixMul: ULong = 0x3f2d22f0405cf24fUL
    var postMixAdd: ULong = 0x316c36e4735ae9bcUL
    var postFoldMul: ULong = 0xa4e2a4f300000000UL

    var callerValue = builder6388f0CallerStreamU64(
        callerWord,
        callerWordMul,
        callerWordAdd,
        0x3013f0,
        callerFoldMul,
        callerMixMul,
        callerMixAdd,
        tables
    )
    var postValue = builder6388f0CallerStreamU64(
        postWord,
        postWordMul,
        postWordAdd,
        0x301370,
        postFoldMul,
        postMixMul,
        postMixAdd,
        tables
    )
    writeUInt64LE(callerValue, stack, 0x4130)
    writeUInt64LE(callerValue, stack, 0x3ef0)
    writeUInt64LE(postValue, stack, 0x3b80)
    writeUInt64LE(postValue, stack, 0x4010)

    var prefixPost = postValue
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x3868 + index * 4)
        word = word * u32TableWord63c278(0x11c3c8 + tableOffset, tables) +
            u32TableWord63c278(0x11d4c8 + tableOffset, tables)
        word = word * 0xc99643bbU + 0xac352509U
        val value = builder6388f0CallerStreamU64(
            word,
            postWordMul,
            postWordAdd,
            0x301370,
            postFoldMul,
            postMixMul,
            postMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x3b80 + index * 8)
        prefixPost = prefixPost + value
        writeUInt64LE(prefixPost, stack, 0x4010 + index * 8)
    }

    var prefixCaller = callerValue
    var callerStream = 0x1aec - pointerDelta
    for (index in 0 until (builder63c278VectorWords - 1)) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x1125e8 + tableOffset, tables) +
            u32TableWord63c278(0x118f08 + tableOffset, tables)
        word = word * 0x31bbe0b7U + 0x3fe25e18U
        val value = builder6388f0CallerStreamU64(
            word,
            callerWordMul,
            callerWordAdd,
            0x3013f0,
            callerFoldMul,
            callerMixMul,
            callerMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x4138 + index * 8)
        prefixCaller = prefixCaller + value
        writeUInt64LE(prefixCaller, stack, 0x3ef8 + index * 8)
    }

    builder6388f0Convolution44(
        stack,
        0x4130,
        0x3ef0,
        0x3b80,
        0x4010,
        0x3ce0,
        Process2P5PublicConstants(
            0x31387af6df27bc34UL,
            0x5e7eda1d7e652662UL,
            0xdc67c7dbf68b7273UL,
            0x8f98298f0679fa22UL,
            0x662a1479caab56ceUL,
            0x26efbb4b51cdc6b5UL,
            0xc5b3a8b6b472e5d3UL
        )
    )

    callerWord = readUInt32LE(stack, 0x40) * 0x63dc1441U + 0xda7427c7U
    postWord = readUInt32LE(stack, 0x3760) * 0xe609bd27U + 0x93c1ccd4U
    callerWordMul = 0xdca944fb28ac47f7UL
    callerWordAdd = 0xd57d3e716bf087fcUL
    callerMixMul = 0xb241122944abe41dUL
    callerMixAdd = 0xeb98df0f724a8bc5UL
    callerFoldMul = 0xd584887500000000UL
    postWordMul = 0x86835750d0f2d33dUL
    postWordAdd = 0x93d6710e2805c2cdUL
    postMixMul = 0x37836d2c6f35aeafUL
    postMixAdd = 0xabfb5017ca2ca427UL
    postFoldMul = 0x749f87a500000000UL

    callerValue = builder6388f0CallerStreamU64(
        callerWord,
        callerWordMul,
        callerWordAdd,
        0x3014f0,
        callerFoldMul,
        callerMixMul,
        callerMixAdd,
        tables
    )
    postValue = builder6388f0CallerStreamU64(
        postWord,
        postWordMul,
        postWordAdd,
        0x301470,
        postFoldMul,
        postMixMul,
        postMixAdd,
        tables
    )
    writeUInt64LE(callerValue, stack, 0x4010)
    writeUInt64LE(callerValue, stack, 0x3e40)
    writeUInt64LE(postValue, stack, 0x4130)
    writeUInt64LE(postValue, stack, 0x3ef0)

    prefixPost = postValue
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x3760 + index * 4)
        word = word * u32TableWord63c278(0x11e588 + tableOffset, tables) +
            u32TableWord63c278(0x122a68 + tableOffset, tables)
        word = word * 0x712dee2fU + 0xecb470d1U
        val value = builder6388f0CallerStreamU64(
            word,
            postWordMul,
            postWordAdd,
            0x301470,
            postFoldMul,
            postMixMul,
            postMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x4130 + index * 8)
        prefixPost = prefixPost + value
        writeUInt64LE(prefixPost, stack, 0x3ef0 + index * 8)
    }

    prefixCaller = callerValue
    callerStream = 0x2f34 - pointerDelta
    for (index in 0 until (builder63c278VectorWords - 1)) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x120628 + tableOffset, tables) +
            u32TableWord63c278(0x122a88 + tableOffset, tables)
        word = word * 0x26f75f39U + 0x1c4c83fcU
        val value = builder6388f0CallerStreamU64(
            word,
            callerWordMul,
            callerWordAdd,
            0x3014f0,
            callerFoldMul,
            callerMixMul,
            callerMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x4018 + index * 8)
        prefixCaller = prefixCaller + value
        writeUInt64LE(prefixCaller, stack, 0x3e48 + index * 8)
    }

    builder6388f0Convolution44(
        stack,
        0x4010,
        0x3e40,
        0x4130,
        0x3ef0,
        0x3b80,
        Process2P5PublicConstants(
            0xf8f0e2182e743120UL,
            0x2ea75adafa845934UL,
            0x49eba04bc8aba147UL,
            0x2ae8b5655df9be65UL,
            0xcc8eaf52163f5260UL,
            0xfee73c0f7de3fa41UL,
            0x7572d2a401ed3b6aUL
        )
    )

    for (offset in 0 until 0x160 step 0x10) {
        val firstA = readUInt64LE(stack, 0x3ce0 + offset)
        val secondA = readUInt64LE(stack, 0x3ce0 + offset + 8)
        val firstB = readUInt64LE(stack, 0x3b80 + offset)
        val secondB = readUInt64LE(stack, 0x3b80 + offset + 8)
        writeUInt64LE(
            firstA * 0xf6ebf5f38b50e6e5UL
                + 0x08494646ffdad49aUL
                + firstB * 0x26f0954510cb129fUL,
            stack,
            0x39c8 + offset
        )
        writeUInt64LE(
            secondA * 0xf6ebf5f38b50e6e5UL
                + 0x08494646ffdad49aUL
                + secondB * 0x26f0954510cb129fUL,
            stack,
            0x39c8 + offset + 8
        )
    }

    return Builder6388f0Caller64CallState(
        stack.copyOfRange(0x330, 0x388),
        readUInt64LE(contextBytes, 0x418),
        stack.copyOfRange(0x39c8, 0x3b28),
        stack.copyOfRange(0x3b28, 0x3b80),
        stack.copyOfRange(0x3778, 0x42c8)
    )
}

internal fun builder6388f0Third64cd40CallState(contextSource: ByteArray, callerStack20: ByteArray, postVectors: Map<Int, ByteArray>, second64cd40Output: ByteArray, entryIndex: Int): Builder6388f0Caller64CallState {
    if (contextSource.size < 0x420) {
        throw FirstPairSourceSliceError.sourceTooShort("6388f0 caller context", contextSource.size, 0x420)
    }
    if (callerStack20.size < builder6473d0CallerStackPreimageBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6388f0 caller stack20", callerStack20.size, builder6473d0CallerStackPreimageBytes)
    }
    if (second64cd40Output.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("second 64cd40 output", second64cd40Output.size, builder63c278VectorBytes)
    }
    if (entryIndex < 0) {
        throw FirstPairSourceSliceError.invalid6388f0EntryIndex(entryIndex)
    }

    val tables = FirstPairConfig.tables
    val contextBytes = contextSource
    val stack = ByteArray(builder6388f0CallerStackBytes)
    checkedReplace(stack, 0x230, contextSource)
    checkedReplace(stack, 0x3708, callerStack20)
    for ((offset, raw) in postVectors) {
        checkedReplace(stack, offset, raw)
    }
    checkedReplace(stack, 0x3b28, second64cd40Output.copyOfRange(0, builder63c278VectorBytes))

    val loopSlot = entryIndex % builder6388f0CallerLoopTableRows
    val loopCounter = builder6388f0CallerLoopTableRows - 1 - loopSlot
    val pointerDelta = loopSlot * builder6388f0CallerLoopRowBytes
    writeUInt32LE(readUInt32LE(stack, 0x6f8 + loopCounter * 0x58), stack, 0x44)
    writeUInt32LE(readUInt32LE(stack, 0x1b40 + loopCounter * 0x58), stack, 0x40)

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        val word = readUInt32LE(stack, 0x3b28 + index * 4) *
            u32TableWord63c278(0x114948 + tableOffset, tables) +
            u32TableWord63c278(0x1184e8 + tableOffset, tables)
        writeUInt32LE(word, stack, 0xfc + index * 4)
    }

    var callerWord = readUInt32LE(stack, 0x44) * 0x52b341e9U + 0x8fe4704aU
    var postWord = readUInt32LE(stack, 0x3810) * 0xb1c3b83dU + 0x4ac96e8dU
    var callerWordMul: ULong = 0xc601c25eb7863abbUL
    var callerWordAdd: ULong = 0x8a9ac40e5bfb780dUL
    var callerMixMul: ULong = 0xe76d920aeec9873dUL
    var callerMixAdd: ULong = 0x63d22c2ddb82d5a1UL
    var callerFoldMul: ULong = 0x9897ad9900000000UL
    var postWordMul: ULong = 0x16aacea9a72f0c45UL
    var postWordAdd: ULong = 0xe952bbc97872445cUL
    var postMixMul: ULong = 0x9c887c5c45db1a3bUL
    var postMixAdd: ULong = 0x6536302ead1b2169UL
    var postFoldMul: ULong = 0x7db1cb8100000000UL

    var callerValue = builder6388f0CallerStreamU64(
        callerWord,
        callerWordMul,
        callerWordAdd,
        0x3015f0,
        callerFoldMul,
        callerMixMul,
        callerMixAdd,
        tables
    )
    var postValue = builder6388f0CallerStreamU64(
        postWord,
        postWordMul,
        postWordAdd,
        0x301570,
        postFoldMul,
        postMixMul,
        postMixAdd,
        tables
    )
    writeUInt64LE(callerValue, stack, 0x4130)
    writeUInt64LE(callerValue, stack, 0x3ef0)
    writeUInt64LE(postValue, stack, 0x3b80)
    writeUInt64LE(postValue, stack, 0x4010)

    var prefixPost = postValue
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x3810 + index * 4)
        word = word * u32TableWord63c278(0x120648 + tableOffset, tables) +
            u32TableWord63c278(0x122208 + tableOffset, tables)
        word = word * 0xad44242bU + 0x28772583U
        val value = builder6388f0CallerStreamU64(
            word,
            postWordMul,
            postWordAdd,
            0x301570,
            postFoldMul,
            postMixMul,
            postMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x3b80 + index * 8)
        prefixPost = prefixPost + value
        writeUInt64LE(prefixPost, stack, 0x4010 + index * 8)
    }

    var prefixCaller = callerValue
    var callerStream = 0x1aec - pointerDelta
    for (index in 0 until (builder63c278VectorWords - 1)) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x119728 + tableOffset, tables) +
            u32TableWord63c278(0x1218e8 + tableOffset, tables)
        word = word * 0xb7911189U + 0x50798488U
        val value = builder6388f0CallerStreamU64(
            word,
            callerWordMul,
            callerWordAdd,
            0x3015f0,
            callerFoldMul,
            callerMixMul,
            callerMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x4138 + index * 8)
        prefixCaller = prefixCaller + value
        writeUInt64LE(prefixCaller, stack, 0x3ef8 + index * 8)
    }

    builder6388f0Convolution44(
        stack,
        0x4130,
        0x3ef0,
        0x3b80,
        0x4010,
        0x3ce0,
        Process2P5PublicConstants(
            0x02949f32b4f07fd8UL,
            0xbea7dd815afcbcc1UL,
            0x7611b37d7c8f4475UL,
            0xb4709b2cff94859cUL,
            0xde2dc8d44e8f4662UL,
            0x3b985d4b603f64d9UL,
            0x9a4acc2ae823c739UL
        )
    )

    callerWord = readUInt32LE(stack, 0x40) * 0x10aa89f9U + 0x5f38d605U
    postWord = readUInt32LE(stack, 0x3708) * 0x49dc9b53U + 0x4de59f05U
    callerWordMul = 0x8b7f3e328f16058bUL
    callerWordAdd = 0xc7947e77ef912670UL
    callerMixMul = 0x272b96c7a7cb8ff9UL
    callerMixAdd = 0x7dff808a85cebcacUL
    callerFoldMul = 0xbcbba6f500000000UL
    postWordMul = 0x4cea0abb01866b97UL
    postWordAdd = 0xd8436bdaf28ca051UL
    postMixMul = 0x4c0e84f7d0089f9bUL
    postMixAdd = 0xb5d5f7a06307c689UL
    postFoldMul = 0x832f036300000000UL

    callerValue = builder6388f0CallerStreamU64FirstNibbleBeforeAdd(
        callerWord,
        callerWordMul,
        callerWordAdd,
        0x3016f0,
        callerFoldMul,
        callerMixMul,
        callerMixAdd,
        tables
    )
    postValue = builder6388f0CallerStreamU64(
        postWord,
        postWordMul,
        postWordAdd,
        0x301670,
        postFoldMul,
        postMixMul,
        postMixAdd,
        tables
    )
    writeUInt64LE(callerValue, stack, 0x4010)
    writeUInt64LE(callerValue, stack, 0x3e40)
    writeUInt64LE(postValue, stack, 0x4130)
    writeUInt64LE(postValue, stack, 0x3ef0)

    prefixPost = postValue
    for (index in 1 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        var word = readUInt32LE(stack, 0x3708 + index * 4)
        word = word * u32TableWord63c278(0x115f28 + tableOffset, tables) +
            u32TableWord63c278(0x118508 + tableOffset, tables)
        word = word * 0x68c9b103U + 0x45ce4a73U
        val value = builder6388f0CallerStreamU64(
            word,
            postWordMul,
            postWordAdd,
            0x301670,
            postFoldMul,
            postMixMul,
            postMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x4130 + index * 8)
        prefixPost = prefixPost + value
        writeUInt64LE(prefixPost, stack, 0x3ef0 + index * 8)
    }

    prefixCaller = callerValue
    callerStream = 0x2f34 - pointerDelta
    for (index in 0 until (builder63c278VectorWords - 1)) {
        val tableOffset = ((index + 1) and 7) * 4
        var word = readUInt32LE(stack, callerStream + index * 4)
        word = word * u32TableWord63c278(0x115468 + tableOffset, tables) +
            u32TableWord63c278(0x118528 + tableOffset, tables)
        word = word * 0xf6c4e17dU + 0x0882a9cfU
        val value = builder6388f0CallerStreamU64FirstNibbleBeforeAdd(
            word,
            callerWordMul,
            callerWordAdd,
            0x3016f0,
            callerFoldMul,
            callerMixMul,
            callerMixAdd,
            tables
        )
        writeUInt64LE(value, stack, 0x4018 + index * 8)
        prefixCaller = prefixCaller + value
        writeUInt64LE(prefixCaller, stack, 0x3e48 + index * 8)
    }

    builder6388f0Convolution44(
        stack,
        0x4010,
        0x3e40,
        0x4130,
        0x3ef0,
        0x3b80,
        Process2P5PublicConstants(
            0x638b8c646690163aUL,
            0x96f60e030a9158deUL,
            0x59ee1b3f304ea615UL,
            0x3a17e3aa11527f5eUL,
            0xcf4a3bc55798adefUL,
            0x15bcf2fe3c06e5afUL,
            0xcc8339ef4cba9cd0UL
        )
    )

    for (offset in 0 until 0x160 step 0x10) {
        val firstA = readUInt64LE(stack, 0x3ce0 + offset)
        val secondA = readUInt64LE(stack, 0x3ce0 + offset + 8)
        val firstB = readUInt64LE(stack, 0x3b80 + offset)
        val secondB = readUInt64LE(stack, 0x3b80 + offset + 8)
        writeUInt64LE(
            firstA * 0x929296759110b0a3UL
                + 0x0ede13af97827959UL
                + firstB * 0x70b7f6eaabceff57UL,
            stack,
            0x39c8 + offset
        )
        writeUInt64LE(
            secondA * 0x929296759110b0a3UL
                + 0x0ede13af97827959UL
                + secondB * 0x70b7f6eaabceff57UL,
            stack,
            0x39c8 + offset + 8
        )
    }

    return Builder6388f0Caller64CallState(
        stack.copyOfRange(0x330, 0x388),
        readUInt64LE(contextBytes, 0x418),
        stack.copyOfRange(0x39c8, 0x3b28),
        stack.copyOfRange(0x3b28, 0x3b80),
        stack.copyOfRange(0x3778, 0x42c8)
    )
}

internal fun builder6388f0Call64Call(state: Builder6388f0Caller64CallState): Builder6388f0Caller64Call {
    val output = packUInt32LE(builder64cd40OutputWords(
        state.arg0,
        state.scalar,
        state.x2Workspace
    ))
    return Builder6388f0Caller64Call(
        state.arg0,
        state.scalar,
        state.x2Workspace,
        state.x3Preimage,
        state.stackWindow,
        output
    )
}

internal fun builder6388f0SeededCaller64Row(index: Int, current642f60: Builder6388f0Next642f60Inputs, preimages: Builder6473d0OutputPreimages, contextSource: ByteArray? = null): Builder6388f0SeededCaller64Row {
    val context = contextSource ?: builder6388f0CallerContextFromBundle()

    val after642f60 = builder642f60Outputs(
        current642f60.x0,
        current642f60.x1,
        current642f60.x2,
        context
    )
    val after6473d0 = builder6473d0Outputs(
        after642f60.out0,
        after642f60.out1,
        after642f60.out2,
        context,
        preimages.out0,
        preimages.out1
    )
    val minimalStack20 = builder6473d0MinimalStack20FromPreimages(preimages)
    val postVectors = builder6473d0PostVectors(after6473d0)

    val first64cd40 = builder6388f0Call64Call(
        builder6388f0First64cd40CallState(
            context,
            minimalStack20,
            postVectors,
            index
        )
    )
    val second64cd40 = builder6388f0Call64Call(
        builder6388f0Second64cd40CallState(
            context,
            minimalStack20,
            postVectors,
            first64cd40.output,
            index
        )
    )
    val third64cd40 = builder6388f0Call64Call(
        builder6388f0Third64cd40CallState(
            context,
            minimalStack20,
            postVectors,
            second64cd40.output,
            index
        )
    )
    val next642f60 = builder6388f0Next642f60InputsFrom64cd40Outputs(
        first64cd40.output,
        second64cd40.output,
        third64cd40.output
    )

    return Builder6388f0SeededCaller64Row(
        index,
        current642f60,
        preimages,
        after642f60,
        after6473d0,
        minimalStack20,
        first64cd40,
        second64cd40,
        third64cd40,
        next642f60
    )
}

internal fun builder6388f0SeededCaller64Rows(starts: Builder6388f0FirstPair642f60Starts, row0LowPreimages: Builder6473d0OutputPreimages, contextSource: ByteArray? = null, limit: Int = 118, x2Source: ByteArray? = null): List<Builder6388f0SeededCaller64Row> {
    if (!(limit >= 0 && limit <= builder6388f0FirstPairStreamRows)) {
        throw FirstPairSourceSliceError.invalid6388f0RowLimit(limit)
    }

    val context = contextSource ?: builder6388f0CallerContextFromBundle()

    val row0Out0 = builder6388f0RecoverStreamStartOut0SeedFrom642f60X0(starts.row0.x0)
    val row0Out1 = builder6388f0RecoverStreamStartOut1SeedFrom642f60X1(starts.row0.x1)
    val row59Out0 = builder6388f0RecoverStreamStartOut0SeedFrom642f60X0(starts.row59.x0)
    val row59Out1 = builder6388f0RecoverStreamStartOut1SeedFrom642f60X1(starts.row59.x1)

    val row0Start = builder6388f0StreamStart642f60Inputs(
        row0Out0,
        row0Out1,
        x2Source
    )
    val row59Start = builder6388f0StreamStart642f60Inputs(
        row59Out0,
        row59Out1,
        x2Source
    )

    val rows = mutableListOf<Builder6388f0SeededCaller64Row>()
    var previous6473d0: Builder6473d0Result? = null
    var carried642f60: Builder6388f0Next642f60Inputs? = null
    var activeOut0Seed: ByteArray? = null
    var activeOut1Seed: ByteArray? = null

    for (index in 0 until limit) {
        val current642f60: Builder6388f0Next642f60Inputs
        val preimages: Builder6473d0OutputPreimages

        if (index == 0) {
            current642f60 = row0Start
            activeOut0Seed = row0Out0
            activeOut1Seed = row0Out1
            preimages = Builder6473d0OutputPreimages(
                row0LowPreimages.out4,
                row0LowPreimages.out3,
                row0LowPreimages.out2,
                row0Out1,
                row0Out0
            )
        } else if (index == builder6388f0CallerLoopTableRows) {
            val prev = previous6473d0 ?: throw FirstPairSourceSliceError.invalid6388f0EntryIndex(index)
            current642f60 = row59Start
            activeOut0Seed = row59Out0
            activeOut1Seed = row59Out1
            preimages = Builder6473d0OutputPreimages(
                prev.out4,
                prev.out3,
                prev.out2,
                row59Out1,
                row59Out0
            )
        } else {
            val carried = carried642f60
            val prev = previous6473d0
            val a0 = activeOut0Seed
            val a1 = activeOut1Seed
            if (carried == null || prev == null || a0 == null || a1 == null) {
                throw FirstPairSourceSliceError.invalid6388f0EntryIndex(index)
            }
            current642f60 = carried
            preimages = Builder6473d0OutputPreimages(
                prev.out4,
                prev.out3,
                prev.out2,
                a1,
                a0
            )
        }

        val row = builder6388f0SeededCaller64Row(
            index,
            current642f60,
            preimages,
            context
        )
        rows.add(row)
        previous6473d0 = row.after6473d0
        carried642f60 = row.next642f60
    }
    return rows
}

internal fun builder6388f0SeededCaller64RowsFromFirstPairStreamSeeds(seeds: Builder6388f0FirstPairStreamSeeds, contextSource: ByteArray? = null, limit: Int = 118, x2Source: ByteArray? = null): List<Builder6388f0SeededCaller64Row> {
    val starts = builder6388f0FirstPair642f60StreamStarts(
        seeds,
        x2Source
    )
    val row0LowPreimages = Builder6473d0OutputPreimages(
        seeds.row0Out4,
        seeds.row0Out3,
        seeds.row0Out2,
        seeds.row0Out1,
        seeds.row0Out0
    )
    return builder6388f0SeededCaller64Rows(
        starts,
        row0LowPreimages,
        contextSource,
        limit,
        x2Source
    )
}

internal fun builder6388f0Seeded63c278SchedulesFromRows(rows: List<Builder6388f0SeededCaller64Row>): Builder6388f0Seeded63c278Schedules {
    return builder6388f0Seeded63c278SchedulesFromRows(
        rows,
        pre63c278Arg0Source,
        pre63c278Scalar
    )
}

internal fun builder6388f0Seeded63c278SchedulesFromRows(rows: List<Builder6388f0SeededCaller64Row>, arg0: ByteArray, scalar: ULong): Builder6388f0Seeded63c278Schedules {
    if (rows.size < builder6388f0FirstPairStreamRows) {
        throw FirstPairSourceSliceError.invalid6388f0RowLimit(rows.size)
    }
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg0", arg0.size, builder63c278VectorBytes)
    }
    val arg0Prefix = arg0.copyOfRange(0, builder63c278VectorBytes)

    fun stream(rowIndex: Int): Builder6388f0Seeded63c278Stream {
        val row = rows[rowIndex]
        val arg1 = row.next642f60.x0
        val arg2 = row.next642f60.x2
        return Builder6388f0Seeded63c278Stream(
            rowIndex,
            arg0Prefix,
            arg1,
            arg2,
            scalar,
            builder63c278ScheduleWords(
                arg0Prefix,
                arg1,
                arg2,
                scalar
            )
        )
    }

    return Builder6388f0Seeded63c278Schedules(
        stream(builder6388f0CallerLoopTableRows - 1),
        stream(builder6388f0FirstPairStreamRows - 1)
    )
}

internal fun builder64cd40Arg0U64Words(arg0: ByteArray): List<ULong> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("64cd40 arg0", arg0.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = arg0
    val out = mutableListOf<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        val affine = u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            0x123e08,
            0x123428,
            tables
        )
        val word = affine * 0x8c2231afU + 0x6d97daf4U
        var folded = word.toULong() * 0xeb7e2c45742037f1UL + 0x3363481bcd8bcd54UL
        folded = fold63c278(folded, 0x3010f0, 8, tables)
        out.add(
            word.toULong() * 0x92d397b84a615e45UL +
                folded * 0x73db406b00000000UL +
                0xbdc443456d026c77UL
        )
    }
    return out
}

internal fun builder64cd40WorkspaceAfterUpdate(arg0U64Words: List<ULong>, scalar: ULong, x2Workspace: ByteArray): ByteArray {
    if (arg0U64Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(arg0U64Words.size)
    }
    if (x2Workspace.size < builder64cd40WorkspaceBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("64cd40 x2 workspace", x2Workspace.size, builder64cd40WorkspaceBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x2Workspace
    val x2Words = MutableList<ULong>(builder64cd40WorkspaceWords) { readUInt64LE(bytes, it * 8) }
    var carryWord = x2Words[0]
    for (base in 0 until builder63c278VectorWords) {
        val params = builder64cd40WorkspaceParams(
            scalar,
            carryWord,
            tables
        )
        for (offset in arg0U64Words.indices) {
            val word = arg0U64Words[offset]
            val pos = base + offset
            val product = word * params.first
            x2Words[pos] = x2Words[pos] + params.second + product
        }
        carryWord = builder64cd40RewriteSecondWord(
            x2Words[base],
            x2Words[base + 1],
            tables
        )
        x2Words[base + 1] = carryWord
    }
    return packUInt64LE64cd40(x2Words)
}

internal fun builder64cd40FinalU32Words(x2Workspace: ByteArray): List<UInt> {
    if (x2Workspace.size < builder64cd40WorkspaceBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("64cd40 x2 workspace", x2Workspace.size, builder64cd40WorkspaceBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x2Workspace
    val x2Words = MutableList<ULong>(builder64cd40WorkspaceWords) { readUInt64LE(bytes, it * 8) }
    var carry: ULong = 0x0b784750d9181757UL
    val out = mutableListOf<UInt>()

    for (index in 0 until builder63c278VectorWords) {
        val tailWord = x2Words[builder63c278VectorWords + index]
        val mixed = carry * 0xcaaf4f9d292a519dUL + tailWord * 0x28d4341977190ea5UL
        val foldedInput = mixed + 0x593214d4b8068287UL
        var foldedParts = builder64cd40FinalFirstFold(foldedInput, tables)
        for (i in 0 until 8) {
            foldedParts = FpTriple(
                foldedParts.first,
                foldedParts.second,
                foldTableU64Word63c278(
                    0x3012f0 + (foldedParts.third and 0x0fUL).toInt() * 8,
                    tables
                ) + (foldedParts.third shr 4)
            )
        }
        val nextCarry = foldedParts.first * 0xda8179ca5c614737UL +
            foldedParts.third * 0x39eb8c9000000000UL
        val tableOffset = (index * 4) and 0x1c
        out.add(u32TableAffine63c278(
            foldedParts.second,
            0x11c3a8 + tableOffset,
            0x11dce8 + tableOffset,
            tables
        ))
        carry = nextCarry + 0xd2a88419cf931098UL
    }
    return out
}

internal fun builder64cd40OutputWords(arg0: ByteArray, scalar: ULong, x2Workspace: ByteArray): List<UInt> {
    val arg0Words = builder64cd40Arg0U64Words(arg0)
    val updated = builder64cd40WorkspaceAfterUpdate(
        arg0Words,
        scalar,
        x2Workspace
    )
    return builder64cd40FinalU32Words(updated)
}
