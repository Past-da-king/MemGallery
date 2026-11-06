package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.composio.ComposioClient
import com.example.memgallery.lifeagent.composio.ComposioErrorKind
import com.example.memgallery.lifeagent.composio.ComposioException
import com.example.memgallery.lifeagent.composio.ComposioProject
import com.example.memgallery.lifeagent.composio.KeyKind
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Which header each Composio key kind goes in, and what checkKey says when Composio refuses one.
 * Every key in here is an obvious fake; nothing real is ever asserted on or logged.
 */
class ComposioKeyRoutingTest {
    private lateinit var server: MockWebServer
    private var key: String? = null
    private var storedKind: KeyKind? = null
    private var orgId: String? = null
    private val sleeps = mutableListOf<Int>()

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    private fun client(attempts: Int = 1) = ComposioClient(
        keyProvider = { key }, baseUrl = server.url("/api/v3").toString().trimEnd('/'),
        http = OkHttpClient(), maxAttempts = attempts, backoff = { sleeps += it },
        keyKindProvider = { storedKind }, orgIdProvider = { orgId },
    )

    // ---- 1. header routing ----

    @Test fun projectKeySendsOnlyTheApiKeyHeader() = runBlocking {
        key = "ak_test_123"
        orgId = "org_should_be_ignored"
        server.enqueue(MockResponse().setBody("""{"items":[],"next_cursor":null}"""))
        client().listConnectedAccounts()
        val req = server.takeRequest()
        assertEquals("ak_test_123", req.getHeader("x-api-key"))
        assertNull(req.getHeader("x-user-api-key"))
        assertNull(req.getHeader("x-org-id"))
    }

    @Test fun userKeySendsOnlyTheUserApiKeyHeaderAndItsOrg() = runBlocking {
        key = "uak_test_123"
        orgId = "org_abc"
        server.enqueue(MockResponse().setBody("""{"items":[],"next_cursor":null}"""))
        client().listConnectedAccounts()
        val req = server.takeRequest()
        assertEquals("uak_test_123", req.getHeader("x-user-api-key"))
        assertEquals("org_abc", req.getHeader("x-org-id"))
        assertNull(req.getHeader("x-api-key"))
    }

    @Test fun userKeyWithoutAStoredOrgSendsNoOrgHeader() = runBlocking {
        key = "uak_test_123"
        orgId = "   "
        server.enqueue(MockResponse().setBody("""{"items":[],"next_cursor":null}"""))
        client().listConnectedAccounts()
        val req = server.takeRequest()
        assertEquals("uak_test_123", req.getHeader("x-user-api-key"))
        assertNull(req.getHeader("x-org-id"))
        assertNull(req.getHeader("x-api-key"))
    }

