package com.example.memgallery.proof

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.memgallery.ui.design.MemTextField
import com.example.memgallery.ui.screens.AiSettingsActions
import com.example.memgallery.ui.screens.AiSettingsContent
import com.example.memgallery.ui.screens.AiSettingsState
import com.example.memgallery.ui.screens.ActiveProviderCard
import com.example.memgallery.ui.screens.BookmarkInputScreen
import com.example.memgallery.ui.screens.FeedbackContent
import com.example.memgallery.ui.screens.HowItWorksPage
import com.example.memgallery.ui.screens.KeyUi
import com.example.memgallery.ui.screens.NotificationsCard
import com.example.memgallery.ui.screens.SettingsCard
import com.example.memgallery.ui.screens.SettingsHeader
import com.example.memgallery.ui.screens.TextInputScreen
import com.example.memgallery.ui.screens.WebSearchContent
import com.example.memgallery.ui.viewmodels.ApiKeyUiState
import com.example.memgallery.ui.viewmodels.FeedbackUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proofs for the second pass: the AI settings (per-job providers, DeepSeek, web search), the one text field,
 * notifications and the pages that were still on stock styling. The harness draws NO surface behind the content, so
 * any text that forgets its colour would show up black on the dark scheme here, as it did on the phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi", application = android.app.Application::class)
class PolishProofTest {
    @get:Rule val rule = createComposeRule()

    private val keys = setOf("GEMINI", "GROQ", "DEEPSEEK")

    private fun aiState(main: String = "DEEPSEEK", voice: String = "GROQ", images: String = "GEMINI") = AiSettingsState(
        main = main, voice = voice, images = images, savedKeys = keys,
        gemini = KeyUi("AIza-demo-key-0000", ApiKeyUiState.Idle),
        groq = KeyUi("gsk_demo_key_0000", ApiKeyUiState.Idle),
        deepSeek = KeyUi("sk-demo-key-0000", ApiKeyUiState.Success("DeepSeek key works and is saved.")),
        custom = KeyUi("", ApiKeyUiState.Error("Validation failed (401): bad key")),
    )

    @Composable
    private fun Page(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState())) {
            SettingsHeader(onBack = {}, version = "0.4.0")
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
        }
    }

    // 13: AI jobs, collapsed
    private fun aiJobs(dark: Boolean) = rule.proof("13-settings-ai-jobs", dark) {
        Page {
            ActiveProviderCard(provider = "DEEPSEEK", localModelLoaded = false)
            SettingsCard(Icons.Filled.Key, "AI Configuration", "", grouped = true) {
                AiSettingsContent(aiState(), AiSettingsActions(), expanded = null, onExpand = {})
            }
        }
    }
    @Test fun aiJobsLight() = aiJobs(false)
    @Test fun aiJobsDark() = aiJobs(true)

    // 14: DeepSeek row open (key field, model group, thinking switch)
    private fun deepSeekOpen(dark: Boolean) = rule.proof("14-settings-deepseek", dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState(100000))) {
            Column(Modifier.padding(16.dp).padding(top = 40.dp)) {
                AiSettingsContent(aiState(), AiSettingsActions(), expanded = "DEEPSEEK", onExpand = {})
            }
        }
    }
    @Test fun deepSeekLight() = deepSeekOpen(false)
    @Test fun deepSeekDark() = deepSeekOpen(true)

    // 15: Custom provider row open (three fields, one in error)
    private fun customOpen(dark: Boolean) = rule.proof("15-settings-custom-provider", dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState(100000))) {
            Column(Modifier.padding(16.dp).padding(top = 40.dp)) {
                AiSettingsContent(aiState(main = "OPENAI_COMPATIBLE", voice = "NONE", images = "NONE"), AiSettingsActions(), expanded = "OPENAI_COMPATIBLE", onExpand = {})
            }
        }
    }
    @Test fun customLight() = customOpen(false)
    @Test fun customDark() = customOpen(true)

    // 16: web search and notifications
    private fun searchAndNotifications(dark: Boolean) = rule.proof("16-settings-search-notifications", dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(16.dp).padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsCard(Icons.Filled.Link, "Web search", "", grouped = true) {
                    WebSearchContent("TAVILY", "tvly-demo-0000", ApiKeyUiState.Success("Search works (Tavily)."), {}, {}, {}, {})
                }
                NotificationsCard(enabled = true, filter = "EVENTS", onToggle = {}, onFilter = {})
            }
        }
    }
    @Test fun searchLight() = searchAndNotifications(false)
    @Test fun searchDark() = searchAndNotifications(true)

    // 17: the one text field in every state
    private fun fields(dark: Boolean) = rule.proof("17-text-fields", dark) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp).padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MemTextField(value = "", onValueChange = {}, label = "Base URL", placeholder = "https://api.example.com/v1/", leadingIcon = Icons.Filled.Link)
            MemTextField(value = "gpt-4o", onValueChange = {}, label = "Model name", leadingIcon = Icons.Filled.Key)
            MemTextField(value = "sk-secret-value", onValueChange = {}, label = "API key", leadingIcon = Icons.Filled.VpnKey, isSecret = true, supportingText = "Kept encrypted on this phone.")
            MemTextField(value = "not-a-key", onValueChange = {}, label = "API key", leadingIcon = Icons.Filled.VpnKey, isSecret = true, errorText = "The provider refused this key (401).")
            MemTextField(value = "Remember to call the dentist on Friday morning about the crown.", onValueChange = {}, placeholder = "What's on your mind?", singleLine = false, minLines = 3)
        }
    }
    @Test fun fieldsLight() = fields(false)
    @Test fun fieldsDark() = fields(true)

    // 18: text note capture
    private fun textNote(dark: Boolean) = rule.proof("18-capture-text", dark) {
        TextInputScreen(navController = rememberNavController())
    }
    @Test fun textNoteLight() = textNote(false)
    @Test fun textNoteDark() = textNote(true)

    // 19: bookmark capture
    private fun bookmark(dark: Boolean) = rule.proof("19-capture-bookmark", dark) {
        BookmarkInputScreen(navController = rememberNavController(), initialUrl = "https://example.com/article")
    }
    @Test fun bookmarkLight() = bookmark(false)
    @Test fun bookmarkDark() = bookmark(true)

    // 20: feedback
    private fun feedback(dark: Boolean) = rule.proof("20-feedback", dark) {
        FeedbackContent(uiState = FeedbackUiState.Idle, onSubmit = { _, _ -> }, onReset = {}, onBack = {})
    }
    @Test fun feedbackLight() = feedback(false)
    @Test fun feedbackDark() = feedback(true)

    // 21: onboarding, how it works
    private fun how(dark: Boolean) = rule.proof("21-onboarding-how-it-works", dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { HowItWorksPage(onGetStarted = {}) }
    }
    @Test fun howLight() = how(false)
    @Test fun howDark() = how(true)
}
