package com.example.memgallery.lifeagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.memgallery.R
import com.example.memgallery.ui.design.MemSheet
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.MemTextField
import com.example.memgallery.ui.design.Overline
import com.example.memgallery.ui.design.SheetTitle
import com.example.memgallery.ui.design.SubScreenHeader
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.theme.memColors

class ConnectionsActions(
    val onBack: () -> Unit = {},
    val onSaveKey: (String) -> Unit = {},
    val onConnect: (LifeAppId) -> Unit = {},
    val onDisconnect: (LifeAppId) -> Unit = {},
    val onToggleCollection: (Boolean) -> Unit = {},
    val onTime: (Int, Int) -> Unit = { _, _ -> },
    val onCollectNow: () -> Unit = {},
)

/** Which slide-up sheet is open on the Connections screen. */
sealed interface ConnectionsSheet {
    data object Key : ConnectionsSheet
    data class Connect(val app: LifeAppId) : ConnectionsSheet
    data class Disconnect(val app: LifeAppId) : ConnectionsSheet
    data object Time : ConnectionsSheet
}

/** Settings > Advanced > Connections. Pure UI: state in, actions out. */
@Composable
fun ConnectionsContent(
    state: ConnectionsUiState,
    actions: ConnectionsActions,
    modifier: Modifier = Modifier,
    initialSheet: ConnectionsSheet? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var sheet by remember { mutableStateOf(initialSheet) }
    val connectedCount = state.rows.count { it.connected }

    Column(modifier.fillMaxSize().background(scheme.surface).verticalScroll(rememberScrollState())) {
        SubScreenHeader(
            title = stringResource(R.string.la_connections_title),
            eyebrow = stringResource(R.string.la_connections_eyebrow),
            onBack = actions.onBack,
        )
        Spacer(Modifier.height(20.dp))
        ReadsOnlyBanner(Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(24.dp))
        Overline(stringResource(R.string.la_key_section))
        Spacer(Modifier.height(8.dp))
        KeyCard(state, onChange = { sheet = ConnectionsSheet.Key }, Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(24.dp))
        Overline(stringResource(R.string.la_apps_section, connectedCount, state.rows.size))
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = RowGap) {
            state.rows.forEach { row ->
                ConnectionRow(
                    row = row,
                    keySet = state.keySet,
                    onConnect = { sheet = ConnectionsSheet.Connect(row.app) },
                    onMore = { sheet = ConnectionsSheet.Disconnect(row.app) },
                    onAddKey = { sheet = ConnectionsSheet.Key },
                )
            }
        }

        if (state.keySet) {
            Spacer(Modifier.height(24.dp))
            Overline(stringResource(R.string.la_daily_section))
            Spacer(Modifier.height(8.dp))
            DailyCard(state, actions, onTime = { sheet = ConnectionsSheet.Time }, Modifier.padding(horizontal = 16.dp), anyConnected = connectedCount > 0)
        }
        Spacer(Modifier.height(32.dp))
        Spacer(Modifier.appNavigationBarsPadding())
    }

    when (val s = sheet) {
        null -> Unit
        ConnectionsSheet.Key -> KeySheet(state, onSave = { actions.onSaveKey(it); sheet = null }, onDismiss = { sheet = null })
        is ConnectionsSheet.Connect -> ConnectSheet(s.app, onConnect = { actions.onConnect(s.app); sheet = null }, onDismiss = { sheet = null })
        is ConnectionsSheet.Disconnect -> DisconnectSheet(s.app, onDisconnect = { actions.onDisconnect(s.app); sheet = null }, onDismiss = { sheet = null })
        ConnectionsSheet.Time -> TimeSheet(state, onPick = { h, m -> actions.onTime(h, m); sheet = null }, onDismiss = { sheet = null })
    }
}

