package com.example.memgallery.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.memgallery.ui.screens.*

sealed class Screen(val route: String) {
    object Gallery : Screen("gallery")
    object ApiKey : Screen("api_key")

    object CameraCapture : Screen("camera_capture")

    object TextInput : Screen("text_input?imageUri={imageUri}&audioUri={audioUri}&userText={userText}") {
        fun createRoute(imageUri: String? = null, audioUri: String? = null, userText: String? = null): String {
            return "text_input?imageUri=${imageUri ?: ""}&audioUri=${audioUri ?: ""}&userText=${userText ?: ""}"
        }
    }

    object AudioCapture : Screen("audio_capture?imageUri={imageUri}&audioUri={audioUri}&userText={userText}") {
        fun createRoute(imageUri: String? = null, audioUri: String? = null, userText: String? = null): String {
            return "audio_capture?imageUri=${imageUri ?: ""}&audioUri=${audioUri ?: ""}&userText=${userText ?: ""}"
        }
    }

    object PostCapture : Screen("post_capture?imageUri={imageUri}&audioUri={audioUri}&userText={userText}") {
        fun createRoute(imageUri: String? = null, audioUri: String? = null, userText: String? = null): String {
            return "post_capture?imageUri=${imageUri ?: ""}&audioUri=${audioUri ?: ""}&userText=${userText ?: ""}"
        }
    }

    object Detail : Screen("detail/{memoryId}") {
        fun createRoute(memoryId: Int) = "detail/$memoryId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Gallery.route) {
        composable(Screen.Gallery.route) {
            GalleryScreen(navController = navController)
        }
        composable(Screen.ApiKey.route) {
            ApiKeyManagementScreen(navController = navController)
        }
        composable(
            route = Screen.TextInput.route,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; nullable = true },
                navArgument("audioUri") { type = NavType.StringType; nullable = true },
                navArgument("userText") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.takeIf { it.isNotEmpty() }
            val audioUri = backStackEntry.arguments?.getString("audioUri")?.takeIf { it.isNotEmpty() }
            val userText = backStackEntry.arguments?.getString("userText")?.takeIf { it.isNotEmpty() }
            TextInputScreen(
                navController = navController,
                existingImageUri = imageUri,
                existingAudioUri = audioUri,
                existingUserText = userText
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
                navArgument("userText") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.takeIf { it.isNotEmpty() }
            val audioUri = backStackEntry.arguments?.getString("audioUri")?.takeIf { it.isNotEmpty() }
            val userText = backStackEntry.arguments?.getString("userText")?.takeIf { it.isNotEmpty() }
            AudioCaptureScreen(
                navController = navController,
                existingImageUri = imageUri,
                existingAudioUri = audioUri,
                existingUserText = userText
            )
        }
        composable(
            route = Screen.PostCapture.route,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; nullable = true },
                navArgument("audioUri") { type = NavType.StringType; nullable = true },
                navArgument("userText") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.takeIf { it.isNotEmpty() }
            val audioUri = backStackEntry.arguments?.getString("audioUri")?.takeIf { it.isNotEmpty() }
            val userText = backStackEntry.arguments?.getString("userText")?.takeIf { it.isNotEmpty() }
            PostCaptureScreen(
                navController = navController,
                initialImageUri = imageUri,
                initialAudioUri = audioUri,
                initialUserText = userText
            )
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("memoryId") { type = NavType.IntType })
        ) { backStackEntry ->
            val memoryId = backStackEntry.arguments?.getInt("memoryId") ?: 0
            MemoryDetailScreen(navController = navController, memoryId = memoryId)
        }
    }
}
