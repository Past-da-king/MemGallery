package com.example.memgallery.proof

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.memgallery.ui.design.FloatingNav
import com.example.memgallery.ui.design.NavItem
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.screens.GalleryActions
import com.example.memgallery.ui.screens.GalleryContent
import com.example.memgallery.ui.screens.GalleryUiState
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

    private fun home(filter: String = "All", memories: List<com.example.memgallery.data.local.entity.MemoryEntity> = sampleMemories()) = GalleryUiState(
        memories = memories,
        collections = sampleCollections(),
        collectionInfo = mapOf(
            1 to CollectionInfo(38, listOf("art://cliff", "art://sunset")),
            2 to CollectionInfo(24, listOf("art://soup")),
            3 to CollectionInfo(61, emptyList()),
            4 to CollectionInfo(17, listOf("art://city")),
        ),
        filter = filter,
        stats = GalleryStats(kept = 412, thisWeek = 38, reviewCount = 9, dayStreak = 5),
        heat = sampleHeat(),
        today = ProofToday,
    )

    @androidx.compose.runtime.Composable
    private fun Nav(selected: Int, fab: androidx.compose.ui.graphics.vector.ImageVector) {
        Box(Modifier.fillMaxSize()) {
            FloatingNav(
                items = listOf(
                    NavItem("Chat", Icons.Outlined.ChatBubbleOutline, Icons.Filled.Chat),
                    NavItem("Memories", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
                    NavItem("Tasks", Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt),
                ),
                selectedIndex = selected, onSelect = {}, fabIcon = fab,
                modifier = Modifier.align(Alignment.BottomCenter).appNavigationBarsPadding().padding16()
            )
        }
    }

    private fun Modifier.padding16() = this.then(Modifier.padding(bottom = 16.dp))

    private fun shoot(name: String, dark: Boolean, ui: GalleryUiState, search: Boolean = false) = rule.proof(name, dark) {
        Box(Modifier.fillMaxSize()) {
            GalleryContent(ui, GalleryActions(), bottomPadding = 128.dp, initialSearchOpen = search)
            if (!search) Nav(1, if (ui.filter == "Collections") Icons.Filled.Add else Icons.Filled.Add)
        }
    }

    @Test fun homeLight() = shoot("01-home", false, home())
    @Test fun homeDark() = shoot("01-home", true, home())
}
