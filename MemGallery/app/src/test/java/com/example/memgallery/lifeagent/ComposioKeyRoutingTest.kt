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

    // ---- 2. checkKey ----

    @Test fun personalKeyIsCheckedAtSessionInfoAndNamesTheProjectAndOrg() = runBlocking {
        key = "uak_test_123"
        orgId = "org_abc"
        server.enqueue(
            MockResponse().setBody(
                """{"project":{"id":"proj_1","nano_id":"proj_nano_1","name":"Ayanda Personal"},""" +
                    """"org":{"id":"org_x","org_id":"org_9","name":"Phaketsi Org"}}"""
            )
        )
        val check = client().checkKey()
        assertTrue(check.ok)
        assertEquals(KeyKind.USER, check.kind)
        assertEquals("Ayanda Personal", check.projectName)
        assertEquals("proj_nano_1", check.projectId)
        assertEquals("Phaketsi Org", check.orgName)
        assertEquals("org_9", check.orgId)
        assertNull(check.error)
        assertNull(check.httpStatus)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue("personal keys are checked at session info: " + req.path, req.path!!.endsWith("/api/v3/auth/session/info"))
        assertEquals("uak_test_123", req.getHeader("x-user-api-key"))
        assertEquals("org_abc", req.getHeader("x-org-id"))
        assertNull(req.getHeader("x-api-key"))
    }

    @Test fun refusedPersonalKeyCarriesTheStatusAndComposiosOwnMessage() = runBlocking {
        key = "uak_test_123"
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid API key"}}"""))
        val check = client().checkKey()
        assertFalse(check.ok)
        assertEquals(401, check.httpStatus)
        val err = check.error ?: ""
        assertTrue("error must carry the status: " + err, err.contains("401"))
        assertTrue("error must carry Composio's message: " + err, err.contains("Invalid API key"))
        assertEquals(1, server.requestCount)
    }

    @Test fun projectKeyIsCheckedAgainstToolkitsAndSurfaces403() = runBlocking {
        key = "ak_test_123"
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"message":"API key is not allowed to list toolkits"}}""")
        )
        val check = client().checkKey()
        assertFalse(check.ok)
        assertEquals(KeyKind.PROJECT, check.kind)
        assertEquals(403, check.httpStatus)
        val err = check.error ?: ""
        assertTrue("error must carry the status: " + err, err.contains("403"))
        assertTrue("error must carry Composio's message: " + err, err.contains("API key is not allowed to list toolkits"))
        val req = server.takeRequest()
        assertTrue("project keys are checked at the toolkits list: " + req.path, req.path!!.startsWith("/api/v3/toolkits"))
        assertTrue("one toolkit is enough to check: " + req.path, req.path!!.contains("limit=1"))
        assertEquals("ak_test_123", req.getHeader("x-api-key"))
        assertNull(req.getHeader("x-user-api-key"))
    }

    @Test fun unknownPrefixIsTriedBothWaysAndEndsUpOkOnTheSecond() = runBlocking {
        key = "zz_test_123"
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Not a project key"}}"""))
        server.enqueue(MockResponse().setBody("""{"project":{"nano_id":"proj_nano_2","name":"Second Try"},"org":{"name":"Org Two"}}"""))
        val check = client().checkKey()
        assertTrue("the second try should have worked", check.ok)
        assertEquals(KeyKind.USER, check.kind)
        assertEquals("Second Try", check.projectName)
        assertEquals("Org Two", check.orgName)
        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertTrue("project is tried first: " + first.path, first.path!!.startsWith("/api/v3/toolkits"))
        assertEquals("zz_test_123", first.getHeader("x-api-key"))
        assertNull(first.getHeader("x-user-api-key"))
        assertTrue("user is tried second: " + second.path, second.path!!.endsWith("/api/v3/auth/session/info"))
        assertEquals("zz_test_123", second.getHeader("x-user-api-key"))
        assertNull(second.getHeader("x-api-key"))
    }

    @Test fun unknownPrefixThatFailsBothWaysKeepsTheLastStatusAndMessage() = runBlocking {
        key = "zz_test_123"
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Not a project key"}}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"Not a user key either"}}"""))
        val check = client().checkKey()
        assertFalse(check.ok)
        assertEquals(403, check.httpStatus)
        assertTrue((check.error ?: "").contains("Not a user key either"))
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
