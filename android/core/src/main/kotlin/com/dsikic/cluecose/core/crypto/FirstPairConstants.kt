// 1:1 mechanical port of LibreCRKit Crypto/FirstPairSourceSlice.swift.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// NOT medical advice. Pure Kotlin/JVM: no Android imports in :core.

package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.util.Hex

/**
 * The 950 `static let` constants of FirstPairSourceSlice.swift, transpiled.
 * Hex values are verbatim; Kotlin UInt/ULong wrapping arithmetic mirrors
 * Swift's fixed-width `&`-prefixed operators. Top-level vals are lazy,
 * so source-order forward references (as in the Swift file) are legal.
 */

const val pre63c278Scalar: ULong = 0x33b7dca8cdf2d720UL
const val highSeed6421c0Scalar: ULong = 0x68404ef676a9b7d3UL

val bundled6388f0LowSeedEntrySource: ByteArray = Hex.decode("05000007010602020306070106030305010005030006070404000501050301050507020005000507050206000104000603060102060003070003020006010703030304050004030306050005070500030600070406070106020002000204070106040307060204050103030302040404000400040701070207060403010704070207060403050302020201070700020001050603000500050607000505000707010507040701020501030102000104070706060405070001050200020102020307020205020707000307070700020004010303030102040007020001060207030304000606040205040003050305030706020500030305030002040101030204030506040701040000020400030704040101070601060704020500050306000007040400020501050007070305040305020604050505030101030300000403050007070207070301020204030701020706010305050506000401050700030607070405040306060105020402040503030206070104000203050700060402050207050003060000000103030704020302040403030605070205000406030006070007000104000102050202060700020101050005050302050300030503000006040405000503010705050507020304060405040207000701010602040101000500030702060600060004010200060103030204030201010106060506070305050403070707070507020300040707000304020001")

val pre63c278Arg0Source: ByteArray =
    byteArrayOf(
        0x21.toByte(), 0xed.toByte(), 0x7e.toByte(), 0x8f.toByte(), 0xc9.toByte(), 0x86.toByte(), 0x29.toByte(), 0x76.toByte(),
        0xac.toByte(), 0x50.toByte(), 0xb4.toByte(), 0xcb.toByte(), 0x1e.toByte(), 0x31.toByte(), 0xa9.toByte(), 0x1f.toByte(),
        0x30.toByte(), 0xfa.toByte(), 0x05.toByte(), 0xc7.toByte(), 0x06.toByte(), 0x82.toByte(), 0xac.toByte(), 0x26.toByte(),
        0xbc.toByte(), 0x7d.toByte(), 0xb7.toByte(), 0x62.toByte(), 0x19.toByte(), 0xfd.toByte(), 0x1d.toByte(), 0x35.toByte(),
        0x21.toByte(), 0xed.toByte(), 0x7e.toByte(), 0x8f.toByte(), 0xb9.toByte(), 0x8b.toByte(), 0xbe.toByte(), 0x51.toByte(),
        0xa3.toByte(), 0x76.toByte(), 0x9d.toByte(), 0xa0.toByte(), 0xc5.toByte(), 0x08.toByte(), 0x6c.toByte(), 0x23.toByte(),
        0x30.toByte(), 0xfa.toByte(), 0x05.toByte(), 0xc7.toByte(), 0x06.toByte(), 0x82.toByte(), 0xac.toByte(), 0x26.toByte(),
        0xbc.toByte(), 0x7d.toByte(), 0xb7.toByte(), 0xd1.toByte(), 0x19.toByte(), 0xfd.toByte(), 0x1d.toByte(), 0x35.toByte(),
        0x46.toByte(), 0x68.toByte(), 0x3b.toByte(), 0x2a.toByte(), 0x18.toByte(), 0xd7.toByte(), 0xe2.toByte(), 0xe2.toByte(),
        0xa3.toByte(), 0x76.toByte(), 0x9d.toByte(), 0xa0.toByte(), 0xc5.toByte(), 0x08.toByte(), 0x6c.toByte(), 0x23.toByte(),
        0x30.toByte(), 0xfa.toByte(), 0x05.toByte(), 0xc7.toByte(), 0x06.toByte(), 0x82.toByte(), 0xac.toByte(), 0x26.toByte(),
    )

val streamStart642f60X2Source: ByteArray =
    byteArrayOf(
        0x4c.toByte(), 0x2d.toByte(), 0xf3.toByte(), 0x05.toByte(), 0xdd.toByte(), 0xb7.toByte(), 0x0c.toByte(), 0x76.toByte(),
        0xe8.toByte(), 0x2a.toByte(), 0xd4.toByte(), 0x04.toByte(), 0x3b.toByte(), 0xe2.toByte(), 0xee.toByte(), 0xa5.toByte(),
        0x81.toByte(), 0xab.toByte(), 0x69.toByte(), 0xf3.toByte(), 0x7c.toByte(), 0xaa.toByte(), 0x49.toByte(), 0xf5.toByte(),
        0xfa.toByte(), 0x7d.toByte(), 0x43.toByte(), 0x81.toByte(), 0x2f.toByte(), 0x10.toByte(), 0x25.toByte(), 0x05.toByte(),
        0xd3.toByte(), 0x4e.toByte(), 0x67.toByte(), 0xbe.toByte(), 0x8d.toByte(), 0x2e.toByte(), 0x98.toByte(), 0xd3.toByte(),
        0xe8.toByte(), 0x2a.toByte(), 0xe3.toByte(), 0x78.toByte(), 0x3b.toByte(), 0x32.toByte(), 0xb0.toByte(), 0x16.toByte(),
        0x81.toByte(), 0xab.toByte(), 0x69.toByte(), 0x5c.toByte(), 0xe3.toByte(), 0x6c.toByte(), 0x62.toByte(), 0xec.toByte(),
        0xa5.toByte(), 0x62.toByte(), 0x1f.toByte(), 0xaf.toByte(), 0xcf.toByte(), 0x68.toByte(), 0x46.toByte(), 0x16.toByte(),
        0xc5.toByte(), 0x19.toByte(), 0x8a.toByte(), 0x79.toByte(), 0x48.toByte(), 0xa0.toByte(), 0x3a.toByte(), 0xd3.toByte(),
        0xe8.toByte(), 0x2a.toByte(), 0xe3.toByte(), 0x78.toByte(), 0x3b.toByte(), 0x32.toByte(), 0xb0.toByte(), 0x16.toByte(),
        0x81.toByte(), 0xab.toByte(), 0x69.toByte(), 0x5c.toByte(), 0xe3.toByte(), 0x6c.toByte(), 0x62.toByte(), 0xec.toByte(),
    )

val highSeed6421c0X2Source: ByteArray =
    byteArrayOf(
        0xd6.toByte(), 0xce.toByte(), 0x5d.toByte(), 0x63.toByte(), 0xde.toByte(), 0x75.toByte(), 0xb3.toByte(), 0x91.toByte(),
        0x43.toByte(), 0x98.toByte(), 0xc9.toByte(), 0xa1.toByte(), 0x23.toByte(), 0x40.toByte(), 0x76.toByte(), 0x0f.toByte(),
        0x3c.toByte(), 0x69.toByte(), 0x5a.toByte(), 0x13.toByte(), 0x9c.toByte(), 0xbb.toByte(), 0xc9.toByte(), 0x13.toByte(),
        0x5d.toByte(), 0x94.toByte(), 0xf6.toByte(), 0x57.toByte(), 0xb7.toByte(), 0x29.toByte(), 0x9c.toByte(), 0xb1.toByte(),
        0x82.toByte(), 0x46.toByte(), 0x31.toByte(), 0x56.toByte(), 0x4e.toByte(), 0x88.toByte(), 0x5b.toByte(), 0x47.toByte(),
        0x9d.toByte(), 0x21.toByte(), 0x1c.toByte(), 0xae.toByte(), 0xf3.toByte(), 0x69.toByte(), 0xd9.toByte(), 0xea.toByte(),
        0x19.toByte(), 0xae.toByte(), 0x4d.toByte(), 0x0d.toByte(), 0xc9.toByte(), 0x70.toByte(), 0x20.toByte(), 0x4b.toByte(),
        0x5d.toByte(), 0x94.toByte(), 0xf6.toByte(), 0x84.toByte(), 0xd7.toByte(), 0xde.toByte(), 0x58.toByte(), 0xc2.toByte(),
        0x35.toByte(), 0xac.toByte(), 0xa4.toByte(), 0x60.toByte(), 0xfc.toByte(), 0x3d.toByte(), 0xd5.toByte(), 0xb4.toByte(),
        0xc8.toByte(), 0x46.toByte(), 0x76.toByte(), 0x15.toByte(), 0xc0.toByte(), 0xa7.toByte(), 0xe6.toByte(), 0xc0.toByte(),
        0x19.toByte(), 0xae.toByte(), 0x4d.toByte(), 0x0d.toByte(), 0xc9.toByte(), 0x70.toByte(), 0x20.toByte(), 0x4b.toByte(),
    )

val highSeed6421c0X1Source: ByteArray =
    byteArrayOf(
        0xf9.toByte(), 0xb8.toByte(), 0xa2.toByte(), 0x3b.toByte(), 0x79.toByte(), 0x89.toByte(), 0x3d.toByte(), 0xab.toByte(),
        0x28.toByte(), 0xf6.toByte(), 0x8f.toByte(), 0x89.toByte(), 0x3a.toByte(), 0x72.toByte(), 0x9b.toByte(), 0xfc.toByte(),
        0x43.toByte(), 0x32.toByte(), 0x3b.toByte(), 0x85.toByte(), 0x8f.toByte(), 0xcb.toByte(), 0xd6.toByte(), 0x95.toByte(),
        0xf4.toByte(), 0xd2.toByte(), 0x62.toByte(), 0x09.toByte(), 0x77.toByte(), 0x91.toByte(), 0x59.toByte(), 0xaf.toByte(),
        0xa1.toByte(), 0x03.toByte(), 0xdf.toByte(), 0xee.toByte(), 0x09.toByte(), 0x58.toByte(), 0xb8.toByte(), 0x3b.toByte(),
        0xb5.toByte(), 0x0a.toByte(), 0x88.toByte(), 0x9d.toByte(), 0x20.toByte(), 0x4a.toByte(), 0xad.toByte(), 0xbb.toByte(),
        0xa4.toByte(), 0x80.toByte(), 0x61.toByte(), 0x06.toByte(), 0xa7.toByte(), 0x57.toByte(), 0x0b.toByte(), 0xca.toByte(),
        0xf4.toByte(), 0x52.toByte(), 0x42.toByte(), 0xee.toByte(), 0x5f.toByte(), 0xa7.toByte(), 0xa2.toByte(), 0x7e.toByte(),
        0xac.toByte(), 0x8b.toByte(), 0xc6.toByte(), 0xb0.toByte(), 0x87.toByte(), 0xa3.toByte(), 0x03.toByte(), 0x84.toByte(),
        0xa3.toByte(), 0xc2.toByte(), 0xaf.toByte(), 0x99.toByte(), 0x20.toByte(), 0x4a.toByte(), 0xad.toByte(), 0xbb.toByte(),
        0xa4.toByte(), 0x80.toByte(), 0x61.toByte(), 0x06.toByte(), 0xa7.toByte(), 0x57.toByte(), 0x0b.toByte(), 0xca.toByte(),
    )

const val block66Size: Int = 66

const val df80InputBlockCount: Int = 4

const val df80WordSize: Int = 18

