// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.RuntimeTable
import com.dsikic.cluecose.core.tables.SourceTables

internal fun builder642f60FirstAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x7a6bdb55U + 0x6f457678U
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60FirstAMulTable,
            addTable = builder642f60FirstAAddTable,
            tables = tables
        ) * 0x9935dc8fU + 0x8faec549U
    }
    val folded = fold63c278(
        w.toULong() * 0x62170eaa882a1aadUL + 0xfcded5c74336bb62UL,
        tableOffset = builder642f60FirstAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0x1ae027ac75efae5dUL +
        folded * 0x6a59778f00000000UL +
        0x272a0fcbb9692010UL
}

internal fun builder642f60FirstBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x8a0c43a1U + 0xe1069988U
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60FirstBMulTable,
            addTable = builder642f60FirstBAddTable,
            tables = tables
        ) * 0x8fce17f9U + 0x9aa95d9cU
    }
    val folded = fold63c278(
        w.toULong() * 0x91c0e3def121255dUL + 0x50be110705349aeaUL,
        tableOffset = builder642f60FirstBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0x861960b1d03ace7fUL +
        folded * 0x7f5bb67500000000UL +
        0x4a2faf413913b4a2UL
}

internal fun builder642f60SecondAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0xcce32bdbU + 0x79dae932U
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60SecondAMulTable,
            addTable = builder642f60SecondAAddTable,
            tables = tables
        ) * 0x7bd77a89U + 0x0d07fa2aU
    }
    val folded = fold63c278(
        w.toULong() * 0x0a0b1df06a7b196dUL + 0xfd9f62e38b4829f7UL,
        tableOffset = builder642f60SecondAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0xb1bf8eba2d4b2a69UL +
        folded * 0xb6b0ac9300000000UL +
        0x381faa6c090fdcd8UL
}

internal fun builder642f60SecondBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x9affbc41U + 0x96a579e0U
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60SecondBMulTable,
            addTable = builder642f60SecondBAddTable,
            tables = tables
        ) * 0x3749a60dU + 0xb803d34cU
    }
    val folded = fold63c278(
        w.toULong() * 0xa5ffca145f08d59bUL + 0x8ce0b7edd5a16ba2UL,
        tableOffset = builder642f60SecondBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0xabe6b5e1333dcc8fUL +
        folded * 0xfbd091e300000000UL +
        0x1df38d76faeb4eadUL
}

internal fun builder642f60ThirdAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x92a36947U + 0xab2632fcU
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60ThirdAMulTable,
            addTable = builder642f60ThirdAAddTable,
            tables = tables
        ) * 0xe77cb783U + 0x000f1f23U
    }
    val folded = fold63c278FirstNibbleBeforeAdd(
        w.toULong() * 0xaf7f459e89cfb7e5UL,
        addend = 0x5c6139b5f80c5a20UL,
        tableOffset = builder642f60ThirdAFoldTable,
        tables = tables
    )
    return w.toULong() * 0x796b8710218eb0c5UL +
        folded * 0x7224389f00000000UL +
        0x086af43c0726c3a9UL
}

internal fun builder642f60ThirdBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x032a79c5U + 0x8da26e96U
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60ThirdBMulTable,
            addTable = builder642f60ThirdBAddTable,
            tables = tables
        ) * 0xb13f4189U + 0x2b7b0e41U
    }
    val folded = fold63c278(
        w.toULong() * 0x3144f0ff41a1df83UL + 0x3d414cbf18310011UL,
        tableOffset = builder642f60ThirdBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0x861a1f875b2dc69bUL +
        folded * 0xf0b786f700000000UL +
        0x63b83ae085557472UL
}

internal fun builder642f60FourthAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x9a4392dbU + 0x0d1015eaU
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60FourthAMulTable,
            addTable = builder642f60FourthAAddTable,
            tables = tables
        ) * 0x97151be3U + 0x70bf5e2bU
    }
    val folded = fold63c278(
        w.toULong() * 0xcf0e32fa8d969f65UL + 0x61afec1284e66a8cUL,
        tableOffset = builder642f60FourthAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0x610ca66bd199f2b5UL +
        folded * 0xde6166ef00000000UL +
        0x50d31e15d8b1af56UL
}

