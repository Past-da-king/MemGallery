package com.example.memgallery.ui.screens

import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.design.appStatusBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.memgallery.R
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
                .appStatusBarsPadding()
                .padding(end = 16.dp, top = 8.dp)
        ) {
            TextButton(
                onClick = { skipToGallery() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.common_skip),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        OnboardingBottomBar(
            currentPage = pagerState.currentPage,
            pageCount = 5,
            onNext = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
/** The first page: a collage of memory shapes, then "Capture. Process. Recall." and the privacy note. */
@Composable
fun WelcomePage() {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val heroScale by animateFloatAsState(if (revealed) 1f else 0.86f, tween(700, easing = EaseOutExpo), label = "heroScale")
    val heroAlpha by animateFloatAsState(if (revealed) 1f else 0f, tween(500, easing = EaseOutExpo), label = "heroAlpha")
    val textAlpha by animateFloatAsState(if (revealed) 1f else 0f, tween(600, delayMillis = 250, easing = EaseOutExpo), label = "textAlpha")
    OnboardingWelcomeContent(heroScale = heroScale, heroAlpha = heroAlpha, textAlpha = textAlpha)
}

@Composable
fun OnboardingWelcomeContent(heroScale: Float = 1f, heroAlpha: Float = 1f, textAlpha: Float = 1f) {
    val scheme = MaterialTheme.colorScheme
    val mem = com.example.memgallery.ui.theme.LocalMemColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(88.dp))
        // Collage, laid out on a 360 x 372 canvas and centred
        Box(
            Modifier
                .fillMaxWidth()
                .height(372.dp)
                .graphicsLayer { scaleX = heroScale; scaleY = heroScale; alpha = heroAlpha },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.size(width = 360.dp, height = 372.dp)) {
                // plant
                ShapedArt("plant", MemShape.puffy, Modifier.offset(0.dp, 28.dp).size(148.dp))
                // sunset, on a soft lavender blob
                Box(Modifier.offset(140.dp, 86.dp).size(150.dp, 74.dp).clip(MemShape.softBurst).background(scheme.primaryContainer))
                ShapedArt("sunset", MemShape.softBurst, Modifier.offset(150.dp, 0.dp).size(176.dp, 164.dp))
                // small sky star
                Box(Modifier.offset(300.dp, 0.dp).size(46.dp).clip(MemShape.pentagon).background(mem.skyContainer))
                // done check on a green puffy circle
                Box(Modifier.offset(102.dp, 124.dp).size(84.dp).clip(MemShape.puffy).background(mem.successContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.TaskAlt, null, tint = mem.onSuccessContainer, modifier = Modifier.size(34.dp))
                }
                // link on a primary gem
                Box(Modifier.offset(294.dp, 124.dp).size(66.dp).clip(MemShape.gem).background(scheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Link, null, tint = scheme.onPrimary, modifier = Modifier.size(32.dp))
                }
                // latte in a cookie
                ShapedArt("latte", MemShape.cookie, Modifier.offset(8.dp, 172.dp).size(168.dp))
                // mic on a pink burst
                Box(Modifier.offset(190.dp, 180.dp).size(138.dp).clip(MemShape.softBurst).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Mic, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(40.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.graphicsLayer { alpha = textAlpha }) {
            Text(
                stringResource(R.string.onboarding_brand_label),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
                color = scheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.onboarding_tagline_capture)); append("\n")
                    append(stringResource(R.string.onboarding_tagline_process)); append("\n")
                    withStyle(SpanStyle(color = scheme.primary)) { append(stringResource(R.string.onboarding_tagline_recall)) }
                },
                style = MaterialTheme.typography.displayMedium.copy(lineHeight = 50.sp),
                color = scheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.onboarding_privacy_chip), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            }
        }
    }
}

