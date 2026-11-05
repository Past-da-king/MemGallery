package com.example.memgallery.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.memgallery.R
import com.example.memgallery.data.remote.ai.JobRouting
import com.example.memgallery.ui.design.MemTextField
import com.example.memgallery.ui.design.SegmentedChoice
import com.example.memgallery.ui.theme.memColors
import com.example.memgallery.ui.viewmodels.ApiKeyUiState
import com.example.memgallery.ui.viewmodels.SettingsViewModel

/** Everything the AI settings draw, as plain values, so the section can be previewed and proofed without the real key store. */
data class KeyUi(val key: String = "", val ui: ApiKeyUiState = ApiKeyUiState.Idle)

data class AiSettingsState(
    val main: String = "GEMINI",
    val voice: String = "GEMINI",
    val images: String = "GEMINI",
    val savedKeys: Set<String> = emptySet(),
    val gemini: KeyUi = KeyUi(),
    val geminiModelId: String = "gemini-3.1-flash-lite",
    val geminiModels: List<String> = emptyList(),
    val geminiModelsLoading: Boolean = false,
    val groq: KeyUi = KeyUi(),
    val groqModelId: String = "meta-llama/llama-4-maverick-17b-128e-instruct",
    val deepSeek: KeyUi = KeyUi(),
    val deepSeekModelId: String = "deepseek-flash",
    val deepSeekModels: List<String> = listOf("deepseek-flash", "deepseek-v4-pro"),
    val deepSeekThinking: Boolean = false,
    val customUrl: String = "https://api.openai.com/v1/",
    val customModel: String = "gpt-4o",
    val custom: KeyUi = KeyUi(),
    val localModelPath: String? = null,
    val localImport: ApiKeyUiState = ApiKeyUiState.Idle,
)

class AiSettingsActions(
    val setMain: (String) -> Unit = {},
    val setVoice: (String) -> Unit = {},
    val setImages: (String) -> Unit = {},
    val geminiKeyChange: (String) -> Unit = {}, val geminiValidate: () -> Unit = {}, val geminiClear: () -> Unit = {},
    val geminiSetModel: (String) -> Unit = {}, val geminiFetchModels: () -> Unit = {},
    val groqKeyChange: (String) -> Unit = {}, val groqValidate: () -> Unit = {}, val groqClear: () -> Unit = {}, val groqSetModel: (String) -> Unit = {},
    val deepSeekKeyChange: (String) -> Unit = {}, val deepSeekValidate: () -> Unit = {}, val deepSeekClear: () -> Unit = {},
    val deepSeekSetModel: (String) -> Unit = {}, val deepSeekSetThinking: (Boolean) -> Unit = {},
    val customUrlChange: (String) -> Unit = {}, val customModelChange: (String) -> Unit = {}, val customKeyChange: (String) -> Unit = {},
    val customSave: () -> Unit = {}, val customClear: () -> Unit = {},
    val importLocal: (Uri) -> Unit = {}, val clearLocal: () -> Unit = {},
)

