//
//  OkHttpFoodClient.kt
//  Cluecose
//
//  The real [FoodClient]: an OpenAI-compatible chat/completions call over
//  OkHttp. The Android mirror of iOS `FoodLLMClient` (which uses URLSession).
//
//  • The prompt is built by the shared `:core` NutritionPrompt.buildPrompt
//    (template loaded from app assets `food/prompt.txt` — the same file the
//    iOS bundle ships), so the model sees the exact same instruction on both
//    platforms.
//  • `.photo` mode sends a `content` array with one text part plus one
//    `image_url` part per photo (up to 5), each image downscaled to ~720 px
//    (PhotoCompressor) and inlined as a `data:image/jpeg;base64,...` URL.
//    Photos are INPUT to a vision model only — Cluecose never generates or
//    synthesizes images.
//  • The request asks for strict JSON (`response_format: json_object`); the
//    raw `choices[0].message.content` is parsed by the shared `:core`
//    NutritionPrompt.parseResponse into a structured Estimate.
//
//  Stateless per call (config is read fresh each time, so Settings edits
//  apply immediately).
//

package com.dsikic.cluecose.food

import android.content.Context
import com.dsikic.cluecose.core.food.NutritionPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A minimal OpenAI-compatible chat/completions client for the
 * nutrition-estimation feature. Reads its [FoodLLMConfig] fresh from
 * SharedPreferences on every call (mirrors the iOS `FoodLLMConfig.load()`
 * per-call pattern).
 */
class OkHttpFoodClient(
    private val context: Context,
    private val client: OkHttpClient = sharedClient,
) : FoodClient {

    /** At most this many photos are sent in a single request. */
    private val maxPhotos = 5

    // MARK: Estimation

    override suspend fun estimate(
        request: NutritionPrompt.Request,
        photos: List<ByteArray>,
    ): NutritionPrompt.Estimate = withContext(Dispatchers.IO) {
        val config = FoodLLMConfig.load(context)
        if (!config.isEnabled) throw LlmError.NoApiKey()
        val prompt = NutritionPrompt.buildPrompt(request, loadTemplate())
            ?: throw LlmError.TemplateMissing()

        val body = JSONObject()
            .put("model", config.model)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", messagesArray(prompt, request, photos, config))

        val raw = post(body, config)
        val content = raw
            .optJSONArray("choices")?.let { choices ->
                choices.optJSONObject(0)?.optJSONObject("message")?.optString("content", null)
            }
        if (content.isNullOrEmpty()) {
            throw LlmError.BadResponse("no message content in response")
        }
        val estimate = NutritionPrompt.parseResponse(content)
            ?: throw LlmError.BadResponse("response was not a valid nutrition estimate")
        estimate
    }

    // MARK: Connection test

    override suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val config = FoodLLMConfig.load(context)
        if (!config.isEnabled) throw LlmError.NoApiKey()
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", 1)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", "Reply with the single word: ok"),
                ),
            )
        post(body, config)
        "OK — ${config.model} is reachable at ${config.baseURL}"
    }

    // MARK: Body building

    /**
     * The `messages` array. Manual = a single user message with a plain
     * string content. Photo = a user message whose content is an array with a
     * text part plus one image part per (downscaled) photo.
     */
    private fun messagesArray(
        prompt: String,
        request: NutritionPrompt.Request,
        photos: List<ByteArray>,
        config: FoodLLMConfig,
    ): JSONArray {
        val message = JSONObject().put("role", "user")
        if (request.mode == NutritionPrompt.Mode.PHOTO && photos.isNotEmpty()) {
            val parts = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
            for (data in photos.take(maxPhotos)) {
                val b64 = PhotoCompressor.base64Jpeg(data) ?: continue
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")),
                )
            }
            message.put("content", parts)
        } else {
            message.put("content", prompt)
        }
        return JSONArray().put(message)
    }

    // MARK: Transport

    /**
     * POST [body] to `{baseURL}/chat/completions` with a Bearer token;
     * returns the parsed JSON response object. Throws a presentable [LlmError]
     * on non-2xx or a non-JSON body (mirrors iOS `post`).
     */
    private fun post(body: JSONObject, config: FoodLLMConfig): JSONObject {
        val base = config.baseURL.trim()
        if (base.isEmpty()) throw LlmError.BadResponse("invalid base URL")
        val url = base.removeSuffix("/") + "/chat/completions"

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw LlmError.HttpStatus(resp.code, text)
            }
            text
        }
        return try {
            JSONObject(response)
        } catch (e: Exception) {
            throw LlmError.BadResponse("response was not JSON")
        }
    }

    /**
     * The bundled prompt template from app assets (`food/prompt.txt`) — the
     * same file the iOS SPM resource bundle ships. Returns null when missing
     * (the caller throws `templateMissing` and the UI falls back to manual
     * entry).
     */
    private fun loadTemplate(): String? = try {
        context.assets.open("food/prompt.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: IOException) {
        null
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** The shared client (like iOS `URLSession.shared`), 60 s timeouts. */
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
