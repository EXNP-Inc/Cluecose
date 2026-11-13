package com.dsikic.cluecose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Mirrors iOS `ContentView` (the connected, live-reading layout): header row,
 * hero card, 24 h chart, the 24 h metrics grid, the metabolic-state card, the
 * tips list, and the brand-free "not a medical device" footer.
 */
@Composable
fun MainScreen(
    state: UiState,
    onUnitChange: (GlucoseUnit) -> Unit,
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val latest = state.latest
        if (state.hasLiveReading && latest != null) {
            HeroCard(state)
            ChartCard(state)
            StatsGridCard(state)
            BurnModeCard(state)
            TipsCard(state)
        }

        Text(
            text = "Reads your sensor directly over Bluetooth — no account, no cloud. Not a medical device.",
            style = MaterialTheme.typography.labelSmall,
            color = CluecoseColors.white(0.35f),
            textAlign = TextAlign.Center,
        )
    }
}

// MARK: - Header

/**
 * Header row shared by the root (it's always on screen, live or not):
 * "Cluecose" title + "CGM · direct Bluetooth" subtitle, the mg/dL / mmol/L
 * unit toggle, and the settings button.
 */
@Composable
fun HeaderRow(
    state: UiState,
    onUnitChange: (GlucoseUnit) -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Cluecose",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White,
            )
            Text(
                text = "CGM · direct Bluetooth",
                style = MaterialTheme.typography.labelMedium,
                color = CluecoseColors.white(0.55f),
            )
        }
        UnitToggle(state.unit) { onUnitChange(it) }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(CluecoseColors.white(0.12f), CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = CluecoseColors.white(0.75f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun UnitToggle(unit: GlucoseUnit, onUnitChange: (GlucoseUnit) -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        modifier = Modifier
            .background(CluecoseColors.white(0.12f), shape)
            .border(1.dp, CluecoseColors.white(0.12f), shape)
            .padding(3.dp),
    ) {
        Row {
            GlucoseUnit.entries.forEach { u ->
                val selected = u == unit
                Box(
                    modifier = Modifier
                        .background(if (selected) Color(0xFFEBFBFF) else Color.Transparent, RoundedCornerShape(100.dp))
                        .clickable { onUnitChange(u) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = u.label,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (selected) Color(0xFF04150C) else CluecoseColors.white(0.7f),
                    )
                }
            }
        }
    }
}

// MARK: - Hero card

@Composable
private fun HeroCard(state: UiState) {
    val latest = state.latest ?: return
    val mgdl = latest.mgdl
    val color = glowColor(RangeCategory.from(mgdl))

    GlassPanel(cornerRadius = 34.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Top row: "Now" + connection pill + live/stale indicator.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.GpsFixed,
                        contentDescription = null,
                        tint = CluecoseColors.white(0.6f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Now",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = CluecoseColors.white(0.6f),
                    )
                }
                ConnectionPill(linkActive = state.connectionState == ConnectionState.CONNECTED)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(CluecoseColors.white(0.12f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (state.isFresh) color else CluecoseColors.GlowHigh, CircleShape),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isFresh) "Live" else "Last reading ${state.readingAgeText ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (state.isFresh) CluecoseColors.white(0.75f) else CluecoseColors.GlowHigh,
                        )
                    }
                }
            }

            // Big value + trend + unit + sensor age.
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HeroValue(
                    text = state.unit.display(mgdl),
                    glow = color,
                    modifier = Modifier.weight(1f),
                )
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = state.unit.label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CluecoseColors.white(0.7f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(text = state.trend.arrow, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
                        Text(
                            text = state.trend.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = color,
                        )
                    }
                    val age = state.readingAgeText
                    if (age != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = CluecoseColors.white(0.4f),
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "Updated ${age}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CluecoseColors.white(0.4f),
                            )
                        }
                    }
                    // Sensor identity (iOS hero: "Sensor on {age}"; the serial
                    // doubles as the "Paired sensor" settings value).
                    val sensorAge = state.sensorAgeText
                    if (sensorAge != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = CluecoseColors.white(0.4f),
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "Sensor on ${sensorAge}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CluecoseColors.white(0.4f),
                            )
                        }
                    }
                    val serial = state.sensorName
                    if (serial != null) {
                        Text(
                            text = "Sensor $serial",
                            style = MaterialTheme.typography.labelSmall,
                            color = CluecoseColors.white(0.3f),
                            maxLines = 1,
                        )
                    }
                }
            }

            RangeGauge(mgdl, color)
        }
    }
}

