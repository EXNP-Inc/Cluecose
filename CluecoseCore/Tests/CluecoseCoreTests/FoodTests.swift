//
//  CluecoseCoreTests
//
//  Golden tests for the food feature's shared layer. These run the SAME fixture
//  files that the Android `:core` tests run — the mechanism that keeps the
//  Swift and Kotlin ports behaviorally identical (the same golden-vector idiom
//  the repo already uses for the Libre 3 crypto data plane).
//
//  Comparison is field-by-field with a small numeric tolerance (not raw JSON
//  string equality), so it is robust to key ordering and float representation
//  on both platforms.
//
//  Fixtures live in the CluecoseCore target's resources at
//  `Resources/food/tests/*.json` and are read via `CluecoseCoreResources.bundle`.
//

import XCTest
@testable import CluecoseCore

// MARK: - Fixture loading

private enum FixtureError: LocalizedError {
    case missing
    var errorDescription: String? { "food/tests fixture directory not found" }
}

private func fixtureFiles(_ prefix: String) throws -> [URL] {
    guard let base = CluecoseCoreResources.bundle.url(forResource: "tests", withExtension: nil, subdirectory: "food") else {
        throw FixtureError.missing
    }
    let all = (try? FileManager.default.contentsOfDirectory(at: base, includingPropertiesForKeys: nil)) ?? []
    let files = all.filter { $0.lastPathComponent.hasPrefix(prefix) && $0.pathExtension == "json" }
        .sorted { $0.lastPathComponent < $1.lastPathComponent }
    guard !files.isEmpty else { throw FixtureError.missing }
    return files
}

private func loadObject(_ url: URL) throws -> [String: Any] {
    let raw = try String(contentsOf: url, encoding: .utf8)
    guard let d = raw.data(using: .utf8),
          let obj = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else {
        throw FixtureError.missing
    }
    return obj
}

/// Compare an optional number against a fixture value. A JSON `null` in a
/// fixture decodes to NSNull via JSONSerialization (not Swift nil), so the
/// two are normalized together: "expect null" / absent key both mean nil.
private func assertClose(_ got: Double?, _ want: Any?, _ ctx: String) {
    if want is NSNull {
        XCTAssertNil(got, ctx)
        return
    }
    guard let want else {
        XCTAssertNil(got, ctx)
        return
    }
    guard let w = (want as? NSNumber)?.doubleValue else {
        return XCTFail("\(ctx): expected number, got \(String(describing: want))")
    }
    let g = got ?? 0
    XCTAssertEqual(g, w, accuracy: 1e-6, "\(ctx): got \(g), want \(w)")
}

// MARK: - Prompt building

final class NutritionPromptBuildTests: XCTestCase {

    func testBuildPromptSubstitutesManual() {
        let template = "Focus: {{focus}}\nFood: {{description}}\nServing: {{serving}}\nContext: {{context}}"
        let req = NutritionPrompt.Request(description: "  two eggs  ", serving: nil, context: nil, mode: .manual)
        let out = NutritionPrompt.buildPrompt(template: template, request: req)
        XCTAssertEqual(out, "Focus: Estimate from the description below.\nFood: two eggs\nServing: not specified\nContext: not specified")
    }

    func testBuildPromptSubstitutesPhotoAndServing() {
        let template = "{{focus}} | {{description}} | {{serving}}"
        let req = NutritionPrompt.Request(description: "a plate", serving: "1 bowl", context: nil, mode: .photo, photoCount: 3)
        let out = NutritionPrompt.buildPrompt(template: template, request: req)
        XCTAssertEqual(out, "Analyze the attached 3 photos of this meal (use all of them). | a plate | 1 bowl")
    }

    func testBuildPromptSinglePhoto() {
        let template = "{{focus}}"
        let req = NutritionPrompt.Request(description: "x", mode: .photo, photoCount: 1)
        XCTAssertEqual(NutritionPrompt.buildPrompt(template: template, request: req),
                       "Analyze the attached 1 photo of this meal (use all of them).")
    }

