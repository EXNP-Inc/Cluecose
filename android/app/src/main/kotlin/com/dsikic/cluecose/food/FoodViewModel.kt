//
//  FoodViewModel.kt
//  Cluecose
//
//  The brain of the food-log feature (the Android mirror of iOS
//  `FoodLogModel`). Owns the persistence store ([FoodRepository]), the LLM
//  client ([FoodClient], injectable for tests), and the on-device insight /
//  patterns computation from the shared `:core` engines.
//
//  • Insights run the shared MealInsightEngine on the merged glucose series
//    (injected via [GlucoseSampleProvider] — the same samples the
//    `GlucoseViewModel` renders), gated on "final only": an entry gets an
//    insight once its full 180-min post-meal window has elapsed and it has
//    none yet. A cross-meal comparison ("your usual response") is built from
//    PRIOR similar entries whose insight already fired.
//  • Patterns / coaching come from the shared PatternsEngine (7-day window).
//  • Nutrition-sleep is an Apple-surface concern (HealthKit sleep) and is
//    deferred on Android.
//

package com.dsikic.cluecose.food

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsikic.cluecose.core.food.CrossMealComparison
import com.dsikic.cluecose.core.food.FoodEntry
import com.dsikic.cluecose.core.food.FoodSource
import com.dsikic.cluecose.core.food.FoodTotals
import com.dsikic.cluecose.core.food.InsightConfig
import com.dsikic.cluecose.core.food.InsightInput
import com.dsikic.cluecose.core.food.MealInsightEngine
import com.dsikic.cluecose.core.food.NutritionPatterns
import com.dsikic.cluecose.core.food.NutritionPrompt
import com.dsikic.cluecose.core.food.PatternsEngine
import com.dsikic.cluecose.core.food.SourceCitation
import com.dsikic.cluecose.ui.GlucoseSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The glucose series source for the insight engine — the Android seam for
 * iOS's `GlucoseModel.allSamples`. The app wires the live `GlucoseViewModel`
 * samples in (the `provided` test seam injects a fixed list instead).
 */
interface GlucoseSampleProvider {
    fun currentSamples(): List<GlucoseSample>
}

/**
 * Whole food-surface snapshot the Compose layer renders. Recomputed whenever
 * anything changes (iOS `FoodLogModel` published properties).
 */
data class FoodUiState(
    /** All logged entries (insertion order = oldest → newest). */
    val entries: List<FoodEntry> = emptyList(),
    /** The day (ms) the food card is showing (default: now). */
    val selectedDayMs: Long = System.currentTimeMillis(),
    /** True while an LLM estimate is in flight (sheet spinner). */
    val isEstimating: Boolean = false,
    /** The 7-day patterns (shared engine). */
    val patterns: NutritionPatterns = NutritionPatterns(),
    /** Coaching follow-up questions from the patterns. */
    val coaching: List<String> = emptyList(),
)

/**
 * The per-meal analytics state, as of `now` (iOS `FoodLogModel.MealAnalytics`):
 *
 *  • [Ready]   — the insight has been computed and is on the entry.
 *  • [Pending] — the post-meal window hasn't fully elapsed yet: the curve is
 *    still forming, and the analysis will run at [Pending.readyAtMs].
 *  • [Settled] — the window has fully elapsed and the engine ran (or found
 *    the curve flat / the data too sparse to judge): no insight will ever
 *    appear for this meal. Terminal, not "waiting".
 *  • [NoData]  — the app has no glucose series at all, so nothing can be
 *    analyzed yet.
 */
sealed interface MealAnalytics {
    object Ready : MealAnalytics
    data class Pending(val readyAtMs: Long, val remainingMin: Int) : MealAnalytics
    object Settled : MealAnalytics
    object NoData : MealAnalytics
    /**
     * There IS a glucose series, but none of it falls inside this meal's
     * analysis window (the 30 min before through the 3 h after): the engine
     * has nothing to compare against, so the analysis can never run. Typical
     * for after-the-fact entries logged before the sensor started, across an
     * app gap, or into a pre-pairing era. A terminal "no data in window"
     * result — NOT a flat-curve "no notable response" result.
     * (iOS `MealAnalytics.noWindowData`.)
     */
    object NoWindowData : MealAnalytics

    /** True while the insight is still being awaited. */
    val isPending: Boolean
        get() = this is Pending
}