    @Test fun noKeySendsNothingAndSaysToAddAKey() = runBlocking {
        key = "   "
        try {
            client().listConnectedAccounts()
            fail("a request was made with no key")
        } catch (e: ComposioException) {
            assertEquals(ComposioErrorKind.NO_KEY, e.kind)
            assertTrue("message should tell the user to add a key: " + e.message, (e.message ?: "").contains("key", ignoreCase = true))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun theStoredKindWinsOverThePrefix() = runBlocking {
        key = "ak_test_123"
        storedKind = KeyKind.USER
        server.enqueue(MockResponse().setBody("""{"items":[],"next_cursor":null}"""))
        client().listConnectedAccounts()
        val req = server.takeRequest()
        assertEquals("ak_test_123", req.getHeader("x-user-api-key"))
        assertNull(req.getHeader("x-api-key"))
    }

    // ---- 2. checkKey: the header is found by trying, never by the prefix ----

    private fun okSession() = MockResponse().setBody(
        """{"project":{"id":"proj_1","nano_id":"proj_nano_1","name":"Ayanda Personal"},""" +
            """"org":{"id":"org_x","org_id":"org_9","name":"Phaketsi Org"}}"""
    )

    private fun refused(message: String, code: Int = 401) =
        MockResponse().setResponseCode(code).setBody("""{"error":{"message":"$message"}}""")

    @Test fun aCkPrefixedKeyIsFoundOnTheProjectHeaderFirst() = runBlocking {
        // The bug he hit: his personal key starts ck_, so the prefix says nothing at all.
        key = "ck_live_abc123"
        server.enqueue(MockResponse().setBody("""{"items":[],"next_cursor":null}"""))
        val check = client().checkKey()
        assertTrue(check.ok)
        assertEquals(KeyKind.PROJECT, check.kind)
        assertEquals("x-api-key", check.header)
        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/api/v3/toolkits"))
        assertEquals("ck_live_abc123", req.getHeader("x-api-key"))
        assertNull(req.getHeader("x-user-api-key"))
        assertNull(req.getHeader("x-org-api-key"))
    }

    @Test fun aKeyThatOnlyWorksAsAPersonalKeyIsFoundOnTheSecondTry() = runBlocking {
        key = "ck_live_abc123"
        orgId = "org_abc"
        server.enqueue(refused("Invalid or revoked project API key"))
        server.enqueue(okSession())
        val check = client().checkKey()
        assertTrue(check.ok)
        assertEquals(KeyKind.USER, check.kind)
        assertEquals("x-user-api-key", check.header)
        assertEquals("Ayanda Personal", check.projectName)
        assertEquals("proj_nano_1", check.projectId)
        assertEquals("Phaketsi Org", check.orgName)
        assertEquals("org_9", check.orgId)
        assertNull(check.error)
        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("ck_live_abc123", first.getHeader("x-api-key"))
        assertTrue(second.path!!.endsWith("/api/v3/auth/session/info"))
        assertEquals("ck_live_abc123", second.getHeader("x-user-api-key"))
        assertEquals("org_abc", second.getHeader("x-org-id"))
        assertEquals(1, check.attempts.size)
        assertTrue(check.attempts.first().startsWith("x-api-key 401"))
    }

    @Test fun aKeyThatOnlyWorksAsAnOrgKeyIsFoundOnTheThirdTry() = runBlocking {
        key = "ck_live_abc123"
        server.enqueue(refused("not a project key"))
        server.enqueue(refused("Invalid or revoked user API key"))
        server.enqueue(MockResponse().setBody("""{"data":[],"next_cursor":null}"""))
        val check = client().checkKey()
        assertTrue(check.ok)
        assertEquals(KeyKind.ORG, check.kind)
        assertEquals("x-org-api-key", check.header)
        assertEquals(3, server.requestCount)
        val orgRequest = server.takeRequest().let { server.takeRequest(); server.takeRequest() }
        assertEquals("ck_live_abc123", orgRequest.getHeader("x-org-api-key"))
        assertEquals(2, check.attempts.size)
    }

    @Test fun everyHeaderFailingKeepsAllThreeStatusesAndMessages() = runBlocking {
        key = "ck_dead_key"
        server.enqueue(refused("Invalid or revoked project API key"))
        server.enqueue(refused("Invalid or revoked user API key"))
        server.enqueue(refused("Invalid org API key", 403))
        val check = client().checkKey()
        assertFalse(check.ok)
        assertNull(check.header)
        assertEquals(3, server.requestCount)
        assertEquals(3, check.attempts.size)
        assertEquals(
            listOf("x-api-key", "x-user-api-key", "x-org-api-key"),
            check.attempts.map { it.substringBefore(' ') },
        )
        val error = check.error ?: ""
        assertTrue(error.contains("Invalid or revoked project API key"))
        assertTrue(error.contains("Invalid or revoked user API key"))
        assertTrue(error.contains("Invalid org API key"))
    }

    @Test fun theKindThatWorkedLastTimeIsTriedFirst() = runBlocking {
        key = "ck_live_abc123"
        storedKind = KeyKind.ORG
        server.enqueue(MockResponse().setBody("""{"data":[],"next_cursor":null}"""))
        val check = client().checkKey()
        assertTrue(check.ok)
        assertEquals(KeyKind.ORG, check.kind)
        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/api/v3/org/owner/project/list"))
        assertEquals("ck_live_abc123", req.getHeader("x-org-api-key"))
    }

    @Test fun projectKeyOnTheProjectHeaderSurfacesA403WithComposiosMessage() = runBlocking {
        key = "ak_test_123"
        server.enqueue(refused("API key is not allowed to list toolkits", 403))
        server.enqueue(refused("not a user key"))
        server.enqueue(refused("not an org key"))
        val check = client().checkKey()
        assertFalse(check.ok)
        val error = check.error ?: ""
        assertTrue(error.contains("403"))
        assertTrue(error.contains("API key is not allowed to list toolkits"))
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/api/v3/toolkits"))
        assertTrue(req.path!!.contains("limit=1"))
        assertEquals("ak_test_123", req.getHeader("x-api-key"))
    }

    // ---- 3. listProjects ----

    @Test fun listProjectsParsesThePagedRows() = runBlocking {
        key = "uak_test_123"
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"proj_1","name":"Personal","org_id":"org_1"},""" +
                    """{"id":"proj_2","name":"Strat Edge","org_id":"org_1"}],"next_cursor":"c2"}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"proj_3","name":"Guavas","org_id":"org_2"}],"next_cursor":null}"""))
        val projects: List<ComposioProject> = client().listProjects()
        assertEquals(listOf("proj_1", "proj_2", "proj_3"), projects.map { it.id })
        assertEquals(listOf("Personal", "Strat Edge", "Guavas"), projects.map { it.name })
        assertEquals(listOf("org_1", "org_1", "org_2"), projects.map { it.orgId })
        val first = server.takeRequest()
        assertTrue(first.path!!.startsWith("/api/v3/org/project/list"))
        assertTrue(first.path!!.contains("list_all_org_projects=true"))
        assertEquals("uak_test_123", first.getHeader("x-user-api-key"))
        val second = server.takeRequest()
        assertTrue(second.path!!.contains("cursor=c2"))
    }

    @Test fun listProjectsSkipsARowWithoutAnId() = runBlocking {
        key = "uak_test_123"
        server.enqueue(MockResponse().setBody("""{"data":[{"name":"No id here"},{"id":"proj_9","name":"Kept","org_id":null}],"next_cursor":null}"""))
        val projects = client().listProjects()
        assertEquals(1, projects.size)
        assertEquals("proj_9", projects[0].id)
        assertNull(projects[0].orgId)
    }
}
