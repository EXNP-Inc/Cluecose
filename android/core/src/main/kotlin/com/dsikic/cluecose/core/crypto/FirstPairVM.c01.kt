// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.SourceTables

internal fun builder6421c0ConvolutionWorkspace(x0Raw: List<ULong>, x0Prefix: List<ULong>, x1Raw: List<ULong>, x1Prefix: List<ULong>): ByteArray {
        if (x0Raw.size != 20 || x0Prefix.size != 20) {
            throw FirstPairSourceSliceError.invalid63c278VectorWordCount(x0Raw.size)
        }
        if (x1Raw.size != builder63c278VectorWords || x1Prefix.size != builder63c278VectorWords) {
            throw FirstPairSourceSliceError.invalid63c278VectorWordCount(x1Raw.size)
        }
        val out = ArrayList<Byte>()
        for (index in 0 until builder64cd40WorkspaceWords) {
            val low = maxOf(0, index - (x1Raw.size - 1))
            val high = minOf(index, x0Raw.size - 1)
            val productSum: ULong
            val x0Sum: ULong
            val x1Sum: ULong
            val count: Int
            if (high >= low) {
                var sum: ULong = 0UL
                for (x0Index in low..high) {
                    sum = sum + x0Raw[x0Index] * x1Raw[index - x0Index]
                }
                productSum = sum
                x0Sum = rangeSumFromPrefix(x0Prefix, low, high)
                x1Sum = rangeSumFromPrefix(x1Prefix, index - high, index - low)
                count = high - low + 1
            } else {
                productSum = 0UL
                x0Sum = 0UL
                x1Sum = 0UL
                count = 0
            }
            val mixed = count.toULong() * 0xdd9e6926c32c9984UL +
                0x7bf33cd7983bce3cUL +
                x0Sum * 0xe703af65ab19ca84UL +
                productSum * 0xe6337be2ad0561b9UL +
                x1Sum * 0x1cd6868a83aeef79UL
            out.addAll(u64LEBytes(
                mixed * 0x2e60fd6d05fe470bUL + 0xf48a714d4ddd3ee7UL
            ).toList())
        }
        return out.toByteArray()
}

internal fun builder6421c0WorkspaceAfterUpdate(workspace: ByteArray, x2Words: List<ULong>, scalar: ULong): ByteArray {
        if (workspace.size < builder64cd40WorkspaceBytes) {
            throw FirstPairSourceSliceError.sourceTooShort("6421c0 workspace", workspace.size, builder64cd40WorkspaceBytes)
        }
        if (x2Words.size != builder63c278VectorWords) {
            throw FirstPairSourceSliceError.invalid63c278VectorWordCount(x2Words.size)
        }
        val tables = FirstPairConfig.tables
        val bytes = workspace
        val words = (0 until builder64cd40WorkspaceWords).map { readUInt64LE(bytes, it * 8) }.toMutableList()

        for (base in 0 until builder63c278VectorWords) {
            val params = builder6421c0WorkspaceParams(scalar, words[base], tables)
            for (offset in x2Words.indices) {
                val pos = base + offset
                // params = (multiplier, broadcast) → first=multiplier, second=broadcast
                words[pos] = words[pos] + params.second + x2Words[offset] * params.first
            }
            words[base + 1] = builder6421c0RewriteSecondWord(
                words[base],
                words[base + 1],
                tables
            )
        }
        return packUInt64LE64cd40(words)
}

internal fun builder6421c0FinalU32Words(workspace: ByteArray): List<UInt> {
        if (workspace.size < builder64cd40WorkspaceBytes) {
            throw FirstPairSourceSliceError.sourceTooShort("6421c0 workspace", workspace.size, builder64cd40WorkspaceBytes)
        }
        val tables = FirstPairConfig.tables
        val bytes = workspace
        val words = (0 until builder64cd40WorkspaceWords).map { readUInt64LE(bytes, it * 8) }
        var carry: ULong = 0x14ee1c03e369d629UL
        val out = ArrayList<UInt>()

        for (index in 0 until builder63c278VectorWords) {
            val tailWord = words[builder63c278VectorWords + index]
            val mixed = carry * 0x0338c0e89dc8ee71UL +
                tailWord * 0x32afeb8e00ff3e85UL +
                0xfc9f014fa6b572f5UL
            val folded7 = fold63c278(
                mixed * 0xea4b89dcd43400c5UL + 0x3ea3d75ac0581688UL,
                builder6421c0FinalFoldTable,
                7,
                tables
            )
            val side = (
                (mixed and 0xffff_ffffUL) * 0x279eaf81UL
                    + (folded7 and 0xffff_ffffUL) * 0x30000000UL
                    + 0xac5f152cUL
                ).toUInt()
            val folded = fold63c278(
                folded7,
                builder6421c0FinalFoldTable,
                9,
                tables
            )
            carry = folded7 * 0x571b49fe43ec4f5dUL +
                folded * 0xc13b0a3000000000UL +
                0x04e301c0d1003cfcUL
            val tableOffset = (index * 4) and 0x1c
            out.add(u32TableAffine63c278(
                side,
                builder6421c0FinalOutMulTable + tableOffset,
                builder6421c0FinalOutAddTable + tableOffset,
                tables
            ))
        }
        return out
}

