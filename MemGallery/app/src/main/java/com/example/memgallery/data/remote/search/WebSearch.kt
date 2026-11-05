package com.example.memgallery.data.remote.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLDecoder
import java.util.Base64

private const val TAG = "WebSearch"
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; SM-A356E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

data class SearchResult(val title: String, val url: String, val snippet: String)

/** One search backend. */
interface SearchEngine {
    val name: String
    suspend fun search(query: String, limit: Int = 6): Result<List<SearchResult>>
}

/** Parsers kept pure so they are tested against saved pages. */
object SearchParsers {
    fun isDuckDuckGoChallenge(html: String) =
        html.contains("anomaly.js") || html.contains("bots use DuckDuckGo too", ignoreCase = true)

    fun parseDuckDuckGo(html: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        return doc.select("div.result").filterNot { it.hasClass("result--ad") }.mapNotNull { el ->
            val a = el.selectFirst("a.result__a") ?: return@mapNotNull null
            val url = unwrapDuckDuckGo(a.attr("href")) ?: return@mapNotNull null
            SearchResult(a.text().trim(), url, el.selectFirst(".result__snippet")?.text()?.trim().orEmpty())
        }.filter { it.title.isNotBlank() }
    }

    fun unwrapDuckDuckGo(href: String): String? {
        val h = if (href.startsWith("//")) "https:$href" else href
        if (h.contains("duckduckgo.com/l/")) {
            val u = h.substringAfter("uddg=", "").substringBefore("&")
            return if (u.isBlank()) null else URLDecoder.decode(u, "UTF-8")
        }
        return h.takeIf { it.startsWith("http") }
    }

    fun parseBing(html: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        return doc.select("li.b_algo").mapNotNull { el ->
            val a = el.selectFirst(".b_algoheader a[href], h2 a[href]") ?: return@mapNotNull null
            val title = a.text().trim()
            val url = unwrapBing(a.attr("href"))
            if (title.isBlank() || !url.startsWith("http")) return@mapNotNull null
            SearchResult(title, url, el.selectFirst(".b_caption p, p")?.text()?.trim().orEmpty())
        }
    }

    /** Bing wraps result links as bing.com/ck/a?...&u=a1<base64url of the real link>. */
    fun unwrapBing(href: String): String {
        if (!href.contains("bing.com/ck/a")) return href
        val u = Regex("[?&]u=([^&]+)").find(href)?.groupValues?.get(1) ?: return href
        if (!u.startsWith("a1")) return href
        return try {
            val raw = u.removePrefix("a1")
            String(Base64.getUrlDecoder().decode(raw + "=".repeat((4 - raw.length % 4) % 4)))
        } catch (e: Exception) { href }
    }

    private fun stripTags(s: String) = Jsoup.parse(s).text().trim()

    fun parseBrave(json: String): List<SearchResult> {
        val arr = JSONObject(json).optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            SearchResult(stripTags(it.optString("title")), it.optString("url"), stripTags(it.optString("description")))
        }.filter { it.url.startsWith("http") }
    }

    fun parseTavily(json: String): List<SearchResult> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            SearchResult(it.optString("title"), it.optString("url"), it.optString("content"))
        }.filter { it.url.startsWith("http") }
    }

    fun tavilyAnswer(json: String): String? = JSONObject(json).optString("answer", "").takeIf { it.isNotBlank() }

    fun parseWikipedia(json: String, base: String): List<SearchResult> {
        val arr = JSONObject(json).optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            val title = it.optString("title")
            SearchResult(title, base + "/wiki/" + title.replace(' ', '_'), stripTags(it.optString("snippet")))
        }
    }
}

private fun OkHttpClient.fetch(request: Request): String =
    newCall(request).execute().use { r ->
        val s = r.body?.string() ?: ""
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
        s
    }

class DuckDuckGoSearch(private val client: OkHttpClient, private val url: String = "https://html.duckduckgo.com/html/") : SearchEngine {
    override val name = "DuckDuckGo"
    override suspend fun search(query: String, limit: Int): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", MOBILE_UA).header("Accept-Language", "en")
                .post(FormBody.Builder().add("q", query).build()).build()
            val html = client.fetch(req)
            if (SearchParsers.isDuckDuckGoChallenge(html)) return@withContext Result.failure(IOException("DuckDuckGo asked for a bot check"))
            Result.success(SearchParsers.parseDuckDuckGo(html).take(limit))
        } catch (e: Exception) { Result.failure(e) }
    }
}

class BingSearch(private val client: OkHttpClient, private val url: String = "https://www.bing.com/search") : SearchEngine {
    override val name = "Bing"
    override suspend fun search(query: String, limit: Int): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val u = url.toHttpUrl().newBuilder().addQueryParameter("q", query).addQueryParameter("setlang", "en").build()
            val req = Request.Builder().url(u).header("User-Agent", MOBILE_UA).header("Accept-Language", "en").build()
            Result.success(SearchParsers.parseBing(client.fetch(req)).take(limit))
        } catch (e: Exception) { Result.failure(e) }
    }
}

