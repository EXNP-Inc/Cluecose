// 1:1 port of LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift`.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// This file covers Swift lines 1169-2889 (tests 22-38). Reuses the shared
// internal helpers from FirstPairSourceSliceKotlinTest.kt.
package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.ClasspathTablesSource
import com.dsikic.cluecose.core.util.Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FirstPairSourceSliceKotlinTest2 {

    companion object {
        init {
            FirstPairConfig.tablesSource = ClasspathTablesSource()
        }
    }

    @Test
    fun `testBuilder633fa8NullEntrySourcesAndInitialMatchPythonReferenceVector`() {
        val sources = builder633fa8NullEntrySourcesFromInvariantEntry()
        assertEquals(0x11a, sources.prologueSource.size)
        assertEquals(
            "b2b08a579ebd69e28c8bbb33b19317c3d4c22cce9ec2a6d60eb81e49c7729115",
            sha256Hex(sources.prologueSource)
        )
        assertEquals(
            "4b29cac325304080f0e7b82a92ffe3ef9c3e252aac748ab6e776673fe7e73db6",
            sha256Hex(packUInt32LE(sources.check1SourceWords))
        )
        assertEquals(
            "8905c6b8cd1d1fec875c168212d3555909e4554ee63432a54744a404db243e4d",
            sha256Hex(packUInt32LE(sources.check2SourceWords))
        )

        val entropy = ByteArray(0x11a) { index -> (index * 11 + 3).toByte() }
        val initial = builder633fa8NullInitialFromEntropy(
            entropy11A = entropy,
            prologueSource = sources.prologueSource
        )
        assertEquals(
            "23db0a42e5599a320a6384094203a1ecf34a1f7517c94c7fd47267708c760834",
            sha256Hex(initial.maskedEntropy)
        )
        assertEquals(
            "9f50c4c539508ffd0d5a87b37d60997bb1622db044b55662c4d1d6d8bad6f532",
            sha256Hex(initial.cf0)
        )
        assertEquals(
            "0d0d59d1394d720b3d30d2a5f0ae4af4e811d2c9c690767b95d603883164cba5",
            sha256Hex(initial.e10)
        )
        assertEquals(
            "296b545eb6c3114d4b731abf59bbcb43e1e34b321e787683228ceb02c11d9cc2",
            sha256Hex(initial.seedInputs)
        )
        assertEquals(
            "30f124b2c0d6cd19c0bbf4e4f8cf1974e5766ed3171e9556e69979108e74626f",
            sha256Hex(initial.seedBlocks)
        )

        val loop = builder633fa8NullFirstLoopFromBlocks(initial.seedBlocks)
        assertEquals("060504020202040404020204040404010504", Hex.encode(loop.finalTLane))
        assertEquals(
            "652ce3a7810e6b09bf6ce92f7029f7a79599a95db235538aea7e84bec65e21f0",
            sha256Hex(packUInt32LE(loop.scheduleWords))
        )
        assertEquals(
            listOf(0x77de69c8u, 0xc857bd48u, 0x65000b63u, 0xa6ddb53bu),
            loop.scheduleWords.subList(0, 4)
        )
        assertEquals(
            listOf(0x7c13a2ceu, 0xe082b5bau, 0xbfaf4d29u, 0xc67887e7u),
            loop.scheduleWords.subList(loop.scheduleWords.size - 4, loop.scheduleWords.size)
        )

        val acceptance = builder633fa8NullScheduleAcceptance(
            scheduleWords = loop.scheduleWords,
            check1SourceWords = sources.check1SourceWords,
            check2SourceWords = sources.check2SourceWords
        )
        assertTrue(acceptance.firstOK)
        assertTrue(acceptance.secondOK)

        val rejectedWords = loop.scheduleWords.toMutableList()
        val rejectedIndex = 19
        rejectedWords[rejectedIndex] = rejectedWords[rejectedIndex] xor 1u
        val rejected = builder633fa8NullScheduleAcceptance(
            scheduleWords = rejectedWords,
            check1SourceWords = sources.check1SourceWords,
            check2SourceWords = sources.check2SourceWords
        )
        assertFalse(rejected.firstOK)
        assertFalse(rejected.secondOK)

        val postAccept = builder633fa8NullPostAcceptBlocks(
            scheduleWords = loop.scheduleWords
        )
        assertEquals(20 * 16, postAccept.blocks4080.size)
        assertEquals(20 * 16, postAccept.blocks3f40.size)
        assertEquals(
            "a8732537d6be3b54f8d00663ae3d0461ed7974b6861b1095b0d16730c08f9c86",
            sha256Hex(postAccept.blocks4080)
        )
        assertEquals(
            "8975ea6381dc1f9149d202522d21abf7105e2faf2888a306b5122d3c8f6f0b7c",
            sha256Hex(postAccept.blocks3f40)
        )
        assertEquals(
            "01070705070306040206010301020603",
            Hex.encode(postAccept.blocks4080.copyOfRange(0, 16))
        )
        assertEquals(
            "02030203010200000706020600030203",
            Hex.encode(postAccept.blocks3f40.copyOfRange(0, 16))
        )

        val prelude = builder633fa8NullPreludeSourceFromPostAccept(
            blocks4080 = postAccept.blocks4080,
            blocks3f40 = postAccept.blocks3f40
        )
        assertEquals(0x10a, prelude.size)
        assertEquals(
            "ed4e5c29dff15da45590bf9bc4ea8b7124af32f51f3add5e786cf77fd36c747d",
            sha256Hex(prelude)
        )
        assertEquals(
            "05000204070405070006020301060606",
            Hex.encode(prelude.copyOfRange(0, 16))
        )
        assertEquals(
            "06040206050602040404020605060204",
            Hex.encode(prelude.copyOfRange(prelude.size - 16, prelude.size))
        )

        val entropyPrelude = builder633fa8NullPreludeSourceFromEntropy(
            entropy11A = entropy
        )
        assertContentEquals(prelude, entropyPrelude)

        val scalar = builder633fa8NullScalarWindowFromEntropy(
            entropy11A = entropy
        )
        assertEquals(
            "c4f2357511bf2071de2a5478a5d3d8a17c2b4da7b46c6cb46f4834ecb3a2f2ba",
            sha256Hex(scalar)
        )
        assertEquals(
            "3b588dd68f20da5f883993332cabcda6576645712cdd039d0a8195f4b1c0b52e" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000",
            Hex.encode(scalar)
        )
        assertContentEquals(scalar, builder633fa8ScalarWindowFromPreludeSource(prelude))

        var entropyCalls = 0
        val retryResult = builder633fa8NullScalarWindowFromEntropySource(
            maxAttempts = 3
        ) { requestedCount ->
            entropyCalls += 1
            assertEquals(0x11a, requestedCount)
            entropy
        }
        assertEquals(1, entropyCalls)
        assertContentEquals(scalar, retryResult.scalarWindow)
        assertContentEquals(entropy, retryResult.entropy11A)
        assertEquals(1, retryResult.attempts)
        val error = assertFailsWith<FirstPairSourceSliceError> {
            builder633fa8NullScalarWindowFromEntropySource(maxAttempts = 0) { entropy }
        }
        assertEquals(FirstPairSourceSliceError.invalid633fa8NullMaxAttempts(0), error)
    }

    @Test
    fun `testBuilder633fa8TailQwordsFromSourcesMatchesPythonReferenceVector`() {
        val words3ab0 = listOf(
            0x561f0a13u, 0x2703b81fu, 0xc60ebb71u, 0x13ae9923u, 0x6151794du,
            0xcbd488b3u, 0x105a57bau, 0xbe270b51u, 0x35178421u, 0x9c1e6b02u,
            0x8131d744u, 0x995e53ccu, 0xe98d93e2u, 0xbcf84415u, 0xbfccce8eu,
            0x6c32338cu, 0xd608b5a1u, 0xe7c2db10u, 0x8131d744u, 0x995e53ccu
        )
        val words3120 = listOf(
            0xb33842d7u, 0x7b6ba784u, 0xa2f90f36u, 0xde5e2ad7u, 0x3c3537a9u,
            0x81d564f6u, 0x339ab4a2u, 0x999de03bu, 0x56c13b42u, 0xff14a487u,
            0x5a31640cu, 0xc3f85236u, 0x3c1dc79eu, 0x58a8d4a6u, 0x541cb00eu,
            0x63323fcdu, 0x1aa54a16u, 0x01f1b661u, 0x5a31640cu, 0xc3f85236u
        )
        val words2dfc = listOf(
            0x9bed19fdu, 0xc70a4d0fu, 0x8257d22bu, 0xe2fafcb3u, 0x02c77d20u,
            0xb5ed0efau, 0x878c1b06u, 0x4bd92d7du, 0x21c6944fu, 0xd3ec5d2fu,
            0x876fda86u, 0x37f3e22au, 0x3cfcd7ceu, 0xabdc16ebu, 0x84ad2f7du,
            0x4bd92d7du, 0xf647adceu, 0xaa7b701eu, 0x876fda86u, 0x37f3e22au
        )
        val qwords = builder633fa8TailQwordsFromSources(
            words3ab0 = words3ab0,
            words3120 = words3120,
            words2dfc = words2dfc,
            seed3110 = 0xb6ccf02833a9825euL
        )
        assertEquals(
            listOf(
                0x278653e978fb8d86uL, 0x01531105e76d5345uL, 0x6ca239d879644a5cuL, 0xa06b5f9758fb4bd5uL,
                0xd4aba6030256919auL, 0x701b8d245771a9c8uL, 0x25f9e61e7612a2cbuL, 0x42af4c71aeed4949uL,
                0xf69e5c8932e52f6cuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
                0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
                0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL
            ),
            qwords
        )
        assertEquals(
            "8718a3b565f0e38d8631d894877d72c491cfaa21abccc8958829a7b0ca97b15d",
            sha256Hex(packUInt64LE(qwords))
        )
        val e10Words = builder633fa8E10WordsFromTailQwords(qwords)
        assertEquals(
            "4532bea83bfdabcf74fdaeeb0319a83c051a31e40a620e3bd0db1cd993ed8522" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000",
            Hex.encode(builder633fa8ScalarWindowFromE10Words(e10Words))
        )
    }

    @Test
    fun `testBuilder633fa8E10WordsFromTailQwordsMatchesPythonReferenceVector`() {
        val tailQwords = listOf(
            0x278653e978fb8d86uL, 0x01531105e76d5345uL, 0x6ca239d879644a5cuL, 0xa06b5f9758fb4bd5uL,
            0xd4aba6030256919auL, 0x701b8d245771a9c8uL, 0x25f9e61e7612a2cbuL, 0x42af4c71aeed4949uL,
            0xf69e5c8932e52f6cuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
            0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL,
            0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL, 0x7785655189e16a0fuL
        )
        val words = builder633fa8E10WordsFromTailQwords(tailQwords)
        assertEquals(
            listOf(
                0x5a1e4b39u, 0x5e9483afu, 0xcf48138fu, 0x9e28b8cdu, 0x55b48903u,
                0xdefd3261u, 0x2c462f90u, 0x5d22446du, 0x5170b893u, 0xdcd2fa37u,
                0xfaacce40u, 0x997a6babu, 0x7781207bu, 0x182c4538u, 0x5475ee9au,
                0xf1fd3b9cu, 0x8281f8c2u, 0x0ba21025u, 0xfaacce40u, 0x997a6babu
            ),
            words
        )
        assertEquals(
            "4f7646b6cb17189560193adc7b951d47443edf292ea8213d2481cd8c89ba79a9",
            sha256Hex(packUInt32LE(words))
        )
        assertEquals(
            "4532bea83bfdabcf74fdaeeb0319a83c051a31e40a620e3bd0db1cd993ed8522" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000",
            Hex.encode(builder633fa8ScalarWindowFromE10Words(words))
        )
    }

    @Test
    fun `testBuilder633fa8ScalarWindowFromE10WordsMatchesPythonReferenceVector`() {
        val e10Words = listOf(
            0xf15eecb3u, 0x6c31d20du, 0x7a812282u, 0x88c66764u, 0xc7daeb98u,
            0xcb55b447u, 0x7dc4c98au, 0xe8533b12u, 0x3976a2b8u, 0x39a2c9bdu,
            0xa7ca28eau, 0x6e74c495u, 0x06708db4u, 0x5a2caf42u, 0xedb8643du,
            0xd19d3544u, 0x8281f8c2u, 0x0ba21025u, 0xfaacce40u, 0x997a6babu
        )
        val scalar = builder633fa8ScalarWindowFromE10Words(e10Words)
        assertEquals(70, scalar.size)
        assertEquals(
            "f38d95844ac5834265c854266814d19822125ef87edcfcab64db2fd1a3b4b0e7a" +
                "d6a1fa15f51ce7eea7853023be2e9ecb5a99876f7a8a0e00000000000000000000000000000",
            Hex.encode(scalar)
        )
        assertEquals(
            "af6aea9e701fb090af64b2446d8ccdef01327837f264bbe65b20db784345fa16",
            sha256Hex(scalar)
        )
    }

    @Test
    fun `testBuilder6388f0CallerContextResourcesMatchPythonReferenceVectors`() {
        val shared = builder6388f0SharedContextFromBundle()
        assertEquals(0x520, shared.size)
        assertEquals(
            "ef3f9995fade12005f0f4410bc1ffa23a03412851e31503042e149da302f2dac",
            sha256Hex(shared)
        )
        assertEquals(
            "21ed7e8fc9862976ac50b4cb1e31a91f30fa05c70682ac26bc7db76219fd1d35",
            Hex.encode(shared.copyOfRange(0, 32))
        )
        assertEquals(
            "4a411f4b3cf073011ded82b57188f50f977c1b57e3fbc2051c7577ffbb255fc9",
            Hex.encode(shared.copyOfRange(shared.size - 32, shared.size))
        )

        val tables = builder6388f0CallerLoopTablesFromBundle()
        assertEquals(59 * 0x58, tables.first.size)
        assertEquals(59 * 0x58, tables.second.size)
        assertEquals(
            "08e40f696924cbde7e31db9c9102d071f1d17a0a60a17f58768b01f5ec067d35",
            sha256Hex(tables.first)
        )
        assertEquals(
            "db7c3afca9d52301c0064bb894889a5e8c592cf871412afd4a411f5dad1b1a64",
            Hex.encode(tables.first.copyOfRange(0, 32))
        )
        assertEquals(
            "4a411f4b3cf073011ded82b57188f50f977c1b57e3fbc2051c7577ffbb255fc9",
            Hex.encode(tables.first.copyOfRange(tables.first.size - 32, tables.first.size))
        )
        assertEquals(
            "6471d5ae1bc99ec976683bc2e568af44b58f900cf0242439b9626d69cb54ec65",
            sha256Hex(tables.second)
        )
        assertEquals(
            "bfcf00c8b7ecf353653481454ad35d2054aae79357422e128e2024529ca00ce0",
            Hex.encode(tables.second.copyOfRange(0, 32))
        )
        assertEquals(
            "fccee0a880f009f9e121349c190bb74368363d144a33c2dd9112d90aeb6e5f5d",
            Hex.encode(tables.second.copyOfRange(tables.second.size - 32, tables.second.size))
        )

        val context = builder6388f0CallerContextFromLoopTables(tables)
        assertEquals(0x2d58, context.size)
        assertEquals(
            "f5059c7c440707b8bdc08c309540e629e109e78941406442a7d189f5c23fbe5f",
            sha256Hex(context)
        )
        assertEquals(
            "21ed7e8fc9862976ac50b4cb1e31a91f30fa05c70682ac26bc7db76219fd1d35",
            Hex.encode(context.copyOfRange(0, 32))
        )
        assertEquals(
            "fccee0a880f009f9e121349c190bb74368363d144a33c2dd9112d90aeb6e5f5d",
            Hex.encode(context.copyOfRange(context.size - 32, context.size))
        )
        assertContentEquals(
            tables.first,
            context.copyOfRange(0x4c8, 0x4c8 + tables.first.size)
        )
        assertContentEquals(
            tables.second,
            context.copyOfRange(0x1910, 0x1910 + tables.second.size)
        )
        assertContentEquals(context, builder6388f0CallerContextFromBundle())
    }

    @Test
    fun `testBuilder642f60InitialStagesMatchPythonReferenceVectors`() {
        val x1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val x0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val x2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val output = ByteArray(88) { index -> (index * 17 + 4).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 19 + 3).toByte() }
        val scalar = 0x0fedcba987654321uL

        val sp2a8 = builder642f60StageSP2A8WordsFromX1(x1)
        assertEquals(
            "8f828a7da4493c59a03f2e57e5040ba00114d22495ebc08a9bb71d06bdc115d5",
            sha256Hex(packUInt32LE(sp2a8))
        )
        assertEquals(
            listOf(0x4a545152u, 0xeb8ceacdu, 0x6ee8542cu, 0x8f614dc2u),
            sp2a8.subList(0, 4)
        )
        assertEquals(
            listOf(0x9da005cbu, 0x375c139au, 0xc085fb32u, 0x8542933bu),
            sp2a8.subList(sp2a8.size - 4, sp2a8.size)
        )
        val firstWorkspace = builder642f60First64bd0cWorkspaceFromX1(
            x1Source = x1,
            sp2a8Words = sp2a8
        )
        assertEquals(44 * 8, firstWorkspace.size)
        assertEquals(
            "71999b7359489904962a33e87d4f7d7b045b1d59b6056d2031c3039df85b9003",
            sha256Hex(firstWorkspace)
        )
        assertEquals(
            "086cb880065cb85808ad97f53a270ada48ff6b096bd1aa3ea0268718ad4e3ddf",
            Hex.encode(firstWorkspace.copyOfRange(0, 32))
        )
        assertEquals(
            "0c413ff42dbed2247aaebca271a82607684e5068580db306084c720cc23e1703",
            Hex.encode(firstWorkspace.copyOfRange(firstWorkspace.size - 32, firstWorkspace.size))
        )
        val arg0Words = builder64bd0cArg0U64Words(arg0 = arg0)
        assertEquals(
            "959177503b2023288b94d3167c99cd47f8e7fc6f3449aa7f20f3a461d18251f9",
            sha256Hex(packUInt64LE(arg0Words))
        )
        val updated64bd0c = builder64bd0cWorkspaceAfterUpdate(
            arg0U64Words = arg0Words,
            scalar = scalar,
            x2Workspace = firstWorkspace
        )
        assertEquals(
            "bb1551ac44a49ebd22583cb1eb07bc918a8b522172e049092b8596fc7ab2bf29",
            sha256Hex(updated64bd0c)
        )
        val output64bd0c = builder64bd0cFinalU32Words(x2Workspace = updated64bd0c)
        assertEquals(
            "5d3ef1f04a3b810f276531edc05b4901e6ec17dca864503d7ee38c1dae9c5a6e",
            sha256Hex(packUInt32LE(output64bd0c))
        )
        assertEquals(
            output64bd0c,
            builder64bd0cOutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = firstWorkspace
            )
        )

        val sp1f8 = builder642f60StageSP1F8WordsFromX0(x0)
        assertEquals(
            "3fd4edb09dc91b6ddc9ad925775420a371151828eac3ee32b1e0e92932651e57",
            sha256Hex(packUInt32LE(sp1f8))
        )
        assertEquals(
            listOf(0x69e6983eu, 0x52adf2b0u, 0x9c3e0b1cu, 0xce05e1cdu),
            sp1f8.subList(0, 4)
        )
        assertEquals(
            listOf(0xf8aada36u, 0x50d2d9efu, 0x8d23cbd4u, 0x222c26acu),
            sp1f8.subList(sp1f8.size - 4, sp1f8.size)
        )
        val sp300FromFirstOutput = builder642f60StageSP300WordsFrom64bd0cOutput(
            packUInt32LE(output64bd0c)
        )
        val secondWorkspace = builder642f60Second64bd0cWorkspace(
            sp1f8Words = sp1f8,
            sp300Words = sp300FromFirstOutput
        )
        assertEquals(44 * 8, secondWorkspace.size)
        assertEquals(
            "ca10191ddcf021d37fe567b3840c74edd67cb2d8eb6f93e062ede78437ac75cd",
            sha256Hex(secondWorkspace)
        )
        assertEquals(
            "b471e2fe79a9a301ac050344d103e81f5a31686d8112adeef233681d13dc7244",
            Hex.encode(secondWorkspace.copyOfRange(0, 32))
        )
        assertEquals(
            "89dee183dabe10816ffdfd9fdd8fb8a0eac33fd506ace277084c720cc23e1703",
            Hex.encode(secondWorkspace.copyOfRange(secondWorkspace.size - 32, secondWorkspace.size))
        )
        val secondOutput = builder64bd0cOutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = secondWorkspace
        )
        val sp250FromSecondOutput = builder642f60StageSP250WordsFrom64bd0cOutput(
            packUInt32LE(secondOutput)
        )
        assertEquals(
            "589183582eca59a369e4539a9af3447aa94a9254685aa95ec7b6425ae70cb98b",
            sha256Hex(packUInt32LE(sp250FromSecondOutput))
        )

        val thirdWorkspace = builder642f60Third64bd0cWorkspaceFromX2(x2)
        assertEquals(44 * 8, thirdWorkspace.size)
        assertEquals(
            "eb364923c9af8081354fdb867b420b25a7671cd6ad29be6a59d6f42575c64b78",
            sha256Hex(thirdWorkspace)
        )
        assertEquals(
            "0c104df2b29622434c82979a125bceab958df21121cfd218fe9b83f1886f08e8",
            Hex.encode(thirdWorkspace.copyOfRange(0, 32))
        )
        assertEquals(
            "8c4da37cf84d2509e8b7b9e083eec7ba482c27a7e28df965084c720cc23e1703",
            Hex.encode(thirdWorkspace.copyOfRange(thirdWorkspace.size - 32, thirdWorkspace.size))
        )
        val thirdOutput = builder64bd0cOutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = thirdWorkspace
        )
        assertEquals(
            "eb48d72aa8e50042313ee7997d44bac8913c326beaf560c1526172eb65aab723",
            sha256Hex(packUInt32LE(thirdOutput))
        )
        val sp148FromThirdOutput = builder642f60StageSP148WordsFrom64bd0cOutput(
            packUInt32LE(thirdOutput)
        )
        assertEquals(
            "dc2f907533997ec0281f478799f738cedb091dd4823583aca78fb29b735ab721",
            sha256Hex(packUInt32LE(sp148FromThirdOutput))
        )
        val fourthWorkspace = builder642f60Fourth64bd0cWorkspace(
            sp148Words = sp148FromThirdOutput
        )
        assertEquals(44 * 8, fourthWorkspace.size)
        assertEquals(
            "4e05b824f913c717b12674a3aab7228c7da82df22939cf9d5aaa17067e2fff63",
            sha256Hex(fourthWorkspace)
        )
        assertEquals(
            "0cffa70f53d9c80d14057382fc257a18b1b8a01412c2938bd8d3ffdcc1cb8d6a",
            Hex.encode(fourthWorkspace.copyOfRange(0, 32))
        )
        assertEquals(
            "a04db05d65de38a4488b463bf601890618d225871af87a14084c720cc23e1703",
            Hex.encode(fourthWorkspace.copyOfRange(fourthWorkspace.size - 32, fourthWorkspace.size))
        )
        val fourthOutput = builder64bd0cOutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = fourthWorkspace
        )
        assertEquals(
            "eb1a69a95b8180f875b7ed6f2e5445b5144eac396c6d2f736dfd52472971aab7",
            sha256Hex(packUInt32LE(fourthOutput))
        )
        val spf0FromFourthOutput = builder642f60StageSPF0WordsFrom64bd0cOutput(
            packUInt32LE(fourthOutput)
        )
        assertEquals(
            "5e40c800bafaf359c2f85016931771aab6bed679314cd57d6b93825ff824b9e1",
            sha256Hex(packUInt32LE(spf0FromFourthOutput))
        )
        val midSPA90 = builder642f60MidStageSPA90WordsFromX0(x0)
        assertEquals(22, midSPA90.size)
        assertEquals(
            "aac4238d8bbb4b8919022d2808a5f78b6b8dfe8b2e65f74bf02e8e4e9c8e8589",
            sha256Hex(packUInt64LE(midSPA90))
        )
        val midSP40 = builder642f60MidStageSP40WordsFromSPA90(midSPA90)
        assertEquals(44, midSP40.size)
        assertEquals(
            "1504c65189554c49926041804dd19ae228fba4c1e385c12314f75bbec25bc375",
            sha256Hex(packUInt32LE(midSP40))
        )
        assertEquals(
            listOf(0x35529dd1u, 0x72007bb1u, 0xb3df1669u, 0x5db9b4dcu),
            midSP40.subList(0, 4)
        )
        assertEquals(
            listOf(0x6c8a982fu, 0x5f4c42a3u, 0x4f51060du, 0x99fb2d87u),
            midSP40.subList(midSP40.size - 4, midSP40.size)
        )

        val midStreams = builder642f60MidStageStreamsFromContextSPF0(
            contextSource = builder6388f0SharedContextFromBundle(),
            spf0Words = spf0FromFourthOutput
        )
        assertEquals(
            "4612a77fcc1862e03e8f9071ed96021fd8278d9e4b759902f9d708d93f015518",
            sha256Hex(packUInt64LE(midStreams.first))
        )
        assertEquals(
            "bd332ed27e750a48e1253148b35979fd033779dd54687278031cdf38487ec1b4",
            sha256Hex(packUInt64LE(midStreams.second))
        )
        assertEquals(
            "1060d4058c115741df2d3c32ecc5b72d07f4d51a0feab492c54d3c05cd9cac3b",
            sha256Hex(packUInt64LE(midStreams.third))
        )
        assertEquals(
            "dc97df1d2bb966138bd3636c12fd741949ed0b64be4ad80d03846a375a5fc19c",
            sha256Hex(packUInt64LE(midStreams.fourth))
        )
        val midSP670 = builder642f60MidStageSP670Words(
            spa90Words = midStreams.first,
            sp510Prefix = midStreams.second,
            sp880Words = midStreams.third,
            sp9e0Prefix = midStreams.fourth
        )
        assertEquals(44, midSP670.size)
        assertEquals(
            "7022e51b4a7f4e8cf3efaf9a22df2490fe16cd52af14345dda8bdfba274913c7",
            sha256Hex(packUInt64LE(midSP670))
        )
        val midSP40B = builder642f60MidStageSPA90SP880FromSP40(
            sp40Words = midSP40
        )
        assertEquals(0x9b3fe2a5f2a431c6uL, midSP40B.third)
        assertEquals(
            "704129f7d2f099279c16bdc46558950398e440a5696c0d3ce486af146f571c16",
            sha256Hex(packUInt64LE(midSP40B.first))
        )
        assertEquals(
            "0c2f3db5097284b92a2c9e9c657723251e88d0404d5557a2082a954bb479b6c2",
            sha256Hex(packUInt64LE(midSP40B.second))
        )
        val midStatic = builder642f60MidStageStaticSP9E0SP7D0(
            sideInit = midSP40B.third
        )
        assertEquals(
            "940228bc7962e67ba2baac5819a3e5fd6f97d4040f6a36f4a962e9d7e71165b8",
            sha256Hex(packUInt64LE(midStatic.first))
        )
        assertEquals(
            "2f9bd217712c0037a4bb0dc7f6428debe9f47d104e394b4aee09c39bf4e3b13f",
            sha256Hex(packUInt64LE(midStatic.second))
        )
        val midSP510 = builder642f60MidStageSP510Words(
            spa90Words = midSP40B.first,
            sp880Prefix = midSP40B.second,
            sp9e0Words = midStatic.first,
            sp7d0Prefix = midStatic.second
        )
        assertEquals(
            "0ec28196c798b8373f978ff6f23daca738c2743fcb3bd6a815fa80e41517e0a1",
            sha256Hex(packUInt64LE(midSP510))
        )
        val fifthWorkspace = builder642f60MidFifth64bd0cWorkspace(
            sp670Words = midSP670,
            sp510Words = midSP510
        )
        assertEquals(44 * 8, fifthWorkspace.size)
        assertEquals(
            "b82a12c1bc5b397f1408b510140ebaf5fc4ceedaedf8b39ae1b83cb406ae246d",
            sha256Hex(fifthWorkspace)
        )
        val fifthOutput = builder64bd0cOutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = fifthWorkspace
        )
        assertEquals(
            "ca6c0bf7532688d60636480d3ff0baed7a7bbb9c14b6f5c102716808baf8551f",
            sha256Hex(packUInt32LE(fifthOutput))
        )
        val sp1a0FromFifthOutput = builder642f60StageSP1A0WordsFrom64bd0cOutput(
            packUInt32LE(fifthOutput)
        )
        assertEquals(
            "faf7f3a4cb372cf5e9c5428cdd2cac3737321290d618c099447201017a2065bd",
            sha256Hex(packUInt32LE(sp1a0FromFifthOutput))
        )
        val sixthStreams = builder642f60SixthStreamsFromSP1A0(
            sp1a0Words = sp1a0FromFifthOutput
        )
        assertEquals(
            "e9c19157950aad3351142adeb2f1fed3804280bf27f8d8b3001f65f5448d1780",
            sha256Hex(packUInt64LE(sixthStreams.first))
        )
        assertEquals(
            "d59a1bea1b4b21a4b33acfc7e6d1ebc349e357a447a4c82fe30c4116ce76075f",
            sha256Hex(packUInt64LE(sixthStreams.second))
        )
        assertEquals(
            "c908be53c01f80bf8c7e4f487a4fecf5fc989aeb62a5a49de086021d946025de",
            sha256Hex(packUInt64LE(sixthStreams.third))
        )
        assertEquals(
            "f2ed82df4b86e6610cddb46a12ca466b28cb7fb8b51e8d48b398729af7b77b81",
            sha256Hex(packUInt64LE(sixthStreams.fourth))
        )
        val sixthWorkspace = builder642f60Sixth64bd0cWorkspace(
            spa90Words = sixthStreams.first,
            sp670Prefix = sixthStreams.second,
            sp880Words = sixthStreams.third,
            sp510Prefix = sixthStreams.fourth
        )
        assertEquals(44 * 8, sixthWorkspace.size)
        assertEquals(
            "f3f92c8a7b28e42606d05893406c61b98ba68869d51dd5886326b27137fdec19",
            sha256Hex(sixthWorkspace)
        )
        val sixthOutput = builder64bd0cOutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = sixthWorkspace
        )
        assertEquals(
            "d5fb1d1b7a140d9fb9530ab11c3e748b7c2cb6a98caa11dcb29027b59aef43ef",
            sha256Hex(packUInt32LE(sixthOutput))
        )
        val out0Source = builder642f60Out0SourceWords(
            sixthOutput = packUInt32LE(sixthOutput),
            contextSource = builder6388f0SharedContextFromBundle(),
            sp250Words = sp250FromSecondOutput
        )
        assertEquals(
            "a786ef481ee4d3a137fb62b582fd2a0a25b067aa747b8e119e6dccb64f3d7931",
            sha256Hex(packUInt32LE(out0Source))
        )
        val out0 = builder642f60Out0WordsFromSource(out0Source)
        assertEquals(
            "ec26506de5e68c3fd206e6ec3d809c9425c8a48e2f7033bfab37f639b2d39ee6",
            sha256Hex(packUInt32LE(out0))
        )
        val seventhSource = builder642f60SeventhSourceWords(
            sp250Words = sp250FromSecondOutput,
            contextSource = builder6388f0SharedContextFromBundle(),
            out0Words = out0
        )
        assertEquals(
            "a2e9303b9b5b9b3d6724efd2270260e25499279966cb839934468d34a4a7a3f2",
            sha256Hex(packUInt32LE(seventhSource))
        )
        val seventhSP148 = builder642f60SeventhStageSP148WordsFromSource(
            seventhSource
        )
        assertEquals(
            "95259f0656efd4da682c6d44b3e75fbd5e0255b2cf4deb18fda24c314361f960",
            sha256Hex(packUInt32LE(seventhSP148))
        )
        val seventhStreams = builder642f60SeventhStreams(
            sp1a0Words = sp1a0FromFifthOutput,
            sp148Words = seventhSP148
        )
        assertEquals(
            "c994283392c158405962b94a3a3d1151c00a1697dda6078880de0549c0ba0ba4",
            sha256Hex(packUInt64LE(seventhStreams.first))
        )
        assertEquals(
            "ad186b4c6c314109cc76496bb2cfd20d4c9ce4b6cb46eabe2fa8bfedece4370d",
            sha256Hex(packUInt64LE(seventhStreams.second))
        )
        assertEquals(
            "9fb2e0f00e7464c47a64ffaa0e82e40b469579a7a95b8de1476b5e219788e21f",
            sha256Hex(packUInt64LE(seventhStreams.third))
        )
        assertEquals(
            "1f9b41793357ab8779a27573de0b8798a97fc3873a9936359e3fd218d7a3d410",
            sha256Hex(packUInt64LE(seventhStreams.fourth))
        )
        val seventhSP9E0 = builder642f60SeventhSP9E0Words(
            sp670Words = seventhStreams.first,
            spa90Prefix = seventhStreams.second,
            sp510Words = seventhStreams.third,
            sp880Prefix = seventhStreams.fourth
        )
        assertEquals(
            "e37413004fb514f85c75729da018412b5c415c9380bda9d0b719fea6db31f3a6",
            sha256Hex(packUInt32LE(seventhSP9E0))
        )
        assertEquals(
            listOf(0x5f6dd1e1u, 0xabcb1928u, 0xa1967cd8u, 0xfeb29d92u),
            seventhSP9E0.subList(0, 4)
        )
        assertEquals(
            listOf(0xdcab123eu, 0x4b8612e8u, 0x02abe7d2u, 0x7a2fefddu),
            seventhSP9E0.subList(seventhSP9E0.size - 4, seventhSP9E0.size)
        )
        val seventhSPA90 = builder642f60SeventhSPA90WordsFromSP300(
            sp300FromFirstOutput
        )
        assertEquals(
            "d6b753435f42d5c24fbb383ff1b3ee1b5fd1aad791a4ef8692902747f0e72997",
            sha256Hex(packUInt64LE(seventhSPA90))
        )
        val seventhSP7D0 = builder642f60SeventhSP7D0WordsFromSPA90(
            seventhSPA90
        )
        assertEquals(
            "3e59a9f321203b7f35d979f45eebfea1886d4cfad5949626bc2664bcd875c748",
            sha256Hex(packUInt32LE(seventhSP7D0))
        )
        val seventhSource44 = builder642f60SeventhSource44Words(
            sp9e0Words = seventhSP9E0,
            contextSource = builder6388f0SharedContextFromBundle(),
            sp7d0Words = seventhSP7D0
        )
        assertEquals(
            "0dc439079188a120ee22095c559c1cc42fff70dd25c14986b09348d215f27b6f",
            sha256Hex(packUInt32LE(seventhSource44))
        )
        val seventhSP40 = builder642f60SeventhSP40WordsFromSource44(
            seventhSource44
        )
        assertEquals(
            "3b9636afe859f7dac63be47e1b5f8ba67ac0b08c025b311d0a155aba9ddd77aa",
            sha256Hex(packUInt32LE(seventhSP40))
        )
        val seventhWorkspace = builder642f60Seventh64bd0cWorkspace(
            sp40Words = seventhSP40
        )
        assertEquals(44 * 8, seventhWorkspace.size)
        assertEquals(
            "622ff09e3ef098d358adda7b57e061abd86c5a5cbc7d5c3f25452330f75ef9cb",
            sha256Hex(seventhWorkspace)
        )
        val seventhOutput = builder64bd0cOutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = seventhWorkspace
        )
        assertEquals(
            "3a822a7f1a90cf5cf5ae217a07640d66269e95f0be53dbbb607f486279ecbc9c",
            sha256Hex(packUInt32LE(seventhOutput))
        )
        val out1 = builder642f60Out1WordsFrom64bd0cOutput(
            packUInt32LE(seventhOutput)
        )
        assertEquals(
            "3e6436605d188c9ced00cd0accd72edf521d96eec87a3ef9527c911c19cb5b0f",
            sha256Hex(packUInt32LE(out1))
        )
        assertEquals(
            listOf(0xce8a9be4u, 0xc663f93du, 0xf77e4c6au, 0x657aca3eu),
            out1.subList(0, 4)
        )
        assertEquals(
            listOf(0x38cb4c20u, 0xd773b033u, 0x6e86dad6u, 0x6a737805u),
            out1.subList(out1.size - 4, out1.size)
        )
        val eighthStreams = builder642f60EighthStreams(
            sp2a8Words = sp2a8,
            x2Source = x2
        )
        assertEquals(
            "c327b7a453a17eb81062883abcbe55d935200cfe912ed92dbbee1734b5afc5d1",
            sha256Hex(packUInt64LE(eighthStreams.first))
        )
        assertEquals(
            "e66cf3bd22a351f438ef25f0489a7e6d7623002d4de4700fa8f665b0355ce8a9",
            sha256Hex(packUInt64LE(eighthStreams.second))
        )
        assertEquals(
            "bbc6d8b43127645cb932c82e7be91e4bae25b6daa6dafd89a42cfa8d60976f92",
            sha256Hex(packUInt64LE(eighthStreams.third))
        )
        assertEquals(
            "e9674687f3fa00f954d928d71797432cb57983150b2428f2dc6a04a020ac2980",
            sha256Hex(packUInt64LE(eighthStreams.fourth))
        )
        val eighthWorkspace = builder642f60Eighth64bd0cWorkspace(
            spa90Words = eighthStreams.first,
            sp670Prefix = eighthStreams.second,
            sp880Words = eighthStreams.third,
            sp510Prefix = eighthStreams.fourth
        )
        assertEquals(44 * 8, eighthWorkspace.size)
        assertEquals(
            "17b5fde88b8e761b44de016710946bbd52847db3f194fb548d424727daf94347",
            sha256Hex(eighthWorkspace)
        )
        val eighthOutput = builder64bd0cOutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = eighthWorkspace
        )
        assertEquals(
            "42e92eaf7ec2510b60090919242b7659adb855a220e3d208895a163f96e6775b",
            sha256Hex(packUInt32LE(eighthOutput))
        )
        val out2 = builder642f60Out2WordsFrom64bd0cOutput(
            packUInt32LE(eighthOutput)
        )
        assertEquals(
            "6742594d34d56c1f0c72ee89012f929548a832844b8b609775e9985fec3746b1",
            sha256Hex(packUInt32LE(out2))
        )
        assertEquals(
            listOf(0x317a551bu, 0x1951c2c8u, 0x696bf2c7u, 0xfeedf1a6u),
            out2.subList(0, 4)
        )
        assertEquals(
            listOf(0xb927d2ddu, 0xd529e3e0u, 0x5dcdae1cu, 0x0f6c6f5au),
            out2.subList(out2.size - 4, out2.size)
        )

        val affineVectors = listOf(
            Triple(
                "sp300",
                builder642f60StageSP300WordsFrom64bd0cOutput(output),
                "230a5a9f7600e5c7f4edf9a41de45b802c0e86c9f0d34d6ec851fc9dfe7a3d01"
            ),
            Triple(
                "sp250",
                builder642f60StageSP250WordsFrom64bd0cOutput(output),
                "06a9ce49a16f0594114a591bf8f10adf630e65f39baab23042372d9b7f7b457a"
            ),
            Triple(
                "sp148",
                builder642f60StageSP148WordsFrom64bd0cOutput(output),
                "814f15ddb3e704703b4cdfbd670e848007b58858cce69cce3c03ab16c1b96465"
            ),
            Triple(
                "spf0",
                builder642f60StageSPF0WordsFrom64bd0cOutput(output),
                "89f74ab7564faa6ec3ce6ff27117df2948ca38e27362c58ba68ed05b53e30039"
            ),
            Triple(
                "sp1a0",
                builder642f60StageSP1A0WordsFrom64bd0cOutput(output),
                "b1364b48497efd7082324483daab477fc7dba684c5673c2b80f109fbbbe73e0a"
            )
        )
        for (vector in affineVectors) {
            assertEquals(22, vector.second.size, vector.first)
            assertEquals(vector.third, sha256Hex(packUInt32LE(vector.second)), vector.first)
        }
    }

    @Test
    fun `testBuilder642f60OutputsWithBundledContextMatchesPythonReferenceVector`() {
        val x1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val x0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val x2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }

        val result = builder642f60OutputsFromBundledContext(
            in0 = x0,
            in1 = x1,
            in2 = x2
        )
        assertEquals(88, result.out0.size)
        assertEquals(88, result.out1.size)
        assertEquals(88, result.out2.size)
        assertEquals(
            "e4e4bc44d23db2b617f3d9a3f84a9dc1a6767d4d242a4c52b8427c587148a813",
            sha256Hex(result.out0)
        )
        assertEquals(
            "f6e027253992cc3f10bc117332271b9c97a5e6570be183b0808309bcc759bfd2",
            sha256Hex(result.out1)
        )
        assertEquals(
            "9c0ec2ac6f581933c3e457e0c4267507a575e1280caf5cbe09b962f265307e92",
            sha256Hex(result.out2)
        )

        val combined = result.out0 + result.out1 + result.out2
        assertEquals(
            "7b97c74090a4e4e2c720abf39d86a1343ba5e1961f206e227b3e06a531bc51ff",
            sha256Hex(combined)
        )

        val explicitContext = builder642f60Outputs(
            in0 = x0,
            in1 = x1,
            in2 = x2,
            contextSource = builder6388f0SharedContextFromBundle()
        )
        assertContentEquals(result.out0, explicitContext.out0)
        assertContentEquals(result.out1, explicitContext.out1)
        assertContentEquals(result.out2, explicitContext.out2)
    }

    @Test
    fun `testBuilder6473d0OutputsWithBundledContextMatchesPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val in1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }

        val result = builder6473d0OutputsFromBundledContext(
            in0 = in0,
            in1 = in1,
            in2 = in2,
            out0Preimage = out0Seed,
            out1Preimage = out1Seed
        )

        val vectors = listOf(
            Triple("in0_after", result.in0After, "9692c8145a24dcadc1fd23963c583512c8aebf55dc7c68ad677cb8f53f2117ea"),
            Triple("in1_after", result.in1After, "a4a1bb98f66e3d53a51c810379507e4a1f856bf51be0d007e10d5b3afc90252b"),
            Triple("in2_after", result.in2After, "8eb586c217d306dbde11f9301ab67d009e8dba5414bcebe90944e8542082edee"),
            Triple("out0", result.out0, "76cebb860262dd83aa186fc63ea614b3af5633e56600dda4d4da79ba840366bd"),
            Triple("out1", result.out1, "c49ad60aa507e639c71430a12067b0eb5d75737460bd9997b020b5760197ceb8"),
            Triple("out2", result.out2, "c5e3ec0675df26d11bd8390e34135652ad6b530fb8e003151b44cf1dfce6e169"),
            Triple("out3", result.out3, "d1486d791a35e129933d31bab4e814a0cdcd3db8c3b4895950882cba18791c90"),
            Triple("out4", result.out4, "3d1a32df33f5ce078ed6cfa67972c041d5aff9606ff86c381b5d257fa4bb3517")
        )
        for (vector in vectors) {
            assertEquals(88, vector.second.size, vector.first)
            assertEquals(vector.third, sha256Hex(vector.second), vector.first)
        }

        val combined = vectors.flatMap { it.second.toList() }.toByteArray()
        assertEquals(
            "62d20b19dfc648c822a404a8672031efe193c1da60496b82a337458c1c1d2a5c",
            sha256Hex(combined)
        )

        val explicitContext = builder6473d0Outputs(
            in0 = in0,
            in1 = in1,
            in2 = in2,
            contextSource = builder6388f0SharedContextFromBundle(),
            out0Preimage = out0Seed,
            out1Preimage = out1Seed
        )
        assertContentEquals(result.in0After, explicitContext.in0After)
        assertContentEquals(result.in1After, explicitContext.in1After)
        assertContentEquals(result.in2After, explicitContext.in2After)
        assertContentEquals(result.out0, explicitContext.out0)
        assertContentEquals(result.out1, explicitContext.out1)
        assertContentEquals(result.out2, explicitContext.out2)
        assertContentEquals(result.out3, explicitContext.out3)
        assertContentEquals(result.out4, explicitContext.out4)
    }

    @Test
    fun `testBuilder6473d0PreimageStackAndPostVectorsMatchPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val in1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }

        val result = builder6473d0OutputsFromBundledContext(
            in0 = in0,
            in1 = in1,
            in2 = in2,
            out0Preimage = out0Seed,
            out1Preimage = out1Seed
        )
        val preimages = Builder6473d0OutputPreimages(
            out4 = result.out4,
            out3 = result.out3,
            out2 = result.out2,
            out1 = result.out1,
            out0 = result.out0
        )

        val stack = builder6473d0MinimalStack20FromPreimages(preimages)
        assertEquals(0xc00, stack.size)
        assertEquals(
            "ee8cadccc44f822947d1cc8acc0b48081211016c8c8d20076751ac592d2a3640",
            sha256Hex(stack)
        )

        val stackChunks = listOf(
            listOf(
                "out4",
                "0x000",
                "3d1a32df33f5ce078ed6cfa67972c041d5aff9606ff86c381b5d257fa4bb3517",
                "ef7d09f934d4eb5e",
                "fc9ab4e34537c920"
            ),
            listOf(
                "out3",
                "0x058",
                "d1486d791a35e129933d31bab4e814a0cdcd3db8c3b4895950882cba18791c90",
                "22b8d195c99bd38f",
                "d7d6048d727efcdb"
            ),
            listOf(
                "out2",
                "0x0b0",
                "c5e3ec0675df26d11bd8390e34135652ad6b530fb8e003151b44cf1dfce6e169",
                "e9be8cecc1fb8a0d",
                "e0a9fd4dacfaa894"
            ),
            listOf(
                "out1",
                "0x210",
                "c49ad60aa507e639c71430a12067b0eb5d75737460bd9997b020b5760197ceb8",
                "0b1c2d3e4f607182",
                "5b6c7d8e9fb0c1d2"
            ),
            listOf(
                "out0",
                "0x268",
                "76cebb860262dd83aa186fc63ea614b3af5633e56600dda4d4da79ba840366bd",
                "05121f2c39465360",
                "15222f3c49566370"
            )
        )
        for (chunk in stackChunks) {
            val offset = chunk[1].removePrefix("0x").toInt(16)
            val window = stack.copyOfRange(offset, offset + 88)
            assertEquals(chunk[2], sha256Hex(window), chunk[0])
            assertEquals(chunk[3], Hex.encode(window.copyOfRange(0, 8)), chunk[0])
            assertEquals(chunk[4], Hex.encode(window.copyOfRange(window.size - 8, window.size)), chunk[0])
        }

        val postVectors = builder6473d0PostVectors(result)
        assertEquals(8, postVectors.size)
        val expectedPostVectors = listOf(
            Pair(0x3708, "3d1a32df33f5ce078ed6cfa67972c041d5aff9606ff86c381b5d257fa4bb3517"),
            Pair(0x3760, "d1486d791a35e129933d31bab4e814a0cdcd3db8c3b4895950882cba18791c90"),
            Pair(0x37b8, "c5e3ec0675df26d11bd8390e34135652ad6b530fb8e003151b44cf1dfce6e169"),
            Pair(0x3810, "8eb586c217d306dbde11f9301ab67d009e8dba5414bcebe90944e8542082edee"),
            Pair(0x3868, "a4a1bb98f66e3d53a51c810379507e4a1f856bf51be0d007e10d5b3afc90252b"),
            Pair(0x38c0, "9692c8145a24dcadc1fd23963c583512c8aebf55dc7c68ad677cb8f53f2117ea"),
            Pair(0x3918, "c49ad60aa507e639c71430a12067b0eb5d75737460bd9997b020b5760197ceb8"),
            Pair(0x3970, "76cebb860262dd83aa186fc63ea614b3af5633e56600dda4d4da79ba840366bd")
        )
        for ((offset, expectedHash) in expectedPostVectors) {
            val actual = assertNotNull(postVectors[offset], "missing post vector at ${offset.toString(16)}")
            assertEquals(88, actual.size)
            assertEquals(expectedHash, sha256Hex(actual), offset.toString(16))
        }
    }

    @Test
    fun `testBuilder6388f0First64cd40CallStateMatchesPythonReferenceVectors`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val in1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }
        val context = builder6388f0CallerContextFromBundle()
        val result = builder6473d0Outputs(
            in0 = in0,
            in1 = in1,
            in2 = in2,
            contextSource = context,
            out0Preimage = out0Seed,
            out1Preimage = out1Seed
        )
        val preimages = Builder6473d0OutputPreimages(
            out4 = result.out4,
            out3 = result.out3,
            out2 = result.out2,
            out1 = result.out1,
            out0 = result.out0
        )
        val stack20 = builder6473d0MinimalStack20FromPreimages(preimages)
        val postVectors = builder6473d0PostVectors(result)

        val expectedRows = listOf(
            listOf(
                "0",
                "9b74a2232218a70449b125c5b25a1be077125ce8b113783a46454ed02d816021",
                "9b8cd70e7c008f70",
                "adfaa15b83fb7c0a",
                "e9a7694cd9ab4b0bae1d8434ff29d370977c0828549628487f9a1395e4469f8e",
                "22604f4f42c846ff",
                "0000000000000000",
                "a87ab02c52a3f0e4d24c373618e06f5ed46879e1cfd78bad86063abb421dbbc4",
                "a29c55c2cd095992",
                "21f8df859b8bfaae"
            ),
            listOf(
                "17",
                "80e40a3e574da19ee4e2698456b25369913b97fa6b5a8c5692dc57e96cef45cb",
                "15c9a7f3b301eb2e",
                "adfaa15b83fb7c0a",
                "4d9957cb000e2390f1f3351a15f22f85d4dde632936f68fc4e91ecafbdf3476e",
                "22604f4f42c846ff",
                "0000000000000000",
                "8970960806c647297af6a2c2e803fc62cb1a188b546b1234c35b507b3854788c",
                "f17f9f4f39b1673b",
                "21f8df859b8bfaae"
            ),
            listOf(
                "58",
                "433d81b9f713d296a31dbd232f7456b5545ca0f89640d5c98f257737207dad79",
                "c18fefc075d749c5",
                "adfaa15b83fb7c0a",
                "ec7eb53f85b5c2ae678ede450ae0d2ab16d1c8b6cdd35de4c6d8db90aefc3a70",
                "22604f4f42c846ff",
                "0000000000000000",
                "fe323876b4ec44df84474ba3d699aa824c9417b8363ab776f23c0c0def8e6359",
                "dfd4e82cac137e2b",
                "21f8df859b8bfaae"
            )
        )

        for (row in expectedRows) {
            val state = builder6388f0First64cd40CallState(
                contextSource = context,
                callerStack20 = stack20,
                postVectors = postVectors,
                entryIndex = row[0].toInt()
            )
            val call = builder6388f0Call64Call(state)

            assertEquals(0x68404ef676a9b7d3uL, call.scalar, "entry ${row[0]}")
            assertEquals(88, call.arg0.size, "entry ${row[0]}")
            assertEquals(
                "496aa2bee379c421196b33f0e1ea8ff833a919340d09d4dd9c360e8322c9d362",
                sha256Hex(call.arg0),
                "entry ${row[0]}"
            )
            assertEquals("d6ce5d63de75b391", Hex.encode(call.arg0.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals("19ae4d0dc970204b", Hex.encode(call.arg0.copyOfRange(call.arg0.size - 8, call.arg0.size)), "entry ${row[0]}")

            assertEquals(352, call.x2Workspace.size, "entry ${row[0]}")
            assertEquals(row[1], sha256Hex(call.x2Workspace), "entry ${row[0]}")
            assertEquals(row[2], Hex.encode(call.x2Workspace.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals(row[3], Hex.encode(call.x2Workspace.copyOfRange(call.x2Workspace.size - 8, call.x2Workspace.size)), "entry ${row[0]}")

            assertEquals(88, call.x3Preimage.size, "entry ${row[0]}")
            assertEquals(
                "10eef285deef7a4b7c82b22aa53589b7833df29de3814649c772bbd5c832f365",
                sha256Hex(call.x3Preimage),
                "entry ${row[0]}"
            )

            assertEquals(0xb50, call.stackWindow.size, "entry ${row[0]}")
            assertEquals(row[4], sha256Hex(call.stackWindow), "entry ${row[0]}")
            assertEquals(row[5], Hex.encode(call.stackWindow.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals(row[6], Hex.encode(call.stackWindow.copyOfRange(call.stackWindow.size - 8, call.stackWindow.size)), "entry ${row[0]}")

            assertEquals(88, call.output.size, "entry ${row[0]}")
            assertEquals(row[7], sha256Hex(call.output), "entry ${row[0]}")
            assertEquals(row[8], Hex.encode(call.output.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals(row[9], Hex.encode(call.output.copyOfRange(call.output.size - 8, call.output.size)), "entry ${row[0]}")
        }
    }

    @Test
    fun `testBuilder6388f0Second64cd40CallStateMatchesPythonReferenceVectors`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val in1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }
        val context = builder6388f0CallerContextFromBundle()
        val result = builder6473d0Outputs(
            in0 = in0,
            in1 = in1,
            in2 = in2,
            contextSource = context,
            out0Preimage = out0Seed,
            out1Preimage = out1Seed
        )
        val preimages = Builder6473d0OutputPreimages(
            out4 = result.out4,
            out3 = result.out3,
            out2 = result.out2,
            out1 = result.out1,
            out0 = result.out0
        )
        val stack20 = builder6473d0MinimalStack20FromPreimages(preimages)
        val postVectors = builder6473d0PostVectors(result)

        val expectedRows = listOf(
            listOf(
                "0",
                "fdf23daa0954614bc792d8a4dfa7bdb95f110bb155ee30e63a3079c2c636d4db",
                "88d330fdf438d13f",
                "adfaa15b83fb7c0a",
                "3a3bd479b489dae29e94ac6fe23fb61edf0444b8489d330b0f86a30530cdc2de",
                "22604f4f42c846ff",
                "0000000000000000",
                "a88449fc45dda85642e0fb0945bdb49cc80f61fb70f2a2973239c4dc66234551",
                "543d01d17c9b3d9d",
                "21f8df859b8bfaae"
            ),
            listOf(
                "17",
                "674d446fa8d4488a5733b4abdea474ae081c4bd0066feff1870a3207d06e935e",
                "a95d0d81704cb873",
                "adfaa15b83fb7c0a",
                "889cd7b12cfc4a4dd772e404acdedc3bf64ea0b7f75e38c179381bfa60e47793",
                "22604f4f42c846ff",
                "0000000000000000",
                "25556b8bcbaede26866b77bd708a51231b62ba928254d3ebc71e8034c580337f",
                "47d5daecac25c281",
                "21f8df859b8bfaae"
            ),
            listOf(
                "58",
                "3bccb00b36a989df5282f89f0d9cad1874be43fc7ade4b7f14c64d16f80696e2",
                "77572d1aa71cdc21",
                "adfaa15b83fb7c0a",
                "fce4abbb0537b096ee71bcd98efb5f056fead875cf1fd53762d5608671e69580",
                "22604f4f42c846ff",
                "0000000000000000",
                "ccdf21b6f656f68f98024e8c8e049460de41630149212b23b38aeec2e6a30235",
                "02802ed57a5241ee",
                "21f8df859b8bfaae"
            )
        )

        for (row in expectedRows) {
            val firstState = builder6388f0First64cd40CallState(
                contextSource = context,
                callerStack20 = stack20,
                postVectors = postVectors,
                entryIndex = row[0].toInt()
            )
            val firstCall = builder6388f0Call64Call(firstState)
            val state = builder6388f0Second64cd40CallState(
                contextSource = context,
                callerStack20 = stack20,
                postVectors = postVectors,
                first64cd40Output = firstCall.output,
                entryIndex = row[0].toInt()
            )
            val call = builder6388f0Call64Call(state)

            assertEquals(0x68404ef676a9b7d3uL, call.scalar, "entry ${row[0]}")
            assertEquals(88, call.arg0.size, "entry ${row[0]}")
            assertEquals(
                "496aa2bee379c421196b33f0e1ea8ff833a919340d09d4dd9c360e8322c9d362",
                sha256Hex(call.arg0),
                "entry ${row[0]}"
            )
            assertEquals(352, call.x2Workspace.size, "entry ${row[0]}")
            assertEquals(row[1], sha256Hex(call.x2Workspace), "entry ${row[0]}")
            assertEquals(row[2], Hex.encode(call.x2Workspace.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals(row[3], Hex.encode(call.x2Workspace.copyOfRange(call.x2Workspace.size - 8, call.x2Workspace.size)), "entry ${row[0]}")

            assertContentEquals(firstCall.output, call.x3Preimage, "entry ${row[0]}")
            assertEquals(88, call.x3Preimage.size, "entry ${row[0]}")

            assertEquals(0xb50, call.stackWindow.size, "entry ${row[0]}")
            assertEquals(row[4], sha256Hex(call.stackWindow), "entry ${row[0]}")
            assertEquals(row[5], Hex.encode(call.stackWindow.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals(row[6], Hex.encode(call.stackWindow.copyOfRange(call.stackWindow.size - 8, call.stackWindow.size)), "entry ${row[0]}")

            assertEquals(88, call.output.size, "entry ${row[0]}")
            assertEquals(row[7], sha256Hex(call.output), "entry ${row[0]}")
            assertEquals(row[8], Hex.encode(call.output.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals(row[9], Hex.encode(call.output.copyOfRange(call.output.size - 8, call.output.size)), "entry ${row[0]}")
        }
    }

    @Test
    fun `testBuilder6388f0Third64cd40CallStateMatchesPythonReferenceVectors`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val in1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }
        val context = builder6388f0CallerContextFromBundle()
        val result = builder6473d0Outputs(
            in0 = in0,
            in1 = in1,
            in2 = in2,
            contextSource = context,
            out0Preimage = out0Seed,
            out1Preimage = out1Seed
        )
        val preimages = Builder6473d0OutputPreimages(
            out4 = result.out4,
            out3 = result.out3,
            out2 = result.out2,
            out1 = result.out1,
            out0 = result.out0
        )
        val stack20 = builder6473d0MinimalStack20FromPreimages(preimages)
        val postVectors = builder6473d0PostVectors(result)

        val expectedRows = listOf(
            listOf(
                "0",
                "27fe07cba0b4e7f62d5da4f07f91f5d1887d8f2038431d7f5d14e6d6c38683eb",
                "cc1a18d569c77ddd",
                "adfaa15b83fb7c0a",
                "a9194cbdf9a9f7e76911b05d2641ecddcb6a76e1721f0f1c37cac228a7b7995e",
                "22604f4f42c846ff",
                "0000000000000000",
                "a1c8d6d83a91ce22e8025eb97c641fa5396cdb629e7105d3b646767e1c2d6a29",
                "13e228e615652c1f",
                "21f8df859b8bfaae"
            ),
            listOf(
                "17",
                "611f5018d6ecd757ec6298405c6da354f4c757fa882244c1dc8e05cf0adadf75",
                "11af0e262ddece94",
                "adfaa15b83fb7c0a",
                "c3d48c9a66812e39a6cffe630fdabaea3a4f449953f3a14fdb30b9222bc34524",
                "22604f4f42c846ff",
                "0000000000000000",
                "4e61a8e846d745236315e1d28fc341466130e735de858695281b7bd40b10f2e2",
                "49821358928f5609",
                "21f8df859b8bfaae"
            ),
            listOf(
                "58",
                "c2ca68df25da9dab6dc07a22501cb037543119fee471ffcf4a139e28240a498b",
                "d7661ccde60dfbe2",
                "adfaa15b83fb7c0a",
                "1a99dbd410c58bc5b0fea71ec0a21804bf5151de2b77ee22ca393cd8355531c2",
                "22604f4f42c846ff",
                "0000000000000000",
                "6348e4b69ed966be0a14615776c7ad56f938543f74f0fea19539ebdd7b3a1449",
                "0bfcade05c9c24dd",
                "21f8df859b8bfaae"
            )
        )

        for (row in expectedRows) {
            val firstState = builder6388f0First64cd40CallState(
                contextSource = context,
                callerStack20 = stack20,
                postVectors = postVectors,
                entryIndex = row[0].toInt()
            )
            val firstCall = builder6388f0Call64Call(firstState)
            val secondState = builder6388f0Second64cd40CallState(
                contextSource = context,
                callerStack20 = stack20,
                postVectors = postVectors,
                first64cd40Output = firstCall.output,
                entryIndex = row[0].toInt()
            )
            val secondCall = builder6388f0Call64Call(secondState)
            val state = builder6388f0Third64cd40CallState(
                contextSource = context,
                callerStack20 = stack20,
                postVectors = postVectors,
                second64cd40Output = secondCall.output,
                entryIndex = row[0].toInt()
            )
            val call = builder6388f0Call64Call(state)

            assertEquals(0x68404ef676a9b7d3uL, call.scalar, "entry ${row[0]}")
            assertEquals(88, call.arg0.size, "entry ${row[0]}")
            assertEquals(
                "496aa2bee379c421196b33f0e1ea8ff833a919340d09d4dd9c360e8322c9d362",
                sha256Hex(call.arg0),
                "entry ${row[0]}"
            )
            assertEquals(352, call.x2Workspace.size, "entry ${row[0]}")
            assertEquals(row[1], sha256Hex(call.x2Workspace), "entry ${row[0]}")
            assertEquals(row[2], Hex.encode(call.x2Workspace.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals(row[3], Hex.encode(call.x2Workspace.copyOfRange(call.x2Workspace.size - 8, call.x2Workspace.size)), "entry ${row[0]}")

            assertContentEquals(secondCall.output, call.x3Preimage, "entry ${row[0]}")
            assertEquals(88, call.x3Preimage.size, "entry ${row[0]}")

            assertEquals(0xb50, call.stackWindow.size, "entry ${row[0]}")
            assertEquals(row[4], sha256Hex(call.stackWindow), "entry ${row[0]}")
            assertEquals(row[5], Hex.encode(call.stackWindow.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals(row[6], Hex.encode(call.stackWindow.copyOfRange(call.stackWindow.size - 8, call.stackWindow.size)), "entry ${row[0]}")

            assertEquals(88, call.output.size, "entry ${row[0]}")
            assertEquals(row[7], sha256Hex(call.output), "entry ${row[0]}")
            assertEquals(row[8], Hex.encode(call.output.copyOfRange(0, 8)), "entry ${row[0]}")
            assertEquals(row[9], Hex.encode(call.output.copyOfRange(call.output.size - 8, call.output.size)), "entry ${row[0]}")
        }
    }

    @Test
    fun `testBuilder6388f0SeededCaller64RowMatchesPythonReferenceVectors`() {
        val current = Builder6388f0Next642f60Inputs(
            x0 = ByteArray(88) { index -> (index * 15 + 2).toByte() },
            x1 = ByteArray(88) { index -> (index * 13 + 9).toByte() },
            x2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        )
        val preimages = Builder6473d0OutputPreimages(
            out4 = ByteArray(88) { index -> (index * 3 + 1).toByte() },
            out3 = ByteArray(88) { index -> (index * 5 + 2).toByte() },
            out2 = ByteArray(88) { index -> (index * 7 + 3).toByte() },
            out1 = ByteArray(88) { index -> (index * 17 + 11).toByte() },
            out0 = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        )
        val context = builder6388f0CallerContextFromBundle()

        val expectedRows = listOf(
            listOf(
                "0",
                "5d2fd9d65710079e70120c4b41d30ef96b43de537f0812f9c55bcde5d13da6ff",
                "a1738cb1be833dfc46837e95defc6b05ce0d477432c31ce23185931226fd54c2",
                "4d643ba15818807886f8ce5a4b5b3dfdd1eaeb0c1fa1aa217372ef3c41daee36",
                "16c108e1f0d3e9f6056c72f55125d51ecebe8499991f335f23e7cbbbb925af77",
                "815963ee2b9a9870282a384b356ef0593e1dafb8b8db3721803374854b2a5a7e",
                "c92bd26c7e87a582242cd62421ca05a6fd2820dead0b9fa14002882b331c0e79"
            ),
            listOf(
                "17",
                "02afcb4bc9530532a885d949bd994c13c953de5ea643dbf476a98a35a7133bfa",
                "2a13bd55dca210d9687243e8871f8e71a130da7ca0d0bd19d3fdc446dd3ff7eb",
                "760c499e675bb4a49c79f3c4ce2ffda348cf28a8238e950121f82ca6b79e6e3e",
                "08d621986111b3b183965665743855d3e3babc160708137f5f38c9ffe236675c",
                "645a5a07228ceb9f3da9396abcdc1b052b50ffe7196e746dfbe9d4b3fc4cae61",
                "1a9300451e9d8f4398354952d1dddfabbbd5123f77649e1b59746df4921096a4"
            ),
            listOf(
                "58",
                "843342ef8bf55e0707d422f4b7df49fe1955f7c2fd9093169d3ce09045ee8c76",
                "81f4b91efc1d50c84ff8d861a4f1cd2fe1aa4e70f7a289bc16570faac0f55e92",
                "3b13937db283e3408050fbb9b07c6b1445ea6d0799c5bbecbbe0cf4edff68730",
                "4cd9881f383c036b27aa7956269a5e7e40863b7849f112c7abae3f459a7143d2",
                "e8e0c80abbcc900e14eaf167711b0aac4b705109f47b0311ea941e3fcc41d32c",
                "011ae4ecb8fdee4ed0158a4e198f43df9aed402ddbc981695e22ff8dfc113059"
            )
        )

        for (expected in expectedRows) {
            val row = builder6388f0SeededCaller64Row(
                index = expected[0].toInt(),
                current642f60 = current,
                preimages = preimages,
                contextSource = context
            )

            assertEquals(expected[0].toInt(), row.index)
            assertContentEquals(current.x0, row.current642f60.x0)
            assertContentEquals(current.x1, row.current642f60.x1)
            assertContentEquals(current.x2, row.current642f60.x2)
            assertContentEquals(preimages.out4, row.preimages.out4)
            assertContentEquals(preimages.out3, row.preimages.out3)
            assertContentEquals(preimages.out2, row.preimages.out2)
            assertContentEquals(preimages.out1, row.preimages.out1)
            assertContentEquals(preimages.out0, row.preimages.out0)
            assertEquals("e4e4bc44d23db2b617f3d9a3f84a9dc1a6767d4d242a4c52b8427c587148a813", sha256Hex(row.after642f60.out0))
            assertEquals("f6e027253992cc3f10bc117332271b9c97a5e6570be183b0808309bcc759bfd2", sha256Hex(row.after642f60.out1))
            assertEquals("9c0ec2ac6f581933c3e457e0c4267507a575e1280caf5cbe09b962f265307e92", sha256Hex(row.after642f60.out2))
            assertEquals("0d5c73fceaada7a6b59cc13b20292c84f7ed53adb1761938dcc102b24df3b333", sha256Hex(row.after6473d0.out2))
            assertEquals("7d7898d5e1c1655ad924fba7ba2ecfd10669e8215583328da2e3b5e579c6d1a2", sha256Hex(row.after6473d0.out3))
            assertEquals("4f8334dadc445d8e94bb4478cc915f6b7ed5097d904b916608a7f96b291c8c1d", sha256Hex(row.after6473d0.out4))
            assertEquals("2a768e7ee55607748ec665cd8f02d2afa8e7585fac8796918438dc443bde1df4", sha256Hex(row.minimalStack20))
            assertEquals(expected[1], sha256Hex(row.first64cd40.output), "entry ${expected[0]}")
            assertEquals(expected[2], sha256Hex(row.second64cd40.output), "entry ${expected[0]}")
            assertEquals(expected[3], sha256Hex(row.third64cd40.output), "entry ${expected[0]}")
            assertEquals(expected[4], sha256Hex(row.next642f60.x0), "entry ${expected[0]}")
            assertEquals(expected[5], sha256Hex(row.next642f60.x1), "entry ${expected[0]}")
            assertEquals(expected[6], sha256Hex(row.next642f60.x2), "entry ${expected[0]}")
        }
    }

    @Test
    fun `testBuilder6388f0SeededCaller64RowsThroughRow59MatchPythonReferenceVectors`() {
        val row0Out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val row0Out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }
        val row59Out0Seed = ByteArray(88) { index -> (index * 19 + 7).toByte() }
        val row59Out1Seed = ByteArray(88) { index -> (index * 23 + 3).toByte() }
        val starts = builder6388f0FirstPair642f60StreamStarts(
            row0Out0Seed = row0Out0Seed,
            row0Out1Seed = row0Out1Seed,
            row59Out0Seed = row59Out0Seed,
            row59Out1Seed = row59Out1Seed
        )
        val row0LowPreimages = Builder6473d0OutputPreimages(
            out4 = ByteArray(88) { index -> (index * 3 + 1).toByte() },
            out3 = ByteArray(88) { index -> (index * 5 + 2).toByte() },
            out2 = ByteArray(88) { index -> (index * 7 + 3).toByte() },
            out1 = row0Out1Seed,
            out0 = row0Out0Seed
        )

        val rows = builder6388f0SeededCaller64Rows(
            starts = starts,
            row0LowPreimages = row0LowPreimages,
            contextSource = builder6388f0CallerContextFromBundle(),
            limit = 60
        )
        assertEquals(60, rows.size)

        val expectedRows = listOf(
            listOf(
                "0",
                "ff807599b1ce0b18fbafc1f5ef3b1af310536e6a355f84b6fe6e5e7e70cb5d07",
                "32c5c3611fb9ddd8920ced6bd80e2f7823679cbeee2de9f315a1f98e47ade845",
                "c49ad60aa507e639c71430a12067b0eb5d75737460bd9997b020b5760197ceb8",
                "76cebb860262dd83aa186fc63ea614b3af5633e56600dda4d4da79ba840366bd",
                "aa133ccd20f64550f79e24d95edb4bd3840ea41170fb073ccfa7ed2f0a5ec2a6",
                "0c4e075f7394388253e849f3d6640211b122a3b80d4ec2f0fd6215ed2e2fa2d9",
                "50f98bbf26612a10b73c619cc69c6762dc158314f8f92eb9d5d6361129d1fae6",
                "82c27ffaaccebe24cfc57998d8c2db525d9fe19c49bca454cf60e9b09be83457",
                "735de0e678900db387d508151c8fecb730ea63cfd0c032c074d1259e6e312982",
                "c7fee1e743d7292d4cbaf16608f95bfdbe17aaac23696d2a79dff247ef44f833",
                "6b3c31cad24792ed5bef2eed597201dcf6ec3f3a0914bdf5a6db1b3fae3b994a"
            ),
            listOf(
                "1",
                "735de0e678900db387d508151c8fecb730ea63cfd0c032c074d1259e6e312982",
                "aa133ccd20f64550f79e24d95edb4bd3840ea41170fb073ccfa7ed2f0a5ec2a6",
                "c49ad60aa507e639c71430a12067b0eb5d75737460bd9997b020b5760197ceb8",
                "76cebb860262dd83aa186fc63ea614b3af5633e56600dda4d4da79ba840366bd",
                "387aa890d590abea241e416b8445b86cfcc0a167dfc7b108d746e462070d677e",
                "b8336b40eeb0d42767f8ff51ef6a32045fa0c08edb97c957f251290b9620bfcf",
                "c558bc603fed9ec13aa0b2479ca91d9eb5e57f14aabf113cfc8771dbc6525324",
                "2d6815f226308263a2648eea425e228698171b3a5947f7e10a615cb472d694ff",
                "66e37479739d0ec8db1f675bbf6e89320057bb0dae89f52fdf0f0f72aa4d3f84",
                "bf69b44f03b7832f07b7ca5e3be463f2884c3a52756834101008fdc9e76d5e7c",
                "95334b88f87b86184812a0451dbbf2138087eaa12cc3748af820ab8645959c5e"
            ),
            listOf(
                "58",
                "0f8cf013564d57471ee687ff1259e4a475284ac082bda3c145d1b39ba10735ff",
                "f8397a23467f7b742df4ac4ad0ef5039a53da85e2c59ecfb9201bf33284bedb3",
                "c49ad60aa507e639c71430a12067b0eb5d75737460bd9997b020b5760197ceb8",
                "76cebb860262dd83aa186fc63ea614b3af5633e56600dda4d4da79ba840366bd",
                "c0a36f54bde4a40820313a1f342aafece509cf5688f1cc6db298966d2c3d0fca",
                "1ea5c372b85c3d5a6d7653ed5e4eb6d3675633ed78224c5b61995994cfc8860e",
                "3ecc46b9fe2761cebc879dd2449b713f0bfadfd62091540e71412c246ef094c5",
                "89751b3b26a68d55cfd1b35764b4f26cf8f708bc04303cabc54228e8b20aaab6",
                "4ea5999b0919caf0302f7a36d44c30f88f5f0ea65ad455d7c8ad87c4d5e3af1a",
                "17c85652013c611c936ffda604594177fdc765a9b180be98c5a0edcff665903b",
                "88a7fe81fc5d1f1b6d5b24290c54f843de57fb3339e423afdb8a0e2da0dfe934"
            ),
            listOf(
                "59",
                "a2105eb9e12ffa599c55ff1714223addc7fb0fcfa9fb7b6ed4bec1acbcf1c31c",
                "c0a36f54bde4a40820313a1f342aafece509cf5688f1cc6db298966d2c3d0fca",
                "378ca711504b9c45e43c6abb6bbcfc018c3f1eb73af7be15195da5c2498d985d",
                "297391c138bee4a4837718a9e29bc9f007a0fd05321b8a84c0d2281d0111f905",
                "d6f95c8bd7aa8b4bdadcd02c4564a79ffa05c47fbc9c9f742a568e898e62dacc",
                "fde9684c6e046abd640e0d23a7df62c944606b55dae31bf0abb273861c7cb8b5",
                "93f147f5441ccbe38cdb51420c5c9a581a3cc59437ab74f6c829ed38276c898e",
                "0243976e2069612ff32ef284e79dfc3b11383403d465954c6b7d5e30aa5988ac",
                "2df364928960728a222d1a524f62a1affac4a974f17d06728aba4d1f0ec9772e",
                "5bbcc6cfaa034ffcc8f11766af29399c0717143569892f2cd0283ca21fb64338",
                "8f8f1879368b68c69b226763814a7c4cff7ed661c93283b3bc1f3c13b66f0ee4"
            )
        )

        for (expected in expectedRows) {
            val row = rows[expected[0].toInt()]
            assertEquals(expected[0].toInt(), row.index)
            assertEquals(expected[1], sha256Hex(row.current642f60.x0), "row ${expected[0]}")
            assertEquals(expected[2], sha256Hex(row.preimages.out4), "row ${expected[0]}")
            assertEquals(expected[3], sha256Hex(row.preimages.out1), "row ${expected[0]}")
            assertEquals(expected[4], sha256Hex(row.preimages.out0), "row ${expected[0]}")
            assertEquals(expected[5], sha256Hex(row.after6473d0.out4), "row ${expected[0]}")
            assertEquals(expected[6], sha256Hex(row.first64cd40.output), "row ${expected[0]}")
            assertEquals(expected[7], sha256Hex(row.second64cd40.output), "row ${expected[0]}")
            assertEquals(expected[8], sha256Hex(row.third64cd40.output), "row ${expected[0]}")
            assertEquals(expected[9], sha256Hex(row.next642f60.x0), "row ${expected[0]}")
            assertEquals(expected[10], sha256Hex(row.next642f60.x1), "row ${expected[0]}")
            assertEquals(expected[11], sha256Hex(row.next642f60.x2), "row ${expected[0]}")
        }
    }

    @Test
    fun `testBuilder6388f0Seeded63c278SchedulesFromRowsMatchPythonReferenceVectors`() {
        val row0Out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val row0Out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }
        val row59Out0Seed = ByteArray(88) { index -> (index * 19 + 7).toByte() }
        val row59Out1Seed = ByteArray(88) { index -> (index * 23 + 3).toByte() }
        val starts = builder6388f0FirstPair642f60StreamStarts(
            row0Out0Seed = row0Out0Seed,
            row0Out1Seed = row0Out1Seed,
            row59Out0Seed = row59Out0Seed,
            row59Out1Seed = row59Out1Seed
        )
        val row0LowPreimages = Builder6473d0OutputPreimages(
            out4 = ByteArray(88) { index -> (index * 3 + 1).toByte() },
            out3 = ByteArray(88) { index -> (index * 5 + 2).toByte() },
            out2 = ByteArray(88) { index -> (index * 7 + 3).toByte() },
            out1 = row0Out1Seed,
            out0 = row0Out0Seed
        )
        val rows = builder6388f0SeededCaller64Rows(
            starts = starts,
            row0LowPreimages = row0LowPreimages,
            contextSource = builder6388f0CallerContextFromBundle()
        )

        val schedules = builder6388f0Seeded63c278SchedulesFromRows(rows = rows)
        assertEquals(118, rows.size)
        assertEquals(58, schedules.first.rowIndex)
        assertEquals(117, schedules.second.rowIndex)
        assertEquals(pre63c278Scalar, schedules.first.scalar)
        assertEquals(pre63c278Scalar, schedules.second.scalar)
        assertEquals(
            "1b0499442309d372cc3cd1fa5ea6d8640f3e14661456c368f50123bebc51b647",
            sha256Hex(schedules.first.arg0)
        )
        assertEquals(
            "4ea5999b0919caf0302f7a36d44c30f88f5f0ea65ad455d7c8ad87c4d5e3af1a",
            sha256Hex(schedules.first.arg1)
        )
        assertEquals(
            "88a7fe81fc5d1f1b6d5b24290c54f843de57fb3339e423afdb8a0e2da0dfe934",
            sha256Hex(schedules.first.arg2)
        )
        assertEquals(
            listOf(
                0x42fa386cu, 0xc627b657u, 0xc8638fd3u, 0xc97a2ab7u, 0xd23eb4acu,
                0x3dc33146u, 0xee7d479cu, 0xb1f34d23u, 0xee536419u, 0xaffc9f1cu,
                0x3b95a64cu, 0xcce6d138u, 0x70227a65u, 0x87ca2121u, 0xefb07a8fu,
                0xc4749659u, 0x1cd92603u, 0xe0ab3767u, 0x3b95a64cu, 0xcce6d138u
            ),
            schedules.first.scheduleWords
        )
        assertEquals(
            "b31620465a69c66dee3504561f60addaf2c74055d82ad778a1f13e52da4750d0",
            sha256Hex(packUInt32LE(schedules.first.scheduleWords))
        )

        assertEquals(
            "1b0499442309d372cc3cd1fa5ea6d8640f3e14661456c368f50123bebc51b647",
            sha256Hex(schedules.second.arg0)
        )
        assertEquals(
            "88e31fd2bc2b9bfc0d710473797991beb5dcd9df6b2489550f2a0d33f6a65b17",
            sha256Hex(schedules.second.arg1)
        )
        assertEquals(
            "8c49a1d43b53e3d6c0f09bbb75dafb847c67f33e4031e2db45e7eadc9145840d",
            sha256Hex(schedules.second.arg2)
        )
        assertEquals(
            listOf(
                0x91390f77u, 0x6f5270d3u, 0x6412ea8cu, 0x4e288c3bu, 0x352b86cfu,
                0x693acdeau, 0x28d9d7d2u, 0x08306255u, 0x8f45b76au, 0x056e96b3u,
                0x3b95a64cu, 0xcce6d138u, 0x70227a65u, 0x87ca2121u, 0xefb07a8fu,
                0xc4749659u, 0x1cd92603u, 0xe0ab3767u, 0x3b95a64cu, 0xcce6d138u
            ),
            schedules.second.scheduleWords
        )
        assertEquals(
            "f5187d8a9042e39cbc2758abd244f0a7bc91bc63a624c743ee8b10bb7af64cf1",
            sha256Hex(packUInt32LE(schedules.second.scheduleWords))
        )

        val source = deriveFrom6388f0SeededCaller64Rows(rows = rows)
        assertEquals(
            "040400010402030107000002030007050505030706070204050000060303020307" +
                "020600070302040604000305030603020102020003010306050605060207060303",
            Hex.encode(source)
        )
        val rawKey = phase5RawKeyFrom6388f0SeededCaller64Rows(rows = rows)
        assertEquals("515ca99cb8c0deaf1208df352078064d", Hex.encode(rawKey))
    }

    @Test
    fun `testDeriveFrom6388f0FirstPairStreamSeedsMatchesPythonReferenceVectors`() {
        val seeds = Builder6388f0FirstPairStreamSeeds(
            nullScalarWindow = ByteArray(70) { index -> (index * 29 + 1).toByte() },
            staticScalarWindow = ByteArray(70) { index -> (index * 31 + 2).toByte() },
            nullEntropy11A = ByteArray(0x11a) { index -> (index * 37 + 3).toByte() },
            nullAttempts = 2,
            row0Out4 = ByteArray(88) { index -> (index * 3 + 1).toByte() },
            row0Out3 = ByteArray(88) { index -> (index * 5 + 2).toByte() },
            row0Out2 = ByteArray(88) { index -> (index * 7 + 3).toByte() },
            row0Out1 = ByteArray(88) { index -> (index * 17 + 11).toByte() },
            row0Out0 = ByteArray(88) { index -> (index * 13 + 5).toByte() },
            row59Out1 = ByteArray(88) { index -> (index * 23 + 3).toByte() },
            row59Out0 = ByteArray(88) { index -> (index * 19 + 7).toByte() }
        )

        val starts = builder6388f0FirstPair642f60StreamStarts(seeds = seeds)
        assertEquals(
            "ff807599b1ce0b18fbafc1f5ef3b1af310536e6a355f84b6fe6e5e7e70cb5d07",
            sha256Hex(starts.row0.x0)
        )
        assertEquals(
            "a2105eb9e12ffa599c55ff1714223addc7fb0fcfa9fb7b6ed4bec1acbcf1c31c",
            sha256Hex(starts.row59.x0)
        )

        val source = deriveFrom6388f0FirstPairStreamSeeds(seeds = seeds)
        assertEquals(
            "040400010402030107000002030007050505030706070204050000060303020307" +
                "020600070302040604000305030603020102020003010306050605060207060303",
            Hex.encode(source)
        )
        val rawKey = phase5RawKeyFrom6388f0FirstPairStreamSeeds(seeds = seeds)
        assertEquals("515ca99cb8c0deaf1208df352078064d", Hex.encode(rawKey))
    }

    @Test
    fun `testBuilder64c524OutputWordsMatchPythonReferenceVector`() {
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL
        val x2Workspace = ByteArray(352) { index -> (index * 5 + 11).toByte() }

        val arg0Words = builder64c524Arg0U64Words(arg0 = arg0)
        assertEquals(
            "b4c289307b76fd400a7ebb93cfd81f6df15931c9553af9a9c8bc1a9f047ca741",
            sha256Hex(packUInt64LE(arg0Words))
        )
        assertEquals(
            listOf(0x3ff86d27c281a51buL, 0x82c9235432f698dauL, 0xf846c40785c0883cuL, 0x9f193ff19acdd538uL),
            arg0Words.subList(0, 4)
        )

        val updated = builder64c524WorkspaceAfterUpdate(
            arg0U64Words = arg0Words,
            scalar = scalar,
            x2Workspace = x2Workspace
        )
        assertEquals(44 * 8, updated.size)
        assertEquals(
            "0dce7bc80d9fd277b44333a61aa8e14a608c6205b63cee6b9ea498a4407533ee",
            sha256Hex(updated)
        )
        assertEquals(
            "af4609c269497b9cc32a915a34c3bbd1c87d116319769c5fab21064d51fe471b",
            Hex.encode(updated.copyOfRange(0, 32))
        )
        assertEquals(
            "88227680af94c55adff3deccb1f86d6c81b410c13274fcc1c3c8cdd2d7dce1e6",
            Hex.encode(updated.copyOfRange(updated.size - 32, updated.size))
        )

        val output = builder64c524FinalU32Words(x2Workspace = updated)
        assertEquals(
            "59eebec8dcac326baa8e1be4844f38155835ea1734a2985fd12249e77de2d6b3",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(0x2c9a5e95u, 0x39ffcae7u, 0xdb27a8feu, 0x35947d74u),
            output.subList(0, 4)
        )
        assertEquals(
            listOf(0x35c32db9u, 0x322abd54u, 0x38781571u, 0xeb184e5fu),
            output.subList(output.size - 4, output.size)
        )
        assertEquals(
            output,
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = x2Workspace
            )
        )
    }
}