/**
 * The pure analytics-state decision (kept static so it is unit-testable
 * without a model) — the Android twin of iOS `analyticsState`.
 *
 * @param hasWindowSamples whether ANY sample of the series falls inside this
 *        meal's analysis window (baseline start → post-window end). When
 *        [hasSamples] is true but this is false, the result is
 *        [MealAnalytics.NoWindowData] — the analysis cannot run and never
 *        will, which the UI must distinguish from [MealAnalytics.Settled]
 *        ("it ran and found nothing notable").
 * @param windowMin the post-meal window in minutes (the engine's default, 180).
 */
fun analyticsState(
    insight: com.dsikic.cluecose.core.food.MealInsight?,
    hasSamples: Boolean,
    hasWindowSamples: Boolean,
    mealDateEpochSeconds: Double,
    nowEpochSeconds: Double,
    windowMin: Double = InsightConfig.DEFAULT.postWindowMin,
): MealAnalytics {
    if (insight != null) return MealAnalytics.Ready
    if (!hasSamples) return MealAnalytics.NoData
    val readyAtEpoch = mealDateEpochSeconds + windowMin * 60
    val remaining = readyAtEpoch - nowEpochSeconds
    if (remaining > 0) {
        // The window is still open: readings are still arriving, so an
        // empty-so-far window is NOT yet "no data" — it is pending.
        return MealAnalytics.Pending(
            readyAtMs = (readyAtEpoch * 1000).toLong(),
            remainingMin = Math.ceil(remaining / 60).toInt(),
        )
    }
    // The window is closed. No sample ever fell inside it (a meal logged
    // before the sensor started / across a gap): the analysis cannot run —
    // terminal "no data in window", not a flat "no notable response".
    if (!hasWindowSamples) return MealAnalytics.NoWindowData
    return MealAnalytics.Settled
}

/**
 * The food-log model. [Application]-backed so it can construct the real
 * [FoodRepository] (SharedPreferences + photo files need the app context).
 * The `provided` seams (sample provider, LLM client) exist for tests, exactly
 * like `GlucoseViewModel`'s `provided` repository.
 */