internal fun builder6421c0OutputWords(x0Source: ByteArray, x1Source: ByteArray, x2Source: ByteArray, scalar: ULong): List<UInt> {
        val x0Streams = builder6421c0X0Streams(x0Source)
        val x1Streams = builder6421c0X1Streams(x1Source)
        val x2Words = builder6421c0X2Words(x2Source)
        val workspace = builder6421c0ConvolutionWorkspace(
            x0Streams.first,
            x0Streams.second,
            x1Streams.first,
            x1Streams.second
        )
        val updated = builder6421c0WorkspaceAfterUpdate(
            workspace,
            x2Words,
            scalar
        )
        return builder6421c0FinalU32Words(updated)
}

internal fun builder6388f0HighSeedX0SourceFrom5bcf98Output(source70: ByteArray): ByteArray {
        if (source70.size < 70) {
            throw FirstPairSourceSliceError.sourceTooShort("6388f0 high x0 source input", source70.size, 70)
        }
        val tables = FirstPairConfig.tables
        val source = source70
        val packedWords = MutableList<UInt>(18) { 0U }
        for (index in 0 until 70) {
            val wordIndex = index / 4
            val shift = ((index * 8) and 0x18).toUInt()
            packedWords[wordIndex] = packedWords[wordIndex] or (((source[index].toInt() and 0xff).toUInt()) shl shift.toInt())
        }

        val out = ArrayList<UInt>()
        for (index in 0 until 20) {
            val bitOffset = index * 28
            val wordIndex = bitOffset ushr 5
            val shift = (bitOffset and 0x1c).toUInt()
            var value = packedWords[wordIndex] shr shift.toInt()
            if (shift != 0U) {
                value = value or (packedWords[wordIndex + 1] shl (32 - shift.toInt()))
            }
            value = value and 0x0fffffffU
            value = value * 0x83dcb233U + 0x774e86a1U
            out.add(u32Affine63c278(
                value,
                index,
                builder6388f0HighSeedX0SourceMulTable,
                builder6388f0HighSeedX0SourceAddTable,
                tables
            ))
        }
        return packUInt32LE(out)
}

internal fun builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(firstOutput70: ByteArray, secondOutput70: ByteArray, x1Source: ByteArray? = null, x2Source: ByteArray? = null, scalar: ULong? = null): Builder6388f0HighSeedStreamStartSeeds {
        val resolvedX1 = x1Source ?: highSeed6421c0X1Source
        val resolvedX2 = x2Source ?: highSeed6421c0X2Source
        val resolvedScalar = scalar ?: highSeed6421c0Scalar
        val out0 = builder6421c0OutputWords(
            builder6388f0HighSeedX0SourceFrom5bcf98Output(firstOutput70),
            resolvedX1,
            resolvedX2,
            resolvedScalar
        )
        val out1 = builder6421c0OutputWords(
            builder6388f0HighSeedX0SourceFrom5bcf98Output(secondOutput70),
            resolvedX1,
            resolvedX2,
            resolvedScalar
        )
        return Builder6388f0HighSeedStreamStartSeeds(
            out0 = packUInt32LE(out0),
            out1 = packUInt32LE(out1)
        )
}

internal fun builder6388f0FirstPairHighSeedStreamStartSeedsFrom5bcf98Outputs(row0FirstOutput70: ByteArray, row0SecondOutput70: ByteArray, row59FirstOutput70: ByteArray, row59SecondOutput70: ByteArray, x1Source: ByteArray? = null, x2Source: ByteArray? = null, scalar: ULong? = null): Builder6388f0FirstPairHighSeedStreamStartSeeds {
        return Builder6388f0FirstPairHighSeedStreamStartSeeds(
            row0 = builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(
                row0FirstOutput70,
                row0SecondOutput70,
                x1Source,
                x2Source,
                scalar
            ),
            row59 = builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(
                row59FirstOutput70,
                row59SecondOutput70,
                x1Source,
                x2Source,
                scalar
            )
        )
}

internal fun builder6388f0FirstPairStreamSeedsFrom5bcf98Outputs(row0Out4: ByteArray, row0Out3: ByteArray, row0Out2: ByteArray, row0FirstOutput70: ByteArray, row0SecondOutput70: ByteArray, row59FirstOutput70: ByteArray, row59SecondOutput70: ByteArray, nullScalarWindow: ByteArray = byteArrayOf(), staticScalarWindow: ByteArray = byteArrayOf(), nullEntropy11A: ByteArray = byteArrayOf(), nullAttempts: Int = 0, x1Source: ByteArray? = null, x2Source: ByteArray? = null, scalar: ULong? = null): Builder6388f0FirstPairStreamSeeds {
        val highSeeds = builder6388f0FirstPairHighSeedStreamStartSeedsFrom5bcf98Outputs(
            row0FirstOutput70,
            row0SecondOutput70,
            row59FirstOutput70,
            row59SecondOutput70,
            x1Source,
            x2Source,
            scalar
        )
        return Builder6388f0FirstPairStreamSeeds(
            nullScalarWindow = nullScalarWindow,
            staticScalarWindow = staticScalarWindow,
            nullEntropy11A = nullEntropy11A,
            nullAttempts = nullAttempts,
            row0Out4 = row0Out4,
            row0Out3 = row0Out3,
            row0Out2 = row0Out2,
            row0Out1 = highSeeds.row0.out1,
            row0Out0 = highSeeds.row0.out0,
            row59Out1 = highSeeds.row59.out1,
            row59Out0 = highSeeds.row59.out0
        )
}