internal fun builder642f60FourthBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x2ac5e1c1U + 0x957be66cU
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60FourthBMulTable,
            addTable = builder642f60FourthBAddTable,
            tables = tables
        ) * 0xee64b1f5U + 0x5df44367U
    }
    val folded = fold63c278(
        w.toULong() * 0x013aed389aef9cd9UL + 0x9ac1ba0fa43555a1UL,
        tableOffset = builder642f60FourthBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0x3ca485be7caa6cf3UL +
        folded * 0xcec7175500000000UL +
        0x44d63f7b1e64fe52UL
}

internal fun builder642f60MidContextStreamWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0xef8a98c3U + 0x5251f797U
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60MidContextMulTable,
            addTable = builder642f60MidContextAddTable,
            tables = tables
        ) * 0x9198bbe1U + 0x96d49925U
    }
    val folded = fold63c278(
        w.toULong() * 0x10aca1fefeaea819UL + 0x791f2f89d18f0bccUL,
        tableOffset = builder642f60MidContextFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0x7e3d39fbe4db207bUL +
        folded * 0xf948d04d00000000UL +
        0xefba822749ae8302UL
}

internal fun builder642f60MidSPF0StreamWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x833f922fU + 0xb8f79a5cU
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60MidSPF0MulTable,
            addTable = builder642f60MidSPF0AddTable,
            tables = tables
        ) * 0xe9ed5087U + 0x99a662bcU
    }
    val folded = fold63c278(
        w.toULong() * 0x9445eb5f6cc20c37UL + 0x2e115166fc9d38deUL,
        tableOffset = builder642f60MidSPF0FoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0x76037a61bba475bdUL +
        folded * 0x7a18645500000000UL +
        0xeb599af66ebe44f8UL
}

internal fun builder642f60MidSP40BWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x68f1c9c3U + 0x75e3de3dU
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60MidSP40BMulTable,
            addTable = builder642f60MidSP40BAddTable,
            tables = tables
        ) * 0x603eaaa7U + 0xf3704eb8U
    }
    val folded = fold63c278(
        w.toULong() * 0x339d03216c178183UL + 0xccccddb48073e82dUL,
        tableOffset = builder642f60MidSP40BFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0x6255799ade203b13UL +
        folded * 0x504804cf00000000UL +
        0xece1b0fccff7a5d6UL
}

internal fun builder642f60SixthAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0xe12f8e63U + 0xed30a70dU
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60SixthAMulTable,
            addTable = builder642f60SixthAAddTable,
            tables = tables
        ) * 0x61e5762bU + 0xd79521cbU
    }
    val folded = fold63c278(
        w.toULong() * 0x5ebafd23d4800453UL + 0xfce166cf66e4ed89UL,
        tableOffset = builder642f60SixthAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0xfe6b40e82ac2cfadUL +
        folded * 0x4b28a40100000000UL +
        0x5ffded7fc281e70cUL
}

internal fun builder642f60SixthBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x5f8eb06bU + 0x71dd9075U
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60SixthBMulTable,
            addTable = builder642f60SixthBAddTable,
            tables = tables
        ) * 0x32ca6d69U + 0x5b73a719U
    }
    val folded = fold63c278(
        w.toULong() * 0xf82a21269cc1d1dbUL + 0xd1172c1561159fb2UL,
        tableOffset = builder642f60SixthBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0xad5daa3cdd561923UL +
        folded * 0x5a9053a700000000UL +
        0x06054c9125875977UL
}

internal fun reducerU32Words63c278(sourceWords: List<UInt>, stateInit: UInt, stateMul: UInt, foldPreMul: UInt, foldPreAdd: UInt, foldTable: Int, sideMul: UInt, sideFoldedMul: UInt, sideAdd: UInt, nextFolded7Mul: UInt, nextFolded8Mul: UInt, nextAdd: UInt, outMulTable: Int, outAddTable: Int): List<UInt> {
    val tables = FirstPairConfig.tables
    var state = stateInit
    val out = ArrayList<UInt>()
    for (index in sourceWords.indices) {
        val word = sourceWords[index]
        val tableOffset = (index * 4) and 0x1c
        state = state * stateMul + word
        var folded7 = state * foldPreMul + foldPreAdd
        folded7 = fold32ByNibbles63c278(
            folded7,
            tableOffset = foldTable,
            rounds = 7,
            tables = tables
        )
        val side = state * sideMul +
            folded7 * sideFoldedMul +
            sideAdd
        val folded8 = fold32ByNibbles63c278(
            folded7,
            tableOffset = foldTable,
            rounds = 1,
            tables = tables
        )
        out.add(u32TableAffine63c278(
            side,
            mulTable = outMulTable + tableOffset,
            addTable = outAddTable + tableOffset,
            tables = tables
        ))
        state = folded7 * nextFolded7Mul +
            folded8 * nextFolded8Mul +
            nextAdd
    }
    return out
}

