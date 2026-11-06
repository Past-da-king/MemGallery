package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.composio.ComposioClient
import com.example.memgallery.lifeagent.composio.ComposioErrorKind
import com.example.memgallery.lifeagent.composio.ComposioException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ComposioClientTest {
    private lateinit var server: MockWebServer
    private var key: String? = "test-key-not-real"
    private val sleeps = mutableListOf<Int>()

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    private fun client(attempts: Int = 3) = ComposioClient(
        keyProvider = { key }, baseUrl = server.url("/api/v3").toString().trimEnd('/'),
        http = OkHttpClient(), maxAttempts = attempts, backoff = { sleeps += it },
    )

    @Test fun listsConnectedAccountsAcrossPagesWithKeyHeader() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"ca_1","toolkit":{"slug":"gmail"},"user_id":"u1","status":"ACTIVE","alias":"work"}],"next_cursor":"c2"}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"ca_2","toolkit":{"slug":"slack"},"status":"EXPIRED"}],"next_cursor":null}"""))
        val accounts = client().listConnectedAccounts()
        assertEquals(listOf("ca_1", "ca_2"), accounts.map { it.id })
        assertTrue(accounts[0].active); assertFalse(accounts[1].active)
        assertEquals("work", accounts[0].label)
        val r1 = server.takeRequest(); val r2 = server.takeRequest()
        assertEquals("GET", r1.method)
        assertEquals("test-key-not-real", r1.getHeader("x-api-key"))
        assertTrue(r1.path!!.startsWith("/api/v3/connected_accounts"))
        assertTrue(r2.path!!.contains("cursor=c2"))
    }

    @Test fun executesAReadToolWithTheContractBody() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"messages":[]},"error":null,"successful":true,"log_id":"l"}"""))
        val r = client().execute("GMAIL_FETCH_EMAILS", "ca_1", "u1", mapOf("max_results" to 5, "verbose" to true))
        assertTrue(r.successful)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v3/tools/execute/GMAIL_FETCH_EMAILS", req.path)
        val body = com.google.gson.JsonParser.parseString(req.body.readUtf8()).asJsonObject
        assertEquals("ca_1", body.get("connected_account_id").asString)
        assertEquals("u1", body.get("user_id").asString)
        assertEquals(5, body.getAsJsonObject("arguments").get("max_results").asInt)
    }

    @Test fun toolFailureComesBackAsUnsuccessfulWithItsMessage() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{},"error":"Token expired","successful":false}"""))
        val r = client().execute("SLACK_LIST_ALL_CHANNELS", "ca", null, emptyMap())
        assertFalse(r.successful); assertEquals("Token expired", r.error)
    }

    @Test fun refusesAnyToolThatIsNotAReadToolAndSendsNothing() = runBlocking {
        for (slug in listOf("GMAIL_SEND_EMAIL", "GMAIL_DELETE_MESSAGE", "SLACK_SEND_MESSAGE", "CLICKUP_CREATE_TASK", "GOOGLECALENDAR_CREATE_EVENT")) {
            try { client().execute(slug, "ca", null, emptyMap()); fail("$slug ran") }
            catch (e: ComposioException) { assertEquals(ComposioErrorKind.BLOCKED, e.kind) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun noKeyMeansNoRequest() = runBlocking {
        key = "  "
        try { client().listConnectedAccounts(); fail() } catch (e: ComposioException) { assertEquals(ComposioErrorKind.NO_KEY, e.kind) }
        assertEquals(0, server.requestCount)
    }

    @Test fun badKeyIsAuthErrorAndNotRetried() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        try { client().listConnectedAccounts(); fail() } catch (e: ComposioException) { assertEquals(ComposioErrorKind.AUTH, e.kind) }
        assertEquals(1, server.requestCount)
    }

    @Test fun retriesServerErrorsAndRateLimitsWithBackoffThenSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody("""{"items":[],"next_cursor":null}"""))
        assertEquals(0, client().listConnectedAccounts().size)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(1, 2), sleeps)
    }

    @Test fun givesUpAfterTheLastAttemptWithAReadableMessage() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        try { client().listConnectedAccounts(); fail() } catch (e: ComposioException) {
            assertEquals(ComposioErrorKind.SERVER, e.kind); assertTrue(e.message!!.contains("500"))
        }
        assertEquals(3, server.requestCount)
    }

    @Test fun networkDropIsRetriedThenReported() = runBlocking {
        server.shutdown()
        try { client(2).listConnectedAccounts(); fail() } catch (e: ComposioException) { assertEquals(ComposioErrorKind.NETWORK, e.kind) }
        assertEquals(listOf(1), sleeps)
    }

    @Test fun garbageBodyIsABadResponseNotACrash() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>oops</html>"))
        try { client().listConnectedAccounts(); fail() } catch (e: ComposioException) { assertEquals(ComposioErrorKind.BAD_RESPONSE, e.kind) }
    }
}