/** Connects the AI settings to the view model. */
@Composable
fun AiSettingsSection(viewModel: SettingsViewModel) {
    val main by viewModel.aiProvider.collectAsState()
    val voice by viewModel.voiceProvider.collectAsState()
    val images by viewModel.imageProvider.collectAsState()
    val saved by viewModel.savedKeys.collectAsState()
    val gKey by viewModel.apiKey.collectAsState()
    val gUi by viewModel.apiKeyUiState.collectAsState()
    val gModel by viewModel.geminiModelId.collectAsState()
    val gModels by viewModel.availableGeminiModels.collectAsState()
    val gLoading by viewModel.isLoadingGeminiModels.collectAsState()
    val qKey by viewModel.groqApiKey.collectAsState()
    val qUi by viewModel.groqApiKeyUiState.collectAsState()
    val qModel by viewModel.groqModelId.collectAsState()
    val dKey by viewModel.deepSeekApiKey.collectAsState()
    val dUi by viewModel.deepSeekUiState.collectAsState()
    val dModel by viewModel.deepSeekModelId.collectAsState()
    val dModels by viewModel.deepSeekModels.collectAsState()
    val dThinking by viewModel.deepSeekThinking.collectAsState()
    val cUrl by viewModel.customBaseUrl.collectAsState()
    val cModel by viewModel.customModelName.collectAsState()
    val cKey by viewModel.customApiKey.collectAsState()
    val cUi by viewModel.customUiState.collectAsState()
    val lPath by viewModel.localModelPath.collectAsState()
    val lImport by viewModel.localModelImportState.collectAsState()
    var expanded by remember { mutableStateOf<String?>(null) }

    val state = AiSettingsState(
        main, voice, images, saved,
        KeyUi(gKey, gUi), gModel, gModels, gLoading,
        KeyUi(qKey, qUi), qModel,
        KeyUi(dKey, dUi), dModel, dModels, dThinking,
        cUrl, cModel, KeyUi(cKey, cUi),
        lPath, lImport
    )
    val actions = remember(viewModel) {
        AiSettingsActions(
            setMain = viewModel::setAiProvider, setVoice = viewModel::setVoiceProvider, setImages = viewModel::setImageProvider,
            geminiKeyChange = viewModel::onApiKeyChange, geminiValidate = viewModel::validateAndSaveKey,
            geminiClear = { viewModel.clearKey(); viewModel.refreshSavedKeys() },
            geminiSetModel = viewModel::setGeminiModelId, geminiFetchModels = viewModel::fetchGeminiModels,
            groqKeyChange = viewModel::onGroqApiKeyChange,
            groqValidate = { viewModel.validateAndSaveGroqKey(); viewModel.refreshSavedKeys() },
            groqClear = { viewModel.clearGroqKey(); viewModel.refreshSavedKeys() }, groqSetModel = viewModel::setGroqModelId,
            deepSeekKeyChange = viewModel::onDeepSeekApiKeyChange, deepSeekValidate = viewModel::validateAndSaveDeepSeekKey,
            deepSeekClear = viewModel::clearDeepSeekKey, deepSeekSetModel = viewModel::setDeepSeekModelId,
            deepSeekSetThinking = viewModel::setDeepSeekThinking,
            customUrlChange = viewModel::setCustomBaseUrl, customModelChange = viewModel::setCustomModelName,
            customKeyChange = viewModel::onCustomApiKeyChange,
            customSave = { viewModel.validateAndSaveCustomSettings(); viewModel.refreshSavedKeys() },
            customClear = { viewModel.clearCustomKey(); viewModel.refreshSavedKeys() },
            importLocal = viewModel::importLocalModel, clearLocal = viewModel::clearLocalModel,
        )
    }
    AiSettingsContent(state, actions, expanded, onExpand = { expanded = it })
}

/**
 * The AI part of Settings: one provider per job (Main brain, Voice transcription, Images), every provider key and model
 * in its own row. All keys stay saved at the same time.
 */
