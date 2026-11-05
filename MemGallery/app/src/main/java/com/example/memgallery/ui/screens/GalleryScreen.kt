package com.example.memgallery.ui.screens

import com.example.memgallery.ui.design.appStatusBarsPadding
import com.example.memgallery.ui.design.appNavigationBarsPadding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.design.ActivityDots
import com.example.memgallery.ui.design.BlobHeader
import com.example.memgallery.ui.design.CookieIconButton
import com.example.memgallery.ui.design.EmptyState
import com.example.memgallery.ui.design.FilterButtonGroup
import com.example.memgallery.ui.design.FilterOption
import com.example.memgallery.ui.design.HeaderTitleRow
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.MemoryTile
import com.example.memgallery.ui.design.RoundIconButton
import com.example.memgallery.ui.design.SearchPill
import com.example.memgallery.ui.design.SectionHeader
import com.example.memgallery.ui.design.StatTileRow
import com.example.memgallery.ui.design.Tone
import com.example.memgallery.ui.design.colors
import com.example.memgallery.ui.design.displayTitle
import com.example.memgallery.ui.design.kind
import com.example.memgallery.ui.design.memoryPainter
import com.example.memgallery.ui.design.overlapUp
import com.example.memgallery.ui.design.fullBleed
import com.example.memgallery.ui.viewmodels.CollectionInfo
import com.example.memgallery.ui.viewmodels.GalleryStats
import com.example.memgallery.ui.viewmodels.GalleryViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Everything the Home page draws. Real data in the app, sample data in screenshot tests. */
data class GalleryUiState(
    val memories: List<MemoryEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val collectionInfo: Map<Int, CollectionInfo> = emptyMap(),
    val searchText: String = "",
    val filter: String = "All",
    val stats: GalleryStats = GalleryStats(),
    val heat: Map<LocalDate, Int> = emptyMap(),
    val showRecent: Boolean = true,
    val selectionActive: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val today: LocalDate = LocalDate.now(),
)

class GalleryActions(
    val onSearchChange: (String) -> Unit = {},
    val onFilter: (String) -> Unit = {},
    val onOpenMemory: (MemoryEntity) -> Unit = {},
    val onToggleSelect: (MemoryEntity) -> Unit = {},
    val onLongPress: (MemoryEntity) -> Unit = {},
    val onCancelSelection: () -> Unit = {},
    val onAddSelectionToCollection: () -> Unit = {},
    val onDeleteSelection: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenStats: () -> Unit = {},
    val onOpenCollection: (CollectionEntity) -> Unit = {},
    val onAsk: (String) -> Unit = {},
    val onCapture: () -> Unit = {},
)

private val FILTER_KEYS = listOf("All", "Images", "Notes", "Audio", "Bookmarks", "Collections")

@Composable
private fun filterOptions(): List<FilterOption> = listOf(
    FilterOption("All", stringResource(R.string.gallery_filter_all), Icons.Outlined.Apps, Icons.Filled.Apps),
    FilterOption("Images", stringResource(R.string.gallery_filter_images), Icons.Outlined.Image, Icons.Filled.Image),
    FilterOption("Notes", stringResource(R.string.gallery_filter_notes), Icons.Outlined.StickyNote2, Icons.Filled.StickyNote2),
    FilterOption("Audio", stringResource(R.string.gallery_filter_audio), Icons.Outlined.Mic, Icons.Filled.Mic),
    FilterOption("Bookmarks", stringResource(R.string.gallery_filter_bookmarks), Icons.Outlined.Bookmark, Icons.Filled.Bookmark),
    FilterOption("Collections", stringResource(R.string.gallery_filter_collections), Icons.Outlined.Folder, Icons.Filled.Folder),
)

