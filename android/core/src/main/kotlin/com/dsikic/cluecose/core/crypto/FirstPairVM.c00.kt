// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.RuntimeTable

internal fun init679f48Context(): ByteArray {
    val tables = FirstPairConfig.tables
    val context = ByteArray(context679f48Size)

    for (spec in init679f48Block18Specs) {
        val src = checkedSlice(
            tables.seedTables679f48,
            spec.srcOffset,
            df80WordSize,
            RuntimeTable.FIRST_PAIR_679F48_SEED_TABLES.fileName
        )
        replace(
            context,
            spec.dstOffset,
            vm67cc18(spec.magic, src, src, tables)
        )
    }

    val src66 = checkedSlice(
        tables.seedTables679f48,
        init679f48Block66SrcOffset,
        block66Size,
        RuntimeTable.FIRST_PAIR_679F48_SEED_TABLES.fileName
    )
    val block66 = vm67cc18(0x42000001e72UL, src66, src66, tables)
    for (dstOffset in init679f48Block66DstOffsets) {
        replace(context, dstOffset, block66)
    }
    return context
}

internal fun update67aa8cLen4Initial(context: ByteArray, src4: ByteArray): ByteArray {
    if (src4.size != 4) {
        throw FirstPairSourceSliceError.invalid67aa8cInitialSourceLength(src4.size)
    }
    if (context.size < context679f48Size) {
        throw FirstPairSourceSliceError.sourceTooShort("67aa8c context", context.size, context679f48Size)
    }
    val tables = FirstPairConfig.tables
    val ctx = context.copyOf()
    if (ctx[0x1a4] != 0.toByte()) {
        throw FirstPairSourceSliceError.invalid67aa8cInitialFlag(ctx[0x1a4])
    }

    ctx[0x1a4] = 1
    seed67aa8cInitialWords(ctx, tables)
    replace(ctx, 0x1a5, src4)
    writeUInt32LE(4U, ctx, 0x1e8)
    return ctx
}

internal fun apply67eb94PendingBlocks(context: ByteArray): ByteArray {
    if (context.size < context679f48Size) {
        throw FirstPairSourceSliceError.sourceTooShort("67eb94 context", context.size, context679f48Size)
    }
    val tables = FirstPairConfig.tables
    val ctx = context.copyOf()
    if (ctx[0x1a4] == 0.toByte()) {
        return ctx
    }

    ctx[0x1a4] = 0
    val words = mutableListOf<ByteArray>()
    for (index in 0 until 8) {
        val start = 0x1ec + index * 4
        words.add(ctx.copyOfRange(start, start + 4))
    }
    replace(ctx, 0x114, update67eb94Blocks(words, tables))
    return ctx
}

internal fun encode67d630Block(src: ByteArray): ByteArray {
    if (!(src.size > 0 && src.size <= 0x10)) {
        throw FirstPairSourceSliceError.invalid67d630BlockLength(src.size)
    }
    val tables = FirstPairConfig.tables
    val srcBytes = src
    val scratch16 = ByteArray(0x10)
    for (index in 0 until srcBytes.size) {
        scratch16[0x10 - srcBytes.size + index] = srcBytes[srcBytes.size - 1 - index]
    }

    var sideA = ByteArray(0)
    var sideB = ByteArray(0)
    for (byte in scratch16) {
        sideA = sideA.concat(expandRawByte67d630(byte, raw67d630TableAOffset, tables))
        sideB = sideB.concat(expandRawByte67d630(byte, raw67d630TableBOffset, tables))
    }

    val foldedA = fold96To66(0x600000032b2UL, 0x60000005e9cUL, sideA, tables)
    val mixedA = vm67cc18(0x42000004b29UL, foldedA, foldedA, tables)

    val foldedB = fold96To66(0x60000000133UL, 0x600000033dbUL, sideB, tables)
    val mixedB = vm67cc18(0x42000000263UL, foldedB, foldedB, tables)

    val mixed = vm67cc18(0x42000000b0eUL, mixedA, mixedB, tables)
    return vm67d524(0xc03f000c0112fUL, mixed, tables)
}

internal fun apply67eb94WithPendingRawAdapter(context: ByteArray): ByteArray {
    if (context.size < context679f48Size) {
        throw FirstPairSourceSliceError.sourceTooShort("67eb94 context", context.size, context679f48Size)
    }
    var ctx = context.copyOf()
    if (ctx[0x1a4] == 0.toByte()) {
        return ctx
    }
    val pendingLength = readUInt32LE(ctx, 0x1e8)
    if (pendingLength > 0x40U) {
        throw FirstPairSourceSliceError.invalid67eb94PendingLength(pendingLength)
    }

    ctx = apply67eb94PendingBlocks(ctx)
    val pending = checkedSlice(ctx, 0x1a5, pendingLength.toInt(), "67eb94 pending bytes")
    var offset = 0
    while (offset < pending.size) {
        val end = minOf(offset + 0x10, pending.size)
        val chunk = pending.copyOfRange(offset, end)
        val encoded = encode67d630Block(chunk)
        ctx = apply67dd7cUpdateUntilDF80(
            ctx,
            encoded,
            chunk.size
        )
        offset = end
    }
    writeUInt32LE(0U, ctx, 0x1e8)
    return ctx
}

