// 1:1 port of LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift`.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// This file covers Swift lines 6-1168 (tests 1-21) and owns the shared
// internal helpers ([sha256Hex], [make679f48Context], [packUInt64LE]) reused
// by FirstPairSourceSliceKotlinTest2 / Test3.
package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.pairing.Phase5KeySchedule
import com.dsikic.cluecose.core.tables.ClasspathTablesSource
import com.dsikic.cluecose.core.util.Hex
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Correctness gate for the pure-Kotlin FirstPairSourceSlice port: golden hex
 * vectors are preserved exactly from the Swift suite. Tuple access uses the
 * frozen Kotlin field order (FpPair.first/.second, FpQuad.first/.../fourth).
 */
class FirstPairSourceSliceKotlinTest {

    companion object {
        init {
            FirstPairConfig.tablesSource = ClasspathTablesSource()
        }
    }

    @Test
    fun `testInit679f48AndInitialAA8CMatchPythonReferenceVectors`() {
        val initial = init679f48Context()
        assertEquals(
            "2df6726428a8e0ab82f3a9807e742ee08fae532a00f8da456b2978ef4a93e6c8",
            sha256Hex(initial)
        )
        assertEquals(
            "000000000000000005000402050203010503050404010603020105040504050703" +
                "010604050703050006020605050606020704070503040303030704070602010704" +
                "010206030501",
            Hex.encode(initial.copyOfRange(0, 72))
        )

        val updated = update67aa8cLen4Initial(
            context = initial,
            src4 = byteArrayOf(0, 0, 0, 1)
        )
        assertEquals(
            "bcfc48c06814b940af45b26c60b35161014f3131cbfa9bcb7a3bdd178b7cc179",
            sha256Hex(updated)
        )
        assertEquals(
            "0000000000000000000000000000000000000000000000000000000000000000" +
                "000000000400000067e6096a85ae67bb72f36e3c3af54fa57f520e518c68059b" +
                "abd9831f19cde05b",
            Hex.encode(updated.copyOfRange(updated.size - 72, updated.size))
        )
    }

    @Test
    fun `testInitialAA8CRejectsInvalidInputs`() {
        val initial = init679f48Context()
        assertFailsWith<FirstPairSourceSliceError> {
            update67aa8cLen4Initial(context = initial, src4 = byteArrayOf(1, 2, 3))
        }.let { error ->
            assertEquals(FirstPairSourceSliceError.invalid67aa8cInitialSourceLength(3), error)
        }

        val flagged = initial.copyOf()
        flagged[0x1a4] = 2
        assertFailsWith<FirstPairSourceSliceError> {
            update67aa8cLen4Initial(context = flagged, src4 = byteArrayOf(0, 0, 0, 1))
        }.let { error ->
            assertEquals(FirstPairSourceSliceError.invalid67aa8cInitialFlag(2.toByte()), error)
        }
    }

    @Test
    fun `testApply67eb94PendingBlocksMatchesPythonReferenceVector`() {
        val initial = init679f48Context()
        val updated = update67aa8cLen4Initial(
            context = initial,
            src4 = byteArrayOf(0, 0, 0, 1)
        )
        val applied = apply67eb94PendingBlocks(context = updated)

        assertEquals(
            "144260d5df72c48af66d02ee64b7ab3e901b4f5b6c1a69cf41020bc8028c8425",
            sha256Hex(applied)
        )
        assertEquals(
            "060604030004050007010507070303070200050206010400020205020203070503" +
                "070602040500000701040300070303030700070400020207020705010103050006" +
                "010606070202060204020100030507040600000004070002060404050203060702" +
                "050704030600020402060404060006060301040705020701070701050206030707" +
                "000306050504000700040403",
            Hex.encode(applied.copyOfRange(0x114, 0x1a4))
        )
    }

    @Test
    fun `testEncode67d630BlockMatchesPythonReferenceVectors`() {
        val vectors = listOf(
            byteArrayOf(0, 0, 0, 1) to
                ("050604040404020504060402060202020206050502020604020605040202050605" +
                    "020506060402050506020202050606040506020206060202060604020606060402"),
            ByteArray(16) { it.toByte() } to
                ("040600060104000207000305060202050606000500030307000302040003050305" +
                    "060004000100000502070400070301050500000302030206060104020606060502"),
        )

        for (vector in vectors) {
            val encoded = encode67d630Block(vector.first)
            assertEquals(vector.second, Hex.encode(encoded))
        }
    }

    @Test
    fun `testEncode67d630BlockRejectsInvalidLengths`() {
        assertFailsWith<FirstPairSourceSliceError> { encode67d630Block(ByteArray(0)) }.let { error ->
            assertEquals(FirstPairSourceSliceError.invalid67d630BlockLength(0), error)
        }
        assertFailsWith<FirstPairSourceSliceError> { encode67d630Block(ByteArray(17)) }.let { error ->
            assertEquals(FirstPairSourceSliceError.invalid67d630BlockLength(17), error)
        }
    }

    @Test
    fun `testApply67eb94WithPendingRawAdapterMatchesPythonReferenceVector`() {
        val initial = init679f48Context()
        val updated = update67aa8cLen4Initial(
            context = initial,
            src4 = byteArrayOf(0, 0, 0, 1)
        )
        val applied = apply67eb94WithPendingRawAdapter(context = updated)

        assertEquals(
            "7311b8040cd2b3c972246f43f27662024b9b64d2c9b117a0571a3eef59e759c1",
            sha256Hex(applied)
        )
        assertEquals(
            "050202020202040604020404020506050502050506040404060206040506060406" +
                "060505040406050504040406060204020507000102050605050504050502040404",
            Hex.encode(applied.copyOfRange(0x08, 0x4a))
        )
    }

    @Test
    fun `testApply67dd7cUpdateCrossesDF80Boundary`() {
        val context = make679f48Context(contextLength = 0uL, blockIndex = 3u)
        val encoded = encode67d630Block(ByteArray(16) { it.toByte() })
        val applied = apply67dd7cUpdateUntilDF80(
            context = context,
            encoded66 = encoded,
            rawLength = 16
        )

        assertEquals(
            "dff592f458a6f495de02a3c60bf4c0f2c46692df8d67020a65bcdf1277ce2030",
            sha256Hex(applied)
        )
        assertEquals(
            "050700050504040205060504010701000006060302070207060503050701060406" +
                "040104030206070100070704010400040006050500000202030200040700060003" +
                "050502070702050503060203010406050606000007070103000306010705030507" +
                "040500000301070101020600010401070102000205050204060007030003060703" +
                "050403060205070004010000",
            Hex.encode(applied.copyOfRange(0x114, 0x1a4))
        )
    }

    @Test
    fun `testDeriveFrom679f48InputsMatchesPythonReferenceVector`() {
        val previous = ByteArray(2 * 66) { index -> ((index * 5 + 2) and 7).toByte() }
        val updates = previousDescriptorBlocksToDD7CInputs(previousBlocks = previous)
        assertEquals(132, updates.size)
        assertEquals(
            "44e2abbbeb7fa7615a64007196fcdbbfb396ed9fe8e48e6b048404e8f96a2730",
            sha256Hex(updates)
        )

        val context = finalized679f48ContextFromInputs(previousBlocks = previous)
        assertEquals(
            "8a57624059dee8d2679edd1b2e6de78f8d1856a871eb268d59f309943d10aa11",
            sha256Hex(context)
        )
        val source = deriveFrom679f48Inputs(
            previousBlocks = previous,
            offset = 0,
            length = 16
        )
        assertEquals(
            "040400020506020406010204030101020502070705070404000302040501050505" +
                "010004030304070206030607070000000005000102030205000107030202050000",
            Hex.encode(source)
        )
    }