internal fun convolutionReducerU32Words63c278(aWords: List<ULong>, bWords: List<ULong>, stateInit: ULong, countMul: ULong, productMul: ULong, sumAMul: ULong, sumBMul: ULong, foldPreMul: ULong, foldPreAdd: ULong, foldTable: Int, sideMul: UInt, sideFoldedMul: UInt, sideAdd: UInt, nextFolded8Mul: ULong, nextFolded16Mul: ULong, nextAdd: ULong, outMulTable: Int, outAddTable: Int): List<UInt> {
    if (aWords.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(aWords.size)
    }
    if (bWords.size != builder63c278VectorWords) {
        throw FirstPairSourceSliceError.invalid63c278VectorWordCount(bWords.size)
    }
    val tables = FirstPairConfig.tables
    val aPrefix = prefixSumsU64(aWords)
    val bPrefix = prefixSumsU64(bWords)
    var state = stateInit
    val out = ArrayList<UInt>()

    for (index in 0 until builder64bd0cWorkspaceWords) {
        val start = maxOf(0, index - (builder63c278VectorWords - 1))
        val end = minOf(index, builder63c278VectorWords - 1)
        var productSum: ULong = 0UL
        if (start <= end) {
            for (position in start..end) {
                productSum = productSum + aWords[position] * bWords[index - position]
            }
        }
        val span = if (start <= end) (end - start + 1).toULong() else 0UL
        val sumA = rangeSumFromPrefix(aPrefix, start = start, end = end)
        val sumB = rangeSumFromPrefix(bPrefix, start = index - end, end = index - start)
        val mixed = state +
            span * countMul +
            productSum * productMul +
            sumA * sumAMul +
            sumB * sumBMul
        val foldedSeed = mixed * foldPreMul + foldPreAdd
        val folded7 = fold63c278(foldedSeed, tableOffset = foldTable, rounds = 7, tables = tables)
        val folded16 = fold63c278(foldedSeed, tableOffset = foldTable, rounds = 16, tables = tables)
        val side = mixed.toUInt() * sideMul +
            folded7.toUInt() * sideFoldedMul +
            sideAdd
        val tableOffset = (index * 4) and 0x1c
        out.add(
            u32TableAffine63c278(
                side,
                mulTable = outMulTable + tableOffset,
                addTable = outAddTable + tableOffset,
                tables = tables
            )
        )
        state = folded7 * nextFolded8Mul +
            folded16 * nextFolded16Mul +
            nextAdd
    }
    return out
}

internal fun builder6388f0CallerStreamU64(word: UInt, wordMul: ULong, wordAdd: ULong, foldTable: Int, foldMul: ULong, mixMul: ULong, mixAdd: ULong, tables: SourceTables): ULong {
    val folded = fold63c278(
        word.toULong() * wordMul + wordAdd,
        tableOffset = foldTable,
        rounds = 8,
        tables = tables
    )
    return folded * foldMul +
        word.toULong() * mixMul +
        mixAdd
}

internal fun builder6388f0CallerStreamU64FirstNibbleBeforeAdd(word: UInt, wordMul: ULong, wordAdd: ULong, foldTable: Int, foldMul: ULong, mixMul: ULong, mixAdd: ULong, tables: SourceTables): ULong {
    val product = word.toULong() * wordMul
    val folded = fold63c278FirstNibbleBeforeAdd(
        product,
        addend = wordAdd,
        tableOffset = foldTable,
        tables = tables
    )
    return folded * foldMul +
        word.toULong() * mixMul +
        mixAdd
}