@Composable
fun AiSettingsContent(state: AiSettingsState, actions: AiSettingsActions, expanded: String?, onExpand: (String?) -> Unit) {
    val main = state.main
    val voice = state.voice
    val images = state.images
    val saved = state.savedKeys
    val gemini = stringResource(R.string.settings_ai_provider_gemini)
    val groq = stringResource(R.string.settings_ai_provider_groq)
    val deepseek = stringResource(R.string.ai_provider_deepseek)
    val local = stringResource(R.string.settings_ai_provider_local)
    val custom = stringResource(R.string.settings_ai_provider_custom)
    val off = stringResource(R.string.ai_option_off)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Jobs
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            JobBlock(
                title = stringResource(R.string.ai_main_title),
                hint = stringResource(R.string.ai_main_hint),
                shape = groupedShape(0, 3),
                note = if (main == JobRouting.DEEPSEEK) stringResource(R.string.ai_deepseek_text_only) else null,
            ) {
                SegmentedChoice(
                    options = listOf(
                        JobRouting.GEMINI to gemini, JobRouting.GROQ to groq, JobRouting.DEEPSEEK to deepseek,
                        JobRouting.LOCAL to local, JobRouting.CUSTOM to custom,
                    ),
                    selectedKey = main,
                    onSelect = actions.setMain,
                )
            }
            JobBlock(
                title = stringResource(R.string.ai_voice_title),
                hint = stringResource(R.string.ai_voice_hint),
                shape = groupedShape(1, 3),
                warn = if (voice == JobRouting.NONE) stringResource(R.string.ai_voice_missing) else null,
            ) {
                SegmentedChoice(
                    options = listOf(JobRouting.GEMINI to gemini, JobRouting.GROQ to groq, JobRouting.NONE to off),
                    selectedKey = voice,
                    onSelect = actions.setVoice,
                )
            }
            JobBlock(
                title = stringResource(R.string.ai_images_title),
                hint = stringResource(R.string.ai_images_hint),
                shape = groupedShape(2, 3),
                warn = if (images == JobRouting.NONE) stringResource(R.string.ai_images_missing) else null,
            ) {
                SegmentedChoice(
                    options = listOf(JobRouting.GEMINI to gemini, JobRouting.GROQ to groq, JobRouting.CUSTOM to custom, JobRouting.NONE to off),
                    selectedKey = images,
                    onSelect = actions.setImages,
                )
            }
        }

        // Keys and models, one row per provider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.ai_keys_hint),
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val rows = listOf(
                Triple(JobRouting.GEMINI, gemini, Icons.Default.Key),
                Triple(JobRouting.GROQ, groq, Icons.Default.Key),
                Triple(JobRouting.DEEPSEEK, deepseek, Icons.Default.Key),
                Triple(JobRouting.CUSTOM, custom, Icons.Default.Link),
                Triple(JobRouting.LOCAL, stringResource(R.string.ai_on_device_title), Icons.Default.Smartphone),
            )
            rows.forEachIndexed { i, (id, name, icon) ->
                ProviderRow(
                    name = name,
                    icon = icon,
                    status = run {
                        val roles = listOfNotNull(
                            if (id == main) stringResource(R.string.ai_main_title) else null,
                            if (id == voice) stringResource(R.string.ai_voice_title) else null,
                            if (id == images) stringResource(R.string.ai_images_title) else null,
                        )
                        val base = when {
                            id == JobRouting.LOCAL -> null
                            saved.contains(id) -> stringResource(R.string.ai_key_saved)
                            else -> stringResource(R.string.ai_key_missing)
                        }
                        val tail = roles.joinToString(", ")
                        when {
                            base == null && tail.isEmpty() -> null
                            base == null -> tail
                            tail.isEmpty() -> base
                            else -> base + " \u00B7 " + tail
                        }
                    },
                    keySaved = saved.contains(id),
                    inUse = id == main || id == voice || id == images,
                    expanded = expanded == id,
                    shape = groupedShape(i, rows.size),
                    onToggle = { onExpand(if (expanded == id) null else id) },
                ) {
                    when (id) {
                        JobRouting.GEMINI -> GeminiKeyPanel(state, actions)
                        JobRouting.GROQ -> GroqKeyPanel(state, actions)
                        JobRouting.DEEPSEEK -> DeepSeekPanel(state, actions)
                        JobRouting.CUSTOM -> CustomPanel(state, actions)
                        else -> LocalModelPanel(state, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun JobBlock(
    title: String,
    hint: String,
    shape: androidx.compose.ui.graphics.Shape,
    note: String? = null,
    warn: String? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(shape).background(scheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        content()
        if (note != null) InfoLine(note, Icons.Default.Info, scheme.secondaryContainer, scheme.onSecondaryContainer)
        if (warn != null) InfoLine(warn, Icons.Default.Error, MaterialTheme.memColors.amberContainer, MaterialTheme.memColors.onAmberContainer)
    }
}

@Composable
private fun InfoLine(text: String, icon: ImageVector, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(container).padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

@Composable
private fun ProviderRow(
    name: String,
    icon: ImageVector,
    status: String?,
    keySaved: Boolean,
    inUse: Boolean,
    expanded: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(shape).background(scheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (keySaved || status == null) scheme.primaryContainer else scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (keySaved || status == null) scheme.onPrimaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium, color = if (keySaved) scheme.primary else scheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = scheme.onSurfaceVariant)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

/** Key field plus validate/clear buttons and the status line. One component for every provider. */
@Composable
fun KeyForm(
    label: String,
    placeholder: String,
    apiKey: String,
    uiState: ApiKeyUiState,
    onApiKeyChange: (String) -> Unit,
    onValidate: () -> Unit,
    onClear: () -> Unit,
    validateLabel: String = stringResource(R.string.common_validate),
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MemTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = label,
            placeholder = placeholder,
            leadingIcon = Icons.Default.VpnKey,
            isSecret = true,
            errorText = (uiState as? ApiKeyUiState.Error)?.message,
        )
        StatusLine(uiState)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onValidate, modifier = Modifier.weight(1f), enabled = apiKey.isNotBlank() && uiState !is ApiKeyUiState.Loading) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(validateLabel)
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f), enabled = apiKey.isNotBlank()) {
                Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.common_clear))
            }
        }
    }
}