internal fun apply67dd7cUpdateUntilDF80(context: ByteArray, encoded66: ByteArray, rawLength: Int): ByteArray {
    if (context.size < context679f48Size) {
        throw FirstPairSourceSliceError.sourceTooShort("67dd7c context", context.size, context679f48Size)
    }
    if (encoded66.size != block66Size) {
        throw FirstPairSourceSliceError.invalid67dd7cEncodedLength(encoded66.size)
    }
    if (!(rawLength > 0 && rawLength <= 0x10)) {
        throw FirstPairSourceSliceError.invalid67dd7cRawLength(rawLength)
    }

    val tables = FirstPairConfig.tables
    val ctx = context.copyOf()
    val encoded = encoded66
    val contextLength = readUInt64LE(ctx, 0)
    val low = (contextLength and 0x0fUL).toInt()
    val room = 0x10 - low
    var blockIndex = readUInt32LE(ctx, 0x110)
    val slot = 0x08 + blockIndex.toInt() * block66Size

    if (low != 0) {
        var staged = vm67cc18(0x42000005c05UL, encoded, encoded, tables)
        for (i in 0 until low) {
            staged = vm67cecc(0x1003e001002eafUL, staged, staged, tables)
        }

        val pad = checkedSlice(
            tables.finalizerTables,
            finalizerDD7CPadOffset + (low xor 0x0f) * block66Size,
            block66Size,
            RuntimeTable.FIRST_PAIR_FINALIZER_TABLES.fileName
        )
        val current = checkedSlice(ctx, slot, block66Size, "67dd7c context slot")
        val prefix = vm67cc18(0x42000002fd4UL, current, pad, tables)
        replace(
            ctx,
            slot,
            vm67cc18(0x42000003060UL, prefix, staged, tables)
        )
    } else {
        replace(
            ctx,
            slot,
            vm67cc18(0x42000001c66UL, encoded, encoded, tables)
        )
    }

    if (room <= rawLength) {
        blockIndex += 1U
        writeUInt32LE(blockIndex, ctx, 0x110)
        if (blockIndex.toInt() == 4) {
            val transformed = df80Transform(
                ctx.copyOfRange(0x114, 0x1a4),
                ctx.copyOfRange(0x08, 0x110)
            )
            replace(ctx, 0x114, transformed)
            blockIndex = 0U
            writeUInt32LE(0U, ctx, 0x110)
        }

        if (room < rawLength) {
            var remainder = vm67cc18(0x42000003d8bUL, encoded, encoded, tables)
            for (i in 0 until room) {
                remainder = shift67dd7cRemainder(remainder, tables)
            }
            val nextSlot = 0x08 + blockIndex.toInt() * block66Size
            replace(
                ctx,
                nextSlot,
                vm67cc18(0x420000008deUL, remainder, remainder, tables)
            )
        }
    }

    writeUInt64LE(contextLength + rawLength.toULong(), ctx, 0)
    return ctx
}

internal fun previousDescriptorBlocksToDD7CInputs(previousBlocks: ByteArray): ByteArray {
    if (previousBlocks.size % block66Size != 0) {
        throw FirstPairSourceSliceError.invalidEncodedBlockLength(previousBlocks.size)
    }
    val tables = FirstPairConfig.tables
    val bytes = previousBlocks
    var out = ByteArray(0)
    for (start in 0 until bytes.size step block66Size) {
        val block = bytes.copyOfRange(start, start + block66Size)
        val encoded = vm67cc18(0x42000001341UL, block, block, tables)
        val staged = vm67cc18(0x420000053baUL, encoded, encoded, tables)
        out = out.concat(vm67cc18(0x42000000c2cUL, staged, staged, tables))
    }
    return out
}

internal fun finalized679f48ContextFromInputs(previousBlocks: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte())): ByteArray {
    var context = init679f48Context()
    context = update67aa8cLen4Initial(context, src4)
    context = apply67eb94WithPendingRawAdapter(context)

    val fullUpdates = previousDescriptorBlocksToDD7CInputs(previousBlocks)
    val updateBytes = fullUpdates
    for (start in 0 until updateBytes.size step block66Size) {
        context = apply67dd7cUpdateUntilDF80(
            context,
            updateBytes.copyOfRange(start, start + block66Size),
            0x10
        )
    }

    context = apply67eb94WithPendingRawAdapter(context)
    return finalize679f48ToSecondDF80(context)
}

internal fun deriveFrom679f48Inputs(previousBlocks: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val context = finalized679f48ContextFromInputs(previousBlocks, src4)
    return deriveFromFinalized679f48Context(context, offset, length)
}

internal fun constructor670978Ptr28Blocks(rawDescriptorBlocks: ByteArray): ByteArray {
    return constructor67076cBlocks(rawDescriptorBlocks, 0x42000000000UL)
}

internal fun constructor670a54Ptr10Blocks(rawDescriptorBlocks: ByteArray): ByteArray {
    return constructor67076cBlocks(rawDescriptorBlocks, 0x42000000042UL)
}

internal fun deriveFrom660448RawDescriptor(rawDescriptorBlocks: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val previousBlocks = constructor670978Ptr28Blocks(rawDescriptorBlocks)
    return deriveFrom679f48Inputs(previousBlocks, src4, offset, length)
}

