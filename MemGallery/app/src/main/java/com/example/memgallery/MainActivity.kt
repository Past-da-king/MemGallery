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

        // Check if share handling is enabled for share intents
        lifecycleScope.launch {
            if (intent?.action == Intent.ACTION_SEND) {
                val enabled = settingsRepository.showInShareSheetFlow.first()
                if (!enabled) {
                    finish()
                    return@launch
                }
            }

            // Extract shared data and shortcuts
            val sharedData = extractSharedData(intent)
            val shortcutAction = intent.getStringExtra("shortcut_action")
            
            // Check onboarding status
            val isOnboardingCompleted = settingsRepository.isOnboardingCompletedFlow.first()

            setContent {
                val dynamicTheming by settingsRepository.dynamicThemingEnabledFlow.collectAsState(initial = true)
                val appThemeMode by settingsRepository.appThemeModeFlow.collectAsState(initial = "SYSTEM")
                val amoledMode by settingsRepository.amoledModeEnabledFlow.collectAsState(initial = false)
                val selectedColor by settingsRepository.selectedColorFlow.collectAsState(initial = -1)

                MemGalleryTheme(
                    dynamicColor = dynamicTheming,
                    appThemeMode = appThemeMode,
                    amoledMode = amoledMode,
                    customColor = selectedColor
                ) {
                    AppNavigation(
                        isOnboardingCompleted = isOnboardingCompleted,
                        sharedImageUri = sharedData?.imageUri,
                        sharedText = sharedData?.text,
                        shortcutAction = shortcutAction,
                        navigateToRoute = intent.getStringExtra("navigate_to")
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

        return when (intent.type) {
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
    }

    data class SharedData(
        val imageUri: String? = null,
        val text: String? = null
    )
}