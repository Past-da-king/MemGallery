package com.example.memgallery.proof

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.data.remote.dto.ActionDto
import com.example.memgallery.ui.design.FloatingNav
import com.example.memgallery.ui.design.MemoryKind
import com.example.memgallery.ui.design.NavItem
import com.example.memgallery.ui.design.Tone
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.screens.ActiveProviderCard
import com.example.memgallery.ui.screens.AppearanceSection
import com.example.memgallery.ui.screens.ChatLayout
import com.example.memgallery.ui.screens.MemoryDetailContent
import com.example.memgallery.ui.screens.OnboardingBottomBar
import com.example.memgallery.ui.screens.OnboardingWelcomeContent
import com.example.memgallery.ui.screens.ProcessingContent
import com.example.memgallery.ui.screens.ProcessingStep
import com.example.memgallery.ui.screens.SettingToggleItem
import com.example.memgallery.ui.screens.SettingsCard
import com.example.memgallery.ui.screens.SettingsHeader
import com.example.memgallery.ui.screens.StepState
import com.example.memgallery.ui.screens.TaskActions
import com.example.memgallery.ui.screens.TaskUiState
import com.example.memgallery.ui.screens.TasksContent
import com.example.memgallery.ui.screens.groupedShape
import com.example.memgallery.ui.design.SegmentedChoice
import com.example.memgallery.data.local.dao.TaskSourceMemory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi", application = android.app.Application::class)
class ScreensProofTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun NavAt(index: Int) {
        Box(Modifier.fillMaxSize()) {
            FloatingNav(
                items = listOf(
                    NavItem("Chat", Icons.Outlined.ChatBubbleOutline, Icons.Filled.Chat),
                    NavItem("Memories", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
                    NavItem("Tasks", Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt),
                ),
                selectedIndex = index, onSelect = {}, fabIcon = Icons.Filled.Add,
                modifier = Modifier.align(Alignment.BottomCenter).appNavigationBarsPadding().padding(bottom = 16.dp)
            )
        }
    }

    // ---- 08 Tasks and 12 Empty ----
    private fun tasksUi(empty: Boolean) = TaskUiState(
        tasks = if (empty) emptyList() else sampleTasks(),
        selectedDate = ProofToday,
        now = ProofNow,
        unapprovedCount = if (empty) 0 else 3,
        sources = mapOf(
            5 to TaskSourceMemory(5, "Night train to Porto", "art://ticket"),
            2 to TaskSourceMemory(2, "Gift ideas", null),
        )
    )

    private fun tasks(dark: Boolean, empty: Boolean) = rule.proof(if (empty) "12-empty" else "08-tasks", dark) {
        Box(Modifier.fillMaxSize()) {
            TasksContent(tasksUi(empty), TaskActions(), bottomPadding = 128.dp)
            NavAt(2)
        }
    }
    @Test fun tasksLight() = tasks(false, false)
    @Test fun tasksDark() = tasks(true, false)
    @Test fun emptyLight() = tasks(false, true)
    @Test fun emptyDark() = tasks(true, true)

    // ---- 02 Detail ----
    private fun detail(dark: Boolean) = rule.proof("02-detail", dark) {
        val m = memory(5, "Night train to Porto", 1, 18, image = "ticket", summary = "Porto Campanhã to Lisbon Oriente on Fri 6 Nov. Departs 08:42, platform 4, coach 6, seat 14A.")
            .copy(
                imageUris = listOf("art://ticket", "art://map"),
                aiActions = listOf(ActionDto("EVENT", "Train to Lisbon", "2026-11-06", "08:42")),
                aiTags = listOf("travel", "train")
            )
        MemoryDetailContent(
            memory = m, onNavigateUp = {}, onEdit = {}, onShare = {}, onCreateTask = { _, _, _, _, _ -> },
            bottomPadding = 0.dp, isLocalAi = true, now = ProofNow
        )
    }
    @Test fun detailLight() = detail(false)
    @Test fun detailDark() = detail(true)

    // ---- 04 Processing ----
    private fun processing(dark: Boolean) = rule.proof("04-processing", dark) {
        ProcessingContent(
            kind = MemoryKind.Image, image = "art://wifi", providerLabel = "Local · Gemma 3", isLocal = true,
            tags = listOf("wifi", "password"),
            steps = listOf(
                ProcessingStep("Text read", StepState.Done),
                ProcessingStep("Describing the image", StepState.Active),
                ProcessingStep("Adding tags", StepState.Pending),
                ProcessingStep("Writing a summary", StepState.Pending),
                ProcessingStep("Looking for tasks and events", StepState.Pending),
            ),
            status = "PROCESSING", onClose = {}, onKeepInBackground = {}, onOpenMemory = {}
        )
    }
    @Test fun processingLight() = processing(false)
    @Test fun processingDark() = processing(true)