internal fun deriveFrom660448Sources(firstRawBlocks: ByteArray, secondRawBlocks: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    var combined = ByteArray(0)
    combined = combined.concat(firstRawBlocks)
    combined = combined.concat(secondRawBlocks)
    return deriveFrom660448RawDescriptor(
        combined,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom64d774RawStreams(firstRawBlocks: ByteArray, secondRawBlocks: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val firstFor660448 = constructor670a54Ptr10Blocks(firstRawBlocks)
    val secondFor660448 = constructor670a54Ptr10Blocks(secondRawBlocks)
    return deriveFrom660448Sources(
        firstFor660448,
        secondFor660448,
        src4,
        offset,
        length
    )
}

internal fun builder6388f0FinalRawBlocks(internalBlocks: ByteArray): ByteArray {
    if (internalBlocks.size % block66Size != 0) {
        throw FirstPairSourceSliceError.invalidEncodedBlockLength(internalBlocks.size)
    }
    val tables = FirstPairConfig.tables
    val bytes = internalBlocks
    var out = ByteArray(0)
    for (start in 0 until bytes.size step block66Size) {
        val block = bytes.copyOfRange(start, start + block66Size)
        out = out.concat(vm638840(0x42000007e29UL, block, block, tables))
    }
    return out
}

internal fun builder6388f0PrefinalLen32InternalBlocks(prefinalSourceBlocks: ByteArray): ByteArray {
    if (prefinalSourceBlocks.size != 2 * block66Size) {
        throw FirstPairSourceSliceError.invalid6388f0PrefinalLength(prefinalSourceBlocks.size)
    }
    val tables = FirstPairConfig.tables
    val bytes = prefinalSourceBlocks
    val call0 = bytes.copyOfRange(0, block66Size)
    val call1 = bytes.copyOfRange(block66Size, 2 * block66Size)
    val block1 = vm638840(0x42000003bf9UL, call0, call0, tables)
    val block0 = vm638840(0x42000003bf9UL, call1, call1, tables)
    return block0.concat(block1)
}

internal fun builder6388f0Len32PrefinalSourcesFromWorkspace(workspaceSource: ByteArray): ByteArray {
    if (workspaceSource.size != builder6388f0WorkspaceSize) {
        throw FirstPairSourceSliceError.invalid6388f0WorkspaceLength(workspaceSource.size)
    }
    val tables = FirstPairConfig.tables
    val source = workspaceSource
    val workspace = vm638840(0x10a000006cd1UL, source, source, tables)
    if (workspace.size != builder6388f0WorkspaceSize) {
        throw FirstPairSourceSliceError.invalid6388f0WorkspaceLength(workspace.size)
    }

    val firstPrefinal = workspace.copyOfRange(0, block66Size)
    val updated = vm6420d8(0x1000ca0100063f1UL, workspace, workspace, tables)
    if (updated.size != builder6388f0WorkspaceSize) {
        throw FirstPairSourceSliceError.invalid6388f0WorkspaceLength(updated.size)
    }

    val secondPrefinal = updated.copyOfRange(0, block66Size)
    return firstPrefinal.concat(secondPrefinal)
}

internal fun builder6388f0Len32PrefinalSourcesFromStageInputs(stageASource: ByteArray, stageBSource: ByteArray): ByteArray {
    if (stageASource.size != builder6388f0StageSize) {
        throw FirstPairSourceSliceError.invalid6388f0StageLength(stageASource.size)
    }
    if (stageBSource.size != builder6388f0StageSize) {
        throw FirstPairSourceSliceError.invalid6388f0StageLength(stageBSource.size)
    }

    val tables = FirstPairConfig.tables
    val a = stageASource
    val b = stageBSource
    val stageA = vm638840(0x11a000003e76UL, a, a, tables)
    val stageB = vm638840(0x11a000004f0cUL, b, stageA, tables)
    val stageC = vm638840(0x11a000004b16UL, stageB, stageA, tables)
    val stageDSource = stageC.copyOfRange(0, builder6388f0WorkspaceSize)
    val stageD = vm638840(0x10a0000078bbUL, stageDSource, stageDSource, tables)
    return builder6388f0Len32PrefinalSourcesFromWorkspace(stageD)
}

internal fun pack6388f0Twenty16To282(head16: ByteArray, bodyBlocks16: ByteArray): ByteArray {
    if (head16.size != builder6388f0LaneBlockSize) {
        throw FirstPairSourceSliceError.invalid6388f0PackHeadLength(head16.size)
    }
    if (bodyBlocks16.size != (builder6388f0LaneBlockCount - 1) * builder6388f0LaneBlockSize) {
        throw FirstPairSourceSliceError.invalid6388f0PackBodyLength(bodyBlocks16.size)
    }

    val body = bodyBlocks16
    var out = head16.copyOf()
    for (offset in 0 until body.size step builder6388f0LaneBlockSize) {
        out = out.concat(body.copyOfRange(offset + 2, offset + builder6388f0LaneBlockSize))
    }
    return out
}

internal fun builder6388f0Len32StageInputsFromPackOutputs(stageBPackHead16: ByteArray, stageBPackBody16: ByteArray, stageAPackHead16: ByteArray, stageAPackBody16: ByteArray): FpPair<ByteArray, ByteArray> {
    val stageBPack = pack6388f0Twenty16To282(stageBPackHead16, stageBPackBody16)
    val tables = FirstPairConfig.tables
    val stageBPackBytes = stageBPack
    val stageBSource = vm638840(
        0x11a000000a2cUL,
        stageBPackBytes,
        stageBPackBytes,
        tables
    )
    val stageASource = pack6388f0Twenty16To282(stageAPackHead16, stageAPackBody16)
    return FpPair(stageASource, stageBSource)
}

internal fun builder6388f0PackOutputsFromLaneBlocks(primaryLaneBlocks: ByteArray, secondaryLaneBlocks: ByteArray): FpQuad<ByteArray, ByteArray, ByteArray, ByteArray> {
    if (primaryLaneBlocks.size != builder6388f0LaneBlocksSize) {
        throw FirstPairSourceSliceError.invalid6388f0LaneBlocksLength(primaryLaneBlocks.size)
    }
    if (secondaryLaneBlocks.size != builder6388f0LaneBlocksSize) {
        throw FirstPairSourceSliceError.invalid6388f0LaneBlocksLength(secondaryLaneBlocks.size)
    }

    val tables = FirstPairConfig.tables
    val primaryBytes = primaryLaneBlocks
    val secondaryBytes = secondaryLaneBlocks
    val primary = mutableListOf<ByteArray>()
    val secondary = mutableListOf<ByteArray>()
    for (offset in 0 until builder6388f0LaneBlocksSize step builder6388f0LaneBlockSize) {
        primary.add(primaryBytes.copyOfRange(offset, offset + builder6388f0LaneBlockSize))
        secondary.add(secondaryBytes.copyOfRange(offset, offset + builder6388f0LaneBlockSize))
    }

    val stageBPackHead = vm638840(0x10000000388UL, primary[0], primary[0], tables)
    var stageBPackBody = ByteArray(0)
    for (block in primary.drop(1)) {
        stageBPackBody = stageBPackBody.concat(vm638840(0x10000003bd7UL, block, block, tables))
    }

    val stageAPackHead = vm638840(0x100000062d7UL, secondary[0], secondary[0], tables)
    var stageAPackBody = ByteArray(0)
    for (block in secondary.drop(1)) {
        stageAPackBody = stageAPackBody.concat(vm638840(0x10000008177UL, block, block, tables))
    }

    return FpQuad(stageBPackHead, stageBPackBody, stageAPackHead, stageAPackBody)
}

internal fun builder63c278InitialVectors(arg0: ByteArray, arg1: ByteArray): FpPair<List<ULong>, List<ULong>> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg0", arg0.size, builder63c278VectorBytes)
    }
    if (arg1.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg1", arg1.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val arg0Bytes = arg0
    val arg1Bytes = arg1

    val x1 = mutableListOf<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        x1.add(builder63c278X1Word(readUInt32LE(arg1Bytes, index * 4), index, tables))
    }
    x1.addAll(List(builder63c278VectorWords) { 0xb7059a553c133489UL })

    val x0 = mutableListOf<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        x0.add(builder63c278X0Word(readUInt32LE(arg0Bytes, index * 4), index, tables))
    }
    return FpPair(x1, x0)
}