internal fun builder6388f0FirstPairStreamSeedsFromEntrySourceAnd5bcf98Outputs(entrySource: ByteArray, row0FirstOutput70: ByteArray, row0SecondOutput70: ByteArray, row59FirstOutput70: ByteArray, row59SecondOutput70: ByteArray, nullScalarWindow: ByteArray = byteArrayOf(), staticScalarWindow: ByteArray = byteArrayOf(), nullEntropy11A: ByteArray = byteArrayOf(), nullAttempts: Int = 0, x1Source: ByteArray? = null, x2Source: ByteArray? = null, scalar: ULong? = null): Builder6388f0FirstPairStreamSeeds {
        val low = builder6388f0Row0LowSeedPreimagesFromEntrySource(entrySource)
        val resolvedStaticScalarWindow = if (staticScalarWindow.isEmpty())
            builder633fa8StaticScalarWindowFromEntrySource(entrySource)
        else
            staticScalarWindow
        return builder6388f0FirstPairStreamSeedsFrom5bcf98Outputs(
            low.out4,
            low.out3,
            low.out2,
            row0FirstOutput70,
            row0SecondOutput70,
            row59FirstOutput70,
            row59SecondOutput70,
            nullScalarWindow,
            resolvedStaticScalarWindow,
            nullEntropy11A,
            nullAttempts,
            x1Source,
            x2Source,
            scalar
        )
}

internal fun builder6388f0FirstPairStreamSeedsFromEntropyAnd5bcf98Outputs(entrySource: ByteArray, row0FirstOutput70: ByteArray, row0SecondOutput70: ByteArray, row59FirstOutput70: ByteArray, row59SecondOutput70: ByteArray, nullEntropy11A: ByteArray, x1Source: ByteArray? = null, x2Source: ByteArray? = null, scalar: ULong? = null): Builder6388f0FirstPairStreamSeeds {
        val nullScalar = builder633fa8NullScalarWindowFromEntropy(nullEntropy11A)
        return builder6388f0FirstPairStreamSeedsFromEntrySourceAnd5bcf98Outputs(
            entrySource,
            row0FirstOutput70,
            row0SecondOutput70,
            row59FirstOutput70,
            row59SecondOutput70,
            nullScalar,
            staticScalarWindow = byteArrayOf(),
            nullEntropy11A = nullEntropy11A,
            nullAttempts = 1,
            x1Source = x1Source,
            x2Source = x2Source,
            scalar = scalar
        )
}

internal fun builder6388f0FirstPairStreamSeedsFromEntropySourceAnd5bcf98Outputs(entrySource: ByteArray, row0FirstOutput70: ByteArray, row0SecondOutput70: ByteArray, row59FirstOutput70: ByteArray, row59SecondOutput70: ByteArray, maxAttempts: Int = 64, x1Source: ByteArray? = null, x2Source: ByteArray? = null, scalar: ULong? = null, entropySource: (Int) -> ByteArray): Builder6388f0FirstPairStreamSeeds {
        val nullResult = builder633fa8NullScalarWindowFromEntropySource(maxAttempts, entropySource)
        return builder6388f0FirstPairStreamSeedsFromEntrySourceAnd5bcf98Outputs(
            entrySource,
            row0FirstOutput70,
            row0SecondOutput70,
            row59FirstOutput70,
            row59SecondOutput70,
            nullResult.scalarWindow,
            staticScalarWindow = byteArrayOf(),
            nullEntropy11A = nullResult.entropy11A,
            nullAttempts = nullResult.attempts,
            x1Source = x1Source,
            x2Source = x2Source,
            scalar = scalar
        )
}

internal fun builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints(entrySource: ByteArray, nullScalarWindow: ByteArray, staticScalarWindow: ByteArray, row0SensorPointXYBE: ByteArray, row59SensorPointXYBE: ByteArray, nullEntropy11A: ByteArray = byteArrayOf(), nullAttempts: Int = 0, x1Source: ByteArray? = null, x2Source: ByteArray? = null, scalar: ULong? = null): Builder6388f0FirstPairStreamSeeds {
        val low = builder6388f0Row0LowSeedPreimagesFromEntrySource(entrySource)
        // Inline the P256 bridge `builder6388f0HighSeedStreamStartSeedsFromScalarP256`
        // (a P256ScalarMultiplier.swift extension not ported as a top-level function):
        //   outputs = builder5bcf98P256Outputs(scalarWindowLE, sensorPointXYBE)
        //   return builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(
        //       outputs.xOutput70, outputs.yOutput70, x1Source, x2Source, scalar)
        val row0Outputs = P256ScalarMultiplier.builder5bcf98P256Outputs(nullScalarWindow, row0SensorPointXYBE)
        val row0High = builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(
            row0Outputs.xOutput70,
            row0Outputs.yOutput70,
            x1Source,
            x2Source,
            scalar
        )
        val row59Outputs = P256ScalarMultiplier.builder5bcf98P256Outputs(staticScalarWindow, row59SensorPointXYBE)
        val row59High = builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(
            row59Outputs.xOutput70,
            row59Outputs.yOutput70,
            x1Source,
            x2Source,
            scalar
        )
        return Builder6388f0FirstPairStreamSeeds(
            nullScalarWindow = nullScalarWindow,
            staticScalarWindow = staticScalarWindow,
            nullEntropy11A = nullEntropy11A,
            nullAttempts = nullAttempts,
            row0Out4 = low.out4,
            row0Out3 = low.out3,
            row0Out2 = low.out2,
            row0Out1 = row0High.out1,
            row0Out0 = row0High.out0,
            row59Out1 = row59High.out1,
            row59Out0 = row59High.out0
        )
}