    // ---- 06 Ask ----
    private fun ask(dark: Boolean) = rule.proof("06-ask", dark) {
        val sources = mapOf(
            5 to memory(5, "Train ticket", 1, 18, image = "ticket"),
            11 to memory(11, "Blue Door Café", 2, 12, image = "map"),
        )
        val messages = listOf(
            ChatMessageEntity(id = 1, chatId = 1, role = "user", content = "What time is my train on Friday, and which platform?", timestamp = at(0, 9)),
            ChatMessageEntity(
                id = 2, chatId = 1, role = "model",
                content = "Your train leaves **Porto Campanhã at 08:42 on Friday 6 Nov** from **platform 4**. You are in coach 6, seat 14A, and it arrives in Lisbon at 11:20.\n\nThe café you saved is an 8 minute walk from the station and is open until 18:00.",
                timestamp = at(0, 9), displayedMemoryIds = listOf(5, 11)
            ),
        )
        ChatLayout(
            title = "Trip planning", subtitle = "Local · Gemma 3", messages = messages, displayedMemories = sources,
            inputMessage = "", isLoading = false, isStreaming = false, streamingContent = "", currentThought = "", currentTool = null,
            isRecording = false, recordingAmplitudes = emptyList(), recordingTime = 0, playingPath = null, isPlaying = false,
            audioProgress = 0f, audioCurrentTime = "00:00", audioTotalTime = "00:00",
            snackbarHostState = SnackbarHostState(), listState = rememberLazyListState(),
            onValueChange = {}, onSend = {}, onAttach = {}, onMic = {}, onHistory = {}, onNewChat = {}, onSaveChat = {},
            onSaveAsNote = {}, onCopy = {}, onPlayAudio = {}, onPauseAudio = {}, onSeekAudio = {}, onMemoryClick = {}, onSuggestion = {}
        )
    }
    @Test fun askLight() = ask(false)
    @Test fun askDark() = ask(true)

    // ---- 09 Settings (the real building blocks, composed the way SettingsScreen composes them) ----
    private fun settings(dark: Boolean) = rule.proof("09-settings", dark) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState())) {
            SettingsHeader(onBack = {}, version = "0.3.4")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                ActiveProviderCard(provider = "LOCAL", localModelLoaded = true)
                SettingsCard(Icons.Filled.AutoAwesome, "AI Provider", "") {
                    SegmentedChoice(
                        options = listOf("GEMINI" to "Gemini", "GROQ" to "Groq", "LOCAL" to "Local", "OPENAI_COMPATIBLE" to "Custom"),
                        selectedKey = "LOCAL", onSelect = {}
                    )
                }
                SettingsCard(Icons.Filled.AutoAwesome, "Appearance", "", grouped = true) {
                    AppearanceSection(
                        appThemeMode = "SYSTEM", dynamicThemingEnabled = true, amoledModeEnabled = false, selectedColor = -1,
                        onAppThemeModeChange = {}, onDynamicThemingChange = {}, onAmoledModeChange = {}, onSelectedColorChange = {}
                    )
                }
                SettingsCard(Icons.Filled.AutoAwesome, "Features", "", grouped = true) {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        SettingToggleItem(Icons.Filled.Screenshot, "Auto-index Screenshots", "Process new screenshots automatically", true, {}, groupedShape(0, 3), Tone.Sky)
                        SettingToggleItem(Icons.Filled.Task, "Task Manager", "Show the Tasks page", true, {}, groupedShape(1, 3), Tone.Success)
                        SettingToggleItem(Icons.Filled.Share, "Share Menu", "Show MemGallery in the share sheet", false, {}, groupedShape(2, 3), Tone.Amber)
                    }
                }
            }
        }
    }
    @Test fun settingsLight() = settings(false)
    @Test fun settingsDark() = settings(true)

    // ---- 10 Onboarding ----
    private fun onboarding(dark: Boolean) = rule.proof("10-onboarding", dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            OnboardingWelcomeContent()
            androidx.compose.material3.TextButton(onClick = {}, modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)) {
                androidx.compose.material3.Text("Skip", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            OnboardingBottomBar(currentPage = 0, pageCount = 4, onNext = {}, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
    @Test fun onboardingLight() = onboarding(false)
    @Test fun onboardingDark() = onboarding(true)
}
