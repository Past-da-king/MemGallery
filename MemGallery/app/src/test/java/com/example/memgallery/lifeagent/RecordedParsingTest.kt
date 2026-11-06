package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.collect.BuildContext
import com.example.memgallery.lifeagent.collect.NoiseFilter
import com.example.memgallery.lifeagent.collect.ProposalBuilder
import com.example.memgallery.lifeagent.composio.ComposioCatalog
import com.example.memgallery.lifeagent.composio.ConnectedAccount
import com.example.memgallery.lifeagent.composio.RawItem
import com.example.memgallery.lifeagent.composio.RawType
import com.example.memgallery.lifeagent.composio.ToolResult
import com.example.memgallery.lifeagent.data.LifeApp
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every recorded tool answer goes through ComposioCatalog.pull, the same path a collection run uses. */
class RecordedParsingTest {
    private val now = Fixtures.NOW
    private val ctx = BuildContext(now = now, zone = Fixtures.ZONE)

    private fun pull(app: LifeApp, api: FakeComposio = RecordedPayloads.fakeWithAllApps(), since: Long = 0L): List<RawItem> =
        runBlocking { ComposioCatalog.pull(api, app, "ca", "u", since, now) }

    // ---- Gmail ----

    @Test fun gmailRecordingParsesFiveMessagesFromRawHeadersAndComposioFields() {
        val items = pull(LifeApp.GMAIL)
        assertEquals(5, items.size)
        assertTrue(items.all { it.app == "gmail" && it.type == RawType.EMAIL })
        assertEquals(
            listOf("Signed lease for Unit 4", "Invoice INV-2043 due 13 Nov", "Black Friday: up to 60% off", "Seven things we liked this week", "Your Friday trip with Uber"),
            items.map { it.title },
        )
        // First message has ONLY payload.headers and a snippet (raw Gmail): subject and sender must come from the headers.
        assertEquals("Nomsa Mahlangu <nomsa@umoya.example>", items[0].author)
        assertEquals("19a4c0de01aa0001", items[0].sourceId)
        assertEquals(1793948400000L, items[0].occurredAt)          // internalDate, a string of millis
        assertTrue(items[0].body.startsWith("Hi Ayanda, please send the signed lease"))
        assertEquals("https://mail.google.com/mail/u/0/#all/19a4c0de01aa0001", items[0].url)
        // Composio style: messageTimestamp as ISO, thread differs from message id.
        assertEquals(1793950200000L, items[1].occurredAt)
        assertEquals("https://mail.google.com/mail/u/0/#all/19a4c0de01aa0099", items[1].url)
        assertTrue("UNREAD" in items[0].labels && "IMPORTANT" in items[0].labels)
        assertTrue("CATEGORY_PROMOTIONS" in items[2].labels && "UNREAD" in items[2].labels)
        assertEquals("Takealot Deals <deals@takealot.example>", items[2].author)
        // Attachments: one in payload.parts, one in attachmentList, none in an empty list.
        assertTrue("ATTACHMENT" in items[0].labels)
        assertTrue("ATTACHMENT" in items[4].labels)
        assertFalse("ATTACHMENT" in items[1].labels)
    }

    @Test fun gmailDateHeaderAloneIsEnoughForTheTime() {
        val json = """{"messages":[{"id":"h1","payload":{"headers":[{"name":"Date","value":"Fri, 6 Nov 2026 09:00:00 +0200 (SAST)"},{"name":"Subject","value":"Hi"}]}}]}"""
        val api = FakeComposio().also { it.on(ComposioCatalog.GMAIL_FETCH) { FakeComposio.ok(json) } }
        val item = pull(LifeApp.GMAIL, api).single()
        assertEquals(1793948400000L, item.occurredAt)
        assertEquals("Hi", item.title)
    }