internal fun builder63c278SecondInitialVectors(arg0: ByteArray, arg2: ByteArray): FpPair<List<ULong>, List<ULong>> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg0", arg0.size, builder63c278VectorBytes)
    }
    if (arg2.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg2", arg2.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val arg0Bytes = arg0
    val arg2Bytes = arg2

    val x2 = mutableListOf<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        x2.add(builder63c278X2Word(readUInt32LE(arg2Bytes, index * 4), index, tables))
    }
    x2.addAll(List(builder63c278VectorWords) { 0x9a6e0b3eab651f3dUL })

    val x0 = mutableListOf<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        x0.add(builder63c278X0BWord(readUInt32LE(arg0Bytes, index * 4), index, tables))
    }
    return FpPair(x2, x0)
}

internal fun builder63c278ScalarMixVector(x1Vec44: List<ULong>, x0Vec22: List<ULong>, scalar: ULong): List<ULong> {
    if (x1Vec44.size != 44) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(x1Vec44.size)
    }
    if (x0Vec22.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(x0Vec22.size)
    }
    val tables = FirstPairConfig.tables
    val vec = x1Vec44.toMutableList()
    val scalarMul = (scalar * 0xc2f49ab55607d661UL) + 0x5cd21b4822401581UL
    val scalarAdd = (scalar * 0x31979e72b90f9217UL) + 0x3a834f793d8d50d2UL

    var carry = vec[0]
    for (index in 0 until builder63c278VectorWords) {
        val seed = builder63c278MixSeed(carry, scalarMul, scalarAdd, tables)
        for (lane in 0 until builder63c278VectorWords) {
            val pos = index + lane
            vec[pos] = vec[pos] + seed.second + (x0Vec22[lane] * seed.first)
        }

        carry = builder63c278NextCarry(vec[index], vec[index + 1], tables)
        vec[index + 1] = carry
    }
    return vec
}

internal fun builder63c278ScalarMix2Vector(x2Vec44: List<ULong>, x0Vec22: List<ULong>, scalar: ULong): List<ULong> {
    if (x2Vec44.size != 44) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(x2Vec44.size)
    }
    if (x0Vec22.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(x0Vec22.size)
    }
    val tables = FirstPairConfig.tables
    val vec = x2Vec44.toMutableList()
    val scalarMul = (scalar * 0xd499812ba25ee663UL) + 0x261ebe70f821cbc3UL
    val scalarAdd = (scalar * 0xb1af6fa1cb6e1d69UL) + 0xbfe73a2bd6da82dcUL

    var carry = vec[0]
    for (index in 0 until builder63c278VectorWords) {
        val seed = builder63c278Mix2Seed(carry, scalarMul, scalarAdd, tables)
        for (lane in 0 until builder63c278VectorWords) {
            val pos = index + lane
            vec[pos] = vec[pos] + seed.second + (x0Vec22[lane] * seed.first)
        }

        carry = builder63c278NextCarry2(vec[index], vec[index + 1], tables)
        vec[index + 1] = carry
    }
    return vec
}

internal fun builder63c278Tail1U32Words(mixedVec44: List<ULong>): List<UInt> {
    if (mixedVec44.size != 44) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(mixedVec44.size)
    }
    val tables = FirstPairConfig.tables
    var carry: ULong = 0x57078c52164039c3UL
    val out = mutableListOf<UInt>()
    for (index in 0 until builder63c278VectorWords) {
        carry = carry * 0xea79f5006ed1ed3dUL
        carry = carry + (mixedVec44[builder63c278VectorWords + index] * 0x66df92deb399335bUL)
        carry = carry + 0x09c9f7e39169d6f1UL

        val folded = fold63c278(
            (carry * 0x4a61801334a2066bUL) + 0x346cdb9fa10bc247UL,
            0x3019f0,
            7,
            tables
        )
        val word = (carry.toUInt().toULong() * 0x6d8d9d63UL + folded.toUInt().toULong() * 0x70000000UL + 0xc780a908UL).and(0xffffffffUL)
        val foldedTail = fold63c278(folded, 0x3019f0, 9, tables)
        carry = (folded * 0xe3d2a03f1bfe297fUL) +
            (foldedTail * 0x401d681000000000UL) +
            0x7b8480dbcf98c453UL

        val tableOffset = (index and 7) * 4
        val mul = u32TableWord63c278(0x123448 + tableOffset, tables)
        val add = u32TableWord63c278(0x123468 + tableOffset, tables)
        out.add((word * mul.toULong() + add.toULong()).and(0xffffffffUL).toUInt())
    }
    return out
}

internal fun builder63c278Tail2U32Words(mixedVec44: List<ULong>): List<UInt> {
    if (mixedVec44.size != 44) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(mixedVec44.size)
    }
    val tables = FirstPairConfig.tables
    var carry: ULong = 0x7b98879460aee9e2UL
    val out = mutableListOf<UInt>()
    for (index in 0 until builder63c278VectorWords) {
        carry = carry * 0xf65fd3833526aa13UL
        carry = carry + (mixedVec44[builder63c278VectorWords + index] * 0x806aa29ec1ed1481UL)
        carry = carry + 0xb4f29f8797e744b7UL

        val folded = fold63c278(
            (carry * 0xa05b2cf659a43c93UL) + 0x48da81dd905ece62UL,
            0x301cf0,
            7,
            tables
        )
        val word = (carry.toUInt().toULong() * 0x0080b9a9UL + folded.toUInt().toULong() * 0xd0000000UL + 0xde4d224eUL).and(0xffffffffUL)
        val foldedTail = fold63c278(folded, 0x301cf0, 9, tables)
        carry = (folded * 0x3fc1e03941c67b59UL) +
            (foldedTail * 0xe3984a7000000000UL) +
            0x05975fb8f5057bb2UL

        val tableOffset = (index and 7) * 4
        val mul = u32TableWord63c278(0x112628 + tableOffset, tables)
        val add = u32TableWord63c278(0x121928 + tableOffset, tables)
        out.add((word * mul.toULong() + add.toULong()).and(0xffffffffUL).toUInt())
    }
    return out
}