    @Test
    fun `testConstructor67076cAndRawDescriptorDerivationMatchPythonReferenceVectors`() {
        val raw = ByteArray(2 * 66) { index -> ((index * 3 + 1) and 7).toByte() }
        val ptr28 = constructor670978Ptr28Blocks(rawDescriptorBlocks = raw)
        assertEquals(
            "706be35f728909a58b2924e4ddb1a8aad7a725fa7f614445cd92feefb611de1c",
            sha256Hex(ptr28)
        )
        val ptr10 = constructor670a54Ptr10Blocks(rawDescriptorBlocks = raw)
        assertEquals(
            "914e5cf5c7677d9c570a74351fffbd879f75aca534c08426f3042eb2c6212d2b",
            sha256Hex(ptr10)
        )

        val source660448 = deriveFrom660448RawDescriptor(
            rawDescriptorBlocks = raw,
            offset = 0,
            length = 16
        )
        assertEquals(
            "040401010705040302070407030002030400040004030507070305050106050402" +
                "070401040604040707070702010000030500040203000304030103030004060301",
            Hex.encode(source660448)
        )

        val first = ByteArray(66) { index -> ((index * 5 + 2) and 7).toByte() }
        val second = ByteArray(66) { index -> ((index * 7 + 4) and 7).toByte() }
        val source64d774 = deriveFrom64d774RawStreams(
            firstRawBlocks = first,
            secondRawBlocks = second,
            offset = 0,
            length = 16
        )
        assertEquals(
            "040406010201060506000205040004000004010001010207060607060607050502" +
                "000107040306040106020704040002000600030200070603040004010004010207",
            Hex.encode(source64d774)
        )
    }

    @Test
    fun `testBuilder6388f0TailLayersMatchPythonReferenceVectors`() {
        val internalData = ByteArray(2 * 66) { index -> ((index * 5 + 1) and 7).toByte() }
        val prefinal = ByteArray(2 * 66) { index -> ((index * 3 + 2) and 7).toByte() }
        val workspace = ByteArray(266) { index -> ((index * 7 + 4) and 7).toByte() }
        val stageA = ByteArray(282) { index -> ((index * 5 + 2) and 7).toByte() }
        val stageB = ByteArray(282) { index -> ((index * 3 + 6) and 7).toByte() }

        val finalRaw = builder6388f0FinalRawBlocks(internalBlocks = internalData)
        assertEquals(132, finalRaw.size)
        assertEquals(
            "56ddfdbf0fcc1b60f339a70d9cba7e8262d02c9e7bed64970126f795fb635576",
            sha256Hex(finalRaw)
        )

        val prefinalInternal = builder6388f0PrefinalLen32InternalBlocks(
            prefinalSourceBlocks = prefinal
        )
        assertEquals(132, prefinalInternal.size)
        assertEquals(
            "b6af68238f927cb2f27d318fa8fb6e0c77494d16555c4dd64a06d1376544995a",
            sha256Hex(prefinalInternal)
        )

        val workspacePrefinal = builder6388f0Len32PrefinalSourcesFromWorkspace(
            workspaceSource = workspace
        )
        assertEquals(132, workspacePrefinal.size)
        assertEquals(
            "b7fed8b0449eb2368269402165ea6bf581f68580bdb3a1719b0ecf14ac2f9172",
            sha256Hex(workspacePrefinal)
        )

        val stagePrefinal = builder6388f0Len32PrefinalSourcesFromStageInputs(
            stageASource = stageA,
            stageBSource = stageB
        )
        assertEquals(132, stagePrefinal.size)
        assertEquals(
            "cc33aa4c896081feb2a44b378afd6b9830ca655c174f6d507c9716e8a68a7830",
            sha256Hex(stagePrefinal)
        )
    }

    @Test
    fun `testDeriveFrom6388f0LayersMatchPythonReferenceVectors`() {
        val internalData = ByteArray(2 * 66) { index -> ((index * 5 + 1) and 7).toByte() }
        val prefinal = ByteArray(2 * 66) { index -> ((index * 3 + 2) and 7).toByte() }
        val workspaceA = ByteArray(266) { index -> ((index * 7 + 4) and 7).toByte() }
        val workspaceB = ByteArray(266) { index -> ((index * 7 + 5) and 7).toByte() }
        val stageA0 = ByteArray(282) { index -> ((index * 5 + 2) and 7).toByte() }
        val stageB0 = ByteArray(282) { index -> ((index * 3 + 6) and 7).toByte() }
        val stageA1 = ByteArray(282) { index -> ((index * 3 + 1) and 7).toByte() }
        val stageB1 = ByteArray(282) { index -> ((index * 5 + 4) and 7).toByte() }

        assertEquals(
            "040400040702000206000504060306040706020100040303030205020506030603" +
                "020207050704040602060006000600020002020305050000020401070006030103",
            Hex.encode(
                deriveFrom6388f0InternalStreams(
                    firstInternalBlocks = internalData,
                    secondInternalBlocks = prefinal,
                    offset = 0,
                    length = 16
                )
            )
        )

        assertEquals(
            "040400000703050704050203030503020706030400060402020001060302050104" +
                "070104060000030706020104020007060300040207050502050502000405050700",
            Hex.encode(
                deriveFrom6388f0PrefinalLen32Streams(
                    firstPrefinalBlocks = prefinal,
                    secondPrefinalBlocks = internalData,
                    offset = 0,
                    length = 16
                )
            )
        )

        assertEquals(
            "040407040606030500050503030005050606010206050607040407000306050202" +
                "070100040200040407070303020600010302040007020501000306000406020107",
            Hex.encode(
                deriveFrom6388f0WorkspaceLen32Streams(
                    firstWorkspaceSource = workspaceA,
                    secondWorkspaceSource = workspaceB,
                    offset = 0,
                    length = 16
                )
            )
        )

        assertEquals(
            "040400030102000103000703030201010707070106000506020306040203060202" +
                "070203000305040200010504020501070103070706000700040501060003060005",
            Hex.encode(
                deriveFrom6388f0StageLen32Streams(
                    firstStageASource = stageA0,
                    firstStageBSource = stageB0,
                    secondStageASource = stageA1,
                    secondStageBSource = stageB1,
                    offset = 0,
                    length = 16
                )
            )
        )
    }