    @Test fun noiseFilterDropsPromotionNewsletterAndReceiptAndKeepsTheRest() {
        val filter = NoiseFilter()
        val verdicts = pull(LifeApp.GMAIL).map { filter.judge(it) }
        assertFalse(verdicts[0].drop); assertFalse(verdicts[1].drop)
        assertEquals(NoiseFilter.R_PROMO, verdicts[2].reason)
        assertEquals(NoiseFilter.R_NEWSLETTER, verdicts[3].reason)
        assertEquals(NoiseFilter.R_RECEIPT, verdicts[4].reason)
    }

    @Test fun survivingMailBecomesTheRightProposalKinds() = runBlocking {
        val items = pull(LifeApp.GMAIL)
        val lease = ProposalBuilder().build(items[0], ctx)!!
        assertEquals("commitment", lease.kind.key)
        assertEquals("Nomsa Mahlangu", lease.personName)
        assertEquals(Fixtures.at(11, 7, 16, 0), lease.dueAt)
        val invoice = ProposalBuilder().build(items[1], ctx)!!
        assertEquals("money", invoice.kind.key)
        assertEquals(840_000L, invoice.amountCents)
        assertEquals("ZAR", invoice.currency)
        assertEquals("thing:anele jacobs:INV-2043", invoice.fingerprint)
    }

    // ---- Calendar ----

    @Test fun calendarRecordingParsesTimedAllDayRecurringAndCancelled() {
        val items = pull(LifeApp.GCAL)
        assertEquals(listOf("Ndlovu pitch", "Mom's birthday", "Weekly standup", "Design review"), items.map { it.title })
        assertTrue(items.all { it.app == "gcal" && it.type == RawType.EVENT })
        val pitch = items[0]
        assertEquals(Fixtures.at(11, 12, 10, 0), pitch.startAt)
        assertEquals(Fixtures.at(11, 12, 11, 0), pitch.endAt)
        assertEquals("https://meet.google.com/abc-defg-hij", pitch.joinUrl)
        assertEquals("Ndlovu Studio, Umhlanga", pitch.location)
        assertEquals(3, pitch.attendeeCount)
        assertEquals("sizwe@ndlovu.example", pitch.author)
        assertEquals("https://www.google.com/calendar/event?eid=bmRsb3Z1cGl0Y2g", pitch.url)
        // All-day uses the `date` form: a whole day long.
        assertEquals(24L * 60 * 60 * 1000, items[1].endAt!! - items[1].startAt!!)
        assertNull(items[1].joinUrl)
        // Recurring instance keeps its instance id, so the same series on another day is another event.
        assertEquals("weeklystandup_20261109T060000Z", items[2].sourceId)
        assertEquals(5, items[2].attendeeCount)
        assertEquals("cancelled", items[3].status)
        assertEquals(NoiseFilter.R_CANCELLED, NoiseFilter().judge(items[3]).reason)
        assertFalse(NoiseFilter().judge(items[0]).drop)
    }

    @Test fun aChangedMeetingBecomesAnEventProposal() = runBlocking {
        val p = ProposalBuilder().build(pull(LifeApp.GCAL)[0], ctx)!!
        assertEquals("event", p.kind.key)
        assertEquals(Fixtures.at(11, 12, 10, 0), p.dueAt)
        assertEquals("a meeting was changed", p.reason)
    }

    // ---- ClickUp ----

    @Test fun clickUpRecordingParsesFiveTasksWithAndWithoutDueDates() {
        val api = RecordedPayloads.fakeWithAllApps()
        val items = pull(LifeApp.CLICKUP, api)
        assertEquals(5, items.size)
        assertTrue(items.all { it.app == "clickup" && it.type == RawType.TASK })
        assertEquals(listOf("WORKSPACES", "TASKS"), api.calls.map { if (it.first == ComposioCatalog.CLICKUP_WORKSPACES) "WORKSPACES" else "TASKS" })
        assertEquals("9001234", api.calls[1].second["team_Id"])
        val t = items[0]
        assertEquals("86abc001", t.sourceId)
        assertEquals(1794297600000L, t.dueAt)                  // due_date is a STRING of millis
        assertEquals("to do", t.status)                        // status.status
        assertEquals("Studio", t.listName)
        assertEquals("https://app.clickup.com/t/86abc001", t.url)
        assertEquals("Owner Example", t.author)
        assertEquals(1793000000000L, t.occurredAt)
        assertNull(items[2].dueAt)                             // due_date null
        assertEquals("Review contract draft", items[2].title)
        assertEquals("complete", items[4].status)
    }

