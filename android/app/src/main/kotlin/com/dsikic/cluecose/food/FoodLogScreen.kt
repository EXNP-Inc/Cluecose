//
//  FoodLogScreen.kt
//  Cluecose
//
//  The home-screen food surface: the day's totals (kcal, protein, carbs,
//  fat, sodium, caffeine in the main line; alcohol + saturated fat as a
//  secondary line), a small in-app search box, the day's entries
//  (description, a small nutrient line, a photo thumb when there is one, and
//  the per-meal insight in a colored callout when present — spike = orange,
//  reactive low = blue, nothing when nil), and the "Add meal" button.
//
//  A tap on an entry opens the add/edit sheet in edit mode; deleting a photo
//  or an entry is handled in the sheet / card. Styled with the shared glass
//  theme. Mirrors iOS `FoodLogCard`.
//

package com.dsikic.cluecose.food

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsikic.cluecose.R
import com.dsikic.cluecose.core.food.FoodEntry
import com.dsikic.cluecose.core.food.FoodTotals
import com.dsikic.cluecose.core.food.MealInsight
import com.dsikic.cluecose.ui.CluecoseColors
import com.dsikic.cluecose.ui.GlassPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// MARK: - Food card (iOS FoodLogCard)

/**
 * The food-log card. [viewModel] drives the day's totals + entries; [onAdd]
 * opens the add sheet (new entry) and [onEdit] opens it in edit mode for an
 * existing entry — both are set by the parent (MainScreen / MainActivity).
 */
@Composable
fun FoodLogScreen(
    viewModel: FoodViewModel,
    onAdd: () -> Unit,
    onEdit: (FoodEntry) -> Unit,
    onReanalyze: (FoodEntry) -> Unit = {},
    onDetail: (FoodEntry) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    GlassPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = CluecoseColors.white(0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Food",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
                Text(
                    text = dayLabel(viewModel.state.value.selectedDayMs),
                    fontSize = 12.sp,
                    color = CluecoseColors.white(0.5f),
                )
            }

            // Totals.
            TotalsRow(viewModel.totalsForSelectedDay())

            // Search box.
            SearchBox(query) { query = it }

            // Entries.
            val day = viewModel.dayEntries()
            val visible = if (query.isBlank()) day else day.filter { it.matches(query) }
            if (day.isEmpty()) {
                EmptyState()
            } else if (visible.isEmpty()) {
                Text(
                    text = "No meals match “$query” today.",
                    fontSize = 12.sp,
                    color = CluecoseColors.white(0.5f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visible.forEach { entry ->
                        EntryRow(
                            entry = entry,
                            photoPath = photoFileFor(entry, viewModel),
                            onClick = { onEdit(entry) },
                            onReanalyze = { onReanalyze(entry) },
                            onRemovePhoto = { viewModel.deletePhoto(entry, it) },
                            onDetail = { onDetail(entry) },
                            analytics = viewModel.analytics(entry),
                        )
                    }
                }
            }

            // Add button.
            AddButton(onClick = onAdd)
        }
    }
}

private fun FoodEntry.matches(query: String): Boolean {
    val needle = query.trim().lowercase()
    return description.lowercase().contains(needle) ||
        (serving?.lowercase()?.contains(needle) == true) ||
        (context?.lowercase()?.contains(needle) == true)
}

// MARK: - Totals (iOS `totals`)