    @Test
    fun `testBuilder6388f0PackAndLaneLayersMatchPythonReferenceVectors`() {
        val primary0 = ByteArray(20 * 16) { index -> ((index * 3 + 1) and 7).toByte() }
        val secondary0 = ByteArray(20 * 16) { index -> ((index * 5 + 2) and 7).toByte() }
        val primary1 = ByteArray(20 * 16) { index -> ((index * 7 + 4) and 7).toByte() }
        val secondary1 = ByteArray(20 * 16) { index -> ((index * 3 + 6) and 7).toByte() }

        val pack0 = builder6388f0PackOutputsFromLaneBlocks(
            primaryLaneBlocks = primary0,
            secondaryLaneBlocks = secondary0
        )
        assertEquals(
            "cbb73fff67fc8d84576195e087bdadabd862476bea09fd3603ec19f75f703f28",
            sha256Hex(pack0.first)
        )
        assertEquals(
            "1ed65e8008b64c579a8dc5ef91bbb8026a7bd10180c16fd9022395cc6f21e583",
            sha256Hex(pack0.second)
        )
        assertEquals(
            "0419abe716a28762aae3d3abdfcbd20069e74db5f4cc62060cbf32f11ed3c2ea",
            sha256Hex(pack0.third)
        )
        assertEquals(
            "ec559fc571319e7d69e31ba7761b9035072bcddc75324c3de9f78b7eca830560",
            sha256Hex(pack0.fourth)
        )

        val stage0 = builder6388f0Len32StageInputsFromPackOutputs(
            stageBPackHead16 = pack0.first,
            stageBPackBody16 = pack0.second,
            stageAPackHead16 = pack0.third,
            stageAPackBody16 = pack0.fourth
        )
        assertEquals(
            "fbece039249b3700c3908af39c16cfdffda2a8e94765ce1243280ede14a0db22",
            sha256Hex(stage0.first)
        )
        assertEquals(
            "55622eae1f29c6264a33d62e2b41fb3875832683281b456831037d8a4e01d4f6",
            sha256Hex(stage0.second)
        )

        val pack1 = builder6388f0PackOutputsFromLaneBlocks(
            primaryLaneBlocks = primary1,
            secondaryLaneBlocks = secondary1
        )
        val sourceFromPack = deriveFrom6388f0PackLen32Streams(
            firstStageBPackHead16 = pack0.first,
            firstStageBPackBody16 = pack0.second,
            firstStageAPackHead16 = pack0.third,
            firstStageAPackBody16 = pack0.fourth,
            secondStageBPackHead16 = pack1.first,
            secondStageBPackBody16 = pack1.second,
            secondStageAPackHead16 = pack1.third,
            secondStageAPackBody16 = pack1.fourth,
            offset = 0,
            length = 16
        )
        assertEquals(
            "040400060501060401060002050500050600010504000007060300050100000407" +
                "060406030405050001050500010002050100020501010304040405040706050200",
            Hex.encode(sourceFromPack)
        )

        val sourceFromLanes = deriveFrom6388f0LaneLen32Streams(
            firstPrimaryLaneBlocks = primary0,
            firstSecondaryLaneBlocks = secondary0,
            secondPrimaryLaneBlocks = primary1,
            secondSecondaryLaneBlocks = secondary1,
            offset = 0,
            length = 16
        )
        assertContentEquals(sourceFromLanes, sourceFromPack)
    }

    @Test
    fun `testBuilder6388f0ScheduleLayerMatchesPythonReferenceVectors`() {
        val schedule0 = listOf(
            0x11223344u, 0x12243648u, 0x1326394cu, 0x14283c50u, 0x152a3f54u,
            0x162c4258u, 0x172e455cu, 0x18304860u, 0x19324b64u, 0x1a344e68u,
            0x1b36516cu, 0x1c385470u, 0x1d3a5774u, 0x1e3c5a78u, 0x1f3e5d7cu,
            0x20406080u, 0x21426384u, 0x22446688u, 0x2346698cu, 0x24486c90u,
        )
        val schedule1 = listOf(
            0x89abcdefu, 0x8aaccef0u, 0x8badcff1u, 0x8caed0f2u, 0x8dafd1f3u,
            0x8eb0d2f4u, 0x8fb1d3f5u, 0x90b2d4f6u, 0x91b3d5f7u, 0x92b4d6f8u,
            0x93b5d7f9u, 0x94b6d8fau, 0x95b7d9fbu, 0x96b8dafcu, 0x97b9dbfdu,
            0x98badcfeu, 0x99bbddffu, 0x9abcdf00u, 0x9bbde001u, 0x9cbee102u,
        )

        val lanes0 = builder6388f0LaneBlocksFromScheduleWords(schedule0)
        assertEquals(
            "1b70254a30185288de09f9a35ec6b1293474b349a720f89794631dd5cd43c2f8",
            sha256Hex(lanes0.first)
        )
        assertEquals(
            "d718360163754dcd6fc33adfe4d184ce65e7f9f6e9bd32b4fcb677b425845bc1",
            sha256Hex(lanes0.second)
        )

        val lanes1 = builder6388f0LaneBlocksFromScheduleWords(schedule1)
        assertEquals(
            "2ebd449c1b18906b11e48c56f3bdef9cb98072e5be686a4b89cb02b2dab45d04",
            sha256Hex(lanes1.first)
        )
        assertEquals(
            "cbc6175ef451f45406d268a601058f257271e2ddcd12d6a773c0b51f48c8f2f0",
            sha256Hex(lanes1.second)
        )

        val source = deriveFrom6388f0ScheduleLen32Streams(
            firstScheduleWords = schedule0,
            secondScheduleWords = schedule1,
            offset = 0,
            length = 16
        )
        assertEquals(
            "040401070203010704050106010001020204070005060100030704020306000701" +
                "070104060605050201000402010102050201000404040305070103060002030602",
            Hex.encode(source)
        )

        val key = phase5RawKeyFrom6388f0ScheduleLen32Streams(
            firstScheduleWords = schedule0,
            secondScheduleWords = schedule1
        )
        assertEquals("e407917d692fd119fbf18baf60644ded", Hex.encode(key))
    }