/** Stateful Home page. Sheets that need the nav bar's FAB (add menu, create collection) live in HomeScreen. */
@Composable
fun GalleryScreen(
    navController: NavController,
    bottomPadding: Dp,
    onCreateCollection: () -> Unit,
    onAddToCollection: () -> Unit,
    onAskAboutSearch: (String) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
    initialSearchOpen: Boolean = false,
) {
    val memories by viewModel.memories.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val collectionInfo by viewModel.collectionInfo.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val filter by viewModel.selectedFilter.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val heat by viewModel.heatMapData.collectAsState()
    val highlights by viewModel.highlightMemories.collectAsState()
    val selectionActive by viewModel.selectionModeActive.collectAsState()
    val selectedIds by viewModel.selectedMemoryIds.collectAsState()

    var showStatsSheet by remember { mutableStateOf(false) }
    var showDeleteSheet by remember { mutableStateOf(false) }

    // Deletion that needs the system's permission (scoped storage) comes back through this launcher.
    val pendingDeletionIntent by viewModel.pendingDeletionIntent.collectAsState()
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.deleteSelectedMemories()
        else viewModel.clearPendingDeletionIntent()
    }
    LaunchedEffect(pendingDeletionIntent) {
        pendingDeletionIntent?.let { deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(it).build()) }
    }

    val state = GalleryUiState(
        memories = memories, collections = collections, collectionInfo = collectionInfo,
        searchText = searchText, filter = filter, stats = stats, heat = heat,
        showRecent = highlights.isNotEmpty(), selectionActive = selectionActive, selectedIds = selectedIds
    )
    val actions = GalleryActions(
        onSearchChange = viewModel::onSearchTextChange,
        onFilter = viewModel::onFilterSelected,
        onOpenMemory = { navController.navigate(Screen.Detail.createRoute(it.id)) },
        onToggleSelect = { viewModel.toggleMemorySelection(it.id) },
        onLongPress = {
            if (!selectionActive) viewModel.toggleSelectionMode()
            viewModel.toggleMemorySelection(it.id)
        },
        onCancelSelection = viewModel::clearSelection,
        onAddSelectionToCollection = onAddToCollection,
        onDeleteSelection = { showDeleteSheet = true },
        onOpenSettings = { navController.navigate(Screen.Settings.route) },
        onOpenStats = { showStatsSheet = true },
        onOpenCollection = { navController.navigate(Screen.CollectionDetail.createRoute(it.id)) },
        onAsk = onAskAboutSearch,
        onCapture = onCreateCollection,
    )

    GalleryContent(state, actions, bottomPadding, initialSearchOpen = initialSearchOpen, onCreateCollection = onCreateCollection)

    if (showStatsSheet) {
        com.example.memgallery.ui.design.MemSheet(onDismiss = { showStatsSheet = false }) {
            com.example.memgallery.ui.design.SheetTitle(stringResource(R.string.gallery_stats_title))
            com.example.memgallery.ui.components.ContributionGraph(
                data = heat,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteSheet) {
        com.example.memgallery.ui.design.ConfirmSheet(
            title = stringResource(R.string.gallery_dialog_confirm_delete_title),
            body = stringResource(R.string.gallery_dialog_confirm_bulk_delete_body, selectedIds.size),
            confirmLabel = stringResource(R.string.gallery_button_delete_all),
            dismissLabel = stringResource(R.string.common_cancel),
            destructive = true,
            onConfirm = {
                viewModel.deleteSelectedMemories()
                showDeleteSheet = false
            },
            onDismiss = { showDeleteSheet = false }
        )
    }
}

private fun MemoryEntity.day(): LocalDate =
    Instant.ofEpochMilli(creationTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryContent(
    state: GalleryUiState,
    actions: GalleryActions,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    initialSearchOpen: Boolean = false,
    onCreateCollection: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    var searchOpen by rememberSaveable { mutableStateOf(initialSearchOpen) }
    androidx.activity.compose.BackHandler(enabled = searchOpen) {
        searchOpen = false
        actions.onSearchChange("")
    }
    androidx.activity.compose.BackHandler(enabled = state.selectionActive && !searchOpen) { actions.onCancelSelection() }
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val untitled = stringResource(R.string.memory_card_untitled)
    val inCollections = state.filter == "Collections"
    val today = state.today

    Box(modifier.fillMaxSize().background(scheme.surface)) {
        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalItemSpacing = 4.dp
        ) {
            // Header, floating search pill and filters
            item(span = StaggeredGridItemSpan.FullLine, key = "header") {
                Column(Modifier.fullBleed(16.dp)) {
                    if (state.selectionActive) {
                        SelectionBar(state.selectedIds.size, actions)
                    } else {
                        BlobHeader {
                            HeaderTitleRow(
                                eyebrow = stringResource(R.string.home_eyebrow, today.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())), state.stats.kept),
                                title = stringResource(R.string.home_title)
                            ) {
                                RoundIconButton(Icons.Rounded.Insights, stringResource(R.string.gallery_cd_activity_stats), actions.onOpenStats)
                                CookieIconButton(Icons.Rounded.Settings, stringResource(R.string.gallery_cd_settings), actions.onOpenSettings)
                            }
                            Spacer(Modifier.height(12.dp))
                            StatTileRow(
                                listOf(
                                    state.stats.thisWeek.toString() to stringResource(R.string.home_stat_this_week),
                                    state.stats.reviewCount.toString() to stringResource(R.string.home_stat_to_review),
                                    state.stats.dayStreak.toString() to stringResource(R.string.home_stat_streak),
                                )
                            )
                            Spacer(Modifier.height(14.dp))
                            ActivityDots(state.heat, today = today)
                            Spacer(Modifier.height(41.dp))
                        }
                        SearchPill(
                            placeholder = stringResource(R.string.gallery_search_placeholder),
                            onClick = { searchOpen = true },
                            modifier = Modifier.overlapUp(24.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        FilterButtonGroup(filterOptions(), state.filter, actions.onFilter)
                        Spacer(Modifier.height(0.dp))
                    }
                }
            }

            if (inCollections) {
                collectionItems(state, actions, onCreateCollection)
            } else if (state.memories.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.PhotoLibrary,
                        title = stringResource(if (state.searchText.isBlank()) R.string.home_empty_title else R.string.home_empty_search),
                        body = stringResource(R.string.home_empty_body),
                        modifier = Modifier.padding(top = 24.dp),
                        primaryLabel = if (state.searchText.isBlank()) stringResource(R.string.gallery_cd_add_memory) else null,
                        onPrimary = actions.onCapture
                    )
                }
            } else {
                // Recent carousel
                val recent = state.memories.filter { it.imageUri != null }.take(8)
                if (state.showRecent && !state.selectionActive && state.filter == "All" && state.searchText.isBlank() && recent.size >= 3) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "recent-header") {
                        Column(Modifier.fullBleed(16.dp)) {
                            Spacer(Modifier.height(16.dp))
                            SectionHeader(stringResource(R.string.home_recent)) {
                                TextButton(onClick = {
                                    scope.launch { gridState.animateScrollToItem(3) }
                                }) { Text(stringResource(R.string.home_see_all), style = MaterialTheme.typography.titleSmall) }
                            }
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine, key = "recent") {
                        Column(Modifier.fullBleed(16.dp)) {
                            RecentCarousel(recent, untitled, actions.onOpenMemory)
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                // Day groups
                val groups = state.memories.groupBy { it.day() }.toSortedMap(compareByDescending { it })
                groups.forEach { (day, list) ->
                    item(span = StaggeredGridItemSpan.FullLine, key = "day-$day") {
                        val label = when (day) {
                            today -> stringResource(R.string.home_today)
                            today.minusDays(1) -> stringResource(R.string.home_yesterday)
                            else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        }
                        Column(Modifier.fullBleed(16.dp)) {
                            Spacer(Modifier.height(12.dp))
                            SectionHeader(label) {
                                Text(
                                    androidx.compose.ui.res.pluralStringResource(R.plurals.home_n_memories, list.size, list.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    items(list.size, key = { "m-${list[it].id}" }) { i ->
                        val m = list[i]
                        MemoryTile(
                            memory = m,
                            selected = state.selectedIds.contains(m.id),
                            onClick = { if (state.selectionActive) actions.onToggleSelect(m) else actions.onOpenMemory(m) },
                            onLongClick = { actions.onLongPress(m) },
                            untitledLabel = untitled,
                            modifier = Modifier.padding(horizontal = if (i % 2 == 0) 0.dp else 0.dp)
                        )
                    }
                }
            }
        }

        if (searchOpen) {
            SearchView(
                query = state.searchText,
                onQueryChange = actions.onSearchChange,
                onClose = {
                    searchOpen = false
                    actions.onSearchChange("")
                },
                filter = state.filter.takeIf { it != "Collections" } ?: "All",
                onFilter = actions.onFilter,
                results = state.memories,
                onOpen = {
                    searchOpen = false
                    actions.onOpenMemory(it)
                },
                onAsk = {
                    searchOpen = false
                    actions.onAsk(it)
                },
                today = today,
                requestFocus = !initialSearchOpen
            )
        }
    }
}

@Composable
private fun SelectionBar(count: Int, actions: GalleryActions) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerHigh)
            .appStatusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = actions.onCancelSelection), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, stringResource(R.string.gallery_cd_cancel_selection), tint = scheme.onSurface)
        }
        Text(
            stringResource(R.string.gallery_selection_count, count),
            Modifier.weight(1f).padding(start = 8.dp),
            style = MaterialTheme.typography.titleLarge, color = scheme.onSurface
        )
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = count > 0, onClick = actions.onAddSelectionToCollection), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CreateNewFolder, stringResource(R.string.gallery_cd_add_to_collection), tint = scheme.onSurface)
        }
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = count > 0, onClick = actions.onDeleteSelection), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Delete, stringResource(R.string.gallery_cd_delete_selected), tint = scheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentCarousel(recent: List<MemoryEntity>, untitled: String, onOpen: (MemoryEntity) -> Unit) {
    val carouselState = rememberCarouselState { recent.size }
    val shapes = listOf<@Composable () -> androidx.compose.ui.graphics.Shape>(
        { RoundedCornerShape(28.dp) }, { RoundedCornerShape(50) }, { MemShape.cookie }, { RoundedCornerShape(28.dp) }, { RoundedCornerShape(50) }
    )
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 168.dp,
        modifier = Modifier.fillMaxWidth().height(196.dp).padding(top = 0.dp),
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) { index ->
        val m = recent[index]
        val shape = shapes[index % shapes.size]()
        Box(Modifier.fillMaxSize().maskClip(shape).clickable { onOpen(m) }) {
            if (m.imageUri != null) {
                Image(
                    memoryPainter(m.imageUri), m.aiTitle, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            } else {
                val tone = Tone.entries[(m.id.mod(3)) * 2 % Tone.entries.size]
                val (bg, fg) = tone.colors()
                Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                    Text(
                        m.displayTitle(untitled),
                        Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 4, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---- Collections ----

private fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.collectionItems(
    state: GalleryUiState,
    actions: GalleryActions,
    onCreate: () -> Unit
) {
    if (state.collections.isEmpty()) {
        item(span = StaggeredGridItemSpan.FullLine, key = "collections-empty") {
            EmptyState(
                icon = Icons.Rounded.CreateNewFolder,
                title = stringResource(R.string.collections_empty_title),
                body = stringResource(R.string.collections_empty_body),
                modifier = Modifier.padding(top = 24.dp),
                artScale = 0.72f,
                primaryLabel = stringResource(R.string.gallery_cd_create_collection),
                primaryIcon = Icons.Rounded.CreateNewFolder,
                onPrimary = onCreate
            )
        }
        return
    }
    state.collections.forEachIndexed { index, c ->
        val info = state.collectionInfo[c.id] ?: CollectionInfo()
        item(span = if (index == 0) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane, key = "c-${c.id}") {
            CollectionTile(c, info, index, featured = index == 0, onClick = { actions.onOpenCollection(c) })
        }
    }
}

private val collectionTones = listOf(Tone.Primary, Tone.Tertiary, Tone.Sky, Tone.Amber, Tone.Success, Tone.Secondary)

/** A collection: tonal card with cover art, name and count. The first one is wide and features its cover stack. */
@Composable
fun CollectionTile(
    collection: CollectionEntity,
    info: CollectionInfo,
    index: Int,
    featured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = collectionTones[index % collectionTones.size].colors()
    val covers = info.covers
    val countLabel = androidx.compose.ui.res.pluralStringResource(R.plurals.collections_n_memories, info.count, info.count)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        if (featured) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                if (collection.description.isNotBlank()) {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = fg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.collections_auto_sorted).uppercase(), style = MaterialTheme.typography.labelMedium, color = fg)
                    }
                } else Spacer(Modifier.size(1.dp))
                CoverStack(covers)
            }
            Spacer(Modifier.height(12.dp))
        } else {
            CoverStack(covers.take(1), size = 64.dp)
            Spacer(Modifier.height(if (covers.isEmpty()) 12.dp else 24.dp))
        }
        Text(collection.name, style = if (featured) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            if (featured && collection.description.isNotBlank()) "${collection.description} · $countLabel" else countLabel,
            style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoverStack(covers: List<String>, size: Dp = 112.dp) {
    if (covers.isEmpty()) return
    val shapes = listOf<@Composable () -> androidx.compose.ui.graphics.Shape>({ MemShape.softBurst }, { RoundedCornerShape(16.dp) }, { MemShape.cookie })
    Box(Modifier.size(width = if (covers.size > 1) size * 1.55f else size, height = size)) {
        covers.take(3).forEachIndexed { i, uri ->
            Image(
                memoryPainter(uri), null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(if (i == 0) Alignment.TopStart else Alignment.BottomEnd)
                    .size(if (i == 0) size * 0.9f else size * 0.7f)
                    .clip(shapes[i % shapes.size]())
            )
        }
    }
}