internal fun builder6388f0Convolution44(stack: ByteArray, aVecOffset: Int, aPrefixOffset: Int, bVecOffset: Int, bPrefixOffset: Int, outOffset: Int, constants: Process2P5PublicConstants): Unit {
    val aVec = (0 until builder63c278VectorWords).map { readUInt64LE(stack, aVecOffset + it * 8) }
    val aPrefix = (0 until builder63c278VectorWords).map { readUInt64LE(stack, aPrefixOffset + it * 8) }
    val bVec = (0 until builder63c278VectorWords).map { readUInt64LE(stack, bVecOffset + it * 8) }
    val bPrefix = (0 until builder63c278VectorWords).map { readUInt64LE(stack, bPrefixOffset + it * 8) }

    for (index in 0 until builder64cd40WorkspaceWords) {
        val low = maxOf(0, index - (builder63c278VectorWords - 1))
        val high = minOf(index, builder63c278VectorWords - 1)
        if (low > high) {
            writeUInt64LE(
                constants.countAdd * constants.finalMul + constants.finalAdd,
                stack,
                outOffset + index * 8
            )
            continue
        }

        var productSum: ULong = 0UL
        for (bIndex in low..high) {
            productSum = productSum + aVec[index - bIndex] * bVec[bIndex]
        }

        var aSum = aPrefix[index - low]
        val previousAIndex = index - high - 1
        if (previousAIndex >= 0) {
            aSum = aSum - aPrefix[previousAIndex]
        }

        var bSum = bPrefix[high]
        if (low > 0) {
            bSum = bSum - bPrefix[low - 1]
        }

        val count = (high - low + 1).toULong()
        var out = count * constants.countMul + constants.countAdd
        out = out + productSum * constants.productMul
        out = out + bSum * constants.bPrefixMul
        out = out + aSum * constants.aPrefixMul
        out = out * constants.finalMul + constants.finalAdd
        writeUInt64LE(out, stack, outOffset + index * 8)
    }
}

internal fun u64StreamWordFromU32Affine(word: UInt, index: Int, mulTable: Int, addTable: Int, u32Mul: UInt, u32Add: UInt, foldMul: ULong, foldAdd: ULong, foldTable: Int, linearMul: ULong, foldedMul: ULong, linearAdd: ULong, tables: SourceTables): ULong {
    val affine = u32Affine63c278(
        word,
        index = index,
        mulTable = mulTable,
        addTable = addTable,
        tables = tables
    )
    val w = affine * u32Mul + u32Add
    val folded = fold63c278(
        w.toULong() * foldMul + foldAdd,
        tableOffset = foldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * linearMul +
        folded * foldedMul +
        linearAdd
}

internal fun builder6473d0TenthAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0TenthA0U32Mul + builder6473d0TenthA0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0TenthAMulTable,
            addTable = builder6473d0TenthAAddTable,
            tables = tables
        ) * builder6473d0TenthAU32Mul + builder6473d0TenthAU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0TenthAFoldMul + builder6473d0TenthAFoldAdd,
        tableOffset = builder6473d0TenthAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0TenthALinearMul +
        folded * builder6473d0TenthAFoldedMul +
        builder6473d0TenthALinearAdd
}

internal fun builder6473d0TenthBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0TenthB0U32Mul + builder6473d0TenthB0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0TenthBMulTable,
            addTable = builder6473d0TenthBAddTable,
            tables = tables
        ) * builder6473d0TenthBU32Mul + builder6473d0TenthBU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0TenthBFoldMul + builder6473d0TenthBFoldAdd,
        tableOffset = builder6473d0TenthBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0TenthBLinearMul +
        folded * builder6473d0TenthBFoldedMul +
        builder6473d0TenthBLinearAdd
}

internal fun builder642f60EighthAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0xcbb0f5d5U + 0xbc0ef378U
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60EighthAMulTable,
            addTable = builder642f60EighthAAddTable,
            tables = tables
        ) * 0xf4ade1bbU + 0x14498d6fU
    }
    val folded = fold63c278(
        w.toULong() * 0x9e47779cb45c572fUL + 0x4d028e31657373f8UL,
        tableOffset = builder642f60EighthAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0xa08be5a120f8c447UL +
        folded * 0xc729619700000000UL +
        0x6e392c9a885df52cUL
}

