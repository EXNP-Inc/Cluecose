// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.
package com.dsikic.cluecose.core.crypto

// 1:1 port of the LibreCRKit FirstPairSourceSlice value structs.
// (Builder5bcf98P256Outputs is the P256 port's data class, alias below.)

/** Shared ByteArray concatenation — Swift `a + b` on `Data`/`[UInt8]`. */
internal fun ByteArray.concat(other: ByteArray): ByteArray {
    val r = ByteArray(size + other.size)
    copyInto(r)
    other.copyInto(r, size)
    return r
}

data class Builder6388f0Next642f60Inputs(
    val x0: ByteArray,
    val x1: ByteArray,
    val x2: ByteArray
)

data class Builder6388f0FirstPair642f60Starts(
    val row0: Builder6388f0Next642f60Inputs,
    val row59: Builder6388f0Next642f60Inputs
)

data class Builder6388f0FirstPairStreamSeeds(
    val nullScalarWindow: ByteArray,
    val staticScalarWindow: ByteArray,
    val nullEntropy11A: ByteArray,
    val nullAttempts: Int,
    val row0Out4: ByteArray,
    val row0Out3: ByteArray,
    val row0Out2: ByteArray,
    val row0Out1: ByteArray,
    val row0Out0: ByteArray,
    val row59Out1: ByteArray,
    val row59Out0: ByteArray
)

data class Builder642f60Result(
    val out0: ByteArray,
    val out1: ByteArray,
    val out2: ByteArray
)

data class Builder6473d0Result(
    val in0After: ByteArray,
    val in1After: ByteArray,
    val in2After: ByteArray,
    val out0: ByteArray,
    val out1: ByteArray,
    val out2: ByteArray,
    val out3: ByteArray,
    val out4: ByteArray
)

data class Builder6473d0OutputPreimages(
    val out4: ByteArray,
    val out3: ByteArray,
    val out2: ByteArray,
    val out1: ByteArray,
    val out0: ByteArray
)

data class Builder6388f0CallerLoopTables(
    val first: ByteArray,
    val second: ByteArray
)

data class Builder6388f0Caller64CallState(
    val arg0: ByteArray,
    val scalar: ULong,
    val x2Workspace: ByteArray,
    val x3Preimage: ByteArray,
    val stackWindow: ByteArray
)

data class Builder6388f0Caller64Call(
    val arg0: ByteArray,
    val scalar: ULong,
    val x2Workspace: ByteArray,
    val x3Preimage: ByteArray,
    val stackWindow: ByteArray,
    val output: ByteArray
)

data class Builder6388f0SeededCaller64Row(
    val index: Int,
    val current642f60: Builder6388f0Next642f60Inputs,
    val preimages: Builder6473d0OutputPreimages,
    val after642f60: Builder642f60Result,
    val after6473d0: Builder6473d0Result,
    val minimalStack20: ByteArray,
    val first64cd40: Builder6388f0Caller64Call,
    val second64cd40: Builder6388f0Caller64Call,
    val third64cd40: Builder6388f0Caller64Call,
    val next642f60: Builder6388f0Next642f60Inputs
)

data class Builder6388f0Seeded63c278Stream(
    val rowIndex: Int,
    val arg0: ByteArray,
    val arg1: ByteArray,
    val arg2: ByteArray,
    val scalar: ULong,
    val scheduleWords: List<UInt>
)

data class Builder6388f0Seeded63c278Schedules(
    val first: Builder6388f0Seeded63c278Stream,
    val second: Builder6388f0Seeded63c278Stream
)

data class Builder6388f0HighSeedStreamStartSeeds(
    val out0: ByteArray,
    val out1: ByteArray
)

data class Builder6388f0FirstPairHighSeedStreamStartSeeds(
    val row0: Builder6388f0HighSeedStreamStartSeeds,
    val row59: Builder6388f0HighSeedStreamStartSeeds
)

data class Builder6388f0LowSeedCF0Seeds(
    val phase1: ByteArray,
    val phase2: ByteArray,
    val phase3: ByteArray
)

data class Builder6388f0LowSeedTailPair(
    val left: ByteArray,
    val right: ByteArray
)

data class Builder6388f0LowSeedLoopResult(
    val final6377f0: ByteArray,
    val scheduleWords: List<UInt>
)

data class Builder6388f0Row0LowSeedPreimages(
    val out4: ByteArray,
    val out3: ByteArray,
    val out2: ByteArray
)

data class Builder633fa8TailBoundary(
    val words3ab0: List<UInt>,
    val words3120: List<UInt>,
    val words2dfc: List<UInt>,
    val seed3110: ULong,
    val preludeSource: ByteArray
)

data class Builder633fa8NullEntrySources(
    val prologueSource: ByteArray,
    val check1SourceWords: List<UInt>,
    val check2SourceWords: List<UInt>
)

data class Builder633fa8NullInitialResult(
    val maskedEntropy: ByteArray,
    val cf0: ByteArray,
    val e10: ByteArray,
    val seedInputs: ByteArray,
    val seedBlocks: ByteArray
)

data class Builder633fa8NullFirstLoopResult(
    val finalTLane: ByteArray,
    val scheduleWords: List<UInt>
)

data class Builder633fa8NullScheduleAcceptance(
    val firstOK: Boolean,
    val secondOK: Boolean
)

data class Builder633fa8NullPostAcceptResult(
    val blocks4080: ByteArray,
    val blocks3f40: ByteArray
)

data class Builder633fa8NullScalarResult(
    val scalarWindow: ByteArray,
    val entropy11A: ByteArray,
    val attempts: Int
)

typealias Builder5bcf98P256Outputs = com.dsikic.cluecose.core.crypto.P256ScalarMultiplier.Builder5bcf98P256Outputs


// -- param containers referenced by the frozen contract (Swift private structs)

/** `BranchAffine63c278Params` (Swift private struct, 19 fields). */
data class BranchAffine63c278Params(
    val arg0Mul: UInt,
    val arg0Add: UInt,
    val halfMul: UInt,
    val bitTable: Int,
    val preMul: UInt,
    val preAdd: UInt,
    val wordMul: UInt,
    val wordAdd: UInt,
    val foldTable: Int,
    val selectTable: Int,
    val argMulTable: Int,
    val argAddTable: Int,
    val carryMul: UInt,
    val valueMul: UInt,
    val nextMul: UInt,
    val loopAdd: UInt,
    val finalAdd: UInt,
    val outMulTable: Int,
    val outAddTable: Int,
)

/** `StageReducerStream63c278` (Swift private struct). */
data class StageReducerStream63c278(val words: List<UInt>, val mulTable: Int)

/** `StageReducer63c278Params` (Swift private struct, 13 fields). */
data class StageReducer63c278Params(
    val carry: UInt,
    val carryMul: UInt,
    val preMul: UInt,
    val preAdd: UInt,
    val foldTable: Int,
    val reduceMul: UInt,
    val sideMul: UInt,
    val reduceAdd: UInt,
    val folded7Mul: UInt,
    val folded8Mul: UInt,
    val nextAdd: UInt,
    val outMulTable: Int,
    val outAddTable: Int,
)

/** `LowSeedPhaseResult` (Swift private struct). */
data class LowSeedPhaseResult(val finalCF0: ByteArray)
