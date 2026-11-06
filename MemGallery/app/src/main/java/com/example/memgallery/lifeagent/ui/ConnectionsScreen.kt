package com.example.memgallery.lifeagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.memgallery.R
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.MemTextField
import com.example.memgallery.ui.design.Overline
import com.example.memgallery.ui.design.SheetTitle
import com.example.memgallery.ui.design.SubScreenHeader
import com.example.memgallery.ui.design.appNavigationBarsPadding

/**
 * What a Connections tap can do. Everything here is read only towards the toolkits: connect is Composio's own link,
 * disconnect only stops MemGallery reading, and nothing is ever written to Gmail, Slack or any other service.
 */
class ConnectionsActions(
    val onBack: () -> Unit = {},
    val onSaveKey: (String) -> Unit = {},
    val onClearKey: () -> Unit = {},
    val onTestKey: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onCategory: (String?) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onConnect: (ToolkitRowUi) -> Unit = {},
    val onConnectFinished: (ToolkitRowUi) -> Unit = {},
    val onDisconnect: (ToolkitRowUi) -> Unit = {},
    val onChooseProject: (ProjectRowUi) -> Unit = {},
    val onDismissProject: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    val onToggleCollection: (Boolean) -> Unit = {},
    val onTime: (Int, Int) -> Unit = { _, _ -> },
    val onCollectNow: () -> Unit = {},
)

/** Which slide-up sheet is open on the Connections screen. */
sealed interface ConnectionsSheet {
    data object Key : ConnectionsSheet
    data class Toolkit(val row: ToolkitRowUi) : ConnectionsSheet
    data object Time : ConnectionsSheet
    data object Project : ConnectionsSheet
}

@Composable
fun ConnectionsContent(
    state: ConnectionsUiState,
    actions: ConnectionsActions,
    initialSheet: ConnectionsSheet? = null,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf(initialSheet) }
    var showKeyInput by remember { mutableStateOf(!state.keySet) }

    Column(modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.la_settings_connections), onBack = actions.onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                KeyCard(
                    state = state,
                    showInput = showKeyInput,
                    onChange = { showKeyInput = !showKeyInput },
                    onSave = actions.onSaveKey,
                    onTest = actions.onTestKey,
                    onClear = { actions.onClearKey(); showKeyInput = true },
                )
            }
            item { DailyCard(state = state, onToggle = actions.onToggleCollection, onPickTime = { sheet = ConnectionsSheet.Time }, onCollectNow = actions.onCollectNow) }
            item { SearchAndFilters(state, actions) }
            if (state.loading && state.connected.isEmpty() && state.others.isEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
            state.catalogError?.let { message ->
                item { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            state.connectMessage?.let { message ->
                item {
                    Column {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        state.connectUrl?.let { url ->
                            TextButton(onClick = { actions.onOpenUrl(url) }) {
                                Text(stringResource(R.string.la_connect_link))
                            }
                        }
                    }
                }
            }
            if (state.connected.isNotEmpty()) {
                item { Overline(stringResource(R.string.la_connected_section)) }
                items(state.connected, key = { "c-" + it.slug }) { row ->
                    ToolkitRowCard(row) { sheet = ConnectionsSheet.Toolkit(row) }
                }
            }
            item { Overline(stringResource(R.string.la_all_apps)) }
            items(state.others, key = { "o-" + it.slug }) { row ->
                ToolkitRowCard(row) { sheet = ConnectionsSheet.Toolkit(row) }
            }
        }
    }

    when (val open = sheet) {
        is ConnectionsSheet.Key -> KeySheet(
            state = state,
            onSave = { actions.onSaveKey(it); sheet = null },
            onDismiss = { sheet = null },
        )
        is ConnectionsSheet.Time -> TimeSheet(
            state = state,
            onPick = { h, m -> actions.onTime(h, m); sheet = null },
            onDismiss = { sheet = null },
        )
        is ConnectionsSheet.Toolkit -> ToolkitSheet(
            row = open.row,
            onConnect = { actions.onConnect(open.row) },
            onFinished = { actions.onConnectFinished(open.row) },
            onOpenUrl = { url -> actions.onOpenUrl(url) },
            connectUrl = state.connectUrl.takeIf { state.connectMessage != null },
            onDisconnect = { actions.onDisconnect(open.row); sheet = null },
            onDismiss = { sheet = null },
        )
        else -> Unit
    }

    if (state.showProjectSheet) {
        ProjectSheet(
            projects = state.projects,
            onPick = { actions.onChooseProject(it) },
            onDismiss = actions.onDismissProject,
        )
    }
}