internal fun builder642f60EighthBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * 0x667888b5U + 0x8f0d98aeU
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder642f60EighthBMulTable,
            addTable = builder642f60EighthBAddTable,
            tables = tables
        ) * 0xea2a6db9U + 0x0a1fb246U
    }
    val folded = fold63c278(
        w.toULong() * 0x5642541b8c3e3bb7UL + 0x9965e0d235e6c59bUL,
        tableOffset = builder642f60EighthBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * 0xb84f64edab558eddUL +
        folded * 0x82850df500000000UL +
        0xe5d3a90393662e86UL
}

internal fun builder6473d0FirstAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0FirstA0U32Mul + builder6473d0FirstA0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0FirstAMulTable,
            addTable = builder6473d0FirstAAddTable,
            tables = tables
        ) * builder6473d0FirstAU32Mul + builder6473d0FirstAU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0FirstAFoldMul + builder6473d0FirstAFoldAdd,
        tableOffset = builder6473d0FirstAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0FirstALinearMul +
        folded * builder6473d0FirstAFoldedMul +
        builder6473d0FirstALinearAdd
}

internal fun builder6473d0FirstBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0FirstB0U32Mul + builder6473d0FirstB0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0FirstBMulTable,
            addTable = builder6473d0FirstBAddTable,
            tables = tables
        ) * builder6473d0FirstBU32Mul + builder6473d0FirstBU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0FirstBFoldMul + builder6473d0FirstBFoldAdd,
        tableOffset = builder6473d0FirstBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0FirstBLinearMul +
        folded * builder6473d0FirstBFoldedMul +
        builder6473d0FirstBLinearAdd
}

internal fun builder6473d0SecondAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0SecondA0U32Mul + builder6473d0SecondA0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0SecondAMulTable,
            addTable = builder6473d0SecondAAddTable,
            tables = tables
        ) * builder6473d0SecondAU32Mul + builder6473d0SecondAU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0SecondAFoldMul + builder6473d0SecondAFoldAdd,
        tableOffset = builder6473d0SecondAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0SecondALinearMul +
        folded * builder6473d0SecondAFoldedMul +
        builder6473d0SecondALinearAdd
}

internal fun builder6473d0SecondBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0SecondB0U32Mul + builder6473d0SecondB0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0SecondBMulTable,
            addTable = builder6473d0SecondBAddTable,
            tables = tables
        ) * builder6473d0SecondBU32Mul + builder6473d0SecondBU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0SecondBFoldMul + builder6473d0SecondBFoldAdd,
        tableOffset = builder6473d0SecondBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0SecondBLinearMul +
        folded * builder6473d0SecondBFoldedMul +
        builder6473d0SecondBLinearAdd
}

internal fun builder6473d0ThirdAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0ThirdA0U32Mul + builder6473d0ThirdA0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0ThirdAMulTable,
            addTable = builder6473d0ThirdAAddTable,
            tables = tables
        ) * builder6473d0ThirdAU32Mul + builder6473d0ThirdAU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0ThirdAFoldMul + builder6473d0ThirdAFoldAdd,
        tableOffset = builder6473d0ThirdAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0ThirdALinearMul +
        folded * builder6473d0ThirdAFoldedMul +
        builder6473d0ThirdALinearAdd
}

internal fun builder6473d0ThirdBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0ThirdB0U32Mul + builder6473d0ThirdB0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0ThirdBMulTable,
            addTable = builder6473d0ThirdBAddTable,
            tables = tables
        ) * builder6473d0ThirdBU32Mul + builder6473d0ThirdBU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0ThirdBFoldMul + builder6473d0ThirdBFoldAdd,
        tableOffset = builder6473d0ThirdBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0ThirdBLinearMul +
        folded * builder6473d0ThirdBFoldedMul +
        builder6473d0ThirdBLinearAdd
}

internal fun builder6473d0FourthAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0FourthA0U32Mul + builder6473d0FourthA0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0FourthAMulTable,
            addTable = builder6473d0FourthAAddTable,
            tables = tables
        ) * builder6473d0FourthAU32Mul + builder6473d0FourthAU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0FourthAFoldMul + builder6473d0FourthAFoldAdd,
        tableOffset = builder6473d0FourthAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0FourthALinearMul +
        folded * builder6473d0FourthAFoldedMul +
        builder6473d0FourthALinearAdd
}

