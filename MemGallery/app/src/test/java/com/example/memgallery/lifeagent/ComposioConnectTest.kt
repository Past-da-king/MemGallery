package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.composio.ConnectRules
import com.example.memgallery.lifeagent.composio.ComposioConnectClient
import com.example.memgallery.lifeagent.composio.ComposioErrorKind
import com.example.memgallery.lifeagent.composio.ComposioException
import com.example.memgallery.lifeagent.composio.KeyKind
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The consumer key (`ck_`) road: Composio Connect over MCP. Every key in here is an obvious fake and every reply is
 * made up for the test; nothing is sent anywhere.
 */
class ComposioConnectTest {
    private lateinit var server: MockWebServer
    private var key: String? = "ck_live_abc123"

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    private fun client() = ComposioConnectClient(
        keyProvider = { key },
        http = OkHttpClient(),
        mcpUrl = server.url("/mcp").toString(),
    )

    // ---- reply builders ----

    private fun sse(resultJson: String, id: Int): MockResponse = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(": connected\n\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$resultJson}\n\n")

    private fun plain(resultJson: String, id: Int): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$resultJson}")

    /** The `initialize` result, then the answer to the initialized notification. */
    private fun handshake(useSse: Boolean = true) {
        val init = """{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"mcp","version":"0.1.0"}}"""
        server.enqueue(if (useSse) sse(init, 1) else plain(init, 1))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
    }

    /** One tools/call answer: Composio wraps its payload as text inside content[0].text. */
    private fun toolCall(inner: JsonObject, id: Int, useSse: Boolean = true, isError: Boolean = false): MockResponse {
        val result = JsonObject().apply {
            add("content", JsonArray().apply {
                add(JsonObject().apply { addProperty("type", "text"); addProperty("text", Gson().toJson(inner)) })
            })
            addProperty("isError", isError)
        }
        return if (useSse) sse(Gson().toJson(result), id) else plain(Gson().toJson(result), id)
    }

    private fun payload(data: JsonObject?, successful: Boolean = true, error: String? = null): JsonObject =
        JsonObject().apply {
            addProperty("successful", successful)
            if (data != null) add("data", data) else add("data", JsonObject())
            if (error != null) addProperty("error", error) else add("error", com.google.gson.JsonNull.INSTANCE)
        }

    /** A search answer naming [count] toolkits, half of them connected. */
    private fun searchWithToolkits(count: Int, connectedEvery: Int = 5): JsonObject {
        val data = JsonObject()
        data.add("toolkit_connection_statuses", JsonArray().apply {
            for (i in 1..count) add(JsonObject().apply {
                addProperty("toolkit", "app$i")
                addProperty("description", "App number $i does things")
                addProperty("has_active_connection", connectedEvery > 0 && i % connectedEvery == 0)
            })
        })
        data.add("tool_schemas", JsonObject().apply {
            for (i in 1..count) add("APP${i}_LIST_ITEMS", JsonObject().apply {
                addProperty("toolkit", "APP$i")
                addProperty("tool_slug", "APP${i}_LIST_ITEMS")
                addProperty("description", "List items in app $i")
            })
        })
        return payload(data)
    }

    // ---- the catalog ----

    @Test fun theCatalogIsBuiltFromASearchAndHoldsEveryToolkitItNames() = runBlocking {
        handshake()
        server.enqueue(toolCall(searchWithToolkits(55), id = 2))
        val page = client().listToolkits(search = "read my emails")
        assertEquals(55, page.items.size)
        assertTrue(page.items.all { it.logoUrl?.contains("logos.composio.dev") == true })
        assertEquals(page.items.map { it.name.lowercase() }.sorted(), page.items.map { it.name.lowercase() })
        assertEquals("App7", page.items.first { it.slug == "app7" }.name)
    }

    @Test fun plainJsonRepliesWorkAsWellAsEventStreams() = runBlocking {
        handshake(useSse = false)
        server.enqueue(toolCall(searchWithToolkits(6), id = 2, useSse = false))
        val page = client().listToolkits(search = "read my emails")
        assertEquals(6, page.items.size)
    }

    @Test fun theReadToolsOfOneToolkitComeFromItsOwnSchemas() = runBlocking {
        handshake()
        val data = JsonObject().apply {
            add("tool_schemas", JsonObject().apply {
                add("GMAIL_FETCH_EMAILS", JsonObject().apply {
                    addProperty("toolkit", "GMAIL"); addProperty("description", "Fetch emails")
                })
                add("GMAIL_SEND_EMAIL", JsonObject().apply {
                    addProperty("toolkit", "GMAIL"); addProperty("description", "Send an email")
                })
                add("SLACK_LIST_CHANNELS", JsonObject().apply {
                    addProperty("toolkit", "SLACK"); addProperty("description", "List channels")
                })
            })
        }
        server.enqueue(toolCall(payload(data), id = 2))
        val tools = client().listTools("gmail")
        assertEquals(2, tools.items.size)
        assertTrue(tools.items.any { it.slug == "GMAIL_FETCH_EMAILS" })
    }

    // ---- connections ----

    @Test fun connectedAccountsComeFromTheToolkitStatusesThenTheAccountList() = runBlocking {
        handshake()
        // Discovery searches one query per known app plus the general seeds (7 in all), then asks each connected
        // toolkit for its accounts. Ids run 2..8 for the searches, 9 for the account list.
        for (id in 2..8) server.enqueue(toolCall(searchWithToolkits(5, connectedEvery = 5), id = id))
        val accountsData = JsonObject().apply {
            add("results", JsonObject().apply {
                add("app5", JsonObject().apply {
                    addProperty("toolkit", "app5")
                    addProperty("status", "active")
                    add("accounts", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("id", "ca_app5_1"); addProperty("alias", "work"); addProperty("status", "ACTIVE")
                            addProperty("toolkit", "app5")
                        })
                    })
                })
            })
        }
        server.enqueue(toolCall(payload(accountsData), id = 9))
        val accounts = client().listConnectedAccounts()
        assertEquals(1, accounts.size)
        assertEquals("ca_app5_1", accounts.first().id)
        assertEquals("work", accounts.first().label)
        assertTrue(accounts.first().active)
    }

    @Test fun aConnectLinkComesStraightFromManageConnections() = runBlocking {
        handshake()
        val data = JsonObject().apply {
            add("results", JsonObject().apply {
                add("gmail", JsonObject().apply {
                    addProperty("status", "initiated")
                    addProperty("redirect_url", "https://connect.composio.dev/link/lk_test_1")
                })
            })
        }
        server.enqueue(toolCall(payload(data), id = 2))
        val link = client().createConnectLink("gmail", userId = "memgallery")
        assertEquals("https://connect.composio.dev/link/lk_test_1", link.redirectUrl)
    }

    // ---- reads ----

    @Test fun aReadToolRunsThroughMultiExecute() = runBlocking {
        handshake()
        val data = JsonObject().apply {
            add("results", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("tool_slug", "GMAIL_FETCH_EMAILS")
                    add("response", JsonObject().apply {
                        addProperty("successful", true)
                        add("data", JsonObject().apply {
                            add("messages", JsonArray().apply { add(JsonObject().apply { addProperty("id", "m1") }) })
                        })
                    })
                })
            })
        }
        server.enqueue(toolCall(payload(data), id = 2))
        val result = client().execute("GMAIL_FETCH_EMAILS", "ca_1", null, mapOf("max_results" to 2))
        assertTrue(result.successful)
        assertNotNull(result.data)
        assertTrue(result.data.toString().contains("m1"))
        server.takeRequest() // initialize
        server.takeRequest() // notifications/initialized
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("COMPOSIO_MULTI_EXECUTE_TOOL"))
        assertTrue(body.contains("GMAIL_FETCH_EMAILS"))
    }

    @Test fun aRefusedReadIsReportedWithComposiosOwnLine() = runBlocking {
        handshake()
        val data = JsonObject().apply {
            add("results", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("tool_slug", "GMAIL_FETCH_EMAILS")
                    addProperty("error", "No active connection found for toolkit(s) 'gmail'")
                })
            })
        }
        server.enqueue(toolCall(payload(data, successful = true), id = 2))
        val result = client().execute("GMAIL_FETCH_EMAILS", "ca_1", null, emptyMap())
        assertFalse(result.successful)
        assertTrue(result.error!!.contains("No active connection"))
    }

    // ---- the hard blocks ----

    @Test fun theCodeRunningToolsAreRefusedBeforeAnyRequest() = runBlocking {
        val c = client()
        for (name in listOf(
            "COMPOSIO_REMOTE_BASH_TOOL",
            "COMPOSIO_REMOTE_WORKBENCH",
            "COMPOSIO_PROXY_EXECUTE",
            "COMPOSIO_SEARCH_SKILLS",
            "COMPOSIO_USE_SKILL",
            "COMPOSIO_SUBMIT_FEEDBACK",
        )) {
            try {
                c.callTool(name)
                fail("$name should have been refused")
            } catch (e: ComposioException) {
                assertEquals(ComposioErrorKind.BLOCKED, e.kind)
            }
        }
        // A tool nobody vetted is refused too: the list of allowed tools is a whitelist, not a blacklist.
        try {
            c.callTool("COMPOSIO_SOMETHING_NEW")
            fail("an unknown meta tool should have been refused")
        } catch (e: ComposioException) {
            assertEquals(ComposioErrorKind.BLOCKED, e.kind)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun aWriteSlugInsideMultiExecuteIsRefusedBeforeAnyRequest() = runBlocking {
        val c = client()
        for (slug in listOf("SLACK_POST_MESSAGE", "GMAIL_SEND_EMAIL", "NOTION_CREATE_PAGE", "github_delete_repo")) {
            try {
                c.execute(slug, "ca_1", null, emptyMap())
                fail("$slug should have been refused")
            } catch (e: ComposioException) {
                assertEquals(ComposioErrorKind.BLOCKED, e.kind)
            }
        }
        assertEquals(0, server.requestCount)
        assertEquals(0, ConnectRules.allowed.intersect(ConnectRules.blocked).size)
    }

    // ---- the key ----

    @Test fun aConsumerKeyIsCheckedByTheMcpHandshakeAndTheHeaderIsSent() = runBlocking {
        handshake()
        val check = client().checkKey()
        assertTrue(check.ok)
        assertEquals(KeyKind.CONSUMER, check.kind)
        assertEquals("x-consumer-api-key", check.header)
        assertEquals("Consumer key (Composio Connect)", check.kind.label)
        val req = server.takeRequest()
        assertEquals("ck_live_abc123", req.getHeader("x-consumer-api-key"))
        assertTrue(req.getHeader("Accept")!!.contains("text/event-stream"))
    }

    @Test fun aRefusedConsumerKeyShowsComposiosStatusAndMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid consumer key"}}"""))
        val check = client().checkKey()
        assertFalse(check.ok)
        assertTrue(check.error!!.contains("401"))
        assertTrue(check.attempts.first().startsWith("x-consumer-api-key"))
    }

    @Test fun thePrefixOnlyHintsAtTheKind() {
        assertEquals(KeyKind.CONSUMER, KeyKind.of("ck_live_abc123"))
        assertEquals(KeyKind.PROJECT, KeyKind.of("ak_test_123"))
        assertEquals(KeyKind.USER, KeyKind.of("uak_test_123"))
        assertEquals(KeyKind.UNKNOWN, KeyKind.of("zq_whatever"))
    }
}