internal fun builder63c278AccumulatorStreams(arg2: ByteArray, tail2Words: List<UInt>): FpQuad<List<ULong>, List<ULong>, List<ULong>, List<ULong>> {
    if (arg2.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg2", arg2.size, builder63c278VectorBytes)
    }
    if (tail2Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(tail2Words.size)
    }
    val tables = FirstPairConfig.tables
    val arg2Bytes = arg2

    val sp5a0 = mutableListOf<ULong>()
    val sp440 = mutableListOf<ULong>()
    var runningA: ULong = 0UL
    for (index in 0 until builder63c278VectorWords) {
        val item = builder63c278AccumAWord(readUInt32LE(arg2Bytes, index * 4), index, tables)
        sp5a0.add(item)
        runningA = runningA + item
        sp440.add(runningA)
    }

    val sp4f0 = mutableListOf<ULong>()
    val sp390 = mutableListOf<ULong>()
    var runningB: ULong = 0UL
    for (index in tail2Words.indices) {
        val word = tail2Words[index]
        val item = builder63c278AccumBWord(word, index, tables)
        sp4f0.add(item)
        runningB = runningB + item
        sp390.add(runningB)
    }

    return FpQuad(sp440, sp4f0, sp5a0, sp390)
}

internal fun builder63c278BridgeConvolutionVector(sp440Cumulative: List<ULong>, sp4f0Words: List<ULong>, sp5a0Words: List<ULong>, sp390Cumulative: List<ULong>): List<ULong> {
    for (stream in listOf(sp440Cumulative, sp4f0Words, sp5a0Words, sp390Cumulative)) {
        if (stream.size != builder63c278VectorWords) {
            throw FirstPairSourceSliceError.invalid63c278VectorWordCount(stream.size)
        }
    }

    val out = mutableListOf<ULong>()
    for (index in 0 until 44) {
        val low = maxOf(index - 21, 0)
        val high = minOf(index, 21)
        val mixed: ULong

        if (low > 21) {
            mixed = 0x67bdf132221fb4e9UL
        } else {
            val start = index - high
            var dot: ULong = 0UL
            for (pos in start..high) {
                dot = dot + (sp4f0Words[index - pos] * sp5a0Words[pos])
            }

            var spanA = sp440Cumulative[high]
            val spanB: ULong
            if (index >= 22) {
                spanA = spanA - sp440Cumulative[low - 1]
                spanB = sp390Cumulative[index - low] - sp390Cumulative[index - high - 1]
            } else {
                spanB = sp390Cumulative[index - low]
            }

            mixed = 0x67bdf132221fb4e9UL +
                ((high - low + 1).toULong() * 0x1593d040a4114154UL) +
                (dot * 0x2edc06a97199e3efUL) +
                (spanA * 0x0557cced2c1cc47eUL) +
                (spanB * 0xc1edf977b66f09caUL)
        }

        out.add((mixed * 0xb3bd694c1c94d1a7UL) + 0x2c0585771e81c36aUL)
    }
    return out
}

internal fun builder63c278BridgeX0Vector(arg0: ByteArray): List<ULong> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg0", arg0.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val arg0Bytes = arg0
    val out = mutableListOf<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        out.add(builder63c278BridgeX0Word(readUInt32LE(arg0Bytes, index * 4), index, tables))
    }
    return out
}

internal fun builder63c278BridgeMixVector(sp230Vec44: List<ULong>, x0Vec22: List<ULong>, scalar: ULong): List<ULong> {
    if (sp230Vec44.size != 44) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp230Vec44.size)
    }
    if (x0Vec22.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(x0Vec22.size)
    }
    val tables = FirstPairConfig.tables
    val vec = sp230Vec44.toMutableList()
    val scalarMul = (scalar * 0x5bcfc2db5b41aa8bUL) + 0xb0be584b9c560cebUL
    val scalarAdd = (scalar * 0x7cb9da0648140cfdUL) + 0xcb165f95963e265bUL

    var carry = vec[0]
    for (index in 0 until builder63c278VectorWords) {
        val seed = builder63c278BridgeMixSeed(
            carry,
            scalarMul,
            scalarAdd,
            tables
        )
        for (lane in 0 until builder63c278VectorWords) {
            val pos = index + lane
            vec[pos] = vec[pos] + seed.second + (x0Vec22[lane] * seed.first)
        }

        carry = builder63c278BridgeNextCarry(vec[index], vec[index + 1], tables)
        vec[index + 1] = carry
    }
    return vec
}

internal fun builder63c278BridgeSP128Words(sp230Vec44: List<ULong>): List<UInt> {
    if (sp230Vec44.size != 44) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp230Vec44.size)
    }
    val tables = FirstPairConfig.tables
    var carry: ULong = 0x18541ef2e5658ac6UL
    val out = mutableListOf<UInt>()
    for (index in 0 until builder63c278VectorWords) {
        carry = carry * 0x590b8c9bda7aa7a5UL
        carry = carry + (sp230Vec44[builder63c278VectorWords + index] * 0x93e68b973b124f01UL)
        carry = carry + 0x0357d31d6340b07aUL

        val folded = fold63c278(
            (carry * 0x052e2e9b238ffd17UL) + 0x7a84d77fc047bb5cUL,
            0x302070,
            7,
            tables
        )
        val word = (carry.toUInt().toULong() * 0xbca742b5UL + folded.toUInt().toULong() * 0xd0000000UL + 0x74c20619UL).and(0xffffffffUL)
        val foldedTail = fold63c278(folded, 0x302070, 9, tables)
        carry = (folded * 0x9d12b2b955ef375bUL) +
            (foldedTail * 0xa10c8a5000000000UL) +
            0x5831e87503aab765UL

        val tableOffset = (index and 7) * 4
        val mul = u32TableWord63c278(0x118568 + tableOffset, tables)
        val add = u32TableWord63c278(0x1234a8 + tableOffset, tables)
        out.add((word * mul.toULong() + add.toULong()).and(0xffffffffUL).toUInt())
    }
    return out
}

