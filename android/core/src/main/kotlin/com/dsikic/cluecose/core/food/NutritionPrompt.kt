//
//  NutritionPrompt.kt
//  CluecoseCore (Android `:core` mirror)
//
//  Kotlin mirror of `CluecoseCore/Sources/CluecoseCore/NutritionPrompt.swift`.
//  Builds the prompt for the nutrition-estimation LLM call and parses its
//  response into structured nutrition values. Pure functions: no network, no
//  bundle dependence in the core logic, so the Swift and Android ports can be
//  golden-tested against the SAME fixture files.
//
//  Cross-platform contract:
//    • The prompt TEMPLATE lives once in `Resources/food/prompt.txt` and is
//      bundled on both platforms (iOS via SPM resources, Android via an
//      assets srcDir pointing at the same file — see the app layer). Both do
//      identical `{{marker}}` substitution, so the model sees the same
//      instruction.
//    • The LLM is asked for strict JSON with the snake_case keys
//      calories_kcal / protein_g / carbs_g / fibre_g / fat_g /
//      saturated_fat_g / sodium_mg / caffeine_mg / alcohol_g / description,
//      plus an optional `citations` array of {title, url} objects for
//      web-grounded estimates.
//      `parseResponse` accepts a raw model string (it tolerates code fences
//      and surrounding prose) and returns a `NutritionEstimate`.
//

package com.dsikic.cluecose.core.food

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure Kotlin mirror of Swift's `NutritionPrompt`.
 */
object NutritionPrompt {

    /** Swift `Mode`: `.manual` / `.photo`. */
    enum class Mode {
        /** Text description (+ optional serving/context). */
        MANUAL,
        /** One or more meal photos are attached to the model call. */
        PHOTO
    }

    /**
     * Everything needed to render the prompt for one entry (or reanalysis of
     * one entry).
     */
    data class Request(
        val description: String,
        val serving: String? = null,
        /** User-supplied context a photo cannot infer (portion, brand,
         *  preparation, hidden ingredients). Fed to the model when present. */
        val context: String? = null,
        val mode: Mode,
        /** How many photos are attached (1...5). Only meaningful for PHOTO. */
        val photoCount: Int = 0,
    )

    /**
     * The structured result of one estimation call. Every field is optional:
     * the model may leave a component out (null) when it genuinely can't
     * estimate it.
     */
    data class Estimate(
        val caloriesKcal: Double? = null,
        val proteinG: Double? = null,
        val carbsG: Double? = null,
        val fibreG: Double? = null,
        val fatG: Double? = null,
        val saturatedFatG: Double? = null,
        val sodiumMg: Double? = null,
        val caffeineMg: Double? = null,
        val alcoholG: Double? = null,
        /** The model's own one-line description of what it identified (used
         *  to prefill/correct the entry's description for photo entries). */
        val description: String? = null,
        /** Clickable sources for web-grounded estimates (empty when none). */
        val citations: List<SourceCitation> = emptyList(),
    ) {
        /** True when nothing usable came back (citations alone don't count). */
        val isEmpty: Boolean
            get() = caloriesKcal == null && proteinG == null && carbsG == null &&
                fibreG == null && fatG == null && saturatedFatG == null &&
                sodiumMg == null && caffeineMg == null && alcoholG == null &&
                description == null
    }

    // MARK: Prompt building

    /**
     * Pure substitution: replace `{{description}}`, `{{serving}}`,
     * `{{context}}` and `{{focus}}` in [template]. Kept separate from bundle
     * loading so it can be unit-tested with an inline template.
     * (Swift `buildPrompt(template:request:)`.)
     */
    fun buildPrompt(template: String, request: Request): String {
        var out = template
        out = out.replace("{{description}}", request.description.trim())
        out = out.replace("{{serving}}", if (!request.serving.isNullOrEmpty()) request.serving!! else "not specified")
        out = out.replace("{{context}}", if (!request.context.isNullOrEmpty()) request.context!! else "not specified")
        out = out.replace("{{focus}}", focusLine(request))
        return out
    }

    private fun focusLine(request: Request): String =
        if (request.mode == Mode.PHOTO) {
            val n = maxOf(request.photoCount, 1)
            val photos = if (n == 1) "1 photo of this meal" else "$n photos of this meal"
            "Analyze the attached $photos (use all of them)."
        } else {
            "Estimate from the description below."
        }