@Composable
private fun TotalsRow(t: FoodTotals) {
    if (t.entryCount == 0) {
        Text(
            text = "No meals logged today.",
            fontSize = 12.sp,
            color = CluecoseColors.white(0.5f),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Main line: kcal, protein, carbs, fat, sodium, caffeine.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TotalPill("kcal", t.kcal, "")
                TotalPill("P", t.proteinG, "g")
                TotalPill("C", t.carbsG, "g")
                TotalPill("F", t.fatG, "g")
                TotalPill("Na", t.sodiumMg, "mg")
                TotalPill("Caf", t.caffeineMg, "mg")
            }
            // Secondary line: alcohol + saturated fat (only when > 0).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                t.alcoholG?.takeIf { it > 0 }?.let { TotalPill("Alc", it, "g") }
                t.saturatedFatG?.takeIf { it > 0 }?.let { TotalPill("Sat", it, "g") }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TotalPill(label: String, value: Double?, unit: String) {
    val text = value?.let { String.format(Locale.US, "%.0f", it) } ?: "–"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .background(CluecoseColors.white(0.08f), RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = CluecoseColors.white(0.55f))
        Text(text = text + unit, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

// MARK: - Search box

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(CluecoseColors.white(0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.FiberManualRecord,
            contentDescription = null,
            tint = CluecoseColors.white(0.4f),
            modifier = Modifier.size(14.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = CluecoseColors.white(0.8f)),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Search meals", fontSize = 12.sp, color = CluecoseColors.white(0.4f))
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear",
                tint = CluecoseColors.white(0.4f),
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

// MARK: - Empty state

@Composable
private fun EmptyState() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = null,
            tint = CluecoseColors.white(0.4f),
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "Log what you eat to see it here.",
            fontSize = 12.sp,
            color = CluecoseColors.white(0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

// MARK: - Entry row

@Composable
private fun EntryRow(
    entry: FoodEntry,
    photoPath: String?,
    onClick: () -> Unit,
    onReanalyze: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onDetail: () -> Unit,
    analytics: MealAnalytics,
) {
    val painter = photoPath?.let { remember(it) { filePainter(it) } }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Photo thumb (first photo only, 40 dp).
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.photoFileNames.isNotEmpty()) {
                    val n = entry.photoFileNames.size
                    Text(
                        text = "$n photo${if (n == 1) "" else "s"}",
                        fontSize = 10.sp,
                        color = CluecoseColors.white(0.4f),
                    )
                }
                NutrientLine(entry)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // The details arrow — top-right of the card entry (iOS
                // `chevron.right` Button → onDetail).
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Details",
                    tint = CluecoseColors.white(0.45f),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onDetail),
                )
                Text(
                    text = timeLabel(entry.dateEpochSeconds),
                    fontSize = 10.sp,
                    color = CluecoseColors.white(0.4f),
                )
            }
        }

        if (entry.insight != null) {
            // The insight callout is itself tappable → detail view (iOS).
            InsightCallout(entry.insight!!, onReanalyze = onDetail)
        } else if (analytics is MealAnalytics.Pending) {
            // The small "analyzing" pill shown while the post-meal window is
            // still forming: what's happening + when the result lands.
            // Tappable → detail. (iOS `pendingPill`.)
            val a = analytics
            val readyLabel = SimpleDateFormat("h:mm a", Locale.US)
                .format(Date(a.readyAtMs))
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CluecoseColors.GlowLow.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
                    .clickable(onClick = onDetail),
            ) {
                Icon(
                    imageVector = Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint = CluecoseColors.GlowLow,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "Blood-sugar analysis in ~${a.remainingMin} min (done $readyLabel)",
                    fontSize = 10.sp,
                    color = CluecoseColors.white(0.75f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A compact single line of the entry's nutrient values (skips nils). */
@Composable
private fun NutrientLine(entry: FoodEntry) {
    val parts = mutableListOf<String>()
    entry.caloriesKcal?.let { parts.add(String.format(Locale.US, "%.0f kcal", it)) }
    entry.proteinG?.let { parts.add(String.format(Locale.US, "P %.0f", it)) }
    entry.carbsG?.let { parts.add(String.format(Locale.US, "C %.0f", it)) }
    entry.fatG?.let { parts.add(String.format(Locale.US, "F %.0f", it)) }
    entry.sodiumMg?.let { parts.add(String.format(Locale.US, "Na %.0f", it)) }
    entry.caffeineMg?.let { parts.add(String.format(Locale.US, "Caf %.0f", it)) }
    Text(
        text = parts.joinToString("  ·  "),
        fontSize = 10.sp,
        color = CluecoseColors.white(0.5f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The per-meal insight callout: orange for a spike, blue for a reactive low.
 * Nothing when there is no insight (the engine returned nil).
 */
@Composable
private fun InsightCallout(insight: MealInsight, onReanalyze: () -> Unit) {
    val color = if (insight.kind == MealInsight.Kind.SPIKE) CluecoseColors.GlowHigh else CluecoseColors.GlowLow
    val symbol = if (insight.kind == MealInsight.Kind.SPIKE) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .clickable(onClick = onReanalyze),
    ) {
        Icon(imageVector = symbol, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(
            text = insight.text,
            fontSize = 10.sp,
            color = CluecoseColors.white(0.75f),
            modifier = Modifier.weight(1f),
        )
    }
}

// MARK: - Add button

@Composable
private fun AddButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(CluecoseColors.Action, CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color(0xFF04150C), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "Add meal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF04150C))
    }
}

// MARK: - Labels / photo

/** "Wed 12" style label for the selected day (iOS `.dateTime.weekday(.abbreviated).day()`). */
private fun dayLabel(ms: Long): String =
    SimpleDateFormat("EEE d", Locale.US).format(Date(ms))

/** "3:42" style time label for an entry (iOS `.dateTime.hour().minute()`). */
private fun timeLabel(epochSeconds: Double): String =
    SimpleDateFormat("h:mm a", Locale.US).format(Date((epochSeconds * 1000).toLong()))

/**
 * The absolute path of the entry's first stored photo, or null when it has
 * none (or the file is missing). Resolved through the [FoodRepository] photos
 * directory so the 40 dp thumb decodes the real `food/photos/{id}_{i}.jpg`.
 */
private fun photoFileFor(entry: FoodEntry, viewModel: FoodViewModel): String? {
    val name = entry.photoFileNames.firstOrNull() ?: return null
    val f = java.io.File(viewModel.store.photosDirectory, name)
    return if (f.exists()) f.absolutePath else null
}

/**
 * A painter for one stored photo file (null when it can't be decoded). Kept
 * out of the composable body: the caller wraps it in `remember(path)` so the
 * decode runs once per file. The stored file is already a ~720 px JPEG, so
 * this is a cheap thumb decode (mirrors iOS decoding the first stored photo
 * inline).
 */
private fun filePainter(path: String): Painter? = try {
    val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return null
    BitmapPainter(bmp.asImageBitmap())
} catch (e: Exception) {
    null
}