    @Test fun onlyOpenTasksWithADueDateBecomeProposals() = runBlocking {
        val kinds = pull(LifeApp.CLICKUP).mapNotNull { ProposalBuilder().build(it, ctx)?.sourceId }
        assertEquals(listOf("86abc001", "86abc002", "86abc004"), kinds)   // null due and closed ones are not proposed
    }

    // ---- Slack ----

    @Test fun slackRecordingParsesChannelsAndSixMessages() {
        val api = RecordedPayloads.fakeWithAllApps()
        val items = pull(LifeApp.SLACK, api)
        // archived channel is never read; the DM (no name) is read by its user id.
        val read = api.calls.filter { it.first == ComposioCatalog.SLACK_HISTORY }.map { it.second["channel"] }
        assertEquals(listOf("C01GENERAL", "C02NDLOVU", "D04DM0001"), read)
        assertEquals(6, items.size)
        assertEquals("C01GENERAL:1793948400.000100", items[0].sourceId)
        assertEquals("Can you send the revised quote to Ndlovu by Monday?", items[0].title)   // first line only
        assertTrue(items[0].body.contains("before the pitch"))
        assertEquals("U02AAA111", items[0].author)
        assertEquals(1793948400000L, items[0].occurredAt)
        assertTrue("#general" in items[0].labels)
        assertEquals("C01GENERAL:1793948700.000200", items[1].sourceId)        // thread reply keeps its own ts
        assertTrue(items[3].isBot); assertTrue(items[4].isBot)                 // bot_message and channel_join
        assertFalse(items[0].isBot)
    }

    @Test fun slackBotsAndJoinsAreDroppedAndARequestBecomesACommitment() = runBlocking {
        val items = pull(LifeApp.SLACK)
        val filter = NoiseFilter()
        assertEquals(NoiseFilter.R_AUTOMATED, filter.judge(items[3]).reason)
        assertEquals(NoiseFilter.R_AUTOMATED, filter.judge(items[4]).reason)
        val ask = ProposalBuilder().build(items[0], ctx)!!
        assertEquals("commitment", ask.kind.key)
        assertNull(ask.personName)            // Slack authors are ids, never shown as a name
        assertNull(ProposalBuilder().build(items[1], ctx))   // "On it" is nothing to act on
    }

    // ---- accounts ----

    @Test fun connectedAccountsRecordingNeverThrowsOnMissingFields() {
        // Parsed by the real client in RecordedRunTest; here the shape facts the matcher relies on.
        val items = RecordedPayloads.json(RecordedPayloads.ACCOUNTS).getAsJsonArray("items")
        assertEquals(6, items.size())
        assertEquals(ConnectedAccount("ca_x", "gmail", null, "ACTIVE", null).active, true)
        assertNotNull(items[0].asJsonObject.getAsJsonObject("toolkit").get("slug"))
    }

    // ---- nothing missing ever throws ----

