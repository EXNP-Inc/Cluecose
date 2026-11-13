package com.dsikic.cluecose.core.pairing

/**
 * Phone certificate — 162 bytes, sent as the first phone-→sensor write
 * during a fresh-pair handshake (Phase 1, handle 0x002d).
 * 1:1 port of LibreCRKit `Pairing/PhoneCert.swift`.
 *
 * Format:
 *
 *   [0..2)    msg_type / version  (e.g. 03 03 or 03 00)
 *   [2..18)   16B test pattern    (canonically 01 02 03 ... 0f 10)
 *   [18..33)  15B header          (00 01 61 89 76 55 01 + 8 zero bytes)
 *   [33]      0x04 (P-256 uncompressed-point prefix)
 *   [34..98)  X(32) || Y(32)      ← phone STATIC pubkey
 *   [98..162) ECDSA signature     (64B raw r || s)
 *
 * Cert family and live first-pair status:
 * The bundled `phone_cert_firstpair.bin` (`03 00...`) is rejected by
 * live fresh sensors and is kept only as a default for tests and controlled
 * experiments. Working first-pair uses the `03 03` family cert
 * (`phone_cert_162b.bin`), now also bundled by this repo and loadable via
 * [bundled162b]. The `03 03` prefix selects the native index-1 static
 * scalar window below via [phase5StaticScalarWindowOverride].
 *
 * Provenance of the `03 03` cert: `phone_cert_162b.bin` is byte-exact
 * Juggluco's `LIBRE3_APP_CERTIFICATES_B[1]` (open-source app, repo
 * `github.com/j-kaltes/Juggluco`, `ECDHCrypto.java`). It is a static, public,
 * artifact shipped identically to every Juggluco install — a universal cert,
 * not per-user/per-device material — which is why it pairs fresh sensors
 * generally and is safe to vendor here.
 *
 * 2026-06-25 update — supersedes the earlier "still failed before Phase 6"
 * note: fresh-pair now completes through Phase 6 on live sensors and has in
 * production for weeks (LoopKit/LibreLoop's `LibreLoopPairingService`). The
 * missing piece was not the cert alone but coupling the `03 03` cert to a
 * deterministic native ephemeral: derive a keypair plus matching entropy with
 * `SessionKey.makeFirstPairNativeEphemeral(entropySource:)`, pass the keypair
 * as `PairingFlow(phoneEph:)`, and feed the SAME entropy into
 * `runCommandGatedFirstPairHandshake(blePIN:maxEntropyAttempts:1,
 * entropySource:)` so the phone ephemeral and the Phase 5 key source derive
 * from one native entropy. A random ephemeral does not pair.
 */
data class PhoneCert(val raw: ByteArray, val staticPub: ByteArray) {

    init {
        if (raw.size != TOTAL_SIZE) throw PhoneCertError.WrongSize(raw.size)
        if ((staticPub[0].toInt() and 0xFF) != 0x04) throw PhoneCertError.NotUncompressedPoint
    }

    /**
     * 1:1 port of Swift `PhoneCert.init(raw:)` — size check first (Swift
     * `guard raw.count == Self.totalSize`), then `raw[33..<98]`, then the
     * 0x04 prefix check.
     */
    constructor(raw: ByteArray) : this(
        raw = raw,
        staticPub = extractStaticPub(raw),
    )



    /**
     * First-pair Phase 5 static-scalar policy keyed on the cert family.
     * `03 00` uses the entry-source-derived scalar; `03 03` uses the index-1
     * native scalar window, which is the family that pairs live sensors when
     * combined with the native-ephemeral Phase 5 derivation (see the cert
     * family note above).
     */
    val phase5StaticScalarWindowOverride: ByteArray?
        get() {
            if (raw.size < 2 ||
                (raw[0].toInt() and 0xFF) != 0x03 ||
                (raw[1].toInt() and 0xFF) != 0x03
            ) {
                return null
            }
            return FirstPairStaticScalarWindow.firstPairIndex1
        }