internal fun builder63c278PrebranchInitialStreams(arg0: ByteArray, tail1Words: List<UInt>, sp128Words: List<UInt>): FpQuad<List<UInt>, List<UInt>, List<UInt>, List<UInt>> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg0", arg0.size, builder63c278VectorBytes)
    }
    if (tail1Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(tail1Words.size)
    }
    if (sp128Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp128Words.size)
    }
    val tables = FirstPairConfig.tables
    val arg0Bytes = arg0

    val sp390 = mutableListOf<UInt>()
    val sp440 = mutableListOf<UInt>()
    val sp6b0 = mutableListOf<UInt>()
    val sp658 = mutableListOf<UInt>()

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index and 7) * 4
        sp390.add(foldTableU32Word63c278(0x3020f0 + index * 4, tables))
        sp440.add(u32TableAffine63c278(tail1Words[index], 0x122228 + tableOffset, 0x11dd08 + tableOffset, tables))
        sp6b0.add(u32TableAffine63c278(sp128Words[index], 0x1234c8 + tableOffset, 0x11b428 + tableOffset, tables))
        sp658.add(u32TableAffine63c278(readUInt32LE(arg0Bytes, index * 4), 0x114968 + tableOffset, 0x11fda8 + tableOffset, tables))
    }

    return FpQuad(sp390, sp440, sp6b0, sp658)
}

internal fun builder63c278PrebranchSP4F0Words(arg0: ByteArray): List<UInt> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg0", arg0.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val arg0Bytes = arg0
    val arg0Words = (0 until builder63c278VectorWords).map { readUInt32LE(arg0Bytes, it * 4) }

    val first = (arg0Words[0].toULong() * 0x7193fc77UL + 0x318e9b49UL).and(0xffffffffUL).toUInt()
    var state = builder63c278PrebranchSP4F0State(first, tables)
    var selected = builder63c278PrebranchSP4F0FoldState(state, tables)
    var carry = (state.toULong() * 0x8e834ce3UL + selected.shl(31).toULong() + 0x0afac599UL).and(0xffffffffUL).toUInt()

    val out = mutableListOf<UInt>()
    var index = 0
    while (true) {
        if (index == builder63c278VectorWords - 1) {
            val storeValue = (carry.toULong() * 0x3f277405UL + 0xa0c1d6f4UL).and(0xffffffffUL).toUInt()
            val tableOffset = (index * 4) and 0x1c
            out.add(u32TableAffine63c278(storeValue, 0x122248 + tableOffset, 0x1172a8 + tableOffset, tables))
            break
        }

        val nextIndex = index + 1
        val nextTableOffset = (nextIndex and 7) * 4
        val value = u32TableAffine63c278(
            arg0Words[nextIndex],
            0x11c3e8 + nextTableOffset,
            0x11b448 + nextTableOffset,
            tables
        )
        carry = (carry.toULong() * 0x3f277405UL + value.toULong() * 0xa8000000UL).and(0xffffffffUL).toUInt()
        state = builder63c278PrebranchSP4F0State(value, tables)
        selected = builder63c278PrebranchSP4F0FoldState(state, tables)
        val word = (state.toULong() * 0x8e834ce3UL + selected.shl(31).toULong() + 0x0afac599UL).and(0xffffffffUL).toUInt()
        carry = (word.toULong() * 0xb0000000UL + carry.toULong()).and(0xffffffffUL).toUInt()
        val storeValue = carry + 0xc8c1d6f4U
        val tableOffset = (index * 4) and 0x1c
        out.add(u32TableAffine63c278(storeValue, 0x122248 + tableOffset, 0x1172a8 + tableOffset, tables))
        carry = word
        index = nextIndex
    }

    return out
}

internal fun builder63c278PrebranchSP230Words(sp4f0Words: List<UInt>): List<UInt> {
    if (sp4f0Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp4f0Words.size)
    }
    val tables = FirstPairConfig.tables
    val q0 = (0 until 4).map { u32TableWord63c278(0x125f20 + it * 4, tables) }
    val q1 = (0 until 4).map { u32TableWord63c278(0x125f30 + it * 4, tables) }
    val out = (q0 + q1 + q0 + q1 + q0 + q1.subList(0, 2)).toMutableList()

    for (index in sp4f0Words.indices) {
        val word = sp4f0Words[index]
        val tableOffset = (index and 7) * 4
        val mul = u32TableWord63c278(0x11c408 + tableOffset, tables)
        out[index] = (word.toULong() * mul.toULong() + out[index].toULong()).and(0xffffffffUL).toUInt()
    }

    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index and 7) * 4
        val staticWord = foldTableU32Word63c278(0x302148 + index * 4, tables)
        val mul = u32TableWord63c278(0x118588 + tableOffset, tables)
        out[index] = (staticWord.toULong() * mul.toULong() + out[index].toULong()).and(0xffffffffUL).toUInt()
    }

    return out
}

