package com.dsikic.cluecose

import android.app.Application
import com.dsikic.cluecose.app.tables.AssetsPhoneCertSource
import com.dsikic.cluecose.core.pairing.PhoneCertConfig

class CluecoseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The `:core` bundled phone certificates load from this installed
        // source (`RuntimeTables/<name>.bin` in app assets) — `PhoneCert`
        // cannot reach the Android asset manager on its own (core is pure
        // Kotlin/JVM).
        PhoneCertConfig.source = AssetsPhoneCertSource(assets)
    }
}