    @Test
    fun `testBuilder63c278InitialMixAndTailReducersMatchPythonReferenceVectors`() {
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val arg1 = ByteArray(88) { index -> (index * 5 + 11).toByte() }
        val arg2 = ByteArray(88) { index -> (index * 3 + 17).toByte() }
        val scalar = 0x0123456789abcdefUL

        val initial = builder63c278InitialVectors(arg0 = arg0, arg1 = arg1)
        assertEquals(44, initial.first.size)
        assertEquals(22, initial.second.size)
        assertEquals(
            "7510279962382f9fbfd7acbc437c419e0efe3aa928c3b86a09fa3235880799d1",
            sha256Hex(packUInt64LE(initial.first))
        )
        assertEquals(
            "cd3066baa97e86c4cd0882325622da57d6283f598225b6c7b19f3e066e18a9d3",
            sha256Hex(packUInt64LE(initial.second))
        )

        val second = builder63c278SecondInitialVectors(arg0 = arg0, arg2 = arg2)
        assertEquals(
            "571998e49bf13bc6a2e9d7df592371186beacebb27c8fd7d498924f41591f53e",
            sha256Hex(packUInt64LE(second.first))
        )
        assertEquals(
            "dbbd9840317de13798bd1cf50ef6fec8ad0e26638b894a62b1cfd14aee785fda",
            sha256Hex(packUInt64LE(second.second))
        )

        val mixed1 = builder63c278ScalarMixVector(
            x1Vec44 = initial.first,
            x0Vec22 = initial.second,
            scalar = scalar
        )
        val mixed2 = builder63c278ScalarMix2Vector(
            x2Vec44 = second.first,
            x0Vec22 = second.second,
            scalar = scalar
        )
        assertEquals(
            "4692583a47aa6989ac7d4fab5d20c97ee27970af79f4590824a8268dbf1b27dd",
            sha256Hex(packUInt64LE(mixed1))
        )
        assertEquals(
            "5b7a4c6be8ac3f3c33331e866588d75f17b62dcdf9b1bfab22846801cd262dca",
            sha256Hex(packUInt64LE(mixed2))
        )

        val tail1 = builder63c278Tail1U32Words(mixed1)
        val tail2 = builder63c278Tail2U32Words(mixed2)
        assertEquals(
            "27e1f0bcd8f8555166c80cb3ee788ce5c03a1ee08dde0f2aa8e9c3282a72b472",
            sha256Hex(packUInt32LE(tail1))
        )
        assertEquals(
            "9b840d5956cdaae86b1835984c6bdcfa4b44577c1cfeae66455c2c04739d1e4f",
            sha256Hex(packUInt32LE(tail2))
        )
        assertEquals(
            listOf(0xc21a61c6u, 0x74c4feafu, 0x58177aecu, 0x7a88bfb1u),
            tail1.subList(0, 4)
        )
        assertEquals(
            listOf(0xc822edf3u, 0x3210de15u, 0x669f83ceu, 0x9d56a88eu),
            tail2.subList(0, 4)
        )

        val accum = builder63c278AccumulatorStreams(arg2 = arg2, tail2Words = tail2)
        assertEquals(
            "b5ba8730b4f348f2bead511a543354ac3cba1388e04d737af3487124cbc79598",
            sha256Hex(packUInt64LE(accum.first))
        )
        assertEquals(
            "2f26f4d41701f9596582f852831caeede87aa078ff624ab70e59dad3eb170b5d",
            sha256Hex(packUInt64LE(accum.second))
        )
        assertEquals(
            "84932501eaef1bcf7c0b58a51b1bf8653c46ae9527a759b0469ff2653e43db03",
            sha256Hex(packUInt64LE(accum.third))
        )
        assertEquals(
            "22518465559d66c1e916c293fe4bc2e9380a5018f23d213d7bedfaef9f3f746b",
            sha256Hex(packUInt64LE(accum.fourth))
        )

        val bridgeConv = builder63c278BridgeConvolutionVector(
            sp440Cumulative = accum.first,
            sp4f0Words = accum.second,
            sp5a0Words = accum.third,
            sp390Cumulative = accum.fourth
        )
        val bridgeX0 = builder63c278BridgeX0Vector(arg0 = arg0)
        val bridgeMix = builder63c278BridgeMixVector(
            sp230Vec44 = bridgeConv,
            x0Vec22 = bridgeX0,
            scalar = scalar
        )
        val sp128 = builder63c278BridgeSP128Words(bridgeMix)
        assertEquals(
            "9d307ee87af9694b08e35935470c931390def241ff8cf03071b0c97e9288a7e6",
            sha256Hex(packUInt64LE(bridgeConv))
        )
        assertEquals(
            "ba1a0930d3edc74b7b1cbc65257d0d5e0b84935ba8baf2a8d1bffb131618f370",
            sha256Hex(packUInt64LE(bridgeX0))
        )
        assertEquals(
            "51c7581842c0ac902cde4145e6edec72344c6b174e1637b7804a54cf8c639e1c",
            sha256Hex(packUInt64LE(bridgeMix))
        )
        assertEquals(
            "eacc144f8735791a4d91c9c60617e7203b18f9338142c63fd549bc9e19957670",
            sha256Hex(packUInt32LE(sp128))
        )

        val prebranch = builder63c278PrebranchInitialStreams(
            arg0 = arg0,
            tail1Words = tail1,
            sp128Words = sp128
        )
        val pre4f0 = builder63c278PrebranchSP4F0Words(arg0 = arg0)
        val pre230 = builder63c278PrebranchSP230Words(sp4f0Words = pre4f0)
        val pre5a0 = builder63c278PrebranchSP5A0Words(sp230Words = pre230)
        assertEquals(
            "bb76f8765891dfcb76e25a5b078bf9a703142137ab005f646526f4654e693626",
            sha256Hex(packUInt32LE(prebranch.first))
        )
        assertEquals(
            "7eb39fcc20f253b51776dd9ea7a697b92813d2db917ca91ed5c8378b1e7fd37c",
            sha256Hex(packUInt32LE(prebranch.second))
        )
        assertEquals(
            "a1594bb70ed406c9e25a8cd067e68d56e23292b717ec02cd551456e5ed89f6a4",
            sha256Hex(packUInt32LE(prebranch.third))
        )
        assertEquals(
            "8ac4e5d77a1070b2926a06ef3c8fbb01a0c4618c8237d5073f4198044ce1c02d",
            sha256Hex(packUInt32LE(prebranch.fourth))
        )
        assertEquals(
            "d9523b2165986722e70834869ba3bc017dfe1c0ae5bc42ca299bb7dabd2450c6",
            sha256Hex(packUInt32LE(pre4f0))
        )
        assertEquals(
            "651bc9459d7ca269bd73de0e77a883f06126649d59e0ed448912291dd626832f",
            sha256Hex(packUInt32LE(pre230))
        )
        assertEquals(
            "fe3dad8c38db4b9a4ebf05fa507d885080f4119e6751a0c30a92ef115b676746",
            sha256Hex(packUInt32LE(pre5a0))
        )
    }

    private data class C278ScheduleVector(
        val arg1: ByteArray,
        val arg2: ByteArray,
        val expected: List<UInt>,
        val hash: String
    )

    @Test
    fun `testBuilder63c278ScheduleWordsMatchCapturedPythonReferenceVectors`() {
        val arg0 = pre63c278Arg0Source
        val vectors = listOf(
            C278ScheduleVector(
                arg1 = Hex.decode(
                    "870f4045410102fa6ae48ed935d4528112946ec5085053dcda8e537f1f02ea84" +
                        "ddb295ec23ff6a8c58b97fed9a2736abd68482c3517b7ad27fbe711c7fb9f" +
                        "32a00c1356f38b3025e143d56dd9017a173d68482c3517b7ad2"
                ),
                arg2 = Hex.decode(
                    "d9f6a9980cacf13569f2843968ae9cdc112262a50b5bdb6a435931a1d02896c3" +
                        "ab70c95476b7ea17fb1fadf32aeabc0881ab695ce36c62eca5621fafcf684" +
                        "616c5198a7948a03ad3e82ae3783b32b01681ab695ce36c62ec"
                ),
                expected = listOf(
                    0x8c15c5dau, 0x34dd429du, 0x955af9feu, 0x6897e537u, 0x1bad4a31u,
                    0xb3206998u, 0x3bda123du, 0x3fdb46c5u, 0xd42db9fdu, 0x29dc0f3au,
                    0x3b95a64cu, 0xcce6d138u, 0x70227a65u, 0x87ca2121u, 0xefb07a8fu,
                    0xc4749659u, 0x1cd92603u, 0xe0ab3767u, 0x3b95a64cu, 0xcce6d138u,
                ),
                hash = "bca47c5f0b63efce696822be0e0b00455d7f4d592cf55332429588fcba3e285b"
            ),
            C278ScheduleVector(
                arg1 = Hex.decode(
                    "2576fe36ecd8e7be514212a7129bcb32c361f0d230d0612528124ca25fd446a2" +
                        "5979de6adb6a70c2b534e301985718a0d68482c3517b7ad27fbe711c7fb9f" +
                        "32a00c1356f38b3025e143d56dd9017a173d68482c3517b7ad2"
                ),
                arg2 = Hex.decode(
                    "37b2af34160b02ddf8e45aef8f22c626ed2984e27b754dc75c89f9a58cb0b0a8" +
                        "eea62a650526362ea30760fa9319f39781ab695ce36c62eca5621fafcf684" +
                        "616c5198a7948a03ad3e82ae3783b32b01681ab695ce36c62ec"
                ),
                expected = listOf(
                    0x04961c3du, 0x1f110752u, 0x271f9e47u, 0x551739bcu, 0x828a0f59u,
                    0xd01fa5beu, 0x6703b5b7u, 0x22e03d75u, 0x9cbed758u, 0x7f4e06d1u,
                    0x3b95a64cu, 0xcce6d138u, 0x70227a65u, 0x87ca2121u, 0xefb07a8fu,
                    0xc4749659u, 0x1cd92603u, 0xe0ab3767u, 0x3b95a64cu, 0xcce6d138u,
                ),
                hash = "8b6ad5e9244eb599dbfdaabee633b41bd6230643ed86ec7c90e9d3335c621a3c"
            ),
        )

        for (vector in vectors) {
            val schedule = builder63c278ScheduleWords(
                arg0 = arg0,
                arg1 = vector.arg1,
                arg2 = vector.arg2,
                scalar = pre63c278Scalar
            )
            assertEquals(vector.expected, schedule)
            assertEquals(vector.hash, sha256Hex(packUInt32LE(schedule)))
        }

        val source = deriveFrom63c278ScheduleInputs(
            arg0 = arg0,
            firstArg1 = vectors[0].arg1,
            firstArg2 = vectors[0].arg2,
            secondArg1 = vectors[1].arg1,
            secondArg2 = vectors[1].arg2,
            scalar = pre63c278Scalar,
            offset = 0,
            length = 16
        )
        val defaultSource = deriveFromPre63c278ScheduleInputs(
            firstArg1 = vectors[0].arg1,
            firstArg2 = vectors[0].arg2,
            secondArg1 = vectors[1].arg1,
            secondArg2 = vectors[1].arg2,
            offset = 0,
            length = 16
        )
        assertContentEquals(defaultSource, source)
        assertEquals(
            "040402020404000202060205040102060705010600010704020506070300050007" +
                "070004010407010502000304070207010604030305070405040204060700000702",
            Hex.encode(source)
        )
        val rawKey = phase5RawKeyFrom63c278ScheduleInputs(
            arg0 = arg0,
            firstArg1 = vectors[0].arg1,
            firstArg2 = vectors[0].arg2,
            secondArg1 = vectors[1].arg1,
            secondArg2 = vectors[1].arg2,
            scalar = pre63c278Scalar
        )
        assertEquals("8df19b56ae4a0d4044a5c0d5fc86a34e", Hex.encode(rawKey))
        val defaultRawKey = phase5RawKeyFromPre63c278ScheduleInputs(
            firstArg1 = vectors[0].arg1,
            firstArg2 = vectors[0].arg2,
            secondArg1 = vectors[1].arg1,
            secondArg2 = vectors[1].arg2
        )
        assertContentEquals(defaultRawKey, rawKey)
    }