const val df80DerivedScheduleSize: Int = 864

const val df80InitialWorkspaceStride: Int = 72

const val df80InitialWorkspaceSize: Int = 288

const val df80ScheduleSize: Int = 1152

const val df80StateSize: Int = 8 * df80WordSize

const val finalizerDD7CPadOffset: Int = 0

const val finalizerZeroLowBlockOffset: Int = 3696

const val finalizerPad2Offset: Int = 3762

const val finalizerStaticBlockOffset: Int = 4752

const val expand67ed24AOffset: Int = 2160

const val expand67ed24BOffset: Int = 2928

const val init679f48Block66SrcOffset: Int = 0

const val raw67d630TableAOffset: Int = 210

const val raw67d630TableBOffset: Int = 978

val init679f48Block66DstOffsets: List<Int> =
    listOf(
        8, 74, 140, 206
    )

val init679f48Block18Specs: List<Spec679f48> = listOf(
        Spec679f48(0x120000058e3UL, 66, 276),
        Spec679f48(0x12000004b6bUL, 84, 294),
        Spec679f48(0x1200000388eUL, 102, 312),
        Spec679f48(0x12000000662UL, 120, 330),
        Spec679f48(0x12000000c90UL, 138, 348),
        Spec679f48(0x120000045f6UL, 156, 366),
        Spec679f48(0x12000000139UL, 174, 384),
        Spec679f48(0x12000002cb1UL, 192, 402)
    )

val aa8cInitialReducerSpecs: List<Spec679f48> = listOf(
        Spec679f48(0x400120000030e8UL, 276, 492),
        Spec679f48(0x40012000000006UL, 294, 496),
        Spec679f48(0x400120000022d5UL, 312, 500),
        Spec679f48(0x40012000001859UL, 330, 504),
        Spec679f48(0x40012000005e7aUL, 348, 508),
        Spec679f48(0x40012000004661UL, 366, 512),
        Spec679f48(0x4001200000178fUL, 384, 516),
        Spec679f48(0x40012000002c8fUL, 402, 520)
    )

val eb94UpdateMagics: List<ULong> =
    listOf(
        0x12000004154UL, 0x1200000392cUL, 0x120000036dbUL, 0x12000000f83UL,
        0x120000000f9UL, 0x12000000a72UL, 0x120000018d7UL, 0x1200000191dUL,
    )

const val scratch130Size: Int = 130

const val context679f48Size: Int = 524

const val builder6388f0WorkspaceSize: Int = 266

const val builder6388f0StageSize: Int = 282

const val builder6388f0LaneBlockCount: Int = 20

const val builder6388f0LaneBlockSize: Int = 16

const val builder6388f0LaneBlocksSize: Int = builder6388f0LaneBlockCount * builder6388f0LaneBlockSize

const val builder6388f0LaneTablePackedRowSize: Int = 9

const val builder6388f0LaneTableExpandedSize: Int = 18

const val builder6388f0LaneATableOffset: Int = 0

const val builder6388f0LaneBTableOffset: Int = 2304

const val builder6388f0LaneAInitOffset: Int = 4608

const val builder6388f0LaneBInitOffset: Int = 4626

const val builder6388f0LanePrimaryStaticOffset: Int = 4644

const val builder6388f0LaneSecondaryStaticOffset: Int = 4662

const val builder63c278VectorWords: Int = 22

const val builder63c278VectorBytes: Int = builder63c278VectorWords * 4

const val builder6388f0CallerStackBytes: Int = 20480

const val builder6473d0CallerStackPreimageBytes: Int = 3072

const val builder64cd40WorkspaceWords: Int = 44

const val builder64cd40WorkspaceBytes: Int = builder64cd40WorkspaceWords * 8

const val builder6421c0X0MulTable: Int = 1130248

const val builder6421c0X0AddTable: Int = 1127976

const val builder6421c0X0FoldTable: Int = 3140376

const val builder6421c0X1MulTable: Int = 1159816

const val builder6421c0X1AddTable: Int = 1147784

const val builder6421c0X1FoldTable: Int = 3140504

const val builder6421c0X2MulTable: Int = 1147816

const val builder6421c0X2AddTable: Int = 1159848

const val builder6421c0X2FoldTable: Int = 3140632

const val builder6421c0WorkspaceFoldTable: Int = 3140760

const val builder6421c0RewriteFold1Table: Int = 3140888

const val builder6421c0RewriteFold2Table: Int = 3141016

const val builder6421c0FinalFoldTable: Int = 3141144

const val builder6421c0FinalOutMulTable: Int = 1138248

const val builder6421c0FinalOutAddTable: Int = 1135368

const val builder6388f0HighSeedX0SourceMulTable: Int = 1178952

const val builder6388f0HighSeedX0SourceAddTable: Int = 1135336

const val builder6388f0Pre63Arg1MulTable: Int = 1157512

const val builder6388f0Pre63Arg1AddTable: Int = 1148104

const val builder6388f0Pre63Arg2MulTable: Int = 1123848

const val builder6388f0Pre63Arg2AddTable: Int = 1155080

const val builder6388f0Next642X1MulTable: Int = 1132872

const val builder6388f0Next642X1AddTable: Int = 1148136

const val builder6388f0StreamStartOut0To642f60X0MulTable: Int = 1178984

const val builder6388f0StreamStartOut0To642f60X0AddTable: Int = 1192872

const val builder6388f0StreamStartOut1To642f60X1MulTable: Int = 1135400

const val builder6388f0StreamStartOut1To642f60X1AddTable: Int = 1159880

const val builder6388f0SharedContextLength: Int = 1312

const val builder6388f0CallerLoopTableRows: Int = 59

const val builder6388f0CallerLoopRowBytes: Int = 88

const val builder6388f0CallerLoopTableBytes: Int = builder6388f0CallerLoopTableRows * builder6388f0CallerLoopRowBytes

const val builder6388f0CallerLoopInterleavedRowBytes: Int = builder6388f0CallerLoopRowBytes * 2

const val builder6388f0CallerLoopInterleavedLength: Int = builder6388f0CallerLoopTableRows * builder6388f0CallerLoopInterleavedRowBytes

const val builder6388f0CallerLoopTable1ContextOffset: Int = 1224

const val builder6388f0CallerLoopTable2ContextOffset: Int = 6416

const val builder6388f0CallerContextLength: Int = builder6388f0CallerLoopTable2ContextOffset + builder6388f0CallerLoopTableBytes

const val builder6388f0FirstPairStreamRows: Int = builder6388f0CallerLoopTableRows * 2

const val builder642f60SP2A8X1MulTable: Int = 1140744

const val builder642f60SP2A8X1AddTable: Int = 1130312

const val builder642f60SP2A8OutMulTable: Int = 1159912

const val builder642f60SP2A8OutAddTable: Int = 1172584

const val builder642f60SP2A8FoldTable: Int = 3141360

const val builder642f60SP300MulTable: Int = 1132552

const val builder642f60SP300AddTable: Int = 1125704

const val builder642f60SP1F8X0MulTable: Int = 1188200

const val builder642f60SP1F8X0AddTable: Int = 1143240

const val builder642f60SP1F8OutMulTable: Int = 1125736

const val builder642f60SP1F8OutAddTable: Int = 1135464

const val builder642f60SP1F8FoldTable: Int = 3142320

const val builder642f60SP250MulTable: Int = 1150504

const val builder642f60SP250AddTable: Int = 1160040

const val builder642f60SP148MulTable: Int = 1128040

const val builder642f60SP148AddTable: Int = 1130344

const val builder642f60SPF0MulTable: Int = 1140776

const val builder642f60SPF0AddTable: Int = 1192904

const val builder642f60SP1A0MulTable: Int = 1145544

const val builder642f60SP1A0AddTable: Int = 1181032

const val builder642f60FirstAMulTable: Int = 1159944

const val builder642f60FirstAAddTable: Int = 1183336

const val builder642f60FirstAFoldTable: Int = 3141424

const val builder642f60FirstBMulTable: Int = 1145480

const val builder642f60FirstBAddTable: Int = 1164040

const val builder642f60FirstBFoldTable: Int = 3141552

const val builder642f60SecondAMulTable: Int = 1135496

const val builder642f60SecondAAddTable: Int = 1135528

const val builder642f60SecondAFoldTable: Int = 3142384

const val builder642f60SecondBMulTable: Int = 1170504

const val builder642f60SecondBAddTable: Int = 1160008

const val builder642f60SecondBFoldTable: Int = 3142512

const val builder642f60ThirdAMulTable: Int = 1145512

const val builder642f60ThirdAAddTable: Int = 1157320

const val builder642f60ThirdAFoldTable: Int = 3142640

const val builder642f60ThirdBMulTable: Int = 1172616

const val builder642f60ThirdBAddTable: Int = 1172648

const val builder642f60ThirdBFoldTable: Int = 3142768

const val builder642f60FourthAMulTable: Int = 1123720

const val builder642f60FourthAAddTable: Int = 1195208

const val builder642f60FourthAFoldTable: Int = 3142896

const val builder642f60FourthBMulTable: Int = 1138280

const val builder642f60FourthBAddTable: Int = 1123752

const val builder642f60FourthBFoldTable: Int = 3143024

const val builder642f60MidAX0MulTable: Int = 1132584

const val builder642f60MidAX0AddTable: Int = 1150536

const val builder642f60MidAFoldTable: Int = 3143152

const val builder642f60MidSP40FoldTable: Int = 3143280

const val builder642f60MidSP40OutMulTable: Int = 1140808

const val builder642f60MidSP40OutAddTable: Int = 1195240

const val builder642f60MidContextMulTable: Int = 1147848

const val builder642f60MidContextAddTable: Int = 1143272

const val builder642f60MidContextFoldTable: Int = 3143408

const val builder642f60MidSPF0MulTable: Int = 1185832

const val builder642f60MidSPF0AddTable: Int = 1132616

const val builder642f60MidSPF0FoldTable: Int = 3143536

const val builder642f60MidSP40BMulTable: Int = 1185864

const val builder642f60MidSP40BAddTable: Int = 1128072

const val builder642f60MidSP40BFoldTable: Int = 3143664

const val builder642f60MidStaticSrcTable: Int = 3141272

const val builder642f60MidStaticMulTable: Int = 1160072

const val builder642f60MidStaticAddTable: Int = 1192936

const val builder642f60MidStaticFoldTable: Int = 3143792

const val builder642f60SixthAMulTable: Int = 1138312

const val builder642f60SixthAAddTable: Int = 1166664

const val builder642f60SixthAFoldTable: Int = 3143920

const val builder642f60SixthBMulTable: Int = 1195272

const val builder642f60SixthBAddTable: Int = 1188232

const val builder642f60SixthBFoldTable: Int = 3144048

const val builder642f60Out0StaticQ0: Int = 1201232

const val builder642f60Out0StaticQ1: Int = 1205568

const val builder642f60Out0StaticD1: Int = 1206528

const val builder642f60Out0SixthMulTable: Int = 1190376

const val builder642f60Out0ContextMulTable: Int = 1195304

const val builder642f60Out0SP250MulTable: Int = 1147880

const val builder642f60Out0FoldTable: Int = 3144176

const val builder642f60Out0OutMulTable: Int = 1168424

const val builder642f60Out0OutAddTable: Int = 1183368

const val builder642f60SeventhStaticQ0: Int = 1201024

const val builder642f60SeventhStaticQ1: Int = 1201856

const val builder642f60SeventhStaticD1: Int = 1207408

const val builder642f60SeventhSP250MulTable: Int = 1168456

