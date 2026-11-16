//
//  FoodDetailSheet.kt
//  Cluecose
//
//  The per-meal detail + analytics sheet, opened from the chevron on a food
//  card entry (or its insight callout / pending pill). Mirrors iOS
//  `FoodDetailView`:
//
//  • the meal's blood-sugar analysis — either the computed `MealInsight`
//    (with its numbers) or, while the post-meal window is still forming, a
//    "collecting the curve" state that says exactly what will happen and
//    WHEN the insight lands (the 3 h window, with a progress bar). The 60 s
//    insight recompute timer keeps the model updating, so the state flips to
//    the result automatically (the stored entry is re-read every recompose).
//  • the full nutrition breakdown with the portion slider (0.1×–4.0× in 0.1
//    steps) — moving it re-scales the entry via `updateEntry`, which re-runs
//    the analysis path and re-arms the insight (persisted debounced, so a
//    drag writes once, not 39 times).
//  • the description / serving / context rows (editing these re-runs the
//    estimate on Save, via the same `saveEntry` path — the rows are
//    read-only here, so they are passed as nil to the model).
//
//  The stored entry in the ViewModel is the source of truth: every change
//  writes through it, and the UI re-reads it each recompose, so the 60 s
//  timer landing a new insight flips this sheet to the result live.
//

