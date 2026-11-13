package com.dsikic.cluecose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dependency-free 24 h glucose chart (no Charts lib):
 *  - x = time over the last 24 h, y = mg/dL 40…300
 *  - in-range band 70–180 shaded green at 10% alpha
 *  - polyline of the samples, colored by the glow color of the LATEST value,
 *    with an area fill fading to clear
 *  - faint axis labels (min / mid / max, 6 h ticks, "Now")
 *  - glowing dot + value badge on the latest sample
 */
@Composable
fun GlucoseChart(
    samples: List<GlucoseSample>,
    nowMs: Long,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier.fillMaxSize()) {
        if (samples.isEmpty()) return@Canvas

        val padTop = 12.dp.toPx()
        val padBottom = 22.dp.toPx()
        val padLeft = 4.dp.toPx()
        val padRight = 8.dp.toPx()
        val plotW = size.width - padLeft - padRight
        val plotH = size.height - padTop - padBottom
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        val yMin = 40.0
        val yMax = 300.0
        val t0 = nowMs - 24L * 60 * 60_000
        val t1 = nowMs
        val lineColor = glowColor(RangeCategory.from(samples.last().mgdl))
        val faint = CluecoseColors.white(0.45f)

        fun xOf(ms: Long): Float {
            val f = (ms - t0).toFloat() / (t1 - t0).toFloat()
            return padLeft + f.coerceIn(0f, 1f) * plotW
        }

        fun yOf(v: Double): Float {
            val f = ((v - yMin) / (yMax - yMin)).coerceIn(0.0, 1.0)
            return (padTop + (1.0 - f) * plotH).toFloat()
        }

        // In-range band 70–180, green 10%.
        val bandTop = yOf(180.0)
        val bandBottom = yOf(70.0)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF4CEB99).copy(alpha = 0.10f), Color(0xFF4CEB99).copy(alpha = 0.10f)),
            ),
            topLeft = Offset(padLeft, bandTop),
            size = Size(plotW, bandBottom - bandTop),
        )
        drawLine(
            color = CluecoseColors.white(0.10f),
            start = Offset(padLeft, bandTop),
            end = Offset(padLeft + plotW, bandTop),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = CluecoseColors.white(0.10f),
            start = Offset(padLeft, bandBottom),
            end = Offset(padLeft + plotW, bandBottom),
            strokeWidth = 1.dp.toPx(),
        )

        // 6 h vertical gridlines + time labels.
        for (h in 0..24 step 6) {
            val x = xOf(t0 + h * 3_600_000L)
            if (h in 1..24 step 6) {
                drawLine(
                    color = CluecoseColors.white(0.08f),
                    start = Offset(x, padTop),
                    end = Offset(x, padTop + plotH),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val hoursAgo = 24 - h
            val label = if (hoursAgo == 0) "Now" else "-${hoursAgo}h"
            drawText(
                textMeasurer,
                text = label,
                topLeft = Offset(x - 10.dp.toPx(), padTop + plotH + 4.dp.toPx()),
                style = TextStyle(color = faint, fontSize = 9.sp),
                maxLines = 1,
            )
        }

        // Y labels: max / mid / min, faint.
        fun yLabel(v: Double) {
            drawText(
                textMeasurer,
                text = unit.format(v),
                topLeft = Offset(padLeft + plotW - 28.dp.toPx(), yOf(v) - 6.dp.toPx()),
                style = TextStyle(color = faint, fontSize = 9.sp),
                maxLines = 1,
            )
        }
        yLabel(yMax)
        yLabel((yMax + yMin) / 2.0)
        yLabel(yMin)

        // Area under the curve, then the polyline itself.
        val pts = samples.map { Offset(xOf(it.dateMs), yOf(it.mgdl)) }
        if (pts.size >= 2) {
            val area = Path().apply {
                moveTo(pts.first().x, padTop + plotH)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, padTop + plotH)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0f)),
                    startY = padTop,
                    endY = padTop + plotH,
                ),
            )

            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (p in pts) lineTo(p.x, p.y)
            }
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Latest point: glowing dot with a small value badge.
        val last = samples.last()
        val lp = Offset(xOf(last.dateMs), yOf(last.mgdl))
        drawCircle(color = lineColor.copy(alpha = 0.35f), radius = 9.dp.toPx(), center = lp)
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = lp)

        val badgeW = 34.dp.toPx()
        val badgeH = 16.dp.toPx()
        val bx = (lp.x - badgeW / 2).coerceIn(padLeft, padLeft + plotW - badgeW)
        val by = (lp.y - badgeH - 8.dp.toPx()).coerceAtLeast(padTop)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.14f)),
            ),
            topLeft = Offset(bx, by),
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(badgeH / 2, badgeH / 2),
        )
        drawText(
            textMeasurer,
            text = unit.format(last.mgdl),
            topLeft = Offset(bx + 5.dp.toPx(), by + 3.dp.toPx()),
            style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}