internal fun builder6473d0FourthBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0FourthB0U32Mul + builder6473d0FourthB0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0FourthBMulTable,
            addTable = builder6473d0FourthBAddTable,
            tables = tables
        ) * builder6473d0FourthBU32Mul + builder6473d0FourthBU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0FourthBFoldMul + builder6473d0FourthBFoldAdd,
        tableOffset = builder6473d0FourthBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0FourthBLinearMul +
        folded * builder6473d0FourthBFoldedMul +
        builder6473d0FourthBLinearAdd
}

internal fun builder6473d0FifthAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0FifthA0U32Mul + builder6473d0FifthA0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0FifthAMulTable,
            addTable = builder6473d0FifthAAddTable,
            tables = tables
        ) * builder6473d0FifthAU32Mul + builder6473d0FifthAU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0FifthAFoldMul + builder6473d0FifthAFoldAdd,
        tableOffset = builder6473d0FifthAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0FifthALinearMul +
        folded * builder6473d0FifthAFoldedMul +
        builder6473d0FifthALinearAdd
}

internal fun builder6473d0FifthBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0FifthB0U32Mul + builder6473d0FifthB0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0FifthBMulTable,
            addTable = builder6473d0FifthBAddTable,
            tables = tables
        ) * builder6473d0FifthBU32Mul + builder6473d0FifthBU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0FifthBFoldMul + builder6473d0FifthBFoldAdd,
        tableOffset = builder6473d0FifthBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0FifthBLinearMul +
        folded * builder6473d0FifthBFoldedMul +
        builder6473d0FifthBLinearAdd
}

internal fun builder6473d0SixthAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0SixthA0U32Mul + builder6473d0SixthA0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0SixthAMulTable,
            addTable = builder6473d0SixthAAddTable,
            tables = tables
        ) * builder6473d0SixthAU32Mul + builder6473d0SixthAU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0SixthAFoldMul + builder6473d0SixthAFoldAdd,
        tableOffset = builder6473d0SixthAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0SixthALinearMul +
        folded * builder6473d0SixthAFoldedMul +
        builder6473d0SixthALinearAdd
}

internal fun builder6473d0SixthBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0SixthB0U32Mul + builder6473d0SixthB0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0SixthBMulTable,
            addTable = builder6473d0SixthBAddTable,
            tables = tables
        ) * builder6473d0SixthBU32Mul + builder6473d0SixthBU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0SixthBFoldMul + builder6473d0SixthBFoldAdd,
        tableOffset = builder6473d0SixthBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0SixthBLinearMul +
        folded * builder6473d0SixthBFoldedMul +
        builder6473d0SixthBLinearAdd
}

internal fun builder6473d0SixthSP750Word(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0SixthB0U32Mul + builder6473d0SixthB0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0SixthAMulTable,
            addTable = builder6473d0SixthAAddTable,
            tables = tables
        ) * builder6473d0SixthAU32Mul + builder6473d0SixthAU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0SixthBFoldMul + builder6473d0SixthBFoldAdd,
        tableOffset = builder6473d0SixthBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0SixthBLinearMul +
        folded * builder6473d0SixthBFoldedMul +
        builder6473d0SixthBLinearAdd
}

internal fun builder6473d0SixthSP698Word(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0SixthA0U32Mul + builder6473d0SixthA0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0SixthBMulTable,
            addTable = builder6473d0SixthBAddTable,
            tables = tables
        ) * builder6473d0SixthBU32Mul + builder6473d0SixthBU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0SixthAFoldMul + builder6473d0SixthAFoldAdd,
        tableOffset = builder6473d0SixthAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0SixthALinearMul +
        folded * builder6473d0SixthAFoldedMul +
        builder6473d0SixthALinearAdd
}

internal fun builder6473d0SeventhSP750Word(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0SeventhSP750A0U32Mul + builder6473d0SeventhSP750A0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0SeventhSP750MulTable,
            addTable = builder6473d0SeventhSP750AddTable,
            tables = tables
        ) * builder6473d0SeventhSP750U32Mul + builder6473d0SeventhSP750U32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0SeventhSP750FoldMul + builder6473d0SeventhSP750FoldAdd,
        tableOffset = builder6473d0SeventhSP750FoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0SeventhSP750LinearMul +
        folded * builder6473d0SeventhSP750FoldedMul +
        builder6473d0SeventhSP750LinearAdd
}

