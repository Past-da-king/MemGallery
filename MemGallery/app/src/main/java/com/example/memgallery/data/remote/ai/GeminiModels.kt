package com.example.memgallery.data.remote.ai

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Live Gemini model list (GET /v1beta/models, every page) and what to do with it. No model ids are trusted
 * from the app: the list the key can use decides what is offered and what the default is.
 */
object GeminiModels {
    const val BASE_URL = "https://generativelanguage.googleapis.com"

    /** Kept only if the live list cannot be fetched at all. Ids come from ai.google.dev (checked 5 Nov 2026). */
    const val FALLBACK_DEFAULT = "gemini-3.5-flash-lite"

    /** Models that answer generateContent but are not for chat, summaries or reading pictures. */
    private val NOT_FOR_TEXT_CHAT = listOf(
        "tts", "image", "live", "native-audio", "transcribe", "embedding", "robotics", "computer-use", "deep-research", "omni", "aqa", "veo", "imagen"
    )

    fun isTextChatModel(id: String): Boolean =
        id.startsWith("gemini-") && NOT_FOR_TEXT_CHAT.none { id.contains(it) }

    /** Parse one models.list page: usable ids plus the next page token (null on the last page). */
    fun parsePage(json: String): Pair<List<String>, String?> {
        val o = JSONObject(json)
        val arr = o.optJSONArray("models")
        val ids = buildList {
            if (arr != null) for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")
                val canGenerate = methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                val id = m.optString("name", "").removePrefix("models/")
                if (canGenerate && isTextChatModel(id)) add(id)
            }
        }
        return ids to o.optString("nextPageToken", "").ifBlank { null }
    }

    /** Every page of models.list, de-duplicated and sorted. */
    fun fetch(client: OkHttpClient, apiKey: String, baseUrl: String = BASE_URL): Result<List<String>> {
        return try {
            val all = linkedSetOf<String>()
            var token: String? = null
            var pages = 0
            do {
                val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("v1beta/models")
                    .addQueryParameter("pageSize", "1000")
                    .apply { token?.let { addQueryParameter("pageToken", it) } }
                    .build()
                // The key goes in a header, not the URL, so it never lands in logs.
                val req = Request.Builder().url(url).addHeader("x-goog-api-key", apiKey).build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful || body == null) throw IOException("HTTP ${resp.code}: ${body?.take(200)}")
                    val (ids, next) = parsePage(body)
                    all += ids
                    token = next
                }
                pages++
            } while (token != null && pages < 10)
            Result.success(all.sortedWith(compareByDescending<String> { versionOf(it) }.thenBy { it }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 3.8 from "gemini-3.8-flash"; 0 when there is no number. */
    fun versionOf(id: String): Double =
        Regex("""gemini-(\d+(?:\.\d+)?)""").find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    private fun isStable(id: String) = !id.contains("preview") && !id.contains("exp")

    /**
     * Default for a key: the newest stable Flash-Lite (cheap and quick for tags and summaries), else the newest stable
     * Flash, else the newest anything in the list.
     */
    fun pickDefault(models: List<String>): String? {
        val text = models.filter(::isTextChatModel)
        fun newest(pred: (String) -> Boolean) = text.filter(pred).maxByOrNull { versionOf(it) }
        return newest { isStable(it) && it.contains("flash-lite") }
            ?: newest { isStable(it) && it.contains("flash") && !it.contains("lite") }
            ?: newest { isStable(it) }
            ?: text.maxByOrNull { versionOf(it) }
    }

    /** The model to call: the saved one while the live list still has it, otherwise the best default from the list. */
    fun resolve(saved: String, live: List<String>): String {
        if (live.isEmpty()) return saved.ifBlank { FALLBACK_DEFAULT }
        if (saved.isNotBlank() && saved in live) return saved
        return pickDefault(live) ?: saved.ifBlank { FALLBACK_DEFAULT }
    }
}
