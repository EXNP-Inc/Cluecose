//
//  NutritionPrompt.swift
//  CluecoseCore
//
//  Builds the prompt for the nutrition-estimation LLM call and parses its
//  response into structured nutrition values. Pure functions: no network, no
//  bundle dependence in the core logic, so the Swift and Android ports can be
//  golden-tested against the SAME fixture files.
//
//  Cross-platform contract:
//    • The prompt TEMPLATE lives once in `Resources/food/prompt.txt` and is
//      bundled on both platforms (iOS via SPM resources, Android via an assets
//      srcDir pointing at the same file). Both do identical `{{marker}}`
//      substitution, so the model sees the same instruction.
//    • The LLM is asked for strict JSON with the snake_case keys
//      calories_kcal / protein_g / carbs_g / fat_g / saturated_fat_g /
//      sodium_mg / caffeine_mg / alcohol_g / description, plus an optional
//      `citations` array of {title, url} objects for web-grounded estimates.
//      `parseResponse` accepts a raw model string (it tolerates code fences
//      and surrounding prose) and returns a `NutritionEstimate`.
//
//  The golden fixtures under `Resources/food/tests/` pin `parseResponse`
//  behavior; the Android `:core` tests run the same files.
//

import Foundation

public enum NutritionPrompt: Sendable {

    public enum Mode: String, Sendable {
        case manual   // text description (+ optional serving/context)
        case photo    // one or more meal photos are attached to the model call
    }

    /// Everything needed to render the prompt for one entry (or reanalysis of
    /// one entry).
    public struct Request: Sendable {
        public var description: String
        public var serving: String?
        /// User-supplied context a photo cannot infer (portion, brand,
        /// preparation, hidden ingredients). Fed to the model when present.
        public var context: String?
        public var mode: Mode
        /// How many photos are attached (1...5). Only meaningful for .photo.
        public var photoCount: Int
        public init(description: String,
                    serving: String? = nil,
                    context: String? = nil,
                    mode: Mode,
                    photoCount: Int = 0) {
            self.description = description
            self.serving = serving
            self.context = context
            self.mode = mode
            self.photoCount = photoCount
        }
    }

    /// The structured result of one estimation call. Every field is optional:
    /// the model may leave a component out (null) when it genuinely can't
    /// estimate it.
    public struct Estimate: Equatable, Sendable {
        public var caloriesKcal: Double?
        public var proteinG: Double?
        public var carbsG: Double?
        public var fatG: Double?
        public var saturatedFatG: Double?
        public var sodiumMg: Double?
        public var caffeineMg: Double?
        public var alcoholG: Double?
        /// The model's own one-line description of what it identified (used to
        /// prefill/correct the entry's description for photo entries).
        public var description: String?
        /// Clickable sources for web-grounded estimates (empty when none).
        public var citations: [SourceCitation]

        public init(caloriesKcal: Double? = nil,
                    proteinG: Double? = nil,
                    carbsG: Double? = nil,
                    fatG: Double? = nil,
                    saturatedFatG: Double? = nil,
                    sodiumMg: Double? = nil,
                    caffeineMg: Double? = nil,
                    alcoholG: Double? = nil,
                    description: String? = nil,
                    citations: [SourceCitation] = []) {
            self.caloriesKcal = caloriesKcal
            self.proteinG = proteinG
            self.carbsG = carbsG
            self.fatG = fatG
            self.saturatedFatG = saturatedFatG
            self.sodiumMg = sodiumMg
            self.caffeineMg = caffeineMg
            self.alcoholG = alcoholG
            self.description = description
            self.citations = citations
        }

        public var isEmpty: Bool {
            [caloriesKcal, proteinG, carbsG, fatG, saturatedFatG, sodiumMg, caffeineMg, alcoholG]
                .allSatisfy { $0 == nil } && description == nil
        }
    }

    // MARK: Prompt building

