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

    object TextInput : Screen("text_input?imageUri={imageUri}&audioUri={audioUri}&userText={userText}&bookmarkUrl={bookmarkUrl}&memoryId={memoryId}") {
        fun createRoute(imageUri: String? = null, audioUri: String? = null, userText: String? = null, bookmarkUrl: String? = null, memoryId: Int? = null): String {
            return "text_input?imageUri=${imageUri ?: ""}&audioUri=${audioUri ?: ""}&userText=${userText ?: ""}&bookmarkUrl=${bookmarkUrl ?: ""}&memoryId=${memoryId ?: ""}"
        }
    }

    object AudioCapture : Screen("audio_capture?imageUri={imageUri}&audioUri={audioUri}&userText={userText}&bookmarkUrl={bookmarkUrl}&memoryId={memoryId}") {
        fun createRoute(imageUri: String? = null, audioUri: String? = null, userText: String? = null, bookmarkUrl: String? = null, memoryId: Int? = null): String {
            return "audio_capture?imageUri=${imageUri ?: ""}&audioUri=${audioUri ?: ""}&userText=${userText ?: ""}&bookmarkUrl=${bookmarkUrl ?: ""}&memoryId=${memoryId ?: ""}"
        }
    }

    object PostCapture : Screen("post_capture?imageUri={imageUri}&audioUri={audioUri}&userText={userText}&bookmarkUrl={bookmarkUrl}&memoryId={memoryId}") {
        fun createRoute(imageUri: String? = null, audioUri: String? = null, userText: String? = null, bookmarkUrl: String? = null, memoryId: Int? = null): String {
            return "post_capture?imageUri=${imageUri ?: ""}&audioUri=${audioUri ?: ""}&userText=${userText ?: ""}&bookmarkUrl=${bookmarkUrl ?: ""}&memoryId=${memoryId ?: ""}"
        }
    }

    object PostCaptureEdit : Screen("post_capture_edit/{memoryId}") {
        fun createRoute(memoryId: Int) = "post_capture_edit/$memoryId"
    }

    object Detail : Screen("detail/{memoryId}") {
        fun createRoute(memoryId: Int) = "detail/$memoryId"
    }

    object CollectionDetail : Screen("collection/{collectionId}") {
        fun createRoute(collectionId: Int) = "collection/$collectionId"
    }

    object BookmarkInput : Screen("bookmark_input") {
        fun createRoute() = "bookmark_input"
    }

    object AdvancedSettings : Screen("advanced_settings")

    // Add Task with Sheet trigger
    object TaskManager : Screen("task_manager?openAddSheet={openAddSheet}") {
        fun createRoute(openAddSheet: Boolean = false) = "task_manager?openAddSheet=$openAddSheet"
    }
    
    // Post Capture with URL Sheet trigger
    object PostCaptureUrl : Screen("post_capture_url?openUrlSheet={openUrlSheet}") {
        fun createRoute(openUrlSheet: Boolean = false) = "post_capture_url?openUrlSheet=$openUrlSheet"
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
                // popUpTo(Screen.Gallery.route) { inclusive = false }
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
            route = Screen.AdvancedSettings.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            AdvancedSettingsScreen(navController = navController)
        }
        composable(
            route = Screen.TextInput.route,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; nullable = true },
                navArgument("audioUri") { type = NavType.StringType; nullable = true },
                navArgument("userText") { type = NavType.StringType; nullable = true },
                navArgument("bookmarkUrl") { type = NavType.StringType; nullable = true },
                navArgument("memoryId") { type = NavType.StringType; nullable = true } // StringType because IntType nullable is tricky in nav, we'll parse
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.takeIf { it.isNotEmpty() }
            val audioUri = backStackEntry.arguments?.getString("audioUri")?.takeIf { it.isNotEmpty() }
            val userText = backStackEntry.arguments?.getString("userText")?.takeIf { it.isNotEmpty() }
            val bookmarkUrl = backStackEntry.arguments?.getString("bookmarkUrl")?.takeIf { it.isNotEmpty() }
            val memoryIdStr = backStackEntry.arguments?.getString("memoryId")
            val memoryId = if (memoryIdStr.isNullOrEmpty()) null else memoryIdStr.toIntOrNull()

            TextInputScreen(
                navController = navController,
                existingImageUri = imageUri,
                existingAudioUri = audioUri,
                existingUserText = userText,
                existingBookmarkUrl = bookmarkUrl,
                memoryId = memoryId
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
                navArgument("bookmarkUrl") { type = NavType.StringType; nullable = true },
                navArgument("memoryId") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.takeIf { it.isNotEmpty() }
            val audioUri = backStackEntry.arguments?.getString("audioUri")?.takeIf { it.isNotEmpty() }
            val userText = backStackEntry.arguments?.getString("userText")?.takeIf { it.isNotEmpty() }
            val bookmarkUrl = backStackEntry.arguments?.getString("bookmarkUrl")?.takeIf { it.isNotEmpty() }
            val memoryIdStr = backStackEntry.arguments?.getString("memoryId")
            val memoryId = if (memoryIdStr.isNullOrEmpty()) null else memoryIdStr.toIntOrNull()

            AudioCaptureScreen(
                navController = navController,
                existingImageUri = imageUri,
                existingAudioUri = audioUri,
                existingUserText = userText,
                existingBookmarkUrl = bookmarkUrl,
                memoryId = memoryId
            )
        }
        composable(
            route = Screen.PostCapture.route,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; nullable = true },
                navArgument("audioUri") { type = NavType.StringType; nullable = true },
                navArgument("userText") { type = NavType.StringType; nullable = true },
                navArgument("bookmarkUrl") { type = NavType.StringType; nullable = true },
                navArgument("memoryId") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.takeIf { it.isNotEmpty() }
            val audioUri = backStackEntry.arguments?.getString("audioUri")?.takeIf { it.isNotEmpty() }
            val userText = backStackEntry.arguments?.getString("userText")?.takeIf { it.isNotEmpty() }
            val bookmarkUrl = backStackEntry.arguments?.getString("bookmarkUrl")?.takeIf { it.isNotEmpty() }
            val memoryIdStr = backStackEntry.arguments?.getString("memoryId")
            val memoryId = if (memoryIdStr.isNullOrEmpty()) null else memoryIdStr.toIntOrNull()

            PostCaptureScreen(
                navController = navController,
                initialImageUri = imageUri,
                initialAudioUri = audioUri,
                initialUserText = userText,
                initialBookmarkUrl = bookmarkUrl,
                memoryId = memoryId
            )
        }
        // New route for opening URL sheet directly
        composable(
            route = Screen.PostCaptureUrl.route,
            arguments = listOf(navArgument("openUrlSheet") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val openUrlSheet = backStackEntry.arguments?.getBoolean("openUrlSheet") ?: false
            PostCaptureScreen(
                navController = navController,
                openUrlSheet = openUrlSheet
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
        composable(
            route = Screen.CollectionDetail.route,
            arguments = listOf(navArgument("collectionId") { type = NavType.IntType })
        ) { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getInt("collectionId") ?: 0
            CollectionDetailScreen(navController = navController, collectionId = collectionId)
        }
        composable(Screen.BookmarkInput.route) {
            BookmarkInputScreen(navController = navController)
        }
        
        // Task Manager specific route for direct access
        composable(
            route = Screen.TaskManager.route,
            arguments = listOf(navArgument("openAddSheet") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
             val openAddSheet = backStackEntry.arguments?.getBoolean("openAddSheet") ?: false
             HomeScreen(navController = navController, openAddTaskSheet = openAddSheet, forceTaskPage = true)
        }
    }
}
