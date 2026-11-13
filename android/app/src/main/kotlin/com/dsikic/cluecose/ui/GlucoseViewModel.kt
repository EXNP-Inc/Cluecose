package com.dsikic.cluecose.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.dsikic.cluecose.libre.RealGlucoseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope

/**
 * Data contract for the glucose source. The UI only ever talks to this
 * interface; the real implementation is
 * [com.dsikic.cluecose.libre.RealGlucoseRepository] (BLE + NFC).
 */
interface GlucoseRepository {
    /** Mirrors the iOS `LibreDirectReader` callbacks. */
    fun interface Listener {
        fun onEvent(event: Event)
    }

    sealed interface Event {
        /** Live progress line (NFC/BLE/handshake status). */
        data class Status(val text: String) : Event
        data class Link(val active: Boolean) : Event

        /**
         * Sensor identity + age. [sensorName] is the paired sensor's serial
         * (what the "Paired sensor" settings row shows); null = no sensor.
         * Defaulted so existing 2-arg call sites still compile (documented
         * relaxation of the original 2-field shape).
         */
        data class SensorMeta(val ageMinutes: Int?, val expired: Boolean, val sensorName: String? = null) : Event

        data class Samples(val samples: List<GlucoseSample>) : Event
        data class NfcAwaiting(val awaiting: Boolean) : Event
    }

    var listener: Listener

    /** Kick the link (from launch or a "Start scanning" tap). */
    fun startListening()

    /** Tear the link down. */
    fun stopListening()

    /** NFC tap to pair a (new) sensor. */
    fun nfcPair()

    /** Forget the paired sensor (clears persisted pairing state; iOS `clearSensor`). */
    fun forgetSensor()
}

/**
 * Owns the [UiState] the screens render. Wires the [GlucoseRepository] events
 * into derived analytics (trend / stats / burn mode / tips) — the Android
 * mirror of iOS `GlucoseModel.refreshDerived` + `recomputeAll`.
 *
 * [android.app.Application]-backed so it can construct the real
 * [RealGlucoseRepository] (SharedPreferences + Bluetooth + NFC all need the
 * app context). The `@JvmOverloads` 1-arg constructor is what the default
 * Compose `viewModel()` factory requires (`AndroidViewModelFactory` looks up
 * `constructor(Application)`); [provided] exists for tests.
 */
class GlucoseViewModel
    @JvmOverloads constructor(
        application: Application,
        private val provided: GlucoseRepository? = null,
    ) : AndroidViewModel(application) {

    /**
     * The real BLE-backed source. Public so MainActivity can attach the
     * Activity-bound NFC reader before first use.
     */
    val repository: GlucoseRepository = provided ?: RealGlucoseRepository(getApplication())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var samples: List<GlucoseSample> = emptyList()
    private var sensorExpired = false
    private var started = false

    init {
        repository.listener = GlucoseRepository.Listener { event -> handleEvent(event) }
        // Keep the "now" clock (and reading-age text) moving without events.
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                refreshDerived()
            }
        }
        startListening()
    }

    fun startListening() {
        started = true
        _state.update { it.copy(connectionState = ConnectionState.LISTENING) }
        repository.startListening()
    }

    fun stopListening() {
        started = false
        repository.stopListening()
    }

    fun nfcPair() {
        repository.nfcPair()
    }

    fun forgetSensor() {
        repository.forgetSensor()
    }

    /**
     * Set the receiver ID (little-endian hex, e.g. `d82eec8f`) the sensor's
     * current owner used to activate it. Presented first by the NFC candidate
     * ladder; null clears it. Mirrors iOS's account-ID field for the
     * "sensor bound to another app/install" case.
     */
    fun setOwnerReceiverId(leHex: String?) {
        (repository as? RealGlucoseRepository)?.nfcOwnerReceiverIdLEHex = leHex
    }

    /** The persisted owner receiver ID ("" when unset) — for the settings field. */
    fun ownerReceiverIdLEHex(): String =
        (repository as? RealGlucoseRepository)?.nfcOwnerReceiverIdLEHex.orEmpty()

    /** Header unit toggle (mg/dL <-> mmol/L). */
    fun setUnit(unit: GlucoseUnit) {
        _state.update { it.copy(unit = unit) }
    }

    private fun handleEvent(event: GlucoseRepository.Event) {
        when (event) {
            is GlucoseRepository.Event.Status ->
                _state.update { it.copy(status = event.text) }

            is GlucoseRepository.Event.Link -> {
                val next = when {
                    event.active -> ConnectionState.CONNECTED
                    samples.isEmpty() -> if (started) ConnectionState.LISTENING else ConnectionState.IDLE
                    else -> ConnectionState.RECONNECTING
                }
                _state.update { it.copy(connectionState = next) }
            }

            is GlucoseRepository.Event.SensorMeta -> {
                sensorExpired = event.expired
                _state.update {
                    it.copy(
                        sensorAgeText = DemoAnalytics.sensorAgeText(event.ageMinutes),
                        isWarmingUp = event.ageMinutes != null && event.ageMinutes <= 24 * 60,
                        // Direct assignment: every live-sensor emission carries
                        // the serial, and "forget" (null) must clear it.
                        sensorName = event.sensorName,
                    )
                }
            }

            is GlucoseRepository.Event.Samples -> {
                samples = event.samples.sortedBy { it.dateMs }
                refreshDerived()
            }

            is GlucoseRepository.Event.NfcAwaiting -> {
                _state.update { it.copy(nfcAwaiting = event.awaiting) }
            }
        }
        if (sensorExpired) {
            _state.update {
                it.copy(error = "This sensor is expired — apply a new one, then tap it with your phone.")
            }
        }
    }

    private fun refreshDerived() {
        val now = System.currentTimeMillis()
        val latest = samples.lastOrNull()
        val trend = DemoAnalytics.computeTrend(samples, now)
        val stats = DemoAnalytics.stats(samples, now)
        _state.update {
            it.copy(
                nowMs = now,
                samples = samples,
                trend = trend,
                stats = stats,
                burnMode = DemoAnalytics.burnMode(stats, trend),
                tips = DemoAnalytics.tips(stats, latest, trend),
                latest = latest,
                isFresh = latest != null && now - latest.dateMs < 25 * 60_000,
                readingAgeText = DemoAnalytics.readingAgeText(latest, now),
                error = if (sensorExpired) {
                    "This sensor is expired — apply a new one, then tap it with your phone."
                } else {
                    null
                },
            )
        }
    }

    override fun onCleared() {
        // Tear down the live link when the app is going away: stops the
        // reconnect loop, disconnects GATT, and drops the foreground
        // "link-keeper" notification. (Documented deviation: the iOS model
        // keeps its reader alive for the process lifetime; Android's
        // ViewModel-onCleared is the right hook for that here.)
        repository.stopListening()
        super.onCleared()
    }
}