const val builder642f60SeventhContextMulTable: Int = 1145576

const val builder642f60SeventhOut0MulTable: Int = 1166696

const val builder642f60SeventhSP148FoldTable: Int = 3144240

const val builder642f60SeventhSP148OutMulTable: Int = 1135560

const val builder642f60SeventhSP148OutAddTable: Int = 1170536

const val builder642f60SeventhAMulTable: Int = 1125768

const val builder642f60SeventhAAddTable: Int = 1135592

const val builder642f60SeventhAFoldTable: Int = 3144304

const val builder642f60SeventhBMulTable: Int = 1157352

const val builder642f60SeventhBAddTable: Int = 1128104

const val builder642f60SeventhBFoldTable: Int = 3144432

const val builder642f60SeventhSP9E0FoldTable: Int = 3144560

const val builder642f60SeventhSP9E0OutMulTable: Int = 1195336

const val builder642f60SeventhSP9E0OutAddTable: Int = 1195368

const val builder642f60SeventhSP300MulTable: Int = 1174440

const val builder642f60SeventhSP300AddTable: Int = 1172680

const val builder642f60SeventhSP300FoldTable: Int = 3144688

const val builder642f60SeventhSP7D0FoldTable: Int = 3144816

const val builder642f60SeventhSP7D0OutMulTable: Int = 1132648

const val builder642f60SeventhSP7D0OutAddTable: Int = 1190408

const val builder642f60SeventhSourceStaticTable: Int = 1147912

const val builder642f60SeventhSourceSP9E0MulTable: Int = 1176872

const val builder642f60SeventhSourceContext368MulTable: Int = 1183400

const val builder642f60SeventhSourceSP7D0MulTable: Int = 1192968

const val builder642f60SeventhSP40FoldTable: Int = 3144944

const val builder642f60SeventhSP40OutMulTable: Int = 1162088

const val builder642f60SeventhSP40OutAddTable: Int = 1152680

const val builder642f60SeventhWorkspaceMulTable: Int = 1195400

const val builder642f60SeventhWorkspaceAddTable: Int = 1130376

const val builder642f60SeventhWorkspaceFoldTable: Int = 3145008

const val builder642f60Out1MulTable: Int = 1174472

const val builder642f60Out1AddTable: Int = 1172712

const val builder642f60EighthAMulTable: Int = 1157384

const val builder642f60EighthAAddTable: Int = 1140840

const val builder642f60EighthAFoldTable: Int = 3145136

const val builder642f60EighthBMulTable: Int = 1138344

const val builder642f60EighthBAddTable: Int = 1150568

const val builder642f60EighthBFoldTable: Int = 3145264

const val builder642f60Out2MulTable: Int = 1154888

const val builder642f60Out2AddTable: Int = 1154920

const val builder64bd0cWorkspaceWords: Int = 44

const val builder64bd0cWorkspaceBytes: Int = builder64bd0cWorkspaceWords * 8

const val builder64c524WorkspaceWords: Int = 44

const val builder64c524WorkspaceBytes: Int = builder64c524WorkspaceWords * 8

const val builder64bd0cArg0MulTable: Int = 1164072

const val builder64bd0cArg0AddTable: Int = 1128008

const val builder64bd0cArg0FoldTable: Int = 3141680

const val builder64bd0cWorkspaceFold1Table: Int = 3141808

const val builder64bd0cRewriteFold1Table: Int = 3141936

const val builder64bd0cRewriteFold2Table: Int = 3142064

const val builder64bd0cFinalFoldTable: Int = 3142192

const val builder64bd0cFinalOutMulTable: Int = 1159976

const val builder64bd0cFinalOutAddTable: Int = 1135432

const val builder64c524Arg0MulTable: Int = 1150600

const val builder64c524Arg0AddTable: Int = 1147944

const val builder64c524Arg0FoldTable: Int = 3145648

const val builder64c524WorkspaceFold1Table: Int = 3145776

const val builder64c524RewriteFold1Table: Int = 3145904

const val builder64c524RewriteFold2Table: Int = 3146032

const val builder64c524FinalFoldTable: Int = 3146160

const val builder64c524FinalOutMulTable: Int = 1183432

const val builder64c524FinalOutAddTable: Int = 1160104

val builder6473d0FirstA0U32Mul: UInt = 2011050905U

val builder6473d0FirstA0U32Add: UInt = 1491268128U

const val builder6473d0FirstAMulTable: Int = 1185896

const val builder6473d0FirstAAddTable: Int = 1188264

val builder6473d0FirstAU32Mul: UInt = 1146471079U

val builder6473d0FirstAU32Add: UInt = 1605680314U

val builder6473d0FirstAFoldMul: ULong = 0x1a5dd9d49d616e49UL

val builder6473d0FirstAFoldAdd: ULong = 0x35ca2ed01cc6fc5eUL

const val builder6473d0FirstAFoldTable: Int = 3145392

val builder6473d0FirstAFoldedMul: ULong = 0xc0342d0500000000UL

val builder6473d0FirstALinearMul: ULong = 0x52a77526a5e20393UL

val builder6473d0FirstALinearAdd: ULong = 0xe18c4214f69259e3UL

val builder6473d0FirstB0U32Mul: UInt = 0x87742383U

val builder6473d0FirstB0U32Add: UInt = 0xc9c817bbU

const val builder6473d0FirstBMulTable: Int = 1181064

const val builder6473d0FirstBAddTable: Int = 1185928

val builder6473d0FirstBU32Mul: UInt = 279611131U

val builder6473d0FirstBU32Add: UInt = 0xc396dd66U

val builder6473d0FirstBFoldMul: ULong = 0xb113a5da27ca9531UL

val builder6473d0FirstBFoldAdd: ULong = 0x9ebadbc5621b3655UL

const val builder6473d0FirstBFoldTable: Int = 3145520

val builder6473d0FirstBFoldedMul: ULong = 0x5cb504100000000UL

val builder6473d0FirstBLinearMul: ULong = 0xc109dc395415ce8fUL

val builder6473d0FirstBLinearAdd: ULong = 0xf36dd79d8bc15312UL

val builder6473d0FirstConvBaseAdd: ULong = 0xea076efa672179abUL

val builder6473d0FirstConvCountMul: ULong = 0x901a2a1a1970b75fUL

val builder6473d0FirstConvProductMul: ULong = 0x4300ab4afadcbbf7UL

val builder6473d0FirstConvSumAMul: ULong = 0x7de2bd685f4f4709UL

val builder6473d0FirstConvSumBMul: ULong = 0xa38b46b3853abda1UL

val builder6473d0FirstConvFinalMul: ULong = 0xd77dcf630e2637dbUL

val builder6473d0FirstConvFinalAdd: ULong = 0x5c2daa5720071908UL

const val builder6473d0SP488MulTable: Int = 1176904

const val builder6473d0SP488AddTable: Int = 1183464

val builder6473d0SecondA0U32Mul: UInt = 961060307U

val builder6473d0SecondA0U32Add: UInt = 0x84fea303U

const val builder6473d0SecondAMulTable: Int = 1152712

const val builder6473d0SecondAAddTable: Int = 1132680

val builder6473d0SecondAU32Mul: UInt = 1437738203U

val builder6473d0SecondAU32Add: UInt = 1121678280U

val builder6473d0SecondAFoldMul: ULong = 0x9ab586ead7a10a23UL

val builder6473d0SecondAFoldAdd: ULong = 0x745539fbd1dd9f3dUL

const val builder6473d0SecondAFoldTable: Int = 3146288

val builder6473d0SecondAFoldedMul: ULong = 0xcf9ca88500000000UL

val builder6473d0SecondALinearMul: ULong = 0x8522c616d05ac3d1UL

val builder6473d0SecondALinearAdd: ULong = 0xf16d077d4bce4ca7UL

val builder6473d0SecondB0U32Mul: UInt = 0xce63ced3U

val builder6473d0SecondB0U32Add: UInt = 879045498U

const val builder6473d0SecondBMulTable: Int = 1160136

const val builder6473d0SecondBAddTable: Int = 1150632

val builder6473d0SecondBU32Mul: UInt = 0xed7fd02dU

val builder6473d0SecondBU32Add: UInt = 0xcaf62b51U

val builder6473d0SecondBFoldMul: ULong = 0x9345f0e11be71ab7UL

val builder6473d0SecondBFoldAdd: ULong = 0xc397557b35344783UL

const val builder6473d0SecondBFoldTable: Int = 3146416

val builder6473d0SecondBFoldedMul: ULong = 0x4e61310500000000UL

val builder6473d0SecondBLinearMul: ULong = 0xfa2624af5708736dUL

val builder6473d0SecondBLinearAdd: ULong = 0x8ace523ec06aa294UL

val builder6473d0SecondConvBaseAdd: ULong = 0x3a938197b6fc0c6dUL

val builder6473d0SecondConvCountMul: ULong = 0x6b6072f5b68541edUL

val builder6473d0SecondConvProductMul: ULong = 0x17273c8d47cdf1cdUL

val builder6473d0SecondConvSumAMul: ULong = 0xbe760fcc7bdf8909UL

val builder6473d0SecondConvSumBMul: ULong = 0xa87db0f0661d8c1UL

val builder6473d0SecondConvFinalMul: ULong = 0x4de8dcb2e8bedd8dUL

val builder6473d0SecondConvFinalAdd: ULong = 0x24bff8d093bcfa48UL

const val builder6473d0ThirdStaticQ0: Int = 1205056

const val builder6473d0ThirdStaticQ1: Int = 1201408

const val builder6473d0ThirdStaticD1: Int = 1207032

const val builder6473d0ThirdSecondMulTable: Int = 1138376

const val builder6473d0ThirdContext208MulTable: Int = 1174504

const val builder6473d0ThirdIn0MulTable: Int = 1152744

val builder6473d0ThirdSP430StateInit: UInt = 0xcf789378U

val builder6473d0ThirdSP430StateMul: UInt = 0x807fabb9U

val builder6473d0ThirdSP430FoldPreMul: UInt = 0xa5cb8299U

val builder6473d0ThirdSP430FoldPreAdd: UInt = 900703206U

val builder6473d0ThirdSP430SideMul: UInt = 0x87693409U

val builder6473d0ThirdSP430SideAdd: UInt = 0xd6091d59U

val builder6473d0ThirdSP430NextFolded7Mul: UInt = 1732545905U

val builder6473d0ThirdSP430NextFolded8Mul: UInt = 0x8bb728f0U

val builder6473d0ThirdSP430NextAdd: UInt = 1263971803U

const val builder6473d0ThirdSP430FoldTable: Int = 3146544

const val builder6473d0ThirdSP430OutMulTable: Int = 1125800

const val builder6473d0ThirdSP430OutAddTable: Int = 1195432

val builder6473d0ThirdA0U32Mul: UInt = 0xa0c3f4e7U

val builder6473d0ThirdA0U32Add: UInt = 0xca140f35U

const val builder6473d0ThirdAMulTable: Int = 1168488

const val builder6473d0ThirdAAddTable: Int = 1185960

val builder6473d0ThirdAU32Mul: UInt = 0xd85895b7U

val builder6473d0ThirdAU32Add: UInt = 0xfe4ad573U

val builder6473d0ThirdAFoldMul: ULong = 0x2d873af1d6d51fabUL

val builder6473d0ThirdAFoldAdd: ULong = 0xfa32d6ea7762f713UL

const val builder6473d0ThirdAFoldTable: Int = 3146608