    @Test
    fun `testBuilder6388f0Next642f60InputsFrom64cd40OutputsMatchPythonReferenceVector`() {
        val first = ByteArray(88) { index -> (index * 3 + 1).toByte() }
        val second = ByteArray(88) { index -> (index * 5 + 2).toByte() }
        val third = ByteArray(88) { index -> (index * 7 + 4).toByte() }

        val next = builder6388f0Next642f60InputsFrom64cd40Outputs(
            first64cd40Output = first,
            second64cd40Output = second,
            third64cd40Output = third
        )
        assertEquals(
            "727ff3b03b7f9b9445dc32088470dba2e6560584f7811dda9c792f21b509e1d2" +
                "92391698dbe3f37f255093af64049e2b463bc0751707c7423cc615e155ef3aa8" +
                "b2f3387f7b484c6b05c4f356449860b4a61f7b67378c0d92",
            Hex.encode(next.x0)
        )
        assertEquals(
            "82da21c2812f75174d7cb165a116dbf009e53aea970434b39f33459e272f861b" +
                "e27b90bce1deb6322d5fcb2a41d541dfa991d42677480a7cbfe36d5d8767d5e1" +
                "424e8159410f29ef0dd7bbe7e1623750495d780257d74f2d",
            Hex.encode(next.x1)
        )
        assertEquals(
            "24966d1c50f77db7f8f3f44c7c6f1c9008083749fa291b844bf1adcccc9903b8" +
                "445a06e7b06968ce9823d9b09c5ab1b3687801c69a9ee22fab48de1eac457f9" +
                "56487f0801049ff2d38d61817bc4546d7c8e8cb423a13aadb",
            Hex.encode(next.x2)
        )
    }

    @Test
    fun `testBuilder6388f0StreamStart642f60InputsMatchPythonReferenceVector`() {
        val out0Seed = ByteArray(88) { index -> (index * 9 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 11 + 7).toByte() }

        val start = builder6388f0StreamStart642f60Inputs(
            out0Seed = out0Seed,
            out1Seed = out1Seed
        )
        assertEquals(
            "6a98ace04c88d51d6ab1d75da53dff508ffdea49d14182592c7500a439baad09" +
                "8a47a5a0ecaafcc40aeef4178563c64f2f9e8ba1f142af1f8c2af649193b49f6" +
                "aaf69d608ccd236caa2a12d265898d4ecf3e2cf91144d37d",
            Hex.encode(start.x0)
        )
        assertEquals(
            "b5912a7855f4aa29d3882cbc13838a3ddb7e261648807def1c24491cda8c00ef" +
                "959d913335cd3f2ef30e15433318e7babb6b628ea8c7820d7ca628d67a01846" +
                "c75a9f8ee15a6d44513dae60c53ca4cfd9bdda4ac08d0b22e",
            Hex.encode(start.x1)
        )
        assertContentEquals(streamStart642f60X2Source, start.x2)
        assertEquals(
            "64eec98b6cf193a8c6f413af4eb1ed6bb4d4f06cb6c343284c46c9ce85ebde6f",
            sha256Hex(start.x2)
        )

        assertContentEquals(
            builder6388f0RecoverStreamStartOut0SeedFrom642f60X0(start.x0),
            out0Seed
        )
        assertContentEquals(
            builder6388f0RecoverStreamStartOut1SeedFrom642f60X1(start.x1),
            out1Seed
        )
    }