    /**
     * Convenience mirror of the Swift `buildPrompt(request:)`: render with a
     * caller-supplied template. On Android the template text is loaded from
     * app assets by the caller (the `:core` module has no bundle of its
     * own); returns null when the template is missing (callers should fall
     * back to manual entry).
     */
    fun buildPrompt(request: Request, template: String? = null): String? =
        template?.let { buildPrompt(it, request) }

    // MARK: Response parsing

    /**
     * Parse a raw model output string into an [Estimate]. Tolerant of a
     * markdown code fence and of leading/trailing prose: it isolates the
     * first balanced `{...}` block, then decodes the known snake_case keys.
     * Returns null when no valid JSON object can be found.
     * (Swift `parseResponse(_:)`.)
     */
    fun parseResponse(raw: String): Estimate? {
        val json = isolateJSONObject(raw) ?: return null
        val obj = try {
            // The isolated substring is expected to be a strict JSON object
            // (Swift uses JSONSerialization here — strict parsing).
            lenientJson.parseToJsonElement(json) as? JsonObject
        } catch (exc: Exception) {
            return null
        } ?: return null

        val e = Estimate(
            caloriesKcal = number(obj["calories_kcal"]),
            proteinG = number(obj["protein_g"]),
            carbsG = number(obj["carbs_g"]),
            fibreG = number(obj["fibre_g"]),
            fatG = number(obj["fat_g"]),
            saturatedFatG = number(obj["saturated_fat_g"]),
            sodiumMg = number(obj["sodium_mg"]),
            caffeineMg = number(obj["caffeine_mg"]),
            alcoholG = number(obj["alcohol_g"]),
            description = obj["description"]?.let { el ->
                if (el is JsonPrimitive && el.isString && el.content.isNotEmpty()) el.content else null
            },
            citations = parseCitations(obj["citations"]),
        )
        return if (e.isEmpty) null else e
    }

    /**
     * Swift `number(_:)`: a JSON number → its double value; a JSON string →
     * its trimmed double value; anything else (null, bool, object, array) →
     * null.
     */
    private fun number(v: JsonElement?): Double? {
        if (v == null || v is JsonNull) return null
        val p = v as? JsonPrimitive ?: return null
        // Both branches parse the raw text to a double:
        //   • string  → Swift `Double(trimmed)` (numeric strings like "120")
        //   • number  → Swift NSNumber `.doubleValue` (ints like 800, doubles
        //     like 800.5). The primitive's content is the number's literal
        //     text; booleans ("true") and anything non-numeric yield null.
        return if (p.isString) p.content.trim().toDoubleOrNull() else p.content.toDoubleOrNull()
    }

    /**
     * Decode a `citations` array of {"title":…, "url":…} objects. Missing or
     * malformed entries are skipped; an absent key yields an empty list.
     * (Swift `parseCitations(_:)`.)
     */
    private fun parseCitations(v: JsonElement?): List<SourceCitation> {
        val arr = v as? JsonArray ?: return emptyList()
        val out = mutableListOf<SourceCitation>()
        for (item in arr) {
            val jo = item as? JsonObject ?: continue
            val url = jo["url"]?.let { el ->
                if (el is JsonPrimitive && el.isString) el.content else null
            }
            if (url.isNullOrEmpty()) continue
            val title = jo["title"]?.let { el ->
                if (el is JsonPrimitive && el.isString) el.content else null
            } ?: ""
            out.add(SourceCitation(title = title, url = url))
        }
        return out
    }

    /**
     * Find the first balanced top-level `{ ... }` substring, or null.
     * String/escape-aware so braces inside quoted values don't count
     * (Swift `isolateJSONObject(_:)`).
     */
    private fun isolateJSONObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var i = start
        var depth = 0
        var inString = false
        var escape = false
        while (i < raw.length) {
            val ch = raw[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (ch == '\\') {
                    escape = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return raw.substring(start, i + 1)
                    }
                    else -> {}
                }
            }
            i++
        }
        return null
    }

    // A lenient instance (numeric-string tolerance is handled explicitly in
    // `number` above; this is just for parsing the isolated block).
    private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }
}
