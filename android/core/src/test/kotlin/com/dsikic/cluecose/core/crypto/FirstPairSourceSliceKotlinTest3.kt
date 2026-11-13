// 1:1 port of LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift`.
// MIT (c) 2024-2025 LibreCRKit contributors — see android/NOTICE.txt.
// This file covers Swift lines 2890-4466 (tests 39-64).
package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.ClasspathTablesSource
import com.dsikic.cluecose.core.util.Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FirstPairSourceSliceKotlinTest3 {

    companion object {
        init {
            FirstPairConfig.tablesSource = ClasspathTablesSource()
        }
    }

    // Swift L2890-2955
    @Test
    fun `testBuilder6473d0First64c524SliceMatchesPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL

        val streams = builder6473d0FirstStreamsFromIn2(in2)
        assertEquals(
            "b1f4dcc49a972799f58fb3a398591ad74882914c02ccbe7e3457bb85d15f9e2a",
            sha256Hex(packUInt64LE(streams.first))
        )
        assertEquals(
            "546485573a7fa23d64c96fb217f9196d379f766f199e80e02609e286e0c0d2ee",
            sha256Hex(packUInt64LE(streams.second))
        )
        assertEquals(
            "07ca498940d4fdd6ecaf9d82d38c47e974e51b0c1afad1db014de69f9bd0b560",
            sha256Hex(packUInt64LE(streams.third))
        )
        assertEquals(
            "6b2c5100098474366f5642502dabe909b52b3ea6217c32b4d5ab4d0e206a805c",
            sha256Hex(packUInt64LE(streams.fourth))
        )
        assertEquals(
            listOf(0x266614493d8fa940uL, 0x6007c6dd9fbb3cceuL, 0x4a0463a7929bbbdbuL, 0xe71ae9adfa4dffb7uL),
            streams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0x21609531c6f7bbc2uL, 0x3a01cb0167bc1884uL, 0x6d10d5fd47b5d72fuL, 0xb3988c7c118905f3uL),
            streams.second.subList(0, 4)
        )

        val workspace = builder6473d0First64c524Workspace(in2)
        assertEquals(44 * 8, workspace.size)
        assertEquals(
            "5acfd8607b33aa61a6f38cc24ecc4555d321cd500d9e6b9ff684d36884a827ed",
            sha256Hex(workspace)
        )
        assertEquals(
            "8c8f9e0050dc253a33746dd5535a791918564522a208dfbd7939d24e6a8f7413",
            Hex.encode(workspace.copyOfRange(0, 32))
        )
        assertEquals(
            "9537b90ea920365d794473ef8e6ecc24fceb758e6044aa2151eb2ff475dc024d",
            Hex.encode(workspace.copyOfRange(workspace.size - 32, workspace.size))
        )

        val output = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = workspace
        )
        assertEquals(
            "107a44c71f1c2072c3659db4f8df499dcd81173019370d11fa3e5bf73ca829cd",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(0x4b27dc37u, 0xbdbbe5dcu, 0xe19df049u, 0xa7b9b740u),
            output.subList(0, 4)
        )
        assertEquals(
            listOf(0xff7a3bdbu, 0x56fb02efu, 0xf5984e81u, 0x9ea400e1u),
            output.subList(output.size - 4, output.size)
        )

        val sp488 = builder6473d0SP488WordsFrom64c524Output(packUInt32LE(output))
        assertEquals(
            "04041ca272f4abc0c853f762e581febd27c7eb6aac3d480aa0d94d5efe605160",
            sha256Hex(packUInt32LE(sp488))
        )
        assertEquals(
            listOf(0xa51e755au, 0x4ce7e2cbu, 0xb6a6acfau, 0xd6d1cdb6u),
            sp488.subList(0, 4)
        )
        assertEquals(
            listOf(0xfa4d4fccu, 0xf93c4e87u, 0xa7b82e47u, 0x8a95e647u),
            sp488.subList(sp488.size - 4, sp488.size)
        )
    }

    // Swift L2957-3029
    @Test
    fun `testBuilder6473d0Second64c524SliceMatchesPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL

        val firstWorkspace = builder6473d0First64c524Workspace(in2)
        val firstOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = firstWorkspace
            )
        )
        val sp488 = builder6473d0SP488WordsFrom64c524Output(firstOutput)

        val streams = builder6473d0SecondStreams(
            out0Seed = out0Seed,
            sp488Words = sp488
        )
        assertEquals(
            "df9ea4f10d2698fde031d956c2143316bb63595e4db592193408cbec3997a792",
            sha256Hex(packUInt64LE(streams.first))
        )
        assertEquals(
            "35e95635019b19c3fc100148b2a078890400453759c7e0e8a45fa91855518d64",
            sha256Hex(packUInt64LE(streams.second))
        )
        assertEquals(
            "b9099f56f72378936c3e52698f85809aede89bf5ca0cdab1248cc9864b954403",
            sha256Hex(packUInt64LE(streams.third))
        )
        assertEquals(
            "c0acb1f9c3464f26c4eb07d3ec1f88aed7a3203c5220256ea342fa643f86907a",
            sha256Hex(packUInt64LE(streams.fourth))
        )
        assertEquals(
            listOf(0xf0e978b818b93c69uL, 0x75a018a3672cb577uL, 0xa4c98421009de949uL, 0x0684e334238a06ceuL),
            streams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0x9fca7b25b214721cuL, 0x9f76e8d6d38da8e4uL, 0x8f10bab198854e7fuL, 0x1b1e7339e8891893uL),
            streams.second.subList(0, 4)
        )

        val workspace = builder6473d0Second64c524Workspace(
            out0Seed = out0Seed,
            sp488Words = sp488
        )
        assertEquals(44 * 8, workspace.size)
        assertEquals(
            "cd1f9403619681b0c5d076306a3a56bff7ae16312a1aca44cc6e81aa7b685fed",
            sha256Hex(workspace)
        )
        assertEquals(
            "9fc6f0b5a5b45c108bae7302f9b19bd803d70a934a2fc7e122894534255008c3",
            Hex.encode(workspace.copyOfRange(0, 32))
        )
        assertEquals(
            "daf46992df1ed21b8b56ac397d611828f1716ca712847b7551eb2ff475dc024d",
            Hex.encode(workspace.copyOfRange(workspace.size - 32, workspace.size))
        )

        val output = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = workspace
        )
        assertEquals(
            "33532e554e19a5b35f3d621d486f9b7716d29c0605198e4a6ac1d320038bc28d",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(0x2b7825c8u, 0xc4c63905u, 0x0211b018u, 0x9c1118f8u),
            output.subList(0, 4)
        )
        assertEquals(
            listOf(0x474ddcc0u, 0xa689879cu, 0xf11dde81u, 0xac1cba94u),
            output.subList(output.size - 4, output.size)
        )
    }

    // Swift L3031-3130
    @Test
    fun `testBuilder6473d0Third64c524SliceMatchesPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL

        val firstWorkspace = builder6473d0First64c524Workspace(in2)
        val firstOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = firstWorkspace
            )
        )
        val sp488 = builder6473d0SP488WordsFrom64c524Output(firstOutput)
        val secondWorkspace = builder6473d0Second64c524Workspace(
            out0Seed = out0Seed,
            sp488Words = sp488
        )
        val secondOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = secondWorkspace
            )
        )

        val source = builder6473d0ThirdSourceWords(
            secondOutput = secondOutput,
            contextSource = builder6388f0SharedContextFromBundle(),
            in0 = in0
        )
        assertEquals(
            "6e34220606e6f5401122a935081884080412601262e305b059fddbe077803051",
            sha256Hex(packUInt32LE(source))
        )
        assertEquals(
            listOf(0xf5b579d3u, 0x9d2de314u, 0x2241a5f4u, 0x79d5b76fu),
            source.subList(0, 4)
        )
        assertEquals(
            listOf(0xab6e381au, 0xe56c72d8u, 0x2e241889u, 0xf358dc37u),
            source.subList(source.size - 4, source.size)
        )

        val sp430 = builder6473d0ThirdSP430Words(sourceWords = source)
        assertEquals(
            "08a18f8160f3efca8c1f5834fa918d904b749724799d71b3d4420491df15318d",
            sha256Hex(packUInt32LE(sp430))
        )
        assertEquals(
            listOf(0x113e3183u, 0x7ec235c3u, 0x55dcb210u, 0xf48aa162u),
            sp430.subList(0, 4)
        )
        assertEquals(
            listOf(0x7b0eec9cu, 0xcdf26752u, 0xca504858u, 0x06f09076u),
            sp430.subList(sp430.size - 4, sp430.size)
        )

        val streams = builder6473d0ThirdStreams(in2 = in2, sp488Words = sp488)
        assertEquals(
            "cdcd8b9cba1a6f86509bbcc4a95e3d88787cfef390803bad4bb1f50cbd55eddf",
            sha256Hex(packUInt64LE(streams.first))
        )
        assertEquals(
            "20e806f4dec45407ae529955b5ae41af7c5228c240dbed1fb916a74a85417c6e",
            sha256Hex(packUInt64LE(streams.second))
        )
        assertEquals(
            "a7ebd396d071b5f6457ce58bbd5111e98dd19a87bf847c7de7d7b4c61cfc3ad3",
            sha256Hex(packUInt64LE(streams.third))
        )
        assertEquals(
            "6626fad83edd6d479df4bdf0c5fbc728c94c4e83fcc3a32eddb68581955954f3",
            sha256Hex(packUInt64LE(streams.fourth))
        )
        assertEquals(
            listOf(0x9ec32fb9eb41c8b4uL, 0xeadea57a0d5f4f66uL, 0xf47a4e4eba3970b9uL, 0xc5338a3849d5495duL),
            streams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0x12ff4184f57f0b10uL, 0x2bba1c1466693768uL, 0x31d61d1b90c42b39uL, 0xe7a4053ead3bfd75uL),
            streams.second.subList(0, 4)
        )

        val workspace = builder6473d0Third64c524Workspace(
            in2 = in2,
            sp488Words = sp488
        )
        assertEquals(44 * 8, workspace.size)
        assertEquals(
            "fb42796391ab808c901953f0f4c3be19bbd4d49553f4df5c111d566766a1ceb6",
            sha256Hex(workspace)
        )
        assertEquals(
            "2c35faea21f269ffd52ef8ec5e1ea3b888891ca17d146b93cddedd013d7ad84c",
            Hex.encode(workspace.copyOfRange(0, 32))
        )
        assertEquals(
            "fe379f241197fec60883cc17e6f9b2c9133658a70c24c1a951eb2ff475dc024d",
            Hex.encode(workspace.copyOfRange(workspace.size - 32, workspace.size))
        )

        val output = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = workspace
        )
        assertEquals(
            "bddbfeeece5007421a61931dd17aa25f9e70ac7141aa394880fabbec7357b655",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(0xbaf05b61u, 0xbcfafe93u, 0xfdfbf603u, 0x32bd3651u),
            output.subList(0, 4)
        )
        assertEquals(
            listOf(0xdd26ba56u, 0x344e60a5u, 0xe12df139u, 0x64f97835u),
            output.subList(output.size - 4, output.size)
        )
    }

    // Swift L3132-3218
    @Test
    fun `testBuilder6473d0Fourth64c524SliceMatchesPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL

        val firstWorkspace = builder6473d0First64c524Workspace(in2)
        val firstOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = firstWorkspace
            )
        )
        val sp488 = builder6473d0SP488WordsFrom64c524Output(firstOutput)
        builder6473d0Second64c524Workspace(
            out0Seed = out0Seed,
            sp488Words = sp488
        )
        val thirdWorkspace = builder6473d0Third64c524Workspace(
            in2 = in2,
            sp488Words = sp488
        )
        val thirdOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = thirdWorkspace
            )
        )

        val streams = builder6473d0FourthStreams(
            out1Seed = out1Seed,
            thirdOutput = thirdOutput
        )
        assertEquals(
            "7d6cc7ceb43b20b5f628756c842c5ac7be1fde886d9309ed4518ac9ae4bb287c",
            sha256Hex(packUInt64LE(streams.first))
        )
        assertEquals(
            "f5bc9070ebae188fb4864386433ac3eeda3e68ed9a9e471aa5c21ebb45ca573b",
            sha256Hex(packUInt64LE(streams.second))
        )
        assertEquals(
            "c7d8186bbd46e981d96b8bf8ebce2bae4735a83eaf70726093750c54a7a1d025",
            sha256Hex(packUInt64LE(streams.third))
        )
        assertEquals(
            "3d7ab324afb3cbbf6fe7dd05ffc55e81d344fb246f24231d4f33db46209452c8",
            sha256Hex(packUInt64LE(streams.fourth))
        )
        assertEquals(
            listOf(0xc718cd948cdbbe6cuL, 0x19ab42c3d336544euL, 0x40aeaaac042cd268uL, 0xf3351ed19d2a2273uL),
            streams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0x0848a5b0e557414auL, 0x8327a66a50524d8fuL, 0xcf6aaf3c6af10b71uL, 0xa598b68e0c681612uL),
            streams.second.subList(0, 4)
        )

        val workspace = builder6473d0Fourth64c524Workspace(
            out1Seed = out1Seed,
            thirdOutput = thirdOutput
        )
        assertEquals(44 * 8, workspace.size)
        assertEquals(
            "2805724203e148d53582172f661f1c76fac1cceb16baa58a2d36f584fde6ebd0",
            sha256Hex(workspace)
        )
        assertEquals(
            "910a780dd0d36d7aa3cf54cfb62cd2bd1d63e86f26c61fe15c2948c36ef5f2c4",
            Hex.encode(workspace.copyOfRange(0, 32))
        )
        assertEquals(
            "ee066461203fc21a55f5ee8cc35f022825c98846d169126651eb2ff475dc024d",
            Hex.encode(workspace.copyOfRange(workspace.size - 32, workspace.size))
        )

        val output = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = workspace
        )
        assertEquals(
            "1be5186490600873daaffdaf8dc02f9b053d163a70b498b6dabee5c10b7dbf88",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(0x9163deaau, 0x817053d2u, 0xad6f4dd2u, 0x74570c4cu),
            output.subList(0, 4)
        )
        assertEquals(
            listOf(0x60ba61fcu, 0xca5e579fu, 0xea186ea5u, 0x758cad4au),
            output.subList(output.size - 4, output.size)
        )
    }

    // Swift L3220-3343
    @Test
    fun `testBuilder6473d0Fifth64c524SliceMatchesPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val in1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL
        val context = builder6388f0SharedContextFromBundle()

        val firstWorkspace = builder6473d0First64c524Workspace(in2)
        val firstOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = firstWorkspace
            )
        )
        val sp488 = builder6473d0SP488WordsFrom64c524Output(firstOutput)
        val secondWorkspace = builder6473d0Second64c524Workspace(
            out0Seed = out0Seed,
            sp488Words = sp488
        )
        val secondOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = secondWorkspace
            )
        )
        val thirdWorkspace = builder6473d0Third64c524Workspace(
            in2 = in2,
            sp488Words = sp488
        )
        val thirdOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = thirdWorkspace
            )
        )
        val thirdSource = builder6473d0ThirdSourceWords(
            secondOutput = secondOutput,
            contextSource = context,
            in0 = in0
        )
        val sp430 = builder6473d0ThirdSP430Words(sourceWords = thirdSource)
        val fourthWorkspace = builder6473d0Fourth64c524Workspace(
            out1Seed = out1Seed,
            thirdOutput = thirdOutput
        )
        val fourthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = fourthWorkspace
            )
        )

        val source = builder6473d0FifthSourceWords(
            fourthOutput = fourthOutput,
            contextSource = context,
            in1 = in1
        )
        assertEquals(
            "45c409753e24cb9cf75d8d77f758eaed100f898588b6ed1159f790a3754ee721",
            sha256Hex(packUInt32LE(source))
        )
        assertEquals(
            listOf(0x038ec1c7u, 0x7d124e33u, 0x9326dce9u, 0x7d8762dfu),
            source.subList(0, 4)
        )
        assertEquals(
            listOf(0x85cf25f9u, 0x09b28e3du, 0xef2023e0u, 0x3b53428du),
            source.subList(source.size - 4, source.size)
        )

        val sp3d8 = builder6473d0FifthSP3D8Words(sourceWords = source)
        assertEquals(
            "d655cd5c70f502d4fa869630aad63fa3640a51cae610093237b5d9a2e5c3fb52",
            sha256Hex(packUInt32LE(sp3d8))
        )
        assertEquals(
            listOf(0x2e3356bbu, 0x050404a6u, 0x85d8c536u, 0xef821eafu),
            sp3d8.subList(0, 4)
        )
        assertEquals(
            listOf(0xd8ade60au, 0x603b94bbu, 0xe4df7216u, 0x39d4082cu),
            sp3d8.subList(sp3d8.size - 4, sp3d8.size)
        )

        val streams = builder6473d0FifthStreams(sp430Words = sp430)
        assertEquals(
            "df784feb1db29901a7c95bd7093566adc6e0bd687800292035222c6c8266f735",
            sha256Hex(packUInt64LE(streams.first))
        )
        assertEquals(
            "520a4f15468ed4cd614ee48c53dd7cc5c37f65f43d8d7eb1b43fdfe50674f3b3",
            sha256Hex(packUInt64LE(streams.second))
        )
        assertEquals(
            "ecfb510b8864783d07f75918ac018b14c18cacefdf787b4f4aa980545635256f",
            sha256Hex(packUInt64LE(streams.third))
        )
        assertEquals(
            "6df97c23d326cb47adfb8243934446f76499ad1ee3eb4efeedd9b0587ee1f232",
            sha256Hex(packUInt64LE(streams.fourth))
        )
        assertEquals(
            listOf(0xd8656d2421961388uL, 0xda62e987118e1762uL, 0x8c90d0b55f2257acuL, 0xdad118e6d0f8f21euL),
            streams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0x9c6325c164fa4842uL, 0x3273ec272266d840uL, 0xc4822fb10931880euL, 0x8af8e29135df5a14uL),
            streams.second.subList(0, 4)
        )

        val workspace = builder6473d0Fifth64c524Workspace(sp430Words = sp430)
        assertEquals(44 * 8, workspace.size)
        assertEquals(
            "08b5f2127f4f9d38a87ed43cc9df67aa41ee69d2f8f59b28bb09cdb00ed7d475",
            sha256Hex(workspace)
        )
        assertEquals(
            "01b043c35a882aca61ea3fa3b362448c9df8b98203ec08e86131a8ae94572ae8",
            Hex.encode(workspace.copyOfRange(0, 32))
        )
        assertEquals(
            "416cbbf2fcd52b7c510c434a347cbb7081571771f3e92ebd51eb2ff475dc024d",
            Hex.encode(workspace.copyOfRange(workspace.size - 32, workspace.size))
        )

        val output = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = workspace
        )
        assertEquals(
            "7b8aeba91679cc87e0038e22256590e68819f8ae38935c5428e89b1783d3f4cc",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(0x7b4d3ac1u, 0x5f7925deu, 0x2d36a812u, 0x4b7e2c6bu),
            output.subList(0, 4)
        )
        assertEquals(
            listOf(0x1c1eacbfu, 0xce0b74f3u, 0x40694fb5u, 0xeee0898fu),
            output.subList(output.size - 4, output.size)
        )
    }

    // Swift L3345-3448
    @Test
    fun `testBuilder6473d0Sixth64c524SliceMatchesPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL
        val context = builder6388f0SharedContextFromBundle()

        val firstWorkspace = builder6473d0First64c524Workspace(in2)
        val firstOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = firstWorkspace
            )
        )
        val sp488 = builder6473d0SP488WordsFrom64c524Output(firstOutput)
        val secondWorkspace = builder6473d0Second64c524Workspace(
            out0Seed = out0Seed,
            sp488Words = sp488
        )
        val secondOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = secondWorkspace
            )
        )
        val thirdSource = builder6473d0ThirdSourceWords(
            secondOutput = secondOutput,
            contextSource = context,
            in0 = in0
        )
        val sp430 = builder6473d0ThirdSP430Words(sourceWords = thirdSource)
        val fifthWorkspace = builder6473d0Fifth64c524Workspace(sp430Words = sp430)
        val fifthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = fifthWorkspace
            )
        )

        val sp380 = builder6473d0SixthSP380Words(fifthOutput = fifthOutput)
        assertEquals(
            "9504a74f2e28965005e5404ec5efe753f7422bcb693e58f4a5a6a7c18c0ad9b0",
            sha256Hex(packUInt32LE(sp380))
        )
        assertEquals(
            listOf(0x90f87c6eu, 0x3895b89cu, 0x930c4ee5u, 0x982ce414u),
            sp380.subList(0, 4)
        )
        assertEquals(
            listOf(0x212cdf90u, 0xf3a5675cu, 0xa476bcfcu, 0xb2c330bau),
            sp380.subList(sp380.size - 4, sp380.size)
        )

        val streams = builder6473d0SixthStreams(
            sp430Words = sp430,
            sp380Words = sp380
        )
        assertEquals(
            "2887bd4468fadc7e22e31f0fb9217bed4d97fe0244489fdf7f68d8bb9be25cbe",
            sha256Hex(packUInt64LE(streams.first))
        )
        assertEquals(
            "0935766dcd7efadb89b9ee300f6515d6919f7ca13efbef3d94b4c4338f44c0a9",
            sha256Hex(packUInt64LE(streams.second))
        )
        assertEquals(
            "a4c2f18f60e79acd4a34eeea084ed1f01cf4e3fc323f3805ee2deaf919331b3d",
            sha256Hex(packUInt64LE(streams.third))
        )
        assertEquals(
            "31ffa478e2e0ebbf1099622b29d76f4a1b9a8c3d9b91f87311818169d3bfb0c2",
            sha256Hex(packUInt64LE(streams.fourth))
        )
        assertEquals(
            listOf(0xe1348b33a7f370a5uL, 0x72acdb6c47b5dbd1uL, 0xcd420f20def32441uL, 0x1f16b89f702955efuL),
            streams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0xc8956057eff4378euL, 0xd3717a55b09625a0uL, 0x13542bc82050c562uL, 0x918fcdcd225d692cuL),
            streams.second.subList(0, 4)
        )

        val workspace = builder6473d0Sixth64c524Workspace(
            sp430Words = sp430,
            sp380Words = sp380
        )
        assertEquals(44 * 8, workspace.size)
        assertEquals(
            "4c4b1b102a9fa301c2ba8541851a2c324d6064c6ca38d695888dd660bd77ef6a",
            sha256Hex(workspace)
        )
        assertEquals(
            "65953c135dd69e4313e0ec23d690d764b3156da680db69c345161f884e4177e3",
            Hex.encode(workspace.copyOfRange(0, 32))
        )
        assertEquals(
            "5dd6d783f1600ea4f55d88064599a48d519049d01c5bcd8751eb2ff475dc024d",
            Hex.encode(workspace.copyOfRange(workspace.size - 32, workspace.size))
        )

        val output = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = workspace
        )
        assertEquals(
            "2f5a21c7c184f09bdb00f9f11eb2d4292e2814306b2cf01cd97806dbb1f9c91b",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(0x7c89cb83u, 0x90e88426u, 0x4d2b1ea6u, 0x017fc88cu),
            output.subList(0, 4)
        )
        assertEquals(
            listOf(0x39f78b96u, 0xae98d6c3u, 0x9d47f134u, 0xae88538bu),
            output.subList(output.size - 4, output.size)
        )
    }

    // Swift L3450-3563
    @Test
    fun `testBuilder6473d0Seventh64c524SliceMatchesPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL
        val context = builder6388f0SharedContextFromBundle()

        val firstWorkspace = builder6473d0First64c524Workspace(in2)
        val firstOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = firstWorkspace
            )
        )
        val sp488 = builder6473d0SP488WordsFrom64c524Output(firstOutput)
        val secondWorkspace = builder6473d0Second64c524Workspace(
            out0Seed = out0Seed,
            sp488Words = sp488
        )
        val secondOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = secondWorkspace
            )
        )
        val thirdSource = builder6473d0ThirdSourceWords(
            secondOutput = secondOutput,
            contextSource = context,
            in0 = in0
        )
        val sp430 = builder6473d0ThirdSP430Words(sourceWords = thirdSource)
        val fifthWorkspace = builder6473d0Fifth64c524Workspace(sp430Words = sp430)
        val fifthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = fifthWorkspace
            )
        )
        val sp380 = builder6473d0SixthSP380Words(fifthOutput = fifthOutput)
        val sixthWorkspace = builder6473d0Sixth64c524Workspace(
            sp430Words = sp430,
            sp380Words = sp380
        )
        val sixthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = sixthWorkspace
            )
        )

        val sp328 = builder6473d0SeventhSP328Words(sixthOutput = sixthOutput)
        assertEquals(
            "82f7fa5ba1bfc8e84f566c3807ad6f87b5d574c792446ed797577ebb74eaf27a",
            sha256Hex(packUInt32LE(sp328))
        )
        assertEquals(
            listOf(0xc92f75a4u, 0x843b24feu, 0xea821e69u, 0x1fa6924fu),
            sp328.subList(0, 4)
        )
        assertEquals(
            listOf(0x1a86ab99u, 0x4a980308u, 0x2dda072fu, 0x84fba1dfu),
            sp328.subList(sp328.size - 4, sp328.size)
        )

        val streams = builder6473d0SeventhStreams(
            in0 = in0,
            sp380Words = sp380
        )
        assertEquals(
            "591cba75b0159e431614c436e10997c886d68a6d759a8d31be910eca22cf85e8",
            sha256Hex(packUInt64LE(streams.first))
        )
        assertEquals(
            "6b23d88bd5e0da2f00dfb68fdfba69ec6d94b5230348df4cb69d7adde0687c10",
            sha256Hex(packUInt64LE(streams.second))
        )
        assertEquals(
            "3c1a6ff0cfdf896e5edb0f455bb62ac7c504900176871e382f2e7c4f14d8a0bc",
            sha256Hex(packUInt64LE(streams.third))
        )
        assertEquals(
            "79ffe314f5ca539b42ab1ffa0e1247adf4ae14f09d5898478b3f3b931010b3d6",
            sha256Hex(packUInt64LE(streams.fourth))
        )
        assertEquals(
            listOf(0x124a61b90fed5e3auL, 0x7c9ab6bc7f99fe0euL, 0xc78f559a52db131euL, 0x13b5c433fb943ac1uL),
            streams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0x586fe37826186e1duL, 0x26c4451dd0d61491uL, 0x5ae38b9970a524a1uL, 0xe4a19cfee3a96103uL),
            streams.second.subList(0, 4)
        )

        val workspace = builder6473d0Seventh64c524Workspace(
            in0 = in0,
            sp380Words = sp380
        )
        assertEquals(44 * 8, workspace.size)
        assertEquals(
            "2700fbf891008ce8edc1f11ba1557fd080d684b965dd65136260fbf2b0964878",
            sha256Hex(workspace)
        )
        assertEquals(
            "a7d5fa4acffa4b5b71873b9227d3fbb39bf0519294c52829de97e9cd730e84c2",
            Hex.encode(workspace.copyOfRange(0, 32))
        )
        assertEquals(
            "3da77073b43b4e286e33ecb22052330e918d36ca6f2384cf51eb2ff475dc024d",
            Hex.encode(workspace.copyOfRange(workspace.size - 32, workspace.size))
        )

        val output = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = workspace
        )
        assertEquals(
            "0eccbe5cdbf85dff7a37c87d7e555481d1fd8bdae3b39088b6b6273af860a786",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(0x0083c378u, 0x3b2d8fffu, 0x1ae9f799u, 0xf338f6ebu),
            output.subList(0, 4)
        )
        assertEquals(
            listOf(0x808945ecu, 0x1da59779u, 0x2d0c9f18u, 0x9b286f04u),
            output.subList(output.size - 4, output.size)
        )
    }

    // Swift L3565-3698
    @Test
    fun `testBuilder6473d0Eighth64c524SliceMatchesPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val in1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL
        val context = builder6388f0SharedContextFromBundle()

        val firstWorkspace = builder6473d0First64c524Workspace(in2)
        val firstOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = firstWorkspace
            )
        )
        val sp488 = builder6473d0SP488WordsFrom64c524Output(firstOutput)
        val secondWorkspace = builder6473d0Second64c524Workspace(
            out0Seed = out0Seed,
            sp488Words = sp488
        )
        val secondOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = secondWorkspace
            )
        )
        val thirdWorkspace = builder6473d0Third64c524Workspace(
            in2 = in2,
            sp488Words = sp488
        )
        val thirdOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = thirdWorkspace
            )
        )
        val thirdSource = builder6473d0ThirdSourceWords(
            secondOutput = secondOutput,
            contextSource = context,
            in0 = in0
        )
        val sp430 = builder6473d0ThirdSP430Words(sourceWords = thirdSource)
        val fourthWorkspace = builder6473d0Fourth64c524Workspace(
            out1Seed = out1Seed,
            thirdOutput = thirdOutput
        )
        val fourthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = fourthWorkspace
            )
        )
        val fifthSource = builder6473d0FifthSourceWords(
            fourthOutput = fourthOutput,
            contextSource = context,
            in1 = in1
        )
        val sp3d8 = builder6473d0FifthSP3D8Words(sourceWords = fifthSource)
        val fifthWorkspace = builder6473d0Fifth64c524Workspace(sp430Words = sp430)
        val fifthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = fifthWorkspace
            )
        )
        val sp380 = builder6473d0SixthSP380Words(fifthOutput = fifthOutput)
        val seventhWorkspace = builder6473d0Seventh64c524Workspace(
            in0 = in0,
            sp380Words = sp380
        )
        val seventhOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = seventhWorkspace
            )
        )

        val sp2d0 = builder6473d0EighthSP2D0Words(seventhOutput = seventhOutput)
        assertEquals(
            "56cbeb55b6aa494a61207e0fca41d0265e917f6002b1ba2b8ece63458b76b085",
            sha256Hex(packUInt32LE(sp2d0))
        )
        assertEquals(
            listOf(0xc96913d6u, 0x99773534u, 0x7817f9b9u, 0x56bb5785u),
            sp2d0.subList(0, 4)
        )
        assertEquals(
            listOf(0x912f288cu, 0x8432d14fu, 0x4a8c6c61u, 0x7c00265du),
            sp2d0.subList(sp2d0.size - 4, sp2d0.size)
        )

        val streams = builder6473d0EighthStreams(sp3d8Words = sp3d8)
        assertEquals(
            "183f2be6b622dc2eb373fa393d1683d6dad201749fa3e60f45f6d027a267bdd9",
            sha256Hex(packUInt64LE(streams.first))
        )
        assertEquals(
            "8b884e80ad5ed6bd11860fe89f504e27b2a92ec8007bda2a1a51bf08fd9bb9f3",
            sha256Hex(packUInt64LE(streams.second))
        )
        assertEquals(
            "dcceda41820f364bf5f77e6c726942b5c6c4f60c558994e809659c233e17ce29",
            sha256Hex(packUInt64LE(streams.third))
        )
        assertEquals(
            "c7cf326fc28abf2692e8e36c18851db85b28e83b92d51c6c27fa8fd1820a8a99",
            sha256Hex(packUInt64LE(streams.fourth))
        )
        assertEquals(
            listOf(0xaec355f61f8f894duL, 0xaf57fb0fb715ffafuL, 0x6753b2a275722caduL, 0x6947fb771dfc5b54uL),
            streams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0x6a6ed2db7d276ceeuL, 0x7fddfe69097ed3f4uL, 0x7fc2a48944be010euL, 0x241e62d2712f2593uL),
            streams.second.subList(0, 4)
        )

        val workspace = builder6473d0Eighth64c524Workspace(sp3d8Words = sp3d8)
        assertEquals(44 * 8, workspace.size)
        assertEquals(
            "be9f91423ea1b893b3e58a877eb02980b4b44bc8906e2cd19a8a6df6b3d941b3",
            sha256Hex(workspace)
        )
        assertEquals(
            "3deb21d360b851b9d15548db2281aed4a9aafb92a4b162f9b5e42cb228336459",
            Hex.encode(workspace.copyOfRange(0, 32))
        )
        assertEquals(
            "d0dc135d19aa1ea3557c6030ac3bef953d026a8e0d3a913f51eb2ff475dc024d",
            Hex.encode(workspace.copyOfRange(workspace.size - 32, workspace.size))
        )

        val output = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = workspace
        )
        assertEquals(
            "cc933de7d8905c4cac18b91c959fea8e7c302bd9fa3573677fe59f251908255a",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(0xa8338b67u, 0x48585485u, 0xa7d8b3f7u, 0x9b238737u),
            output.subList(0, 4)
        )
        assertEquals(
            listOf(0x57541043u, 0x528d8294u, 0xb0149d05u, 0xc47caee4u),
            output.subList(output.size - 4, output.size)
        )
    }

    // Swift L3700-4024
    @Test
    fun `testBuilder6473d0NinthSourceReducersMatchPythonReferenceVector`() {
        val in2 = ByteArray(88) { index -> (index * 21 + 7).toByte() }
        val in0 = ByteArray(88) { index -> (index * 15 + 2).toByte() }
        val in1 = ByteArray(88) { index -> (index * 13 + 9).toByte() }
        val out0Seed = ByteArray(88) { index -> (index * 13 + 5).toByte() }
        val out1Seed = ByteArray(88) { index -> (index * 17 + 11).toByte() }
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL
        val context = builder6388f0SharedContextFromBundle()

        val firstWorkspace = builder6473d0First64c524Workspace(in2)
        val firstOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = firstWorkspace
            )
        )
        val sp488 = builder6473d0SP488WordsFrom64c524Output(firstOutput)
        val secondWorkspace = builder6473d0Second64c524Workspace(
            out0Seed = out0Seed,
            sp488Words = sp488
        )
        val secondOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = secondWorkspace
            )
        )
        val thirdWorkspace = builder6473d0Third64c524Workspace(
            in2 = in2,
            sp488Words = sp488
        )
        val thirdOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = thirdWorkspace
            )
        )
        val thirdSource = builder6473d0ThirdSourceWords(
            secondOutput = secondOutput,
            contextSource = context,
            in0 = in0
        )
        val sp430 = builder6473d0ThirdSP430Words(sourceWords = thirdSource)
        val fourthWorkspace = builder6473d0Fourth64c524Workspace(
            out1Seed = out1Seed,
            thirdOutput = thirdOutput
        )
        val fourthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = fourthWorkspace
            )
        )
        val fifthSource = builder6473d0FifthSourceWords(
            fourthOutput = fourthOutput,
            contextSource = context,
            in1 = in1
        )
        val sp3d8 = builder6473d0FifthSP3D8Words(sourceWords = fifthSource)
        val fifthWorkspace = builder6473d0Fifth64c524Workspace(sp430Words = sp430)
        val fifthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = fifthWorkspace
            )
        )
        val sp380 = builder6473d0SixthSP380Words(fifthOutput = fifthOutput)
        val sixthWorkspace = builder6473d0Sixth64c524Workspace(
            sp430Words = sp430,
            sp380Words = sp380
        )
        val sixthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = sixthWorkspace
            )
        )
        val sp328 = builder6473d0SeventhSP328Words(sixthOutput = sixthOutput)
        val seventhWorkspace = builder6473d0Seventh64c524Workspace(
            in0 = in0,
            sp380Words = sp380
        )
        val seventhOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = seventhWorkspace
            )
        )
        val sp2d0 = builder6473d0EighthSP2D0Words(seventhOutput = seventhOutput)
        val eighthWorkspace = builder6473d0Eighth64c524Workspace(sp3d8Words = sp3d8)
        val eighthOutput = packUInt32LE(
            builder64c524OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = eighthWorkspace
            )
        )

        val source1 = builder6473d0NinthFirstSourceWords(
            eighthOutput = eighthOutput,
            contextSource = context,
            sp328Words = sp328,
            sp2d0Words = sp2d0
        )
        assertEquals(
            "e18c8416b17464c4bae6ff625375d164dfec55af019420d2ca0116c3cc0ebed6",
            sha256Hex(packUInt32LE(source1))
        )
        assertEquals(
            listOf(0x955b96bfu, 0xa2b58afdu, 0xd700ff5au, 0x06a83167u),
            source1.subList(0, 4)
        )
        assertEquals(
            listOf(0xd2d4d332u, 0x351e47aau, 0x93b71ffeu, 0x2c7ac25cu),
            source1.subList(source1.size - 4, source1.size)
        )

        val out2 = builder6473d0NinthOut2Words(sourceWords = source1)
        assertEquals(
            "8368856aed45b1d49270602d6edba23e92809b2346290be6bc6a392f0aaf988d",
            sha256Hex(packUInt32LE(out2))
        )
        assertEquals(
            listOf(0x0d39094bu, 0x077ba0c5u, 0x54ba9e1du, 0x94e0f0dfu),
            out2.subList(0, 4)
        )
        assertEquals(
            listOf(0xaf6062a0u, 0x87026000u, 0x552ef927u, 0x67a5cc22u),
            out2.subList(out2.size - 4, out2.size)
        )

        val source2 = builder6473d0NinthSecondSourceWords(
            sp2d0Words = sp2d0,
            contextSource = context,
            out2Words = out2
        )
        assertEquals(
            "f8eb019cf9c0f4154874174ce8abf240b8e14a3ec59e411d84a550998a7a9d5c",
            sha256Hex(packUInt32LE(source2))
        )
        assertEquals(
            listOf(0x0aa6b2bau, 0x62d4fe90u, 0x681904f8u, 0xdf2014ffu),
            source2.subList(0, 4)
        )
        assertEquals(
            listOf(0x231bce32u, 0x3be767fbu, 0x21037d7fu, 0x2c868663u),
            source2.subList(source2.size - 4, source2.size)
        )

        val sp278 = builder6473d0NinthSP278Words(sourceWords = source2)
        assertEquals(
            "d572b9c1bb74f6bfc893a8cf8991fee8f10520a516289e4ba9c18db8eccd7750",
            sha256Hex(packUInt32LE(sp278))
        )
        assertEquals(
            listOf(0x9413cee2u, 0x0460a288u, 0x22d03034u, 0xbd433cdfu),
            sp278.subList(0, 4)
        )
        assertEquals(
            listOf(0xb3476dc3u, 0x694125fdu, 0x80373a26u, 0x76a974a1u),
            sp278.subList(sp278.size - 4, sp278.size)
        )

        val ninthFirstStreams = builder6473d0NinthFirstStreams(
            sp3d8Words = sp3d8,
            sp278Words = sp278
        )
        assertEquals(
            "b71bdd49fef5edf17f87cbed6f144013ad6570fb619d7259eecee78563d4321e",
            sha256Hex(packUInt64LE(ninthFirstStreams.first))
        )
        assertEquals(
            "95ceb0b250e78470133394a723b433a5bf8e3f7669cfb77be6dcaefcccac094d",
            sha256Hex(packUInt64LE(ninthFirstStreams.second))
        )
        assertEquals(
            "43db401e21867455f70a7da5b3688986851ff1c50922e2b4a764382a75ec7bd7",
            sha256Hex(packUInt64LE(ninthFirstStreams.third))
        )
        assertEquals(
            "e327fc0348a9eb211eec7c47eb4ad39feeabd01ad929c43990736a69c6961b40",
            sha256Hex(packUInt64LE(ninthFirstStreams.fourth))
        )
        assertEquals(
            listOf(0x545b0c404f636e24uL, 0xb5392a3d7628dcd6uL, 0x7ff978526fe2c884uL, 0xb99d48d053f750c3uL),
            ninthFirstStreams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0x098d91ac4188cdd0uL, 0xd599083ddc2ffcb2uL, 0x8982a5c60026fe6auL, 0xaf9785833d9b9f3euL),
            ninthFirstStreams.second.subList(0, 4)
        )

        val sp1c8 = builder6473d0NinthSP1C8Words(
            aWords = ninthFirstStreams.first,
            bWords = ninthFirstStreams.second
        )
        assertEquals(
            "86cae00a8644b96d2f81fdcc84e5a861e6086d2e57635918c1aa0a285f01a839",
            sha256Hex(packUInt32LE(sp1c8))
        )
        assertEquals(
            listOf(0x1ca7a428u, 0x2c776676u, 0x334f6402u, 0xf76aaadau),
            sp1c8.subList(0, 4)
        )
        assertEquals(
            listOf(0x6ffb40fdu, 0xcfc772ccu, 0x84b24ddfu, 0x72f88ab3u),
            sp1c8.subList(sp1c8.size - 4, sp1c8.size)
        )

        val ninthSecondStreams = builder6473d0NinthSecondStreams(
            in1 = in1,
            sp328Words = sp328
        )
        assertEquals(
            "e2610e9a2bb7e7431a9d50e0c52d546c4dbf46dbf3d806253e11af0b66db64e0",
            sha256Hex(packUInt64LE(ninthSecondStreams.first))
        )
        assertEquals(
            "5cb107493620008b697a14577e98aca3b0b99ecbc0d5eb11824683df14589d8f",
            sha256Hex(packUInt64LE(ninthSecondStreams.second))
        )
        assertEquals(
            "89e8408978c20c1bdfb852e7f784ce2693f894aa065649d7a01aeacfd55e8594",
            sha256Hex(packUInt64LE(ninthSecondStreams.third))
        )
        assertEquals(
            "43af916e53a4d9773e251fd1a3401b53ef0acb92af42e8f008c539d39c6ed808",
            sha256Hex(packUInt64LE(ninthSecondStreams.fourth))
        )
        assertEquals(
            listOf(0xfb7a80d9192d1ec3uL, 0x6e6d75ae9199026buL, 0xa2b5cbeef35ac70auL, 0x5de39a957ccb60eeuL),
            ninthSecondStreams.first.subList(0, 4)
        )
        assertEquals(
            listOf(0x6634d7f4c2cf2b36uL, 0x3cf9a20168417e5cuL, 0x73d6e1fd6db42ec0uL, 0x9fb8812a0485cfe1uL),
            ninthSecondStreams.second.subList(0, 4)
        )

        val sp118 = builder6473d0NinthSP118Words(
            aWords = ninthSecondStreams.first,
            bWords = ninthSecondStreams.second
        )
        assertEquals(
            "ded780efbdff94c54fe57299d3fa3542f688bd86e0fc0b6890bd0a135e686c4c",
            sha256Hex(packUInt32LE(sp118))
        )
        assertEquals(
            listOf(0x4a28ffdeu, 0x782ea9beu, 0x71a8268fu, 0xf40984a7u),
            sp118.subList(0, 4)
        )
        assertEquals(
            listOf(0xea23c4c1u, 0x34cb8794u, 0x8c5f0b7eu, 0x6fbb76aeu),
            sp118.subList(sp118.size - 4, sp118.size)
        )

        val source3 = builder6473d0NinthThirdSourceWords(
            sp1c8Words = sp1c8,
            contextSource = context,
            sp118Words = sp118
        )
        assertEquals(
            "551881a522a53344a1f14689741406abc30267914aa02e8d36bd722182015f11",
            sha256Hex(packUInt32LE(source3))
        )
        assertEquals(
            listOf(0x61ce1299u, 0x99472497u, 0xc507ee19u, 0xb11859f9u),
            source3.subList(0, 4)
        )
        assertEquals(
            listOf(0xad72c809u, 0x26e0941cu, 0xa45e51b4u, 0x0394a1b2u),
            source3.subList(source3.size - 4, source3.size)
        )

        val sp68 = builder6473d0NinthSP68Words(sourceWords = source3)
        assertEquals(
            "b5b7d07e747d5a677fb6cf9182a96755196408c6487d1e4e77bb37364cd12a07",
            sha256Hex(packUInt32LE(sp68))
        )
        assertEquals(
            listOf(0xf69fa821u, 0x6d436f56u, 0x7165aa1fu, 0x0a29d5a2u),
            sp68.subList(0, 4)
        )
        assertEquals(
            listOf(0xcebd599cu, 0xfa56cc6bu, 0x26f17702u, 0x1d4c6065u),
            sp68.subList(sp68.size - 4, sp68.size)
        )

        val ninthWorkspace = builder6473d0Ninth64c524Workspace(sp68Words = sp68)
        assertEquals(44 * 8, ninthWorkspace.size)
        assertEquals(
            "01fab44ba58a157c53f8035e82a33ad31e7d5f3090040841bd4041841e9a2b22",
            sha256Hex(ninthWorkspace)
        )
        assertEquals(
            "6bc3af8c573468f34171d1b016dea4b2ebc4e3cff360f7404bdeb7283373accf",
            Hex.encode(ninthWorkspace.copyOfRange(0, 32))
        )
        assertEquals(
            "18cde1b75184abdb2a95c21dea4ed777a2b23d7400c863cc78769979df2c176d",
            Hex.encode(ninthWorkspace.copyOfRange(ninthWorkspace.size - 32, ninthWorkspace.size))
        )

        val ninthOutput = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = ninthWorkspace
        )
        assertEquals(
            "d81b8468a5a7d4b17bf44f39b479476a8568bfd82aadc937b563221ed16604c7",
            sha256Hex(packUInt32LE(ninthOutput))
        )
        assertEquals(
            listOf(0xa6afa142u, 0xadd6d28au, 0x52b1d3b4u, 0xa8a41d1bu),
            ninthOutput.subList(0, 4)
        )
        assertEquals(
            listOf(0x546fe16du, 0xfcd5ce72u, 0xba890604u, 0x5ef00136u),
            ninthOutput.subList(ninthOutput.size - 4, ninthOutput.size)
        )

        val out3 = builder6473d0TenthOut3Words(ninthOutput = packUInt32LE(ninthOutput))
        assertEquals(
            "8b4cdea006adbc427fc62ed7e593a7368be01c5433e4792558564f7d944edcd1",
            sha256Hex(packUInt32LE(out3))
        )
        assertEquals(
            listOf(0x02cfb61bu, 0x83a302bfu, 0x7dce5814u, 0xa99120b6u),
            out3.subList(0, 4)
        )
        assertEquals(
            listOf(0x1733e4c3u, 0x509ea371u, 0x2e866201u, 0xdcad41e9u),
            out3.subList(out3.size - 4, out3.size)
        )

        val tenthStreams = builder6473d0TenthStreams(
            in2 = in2,
            sp430Words = sp430
        )
        assertEquals(
            "bdb411ed05059270ed4e6b090a287fefb9565b45f1c041d2a752d359c48a73c4",
            sha256Hex(packUInt64LE(tenthStreams.first))
        )
        assertEquals(
            "b208110ec78dc587a68c8f8684542ec44e1629968c474e2a573ec958bba72774",
            sha256Hex(packUInt64LE(tenthStreams.second))
        )
        assertEquals(
            "115333e8d18efc2935219ffc80e2686f551d909b52fdaf22e135c5b67cd24f3a",
            sha256Hex(packUInt64LE(tenthStreams.third))
        )
        assertEquals(
            "0effdc504ddc2b26b284681c862826d56a3b7ca3f59dda63f6a6196913b7904b",
            sha256Hex(packUInt64LE(tenthStreams.fourth))
        )

        val tenthWorkspace = builder6473d0Tenth64c524Workspace(
            in2 = in2,
            sp430Words = sp430
        )
        assertEquals(44 * 8, tenthWorkspace.size)
        assertEquals(
            "29506e31bf43027e2b607a86a6b4dd22f33d88454f491b93e8872a9171e6a9e2",
            sha256Hex(tenthWorkspace)
        )
        assertEquals(
            "dd5e1b73483adb7c6779b22e8a27aabb3763e5ce240fc28b13c06819287ab5a1",
            Hex.encode(tenthWorkspace.copyOfRange(0, 32))
        )
        assertEquals(
            "9330f51ba1248fb1a1b5f5113f6fbaf9d58bd27f24e9617451eb2ff475dc024d",
            Hex.encode(tenthWorkspace.copyOfRange(tenthWorkspace.size - 32, tenthWorkspace.size))
        )

        val tenthOutput = builder64c524OutputWords(
            arg0 = arg0,
            scalar = scalar,
            x2Workspace = tenthWorkspace
        )
        assertEquals(
            "a36706388c5ac543203e0cd3eb2af42d02f3242aec9d4c183cb4fba7a3d7bb0c",
            sha256Hex(packUInt32LE(tenthOutput))
        )
        assertEquals(
            listOf(0x8783fae8u, 0x0ddad1ebu, 0x8749248cu, 0x4fe49618u),
            tenthOutput.subList(0, 4)
        )
        assertEquals(
            listOf(0xb2d83004u, 0x1d858880u, 0x3da98491u, 0x53b73a9bu),
            tenthOutput.subList(tenthOutput.size - 4, tenthOutput.size)
        )

        val out4 = builder6473d0FinalOut4Words(tenthOutput = packUInt32LE(tenthOutput))
        assertEquals(
            "401fe726d4a75d7c7e6d399aa2e00fea0d56a3d6c5b3dc1b328178ea24d7a442",
            sha256Hex(packUInt32LE(out4))
        )
        assertEquals(
            listOf(0xb434cfbdu, 0x433f90dfu, 0xc1b14cc0u, 0xcff6365fu),
            out4.subList(0, 4)
        )
        assertEquals(
            listOf(0x9b1fe3f8u, 0x4823bd87u, 0xb576ca82u, 0xf3f2e79cu),
            out4.subList(out4.size - 4, out4.size)
        )
    }

    // Swift L4026-4086
    @Test
    fun `testBuilder64cd40OutputWordsMatchPythonReferenceVector`() {
        val arg0 = ByteArray(88) { index -> (index * 7 + 3).toByte() }
        val scalar = 0x0123456789abcdefuL
        val workspaceBytes = ByteArray(352) { index -> (index * 5 + 11).toByte() }
        val x2Workspace = workspaceBytes

        val arg0Words = builder64cd40Arg0U64Words(arg0 = arg0)
        assertEquals(
            "a44f4e91a177dc3a5cb312ef4f9719cdc71ddecceaddc3ec4e2edf789628e5d1",
            sha256Hex(packUInt64LE(arg0Words))
        )
        assertEquals(
            listOf(0x10ddde967190d344uL, 0xebd463e50c8fe285uL, 0x1a0e6a0158814d23uL, 0x0e751bdbe34a0c27uL),
            arg0Words.subList(0, 4)
        )

        val updated = builder64cd40WorkspaceAfterUpdate(
            arg0U64Words = arg0Words,
            scalar = scalar,
            x2Workspace = x2Workspace
        )
        assertEquals(44 * 8, updated.size)
        assertEquals(
            "4f72ca5ec1a21eaefb25bc5e1952d71e22482eff8776ab4d7d7b097dd898f698",
            sha256Hex(updated)
        )
        assertEquals(
            "d7b094c172d1d6f663803ebc41632b3a20173805dd60fba63f6cdf91fc96abe6",
            Hex.encode(updated.copyOfRange(0, 32))
        )
        assertEquals(
            "428e3ebc4f8e7959b5f33857d937a682a920452868aad593c3c8cdd2d7dce1e6",
            Hex.encode(updated.copyOfRange(updated.size - 32, updated.size))
        )

        val output = builder64cd40FinalU32Words(x2Workspace = updated)
        assertEquals(
            "d9242621c538e422518e9e87083886228bb6f88827716cf209de9e42fb4a336d",
            sha256Hex(packUInt32LE(output))
        )
        assertEquals(
            listOf(
                0x00cec4dfu, 0x26e4cde3u, 0xcaeeb424u, 0xe561e5c9u, 0xe47fcf9fu,
                0x0946526du, 0xed187991u, 0xb16fcb0du, 0x3165ae59u, 0xedbc680cu,
                0x5d8e3672u, 0x6f6ecb46u, 0x73c1ecadu, 0xdb28c019u, 0x4d2396d0u,
                0xb9045673u, 0x6e108816u, 0x491e7a22u, 0x6ca69691u, 0xa935ba59u,
                0x67d4d8c8u, 0x511b912cu
            ),
            output
        )
        assertEquals(
            builder64cd40OutputWords(
                arg0 = arg0,
                scalar = scalar,
                x2Workspace = x2Workspace
            ),
            output
        )
    }

    // Swift L4088-4128
    @Test
    fun `testFinalize679f48ToSecondDF80MatchesPythonReferenceVectors`() {
        val vectors = listOf(
            listOf(
                "low4_idx2",
                "132",
                "2",
                "010602060707010605040204060404050206040406050406020404040402020402" +
                    "020204040506060504020604060604040502040504020602040505050604050502",
                "0a47106ad8b1d372b9f821d1ed1c421c9a33ac9c6f91e350c8bd89e4d1497143",
                "040407050303060105040303000500050302040700030303070502040102030405" +
                    "000604000506020200030003060500040200000306060700070205070206030600"
            ),
            listOf(
                "low0_idx3",
                "128",
                "3",
                "010606060505040605040204060404050206040406050406020404040402020402" +
                    "020204040506060504020604060604040502040504020602040505050604050506",
                "17967df31a85c7937a2c2c0da54297a4f45387ede33b3c8aa10d8f584443c20f",
                "040402060207050701070101000501060401010007010403050107000401060205" +
                    "070107070404010306000003050006020503030402070706000100010206040004"
            ),
        )

        for (vector in vectors) {
            val context = make679f48Context(vector[1].toULong(), vector[2].toUInt())
            val finalized = finalize679f48ToSecondDF80(context = context)
            assertEquals(
                vector[3],
                Hex.encode(final679f48LengthBlock(vector[1].toInt())),
                vector[0]
            )
            assertEquals(vector[4], sha256Hex(finalized), vector[0])
            val source = deriveFrom679f48Context(
                context,
                offset = 0,
                length = 16
            )
            assertEquals(vector[5], Hex.encode(source), vector[0])
        }
    }

    // Swift L4130-4137
    @Test
    fun `testFinalize679f48RejectsInvalidBlockIndex`() {
        val context = make679f48Context(contextLength = 132uL, blockIndex = 5u)
        assertFailsWith<FirstPairSourceSliceError> {  finalize679f48ToSecondDF80(context = context)  }.let {
            assertEquals(FirstPairSourceSliceError.invalid679f48BlockIndex(5u), it)
        }
    }

    // Swift L4139-4172
    @Test
    fun `testDF80TransformMatchesPythonReferenceVector`() {
        val blockBytes = ByteArray(4 * 66) { index -> ((index * 5 + 3) and 7).toByte() }
        val stateBytes = ByteArray(8 * 18) { index -> ((index * 7 + 2) and 7).toByte() }
        val blocks = blockBytes
        val state = stateBytes
        val transformed = df80Transform(state = state, blocks = blocks)

        assertEquals(144, transformed.size)
        assertEquals(
            "83d6b1d8af5c9ae3696aa44b9f62680633b0a996598b934781aa571ba0bbe58d",
            sha256Hex(transformed)
        )
        assertEquals(
            "060505060603010607050500030303010703040102050702030300020103040406" +
                "060304030403060005010706050001060203000404030301030000040706010105" +
                "000102030607",
            Hex.encode(transformed.copyOfRange(0, 72))
        )
        assertEquals(
            "040707050405010406070505010204020502060200000307060605040407020503" +
                "070404070704010106060002050303040503010604070007070402050504070703" +
                "040704070607",
            Hex.encode(transformed.copyOfRange(transformed.size - 72, transformed.size))
        )

        val workspace = df80InitialWorkspace(blocks = blocks)
        val schedule = df80ExpandedSchedule(initialWorkspace = workspace)
        val compressed = df80CompressState(state = state, schedule = schedule)
        assertContentEquals(compressed, transformed)
    }

    // Swift L4174-4185
    @Test
    fun `testRejectsInvalidDF80CompressionInputs`() {
        assertFailsWith<FirstPairSourceSliceError> {  df80CompressState(state = ByteArray(143), schedule = ByteArray(0x480))  }.let {
            assertEquals(FirstPairSourceSliceError.invalidDF80StateLength(143), it)
        }
        assertFailsWith<FirstPairSourceSliceError> {  df80CompressState(state = ByteArray(144), schedule = ByteArray(0x47f))  }.let {
            assertEquals(FirstPairSourceSliceError.invalidDF80ScheduleLength(0x47f), it)
        }
    }

    // Swift L4187-4211
    @Test
    fun `testDF80ExpandedScheduleMatchesPythonReferenceVector`() {
        val blockBytes = ByteArray(4 * 66) { index -> ((index * 5 + 3) and 7).toByte() }
        val workspace = df80InitialWorkspace(blocks = blockBytes)
        val schedule = df80ExpandedSchedule(initialWorkspace = workspace)

        assertEquals(0x480, schedule.size)
        assertEquals(
            "19a0a495eb712175fc15dda37e6a5719940376609560f2ca26e3586abde2db77",
            sha256Hex(schedule)
        )
        assertEquals(
            "020607070005060306030005020607060101020000050407040500050007030003" +
                "000706030505050407040304000200040402050302010205040206000104010503" +
                "050402060306",
            Hex.encode(schedule.copyOfRange(0x120, 0x120 + 72))
        )
        assertEquals(
            "030505060104000601010201060203060004070203010405040204000207040103" +
                "060500020604000107000107030305000001000005010004010706020701070400" +
                "050103070204",
            Hex.encode(schedule.copyOfRange(schedule.size - 72, schedule.size))
        )
    }

    // Swift L4213-4219
    @Test
    fun `testRejectsInvalidDF80ScheduleWorkspaceLength`() {
        assertFailsWith<FirstPairSourceSliceError> {  df80ExpandedSchedule(initialWorkspace = ByteArray(287))  }.let {
            assertEquals(FirstPairSourceSliceError.invalidDF80WorkspaceLength(287), it)
        }
    }

    // Swift L4221-4239
    @Test
    fun `testDF80InitialWorkspaceMatchesPythonReferenceVector`() {
        val blockBytes = ByteArray(4 * 66) { index -> ((index * 5 + 3) and 7).toByte() }
        val expectedHex = listOf(
            "020606050700020404010501070401010504060201020704010204010501060601",
            "010305020601020201030306000307070005050200040200040203020404020407",
            "030401070102020600020104040506050500010603040207060207040106030303",
            "050500020506040501020605070606040204030206040403040502060206060301",
            "070106000103000603050002020606040301060100020107060205070303060204",
            "060300050606000107050405070702020604060702060001070305060105070704",
            "000402050404060100040705010205010207020605010505000002070601050207",
            "060001060203000502070503040305020107060504020603000302000407030707",
            "010507060106030301040304000002060007020004030006",
        ).joinToString("")
        val blocks = blockBytes
        val got = df80InitialWorkspace(blocks = blocks)
        assertEquals(expectedHex, Hex.encode(got))
    }

    // Swift L4241-4247
    @Test
    fun `testRejectsInvalidDF80InitialWorkspaceBlockLength`() {
        assertFailsWith<FirstPairSourceSliceError> {  df80InitialWorkspace(blocks = ByteArray(263))  }.let {
            assertEquals(FirstPairSourceSliceError.invalidDF80BlockLength(263), it)
        }
    }

    // Swift L4249-4277
    @Test
    fun `testFinal679f48LengthBlockMatchesPythonReferenceVectors`() {
        val vectors = listOf(
            listOf(
                "0",
                "010606060404040505040204060404050206040406050406020404040402020402" +
                    "020204040506060504020604060604040502040504020602040505050604050506"
            ),
            listOf(
                "4",
                "010602060101040505040204060404050206040406050406020404040402020402" +
                    "020204040506060504020604060604040502040504020602040505050604050502"
            ),
            listOf(
                "68",
                "010602060701070707010204060404050206040406050406020404040402020402" +
                    "020204040506060504020604060604040502040504020602040505050604050502"
            ),
            listOf(
                "132",
                "010602060707010605040204060404050206040406050406020404040402020402" +
                    "020204040506060504020604060604040502040504020602040505050604050502"
            ),
        )

        for (vector in vectors) {
            val got = final679f48LengthBlock(vector[0].toInt())
            assertEquals(vector[1], Hex.encode(got))
        }
    }

    // Swift L4279-4305
    @Test
    fun `testDerive64de54SliceMatchesPythonReferenceVectors`() {
        val encodedBytes = ByteArray(66 * 2) { index -> ((index * 3 + 1) and 7).toByte() }
        val encoded = encodedBytes
        val vectors = listOf(
            listOf(
                "0",
                "040404000403000004010706050306060105010204060302010504030702000005" +
                    "040100010000060707050301030202050401000403030601050100040200020405"
            ),
            listOf(
                "16",
                "040407000000070705020503020004070506070000030105040307060003070705" +
                    "030702030207040100070200030105070205030003010407060403000307040103"
            ),
        )

        for (vector in vectors) {
            val got = derive64de54Slice(
                encodedBlocks = encoded,
                offset = vector[0].toInt(),
                length = 16
            )
            assertEquals(vector[1], Hex.encode(got))
        }
    }

    // Swift L4307-4313
    @Test
    fun `testRejectsInvalidEncodedBlockLength`() {
        assertFailsWith<FirstPairSourceSliceError> {  derive64de54Slice(encodedBlocks = ByteArray(65))  }.let {
            assertEquals(FirstPairSourceSliceError.invalidEncodedBlockLength(65), it)
        }
    }

    // Swift L4315-4341
    @Test
    fun `testDeriveFrom67cc18SourcesMatchesPythonReferenceVectors`() {
        val sourceBytes = ByteArray(66 * 2) { index -> ((index * 5 + 2) and 7).toByte() }
        val source = sourceBytes
        val vectors = listOf(
            listOf(
                "0",
                "040400010204020403070505010102040500070506070505040300070500020600" +
                    "000406070603060706070607020602010602010202010400040206060406000602"
            ),
            listOf(
                "16",
                "040404030306020107070006010600010003020602000507000601000701020004" +
                    "000004010506000003060407010603030005020605010101070405020206020702"
            ),
        )

        for (vector in vectors) {
            val got = deriveFrom67cc18Sources(
                sourceChunks = source,
                offset = vector[0].toInt(),
                length = 16
            )
            assertEquals(vector[1], Hex.encode(got))
        }
    }

    // Swift L4343-4360
    @Test
    fun `testPhase5RawKeyFrom67cc18SourcesMatchesPythonReferenceVectors`() {
        val sourceBytes = ByteArray(66 * 2) { index -> ((index * 5 + 2) and 7).toByte() }
        val source = sourceBytes
        val vectors = listOf(
            listOf("0", "1fc9367dbfe4d23015419023b8ff18b6"),
            listOf("16", "4b92eac60192ed83e6666a2810a936a6"),
        )

        for (vector in vectors) {
            val got = phase5RawKeyFrom67cc18Sources(
                sourceChunks = source,
                offset = vector[0].toInt()
            )
            assertEquals(vector[1], Hex.encode(got))
        }
    }

    // Swift L4362-4395
    @Test
    fun `testDeriveFrom67a960InputsMatchesPythonReferenceVectors`() {
        val src1 = ByteArray(130) { index -> ((index * 3 + 4) and 7).toByte() }
        val src2 = ByteArray(130) { index -> ((index * 5 + 1) and 7).toByte() }
        val vectors = listOf(
            listOf(
                "0",
                "040400040302000203010504050606010602060206010706040604020201000605" +
                    "060300050706070506050406030505060205040406070504060105050706010702",
                "1a36bec545101e734f469c930b565b59"
            ),
            listOf(
                "16",
                "040400000700070601050100070301030104070402060100060100050502030707" +
                    "030004040000020104020600040306040607040304000206050404040402060606",
                "61efbe0d4f32b0c424a29ff609c73a18"
            ),
        )

        for (vector in vectors) {
            val source = deriveFrom67a960Inputs(
                src1 = src1,
                src2 = src2,
                offset = vector[0].toInt(),
                length = 16
            )
            assertEquals(vector[1], Hex.encode(source))
            val key = phase5RawKeyFrom67a960Inputs(
                src1 = src1,
                src2 = src2,
                offset = vector[0].toInt()
            )
            assertEquals(vector[2], Hex.encode(key))
        }
    }

    // Swift L4397-4427
    @Test
    fun `testDeriveFromFinalized679f48ContextMatchesPythonReferenceVectors`() {
        val context = ByteArray(0x20c) { index -> ((index * 7 + 3) and 7).toByte() }
        val vectors = listOf(
            listOf(
                "0",
                "040405070607060600000304050601020107020701070601000201030506040705" +
                    "060707060203060200050700050006050303040107020404040607010306000605",
                "1e6348e3a52751cbac7cc95200f39d9e"
            ),
            listOf(
                "16",
                "040405010207070104030605020701010403030404070701070005030507010101" +
                    "070705070202030500000005010301060606010703040400070207020302020707",
                "b2f4925e0545eb07acd86a4c00beee05"
            ),
        )

        for (vector in vectors) {
            val source = deriveFromFinalized679f48Context(
                context,
                offset = vector[0].toInt(),
                length = 16
            )
            assertEquals(vector[1], Hex.encode(source))
            val key = phase5RawKeyFromFinalized679f48Context(
                context,
                offset = vector[0].toInt()
            )
            assertEquals(vector[2], Hex.encode(key))
        }
    }

    // Swift L4429-4466
    @Test
    fun `testProcess2P5PublicKeyMatchesAndroidEntryTraces`() {
        val vectors = listOf(
            listOf(
                "8987c91f1595e8a060e4cba652368ae8797e9113cfd412bebd0ea1a03783ae59" +
                    "ee70d2c947578803b06b275c96632d148b81658bb87a3eabb5755273c40c397" +
                    "f7255f3c1d742df608383fbbfff5a9b9fbc11a1ab525382024c85687cf79c2" +
                    "a391ca7cc309ff82fe098c2d86e49f8b26364153f0bcb8945c887f5a2a7b5" +
                    "4d568daa373a86c85c283fbb6285f35dca2d30263c34ce182c1fc63e6022a" +
                    "3c7e6eaebe3a473d3c754bb8f3982172431af66388948aaf5c709f6699b76" +
                    "08dcd161811dda99c61b302f46684433e61ef2afa4dd9f8b0f2472f612019" +
                    "7cdfc0b940ad5f93ac01fc7497fb355c753df9c65fc68721690c35a09550fb" +
                    "3c326e38bcbe37ebb309a680c383967627f58a108e1e94ecd16c5d2bc2f57" +
                    "6dabdc7b",
                "04b60e0f455a1f2ebc3a1246d9311a66722f80fbc0cbdc23d18ae5e50693eed2" +
                    "b1ea74d24eddcc8dd1957cf621a1f5514fcd7b40ec37f18f8c8060db6f8076b121"
            ),
            listOf(
                "726d47655b9434b44cd08664665dfb86934638911b6ebcc26420fe124ab654fd" +
                    "e722e77f43756603943a8ee8196c6d5f83fc9cfe637e309f6f4b3c8fd5f10" +
                    "9596f60b9e4899422925b8a0368b143580541bcaac3b4017b82f38d00c14d" +
                    "46fbe3197ccfa9af048f6b446973c664901b84d362e95086e235e58517883" +
                    "f7b89aef742768adc355131885657b686bdb6bd82feb11591b63f3e9466f0" +
                    "e21f20cc58757ac547f57a21ee59b4816779510bd7d911861a116c40332328" +
                    "cd4ec68579831e76ede1a5c6776c9d114a2788e8aed94b8f50a051da8cd8b" +
                    "dbdf7c77f53ce76ee259d5d568a7b71edd3564f80969a4550a920238d1739" +
                    "b34eceeb275c29f8dfb94796005ff15989a177536119388ed70c8fb6fa721" +
                    "09635da2741",
                "049cb2d2658568e6685fea83f5051ff703baec07cbca3b10e58600d538b85795db" +
                    "5cd35248bd30f1918627a6d4f2f91ce31d21057279fa790b895b15192d040a99"
            ),
        )

        for (vector in vectors) {
            val entropy = Hex.decode(vector[0])
            val publicKey = builderProcess2P5PublicKey65FromEntropy(entropy11A = entropy)
            assertEquals(vector[1], Hex.encode(publicKey))
        }
    }
}
