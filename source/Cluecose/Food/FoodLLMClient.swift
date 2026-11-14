//
//  FoodLLMClient.swift
//  Cluecose
//
//  OpenAI-compatible chat/completions client for the nutrition-estimation
//  feature. Uses URLSession only — no new dependency.
//
//  • The prompt is built by the shared core (`NutritionPrompt.buildPrompt`) so
//    the model sees the exact same instruction as the Android port.
//  • `.photo` mode sends a `content` array with one text part plus one
//    `image_url` part per photo (up to 5), each image downscaled to ~720 px
//    on the longest side and inlined as a `data:image/jpeg;base64,...` URL.
//    Photos are INPUT to a vision model only — Cluecose never generates or
//    synthesizes images.
//  • The request asks for strict JSON (`response_format: json_object`); the
//    raw `choices[0].message.content` is parsed by the shared core
//    (`NutritionPrompt.parseResponse`) into a structured `Estimate`.
//
//  Privacy: food details + photos go ONLY to the endpoint the user configures.
//  Glucose never leaves the phone.
//

import Foundation
import UIKit
import CluecoseCore

/// Errors thrown by `FoodLLMClient` with a short, user-presentable message.
enum FoodLLMError: LocalizedError {
    /// No API key configured — the feature is disabled until one is set.
    case noAPIKey
    /// The bundled prompt template was missing (should not happen).
    case templateMissing
    /// The endpoint returned a non-2xx status.
    case httpStatus(Int, String)
    /// The response could not be parsed into the expected shape.
    case badResponse(String)

    var errorDescription: String? {
        switch self {
        case .noAPIKey:
            return "Set an API key in Settings › Nutrition analysis to use this."
        case .templateMissing:
            return "The nutrition prompt template is missing."
        case .httpStatus(let code, let body):
            return "The model endpoint returned an error (HTTP \(code)). \(body.prefix(160))"
        case .badResponse(let detail):
            return "Could not read the model's answer. \(detail)"
        }
    }
}

/// User-configurable endpoint + model + key. Persisted as JSON data under
/// `cluecose.food.llm.config`. The feature is DISABLED until `apiKey` is
/// non-empty (checked by the UI and by `estimate`).
struct FoodLLMConfig: Codable {
    var baseURL: String
    var model: String
    var apiKey: String

    static let key = "cluecose.food.llm.config"
    static let defaultBaseURL = "https://api.openai.com/v1"
    static let defaultModel = "gpt-4o-mini"
    /// Built-in OpenAI key so the feature works out of the box (simulator demo).
    /// Overridable in Settings; if a user clears the field it falls back to this.
    static let builtInKey = "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"

    init(baseURL: String = FoodLLMConfig.defaultBaseURL,
         model: String = FoodLLMConfig.defaultModel,
         apiKey: String = FoodLLMConfig.builtInKey) {
        self.baseURL = baseURL
        self.model = model
        self.apiKey = apiKey
    }

    var isEnabled: Bool { !apiKey.trimmingCharacters(in: .whitespaces).isEmpty }

    static func load() -> FoodLLMConfig {
        if let data = UserDefaults.standard.data(forKey: key),
           let cfg = try? JSONDecoder().decode(FoodLLMConfig.self, from: data) {
            return cfg   // respect whatever the user last saved (incl. a cleared key)
        }
        // Fresh install: default to the built-in OpenAI key so the feature is
        // enabled out of the box.
        return FoodLLMConfig()
    }

    func save() {
        if let data = try? JSONEncoder().encode(self) {
            UserDefaults.standard.set(data, forKey: Self.key)
        }
    }
}

/// A minimal OpenAI-compatible client. Stateless per call (config is read
/// fresh from UserDefaults each time, so Settings edits apply immediately).
final class FoodLLMClient {
    static let shared = FoodLLMClient()

    /// Longest-side target for inlined photos (pixels).
    private let maxImageSide: CGFloat = 720
    /// JPEG quality for inlined photos.
    private let jpegQuality: CGFloat = 0.8
    /// At most this many photos are sent in a single request.
    private let maxPhotos = 5