@Composable
private fun ShapedArt(name: String, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier) {
    androidx.compose.foundation.Image(
        painter = remember(name) { com.example.memgallery.ui.design.ArtPainter(name) },
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier.clip(shape)
    )
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
            text = stringResource(R.string.onboarding_choose_intelligence_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_choose_intelligence_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Google Gemini
        AiProviderCard(
            title = stringResource(R.string.onboarding_provider_gemini_title),
            description = stringResource(R.string.onboarding_provider_gemini_desc),
            icon = Icons.Default.AutoAwesome,
            isSelected = aiProvider == "GEMINI",
            onClick = { viewModel.setAiProvider("GEMINI") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Groq
        AiProviderCard(
            title = stringResource(R.string.onboarding_provider_groq_title),
            description = stringResource(R.string.onboarding_provider_groq_desc),
            icon = Icons.Default.Bolt,
            isSelected = aiProvider == "GROQ",
            onClick = { viewModel.setAiProvider("GROQ") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // OpenAI-compatible (OpenRouter, Together, self-hosted, etc.)
        AiProviderCard(
            title = stringResource(R.string.onboarding_provider_openai_title),
            description = stringResource(R.string.onboarding_provider_openai_desc),
            icon = Icons.Default.Hub,
            isSelected = aiProvider == "OPENAI_COMPATIBLE",
            onClick = { viewModel.setAiProvider("OPENAI_COMPATIBLE") }
        )
    }
}

@Composable
fun AiProviderCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                imageVector = icon,
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
                    contentDescription = stringResource(R.string.common_selected),
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
            text = stringResource(R.string.onboarding_connect_ai_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_connect_ai_body),
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

    val providerName = if (isGroq) stringResource(R.string.onboarding_provider_groq_cloud) else stringResource(R.string.onboarding_provider_google_ai)
    val providerUrl = if (isGroq) "console.groq.com" else "aistudio.google.com"
    val inputLabel = if (isGroq) stringResource(R.string.onboarding_field_groq_key) else stringResource(R.string.onboarding_field_google_key)
    val placeholder = if (isGroq) stringResource(R.string.settings_groq_placeholder) else stringResource(R.string.settings_gemini_placeholder)

    // Get-the-key instructions
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.onboarding_card_get_key_title, providerName),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_card_get_key_body, providerUrl),
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
            Text(stringResource(R.string.onboarding_validate_save))
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = stringResource(R.string.onboarding_no_key_caption),
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
                text = stringResource(R.string.onboarding_openai_card_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_openai_card_body),
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
        label = { Text(stringResource(R.string.settings_custom_base_url_label)) },
        placeholder = { Text(stringResource(R.string.onboarding_openai_base_url_placeholder)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = modelName,
        onValueChange = viewModel::setCustomModelName,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.onboarding_openai_model_label)) },
        placeholder = { Text(stringResource(R.string.onboarding_openai_model_placeholder)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = apiKey,
        onValueChange = viewModel::onCustomApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.onboarding_openai_key_label)) },
        placeholder = { Text(stringResource(R.string.onboarding_openai_key_placeholder)) },
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
            Text(stringResource(R.string.onboarding_validate_save))
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = stringResource(R.string.onboarding_no_key_caption_alt),
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
                    text = stringResource(R.string.onboarding_status_connected),
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
            text = stringResource(R.string.onboarding_powers_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_powers_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_media_access_explainer),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.onboarding_perm_notifications_title),
            desc = stringResource(R.string.onboarding_perm_notifications_desc),
            isGranted = hasNotificationPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.CameraAlt,
            title = stringResource(R.string.onboarding_perm_camera_title),
            desc = stringResource(R.string.onboarding_perm_camera_desc),
            isGranted = hasCameraPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.Mic,
            title = stringResource(R.string.onboarding_perm_mic_title),
            desc = stringResource(R.string.onboarding_perm_mic_desc),
            isGranted = hasAudioPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.PhotoLibrary,
            title = stringResource(R.string.onboarding_perm_media_title),
            desc = stringResource(R.string.onboarding_perm_media_desc),
            isGranted = hasMediaPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.Layers,
            title = stringResource(R.string.onboarding_perm_overlay_title),
            desc = stringResource(R.string.onboarding_perm_overlay_desc),
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
            Text(if (!hasNotificationPermission || !hasCameraPermission || !hasAudioPermission || !hasMediaPermission) stringResource(R.string.onboarding_grant_standard) else stringResource(R.string.onboarding_standard_granted))
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
                Text(stringResource(R.string.onboarding_enable_overlay))
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
                contentDescription = stringResource(R.string.onboarding_cd_granted),
                tint = MaterialTheme.colorScheme.primary
            )
        } else if (onClick != {}) {
             Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = stringResource(R.string.onboarding_cd_enable),
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
            text = stringResource(R.string.onboarding_how_it_works_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(48.dp))

        StepItem(number = "1", title = stringResource(R.string.onboarding_step_capture_title), desc = stringResource(R.string.onboarding_step_capture_desc))
        Spacer(modifier = Modifier.height(24.dp))
        StepItem(number = "2", title = stringResource(R.string.onboarding_step_process_title), desc = stringResource(R.string.onboarding_step_process_desc))
        Spacer(modifier = Modifier.height(24.dp))
        StepItem(number = "3", title = stringResource(R.string.onboarding_step_recall_title), desc = stringResource(R.string.onboarding_step_recall_desc))

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
                text = stringResource(R.string.onboarding_get_started),
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

/** Page indicator (the current page stretches into a pill) and the main button. */
@Composable
fun OnboardingBottomBar(currentPage: Int, pageCount: Int, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .appNavigationBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pageCount) { iteration ->
                val isActive = currentPage == iteration
                val width by animateDpAsState(
                    targetValue = if (isActive) 28.dp else 8.dp,
                    animationSpec = tween(durationMillis = 350, easing = EaseOutExpo),
                    label = "pillWidth"
                )
                Box(
                    modifier = Modifier
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        if (currentPage < pageCount - 1) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text(
                    stringResource(if (currentPage == 0) R.string.onboarding_get_started else R.string.common_next),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.ArrowForward, null, Modifier.size(24.dp))
            }
        }
    }
}