    @Test
    fun `testBuilder6421c0HighSeedHelpersMatchPythonReferenceVectors`() {
        val x0 = ByteArray(80) { index -> (index * 11 + 7).toByte() }
        val x1 = ByteArray(88) { index -> (index * 13 + 3).toByte() }
        val x2 = ByteArray(88) { index -> (index * 17 + 5).toByte() }
        val output = builder6421c0OutputWords(
            x0Source = x0,
            x1Source = x1,
            x2Source = x2,
            scalar = 0x0123456789abcdefUL
        )
        assertEquals(
            listOf(
                0xdbc1c7c6u, 0x2033fae4u, 0xdbba46f4u, 0x51d8e106u, 0x06acf332u,
                0x8bad4314u, 0xb5c9adb4u, 0x54da2609u, 0x4ea01830u, 0x00da7af7u,
                0x207da04au, 0xbaa6764du, 0x0e8a02aau, 0x41fc4b04u, 0x299ed743u,
                0xa8d7eaf6u, 0x088c1fe0u, 0x83d47285u, 0x9d6a5499u, 0x640e0bb3u,
                0x799af52du, 0xa7308434u,
            ),
            output
        )
        assertEquals(
            "613beae0326a26de5b07c1bca00a356d6d497e222d8cfe34e4cb84ae26be14a8",
            sha256Hex(packUInt32LE(output))
        )

        val source70 = ByteArray(70) { index -> (index * 19 + 9).toByte() }
        val highX0 = builder6388f0HighSeedX0SourceFrom5bcf98Output(source70)
        assertEquals(
            "3a2dfcb318b7344cf51e5b96b0815468383cdb6a10f727cbc3e953daf6513562" +
                "261b8e3aeda4df7071901e85677af85bdc709a418e53b9f15fe323f7937078" +
                "c9120920c1c2928a95ed01e2731e03cdea",
            Hex.encode(highX0)
        )
        val highOutput = builder6421c0OutputWords(
            x0Source = highX0,
            x1Source = highSeed6421c0X1Source,
            x2Source = highSeed6421c0X2Source,
            scalar = highSeed6421c0Scalar
        )
        assertEquals(
            listOf(
                0x808a1855u, 0x783ef112u, 0x27aa1861u, 0x18f09114u, 0x3d286c05u,
                0x83db42f3u, 0x57a5bb1eu, 0x208b0c9eu, 0x64223ac2u, 0x97cc4564u,
                0x0ef21945u, 0xe627151fu, 0xd8178670u, 0xdba71039u, 0xdcae32d6u,
                0x26e1b50bu, 0x8fb269cbu, 0x6bcc9065u, 0x9d1492afu, 0x94fe8376u,
                0xd8178670u, 0xdba71039u,
            ),
            highOutput
        )
        assertEquals(
            "cbe9227ccdfa92d4e23f2bd4f11e67cc0ef66de6b945f812bd0c2d213b7afd93",
            sha256Hex(packUInt32LE(highOutput))
        )

        val secondSource70 = ByteArray(70) { index -> (index * 23 + 4).toByte() }
        val highSeeds = builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(
            firstOutput70 = source70,
            secondOutput70 = secondSource70
        )
        assertContentEquals(packUInt32LE(highOutput), highSeeds.out0)
        assertEquals(
            "8d85f2399a367b70a58ac991bc36c7604a45a128e91d968a99cb7b3dd020a2b5" +
                "f7b82949a78159b9b962810cbd3e57fd708617d83910a7dbd632aedc0bb5e126" +
                "cb69b28f6590cc6baf92149d7683fe94708617d83910a7db",
            Hex.encode(highSeeds.out1)
        )
        assertEquals(
            "bd9129fe22f4ab7d395e31c3e369cfc6cc62b3109df0b4ec7d82cffa5117d0e2",
            sha256Hex(highSeeds.out0 + highSeeds.out1)
        )

        val scalarWindow = Hex.decode(
            "3b588dd68f20da5f883993332cabcda6576645712cdd039d0a8195f4b1c0b52e" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000"
        )
        val generatorPoint = Hex.decode(
            "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
                "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"
        )
        val p256Outputs = P256ScalarMultiplier.builder5bcf98P256Outputs(
            scalarWindowLE = scalarWindow,
            sensorPointXYBE = generatorPoint
        )
        assertEquals(
            "fb123cffe9d4e8e9e27f9c5251cdcd24a9f513c43d130a07925b8ceee0fe75d0",
            sha256Hex(p256Outputs.xOutput70)
        )
        assertEquals(
            "5f6979616cb8bbeb57dac5b362653508b597e8292b6bc0a3defc0787cc4737ca",
            sha256Hex(p256Outputs.yOutput70)
        )
        assertEquals(
            "a1e69a746868223565f55b036dcb352ac7ad64457d8304d2a015b5ee90942023" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000",
            Hex.encode(p256Outputs.xOutput70)
        )
        assertEquals(
            "3ac85ab9f4754fade9fb79588ec4d48ef3af4d916151ad0477d595de947261ea" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000",
            Hex.encode(p256Outputs.yOutput70)
        )

        // P256 bridge (inline port of the Swift extension
        // `builder6388f0HighSeedStreamStartSeedsFromScalarP256`):
        val p256Bridge = P256ScalarMultiplier.builder5bcf98P256Outputs(
            scalarWindowLE = scalarWindow,
            sensorPointXYBE = generatorPoint
        )
        val p256HighSeeds = builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(
            firstOutput70 = p256Bridge.xOutput70,
            secondOutput70 = p256Bridge.yOutput70
        )
        assertEquals(
            "fbc744031431d9fda2ceed80266ee2dfefb9a55e585ea5bbc2666a144379f042",
            sha256Hex(p256HighSeeds.out0)
        )
        assertEquals(
            "f9b223b45fe8ec5687cdcbd18218714f4b261938a4befb37e0ced7b42d012289",
            sha256Hex(p256HighSeeds.out1)
        )
    }

