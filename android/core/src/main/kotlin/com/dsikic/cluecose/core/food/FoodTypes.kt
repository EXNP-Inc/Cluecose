//
//  FoodTypes.kt
//  CluecoseCore (Android `:core` mirror)
//
//  Domain types for the food / nutrition feature — a 1:1 Kotlin mirror of
//  `CluecoseCore/Sources/CluecoseCore/FoodTypes.swift`. Pure Kotlin: no
//  Android dependencies (kotlinx.serialization only).
//
//  Cross-platform contract:
//    • The JSON field names below ARE the storage contract. Both persistence
//      layers (iOS UserDefaults, Android SharedPreferences) write the same
//      schema under the key `cluecose.food.entries`.
//    • Dates are stored as `dateEpochSeconds` (seconds since 1970-01-01) so
//      the encoding is unambiguous across Swift (Codable) and Kotlin
//      (kotlinx.serialization) with no Date-strategy coupling.
//    • Nutrition values are nullable: an LLM estimate may be missing a
//      component, and a hand-entered row may leave one blank.
//
//  Nutrient set (deliberately limited — this is a CGM companion, not a
//  clinical diet app): calories, the three macros, sodium and caffeine, plus
//  two macro-level nutrients carried over from the ported product (alcohol
//  and saturated fat). There are NO vitamins or minerals by design.
//
//  Leniency (mirrors the Swift custom Codable):
//    • On decode, a missing `id` gets a fresh UUID, a missing `source`
//      defaults to "manual", a missing `isEstimated` defaults to false, and
//      every optional field (incl. `citations` / `insight`) is absent→nil,
//      `photoFileNames` absent→[].
//    • On encode, all fields are written with their exact camelCase key
//      names; null optionals are OMITTED (Swift `encodeIfPresent`) so the
//      two platforms round-trip the same JSON shape.
//
//  The hand-written serializers (instead of plain generated codecs) exist
//  because kotlinx.serialization's generated codecs cannot express "omit
//  nulls but tolerate missing keys with defaults" the way Swift's
//  `encodeIfPresent` / `decodeIfPresent` do.
//

package com.dsikic.cluecose.core.food

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// MARK: - Source citation

/**
 * A clickable reference a web-grounded nutrition estimate can carry. Present
 * only when the model returned supporting sources; an empty/absent list means
 * the estimate was produced from general knowledge alone.
 */
@kotlinx.serialization.Serializable
data class SourceCitation(
    val title: String,
    val url: String,
)

// MARK: - Meal entry

/**
 * How an entry's values were produced. JSON wire form: the strings
 * `"manual"` / `"photo"` (Swift `Source` enum, same raw values).
 */
enum class FoodSource {
    MANUAL,
    PHOTO;

    /** Swift `Source.rawValue`; also the JSON wire form. */
    val rawValue: String
        get() = when (this) {
            MANUAL -> "manual"
            PHOTO -> "photo"
        }

    companion object {
        /**
         * Swift `Source` decode: `decodeIfPresent` — a missing key (or an
         * explicit null) defaults to `.manual`; an unparseable value throws.
         */
        fun fromJson(el: JsonElement?): FoodSource {
            if (el == null || el is JsonNull) return MANUAL
            val s = el.jsonPrimitive.takeIf { it.isString }?.content
            return when (s) {
                null -> throw SerializationException("source is not a string")
                "manual" -> MANUAL
                "photo" -> PHOTO
                else -> throw SerializationException("unknown food source: $s")
            }
        }
    }
}

/**
 * One logged meal/drink with its (optionally estimated) nutrition values.
 *
 * Decoding is LENIENT (missing optionals → null, missing `id` → fresh UUID,
 * missing `source` → manual, missing `isEstimated` → false) so records
 * written by older versions or the other platform still load. Encoding is
 * strict: every field present, null optionals omitted (see [FoodEntrySerializer]).
 */
