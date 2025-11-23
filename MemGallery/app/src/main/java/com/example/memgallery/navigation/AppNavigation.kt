package com.example.memgallery.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.memgallery.ui.screens.*

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Gallery : Screen("gallery")
    object Settings : Screen("settings")

    object CameraCapture : Screen("camera_capture")

    object TextInput : Screen("text_input?imageUri={imageUri}&audioUri={audioUri}&userText={userText}&bookmarkUrl={bookmarkUrl}") {
        fun createRoute(imageUri: String? = null, audioUri: String? = null, userText: String? = null, bookmarkUrl: String? = null): String {
            return "text_input?imageUri=${imageUri ?: ""}&audioUri=${audioUri ?: ""}&userText=${userText ?: ""}&bookmarkUrl=${bookmarkUrl ?: ""}"
        }
    }

    object AudioCapture : Screen("audio_capture?imageUri={imageUri}&audioUri={audioUri}&userText={userText}&bookmarkUrl={bookmarkUrl}") {
        fun createRoute(imageUri: String? = null, audioUri: String? = null, userText: String? = null, bookmarkUrl: String? = null): String {
            return "audio_capture?imageUri=${imageUri ?: ""}&audioUri=${audioUri ?: ""}&userText=${userText ?: ""}&bookmarkUrl=${bookmarkUrl ?: ""}"
        }
    }

    object PostCapture : Screen("post_capture?imageUri={imageUri}&audioUri={audioUri}&userText={userText}&bookmarkUrl={bookmarkUrl}") {
        fun createRoute(imageUri: String? = null, audioUri: String? = null, userText: String? = null, bookmarkUrl: String? = null): String {
            return "post_capture?imageUri=${imageUri ?: ""}&audioUri=${audioUri ?: ""}&userText=${userText ?: ""}&bookmarkUrl=${bookmarkUrl ?: ""}"
        }
    }

    object PostCaptureEdit : Screen("post_capture_edit/{memoryId}") {
        fun createRoute(memoryId: Int) = "post_capture_edit/$memoryId"
    }

    object Detail : Screen("detail/{memoryId}") {
        fun createRoute(memoryId: Int) = "detail/$memoryId"
    }

    object BookmarkInput : Screen("bookmark_input") {
        fun createRoute() = "bookmark_input"
    }
}

