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
        var fatG: Double? = null,
        var saturatedFatG: Double? = null,
        var sodiumMg: Double? = null,
        var caffeineMg: Double? = null,
        var alcoholG: Double? = null,
        var citations: List<SourceCitation> = emptyList(),
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
     * Save a meal from the sheet, for a NEW entry or an EDIT of [existing].
     * Photos in [photos] replace the entry's stored photos (old files are
     * removed); for manual entries pass an empty list. The entry's date is
     * preserved on edit and is "now" for new entries.
     */
    fun saveEntry(
        existing: FoodEntry?,
        values: MealValues,
        isEstimated: Boolean,
        photos: List<ByteArray> = emptyList(),
    ): FoodEntry {
        // The picker supplies the meal time (default now; after-the-fact
        // logging picks an earlier time). On edit it pre-fills with the
        // stored date, so an untouched save keeps it.
        val dateSec = values.dateEpochSeconds
            ?: existing?.dateEpochSeconds
            ?: System.currentTimeMillis() / 1000.0
        val id = existing?.id ?: newEntryId()

        var entry = FoodEntry(
            id = id,
            dateEpochSeconds = dateSec,
            description = values.description.ifBlank { existing?.description ?: "Meal" },
            serving = values.serving,
            context = values.context,
            source = values.source,
            isEstimated = isEstimated,
        )
        entry = entry.copy(
            caloriesKcal = values.caloriesKcal,
            proteinG = values.proteinG,
            carbsG = values.carbsG,
            fatG = values.fatG,
            saturatedFatG = values.saturatedFatG,
            sodiumMg = values.sodiumMg,
            caffeineMg = values.caffeineMg,
            alcoholG = values.alcoholG,
            citations = values.citations.takeIf { it.isNotEmpty() } ?: existing?.citations,
        )
        // Editing a stored value clears any earlier insight — the curve is
        // re-evaluated against the (possibly new) carb value.
        if (existing != null) entry = entry.copy(insight = null)

        // Replace stored photos: drop the old files, write the new ones under
        // the stable `{id}_{index}.jpg` names.
        store.deletePhotos(id)
        val names = mutableListOf<String>()
        if (values.source == FoodSource.PHOTO) {
            for ((i, raw) in photos.withIndex()) {
                val jpeg = PhotoCompressor.toJpegBytes(raw) ?: continue
                names.add(store.savePhoto(jpeg, id, i))
            }
        }
        entry = entry.copy(photoFileNames = names)

        if (existing != null) store.update(entry) else store.add(entry)
        refresh()
        recomputeInsights(force = true)
        return entry
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