@kotlinx.serialization.Serializable(with = FoodEntrySerializer::class)
data class FoodEntry(
    val id: String,
    /** When the meal was eaten, as seconds since 1970-01-01 (UTC). */
    val dateEpochSeconds: Double,
    /** Free-text description of the food (e.g. "two slices of turkey sandwich"). */
    val description: String,
    /** Optional portion/serving note fed to the estimator ("2 slices"). */
    val serving: String? = null,
    /** Free-text context the user knows that a photo cannot infer — quantity,
     *  ingredient, brand, preparation, hidden side. Fed to the estimator on
     *  (re)analysis and stored on the record. */
    val context: String? = null,
    /** How the estimate was produced. */
    val source: FoodSource = FoodSource.MANUAL,
    /** File names (not paths) of the stored photos, when source == photo;
     *  up to 5; the first is the primary image. */
    val photoFileNames: List<String> = emptyList(),
    /** Portion multiplier the user adjusted for this meal (0.1..4.0, default
     *  1.0): the estimate describes the full photographed/typed portion, and
     *  the user may have eaten a fraction or a multiple of it. The stored
     *  nutrition values are the estimate x this factor; the UI shows the
     *  slider so the user can see and re-scale the amount eaten. Lenient on
     *  decode: missing/absent -> 1.0 (older records are a single full portion).
     *  (Swift `portionMultiplier`.) */
    val portionMultiplier: Double = 1.0,
    // Nutrition — all optional; null = unknown / not applicable.
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val sodiumMg: Double? = null,
    val caffeineMg: Double? = null,
    /** Total alcohol in grams (ported dimension; null = none/unknown). */
    val alcoholG: Double? = null,
    /** Saturated fat in grams (ported fat-quality dimension; null = unknown). */
    val saturatedFatG: Double? = null,
    /** Clickable sources supporting the estimate (web-grounded analyses). */
    val citations: List<SourceCitation>? = null,
    /** True while the values are still the model's estimate and the user has
     *  not edited them. Flips to false on the first manual edit. */
    val isEstimated: Boolean = false,
    /** Phase 2: a per-meal blood-sugar insight, present only when the
     *  post-meal curve was insight-worthy. null = nothing to show. */
    val insight: MealInsight? = null,
)

/**
 * Lenient-decode / strict-encode [FoodEntry] serializer — the exact behavior
 * of the Swift custom `init(from:)` / `encode(to:)` in FoodTypes.swift.
 */
