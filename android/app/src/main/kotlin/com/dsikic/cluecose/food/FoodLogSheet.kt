//
//  FoodLogSheet.kt
//  Cluecose
//
//  The add/edit meal sheet (presented like the SettingsSheet). A segmented
//  control switches between Type (typed description) and Photo (capture, or
//  pick up to 5 from the gallery).
//
//  "Estimate" / "Reanalyze" sends the description (and photos, in photo mode)
//  to the configured model endpoint and fills the eight editable nutrient rows
//  plus the model's own description and any citations (each a tappable link).
//  If the endpoint fails, an "enter values by hand" fallback banner shows and
//  every row stays editable — manual entry always works.
//
//  Edit mode prefills from the existing entry; deleting an entry removes it;
//  saving an edit flips isEstimated=false. Mirrors iOS `FoodLogSheet`.
//

package com.dsikic.cluecose.food

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dsikic.cluecose.core.food.FoodEntry
import com.dsikic.cluecose.core.food.FoodSource
import com.dsikic.cluecose.core.food.NutritionPrompt
import com.dsikic.cluecose.core.food.SourceCitation
import com.dsikic.cluecose.ui.CluecoseColors
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * The add/edit meal sheet. [existing] = null for a new meal; an entry for
 * edit mode (prefills all fields). Presented as a Dialog (like SettingsSheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLogSheet(
    viewModel: FoodViewModel,
    existing: FoodEntry?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // `state` is a StateFlow<FoodUiState>; read just the `isEstimating` boolean
    // (the composable recomposes when the flow emits, so this stays live).
    val isEstimating: Boolean = viewModel.state.collectAsState().value.isEstimating

    var modeIsPhoto by remember { mutableStateOf(existing?.source == FoodSource.PHOTO) }
    var descriptionText by remember { mutableStateOf(existing?.description.orEmpty()) }
    var servingText by remember { mutableStateOf(existing?.serving.orEmpty()) }
    var contextText by remember { mutableStateOf(existing?.context.orEmpty()) }
    // When the meal was (epoch ms) — defaults to now; pick an earlier time to
    // log after-the-fact. Prefilled from the entry on edit. (iOS mealDate.)
    val nowMs = System.currentTimeMillis()
    var mealDateMs by remember { mutableStateOf(existing?.dateEpochSeconds?.toLong()?.times(1000) ?: nowMs) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Nutrient rows (editable; prefilled from an estimate or an edit).
    var kcalText by remember { mutableStateOf(number(existing?.caloriesKcal)) }
    var proteinText by remember { mutableStateOf(number(existing?.proteinG)) }
    var carbsText by remember { mutableStateOf(number(existing?.carbsG)) }
    var fatText by remember { mutableStateOf(number(existing?.fatG)) }
    var satFatText by remember { mutableStateOf(number(existing?.saturatedFatG)) }
    var sodiumText by remember { mutableStateOf(number(existing?.sodiumMg)) }
    var caffeineText by remember { mutableStateOf(number(existing?.caffeineMg)) }
    var alcoholText by remember { mutableStateOf(number(existing?.alcoholG)) }

    // Estimate results + state.
    var modelDescription by remember { mutableStateOf("") }
    var citations by remember { mutableStateOf(existing?.citations.orEmpty()) }
    var hasEstimated by remember { mutableStateOf(false) }
    var showFallback by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Photos (photo mode): existing stored files + new picks/captures.
    var photos by remember {
        mutableStateOf(
            (existing?.photoFileNames.orEmpty()).map { name ->
                Uri.fromFile(File(viewModel.store.photosDirectory, name))
            },
        )
    }

    // Camera capture → one new photo (≤5 total enforced on launch).
    val lastCapture = remember { mutableStateOf<Uri?>(null) }
    val captureFile = remember { File(context.cacheDir, "capture.jpg").apply { delete() } }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val captured = lastCapture.value
        if (ok && captured != null) {
            photos = (photos + captured).take(5)
        }
        lastCapture.value = null
    }
    // Gallery multi-pick (≤5). The system picker degrades to single-select on
    // devices where multi-select is unavailable; total is capped at 5 here.
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5),
    ) { uris ->
        val stored = photos.filter { it.scheme == "file" }
        photos = (stored + uris).take(5)
    }

    // When the ViewModel's estimate finishes (the sheet's "Estimate"/
    // "Reanalyze" kicked it off), the result is applied here by the caller of
    // `runEstimate` below (it holds the row state). We track it only for the
    // spinner.
    val configEnabled = FoodLLMConfig.load(context).isEnabled

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(Color(0xF20B2530), RoundedCornerShape(24.dp))
                .border(1.dp, CluecoseColors.white(0.12f), RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            // Title + Cancel.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (existing == null) "Add meal" else "Edit meal",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CluecoseColors.white(0.7f),
                    modifier = Modifier
                        .background(CluecoseColors.white(0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .clickable(onClick = onDismiss),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .height(460.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ModeSegment(modeIsPhoto) { modeIsPhoto = it }

                // About this meal.
                SectionLabel("About this meal")
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
                // When it was eaten — defaults to now; pick an earlier time to
                // log after-the-fact. Capped at now (no future meals).
                // (iOS DatePicker row in FoodLogSheet.)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Text(
                        text = "When did you eat it?",
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Instant.ofEpochMilli(mealDateMs)
                            .atZone(ZoneId.systemDefault())
                            .format(MEAL_DATE_FORMAT),
                        fontSize = 14.sp,
                        color = CluecoseColors.GlowInRange,
                    )
                    Text(
                        text = "Change",
                        fontSize = 13.sp,
                        color = CluecoseColors.white(0.7f),
                        modifier = Modifier
                            .background(CluecoseColors.white(0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clickable { showDatePicker = true },
                    )
                }
                Text(
                    text = "What the camera can't know: portion, brand, preparation, hidden ingredients.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CluecoseColors.white(0.4f),
                )

                if (modeIsPhoto) {
                    SectionLabel("Photos (up to 5)")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PhotoChip("Capture") {
                            val uri = Uri.fromFile(captureFile)
                            lastCapture.value = uri
                            takePicture.launch(uri)
                        }
                        PhotoChip("Pick from gallery") {
                            pickMultiple.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        }
                    }
                    if (photos.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            photos.forEachIndexed { i, uri ->
                                PhotoThumb(uri) { photos = photos.filterIndexed { idx, _ -> idx != i } }
                            }
                        }
                    }
                }

                // Estimate section.
                SectionLabel("Estimate")
                if (showFallback) {
                    Text(
                        text = "Enter values by hand — the estimate failed, but you can still log this meal.",
                        fontSize = 12.sp,
                        color = CluecoseColors.GlowHigh,
                    )
                }
                if (isEstimating) {
                    Text(text = "Estimating…", fontSize = 12.sp, color = CluecoseColors.white(0.6f))
                }
                val buttonLabel = if (existing == null && !hasEstimated) "Estimate" else "Reanalyze"
                Text(
                    text = buttonLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CluecoseColors.Action,
                    modifier = Modifier
                        .background(CluecoseColors.Action.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .clickable(enabled = configEnabled && !isEstimating) {
                            errorMessage = null
                            scope.launch {
                                val request = NutritionPrompt.Request(
                                    description = descriptionText.ifBlank { if (modeIsPhoto) "meal photo" else "" },
                                    serving = servingText.takeIf { it.isNotBlank() },
                                    context = contextText.takeIf { it.isNotBlank() },
                                    mode = if (modeIsPhoto) NutritionPrompt.Mode.PHOTO else NutritionPrompt.Mode.MANUAL,
                                    photoCount = if (modeIsPhoto) photos.size else 0,
                                )
                                val photosToSend = if (modeIsPhoto) photos.mapNotNull { it.toJpegBytes(context) } else emptyList()
                                try {
                                    val est = viewModel.runEstimate(request, photosToSend)
                                    kcalText = number(est.caloriesKcal)
                                    proteinText = number(est.proteinG)
                                    carbsText = number(est.carbsG)
                                    fatText = number(est.fatG)
                                    satFatText = number(est.saturatedFatG)
                                    sodiumText = number(est.sodiumMg)
                                    caffeineText = number(est.caffeineMg)
                                    alcoholText = number(est.alcoholG)
                                    est.description?.takeIf { it.isNotEmpty() }?.let {
                                        modelDescription = it
                                        if (descriptionText.isEmpty()) descriptionText = it
                                    }
                                    citations = est.citations
                                    hasEstimated = true
                                    showFallback = false
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                    showFallback = true
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (!configEnabled) {
                    Text(
                        text = "Set an API key in Settings › Nutrition analysis to enable estimation.",
                        style = MaterialTheme.typography.labelSmall,
                        color = CluecoseColors.white(0.4f),
                    )
                }

                // Nutrition (editable) rows.
                SectionLabel("Nutrition (editable)")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutrientField("Calories", "kcal", kcalText) { kcalText = it }
                    NutrientField("Protein", "g", proteinText) { proteinText = it }
                    NutrientField("Carbs", "g", carbsText) { carbsText = it }
                    NutrientField("Fat", "g", fatText) { fatText = it }
                    NutrientField("Saturated fat", "g", satFatText) { satFatText = it }
                    NutrientField("Sodium", "mg", sodiumText) { sodiumText = it }
                    NutrientField("Caffeine", "mg", caffeineText) { caffeineText = it }
                    NutrientField("Alcohol", "g", alcoholText) { alcoholText = it }
                }

                // Identified (model description).
                if (modelDescription.isNotEmpty()) {
                    SectionLabel("Identified")
                    Text(modelDescription, fontSize = 12.sp, color = CluecoseColors.white(0.7f))
                }

                // Sources (citations, tappable).
                if (citations.isNotEmpty()) {
                    SectionLabel("Sources")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        citations.forEach { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { context.startActivitySafe(Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = c.title.ifEmpty { c.url },
                                    fontSize = 12.sp,
                                    color = CluecoseColors.GlowInRange,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            errorMessage?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = CluecoseColors.GlowHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            // Action row: [Delete] [Save].
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (existing != null) {
                    Text(
                        text = "Delete",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CluecoseColors.GlowHigh,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(CluecoseColors.GlowHigh.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .clickable {
                                viewModel.delete(existing)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                    )
                }
                val canSave = modeIsPhoto || descriptionText.isNotBlank()
                Text(
                    text = "Save",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canSave) Color(0xFF04150C) else CluecoseColors.white(0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (canSave) CluecoseColors.Action else CluecoseColors.white(0.15f), RoundedCornerShape(10.dp))
                        .clickable(enabled = canSave) {
                            val values = FoodViewModel.MealValues(
                                description = descriptionText,
                                serving = servingText.takeIf { it.isNotBlank() },
                                context = contextText.takeIf { it.isNotBlank() },
                                source = if (modeIsPhoto) FoodSource.PHOTO else FoodSource.MANUAL,
                                caloriesKcal = parse(kcalText),
                                proteinG = parse(proteinText),
                                carbsG = parse(carbsText),
                                fatG = parse(fatText),
                                saturatedFatG = parse(satFatText),
                                sodiumMg = parse(sodiumText),
                                caffeineMg = parse(caffeineText),
                                alcoholG = parse(alcoholText),
                                citations = citations,
                                dateEpochSeconds = mealDateMs / 1000.0,
                            )
                            val isEstimated = if (existing != null) false else hasEstimated
                            val photosToSave = if (modeIsPhoto) photos.mapNotNull { it.toJpegBytes(context) } else emptyList()
                            viewModel.saveEntry(existing, values, isEstimated, photosToSave)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }

    // Meal date/time pickers (Material3). Date first, then time; either can
    // be picked alone (the other half of the current mealDateMs is kept).
    // Future dates clamp back to now — you can't log a meal that hasn't
    // happened. (iOS DatePicker row, capped at now.)
    if (showDatePicker) {
        // Year range: the sensor's era through this year — no future years
        // (Material3 1.3 has no day-level range; the apply clamps to now).
        val thisYear = LocalDate.now().year
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = mealDateMs,
            yearRange = 2020..thisYear,
        )
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("When did you eat it?") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DatePicker(state = datePickerState)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { mealDateMs = System.currentTimeMillis() }) {
                        Text("Back to now")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        val picked = datePickerState.selectedDateMillis
                        if (picked != null) {
                            // Keep the current time-of-day, clamp to now
                            // (a later-in-today day would be a future meal).
                            val zone = ZoneId.systemDefault()
                            val keptTime = LocalTime.ofInstant(
                                Instant.ofEpochMilli(mealDateMs), zone,
                            )
                            val newDay = Instant.ofEpochMilli(picked)
                                .atZone(zone).toLocalDate()
                            val ms = newDay.atTime(keptTime)
                                .atZone(zone).toInstant().toEpochMilli()
                            mealDateMs = ms.coerceAtMost(nowMs)
                        }
                        showDatePicker = false
                        showTimePicker = true
                    },
                ) { Text("Next: time") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        )
    }
    if (showTimePicker) {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(mealDateMs).atZone(zone)
        val timePickerState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("What time?") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newTime = current.toLocalDate()
                        .atTime(timePickerState.hour, timePickerState.minute)
                    var ms = newTime.atZone(zone).toInstant().toEpochMilli()
                    if (ms > nowMs) ms = nowMs // no future meals
                    mealDateMs = ms
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}

// MARK: - Sub-components

private val MEAL_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")

@Composable
private fun ModeSegment(isPhoto: Boolean, onSelect: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CluecoseColors.white(0.08f), shape)
            .padding(3.dp),
    ) {
        Row {
            listOf(false to "Type", true to "Photo").forEach { (photo, label) ->
                val selected = photo == isPhoto
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) CluecoseColors.white(0.16f) else Color.Transparent, RoundedCornerShape(100.dp))
                        .clickable { onSelect(photo) }
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else CluecoseColors.white(0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = CluecoseColors.white(0.45f),
    )
}

/** An editable nutrient row: title left, decimal field, unit right. */
@Composable
private fun NutrientField(title: String, unit: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, color = CluecoseColors.white(0.75f), modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(110.dp),
        )
        Text(unit, fontSize = 13.sp, color = CluecoseColors.white(0.5f), modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun PhotoChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = CluecoseColors.GlowInRange,
        modifier = Modifier
            .background(CluecoseColors.white(0.08f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun PhotoThumb(uri: Uri, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(CluecoseColors.white(0.1f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "×",
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .background(Color(0x99000000), RoundedCornerShape(50))
                .size(18.dp)
                .clickable(onClick = onRemove)
                .padding(4.dp),
        )
    }
}

// MARK: - Helpers

private fun number(v: Double?): String = v?.let { String.format("%.1f", it) } ?: ""

private fun parse(text: String): Double? = text.trim().toDoubleOrNull()

/**
 * Load a picked/captured image URI as a downscale JPEG byte array (null when
 * it can't be read/decoded). Used on save + estimate for photo entries.
 */
private fun Uri.toJpegBytes(context: android.content.Context): ByteArray? {
    return try {
        if (scheme == "file") {
            val f = path?.let { File(it) } ?: return null
            if (!f.exists()) return null
            PhotoCompressor.toJpegBytes(f.readBytes())
        } else {
            context.contentResolver.openInputStream(this)?.use { stream ->
                PhotoCompressor.toJpegBytes(stream.readBytes())
            }
        }
    } catch (e: Exception) {
        null
    }
}

/** Best-effort startActivity (a missing handler is a no-op, not a crash). */
private fun android.content.Context.startActivitySafe(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        // No handler for the URL — ignore (the citation text is still shown).
    }
}
