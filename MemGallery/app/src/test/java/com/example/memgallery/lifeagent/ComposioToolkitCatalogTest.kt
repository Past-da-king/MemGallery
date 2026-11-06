package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.composio.ComposioClient
import com.example.memgallery.lifeagent.composio.ComposioErrorKind
import com.example.memgallery.lifeagent.composio.ComposioException
import com.example.memgallery.lifeagent.composio.ReadToolRules
import com.example.memgallery.lifeagent.composio.ToolkitPage
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
 * The toolkit catalog: a paged list of every toolkit Composio offers, with its logo, categories and query parameters,
 * plus the read gate that decides whether any toolkit's tool may ever run.
 */
class ComposioToolkitCatalogTest {
    private lateinit var server: MockWebServer
    private var key: String? = "ak_test_123"

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    private fun client(attempts: Int = 1) = ComposioClient(
        keyProvider = { key }, baseUrl = server.url("/api/v3").toString().trimEnd('/'),
        http = OkHttpClient(), maxAttempts = attempts, backoff = { },
    )

    /** One toolkit the way Composio sends it: slug, name, meta.logo, meta.categories, auth_schemes. */
    private fun toolkit(slug: String, name: String, categories: List<String>, logo: String? = null) = JsonObject().apply {
        addProperty("slug", slug)
        addProperty("name", name)
        addProperty("toolkit_type", "official")
        add("auth_schemes", JsonArray().apply { add("OAUTH2") })
        addProperty("no_auth", false)
        add("meta", JsonObject().apply {
            addProperty("description", name + " for the Life Agent")
            addProperty("logo", logo ?: ("https://logos.composio.dev/api/" + slug))
            add("categories", JsonArray().apply {
                categories.forEach { c -> add(JsonObject().apply { addProperty("name", c) }) }
            })
        })
    }

    /** 55 realistic toolkits, the first page. */
    private fun firstPage(): JsonArray {
        val rows = listOf(
            Triple("gmail", "Gmail", listOf("email")),
            Triple("googlecalendar", "Google Calendar", listOf("productivity", "calendar")),
            Triple("slack", "Slack", listOf("team", "communication")),
            Triple("notion", "Notion", listOf("productivity")),
            Triple("github", "GitHub", listOf("developer", "code")),
            Triple("hubspot", "HubSpot", listOf("crm", "sales")),
            Triple("jira", "Jira", listOf("project management")),
            Triple("linear", "Linear", listOf("project management")),
            Triple("asana", "Asana", listOf("project management")),
            Triple("trello", "Trello", listOf("project management")),
            Triple("clickup", "ClickUp", listOf("project management")),
            Triple("discord", "Discord", listOf("communication")),
            Triple("telegram", "Telegram", listOf("communication")),
            Triple("zoom", "Zoom", listOf("video")),
            Triple("dropbox", "Dropbox", listOf("file management")),
            Triple("googledrive", "Google Drive", listOf("file management")),
            Triple("googlesheets", "Google Sheets", listOf("spreadsheet")),
            Triple("google_docs", "Google Docs", listOf("documents")),
            Triple("excel", "Excel", listOf("spreadsheet")),
            Triple("airtable", "Airtable", listOf("database")),
            Triple("stripe", "Stripe", listOf("payments")),
            Triple("shopify", "Shopify", listOf("ecommerce")),
            Triple("salesforce", "Salesforce", listOf("crm")),
            Triple("pipedrive", "Pipedrive", listOf("crm")),
            Triple("zendesk", "Zendesk", listOf("customer support")),
            Triple("intercom", "Intercom", listOf("customer support")),
            Triple("freshdesk", "Freshdesk", listOf("customer support")),
            Triple("figma", "Figma", listOf("design")),
            Triple("canva", "Canva", listOf("design")),
            Triple("miro", "Miro", listOf("design")),
            Triple("confluence", "Confluence", listOf("documents")),
            Triple("monday", "Monday.com", listOf("project management")),
            Triple("calendly", "Calendly", listOf("scheduling")),
            Triple("typeform", "Typeform", listOf("forms")),
            Triple("mailchimp", "Mailchimp", listOf("marketing")),
            Triple("sendgrid", "SendGrid", listOf("email")),
            Triple("twilio", "Twilio", listOf("communication")),
            Triple("whatsapp", "WhatsApp", listOf("communication")),
            Triple("linkedin", "LinkedIn", listOf("social media")),
            Triple("twitter", "Twitter", listOf("social media")),
            Triple("facebook", "Facebook", listOf("social media")),
            Triple("instagram", "Instagram", listOf("social media")),
            Triple("youtube", "YouTube", listOf("video")),
            Triple("spotify", "Spotify", listOf("music")),
            Triple("todoist", "Todoist", listOf("task management")),
            Triple("obsidian", "Obsidian", listOf("notes")),
            Triple("evernote", "Evernote", listOf("notes")),
            Triple("google_tasks", "Google Tasks", listOf("task management")),
            Triple("google_maps", "Google Maps", listOf("location")),
            Triple("quickbooks", "QuickBooks", listOf("accounting")),
            Triple("xero", "Xero", listOf("accounting")),
            Triple("zoho_crm", "Zoho CRM", listOf("crm")),
            Triple("servicenow", "ServiceNow", listOf("IT service management")),
            Triple("sentry", "Sentry", listOf("developer")),
            Triple("vercel", "Vercel", listOf("developer")),
        )
        return JsonArray().apply { rows.forEach { (slug, name, cats) -> add(toolkit(slug, name, cats)) } }
    }

