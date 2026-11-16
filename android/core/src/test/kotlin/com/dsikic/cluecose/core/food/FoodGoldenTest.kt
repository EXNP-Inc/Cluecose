//
//  FoodGoldenTest.kt
//  CluecoseCore (Android `:core`)
//
//  Golden tests for the food feature's shared layer. These run the SAME fixture
//  files that the Swift `CluecoseCoreTests/FoodTests.swift` runs — the
//  mechanism that keeps the Swift and Kotlin ports behaviorally identical (the
//  same golden-vector idiom the repo already uses for the Libre 3 crypto data
//  plane). The fixtures are placed on the test classpath by the `sourceSets`
//  block in core/build.gradle.kts, which points the test resources at the
//  shared Swift package's `Resources` directory (so `food/prompt.txt` and
//  `food/tests/*.json` are available as classpath resources).
//
//  Comparison is field-by-field with a small numeric tolerance (not raw JSON
//  string equality), so it is robust to key ordering and float representation
//  on both platforms.
//

package com.dsikic.cluecose.core.food

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import java.io.File

// MARK: - Fixture loading (mirrors Swift `fixtureFiles` / `loadObject`)

/**
 * Classpath access for the shared fixtures. Kept in an `object` so that
 * `javaClass.classLoader` resolves (a top-level function has no `this`).
 */
internal object FixtureLoader {
    private const val FIXTURES_DIR = "food/tests"

    /** List the fixture file names matching [prefix] (e.g. `parse-`), sorted. */
    fun fixtureFiles(prefix: String): List<String> {
        val names = (javaClass.classLoader.getResources(FIXTURES_DIR) as java.util.Enumeration<java.net.URL>)
            .asSequence()
            .mapNotNull { base ->
                val dir = File(base.toURI())
                if (dir.isDirectory) dir.listFiles { _, n -> n.startsWith(prefix) && n.endsWith(".json") }?.map { it.name } else null
            }
            .flatten()
            .toList()
            .distinct()
            .sorted()
        check(names.isNotEmpty()) { "no $prefix fixtures found on the test classpath under $FIXTURES_DIR" }
        return names
    }

    /** Load one fixture file (by classpath name) into a [FoodFixture]. */
    fun loadFixture(fileName: String): FoodFixture {
        val raw = javaClass.classLoader.getResourceAsStream("$FIXTURES_DIR/$fileName")
            ?.reader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("fixture not found: $fileName")
        val obj = Json.parseToJsonElement(raw) as JsonObject
        val name = obj["name"]?.let { if (it is JsonPrimitive && it.isString) it.content else null } ?: fileName
        return FoodFixture(name, obj)
    }

    /** The bundled `food/prompt.txt` template (null when absent). */
    fun templateText(): String? =
        javaClass.classLoader.getResourceAsStream("food/prompt.txt")
            ?.reader(Charsets.UTF_8)?.use { it.readText() }
}

/**
 * A parsed food fixture: the raw JSON object plus its top-level [name].
 */
internal data class FoodFixture(val name: String, val obj: JsonObject) {
    /** A top-level double, or null when the key is absent or null. */
    fun d(key: String): Double? = optNum(obj[key])
    fun s(key: String): String? =
        obj[key]?.let { if (it is JsonPrimitive && it.isString) it.content else null }
    /** The nested `expect` object, or null when absent / JSON null. */
    val expect: JsonObject?
        get() = obj["expect"]?.let { if (it is JsonNull) null else it as? JsonObject }
}

/** A JSON element → Double, treating absent/null as null (Swift NSNumber path). */
internal fun optNum(v: JsonElement?): Double? =
    v?.let {
        if (it is JsonNull) null
        else it.jsonPrimitive.let { p -> p.doubleOrNull ?: p.longOrNull?.toDouble() }
    }

/**
 * Compare an optional number against a fixture value. A JSON `null` in a
 * fixture means "expect nil"; an absent key also means nil. (Swift
 * `assertClose`.)
 */