@Composable
private fun HeroValue(text: String, glow: Color, modifier: Modifier = Modifier) {
    val style = androidx.compose.ui.text.TextStyle(
        fontSize = 64.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )
    Box(modifier = modifier) {
        // Glow layer: a blurred colored copy behind the crisp near-white value
        // (iOS paints the digits with a white->white(0.82) gradient; the glow
        // does the same job on Android).
        Text(
            text = text,
            style = style,
            color = glow.copy(alpha = 0.85f),
            modifier = Modifier.blur(14.dp),
            maxLines = 1,
        )
        Text(
            text = text,
            style = style,
            color = Color(0xFFEAF6F9),
            maxLines = 1,
        )
    }
}

@Composable
private fun ConnectionPill(linkActive: Boolean) {
    Box(
        modifier = Modifier
            .background(CluecoseColors.white(0.10f), RoundedCornerShape(100.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(
                imageVector = if (linkActive) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                contentDescription = null,
                tint = if (linkActive) Color(0xFF66F2B3) else CluecoseColors.white(0.65f),
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = if (linkActive) "Direct link" else "Reconnecting",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (linkActive) Color(0xFF66F2B3) else CluecoseColors.white(0.65f),
            )
        }
    }
}

@Composable
private fun RangeGauge(mgdl: Double, color: Color, modifier: Modifier = Modifier) {
    val lo = 40.0
    val hi = 300.0
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(20.dp)) {
        // constraints.maxWidth is a FLOAT IN PIXELS. The original bug was
        // taking that px value and applying the `.dp` extension, which
        // re-multiplies by density — on a 420dpi device the band and
        // marker landed ~2.6x past the card's right edge. Convert px -> Dp
        // exactly once, through the density.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val w = constraints.maxWidth
        val bandStart = with(density) { (((70.0 - lo) / (hi - lo)).toFloat() * w).toDp() }
        val bandWidth = with(density) { (((180.0 - 70.0) / (hi - lo)).toFloat() * w).toDp() }
        val frac = ((mgdl - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
        val markerX = with(density) { (frac * w).toDp() }
        val dotOffset = 8.dp

        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .align(Alignment.Center)
                .background(CluecoseColors.white(0.12f), CircleShape),
        )
        // In-range band (low->inRange->high gradient)
        Box(
            modifier = Modifier
                .width(bandWidth)
                .height(8.dp)
                .align(Alignment.CenterStart)
                .offset(x = bandStart)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            CluecoseColors.GlowLow.copy(alpha = 0.5f),
                            CluecoseColors.GlowInRange.copy(alpha = 0.85f),
                            CluecoseColors.GlowHigh.copy(alpha = 0.5f),
                        ),
                    ),
                    CircleShape,
                ),
        )
        // Marker dot
        Box(
            modifier = Modifier
                .size(16.dp)
                .offset(x = markerX - dotOffset, y = (-4).dp)
                .background(Color.White, CircleShape)
                .drawBehind {
                    drawCircle(color = color.copy(alpha = 0.9f), radius = 3.5.dp.toPx())
                },
        )
    }
}

// MARK: - Chart card