val builder6473d0ThirdAFoldedMul: ULong = 0xd60956a700000000UL

val builder6473d0ThirdALinearMul: ULong = 0x7c054da63351e573UL

val builder6473d0ThirdALinearAdd: ULong = 0x8b3f83af9a986f82UL

val builder6473d0ThirdB0U32Mul: UInt = 793286089U

val builder6473d0ThirdB0U32Add: UInt = 1417900223U

const val builder6473d0ThirdBMulTable: Int = 1145608

const val builder6473d0ThirdBAddTable: Int = 1125832

val builder6473d0ThirdBU32Mul: UInt = 1595466307U

val builder6473d0ThirdBU32Add: UInt = 1612620675U

val builder6473d0ThirdBFoldMul: ULong = 0xccb971c8df4a10cbUL

val builder6473d0ThirdBFoldAdd: ULong = 0xbdc12d7c86b96bc9UL

const val builder6473d0ThirdBFoldTable: Int = 3146736

val builder6473d0ThirdBFoldedMul: ULong = 0x8970cf0900000000UL

val builder6473d0ThirdBLinearMul: ULong = 0x8ae83bc3470143ddUL

val builder6473d0ThirdBLinearAdd: ULong = 0x888ef8790921d76bUL

val builder6473d0ThirdConvBaseAdd: ULong = 0x917278c29688d77bUL

val builder6473d0ThirdConvCountMul: ULong = 0xc7906210d3d1e095UL

val builder6473d0ThirdConvProductMul: ULong = 0x4701e4eecdc47ad3UL

val builder6473d0ThirdConvSumAMul: ULong = 0xaa41cc7914d43151UL

val builder6473d0ThirdConvSumBMul: ULong = 0x4ed4ffc26ea9e41fUL

val builder6473d0ThirdConvFinalMul: ULong = 0xddb691aa5df690b3UL

val builder6473d0ThirdConvFinalAdd: ULong = 0xa100d744fa1a1050UL

val builder6473d0FourthA0U32Mul: UInt = 0x984ca5a3U

val builder6473d0FourthA0U32Add: UInt = 1001657965U

const val builder6473d0FourthAMulTable: Int = 1172744

const val builder6473d0FourthAAddTable: Int = 1170568

val builder6473d0FourthAU32Mul: UInt = 0xb2d6e3f9U

val builder6473d0FourthAU32Add: UInt = 0xfb7f4287U

val builder6473d0FourthAFoldMul: ULong = 0xd1ffa13328840637UL

val builder6473d0FourthAFoldAdd: ULong = 0x2262d6944a6e7f87UL

const val builder6473d0FourthAFoldTable: Int = 3146864

val builder6473d0FourthAFoldedMul: ULong = 0xac42268700000000UL

val builder6473d0FourthALinearMul: ULong = 0x814152cd7b468effUL

val builder6473d0FourthALinearAdd: ULong = 0xef9b4475a4d654daUL

val builder6473d0FourthB0U32Mul: UInt = 0xe701693fU

val builder6473d0FourthB0U32Add: UInt = 0xf6342272U

const val builder6473d0FourthBMulTable: Int = 1162120

const val builder6473d0FourthBAddTable: Int = 1162152

val builder6473d0FourthBU32Mul: UInt = 0xc6f77079U

val builder6473d0FourthBU32Add: UInt = 0xe9127329U

val builder6473d0FourthBFoldMul: ULong = 0xd34530d23ec1313bUL

val builder6473d0FourthBFoldAdd: ULong = 0xb004febcdc86f0edUL

const val builder6473d0FourthBFoldTable: Int = 3146992

val builder6473d0FourthBFoldedMul: ULong = 0xdc92270100000000UL

val builder6473d0FourthBLinearMul: ULong = 0xceecbc048b18d1c5UL

val builder6473d0FourthBLinearAdd: ULong = 0xc37fd29022b6d9f5UL

val builder6473d0FourthConvBaseAdd: ULong = 0xae9114e278083c51UL

val builder6473d0FourthConvCountMul: ULong = 0x4fe0792a5595a26cUL

val builder6473d0FourthConvProductMul: ULong = 0x803778f9280a64a7UL

val builder6473d0FourthConvSumAMul: ULong = 0xe524e08d7cfc960fUL

val builder6473d0FourthConvSumBMul: ULong = 0x21c7bf84b2e3e84cUL

val builder6473d0FourthConvFinalMul: ULong = 0x46c1c6f2d742d5b5UL

val builder6473d0FourthConvFinalAdd: ULong = 0x1f5c231ea84be10cUL

const val builder6473d0FifthStaticQ0: Int = 1199680

const val builder6473d0FifthStaticQ1: Int = 1203232

const val builder6473d0FifthStaticD1: Int = 1206136

const val builder6473d0FifthFourthMulTable: Int = 1132712

const val builder6473d0FifthContext158MulTable: Int = 1172776

const val builder6473d0FifthIn1MulTable: Int = 1132744

val builder6473d0FifthSP3D8StateInit: UInt = 1757863590U

val builder6473d0FifthSP3D8StateMul: UInt = 1044148329U

val builder6473d0FifthSP3D8FoldPreMul: UInt = 0xe2d8f8a1U

val builder6473d0FifthSP3D8FoldPreAdd: UInt = 0xcb837279U

val builder6473d0FifthSP3D8SideMul: UInt = 1061827771U

val builder6473d0FifthSP3D8SideFoldedMul: UInt = 1342177280U

val builder6473d0FifthSP3D8SideAdd: UInt = 0xd3af772cU

val builder6473d0FifthSP3D8NextFolded7Mul: UInt = 164512825U

val builder6473d0FifthSP3D8NextFolded8Mul: UInt = 1662762096U

val builder6473d0FifthSP3D8NextAdd: UInt = 1229568950U

const val builder6473d0FifthSP3D8FoldTable: Int = 3147120

const val builder6473d0FifthSP3D8OutMulTable: Int = 1195464

const val builder6473d0FifthSP3D8OutAddTable: Int = 1135624

val builder6473d0FifthA0U32Mul: UInt = 1928819871U

val builder6473d0FifthA0U32Add: UInt = 0xf4acd75fU

const val builder6473d0FifthAMulTable: Int = 1176936

const val builder6473d0FifthAAddTable: Int = 1179016

val builder6473d0FifthAU32Mul: UInt = 1231762711U

val builder6473d0FifthAU32Add: UInt = 0xcb6c6440U

val builder6473d0FifthAFoldMul: ULong = 0xed35bde10b7f0a6dUL

val builder6473d0FifthAFoldAdd: ULong = 0x6dc10d7393e30fd4UL

const val builder6473d0FifthAFoldTable: Int = 3147184

val builder6473d0FifthAFoldedMul: ULong = 0x2ff00d6100000000UL

val builder6473d0FifthALinearMul: ULong = 0x35078add682583b3UL

val builder6473d0FifthALinearAdd: ULong = 0x98856e9773f37314UL

val builder6473d0FifthB0U32Mul: UInt = 936791467U

val builder6473d0FifthB0U32Add: UInt = 0xc5e5e2fbU

const val builder6473d0FifthBMulTable: Int = 1132776

const val builder6473d0FifthBAddTable: Int = 1150664

val builder6473d0FifthBU32Mul: UInt = 0xbfc55b8fU

val builder6473d0FifthBU32Add: UInt = 305779741U

val builder6473d0FifthBFoldMul: ULong = 0xb32c7a78268d803fUL

val builder6473d0FifthBFoldAdd: ULong = 0x3751011be1daba47UL

const val builder6473d0FifthBFoldTable: Int = 3147312

val builder6473d0FifthBFoldedMul: ULong = 0x5c54cc7d00000000UL

val builder6473d0FifthBLinearMul: ULong = 0x769635acb20a2d3dUL

val builder6473d0FifthBLinearAdd: ULong = 0xd7e17822568f72b6UL

val builder6473d0FifthConvBaseAdd: ULong = 0x6bb3ed620e4062d1UL

val builder6473d0FifthConvCountMul: ULong = 0x5a7ef60c532941a8UL

val builder6473d0FifthConvProductMul: ULong = 0x6d7ef3b93c27bb1bUL

val builder6473d0FifthConvSumAMul: ULong = 0x4899963aacd79adeUL

val builder6473d0FifthConvSumBMul: ULong = 0xb234bdac100e1d64UL

val builder6473d0FifthConvFinalMul: ULong = 0x7c7297a28fd61e6bUL

val builder6473d0FifthConvFinalAdd: ULong = 0xc0f5768719fc1ff6UL

const val builder6473d0SixthSP380MulTable: Int = 1181096

const val builder6473d0SixthSP380AddTable: Int = 1154952

val builder6473d0SixthA0U32Mul: UInt = 0xaab2b12bU

val builder6473d0SixthA0U32Add: UInt = 1985468896U

const val builder6473d0SixthAMulTable: Int = 1125864

const val builder6473d0SixthAAddTable: Int = 1183496

val builder6473d0SixthAU32Mul: UInt = 0xe1c1f541U

val builder6473d0SixthAU32Add: UInt = 0xa44e1c99U

val builder6473d0SixthAFoldMul: ULong = 0x14ba61e7dca94a8bUL

val builder6473d0SixthAFoldAdd: ULong = 0x50b1b5306d47040bUL

const val builder6473d0SixthAFoldTable: Int = 3147568

val builder6473d0SixthAFoldedMul: ULong = 0xb5f6b68f00000000UL

val builder6473d0SixthALinearMul: ULong = 0xd41818fe79de8a5bUL

val builder6473d0SixthALinearAdd: ULong = 0xd151dec0329e6e93UL

val builder6473d0SixthB0U32Mul: UInt = 0x84509d71U

val builder6473d0SixthB0U32Add: UInt = 471773271U

const val builder6473d0SixthBMulTable: Int = 1130408

const val builder6473d0SixthBAddTable: Int = 1190440

val builder6473d0SixthBU32Mul: UInt = 0xeaf35335U

val builder6473d0SixthBU32Add: UInt = 178678339U

val builder6473d0SixthBFoldMul: ULong = 0x601f255bbc8594afUL

val builder6473d0SixthBFoldAdd: ULong = 0x3abe7da8622c20aaUL

const val builder6473d0SixthBFoldTable: Int = 3147440

val builder6473d0SixthBFoldedMul: ULong = 0xa22de34100000000UL

val builder6473d0SixthBLinearMul: ULong = 0x1a4af047ce7b1291UL

val builder6473d0SixthBLinearAdd: ULong = 0xfc749709a3c0a2f0UL

val builder6473d0SixthConvBaseAdd: ULong = 0x48627c9e905863c1UL

val builder6473d0SixthConvCountMul: ULong = 0x870c05f8b8020220UL

val builder6473d0SixthConvProductMul: ULong = 0x171538f14356cf37UL

val builder6473d0SixthConvSumAMul: ULong = 0x77d4a1b0a412afaUL

val builder6473d0SixthConvSumBMul: ULong = 0xe17b173ed4c11f30UL

val builder6473d0SixthConvFinalMul: ULong = 0xcfb213eb0fd9778fUL

val builder6473d0SixthConvFinalAdd: ULong = 0xad771cda5ed87b82UL

const val builder6473d0SeventhSP328MulTable: Int = 1140872

const val builder6473d0SeventhSP328AddTable: Int = 1138408

val builder6473d0SeventhSP750A0U32Mul: UInt = 0xcbc32467U

val builder6473d0SeventhSP750A0U32Add: UInt = 482085275U

