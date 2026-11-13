package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.tables.SourceTables
import com.dsikic.cluecose.core.tables.TablesSource

/**
 * VM configuration seam. The Swift original loads its 23 vendored tables once
 * via `cachedTables` (a lazy `Result` over the global `Tables` source). Android
 * keeps the same lazy-load semantics but takes the [TablesSource] explicitly:
 *
 * - `:core` JVM tests install `ClasspathTablesSource()` once;
 * - the app installs `AssetsTablesSource(context.assets)` once at startup.
 *
 * Set [tablesSource] exactly once before the first VM call.
 */
object FirstPairConfig {
    var tablesSource: TablesSource? = null

    /** Lazy, cached — mirrors Swift `cachedTables: Result<SourceTables, Error>`. */
    val tables: SourceTables
        get() = SourceTables.load(tablesSource ?: error(
            "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
        ))
}