internal fun builder63c278PrebranchSP5A0Words(sp230Words: List<UInt>): List<UInt> {
    if (sp230Words.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp230Words.size)
    }
    val tables = FirstPairConfig.tables
    var carry: UInt = 0xa7964b7dU
    val out = mutableListOf<UInt>()
    for (index in sp230Words.indices) {
        val word = sp230Words[index]
        carry = (carry.toULong() * 0x856c3a53UL + word.toULong()).and(0xffffffffUL).toUInt()
        var folded = (carry.toULong() * 0x287caef9UL + 0x0ac0f465UL).and(0xffffffffUL).toUInt()
        folded = fold32ByNibbles63c278(folded, 0x302238, 7, tables)
        val foldedTail = foldTableU32Word63c278(0x302238 + folded.and(0x0fU).toInt() * 4, tables) + (folded shr 4)
        val nextPart = (carry.toULong() * 0xd8018ba1UL + folded.toULong() * 0x70000000UL + 0x63f7e16aUL).and(0xffffffffUL).toUInt()
        val tail = (folded.toULong() * 0x20718073UL + foldedTail.toULong() * 0xf8e7f8d0UL).and(0xffffffffUL).toUInt()

        val tableOffset = (index * 4) and 0x1c
        out.add(u32TableAffine63c278(nextPart, 0x11a028 + tableOffset, 0x115f48 + tableOffset, tables))
        carry = tail + 0xe056c4a1U
    }
    return out
}

internal fun builder63c278BranchLoop(arg0: ByteArray, sp390Words: List<UInt>, sp440Words: List<UInt>, sp6b0Words: List<UInt>, sp658Words: List<UInt>, sp5a0Words: List<UInt>, maxIterations: Int = 2000): FpQuad<List<UInt>, List<UInt>, List<UInt>, List<UInt>> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg0", arg0.size, builder63c278VectorBytes)
    }
    for (stream in listOf(sp390Words, sp440Words, sp6b0Words, sp658Words, sp5a0Words)) {
        if (stream.size != builder63c278VectorWords) {
            throw FirstPairSourceSliceError.invalid63c278VectorWordCount(stream.size)
        }
    }
    val tables = FirstPairConfig.tables
    var sp390: List<UInt> = sp390Words
    var sp440: List<UInt> = sp440Words
    var sp6b0: List<UInt> = sp6b0Words
    var sp658: List<UInt> = sp658Words
    val sp5a0 = sp5a0Words

    for (i in 0 until maxIterations) {
        if (builder63c278TerminalSP658Ready(sp658, tables)) {
            return FpQuad(sp390, sp440, sp6b0, sp658)
        }

        if (sp6b0[0].and(1U) == 0U) {
            sp6b0 = builder63c278LoopUpdateSP6B0Even(sp6b0, tables)
            sp440 = builder63c278LoopUpdateSP440(sp440, sp5a0, tables)
            continue
        }

        while (sp658[0].and(1U) != 0U) {
            sp658 = builder63c278LoopUpdateSP658Odd(sp658, tables)
            sp390 = builder63c278LoopUpdateSP390(sp390, sp5a0, tables)
        }

        if (builder63c278LoopSP658EvenUsesSuccessPath(sp658, sp6b0, tables)) {
            sp658 = builder63c278LoopUpdateSP658EvenSuccess(sp658, sp6b0, tables)
            if (builder63c278Predicate64D55C(sp440, sp390, tables) == 0) {
                sp390 = builder63c278LoopUpdateSP390PredicateFalse(sp390, arg0, tables)
            }
            sp390 = builder63c278LoopUpdateSP390PredicateJoin(sp390, sp440, tables)
        } else {
            sp6b0 = builder63c278LoopUpdateSP6B0Failure(sp6b0, sp658, tables)
            if (builder63c278Predicate64D55C(sp440, sp390, tables) != 0) {
                sp440 = builder63c278LoopUpdateSP440PredicateTrue(sp440, arg0, tables)
            }
            sp440 = builder63c278LoopUpdateSP440PredicateJoin(sp440, sp390, tables)
        }
    }

    throw FirstPairSourceSliceError.branchLoopDidNotTerminate(maxIterations)
}

internal fun builder63c278FinalScheduleFromSP440U32(sp440Words: List<UInt>): List<UInt> {
    if (sp440Words.size < builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(sp440Words.size)
    }
    val tables = FirstPairConfig.tables
    val staged = mutableListOf<UInt>()
    for (index in 0 until builder63c278VectorWords) {
        val tableOffset = (index * 4) and 0x1c
        staged.add(u32TableAffine63c278(sp440Words[index], 0x117308 + tableOffset, 0x11b508 + tableOffset, tables))
    }

    val out = mutableListOf<UInt>()
    for (index in 0 until 20) {
        val tableOffset = (index * 4) and 0x1c
        out.add(u32TableAffine63c278(staged[index], 0x11f5e8 + tableOffset, 0x1154e8 + tableOffset, tables))
    }
    return out
}

internal fun builder63c278ScheduleWords(arg0: ByteArray, arg1: ByteArray, arg2: ByteArray, scalar: ULong): List<UInt> {
    val initial = builder63c278InitialVectors(arg0, arg1)
    val mixed = builder63c278ScalarMixVector(initial.first, initial.second, scalar)
    val tail1 = builder63c278Tail1U32Words(mixed)

    val second = builder63c278SecondInitialVectors(arg0, arg2)
    val mixed2 = builder63c278ScalarMix2Vector(second.first, second.second, scalar)
    val tail2 = builder63c278Tail2U32Words(mixed2)

    val accum = builder63c278AccumulatorStreams(arg2, tail2)
    val bridge = builder63c278BridgeConvolutionVector(
        accum.first,
        accum.second,
        accum.third,
        accum.fourth
    )
    val bridgeX0 = builder63c278BridgeX0Vector(arg0)
    val bridgeMixed = builder63c278BridgeMixVector(bridge, bridgeX0, scalar)
    val sp128 = builder63c278BridgeSP128Words(bridgeMixed)

    val prebranch = builder63c278PrebranchInitialStreams(arg0, tail1, sp128)
    val pre4f0 = builder63c278PrebranchSP4F0Words(arg0)
    val pre230 = builder63c278PrebranchSP230Words(pre4f0)
    val pre5a0 = builder63c278PrebranchSP5A0Words(pre230)
    val final = builder63c278BranchLoop(
        arg0,
        prebranch.first,
        prebranch.second,
        prebranch.third,
        prebranch.fourth,
        pre5a0
    )
    return builder63c278FinalScheduleFromSP440U32(final.second)
}