package com.dsikic.cluecose.food

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dsikic.cluecose.core.food.FoodEntry
import com.dsikic.cluecose.core.food.MealInsight
import com.dsikic.cluecose.ui.CluecoseColors
import com.dsikic.cluecose.ui.GlucoseUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The per-meal detail sheet. Presented as a full-screen dialog (the Android
 * equivalent of iOS's pushed `FoodDetailView`). [unit] mirrors the app's
 * glucose unit so the insight metrics render the same way they do on the
 * main chart.
 */
@Composable
fun FoodDetailSheet(
    viewModel: FoodViewModel,
    initialEntryId: String,
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Read the STORED entry (the source of truth) — the 60 s recompute timer
    // sets `insight` on it while this sheet is open, and every recompose
    // re-reads it, so the state flips to the result automatically.
    val entries = viewModel.state.value.entries
    val stored = entries.firstOrNull { it.id == initialEntryId }
    if (stored == null) {
        // The entry was deleted from the log meanwhile → dismiss (iOS
        // `onChange` → `dismiss()`).
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    // Local edit state for the text fields (committed on Save).
    var descriptionText by remember { mutableStateOf(stored.description) }
    var servingText by remember { mutableStateOf(stored.serving.orEmpty()) }
    var contextText by remember { mutableStateOf(stored.context.orEmpty()) }
    // Local portion value; the slider writes through (debounced) so the
    // user sees the scaled numbers move as they drag.
    var portion by remember { mutableStateOf(if (stored.portionMultiplier == 0.0) 1.0 else stored.portionMultiplier) }
    var isSaving by remember { mutableStateOf(false) }

    // Live rescale of the on-screen numbers while the slider is dragged:
    // from the STORE's values (not a locally-rescaled entry — rescaling it
    // again per tick would compound). The store is untouched until the drag
    // commits. (iOS `applyLivePortion`.)
    var displayPortion by remember { mutableStateOf(if (stored.portionMultiplier == 0.0) 1.0 else stored.portionMultiplier) }
    var displayValues by remember {
        mutableStateOf(
            DisplayValues(
                stored.caloriesKcal, stored.proteinG, stored.carbsG, stored.fatG,
                stored.saturatedFatG, stored.sodiumMg, stored.caffeineMg, stored.alcoholG,
            ),
        )
    }
    fun applyLivePortion(newPortion: Double) {
        val old = if (stored.portionMultiplier == 0.0) 1.0 else stored.portionMultiplier
        if (old == newPortion) return
        val f: (Double?) -> Double? = { v -> v?.let { Math.round(it / old * newPortion).toDouble() } }
        val entry = stored
        displayPortion = newPortion
        displayValues = DisplayValues(
            caloriesKcal = f(entry.caloriesKcal),
            proteinG = f(entry.proteinG),
            carbsG = f(entry.carbsG),
            fatG = f(entry.fatG),
            saturatedFatG = f(entry.saturatedFatG),
            sodiumMg = f(entry.sodiumMg),
            caffeineMg = f(entry.caffeineMg),
            alcoholG = f(entry.alcoholG),
        )
    }
    // Keep the display in sync when the stored entry changes underneath (a
    // commit, or the timer recompute landing an insight).
    LaunchedEffect(stored.portionMultiplier) {
        displayPortion = if (stored.portionMultiplier == 0.0) 1.0 else stored.portionMultiplier
        displayValues = DisplayValues(
            stored.caloriesKcal, stored.proteinG, stored.carbsG, stored.fatG,
            stored.saturatedFatG, stored.sodiumMg, stored.caffeineMg, stored.alcoholG,
        )
    }

    // Debounced persist for the portion slider (iOS `schedulePortionPersist`):
    // the on-screen numbers are rescaled live from the STORE's values; the
    // write lands once the drag settles for 0.5 s, so a drag writes once.
    val lastPersistedPortion = remember { AtomicInteger((stored.portionMultiplier * 10).toInt()) }
    var portionPersistJob: Job? by remember { mutableStateOf(null) }
    fun schedulePortionPersist() {
        portionPersistJob?.cancel()
        portionPersistJob = scope.launch {
            delay(500)
            val target = portion
            if (lastPersistedPortion.get() == (target * 10).toInt()) return@launch
            lastPersistedPortion.set((target * 10).toInt())
            // Re-read the store (it may have moved under us — the timer
            // recompute, a concurrent save) and push the current position.
            val current = viewModel.state.value.entries.firstOrNull { it.id == initialEntryId } ?: return@launch
            viewModel.updateEntry(current.copy(portionMultiplier = target))
        }
    }
    fun cancelPendingPortionPersist() {
        portionPersistJob?.cancel()
        portionPersistJob = null
    }

    // Save the About-section edits (portion already writes through on drag).
    // The nutrient rows in this view are READ-ONLY, so they are passed as nil
    // — that way a change to the description/serving/context re-runs the
    // analysis and the fresh estimate (scaled by the portion) fills the
    // values. When nothing changed, the stored values round-trip unchanged.
    // (iOS `commitEdits`.)
    suspend fun commitEdits() {
        isSaving = true
        cancelPendingPortionPersist()
        try {
            val current = viewModel.state.value.entries.firstOrNull { it.id == initialEntryId } ?: return
            val values = FoodViewModel.MealValues(
                description = descriptionText,
                serving = servingText.takeIf { it.isNotBlank() },
                context = contextText.takeIf { it.isNotBlank() },
                source = current.source,
                caloriesKcal = null, proteinG = null, carbsG = null, fatG = null,
                saturatedFatG = null, sodiumMg = null, caffeineMg = null, alcoholG = null,
                citations = current.citations.orEmpty(),
                portionMultiplier = portion,
                dateEpochSeconds = current.dateEpochSeconds,
            )
            val photos = viewModel.store.photoBytes(current)
            viewModel.saveEntry(existing = current, values = values, photos = photos)
        } finally {
            isSaving = false
        }
    }

    val a = viewModel.analytics(entry = stored)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20B2530)),
        ) {
            // Title bar.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Done",
                    tint = CluecoseColors.white(0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onDismiss)
                        .padding(end = 8.dp),
                )
                Text(
                    text = SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date(stored.dateEpochSeconds.toLong() * 1000L)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (isSaving) "Saving…" else "Save",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSaving) CluecoseColors.white(0.5f) else CluecoseColors.Action,
                    modifier = Modifier
                        .background(CluecoseColors.Action.copy(alpha = if (isSaving) 0.06f else 0.14f), RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSaving) {
                            scope.launch { commitEdits() }
                        }
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Meal header (photo thumb + name + time).
                MealHeader(stored, viewModel)

                // Analytics (blood sugar).
                GlassPanelLite {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Blood sugar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        if (stored.insight != null) {
                            InsightResult(stored.insight!!, unit)
                        } else when (a) {
                            is MealAnalytics.Pending -> CollectingState(a, stored)
                            is MealAnalytics.NoData -> NoDataState()
                            is MealAnalytics.NoWindowData -> NoWindowDataState(stored)
                            else -> SettledState()
                        }
                    }
                }

                // Portion slider (live rescale + debounced persist).
                GlassPanelLite {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("How much did you eat?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                            Text(
                                text = String.format(Locale.US, "%.1f×", displayPortion),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                        Slider(
                            value = displayPortion.toFloat(),
                            onValueChange = { v ->
                                displayPortion = v.toDouble()
                                applyLivePortion(v.toDouble())
                                schedulePortionPersist()
                            },
                            valueRange = 0.1f..4.0f,
                            steps = 38,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "The estimate describes the full portion; this scales everything to what you actually ate. Changing it re-runs the meal's blood-sugar analysis against the new carb amount.",
                            fontSize = 11.sp,
                            color = CluecoseColors.white(0.5f),
                        )
                    }
                }

                // Nutrition (read-only metrics; the detail view's rows are
                // read-only — editing is the sheet's job).
                GlassPanelLite {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nutrition", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        MetricRow(
                            listOf(
                                "Calories" to (displayValues.caloriesKcal?.let { String.format(Locale.US, "%.0f kcal", it) } ?: "–"),
                                "Protein" to (displayValues.proteinG?.let { String.format(Locale.US, "%.0f g", it) } ?: "–"),
                                "Carbs" to (displayValues.carbsG?.let { String.format(Locale.US, "%.0f g", it) } ?: "–"),
                            ),
                        )
                        MetricRow(
                            listOf(
                                "Fat" to (displayValues.fatG?.let { String.format(Locale.US, "%.0f g", it) } ?: "–"),
                                "Sat fat" to (displayValues.saturatedFatG?.let { String.format(Locale.US, "%.0f g", it) } ?: "–"),
                                "Sodium" to (displayValues.sodiumMg?.let { String.format(Locale.US, "%.0f mg", it) } ?: "–"),
                            ),
                        )
                        MetricRow(
                            listOf(
                                "Caffeine" to (displayValues.caffeineMg?.let { String.format(Locale.US, "%.0f mg", it) } ?: "–"),
                                "Alcohol" to (displayValues.alcoholG?.let { String.format(Locale.US, "%.0f g", it) } ?: "–"),
                            ),
                        )
                        if (stored.isEstimated) {
                            Text(
                                text = "Values are an estimate — edit them in the meal sheet to make them exact.",
                                fontSize = 10.sp,
                                color = CluecoseColors.white(0.45f),
                            )
                        }
                        if (listOf(
                                stored.caloriesKcal, stored.proteinG, stored.carbsG, stored.fatG,
                                stored.saturatedFatG, stored.sodiumMg, stored.caffeineMg, stored.alcoholG,
                            ).all { it == null }) {
                            Text(
                                text = "Nutrition wasn't analyzed (the model endpoint is unavailable right now). The meal is logged as-is — open it and enter values, or save it again later to retry.",
                                fontSize = 10.sp,
                                color = Color(0xCCFF9E59),
                            )
                        }
                    }
                }

                // About (editable — re-runs the analysis on Save).
                GlassPanelLite {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("About this meal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        OutlinedTextField(
                            value = descriptionText,
                            onValueChange = { descriptionText = it },
                            singleLine = true,
                            label = { Text("What did you eat?", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = servingText,
                            onValueChange = { servingText = it },
                            singleLine = true,
                            label = { Text("Serving / portion (optional)", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = contextText,
                            onValueChange = { contextText = it },
                            singleLine = true,
                            label = { Text("Context (optional)", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Changing these re-analyzes the meal with the model when you save (your entered values are kept).",
                            fontSize = 10.sp,
                            color = CluecoseColors.white(0.45f),
                        )
                    }
                }
            }
        }
    }
}

/** Read-only nutrient display values (the detail view rescales these live). */
private data class DisplayValues(
    val caloriesKcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val saturatedFatG: Double?,
    val sodiumMg: Double?,
    val caffeineMg: Double?,
    val alcoholG: Double?,
)

// MARK: - Sections

@Composable
private fun MealHeader(entry: FoodEntry, viewModel: FoodViewModel) {
    val name = entry.photoFileNames.firstOrNull()
    val path = name?.let { n ->
        val f = java.io.File(viewModel.store.photosDirectory, n)
        if (f.exists()) f.absolutePath else null
    }
    val painter: Painter? = path?.let { remember(it) {
        try {
            android.graphics.BitmapFactory.decodeFile(it)?.let { bmp -> BitmapPainter(bmp.asImageBitmap()) }
        } catch (e: Exception) {
            null
        }
    } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(CluecoseColors.white(0.08f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = CluecoseColors.white(0.4f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.description, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                text = SimpleDateFormat("h:mm a", Locale.US).format(Date(entry.dateEpochSeconds.toLong() * 1000L)),
                fontSize = 11.sp,
                color = CluecoseColors.white(0.5f),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** The computed insight: the text plus the underlying numbers. (iOS `insightResult`.) */
@Composable
private fun InsightResult(insight: MealInsight, unit: GlucoseUnit) {
    val color = if (insight.kind == MealInsight.Kind.SPIKE) CluecoseColors.GlowHigh else CluecoseColors.GlowLow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Text(
            text = insight.text,
            fontSize = 12.sp,
            color = CluecoseColors.white(0.85f),
        )
    }
    val delta = (if (insight.peakDeltaMgdl >= 0) "+" else "") + unit.format(insight.peakDeltaMgdl)
    MetricRow(
        listOf(
            "Baseline" to (unit.format(insight.baselineMgdl) + " " + unit.label),
            "Peak" to (unit.format(insight.peakMgdl) + " " + unit.label),
            "Δ from baseline" to delta,
        ),
    )
    val secondRow = mutableListOf<Pair<String, String>>()
    secondRow.add("Time to peak" to String.format(Locale.US, "%.0f min", insight.timeToPeakMin))
    insight.returnToBaselineMin?.let { secondRow.add("Back to baseline" to String.format(Locale.US, "%.0f min", it)) }
    insight.per10gCarbDeltaMgdl?.let { secondRow.add("Per 10 g carbs" to String.format(Locale.US, "%+.0f mg/dL", it)) }
    MetricRow(secondRow)
}

/** The window is still forming: say WHAT will happen and WHEN it lands. (iOS `collectingState`.) */
@Composable
private fun CollectingState(a: MealAnalytics.Pending, entry: FoodEntry) {
    val windowMin = InsightConfigWindowMin
    val fraction = (minOf(1.0, maxOf(0.0, (entry.dateEpochSeconds - System.currentTimeMillis() / 1000.0) / -(windowMin * 60))))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Filled.HourglassEmpty,
                contentDescription = null,
                tint = CluecoseColors.GlowLow,
                modifier = Modifier.size(14.dp),
            )
            Text("Collecting your blood-sugar curve for this meal…", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
        val readyLabel = SimpleDateFormat("h:mm a", Locale.US).format(Date(a.readyAtMs))
        Text(
            text = "We measure your pre-meal baseline, then watch your glucose for ${windowMin.toInt()} minutes after eating. When the window closes at $readyLabel (in about ${a.remainingMin} min), we'll compute how this meal moved your blood sugar — a spike, a reactive dip, or no meaningful change — and show the result here automatically.",
            fontSize = 11.sp,
            color = CluecoseColors.white(0.65f),
        )
        LinearProgressIndicator(
            progress = { fraction.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(100.dp)),
        )
        Text(
            text = "${(fraction * 100).toInt()}% of the analysis window elapsed",
            fontSize = 10.sp,
            color = CluecoseColors.white(0.45f),
        )
    }
}

/** No glucose series at all (no sensor data). (iOS `noDataState`.) */
@Composable
private fun NoDataState() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Filled.Sensors,
                contentDescription = null,
                tint = CluecoseColors.white(0.5f),
                modifier = Modifier.size(14.dp),
            )
            Text("Waiting for glucose data", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
        Text(
            text = "No glucose readings are available yet, so this meal's blood-sugar response can't be analyzed. Pair your sensor and the analysis will run automatically once the post-meal window has elapsed.",
            fontSize = 11.sp,
            color = CluecoseColors.white(0.65f),
        )
    }
}

/** There IS a glucose series, but none of it falls inside this meal's
 * analysis window (30 min before → 3 h after) — a meal logged before the
 * sensor started, across an app gap, or into an era with no readings. The
 * analysis cannot run and never will; this is distinct from [SettledState]
 * (the window had data and was flat). (iOS `noWindowDataState`.) */
@Composable
private fun NoWindowDataState(entry: FoodEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Filled.HourglassEmpty,
                contentDescription = null,
                tint = CluecoseColors.white(0.5f),
                modifier = Modifier.size(14.dp),
            )
            Text("No glucose data in this meal's window", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
        val mealLabel = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US)
            .format(Date(entry.dateEpochSeconds.toLong() * 1000L))
        Text(
            text = "This meal was logged for $mealLabel, and no glucose readings exist between 30 minutes before it and 3 hours after it (the analysis window). The sensor wasn't streaming then — it started later, or the data isn't available on this device — so this meal's blood-sugar response can't be analyzed. Meals logged from now on get analyzed automatically once their 3-hour window closes.",
            fontSize = 11.sp,
            color = CluecoseColors.white(0.65f),
        )
    }
}

