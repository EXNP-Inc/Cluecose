package com.dsikic.cluecose.core.crypto

/** Named-tuple stand-in for Swift 2-tuples returned/accepted by the VM. */
data class FpPair<T, U>(val first: T, val second: U)

/** Named-tuple stand-in for Swift 3-tuples. */
data class FpTriple<T, U, V>(val first: T, val second: U, val third: V)

/** Named-tuple stand-in for Swift 4-tuples. */
data class FpQuad<T, U, V, W>(val first: T, val second: U, val third: V, val fourth: W)

/** `(offset: Int, byteCount: Int)` — table read segments. */
data class TableSegment(val offset: Int, val byteCount: Int)

/** `(magic: UInt64, srcOffset: Int, dstOffset: Int)` — 679f48 / 67aa8c reducer specs. */
data class Spec679f48(val magic: ULong, val srcOffset: Int, val dstOffset: Int)

/** `(offset: Int, tableIndex: Int)` — process2P5 low-copy offsets. */
data class LowCopyOffset(val offset: Int, val tableIndex: Int)

/** 7-tuple `process2P5PublicInitWorkspaceConstants`. */
data class Process2P5PublicConstants(
    val countMul: ULong,
    val countAdd: ULong,
    val productMul: ULong,
    val bPrefixMul: ULong,
    val aPrefixMul: ULong,
    val finalMul: ULong,
    val finalAdd: ULong,
)

/** `LowSeedPhaseSpec` (Swift private struct, 8 fields). */
data class LowSeedPhaseSpec(
    val phaseMagic: ULong,
    val auxMagics: List<ULong>,
    val e10Magics: List<ULong>,
    val e10Markers: List<Int>,
    val bd0Magic: ULong,
    val staticOffset: Int,
    val unaryMagic: ULong,
    val f40Magics: List<ULong>,
)
