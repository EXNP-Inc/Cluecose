package com.dsikic.cluecose.app.tables

import android.content.res.AssetManager
import com.dsikic.cluecose.core.tables.RuntimeTable
import com.dsikic.cluecose.core.tables.TablesSource
import com.dsikic.cluecose.core.tables.TablesSourceError

/**
 * [TablesSource] over the app's vendored runtime tables in
 * `app/src/main/assets/RuntimeTables/`.
 */
class AssetsTablesSource(
    private val assets: AssetManager,
    private val root: String = "RuntimeTables",
) : TablesSource {
    override fun load(table: RuntimeTable): ByteArray {
        return try {
            assets.open("$root/${table.fileName}.bin").use { it.readBytes() }
        } catch (e: Exception) {
            throw TablesSourceError.MissingTable(table.fileName)
        }
    }
}