const val builder6473d0SeventhSP750MulTable: Int = 1157416

const val builder6473d0SeventhSP750AddTable: Int = 1181128

val builder6473d0SeventhSP750U32Mul: UInt = 0xd5de3091U

val builder6473d0SeventhSP750U32Add: UInt = 0xc850f622U

val builder6473d0SeventhSP750FoldMul: ULong = 0x29ccd0b4b66bf69bUL

val builder6473d0SeventhSP750FoldAdd: ULong = 0x92b5c9aad779cd01UL

const val builder6473d0SeventhSP750FoldTable: Int = 3147696

val builder6473d0SeventhSP750FoldedMul: ULong = 0x69bd621900000000UL

val builder6473d0SeventhSP750LinearMul: ULong = 0xf8eecd0e379e94ddUL

val builder6473d0SeventhSP750LinearAdd: ULong = 0x42ed620b1951ea95UL

val builder6473d0SeventhSP698A0U32Mul: UInt = 0xa331630fU

val builder6473d0SeventhSP698A0U32Add: UInt = 0x9bfc072bU

const val builder6473d0SeventhSP698MulTable: Int = 1188296

const val builder6473d0SeventhSP698AddTable: Int = 1128136

val builder6473d0SeventhSP698U32Mul: UInt = 1550489327U

val builder6473d0SeventhSP698U32Add: UInt = 2076052768U

val builder6473d0SeventhSP698FoldMul: ULong = 0xf9465160d0a8253UL

val builder6473d0SeventhSP698FoldAdd: ULong = 0xf56e8b822124be55UL

const val builder6473d0SeventhSP698FoldTable: Int = 3147824

val builder6473d0SeventhSP698FoldedMul: ULong = 0x5f21e5dd00000000UL

val builder6473d0SeventhSP698LinearMul: ULong = 0x889f295bda63f59UL

val builder6473d0SeventhSP698LinearAdd: ULong = 0x827f2e3ed1537188UL

val builder6473d0SeventhConvBaseAdd: ULong = 0x4f71889106fdcf1aUL

val builder6473d0SeventhConvCountMul: ULong = 0xc645bd197ffec780UL

val builder6473d0SeventhConvProductMul: ULong = 0x902bc054731b0e19UL

val builder6473d0SeventhConvSumAMul: ULong = 0x6ee76be95cd58d16UL

val builder6473d0SeventhConvSumBMul: ULong = 0x2975699057393140UL

val builder6473d0SeventhConvFinalMul: ULong = 0xfabebc0148fce955UL

val builder6473d0SeventhConvFinalAdd: ULong = 0xd9e959c072d37dafUL

const val builder6473d0EighthSP2D0MulTable: Int = 1190472

const val builder6473d0EighthSP2D0AddTable: Int = 1143304

val builder6473d0EighthA0U32Mul: UInt = 113982201U

val builder6473d0EighthA0U32Add: UInt = 901447977U

const val builder6473d0EighthAMulTable: Int = 1147976

const val builder6473d0EighthAAddTable: Int = 1132808

val builder6473d0EighthAU32Mul: UInt = 0xac8612f9U

val builder6473d0EighthAU32Add: UInt = 0x93df82d0U

val builder6473d0EighthAFoldMul: ULong = 0xea864d2bbd71e693UL

val builder6473d0EighthAFoldAdd: ULong = 0xbd72f04e9eb16893UL

const val builder6473d0EighthAFoldTable: Int = 3147952

val builder6473d0EighthAFoldedMul: ULong = 0x89c4bba700000000UL

val builder6473d0EighthALinearMul: ULong = 0xfc50406703b9351bUL

val builder6473d0EighthALinearAdd: ULong = 0xdc2e9343b6f35309UL

val builder6473d0EighthB0U32Mul: UInt = 1951215447U

val builder6473d0EighthB0U32Add: UInt = 1421765957U

const val builder6473d0EighthBMulTable: Int = 1128168

const val builder6473d0EighthBAddTable: Int = 1195496

val builder6473d0EighthBU32Mul: UInt = 0xed2a4af5U

val builder6473d0EighthBU32Add: UInt = 1901714902U

val builder6473d0EighthBFoldMul: ULong = 0xf5a291e49092493dUL

val builder6473d0EighthBFoldAdd: ULong = 0x317bb3a8544de11fUL

const val builder6473d0EighthBFoldTable: Int = 3148080

val builder6473d0EighthBFoldedMul: ULong = 0x33652f4500000000UL

val builder6473d0EighthBLinearMul: ULong = 0x4de23df1210f0f8fUL

val builder6473d0EighthBLinearAdd: ULong = 0xc716615aa5d2b4a0UL

val builder6473d0EighthConvBaseAdd: ULong = 0x6b3d1044d1e0bae4UL

val builder6473d0EighthConvCountMul: ULong = 0x1cfde242e61ced64UL

val builder6473d0EighthConvProductMul: ULong = 0x9c4b2fa9d6a82bcbUL

val builder6473d0EighthConvSumAMul: ULong = 0x397a076065abdda4UL

val builder6473d0EighthConvSumBMul: ULong = 0xd82dc30e2ff5c69bUL

val builder6473d0EighthConvFinalMul: ULong = 0xfc34a2ab410fb0abUL

val builder6473d0EighthConvFinalAdd: ULong = 0x750634486b3a5505UL

const val builder6473d0Ninth1StaticQ0: Int = 1205584

const val builder6473d0Ninth1StaticQ1: Int = 1199920

const val builder6473d0Ninth1StaticD1: Int = 1207344

const val builder6473d0Ninth1EighthMulTable: Int = 1168520

const val builder6473d0Ninth1Context208MulTable: Int = 1130440

const val builder6473d0Ninth1SP328MulTable: Int = 1125896

const val builder6473d0Ninth1SP2D0MulTable: Int = 1145640

val builder6473d0Ninth1Out3StateInit: UInt = 1816277828U

val builder6473d0Ninth1Out3StateMul: UInt = 1602109975U

val builder6473d0Ninth1Out3FoldPreMul: UInt = 2063683605U

val builder6473d0Ninth1Out3FoldPreAdd: UInt = 1601075918U

val builder6473d0Ninth1Out3SideMul: UInt = 0xb4d76dfbU

val builder6473d0Ninth1Out3SideFoldedMul: UInt = 268435456U

val builder6473d0Ninth1Out3SideAdd: UInt = 0x9eeaec8fU

val builder6473d0Ninth1Out3NextFolded7Mul: UInt = 0x9b8c81cbU

val builder6473d0Ninth1Out3NextFolded8Mul: UInt = 1194845008U

val builder6473d0Ninth1Out3NextAdd: UInt = 0xd38a5bb7U

const val builder6473d0Ninth1Out3FoldTable: Int = 3148208

const val builder6473d0Ninth1Out3OutMulTable: Int = 1154984

const val builder6473d0Ninth1Out3OutAddTable: Int = 1174536

const val builder6473d0Ninth2StaticQ0: Int = 1199936

const val builder6473d0Ninth2StaticQ1: Int = 1205776

const val builder6473d0Ninth2StaticD1: Int = 1207568

const val builder6473d0Ninth2SP2D0MulTable: Int = 1130472

const val builder6473d0Ninth2Context260MulTable: Int = 1143336

const val builder6473d0Ninth2Out2MulTable: Int = 1160168

val builder6473d0Ninth2SP278StateInit: UInt = 0x8d141c95U

val builder6473d0Ninth2SP278StateMul: UInt = 0xb65c329fU

val builder6473d0Ninth2SP278FoldPreMul: UInt = 1981252417U

val builder6473d0Ninth2SP278FoldPreAdd: UInt = 0xda083f12U

val builder6473d0Ninth2SP278SideMul: UInt = 2108069159U

val builder6473d0Ninth2SP278SideFoldedMul: UInt = 0x90000000U

val builder6473d0Ninth2SP278SideAdd: UInt = 32924141U

val builder6473d0Ninth2SP278NextFolded7Mul: UInt = 1329259679U

val builder6473d0Ninth2SP278NextFolded8Mul: UInt = 206681616U

val builder6473d0Ninth2SP278NextAdd: UInt = 0xc23b392aU

const val builder6473d0Ninth2SP278FoldTable: Int = 3148272

const val builder6473d0Ninth2SP278OutMulTable: Int = 1123784

const val builder6473d0Ninth2SP278OutAddTable: Int = 1148008

const val builder6473d0NinthSP3D8AMulTable: Int = 1155016

const val builder6473d0NinthSP3D8AAddTable: Int = 1157448

val builder6473d0NinthSP3D8AU32Mul: UInt = 1060833205U

val builder6473d0NinthSP3D8AU32Add: UInt = 0xa76f4d34U

val builder6473d0NinthSP3D8AFoldMul: ULong = 0x42a5684e40c837e5UL

val builder6473d0NinthSP3D8AFoldAdd: ULong = 0x8187dd38d660ab02UL

const val builder6473d0NinthSP3D8AFoldTable: Int = 3148336

val builder6473d0NinthSP3D8AFoldedMul: ULong = 0xc387f23b00000000UL

val builder6473d0NinthSP3D8ALinearMul: ULong = 0x41edfb94e441a439UL

val builder6473d0NinthSP3D8ALinearAdd: ULong = 0x8630a437323b9a06UL

const val builder6473d0NinthSP278BMulTable: Int = 1130504

const val builder6473d0NinthSP278BAddTable: Int = 1135656

val builder6473d0NinthSP278BU32Mul: UInt = 1002453921U

val builder6473d0NinthSP278BU32Add: UInt = 1428032280U

val builder6473d0NinthSP278BFoldMul: ULong = 0x4a4ebd73a2ac7ecfUL

val builder6473d0NinthSP278BFoldAdd: ULong = 0xc26c352e790eb9d2UL

const val builder6473d0NinthSP278BFoldTable: Int = 3148464

val builder6473d0NinthSP278BFoldedMul: ULong = 0x325b382b00000000UL

val builder6473d0NinthSP278BLinearMul: ULong = 0x3b1bfc266fb46b3bUL

val builder6473d0NinthSP278BLinearAdd: ULong = 0xc26ae547d56e3752UL

val builder6473d0NinthConv1StateInit: ULong = 0x44890e57fee2b127UL

val builder6473d0NinthConv1CountMul: ULong = 0x22e7e2dd39986f0cUL

val builder6473d0NinthConv1SumAMul: ULong = 0xefcd57be2939dd8eUL

val builder6473d0NinthConv1ProductMul: ULong = 0x3579615d7809d6f9UL

val builder6473d0NinthConv1SumBMul: ULong = 0xfb335b5dda0d71faUL

val builder6473d0NinthConv1FoldPreMul: ULong = 0xa56af869b8f14f9fUL

val builder6473d0NinthConv1FoldPreAdd: ULong = 0x4dce076dc16d62a6UL

const val builder6473d0NinthConv1FoldTable: Int = 3148592

val builder6473d0NinthConv1SideMul: UInt = 1543873039U

val builder6473d0NinthConv1SideFoldedMul: UInt = 0xf0000000U

val builder6473d0NinthConv1SideAdd: UInt = 1121227003U

val builder6473d0NinthConv1NextFolded8Mul: ULong = 0x3422cdbd16480c5fUL

val builder6473d0NinthConv1NextFolded16Mul: ULong = 0x9b7f3a1000000000UL

val builder6473d0NinthConv1NextAdd: ULong = 0x5788d57815189c66UL

const val builder6473d0NinthConv1OutMulTable: Int = 1172808

