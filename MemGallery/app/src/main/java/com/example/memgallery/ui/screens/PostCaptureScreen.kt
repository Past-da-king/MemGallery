package com.example.memgallery.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.theme.memColors
import com.example.memgallery.ui.design.MemSheet
import com.example.memgallery.ui.design.SheetTitle
import com.example.memgallery.ui.design.appStatusBarsPadding
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.design.memoryPainter
import com.example.memgallery.ui.design.MemoryKind
import com.example.memgallery.ui.design.kind
import com.example.memgallery.ui.viewmodels.MemoryCreationUiState
import com.example.memgallery.ui.viewmodels.MemoryCreationViewModel
import com.example.memgallery.ui.viewmodels.MemoryUpdateViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects

/** The five analysis steps, with the real state each one is in. */
enum class StepState { Done, Active, Pending }

data class ProcessingStep(val label: String, val state: StepState, val detail: String? = null)

/**
 * Derives the progress of the analysis from what has really been written to the memory.
 * The worker stores its results together, so steps move as the fields appear.
 */
fun processingSteps(memory: MemoryEntity?, labels: List<String>): List<ProcessingStep> {
    val m = memory
    val done = BooleanArray(5)
    if (m != null) {
        val finished = m.status == "COMPLETED"
        done[0] = finished || m.aiImageAnalysis != null || m.aiAudioTranscription != null
        done[1] = finished || m.aiImageAnalysis != null || m.aiAudioTranscription != null
        done[2] = finished || !m.aiTags.isNullOrEmpty()
        done[3] = finished || !m.aiSummary.isNullOrBlank()
        done[4] = finished
    }
    val firstOpen = done.indexOfFirst { !it }
    return labels.mapIndexed { i, label ->
        ProcessingStep(
            label,
            when {
                done[i] -> StepState.Done
                i == firstOpen && m?.status != "FAILED" -> StepState.Active
                else -> StepState.Pending
            },
            detail = if (i == 2 && done[2] && !m?.aiTags.isNullOrEmpty()) m?.aiTags?.take(4)?.joinToString(", ") else null
        )
    }
}

