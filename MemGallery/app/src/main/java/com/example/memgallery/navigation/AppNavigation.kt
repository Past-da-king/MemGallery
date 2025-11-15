package com.example.memgallery.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.memgallery.ui.screens.ApiKeyManagementScreen
import com.example.memgallery.ui.screens.AudioCaptureScreen
import com.example.memgallery.ui.screens.GalleryScreen
import com.example.memgallery.ui.screens.ImageCreationScreen
import com.example.memgallery.ui.screens.MemoryDetailScreen
import com.example.memgallery.ui.screens.TextInputScreen

sealed class Screen(val route: String) {
    object Gallery : Screen("gallery")
    object ApiKey : Screen("api_key")
    object TextInput : Screen("text_input")
    object ImageCreation : Screen("image_creation")
    object AudioCapture : Screen("audio_capture")
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
        composable(Screen.TextInput.route) {
            TextInputScreen(navController = navController)
        }
        composable(Screen.ImageCreation.route) {
            ImageCreationScreen(navController = navController)
        }
        composable(Screen.AudioCapture.route) {
            AudioCaptureScreen(navController = navController)
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