    /// Pure substitution: replace `{{description}}`, `{{serving}}`,
    /// `{{context}}` and `{{focus}}` in `template`. Kept separate from bundle
    /// loading so it can be unit-tested with an inline template.
    public static func buildPrompt(template: String, request: Request) -> String {
        var out = template
        out = out.replacingOccurrences(of: "{{description}}",
                                       with: request.description.trimmingCharacters(in: .whitespacesAndNewlines))
        out = out.replacingOccurrences(of: "{{serving}}",
                                       with: (request.serving?.isEmpty == false ? request.serving! : "not specified"))
        out = out.replacingOccurrences(of: "{{context}}",
                                       with: (request.context?.isEmpty == false ? request.context! : "not specified"))
        out = out.replacingOccurrences(of: "{{focus}}",
                                       with: focusLine(request))
        return out
    }

    private static func focusLine(_ request: Request) -> String {
        if request.mode == .photo {
            let n = max(request.photoCount, 1)
            let photos = n == 1 ? "1 photo of this meal" : "\(n) photos of this meal"
            return "Analyze the attached \(photos) (use all of them)."
        }
        return "Estimate from the description below."
    }

    /// The bundled default template, if the resource is present.
    public static func bundledTemplate() -> String? {
        guard let url = CluecoseCoreResources.bundle.url(forResource: "prompt", withExtension: "txt", subdirectory: "food"),
              let s = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        return s
    }

    /// Convenience: load the bundled template and render it. Returns nil when
    /// the template resource is missing (callers should fall back to manual entry).
    public static func buildPrompt(request: Request) -> String? {
        bundledTemplate().map { buildPrompt(template: $0, request: request) }
    }

    // MARK: Response parsing

    /// Parse a raw model output string into an `Estimate`. Tolerant of a
    /// markdown code fence and of leading/trailing prose: it isolates the
    /// first balanced `{...}` block, then decodes the known snake_case keys.
    /// Returns nil when no valid JSON object can be found.
    public static func parseResponse(_ raw: String) -> Estimate? {
        guard let json = isolateJSONObject(raw) else { return nil }
        guard let data = json.data(using: .utf8) else { return nil }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }

        var e = Estimate()
        e.caloriesKcal = number(obj["calories_kcal"])
        e.proteinG = number(obj["protein_g"])
        e.carbsG = number(obj["carbs_g"])
        e.fatG = number(obj["fat_g"])
        e.saturatedFatG = number(obj["saturated_fat_g"])
        e.sodiumMg = number(obj["sodium_mg"])
        e.caffeineMg = number(obj["caffeine_mg"])
        e.alcoholG = number(obj["alcohol_g"])
        if let d = obj["description"] as? String, !d.isEmpty { e.description = d }
        e.citations = parseCitations(obj["citations"])
        return e.isEmpty ? nil : e
    }

    private static func number(_ v: Any?) -> Double? {
        switch v {
        case let n as NSNumber: return n.doubleValue
        case let s as String:   return Double(s.trimmingCharacters(in: .whitespaces))
        default:                return nil
        }
    }

    /// Decode a `citations` array of {"title":…, "url":…} objects. Missing or
    /// malformed entries are skipped; an absent key yields an empty array.
    private static func parseCitations(_ v: Any?) -> [SourceCitation] {
        guard let arr = v as? [[String: Any]] else { return [] }
        var out: [SourceCitation] = []
        for item in arr {
            guard let url = item["url"] as? String, !url.isEmpty else { continue }
            let title = (item["title"] as? String).map { $0.isEmpty ? "" : $0 } ?? ""
            out.append(SourceCitation(title: title, url: url))
        }
        return out
    }

    /// Find the first balanced top-level `{ ... }` substring, or nil.
    private static func isolateJSONObject(_ raw: String) -> String? {
        guard let start = raw.firstIndex(of: "{") else { return nil }
        var i = start
        var depth = 0
        var inString = false
        var escape = false
        while i != raw.endIndex {
            let ch = raw[i]
            if inString {
                if escape { escape = false }
                else if ch == "\\" { escape = true }
                else if ch == "\"" { inString = false }
            } else {
                switch ch {
                case "\"": inString = true
                case "{": depth += 1
                case "}":
                    depth -= 1
                    if depth == 0 {
                        return String(raw[start...i])
                    }
                default: break
                }
            }
            raw.formIndex(after: &i)
        }
        return nil
    }
}