internal fun assertClose(got: Double?, want: JsonElement?, ctx: String) {
    when {
        want == null || want is JsonNull -> check(got == null) { "$ctx: expected null, got $got" }
        else -> {
            val w = optNum(want) ?: error("$ctx: expected a number, got $want")
            check(got != null) { "$ctx: expected $w, got null" }
            check(kotlin.math.abs(got!! - w) <= 1e-6) { "$ctx: got $got, want $w (tolerance 1e-6)" }
        }
    }
}

internal fun checkClose(got: Double?, want: Double) {
    check(got != null) { "got null, want $want" }
    check(kotlin.math.abs(got!! - want) <= 1e-6) { "got $got, want $want" }
}

// MARK: - Prompt building

class NutritionPromptBuildTests {

    @Test
    fun buildPromptSubstitutesManual() {
        val template = "Focus: {{focus}}\nFood: {{description}}\nServing: {{serving}}\nContext: {{context}}"
        val req = NutritionPrompt.Request(description = "  two eggs  ", serving = null, context = null, mode = NutritionPrompt.Mode.MANUAL)
        val out = NutritionPrompt.buildPrompt(template, req)
        check(out == "Focus: Estimate from the description below.\nFood: two eggs\nServing: not specified\nContext: not specified") { "got: $out" }
    }

    @Test
    fun buildPromptSubstitutesPhotoAndServing() {
        val template = "{{focus}} | {{description}} | {{serving}}"
        val req = NutritionPrompt.Request(description = "a plate", serving = "1 bowl", context = null, mode = NutritionPrompt.Mode.PHOTO, photoCount = 3)
        val out = NutritionPrompt.buildPrompt(template, req)
        check(out == "Analyze the attached 3 photos of this meal (use all of them). | a plate | 1 bowl") { "got: $out" }
    }

    @Test
    fun buildPromptSinglePhoto() {
        val template = "{{focus}}"
        val req = NutritionPrompt.Request(description = "x", mode = NutritionPrompt.Mode.PHOTO, photoCount = 1)
        val out = NutritionPrompt.buildPrompt(template, req)
        check(out == "Analyze the attached 1 photo of this meal (use all of them).") { "got: $out" }
    }

    @Test
    fun bundledTemplateLoadsAndRenders() {
        val template = FixtureLoader.templateText() ?: error("prompt.txt not found on the test classpath")
        val req = NutritionPrompt.Request(description = "a latte", serving = "1 cup", context = "decaf, oat milk", mode = NutritionPrompt.Mode.MANUAL)
        val prompt = NutritionPrompt.buildPrompt(template, req)
        check(prompt.contains("a latte")) { "template did not render description" }
        check(prompt.contains("1 cup")) { "template did not render serving" }
        check(prompt.contains("decaf, oat milk")) { "template did not render context" }
        for (key in listOf("calories_kcal", "caffeine_mg", "alcohol_g", "saturated_fat_g", "citations")) {
            check(prompt.contains(key)) { "template missing key: $key" }
        }
    }
}

// MARK: - Response parsing (golden, field-by-field)

class NutritionPromptParseTests {

