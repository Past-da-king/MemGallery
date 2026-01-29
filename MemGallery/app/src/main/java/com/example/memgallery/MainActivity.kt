package com.example.memgallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.navigation.AppNavigation
import com.example.memgallery.ui.theme.MemGalleryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Extract shared data and shortcuts immediately
        val sharedData = extractSharedData(intent)
        val shortcutAction = intent.getStringExtra("shortcut_action")
        val navigateToRoute = intent.getStringExtra("navigate_to")

        setContent {
            val isOnboardingCompleted by settingsRepository.isOnboardingCompletedFlow.collectAsState(initial = true)
            val dynamicTheming by settingsRepository.dynamicThemingEnabledFlow.collectAsState(initial = true)
            val appThemeMode by settingsRepository.appThemeModeFlow.collectAsState(initial = "SYSTEM")
            val amoledMode by settingsRepository.amoledModeEnabledFlow.collectAsState(initial = false)
            val selectedColor by settingsRepository.selectedColorFlow.collectAsState(initial = -1)
            
            val showInShareSheet by settingsRepository.showInShareSheetFlow.collectAsState(initial = true)

            // Handle share sheet permission check
            if (intent?.action == Intent.ACTION_SEND && !showInShareSheet) {
                finish()
                return@setContent
            }

            MemGalleryTheme(
                dynamicColor = dynamicTheming,
                appThemeMode = appThemeMode,
                amoledMode = amoledMode,
                customColor = selectedColor
            ) {
                val latestChangeLog by settingsRepository.latestChangeLogFlow.collectAsState(initial = null)
                val hasShownUpdateLog by settingsRepository.hasShownUpdateLogFlow.collectAsState(initial = true)
                val lastSeenVersion by settingsRepository.lastSeenVersionFlow.collectAsState(initial = null)
                var showLogSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                androidx.compose.runtime.LaunchedEffect(lastSeenVersion) {
                    val currentVersion = BuildConfig.VERSION_NAME
                    if (lastSeenVersion != null && lastSeenVersion != currentVersion) {
                        if (!hasShownUpdateLog) {
                            showLogSheet = true
                        }
                    }
                    if (lastSeenVersion != currentVersion) {
                        settingsRepository.setLastSeenVersion(currentVersion)
                    }
                }

                AppNavigation(
                    isOnboardingCompleted = isOnboardingCompleted,
                    sharedImageUri = sharedData?.imageUri,
                    sharedText = sharedData?.text,
                    shortcutAction = shortcutAction,
                    navigateToRoute = navigateToRoute
                )

                if (showLogSheet && latestChangeLog != null) {
                    com.example.memgallery.ui.components.ChangeLogBottomSheet(
                        versionName = BuildConfig.VERSION_NAME,
                        changeLog = latestChangeLog!!,
                        onDismiss = {
                            showLogSheet = false
                            lifecycleScope.launch {
                                settingsRepository.setHasShownUpdateLog(true)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Recreate to handle the new intent
        recreate()
    }

    private fun extractSharedData(intent: Intent?): SharedData? {
        if (intent?.action != Intent.ACTION_SEND) return null

        val result = when (intent.type) {
            "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getStringExtra("shared_text")
                SharedData(text = text)
            }
            else -> {
                if (intent.type?.startsWith("image/") == true) {
                    val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    SharedData(imageUri = imageUri?.toString())
                } else null
            }
        }
        
        // Clear the intent action to prevent reprocessing on activity recreation
        if (result != null) {
            intent.action = null
            intent.removeExtra(Intent.EXTRA_TEXT)
            intent.removeExtra(Intent.EXTRA_STREAM)
        }
        
        return result
    }

    data class SharedData(
        val imageUri: String? = null,
        val text: String? = null
    )
}