    companion object {
        const val TOTAL_SIZE: Int = 162
        const val STATIC_PUB_SIZE: Int = 65
        const val PUBKEY_RANGE_START: Int = 33
        const val PUBKEY_RANGE_END: Int = 98 // Swift `pubkeyRange = 33..<98` (half-open)

        /**
         * Loads the bundled `phone_cert_firstpair.bin` (`03 00`). This is the
         * default resource for tests and controlled experiments only — live
         * fresh sensors reject it. For live first-pair use [bundled162b].
         */
        fun bundledFirstPair(): PhoneCert = bundled(named = "phone_cert_firstpair")

        /**
         * Loads the bundled `phone_cert_162b.bin` (`03 03`), the cert family
         * that pairs live fresh sensors when combined with the
         * native-ephemeral Phase 5 derivation (see the cert-family note above).
         * This is Juggluco's universal `LIBRE3_APP_CERTIFICATES_B[1]`; clients no
         * longer need to vendor their own copy.
         */
        fun bundled162b(): PhoneCert = bundled(named = "phone_cert_162b")

        /**
         * Android seam for Swift `Bundle.module.url(forResource:withExtension:"bin",
         * subdirectory:"RuntimeTables")` + `Data(contentsOf:)`: the bytes come
         * from the installed [PhoneCertConfig.source] (`RuntimeTables/<name>.bin`).
         *
         * @throws PhoneCertError.BundledResourceMissing when no source is installed
         *   or it does not provide [named].
         */
        fun bundled(named: String): PhoneCert {
            val bytes = PhoneCertConfig.source?.cert(named)
                ?: throw PhoneCertError.BundledResourceMissing(named)
            return PhoneCert(bytes)
        }

        // Kept out of the constructor delegate so the size guard (and thus the
        // Swift error order) runs before the slice — same order as
        // `guard raw.count == Self.totalSize` → `raw.subdata(in: pubkeyRange)`.
        private fun extractStaticPub(raw: ByteArray): ByteArray {
            if (raw.size != TOTAL_SIZE) throw PhoneCertError.WrongSize(raw.size)
            return raw.copyOfRange(PUBKEY_RANGE_START, PUBKEY_RANGE_END)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhoneCert) return false
        return raw.contentEquals(other.raw) && staticPub.contentEquals(other.staticPub)
    }

    override fun hashCode(): Int = 31 * raw.contentHashCode() + staticPub.contentHashCode()
}

/**
 * Native `0x6388f0` row-59 static scalar window for the Abbott
 * cert-private index 1 (`03 03` cert family).
 * 1:1 port of Swift `FirstPairStaticScalarWindow`.
 */
object FirstPairStaticScalarWindow {

    private val firstPairIndex1Prefix: ByteArray = byteArrayOf(
        0x97.toByte(), 0x8d.toByte(), 0x11.toByte(), 0xed.toByte(), 0x64.toByte(), 0x6e.toByte(), 0xe3.toByte(), 0x55.toByte(),
        0x93.toByte(), 0x36.toByte(), 0xd5.toByte(), 0xfe.toByte(), 0xba.toByte(), 0x58.toByte(), 0x7c.toByte(), 0xe9.toByte(),
        0x84.toByte(), 0x12.toByte(), 0x31.toByte(), 0x98.toByte(), 0xcd.toByte(), 0x9e.toByte(), 0x88.toByte(), 0x0d.toByte(),
        0x34.toByte(), 0xba.toByte(), 0xd0.toByte(), 0xfa.toByte(), 0xc8.toByte(), 0xa9.toByte(), 0x97.toByte(), 0xbf.toByte(),
    )

    /**
     * Native `0x6388f0` row-59 static scalar window for the Abbott
     * cert-private index 1 (`03 03` cert family): 32-byte prefix + 38 zero
     * bytes = 70 bytes.
     */
    val firstPairIndex1: ByteArray = ByteArray(70).also { out ->
        System.arraycopy(firstPairIndex1Prefix, 0, out, 0, firstPairIndex1Prefix.size)
    }
}

/**
 * 1:1 port of Swift `PhoneCertError` (same cases, so error equality matches).
 */
sealed class PhoneCertError(message: String = "phone certificate failure") : Exception(message) {

    /** `raw.count != 162` (Swift `.wrongSize`). */
    data class WrongSize(val size: Int) : PhoneCertError("wrong size: $size")

    /** `raw[33] != 0x04` (Swift `.notUncompressedPoint`). */
    object NotUncompressedPoint : PhoneCertError("static pubkey is not an uncompressed point")

    /** Bundled `RuntimeTables/<resource>.bin` unavailable (Swift `.bundledResourceMissing`). */
    data class BundledResourceMissing(val resource: String) :
        PhoneCertError("bundled resource missing: $resource")
}

/**
 * Injectable source for the bundled phone certificates — mirrors the
 * `com.dsikic.cluecose.core.tables.TablesSource` seam so `:core` stays
 * pure JVM:
 *
 * - JVM tests: `ClasspathPhoneCertSource` over
 *   `core/src/test/resources/RuntimeTables/`.
 * - Android app: an `AssetsPhoneCertSource` over
 *   `app/src/main/assets/RuntimeTables/` (installed by the app layer).
 */
fun interface PhoneCertSource {
    /**
     * @param name the certificate stem: `"phone_cert_162b"` or
     *   `"phone_cert_firstpair"` (i.e. `RuntimeTables/<name>.bin`).
     * @return the certificate bytes, or null when the resource is absent.
     */
    fun cert(name: String): ByteArray?
}

/**
 * Holder for the installed [PhoneCertSource] (null until one is installed —
 * the default, matching "no bundle available on this platform").
 *
 * - Tests install [ClasspathPhoneCertSource] in `@BeforeTest`.
 * - The Android app installs its `AssetsPhoneCertSource` at startup.
 */
object PhoneCertConfig {
    var source: PhoneCertSource? = null
}