internal fun builder6473d0SeventhSP698Word(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0SeventhSP698A0U32Mul + builder6473d0SeventhSP698A0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0SeventhSP698MulTable,
            addTable = builder6473d0SeventhSP698AddTable,
            tables = tables
        ) * builder6473d0SeventhSP698U32Mul + builder6473d0SeventhSP698U32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0SeventhSP698FoldMul + builder6473d0SeventhSP698FoldAdd,
        tableOffset = builder6473d0SeventhSP698FoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0SeventhSP698LinearMul +
        folded * builder6473d0SeventhSP698FoldedMul +
        builder6473d0SeventhSP698LinearAdd
}

internal fun builder6473d0EighthAWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0EighthA0U32Mul + builder6473d0EighthA0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0EighthAMulTable,
            addTable = builder6473d0EighthAAddTable,
            tables = tables
        ) * builder6473d0EighthAU32Mul + builder6473d0EighthAU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0EighthAFoldMul + builder6473d0EighthAFoldAdd,
        tableOffset = builder6473d0EighthAFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0EighthALinearMul +
        folded * builder6473d0EighthAFoldedMul +
        builder6473d0EighthALinearAdd
}

internal fun builder6473d0EighthBWord(word: UInt, index: Int, tables: SourceTables): ULong {
    val w: UInt
    if (index == 0) {
        w = word * builder6473d0EighthB0U32Mul + builder6473d0EighthB0U32Add
    } else {
        w = u32Affine63c278(
            word,
            index = index,
            mulTable = builder6473d0EighthBMulTable,
            addTable = builder6473d0EighthBAddTable,
            tables = tables
        ) * builder6473d0EighthBU32Mul + builder6473d0EighthBU32Add
    }
    val folded = fold63c278(
        w.toULong() * builder6473d0EighthBFoldMul + builder6473d0EighthBFoldAdd,
        tableOffset = builder6473d0EighthBFoldTable,
        rounds = 8,
        tables = tables
    )
    return w.toULong() * builder6473d0EighthBLinearMul +
        folded * builder6473d0EighthBFoldedMul +
        builder6473d0EighthBLinearAdd
}

internal fun convolutionWorkspaceU64(aWords: List<ULong>, bWords: List<ULong>, baseAdd: ULong, countMul: ULong, productMul: ULong, sumAMul: ULong, sumBMul: ULong, finalMul: ULong, finalAdd: ULong): ByteArray {
    val aPrefix = prefixSumsU64(aWords)
    val bPrefix = prefixSumsU64(bWords)
    val out = ArrayList<Byte>()

    for (index in 0 until 44) {
        val start = maxOf(0, index - 21)
        val end = minOf(index, 21)
        if (start > end) {
            out.addAll(u64LEBytes(baseAdd * finalMul + finalAdd).toList())
            continue
        }
        var productSum: ULong = 0UL
        for (pos in start..end) {
            productSum = productSum + (aWords[pos] * bWords[index - pos])
        }
        val sumA = rangeSumFromPrefix(aPrefix, start = start, end = end)
        val sumB = rangeSumFromPrefix(bPrefix, start = index - end, end = index - start)
        val mixed = (end - start + 1).toULong() * countMul +
            baseAdd +
            productSum * productMul +
            sumA * sumAMul +
            sumB * sumBMul
        out.addAll(u64LEBytes(mixed * finalMul + finalAdd).toList())
    }
    return out.toByteArray()
}

internal fun prefixSumsU64(words: List<ULong>): List<ULong> {
    var total: ULong = 0UL
    val out = ArrayList<ULong>()
    for (word in words) {
        total = total + word
        out.add(total)
    }
    return out
}

internal fun shiftedPrefixSumsU64(words: List<ULong>): List<ULong> {
    var total: ULong = 0UL
    val out = ArrayList<ULong>()
    out.add(0UL)
    for (word in words) {
        total = total + word
        out.add(total)
    }
    return out
}

internal fun rangeSumFromPrefix(prefix: List<ULong>, start: Int, end: Int): ULong {
    if (start > end) {
        return 0UL
    }
    val total = prefix[end]
    if (start == 0) {
        return total
    }
    return total - prefix[start - 1]
}