internal fun builder6388f0Next642f60InputsFrom64cd40Outputs(first64cd40Output: ByteArray, second64cd40Output: ByteArray, third64cd40Output: ByteArray): Builder6388f0Next642f60Inputs {
    val tables = FirstPairConfig.tables
    return Builder6388f0Next642f60Inputs(
        x0 = u32AffineBytes63c278(
            first64cd40Output,
            builder6388f0Pre63Arg1MulTable,
            builder6388f0Pre63Arg1AddTable,
            "first 64cd40 output",
            tables
        ),
        x1 = u32AffineBytes63c278(
            second64cd40Output,
            builder6388f0Next642X1MulTable,
            builder6388f0Next642X1AddTable,
            "second 64cd40 output",
            tables
        ),
        x2 = u32AffineBytes63c278(
            third64cd40Output,
            builder6388f0Pre63Arg2MulTable,
            builder6388f0Pre63Arg2AddTable,
            "third 64cd40 output",
            tables
        )
    )
}

internal fun builder6388f0StreamStart642f60X0FromOut0Seed(out0Seed: ByteArray): ByteArray {
    val tables = FirstPairConfig.tables
    return u32AffineBytes63c278(
        out0Seed,
        builder6388f0StreamStartOut0To642f60X0MulTable,
        builder6388f0StreamStartOut0To642f60X0AddTable,
        "6388f0 stream-start out0 seed",
        tables
    )
}

internal fun builder6388f0StreamStart642f60X1FromOut1Seed(out1Seed: ByteArray): ByteArray {
    val tables = FirstPairConfig.tables
    return u32AffineBytes63c278(
        out1Seed,
        builder6388f0StreamStartOut1To642f60X1MulTable,
        builder6388f0StreamStartOut1To642f60X1AddTable,
        "6388f0 stream-start out1 seed",
        tables
    )
}

internal fun builder6388f0RecoverStreamStartOut0SeedFrom642f60X0(x0Source: ByteArray): ByteArray {
    val tables = FirstPairConfig.tables
    return u32AffineInverseBytes63c278(
        x0Source,
        builder6388f0StreamStartOut0To642f60X0MulTable,
        builder6388f0StreamStartOut0To642f60X0AddTable,
        "6388f0 stream-start 642f60 x0 source",
        tables
    )
}

internal fun builder6388f0RecoverStreamStartOut1SeedFrom642f60X1(x1Source: ByteArray): ByteArray {
    val tables = FirstPairConfig.tables
    return u32AffineInverseBytes63c278(
        x1Source,
        builder6388f0StreamStartOut1To642f60X1MulTable,
        builder6388f0StreamStartOut1To642f60X1AddTable,
        "6388f0 stream-start 642f60 x1 source",
        tables
    )
}

internal fun builder6421c0X0Streams(x0Source: ByteArray): FpPair<List<ULong>, List<ULong>> {
    if (x0Source.size < 20 * 4) {
        throw FirstPairSourceSliceError.sourceTooShort("6421c0 x0 source", x0Source.size, 20 * 4)
    }
    val tables = FirstPairConfig.tables
    val bytes = x0Source
    val raw = mutableListOf<ULong>()
    for (index in 0 until 20) {
        val word = readUInt32LE(bytes, index * 4)
        val mixed: UInt
        if (index == 0) {
            mixed = word * 0x3239bd21U + 0x5c47f2f0U
        } else {
            val affine = u32Affine63c278(
                word,
                index,
                builder6421c0X0MulTable,
                builder6421c0X0AddTable,
                tables
            )
            mixed = affine * 0x5da2e52fU + 0x6605175eU
        }
        val folded = fold63c278(
            mixed.toULong() * 0x430e55e51aa99355UL + 0x15551dd776f38e14UL,
            builder6421c0X0FoldTable,
            8,
            tables
        )
        raw.add(
            mixed.toULong() * 0xc788d39836400f55UL
                + folded * 0xd50b73ff00000000UL
                + 0xce6055b08c097bf0UL
        )
    }
    return FpPair(raw, prefixSumsU64(raw))
}

internal fun builder6421c0X1Streams(x1Source: ByteArray): FpPair<List<ULong>, List<ULong>> {
    if (x1Source.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6421c0 x1 source", x1Source.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x1Source
    val raw = mutableListOf<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        val word = readUInt32LE(bytes, index * 4)
        val mixed: UInt
        if (index == 0) {
            mixed = word * 0x105085d7U + 0x841874d8U
        } else {
            val affine = u32Affine63c278(
                word,
                index,
                builder6421c0X1MulTable,
                builder6421c0X1AddTable,
                tables
            )
            mixed = affine * 0x97c9fb77U + 0x6b1b1a39U
        }
        val folded = fold63c278(
            mixed.toULong() * 0x8f1272d1ced32651UL + 0x7eda487fd3a46989UL,
            builder6421c0X1FoldTable,
            8,
            tables
        )
        raw.add(
            mixed.toULong() * 0x9dcd2446c70edca3UL
                + folded * 0xe9148d4d00000000UL
                + 0x2df1f5e9fb0ab4f8UL
        )
    }
    return FpPair(raw, prefixSumsU64(raw))
}

internal fun builder6421c0X2Words(x2Source: ByteArray): List<ULong> {
    if (x2Source.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("6421c0 x2 source", x2Source.size, builder63c278VectorBytes)
    }
    val tables = FirstPairConfig.tables
    val bytes = x2Source
    val out = mutableListOf<ULong>()
    for (index in 0 until builder63c278VectorWords) {
        val affine = u32Affine63c278(
            readUInt32LE(bytes, index * 4),
            index,
            builder6421c0X2MulTable,
            builder6421c0X2AddTable,
            tables
        )
        val mixed = affine * 0x6819ef77U + 0x57cf46ceU
        val folded = fold63c278(
            mixed.toULong() * 0xc4e90084bd222fd1UL + 0xf9e4937efa15a0b7UL,
            builder6421c0X2FoldTable,
            8,
            tables
        )
        out.add(
            mixed.toULong() * 0x06447e0a39c79467UL
                + folded * 0xcfaf794900000000UL
                + 0xe882bfc48de82700UL
        )
    }
    return out
}