internal fun builder6388f0FirstPairStreamSeedsFromEntropyAndSensorPoints(entrySource: ByteArray, nullEntropy11A: ByteArray, row0SensorPointXYBE: ByteArray, row59SensorPointXYBE: ByteArray, x1Source: ByteArray? = null, x2Source: ByteArray? = null, scalar: ULong? = null): Builder6388f0FirstPairStreamSeeds {
        val nullScalar = builder633fa8NullScalarWindowFromEntropy(nullEntropy11A)
        val staticScalar = builder633fa8StaticScalarWindowFromEntrySource(entrySource)
        return builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints(
            entrySource,
            nullScalar,
            staticScalar,
            row0SensorPointXYBE,
            row59SensorPointXYBE,
            nullEntropy11A,
            1,
            x1Source,
            x2Source,
            scalar
        )
}

internal fun builder6388f0FirstPairStreamSeedsFromEntropySourceAndSensorPoints(entrySource: ByteArray, row0SensorPointXYBE: ByteArray, row59SensorPointXYBE: ByteArray, maxAttempts: Int = 64, x1Source: ByteArray? = null, x2Source: ByteArray? = null, scalar: ULong? = null, entropySource: (Int) -> ByteArray): Builder6388f0FirstPairStreamSeeds {
        val nullResult = builder633fa8NullScalarWindowFromEntropySource(maxAttempts, entropySource)
        val staticScalar = builder633fa8StaticScalarWindowFromEntrySource(entrySource)
        return builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints(
            entrySource,
            nullResult.scalarWindow,
            staticScalar,
            row0SensorPointXYBE,
            row59SensorPointXYBE,
            nullResult.entropy11A,
            nullResult.attempts,
            x1Source,
            x2Source,
            scalar
        )
}

internal fun builder6388f0LowSeedCF0SeedsFromEntrySource(entrySource: ByteArray): Builder6388f0LowSeedCF0Seeds {
        if (entrySource.size < builder6388f0LowSeedEntrySourceBytes) {
            throw FirstPairSourceSliceError.sourceTooShort(
                "6388f0 low-seed entry source",
                entrySource.size,
                builder6388f0LowSeedEntrySourceBytes
            )
        }

        val tables = FirstPairConfig.tables
        val source = entrySource
        val entryHead = source.copyOfRange(0, builder6388f0LowSeedBlockBytes)
        val entryTail = source.copyOfRange(builder6388f0LowSeedBlockBytes, builder6388f0LowSeedEntrySourceBytes)

        val pre2S898 = vm638840(
            builder6388f0LowSeedEntryS898Magic,
            entryHead,
            entryHead,
            tables
        )
        val pre2S78E = vm638840(
            builder6388f0LowSeedEntryS78EMagic,
            entryTail,
            entryTail,
            tables
        )
        val phase1SeedCF0 = vm641fcc(
            builder6388f0LowSeedCF0Phase1SeedMagic,
            pre2S78E,
            tables
        )
        val phase1 = builder6388f0LowSeedPhaseFromCF0Seed(
            builder6388f0LowSeedPhase1Spec,
            phase1SeedCF0,
            tables
        )

        val pre2Static = builder6388f0LowSeedStaticBlock(
            builder6388f0LowSeedPrev2Static,
            tables
        )
        val pre2BD0 = vm638840(
            builder6388f0LowSeedPrev2BD0Magic,
            phase1.finalCF0,
            pre2Static,
            tables
        )
        val pre2S684 = vm638840(
            builder6388f0LowSeedPrev2S684Magic,
            pre2BD0,
            pre2BD0,
            tables
        )
        val pre2S57A = vm638840(
            builder6388f0LowSeedMiddleMagic,
            pre2S898,
            pre2S684,
            tables
        )
        val preS898 = vm638840(
            builder6388f0LowSeedTailLeftMagic,
            pre2S78E,
            pre2S78E,
            tables
        )
        val preS78E = vm638840(
            builder6388f0LowSeedTailRightMagic,
            pre2S57A,
            pre2S57A,
            tables
        )
        val phase2SeedCF0 = vm641fcc(
            builder6388f0LowSeedCF0Phase2SeedMagic,
            preS78E,
            tables
        )
        val phase2 = builder6388f0LowSeedPhaseFromCF0Seed(
            builder6388f0LowSeedPhase2Spec,
            phase2SeedCF0,
            tables
        )

        val preStatic = builder6388f0LowSeedStaticBlock(
            builder6388f0LowSeedPreStatic,
            tables
        )
        val preBD0 = vm638840(
            builder6388f0LowSeedPreBD0Magic,
            phase2.finalCF0,
            preStatic,
            tables
        )
        val prevS684 = vm638840(
            builder6388f0LowSeedPrevS684Magic,
            preBD0,
            preBD0,
            tables
        )
        val prevS57A = vm638840(
            builder6388f0LowSeedMiddleMagic,
            preS898,
            prevS684,
            tables
        )
        val seedS78E = vm638840(
            builder6388f0LowSeedTailRightMagic,
            prevS57A,
            prevS57A,
            tables
        )
        val phase3SeedCF0 = vm641fcc(
            builder6388f0LowSeedCF0Phase3SeedMagic,
            seedS78E,
            tables
        )

        return Builder6388f0LowSeedCF0Seeds(
            phase1 = phase1SeedCF0,
            phase2 = phase2SeedCF0,
            phase3 = phase3SeedCF0
        )
}