internal fun arg0Words63c278(arg0: ByteArray): List<UInt> {
    if (arg0.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort("63c278 arg0", arg0.size, builder63c278VectorBytes)
    }
    val bytes = arg0
    return (0 until builder63c278VectorWords).map { readUInt32LE(bytes, it * 4) }
}

internal fun u32Affine63c278(word: UInt, index: Int, mulTable: Int, addTable: Int, tables: SourceTables): UInt {
    val tableOffset = (index * 4) and 0x1c
    val multiplier = u32TableWord63c278(mulTable + tableOffset, tables)
    val addend = u32TableWord63c278(addTable + tableOffset, tables)
    return (word.toULong() * multiplier.toULong() + addend.toULong()).toUInt()
}

internal fun u32Affine633fa8Tail(word: UInt, index: Int, mulTable: Int, addTable: Int, tables: SourceTables): UInt {
    val tableOffset = (index * 4) and 0x1c
    val multiplier = u32TableWord633fa8Tail(mulTable + tableOffset, tables)
    val addend = u32TableWord633fa8Tail(addTable + tableOffset, tables)
    return (word.toULong() * multiplier.toULong() + addend.toULong()).toUInt()
}

internal fun u32AffineInverse63c278(word: UInt, index: Int, mulTable: Int, addTable: Int, tables: SourceTables): UInt {
    val tableOffset = (index * 4) and 0x1c
    val multiplier = u32TableWord63c278(mulTable + tableOffset, tables)
    if (multiplier and 1U != 1U) {
        throw FirstPairSourceSliceError.nonInvertibleAffineMultiplier(multiplier.toInt())
    }
    val addend = u32TableWord63c278(addTable + tableOffset, tables)
    val inverse = modularInverseOddUInt32(multiplier)
    return (word - addend) * inverse
}

internal fun u32TableAffine63c278(word: UInt, mulTable: Int, addTable: Int, tables: SourceTables): UInt {
    val multiplier = u32TableWord63c278(mulTable, tables)
    val addend = u32TableWord63c278(addTable, tables)
    return (word.toULong() * multiplier.toULong() + addend.toULong()).toUInt()
}

internal fun u32AffineInverseBytes63c278(input: ByteArray, mulTable: Int, addTable: Int, label: String, tables: SourceTables): ByteArray {
    if (input.size < builder63c278VectorBytes) {
        throw FirstPairSourceSliceError.sourceTooShort(label, input.size, builder63c278VectorBytes)
    }
    val bytes = input
    val out = ArrayList<Byte>()
    for (index in 0 until builder63c278VectorWords) {
        val word = u32AffineInverse63c278(
            readUInt32LE(bytes, index * 4),
            index = index,
            mulTable = mulTable,
            addTable = addTable,
            tables = tables
        )
        out.addAll(u32LEBytes(word).toList())
    }
    return out.toByteArray()
}

internal fun modularInverseOddUInt32(value: UInt): UInt {
    var inverse = value
    for (i in 0 until 5) {
        inverse = inverse * (2U - value * inverse)
    }
    return inverse
}

internal fun u32TableWord63c278(absoluteOffset: Int, tables: SourceTables): UInt {
    val relative = absoluteOffset - table63c278U32Base
    if (relative < 0 || relative + 4 > tables.u32Tables63c278.size) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(
            RuntimeTable.FIRST_PAIR_63C278_U32_TABLES.fileName,
            absoluteOffset
        )
    }
    return readUInt32LE(tables.u32Tables63c278, relative)
}

internal fun u32TableWord633fa8Tail(absoluteOffset: Int, tables: SourceTables): UInt {
    if (absoluteOffset >= table63c278U32Base) {
        return u32TableWord63c278(absoluteOffset, tables)
    }
    val relative = absoluteOffset - table633fa8TailU32LowBase
    if (relative < 0 || relative + 4 > tables.tailU32LowTables633fa8.size) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(
            RuntimeTable.FIRST_PAIR_633FA8_TAIL_U32_LOW_TABLES.fileName,
            absoluteOffset
        )
    }
    return readUInt32LE(tables.tailU32LowTables633fa8, relative)
}