    // MARK: Estimation

    /// Run one nutrition estimate against the model. For `.manual` this is a
    /// text-only chat completion; for `.photo` it attaches up to 5 downscaled
    /// images as data-URL parts. Throws when the endpoint is unusable (no key,
    /// no credits / HTTP 429, offline, or an unparseable body) — callers catch
    /// that and fall back to a manual entry or to `fallbackEstimate`.
    func estimate(_ request: NutritionPrompt.Request,
                  photos: [Data] = []) async throws -> NutritionPrompt.Estimate {
        let config = FoodLLMConfig.load()
        guard config.isEnabled else { throw FoodLLMError.noAPIKey }
        guard let prompt = NutritionPrompt.buildPrompt(request: request) else {
            throw FoodLLMError.templateMissing
        }

        var body: [String: Any] = [
            "model": config.model,
            "response_format": ["type": "json_object"],
        ]

        // Build the user message content. Manual = plain string. Photo = an
        // array with a text part plus one image part per photo.
        if request.mode == .photo && !photos.isEmpty {
            var parts: [[String: Any]] = [["type": "text", "text": prompt]]
            for data in photos.prefix(maxPhotos) {
                if let b64 = downscaleBase64JPEG(data) {
                    parts.append([
                        "type": "image_url",
                        "image_url": ["url": "data:image/jpeg;base64,\(b64)"],
                    ])
                }
            }
            body["messages"] = [[
                "role": "user",
                "content": parts,
            ]]
        } else {
            body["messages"] = [[
                "role": "user",
                "content": prompt,
            ]]
        }

        let raw = try await post(body: body, config: config)
        guard let content = raw["choices"] as? [[String: Any]],
              let message = content.first?["message"] as? [String: Any],
              let text = message["content"] as? String else {
            throw FoodLLMError.badResponse("no message content in response")
        }
        guard let estimate = NutritionPrompt.parseResponse(text) else {
            throw FoodLLMError.badResponse("response was not a valid nutrition estimate")
        }
        return estimate
    }

    // MARK: Connection test

    /// A trivial chat completion to verify the endpoint + key. Returns a short
    /// "ok" string on success; throws (with a presentable message) on failure.
    func testConnection() async throws -> String {
        let config = FoodLLMConfig.load()
        guard config.isEnabled else { throw FoodLLMError.noAPIKey }
        var body: [String: Any] = [
            "model": config.model,
            "max_tokens": 1,
            "messages": [["role": "user", "content": "Reply with the single word: ok"]],
        ]
        _ = try await post(body: body, config: config)
        return "OK — \(config.model) is reachable at \(config.baseURL)"
    }

    // MARK: Local fallback estimate

