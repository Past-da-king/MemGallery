package com.example.memg.presentation

import androidx.compose.foundation.layout.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.*
import com.example.memg.model.MemoryObject
import com.example.memg.viewmodel.MemGalleryViewModel
import com.example.memg.presentation.CaptureScreen
import com.example.memg.presentation.DetailScreen
import com.example.memg.presentation.GalleryScreen
import com.example.memg.presentation.SettingsScreen


@Composable
fun MemGalleryApp(startScreenCapture: () -> Unit) {
    val navController = rememberNavController()
    val viewModel: MemGalleryViewModel = hiltViewModel()

    // Add memory when a capture is completed
    fun handleCaptureComplete(memory: MemoryObject) {
        // Process the memory with AI and add it to the repository
        viewModel.addMemoryWithAIProcessing(memory)
        navController.popBackStack()
    }
    
    NavHost(
        navController = navController,
        startDestination = "gallery"
    ) {
        composable("gallery") {
            GalleryScreen(
                navigateToDetail = { memoryId ->
                    navController.navigate("detail/$memoryId")
                },
                navigateToAdd = {
                    navController.navigate("capture")
                },
                navigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }
        composable("capture") {
            CaptureScreen(
                onCaptureComplete = { memory: MemoryObject ->
                    handleCaptureComplete(memory)
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                startScreenCapture = startScreenCapture
            )
        }
        composable("detail/{memoryId}") { backStackEntry ->
            val memoryId = backStackEntry.arguments?.getString("memoryId")
            DetailScreen(
                memoryId = memoryId ?: "",
                onNavigateBack = {
                    navController.popBackStack()
                },
                onEditComplete = { updatedMemory: MemoryObject ->
                    viewModel.updateMemory(updatedMemory)
                },
                onDeleteComplete = {
                    navController.navigate("gallery") {
                        popUpTo("gallery") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}