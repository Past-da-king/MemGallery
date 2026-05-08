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
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val skipToGallery = {
        viewModel.completeOnboarding()
        navController.navigate(Screen.Gallery.route) {
            popUpTo(Screen.Onboarding.route) { inclusive = true }
        }
    }

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
                    onGetStarted = skipToGallery
                )
            }
        }

        // Skip — visible on every page except the final "Get Started" page
        AnimatedVisibility(
            visible = pagerState.currentPage < 4,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 16.dp, top = 8.dp)
        ) {
            TextButton(
                onClick = { skipToGallery() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Page progress + Next FAB
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expanding-pill indicators
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(5) { iteration ->
                    val isActive = pagerState.currentPage == iteration
                    val width by animateDpAsState(
                        targetValue = if (isActive) 28.dp else 8.dp,
                        animationSpec = tween(durationMillis = 350, easing = EaseOutExpo),
                        label = "pillWidth"
                    )
                    val color = if (isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (pagerState.currentPage < 4) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                }
            }
        }
    }
}

@Composable
fun WelcomePage() {
    // Single-shot reveal trigger — animations start as soon as the page composes
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val heroScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.82f,
        animationSpec = tween(durationMillis = 700, easing = EaseOutExpo),
        label = "heroScale"
    )
    val heroAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = EaseOutExpo),
        label = "heroAlpha"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 200, easing = EaseOutExpo),
        label = "titleAlpha"
    )
    val bodyAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 400, easing = EaseOutExpo),
        label = "bodyAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Hero — icon nested inside two soft circular halos for atmospheric depth
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(heroScale)
                .alpha(heroAlpha),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            )
            Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.memgallery.R.mipmap.ic_launcher_foreground),
                contentDescription = "MemGallery Icon",
                modifier = Modifier.size(140.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "YOUR SECOND BRAIN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            modifier = Modifier.alpha(titleAlpha)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "MemGallery",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.alpha(titleAlpha)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Capture. Process. Recall.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(bodyAlpha)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Privacy assurance — small, set apart with a tertiary tint to give it weight
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.alpha(bodyAlpha)
        ) {
            Text(
                text = "Stays on your device. No cloud.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ChooseAiPage(viewModel: SettingsViewModel = hiltViewModel()) {
    val aiProvider by viewModel.aiProvider.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
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
            text = "Pick the AI engine that powers MemGallery. You can change this anytime in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Google Gemini
        AiProviderCard(
            title = "Google Gemini",
            description = "Google's trusted multimodal AI. Good balance of speed and reasoning.",
            isSelected = aiProvider == "GEMINI",
            onClick = { viewModel.setAiProvider("GEMINI") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Groq
        AiProviderCard(
            title = "Groq LPU (Llama)",
            description = "Blazing fast inference. Best for real-time interactions.",
            isSelected = aiProvider == "GROQ",
            onClick = { viewModel.setAiProvider("GROQ") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // OpenAI-compatible (OpenRouter, Together, self-hosted, etc.)
        AiProviderCard(
            title = "OpenAI-Compatible",
            description = "OpenRouter, OpenAI, Together AI, or any compatible endpoint.",
            isSelected = aiProvider == "OPENAI_COMPATIBLE",
            onClick = { viewModel.setAiProvider("OPENAI_COMPATIBLE") }
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
            Icon(
                imageVector = when {
                    title.contains("Groq") -> Icons.Default.Bolt
                    title.contains("Compatible") -> Icons.Default.Hub
                    else -> Icons.Default.AutoAwesome
                },
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Key Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Connect Your AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add your key now, or skip and set this up anytime from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        when (aiProvider) {
            "OPENAI_COMPATIBLE" -> OpenAiCompatibleSetupBlock(viewModel)
            else -> StandardKeySetupBlock(viewModel, isGroq = aiProvider == "GROQ")
        }
    }
}

@Composable
private fun StandardKeySetupBlock(
    viewModel: SettingsViewModel,
    isGroq: Boolean
) {
    val apiKey by if (isGroq) viewModel.groqApiKey.collectAsState() else viewModel.apiKey.collectAsState()
    val apiKeyUiState by if (isGroq) viewModel.groqApiKeyUiState.collectAsState() else viewModel.apiKeyUiState.collectAsState()

    val providerName = if (isGroq) "Groq Cloud" else "Google AI Studio"
    val providerUrl = if (isGroq) "console.groq.com" else "aistudio.google.com"
    val inputLabel = if (isGroq) "Groq API Key" else "Google AI Studio API Key"
    val placeholder = if (isGroq) "gsk_..." else "AIzaSy..."

    // Get-the-key instructions
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Get a free key from $providerName",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Visit $providerUrl, sign in, create an API key, and paste it below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    OutlinedTextField(
        value = apiKey,
        onValueChange = {
            if (isGroq) viewModel.onGroqApiKeyChange(it)
            else viewModel.onApiKeyChange(it)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(inputLabel) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        isError = apiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Error
    )

    KeyValidationFeedback(apiKeyUiState)

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            if (isGroq) viewModel.validateAndSaveGroqKey()
            else viewModel.validateAndSaveKey()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = apiKey.isNotBlank() && apiKeyUiState !is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Loading,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (apiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text("Validate & Save")
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = "Don't have a key? Tap Skip in the corner.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OpenAiCompatibleSetupBlock(viewModel: SettingsViewModel) {
    val baseUrl by viewModel.customBaseUrl.collectAsState()
    val modelName by viewModel.customModelName.collectAsState()
    val apiKey by viewModel.customApiKey.collectAsState()
    val uiState by viewModel.customUiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "OpenAI-compatible endpoint",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Works with OpenRouter, OpenAI, Together AI, or any server that speaks the OpenAI Chat Completions format.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    OutlinedTextField(
        value = baseUrl,
        onValueChange = viewModel::setCustomBaseUrl,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Base URL") },
        placeholder = { Text("https://openrouter.ai/api/v1/") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = modelName,
        onValueChange = viewModel::setCustomModelName,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Model") },
        placeholder = { Text("e.g. google/gemini-2.5-flash") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = apiKey,
        onValueChange = viewModel::onCustomApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API Key") },
        placeholder = { Text("sk-...") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        isError = uiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Error
    )

    KeyValidationFeedback(uiState)

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = { viewModel.validateAndSaveCustomSettings() },
        modifier = Modifier.fillMaxWidth(),
        enabled = baseUrl.isNotBlank() && modelName.isNotBlank()
            && uiState !is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Loading,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (uiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text("Validate & Save")
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = "Don't have it ready? Tap Skip in the corner.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun KeyValidationFeedback(state: com.example.memgallery.ui.viewmodels.ApiKeyUiState) {
    when (state) {
        is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Error -> {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Success -> {
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
                    text = "Connected",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        else -> Unit
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
