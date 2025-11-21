package com.example.memgallery.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    openAddSheet: Boolean = false,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val taskScreenEnabled by settingsViewModel.taskScreenEnabled.collectAsState()
    val pageCount = if (taskScreenEnabled) 2 else 1
    val pagerState = rememberPagerState(pageCount = { pageCount })

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> GalleryScreen(navController = navController, openAddSheet = openAddSheet)
            1 -> if (taskScreenEnabled) TaskScreen()
        }
    }
}
