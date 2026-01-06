package com.example.memgallery.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()
    val apiKeyUiState by viewModel.apiKeyUiState.collectAsState()
    val groqApiKeyUiState by viewModel.groqApiKeyUiState.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false // Force valid selection before moving
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> ChooseAiPage(viewModel = viewModel)
                2 -> ApiKeySetupPage(viewModel = viewModel)
                3 -> PermissionsPage()
                4 -> HowItWorksPage(
                    onGetStarted = {
                        viewModel.completeOnboarding()
                        navController.navigate(Screen.Gallery.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        // Pager Indicators & Navigation
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(5) { iteration ->
                    val color = if (pagerState.currentPage == iteration)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            if (pagerState.currentPage < 4) {
                val isNextEnabled = when (pagerState.currentPage) {
                    1 -> true // Always allow moving from selection (selection is immediate)
                    2 -> if (aiProvider == "GROQ") 
                            groqApiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Success
                         else 
                            apiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Success
                    else -> true
                }

                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            // API Key Validation Check
                            if (pagerState.currentPage == 2) {
                                val isValid = if (aiProvider == "GROQ") 
                                    groqApiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Success
                                else 
                                    apiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Success
                                    
                                if (!isValid) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Please validate your API key before continuing",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                            }
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    containerColor = if (isNextEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isNextEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                }
            }
        }
    }
}

@Composable
fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Icon
        Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.memgallery.R.mipmap.ic_launcher_foreground),
            contentDescription = "MemGallery Icon",
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Welcome to MemGallery",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your external brain for memories.\nCapture, process, and recall everything.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChooseAiPage(viewModel: SettingsViewModel = hiltViewModel()) {
    val aiProvider by viewModel.aiProvider.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choose Your Intelligence",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Select the AI brain needed to power MemGallery.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Google Gemini Card
        AiProviderCard(
            title = "Google Gemini",
            description = "Google's trusted multimodal AI. Good balance of speed and reasoning.",
            isSelected = aiProvider == "GEMINI",
            onClick = { viewModel.setAiProvider("GEMINI") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Groq Card
        AiProviderCard(
            title = "Groq LPU (Llama 3)",
            description = "Blazing fast inference. Best for real-time interactions.",
            isSelected = aiProvider == "GROQ",
            onClick = { viewModel.setAiProvider("GROQ") }
        )
    }
}

@Composable
fun AiProviderCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder Icon
            Icon(
                imageVector = if (title.contains("Groq")) Icons.Default.Layers else Icons.Default.Check, // Replace with proper icons if available
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ApiKeySetupPage(viewModel: SettingsViewModel = hiltViewModel()) {
    val aiProvider by viewModel.aiProvider.collectAsState()
    
    // Dynamic State based on provider
    val apiKey by if (aiProvider == "GROQ") viewModel.groqApiKey.collectAsState() else viewModel.apiKey.collectAsState()
    val apiKeyUiState by if (aiProvider == "GROQ") viewModel.groqApiKeyUiState.collectAsState() else viewModel.apiKeyUiState.collectAsState()

    val providerName = if (aiProvider == "GROQ") "Groq Cloud" else "Google AI Studio"
    val providerUrl = if (aiProvider == "GROQ") "console.groq.com" else "aistudio.google.com"
    val inputLabel = if (aiProvider == "GROQ") "Groq API Key" else "Google AI Studio API Key"
    val placeholder = if (aiProvider == "GROQ") "gsk_..." else "AIzaSy..."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Key Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check, // Using Check as placeholder for Key
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "API Key Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "MemGallery uses $providerName to analyze your memories.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Instructions Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Get your FREE API key:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Visit $providerUrl\n2. Sign in\n3. Create API Key\n4. Copy and paste below",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // API Key Input
        OutlinedTextField(
            value = apiKey,
            onValueChange = { 
                if (aiProvider == "GROQ") viewModel.onGroqApiKeyChange(it) 
                else viewModel.onApiKeyChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(inputLabel) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            isError = apiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Error
        )

        if (apiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Error) {
            Text(
                text = (apiKeyUiState as com.example.memgallery.ui.viewmodels.ApiKeyUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (apiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Success) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "API Key Valid!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (aiProvider == "GROQ") viewModel.validateAndSaveGroqKey()
                else viewModel.validateAndSaveKey()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = apiKey.isNotBlank() && apiKeyUiState !is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Loading,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (apiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Validate API Key")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You can change this later in Settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PermissionsPage() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Permission Launchers
    val permissionsToRequest = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    permissionsToRequest.add(Manifest.permission.CAMERA)
    permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)

    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }
    var hasMediaPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }

    val checkPermissions = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            hasMediaPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            hasNotificationPermission = true
            hasMediaPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    // Observer lifecycle to check permissions when returning from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { checkPermissions() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enable Powers",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "To function as your second brain, MemGallery needs access to your senses.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Media Access allows auto-detection of screenshots.\n\nNote: Android requires access to all photos. Your privacy is protected - the app only processes screenshots.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            desc = "For AI insights and reminders",
            isGranted = hasNotificationPermission
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.CameraAlt,
            title = "Camera",
            desc = "To capture visual memories",
            isGranted = hasCameraPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.Mic,
            title = "Microphone",
            desc = "To record audio notes",
            isGranted = hasAudioPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.PhotoLibrary,
            title = "Media",
            desc = "To auto-index screenshots",
            isGranted = hasMediaPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.Layers,
            title = "Quick Capture",
            desc = "Instant capture from anywhere",
            isGranted = hasOverlayPermission,
            onClick = {
                if (!hasOverlayPermission) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { launcher.launch(permissionsToRequest.toTypedArray()) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !hasNotificationPermission || !hasCameraPermission || !hasAudioPermission || !hasMediaPermission
        ) {
            Text(if (!hasNotificationPermission || !hasCameraPermission || !hasAudioPermission || !hasMediaPermission) "Grant Standard Permissions" else "Standard Permissions Granted")
        }
        
        if (!hasOverlayPermission) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Enable Quick Capture Overlay")
            }
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    desc: String,
    isGranted: Boolean,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isGranted, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.primary
            )
        } else if (onClick != {}) {
             Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Enable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun HowItWorksPage(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "How it Works",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(48.dp))

        StepItem(number = "1", title = "Capture", desc = "Take photos, record audio, save web bookmarks, or share content from other apps.")
        Spacer(modifier = Modifier.height(24.dp))
        StepItem(number = "2", title = "Process", desc = "AI analyzes your memories to make them searchable.")
        Spacer(modifier = Modifier.height(24.dp))
        StepItem(number = "3", title = "Recall", desc = "Ask questions or search to instantly retrieve information.")

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StepItem(number: String, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = number,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