@Composable
private fun KeyCard(
    state: ConnectionsUiState,
    showInput: Boolean,
    onChange: () -> Unit,
    onSave: (String) -> Unit,
    onTest: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer).padding(16.dp)) {
        if (state.keySet && !showInput) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.VpnKey, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.maskedKey, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    val subtitle = buildString {
                        state.keyKind?.let { append(it) }
                        state.projectName?.let { if (isNotEmpty()) append(" · "); append(it) }
                        if (isEmpty()) append(stringResource(R.string.la_key_private))
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                IconButton(onClick = onChange) { Icon(Icons.Rounded.VpnKey, stringResource(R.string.la_key_change), tint = scheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onTest, enabled = !state.checking, modifier = Modifier.height(44.dp)) {
                    if (state.checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.la_test_key), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClear) { Text(stringResource(R.string.la_key_change)) }
            }
            state.keyCheckMessage?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.keyCheckOk == true) scheme.primary else scheme.error,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(MemShape.cookie).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.VpnKey, null, tint = scheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.la_nokey_title), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    Text(stringResource(R.string.la_nokey_body), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            KeyInput(onSave = onSave, saving = state.keySaving, error = state.keyError)
            state.keyCheckMessage?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = if (state.keyCheckOk == true) scheme.primary else scheme.error)
            }
        }
    }
}

@Composable
private fun KeyInput(onSave: (String) -> Unit, saving: Boolean, error: String?) {
    var text by remember { mutableStateOf("") }
    Column {
        MemTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(R.string.la_key_label),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onSave(text) },
            enabled = !saving && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.la_key_save), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SearchAndFilters(state: ConnectionsUiState, actions: ConnectionsActions) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemTextField(
                value = state.query,
                onValueChange = actions.onQuery,
                label = stringResource(R.string.la_search_apps),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = actions.onRefresh) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.la_refresh_catalog), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.categories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryChip(stringResource(R.string.la_category_all), state.selectedCategory == null) { actions.onCategory(null) }
                state.categories.forEach { cat ->
                    CategoryChip(cat, state.selectedCategory.equals(cat, ignoreCase = true)) { actions.onCategory(cat) }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ToolkitRowCard(row: ToolkitRowUi, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolkitLogo(row)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = row.error ?: row.lastPulled?.let { stringResource(R.string.la_last_pulled, it) } ?: row.description
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.error != null) scheme.error else scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            row.busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            row.connected -> Box(Modifier.size(28.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
            else -> Text(stringResource(R.string.la_connect), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        }
    }
}

@Composable
private fun LogoLetter(name: String, scheme: androidx.compose.material3.ColorScheme) {
    Text(
        name.take(1).uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = scheme.onSurfaceVariant,
    )
}

@Composable
private fun ToolkitLogo(row: ToolkitRowUi) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (!row.logoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = row.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(26.dp),
                content = {
                    val state = painter.state
                    if (state is AsyncImagePainter.State.Error) {
                        LogoLetter(row.name, scheme)
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                },
            )
        } else {
            Text(
                row.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolkitSheet(
    row: ToolkitRowUi,
    onConnect: () -> Unit,
    onFinished: () -> Unit,
    onOpenUrl: (String) -> Unit,
    connectUrl: String?,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    SheetFrame(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolkitLogo(row)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SheetTitle(row.name)
                    if (row.known) {
                        Text(
                            stringResource(R.string.la_tuned_parser),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.la_read_only_promise),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
            }
            row.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            if (row.connected) {
                OutlinedLikeButton(stringResource(R.string.la_disconnect), onDisconnect)
            } else {
                Button(onClick = onConnect, enabled = !row.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (row.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.la_connect), style = MaterialTheme.typography.titleMedium)
                }
                if (connectUrl != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onOpenUrl(connectUrl) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.la_connect_link), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.la_i_connected))
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlinedLikeButton(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ProjectSheet(projects: List<ProjectRowUi>, onPick: (ProjectRowUi) -> Unit, onDismiss: () -> Unit) {
    SheetFrame(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle(stringResource(R.string.la_choose_project))
            Text(
                stringResource(R.string.la_choose_project_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            projects.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPick(p) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        p.orgId?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (p.selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun KeySheet(state: ConnectionsUiState, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    SheetFrame(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle(stringResource(R.string.la_key_label))
            Text(
                stringResource(R.string.la_key_private),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            KeyInput(onSave = onSave, saving = state.keySaving, error = state.keyError)
        }
    }
}

@Composable
private fun DailyCard(state: ConnectionsUiState, onToggle: (Boolean) -> Unit, onPickTime: () -> Unit, onCollectNow: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Schedule, null, tint = scheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.la_daily_title), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(
                    stringResource(R.string.la_daily_body, String.format("%02d:%02d", state.hour, state.minute)),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.collectionEnabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onPickTime, modifier = Modifier.height(44.dp)) {
                Text(String.format("%02d:%02d", state.hour, state.minute), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onCollectNow) { Text(stringResource(R.string.la_collect_now)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSheet(state: ConnectionsUiState, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = state.hour, initialMinute = state.minute, is24Hour = true)
    SheetFrame(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SheetTitle(stringResource(R.string.la_daily_title))
            TimePicker(state = picker)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onPick(picker.hour, picker.minute) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.la_save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
