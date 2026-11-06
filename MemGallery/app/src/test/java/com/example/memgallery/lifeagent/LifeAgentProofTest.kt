package com.example.memgallery.lifeagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.memgallery.lifeagent.ui.*
import com.example.memgallery.ui.design.FixedInsets
import com.example.memgallery.ui.design.LocalFixedInsets
import com.example.memgallery.ui.theme.MemGalleryTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshot proof of every Life Agent screen at 412x915 dp, light and dark, from generated fixtures (no Hilt, no
 * repository, no real account). PNGs go to app/build/life-agent-proof (override with -Dlifeproof.dir).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi", application = android.app.Application::class)
class LifeAgentProofTest {
    @get:Rule val rule = createComposeRule()

    private val outDir = File(System.getProperty("lifeproof.dir") ?: "build/life-agent-proof")

    private fun shot(name: String, dark: Boolean, sheetInPlace: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(
                LocalFixedInsets provides FixedInsets(top = 40.dp, bottom = 24.dp),
                LocalSheetInPlace provides sheetInPlace,
            ) {
                MemGalleryTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
                }
            }
        }
        outDir.mkdirs()
        rule.onRoot().captureRoboImage(File(outDir, "$name-${if (dark) "dark" else "light"}.png").path)
    }

    private fun both(name: String, sheetInPlace: Boolean = false, content: @Composable () -> Unit) {
        shot(name, false, sheetInPlace, content)
    }

    // ---- Connections ----
    @Test fun connectionsLight() = shot("connections", false) { ConnectionsContent(LifeFixtures.connections(), ConnectionsActions()) }
    @Test fun connectionsDark() = shot("connections", true) { ConnectionsContent(LifeFixtures.connections(), ConnectionsActions()) }
    @Test fun connectionsNoKeyLight() = shot("connections-nokey", false) { ConnectionsContent(LifeFixtures.connectionsNoKey(), ConnectionsActions()) }
    @Test fun connectionsNoKeyDark() = shot("connections-nokey", true) { ConnectionsContent(LifeFixtures.connectionsNoKey(), ConnectionsActions()) }
    @Test fun connectionsFailedLight() = shot("connections-failed", false) { ConnectionsContent(LifeFixtures.connectionsFailed(), ConnectionsActions()) }
    @Test fun connectionsFailedDark() = shot("connections-failed", true) { ConnectionsContent(LifeFixtures.connectionsFailed(), ConnectionsActions()) }
    @Test fun connectionsPersonalKeyLight() = shot("connections-project-sheet", false, true) { ConnectionsContent(LifeFixtures.connectionsPersonalKey(), ConnectionsActions()) }
    @Test fun connectionsPersonalKeyDark() = shot("connections-project-sheet", true, true) { ConnectionsContent(LifeFixtures.connectionsPersonalKey(), ConnectionsActions()) }
    @Test fun toolkitSheetLight() = shot("connections-sheet-toolkit", false, true) { ConnectionsContent(LifeFixtures.connections(), ConnectionsActions(), initialSheet = ConnectionsSheet.Toolkit(LifeFixtures.connections().others.first())) }
    @Test fun toolkitSheetDark() = shot("connections-sheet-toolkit", true, true) { ConnectionsContent(LifeFixtures.connections(), ConnectionsActions(), initialSheet = ConnectionsSheet.Toolkit(LifeFixtures.connections().others.first())) }
    @Test fun keySheetLight() = shot("connections-sheet-key", false, true) { ConnectionsContent(LifeFixtures.connectionsNoKey(), ConnectionsActions(), initialSheet = ConnectionsSheet.Key) }
    @Test fun keySheetDark() = shot("connections-sheet-key", true, true) { ConnectionsContent(LifeFixtures.connectionsNoKey(), ConnectionsActions(), initialSheet = ConnectionsSheet.Key) }

    // ---- Collecting ----
    @Test fun collectingLight() = shot("collecting", false) { CollectingContent(LifeFixtures.collectingRunning(), CollectingActions()) }
    @Test fun collectingDark() = shot("collecting", true) { CollectingContent(LifeFixtures.collectingRunning(), CollectingActions()) }
    @Test fun collectingFailedLight() = shot("collecting-failed", false) { CollectingContent(LifeFixtures.collectingFailedOne(), CollectingActions()) }
    @Test fun collectingFailedDark() = shot("collecting-failed", true) { CollectingContent(LifeFixtures.collectingFailedOne(), CollectingActions()) }
    @Test fun collectingFinishedLight() = shot("collecting-finished", false) { CollectingContent(LifeFixtures.collectingFinished(), CollectingActions()) }
    @Test fun collectingFinishedDark() = shot("collecting-finished", true) { CollectingContent(LifeFixtures.collectingFinished(), CollectingActions()) }
    @Test fun collectingIdleLight() = shot("collecting-idle", false) { CollectingContent(LifeFixtures.collectingIdle(), CollectingActions()) }
    @Test fun collectingIdleDark() = shot("collecting-idle", true) { CollectingContent(LifeFixtures.collectingIdle(), CollectingActions()) }
    @Test fun collectingNoAppsLight() = shot("collecting-noapps", false) { CollectingContent(LifeFixtures.collectingNoApps(), CollectingActions()) }
    @Test fun collectingNoAppsDark() = shot("collecting-noapps", true) { CollectingContent(LifeFixtures.collectingNoApps(), CollectingActions()) }

    // ---- Review ----
    @Test fun reviewLight() = shot("review-deck", false) { ReviewContent(LifeFixtures.reviewDeck(), ReviewActions()) }
    @Test fun reviewDark() = shot("review-deck", true) { ReviewContent(LifeFixtures.reviewDeck(), ReviewActions()) }
    @Test fun reviewDropSuggestionLight() = shot("review-deck-drop", false) {
        ReviewContent(LifeFixtures.reviewDeck().copy(remaining = listOf(LifeFixtures.takealot, LifeFixtures.nomsa), canUndo = false), ReviewActions())
    }
    @Test fun reviewDropSuggestionDark() = shot("review-deck-drop", true) {
        ReviewContent(LifeFixtures.reviewDeck().copy(remaining = listOf(LifeFixtures.takealot, LifeFixtures.nomsa), canUndo = false), ReviewActions())
    }
    @Test fun approveAllLight() = shot("review-approve-all", false, true) { ReviewContent(LifeFixtures.reviewApproveAll(), ReviewActions(), initialApproveSheet = true) }
    @Test fun approveAllDark() = shot("review-approve-all", true, true) { ReviewContent(LifeFixtures.reviewApproveAll(), ReviewActions(), initialApproveSheet = true) }
    @Test fun caughtUpLight() = shot("review-caught-up", false) { ReviewContent(LifeFixtures.reviewCaughtUp(), ReviewActions()) }
    @Test fun caughtUpDark() = shot("review-caught-up", true) { ReviewContent(LifeFixtures.reviewCaughtUp(), ReviewActions()) }
    @Test fun reviewNothingLight() = shot("review-nothing", false) { ReviewContent(LifeFixtures.reviewNothing(), ReviewActions()) }
    @Test fun reviewNothingDark() = shot("review-nothing", true) { ReviewContent(LifeFixtures.reviewNothing(), ReviewActions()) }

    // ---- People and person ----
    @Test fun peopleLight() = shot("people", false) { PeopleContent(LifeFixtures.people(), PeopleActions()) }
    @Test fun peopleDark() = shot("people", true) { PeopleContent(LifeFixtures.people(), PeopleActions()) }
    @Test fun peopleEmptyLight() = shot("people-empty", false) { PeopleContent(PeopleUiState(), PeopleActions()) }
    @Test fun peopleEmptyDark() = shot("people-empty", true) { PeopleContent(PeopleUiState(), PeopleActions()) }
    @Test fun peopleNoMatchLight() = shot("people-nomatch", false) { PeopleContent(LifeFixtures.people().copy(query = "zzz"), PeopleActions()) }
    @Test fun peopleNoMatchDark() = shot("people-nomatch", true) { PeopleContent(LifeFixtures.people().copy(query = "zzz"), PeopleActions()) }
    @Test fun personLight() = shot("person", false) { PersonContent(LifeFixtures.person(), PersonActions()) }
    @Test fun personDark() = shot("person", true) { PersonContent(LifeFixtures.person(), PersonActions()) }
    @Test fun personMissingLight() = shot("person-missing", false) { PersonContent(LifeFixtures.personMissing(), PersonActions()) }
    @Test fun personMissingDark() = shot("person-missing", true) { PersonContent(LifeFixtures.personMissing(), PersonActions()) }
    @Test fun personSnoozeLight() = shot("person-snooze", false, true) { PersonContent(LifeFixtures.person(), PersonActions(), initialSnoozeTaskId = "t1") }
    @Test fun personSnoozeDark() = shot("person-snooze", true, true) { PersonContent(LifeFixtures.person(), PersonActions(), initialSnoozeTaskId = "t1") }

    // ---- Daily report ----
    @Test fun reportLight() = shot("daily-report", false) { DailyReportContent(LifeFixtures.report(), DailyReportActions()) }
    @Test fun reportDark() = shot("daily-report", true) { DailyReportContent(LifeFixtures.report(), DailyReportActions()) }
    @Test fun reportNoAppsLight() = shot("daily-report-noapps", false) { DailyReportContent(LifeFixtures.reportNoApps(), DailyReportActions()) }
    @Test fun reportNoAppsDark() = shot("daily-report-noapps", true) { DailyReportContent(LifeFixtures.reportNoApps(), DailyReportActions()) }
    @Test fun reportEmptyLight() = shot("daily-report-empty", false) { DailyReportContent(LifeFixtures.reportEmpty(), DailyReportActions()) }
    @Test fun reportEmptyDark() = shot("daily-report-empty", true) { DailyReportContent(LifeFixtures.reportEmpty(), DailyReportActions()) }
    @Test fun reportSnoozeLight() = shot("daily-report-snooze", false, true) { DailyReportContent(LifeFixtures.report(), DailyReportActions(), initialSnoozeTaskId = "t1") }
    @Test fun reportSnoozeDark() = shot("daily-report-snooze", true, true) { DailyReportContent(LifeFixtures.report(), DailyReportActions(), initialSnoozeTaskId = "t1") }

    // ---- Home entry and chat sources ----
    @Test fun homeEntryLight() = shot("home-entry", false) { Column(Modifier.padding(top = 40.dp)) { LifeAgentEntryCard(8, {}); LifeAgentEntryCard(0, {}, Modifier.padding(top = 12.dp)) } }
    @Test fun homeEntryDark() = shot("home-entry", true) { Column(Modifier.padding(top = 40.dp)) { LifeAgentEntryCard(8, {}); LifeAgentEntryCard(0, {}, Modifier.padding(top = 12.dp)) } }
    @Test fun chatSourcesLight() = shot("chat-sources", false) { ChatSourcesFixture() }
    @Test fun chatSourcesDark() = shot("chat-sources", true) { ChatSourcesFixture() }

    @Composable
    private fun ChatSourcesFixture() {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 40.dp)) {
            Text(LifeFixtures.chatAnswer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            LifeSourceRows(LifeFixtures.chatAnswer, LifeFixtures.chatSources, {})
        }
    }

    // ---- Gestures (behaviour, not only looks) ----

    @Test fun swipeRightKeepsTopCard() {
        val kept = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalFixedInsets provides FixedInsets(top = 40.dp, bottom = 24.dp)) {
                MemGalleryTheme(darkTheme = false, dynamicColor = false) {
                    ReviewContent(LifeFixtures.reviewDeck(), ReviewActions(onKeep = { kept += it }, onDrop = { dropped += it }))
                }
            }
        }
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.2f, height * 0.45f), Offset(width * 0.95f, height * 0.45f), 300) }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(listOf("p4"), kept)
        assertTrue(dropped.isEmpty())
    }

    @Test fun swipeLeftDropsTopCard() {
        val kept = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalFixedInsets provides FixedInsets(top = 40.dp, bottom = 24.dp)) {
                MemGalleryTheme(darkTheme = false, dynamicColor = false) {
                    ReviewContent(LifeFixtures.reviewDeck(), ReviewActions(onKeep = { kept += it }, onDrop = { dropped += it }))
                }
            }
        }
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.8f, height * 0.45f), Offset(width * 0.05f, height * 0.45f), 300) }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(listOf("p4"), dropped)
        assertTrue(kept.isEmpty())
    }

    @Test fun shortSwipeSpringsBack() {
        val decided = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalFixedInsets provides FixedInsets(top = 40.dp, bottom = 24.dp)) {
                MemGalleryTheme(darkTheme = false, dynamicColor = false) {
                    ReviewContent(LifeFixtures.reviewDeck(), ReviewActions(onKeep = { decided += it }, onDrop = { decided += it }))
                }
            }
        }
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.4f, height * 0.45f), Offset(width * 0.5f, height * 0.45f), 200) }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        assertTrue(decided.isEmpty())
    }
}