    private val oddPayloads = listOf(
        "null", "{}", "[]", "\"just text\"", "42", "{\"data\":null}", "{\"data\":{\"data\":{}}}", "{\"response_data\":{\"messages\":[]}}",
        """{"messages":[null,1,"x",[],{},{"id":null},{"id":"a","labelIds":"nope","payload":{"headers":[null,{"name":null},{"name":"Subject"},{"name":"From","value":null}],"parts":[null,{"filename":null}]},"internalDate":"99999999999999999999999999"},{"id":"b","internalDate":"0","snippet":null,"subject":{}}]}""",
        """{"items":[null,{},{"id":"e"},{"id":"e","start":{"date":"garbage"}},{"id":"f","start":{"dateTime":"2026-11-12T10:00:00+02:00"},"attendees":"x","end":null,"organizer":"me","updated":"x"}]}""",
        """{"tasks":[null,{},{"id":"t","status":{},"due_date":"0","assignees":[null,1,{}],"list":null,"date_created":"x"},{"id":"u","status":"open","due_date":"99999999999999999999","name":null}],"teams":[null,{},{"id":"1"}]}""",
        """{"channels":[null,{},{"id":"C1","is_archived":"maybe"},{"id":"C2","name":null}],"messages":[null,{},{"ts":"1.5"},{"ts":"zz","text":""},{"ts":"abc","text":"hello there","bot_id":3}]}""",
    )

    @Test fun missingNullAndWrongTypedFieldsNeverThrowInAnyParser() {
        val handler = { raw: String -> ToolResult(true, JsonParser.parseString(raw), null) }
        for (raw in oddPayloads) {
            val api = FakeComposio().also { f ->
                listOf(
                    ComposioCatalog.GMAIL_FETCH, ComposioCatalog.CAL_LIST, ComposioCatalog.CLICKUP_WORKSPACES,
                    ComposioCatalog.CLICKUP_TASKS, ComposioCatalog.SLACK_CHANNELS, ComposioCatalog.SLACK_HISTORY,
                ).forEach { slug -> f.on(slug) { handler(raw) } }
            }
            for (app in LifeApp.entries) {
                val items = try { pull(app, api) } catch (e: Throwable) { throw AssertionError("$app threw on $raw", e) }
                // and the next stages cope with whatever came out
                runBlocking { items.forEach { NoiseFilter().judge(it); ProposalBuilder().build(it, ctx) } }
            }
        }
        // a null `data` (tool answered successful with nothing) is an empty pull, not a crash
        val nullData = FakeComposio().also { f -> f.on(ComposioCatalog.GMAIL_FETCH) { ToolResult(true, null, null) } }
        assertEquals(0, pull(LifeApp.GMAIL, nullData).size)
    }

    @Test fun hugeAndZeroTimestampsAreNoTimeRatherThanACrashOrTheYear1970() {
        assertNull(ComposioCatalog.parseTime("99999999999999999999999999"))
        assertNull(ComposioCatalog.parseTime("0"))
        assertNull(ComposioCatalog.parseTime("0.000000"))
        assertNull(ComposioCatalog.parseTime("  "))
        assertEquals(1793948400000L, ComposioCatalog.parseTime("1793948400"))          // seconds
        assertEquals(1793948400000L, ComposioCatalog.parseTime("1793948400000"))       // millis
        assertEquals(1793948400000L, ComposioCatalog.parseTime("1793948400.000100"))   // Slack ts
        assertEquals(1793948400000L, ComposioCatalog.parseTime("2026-11-06T09:00:00+02:00"))
        assertEquals(1793948400000L, ComposioCatalog.parseTime("2026-11-06T07:00:00.000Z"))
    }

    @Test fun theResponseCanBeWrappedOrAJsonString() {
        val inner = """{"messages":[{"id":"w1","subject":"Wrapped"}]}"""
        val shapes = listOf(
            inner,
            """{"data":$inner}""",
            """{"response_data":$inner}""",
            JsonParser.parseString(inner).toString().let { com.google.gson.JsonPrimitive(it).toString() },
        )
        for (s in shapes) {
            val api = FakeComposio().also { f -> f.on(ComposioCatalog.GMAIL_FETCH) { ToolResult(true, JsonParser.parseString(s), null) } }
            assertEquals(s, "Wrapped", pull(LifeApp.GMAIL, api).single().title)
        }
    }
}
