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

    // Identity-keyed cache of the 23-table bundle (5.1 MB — 512 KB sbox19 +
    // 1 MB ttable_b_ext + 20 more). Mirrors Swift `cachedTables:
    // Result<SourceTables, Error>`: the tables are immutable for the process
    // lifetime, so load them ONCE per installed source.
    //
    // The Phase-5 VM pipeline touches `FirstPairConfig.tables` in ~184 places
    // (FirstPairVM.c00–c09). Before this cache existed the property was a
    // plain getter: every access re-read all 23 files from the APK. On the
    // field Samsung that turned the ~2-s Phase-5 derivation into a 3.6-min
    // allocation storm (pair19 04:37:55→04:41:34: ~13 GC collections freeing
    // 36 MB of large objects each ≈ 90 full reloads). The sensor kills the
    // link ~24 s after the challenge, so the derivation had to land within
    // that window — the cache is what makes it possible.
    @Volatile private var cachedTables: SourceTables? = null
    @Volatile private var cachedTablesSource: TablesSource? = null

    /** Lazy, cached per source identity — mirrors Swift `cachedTables`. */
    val tables: SourceTables
        get() {
            val source = tablesSource ?: error(
                "FirstPairConfig.tablesSource not installed — set FirstPairConfig.tablesSource first"
            )
            val cached = cachedTables
            val cachedFor = cachedTablesSource
            if (cached != null && cachedFor === source) return cached
            val loaded = SourceTables.load(source)
            cachedTables = loaded
            cachedTablesSource = source
            return loaded
        }
}