const val builder6473d0NinthConv1OutAddTable: Int = 1155048

const val builder6473d0NinthIn1AMulTable: Int = 1138440

const val builder6473d0NinthIn1AAddTable: Int = 1148040

val builder6473d0NinthIn1AU32Mul: UInt = 0xa23b4591U

val builder6473d0NinthIn1AU32Add: UInt = 666171797U

val builder6473d0NinthIn1AFoldMul: ULong = 0xe3d571fe10b7dd6fUL

val builder6473d0NinthIn1AFoldAdd: ULong = 0xead98380f165a1bUL

const val builder6473d0NinthIn1AFoldTable: Int = 3148720

val builder6473d0NinthIn1AFoldedMul: ULong = 0x24883b3100000000UL

val builder6473d0NinthIn1ALinearMul: ULong = 0xb919418f2dce08c1UL

val builder6473d0NinthIn1ALinearAdd: ULong = 0xc178a260ab561c2dUL

const val builder6473d0NinthSP328BMulTable: Int = 1164104

const val builder6473d0NinthSP328BAddTable: Int = 1172840

val builder6473d0NinthSP328BU32Mul: UInt = 0xc7e7ff39U

val builder6473d0NinthSP328BU32Add: UInt = 1860083040U

val builder6473d0NinthSP328BFoldMul: ULong = 0xb7f69af6de331b05UL

val builder6473d0NinthSP328BFoldAdd: ULong = 0xfb07ecba9e561d89UL

const val builder6473d0NinthSP328BFoldTable: Int = 3148848

val builder6473d0NinthSP328BFoldedMul: ULong = 0x9c0181bb00000000UL

val builder6473d0NinthSP328BLinearMul: ULong = 0xc2cb60ecc908be59UL

val builder6473d0NinthSP328BLinearAdd: ULong = 0xf63168d72b49d4d5UL

val builder6473d0NinthConv2StateInit: ULong = 0xc46e4bf2bc94905fUL

val builder6473d0NinthConv2CountMul: ULong = 0xb43becaf979eccdbUL

val builder6473d0NinthConv2SumAMul: ULong = 0xea769816201b6855UL

val builder6473d0NinthConv2ProductMul: ULong = 0x286e00ddaf23867UL

val builder6473d0NinthConv2SumBMul: ULong = 0xa5fd2d47569ca4a9UL

val builder6473d0NinthConv2FoldPreMul: ULong = 0x131a43f6b293c469UL

val builder6473d0NinthConv2FoldPreAdd: ULong = 0xae11e8f737712446UL

const val builder6473d0NinthConv2FoldTable: Int = 3148976

val builder6473d0NinthConv2SideMul: UInt = 0xe9b78f65U

val builder6473d0NinthConv2SideFoldedMul: UInt = 805306368U

val builder6473d0NinthConv2SideAdd: UInt = 1025048669U

val builder6473d0NinthConv2NextFolded8Mul: ULong = 0xd206f236cb1e0bd9UL

val builder6473d0NinthConv2NextFolded16Mul: ULong = 0x4e1f427000000000UL

val builder6473d0NinthConv2NextAdd: ULong = 0x1008f89662553eaaUL

const val builder6473d0NinthConv2OutMulTable: Int = 1168552

const val builder6473d0NinthConv2OutAddTable: Int = 1130536

const val builder6473d0Ninth3StaticTable: Int = 1130568

const val builder6473d0Ninth3SP1C8MulTable: Int = 1145672

const val builder6473d0Ninth3Context2B8MulTable: Int = 1148072

const val builder6473d0Ninth3SP118MulTable: Int = 1140904

val builder6473d0Ninth3SP68StateInit: UInt = 0xa2d2a851U

val builder6473d0Ninth3SP68StateMul: UInt = 0x9b30705fU

val builder6473d0Ninth3SP68FoldPreMul: UInt = 1824438573U

val builder6473d0Ninth3SP68FoldPreAdd: UInt = 0x82af87fcU

val builder6473d0Ninth3SP68SideMul: UInt = 1917332015U

val builder6473d0Ninth3SP68SideFoldedMul: UInt = 1342177280U

val builder6473d0Ninth3SP68SideAdd: UInt = 0xc925d16aU

val builder6473d0Ninth3SP68NextFolded7Mul: UInt = 492347259U

val builder6473d0Ninth3SP68NextFolded8Mul: UInt = 712378448U

val builder6473d0Ninth3SP68NextAdd: UInt = 0xa10a4f8bU

const val builder6473d0Ninth3SP68FoldTable: Int = 3149104

const val builder6473d0Ninth3SP68OutMulTable: Int = 1150696

const val builder6473d0Ninth3SP68OutAddTable: Int = 1164136

const val builder6473d0NinthWorkspaceMulTable: Int = 1143368

const val builder6473d0NinthWorkspaceAddTable: Int = 1140936

val builder6473d0NinthWorkspaceU32Mul: UInt = 0xddfc952fU

val builder6473d0NinthWorkspaceU32Add: UInt = 0xfa88584bU

val builder6473d0NinthWorkspaceFoldMul: ULong = 0x4e5c7da178b9e563UL

val builder6473d0NinthWorkspaceFoldAdd: ULong = 0xd8a71f348e783f37UL

const val builder6473d0NinthWorkspaceFoldTable: Int = 3149168

val builder6473d0NinthWorkspaceFoldedMul: ULong = 0x27c0cb3900000000UL

val builder6473d0NinthWorkspaceLinearMul: ULong = 0x81df0a6c96766bf5UL

val builder6473d0NinthWorkspaceLinearAdd: ULong = 0xc97918a2a3eb7f6bUL

const val builder6473d0TenthOut3MulTable: Int = 1162184

const val builder6473d0TenthOut3AddTable: Int = 1135688

const val builder6473d0TenthAMulTable: Int = 1157480

const val builder6473d0TenthAAddTable: Int = 1164168

val builder6473d0TenthA0U32Mul: UInt = 1283609375U

val builder6473d0TenthA0U32Add: UInt = 0x9d6c5712U

val builder6473d0TenthAU32Mul: UInt = 2103955815U

val builder6473d0TenthAU32Add: UInt = 0x9fe36605U

val builder6473d0TenthAFoldMul: ULong = 0xdaea1b52b6016691UL

val builder6473d0TenthAFoldAdd: ULong = 0x3b80308f53669e2eUL

const val builder6473d0TenthAFoldTable: Int = 3149296

val builder6473d0TenthAFoldedMul: ULong = 0x418bbf9900000000UL

val builder6473d0TenthALinearMul: ULong = 0x4b523055abe88457UL

val builder6473d0TenthALinearAdd: ULong = 0x70cd301dacb08338UL

const val builder6473d0TenthBMulTable: Int = 1170600

const val builder6473d0TenthBAddTable: Int = 1132840

val builder6473d0TenthB0U32Mul: UInt = 0xfcd41a2bU

val builder6473d0TenthB0U32Add: UInt = 0xf953e1f8U

val builder6473d0TenthBU32Mul: UInt = 0xee73b901U

val builder6473d0TenthBU32Add: UInt = 0xce0a7777U

val builder6473d0TenthBFoldMul: ULong = 0xa7fa1537bf414305UL

val builder6473d0TenthBFoldAdd: ULong = 0x7a282e2032d7105UL

const val builder6473d0TenthBFoldTable: Int = 3149424

val builder6473d0TenthBFoldedMul: ULong = 0xa8b5cc4500000000UL

val builder6473d0TenthBLinearMul: ULong = 0x4ebe8304c777f3a7UL

val builder6473d0TenthBLinearAdd: ULong = 0xe1237f2df16faf59UL

val builder6473d0TenthConvBaseAdd: ULong = 0x4f04381a3a25c55fUL

val builder6473d0TenthConvCountMul: ULong = 0xeda7ceed201a4f20UL

val builder6473d0TenthConvProductMul: ULong = 0x5f94dd0a9ca697b5UL

val builder6473d0TenthConvSumAMul: ULong = 0xacff7aae673c319cUL

val builder6473d0TenthConvSumBMul: ULong = 0x11489ebd7e12a758UL

val builder6473d0TenthConvFinalMul: ULong = 0xf234ac9ea555f56fUL

val builder6473d0TenthConvFinalAdd: ULong = 0x4f16240dd4606c20UL

const val builder6473d0FinalOut4MulTable: Int = 1160200

const val builder6473d0FinalOut4AddTable: Int = 1170632

const val table63c278U32Base: Int = 1123720

const val table63c278FoldBase: Int = 3140376

const val table633fa8TailFoldBase: Int = 3139480

const val table633fa8TailU32LowBase: Int = 1123624

const val builder633fa8NullTableBase: Int = 3133937

const val builder633fa8NullNibbleTableBase: Int = 3160596

const val prog64e2b8Length: Int = 592

const val prog638840Length: Int = 33280

const val lowSeedStatics6388f0Length: Int = 798

const val lowLoopStatics6388f0Length: Int = 404

const val laneTables6388f0Length: Int = 4680

const val selectorTables6388f0Length: Int = 32

const val u32Tables63c278Length: Int = 83856

const val foldTables63c278Length: Int = 15200

const val tailFoldTables633fa8Length: Int = 896

const val tailU32LowTables633fa8Length: Int = 64

const val nullTables633fa8Length: Int = 5135

const val nullNibble633fa8Length: Int = 64

const val process2P5PublicTableBase: Int = 3160256

const val process2P5PublicTableLength: Int = 1304

const val prog67cc18Length: Int = 24832

const val ttableBExtLength: Int = 1048576

const val finalLenTablesLength: Int = 1536

const val df80RoundTablesLength: Int = 1170

const val finalizerTablesLength: Int = 4818

const val seedTables679f48Length: Int = 1746

const val reducer67ea28NibbleLength: Int = 64

const val prog67076cLength: Int = 132

const val vm64e17cProgOffset: Int = 462

const val vm64e17cProgLength: Int = 126

const val builder6388f0LowSeedBlockBytes: Int = 266

const val builder6388f0LowSeedEntrySourceBytes: Int = 532

val builder6388f0LowSeedEntryS898Magic: ULong = 0x10a000001764UL

val builder6388f0LowSeedEntryS78EMagic: ULong = 0x10a000000fe9UL

val builder6388f0LowSeedPrev2BD0Magic: ULong = 0x10a000006abdUL

val builder6388f0LowSeedPrev2S684Magic: ULong = 0x10a000005fdbUL

val builder6388f0LowSeedPrevS684Magic: ULong = 0x10a0000004c6UL

val builder6388f0LowSeedMiddleMagic: ULong = 0x10a00000141cUL

val builder6388f0LowSeedPreBD0Magic: ULong = 0x10a000003360UL

val builder6388f0LowSeedTailBD0Magic: ULong = 0x10a00000505eUL

val builder6388f0LowSeedTailLeftMagic: ULong = 0x10a0000003bcUL

val builder6388f0LowSeedTailRightMagic: ULong = 0x10a000006bc7UL

val builder6388f0LowSeedTailS684Magic: ULong = 0x10a000004a08UL

val builder6388f0LowSeedTailStageMagic: ULong = 0x10a000007d1fUL

val builder6388f0LowSeedPreludeStageMagic: ULong = 0x10a0000018b4UL

val builder6388f0LowSeedPreludeSourceMagic: ULong = 0x10a000000c72UL

val builder6388f0LowSeedPreludeMagic: ULong = 0x810a000003acbUL

val builder6388f0LowSeedCF0Phase1SeedMagic: ULong = 0xc107000c03d57UL

