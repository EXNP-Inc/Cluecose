//
//  FoodClient.kt
//  Cluecose
//
//  The seam for the nutrition-estimation LLM endpoint. The UI / ViewModel
//  only ever talk to the [FoodClient] interface; the real implementation is
//  [com.dsikic.cluecose.food.OkHttpFoodClient] (an OpenAI-compatible
//  chat/completions call over OkHttp). A test double can be `provided` to the
//  ViewModel the same way [com.dsikic.cluecose.ui.GlucoseRepository] is.
//
//  • The prompt is built by the shared `:core` NutritionPrompt.buildPrompt so
//    the model sees the exact same instruction as the iOS port.
//  • `.photo` mode attaches up to 5 photos (each inlined as a
//    `data:image/jpeg;base64,…` part, ~720 px). Photos are INPUT to a vision
//    model only — Cluecose never generates or synthesizes images.
//  • The request asks for strict JSON (`response_format: json_object`); the
//    raw `choices[0].message.content` is parsed by the shared
//    `:core` NutritionPrompt.parseResponse into a structured Estimate.
//
//  Privacy: food details + photos go ONLY to the endpoint the user configures
//  in Settings. Glucose never leaves the phone.
//

package com.dsikic.cluecose.food

import android.content.Context
import com.dsikic.cluecose.core.food.NutritionPrompt
import org.json.JSONObject

// MARK: - Errors

/**
 * Errors thrown by a [FoodClient] with a short, user-presentable message.
 * Mirrors iOS `FoodLLMError`.
 */
sealed class LlmError : Exception() {
    override val message: String
        get() = when (this) {
            is NoApiKey -> "Set an API key in Settings › Nutrition analysis to use this."
            is TemplateMissing -> "The nutrition prompt template is missing."
            is HttpStatus -> "The model endpoint returned an error (HTTP $code). ${body.take(160)}"
            is BadResponse -> "Could not read the model's answer. $detail"
        }

    /** No API key configured — the feature is disabled until one is set. */
    class NoApiKey : LlmError()

    /** The bundled prompt template was missing (should not happen). */
    class TemplateMissing : LlmError()

    /** The endpoint returned a non-2xx status. */
    class HttpStatus(val code: Int, val body: String) : LlmError()

    /** The response could not be parsed into the expected shape. */
    class BadResponse(val detail: String) : LlmError()
}

// MARK: - Config

/**
 * User-configurable endpoint + model + key. Persisted as JSON under
 * [key] in the shared `"cluecose"` preferences. The feature is DISABLED until
 * [apiKey] is non-empty (checked by the UI and by [FoodClient.estimate]).
 * Mirrors iOS `FoodLLMConfig`.
 */
data class FoodLLMConfig(
    val baseURL: String = defaultBaseURL,
    val model: String = defaultModel,
    val apiKey: String = "",
) {
    val isEnabled: Boolean get() = apiKey.trim().isNotEmpty()

    companion object {
        const val key = "cluecose.food.llm.config"
        const val defaultBaseURL = "https://api.openai.com/v1"
        const val defaultModel = "gpt-4o-mini"

        /** Load the stored config (defaults when absent/unreadable). */
        fun load(context: Context): FoodLLMConfig {
            val prefs = context.getSharedPreferences("cluecose", Context.MODE_PRIVATE)
            val raw = prefs.getString(key, null) ?: return FoodLLMConfig()
            return try {
                val o = JSONObject(raw)
                FoodLLMConfig(
                    baseURL = o.optString("baseURL", defaultBaseURL),
                    model = o.optString("model", defaultModel),
                    apiKey = o.optString("apiKey", ""),
                )
            } catch (e: Exception) {
                FoodLLMConfig()
            }
        }

        /** Persist the config (JSON under [key]). */
        fun save(context: Context, config: FoodLLMConfig) {
            val prefs = context.getSharedPreferences("cluecose", Context.MODE_PRIVATE)
            val o = JSONObject()
            o.put("baseURL", config.baseURL)
            o.put("model", config.model)
            o.put("apiKey", config.apiKey)
            prefs.edit().putString(key, o.toString()).apply()
        }
    }
}

// MARK: - Client seam

/**
 * The nutrition-estimation endpoint. Stateless per call (config is read fresh
 * each time, so Settings edits apply immediately — like the iOS client).
 */
interface FoodClient {
    /**
     * Run one nutrition estimate. [request.mode] PHOTO + [photos] attaches up
     * to 5 photos as data-URL parts. Throws an [LlmError] (presentable) when
     * the estimate fails or is empty, so the caller can offer manual entry.
     */
    suspend fun estimate(
        request: NutritionPrompt.Request,
        photos: List<ByteArray> = emptyList(),
    ): NutritionPrompt.Estimate

    /** A trivial completion to verify the endpoint + key. Returns an "OK …" line. */
    suspend fun testConnection(): String
}
