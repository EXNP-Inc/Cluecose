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

    /// The OpenAI key bundled with THIS build. It is written into
    /// `source/Cluecose/Resources/FoodLLMKey.json` by the build scripts from a
    /// gitignored local file, so the key never lands in the repository (GitHub
    /// secret scanning rejects pushes that carry a real `sk-proj-` key).
    /// `nil` on builds made from a clean checkout without the local file.
    static func bundledKey() -> String? {
        guard let url = Bundle.main.url(forResource: "FoodLLMKey", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let key = obj["apiKey"] as? String,
              !key.isEmpty else { return nil }
        return key
    }

    init(baseURL: String = FoodLLMConfig.defaultBaseURL,
         model: String = FoodLLMConfig.defaultModel,
         apiKey: String = FoodLLMConfig.bundledKey() ?? "") {
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
        // Fresh install: default to the build-bundled key, if one was
        // injected at build time (feature enabled out of the box).
        return FoodLLMConfig()
    }

    func save() {
        if let data = try? JSONEncoder().encode(self) {
            UserDefaults.standard.set(data, forKey: Self.key)
        }
    }
}

/// The nutrition-estimation dependency, as used by `FoodLogModel`. The real
/// implementation is `FoodLLMClient`; tests inject a deterministic stub so the
/// save/analysis logic can be verified without a network.
protocol FoodEstimating {
    func estimate(_ request: NutritionPrompt.Request,
                  photos: [Data]) async throws -> NutritionPrompt.Estimate
    func fallbackEstimate(description: String,
                          serving: String?,
                          context: String?) -> NutritionPrompt.Estimate
}

/// A minimal OpenAI-compatible client. Stateless per call (config is read
/// fresh from UserDefaults each time, so Settings edits apply immediately).
final class FoodLLMClient: FoodEstimating {
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

    /// Used when the model endpoint can't return an estimate (no key,
    /// invalid/out-of-credits key, offline, or unparseable response).
    ///
    /// It deliberately returns NO nutrient values. An earlier version guessed
    /// a keyword-based "typical meal" (a 450 kcal base, then nudged by words
    /// like "sugar"), which silently logged wrong macros — a 5-kcal serving
    /// of Metamucil was stored as ~475 kcal. A meal we couldn't analyze must
    /// be saved with blank values and clearly flagged, never with invented
    /// numbers: the user then enters values by hand (or the LLM works again
    /// and a re-save fills them in). The empty estimate is also what tells
    /// `FoodLogModel.saveEntry` the analysis failed.
    func fallbackEstimate(description: String,
                          serving: String? = nil,
                          context: String? = nil) -> NutritionPrompt.Estimate {
        NutritionPrompt.Estimate(description: description.isEmpty ? nil : description)
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