/** The 3 h window has fully elapsed and the analysis ran on real data in
 * the window but found nothing worth flagging: the result is final — no
 * insight will ever appear for this meal. A terminal state, not "waiting".
 * (iOS `settledState`.) */
@Composable
private fun SettledState() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF73D99B),
                modifier = Modifier.size(14.dp),
            )
            Text("Analysis complete — no notable response", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
        Text(
            text = "We analyzed your blood sugar for ${InsightConfigWindowMin.toInt()} minutes after this meal: no spike or dip worth flagging — it stayed within ±30 mg/dL of your pre-meal level. Nothing to act on here; the analysis for this meal is complete.",
            fontSize = 11.sp,
            color = CluecoseColors.white(0.65f),
        )
    }
}

/** A row of small labeled metric tiles. (iOS `metric`.) */
@Composable
private fun MetricRow(items: List<Pair<String, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (label, value) ->
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .background(CluecoseColors.white(0.06f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(label, fontSize = 10.sp, color = CluecoseColors.white(0.45f))
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

/** A simple dark glass panel (the sheets' shared surface). */
@Composable
private fun GlassPanelLite(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF2101C2A), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

/** The engine's post-meal analysis window, in minutes (180). */
private val InsightConfigWindowMin: Double =
    com.dsikic.cluecose.core.food.InsightConfig.DEFAULT.postWindowMin