    @Test
    fun `testBuilder6388f0FirstPairStreamSeedsFrom5bcf98OutputsMatchPythonReferenceVectors`() {
        val row0FirstOutput70 = ByteArray(70) { index -> (index * 19 + 9).toByte() }
        val row0SecondOutput70 = ByteArray(70) { index -> (index * 23 + 4).toByte() }
        val row59FirstOutput70 = ByteArray(70) { index -> (index * 41 + 6).toByte() }
        val row59SecondOutput70 = ByteArray(70) { index -> (index * 43 + 7).toByte() }

        val highSeeds = builder6388f0FirstPairHighSeedStreamStartSeedsFrom5bcf98Outputs(
            row0FirstOutput70 = row0FirstOutput70,
            row0SecondOutput70 = row0SecondOutput70,
            row59FirstOutput70 = row59FirstOutput70,
            row59SecondOutput70 = row59SecondOutput70
        )
        assertEquals(
            "55188a8012f13e786118aa271491f018056c283df342db831ebba5579e0c8b20" +
                "c23a22646445cc974519f20e1f1527e6708617d83910a7dbd632aedc0bb5e126" +
                "cb69b28f6590cc6baf92149d7683fe94708617d83910a7db",
            Hex.encode(highSeeds.row0.out0)
        )
        assertEquals(
            "59a1e91687277937b599fe911da5ba4ac96f50c6c5cebb77a0a54a7387248dad" +
                "a8360f4c149e5731729ffa13bd3e57fd708617d83910a7dbd632aedc0bb5e126" +
                "cb69b28f6590cc6baf92149d7683fe94708617d83910a7db",
            Hex.encode(highSeeds.row59.out1)
        )

        val seeds = builder6388f0FirstPairStreamSeedsFrom5bcf98Outputs(
            row0Out4 = ByteArray(88) { index -> (index * 3 + 1).toByte() },
            row0Out3 = ByteArray(88) { index -> (index * 5 + 2).toByte() },
            row0Out2 = ByteArray(88) { index -> (index * 7 + 3).toByte() },
            row0FirstOutput70 = row0FirstOutput70,
            row0SecondOutput70 = row0SecondOutput70,
            row59FirstOutput70 = row59FirstOutput70,
            row59SecondOutput70 = row59SecondOutput70,
            nullScalarWindow = ByteArray(70) { index -> (index * 29 + 1).toByte() },
            staticScalarWindow = ByteArray(70) { index -> (index * 31 + 2).toByte() },
            nullEntropy11A = ByteArray(0x11a) { index -> (index * 37 + 3).toByte() },
            nullAttempts = 2
        )
        assertContentEquals(seeds.row0Out0, highSeeds.row0.out0)
        assertContentEquals(seeds.row0Out1, highSeeds.row0.out1)
        assertContentEquals(seeds.row59Out0, highSeeds.row59.out0)
        assertContentEquals(seeds.row59Out1, highSeeds.row59.out1)
        assertEquals(2, seeds.nullAttempts)

        val starts = builder6388f0FirstPair642f60StreamStarts(seeds = seeds)
        assertEquals(
            "6cf7247e7ccce409f16a110e66e29d319ef41e9dc20b12951b97f9d3a996166a",
            sha256Hex(starts.row0.x0)
        )
        assertEquals(
            "3e9f58eae41463a5ddc7b08db2cd537e8bff419108e5d5b9c1e6fd6e6bbfcc9d",
            sha256Hex(starts.row59.x1)
        )

        val source = deriveFrom6388f0FirstPairStreamSeeds(seeds = seeds)
        assertEquals(
            "0404040101050405010306060207010704000002020100020106040104030400" +
                "07050303000306070304070503010202020402050106030505050207070104020100",
            Hex.encode(source)
        )

        val entrySource = ByteArray(0x214) { index -> ((index * 5 + 1) and 7).toByte() }
        val entrySeeds = builder6388f0FirstPairStreamSeedsFromEntrySourceAnd5bcf98Outputs(
            entrySource = entrySource,
            row0FirstOutput70 = row0FirstOutput70,
            row0SecondOutput70 = row0SecondOutput70,
            row59FirstOutput70 = row59FirstOutput70,
            row59SecondOutput70 = row59SecondOutput70,
            nullScalarWindow = seeds.nullScalarWindow,
            staticScalarWindow = seeds.staticScalarWindow,
            nullEntropy11A = seeds.nullEntropy11A,
            nullAttempts = seeds.nullAttempts
        )
        assertEquals(
            "e70d3f912b290b5bd31c6dd27e8816448c16863247354286fc66957bdf2a8e27",
            sha256Hex(entrySeeds.row0Out4)
        )
        assertContentEquals(entrySeeds.row0Out0, highSeeds.row0.out0)
        assertContentEquals(entrySeeds.row0Out1, highSeeds.row0.out1)
        assertContentEquals(entrySeeds.row59Out0, highSeeds.row59.out0)
        assertContentEquals(entrySeeds.row59Out1, highSeeds.row59.out1)

        val entrySeedsWithDerivedStatic = builder6388f0FirstPairStreamSeedsFromEntrySourceAnd5bcf98Outputs(
            entrySource = entrySource,
            row0FirstOutput70 = row0FirstOutput70,
            row0SecondOutput70 = row0SecondOutput70,
            row59FirstOutput70 = row59FirstOutput70,
            row59SecondOutput70 = row59SecondOutput70,
            nullScalarWindow = seeds.nullScalarWindow,
            nullEntropy11A = seeds.nullEntropy11A,
            nullAttempts = seeds.nullAttempts
        )
        assertContentEquals(entrySeedsWithDerivedStatic.row0Out4, entrySeeds.row0Out4)
        assertEquals(
            "f38d95844ac5834265c854266814ed9e67ce508eea912fc81a9b2d28db0ddd5e" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000",
            Hex.encode(entrySeedsWithDerivedStatic.staticScalarWindow)
        )

        val nullEntropy = ByteArray(0x11a) { index -> (index * 11 + 3).toByte() }
        val entropySeeds = builder6388f0FirstPairStreamSeedsFromEntropyAnd5bcf98Outputs(
            entrySource = entrySource,
            row0FirstOutput70 = row0FirstOutput70,
            row0SecondOutput70 = row0SecondOutput70,
            row59FirstOutput70 = row59FirstOutput70,
            row59SecondOutput70 = row59SecondOutput70,
            nullEntropy11A = nullEntropy
        )
        assertContentEquals(entropySeeds.nullEntropy11A, nullEntropy)
        assertEquals(1, entropySeeds.nullAttempts)
        assertEquals(
            "c4f2357511bf2071de2a5478a5d3d8a17c2b4da7b46c6cb46f4834ecb3a2f2ba",
            sha256Hex(entropySeeds.nullScalarWindow)
        )

        val retrySeeds = builder6388f0FirstPairStreamSeedsFromEntropySourceAnd5bcf98Outputs(
            entrySource = entrySource,
            row0FirstOutput70 = row0FirstOutput70,
            row0SecondOutput70 = row0SecondOutput70,
            row59FirstOutput70 = row59FirstOutput70,
            row59SecondOutput70 = row59SecondOutput70,
            maxAttempts = 4,
            entropySource = { requestedCount: Int ->
                assertEquals(requestedCount, 0x11a)
                nullEntropy
            }
        )
        // Swift compares the whole struct field-wise (Data == Data); the Kotlin
        // data class compares ByteArray fields by reference, so compare fields.
        assertContentEquals(retrySeeds.nullScalarWindow, entropySeeds.nullScalarWindow)
        assertContentEquals(retrySeeds.staticScalarWindow, entropySeeds.staticScalarWindow)
        assertContentEquals(retrySeeds.nullEntropy11A, entropySeeds.nullEntropy11A)
        assertEquals(retrySeeds.nullAttempts, entropySeeds.nullAttempts)
        assertContentEquals(retrySeeds.row0Out4, entropySeeds.row0Out4)
        assertContentEquals(retrySeeds.row0Out3, entropySeeds.row0Out3)
        assertContentEquals(retrySeeds.row0Out2, entropySeeds.row0Out2)
        assertContentEquals(retrySeeds.row0Out1, entropySeeds.row0Out1)
        assertContentEquals(retrySeeds.row0Out0, entropySeeds.row0Out0)
        assertContentEquals(retrySeeds.row59Out1, entropySeeds.row59Out1)
        assertContentEquals(retrySeeds.row59Out0, entropySeeds.row59Out0)

        val generatorPoint = Hex.decode(
            "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
                "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"
        )
        val sensorPointSeeds = builder6388f0FirstPairStreamSeedsFromEntropyAndSensorPoints(
            entrySource = entrySource,
            nullEntropy11A = nullEntropy,
            row0SensorPointXYBE = generatorPoint,
            row59SensorPointXYBE = generatorPoint
        )
        assertEquals(1, sensorPointSeeds.nullAttempts)
        assertContentEquals(sensorPointSeeds.nullEntropy11A, nullEntropy)
        assertEquals(
            "581f613027f3b91683819a69b62ac6b691103abc55a62825f0f6a889322c1269",
            sha256Hex(sensorPointSeeds.staticScalarWindow)
        )
        assertEquals(
            "fbc744031431d9fda2ceed80266ee2dfefb9a55e585ea5bbc2666a144379f042",
            sha256Hex(sensorPointSeeds.row0Out0)
        )
        assertEquals(
            "f9b223b45fe8ec5687cdcbd18218714f4b261938a4befb37e0ced7b42d012289",
            sha256Hex(sensorPointSeeds.row0Out1)
        )
        assertEquals(
            "4e9c7fbc86f08bf6293e64ffc1c7aad72df8289410f1928576d42e0062bbf929",
            sha256Hex(sensorPointSeeds.row59Out0)
        )
        assertEquals(
            "58f151f44d8166ab108b348e911e479fe881bb31598ae34e709a8f4bff586c80",
            sha256Hex(sensorPointSeeds.row59Out1)
        )

        val retryPointSeeds = builder6388f0FirstPairStreamSeedsFromEntropySourceAndSensorPoints(
            entrySource = entrySource,
            row0SensorPointXYBE = generatorPoint,
            row59SensorPointXYBE = generatorPoint,
            maxAttempts = 4,
            entropySource = { requestedCount: Int ->
                assertEquals(requestedCount, 0x11a)
                nullEntropy
            }
        )
        // Whole-struct equality in Swift compares fields by value; mirror that.
        assertContentEquals(retryPointSeeds.nullScalarWindow, sensorPointSeeds.nullScalarWindow)
        assertContentEquals(retryPointSeeds.staticScalarWindow, sensorPointSeeds.staticScalarWindow)
        assertContentEquals(retryPointSeeds.nullEntropy11A, sensorPointSeeds.nullEntropy11A)
        assertEquals(retryPointSeeds.nullAttempts, sensorPointSeeds.nullAttempts)
        assertContentEquals(retryPointSeeds.row0Out4, sensorPointSeeds.row0Out4)
        assertContentEquals(retryPointSeeds.row0Out3, sensorPointSeeds.row0Out3)
        assertContentEquals(retryPointSeeds.row0Out2, sensorPointSeeds.row0Out2)
        assertContentEquals(retryPointSeeds.row0Out1, sensorPointSeeds.row0Out1)
        assertContentEquals(retryPointSeeds.row0Out0, sensorPointSeeds.row0Out0)
        assertContentEquals(retryPointSeeds.row59Out1, sensorPointSeeds.row59Out1)
        assertContentEquals(retryPointSeeds.row59Out0, sensorPointSeeds.row59Out0)

        val sensorPointSource = deriveFrom6388f0FirstPairEntropyAndSensorPoints(
            entrySource = entrySource,
            nullEntropy11A = nullEntropy,
            row0SensorPointXYBE = generatorPoint,
            row59SensorPointXYBE = generatorPoint
        )
        assertEquals(
            "ef4495c4b868489d0b4a30546bbf3d3b3ef51498e314a792214092d50ea09f2f",
            sha256Hex(sensorPointSource)
        )
        assertEquals(
            "04040706000606020005070707050402050701070106000602000707060002050402" +
                "0407050605040400060004020400000106060102060205030303040600040606",
            Hex.encode(sensorPointSource)
        )

        val retryPointSource = deriveFrom6388f0FirstPairEntropySourceAndSensorPoints(
            entrySource = entrySource,
            row0SensorPointXYBE = generatorPoint,
            row59SensorPointXYBE = generatorPoint,
            maxAttempts = 4,
            entropySource = { requestedCount: Int ->
                assertEquals(requestedCount, 0x11a)
                nullEntropy
            }
        )
        assertContentEquals(retryPointSource, sensorPointSource)

        val sensorPointRawKey = phase5RawKeyFrom6388f0FirstPairEntropyAndSensorPoints(
            entrySource = entrySource,
            nullEntropy11A = nullEntropy,
            row0SensorPointXYBE = generatorPoint,
            row59SensorPointXYBE = generatorPoint
        )
        assertContentEquals(
            sensorPointRawKey,
            Phase5KeySchedule(ClasspathTablesSource()).deriveRawKey(input66 = sensorPointSource)
        )
    }