    @Test
    fun parseGoldenFixtures() {
        for (fileName in FixtureLoader.fixtureFiles("parse-")) {
            val fx = FixtureLoader.loadFixture(fileName)
            val modelRaw = fx.obj["raw"]?.let { checkNotNull(it.jsonPrimitive.content) { "${fx.name}: missing raw" } }
                ?: error("${fx.name}: missing raw")
            val got = NutritionPrompt.parseResponse(modelRaw)
            val expect = fx.expect ?: error("${fx.name}: missing expect")
            if (expect.isEmpty()) {
                check(got == null) { "${fx.name}: expected no estimate, got $got" }
            } else {
                checkNotNull(got) { "${fx.name}: parse returned null for $modelRaw" }
                val ctx = "${fx.name}:"
                assertClose(got.caloriesKcal, expect["caloriesKcal"], ctx + " caloriesKcal")
                assertClose(got.proteinG, expect["proteinG"], ctx + " proteinG")
                assertClose(got.carbsG, expect["carbsG"], ctx + " carbsG")
                assertClose(got.fibreG, expect["fibreG"], ctx + " fibreG")
                assertClose(got.fatG, expect["fatG"], ctx + " fatG")
                assertClose(got.saturatedFatG, expect["saturatedFatG"], ctx + " saturatedFatG")
                assertClose(got.sodiumMg, expect["sodiumMg"], ctx + " sodiumMg")
                assertClose(got.caffeineMg, expect["caffeineMg"], ctx + " caffeineMg")
                assertClose(got.alcoholG, expect["alcoholG"], ctx + " alcoholG")
                val wantDesc = expect["description"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
                check(got.description == wantDesc) { "$ctx description: got ${got.description}, want $wantDesc" }
                val wantCount = expect["citationsCount"]?.let { if (it is JsonNull) null else it.jsonPrimitive.long }
                if (wantCount != null) {
                    check(got.citations.size.toLong() == wantCount) { "$ctx citationsCount: got ${got.citations.size}, want $wantCount" }
                }
            }
        }
    }

    @Test
    fun parseCitationsKeepsTitleAndUrl() {
        val raw = "{\"calories_kcal\": 100, \"citations\": [{\"title\": \"A\", \"url\": \"https://a.example\"}, {\"url\": \"\"}]}"
        val g = NutritionPrompt.parseResponse(raw)
        checkNotNull(g)
        check(g.citations.size == 1) { "got ${g.citations.size} citations, want 1" }
        check(g.citations.first().title == "A")
        check(g.citations.first().url == "https://a.example")
    }

    @Test
    fun parseGarbageReturnsNil() {
        check(NutritionPrompt.parseResponse("no json here at all") == null)
        check(NutritionPrompt.parseResponse("{ not valid") == null)
    }
}

// MARK: - Insight engine (golden, field-by-field)

class MealInsightTests {

