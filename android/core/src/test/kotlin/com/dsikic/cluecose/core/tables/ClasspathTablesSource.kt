package com.dsikic.cluecose.core.tables

/**
 * [TablesSource] backed by JVM classpath resources under `RuntimeTables/`
 * (see `core/src/test/resources/RuntimeTables/`). Test-only.
 */
class ClasspathTablesSource(private val root: String = "RuntimeTables") : TablesSource {
    override fun load(table: RuntimeTable): ByteArray {
        val stream = javaClass.classLoader.getResourceAsStream("$root/${table.fileName}.bin")
            ?: throw TablesSourceError.MissingTable(table.fileName)
        return stream.use { it.readBytes() }
    }
}