val builder6388f0LowSeedCF0Phase2SeedMagic: ULong = 0xc107000c05886UL

val builder6388f0LowSeedCF0Phase3SeedMagic: ULong = 0xc107000c036a0UL

const val builder6388f0LowSeedStaticBase: Int = 3099944

const val builder6388f0LowSeedPrev2Static: Int = 3099944

const val builder6388f0LowSeedPreStatic: Int = 3100210

const val builder6388f0LowSeedTailStatic: Int = 3100476

val builder6388f0LowSeedE10SourceShifts: List<Int> =
    listOf(
        4, 8, 16, 32, 64, 128, 256
    )

const val builder6388f0LowSeedExpandedPreludeBytes: Int = 268

const val builder6388f0LowSeedSeedBlocksBytes: Int = 20 * 0x10

val builder6388f0Row0SeedBlockMagic: ULong = 0x10000005fcbUL

const val builder6388f0LowLoopStaticBase: Int = 3139072

const val builder6388f0LowLoopStaticETable: Int = 3139072

const val builder6388f0LowLoopStaticDTable: Int = 3139216

const val builder6388f0LowSeedStaticBlockOffset: Int = 3139360

const val builder6388f0LowLoopStaticCTable: Int = 3139376

const val builder6388f0LowLoopStaticATable: Int = 3139394

const val builder6388f0LowLoopNibbleTable: Int = 3139412

const val builder6388f0LowLoopLaneBytes: Int = 18

val builder6388f0LowLoopEInitMagic: ULong = 0x12000006ef9UL

val builder6388f0LowLoopDInitMagic: ULong = 0x801000000654dUL

val builder6388f0LowLoopBInitMagic: ULong = 0x12000007d0dUL

val builder6388f0LowLoopFMagic: ULong = 0x12000000398UL

val builder6388f0LowLoopAMagic: ULong = 0x12000006ddbUL

val builder6388f0LowLoopTMagic: ULong = 0x12000001752UL

val builder6388f0LowLoopBMixMagic: ULong = 0x12000002dd3UL

val builder6388f0LowLoopEAdvanceMagic: ULong = 0x12000007ae7UL

val builder6388f0LowLoopCAdvanceMagic: ULong = 0x12000006897UL

val builder6388f0LowLoopPostFMagic: ULong = 0x12000003241UL

val builder6388f0LowLoopPostDMagic: ULong = 0x120000045a8UL

val builder6388f0LowLoopPostEMagic: ULong = 0xc000f000c01bfaUL

val builder6388f0LowLoopPackCMagic: ULong = 0x4000004a04UL

val builder6388f0LowLoopPackEMagic: ULong = 0x8010000800350UL

val builder6388f0LowLoopPackBMagic: ULong = 0x4000002271UL

const val builder633fa8ScalarWordCount: Int = 20

const val builder633fa8ScalarWindowBytes: Int = 70

const val builder633fa8ScalarPackMulTable: Int = 1185800

const val builder633fa8ScalarPackAddTable: Int = 1176840

val builder633fa8ScalarPackMul: UInt = 935380313U

val builder633fa8ScalarPackAdd: UInt = 0xfa73673bU

const val builder633fa8E10TailFoldTable: Int = 3140248

const val builder633fa8E10TailMulTable: Int = 1150472

const val builder633fa8E10TailAddTable: Int = 1190344

val builder633fa8E10InitialCarry: ULong = 0x7f9e71176c43f336UL

val builder633fa8E10QwordMul: ULong = 0xa44fd620a45fddc7UL

val builder633fa8E10CarryMul: ULong = 0x8b3babe0304f96f9UL

val builder633fa8E10CarryAdd: ULong = 0x12e00771bb9547afUL

val builder633fa8E10FoldSeedMul: ULong = 0x39ae28b51354965UL

val builder633fa8E10FoldSeedAdd: ULong = 0x248e5dc60fc0f4fbUL

val builder633fa8E10WordMul: UInt = 1241615419U

val builder633fa8E10WordAdd: UInt = 2044219163U

val builder633fa8E10NextCarryFolded7Mul: ULong = 0xd310088be2b9ce15UL

val builder633fa8E10NextCarryFolded16Mul: ULong = 0xd4631eb000000000UL

val builder633fa8E10NextCarryAdd: ULong = 0x2f149c1c6520051UL

const val builder633fa8TailStackBytes: Int = 17152

const val builder633fa8TailAFoldTable: Int = 3139480

const val builder633fa8TailBFoldTable: Int = 3139608

const val builder633fa8TailCFoldTable: Int = 3139736

const val builder633fa8TailDFoldTable: Int = 3139864

const val builder633fa8TailEFoldTable: Int = 3139992

const val builder633fa8TailFFoldTable: Int = 3140120

val builder633fa8InvariantWords2dfc: List<UInt> =
    listOf(
        0x9bed19fdU, 0xc70a4d0fU, 0x8257d22bU, 0xe2fafcb3U,
        46628128U, 0xb5ed0efaU, 0x878c1b06U, 1272524157U,
        566662223U, 0xd3ec5d2fU, 0x876fda86U, 938730026U,
        1023203278U, 0xabdc16ebU, 0x84ad2f7dU, 1272524157U,
        0xf647adceU, 0xaa7b701eU, 0x876fda86U, 938730026U,
    )

val builder633fa8InvariantSeed3110: ULong = 0xb6ccf02833a9825eUL

val builder633fa8InvariantWords3120: List<UInt> =
    listOf(
        0xb33842d7U, 2070652804U, 0xa2f90f36U, 0xde5e2ad7U,
        1010120617U, 0x81d564f6U, 865776802U, 0x999de03bU,
        1455504194U, 0xff14a487U, 1513186316U, 0xc3f85236U,
        1008584606U, 1487459494U, 1411166222U, 1664237517U,
        447040022U, 32618081U, 1513186316U, 0xc3f85236U,
    )

const val builder633fa8NullEntropyBytes: Int = 282

val builder633fa8NullInitialAMagic: ULong = 0x11a000000236UL

val builder633fa8NullInitialBMagic: ULong = 0x11a0000047e0UL

val builder633fa8NullSeedBlockMagic: ULong = 0x1000000725dUL

const val builder633fa8NullSeedBlockBytes: Int = 16

const val builder633fa8NullSeedBlockStride: Int = 14

const val builder633fa8NullSeedBlocksBytes: Int = 20 * builder633fa8NullSeedBlockBytes

const val builder633fa8NullLoopStaticETable: Int = 3133937

const val builder633fa8NullLoopStaticDTable: Int = 3134081

const val builder633fa8NullLoopStaticCTable: Int = 3134225

const val builder633fa8NullLoopStaticATable: Int = 3134243

const val builder633fa8NullLoopNibbleTable: Int = 3160596

const val builder633fa8NullLoopLaneBytes: Int = 18

val builder633fa8NullLoopEInitMagic: ULong = 0x12000000376UL

val builder633fa8NullLoopDInitMagic: ULong = 0x8010000002447UL

val builder633fa8NullLoopBInitMagic: ULong = 0x120000010f3UL

val builder633fa8NullLoopFMagic: ULong = 0x12000004596UL

val builder633fa8NullLoopAMagic: ULong = 0x12000007141UL

val builder633fa8NullLoopTMagic: ULong = 0x12000008199UL

val builder633fa8NullLoopBMixMagic: ULong = 0x1200000726dUL

val builder633fa8NullLoopEAdvanceMagic: ULong = 0x12000000eabUL

val builder633fa8NullLoopCAdvanceMagic: ULong = 0x12000005026UL

val builder633fa8NullLoopPostFMagic: ULong = 0x12000003e64UL

val builder633fa8NullLoopPostDMagic: ULong = 0x12000001be8UL

val builder633fa8NullLoopPostEMagic: ULong = 0xc00f000c079c5UL

val builder633fa8NullLoopPackCMagic: ULong = 0x4000000a28UL

val builder633fa8NullLoopPackEMagic: ULong = 0x8010000806883UL

val builder633fa8NullLoopPackBMagic: ULong = 0x400000186eUL

const val builder633fa8NullCheck1ScheduleMulTable: Int = 1159720

const val builder633fa8NullCheck1SourceMulTable: Int = 1135304

const val builder633fa8NullCheck1AddTable: Int = 1152648

const val builder633fa8NullCheck1FoldTable: Int = 3134264

val builder633fa8NullCheck1Target: UInt = 0xc5e51debU

val builder633fa8NullCheck1FoldTarget: UInt = 11U

const val builder633fa8NullCheck2ScheduleMulTable: Int = 1183272

const val builder633fa8NullCheck2SourceMulTable: Int = 1159752

const val builder633fa8NullCheck2AddTable: Int = 1176808

const val builder633fa8NullCheck2FoldTable: Int = 3134328

val builder633fa8NullCheck2Target: UInt = 0xfbc7d17cU

val builder633fa8NullCheck2FoldTarget: UInt = 12U

const val builder633fa8NullPostKeyMulTable: Int = 1150440

const val builder633fa8NullPostKeyAddTable: Int = 1130216

const val builder633fa8NullPostInitCF0Static: Int = 3139000

const val builder633fa8NullPostInitBD0Static: Int = 3139018

const val builder633fa8NullPostTableCF0: Int = 3134392

const val builder633fa8NullPostTableBD0: Int = 3136696

const val builder633fa8NullPostFinalCF0Static: Int = 3139036

const val builder633fa8NullPostFinalBD0Static: Int = 3139054

val builder633fa8NullPostInitCF0Magic: ULong = 0x12000005508UL

val builder633fa8NullPostInitBD0Magic: ULong = 0x12000005874UL

val builder633fa8NullPostMixCF0Magic: ULong = 0x12000002dc1UL

val builder633fa8NullPostMixBD0Magic: ULong = 0x1200000653bUL

val builder633fa8NullPostFinalCF0Magic: ULong = 0x120000019c2UL

val builder633fa8NullPostFinalBD0Magic: ULong = 0x1200000552cUL

val builder633fa8NullPostBlock4080Magic: ULong = 0x10000005864UL

val builder633fa8NullPostBlock3f40Magic: ULong = 0x10000006f0bUL

val builder633fa8NullPreludeFirst4080Magic: ULong = 0x10000001638UL

val builder633fa8NullPreludeRest4080Magic: ULong = 0x10000000d7cUL

val builder633fa8NullPreludeBD0Magic: ULong = 0x11a000002ffdUL

val builder633fa8NullPreludeFirst3f40Magic: ULong = 0x10000003690UL

val builder633fa8NullPreludeRest3f40Magic: ULong = 0x10000008167UL

val builder633fa8NullPreludeAB0Magic: ULong = 0x11a000003117UL

val builder633fa8NullPreludeStage4080Magic: ULong = 0x11a00000267fUL

val builder633fa8NullPreludeF40Magic: ULong = 0x11a000000ecfUL

val builder633fa8NullPreludeSourceMagic: ULong = 0x10a000004e02UL

val builder633fa8NullEntryBitsChecksSource: ByteArray = Hex.decode("3674f8f8a81c394e2bca21f938be42b1adbc94923891e2d38ee57c2d131dcebb6eed185b2fe5d82f9543c721bdf818eb782dd2545d9b6429daaa6d5b725db6144b8b6d5dca64a99a7565cb64a9baa66599b5688b34dd9aaadc9a354d53a2cd8a756bca955b56b42bca12e1343551a11412fbcb2ecd59982c841bdca6eeda33bd5e2cf8e2f1b468845576104cfaf7f8ceecfa7a15262ed5f6fa9bd9d442e12e97ec15c1cb4c3ec1ec2881104cfaf7f8ceecfa7a15262ed5f6fa9b8bb0d0b3c47a1c6cf95016b01676804ff8491d5e0e08e8c3b0504bc066ef57b66fbe719164d219086a9310bf190e20a7c27976c5579249c17bedcf2166ef57b6453b9c865799a2246a9310bf190e20a7")

