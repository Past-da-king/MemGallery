package com.example.memgallery.ui.screens

import com.example.memgallery.ui.design.appStatusBarsPadding
import com.example.memgallery.ui.design.appNavigationBarsPadding
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.design.AddFabMenu
import com.example.memgallery.ui.design.AddMenuItem
import com.example.memgallery.ui.design.FloatingNav
import com.example.memgallery.ui.design.MemSheet
import com.example.memgallery.ui.design.NavItem
import com.example.memgallery.ui.theme.memColors
import com.example.memgallery.ui.viewmodels.ChatViewModel
import com.example.memgallery.ui.viewmodels.GalleryViewModel
import com.example.memgallery.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

/** Height reserved at the bottom of every page so content clears the floating nav and its FAB. */
private val NavClearance = 112.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    openAddSheet: Boolean = false,
    openAddTaskSheet: Boolean = false,
    forceTaskPage: Boolean = false,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    galleryViewModel: GalleryViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val taskScreenEnabled by settingsViewModel.taskScreenEnabled.collectAsState()
    // Page 0: Chat, Page 1: Gallery, Page 2: Task (if enabled)
    val pageCount = if (taskScreenEnabled) 3 else 2
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    LaunchedEffect(forceTaskPage) {
        if (forceTaskPage && taskScreenEnabled) pagerState.scrollToPage(2)
    }
    // "Ask about" from the Life Agent screens: show the chat, which picks the question up
    val lifeQuestion by com.example.memgallery.lifeagent.ui.LifeChatBridge.pending.collectAsState()
    LaunchedEffect(lifeQuestion) {
        if (lifeQuestion != null) pagerState.scrollToPage(0)
    }

    val filter by galleryViewModel.selectedFilter.collectAsState()
    val selectionActive by galleryViewModel.selectionModeActive.collectAsState()
    val collections by galleryViewModel.collections.collectAsState()

    var addMenuOpen by remember { mutableStateOf(openAddSheet) }
    var showCreateCollection by remember { mutableStateOf(false) }
    var showAddToCollection by remember { mutableStateOf(false) }
    var addTaskTrigger by remember { mutableIntStateOf(if (openAddTaskSheet) 1 else 0) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            navController.navigate(Screen.PostCapture.createRoute(imageUri = uri.toString())) {
                popUpTo(Screen.Gallery.route) { inclusive = false }
            }
        }
    }

    val page = pagerState.currentPage
    androidx.activity.compose.BackHandler(enabled = addMenuOpen) { addMenuOpen = false }
    val showNav = page != 0 && !selectionActive

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { p ->
            when (p) {
                0 -> ChatScreen(navController = navController)
                1 -> GalleryScreen(
                    navController = navController,
                    bottomPadding = NavClearance + 16.dp,
                    onCreateCollection = { showCreateCollection = true },
                    onAddToCollection = { showAddToCollection = true },
                    onAskAboutSearch = { query ->
                        chatViewModel.updateInputMessage(query)
                        galleryViewModel.onSearchTextChange("")
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    viewModel = galleryViewModel
                )
                2 -> if (taskScreenEnabled) {
                    TaskScreen(
                        openAddSheet = openAddTaskSheet,
                        addTaskTrigger = addTaskTrigger,
                        onCapture = { addMenuOpen = true },
                        bottomPadding = NavClearance + 16.dp,
                        onNavigateToMemory = { memoryId -> navController.navigate(Screen.Detail.createRoute(memoryId)) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showNav,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            val items = buildList {
                add(NavItem(stringResource(R.string.nav_chat), Icons.Outlined.ChatBubbleOutline, Icons.Filled.Chat))
                add(NavItem(stringResource(R.string.nav_memories), Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary))
                if (taskScreenEnabled) add(NavItem(stringResource(R.string.nav_tasks), Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt))
            }
            val onCollections = page == 1 && filter == "Collections"
            FloatingNav(
                items = items,
                selectedIndex = page,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                modifier = Modifier.appNavigationBarsPadding().padding(bottom = 16.dp),
                fabIcon = if (onCollections) Icons.Filled.CreateNewFolder else Icons.Filled.Add,
                fabDescription = stringResource(
                    when {
                        onCollections -> R.string.gallery_cd_create_collection
                        page == 2 -> R.string.tasks_cd_add_task
                        else -> R.string.gallery_cd_add_memory
                    }
                ),
                onFabClick = {
                    when {
                        onCollections -> showCreateCollection = true
                        page == 2 -> addTaskTrigger++
                        else -> addMenuOpen = true
                    }
                }
            )
        }

        val scheme = MaterialTheme.colorScheme
        val mem = MaterialTheme.memColors
        AddFabMenu(
            expanded = addMenuOpen,
            onExpandedChange = { addMenuOpen = it },
            items = listOf(
                AddMenuItem(stringResource(R.string.add_menu_save_bookmark), Icons.Filled.Link, scheme.secondaryContainer, scheme.onSecondaryContainer) {
                    navController.navigate(Screen.BookmarkInput.createRoute())
                },
                AddMenuItem(stringResource(R.string.add_menu_record_audio), Icons.Filled.Mic, mem.skyContainer, mem.onSkyContainer) {
                    navController.navigate(Screen.AudioCapture.createRoute())
                },
                AddMenuItem(stringResource(R.string.add_menu_take_photo), Icons.Filled.PhotoCamera, scheme.tertiaryContainer, scheme.onTertiaryContainer) {
                    navController.navigate(Screen.CameraCapture.route)
                },
                AddMenuItem(stringResource(R.string.add_menu_upload_image), Icons.Filled.AddPhotoAlternate, scheme.primaryContainer, scheme.onPrimaryContainer) {
                    imagePicker.launch("image/*")
                },
                AddMenuItem(stringResource(R.string.add_menu_text_note), Icons.Filled.EditNote, scheme.surfaceContainerHighest, scheme.onSurface) {
                    navController.navigate(Screen.TextInput.createRoute())
                },
            )
        )
    }

    if (showCreateCollection) {
        MemSheet(onDismiss = { showCreateCollection = false }) {
            com.example.memgallery.ui.components.sheets.CreateCollectionSheet(
                onDismiss = { showCreateCollection = false },
                onCreate = { name, desc ->
                    val toAdd = galleryViewModel.selectedMemoryIds.value.toList()
                    galleryViewModel.createCollection(name, desc, toAdd)
                    showCreateCollection = false
                }
            )
        }
    }

    if (showAddToCollection) {
        MemSheet(onDismiss = { showAddToCollection = false }) {
            com.example.memgallery.ui.components.sheets.SelectCollectionSheet(
                collections = collections,
                selectedCount = galleryViewModel.selectedMemoryIds.collectAsState().value.size,
                collectionInfo = galleryViewModel.collectionInfo.collectAsState().value,
                onSelect = { collectionId ->
                    galleryViewModel.addSelectedMemoriesToCollection(collectionId)
                    showAddToCollection = false
                },
                onCreateNew = {
                    showAddToCollection = false
                    showCreateCollection = true
                },
                onDismiss = { showAddToCollection = false }
            )
        }
    }
}
