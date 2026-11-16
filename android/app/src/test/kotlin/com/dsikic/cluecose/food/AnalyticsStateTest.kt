//
//  AnalyticsStateTest.kt
//  Cluecose (Android)
//
//  Unit tests for the pure per-meal analytics-state decision (the Android
//  twin of iOS `FoodLogModelTests.testAnalyticsStateTransitions`, plus the
//  `noWindowData` cases added for after-the-fact logging).
//
//  The past-meal rule under test: a meal whose post window has ALREADY
//  elapsed ("final only") must resolve to
//    • NoWindowData — a series exists but NOTHING falls inside the meal's
//      analysis window (baseline start → post-window end). The meal was
//      logged before the sensor started / across a gap; the analysis cannot
//      run and never will.
//    • Settled — the window had data and the curve was flat (or too sparse
//      to judge): the analysis ran and found nothing notable.
//  A meal whose window is still OPEN is Pending even with zero window
//  samples so far (readings are still arriving).
//

package com.dsikic.cluecose.food

import com.dsikic.cluecose.core.food.InsightConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsStateTest {

    private val meal = 1_000_000.0            // an arbitrary past epoch
    private val window = InsightConfig.DEFAULT.postWindowMin   // 180 min
    private val mid = meal + 30.0 * 60             // 30 min into the window
    private val closed = meal + window * 60 + 60

    private fun pending(readyAtMs: Long, remainingMin: Int) =
        MealAnalytics.Pending(readyAtMs = readyAtMs, remainingMin = remainingMin)

    /** meal + window*60, in milliseconds (a Long, as Pending carries it). */
    private val readyAtMs = ((meal + window * 60) * 1000).toLong()

    @Test
    fun noSamplesAtAllIsNoData() {
        assertEquals(MealAnalytics.NoData,
            analyticsState(null, hasSamples = false, hasWindowSamples = false, meal, mid))
        assertEquals(MealAnalytics.NoData,
            analyticsState(null, hasSamples = false, hasWindowSamples = false, meal, closed))
    }

    @Test
    fun openWindowIsPendingEvenWithNoWindowSamplesYet() {
        // Readings are still arriving for an open window: not "no data".
        assertEquals(
            pending(readyAtMs, (window - 30).toInt()),
            analyticsState(null, hasSamples = true, hasWindowSamples = false, meal, mid))
        // Window samples present but the window is still open → pending too.
        assertEquals(
            pending(readyAtMs, (window - 30).toInt()),
            analyticsState(null, hasSamples = true, hasWindowSamples = true, meal, mid))
    }

    @Test
    fun closedWindowWithDataIsSettled() {
        assertEquals(MealAnalytics.Settled,
            analyticsState(null, hasSamples = true, hasWindowSamples = true, meal, closed))
    }

    @Test
    fun closedWindowWithNoWindowSamplesIsNoWindowData() {
        // The past-meal case: a series exists (hasSamples) but none of it
        // falls inside [meal-30, meal+180] — the analysis never ran and
        // never will. Terminal, and distinct from Settled.
        assertEquals(MealAnalytics.NoWindowData,
            analyticsState(null, hasSamples = true, hasWindowSamples = false, meal, closed))
    }

    @Test
    fun insightPresentAlwaysReady() {
        // Even with an "impossible" sample situation, a stored insight wins.
        // (MealInsight construction needs the full numeric shape; use a
        // minimal spike via the core's public constructor.)
        val insight = com.dsikic.cluecose.core.food.MealInsight(
            kind = com.dsikic.cluecose.core.food.MealInsight.Kind.SPIKE,
            baselineMgdl = 90.0,
            peakMgdl = 150.0,
            peakDeltaMgdl = 60.0,
            timeToPeakMin = 55.0,
            minMgdl = 88.0,
            minDeltaMgdl = -2.0,
            text = "x",
        )
        assertEquals(MealAnalytics.Ready,
            analyticsState(insight, hasSamples = false, hasWindowSamples = false, meal, closed))
    }

    @Test
    fun pendingRemainingMinutesRoundsUp() {
        // 179 min into the window → 1 min left → remaining 1.
        val near = meal + (window * 60 - 60)
        val p = analyticsState(null, hasSamples = true, hasWindowSamples = true, meal, near)
        assertEquals(MealAnalytics.Pending(readyAtMs, 1), p)
    }
}
