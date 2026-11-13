package com.dsikic.cluecose.core.pairing

/**
 * [PhoneCertSource] backed by JVM classpath resources under `RuntimeTables/`
 * (see `core/src/test/resources/RuntimeTables/`). Test-only.
 *
 * The app layer installs its `AssetsPhoneCertSource` over
 * `app/src/main/assets/RuntimeTables/` instead — same [PhoneCertSource] seam,
 * mirroring `TablesSource` / `ClasspathTablesSource` / `AssetsTablesSource`.
 */
class ClasspathPhoneCertSource(private val root: String = "RuntimeTables") : PhoneCertSource {
    override fun cert(name: String): ByteArray? {
        val stream = javaClass.classLoader.getResourceAsStream("$root/$name.bin") ?: return null
        return stream.use { it.readBytes() }
    }
}
