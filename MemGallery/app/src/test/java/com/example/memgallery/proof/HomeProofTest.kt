package com.example.memgallery.proof

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.ui.components.sheets.SelectCollectionSheet
import com.example.memgallery.ui.design.AddFabMenu
import com.example.memgallery.ui.design.AddMenuItem
import com.example.memgallery.ui.design.FloatingNav
import com.example.memgallery.ui.design.NavItem
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.screens.GalleryActions
import com.example.memgallery.ui.screens.GalleryContent
import com.example.memgallery.ui.screens.GalleryUiState
import com.example.memgallery.ui.theme.memColors
import com.example.memgallery.ui.viewmodels.CollectionInfo
import com.example.memgallery.ui.viewmodels.GalleryStats
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi", application = android.app.Application::class)
class HomeProofTest {
    @get:Rule val rule = createComposeRule()

    private fun home(
        filter: String = "All",
        memories: List<MemoryEntity> = sampleMemories(),
        collections: Boolean = true,
        search: String = "",
        selected: Set<Int> = emptySet(),
    ) = GalleryUiState(
        memories = memories,
        collections = if (collections) sampleCollections() else emptyList(),
        collectionInfo = mapOf(
            1 to CollectionInfo(38, listOf("art://cliff", "art://sunset")),
            2 to CollectionInfo(24, listOf("art://soup")),
            3 to CollectionInfo(61, emptyList()),
            4 to CollectionInfo(17, listOf("art://city")),
        ),
        filter = filter,
        searchText = search,
        stats = GalleryStats(kept = 412, thisWeek = 38, reviewCount = 9, dayStreak = 5),
        heat = sampleHeat(),
        selectionActive = selected.isNotEmpty(),
        selectedIds = selected,
        today = ProofToday,
    )

    @Composable
    private fun Nav(fab: androidx.compose.ui.graphics.vector.ImageVector) {
        Box(Modifier.fillMaxSize()) {
            FloatingNav(
                items = listOf(
                    NavItem("Chat", Icons.Outlined.ChatBubbleOutline, Icons.Filled.Chat),
                    NavItem("Memories", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
                    NavItem("Tasks", Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt),
                ),
                selectedIndex = 1, onSelect = {}, fabIcon = fab,
                modifier = Modifier.align(Alignment.BottomCenter).appNavigationBarsPadding().padding(bottom = 16.dp)
            )
        }
    }

    private fun shoot(name: String, dark: Boolean, ui: GalleryUiState, search: Boolean = false) = rule.proof(name, dark) {
        Box(Modifier.fillMaxSize()) {
            GalleryContent(ui, GalleryActions(), bottomPadding = 128.dp, initialSearchOpen = search)
            if (!search && !ui.selectionActive) Nav(if (ui.filter == "Collections") Icons.Filled.CreateNewFolder else Icons.Filled.Add)
        }
    }

    // 01 Home
    @Test fun homeLight() = shoot("01-home", false, home())
    @Test fun homeDark() = shoot("01-home", true, home())

    // 05 Search
    private fun searchUi() = home(search = "wifi pass", memories = sampleMemories().filter { it.id in listOf(1, 8, 4, 7) })
    @Test fun searchLight() = shoot("05-search", false, searchUi(), search = true)
    @Test fun searchDark() = shoot("05-search", true, searchUi(), search = true)

    // 07 Collections
    @Test fun collectionsLight() = shoot("07-collections", false, home("Collections"))
    @Test fun collectionsDark() = shoot("07-collections", true, home("Collections"))

    // 12 Empty collections (same pattern as empty tasks)
    @Test fun emptyCollectionsLight() = shoot("12-empty-collections", false, home("Collections", collections = false))
    @Test fun emptyCollectionsDark() = shoot("12-empty-collections", true, home("Collections", collections = false))

    // 03 Add menu (FAB menu over Home)
    private fun addMenu(dark: Boolean) = rule.proof("03-capture", dark) {
        val scheme = MaterialTheme.colorScheme
        val mem = MaterialTheme.memColors
        Box(Modifier.fillMaxSize()) {
            GalleryContent(home(), GalleryActions(), bottomPadding = 128.dp)
            AddFabMenu(
                expanded = true, onExpandedChange = {},
                items = listOf(
                    AddMenuItem("Save bookmark", Icons.Filled.Link, scheme.secondaryContainer, scheme.onSecondaryContainer) {},
                    AddMenuItem("Record audio", Icons.Filled.Mic, mem.skyContainer, mem.onSkyContainer) {},
                    AddMenuItem("Take photo", Icons.Filled.PhotoCamera, scheme.tertiaryContainer, scheme.onTertiaryContainer) {},
                    AddMenuItem("Upload image", Icons.Filled.AddPhotoAlternate, scheme.primaryContainer, scheme.onPrimaryContainer) {},
                    AddMenuItem("Text note", Icons.Filled.EditNote, scheme.surfaceContainerHighest, scheme.onSurface) {},
                )
            )
        }
    }
    @Test fun addMenuLight() = addMenu(false)
    @Test fun addMenuDark() = addMenu(true)

    // 11 Selection with the Add to Collection sheet (the sheet content is the real composable, drawn in place)
    private fun sheet(dark: Boolean) = rule.proof("11-sheet", dark) {
        val scheme = MaterialTheme.colorScheme
        val ui = home(selected = setOf(1, 5, 6))
        Box(Modifier.fillMaxSize()) {
            GalleryContent(ui, GalleryActions(), bottomPadding = 128.dp)
            Box(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.42f)))
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(scheme.surfaceContainerLow)
                    .appNavigationBarsPadding()
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.onSurfaceVariant.copy(alpha = 0.4f)))
                SelectCollectionSheet(
                    collections = sampleCollections().take(3),
                    onSelect = {}, onCreateNew = {}, onDismiss = {},
                    selectedCount = 3,
                    collectionInfo = mapOf(
                        1 to CollectionInfo(38, listOf("art://cliff")),
                        2 to CollectionInfo(24, listOf("art://soup")),
                        3 to CollectionInfo(17, listOf("art://city")),
                    ),
                    initialSelection = 1
                )
            }
        }
    }
    @Test fun sheetLight() = sheet(false)
    @Test fun sheetDark() = sheet(true)
}