    func testBundledTemplateLoadsAndRenders() throws {
        let req = NutritionPrompt.Request(description: "a latte", serving: "1 cup", context: "decaf, oat milk", mode: .manual)
        let prompt = try XCTUnwrap(NutritionPrompt.buildPrompt(request: req))
        XCTAssertTrue(prompt.contains("a latte"))
        XCTAssertTrue(prompt.contains("1 cup"))
        XCTAssertTrue(prompt.contains("decaf, oat milk"))
        XCTAssertTrue(prompt.contains("calories_kcal"))
        XCTAssertTrue(prompt.contains("caffeine_mg"))
        XCTAssertTrue(prompt.contains("alcohol_g"))
        XCTAssertTrue(prompt.contains("saturated_fat_g"))
        XCTAssertTrue(prompt.contains("citations"))
    }
}

// MARK: - Response parsing (golden, field-by-field)

final class NutritionPromptParseTests: XCTestCase {

    func testParseGoldenFixtures() throws {
        for url in try fixtureFiles("parse-") {
            let obj = try loadObject(url)
            let name = (obj["name"] as? String) ?? url.lastPathComponent
            let modelRaw = try XCTUnwrap(obj["raw"] as? String, "\(name): missing raw")
            let expect = try XCTUnwrap(obj["expect"] as? [String: Any], "\(name): missing expect")

            let got = NutritionPrompt.parseResponse(modelRaw)
            if expect.isEmpty {
                XCTAssertNil(got, "\(name): expected no estimate")
                continue
            }
            let g = try XCTUnwrap(got, "\(name): parse returned nil for \(modelRaw)")
            let ctx = "\(name):"
            assertClose(g.caloriesKcal, expect["caloriesKcal"], ctx + " caloriesKcal")
            assertClose(g.proteinG, expect["proteinG"], ctx + " proteinG")
            assertClose(g.carbsG, expect["carbsG"], ctx + " carbsG")
            assertClose(g.fatG, expect["fatG"], ctx + " fatG")
            assertClose(g.saturatedFatG, expect["saturatedFatG"], ctx + " saturatedFatG")
            assertClose(g.sodiumMg, expect["sodiumMg"], ctx + " sodiumMg")
            assertClose(g.caffeineMg, expect["caffeineMg"], ctx + " caffeineMg")
            assertClose(g.alcoholG, expect["alcoholG"], ctx + " alcoholG")
            if let want = expect["description"] as? String {
                XCTAssertEqual(g.description, want, "\(ctx) description")
            } else {
                XCTAssertNil(g.description, "\(ctx) description should be nil")
            }
            if let wantCount = (expect["citationsCount"] as? NSNumber)?.intValue {
                XCTAssertEqual(g.citations.count, wantCount, "\(ctx) citationsCount")
            }
        }
    }

    func testParseCitationsKeepsTitleAndUrl() {
        let raw = "{\"calories_kcal\": 100, \"citations\": [{\"title\": \"A\", \"url\": \"https://a.example\"}, {\"url\": \"\"}]}"
        let g = NutritionPrompt.parseResponse(raw)
        XCTAssertEqual(g?.citations.count, 1)
        XCTAssertEqual(g?.citations.first?.title, "A")
        XCTAssertEqual(g?.citations.first?.url, "https://a.example")
    }

    func testParseGarbageReturnsNil() {
        XCTAssertNil(NutritionPrompt.parseResponse("no json here at all"))
        XCTAssertNil(NutritionPrompt.parseResponse("{ not valid"))
    }
}

// MARK: - Insight engine (golden, field-by-field)

final class MealInsightTests: XCTestCase {