object FoodEntrySerializer : KSerializer<FoodEntry> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.dsikic.cluecose.core.food.FoodEntry") {
        element("id", String.serializer().descriptor)
        element("dateEpochSeconds", Double.serializer().descriptor)
        element("description", String.serializer().descriptor)
        element("serving", String.serializer().descriptor)
        element("context", String.serializer().descriptor)
        element("source", String.serializer().descriptor)
        element("photoFileNames", ListSerializer(String.serializer()).descriptor)
        element("portionMultiplier", Double.serializer().descriptor)
        element("caloriesKcal", Double.serializer().descriptor)
        element("proteinG", Double.serializer().descriptor)
        element("carbsG", Double.serializer().descriptor)
        element("fatG", Double.serializer().descriptor)
        element("sodiumMg", Double.serializer().descriptor)
        element("caffeineMg", Double.serializer().descriptor)
        element("alcoholG", Double.serializer().descriptor)
        element("saturatedFatG", Double.serializer().descriptor)
        element("citations", ListSerializer(SourceCitation.serializer()).descriptor)
        element("isEstimated", Boolean.serializer().descriptor)
        element("insight", MealInsight.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: FoodEntry) {
        if (encoder !is JsonEncoder) {
            throw SerializationException("FoodEntry serialization requires a JSON encoder")
        }
        // Strict encode: exact camelCase keys, null optionals omitted
        // (Swift `encodeIfPresent`). Built as an ordered map so the wire form
        // is stable and matches the Swift `encode(to:)` field order.
        val o = LinkedHashMap<String, JsonElement>()
        o["id"] = JsonPrimitive(value.id)
        o["dateEpochSeconds"] = JsonPrimitive(value.dateEpochSeconds)
        o["description"] = JsonPrimitive(value.description)
        value.serving?.let { o["serving"] = JsonPrimitive(it) }
        value.context?.let { o["context"] = JsonPrimitive(it) }
        o["source"] = JsonPrimitive(value.source.rawValue)
        // Always encode the array (possibly empty) so the schema is stable
        // (Swift: `try c.encode(photoFileNames, ...)`).
        o["photoFileNames"] = JsonArray(value.photoFileNames.map { JsonPrimitive(it) })
        // Always encode (even 1.0) so the schema is stable on both platforms
        // (Swift: `try c.encode(portionMultiplier, ...)`).
        o["portionMultiplier"] = JsonPrimitive(value.portionMultiplier)
        value.caloriesKcal?.let { o["caloriesKcal"] = JsonPrimitive(it) }
        value.proteinG?.let { o["proteinG"] = JsonPrimitive(it) }
        value.carbsG?.let { o["carbsG"] = JsonPrimitive(it) }
        value.fatG?.let { o["fatG"] = JsonPrimitive(it) }
        value.sodiumMg?.let { o["sodiumMg"] = JsonPrimitive(it) }
        value.caffeineMg?.let { o["caffeineMg"] = JsonPrimitive(it) }
        value.alcoholG?.let { o["alcoholG"] = JsonPrimitive(it) }
        value.saturatedFatG?.let { o["saturatedFatG"] = JsonPrimitive(it) }
        // `encodeIfPresent` — omitted entirely when null (not written as []).
        value.citations?.let { list ->
            o["citations"] = JsonArray(
                list.map {
                    buildJsonObject {
                        put("title", JsonPrimitive(it.title))
                        put("url", JsonPrimitive(it.url))
                    }
                }
            )
        }
        o["isEstimated"] = JsonPrimitive(value.isEstimated)
        value.insight?.let { o["insight"] = MealInsight.encodeToElement(it) }
        encoder.encodeJsonElement(JsonObject(o))
    }

    override fun deserialize(decoder: Decoder): FoodEntry {
        if (decoder !is JsonDecoder) {
            throw SerializationException("FoodEntry deserialization requires a JSON decoder")
        }
        return entryFromJson(decoder.decodeJsonElement().jsonObject)
    }

    /**
     * The lenient decode — the 1:1 of the Swift custom `init(from:)`:
     *  - `id` missing → fresh UUID (fixtures may omit it)
     *  - `dateEpochSeconds` / `description` missing → decode failure
     *  - `source` missing → manual; `isEstimated` missing → false
     *  - `photoFileNames` missing → empty list
     *  - every other optional: missing or null → null
     */
    internal fun entryFromJson(o: JsonObject): FoodEntry {
        // decodeIfPresent(String.self) — null/absent → null; non-string → null here
        // (lenient; the app never writes non-strings for these keys).
        fun strOrNull(k: String): String? =
            o[k]?.let { el -> if (el is JsonPrimitive && el.isString) el.content else null }

        // decodeIfPresent(Double.self) — null/absent → null.
        fun numOrNull(k: String): Double? =
            o[k]?.let { el ->
                if (el is JsonNull) null
                else el.jsonPrimitive.let { p -> p.doubleOrNull ?: p.longOrNull?.toDouble() }
            }

        return FoodEntry(
            id = strOrNull("id") ?: newId(),
            dateEpochSeconds = numOrNull("dateEpochSeconds")
                ?: throw SerializationException("missing dateEpochSeconds"),
            description = strOrNull("description")
                ?: throw SerializationException("missing description"),
            serving = strOrNull("serving"),
            context = strOrNull("context"),
            source = FoodSource.fromJson(o["source"]),
            photoFileNames = o["photoFileNames"]?.let { el ->
                if (el is JsonNull) emptyList()
                else (el as? JsonArray)?.mapNotNull { item ->
                    (item as? JsonPrimitive)?.takeIf { it.isString }?.content
                } ?: emptyList()
            } ?: emptyList(),
            // Lenient: missing/absent/null -> 1.0 (a single full portion,
            // pre-multiplier records). (Swift decodeIfPresent ?? 1.0.)
            portionMultiplier = numOrNull("portionMultiplier") ?: 1.0,
            caloriesKcal = numOrNull("caloriesKcal"),
            proteinG = numOrNull("proteinG"),
            carbsG = numOrNull("carbsG"),
            fatG = numOrNull("fatG"),
            sodiumMg = numOrNull("sodiumMg"),
            caffeineMg = numOrNull("caffeineMg"),
            alcoholG = numOrNull("alcoholG"),
            saturatedFatG = numOrNull("saturatedFatG"),
            citations = o["citations"]?.let { el ->
                if (el is JsonNull) null
                else (el as? JsonArray)?.mapNotNull { item ->
                    val jo = item as? JsonObject ?: return@mapNotNull null
                    val url = jo["url"]?.let { u ->
                        if (u is JsonPrimitive && u.isString) u.content else null
                    }
                    if (url.isNullOrEmpty()) null
                    else SourceCitation(
                        title = jo["title"]?.let { t ->
                            if (t is JsonPrimitive && t.isString) t.content else ""
                        } ?: "",
                        url = url,
                    )
                }
            },
            isEstimated = o["isEstimated"]?.let {
                if (it is JsonNull) false else it.jsonPrimitive.boolean ?: false
            } ?: false,
            insight = o["insight"]?.let {
                if (it is JsonNull) null else MealInsight.decodeFromElement(it)
            },
        )
    }
}

// MARK: - Per-meal insight (Phase 2)