class FoodViewModel
    @JvmOverloads constructor(
        application: Application,
        providedProvider: GlucoseSampleProvider? = null,
        private val providedClient: FoodClient? = null,
    ) : AndroidViewModel(application) {

    /**
     * The live glucose-series source (iOS `GlucoseModel.allSamples`). Set via
     * [attach] after construction (the default `viewModels()` factory can only
     * call `constructor(Application)`), or injected at construction for tests.
     */
    @Volatile
    private var sampleProvider: GlucoseSampleProvider? = providedProvider

    /**
     * Wire up to the app's glucose model (call once after both ViewModels
     * exist). The provider is read lazily on each recompute, so it always sees
     * the freshest samples. Mirrors iOS `FoodLogModel.attach(_ glucoseModel)`.
     */
    fun attach(provider: GlucoseSampleProvider) {
        sampleProvider = provider
    }

    val store: FoodRepository = FoodRepository(application)
    val client: FoodClient = providedClient ?: OkHttpFoodClient(application)

    private val _state = MutableStateFlow(FoodUiState(entries = store.all()))
    val state: StateFlow<FoodUiState> = _state.asStateFlow()

    /** How often the lightweight recompute timer may run (seconds). */
    private val recomputeIntervalMs = 120_000L
    private var lastRecomputeMs = 0L

    init {
        // The iOS model owns a 60 s timer that re-runs (throttled) the insight
        // recompute as `now` advances past each meal's 3 h mark. The Android
        // app already has a 30 s tick in GlucoseViewModel; this is the food
        // surface's own tick for the same job.
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                recomputeInsights(force = false)
            }
        }
    }

    // MARK: Derived

    /** Totals for the day the card is showing (iOS `todayTotals`). */
    fun totalsForSelectedDay(): FoodTotals =
        FoodTotals.sum(store.entries(_state.value.selectedDayMs.toDouble() / 1000.0))

    /** The selected day's entries, newest first (iOS `dayEntries`). */
    fun dayEntries(): List<FoodEntry> =
        store.entries(_state.value.selectedDayMs.toDouble() / 1000.0).sortedByDescending { it.dateEpochSeconds }

    /** The most recent insight that has fired (for the watch push; unused here). */
    val latestInsightText: String?
        get() = _state.value.entries
            .mapNotNull { it.insight }
            .sortedBy { it.peakDeltaMgdl }
            .lastOrNull()?.text

    fun selectDay(ms: Long) {
        _state.update { it.copy(selectedDayMs = ms) }
    }

    /**
     * The analytics state for [entry] right now (iOS `analytics(for:)`).
     * [hasSamples] reads the live series through the injected provider —
     * the same seam the insight recompute uses. [hasWindowSamples] narrows
     * it to the meal's own analysis window (30 min before → 3 h after), so
     * a meal logged before the sensor started (or across an app gap)
     * reports "no data in this window" instead of the misleading
     * "no notable response".
     */
    fun analytics(entry: FoodEntry, nowEpochSeconds: Double = System.currentTimeMillis() / 1000.0): MealAnalytics {
        val samples = sampleProvider?.currentSamples().orEmpty()
        val hasSamples = samples.isNotEmpty()
        val config = InsightConfig.DEFAULT
        val winLo = entry.dateEpochSeconds - config.baselineWindowMin * 60
        val winHi = entry.dateEpochSeconds + config.postWindowMin * 60
        val hasWindowSamples = samples.any {
            it.dateMs / 1000.0 in winLo..winHi
        }
        return analyticsState(
            insight = entry.insight,
            hasSamples = hasSamples,
            hasWindowSamples = hasWindowSamples,
            mealDateEpochSeconds = entry.dateEpochSeconds,
            nowEpochSeconds = nowEpochSeconds,
        )
    }

    // MARK: Sheet-facing save / estimate

    /**
     * The structured values the sheet collects from its editable rows.
     * (iOS `FoodLogModel.MealValues`.)
     */
    data class MealValues(
        var description: String = "",
        var serving: String? = null,
        var context: String? = null,
        var source: FoodSource = FoodSource.MANUAL,
        var caloriesKcal: Double? = null,
        var proteinG: Double? = null,
        var carbsG: Double? = null,
        var fibreG: Double? = null,
        var fatG: Double? = null,
        var saturatedFatG: Double? = null,
        var sodiumMg: Double? = null,
        var caffeineMg: Double? = null,
        var alcoholG: Double? = null,
        var citations: List<SourceCitation> = emptyList(),
        /// Portion slider (0.1…4.0, default 1.0) — how much of the described
        /// portion the user actually ate. (iOS `MealValues.portionMultiplier`.)
        var portionMultiplier: Double = 1.0,
        /// When the meal was (date/time picker, default now — logging
        /// after-the-fact). null on edit = keep the stored date.
        /// (iOS `MealValues.date`.)
        var dateEpochSeconds: Double? = null,
    )

    /**
     * Run the LLM estimate the sheet's "Estimate"/"Reanalyze" buttons use
     * (exposes `isEstimating` for the UI spinner).
     */
    suspend fun runEstimate(
        request: NutritionPrompt.Request,
        photos: List<ByteArray> = emptyList(),
    ): NutritionPrompt.Estimate {
        _state.update { it.copy(isEstimating = true) }
        try {
            return client.estimate(request, photos)
        } finally {
            _state.update { it.copy(isEstimating = false) }
        }
    }

    /**
     * Save a meal, for a NEW entry or an EDIT of [existing]. This is the ONLY
     * save path — saving always keeps the entry analyzed
     * (iOS `FoodLogModel.saveEntry`):
     *
     *  • The meal's description / serving / context / source changed → run a
     *    fresh estimate (model endpoint) of the FULL described portion,
     *    scaled by [MealValues.portionMultiplier] (0.1…4.0). When the
     *    endpoint fails the estimate is EMPTY — it never invents values, so
     *    the meal saves with blank rows instead of fabricated macros.
     *  • Same meal, anything else changed (rows, portion) → NO LLM call. If
     *    the portion moved, every value the entry has is rescaled (÷ old ×
     *    new) — the slider is about the whole meal, whether the values were
     *    estimated or typed by hand. If it didn't move, the values
     *    round-trip verbatim, so an unchanged save is an idempotent no-op.
     *  • The user's hand-edited nutrient rows (non-nil in [values]) always
     *    override whatever the analysis produced, scaled by the portion.
     *  • A meal with no data at all (no nutrient values AND no photos AND no
     *    failed analysis) cannot be saved — it is returned unchanged and NOT
     *    persisted.
     *
     * Photos in [photos] replace the entry's stored photos — but only when
     * the photo set actually changed, so a save that doesn't touch photos
     * rewrites nothing on disk. The meal date comes from
     * [MealValues.dateEpochSeconds] (the picker; default now, so
     * after-the-fact logging works); null falls back to the stored date
     * (edit) or now (new).
     */
    @JvmOverloads
    suspend fun saveEntry(
        existing: FoodEntry?,
        values: MealValues,
        photos: List<ByteArray> = emptyList(),
    ): FoodEntry {
        val id = existing?.id ?: newEntryId()
        // The store is the source of truth for the CURRENT state (an
        // `existing` copy may already carry a newer portion value, e.g.
        // straight off the slider). All "what changed" comparisons use the
        // stored entry.
        val current: FoodEntry? = if (id == existing?.id) store.all().firstOrNull { it.id == id } else null
        // The picker supplies the meal time (default now); on edit it
        // pre-fills with the stored date, so an untouched save keeps it.
        val dateSec = values.dateEpochSeconds ?: current?.dateEpochSeconds ?: System.currentTimeMillis() / 1000.0

        var entry = FoodEntry(
            id = id,
            dateEpochSeconds = dateSec,
            description = values.description.ifBlank { existing?.description ?: "Meal" },
            serving = values.serving,
            context = values.context,
            source = values.source,
            photoFileNames = current?.photoFileNames ?: (existing?.photoFileNames ?: emptyList()),
            portionMultiplier = values.portionMultiplier,
        )
        entry = entry.copy(
            citations = values.citations.takeIf { it.isNotEmpty() } ?: (current?.citations ?: existing?.citations),
        )

        // ---- Gate (up front): a NEW meal needs some data of its own -------
        // (a non-empty description, a photo, or a hand-entered row). Without
        // any of that there is nothing to analyze — and the analysis must
        // not be allowed to manufacture a "Meal" out of nothing. (Existing
        // entries already carry data by definition: they're in the store.)
        if (current == null) {
            val hasDescription = values.description.isNotBlank()
            val hasPhotos = values.source == FoodSource.PHOTO && photos.isNotEmpty()
            val hasRows = listOf(
                values.caloriesKcal, values.proteinG, values.carbsG, values.fibreG,
                values.fatG, values.saturatedFatG, values.sodiumMg, values.caffeineMg,
                values.alcoholG,
            ).any { it != null }
            if (!hasDescription && !hasPhotos && !hasRows) return entry
        }

        // ---- Analyze (the meal's nutrition comes from the analysis) -------
        // 1. The meal was actually re-described (description / serving /
        //    context / source changed vs the stored entry) → run a fresh
        //    estimate of the FULL described portion.
        // 2. Same meal → NO LLM call. Portion moved → rescale everything
        //    (÷ old × new); not moved → values pass through as-is.
        // 3. A new entry whose estimate produced nothing → the gate below
        //    decides whether it can exist at all.
        val descriptionChanged = current == null ||
            current.description != entry.description ||
            current.serving != values.serving ||
            current.context != values.context ||
            current.source != values.source
        // True once the estimate branch ran and the endpoint returned a
        // usable analysis (a failed analysis is still savable, with blank
        // values — a meal with NO data is not).
        var analysisSucceeded = false

        if (descriptionChanged) {
            _state.update { it.copy(isEstimating = true) }
            var estimate: NutritionPrompt.Estimate
            val request = NutritionPrompt.Request(
                description = entry.description,
                serving = values.serving,
                context = values.context,
                mode = if (values.source == FoodSource.PHOTO) NutritionPrompt.Mode.PHOTO else NutritionPrompt.Mode.MANUAL,
                photoCount = if (values.source == FoodSource.PHOTO) photos.size else (current?.photoFileNames?.size ?: 0),
            )
            val photosToSend = if (values.source == FoodSource.PHOTO) photos else emptyList()
            try {
                estimate = client.estimate(request, photosToSend)
                analysisSucceeded = !estimate.isEmpty
            } catch (e: Exception) {
                // The endpoint is unusable (no key, offline, unparseable):
                // save the meal WITHOUT analyzed values — never fabricate.
                // (isEstimating drives the Save-button spinner.)
                estimate = NutritionPrompt.Estimate()
                analysisSucceeded = false
                android.util.Log.w("FoodLogModel", "FOOD: estimate failed (${e.message}) — saving the meal without analyzed values")
            } finally {
                _state.update { it.copy(isEstimating = false) }
            }
            apply(scaled(estimate, multiplier = values.portionMultiplier), entry)
            entry = entry.copy(isEstimated = analysisSucceeded)
            // A fresh photo estimate may rename the meal (its own
            // description).
            if (values.source == FoodSource.PHOTO) {
                estimate.description?.takeIf { it.isNotEmpty() && values.description.isBlank() }
                    ?.let { entry = entry.copy(description = it) }
            }
        } else if (current != null) {
            // Same meal: no LLM call, no re-estimation. Materialize the
            // stored values onto the entry so it is always a complete,
            // savable record. The portion slider is about the WHOLE meal —
            // "how much of it did you eat" — so when it moved, every value
            // is rescaled (÷ old × new), whether estimated or typed by hand;
            // when it didn't move, they pass through as-is.
            val oldPortion = if (current.portionMultiplier == 0.0) 1.0 else current.portionMultiplier
            val newPortion = if (values.portionMultiplier == 0.0) 1.0 else values.portionMultiplier
            val s: (Double?) -> Double? = { v ->
                if (oldPortion != newPortion) v?.let { Math.round(it / oldPortion * newPortion).toDouble() } else v
            }
            entry = entry.copy(
                caloriesKcal = s(current.caloriesKcal),
                proteinG = s(current.proteinG),
                carbsG = s(current.carbsG),
                fibreG = s(current.fibreG),
                fatG = s(current.fatG),
                saturatedFatG = s(current.saturatedFatG),
                sodiumMg = s(current.sodiumMg),
                caffeineMg = s(current.caffeineMg),
                alcoholG = s(current.alcoholG),
                isEstimated = current.isEstimated,
            )
        }

        // ---- Honor the user's hand-edited rows ---------------------------
        // The sheet's rows are values of the FULL described portion (its
        // prefill divides the stored value by the portion). Like the
        // estimate, a hand-entered row is scaled by the portion to become
        // what the user actually ate.
        val p = if (values.portionMultiplier == 0.0) 1.0 else values.portionMultiplier
        entry = entry.copy(
            caloriesKcal = values.caloriesKcal?.let { Math.round(it * p).toDouble() } ?: entry.caloriesKcal,
            proteinG = values.proteinG?.let { Math.round(it * p).toDouble() } ?: entry.proteinG,
            carbsG = values.carbsG?.let { Math.round(it * p).toDouble() } ?: entry.carbsG,
            fibreG = values.fibreG?.let { Math.round(it * p).toDouble() } ?: entry.fibreG,
            fatG = values.fatG?.let { Math.round(it * p).toDouble() } ?: entry.fatG,
            saturatedFatG = values.saturatedFatG?.let { Math.round(it * p).toDouble() } ?: entry.saturatedFatG,
            sodiumMg = values.sodiumMg?.let { Math.round(it * p).toDouble() } ?: entry.sodiumMg,
            caffeineMg = values.caffeineMg?.let { Math.round(it * p).toDouble() } ?: entry.caffeineMg,
            alcoholG = values.alcoholG?.let { Math.round(it * p).toDouble() } ?: entry.alcoholG,
        )

        // ---- Gate: no data at all -> not savable --------------------------
        // (The sheet blocks the button too; this is the model-level
        // guarantee. Nothing is persisted in that case.)
        val hasNutrition = listOf(
            entry.caloriesKcal, entry.proteinG, entry.carbsG, entry.fibreG,
            entry.fatG, entry.saturatedFatG, entry.sodiumMg, entry.caffeineMg,
            entry.alcoholG,
        ).any { it != null }
        val hasPhotos = values.source == FoodSource.PHOTO && photos.isNotEmpty()
        // A meal whose analysis FAILED (the endpoint was unusable) carries
        // no nutrient values by design — it is still savable, with the blank
        // rows clearly flagged, so the log keeps WHAT was eaten.
        val analysisFailed = descriptionChanged && !analysisSucceeded
        if (!hasNutrition && !hasPhotos && !analysisFailed) return entry

        // ---- Photos --------------------------------------------------------
        // The photo set is rewritten ONLY when it actually changed
        // (different count or any file's bytes differ). Saving a meal whose
        // photos are untouched must not re-encode its files on disk.
        val storedNames = current?.photoFileNames ?: emptyList()
        val storedData: List<ByteArray> =
            if (values.source == FoodSource.PHOTO) store.photoBytes(current ?: entry) else emptyList()
        val photosUnchanged = if (values.source != FoodSource.PHOTO) {
            storedNames.isEmpty() && photos.isEmpty()
        } else {
            photos.size == storedData.size &&
                photos.zip(storedData).all { (a, b) -> a.contentEquals(b) }
        }
        if (photosUnchanged) {
            entry = entry.copy(photoFileNames = storedNames)
        } else {
            store.deletePhotos(id)
            val names = mutableListOf<String>()
            if (values.source == FoodSource.PHOTO) {
                for ((i, raw) in photos.withIndex()) {
                    val jpeg = PhotoCompressor.toJpegBytes(raw) ?: continue
                    names.add(store.savePhoto(jpeg, id, i))
                }
            }
            entry = entry.copy(photoFileNames = names)
        }

        // Editing the meal re-arms its insight: the curve is re-evaluated
        // against the (possibly new) carb value once the window allows.
        if (current != null && current.insight != null) {
            entry = entry.copy(insight = null)
        }

        if (current != null) store.update(entry) else store.add(entry)
        refresh()
        recomputeInsights(force = true)
        return entry
    }

    /** Scale every non-nil estimate value by [multiplier] (portion adjust). (iOS `scaled(_:by:)`.) */
    private fun scaled(e: NutritionPrompt.Estimate, multiplier: Double): NutritionPrompt.Estimate {
        val s: (Double?) -> Double? = { v -> v?.let { Math.round(it * multiplier).toDouble() } }
        return e.copy(
            caloriesKcal = s(e.caloriesKcal),
            proteinG = s(e.proteinG),
            carbsG = s(e.carbsG),
            fibreG = s(e.fibreG),
            fatG = s(e.fatG),
            saturatedFatG = s(e.saturatedFatG),
            sodiumMg = s(e.sodiumMg),
            caffeineMg = s(e.caffeineMg),
            alcoholG = s(e.alcoholG),
        )
    }

    /**
     * Update an existing entry (detail view: portion slider or edited rows).
     * Reuses [saveEntry] so analysis + insight re-arm are identical to the
     * sheet's save path. (iOS `updateEntry`.)
     */
    suspend fun updateEntry(updated: FoodEntry): FoodEntry {
        val photos = store.photoBytes(updated)
        val values = MealValues(
            description = updated.description,
            serving = updated.serving,
            context = updated.context,
            source = updated.source,
            // nil = "no hand edit here" → the (re)estimate / stored values win.
            caloriesKcal = null, proteinG = null, carbsG = null, fibreG = null,
            fatG = null, saturatedFatG = null, sodiumMg = null, caffeineMg = null,
            alcoholG = null,
            citations = updated.citations.orEmpty(),
            portionMultiplier = updated.portionMultiplier,
            dateEpochSeconds = updated.dateEpochSeconds,
        )
        return saveEntry(existing = updated, values = values, photos = photos)
    }

    /**
     * Manual "clear food log" (Settings → Data). Deletes every meal entry
     * and its photos. Returns the number of meals cleared.
     * (iOS `FoodLogModel.clearAll` / `GlucoseModel.clearFoodLogData`.)
     */
    fun clearAll(): Int {
        val entries = store.all()
        for (e in entries) store.deletePhotos(e.id)
        if (entries.isNotEmpty()) {
            store.persistAll(emptyList())
            refresh()
            recomputeInsights(force = true)
        }
        return entries.size
    }

    // MARK: One-shot entry actions (card "Add meal" quick path)

    /**
     * Log a meal typed by hand: description → LLM estimate → stored entry
     * (isEstimated=true). Throws if the estimate fails or is empty, so the
     * sheet can fall back to manual value entry. (iOS `addManual`.)
     */
    suspend fun addManual(
        description: String,
        serving: String? = null,
        context: String? = null,
    ) {
        _state.update { it.copy(isEstimating = true) }
        try {
            val request = NutritionPrompt.Request(
                description = description,
                serving = serving,
                context = context,
                mode = NutritionPrompt.Mode.MANUAL,
                photoCount = 0,
            )
            val estimate = client.estimate(request)
            var entry = FoodEntry(
                id = newEntryId(),
                dateEpochSeconds = System.currentTimeMillis() / 1000.0,
                description = description,
                serving = serving,
                context = context,
                source = FoodSource.MANUAL,
                isEstimated = true,
            )
            entry = apply(estimate, entry)
            store.add(entry)
            refresh()
            recomputeInsights(force = false)
        } finally {
            _state.update { it.copy(isEstimating = false) }
        }
    }

    /**
     * Log a meal from photos: vision LLM estimate → stored entry with the
     * photos persisted under `food/photos`. The model's own description is
     * used to prefill when the user left the description empty.
     * (iOS `addPhoto`.)
     */
    suspend fun addPhoto(
        description: String?,
        serving: String? = null,
        context: String? = null,
        photos: List<ByteArray> = emptyList(),
    ) {
        _state.update { it.copy(isEstimating = true) }
        try {
            val text = description?.trim().orEmpty()
            val request = NutritionPrompt.Request(
                description = text.ifEmpty { "meal photo" },
                serving = serving,
                context = context,
                mode = NutritionPrompt.Mode.PHOTO,
                photoCount = photos.size,
            )
            val estimate = client.estimate(request, photos)
            var entry = FoodEntry(
                id = newEntryId(),
                dateEpochSeconds = System.currentTimeMillis() / 1000.0,
                description = estimate.description ?: text,
                serving = serving,
                context = context,
                source = FoodSource.PHOTO,
                isEstimated = true,
            )
            entry = apply(estimate, entry)
            val names = mutableListOf<String>()
            for ((i, raw) in photos.withIndex()) {
                val jpeg = PhotoCompressor.toJpegBytes(raw) ?: continue
                names.add(store.savePhoto(jpeg, entry.id, i))
            }
            entry = entry.copy(photoFileNames = names)
            store.add(entry)
            refresh()
            recomputeInsights(force = false)
        } finally {
            _state.update { it.copy(isEstimating = false) }
        }
    }

    /**
     * Re-run the estimate for an existing entry using its current
     * description / context / photos, and update it (isEstimated=true).
     * (iOS `reanalyze`.)
     */
    suspend fun reanalyze(entry: FoodEntry) {
        _state.update { it.copy(isEstimating = true) }
        try {
            val photos = store.photoBytes(entry)
            val request = NutritionPrompt.Request(
                description = entry.description,
                serving = entry.serving,
                context = entry.context,
                mode = if (entry.source == FoodSource.PHOTO) NutritionPrompt.Mode.PHOTO else NutritionPrompt.Mode.MANUAL,
                photoCount = entry.photoFileNames.size,
            )
            val estimate = if (entry.source == FoodSource.PHOTO) {
                client.estimate(request, photos)
            } else {
                client.estimate(request)
            }
            var updated = apply(estimate, entry)
            // A fresh photo estimate may rename the meal (its own description).
            if (entry.source == FoodSource.PHOTO) {
                estimate.description?.takeIf { it.isNotEmpty() }?.let { updated = updated.copy(description = it) }
            }
            updated = updated.copy(isEstimated = true)
            store.update(updated)
            refresh()
            recomputeInsights(force = false)
        } finally {
            _state.update { it.copy(isEstimating = false) }
        }
    }

    /** Remove an entry and its photos. (iOS `delete`.) */
    fun delete(entry: FoodEntry) {
        store.delete(entry.id)
        refresh()
    }

    /**
     * Remove one photo from an entry (file + name), persisting the change.
     * (iOS `deletePhoto`.)
     */
    fun deletePhoto(entry: FoodEntry, index: Int) {
        val updated = store.removePhotoFrom(entry, index) ?: return
        store.update(updated)
        refresh()
    }

    // MARK: Insight recompute

    /**
     * Compute (once) a `MealInsight` for any entry whose full post-meal window
     * has elapsed and which has no insight yet. Cheap: it only reads the
     * series and the engine filters; throttled to at most once per
     * [recomputeIntervalMs] unless [force]. (iOS `recomputeInsights`.)
     */
    fun recomputeInsights(force: Boolean = false) {
        val nowMs = System.currentTimeMillis()
        if (!force && nowMs - lastRecomputeMs < recomputeIntervalMs) return
        lastRecomputeMs = nowMs

        val samples = sampleProvider?.currentSamples() ?: return
        val nowEpoch = nowMs / 1000.0
        val samplePoints = samples.map {
            com.dsikic.cluecose.core.food.GlucoseSample(it.dateMs / 1000.0, it.mgdl)
        }
        val config = InsightConfig.DEFAULT
        val entries = _state.value.entries
        var changed = false
        val updated = entries.toMutableList()
        for (i in entries.indices) {
            val e = entries[i]
            if (e.insight != null) continue
            // "Final only": the whole post window must have elapsed.
            val postHi = e.dateEpochSeconds + config.postWindowMin * 60
            if (nowEpoch < postHi) continue
            val crossMeal = crossMealComparisonFor(e, entries)
            val input = InsightInput(
                mealDateEpochSeconds = e.dateEpochSeconds,
                carbsG = e.carbsG,
                description = e.description,
                samples = samplePoints,
                nowEpochSeconds = nowEpoch,
            )
            val insight = MealInsightEngine.insight(input = input, config = config, crossMeal = crossMeal)
            if (insight != null) {
                updated[i] = e.copy(insight = insight)
                changed = true
            }
        }
        if (changed) {
            store.persistAll(updated)
            refreshFrom(updated)
        }
    }

    /**
     * Build a "your usual response" comparison from PRIOR similar entries
     * whose insight already fired (instance count + average peak delta).
     * (iOS `crossMealComparison(for:)`.)
     */
    private fun crossMealComparisonFor(entry: FoodEntry, all: List<FoodEntry>): CrossMealComparison? {
        val prior = all
            .filter { it.id != entry.id && it.dateEpochSeconds < entry.dateEpochSeconds }
            .filter { descriptionsSimilar(it.description, entry.description) }
            .mapNotNull { it.insight }
        if (prior.size < 2) return null
        val avg = prior.sumOf { it.peakDeltaMgdl } / prior.size
        return CrossMealComparison(instanceCount = prior.size + 1, averagePeakDeltaMgdl = avg)
    }

    /**
     * Similar = shared description, or a common content word of 4+ letters
     * (so "bagel with cream cheese" and "bagel" compare). (iOS
     * `descriptionsSimilar`.)
     */
    private fun descriptionsSimilar(a: String, b: String): Boolean {
        val words: (String) -> Set<String> = { s ->
            s.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 4 }
                .toSet()
        }
        val wa = words(a)
        val wb = words(b)
        if (wa.isNotEmpty() && wb.isNotEmpty()) return wa.intersect(wb).isNotEmpty()
        return a.lowercase() == b.lowercase()
    }

    // MARK: Helpers

    /**
     * Fold an LLM estimate into an entry (keeps any value the estimate left
     * null; does NOT clear the description — the caller decides on that).
     * (iOS `apply(estimate:to:)`.)
     */
    private fun apply(estimate: NutritionPrompt.Estimate, entry: FoodEntry): FoodEntry =
        entry.copy(
            caloriesKcal = estimate.caloriesKcal,
            proteinG = estimate.proteinG,
            carbsG = estimate.carbsG,
            fibreG = estimate.fibreG,
            fatG = estimate.fatG,
            sodiumMg = estimate.sodiumMg,
            caffeineMg = estimate.caffeineMg,
            alcoholG = estimate.alcoholG,
            saturatedFatG = estimate.saturatedFatG,
            citations = estimate.citations.takeIf { it.isNotEmpty() } ?: entry.citations,
        )

    /** Reload entries from the store into the published state (+ patterns). */
    fun refresh() {
        refreshFrom(store.all())
    }

    private fun refreshFrom(entries: List<FoodEntry>) {
        val nowEpoch = System.currentTimeMillis() / 1000.0
        val p = PatternsEngine.patterns(entries, inDays = 7, nowEpochSeconds = nowEpoch)
        _state.update {
            it.copy(
                entries = entries,
                patterns = p,
                coaching = PatternsEngine.coachingQuestions(p),
            )
        }
    }

    private fun newEntryId(): String = java.util.UUID.randomUUID().toString()
}