    func testInsightGoldenFixtures() throws {
        for url in try fixtureFiles("insight-") {
            let obj = try loadObject(url)
            let name = (obj["name"] as? String) ?? url.lastPathComponent
            let cfg = config(from: obj)
            let input = input(from: obj)

            let got = MealInsightEngine.insight(for: input, config: cfg)
            let expect = obj["expect"] as? [String: Any]
            if expect == nil {
                XCTAssertNil(got, "\(name): expected no insight, got \(String(describing: got?.kind))")
                continue
            }
            let g = try XCTUnwrap(got, "\(name): expected an insight but engine returned nil")
            let ctx = "\(name):"
            XCTAssertEqual(g.kind.rawValue, expect?["kind"] as? String, ctx + " kind")
            assertClose(g.baselineMgdl, expect?["baselineMgdl"], ctx + " baselineMgdl")
            assertClose(g.peakMgdl, expect?["peakMgdl"], ctx + " peakMgdl")
            assertClose(g.peakDeltaMgdl, expect?["peakDeltaMgdl"], ctx + " peakDeltaMgdl")
            assertClose(g.timeToPeakMin, expect?["timeToPeakMin"], ctx + " timeToPeakMin")
            assertClose(g.returnToBaselineMin, expect?["returnToBaselineMin"], ctx + " returnToBaselineMin")
            assertClose(g.minMgdl, expect?["minMgdl"], ctx + " minMgdl")
            assertClose(g.minDeltaMgdl, expect?["minDeltaMgdl"], ctx + " minDeltaMgdl")
            assertClose(g.per10gCarbDeltaMgdl, expect?["per10gCarbDeltaMgdl"], ctx + " per10gCarbDeltaMgdl")
            // text is a presentation field (not in the parity contract) — just
            // assert it's non-empty when an insight fired.
            XCTAssertFalse(g.text.isEmpty, "\(ctx) text should be non-empty")
        }
    }

    private func config(from o: [String: Any]) -> MealInsightEngine.Config {
        MealInsightEngine.Config(
            baselineWindowMin: num(o["baselineWindowMin"]) ?? 30,
            postWindowMin: num(o["postWindowMin"]) ?? 180,
            spikeThresholdMgdl: num(o["spikeThresholdMgdl"]) ?? 30,
            lowThresholdMgdl: num(o["lowThresholdMgdl"]) ?? 30,
            baselineBandMgdl: num(o["baselineBandMgdl"]) ?? 10)
    }

    private func input(from o: [String: Any]) -> MealInsightEngine.Input {
        let meal = (o["mealEpochSeconds"] as? Double) ?? 0
        let nowMin = (o["nowMin"] as? Double) ?? 240
        let now = meal + nowMin * 60
        let samplesArr = (o["samples"] as? [[String: Any]]) ?? []
        let samples = samplesArr.map { s in
            MealInsightEngine.Sample(
                epochSeconds: meal + (s["min"] as! Double) * 60,
                mgdl: s["mgdl"] as! Double)
        }
        return MealInsightEngine.Input(mealDateEpochSeconds: meal,
                                       carbsG: o["carbsG"] as? Double,
                                       description: o["description"] as? String,
                                       samples: samples,
                                       nowEpochSeconds: now)
    }

    private func num(_ v: Any?) -> Double? { (v as? NSNumber)?.doubleValue }
}

// MARK: - Patterns engine (golden, field-by-field)

final class PatternsEngineTests: XCTestCase {