    /// A deterministic, keyword-based nutrition estimate used when the model
    /// endpoint is unreachable — most notably when the configured OpenAI key
    /// has no credits (HTTP 429 `credit_balance_exhausted`), as on this
    /// simulator, but also when offline or the model returns garbage. The LLM
    /// is still the primary estimator; this only ensures the feature WORKS on
    /// a device without a functioning LLM account. Values are rough (an order
    /// of magnitude), clearly an estimate, and the caller labels them as such.
    func fallbackEstimate(description: String,
                          serving: String? = nil,
                          context: String? = nil) -> NutritionPrompt.Estimate {
        let text = (description + " " + (serving ?? "") + " " + (context ?? "")).lowercased()
        func has(_ words: [String]) -> Bool { words.contains { text.contains($0) } }

        // Plausible base for a typical mixed meal, then nudged by keywords.
        var e = NutritionPrompt.Estimate(caloriesKcal: 450, proteinG: 20, carbsG: 50,
                                         fatG: 15, saturatedFatG: 5, sodiumMg: 500,
                                         caffeineMg: 0, alcoholG: 0,
                                         description: description.isEmpty ? nil : description)

        if has(["coffee", "espresso", "latte", "cappuccino", "americano", "mocha",
                "matcha", "energy drink", "red bull", "monster", "black tea", "green tea", "chai"]) {
            e.caffeineMg = has(["espresso", "double", "shot", "red bull", "monster"]) ? 180 : 95
        }
        if has(["soda", "coke", "cola", "pepsi", "fanta", "sprite", "root beer"]) {
            e.caffeineMg = 34
        }
        if has(["beer", "ale", "lager", "stout", "wine", "cocktail", "whiskey", "whisky",
                "vodka", "rum", "gin", "bourbon", "tequila", "martini", "mojito", "champagne"]) {
            e.alcoholG = has(["cocktail", "martini", "mojito"]) ? 21 : 14
        }
        if has(["chicken", "beef", "steak", "pork", "fish", "salmon", "tuna", "shrimp",
                "prawn", "egg", "eggs", "tofu", "bean", "lentil", "meat", "lamb", "turkey",
                "ham", "bacon", "chickpea", "tempeh"]) {
            e.proteinG = 35
            e.saturatedFatG = has(["bacon", "beef", "pork", "lamb", "steak"]) ? 9 : 6
        }
        if has(["rice", "bread", "pasta", "noodle", "spaghetti", "potato", "mashed",
                "wrap", "tortilla", "pizza", "bagel", "croissant", "cereal", "oat", "oatmeal",
                "quinoa", "couscous", "tortilla", "cracker"]) {
            e.carbsG = 60
        }
        if has(["cake", "cookie", "cupcake", "donut", "pastry", "dessert", "ice cream",
                "chocolate", "sugar", "honey", "syrup", "candy", "donut"]) {
            e.carbsG = 65
            e.saturatedFatG = max(e.saturatedFatG ?? 5, 8)
            e.caloriesKcal = 400
        }
        if has(["cheese", "butter", "cream", "mayo", "mayonnaise", "avocado", "oil",
                "greek yogurt", "yogurt", "milk", "almond milk", "olive"]) {
            e.fatG = 22
            e.saturatedFatG = max(e.saturatedFatG ?? 5, has(["cheese", "butter", "cream", "mayo", "mayonnaise"]) ? 12 : 6)
        }
        // Keep calories roughly consistent with the macros (4/9/7 kcal per g).
        if let p = e.proteinG, let c = e.carbsG, let f = e.fatG {
            e.caloriesKcal = (p * 4 + c * 4 + f * 9).rounded()
        }
        return e
    }

    // MARK: Transport

    private func post(body: [String: Any], config: FoodLLMConfig) async throws -> [String: Any] {
        guard let url = URL(string: config.baseURL.trimmingCharacters(in: .whitespaces) + "/chat/completions") else {
            throw FoodLLMError.badResponse("invalid base URL")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(config.apiKey)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 60
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw FoodLLMError.badResponse("no HTTP response")
        }
        guard (200..<300).contains(http.statusCode) else {
            let snippet = String(data: data, encoding: .utf8) ?? ""
            throw FoodLLMError.httpStatus(http.statusCode, snippet)
        }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw FoodLLMError.badResponse("response was not JSON")
        }
        return obj
    }

    // MARK: Image downscaling

    /// Downscale a photo to ~720 px on the longest side, re-encode as JPEG,
    /// and base64-encode it for a data-URL. Returns nil if the data is not a
    /// decodable image (it is simply skipped — the text still goes out).
    private func downscaleBase64JPEG(_ data: Data) -> String? {
        guard let image = UIImage(data: data) else { return nil }
        let longest = max(image.size.width, image.size.height)
        guard longest > 0 else { return nil }
        let scale = min(1, maxImageSide / longest)
        let size = CGSize(width: (image.size.width * scale).rounded(),
                          height: (image.size.height * scale).rounded())
        let renderer = UIGraphicsImageRenderer(size: size)
        let resized = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        guard let jpeg = resized.jpegData(compressionQuality: jpegQuality) else { return nil }
        return jpeg.base64EncodedString()
    }
}