@Composable
private fun ChartCard(state: UiState) {
    GlassPanel() {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Last 24 hours",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (state.stats.count > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${state.stats.count} readings",
                        style = MaterialTheme.typography.labelMedium,
                        color = CluecoseColors.white(0.5f),
                    )
                }
            }
            if (state.samples.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = null,
                            tint = CluecoseColors.white(0.4f),
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No readings yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = CluecoseColors.white(0.5f),
                        )
                    }
                }
            } else {
                GlucoseChart(
                    samples = state.samples,
                    nowMs = state.nowMs,
                    unit = state.unit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                )
            }
        }
    }
}

// MARK: - 24 h metrics grid

private data class MetricCell(val title: String, val value: String, val icon: ImageVector, val tint: Color, val warn: Boolean)

@Composable
private fun StatsGridCard(state: UiState) {
    val stats = state.stats
    val unit = state.unit
    GlassPanel() {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "24 h metrics",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (stats.isMeaningful) {
                    Text(
                        text = "${stats.count} readings",
                        style = MaterialTheme.typography.labelMedium,
                        color = CluecoseColors.white(0.4f),
                    )
                }
            }

            if (!stats.isMeaningful) {
                Text(
                    text = "Need a few more readings to compute these — they fill in as the day goes on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CluecoseColors.white(0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
            } else {
                val cells = listOf(
                    MetricCell("TIR 70–180", pct(stats.tir), Icons.Filled.VerifiedUser, CluecoseColors.GlowInRange, stats.tir < 0.70),
                    MetricCell("Time high", pct(stats.tar), Icons.Filled.ArrowUpward, CluecoseColors.GlowHigh, stats.tar > 0.25),
                    MetricCell("Time low", pct(stats.tbr), Icons.Filled.ArrowDownward, CluecoseColors.GlowLow, stats.tbr > 0.04),
                    MetricCell("Mean", unit.format(stats.mean), Icons.Filled.Numbers, CluecoseColors.white(0.85f), false),
                    MetricCell("Min", unit.format(stats.min), Icons.Filled.ArrowDownward, CluecoseColors.GlowLow, false),
                    MetricCell("Max", unit.format(stats.max), Icons.Filled.ArrowUpward, CluecoseColors.GlowHigh, false),
                    MetricCell("CV", "${stats.cv.roundToInt()}%", Icons.Filled.Audiotrack, CluecoseColors.TintCv, stats.cv > 36),
                    MetricCell("GMI", String.format("%.1f%%", stats.gmi), Icons.Filled.Speed, CluecoseColors.TintGmi, stats.gmi > 7.0),
                    MetricCell("AUC > 140", stats.aucAbove140.roundToInt().toString(), Icons.Filled.LocalFireDepartment, CluecoseColors.GlowHigh, false),
                )
                // Static 3x3 of fixed cells — a plain nested Row layout, since
                // a LazyVerticalGrid cannot live inside the root's
                // verticalScroll (infinite-height measurement crash).
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    cells.chunked(3).forEach { rowCells ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowCells.forEach { cell ->
                                Box(modifier = Modifier.weight(1f)) {
                                    MetricCellView(cell)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCellView(cell: MetricCell) {
    val valueColor = if (cell.warn) CluecoseColors.Warn else Color.White
    val labelColor = if (cell.warn) CluecoseColors.Warn else CluecoseColors.white(0.55f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CluecoseColors.white(0.05f), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = cell.icon,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = cell.title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                )
            }
            Text(
                text = cell.value,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

// MARK: - Metabolic state (storage vs burning)

@Composable
private fun BurnModeCard(state: UiState) {
    val mode = state.burnMode
    val tint = when (mode) {
        BurnMode.STORING -> CluecoseColors.GlowHigh
        BurnMode.BALANCED -> CluecoseColors.GlowInRange
        BurnMode.BURNING -> CluecoseColors.GlowLow
    }
    val icon = when (mode) {
        BurnMode.STORING -> Icons.Filled.LocalFireDepartment
        BurnMode.BALANCED -> Icons.Filled.Scale
        BurnMode.BURNING -> Icons.Outlined.LocalFireDepartment
    }
    val subtitle = when (mode) {
        BurnMode.STORING ->
            "Repeated time above 140 mg/dL — insulin is working overtime, and that's when glucose gets shunted into fat stores."
        BurnMode.BALANCED ->
            "Glucose is holding in range without much storage pressure. Neither storing hard nor in a clear burn."
        BurnMode.BURNING ->
            "Low storage pressure and falling glucose — the body is running on its own stores (glycogen, then fat)."
    }

    GlassPanel() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Metabolic state",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "last 24 h",
                    style = MaterialTheme.typography.labelMedium,
                    color = CluecoseColors.white(0.4f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Shrink-to-fit (iOS resizes the word instead of wrapping it):
                // measure at 34 sp and step the size down until it fits the
                // available width, floored at 16 sp.
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val labelMeasurer = rememberTextMeasurer()
                    val maxWidthPx = constraints.maxWidth // already px
                    val word = mode.label.uppercase()
                    // Measure with the SAME style the rendered Text uses
                    // (a bare TextStyle + the fontSize/weight overrides below).
                    var fontSize = 34.sp
                    while (true) {
                        val width = labelMeasurer.measure(
                            text = word,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                        ).size.width
                        if (width <= maxWidthPx - 2 || fontSize <= 16.sp) break
                        fontSize = fontSize * 0.95f
                    }
                    Text(
                        text = word,
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        color = tint,
                        maxLines = 1,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "AUC > 140",
                        style = MaterialTheme.typography.labelSmall,
                        color = CluecoseColors.white(0.45f),
                    )
                    Text(
                        text = state.stats.aucAbove140.roundToInt().toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = CluecoseColors.white(0.9f),
                    )
                    Text(
                        text = "mg/dL·min of storage pressure",
                        style = MaterialTheme.typography.labelSmall,
                        color = CluecoseColors.white(0.35f),
                    )
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CluecoseColors.white(0.6f),
            )
            Text(
                text = "Estimate from glucose + trend — an index of insulin activity, not a direct fat-oxidation measurement. Not medical advice.",
                style = MaterialTheme.typography.labelSmall,
                color = CluecoseColors.white(0.3f),
            )
        }
    }
}

// MARK: - Tips

@Composable
private fun TipsCard(state: UiState) {
    GlassPanel() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = CluecoseColors.TintTip,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tips",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.tips.isEmpty()) {
                Text(
                    text = "No tips right now — steady is good.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CluecoseColors.white(0.5f),
                )
            } else {
                state.tips.forEach { tip ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = tipIcon(tip.kind),
                            contentDescription = null,
                            tint = tipTint(tip.kind),
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 2.dp),
                        )
                        SelectionContainer {
                            Text(
                                text = tip.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = CluecoseColors.white(0.72f),
                            )
                        }
                    }
                }
            }

            Text(
                text = "General information, not medical advice. For persistent lows, highs, or symptoms, talk to a clinician.",
                style = MaterialTheme.typography.labelSmall,
                color = CluecoseColors.white(0.3f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun tipIcon(kind: GlucoseTipKind): ImageVector = when (kind) {
    GlucoseTipKind.LOW -> Icons.Filled.ArrowDownward
    GlucoseTipKind.HIGH -> Icons.Filled.ArrowUpward
    GlucoseTipKind.VARIABILITY -> Icons.Filled.Audiotrack
    GlucoseTipKind.RANGE -> Icons.Filled.Speed
    GlucoseTipKind.STORAGE -> Icons.Filled.LocalFireDepartment
    GlucoseTipKind.GOOD -> Icons.Filled.VerifiedUser
}

@Composable
private fun tipTint(kind: GlucoseTipKind): Color = when (kind) {
    GlucoseTipKind.LOW -> CluecoseColors.GlowLow
    GlucoseTipKind.HIGH -> CluecoseColors.GlowHigh
    GlucoseTipKind.VARIABILITY, GlucoseTipKind.RANGE, GlucoseTipKind.STORAGE -> CluecoseColors.GlowHigh
    GlucoseTipKind.GOOD -> CluecoseColors.GlowInRange
}