class WikipediaSearch(private val client: OkHttpClient, private val base: String = "https://en.wikipedia.org") : SearchEngine {
    override val name = "Wikipedia"
    override suspend fun search(query: String, limit: Int): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val u = "$base/w/api.php".toHttpUrl().newBuilder().addQueryParameter("action", "query").addQueryParameter("list", "search")
                .addQueryParameter("srsearch", query).addQueryParameter("format", "json").addQueryParameter("srlimit", limit.toString()).build()
            val req = Request.Builder().url(u).header("User-Agent", "MemGallery/0.3 (Android)").build()
            Result.success(SearchParsers.parseWikipedia(client.fetch(req), base))
        } catch (e: Exception) { Result.failure(e) }
    }
}

class BraveSearch(private val client: OkHttpClient, private val apiKey: String, private val url: String = "https://api.search.brave.com/res/v1/web/search") : SearchEngine {
    override val name = "Brave Search"
    override suspend fun search(query: String, limit: Int): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val u = url.toHttpUrl().newBuilder().addQueryParameter("q", query).addQueryParameter("count", limit.toString()).build()
            val req = Request.Builder().url(u).header("X-Subscription-Token", apiKey).header("Accept", "application/json").build()
            Result.success(SearchParsers.parseBrave(client.fetch(req)).take(limit))
        } catch (e: Exception) { Result.failure(e) }
    }
}

class TavilySearch(private val client: OkHttpClient, private val apiKey: String, private val url: String = "https://api.tavily.com/search") : SearchEngine {
    override val name = "Tavily"
    override suspend fun search(query: String, limit: Int): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("query", query).put("max_results", limit).put("search_depth", "basic")
            val req = Request.Builder().url(url).header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            Result.success(SearchParsers.parseTavily(client.fetch(req)).take(limit))
        } catch (e: Exception) { Result.failure(e) }
    }
}

data class SearchOutcome(val results: List<SearchResult>, val engine: String?, val error: String?)

/**
 * Runs a chain of engines and returns the first that gives results.
 * Keyless chain: DuckDuckGo, then Bing, then Wikipedia. A keyed provider is a chain of one, with the keyless chain behind it.
 */
class WebSearchService(private val engines: List<SearchEngine>) {
    suspend fun search(query: String, limit: Int = 6): SearchOutcome {
        val errors = mutableListOf<String>()
        for (e in engines) {
            val r = e.search(query, limit)
            val list = r.getOrNull()
            if (!list.isNullOrEmpty()) return SearchOutcome(list, e.name, null)
            errors += "${e.name}: ${r.exceptionOrNull()?.message ?: "no results"}"
            Log.w(TAG, "${e.name} gave nothing: ${errors.last()}")
        }
        return SearchOutcome(emptyList(), null, errors.joinToString("; "))
    }

    /** The text handed back to the model: numbered so it can cite [1], [2]. */
    fun format(o: SearchOutcome, startAt: Int = 1): String {
        if (o.results.isEmpty()) return "Web search failed (${o.error}). Tell the user the web is unreachable right now and answer only from what you know."
        return buildString {
            appendLine("Web results from ${o.engine}. Answer from these and cite them as [1], [2] in your answer.")
            o.results.forEachIndexed { i, r ->
                appendLine("[${i + startAt}] ${r.title}")
                appendLine(r.url)
                if (r.snippet.isNotBlank()) appendLine(r.snippet)
                appendLine()
            }
        }.trim()
    }
}

/** Markdown block appended to a chat answer so the user sees what the web part came from. */
object SourcesBlock {
    fun render(sources: List<SearchResult>): String {
        if (sources.isEmpty()) return ""
        val seen = LinkedHashSet<String>()
        val rows = sources.filter { seen.add(it.url) }.mapIndexed { i, s ->
            val host = runCatching { java.net.URI(s.url).host?.removePrefix("www.") }.getOrNull() ?: s.url
            "${i + 1}. [${s.title.replace("[", "(").replace("]", ")").take(80)}](${s.url}) - $host"
        }
        return "\n\n---\n**Sources**\n" + rows.joinToString("\n")
    }
}

/**
 * Builds the search chain from Settings on every call, so a key added a moment ago works at once.
 * AUTO (the default, no key): DuckDuckGo, then Bing, then Wikipedia. BRAVE or TAVILY: that provider first, keyless chain as the fallback.
 */
class WebSearchFactory(private val settings: com.example.memgallery.data.repository.SettingsRepository) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun engines(): List<SearchEngine> {
        val keyless = listOf(DuckDuckGoSearch(client), BingSearch(client), WikipediaSearch(client))
        val brave = settings.braveApiKeyFlow.first()
        val tavily = settings.tavilyApiKeyFlow.first()
        return when (settings.searchProviderFlow.first()) {
            "BRAVE" -> if (!brave.isNullOrBlank()) listOf<SearchEngine>(BraveSearch(client, brave)) + keyless else keyless
            "TAVILY" -> if (!tavily.isNullOrBlank()) listOf<SearchEngine>(TavilySearch(client, tavily)) + keyless else keyless
            else -> keyless
        }
    }

    suspend fun run(query: String, startAt: Int): Pair<String, List<SearchResult>> {
        val service = WebSearchService(engines())
        val outcome = service.search(query)
        return service.format(outcome, startAt) to outcome.results
    }
}
