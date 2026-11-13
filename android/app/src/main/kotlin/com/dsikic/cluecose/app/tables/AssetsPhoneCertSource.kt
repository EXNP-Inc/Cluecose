package com.dsikic.cluecose.app.tables

import android.content.res.AssetManager
import com.dsikic.cluecose.core.pairing.PhoneCertSource

/**
 * [PhoneCertSource] over the app's vendored phone certificates in
 * `app/src/main/assets/RuntimeTables/` (`phone_cert_162b.bin`,
 * `phone_cert_firstpair.bin`), mirroring [AssetsTablesSource].
 */
class AssetsPhoneCertSource(
    private val assets: AssetManager,
    private val root: String = "RuntimeTables",
) : PhoneCertSource {
    override fun cert(name: String): ByteArray? {
        return try {
            assets.open("$root/$name.bin").use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}
