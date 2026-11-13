// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.pairing.Phase5KeySchedule
import com.dsikic.cluecose.core.tables.RuntimeTable
import com.dsikic.cluecose.core.tables.SourceTables

internal fun builder6388f0LaneBlocksFromScheduleWords(scheduleWords: List<UInt>): FpPair<ByteArray, ByteArray> {
    if (scheduleWords.size != builder6388f0LaneBlockCount) {
        throw FirstPairSourceSliceError.invalid6388f0ScheduleWordCount(scheduleWords.size)
    }
    val tables = FirstPairConfig.tables
    val primaryStatic = checkedSlice(
        tables.laneTables6388f0,
        builder6388f0LanePrimaryStaticOffset,
        builder6388f0LaneTableExpandedSize,
        RuntimeTable.FIRST_PAIR_6388F0_LANE_TABLES.fileName
    )
    val secondaryStatic = checkedSlice(
        tables.laneTables6388f0,
        builder6388f0LaneSecondaryStaticOffset,
        builder6388f0LaneTableExpandedSize,
        RuntimeTable.FIRST_PAIR_6388F0_LANE_TABLES.fileName
    )

    var primaryLanes = ByteArray(0)
    var secondaryLanes = ByteArray(0)

    for ((index, word) in scheduleWords.withIndex()) {
        val selector = selector6388f0(index, word, tables)
        var primaryState = checkedSlice(
            tables.laneTables6388f0,
            builder6388f0LaneAInitOffset,
            builder6388f0LaneBlockSize,
            RuntimeTable.FIRST_PAIR_6388F0_LANE_TABLES.fileName
        )
        primaryState = primaryState.concat(byteArrayOf(0x05.toByte(), 0x04.toByte()))
        var secondaryState = checkedSlice(
            tables.laneTables6388f0,
            builder6388f0LaneBInitOffset,
            builder6388f0LaneBlockSize,
            RuntimeTable.FIRST_PAIR_6388F0_LANE_TABLES.fileName
        )
        secondaryState = secondaryState.concat(byteArrayOf(0x05.toByte(), 0x02.toByte()))

        for (shift in listOf(24, 16, 8, 0)) {
            val primaryPrimer = lanePrefixed6388f0(0x03000000U, primaryState)
            primaryState = vm638840(
                0x1200000712fUL,
                primaryPrimer,
                primaryPrimer,
                tables
            )

            val secondaryPrimer = lanePrefixed6388f0(0x01000000U, secondaryState)
            secondaryState = vm638840(
                0x120000003aaUL,
                secondaryPrimer,
                secondaryPrimer,
                tables
            )

            val selectorByte = ((selector shr shift) and 0xFFU).toByte()
            primaryState = vm638840(
                0x1200000551aUL,
                primaryState,
                laneTable6388f0Expand(
                    builder6388f0LaneATableOffset,
                    selectorByte,
                    tables
                ),
                tables
            )
            secondaryState = vm638840(
                0x12000000c60UL,
                secondaryState,
                laneTable6388f0Expand(
                    builder6388f0LaneBTableOffset,
                    selectorByte,
                    tables
                ),
                tables
            )
        }

        val primarySource = vm638840(
            0x12000003d45UL,
            primaryState,
            primaryStatic,
            tables
        )
        val secondarySource = vm638840(
            0x12000005e9dUL,
            secondaryState,
            secondaryStatic,
            tables
        )
        primaryLanes = primaryLanes.concat(
            vm638840(
                0x10000000214UL,
                primarySource,
                primarySource,
                tables
            )
        )
        secondaryLanes = secondaryLanes.concat(
            vm638840(
                0x10000003231UL,
                secondarySource,
                secondarySource,
                tables
            )
        )
    }

    return FpPair(primaryLanes, secondaryLanes)
}