    @Test
    fun insightGoldenFixtures() {
        for (fileName in FixtureLoader.fixtureFiles("insight-")) {
            val fx = FixtureLoader.loadFixture(fileName)
            val cfg = InsightConfig(
                baselineWindowMin = fx.d("baselineWindowMin") ?: 30.0,
                postWindowMin = fx.d("postWindowMin") ?: 180.0,
                spikeThresholdMgdl = fx.d("spikeThresholdMgdl") ?: 30.0,
                lowThresholdMgdl = fx.d("lowThresholdMgdl") ?: 30.0,
                baselineBandMgdl = fx.d("baselineBandMgdl") ?: 10.0,
            )
            val meal = fx.d("mealEpochSeconds") ?: 0.0
            val nowMin = fx.d("nowMin") ?: 240.0
            val samplesArr = (fx.obj["samples"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
            val samples = samplesArr.map { s ->
                GlucoseSample(
                    epochSeconds = meal + (optNum(s["min"]) ?: 0.0) * 60,
                    mgdl = optNum(s["mgdl"]) ?: 0.0,
                )
            }
            val input = InsightInput(
                mealDateEpochSeconds = meal,
                carbsG = fx.d("carbsG"),
                description = fx.s("description"),
                samples = samples,
                nowEpochSeconds = meal + nowMin * 60,
            )

            val got = MealInsightEngine.insight(input, cfg)
            val expect = fx.expect
            if (expect == null) {
                check(got == null) { "${fx.name}: expected no insight, got ${got?.kind}" }
            } else {
                checkNotNull(got) { "${fx.name}: expected an insight but engine returned null" }
                val ctx = "${fx.name}:"
                check(got.kind.rawValue == (expect["kind"]?.jsonPrimitive?.content)) { "$ctx kind: got ${got.kind.rawValue}, want ${expect["kind"]}" }
                assertClose(got.baselineMgdl, expect["baselineMgdl"], ctx + " baselineMgdl")
                assertClose(got.peakMgdl, expect["peakMgdl"], ctx + " peakMgdl")
                assertClose(got.peakDeltaMgdl, expect["peakDeltaMgdl"], ctx + " peakDeltaMgdl")
                assertClose(got.timeToPeakMin, expect["timeToPeakMin"], ctx + " timeToPeakMin")
                assertClose(got.returnToBaselineMin, expect["returnToBaselineMin"], ctx + " returnToBaselineMin")
                assertClose(got.minMgdl, expect["minMgdl"], ctx + " minMgdl")
                assertClose(got.minDeltaMgdl, expect["minDeltaMgdl"], ctx + " minDeltaMgdl")
                assertClose(got.per10gCarbDeltaMgdl, expect["per10gCarbDeltaMgdl"], ctx + " per10gCarbDeltaMgdl")
                check(got.text.isNotEmpty()) { "$ctx text should be non-empty" }
            }
        }
    }

    @Test
    fun crossMealComparisonFoldedIntoText() {
        val meal = 1_730_000_000.0
        val samples = listOf(
            GlucoseSample(meal - 30 * 60, 95.0),
            GlucoseSample(meal, 95.0),
            GlucoseSample(meal + 40 * 60, 152.0),
            GlucoseSample(meal + 120 * 60, 100.0),
        )
        val input = InsightInput(mealDateEpochSeconds = meal, carbsG = 48.0, description = "bagel", samples = samples, nowEpochSeconds = meal + 240 * 60)
        val withCm = MealInsightEngine.insight(input, crossMeal = CrossMealComparison(instanceCount = 3, averagePeakDeltaMgdl = 50.0))
        checkNotNull(withCm)
        check(withCm.text.contains("Across 3 similar meals your average rise is about 50 mg/dL.")) { "text missing cross-meal line: ${withCm.text}" }
        val without = MealInsightEngine.insight(input)
        check(without != null)
        check(!without.text.contains("similar meals"))
        val one = MealInsightEngine.insight(input, crossMeal = CrossMealComparison(instanceCount = 1, averagePeakDeltaMgdl = 50.0))
        check(one != null)
        check(!one.text.contains("similar meals")) { "1 instance should not fold into text: ${one.text}" }
    }
}

// MARK: - Patterns engine (golden, field-by-field)

class PatternsEngineTests {

    @Test
    fun patternsGoldenFixtures() {
        for (fileName in FixtureLoader.fixtureFiles("patterns-")) {
            val fx = FixtureLoader.loadFixture(fileName)
            val expect = fx.expect ?: error("${fx.name}: missing expect")
            val now = fx.d("nowEpochSeconds") ?: error("${fx.name}: missing nowEpochSeconds")
            val inDays = optNum(fx.obj["inDays"])?.toInt() ?: 7
            val entries = (fx.obj["entries"] as? JsonArray)?.map { it.jsonObject }?.mapNotNull { e ->
                val date = optNum(e["dateEpochSeconds"])
                if (date == null) null
                else FoodEntry(
                    id = "fixture",
                    dateEpochSeconds = date,
                    description = e["description"]?.let { if (it is JsonPrimitive && it.isString) it.content else null } ?: "",
                    caloriesKcal = optNum(e["caloriesKcal"]),
                    proteinG = optNum(e["proteinG"]),
                    carbsG = optNum(e["carbsG"]),
                    fatG = optNum(e["fatG"]),
                    sodiumMg = optNum(e["sodiumMg"]),
                    caffeineMg = optNum(e["caffeineMg"]),
                    alcoholG = optNum(e["alcoholG"]),
                    saturatedFatG = optNum(e["saturatedFatG"]),
                )
            } ?: emptyList()

            val p = PatternsEngine.patterns(entries, inDays, now)
            val ctx = "${fx.name}:"
            val wantCount = optNum(expect["entryCount"])?.toInt() ?: 0
            check(p.entryCount == wantCount) { "$ctx entryCount: got ${p.entryCount}, want $wantCount" }
            val wantDays = optNum(expect["distinctDays"])?.toInt() ?: 0
            check(p.distinctDays == wantDays) { "$ctx distinctDays: got ${p.distinctDays}, want $wantDays" }
            assertClose(p.avgKcalPerDay, expect["avgKcalPerDay"], ctx + " avgKcalPerDay")
            assertClose(p.avgProteinPerDay, expect["avgProteinPerDay"], ctx + " avgProteinPerDay")
            assertClose(p.avgCarbsPerDay, expect["avgCarbsPerDay"], ctx + " avgCarbsPerDay")
            assertClose(p.avgFatPerDay, expect["avgFatPerDay"], ctx + " avgFatPerDay")
            assertClose(p.avgSodiumPerDay, expect["avgSodiumPerDay"], ctx + " avgSodiumPerDay")
            assertClose(p.avgCaffeinePerDay, expect["avgCaffeinePerDay"], ctx + " avgCaffeinePerDay")
            assertClose(p.avgAlcoholPerDay, expect["avgAlcoholPerDay"], ctx + " avgAlcoholPerDay")
            assertClose(p.avgSaturatedFatPerDay, expect["avgSaturatedFatPerDay"], ctx + " avgSaturatedFatPerDay")
            assertClose(p.saturatedFatToFatRatio, expect["saturatedFatToFatRatio"], ctx + " saturatedFatToFatRatio")
        }
    }
}

// MARK: - Daily totals (nil-aware)

class FoodTotalsTests {

    @Test
    fun sumSkipsNilComponents() {
        val a = FoodEntry(id = "a", dateEpochSeconds = 100.0, description = "a", caloriesKcal = 100.0, carbsG = 20.0, sodiumMg = 100.0, alcoholG = 10.0)
        val b = FoodEntry(id = "b", dateEpochSeconds = 200.0, description = "b", caloriesKcal = 50.0, proteinG = 10.0, saturatedFatG = 3.0)
        val t = FoodTotals.sum(listOf(a, b))
        check(t.entryCount == 2) { "entryCount" }
        checkClose(t.kcal, 150.0)
        checkClose(t.carbsG, 20.0)
        checkClose(t.proteinG, 10.0)
        checkClose(t.sodiumMg, 100.0)
        checkClose(t.alcoholG, 10.0)
        checkClose(t.saturatedFatG, 3.0)
        check(t.fatG == null) { "fatG should be nil" }
        check(t.caffeineMg == null) { "caffeineMg should be nil" }
    }

    @Test
    fun sumEmptyIsAllNil() {
        val t = FoodTotals.sum(emptyList())
        check(t.entryCount == 0)
        check(t.kcal == null && t.carbsG == null && t.caffeineMg == null)
        check(t.alcoholG == null && t.saturatedFatG == null)
    }
}

// MARK: - Coaching questions (presentation, thresholds)

class CoachingQuestionsTest {

    @Test
    fun thresholdsAndOrder() {
        val p = NutritionPatterns(
            entryCount = 3,
            distinctDays = 3,
            avgCaffeinePerDay = 350.0,      // >= 300 → question
            avgProteinPerDay = 30.0,        // 0 < 30 < 40 → question
            saturatedFatToFatRatio = 0.45,  // > 0.4 → question
            avgSodiumPerDay = 2200.0,       // > 2000 → question
            avgAlcoholPerDay = 35.0,        // > 30 → question
        )
        val q = PatternsEngine.coachingQuestions(p)
        check(q.size == 5) { "expected 5 questions, got ${q.size}: $q" }
        check(q[0].contains("350 mg")) { "caffeine question: ${q[0]}" }
        check(q[1].contains("30 g")) { "protein question: ${q[1]}" }
        check(q[2].contains("45%")) { "sat-fat question: ${q[2]}" }
        check(q[3].contains("2200 mg")) { "sodium question: ${q[3]}" }
        check(q[4].contains("35 g")) { "alcohol question: ${q[4]}" }

        check(PatternsEngine.coachingQuestions(NutritionPatterns()).isEmpty()) { "empty patterns should yield no questions" }
    }
}

// MARK: - FoodEntry Codable round-trip (cross-platform schema sanity)

class FoodEntryCodableTests {

    @Test
    fun roundTripPreservesAllFields() {
        val entry = FoodEntry(
            id = "rt",
            dateEpochSeconds = 1_700_000_000.0,
            description = "chicken burrito",
            serving = "1",
            context = "extra cheese, no salsa",
            source = FoodSource.PHOTO,
            photoFileNames = listOf("a.jpg", "b.jpg"),
            portionMultiplier = 1.5,
            caloriesKcal = 650.0, proteinG = 35.0, carbsG = 50.0, fatG = 30.0,
            sodiumMg = 1100.0, caffeineMg = null, alcoholG = null, saturatedFatG = 12.0,
            citations = listOf(SourceCitation(title = "Burrito ref", url = "https://ref.example/burrito")),
            isEstimated = true,
        )
        val json = FoodJson.instance.encodeToString(FoodEntrySerializer, entry)
        val back = FoodJson.instance.decodeFromString(FoodEntrySerializer, json)

        check(back.description == "chicken burrito")
        check(back.serving == "1")
        check(back.context == "extra cheese, no salsa")
        check(back.source == FoodSource.PHOTO)
        check(back.photoFileNames == listOf("a.jpg", "b.jpg"))
        checkClose(back.portionMultiplier, 1.5)
        checkClose(back.caloriesKcal, 650.0)
        checkClose(back.proteinG, 35.0)
        checkClose(back.carbsG, 50.0)
        checkClose(back.fatG, 30.0)
        checkClose(back.sodiumMg, 1100.0)
        check(back.caffeineMg == null)
        check(back.alcoholG == null)
        checkClose(back.saturatedFatG, 12.0)
        val cits = back.citations
        check(cits != null && cits.size == 1)
        check(cits!!.first().url == "https://ref.example/burrito")
        check(back.isEstimated)

        // The key is always encoded (even for the 1.0 default) so the schema
        // is stable on both platforms.
        val obj = FoodJson.instance.parseToJsonElement(json).jsonObject
        checkClose(obj["portionMultiplier"]?.jsonPrimitive?.let { it.doubleOrNull ?: it.longOrNull?.toDouble() }, 1.5)
    }

    @Test
    fun portionMultiplierDefaultsToOneWhenAbsent() {
        // A record written before the multiplier existed must decode with a
        // full portion (1.0), and re-encoding must now carry the key.
        val legacy = """
            {"id":"y","dateEpochSeconds":123,"description":"legacy meal","source":"manual","caloriesKcal":300}
        """.trimIndent()
        val e = FoodJson.instance.decodeFromString(FoodEntrySerializer, legacy)
        checkClose(e.portionMultiplier, 1.0)
        val re = FoodJson.instance.encodeToString(FoodEntrySerializer, e)
        val obj = FoodJson.instance.parseToJsonElement(re).jsonObject
        checkClose(obj["portionMultiplier"]?.jsonPrimitive?.let { it.doubleOrNull ?: it.longOrNull?.toDouble() }, 1.0)
    }

    @Test
    fun decodeToleratesMissingNewFields() {
        // A record written by an older version: no context/citations/
        // saturatedFatG/alcoholG, and no photoFileNames. Must still decode.
        val legacy = """
            {"id":"x","dateEpochSeconds":123,"description":"old","source":"manual","isEstimated":true}
        """.trimIndent()
        val e = FoodJson.instance.decodeFromString(FoodEntrySerializer, legacy)
        check(e.description == "old")
        check(e.context == null)
        check(e.photoFileNames == emptyList<String>())
        check(e.saturatedFatG == null)
        check(e.alcoholG == null)
        check(e.isEstimated)
    }
}