/**
 * A deterministic, on-device blood-sugar insight for one meal, computed from
 * the pre-meal baseline and the post-meal curve. Present only when the
 * response was "insight-worthy" (a meaningful spike or a reactive dip);
 * otherwise the entry's `insight` is null and the UI shows nothing.
 *
 * The NUMERIC fields are the cross-platform contract — both the Swift and
 * Kotlin engines are golden-tested against the same `insight-*.json`
 * fixtures. `text` is a presentation string built from those numbers
 * (localized rendering is a platform concern); it is not part of the
 * parity contract.
 */
@kotlinx.serialization.Serializable(with = MealInsightSerializer::class)
data class MealInsight(
    val kind: Kind,
    /** Mean glucose over the pre-meal baseline window (mg/dL). */
    val baselineMgdl: Double,
    /** Highest glucose reached in the post-meal window (mg/dL). */
    val peakMgdl: Double,
    /** peakMgdl - baselineMgdl. */
    val peakDeltaMgdl: Double,
    /** Minutes from the meal to the peak. */
    val timeToPeakMin: Double,
    /** Minutes from the meal to the first return to within ±band of baseline
     *  after the peak; null if it had not returned by window end. */
    val returnToBaselineMin: Double? = null,
    /** Lowest glucose in the post-meal window (mg/dL). */
    val minMgdl: Double,
    /** minMgdl - baselineMgdl (negative when below baseline). */
    val minDeltaMgdl: Double,
    /** peakDeltaMgdl normalized per 10 g of carbs from the entry; null when
     *  the entry has no carb value. */
    val per10gCarbDeltaMgdl: Double? = null,
    /** One-to-two sentence summary built from the fields above. */
    val text: String,
) {
    /** Swift `MealInsight.Kind` (JSON strings "spike" / "reactiveLow"). */
    enum class Kind(val rawValue: String) {
        /** A clear post-meal rise above baseline. */
        SPIKE("spike"),
        /** A post-meal dip below baseline (reactive hypoglycemia). */
        REACTIVE_LOW("reactiveLow");

        companion object {
            fun fromJson(v: String): Kind = when (v) {
                "spike" -> SPIKE
                "reactiveLow" -> REACTIVE_LOW
                else -> throw SerializationException("unknown insight kind: $v")
            }
        }
    }

    companion object {
        /** Wire form (parity with the Swift `encode(to:)`). */
        fun encodeToElement(v: MealInsight): JsonElement {
            val o = LinkedHashMap<String, JsonElement>()
            o["kind"] = JsonPrimitive(v.kind.rawValue)
            o["baselineMgdl"] = JsonPrimitive(v.baselineMgdl)
            o["peakMgdl"] = JsonPrimitive(v.peakMgdl)
            o["peakDeltaMgdl"] = JsonPrimitive(v.peakDeltaMgdl)
            o["timeToPeakMin"] = JsonPrimitive(v.timeToPeakMin)
            v.returnToBaselineMin?.let { o["returnToBaselineMin"] = JsonPrimitive(it) }
            o["minMgdl"] = JsonPrimitive(v.minMgdl)
            o["minDeltaMgdl"] = JsonPrimitive(v.minDeltaMgdl)
            v.per10gCarbDeltaMgdl?.let { o["per10gCarbDeltaMgdl"] = JsonPrimitive(it) }
            o["text"] = JsonPrimitive(v.text)
            return JsonObject(o)
        }

        /**
         * Lenient decode of an embedded insight element (the Swift
         * `decodeIfPresent(MealInsight.self, ...)`); a null or malformed
         * object yields null.
         */
        fun decodeFromElement(e: JsonElement): MealInsight? {
            if (e is JsonNull) return null
            val o = e as? JsonObject ?: return null
            fun num(k: String): Double? = o[k]?.let { el ->
                if (el is JsonNull) null
                else el.jsonPrimitive.let { p -> p.doubleOrNull ?: p.longOrNull?.toDouble() }
            }
            val kindStr = o["kind"]?.let { el ->
                if (el is JsonPrimitive && el.isString) el.content else null
            } ?: return null
            return try {
                MealInsight(
                    kind = Kind.fromJson(kindStr),
                    baselineMgdl = num("baselineMgdl") ?: 0.0,
                    peakMgdl = num("peakMgdl") ?: 0.0,
                    peakDeltaMgdl = num("peakDeltaMgdl") ?: 0.0,
                    timeToPeakMin = num("timeToPeakMin") ?: 0.0,
                    returnToBaselineMin = num("returnToBaselineMin"),
                    minMgdl = num("minMgdl") ?: 0.0,
                    minDeltaMgdl = num("minDeltaMgdl") ?: 0.0,
                    per10gCarbDeltaMgdl = num("per10gCarbDeltaMgdl"),
                    text = o["text"]?.let { el ->
                        if (el is JsonPrimitive && el.isString) el.content else ""
                    } ?: "",
                )
            } catch (exc: SerializationException) {
                null
            }
        }
    }
}