    func testPatternsGoldenFixtures() throws {
        for url in try fixtureFiles("patterns-") {
            let obj = try loadObject(url)
            let name = (obj["name"] as? String) ?? url.lastPathComponent
            let expect = try XCTUnwrap(obj["expect"] as? [String: Any], "\(name): missing expect")
            let now = try XCTUnwrap(num(obj["nowEpochSeconds"]), "\(name): missing nowEpochSeconds")
            let inDays = (obj["inDays"] as? NSNumber)?.intValue ?? 7
            let entries = (obj["entries"] as? [[String: Any]])?.compactMap { e in
                FoodEntry(
                    dateEpochSeconds: (e["dateEpochSeconds"] as? Double) ?? 0,
                    description: e["description"] as? String ?? "",
                    caloriesKcal: optNum(e["caloriesKcal"]),
                    proteinG: optNum(e["proteinG"]),
                    carbsG: optNum(e["carbsG"]),
                    fatG: optNum(e["fatG"]),
                    sodiumMg: optNum(e["sodiumMg"]),
                    caffeineMg: optNum(e["caffeineMg"]),
                    alcoholG: optNum(e["alcoholG"]),
                    saturatedFatG: optNum(e["saturatedFatG"]))
            } ?? []

            let p = PatternsEngine.patterns(entries, inDays: inDays, nowEpochSeconds: now)
            let ctx = "\(name):"
            XCTAssertEqual(p.entryCount, (expect["entryCount"] as? NSNumber)?.intValue ?? 0, ctx + " entryCount")
            XCTAssertEqual(p.distinctDays, (expect["distinctDays"] as? NSNumber)?.intValue ?? 0, ctx + " distinctDays")
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

    private func num(_ v: Any?) -> Double? { (v as? NSNumber)?.doubleValue }
    private func optNum(_ v: Any?) -> Double? { v is NSNull ? nil : (v as? NSNumber)?.doubleValue }
}

// MARK: - Daily totals (nil-aware)

final class FoodTotalsTests: XCTestCase {
    func testSumSkipsNilComponents() {
        let a = FoodEntry(dateEpochSeconds: 100, description: "a",
                          caloriesKcal: 100, carbsG: 20, sodiumMg: 100, alcoholG: 10)
        let b = FoodEntry(dateEpochSeconds: 200, description: "b",
                          caloriesKcal: 50, proteinG: 10, saturatedFatG: 3) // no carbs/sodium/alcohol
        let t = FoodTotals.sum([a, b])
        XCTAssertEqual(t.entryCount, 2)
        XCTAssertEqual(t.kcal, 150)
        XCTAssertEqual(t.carbsG, 20)
        XCTAssertEqual(t.proteinG, 10)
        XCTAssertEqual(t.sodiumMg, 100)
        XCTAssertEqual(t.alcoholG, 10)
        XCTAssertEqual(t.saturatedFatG, 3)
        XCTAssertNil(t.fatG)
        XCTAssertNil(t.caffeineMg)
    }

    func testSumEmptyIsAllNil() {
        let t = FoodTotals.sum([])
        XCTAssertEqual(t.entryCount, 0)
        XCTAssertNil(t.kcal); XCTAssertNil(t.carbsG); XCTAssertNil(t.caffeineMg)
        XCTAssertNil(t.alcoholG); XCTAssertNil(t.saturatedFatG)
    }
}

// MARK: - FoodEntry Codable round-trip (cross-platform schema sanity)

final class FoodEntryCodableTests: XCTestCase {
    func testRoundTripPreservesAllFields() throws {
        let entry = FoodEntry(
            dateEpochSeconds: 1_700_000_000,
            description: "chicken burrito",
            serving: "1",
            context: "extra cheese, no salsa",
            source: .photo,
            photoFileNames: ["a.jpg", "b.jpg"],
            caloriesKcal: 650, proteinG: 35, carbsG: 50, fatG: 30,
            sodiumMg: 1100, caffeineMg: nil, alcoholG: nil, saturatedFatG: 12,
            citations: [SourceCitation(title: "Burrito ref", url: "https://ref.example/burrito")],
            isEstimated: true)

        let data = try JSONEncoder().encode(entry)
        let back = try JSONDecoder().decode(FoodEntry.self, from: data)

        XCTAssertEqual(back.description, "chicken burrito")
        XCTAssertEqual(back.serving, "1")
        XCTAssertEqual(back.context, "extra cheese, no salsa")
        XCTAssertEqual(back.source, .photo)
        XCTAssertEqual(back.photoFileNames, ["a.jpg", "b.jpg"])
        XCTAssertEqual(back.caloriesKcal, 650)
        XCTAssertEqual(back.proteinG, 35)
        XCTAssertEqual(back.carbsG, 50)
        XCTAssertEqual(back.fatG, 30)
        XCTAssertEqual(back.sodiumMg, 1100)
        XCTAssertNil(back.caffeineMg)
        XCTAssertNil(back.alcoholG)
        XCTAssertEqual(back.saturatedFatG, 12)
        XCTAssertEqual(back.citations?.count, 1)
        XCTAssertEqual(back.citations?.first?.url, "https://ref.example/burrito")
        XCTAssertTrue(back.isEstimated)
    }

    func testDecodeToleratesMissingNewFields() throws {
        // A record written by an older version: no context/citations/
        // saturated_fat_g/alcohol_g, and a single legacy photo file name is
        // absent too. Must still decode.
        let legacy = """
        {"id":"x","dateEpochSeconds":123,"description":"old","source":"manual","isEstimated":true}
        """
        let e = try JSONDecoder().decode(FoodEntry.self, from: legacy.data(using: .utf8)!)
        XCTAssertEqual(e.description, "old")
        XCTAssertNil(e.context)
        XCTAssertEqual(e.photoFileNames, [])
        XCTAssertNil(e.saturatedFatG)
        XCTAssertNil(e.alcoholG)
        XCTAssertTrue(e.isEstimated)
    }
}