internal fun deriveFrom6388f0InternalStreams(firstInternalBlocks: ByteArray, secondInternalBlocks: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val firstRaw = builder6388f0FinalRawBlocks(firstInternalBlocks)
    val secondRaw = builder6388f0FinalRawBlocks(secondInternalBlocks)
    return deriveFrom64d774RawStreams(
        firstRaw,
        secondRaw,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0PrefinalLen32Streams(firstPrefinalBlocks: ByteArray, secondPrefinalBlocks: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val firstInternal = builder6388f0PrefinalLen32InternalBlocks(firstPrefinalBlocks)
    val secondInternal = builder6388f0PrefinalLen32InternalBlocks(secondPrefinalBlocks)
    return deriveFrom6388f0InternalStreams(
        firstInternal,
        secondInternal,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0WorkspaceLen32Streams(firstWorkspaceSource: ByteArray, secondWorkspaceSource: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val firstPrefinal = builder6388f0Len32PrefinalSourcesFromWorkspace(firstWorkspaceSource)
    val secondPrefinal = builder6388f0Len32PrefinalSourcesFromWorkspace(secondWorkspaceSource)
    return deriveFrom6388f0PrefinalLen32Streams(
        firstPrefinal,
        secondPrefinal,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0StageLen32Streams(firstStageASource: ByteArray, firstStageBSource: ByteArray, secondStageASource: ByteArray, secondStageBSource: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val firstPrefinal = builder6388f0Len32PrefinalSourcesFromStageInputs(
        firstStageASource,
        firstStageBSource
    )
    val secondPrefinal = builder6388f0Len32PrefinalSourcesFromStageInputs(
        secondStageASource,
        secondStageBSource
    )
    return deriveFrom6388f0PrefinalLen32Streams(
        firstPrefinal,
        secondPrefinal,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0PackLen32Streams(firstStageBPackHead16: ByteArray, firstStageBPackBody16: ByteArray, firstStageAPackHead16: ByteArray, firstStageAPackBody16: ByteArray, secondStageBPackHead16: ByteArray, secondStageBPackBody16: ByteArray, secondStageAPackHead16: ByteArray, secondStageAPackBody16: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val firstStage = builder6388f0Len32StageInputsFromPackOutputs(
        firstStageBPackHead16,
        firstStageBPackBody16,
        firstStageAPackHead16,
        firstStageAPackBody16
    )
    val secondStage = builder6388f0Len32StageInputsFromPackOutputs(
        secondStageBPackHead16,
        secondStageBPackBody16,
        secondStageAPackHead16,
        secondStageAPackBody16
    )
    return deriveFrom6388f0StageLen32Streams(
        firstStage.first,
        firstStage.second,
        secondStage.first,
        secondStage.second,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0LaneLen32Streams(firstPrimaryLaneBlocks: ByteArray, firstSecondaryLaneBlocks: ByteArray, secondPrimaryLaneBlocks: ByteArray, secondSecondaryLaneBlocks: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val firstPack = builder6388f0PackOutputsFromLaneBlocks(
        firstPrimaryLaneBlocks,
        firstSecondaryLaneBlocks
    )
    val secondPack = builder6388f0PackOutputsFromLaneBlocks(
        secondPrimaryLaneBlocks,
        secondSecondaryLaneBlocks
    )
    return deriveFrom6388f0PackLen32Streams(
        firstPack.first,
        firstPack.second,
        firstPack.third,
        firstPack.fourth,
        secondPack.first,
        secondPack.second,
        secondPack.third,
        secondPack.fourth,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0ScheduleLen32Streams(firstScheduleWords: List<UInt>, secondScheduleWords: List<UInt>, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val firstLanes = builder6388f0LaneBlocksFromScheduleWords(firstScheduleWords)
    val secondLanes = builder6388f0LaneBlocksFromScheduleWords(secondScheduleWords)
    return deriveFrom6388f0LaneLen32Streams(
        firstLanes.first,
        firstLanes.second,
        secondLanes.first,
        secondLanes.second,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom63c278ScheduleInputs(arg0: ByteArray, firstArg1: ByteArray, firstArg2: ByteArray, secondArg1: ByteArray, secondArg2: ByteArray, scalar: ULong, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val firstSchedule = builder63c278ScheduleWords(
        arg0,
        firstArg1,
        firstArg2,
        scalar
    )
    val secondSchedule = builder63c278ScheduleWords(
        arg0,
        secondArg1,
        secondArg2,
        scalar
    )
    return deriveFrom6388f0ScheduleLen32Streams(
        firstSchedule,
        secondSchedule,
        src4,
        offset,
        length
    )
}

internal fun deriveFromPre63c278ScheduleInputs(firstArg1: ByteArray, firstArg2: ByteArray, secondArg1: ByteArray, secondArg2: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    return deriveFrom63c278ScheduleInputs(
        pre63c278Arg0Source,
        firstArg1,
        firstArg2,
        secondArg1,
        secondArg2,
        pre63c278Scalar,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0SeededCaller64Rows(rows: List<Builder6388f0SeededCaller64Row>, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    return deriveFrom6388f0SeededCaller64Rows(
        rows,
        pre63c278Arg0Source,
        pre63c278Scalar,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0SeededCaller64Rows(rows: List<Builder6388f0SeededCaller64Row>, arg0: ByteArray, scalar: ULong, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val schedules = builder6388f0Seeded63c278SchedulesFromRows(
        rows,
        arg0,
        scalar
    )
    return deriveFrom6388f0ScheduleLen32Streams(
        schedules.first.scheduleWords,
        schedules.second.scheduleWords,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0FirstPairStreamSeeds(seeds: Builder6388f0FirstPairStreamSeeds, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    return deriveFrom6388f0FirstPairStreamSeeds(
        seeds,
        pre63c278Arg0Source,
        pre63c278Scalar,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0FirstPairStreamSeeds(seeds: Builder6388f0FirstPairStreamSeeds, arg0: ByteArray, scalar: ULong, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val rows = builder6388f0SeededCaller64RowsFromFirstPairStreamSeeds(seeds)
    return deriveFrom6388f0SeededCaller64Rows(
        rows,
        arg0,
        scalar,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0FirstPairEntropyAndSensorPoints(entrySource: ByteArray, nullEntropy11A: ByteArray, row0SensorPointXYBE: ByteArray, row59SensorPointXYBE: ByteArray, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10): ByteArray {
    val seeds = builder6388f0FirstPairStreamSeedsFromEntropyAndSensorPoints(
        entrySource,
        nullEntropy11A,
        row0SensorPointXYBE,
        row59SensorPointXYBE
    )
    return deriveFrom6388f0FirstPairStreamSeeds(
        seeds,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom6388f0FirstPairEntropySourceAndSensorPoints(entrySource: ByteArray, row0SensorPointXYBE: ByteArray, row59SensorPointXYBE: ByteArray, maxAttempts: Int = 64, src4: ByteArray = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()), offset: Int = 0, length: Int = 0x10, entropySource: (Int) -> ByteArray): ByteArray {
    val seeds = builder6388f0FirstPairStreamSeedsFromEntropySourceAndSensorPoints(
        entrySource,
        row0SensorPointXYBE,
        row59SensorPointXYBE,
        maxAttempts = maxAttempts,
        entropySource = entropySource
    )
    return deriveFrom6388f0FirstPairStreamSeeds(
        seeds,
        src4,
        offset,
        length
    )
}

internal fun deriveFrom679f48Context(context: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    val finalized = finalize679f48ToSecondDF80(context)
    return deriveFromFinalized679f48Context(finalized, offset, length)
}

internal fun finalize679f48ToSecondDF80(context: ByteArray): ByteArray {
    if (context.size < context679f48Size) {
        throw FirstPairSourceSliceError.sourceTooShort("679f48 context", context.size, context679f48Size)
    }
    val tables = FirstPairConfig.tables
    val ctx = context.copyOf()
    val contextLength = readUInt64LE(ctx, 0)
    // Swift: guard contextLength <= UInt64(Int.max) — Int is 64-bit, so the bound
    // is 0x7fffffffffffffff. (Error field is Int, so the thrown value is the
    // 32-bit truncation of 64-bit Int.max.)
    if (contextLength > Long.MAX_VALUE.toULong()) {
        throw FirstPairSourceSliceError.invalidContextLength(Int.MAX_VALUE)
    }
    val low = (contextLength and 0x0FUL).toInt()
    var blockIndex = readUInt32LE(ctx, 0x110)
    if (blockIndex > 4U) {
        throw FirstPairSourceSliceError.invalid679f48BlockIndex(blockIndex)
    }

    val slot = 0x08 + blockIndex.toInt() * block66Size
    if (low != 0) {
        val padIndex = low xor 0x0f
        val pad1 = checkedSlice(
            tables.finalizerTables,
            finalizerDD7CPadOffset + padIndex * block66Size,
            block66Size,
            RuntimeTable.FIRST_PAIR_FINALIZER_TABLES.fileName
        )
        val pad2 = checkedSlice(
            tables.finalizerTables,
            finalizerPad2Offset + padIndex * block66Size,
            block66Size,
            RuntimeTable.FIRST_PAIR_FINALIZER_TABLES.fileName
        )
        val current = checkedSlice(ctx, slot, block66Size, "679f48 finalizer context")
        val mixed = vm67cc18(0x42000005702UL, current, pad1, tables)
        replace(
            ctx,
            slot,
            vm67cc18(0x42000005c47UL, mixed, pad2, tables)
        )
    } else {
        val staticBlock = checkedSlice(
            tables.finalizerTables,
            finalizerZeroLowBlockOffset,
            block66Size,
            RuntimeTable.FIRST_PAIR_FINALIZER_TABLES.fileName
        )
        replace(
            ctx,
            slot,
            vm67cc18(0x42000000ffbUL, staticBlock, staticBlock, tables)
        )
    }

    if (low > 7 || blockIndex <= 2U) {
        blockIndex += 1U
        writeUInt32LE(blockIndex, ctx, 0x110)
        if (blockIndex == 4U) {
            val transformed = df80Transform(
                ctx.copyOfRange(0x114, 0x1a4),
                ctx.copyOfRange(0x08, 0x110)
            )
            replace(ctx, 0x114, transformed)
            blockIndex = 0U
            writeUInt32LE(0U, ctx, 0x110)
        }

        if (blockIndex <= 3U) {
            val staticBlock = checkedSlice(
                tables.finalizerTables,
                finalizerStaticBlockOffset,
                block66Size,
                RuntimeTable.FIRST_PAIR_FINALIZER_TABLES.fileName
            )
            while (blockIndex < 4U) {
                val fillSlot = 0x08 + blockIndex.toInt() * block66Size
                replace(
                    ctx,
                    fillSlot,
                    vm67cc18(
                        0x42000005d9dUL,
                        staticBlock,
                        staticBlock,
                        tables
                    )
                )
                blockIndex += 1U
                writeUInt32LE(blockIndex, ctx, 0x110)
            }
        }
    }

    writeUInt64LE(contextLength shl 3, ctx, 0)
    val finalLength = final679f48LengthBlock(contextLength.toInt())
    val finalMixed = vm67cc18(
        0x420000040aaUL,
        finalLength,
        ctx.copyOfRange(0xce, 0x110),
        tables
    )
    replace(ctx, 0xce, finalMixed)
    val transformed = df80Transform(
        ctx.copyOfRange(0x114, 0x1a4),
        ctx.copyOfRange(0x08, 0x110)
    )
    replace(ctx, 0x114, transformed)
    return ctx
}

internal fun df80Transform(state: ByteArray, blocks: ByteArray): ByteArray {
    val workspace = df80InitialWorkspace(blocks)
    val schedule = df80ExpandedSchedule(workspace)
    return df80CompressState(state, schedule)
}

internal fun df80CompressState(state: ByteArray, schedule: ByteArray): ByteArray {
    if (state.size != df80StateSize) {
        throw FirstPairSourceSliceError.invalidDF80StateLength(state.size)
    }
    if (schedule.size != df80ScheduleSize) {
        throw FirstPairSourceSliceError.invalidDF80ScheduleLength(schedule.size)
    }
    val tables = FirstPairConfig.tables
    val stateBytes = state
    val scheduleBytes = schedule
    val original = mutableListOf<ByteArray>()
    for (start in 0 until df80StateSize step df80WordSize) {
        original.add(stateBytes.copyOfRange(start, start + df80WordSize))
    }

    var s0 = vm67cc18(0x1200000189dUL, original[0], original[0], tables)
    var s1 = vm67cc18(0x12000001e60UL, original[1], original[1], tables)
    var s2 = vm67cc18(0x1200000152dUL, original[2], original[2], tables)
    var s3 = vm67cc18(0x120000029edUL, original[3], original[3], tables)
    var s4 = vm67cc18(0x120000040ecUL, original[4], original[4], tables)
    var s5 = vm67cc18(0x120000036edUL, original[5], original[5], tables)
    var s6 = vm67cc18(0x12000003423UL, original[6], original[6], tables)
    var s7 = vm67cc18(0x12000004056UL, original[7], original[7], tables)

    val staticB = checkedSlice(
        tables.df80RoundTables,
        df80ScheduleSize,
        df80WordSize,
        RuntimeTable.FIRST_PAIR_DF80_ROUND_TABLES.fileName
    )

    for (offset in 0 until df80ScheduleSize step df80WordSize) {
        val word = scheduleBytes.copyOfRange(offset, offset + df80WordSize)
        val roundA = checkedSlice(
            tables.df80RoundTables,
            offset,
            df80WordSize,
            RuntimeTable.FIRST_PAIR_DF80_ROUND_TABLES.fileName
        )

        var tmp80 = vm67cecc(0x0c00f000c01a40UL, s4, s4, tables)
        var tmp58 = packDF80Zeros12Marker3(s4)
        var tmp22 = vm67cc18(0x120000032dcUL, tmp58, tmp58, tables)
        val mix10 = vm67cc18(0x12000001105UL, tmp22, tmp80, tables)

        tmp22 = vm67cecc(0x1800c0018050f7UL, s4, s4, tables)
        tmp80 = vm67cc18(0x12000002cc3UL, s4, s4, tables)
        tmp58 = packDF80Zeros8Zero6(tmp80)
        var t64 = vm67cc18(0x120000030c4UL, tmp58, tmp58, tables)
        val mix14 = vm67cc18(0x12000001251UL, t64, tmp22, tables)

        val mix15 = vm67cecc(0x34005003403785UL, s4, s4, tables)
        tmp80 = vm67cc18(0x120000025a0UL, s4, s4, tables)
        tmp58 = packDF80Zeros2Marker6(tmp80)
        val mix17 = vm67cc18(0x12000000d92UL, tmp58, tmp58, tables)
        val mix18 = vm67cc18(0x120000026f3UL, mix17, mix15, tables)
        val mix19 = vm67cc18(0x12000000e82UL, mix10, mix14, tables)
        var t76 = vm67cc18(0x120000023eeUL, mix18, mix19, tables)
        t76 = vm67cc18(0x120000043a5UL, s7, t76, tables)

        t64 = vm67cc18(0x12000005386UL, roundA, word, tables)
        val t52 = vm67cc18(0x12000004501UL, t76, t64, tables)

        tmp58 = vm67cc18(0x12000000ce4UL, s4, s5, tables)
        tmp80 = vm67cc18(0x12000003aa4UL, staticB, s4, tables)
        tmp22 = vm67cc18(0x12000000f71UL, tmp80, s6, tables)
        val t40 = vm67cc18(0x120000020bcUL, tmp58, tmp22, tables)
        val tmp92 = vm67cc18(0x12000000aa6UL, t52, t40, tables)

        tmp80 = vm67cecc(0x04011000404984UL, s0, s0, tables)
        tmp58 = packDF80Zeros14Marker1(s0)
        tmp22 = vm67cc18(0x1200000190bUL, tmp58, tmp58, tables)
        val t2e = vm67cc18(0x12000003cd1UL, tmp22, tmp80, tables)

        tmp22 = vm67cecc(0x1c00b001c048a7UL, s0, s0, tables)
        tmp80 = vm67cc18(0x12000003683UL, s0, s0, tables)
        tmp58 = packDF80Zeros9(tmp80)
        tmp58 = vm67cc18(0x120000017b1UL, tmp58, tmp58, tables)
        val t1c = vm67cc18(0x12000000d5eUL, tmp58, tmp22, tables)

        tmp80 = vm67cecc(0x2c007002c000baUL, s0, s0, tables)
        tmp58 = packDF80Zeros4Marker3(s0)
        tmp22 = vm67cc18(0x120000001fdUL, tmp58, tmp58, tables)
        val tmp34First = vm67cc18(0x12000006062UL, tmp22, tmp80, tables)

        tmp80 = vm67cc18(0x12000004ffbUL, t2e, t1c, tables)
        tmp58 = vm67cc18(0x120000032caUL, tmp34First, tmp80, tables)

        tmp22 = vm67cc18(0x12000000d08UL, s0, s1, tables)
        val tmp34 = vm67cc18(0x120000019b6UL, s0, s2, tables)
        val t2eSecond = vm67cc18(0x12000004352UL, s1, s2, tables)
        val t1cSecond = vm67cc18(0x12000000f5fUL, tmp22, tmp34, tables)
        tmp80 = vm67cc18(0x12000004010UL, t2eSecond, t1cSecond, tables)
        val tmpa4 = vm67cc18(0x120000035d7UL, tmp58, tmp80, tables)

        val newS7 = vm67cc18(0x12000003bc0UL, s6, s6, tables)
        val newS6 = vm67cc18(0x120000042aaUL, s5, s5, tables)
        val newS5 = vm67cc18(0x120000042bcUL, s4, s4, tables)
        val newS4 = vm67cc18(0x12000006050UL, s3, tmp92, tables)
        val newS3 = vm67cc18(0x120000047c5UL, s2, s2, tables)
        val newS2 = vm67cc18(0x12000004e83UL, s1, s1, tables)
        val newS1 = vm67cc18(0x120000055c9UL, s0, s0, tables)
        val newS0 = vm67cc18(0x12000002088UL, tmp92, tmpa4, tables)

        s0 = newS0
        s1 = newS1
        s2 = newS2
        s3 = newS3
        s4 = newS4
        s5 = newS5
        s6 = newS6
        s7 = newS7
    }

    var out = ByteArray(0)
    out = out.concat(vm67cc18(0x12000001eb4UL, original[0], s0, tables))
    out = out.concat(vm67cc18(0x12000005b5bUL, original[1], s1, tables))
    out = out.concat(vm67cc18(0x120000042e6UL, original[2], s2, tables))
    out = out.concat(vm67cc18(0x12000000b94UL, original[3], s3, tables))
    out = out.concat(vm67cc18(0x1200000383aUL, original[4], s4, tables))
    out = out.concat(vm67cc18(0x12000003581UL, original[5], s5, tables))
    out = out.concat(vm67cc18(0x12000004deaUL, original[6], s6, tables))
    out = out.concat(vm67cc18(0x12000000dd8UL, original[7], s7, tables))
    return out
}

internal fun df80ExpandedSchedule(initialWorkspace: ByteArray): ByteArray {
    if (initialWorkspace.size != df80InitialWorkspaceSize) {
        throw FirstPairSourceSliceError.invalidDF80WorkspaceLength(initialWorkspace.size)
    }
    val tables = FirstPairConfig.tables
    val schedule = initialWorkspace.concat(ByteArray(df80DerivedScheduleSize))

    for (offset in 0 until df80DerivedScheduleSize step df80WordSize) {
        val w0 = schedule.copyOfRange(offset, offset + df80WordSize)
        val w1 = schedule.copyOfRange(offset + 0x12, offset + 0x24)
        val w9 = schedule.copyOfRange(offset + 0xa2, offset + 0xb4)
        val w14 = schedule.copyOfRange(offset + 0xfc, offset + 0x10e)

        var tmp22 = vm67cecc(0x2400900240463cUL, w14, w14, tables)
        var tmp80 = vm67cc18(0x12000005af5UL, w14, w14, tables)
        var tmp58 = packDF80Zeros6Marker(0x06.toByte(), tmp80)
        var tmp34 = vm67cc18(0x12000005ddfUL, tmp58, tmp58, tables)
        var tmpa4 = vm67cc18(0x12000000523UL, tmp34, tmp22, tables)

        tmp22 = vm67cecc(0x2800800280004aUL, w14, w14, tables)
        tmp80 = vm67cc18(0x12000002717UL, w14, w14, tables)
        tmp58 = packDF80Zeros5Marker6(tmp80)
        tmp34 = vm67cc18(0x1200000304eUL, tmp58, tmp58, tables)
        tmp58 = vm67cc18(0x120000016d7UL, tmp34, tmp22, tables)

        tmp80 = vm67cecc(0x1400d001403ea3UL, w14, w14, tables)
        tmp22 = vm67cc18(0x120000037a4UL, tmpa4, tmp58, tables)
        val tmp92 = vm67cc18(0x120000034f0UL, tmp80, tmp22, tables)
        tmpa4 = vm67cc18(0x1200000050bUL, tmp92, w9, tables)

        tmp22 = vm67cecc(0x1000e0010042f8UL, w1, w1, tables)
        tmp80 = vm67cc18(0x12000000251UL, w1, w1, tables)
        tmp58 = packDF80Zeros11Marker5(tmp80)
        tmp34 = vm67cc18(0x12000005e68UL, tmp58, tmp58, tables)
        val tmp118 = vm67cc18(0x12000004120UL, tmp34, tmp22, tables)

        tmp80 = vm67cecc(0x24009002402391UL, w1, w1, tables)
        tmp58 = packDF80Zeros6Marker(0x07.toByte(), w1)
        tmp22 = vm67cc18(0x12000000da4UL, tmp58, tmp58, tables)
        tmp34 = vm67cc18(0x12000003e91UL, tmp22, tmp80, tables)

        tmp80 = vm67cecc(0x08010000802f7eUL, w1, w1, tables)
        tmp22 = vm67cc18(0x120000041eaUL, tmp118, tmp34, tables)
        tmp58 = vm67cc18(0x12000000846UL, tmp80, tmp22, tables)
        tmp80 = vm67cc18(0x120000019eaUL, tmp58, w0, tables)
        val derived = vm67cc18(0x12000003ac8UL, tmpa4, tmp80, tables)
        replace(schedule, offset + df80InitialWorkspaceSize, derived)
    }

    return schedule
}

internal fun df80InitialWorkspace(blocks: ByteArray): ByteArray {
    if (blocks.size != df80InputBlockCount * block66Size) {
        throw FirstPairSourceSliceError.invalidDF80BlockLength(blocks.size)
    }
    val tables = FirstPairConfig.tables
    val blockBytes = blocks
    val workspace = ByteArray(df80InitialWorkspaceSize)

    for (index in 0 until df80InputBlockCount) {
        val start = index * block66Size
        val dst = index * df80InitialWorkspaceStride
        val src = blockBytes.copyOfRange(start, start + block66Size)
        val sideA = vm67cc18(0x22000002444UL, src, src, tables)
        val sideB = vm67cecc(0x22008004942UL, src, src, tables)

        replace(
            workspace,
            dst + 0x36,
            vm67cc18(0x12000000deaUL, sideA, sideA, tables)
        )
        replace(
            workspace,
            dst + 0x24,
            vm67cecc(0x12004003dcdUL, sideA, sideA, tables)
        )
        replace(
            workspace,
            dst + 0x12,
            vm67cc18(0x120000052bbUL, sideB, sideB, tables)
        )
        replace(
            workspace,
            dst,
            vm67cecc(0x12004003d69UL, sideB, sideB, tables)
        )
    }

    return workspace
}

internal fun final679f48LengthBlock(contextLength: Int): ByteArray {
    if (contextLength < 0) {
        throw FirstPairSourceSliceError.invalidContextLength(contextLength)
    }
    val tables = FirstPairConfig.tables
    val bitLength = contextLength.toULong() shl 3
    val sideA = expandU64Trits(bitLength, 0, tables)
    val sideB = expandU64Trits(bitLength, 0x300, tables)

    val foldedA = fold48To34(0x600000051dUL, 0x6000002556UL, sideA, tables)
    val laneA = vm67cecc(0x800220000010a1UL, foldedA, foldedA, tables)

    val foldedB = fold48To34(0x60000018afUL, 0x6000005ee6UL, sideB, tables)
    val laneB = vm67cecc(0x80022000004224UL, foldedB, foldedB, tables)

    val mixed = vm67cc18(0x420000007c0UL, laneA, laneB, tables)
    return vm67d524(0xc03f000c0192fUL, mixed, tables)
}

internal fun phase5RawKeyFrom67cc18Sources(sourceChunks: ByteArray, offset: Int = 0): ByteArray {
    val source = deriveFrom67cc18Sources(sourceChunks, offset, 0x10)
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom67a960Inputs(src1: ByteArray, src2: ByteArray, offset: Int = 0): ByteArray {
    val source = deriveFrom67a960Inputs(src1, src2, offset, 0x10)
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFromFinalized679f48Context(context: ByteArray, offset: Int = 0): ByteArray {
    val source = deriveFromFinalized679f48Context(context, offset, 0x10)
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom64de54EncodedBlocks(encodedBlocks: ByteArray, offset: Int = 0): ByteArray {
    val source = derive64de54Slice(encodedBlocks, offset, 0x10)
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom6388f0ScheduleLen32Streams(firstScheduleWords: List<UInt>, secondScheduleWords: List<UInt>, offset: Int = 0): ByteArray {
    val source = deriveFrom6388f0ScheduleLen32Streams(
        firstScheduleWords,
        secondScheduleWords,
        byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()),
        offset,
        0x10
    )
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom63c278ScheduleInputs(arg0: ByteArray, firstArg1: ByteArray, firstArg2: ByteArray, secondArg1: ByteArray, secondArg2: ByteArray, scalar: ULong, offset: Int = 0): ByteArray {
    val source = deriveFrom63c278ScheduleInputs(
        arg0,
        firstArg1,
        firstArg2,
        secondArg1,
        secondArg2,
        scalar,
        byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()),
        offset,
        0x10
    )
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFromPre63c278ScheduleInputs(firstArg1: ByteArray, firstArg2: ByteArray, secondArg1: ByteArray, secondArg2: ByteArray, offset: Int = 0): ByteArray {
    val source = deriveFromPre63c278ScheduleInputs(
        firstArg1,
        firstArg2,
        secondArg1,
        secondArg2,
        byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()),
        offset,
        0x10
    )
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom6388f0SeededCaller64Rows(rows: List<Builder6388f0SeededCaller64Row>, offset: Int = 0): ByteArray {
    val source = deriveFrom6388f0SeededCaller64Rows(
        rows,
        byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()),
        offset,
        0x10
    )
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom6388f0SeededCaller64Rows(rows: List<Builder6388f0SeededCaller64Row>, arg0: ByteArray, scalar: ULong, offset: Int = 0): ByteArray {
    val source = deriveFrom6388f0SeededCaller64Rows(
        rows,
        arg0,
        scalar,
        byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()),
        offset,
        0x10
    )
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom6388f0FirstPairStreamSeeds(seeds: Builder6388f0FirstPairStreamSeeds, offset: Int = 0): ByteArray {
    val source = deriveFrom6388f0FirstPairStreamSeeds(
        seeds,
        byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()),
        offset,
        0x10
    )
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom6388f0FirstPairStreamSeeds(seeds: Builder6388f0FirstPairStreamSeeds, arg0: ByteArray, scalar: ULong, offset: Int = 0): ByteArray {
    val source = deriveFrom6388f0FirstPairStreamSeeds(
        seeds,
        arg0,
        scalar,
        byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()),
        offset,
        0x10
    )
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom6388f0FirstPairEntropyAndSensorPoints(entrySource: ByteArray, nullEntropy11A: ByteArray, row0SensorPointXYBE: ByteArray, row59SensorPointXYBE: ByteArray, offset: Int = 0): ByteArray {
    val source = deriveFrom6388f0FirstPairEntropyAndSensorPoints(
        entrySource,
        nullEntropy11A,
        row0SensorPointXYBE,
        row59SensorPointXYBE,
        byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()),
        offset,
        0x10
    )
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun phase5RawKeyFrom6388f0FirstPairEntropySourceAndSensorPoints(entrySource: ByteArray, row0SensorPointXYBE: ByteArray, row59SensorPointXYBE: ByteArray, maxAttempts: Int = 64, offset: Int = 0, entropySource: (Int) -> ByteArray): ByteArray {
    val source = deriveFrom6388f0FirstPairEntropySourceAndSensorPoints(
        entrySource,
        row0SensorPointXYBE,
        row59SensorPointXYBE,
        maxAttempts,
        byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()),
        offset,
        0x10,
        entropySource = entropySource
    )
    return Phase5KeySchedule(FirstPairConfig.tablesSource ?: error(
        "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
    )).deriveRawKey(source)
}

internal fun deriveFrom67cc18Sources(sourceChunks: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (sourceChunks.size % block66Size != 0) {
        throw FirstPairSourceSliceError.invalidEncodedBlockLength(sourceChunks.size)
    }
    val tables = FirstPairConfig.tables
    val source = sourceChunks
    var encoded = ByteArray(0)
    for (start in 0 until source.size step block66Size) {
        val chunk = source.copyOfRange(start, start + block66Size)
        encoded = encoded.concat(vm67cc18(0x420000059c9UL, chunk, chunk, tables))
    }
    return derive64de54Slice(encoded, offset, length)
}

internal fun deriveFrom67a960Inputs(src1: ByteArray, src2: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (src1.size != scratch130Size || src2.size != scratch130Size) {
        throw FirstPairSourceSliceError.sourceTooShort(
            "67a960 sources",
            minOf(src1.size, src2.size),
            scratch130Size
        )
    }
    val tables = FirstPairConfig.tables
    val source67a978 = vm67cc18(
        0x1c0012000003b1cUL,
        src1,
        src2,
        tables
    )
    return deriveFrom67a978Source(source67a978, offset, length)
}

internal fun deriveFromFinalized679f48Context(context: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (context.size < context679f48Size) {
        throw FirstPairSourceSliceError.sourceTooShort("679f48 context", context.size, context679f48Size)
    }
    val tables = FirstPairConfig.tables
    val srcPair = postDF80_67a960Inputs(context, tables)
    return deriveFrom67a960Inputs(srcPair.first, srcPair.second, offset, length)
}

internal fun deriveFrom67a978Source(source: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (source.size != scratch130Size) {
        throw FirstPairSourceSliceError.sourceTooShort("67a978 source", source.size, scratch130Size)
    }
    val tables = FirstPairConfig.tables
    val source67a990 = vm67cc18(
        0x82000000477UL,
        source,
        source,
        tables
    )
    return deriveFrom67a990Source(source67a990, offset, length)
}

internal fun deriveFrom67a990Source(source: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (source.size != scratch130Size) {
        throw FirstPairSourceSliceError.sourceTooShort("67a990 source", source.size, scratch130Size)
    }
    val tables = FirstPairConfig.tables
    val window = vm67cc18(
        0x82000003c2dUL,
        source,
        source,
        tables
    )
    val chunks = final67cc18Sources(window, tables)
    return deriveFrom67cc18Sources(chunks, offset, length)
}

internal fun derive64de54Slice(encodedBlocks: ByteArray, offset: Int = 0, length: Int = 0x10): ByteArray {
    if (offset < 0 || length < 0) {
        throw FirstPairSourceSliceError.invalidSlice(offset, length)
    }
    if (encodedBlocks.size % block66Size != 0) {
        throw FirstPairSourceSliceError.invalidEncodedBlockLength(encodedBlocks.size)
    }

    val tables = FirstPairConfig.tables
    val source = encodedBlocks
    val sourceBlocks = mutableListOf<ByteArray>()
    for (start in 0 until source.size step block66Size) {
        sourceBlocks.add(source.copyOfRange(start, start + block66Size))
    }
    if (sourceBlocks.isEmpty() && length > 0) {
        throw FirstPairSourceSliceError.emptySource
    }

    val expanded = sourceBlocks.map {
        vm64e2b8(0x42000000106UL, it, it, tables)
    }

    val startBlock = offset shr 4
    val lowNibble = offset and 0x0f
    val outBlocks = (length + 0x0f) shr 4
    val stageBlocks = mutableListOf<ByteArray>()

    for (outIndex in 0 until outBlocks) {
        val idx = startBlock + outIndex
        if (idx >= expanded.size) {
            throw FirstPairSourceSliceError.sliceStartsPastSource(idx)
        }

        val scratchSrc2 = shiftedScratch(expanded[idx])
        val scratch: ByteArray
        if (idx + 1 < expanded.size) {
            val src1 = expanded[idx + 1].concat(ByteArray(64))
            scratch = vm64e2b8(
                0x100042000000148UL,
                src1,
                scratchSrc2,
                tables
            )
        } else {
            scratch = vm64e2b8(
                0x82000000084UL,
                scratchSrc2,
                scratchSrc2,
                tables
            )
        }

        var shifted = scratch
        for (loopCount in 0 until (16 - lowNibble)) {
            shifted = vm64e17c(shifted, shifted, tables)
        }
        stageBlocks.add(
            vm64e2b8(0x42000000000UL, shifted, shifted, tables)
        )
    }

    var out = ByteArray(0)
    for (block in stageBlocks) {
        out = out.concat(vm64e2b8(0x42000000042UL, block, block, tables))
    }
    return out
}

internal fun final67cc18Sources(window: ByteArray, tables: SourceTables): ByteArray {
    require(window, scratch130Size, "67a990 overlap window")
    val firstSrc = window.copyOfRange(0x40, 0x40 + block66Size)
    val secondSrc = window.copyOfRange(0, block66Size)
    var out = vm67cc18(0x420000054c3UL, firstSrc, firstSrc, tables)
    out = out.concat(vm67cc18(0x420000054c3UL, secondSrc, secondSrc, tables))
    return out
}

internal fun postDF80_67a960Inputs(context: ByteArray, tables: SourceTables): FpPair<ByteArray, ByteArray> {
    require(context, context679f48Size, "679f48 context")
    val state = context.copyOfRange(0x114, 0x1a4)

    val buf3b0 = ByteArray(scratch130Size)
    val buf320 = ByteArray(scratch130Size)
    val buf3e = ByteArray(scratch130Size)
    val bufd = ByteArray(scratch130Size)

    buf3b0[15] = 6.toByte()
    replace(buf3b0, 16, state.copyOfRange(0x5a, 0x6c))
    replace(
        buf3e,
        0,
        vm67cc18(
            0x40012000002ba3UL,
            context.copyOfRange(0x180, 0x180 + 34),
            buf3b0.copyOfRange(0, 34),
            tables
        )
    )

    buf320[15] = 6.toByte()
    replace(buf320, 16, state.copyOfRange(0x36, 0x48))
    replace(
        buf3b0,
        0x20,
        vm67cc18(
            0x4001200000255cUL,
            context.copyOfRange(0x15c, 0x15c + 34),
            buf320.copyOfRange(0, 34),
            tables
        )
    )

    replace(buf3b0, 0, ByteArray(31))
    buf3b0[31] = 7.toByte()
    replace(
        bufd,
        0,
        vm67cc18(
            0x80022000000ca2UL,
            buf3e.copyOfRange(0, 66),
            buf3b0.copyOfRange(0, 66),
            tables
        )
    )

    replace(buf3b0, 0, ByteArray(16))
    replace(buf3b0, 16, state.copyOfRange(0x12, 0x24))
    replace(
        buf3e,
        0,
        vm67cc18(
            0x40012000005f0eUL,
            context.copyOfRange(0x138, 0x138 + 34),
            buf3b0.copyOfRange(0, 34),
            tables
        )
    )

    replace(buf3b0, 0, ByteArray(31))
    buf3b0[31] = 7.toByte()
    replace(buf3b0, 32, state.copyOfRange(0, 0x12))
    replace(
        buf320,
        0x40,
        vm67cc18(
            0x400220000013d9UL,
            buf3e.copyOfRange(0, 50),
            buf3b0.copyOfRange(0, 50),
            tables
        )
    )

    replace(buf320, 0, ByteArray(0x40))
    replace(
        buf3b0,
        0x10,
        vm67cc18(
            0xc00420000016e9UL,
            bufd.copyOfRange(0, 114),
            buf320.copyOfRange(0, 114),
            tables
        )
    )
    replace(buf3b0, 0, ByteArray(15))
    buf3b0[15] = 1.toByte()

    val src1 = context.copyOfRange(0x192, 0x1a4).concat(ByteArray(112))
    return FpPair(src1, buf3b0)
}

internal fun replace(dst: ByteArray, offset: Int, src: ByteArray): Unit {

        for (i in src.indices) {
            dst[offset + i] = src[i]
        }

}

internal fun checkedReplace(dst: ByteArray, offset: Int, src: ByteArray): Unit {

        if (offset < 0 || offset + src.size > dst.size) {
            throw FirstPairSourceSliceError.invalidSlice(offset, src.size)
        }
        replace(dst, offset, src)

}

internal fun seed67aa8cInitialWords(context: ByteArray, tables: SourceTables): Unit {
    for (spec in aa8cInitialReducerSpecs) {
        val window = context.copyOfRange(spec.srcOffset, spec.srcOffset + df80WordSize)
        val reduced = reducer67ea28Word(
            vm67cecc(spec.magic, window, window, tables),
            tables
        )
        replace(context, spec.dstOffset, reduced)
    }
}