    private fun page(items: JsonArray, cursor: String?) = JsonObject().apply {
        add("items", items)
        if (cursor == null) add("next_cursor", com.google.gson.JsonNull.INSTANCE) else addProperty("next_cursor", cursor)
        addProperty("total_items", items.size())
    }.toString()

    private val secondPageRows = listOf(
        Triple("google_analytics", "Google Analytics", listOf("analytics")),
        Triple("mixpanel", "Mixpanel", listOf("analytics")),
        Triple("amplitude", "Amplitude", listOf("analytics")),
        Triple("datadog", "Datadog", listOf("monitoring")),
        Triple("pagerduty", "PagerDuty", listOf("monitoring")),
    )

    // ---- 4. catalog paging ----

    @Test fun parsesFiftyFiveToolkitsWithNamesLogosAndCategories() = runBlocking {
        server.enqueue(MockResponse().setBody(page(firstPage(), null)))
        val result: ToolkitPage = client().listToolkits()
        assertEquals(55, result.items.size)
        assertNull(result.nextCursor)
        val slack = result.items.first { it.slug == "slack" }
        assertEquals("Slack", slack.name)
        assertEquals("https://logos.composio.dev/api/slack", slack.logoUrl)
        assertEquals(listOf("team", "communication"), slack.categories)
        assertEquals(listOf("OAUTH2"), slack.authSchemes)
        assertEquals("official", slack.toolkitType)
        assertEquals("Slack for the Life Agent", slack.description)
        assertFalse(slack.noAuth)
        val names = result.items.map { it.name }
        for (expected in listOf("Gmail", "Google Calendar", "Notion", "GitHub", "HubSpot", "Jira", "Linear", "Salesforce")) {
            assertTrue("missing " + expected, names.contains(expected))
        }
        assertEquals(55, names.toSet().size)
        assertTrue("every row needs a logo", result.items.all { !it.logoUrl.isNullOrBlank() })
        assertTrue("every row needs a category", result.items.all { it.categories.isNotEmpty() })
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/api/v3/toolkits"))
        assertTrue("the page size is sent: " + req.path, req.path!!.contains("limit=100"))
        assertEquals("ak_test_123", req.getHeader("x-api-key"))
    }

    @Test fun followsNextCursorToASecondPage() = runBlocking {
        val second = JsonArray().apply { secondPageRows.forEach { (s, n, c) -> add(toolkit(s, n, c)) } }
        server.enqueue(MockResponse().setBody(page(firstPage(), "cursor_2")))
        server.enqueue(MockResponse().setBody(page(second, null)))
        val first = client().listToolkits()
        assertEquals(55, first.items.size)
        assertEquals("cursor_2", first.nextCursor)
        val next = client().listToolkits(cursor = first.nextCursor)
        assertEquals(5, next.items.size)
        assertNull(next.nextCursor)
        assertEquals("Google Analytics", next.items[0].name)
        server.takeRequest()
        val req = server.takeRequest()
        assertTrue("the cursor is sent: " + req.path, req.path!!.contains("cursor=cursor_2"))
    }

