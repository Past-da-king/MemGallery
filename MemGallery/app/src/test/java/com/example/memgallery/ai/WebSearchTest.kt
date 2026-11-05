package com.example.memgallery.ai

import com.example.memgallery.data.remote.search.BraveSearch
import com.example.memgallery.data.remote.search.SearchEngine
import com.example.memgallery.data.remote.search.SearchParsers
import com.example.memgallery.data.remote.search.SearchResult
import com.example.memgallery.data.remote.search.TavilySearch
import com.example.memgallery.data.remote.search.WebSearchService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WebSearchTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun duckDuckGoParserUnwrapsRedirectsAndSkipsAds() {
        val r = SearchParsers.parseDuckDuckGo(res("ddg_serp.html"))
        assertEquals(2, r.size)
        assertEquals("https://api-docs.deepseek.com/", r[0].url)
        assertEquals("DeepSeek API Docs", r[0].title)
        assertTrue(r[0].snippet.contains("OpenAI-compatible"))
        assertEquals("https://example.org/direct", r[1].url)
    }

    @Test fun duckDuckGoChallengePageIsRecognisedAsBlocked() {
        assertTrue(SearchParsers.isDuckDuckGoChallenge(res("ddg_challenge.html")))
        assertFalse(SearchParsers.isDuckDuckGoChallenge(res("ddg_serp.html")))
    }

    @Test fun bingParserReadsRealSerpFixture() {
        val r = SearchParsers.parseBing(res("bing_serp.html"))
        assertEquals(5, r.size)
        assertTrue(r.all { it.url.startsWith("http") && it.title.isNotBlank() })
        assertTrue(r[0].title.contains("DeepSeek"))
    }

    @Test fun bingRedirectLinksAreDecoded() {
        val target = "https://example.com/page?a=1"
        val enc = "a1" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(target.toByteArray())
        assertEquals(target, SearchParsers.unwrapBing("https://www.bing.com/ck/a?!&&p=x&u=$enc&ntb=1"))
        assertEquals("https://plain.example/", SearchParsers.unwrapBing("https://plain.example/"))
    }

    @Test fun braveAndTavilyJsonParse() {
        val brave = """{"web":{"results":[{"title":"A","url":"https://a.example","description":"da <strong>x</strong>"},{"title":"B","url":"https://b.example","description":"db"}]}}"""
        val b = SearchParsers.parseBrave(brave)
        assertEquals(listOf("https://a.example", "https://b.example"), b.map { it.url })
        assertEquals("da x", b[0].snippet)
        val tavily = """{"answer":"short answer","results":[{"title":"T","url":"https://t.example","content":"ct","score":0.9}]}"""
        val t = SearchParsers.parseTavily(tavily)
        assertEquals("https://t.example", t[0].url)
        assertEquals("ct", t[0].snippet)
        assertEquals("short answer", SearchParsers.tavilyAnswer(tavily))
    }

    @Test fun wikipediaParserBuildsPageUrls() {
        val json = """{"query":{"search":[{"title":"DeepSeek","pageid":78452842,"snippet":"Hangzhou <span class=\"searchmatch\">DeepSeek</span> Co."}]}}"""
        val r = SearchParsers.parseWikipedia(json, "https://en.wikipedia.org")
        assertEquals("https://en.wikipedia.org/wiki/DeepSeek", r[0].url)
        assertEquals("Hangzhou DeepSeek Co.", r[0].snippet)
    }

    private class Fake(override val name: String, val out: Result<List<SearchResult>>) : SearchEngine {
        var calls = 0
        override suspend fun search(query: String, limit: Int): Result<List<SearchResult>> { calls++; return out }
    }

    @Test fun keylessChainFallsThroughBlockedEnginesToTheFirstOneThatAnswers() = runBlocking {
        val a = Fake("ddg", Result.failure(Exception("blocked")))
        val b = Fake("bing", Result.success(emptyList()))
        val c = Fake("wiki", Result.success(listOf(SearchResult("W", "https://w.example", "snip"))))
        val svc = WebSearchService(listOf(a, b, c))
        val out = svc.search("q")
        assertEquals(1, out.results.size)
        assertEquals("wiki", out.engine)
        assertEquals(1, a.calls); assertEquals(1, b.calls); assertEquals(1, c.calls)
    }

    @Test fun allEnginesFailingGivesAReadableErrorNotACrash() = runBlocking {
        val svc = WebSearchService(listOf(Fake("x", Result.failure(Exception("boom")))))
        val out = svc.search("q")
        assertTrue(out.results.isEmpty())
        assertTrue(out.error!!.contains("boom"))
        assertTrue(svc.format(out).startsWith("Web search failed"))
    }

    @Test fun formattedResultsAreNumberedWithUrlsSoTheModelCanCite() = runBlocking {
        val svc = WebSearchService(listOf(Fake("x", Result.success(listOf(SearchResult("T1", "https://one.example", "s1"), SearchResult("T2", "https://two.example", "s2"))))))
        val text = svc.format(svc.search("q"))
        assertTrue(text.contains("[1] T1"))
        assertTrue(text.contains("https://two.example"))
        assertTrue(text.contains("cite"))
    }

    @Test fun braveEngineSendsTokenHeaderAndQuery() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"web":{"results":[{"title":"A","url":"https://a.example","description":"d"}]}}"""))
        server.start()
        val r = BraveSearch(OkHttpClient(), "KEY123", server.url("/res/v1/web/search").toString()).search("hello world", 3)
        val req = server.takeRequest()
        assertEquals("KEY123", req.getHeader("X-Subscription-Token"))
        assertTrue(req.path!!.contains("q=hello+world") || req.path!!.contains("q=hello%20world"))
        assertEquals(1, r.getOrThrow().size)
        server.shutdown()
    }

    @Test fun tavilyEngineSendsBearerAndJsonBody() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"results":[{"title":"T","url":"https://t.example","content":"c"}]}"""))
        server.start()
        val r = TavilySearch(OkHttpClient(), "TKEY", server.url("/search").toString()).search("hello", 4)
        val req = server.takeRequest()
        assertEquals("Bearer TKEY", req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains("\"query\":\"hello\""))
        assertEquals("https://t.example", r.getOrThrow()[0].url)
        server.shutdown()
    }
}
