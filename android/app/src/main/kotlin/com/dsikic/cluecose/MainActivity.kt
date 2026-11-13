package com.dsikic.cluecose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dsikic.cluecose.libre.RealGlucoseRepository
import com.dsikic.cluecose.nfc.NfcActivationReader
import com.dsikic.cluecose.ui.Backdrop
import com.dsikic.cluecose.ui.CluecoseColors
import com.dsikic.cluecose.ui.CluecoseUiTheme
import com.dsikic.cluecose.ui.GlucoseViewModel
import com.dsikic.cluecose.ui.HeaderRow
import com.dsikic.cluecose.ui.ListeningScreen
import com.dsikic.cluecose.ui.MainScreen
import com.dsikic.cluecose.ui.SettingsSheet
import com.dsikic.cluecose.ui.UiState

/**
 * Single-activity host. Wires the two Activity-bound pieces the repository
 * cannot own itself:
 *
 *   1. the runtime permission request (BLUETOOTH_SCAN/CONNECT on API 31+,
 *      ACCESS_FINE_LOCATION on ≤30, POST_NOTIFICATIONS on 33+), and
 *   2. the [NfcActivationReader] — Android's reader mode is bound to the
 *      [Activity], so there is no CoreNFC-style system sheet; the in-app
 *      "hold the top of the phone to the sensor" prompt (driven by
 *      [UiState.nfcAwaiting]) plays that role.
 *
 * The ViewModel already calls `startListening()` in its init: before the
 * permissions are granted that surfaces "Reconnect failed: …" and the
 * repository retries on its own backoff (2/4/8/16/30 s) until the grant
 * arrives, at which point this activity also kicks it once more.
 */
class MainActivity : ComponentActivity() {

    /** Same instance the Composable layer renders (shared ViewModelStore). */
    private val viewModel: GlucoseViewModel by viewModels()

    /** Activity-bound NFC reader (reader mode needs the Activity). */
    private var nfcReader: NfcActivationReader? = null

    /**
     * Grant → kick the link. POST_NOTIFICATIONS is deliberately NOT gated on:
     * without it the link-keeper notification is simply hidden on API 33+,
     * the foreground service itself still runs.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val coreNeeded = requiredPermissions().filter { it != Manifest.permission.POST_NOTIFICATIONS }
        if (coreNeeded.isNotEmpty() && coreNeeded.all { grants[it] == true }) {
            viewModel.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcReader = NfcActivationReader(this).also { reader ->
            // The reader hook lives on the concrete repository (the
            // ViewModel exposes the [GlucoseRepository] interface).
            (viewModel.repository as? RealGlucoseRepository)?.nfcReader = reader
        }

        val pending = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (pending.isNotEmpty()) {
            permissionLauncher.launch(pending.toTypedArray())
        }

        setContent {
            CluecoseUiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CluecoseRoot(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reader mode is suspended while the activity is paused; this
        // re-arms an in-flight pairing exchange.
        nfcReader?.resume()
    }

    override fun onDestroy() {
        // Idempotent: the ViewModel's onCleared (fired by super.onDestroy
        // clearing the store) already stopped it via the repository.
        nfcReader?.stop()
        super.onDestroy()
    }

    /**
     * The runtime permissions the link needs, per API level:
     * API 31+ — BLUETOOTH_SCAN (neverForLocation) + BLUETOOTH_CONNECT;
     * API ≤ 30 — ACCESS_FINE_LOCATION (scan results require it);
     * API 33+ — POST_NOTIFICATIONS (link-keeper notification visibility).
     */
    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Root: the full-bleed ocean-teal backdrop behind a scrollable column.
 * The header ("Cluecose / CGM · direct Bluetooth" + unit toggle + settings)
 * is always up, mirroring iOS `ContentView`; below it the listening state or
 * the live main screen, switched on the connection state.
 */
@Composable
private fun CluecoseRoot(viewModel: GlucoseViewModel) {
    val state: UiState by viewModel.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Backdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            HeaderRow(
                state = state,
                onUnitChange = viewModel::setUnit,
                onSettings = { showSettings = true },
            )
            if (state.hasLiveReading) {
                MainScreen(
                    state = state,
                    onUnitChange = viewModel::setUnit,
                )
            } else {
                ListeningScreen(
                    state = state,
                    onStart = viewModel::startListening,
                    onNfc = viewModel::nfcPair,
                )
                Text(
                    text = "Reads your sensor directly over Bluetooth — no account, no cloud. Not a medical device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CluecoseColors.white(0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                )
            }
        }

        if (showSettings) {
            var receiverId by remember {
                mutableStateOf(viewModel.ownerReceiverIdLEHex())
            }
            SettingsSheet(
                state = state,
                ownerReceiverId = receiverId,
                onOwnerReceiverIdChange = {
                    receiverId = it
                    viewModel.setOwnerReceiverId(it.trim().takeIf { s -> s.isNotEmpty() })
                },
                onDismiss = { showSettings = false },
                onRePair = {
                    showSettings = false
                    viewModel.nfcPair()
                },
                onForgetSensor = {
                    showSettings = false
                    viewModel.forgetSensor()
                },
            )
        }
    }
}