@Composable
fun StatusLine(state: ApiKeyUiState) {
    val scheme = MaterialTheme.colorScheme
    when (state) {
        is ApiKeyUiState.Success -> InfoLine(state.message, Icons.Default.CheckCircle, scheme.secondaryContainer, scheme.onSecondaryContainer)
        is ApiKeyUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.common_validating), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        else -> {}
    }
}

@Composable
private fun GeminiKeyPanel(st: AiSettingsState, ac: AiSettingsActions) {
    var sheet by remember { mutableStateOf(false) }
    GeminiModelSelection(selectedModelId = st.geminiModelId, onClick = { sheet = true; if (st.geminiModels.isEmpty()) ac.geminiFetchModels() })
    KeyForm(
        label = stringResource(R.string.settings_gemini_api_key_label),
        placeholder = stringResource(R.string.settings_gemini_placeholder),
        apiKey = st.gemini.key, uiState = st.gemini.ui,
        onApiKeyChange = ac.geminiKeyChange, onValidate = ac.geminiValidate, onClear = ac.geminiClear,
    )
    if (sheet) GeminiModelSheet(
        currentModelId = st.geminiModelId, availableModels = st.geminiModels, isLoading = st.geminiModelsLoading,
        onModelSelected = { ac.geminiSetModel(it); sheet = false }, onRefresh = ac.geminiFetchModels, onDismiss = { sheet = false }
    )
}

@Composable
private fun GroqKeyPanel(st: AiSettingsState, ac: AiSettingsActions) {
    var sheet by remember { mutableStateOf(false) }
    GridModelSelection(selectedModelId = st.groqModelId, onClick = { sheet = true })
    KeyForm(
        label = stringResource(R.string.settings_groq_api_key_label),
        placeholder = stringResource(R.string.settings_groq_placeholder),
        apiKey = st.groq.key, uiState = st.groq.ui,
        onApiKeyChange = ac.groqKeyChange, onValidate = ac.groqValidate, onClear = ac.groqClear,
        validateLabel = stringResource(R.string.common_save),
    )
    if (sheet) GroqModelSheet(currentModelId = st.groqModelId, onModelSelected = { ac.groqSetModel(it); sheet = false }, onDismiss = { sheet = false })
}

