//
//  PatternsCard.kt
//  Cluecose
//
//  The "Learn" surface: 7-day averages from the shared PatternsEngine (kcal,
//  protein, carbs, fat, sodium, caffeine, alcohol, saturated fat, and the
//  saturated-fat share of total fat) plus the deterministic coaching
//  follow-up questions.
//
//  // TODO(defer): the nutrition-sleep association lines are an Apple-surface
//  // concern (HealthKit sleep) and are deferred on Android; the card shows
//  // nothing for that section here.
//

package com.dsikic.cluecose.food

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsikic.cluecose.core.food.NutritionPatterns
import com.dsikic.cluecose.ui.CluecoseColors
import com.dsikic.cluecose.ui.GlassPanel

/**
 * The 7-day patterns + coaching card (iOS `PatternsCard`). Driven by the
 * ViewModel's [NutritionPatterns] + coaching list.
 */
@Composable
fun PatternsCard(viewModel: FoodViewModel, modifier: Modifier = Modifier) {
    val patterns = viewModel.state.value.patterns
    val coaching = viewModel.state.value.coaching

    GlassPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Learn",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )

            if (patterns.entryCount == 0) {
                Text(
                    text = "Log meals for a few days and 7-day patterns appear here.",
                    fontSize = 12.sp,
                    color = CluecoseColors.white(0.5f),
                )
            } else {
                AveragesGrid(patterns)
                if (coaching.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Coaching",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = CluecoseColors.white(0.6f),
                        )
                        coaching.forEach { q ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.QuestionMark,
                                    contentDescription = null,
                                    tint = CluecoseColors.GlowInRange,
                                    modifier = Modifier.height(14.dp),
                                )
                                Text(
                                    text = q,
                                    fontSize = 12.sp,
                                    color = CluecoseColors.white(0.8f),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The 3×3 grid of 7-day averages (iOS `averagesGrid`). */
@Composable
private fun AveragesGrid(p: NutritionPatterns) {
    fun fmt(v: Double?): String? = v?.let { String.format("%.0f", it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GridRow(
            listOf(
                Triple("Calories", fmt(p.avgKcalPerDay), "kcal/day"),
                Triple("Protein", fmt(p.avgProteinPerDay), "g/day"),
                Triple("Carbs", fmt(p.avgCarbsPerDay), "g/day"),
            ),
        )
        GridRow(
            listOf(
                Triple("Fat", fmt(p.avgFatPerDay), "g/day"),
                Triple("Sodium", fmt(p.avgSodiumPerDay), "mg/day"),
                Triple("Caffeine", fmt(p.avgCaffeinePerDay), "mg/day"),
            ),
        )
        GridRow(
            listOf(
                Triple("Alcohol", fmt(p.avgAlcoholPerDay), "g/day"),
                Triple("Sat. fat", fmt(p.avgSaturatedFatPerDay), "g/day"),
                Triple("Sat. of fat", p.saturatedFatToFatRatio?.let { "${(it * 100).toInt()}%" }, "share"),
            ),
        )
    }
}

@Composable
private fun GridRow(cells: List<Triple<String, String?, String>>) {
    Row {
        cells.forEachIndexed { i, (title, value, unit) ->
            if (i > 0) {
                Spacer(modifier = Modifier.width(1.dp).background(CluecoseColors.white(0.1f)))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(title, fontSize = 10.sp, color = CluecoseColors.white(0.5f))
                Text(value ?: "–", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(unit, fontSize = 9.sp, color = CluecoseColors.white(0.4f))
            }
        }
    }
}