@Composable
fun AppNavigation(
    isOnboardingCompleted: Boolean = true,
    sharedImageUri: String? = null,
    sharedText: String? = null,
    shortcutAction: String? = null,
    navigateToRoute: String? = null
) {
    val navController = rememberNavController()
    val startDestination = if (isOnboardingCompleted) Screen.Gallery.route else Screen.Onboarding.route

    // Handle direct navigation
    LaunchedEffect(navigateToRoute) {
        if (navigateToRoute != null) {
            navController.navigate(navigateToRoute) {
                popUpTo(Screen.Gallery.route) { inclusive = false }
            }
        }
    }

    // Handle share intents - navigate to PostCaptureScreen
    LaunchedEffect(sharedImageUri, sharedText) {
        if (sharedImageUri != null || sharedText != null) {
            // URL encode the text to handle special characters
            val encodedText = sharedText?.let { 
                java.net.URLEncoder.encode(it, "UTF-8") 
            }
            
            // Extract URL from text if present
            val urlRegex = "(https?://\\S+)".toRegex()
            val bookmarkUrl = sharedText?.let { urlRegex.find(it)?.value }
            
            navController.navigate(
                Screen.PostCapture.createRoute(
                    imageUri = sharedImageUri,
                    audioUri = null,
                    userText = encodedText,
                    bookmarkUrl = bookmarkUrl
                )
            ) {
                popUpTo(Screen.Gallery.route) { inclusive = false }
            }
        }
    }

    // Handle shortcuts
    LaunchedEffect(shortcutAction) {
        when (shortcutAction) {
            "record_audio" -> {
                navController.navigate(Screen.AudioCapture.createRoute(null, null, null, null))
            }
            "text_note" -> {
                navController.navigate(Screen.TextInput.createRoute(null, null, null, null))
            }
            // "add_memory" will need to trigger bottom sheet in GalleryScreen
            // This is handled differently as it's not a separate screen
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(navController = navController)
        }
        composable(Screen.Gallery.route) {
            HomeScreen(navController = navController, openAddSheet = shortcutAction == "add_memory")
        }
        composable(
            route = Screen.Settings.route,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            }
        ) {
            SettingsScreen(navController = navController)
        }
        composable(
            route = Screen.TextInput.route,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; nullable = true },
                navArgument("audioUri") { type = NavType.StringType; nullable = true },
                navArgument("userText") { type = NavType.StringType; nullable = true },
                navArgument("bookmarkUrl") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.takeIf { it.isNotEmpty() }
            val audioUri = backStackEntry.arguments?.getString("audioUri")?.takeIf { it.isNotEmpty() }
            val userText = backStackEntry.arguments?.getString("userText")?.takeIf { it.isNotEmpty() }
            val bookmarkUrl = backStackEntry.arguments?.getString("bookmarkUrl")?.takeIf { it.isNotEmpty() }
            TextInputScreen(
                navController = navController,
                existingImageUri = imageUri,
                existingAudioUri = audioUri,
                existingUserText = userText,
                existingBookmarkUrl = bookmarkUrl
            )
        }

        composable(Screen.CameraCapture.route) {
            CameraCaptureScreen(navController = navController)
        }
        composable(
            route = Screen.AudioCapture.route,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; nullable = true },
                navArgument("audioUri") { type = NavType.StringType; nullable = true },
                navArgument("userText") { type = NavType.StringType; nullable = true },
                navArgument("bookmarkUrl") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.takeIf { it.isNotEmpty() }
            val audioUri = backStackEntry.arguments?.getString("audioUri")?.takeIf { it.isNotEmpty() }
            val userText = backStackEntry.arguments?.getString("userText")?.takeIf { it.isNotEmpty() }
            val bookmarkUrl = backStackEntry.arguments?.getString("bookmarkUrl")?.takeIf { it.isNotEmpty() }
            AudioCaptureScreen(
                navController = navController,
                existingImageUri = imageUri,
                existingAudioUri = audioUri,
                existingUserText = userText,
                existingBookmarkUrl = bookmarkUrl
            )
        }
        composable(
            route = Screen.PostCapture.route,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; nullable = true },
                navArgument("audioUri") { type = NavType.StringType; nullable = true },
                navArgument("userText") { type = NavType.StringType; nullable = true },
                navArgument("bookmarkUrl") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.takeIf { it.isNotEmpty() }
            val audioUri = backStackEntry.arguments?.getString("audioUri")?.takeIf { it.isNotEmpty() }
            val userText = backStackEntry.arguments?.getString("userText")?.takeIf { it.isNotEmpty() }
            val bookmarkUrl = backStackEntry.arguments?.getString("bookmarkUrl")?.takeIf { it.isNotEmpty() }
            PostCaptureScreen(
                navController = navController,
                initialImageUri = imageUri,
                initialAudioUri = audioUri,
                initialUserText = userText,
                initialBookmarkUrl = bookmarkUrl
            )
        }
        composable(
            route = Screen.PostCaptureEdit.route,
            arguments = listOf(navArgument("memoryId") { type = NavType.IntType })
        ) { backStackEntry ->
            val memoryId = backStackEntry.arguments?.getInt("memoryId") ?: 0
            PostCaptureScreen(
                navController = navController,
                memoryId = memoryId
            )
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("memoryId") { type = NavType.IntType })
        ) { backStackEntry ->
            val memoryId = backStackEntry.arguments?.getInt("memoryId") ?: 0
            MemoryDetailScreen(navController = navController, memoryId = memoryId)
        }
        composable(Screen.BookmarkInput.route) {
            BookmarkInputScreen(navController = navController)
        }
    }
}
