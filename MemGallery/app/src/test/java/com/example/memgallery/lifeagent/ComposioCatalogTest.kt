package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.composio.ComposioCatalog
import com.example.memgallery.lifeagent.composio.ComposioErrorKind
import com.example.memgallery.lifeagent.composio.ComposioException
import com.example.memgallery.lifeagent.composio.RawType
import com.example.memgallery.lifeagent.data.LifeApp
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ComposioCatalogTest {
    private fun j(s: String) = JsonParser.parseString(s)

    @Test fun everyListedToolIsARead() {
        val bad = Regex("(CREATE|SEND|DELETE|UPDATE|REPLY|POST|MOVE|REMOVE|ADD|EDIT|PATCH|DRAFT|FORWARD|ARCHIVE|TRASH|LABEL)")
        ComposioCatalog.READ_TOOLS.forEach { assertFalse("$it looks like a write", bad.containsMatchIn(it.substringAfter('_'))) }
        assertTrue(ComposioCatalog.READ_TOOLS.all { it.contains("FETCH") || it.contains("LIST") || it.contains("GET") })
    }

    @Test fun gmailMessagesParseWithPreviewFallbackAndLabels() {
        val items = ComposioCatalog.parseGmail(j("""{"messages":[
          {"messageId":"m1","threadId":"t1","subject":"Signed lease","sender":"Nomsa Mahlangu <nomsa@umoya.example>","messageText":"Please send it by Sat 7 Nov 16:00","labelIds":["INBOX","CATEGORY_PERSONAL"],"messageTimestamp":"2026-11-06T08:00:00Z"},
          {"messageId":"m2","preview":{"subject":"From preview","body":"hello"},"sender":"a@b.example"},
          {"nope":1}],"nextPageToken":"p2"}"""))
        assertEquals(2, items.size)
        assertEquals("Signed lease", items[0].title); assertEquals(RawType.EMAIL, items[0].type)
        assertEquals(listOf("INBOX", "CATEGORY_PERSONAL"), items[0].labels)
        assertTrue(items[0].occurredAt > 0)
        assertEquals("From preview", items[1].title); assertEquals("hello", items[1].body)
    }

    @Test fun dataAsAJsonStringOrWrappedTwiceStillParses() {
        val inner = """{"messages":[{"messageId":"m1","subject":"S","sender":"x@y.example"}]}"""
        assertEquals(1, ComposioCatalog.parseGmail(com.google.gson.JsonPrimitive(inner)).size)
        assertEquals(1, ComposioCatalog.parseGmail(j("""{"data":$inner,"successful":true}""")).size)
        assertEquals(0, ComposioCatalog.parseGmail(j("[]")).size)
    }

    @Test fun calendarEventsParseTimedAndAllDay() {
        val ev = ComposioCatalog.parseCalendar(j("""{"items":[
          {"id":"e1","summary":"Pitch","start":{"dateTime":"2026-11-12T10:00:00+02:00"},"end":{"dateTime":"2026-11-12T11:00:00+02:00"},"attendees":[{},{}],"hangoutLink":"https://meet.example/x","updated":"2026-11-06T09:00:00Z","created":"2026-11-01T09:00:00Z","status":"confirmed","htmlLink":"https://cal.example/e1"},
          {"id":"e2","summary":"Holiday","start":{"date":"2026-12-16"},"end":{"date":"2026-12-17"}}]}"""))
        assertEquals(2, ev.size)
        assertEquals(2, ev[0].attendeeCount); assertEquals("https://meet.example/x", ev[0].joinUrl)
        assertEquals(Fixtures.at(11, 12, 10), ev[0].startAt)
        assertNotNull(ev[1].startAt)
    }

    @Test fun clickupParsesTeamsAndTasksWithMillisecondDueDates() {
        assertEquals(listOf("900"), ComposioCatalog.parseClickUpTeams(j("""{"teams":[{"id":"900","name":"W"}]}""")))
        val t = ComposioCatalog.parseClickUp(j("""{"tasks":[{"id":"t1","name":"Q4 sheet","status":{"status":"open"},"due_date":"1762646400000","url":"https://t.example/t1","list":{"name":"Studio"},"date_updated":"1762500000000"}]}"""))
        assertEquals(1, t.size)
        assertEquals(1762646400000L, t[0].dueAt); assertEquals("open", t[0].status); assertEquals("Studio", t[0].listName)
    }

    @Test fun slackChannelsAndMessagesParseAndFlagBots() {
        val ch = ComposioCatalog.parseSlackChannels(j("""{"channels":[{"id":"C1","name":"general"},{"id":"C2","name":"old","is_archived":true}]}"""))
        assertEquals(listOf("C1" to "general"), ch)
        val m = ComposioCatalog.parseSlack(j("""{"messages":[{"ts":"1762420000.000100","user":"U1","text":"I will send the set today"},{"ts":"1762420100.0","bot_id":"B1","text":"deploy ok"},{"ts":"3"}]}"""), "C1", "general")
        assertEquals(2, m.size)
        assertEquals("C1:1762420000.000100", m[0].sourceId); assertFalse(m[0].isBot); assertTrue(m[1].isBot)
        assertEquals(1762420000000L, m[0].occurredAt)
    }

    @Test fun gmailPullFollowsPagesAndUsesOnlyTheReadSlug() = runBlocking {
        val api = FakeComposio()
        api.on(ComposioCatalog.GMAIL_FETCH) { a ->
            if (a["page_token"] == null) FakeComposio.ok("""{"messages":[{"messageId":"a","subject":"A"}],"nextPageToken":"p2"}""")
            else FakeComposio.ok("""{"messages":[{"messageId":"b","subject":"B"}]}""")
        }
        val items = ComposioCatalog.pull(api, LifeApp.GMAIL, "ca", "u", Fixtures.NOW - Fixtures.DAY, Fixtures.NOW)
        assertEquals(listOf("a", "b"), items.map { it.sourceId })
        assertEquals(listOf(ComposioCatalog.GMAIL_FETCH, ComposioCatalog.GMAIL_FETCH), api.calls.map { it.first })
        assertTrue((api.calls[0].second["query"] as String).startsWith("after:"))
    }

    @Test fun calendarPullAsksForTodayOnwardsOnly() = runBlocking {
        val api = FakeComposio()
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
        ComposioCatalog.pull(api, LifeApp.GCAL, "ca", null, 0, Fixtures.NOW)
        val a = api.calls.single().second
        assertEquals(true, a["singleEvents"]); assertEquals("primary", a["calendarId"])
        assertTrue((a["timeMin"] as String) < (a["timeMax"] as String))
    }

    @Test fun clickupFirstRunAsksOpenTasksOnlyThenOnlyChanges() = runBlocking {
        val api = FakeComposio()
        api.on(ComposioCatalog.CLICKUP_WORKSPACES) { FakeComposio.ok("""{"teams":[{"id":"900"}]}""") }
        api.on(ComposioCatalog.CLICKUP_TASKS) { FakeComposio.ok("""{"tasks":[]}""") }
        ComposioCatalog.pull(api, LifeApp.CLICKUP, "ca", null, 0L, Fixtures.NOW)
        ComposioCatalog.pull(api, LifeApp.CLICKUP, "ca", null, Fixtures.NOW - 1000, Fixtures.NOW)
        val first = api.calls[1].second; val later = api.calls[3].second
        assertEquals(false, first["include_closed"]); assertFalse(first.containsKey("date_updated_gt"))
        assertEquals(true, later["include_closed"]); assertNotNull(later["date_updated_gt"])
        assertEquals("900", first["team_Id"])
    }

    @Test fun slackReadsHistoryPerChannelAndSurvivesOneBadChannel() = runBlocking {
        val api = FakeComposio()
        api.on(ComposioCatalog.SLACK_CHANNELS) { FakeComposio.ok("""{"channels":[{"id":"C1","name":"a"},{"id":"C2","name":"b"}]}""") }
        api.on(ComposioCatalog.SLACK_HISTORY) { a ->
            if (a["channel"] == "C1") throw ComposioException(ComposioErrorKind.TOOL_FAILED, "not_in_channel")
            FakeComposio.ok("""{"messages":[{"ts":"1762420000.1","user":"U","text":"hello there"}]}""")
        }
        val items = ComposioCatalog.pull(api, LifeApp.SLACK, "ca", null, 0, Fixtures.NOW)
        assertEquals(1, items.size); assertEquals("C2:1762420000.1", items[0].sourceId)
    }

    @Test fun authFailureInSlackHistoryFailsTheApp() = runBlocking {
        val api = FakeComposio()
        api.on(ComposioCatalog.SLACK_CHANNELS) { FakeComposio.ok("""{"channels":[{"id":"C1","name":"a"}]}""") }
        api.fail(ComposioCatalog.SLACK_HISTORY, ComposioErrorKind.AUTH, "Composio did not accept the key (401)")
        try { ComposioCatalog.pull(api, LifeApp.SLACK, "ca", null, 0, Fixtures.NOW); fail() } catch (e: ComposioException) { assertEquals(ComposioErrorKind.AUTH, e.kind) }
    }

    @Test fun unsuccessfulToolResultBecomesAReadableError() = runBlocking {
        val api = FakeComposio()
        api.on(ComposioCatalog.GMAIL_FETCH) { com.example.memgallery.lifeagent.composio.ToolResult(false, null, "Token expired") }
        try { ComposioCatalog.pull(api, LifeApp.GMAIL, "ca", null, 0, Fixtures.NOW); fail() } catch (e: ComposioException) {
            assertEquals(ComposioErrorKind.TOOL_FAILED, e.kind); assertEquals("Token expired", e.message)
        }
    }

    @Test fun parseTimeHandlesEverySourceFormat() {
        assertEquals(1762420000000L, ComposioCatalog.parseTime("1762420000"))
        assertEquals(1762420000000L, ComposioCatalog.parseTime("1762420000000"))
        assertEquals(1762420000000L, ComposioCatalog.parseTime("1762420000.000100"))
        assertNotNull(ComposioCatalog.parseTime("2026-11-06T08:00:00Z"))
        assertNotNull(ComposioCatalog.parseTime("2026-11-06"))
        assertNull(ComposioCatalog.parseTime("soon")); assertNull(ComposioCatalog.parseTime(null))
    }
}
