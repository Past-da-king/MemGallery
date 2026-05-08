package com.example.memgallery.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    openAddSheet: Boolean = false,
    openAddTaskSheet: Boolean = false,
    forceTaskPage: Boolean = false,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val taskScreenEnabled by settingsViewModel.taskScreenEnabled.collectAsState()
    // Page 0: Chat, Page 1: Gallery, Page 2: Task (if enabled)
    val pageCount = if (taskScreenEnabled) 3 else 2
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { pageCount })

    LaunchedEffect(forceTaskPage) {
        if (forceTaskPage && taskScreenEnabled) {
            pagerState.scrollToPage(2)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> com.example.memgallery.ui.screens.ChatScreen(navController = navController)
            1 -> GalleryScreen(navController = navController, openAddSheet = openAddSheet)
            2 -> if (taskScreenEnabled) {
                TaskScreen(
                    openAddSheet = openAddTaskSheet,
                    onNavigateToMemory = { memoryId ->
                        navController.navigate(Screen.Detail.createRoute(memoryId))
                    }
                )
            }
        }
    }
}