@Composable
fun PostCaptureScreen(
    navController: NavController,
    initialImageUri: String? = null,
    initialAudioUri: String? = null,
    initialUserText: String? = null,
    initialBookmarkUrl: String? = null,
    memoryId: Int? = null,
    openUrlSheet: Boolean = false,
    creationViewModel: MemoryCreationViewModel = hiltViewModel(),
    updateViewModel: MemoryUpdateViewModel = hiltViewModel()
) {
    val isEditMode = memoryId != null
    val memory by updateViewModel.memory.collectAsState()
    val providerKey by updateViewModel.providerKey.collectAsState()

    val draftImageUris by creationViewModel.draftImageUris.collectAsState()
    val draftAudioUri by creationViewModel.draftAudioUri.collectAsState()
    val draftUserText by creationViewModel.draftUserText.collectAsState()
    val draftBookmarkUrl by creationViewModel.draftBookmarkUrl.collectAsState()
    val uiState by creationViewModel.uiState.collectAsState()

    val context = LocalContext.current
    var showAddImageSheet by remember { mutableStateOf(false) }
    var showUrlSheet by remember { mutableStateOf(false) }
    var tempUrl by remember { mutableStateOf("") }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    // Once saved, the screen turns into the live view of the analysis.
    var processingId by remember { mutableStateOf<Int?>(null) }
    // What the memory was made of, kept so the processing view still knows after the draft is cleared.
    var savedKind by remember { mutableStateOf(MemoryKind.Note) }
    var savedFirstImage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openUrlSheet) { if (openUrlSheet) showUrlSheet = true }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        uris.forEach { creationViewModel.addDraftImageUri(it.toString()) }
        showAddImageSheet = false
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempImageUri?.let { creationViewModel.addDraftImageUri(it.toString()) }
        showAddImageSheet = false
    }

    LaunchedEffect(memoryId) { if (isEditMode) updateViewModel.loadMemory(memoryId!!) }

    LaunchedEffect(memory) {
        if (isEditMode && memory != null) {
            if (initialImageUri == null) {
                val uris = memory!!.imageUris ?: listOfNotNull(memory!!.imageUri)
                creationViewModel.setDraftImageUris(uris)
            }
            if (initialAudioUri == null) creationViewModel.setDraftAudioUri(memory!!.audioFilePath)
            if (initialUserText == null) creationViewModel.setDraftUserText(memory!!.userText)
            if (initialBookmarkUrl == null) creationViewModel.setDraftBookmarkUrl(memory!!.bookmarkUrl)
        }
    }

    LaunchedEffect(initialImageUri, initialAudioUri, initialUserText, initialBookmarkUrl) {
        if (initialImageUri != null && draftImageUris.isEmpty()) creationViewModel.addDraftImageUri(initialImageUri)
        if (initialAudioUri != null) creationViewModel.setDraftAudioUri(initialAudioUri)
        if (initialBookmarkUrl != null) creationViewModel.setDraftBookmarkUrl(initialBookmarkUrl)
        if (initialUserText != null) {
            val decoded = try { java.net.URLDecoder.decode(initialUserText, "UTF-8") } catch (e: Exception) { initialUserText }
            creationViewModel.setDraftUserText(decoded)
        }
    }

    fun goHome() {
        navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
        creationViewModel.resetState()
    }

    // Saved: show the live analysis instead of leaving at once.
    LaunchedEffect(uiState) {
        val s = uiState
        if (s is MemoryCreationUiState.Success) {
            savedKind = when {
                draftBookmarkUrl != null -> MemoryKind.Link
                draftImageUris.isNotEmpty() -> MemoryKind.Image
                draftAudioUri != null -> MemoryKind.Audio
                else -> MemoryKind.Note
            }
            savedFirstImage = draftImageUris.firstOrNull()
            processingId = s.newMemoryId.toInt()
            updateViewModel.loadMemory(s.newMemoryId.toInt())
        }
    }

    val pid = processingId
    if (pid != null) {
        BackHandler { goHome() }
        val labels = processingLabels(savedKind)
        val steps = processingSteps(memory, labels)
        ProcessingContent(
            kind = savedKind,
            image = savedFirstImage,
            providerLabel = providerLabel(providerKey),
            isLocal = providerKey == "LOCAL",
            tags = memory?.aiTags.orEmpty(),
            steps = steps,
            status = memory?.status ?: "PENDING",
            onClose = ::goHome,
            onKeepInBackground = ::goHome,
            onOpenMemory = {
                navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
                creationViewModel.resetState()
                navController.navigate(Screen.Detail.createRoute(pid))
            }
        )
        return
    }

    DraftContent(
        isEditMode = isEditMode,
        images = draftImageUris,
        hasAudio = draftAudioUri != null,
        userText = draftUserText,
        bookmarkUrl = draftBookmarkUrl,
        saving = uiState is MemoryCreationUiState.Loading && !isEditMode,
        loadingExisting = isEditMode && memory == null,
        error = (uiState as? MemoryCreationUiState.Error)?.message,
        onClose = ::goHome,
        onRemoveImage = creationViewModel::removeDraftImageUri,
        onAddImage = { showAddImageSheet = true },
        onAddAudio = {
            navController.navigate(Screen.AudioCapture.createRoute(imageUri = draftImageUris.firstOrNull(), audioUri = draftAudioUri, userText = draftUserText, bookmarkUrl = draftBookmarkUrl, memoryId = memoryId))
        },
        onAddText = {
            navController.navigate(Screen.TextInput.createRoute(imageUri = draftImageUris.firstOrNull(), audioUri = draftAudioUri, userText = draftUserText, bookmarkUrl = draftBookmarkUrl, memoryId = memoryId))
        },
        onAddUrl = { showUrlSheet = true },
        onSave = {
            if (isEditMode) {
                memory?.let { existing ->
                    updateViewModel.updateMemory(
                        existing.copy(
                            userText = draftUserText,
                            imageUri = draftImageUris.firstOrNull(),
                            imageUris = draftImageUris,
                            audioFilePath = draftAudioUri,
                            bookmarkUrl = draftBookmarkUrl
                        )
                    )
                    goHome()
                }
            } else {
                creationViewModel.createMemory()
            }
        }
    )

    if (showAddImageSheet) {
        MemSheet(onDismiss = { showAddImageSheet = false }) {
            SheetTitle(stringResource(R.string.post_capture_sheet_add_image))
            SourceRow(Icons.Rounded.Image, stringResource(R.string.post_capture_choose_gallery)) { imagePickerLauncher.launch("image/*") }
            SourceRow(Icons.Rounded.PhotoCamera, stringResource(R.string.post_capture_take_picture)) {
                val newImageUri = createImageUri(context)
                tempImageUri = newImageUri
                cameraLauncher.launch(newImageUri)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showUrlSheet) {
        MemSheet(onDismiss = { showUrlSheet = false }) {
            SheetTitle(stringResource(R.string.post_capture_sheet_add_link_title), stringResource(R.string.post_capture_sheet_add_link_body))
            OutlinedTextField(
                value = tempUrl,
                onValueChange = { tempUrl = it },
                placeholder = { Text(stringResource(R.string.common_url_placeholder)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Link, null) }
            )
            Button(
                onClick = {
                    creationViewModel.setDraftBookmarkUrl(tempUrl)
                    showUrlSheet = false
                    tempUrl = ""
                },
                modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp)
            ) { Text(stringResource(R.string.post_capture_button_add_link), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun processingLabels(kind: MemoryKind): List<String> = listOf(
    stringResource(if (kind == MemoryKind.Audio) R.string.processing_step_listen else R.string.processing_step_read),
    stringResource(if (kind == MemoryKind.Image) R.string.processing_step_describe else R.string.processing_step_understand),
    stringResource(R.string.processing_step_tags),
    stringResource(R.string.processing_step_summary),
    stringResource(R.string.processing_step_tasks),
)

@Composable
private fun providerLabel(key: String): String = when (key) {
    "LOCAL" -> stringResource(R.string.processing_provider_local)
    "GROQ" -> "Groq"
    "GEMINI" -> "Gemini"
    "CUSTOM" -> stringResource(R.string.settings_ai_provider_custom)
    else -> key
}

@Composable
private fun SourceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The captured content on a slightly tilted card, with two soft shapes behind it. */
@Composable
private fun CapturedHero(images: List<String>, kind: MemoryKind, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(140.dp).clip(MemShape.softBurst).background(scheme.tertiaryContainer.copy(alpha = 0.9f)))
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 0.dp).size(140.dp).clip(MemShape.cookie).background(scheme.primaryContainer))
        val first = images.firstOrNull()
        if (first != null) {
            Image(
                memoryPainter(first), null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 176.dp, height = 224.dp)
                    .rotate(-3f)
                    .shadow(12.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .background(scheme.surfaceContainerHigh)
            )
        } else {
            Box(
                Modifier.size(width = 170.dp, height = 216.dp).rotate(-3f).shadow(12.dp, RoundedCornerShape(32.dp)).clip(RoundedCornerShape(32.dp)).background(scheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (kind) {
                        MemoryKind.Audio -> Icons.Rounded.Mic
                        MemoryKind.Link -> Icons.Rounded.Link
                        else -> Icons.Rounded.EditNote
                    },
                    null, tint = scheme.primary, modifier = Modifier.size(72.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProcessingContent(
    kind: MemoryKind,
    image: String?,
    providerLabel: String,
    isLocal: Boolean,
    tags: List<String>,
    steps: List<ProcessingStep>,
    status: String,
    onClose: () -> Unit,
    onKeepInBackground: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val doneCount = steps.count { it.state == StepState.Done }
    val progress = doneCount / steps.size.toFloat()
    val active = steps.indexOfFirst { it.state == StepState.Active }
    val completed = status == "COMPLETED"
    val failed = status == "FAILED"
    val title = when {
        completed -> stringResource(R.string.processing_title_done)
        failed -> stringResource(R.string.processing_title_failed)
        else -> stringResource(
            when (kind) {
                MemoryKind.Image -> R.string.processing_title_image
                MemoryKind.Audio -> R.string.processing_title_audio
                MemoryKind.Link -> R.string.processing_title_link
                MemoryKind.Note -> R.string.processing_title_note
            }
        )
    }
    Column(Modifier.fillMaxSize().background(scheme.surface).appStatusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = scheme.onSurface)
            }
            Text(stringResource(R.string.post_capture_new_title), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = scheme.onSurface)
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
            CapturedHero(listOfNotNull(image), kind, Modifier.height(236.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(56.dp),
                    containerColor = scheme.primaryContainer,
                    indicatorColor = scheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface)
                    Text(
                        if (isLocal) stringResource(R.string.processing_sub_local) else stringResource(R.string.processing_sub_cloud, providerLabel),
                        style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.processing_step_of, (if (active >= 0) active else steps.size - 1) + 1, steps.size),
                    style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant
                )
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = scheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(scheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Smartphone.takeIf { isLocal } ?: Icons.Rounded.AddAPhoto, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(providerLabel, style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer)
                }
                tags.take(3).forEach { tag ->
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.LocalOffer, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tag, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                steps.forEach { StepRow(it) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).appNavigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onKeepInBackground, modifier = Modifier.weight(1.15f).height(56.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(stringResource(R.string.processing_keep_background), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Button(onClick = onOpenMemory, enabled = completed, modifier = Modifier.weight(1f).height(56.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Filled.Check, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.processing_open_memory), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepRow(step: ProcessingStep) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (step.state == StepState.Active) scheme.surfaceContainerHigh else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (step.state) {
            StepState.Done -> Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.memColors.successContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.memColors.onSuccessContainer, modifier = Modifier.size(22.dp))
            }
            StepState.Active -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(scheme.primary), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(28.dp), color = scheme.onPrimary)
            }
            StepState.Pending -> Box(Modifier.size(40.dp).border(2.dp, scheme.outlineVariant, CircleShape))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                step.label,
                style = if (step.state == StepState.Pending) MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal) else MaterialTheme.typography.titleLarge,
                color = if (step.state == StepState.Pending) scheme.onSurfaceVariant.copy(alpha = 0.8f) else scheme.onSurface
            )
            if (step.detail != null) Text(step.detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DraftContent(
    isEditMode: Boolean,
    images: List<String>,
    hasAudio: Boolean,
    userText: String?,
    bookmarkUrl: String?,
    saving: Boolean,
    loadingExisting: Boolean,
    error: String?,
    onClose: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onAddImage: () -> Unit,
    onAddAudio: () -> Unit,
    onAddText: () -> Unit,
    onAddUrl: () -> Unit,
    onSave: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.surface).appStatusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = scheme.onSurface)
            }
            Text(
                if (isEditMode) stringResource(R.string.post_capture_edit_title) else stringResource(R.string.post_capture_new_title),
                Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = scheme.onSurface
            )
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            // Pictures: a row of cards, each removable, with a tile to add more
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(32.dp)).background(scheme.surfaceContainerLow)) {
                if (images.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().clickable(onClick = onAddImage),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.size(88.dp).clip(MemShape.cookie).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AddAPhoto, stringResource(R.string.post_capture_cd_add_image), tint = scheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.post_capture_empty_tap_add), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
                    }
                } else {
                    LazyRow(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        itemsIndexed(images) { index, uri ->
                            Box(Modifier.fillMaxHeight().aspectRatio(0.8f).clip(RoundedCornerShape(24.dp))) {
                                Image(
                                    memoryPainter(uri), stringResource(R.string.post_capture_cd_captured_image, index),
                                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                )
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(Color(0x8C141028)).clickable { onRemoveImage(uri) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.Close, stringResource(R.string.post_capture_cd_remove_image), tint = Color.White, modifier = Modifier.size(18.dp)) }
                            }
                        }
                        item {
                            Box(
                                Modifier.fillMaxHeight().aspectRatio(0.8f).clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainerHigh).clickable(onClick = onAddImage),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.post_capture_cd_add_more), tint = scheme.onSurfaceVariant)
                                    Text(stringResource(R.string.post_capture_label_add), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // What is attached
            if (hasAudio) AttachedRow(Icons.Rounded.Mic, stringResource(R.string.post_capture_audio_added_status))
            if (userText != null) AttachedRow(Icons.Rounded.EditNote, userText)
            if (bookmarkUrl != null) AttachedRow(Icons.Rounded.Link, bookmarkUrl)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AddChip(Icons.Rounded.Mic, stringResource(R.string.post_capture_label_audio), enabled = !hasAudio, onClick = onAddAudio, modifier = Modifier.weight(1f))
                AddChip(Icons.Rounded.EditNote, stringResource(R.string.post_capture_label_text), enabled = true, onClick = onAddText, modifier = Modifier.weight(1f))
                AddChip(Icons.Rounded.Link, stringResource(R.string.post_capture_label_url), enabled = true, onClick = onAddUrl, modifier = Modifier.weight(1f))
            }
        }
        if (error != null) Text(error, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = scheme.error, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onSave,
            enabled = !saving && !loadingExisting,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp).appNavigationBarsPadding()
        ) {
            if (saving || loadingExisting) {
                LoadingIndicator(Modifier.size(28.dp), color = scheme.onPrimary)
            } else {
                Icon(Icons.Filled.Check, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isEditMode) stringResource(R.string.common_update) else stringResource(R.string.post_capture_save_now), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AttachedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AddChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) scheme.secondaryContainer else scheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) scheme.onSecondaryContainer else scheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (enabled) scheme.onSecondaryContainer else scheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

private fun createImageUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val imageFileName = "JPEG_${timeStamp}_"
    val storageDir: File? = context.filesDir
    val image = File.createTempFile(imageFileName, ".jpg", storageDir)
    return FileProvider.getUriForFile(Objects.requireNonNull(context), context.packageName + ".provider", image)
}