    @Test
    fun `testBuilder6388f0LowSeedCF0SeedsFromEntrySourceMatchesPythonReferenceVectors`() {
        val entrySource = ByteArray(0x214) { index -> ((index * 5 + 1) and 7).toByte() }
        val seeds = builder6388f0LowSeedCF0SeedsFromEntrySource(entrySource)
        assertEquals(0x10a, seeds.phase1.size)
        assertEquals(0x10a, seeds.phase2.size)
        assertEquals(0x10a, seeds.phase3.size)
        assertEquals(
            "9b2d39eb30062613f7ccf5520345c80937d149f06c42c3816f9547388745df3b",
            sha256Hex(seeds.phase1)
        )
        assertEquals(
            "a34a6cc968db094daaecfd303bbecdda322cd0e01c606c1705422f800dbf02fd",
            sha256Hex(seeds.phase2)
        )
        assertEquals(
            "c60ed79f4e6da3aa3768e10d95a2c5837708652bb7e2c06c1b91b09adcb2d451",
            sha256Hex(seeds.phase3)
        )
        assertEquals(
            "04020200010104050701060202000004",
            Hex.encode(seeds.phase3.copyOfRange(seeds.phase3.size - 16, seeds.phase3.size))
        )

        val pair = builder6388f0LowSeedTailPairFromEntrySource(entrySource)
        assertEquals(
            "2c31a9b72d1587155839611a00ebb6756ae34b459dbcbb10b7977ec6f2f85fa8",
            sha256Hex(pair.left)
        )
        assertEquals(
            "2da6677e738231f8eeeec117db576aaffd4c1c495efd72bf847bf15813ed1d4c",
            sha256Hex(pair.right)
        )
        val tailStage = builder6388f0LowSeedTailStageFromPair(pair)
        assertEquals(
            "05755c6dd9bc68d980beeef36392143524e6bbfcb20b270893509d57ebbc83a3",
            sha256Hex(tailStage)
        )
        val prelude = builder6388f0LowSeedPreludeSourceFromTailStage(tailStage)
        assertEquals(
            "e07c11f4368e33eb9812c3d31c186b76741a5b63eb7e77c1fd79a80dd680aaf2",
            sha256Hex(prelude)
        )
        val seedBlocks = builder6388f0LowSeedBlocksFromPreludeSource(prelude)
        assertEquals(20 * 16, seedBlocks.size)
        assertEquals(
            "77da5cce8122c3f8309100320249ddfc7d87e8a407e1699eb0d96032a3eb1283",
            sha256Hex(seedBlocks)
        )

        val loop = builder6388f0LowSeedLoopFromBlocks(seedBlocks)
        assertEquals("010103020202020202040402040402030502", Hex.encode(loop.final6377f0))
        assertEquals(
            listOf(0x27985d74u, 0x602c800bu, 0xb5823fb5u, 0x3b970a6fu),
            loop.scheduleWords.subList(0, 4)
        )
        assertEquals(
            listOf(0x1185db13u, 0x397e64c3u, 0xec257cd4u, 0x995e53ccu),
            loop.scheduleWords.subList(loop.scheduleWords.size - 4, loop.scheduleWords.size)
        )

        val preimages = builder6388f0Row0LowSeedPreimagesFromEntrySource(entrySource)
        assertEquals(
            "e70d3f912b290b5bd31c6dd27e8816448c16863247354286fc66957bdf2a8e27",
            sha256Hex(preimages.out4)
        )
        assertEquals(
            "feb5a841e9f99f5c149350296ffb74725c839af719015cfebfc2e1c01714acbc",
            sha256Hex(preimages.out3)
        )
        assertEquals(
            "236c8c5040f999f86bfa6bdfd7f9e8e3ee79ce19a6568cf75fd6ef58880bced2",
            sha256Hex(preimages.out2)
        )
    }

    @Test
    fun `testBuilder633fa8StaticScalarWindowFromEntrySourceMatchesPythonReferenceVector`() {
        val entrySource = ByteArray(0x214) { index -> ((index * 5 + 1) and 7).toByte() }
        val boundary = builder633fa8StaticTailBoundaryFromEntrySource(entrySource)
        assertEquals(
            "e07c11f4368e33eb9812c3d31c186b76741a5b63eb7e77c1fd79a80dd680aaf2",
            sha256Hex(boundary.preludeSource)
        )
        assertEquals(
            "9bb588ed741963c1ed0b32efab701fbd87819dfe65f9e0192e8830e8a7a7574d",
            sha256Hex(packUInt32LE(boundary.words3ab0))
        )
        assertEquals(
            "fe4e9fc8207e0cc3276f2cb073a8050bbaa842cd2b114165630eb8214fb30b01",
            sha256Hex(packUInt32LE(boundary.words3120))
        )
        assertEquals(
            "2acd8bebf1f8746c4d0c264f28cd42010725f116a4587b702b29b75b8fbb2052",
            sha256Hex(packUInt32LE(boundary.words2dfc))
        )
        assertEquals(0xb6ccf02833a9825eUL, boundary.seed3110)

        val scalar = builder633fa8StaticScalarWindowFromEntrySource(entrySource)
        assertEquals(
            "f38d95844ac5834265c854266814ed9e67ce508eea912fc81a9b2d28db0ddd5e" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000",
            Hex.encode(scalar)
        )
    }
}

/** SHA-256 of [bytes] as lowercase hex (mirrors Swift `SHA256.hash(data:).hex`). */
internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

/**
 * Mirrors the Swift private helper `make679f48Context(contextLength:blockIndex:)`
 * (FirstPairSourceSliceTests.swift L4468-4479).
 */
internal fun make679f48Context(contextLength: ULong, blockIndex: UInt): ByteArray {
    val bytes = ByteArray(0x20c) { index -> ((index * 7 + 3) and 7).toByte() }
    for (index in 0 until 8) {
        bytes[index] = ((contextLength shr (index * 8)).toInt() and 0xff).toByte()
    }
    for (index in 0 until 4) {
        bytes[0x110 + index] = ((blockIndex shr (index * 8)).toInt() and 0xff).toByte()
    }
    return bytes
}

/** Mirrors the Swift private helper `packUInt64LE` (FirstPairSourceSliceTests.swift L4481-4490). */
internal fun packUInt64LE(words: List<ULong>): ByteArray {
    val out = ByteArray(words.size * 8)
    words.forEachIndexed { wordIndex, word ->
        for (shift in 0 until 64 step 8) {
            out[wordIndex * 8 + shift / 8] = ((word shr shift).toInt() and 0xff).toByte()
        }
    }
    return out
}