internal fun builder6388f0LowSeedTailPairFromEntrySource(entrySource: ByteArray): Builder6388f0LowSeedTailPair {
        val tables = FirstPairConfig.tables
        val seeds = builder6388f0LowSeedCF0SeedsFromEntrySource(entrySource)
        val phase1 = builder6388f0LowSeedPhaseFromCF0Seed(
            builder6388f0LowSeedPhase1Spec,
            seeds.phase1,
            tables
        )
        val phase2 = builder6388f0LowSeedPhaseFromCF0Seed(
            builder6388f0LowSeedPhase2Spec,
            seeds.phase2,
            tables
        )
        val phase3 = builder6388f0LowSeedPhaseFromCF0Seed(
            builder6388f0LowSeedPhase3Spec,
            seeds.phase3,
            tables
        )
        return builder6388f0LowSeedTailPairFromEntryAndCF0(
            entrySource,
            phase1.finalCF0,
            phase2.finalCF0,
            phase3.finalCF0,
            tables
        )
}

internal fun builder6388f0LowSeedTailStageFromPair(pair: Builder6388f0LowSeedTailPair): ByteArray {
        val tables = FirstPairConfig.tables
        require(pair.left, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail left")
        require(pair.right, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail right")
        return vm638840(
            builder6388f0LowSeedTailStageMagic,
            pair.left,
            pair.right,
            tables
        )
}

internal fun builder6388f0LowSeedPreludeSourceFromTailStage(tailStage: ByteArray): ByteArray {
        val tables = FirstPairConfig.tables
        val stageInput = tailStage
        require(stageInput, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail stage")
        val stage = vm638840(
            builder6388f0LowSeedPreludeStageMagic,
            stageInput,
            stageInput,
            tables
        )
        return vm638840(
            builder6388f0LowSeedPreludeSourceMagic,
            stage,
            stage,
            tables
        )
}

internal fun builder6388f0LowSeedBlocksFromPreludeSource(preludeSource: ByteArray): ByteArray {
        val tables = FirstPairConfig.tables
        val prelude = preludeSource
        require(prelude, builder6388f0LowSeedBlockBytes, "6388f0 low-seed prelude source")
        val expanded = vm6420d8(
            builder6388f0LowSeedPreludeMagic,
            prelude,
            prelude,
            tables
        )
        if (expanded.size != builder6388f0LowSeedExpandedPreludeBytes) {
            throw FirstPairSourceSliceError.invalidLowSeedExpandedPreludeLength(expanded.size)
        }

        val out = ArrayList<Byte>()
        for (index in 0 until 19) {
            val start = index * 0x0e
            val block = expanded.copyOfRange(start, start + 0x10)
            out.addAll(vm638840(
                builder6388f0Row0SeedBlockMagic,
                block,
                block,
                tables
            ).toList())
        }
        val staticBlock = builder6388f0LowLoopStaticBlock(
            builder6388f0LowSeedStaticBlockOffset,
            0x10,
            tables
        )
        out.addAll(vm638840(
            builder6388f0Row0SeedBlockMagic,
            staticBlock,
            staticBlock,
            tables
        ).toList())
        return out.toByteArray()
}

internal fun builder6388f0LowSeedLoopFromBlocks(seedBlocks: ByteArray): Builder6388f0LowSeedLoopResult {
        if (seedBlocks.size != builder6388f0LowSeedSeedBlocksBytes) {
            throw FirstPairSourceSliceError.invalidLowSeedSeedBlocksLength(seedBlocks.size)
        }
        val tables = FirstPairConfig.tables
        val blocks = seedBlocks
        val scheduleWords = ArrayList<UInt>()
        var final6377f0: ByteArray = ByteArray(0)

        for (outerIndex in 0 until 20) {
            val lane = outerIndex and 7
            var cLane = builder6388f0LowLoopStaticBlock(
                builder6388f0LowLoopStaticCTable,
                0x10,
                tables
            )
            cLane = cLane.concat(byteArrayOf(0x05.toByte(), 0x04.toByte()))

            val eSource = builder6388f0LowLoopStaticBlock(
                builder6388f0LowLoopStaticETable + builder6388f0LowLoopLaneBytes * lane,
                builder6388f0LowLoopLaneBytes,
                tables
            )
            var eLane = vm638840(
                builder6388f0LowLoopEInitMagic,
                eSource,
                eSource,
                tables
            )
            val blockOffset = outerIndex * 0x10
            val block = blocks.copyOfRange(blockOffset, blockOffset + 0x10)
            val dLane = vm6420d8(
                builder6388f0LowLoopDInitMagic,
                block,
                block,
                tables
            )
            var bLane = vm638840(
                builder6388f0LowLoopBInitMagic,
                eLane,
                eLane,
                tables
            )

            var aLane = ByteArray(builder6388f0LowLoopLaneBytes)
            var tLane = ByteArray(builder6388f0LowLoopLaneBytes)
            for (iter in 0 until 28) {
                val fLane = vm638840(
                    builder6388f0LowLoopFMagic,
                    dLane,
                    cLane,
                    tables
                )
                val aSource = builder6388f0LowLoopStaticBlock(
                    builder6388f0LowLoopStaticATable,
                    builder6388f0LowLoopLaneBytes,
                    tables
                )
                aLane = vm638840(
                    builder6388f0LowLoopAMagic,
                    aSource,
                    fLane,
                    tables
                )
                tLane = vm638840(
                    builder6388f0LowLoopTMagic,
                    eLane,
                    aLane,
                    tables
                )
                bLane = vm638840(
                    builder6388f0LowLoopBMixMagic,
                    bLane,
                    tLane,
                    tables
                )
                eLane = vm638840(
                    builder6388f0LowLoopEAdvanceMagic,
                    eLane,
                    eLane,
                    tables
                )
                cLane = vm638840(
                    builder6388f0LowLoopCAdvanceMagic,
                    cLane,
                    cLane,
                    tables
                )
            }

            final6377f0 = tLane
            val fLane = vm638840(
                builder6388f0LowLoopPostFMagic,
                bLane,
                eLane,
                tables
            )
            val dSource = builder6388f0LowLoopStaticBlock(
                builder6388f0LowLoopStaticDTable + builder6388f0LowLoopLaneBytes * lane,
                builder6388f0LowLoopLaneBytes,
                tables
            )
            val postDLane = vm638840(
                builder6388f0LowLoopPostDMagic,
                fLane,
                dSource,
                tables
            )
            var packELane = vm641fcc(
                builder6388f0LowLoopPostEMagic,
                postDLane,
                tables
            )

            var packedLane = aLane
            packedLane[0] = 0
            packedLane[1] = 0
            packedLane[2] = 0
            packedLane[3] = 0
            var shift = 32
            for (packIndex in 0 until 8) {
                val cWord = vm638840(
                    builder6388f0LowLoopPackCMagic,
                    packELane,
                    packELane,
                    tables
                )
                if (shift >= 5) {
                    packELane = vm6420d8(
                        builder6388f0LowLoopPackEMagic,
                        packELane,
                        packELane,
                        tables
                    )
                }
                val bWord = vm638840(
                    builder6388f0LowLoopPackBMagic,
                    cWord,
                    cWord,
                    tables
                )
                val selected = bWord[2].toInt() xor (bWord[3].toInt() shl 3)
                val packed = builder6388f0LowLoopStaticByte(
                    builder6388f0LowLoopNibbleTable + selected,
                    tables
                )
                var nibble: Int = if ((packIndex and 1) == 0) ((packed.toInt()) and 0x0f) else ((packed.toInt()) ushr 4)
                if (shift < 4) {
                    val mask = if (shift == 0) 0 else ((1 shl shift) - 1)
                    nibble = nibble and mask
                }

                val byteIndex = packIndex ushr 1
                if ((packIndex and 1) == 0) {
                    packedLane[byteIndex] = nibble.toByte()
                } else {
                    packedLane[byteIndex] = (packedLane[byteIndex].toInt() xor (nibble shl 4)).toByte()
                }
                shift = maxOf(shift - 4, 0)
            }
            scheduleWords.add(readUInt32LE(packedLane, 0))
        }

        return Builder6388f0LowSeedLoopResult(
            final6377f0 = final6377f0,
            scheduleWords = scheduleWords
        )
}

internal fun builder6388f0Row0LowSeedPreimagesFromEntrySource(entrySource: ByteArray): Builder6388f0Row0LowSeedPreimages {
        val tables = FirstPairConfig.tables
        val seeds = builder6388f0LowSeedCF0SeedsFromEntrySource(entrySource)
        val phase3 = builder6388f0LowSeedPhaseFromCF0Seed(
            builder6388f0LowSeedPhase3Spec,
            seeds.phase3,
            tables
        )
        val tailStatic = builder6388f0LowSeedStaticBlock(
            builder6388f0LowSeedTailStatic,
            tables
        )
        val tailBD0 = vm638840(
            builder6388f0LowSeedTailBD0Magic,
            phase3.finalCF0,
            tailStatic,
            tables
        )
        val tailS684 = vm638840(
            builder6388f0LowSeedTailS684Magic,
            tailBD0,
            tailBD0,
            tables
        )
        val pair = builder6388f0LowSeedTailPairFromEntrySource(entrySource)
        val tailStage = builder6388f0LowSeedTailStageFromPair(pair)
        val preludeSource = builder6388f0LowSeedPreludeSourceFromTailStage(tailStage)
        val seedBlocks = builder6388f0LowSeedBlocksFromPreludeSource(preludeSource)
        val loop = builder6388f0LowSeedLoopFromBlocks(seedBlocks)

        val baseOut3 = tailS684.copyOfRange(204, 266).concat(pair.right.copyOfRange(0, 26))
        val out3 = baseOut3.copyOfRange(0, 62).concat(loop.final6377f0).concat(baseOut3.copyOfRange(baseOut3.size - 8, baseOut3.size))
        return Builder6388f0Row0LowSeedPreimages(
            out4 = tailS684.copyOfRange(116, 204),
            out3 = out3,
            out2 = pair.right.copyOfRange(26, 114)
        )
}

internal fun builder633fa8StaticPreludeSourceFromEntrySource(entrySource: ByteArray): ByteArray {
        val pair = builder6388f0LowSeedTailPairFromEntrySource(entrySource)
        val tailStage = builder6388f0LowSeedTailStageFromPair(pair)
        return builder6388f0LowSeedPreludeSourceFromTailStage(tailStage)
}

internal fun builder633fa8StaticTailBoundaryFromEntrySource(entrySource: ByteArray): Builder633fa8TailBoundary {
        val preludeSource = builder633fa8StaticPreludeSourceFromEntrySource(entrySource)
        return builder633fa8TailBoundaryFromPreludeSource(preludeSource)
}

internal fun builder633fa8TailBoundaryFromPreludeSource(preludeSource: ByteArray): Builder633fa8TailBoundary {
        val seedBlocks = builder6388f0LowSeedBlocksFromPreludeSource(preludeSource)
        val loop = builder6388f0LowSeedLoopFromBlocks(seedBlocks)
        return Builder633fa8TailBoundary(
            words3ab0 = loop.scheduleWords,
            words3120 = builder633fa8InvariantWords3120,
            words2dfc = builder633fa8InvariantWords2dfc,
            seed3110 = builder633fa8InvariantSeed3110,
            preludeSource = preludeSource
        )
}

internal fun builder633fa8ScalarWindowFromPreludeSource(preludeSource: ByteArray): ByteArray {
        val boundary = builder633fa8TailBoundaryFromPreludeSource(preludeSource)
        val qwords = builder633fa8TailQwordsFromSources(
            boundary.words3ab0,
            boundary.words3120,
            boundary.words2dfc,
            boundary.seed3110
        )
        val e10Words = builder633fa8E10WordsFromTailQwords(qwords)
        return builder633fa8ScalarWindowFromE10Words(e10Words)
}

internal fun builder633fa8StaticScalarWindowFromEntrySource(entrySource: ByteArray): ByteArray {
        val preludeSource = builder633fa8StaticPreludeSourceFromEntrySource(entrySource)
        return builder633fa8ScalarWindowFromPreludeSource(preludeSource)
}

internal fun builder633fa8NullEntrySourcesFromInvariantEntry(): Builder633fa8NullEntrySources {
        val pc = unpack3BitStream5bdd14(
            builder633fa8NullEntryBitsChecksSource,
            0,
            builder633fa8NullEntropyBytes
        )
        val prologue = pc.first
        val cursor = pc.second
        val checkBytes = builder633fa8ScalarWordCount * 4
        if (cursor + 2 * checkBytes > builder633fa8NullEntryBitsChecksSource.size) {
            throw FirstPairSourceSliceError.sourceTooShort(
                "633fa8 null entry checks",
                builder633fa8NullEntryBitsChecksSource.size,
                cursor + 2 * checkBytes
            )
        }
        val check1 = (0 until builder633fa8ScalarWordCount).map {
            readUInt32LE(builder633fa8NullEntryBitsChecksSource, cursor + it * 4)
        }
        val check2 = (0 until builder633fa8ScalarWordCount).map {
            readUInt32LE(builder633fa8NullEntryBitsChecksSource, cursor + checkBytes + it * 4)
        }
        return Builder633fa8NullEntrySources(
            prologueSource = prologue,
            check1SourceWords = check1,
            check2SourceWords = check2
        )
}

internal fun builder633fa8NullInitialFromEntropy(entropy11A: ByteArray, prologueSource: ByteArray): Builder633fa8NullInitialResult {
        if (entropy11A.size != builder633fa8NullEntropyBytes) {
            throw FirstPairSourceSliceError.invalid633fa8NullEntropyLength(entropy11A.size)
        }
        if (prologueSource.size < builder633fa8NullEntropyBytes) {
            throw FirstPairSourceSliceError.sourceTooShort(
                "633fa8 null prologue source",
                prologueSource.size,
                builder633fa8NullEntropyBytes
            )
        }
        val tables = FirstPairConfig.tables
        val maskedEntropy = entropy11A.map { (it.toInt() and 7).toByte() }.toByteArray()
        val prologue = prologueSource.copyOfRange(0, builder633fa8NullEntropyBytes)
        val cf0 = vm638840(
            builder633fa8NullInitialAMagic,
            maskedEntropy,
            prologue,
            tables
        )
        val e10 = vm638840(
            builder633fa8NullInitialBMagic,
            cf0,
            cf0,
            tables
        )
        if (e10.size < builder633fa8NullEntropyBytes) {
            throw FirstPairSourceSliceError.sourceTooShort(
                "633fa8 null initial e10",
                e10.size,
                builder633fa8NullEntropyBytes
            )
        }

        val lastSeedInputEnd = (builder633fa8ScalarWordCount - 1) * builder633fa8NullSeedBlockStride +
            builder633fa8NullSeedBlockBytes
        if (e10.size < lastSeedInputEnd) {
            throw FirstPairSourceSliceError.sourceTooShort(
                "633fa8 null initial seed input source",
                e10.size,
                lastSeedInputEnd
            )
        }

        val seedInputs = ArrayList<Byte>()
        for (index in 0 until builder633fa8ScalarWordCount) {
            val start = index * builder633fa8NullSeedBlockStride
            seedInputs.addAll(e10.copyOfRange(start, start + builder633fa8NullSeedBlockBytes).toList())
        }

        val seedBlocks = ArrayList<Byte>()
        for (index in 0 until builder633fa8ScalarWordCount) {
            val start = index * builder633fa8NullSeedBlockBytes
            val block = seedInputs.toByteArray().copyOfRange(start, start + builder633fa8NullSeedBlockBytes)
            seedBlocks.addAll(vm638840(
                builder633fa8NullSeedBlockMagic,
                block,
                block,
                tables
            ).toList())
        }

        return Builder633fa8NullInitialResult(
            maskedEntropy = maskedEntropy,
            cf0 = cf0,
            e10 = e10,
            seedInputs = seedInputs.toByteArray(),
            seedBlocks = seedBlocks.toByteArray()
        )
}

internal fun builder633fa8NullFirstLoopFromBlocks(seedBlocks: ByteArray): Builder633fa8NullFirstLoopResult {
        if (seedBlocks.size != builder633fa8NullSeedBlocksBytes) {
            throw FirstPairSourceSliceError.invalid633fa8NullSeedBlocksLength(seedBlocks.size)
        }
        val tables = FirstPairConfig.tables
        val blocks = seedBlocks
        val scheduleWords = ArrayList<UInt>()
        var finalTLane: ByteArray = ByteArray(0)

        for (outerIndex in 0 until builder633fa8ScalarWordCount) {
            val lane = outerIndex and 7
            var cLane = builder633fa8NullTableBlock(
                builder633fa8NullLoopStaticCTable,
                0x10,
                tables
            )
            cLane = cLane.concat(byteArrayOf(0x05.toByte(), 0x05.toByte()))

            val eSource = builder633fa8NullTableBlock(
                builder633fa8NullLoopStaticETable + builder633fa8NullLoopLaneBytes * lane,
                builder633fa8NullLoopLaneBytes,
                tables
            )
            var eLane = vm638840(
                builder633fa8NullLoopEInitMagic,
                eSource,
                eSource,
                tables
            )
            val blockOffset = outerIndex * builder633fa8NullSeedBlockBytes
            val block = blocks.copyOfRange(blockOffset, blockOffset + builder633fa8NullSeedBlockBytes)
            val dLane = vm6420d8(
                builder633fa8NullLoopDInitMagic,
                block,
                block,
                tables
            )
            var bLane = vm638840(
                builder633fa8NullLoopBInitMagic,
                eLane,
                eLane,
                tables
            )

            var aLane = ByteArray(builder633fa8NullLoopLaneBytes)
            var tLane = ByteArray(builder633fa8NullLoopLaneBytes)
            for (iter in 0 until 28) {
                val fLane = vm638840(
                    builder633fa8NullLoopFMagic,
                    dLane,
                    cLane,
                    tables
                )
                val aSource = builder633fa8NullTableBlock(
                    builder633fa8NullLoopStaticATable,
                    builder633fa8NullLoopLaneBytes,
                    tables
                )
                aLane = vm638840(
                    builder633fa8NullLoopAMagic,
                    aSource,
                    fLane,
                    tables
                )
                tLane = vm638840(
                    builder633fa8NullLoopTMagic,
                    eLane,
                    aLane,
                    tables
                )
                bLane = vm638840(
                    builder633fa8NullLoopBMixMagic,
                    bLane,
                    tLane,
                    tables
                )
                eLane = vm638840(
                    builder633fa8NullLoopEAdvanceMagic,
                    eLane,
                    eLane,
                    tables
                )
                cLane = vm638840(
                    builder633fa8NullLoopCAdvanceMagic,
                    cLane,
                    cLane,
                    tables
                )
            }

            finalTLane = tLane
            val fLane = vm638840(
                builder633fa8NullLoopPostFMagic,
                bLane,
                eLane,
                tables
            )
            val dSource = builder633fa8NullTableBlock(
                builder633fa8NullLoopStaticDTable + builder633fa8NullLoopLaneBytes * lane,
                builder633fa8NullLoopLaneBytes,
                tables
            )
            val postDLane = vm638840(
                builder633fa8NullLoopPostDMagic,
                fLane,
                dSource,
                tables
            )
            var packELane = vm641fcc(
                builder633fa8NullLoopPostEMagic,
                postDLane,
                tables
            )

            var packedLane = aLane
            packedLane[0] = 0
            packedLane[1] = 0
            packedLane[2] = 0
            packedLane[3] = 0
            var shift = 32
            for (packIndex in 0 until 8) {
                val cWord = vm638840(
                    builder633fa8NullLoopPackCMagic,
                    packELane,
                    packELane,
                    tables
                )
                if (shift >= 5) {
                    packELane = vm6420d8(
                        builder633fa8NullLoopPackEMagic,
                        packELane,
                        packELane,
                        tables
                    )
                }
                val bWord = vm638840(
                    builder633fa8NullLoopPackBMagic,
                    cWord,
                    cWord,
                    tables
                )
                val selected = bWord[2].toInt() xor (bWord[3].toInt() shl 3)
                val packed = builder633fa8NullNibbleByte(
                    builder633fa8NullLoopNibbleTable + selected,
                    tables
                )
                var nibble: Int = if ((packIndex and 1) == 0) ((packed.toInt()) and 0x0f) else ((packed.toInt()) ushr 4)
                if (shift < 4) {
                    val mask = if (shift == 0) 0 else ((1 shl shift) - 1)
                    nibble = nibble and mask
                }

                val byteIndex = packIndex ushr 1
                if ((packIndex and 1) == 0) {
                    packedLane[byteIndex] = nibble.toByte()
                } else {
                    packedLane[byteIndex] = (packedLane[byteIndex].toInt() xor (nibble shl 4)).toByte()
                }
                shift = maxOf(shift - 4, 0)
            }

            scheduleWords.add(readUInt32LE(packedLane, 0))
        }

        return Builder633fa8NullFirstLoopResult(
            finalTLane = finalTLane,
            scheduleWords = scheduleWords
        )
}

internal fun builder633fa8NullScheduleAcceptance(scheduleWords: List<UInt>, check1SourceWords: List<UInt>, check2SourceWords: List<UInt>): Builder633fa8NullScheduleAcceptance {
        val tables = FirstPairConfig.tables
        val firstOK = builder633fa8NullScheduleCheck(
            scheduleWords,
            check1SourceWords,
            builder633fa8NullCheck1ScheduleMulTable,
            builder633fa8NullCheck1SourceMulTable,
            builder633fa8NullCheck1AddTable,
            builder633fa8NullCheck1FoldTable,
            builder633fa8NullCheck1Target,
            builder633fa8NullCheck1FoldTarget,
            tables
        )
        val secondOK = builder633fa8NullScheduleCheck(
            scheduleWords,
            check2SourceWords,
            builder633fa8NullCheck2ScheduleMulTable,
            builder633fa8NullCheck2SourceMulTable,
            builder633fa8NullCheck2AddTable,
            builder633fa8NullCheck2FoldTable,
            builder633fa8NullCheck2Target,
            builder633fa8NullCheck2FoldTarget,
            tables
        )
        return Builder633fa8NullScheduleAcceptance(firstOK = firstOK, secondOK = secondOK)
}