    @Test fun sendsTheSearchAndCategoryTheUserTyped() = runBlocking {
        server.enqueue(MockResponse().setBody(page(JsonArray(), null)))
        client().listToolkits(search = "mail", category = "productivity", limit = 25)
        val req = server.takeRequest()
        val path = req.path!!
        assertTrue("search is sent: " + path, path.contains("search=mail"))
        assertTrue("category is sent: " + path, path.contains("category=productivity"))
        assertTrue("limit is sent: " + path, path.contains("limit=25"))
        assertFalse("no cursor unless paging: " + path, path.contains("cursor="))
    }

    @Test fun anEmptyCatalogIsAnEmptyPageNotAnError() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[],"next_cursor":null}"""))
        val result = client().listToolkits()
        assertTrue(result.items.isEmpty())
        assertNull(result.nextCursor)
    }

    // ---- 5. the read gate, for any toolkit ----

    @Test fun readsAreAllowedForAnyToolkit() {
        val allowed = listOf(
            "GITHUB_GET_ISSUE", "NOTION_LIST_PAGES", "JIRA_SEARCH_ISSUES", "GMAIL_READ_MESSAGE",
            "SLACK_FETCH_CONVERSATION_HISTORY", "GOOGLECALENDAR_EVENTS_LIST", "LINEAR_GET_ISSUES",
            "HUBSPOT_LIST_CONTACTS", "NOTION_FETCH_PAGE", "GMAIL_FETCH_EMAILS", "STRIPE_GET_CUSTOMER",
            "asana_list_projects", "GOOGLE_SHEETS_READ_ROWS",
        )
        for (slug in allowed) assertTrue("should be a read tool: " + slug, ReadToolRules.isReadTool(slug))
    }

    @Test fun writesAreRefusedForAnyToolkit() {
        val refused = listOf(
            "NOTION_CREATE_PAGE", "SLACK_SEND_MESSAGE", "GMAIL_DELETE_MESSAGE", "SLACK_UPDATE_MESSAGE",
            "GMAIL_POST_LABEL", "GMAIL_ARCHIVE_EMAIL", "SLACK_REPLY_TO_THREAD", "SLACK_INVITE_USER",
            "JIRA_ADD_COMMENT", "HUBSPOT_CREATE_CONTACT", "GITHUB_CREATE_PULL_REQUEST", "CALENDAR_UPDATE_EVENT",
            "slack_send_message", "notion_create_page",
        )
        for (slug in refused) assertFalse("must be refused: " + slug, ReadToolRules.isReadTool(slug))
    }

    @Test fun blankAndOddSlugsAreNotReadTools() {
        assertFalse(ReadToolRules.isReadTool(null))
        assertFalse(ReadToolRules.isReadTool(""))
        assertFalse(ReadToolRules.isReadTool("   "))
        assertFalse(ReadToolRules.isReadTool("GITHUB"))
    }

    @Test fun executeRefusesAWriteSlugAndSendsNoRequestAtAll() = runBlocking {
        for (slug in listOf("NOTION_CREATE_PAGE", "SLACK_SEND_MESSAGE", "GMAIL_DELETE_MESSAGE", "JIRA_ADD_COMMENT")) {
            try {
                client().execute(slug, "ca_test_1", null, emptyMap())
                fail(slug + " ran")
            } catch (e: ComposioException) {
                assertEquals(ComposioErrorKind.BLOCKED, e.kind)
                assertTrue("the refusal should name the tool: " + e.message, (e.message ?: "").contains(slug))
            }
        }
        assertEquals("a refused tool must not reach the network", 0, server.requestCount)
    }

    @Test fun executeStillRunsAReadTool() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"issues":[]},"successful":true,"error":null}"""))
        val result = client().execute("GITHUB_GET_ISSUE", "ca_test_1", null, emptyMap())
        assertTrue(result.successful)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v3/tools/execute/GITHUB_GET_ISSUE", req.path)
    }
}
