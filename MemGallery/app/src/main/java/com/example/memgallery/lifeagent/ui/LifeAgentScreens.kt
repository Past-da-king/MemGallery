package com.example.memgallery.lifeagent.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.navigation.Screen

/** Routes of the Life Agent screens. Registered in AppNavigation. */
object LifeRoutes {
    const val Connections = "life_connections"
    const val Collecting = "life_collecting"
    const val Review = "life_review"
    const val People = "life_people"
    const val Person = "life_person/{name}"
    const val Report = "life_report"
    fun person(name: String) = "life_person/${Uri.encode(name)}"
}

private fun androidx.compose.ui.platform.UriHandler.openSafely(url: String) {
    // Only web links the user's own apps gave us; anything else is ignored.
    if (url.startsWith("https://") || url.startsWith("http://")) runCatching { openUri(url) }
}

@Composable
fun ConnectionsScreen(navController: NavController, viewModel: ConnectionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    ConnectionsContent(
        state = state,
        actions = ConnectionsActions(
            onBack = { navController.navigateUp() },
            onSaveKey = viewModel::saveKey,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
            onToggleCollection = viewModel::setEnabled,
            onTime = viewModel::setTime,
            onCollectNow = { viewModel.collectNow(); navController.navigate(LifeRoutes.Collecting) },
        ),
    )
}

@Composable
fun CollectingScreen(navController: NavController, viewModel: CollectingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    CollectingContent(
        state = state,
        actions = CollectingActions(
            onBack = { navController.navigateUp() },
            onCancel = { viewModel.cancel(); navController.navigateUp() },
            onBackground = { navController.navigateUp() },
            onCollectNow = viewModel::start,
            onReview = { navController.navigate(LifeRoutes.Review) { popUpTo(LifeRoutes.Collecting) { inclusive = true } } },
            onOpenConnections = { navController.navigate(LifeRoutes.Connections) },
        ),
    )
}

@Composable
fun ReviewScreen(navController: NavController, viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val uri = LocalUriHandler.current
    // Leaving the screen ends the undo window: write the last decision.
    DisposableEffect(Unit) { onDispose { viewModel.commitPending() } }
    ReviewContent(
        state = state,
        actions = ReviewActions(
            onClose = { navController.navigateUp() },
            onKeep = viewModel::keep,
            onDrop = viewModel::drop,
            onUndo = viewModel::undo,
            onApproveAll = viewModel::approveAll,
            onOpenSource = uri::openSafely,
            onAsk = { id ->
                viewModel.ask(id) {
                    navController.navigate(Screen.Gallery.route) { popUpTo(Screen.Gallery.route) { inclusive = false } }
                }
            },
        ),
    )
}

@Composable
fun PeopleScreen(navController: NavController, viewModel: PeopleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    PeopleContent(
        state = state,
        actions = PeopleActions(
            onBack = { navController.navigateUp() },
            onQuery = viewModel::setQuery,
            onSort = viewModel::setSort,
            onOpenPerson = { navController.navigate(LifeRoutes.person(it)) },
            onOpenConnections = { navController.navigate(LifeRoutes.Connections) },
        ),
    )
}

@Composable
fun PersonScreen(navController: NavController, viewModel: PersonViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    PersonContent(
        state = state,
        actions = PersonActions(
            onBack = { navController.navigateUp() },
            onAsk = {
                viewModel.ask {
                    navController.navigate(Screen.Gallery.route) { popUpTo(Screen.Gallery.route) { inclusive = false } }
                }
            },
            onTick = viewModel::tick,
            onSnooze = viewModel::snooze,
        ),
    )
}

@Composable
fun DailyReportScreen(navController: NavController, viewModel: DailyReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val uri = LocalUriHandler.current
    DailyReportContent(
        state = state,
        actions = DailyReportActions(
            onBack = { navController.navigateUp() },
            onRefresh = { viewModel.refresh(); navController.navigate(LifeRoutes.Collecting) },
            onReview = { navController.navigate(LifeRoutes.Review) },
            onPeople = { navController.navigate(LifeRoutes.People) },
            onOpenConnections = { navController.navigate(LifeRoutes.Connections) },
            onTick = viewModel::tick,
            onSnooze = viewModel::snooze,
            onOpenUrl = uri::openSafely,
        ),
    )
}

/** The one entry on Home: a card that opens the Daily report, with the number of memories waiting for review. */
@Composable
fun LifeAgentHomeEntry(navController: NavController, modifier: Modifier = Modifier, viewModel: LifeEntryViewModel = hiltViewModel()) {
    val toReview by viewModel.toReview.collectAsState()
    LifeAgentEntryCard(toReview = toReview, onClick = { navController.navigate(LifeRoutes.Report) }, modifier = modifier)
}

@Composable
fun LifeAgentEntryCard(toReview: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(scheme.secondaryContainer)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(com.example.memgallery.ui.design.MemShape.cookie).background(scheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Today, null, tint = scheme.onPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.la_report_entry_title), style = MaterialTheme.typography.titleMedium, color = scheme.onSecondaryContainer)
            Text(
                if (toReview > 0) stringResource(R.string.la_report_entry_review, toReview) else stringResource(R.string.la_report_entry_sub),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = scheme.onSecondaryContainer)
    }
}