/** Strict-encode / lenient-decode [MealInsight] JSON serializer. */
object MealInsightSerializer : KSerializer<MealInsight> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.dsikic.cluecose.core.food.MealInsight") {
        element("kind", String.serializer().descriptor)
        element("baselineMgdl", Double.serializer().descriptor)
        element("peakMgdl", Double.serializer().descriptor)
        element("peakDeltaMgdl", Double.serializer().descriptor)
        element("timeToPeakMin", Double.serializer().descriptor)
        element("returnToBaselineMin", Double.serializer().descriptor)
        element("minMgdl", Double.serializer().descriptor)
        element("minDeltaMgdl", Double.serializer().descriptor)
        element("per10gCarbDeltaMgdl", Double.serializer().descriptor)
        element("text", String.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: MealInsight) {
        if (encoder !is JsonEncoder) {
            throw SerializationException("MealInsight serialization requires a JSON encoder")
        }
        encoder.encodeJsonElement(MealInsight.encodeToElement(value))
    }

    override fun deserialize(decoder: Decoder): MealInsight {
        if (decoder !is JsonDecoder) {
            throw SerializationException("MealInsight deserialization requires a JSON decoder")
        }
        return MealInsight.decodeFromElement(decoder.decodeJsonElement())
            ?: throw SerializationException("insight object was missing or malformed")
    }
}

// MARK: - Daily / period totals

/**
 * Sums of a set of entries for the header rows of the food and patterns
 * surfaces. Each value is nil-aware: a missing value never zeroes or skews
 * the sum; a nutrient's total is null only when NO entry reported it.
 */
data class FoodTotals(
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val sodiumMg: Double? = null,
    val caffeineMg: Double? = null,
    val alcoholG: Double? = null,
    val saturatedFatG: Double? = null,
    val entryCount: Int = 0,
) {
    /** Fold a set of entries into running totals (Swift `FoodTotals.sum`). */
    companion object {
        fun sum(entries: List<FoodEntry>): FoodTotals {
            var kcal: Double? = null
            var protein: Double? = null
            var carbs: Double? = null
            var fat: Double? = null
            var sodium: Double? = null
            var caffeine: Double? = null
            var alcohol: Double? = null
            var satFat: Double? = null
            for (e in entries) {
                kcal = add(kcal, e.caloriesKcal)
                protein = add(protein, e.proteinG)
                carbs = add(carbs, e.carbsG)
                fat = add(fat, e.fatG)
                sodium = add(sodium, e.sodiumMg)
                caffeine = add(caffeine, e.caffeineMg)
                alcohol = add(alcohol, e.alcoholG)
                satFat = add(satFat, e.saturatedFatG)
            }
            return FoodTotals(
                kcal = kcal,
                proteinG = protein,
                carbsG = carbs,
                fatG = fat,
                sodiumMg = sodium,
                caffeineMg = caffeine,
                alcoholG = alcohol,
                saturatedFatG = satFat,
                entryCount = entries.size,
            )
        }

        private fun add(acc: Double?, v: Double?): Double? = when {
            acc == null && v == null -> null
            v == null -> acc
            acc == null -> v
            else -> acc + v
        }
    }
}

// MARK: - JSON config (shared by the app persistence layer)

/**
 * The [Json] instance for the food storage contract: lenient on unknown keys
 * (forward/back-compat). The app's `FoodRepository` uses [encodeList] /
 * [decodeList] to (de)serialize the `cluecose.food.entries` payload as a JSON
 * array of [FoodEntry]. Keeping the (de)serialization here (in `:core`) means
 * the app module never references `kotlinx.serialization` directly.
 */
object FoodJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
    }

    /** The list serializer for the storage payload (a JSON array of entries). */
    private val listSerializer: KSerializer<List<FoodEntry>> = ListSerializer(FoodEntrySerializer)

    /** Encode a list of entries as a JSON-array string (the storage payload). */
    fun encodeList(entries: List<FoodEntry>): String =
        instance.encodeToString(listSerializer, entries)

    /** Decode a JSON-array string into entries (empty list on malformed input). */
    fun decodeList(json: String): List<FoodEntry> =
        try {
            instance.decodeFromString(listSerializer, json)
        } catch (e: Exception) {
            emptyList()
        }
}

/** A fresh entry id (Swift uses `UUID().uuidString`). */
internal fun newId(): String = java.util.UUID.randomUUID().toString()
