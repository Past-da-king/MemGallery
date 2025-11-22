package com.example.memgallery.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.viewmodels.MemoryCreationViewModel
import com.example.memgallery.ui.viewmodels.MemoryCreationUiState
import com.example.memgallery.ui.viewmodels.MemoryUpdateViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCaptureScreen(
    navController: NavController,
    initialImageUri: String? = null,
    initialAudioUri: String? = null,
    initialUserText: String? = null,
    initialBookmarkUrl: String? = null,
    memoryId: Int? = null,
    creationViewModel: MemoryCreationViewModel = hiltViewModel(),
    updateViewModel: MemoryUpdateViewModel = hiltViewModel()
) {
    val isEditMode = memoryId != null
    val memory by updateViewModel.memory.collectAsState()

    val draftImageUri by creationViewModel.draftImageUri.collectAsState()
    val draftAudioUri by creationViewModel.draftAudioUri.collectAsState()
    val draftUserText by creationViewModel.draftUserText.collectAsState()
    val draftBookmarkUrl by creationViewModel.draftBookmarkUrl.collectAsState()
    val uiState by creationViewModel.uiState.collectAsState()

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var showAddImageSheet by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var tempUrl by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { creationViewModel.setDraftImageUri(it.toString()) }
        showAddImageSheet = false
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                tempImageUri?.let { creationViewModel.setDraftImageUri(it.toString()) }
            }
            showAddImageSheet = false
        }
    )

    LaunchedEffect(memoryId) {
        if (isEditMode) {
            updateViewModel.loadMemory(memoryId!!)
        }
    }

    LaunchedEffect(memory) {
        if (isEditMode && memory != null) {
            creationViewModel.setDraftImageUri(memory!!.imageUri)
            creationViewModel.setDraftAudioUri(memory!!.audioFilePath)
            creationViewModel.setDraftUserText(memory!!.userText)
            creationViewModel.setDraftBookmarkUrl(memory!!.bookmarkUrl)
        }
    }

    // Set initial draft values from navigation arguments
    LaunchedEffect(initialImageUri, initialAudioUri, initialUserText, initialBookmarkUrl) {
        if (!isEditMode) {
            if (initialImageUri != null) creationViewModel.setDraftImageUri(initialImageUri)
            if (initialAudioUri != null) creationViewModel.setDraftAudioUri(initialAudioUri)
            if (initialBookmarkUrl != null) creationViewModel.setDraftBookmarkUrl(initialBookmarkUrl)
            if (initialUserText != null) {
                // URL decode the text to handle special characters
                val decodedText = try {
                    java.net.URLDecoder.decode(initialUserText, "UTF-8")
                } catch (e: Exception) {
                    initialUserText
                }
                creationViewModel.setDraftUserText(decodedText)
            }
        }
    }

    // Navigate back on success
    LaunchedEffect(uiState) {
        if (uiState is MemoryCreationUiState.Success) {
            navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
            creationViewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Memory" else "New Memory Captured") },
                actions = {
                    IconButton(onClick = {
                        navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
                        creationViewModel.resetState()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Captured Content Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = draftImageUri == null && !isEditMode) {
                            showAddImageSheet = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (draftImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(model = Uri.parse(draftImageUri!!)),
                            contentDescription = "Captured Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Add Image",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tap to add an image", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (draftAudioUri != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Icon(Icons.Default.Mic, contentDescription = "Audio Added")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audio added")
                    }
                }
                if (draftUserText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Icon(Icons.Default.EditNote, contentDescription = "Text Added")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(draftUserText!!, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                if (draftBookmarkUrl != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Icon(Icons.Default.Link, contentDescription = "URL Added")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(draftBookmarkUrl!!, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button Group
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { navController.navigate(Screen.AudioCapture.createRoute(imageUri = draftImageUri, audioUri = draftAudioUri, userText = draftUserText)) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    enabled = draftAudioUri == null
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Add Audio", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Audio")
                }
                Button(
                    onClick = { navController.navigate(Screen.TextInput.createRoute(imageUri = draftImageUri, audioUri = draftAudioUri, userText = draftUserText)) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = "Add Text", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Text")
                }
                Button(
                    onClick = { showUrlDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = "Add URL", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("URL")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Now Button
            Button(
                onClick = {
                    if (isEditMode) {
                        val updatedMemory = memory!!.copy(
                            userText = draftUserText,
                            imageUri = draftImageUri,
                            audioFilePath = draftAudioUri,
                            bookmarkUrl = draftBookmarkUrl
                        )
                        updateViewModel.updateMemory(updatedMemory)
                        navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
                    } else {
                        creationViewModel.createMemory()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                enabled = uiState !is MemoryCreationUiState.Loading
            ) {
                if (uiState is MemoryCreationUiState.Loading && !isEditMode) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.AddCircle, contentDescription = "Save Now", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEditMode) "Update" else "Save Now")
                }
            }

            if (uiState is MemoryCreationUiState.Error) {
                Text(text = (uiState as MemoryCreationUiState.Error).message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showAddImageSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddImageSheet = false },
                sheetState = sheetState
            ) {
                AddImageSourceSheet(
                    onGalleryClick = { imagePickerLauncher.launch("image/*") },
                    onCameraClick = {
                        val newImageUri = createImageUri(context)
                        tempImageUri = newImageUri
                        cameraLauncher.launch(newImageUri)
                    }
                )
            }
        }
        
        if (showUrlDialog) {
            AlertDialog(
                onDismissRequest = { showUrlDialog = false },
                title = { Text("Add URL") },
                text = {
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Enter URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        creationViewModel.setDraftBookmarkUrl(tempUrl)
                        showUrlDialog = false
                        tempUrl = ""
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUrlDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun AddImageSourceSheet(
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Add an image", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onGalleryClick).padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Image, contentDescription = "Choose from Gallery")
            Spacer(modifier = Modifier.width(16.dp))
            Text("Choose from Gallery", style = MaterialTheme.typography.bodyLarge)
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onCameraClick).padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "Take a Picture")
            Spacer(modifier = Modifier.width(16.dp))
            Text("Take a Picture", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun createImageUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val imageFileName = "JPEG_${timeStamp}_"
    val storageDir: File? = context.filesDir
    val image = File.createTempFile(
        imageFileName,  /* prefix */
        ".jpg",         /* suffix */
        storageDir      /* directory */
    )
    return FileProvider.getUriForFile(
        Objects.requireNonNull(context),
        context.packageName + ".provider",
        image
    )
}