const val process2P5PublicScalarQwordFoldTable: Int = 3161432

const val process2P5PublicScalarQwordMulTable: Int = 1186216

const val process2P5PublicScalarQwordAddTable: Int = 1133032

const val process2P5PublicScalarPackMulTable: Int = 1148424

const val process2P5PublicScalarPackAddTable: Int = 1150824

const val process2P5PublicQwordFoldTableA: Int = 3161048

const val process2P5PublicQwordFoldTableB: Int = 3161176

const val process2P5PublicQwordFoldTableC: Int = 3161304

val process2P5PublicInitWorkspaceConstants: Process2P5PublicConstants = Process2P5PublicConstants(
        countMul = 0x94dfbb91a5378e68UL,
        countAdd = 0x4218665245881823UL,
        productMul = 0x501edede429b621fUL,
        bPrefixMul = 0x6658ca76ca6e396aUL,
        aPrefixMul = 0x918160dbec5e059cUL,
        finalMul = 0xbcb96bc3c168e865UL,
        finalAdd = 0x242a710f34e73ceaUL
    )

const val process2P5PublicTableU32MulTable: Int = 1141128

const val process2P5PublicTableU32AddTable: Int = 1168680

const val process2P5PublicTableFoldTable: Int = 3160920

val process2P5PublicTableWordMul: UInt = 879965431U

val process2P5PublicTableWordAdd: UInt = 1997787469U

val process2P5PublicTableQwordMul: ULong = 0x7378135b2404ba5fUL

val process2P5PublicTableQwordAdd: ULong = 0x2bb7cb5d40ee4303UL

val process2P5PublicTableFinalMul: ULong = 0x714b9632f149a92dUL

val process2P5PublicTableFoldMul: ULong = 0x25f3200d00000000UL

val process2P5PublicTableFinalAdd: ULong = 0x77db08099d019a2fUL

val process2P5PublicPrefixAInitWordMul: UInt = 1748017119U

val process2P5PublicPrefixAInitWordAdd: UInt = 0x9a8acd31U

const val process2P5PublicPrefixAWordMulTable: Int = 1186184

const val process2P5PublicPrefixAWordAddTable: Int = 1143592

val process2P5PublicPrefixAWordMul: UInt = 0xa14d75f7U

val process2P5PublicPrefixAWordAdd: UInt = 603863277U

const val process2P5PublicPrefixAFoldTable: Int = 3160664

val process2P5PublicPrefixAQwordMul: ULong = 0xdea88f4cd7aa9967UL

val process2P5PublicPrefixAQwordAdd: ULong = 0x2498e0a8ace26d05UL

val process2P5PublicPrefixAFoldMul: ULong = 0x2403505500000000UL

val process2P5PublicPrefixAFinalMul: ULong = 0x77260d39cc35e0cdUL

val process2P5PublicPrefixAFinalAdd: ULong = 0xf68d6a799b022952UL

val process2P5PublicPrefixBInitWordMul: UInt = 0xb417ac45U

val process2P5PublicPrefixBInitWordAdd: UInt = 0xb9d0b931U

const val process2P5PublicPrefixBWordMulTable: Int = 1148392

const val process2P5PublicPrefixBWordAddTable: Int = 1123976

val process2P5PublicPrefixBWordMul: UInt = 1453228691U

val process2P5PublicPrefixBWordAdd: UInt = 0xa7b25d96U

const val process2P5PublicPrefixBFoldTable: Int = 3160792

val process2P5PublicPrefixBQwordMul: ULong = 0xac344b5a12897c6dUL

val process2P5PublicPrefixBQwordAdd: ULong = 0x7f6923d8cce61732UL

val process2P5PublicPrefixBFoldMul: ULong = 0xcf8f92cb00000000UL

val process2P5PublicPrefixBFinalMul: ULong = 0x671bb0c140212b91UL

val process2P5PublicPrefixBFinalAdd: ULong = 0x2ecd4bceff393710UL

val process2P5PublicASourceInitialMagic: ULong = 0x810a000006dedUL

val process2P5PublicASourceBlockMagic: ULong = 0x10000000b46UL

const val process2P5PublicASourceStaticTailBlock: Int = 3160544

const val process2P5PublicASourceStaticETable: Int = 3160256

const val process2P5PublicASourceStaticDTable: Int = 3160400

const val process2P5PublicASourceStaticCTable: Int = 3160560

const val process2P5PublicASourceStaticATable: Int = 3160578

const val process2P5PublicASourceNibbleTable: Int = 3160596

val process2P5PublicASourceBInitMagic: ULong = 0x12000004162UL

val process2P5PublicASourceDInitMagic: ULong = 0x8010000000364UL

val process2P5PublicASourcePrebridgeMagic: ULong = 0x120000047ceUL

val process2P5PublicASourceFMagic: ULong = 0x12000005eafUL

val process2P5PublicASourceAMagic: ULong = 0x12000003574UL

val process2P5PublicASourceTMagic: ULong = 0x12000008187UL

val process2P5PublicASourceBMixMagic: ULong = 0x1200000266dUL

val process2P5PublicASourceEAdvanceMagic: ULong = 0x12000000ebdUL

val process2P5PublicASourceCAdvanceMagic: ULong = 0x1200000504cUL

val process2P5PublicASourcePostFMagic: ULong = 0x12000003be7UL

val process2P5PublicASourcePostDMagic: ULong = 0x12000000224UL

val process2P5PublicASourcePostEMagic: ULong = 0xc00f000c00e96UL

val process2P5PublicASourcePackCMagic: ULong = 0x4000004b12UL

val process2P5PublicASourcePackEMagic: ULong = 0x8010000805038UL

val process2P5PublicASourcePackBMagic: ULong = 0x40000019beUL

val process2P5PublicLowA8Mul: ULong = 0x93b6e33be4ad3c3fUL

val process2P5PublicLowA8Add: ULong = 0x698d7878bd852e23UL

val process2P5PublicLow80Mul: ULong = 0x4a0e602e6ec97079UL

val process2P5PublicLow80Add: ULong = 0xad7d39c097694af0UL

val process2P5PublicLowCopyOffsets: List<LowCopyOffset> = listOf(
    LowCopyOffset(0x88, 1), LowCopyOffset(0x90, 0), LowCopyOffset(0x78, 2),
    LowCopyOffset(0x68, 4), LowCopyOffset(0x70, 3), LowCopyOffset(0x58, 6),
    LowCopyOffset(0x60, 5), LowCopyOffset(0x48, 8), LowCopyOffset(0x50, 7),
    LowCopyOffset(0x38, 10), LowCopyOffset(0x40, 9), LowCopyOffset(0x28, 12),
    LowCopyOffset(0x30, 11),
)

val process2P5PublicBSourceStaticWords: List<UInt> =
    listOf(
        0xa99f067dU, 0xb7043f80U, 728687249U, 0xa4732ba2U,
        1832557969U, 1339676025U, 831887333U, 0xfce96d28U,
        1219653493U, 96474745U, 1350613702U, 774235814U,
        0xbfbafcdfU, 0x8e127707U, 855987U, 1254586400U,
        2032392927U, 0xe4ae8f3aU, 1350613702U, 774235814U,
    )

val process2P5PublicFixedPointBE: ByteArray = Hex.decode("04a9bf2be2fd3d90f6467b8ca074710db3804eb0cfcc952a86d23289695d435ee09523a7d0e8aa2c53c6f7a49e9b6bd0db7a2d1035cd61876f37e43a74a1b65237")

val builder6388f0LowSeedPhase1Spec: LowSeedPhaseSpec = LowSeedPhaseSpec(
        phaseMagic = 0x10a000002563UL,
        auxMagics = listOf(
        0x10a0000076a7UL, 0x10a000002de5UL, 0x10a000007af9UL, 0x10a000007e6bUL,
        0x10a0000068a9UL, 0x10a0000062e7UL, 0x10a000007025UL
    ),
        e10Magics = listOf(
        0x10a0000008dcUL, 0x10a00000448cUL, 0x10a000000000UL, 0x10a000007493UL,
        0x10a0000039c1UL, 0x10a000000b56UL, 0x10a000005648UL
    ),
        e10Markers = listOf(
        3, 2, 3, 3, 7, 6, 3
    ),
        bd0Magic = builder6388f0LowSeedPrev2BD0Magic,
        staticOffset = builder6388f0LowSeedPrev2Static,
        unaryMagic = 0xc107000c03253UL,
        f40Magics = listOf(
        0x410006041004174UL, 0x40000a040002bb7UL, 0x3e001203e001e19UL, 0x3a002203a005ba1UL,
        0x320042032003f90UL, 0x2200820220066f1UL, 0x20102002001526UL
    )
    )

val builder6388f0LowSeedPhase2Spec: LowSeedPhaseSpec = LowSeedPhaseSpec(
        phaseMagic = 0x10a0000046c4UL,
        auxMagics = listOf(
        0x10a0000077b1UL, 0x10a0000038b7UL, 0x10a000001adeUL, 0x10a00000727fUL,
        0x10a0000069b3UL, 0x10a000007c03UL, 0x10a00000201bUL
    ),
        e10Magics = listOf(
        0x10a000006f1bUL, 0x10a00000553eUL, 0x10a000005d93UL, 0x10a000001648UL,
        0x10a000002459UL, 0x10a000002aadUL, 0x10a0000005d0UL
    ),
        e10Markers = listOf(
        3, 1, 5, 7, 6, 0, 7
    ),
        bd0Magic = builder6388f0LowSeedPreBD0Magic,
        staticOffset = builder6388f0LowSeedPreStatic,
        unaryMagic = 0xc107000c01105UL,
        f40Magics = listOf(
        0x4100060410052faUL, 0x40000a040001c0fUL, 0x3e001203e0028abUL, 0x3a002203a007f75UL,
        0x320042032002275UL, 0x220082022005168UL, 0x20102002002799UL
    )
    )

val builder6388f0LowSeedPhase3Spec: LowSeedPhaseSpec = LowSeedPhaseSpec(
        phaseMagic = 0x10a0000048faUL,
        auxMagics = listOf(
        0x10a0000045baUL, 0x10a000000d8cUL, 0x10a0000019d4UL, 0x10a000003586UL,
        0x10a0000037adUL, 0x10a00000759dUL, 0x10a000007389UL
    ),
        e10Magics = listOf(
        0x10a000004382UL, 0x10a00000010aUL, 0x10a000005ec1UL, 0x10a000003c3bUL,
        0x10a000007153UL, 0x10a000002125UL, 0x10a00000346aUL
    ),
        e10Markers = listOf(
        3, 3, 0, 3, 1, 0, 5
    ),
        bd0Magic = builder6388f0LowSeedTailBD0Magic,
        staticOffset = builder6388f0LowSeedTailStatic,
        unaryMagic = 0xc107000c079daUL,
        f40Magics = listOf(
        0x410006041005993UL, 0x40000a040001212UL, 0x3e001203e0006daUL, 0x3a002203a0060e5UL,
        0x320042032004c30UL, 0x22008202200655fUL, 0x20102002005752UL
    )
    )