@Composable
private fun DeepSeekPanel(st: AiSettingsState, ac: AiSettingsActions) {
    val scheme = MaterialTheme.colorScheme
    Text(stringResource(R.string.ai_deepseek_get_key), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    Text(stringResource(R.string.ai_deepseek_model), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
    val shown = (st.deepSeekModels + st.deepSeekModelId).distinct().take(4)
    SegmentedChoice(options = shown.map { it to it.removePrefix("deepseek-") }, selectedKey = st.deepSeekModelId, onSelect = ac.deepSeekSetModel, selectedCheck = false)
    Row(
        Modifier.fillMaxWidth().clickable { ac.deepSeekSetThinking(!st.deepSeekThinking) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ai_deepseek_thinking), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Text(stringResource(R.string.ai_deepseek_thinking_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(checked = st.deepSeekThinking, onCheckedChange = ac.deepSeekSetThinking)
    }
    KeyForm(
        label = stringResource(R.string.ai_deepseek_key_label),
        placeholder = stringResource(R.string.ai_deepseek_key_placeholder),
        apiKey = st.deepSeek.key, uiState = st.deepSeek.ui,
        onApiKeyChange = ac.deepSeekKeyChange, onValidate = ac.deepSeekValidate, onClear = ac.deepSeekClear,
    )
}

@Composable
private fun CustomPanel(st: AiSettingsState, ac: AiSettingsActions) {
    Text(stringResource(R.string.settings_custom_provider_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CustomProviderSection(
        baseUrl = st.customUrl, modelName = st.customModel, apiKey = st.custom.key, uiState = st.custom.ui,
        onBaseUrlChange = ac.customUrlChange, onModelNameChange = ac.customModelChange,
        onApiKeyChange = ac.customKeyChange, onSave = ac.customSave, onClearKey = ac.customClear
    )
}

@Composable
private fun LocalModelPanel(st: AiSettingsState, ac: AiSettingsActions) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val path = st.localModelPath
    val importState = st.localImport
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(ac.importLocal) }
    Text(stringResource(R.string.settings_local_model_description), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    if (path.isNullOrBlank()) {
        FilledTonalButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_local_model_import))
        }
        TextButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/index#models"))) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.settings_local_model_download_link)) }
    } else {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.secondaryContainer).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = scheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_local_model_loaded), style = MaterialTheme.typography.titleSmall, color = scheme.onSecondaryContainer)
            }
            Text(path.substringAfterLast("/"), style = MaterialTheme.typography.bodySmall, color = scheme.onSecondaryContainer)
            OutlinedButton(
                onClick = ac.clearLocal, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error)
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_local_model_delete))
            }
        }
    }
    if (importState is ApiKeyUiState.Loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(R.string.settings_local_model_importing), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    } else if (importState is ApiKeyUiState.Error) {
        Text(
            stringResource(R.string.settings_error_with_message, importState.message),
            color = scheme.error, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun CustomProviderSection(
    baseUrl: String,
    modelName: String,
    apiKey: String,
    uiState: ApiKeyUiState,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClearKey: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MemTextField(
            value = baseUrl, onValueChange = onBaseUrlChange,
            label = stringResource(R.string.settings_custom_base_url_label),
            placeholder = stringResource(R.string.settings_custom_base_url_placeholder),
            leadingIcon = Icons.Default.Link,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        MemTextField(
            value = modelName, onValueChange = onModelNameChange,
            label = stringResource(R.string.settings_custom_model_name_label),
            placeholder = stringResource(R.string.settings_custom_model_name_placeholder),
            leadingIcon = Icons.Default.Key,
        )
        MemTextField(
            value = apiKey, onValueChange = onApiKeyChange,
            label = stringResource(R.string.settings_custom_api_key_label),
            leadingIcon = Icons.Default.VpnKey,
            isSecret = true,
            errorText = (uiState as? ApiKeyUiState.Error)?.message,
        )
        StatusLine(uiState)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = uiState !is ApiKeyUiState.Loading) {
                Text(stringResource(R.string.settings_custom_button_validate_save))
            }
            if (apiKey.isNotEmpty()) {
                OutlinedButton(onClick = onClearKey) { Text(stringResource(R.string.common_clear)) }
            }
        }
    }
}

/** Web search: Free (no key), Brave or Tavily. */
@Composable
fun WebSearchSection(viewModel: SettingsViewModel) {
    val provider by viewModel.searchProvider.collectAsState()
    val key by viewModel.searchKey.collectAsState()
    val ui by viewModel.searchUiState.collectAsState()
    WebSearchContent(
        provider, key, ui,
        onProvider = viewModel::setSearchProvider, onKey = viewModel::onSearchKeyChange,
        onSaveTest = viewModel::saveSearchKey, onTest = viewModel::testSearch
    )
}

@Composable
fun WebSearchContent(
    provider: String, key: String, ui: ApiKeyUiState,
    onProvider: (String) -> Unit, onKey: (String) -> Unit, onSaveTest: () -> Unit, onTest: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.websearch_hint), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        SegmentedChoice(
            options = listOf("AUTO" to stringResource(R.string.search_free), "BRAVE" to "Brave", "TAVILY" to "Tavily"),
            selectedKey = provider,
            onSelect = onProvider,
        )
        Text(
            stringResource(when (provider) { "BRAVE" -> R.string.search_brave_hint; "TAVILY" -> R.string.search_tavily_hint; else -> R.string.search_free_hint }),
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
        )
        if (provider != "AUTO") {
            MemTextField(
                value = key, onValueChange = onKey,
                label = stringResource(R.string.search_key_label, if (provider == "BRAVE") "Brave" else "Tavily"),
                leadingIcon = Icons.Default.VpnKey, isSecret = true,
                errorText = (ui as? ApiKeyUiState.Error)?.message,
            )
        }
        StatusLine(ui)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (provider != "AUTO") {
                FilledTonalButton(onClick = onSaveTest, modifier = Modifier.weight(1f), enabled = key.isNotBlank()) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.search_save_test))
                }
            }
            OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.search_test))
            }
        }
    }
}