@Composable
private fun KeyCard(state: ConnectionsUiState, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer).padding(16.dp)) {
        if (state.keySet) {
            MemTextField(
                value = state.maskedKey,
                onValueChange = {},
                label = stringResource(R.string.la_key_label),
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.la_key_private),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = onChange, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Rounded.VpnKey, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.la_key_change), style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(MemShape.cookie).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.VpnKey, null, tint = scheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.la_nokey_title), style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                    Text(stringResource(R.string.la_nokey_body), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onChange, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.VpnKey, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.la_nokey_add), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    row: ConnectionRowUi,
    keySet: Boolean,
    onConnect: () -> Unit,
    onMore: () -> Unit,
    onAddKey: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val mem = MaterialTheme.memColors
    val failed = row.error != null
    RowCard(container = if (failed && !row.connected) scheme.errorContainer.copy(alpha = 0.5f) else scheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppBadge(row.app, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    appLabel(row.app, ""),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (failed) scheme.error else if (row.connected) mem.success else scheme.outline)
                    Spacer(Modifier.width(8.dp))
                    val status = when {
                        failed -> row.error!!
                        row.connected && row.lastPulled != null -> stringResource(R.string.la_status_connected_pulled, row.lastPulled)
                        row.connected -> stringResource(R.string.la_status_connected_never)
                        !keySet -> stringResource(R.string.la_status_needs_key)
                        else -> stringResource(R.string.la_status_not_connected)
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (failed) scheme.error else scheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            when {
                row.busy -> Text(stringResource(R.string.la_busy), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                row.connected -> IconButton(onClick = onMore, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.la_cd_more), tint = scheme.onSurfaceVariant)
                }
                !keySet -> TextButton(onClick = onAddKey) { Text(stringResource(R.string.la_nokey_add_short), style = MaterialTheme.typography.titleMedium) }
                failed -> FilledTonalButton(onClick = onConnect, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.la_retry), style = MaterialTheme.typography.titleMedium)
                }
                else -> FilledTonalButton(onClick = onConnect, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(R.string.la_connect), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun DailyCard(
    state: ConnectionsUiState,
    actions: ConnectionsActions,
    onTime: () -> Unit,
    modifier: Modifier = Modifier,
    anyConnected: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.la_daily_title), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(stringResource(R.string.la_daily_body), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Switch(checked = state.collectionEnabled, onCheckedChange = actions.onToggleCollection, enabled = anyConnected)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(onClick = onTime, modifier = Modifier.height(48.dp).weight(1f)) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.la_daily_time, state.hour, state.minute), style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = actions.onCollectNow, enabled = anyConnected, modifier = Modifier.height(48.dp).weight(1f)) {
                Text(stringResource(R.string.la_collect_now), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---- Sheets (always slide-up, never a centred dialog) ----

@Composable
private fun KeySheet(state: ConnectionsUiState, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    SheetFrame(onDismiss) {
        SheetTitle(
            stringResource(if (state.keySet) R.string.la_keysheet_title_change else R.string.la_keysheet_title_add),
            stringResource(R.string.la_keysheet_body)
        )
        Column(Modifier.padding(horizontal = 24.dp)) {
            MemTextField(
                value = value, onValueChange = { value = it },
                label = stringResource(R.string.la_key_label), isSecret = true,
                errorText = state.keyError,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.la_key_private), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSave(value.trim()) }, enabled = value.isNotBlank() && !state.keySaving,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(stringResource(R.string.la_keysheet_save), style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(stringResource(R.string.la_cancel), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun willAndNever(app: LifeAppId): Pair<List<String>, List<String>> = when (app) {
    LifeAppId.GMAIL -> stringArrayResource(R.array.la_gmail_will).toList() to stringArrayResource(R.array.la_gmail_never).toList()
    LifeAppId.GCAL -> stringArrayResource(R.array.la_gcal_will).toList() to stringArrayResource(R.array.la_gcal_never).toList()
    LifeAppId.CLICKUP -> stringArrayResource(R.array.la_clickup_will).toList() to stringArrayResource(R.array.la_clickup_never).toList()
    LifeAppId.SLACK -> stringArrayResource(R.array.la_slack_will).toList() to stringArrayResource(R.array.la_slack_never).toList()
}

@Composable
fun ConnectSheet(app: LifeAppId, onConnect: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val name = appLabel(app, "")
    val (will, never) = willAndNever(app)
    SheetFrame(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppBadge(app, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.la_connect_sheet_title, name), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(stringResource(R.string.la_connect_sheet_sub), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                }
            }
            ReadsOnlyBanner(
                Modifier.padding(horizontal = 24.dp),
                title = stringResource(R.string.la_connect_banner), body = ""
            )
            Spacer(Modifier.height(16.dp))
            Overline(stringResource(R.string.la_it_will))
            Spacer(Modifier.height(8.dp))
            will.forEach { PermissionLine(it, allowed = true) }
            Spacer(Modifier.height(12.dp))
            Overline(stringResource(R.string.la_it_never))
            Spacer(Modifier.height(8.dp))
            never.forEach { PermissionLine(it, allowed = false) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onConnect, modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Link, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.la_connect_cta, name), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.la_cancel), style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.la_connect_foot), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

@Composable
private fun PermissionLine(text: String, allowed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val mem = MaterialTheme.memColors
    Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 48.dp, height = 36.dp).clip(RoundedCornerShape(12.dp))
                .background(if (allowed) mem.successContainer else scheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (allowed) Icons.Rounded.Check else Icons.Rounded.Close, null,
                tint = if (allowed) mem.onSuccessContainer else scheme.onErrorContainer, modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
    }
}

@Composable
private fun DisconnectSheet(app: LifeAppId, onDisconnect: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val name = appLabel(app, "")
    SheetFrame(onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppBadge(app, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.la_disconnect_title, name), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                Text(stringResource(R.string.la_disconnect_body), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(stringResource(R.string.la_cancel), style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = onDisconnect, modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
            ) {
                Icon(Icons.Rounded.LinkOff, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.la_disconnect), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSheet(state: ConnectionsUiState, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = state.hour, initialMinute = state.minute, is24Hour = true)
    SheetFrame(onDismiss) {
        SheetTitle(stringResource(R.string.la_time_title), stringResource(R.string.la_time_body))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TimePicker(state = picker)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onPick(picker.hour, picker.minute) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.la_time_save), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
