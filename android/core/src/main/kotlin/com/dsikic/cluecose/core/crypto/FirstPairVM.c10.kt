// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.RuntimeTable
import com.dsikic.cluecose.core.tables.SourceTables

internal fun builder6388f0LowSeedTailPairFromEntryAndCF0(entrySource: ByteArray, pre2CF0: ByteArray, preCF0: ByteArray, tailCF0: ByteArray, tables: SourceTables): Builder6388f0LowSeedTailPair {
    if (entrySource.size < builder6388f0LowSeedEntrySourceBytes) {
        throw FirstPairSourceSliceError.sourceTooShort(
            "6388f0 low-seed entry source",
            entrySource.size,
            builder6388f0LowSeedEntrySourceBytes
        )
    }
    require(pre2CF0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre2 cf0")
    require(preCF0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre cf0")
    require(tailCF0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail cf0")

    val source = entrySource
    val entryHead = source.copyOfRange(0, builder6388f0LowSeedBlockBytes)
    val entryTail = source.copyOfRange(builder6388f0LowSeedBlockBytes, builder6388f0LowSeedEntrySourceBytes)
    val pre2S898 = vm638840(builder6388f0LowSeedEntryS898Magic, entryHead, entryHead, tables)
    val pre2S78E = vm638840(builder6388f0LowSeedEntryS78EMagic, entryTail, entryTail, tables)

    val pre2Static = builder6388f0LowSeedStaticBlock(builder6388f0LowSeedPrev2Static, tables)
    val preStatic = builder6388f0LowSeedStaticBlock(builder6388f0LowSeedPreStatic, tables)
    val tailStatic = builder6388f0LowSeedStaticBlock(builder6388f0LowSeedTailStatic, tables)
    val pre2BD0 = vm638840(builder6388f0LowSeedPrev2BD0Magic, pre2CF0, pre2Static, tables)
    val pre2S684 = vm638840(builder6388f0LowSeedPrev2S684Magic, pre2BD0, pre2BD0, tables)
    val pre2S57A = vm638840(builder6388f0LowSeedMiddleMagic, pre2S898, pre2S684, tables)
    val preS898 = vm638840(builder6388f0LowSeedTailLeftMagic, pre2S78E, pre2S78E, tables)
    val preS78E = vm638840(builder6388f0LowSeedTailRightMagic, pre2S57A, pre2S57A, tables)
    val preBD0 = vm638840(builder6388f0LowSeedPreBD0Magic, preCF0, preStatic, tables)
    val tailBD0 = vm638840(builder6388f0LowSeedTailBD0Magic, tailCF0, tailStatic, tables)
    return builder6388f0LowSeedTailPairFromSlotState(preS898, preS78E, preBD0, tailBD0, tables)
}

internal fun builder6388f0LowSeedTailPairFromSlotState(preS898: ByteArray, preS78E: ByteArray, preBD0: ByteArray, tailBD0: ByteArray, tables: SourceTables): Builder6388f0LowSeedTailPair {
    require(preS898, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre s898")
    require(preS78E, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre s78e")
    require(preBD0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed pre bd0")
    require(tailBD0, builder6388f0LowSeedBlockBytes, "6388f0 low-seed tail bd0")

    val prevS684 = vm638840(builder6388f0LowSeedPrevS684Magic, preBD0, preBD0, tables)
    val prevS57A = vm638840(builder6388f0LowSeedMiddleMagic, preS898, prevS684, tables)
    val seedS898 = vm638840(builder6388f0LowSeedTailLeftMagic, preS78E, preS78E, tables)
    val seedS78E = vm638840(builder6388f0LowSeedTailRightMagic, prevS57A, prevS57A, tables)
    val tailS684 = vm638840(builder6388f0LowSeedTailS684Magic, tailBD0, tailBD0, tables)
    val tailS57A = vm638840(builder6388f0LowSeedMiddleMagic, seedS898, tailS684, tables)
    val left = vm638840(builder6388f0LowSeedTailLeftMagic, seedS78E, seedS78E, tables)
    val right = vm638840(builder6388f0LowSeedTailRightMagic, tailS57A, tailS57A, tables)
    return Builder6388f0LowSeedTailPair(left, right)
}

internal fun vm64e2b8(magic: ULong, src1: ByteArray, src2: ByteArray, tables: SourceTables): ByteArray {
    val progOff = (magic and 0x3FFFFUL).toInt()
    val count = ((magic shr 36) and 0x3FFFUL).toInt()
    val tail = (magic shr 50).toInt()
    val total = count + tail
    val prog = checkedSlice(
        tables.prog64e2b8,
        progOff,
        total,
        RuntimeTable.FIRST_PAIR_PROG_64E2B8.fileName
    )
    require(src1, count, "vm64e2b8 src1")
    require(src2, total, "vm64e2b8 src2")

    var state = 0
    val out = ByteArray(total)
    for (i in 0 until count) {
        state = step(state, src1[i], src2[i], prog[i], tables)
        out[i] = (state and 7).toByte()
    }
    for (i in 0 until tail) {
        val pos = count + i
        state = step(state, null, src2[pos], prog[pos], tables)
        out[pos] = (state and 7).toByte()
    }
    return out
}

internal fun vm638840(magic: ULong, src1: ByteArray, src2: ByteArray, tables: SourceTables): ByteArray {
    val progOff = (magic and 0x3FFFFUL).toInt()
    val count = ((magic shr 36) and 0x3FFFUL).toInt()
    val tail = (magic shr 50).toInt()
    val total = count + tail
    val prog = checkedSlice(
        tables.prog638840,
        progOff,
        total,
        RuntimeTable.FIRST_PAIR_PROG_638840.fileName
    )
    require(src1, count, "vm638840 src1")
    require(src2, total, "vm638840 src2")

    var state = 0
    val out = ByteArray(total)
    for (i in 0 until count) {
        state = step(state, src1[i], src2[i], prog[i], tables)
        out[i] = (state and 7).toByte()
    }
    for (i in 0 until tail) {
        val pos = count + i
        state = step(state, null, src2[pos], prog[pos], tables)
        out[pos] = (state and 7).toByte()
    }
    return out
}

internal fun vm6420d8(magic: ULong, src1: ByteArray, src2: ByteArray, tables: SourceTables): ByteArray {
    val progOff = (magic and 0x3FFFFUL).toInt()
    val primer = ((magic shr 22) and 0x3FFFUL).toInt()
    val count = ((magic shr 36) and 0x3FFFUL).toInt()
    val tail = (magic shr 50).toInt()
    val totalProg = primer + count + tail
    val prog = checkedSlice(
        tables.prog638840,
        progOff,
        totalProg,
        RuntimeTable.FIRST_PAIR_PROG_638840.fileName
    )
    require(src1, primer + count, "vm6420d8 src1")
    require(src2, primer + count, "vm6420d8 src2")

    var state = 0
    for (i in 0 until primer) {
        state = step(state, src1[i], src2[i], prog[i], tables)
    }

    val out = ByteArray(count + tail)
    for (i in 0 until count) {
        val srcPos = primer + i
        state = step(state, src1[srcPos], src2[srcPos], prog[srcPos], tables)
        out[i] = (state and 7).toByte()
    }
    for (i in 0 until tail) {
        val progPos = primer + count + i
        state = step(state, null, null, prog[progPos], tables)
        out[count + i] = (state and 7).toByte()
    }
    return out
}

internal fun vm641fcc(magic: ULong, src: ByteArray, tables: SourceTables): ByteArray {
    val progOff = (magic and 0x3FFFFUL).toInt()
    val primer = ((magic shr 22) and 0x3FFFUL).toInt()
    val count = ((magic shr 36) and 0x3FFFUL).toInt()
    if (primer < 2) {
        throw FirstPairSourceSliceError.invalid641fccPrimer(primer)
    }
    val totalProg = primer + count + 3
    val prog = checkedSlice(
        tables.prog638840,
        progOff,
        totalProg,
        RuntimeTable.FIRST_PAIR_PROG_638840.fileName
    )
    require(src, maxOf(primer + count, 5), "vm641fcc src")

    var state = 0
    for (i in 0 until primer) {
        state = step16Masked(state, src[i], prog[i], tables)
    }

    val out = ByteArray(count + 3)
    for (i in 0 until count) {
        val srcPos = primer + i
        state = step16Masked(state, src[srcPos], prog[srcPos], tables)
        out[i] = (state and 7).toByte()
    }

    val tailProg = primer + count
    for (i in 0 until 3) {
        state = step16Full(state, src[2 + i], prog[tailProg + i], tables)
        out[count + i] = (state and 7).toByte()
    }
    return out
}

internal fun vm67cecc(magic: ULong, src1: ByteArray, src2: ByteArray, tables: SourceTables): ByteArray {
    val progOff = (magic and 0x3FFFFUL).toInt()
    val primer = ((magic shr 22) and 0x3FFFUL).toInt()
    val count = ((magic shr 36) and 0x3FFFUL).toInt()
    val tail = (magic shr 50).toInt()
    val totalProg = primer + count + tail
    val prog = checkedSlice(
        tables.prog67cc18,
        progOff,
        totalProg,
        RuntimeTable.FIRST_PAIR_PROG_67CC18.fileName
    )
    require(src1, primer + count, "vm67cecc src1")
    require(src2, primer + count, "vm67cecc src2")

    var state = 0
    for (i in 0 until primer) {
        state = step(state, src1[i], src2[i], prog[i], tables)
    }

    val out = ByteArray(count + tail)
    for (i in 0 until count) {
        val srcPos = primer + i
        state = step(state, src1[srcPos], src2[srcPos], prog[srcPos], tables)
        out[i] = (state and 7).toByte()
    }
    for (i in 0 until tail) {
        val progPos = primer + count + i
        state = step(state, null, null, prog[progPos], tables)
        out[count + i] = (state and 7).toByte()
    }
    return out
}

internal fun vm67d524(magic: ULong, src: ByteArray, tables: SourceTables): ByteArray {
    val progOff = (magic and 0x3FFFFUL).toInt()
    val primer = ((magic shr 22) and 0x3FFFUL).toInt()
    val count = ((magic shr 36) and 0x3FFFUL).toInt()
    val totalProg = primer + count + 3
    val prog = checkedSlice(
        tables.prog67cc18,
        progOff,
        totalProg,
        RuntimeTable.FIRST_PAIR_PROG_67CC18.fileName
    )
    require(src, maxOf(primer + count, 5), "vm67d524 src")

    var state = 0
    for (i in 0 until primer) {
        state = step16Masked(state, src[i], prog[i], tables)
    }

    val out = ByteArray(count + 3)
    for (i in 0 until count) {
        val srcPos = primer + i
        state = step16Masked(state, src[srcPos], prog[srcPos], tables)
        out[i] = (state and 7).toByte()
    }

    val tailProg = primer + count
    for (i in 0 until 3) {
        state = step16Full(state, src[2 + i], prog[tailProg + i], tables)
        out[count + i] = (state and 7).toByte()
    }
    return out
}

internal fun vm67cc18(magic: ULong, src1: ByteArray, src2: ByteArray, tables: SourceTables): ByteArray {
    val progOff = (magic and 0x3FFFFUL).toInt()
    val count = ((magic shr 36) and 0x3FFFUL).toInt()
    val tail = (magic shr 50).toInt()
    val total = count + tail
    val prog = checkedSlice(
        tables.prog67cc18,
        progOff,
        total,
        RuntimeTable.FIRST_PAIR_PROG_67CC18.fileName
    )
    require(src1, count, "vm67cc18 src1")
    require(src2, total, "vm67cc18 src2")

    var state = 0
    val out = ByteArray(total)
    for (i in 0 until count) {
        state = step(state, src1[i], src2[i], prog[i], tables)
        out[i] = (state and 7).toByte()
    }
    for (i in 0 until tail) {
        val pos = count + i
        state = step(state, null, src2[pos], prog[pos], tables)
        out[pos] = (state and 7).toByte()
    }
    return out
}

internal fun vm67076c(magic: ULong, src1: ByteArray, src2: ByteArray, tables: SourceTables): ByteArray {
    val progOff = (magic and 0x3FFFFUL).toInt()
    val prog = checkedSlice(
        tables.prog67076c,
        progOff,
        block66Size,
        RuntimeTable.FIRST_PAIR_PROG_67076C.fileName
    )
    require(src1, block66Size, "vm67076c src1")
    require(src2, block66Size, "vm67076c src2")

    var state = 0
    val out = ByteArray(block66Size)
    for (i in 0 until block66Size) {
        state = step(state, src1[i], src2[i], prog[i], tables)
        out[i] = (state and 7).toByte()
    }
    return out
}

internal fun vm64e17c(src0: ByteArray, src1: ByteArray, tables: SourceTables): ByteArray {
    require(src0, scratch130Size, "vm64e17c src0")
    require(src1, scratch130Size, "vm64e17c src1")

    var state = step(0, src0[0], src1[0], 14.toByte(), tables)
    val headProgs = byteArrayOf(22.toByte(), 9.toByte(), 33.toByte())
    for (index in headProgs.indices) {
        val pos = index + 1
        state = step(state, src0[pos], src1[pos], headProgs[index], tables)
    }

    val prog = checkedSlice(
        tables.prog64e2b8,
        vm64e17cProgOffset,
        vm64e17cProgLength,
        RuntimeTable.FIRST_PAIR_PROG_64E2B8.fileName
    )
    val out = ByteArray(scratch130Size)
    for (i in 0 until vm64e17cProgLength) {
        state = step(state, src0[4 + i], src1[4 + i], prog[i], tables)
        out[i] = (state and 7).toByte()
    }
    val tailProgs = byteArrayOf(12.toByte(), 17.toByte(), 18.toByte(), 27.toByte())
    for (i in tailProgs.indices) {
        val pos = vm64e17cProgLength + i
        state = step(state, null, null, tailProgs[i], tables)
        out[pos] = (state and 7).toByte()
    }
    return out
}

internal fun shiftedScratch(block66: ByteArray): ByteArray {
    val scratch = ByteArray(scratch130Size)
    scratch[0x3f] = 3
    for (i in 0 until block66Size) {
        scratch[0x40 + i] = block66[i]
    }
    return scratch
}

internal fun step(state: Int, src1: Byte?, src2: Byte?, prog: Byte, tables: SourceTables): Int {
    var idx = state and 0xf8
    if (src1 != null) {
        idx = idx xor (src1.toInt() and 0xFF)
    }
    if (src2 != null) {
        idx = idx or ((src2.toInt() and 0xFF) shl 8)
    }
    idx = idx xor ((prog.toInt() and 0xFF) shl 11)
    if (idx < 0 || idx >= tables.sbox19.size) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(RuntimeTable.SBOX19.fileName, idx)
    }
    return tables.sbox19[idx].toInt() and 0xFF
}

internal fun step16Masked(state: Int, src: Byte, prog: Byte, tables: SourceTables): Int {
    val byteOffset = (((state and 0xff8) xor (src.toInt() and 0xFF)) shl 1) or ((prog.toInt() and 0xFF) shl 13)
    return ttableBHalfword(byteOffset, tables)
}

internal fun step16Full(state: Int, src: Byte, prog: Byte, tables: SourceTables): Int {
    val byteOffset = ((prog.toInt() and 0xFF) shl 13) xor ((state xor (src.toInt() and 0xFF)) shl 1)
    return ttableBHalfword(byteOffset, tables)
}

internal fun ttableBHalfword(byteOffset: Int, tables: SourceTables): Int {
    if (byteOffset < 0 || byteOffset + 1 >= tables.ttableBExt.size) {
        throw FirstPairSourceSliceError.tableReadOutOfBounds(RuntimeTable.CHILD23_TTABLE_B_EXT.fileName, byteOffset)
    }
    return (tables.ttableBExt[byteOffset].toInt() and 0xFF) or ((tables.ttableBExt[byteOffset + 1].toInt() and 0xFF) shl 8)
}

internal fun expandU64Trits(value: ULong, tableOffset: Int, tables: SourceTables): ByteArray {
    val out = mutableListOf<Byte>()
    for (shift in 0 until 64 step 8) {
        val index = tableOffset + ((value shr shift) and 0xFFUL).toInt() * 3
        val packed = checkedSlice(
            tables.finalLenTables,
            index,
            3,
            RuntimeTable.FIRST_PAIR_FINAL_LEN_TABLES.fileName
        )
        for (byte in packed) {
            out.add(((byte.toInt() and 0xFF) and 7).toByte())
            out.add(((byte.toInt() and 0xFF) shr 3).toByte())
        }
    }
    return out.toByteArray()
}

internal fun fold48To34(firstMagic: ULong, tailMagic: ULong, src48: ByteArray, tables: SourceTables): ByteArray {
    require(src48, 0x30, "679f48 length fold source")
    val out = vm67cc18(firstMagic, src48, src48, tables).toMutableList()
    for (offset in 6 until 0x30 step 6) {
        val src = src48.copyOfRange(offset, src48.size)
        val chunk = vm67cc18(tailMagic, src, src, tables)
        out.addAll(chunk.copyOfRange(2, 6).toList())
    }
    return out.toByteArray()
}

internal fun checkedSlice(bytes: ByteArray, offset: Int, count: Int, name: String): ByteArray {

        if (offset < 0 || count < 0 || offset + count > bytes.size) {
            throw FirstPairSourceSliceError.tableReadOutOfBounds(name, offset)
        }
        return bytes.copyOfRange(offset, offset + count)

}

internal fun require(bytes: ByteArray, count: Int, label: String): Unit {

        if (bytes.size < count) {
            throw FirstPairSourceSliceError.sourceTooShort(label, bytes.size, count)
        }

}

internal fun readUInt32LE(bytes: ByteArray, offset: Int): UInt {

        require(offset + 3 < bytes.size) { "readUInt32LE: out of bounds" }
        return ((bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)).toUInt()

}

internal fun readUInt64LE(bytes: ByteArray, offset: Int): ULong {

        require(offset + 7 < bytes.size) { "readUInt64LE: out of bounds" }
        var value: ULong = 0UL
        for (index in 0..7) {
            value = value or ((bytes[offset + index].toULong() and 0xFFUL) shl (index * 8))
        }
        return value

}

internal fun writeUInt32LE(value: UInt, bytes: ByteArray, offset: Int): Unit {

        for (index in 0..3) {
            bytes[offset + index] = ((value shr (index * 8)) and 0xFFU).toByte()
        }

}

internal fun writeUInt64LE(value: ULong, bytes: ByteArray, offset: Int): Unit {

        for (index in 0..7) {
            bytes[offset + index] = ((value shr (index * 8)) and 0xFFUL).toByte()
        }

}

internal fun hexBytes(hex: String): ByteArray {

        require(hex.length % 2 == 0) { "hexBytes: odd-length hex" }
        val bytes = ByteArray(hex.length / 2)
        for (index in bytes.indices) {
            bytes[index] = ((hex[2 * index].digitToInt(16) shl 4) or hex[2 * index + 1].digitToInt(16)).toByte()
        }
        return bytes

}

internal fun u32LEBytes(value: UInt): ByteArray {

        return byteArrayOf(
            (value and 0xFFU).toByte(),
            ((value shr 8) and 0xFFU).toByte(),
            ((value shr 16) and 0xFFU).toByte(),
            ((value shr 24) and 0xFFU).toByte(),
        )

}

internal fun u64LEBytes(value: ULong): ByteArray {

        return (0..7).map { ((value shr (it * 8)) and 0xFFUL).toByte() }.toByteArray()

}
