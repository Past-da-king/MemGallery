package com.example.memgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.agent.model.AgentScope
import com.example.memgallery.ui.viewmodels.AgentAccessViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Agent Access.
 *
 * The whole security model of the bridge is visible on this one screen: who holds a
 * token, what each one may do, when it was last used, and a revoke button next to it.
 * If the operator can't answer "who can see my stuff" at a glance, the feature is a
 * liability rather than a capability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentAccessScreen(
    navController: NavController,
    viewModel: AgentAccessViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agent_access_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.agent_access_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.error?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                SectionCard {
                    ToggleRow(
                        title = stringResource(R.string.agent_access_enable),
                        subtitle = if (state.clients.isEmpty()) {
                            stringResource(R.string.agent_access_no_tokens)
                        } else {
                            stringResource(R.string.agent_access_enable_desc)
                        },
                        checked = state.enabled,
                        enabled = state.clients.isNotEmpty() || state.enabled,
                        onCheckedChange = viewModel::toggleBridge
                    )
                    if (state.enabled && state.reachableAddress != null) {
                        Spacer(Modifier.height(8.dp))
                        MonoBlock("http://${state.reachableAddress}:${state.port}")
                    }
                }
            }

            // --- tokens ---

            item { SectionHeader(stringResource(R.string.agent_access_tokens)) }

            items(state.clients, key = { it.id }) { client ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(client.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                client.scopes.joinToString(" · ") { it.wire },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                client.lastSeenAt?.let {
                                    stringResource(R.string.agent_access_last_seen, timeFormat.format(Date(it)))
                                } ?: stringResource(R.string.agent_access_never_seen),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.revoke(client.id) }) {
                            Text(stringResource(R.string.agent_access_revoke), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = { showCreate = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.agent_access_new_token)) }
            }

            // --- pairing ---

            item {
                SectionHeader(stringResource(R.string.agent_access_pairing))
                SectionCard {
                    Text(
                        stringResource(R.string.agent_access_pairing_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    MonoBlock(viewModel.pairingCommand())
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(viewModel.pairingCommand()))
                    }) { Text(stringResource(R.string.agent_access_copy)) }
                }
            }

            // --- agent inbox ---

            item { SectionHeader(stringResource(R.string.agent_access_inbox)) }

            if (state.pendingAsks.isEmpty()) {
                item { EmptyNote(stringResource(R.string.agent_access_inbox_empty)) }
            } else {
                items(state.pendingAsks, key = { it.id }) { ask ->
                    SectionCard {
                        Text(ask.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        ask.context?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            stringResource(R.string.agent_ask_asked_by, ask.askedBy),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        val options = when {
                            ask.options.isNotEmpty() -> ask.options
                            else -> listOf(
                                stringResource(R.string.agent_ask_yes).lowercase(),
                                stringResource(R.string.agent_ask_no).lowercase()
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.take(3).forEach { option ->
                                FilledTonalButton(onClick = { viewModel.answerAsk(ask.id, option) }) {
                                    Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // --- network ---

            item {
                SectionHeader(stringResource(R.string.agent_access_network))
                SectionCard {
                    ToggleRow(
                        title = stringResource(R.string.agent_access_allow_lan),
                        subtitle = stringResource(R.string.agent_access_allow_lan_desc),
                        checked = state.allowLan,
                        onCheckedChange = viewModel::setAllowLan
                    )
                    Spacer(Modifier.height(12.dp))
                    var portText by remember(state.port) { mutableStateOf(state.port.toString()) }
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.agent_access_port)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { portText.toIntOrNull()?.let(viewModel::setPort) }) {
                                Text(stringResource(R.string.agent_access_save))
                            }
                        }
                    )
                }
            }

            // --- behaviour ---

            item {
                SectionCard {
                    ToggleRow(
                        title = stringResource(R.string.agent_access_ai),
                        subtitle = stringResource(R.string.agent_access_ai_desc),
                        checked = state.aiProcessingAllowed,
                        onCheckedChange = viewModel::setAiProcessing
                    )
                    Spacer(Modifier.height(12.dp))
                    ToggleRow(
                        title = stringResource(R.string.agent_access_quiet),
                        subtitle = stringResource(R.string.agent_access_quiet_desc),
                        checked = state.quietHoursEnabled,
                        onCheckedChange = { viewModel.setQuietHours(it, state.quietStart, state.quietEnd) }
                    )
                }
            }

            // --- hub ---

            item {
                SectionHeader(stringResource(R.string.agent_access_hub))
                SectionCard {
                    Text(
                        stringResource(R.string.agent_access_hub_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    var url by remember(state.hubUrl) { mutableStateOf(state.hubUrl) }
                    var token by remember(state.hubToken) { mutableStateOf(state.hubToken) }
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.agent_access_hub_url)) },
                        placeholder = { Text("https://hub-xxxx.tailnet.ts.net") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.agent_access_hub_token)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        title = stringResource(R.string.agent_access_mirror),
                        subtitle = null,
                        checked = state.mirrorEnabled,
                        onCheckedChange = { viewModel.setHub(url, token, it) }
                    )
                    TextButton(onClick = { viewModel.setHub(url, token, state.mirrorEnabled) }) {
                        Text(stringResource(R.string.agent_access_save))
                    }
                }
            }

            // --- push ---

            item {
                SectionCard {
                    var topic by remember(state.pushTopic) { mutableStateOf(state.pushTopic) }
                    ToggleRow(
                        title = stringResource(R.string.agent_access_push),
                        subtitle = stringResource(R.string.agent_access_push_desc),
                        checked = state.pushEnabled,
                        onCheckedChange = { viewModel.setPush(it, topic) }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text(stringResource(R.string.agent_access_push_topic)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { viewModel.setPush(state.pushEnabled, topic) }) {
                                Text(stringResource(R.string.agent_access_save))
                            }
                        }
                    )
                }
            }

            // --- audit ---

            item { SectionHeader(stringResource(R.string.agent_access_activity)) }

            if (state.recentActivity.isEmpty()) {
                item { EmptyNote(stringResource(R.string.agent_access_activity_empty)) }
            } else {
                items(state.recentActivity, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            timeFormat.format(Date(entry.at)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${entry.clientName} ${entry.action} ${entry.entityType}${entry.entityId?.let { " #$it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showCreate) {
        CreateTokenDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, scopes ->
                viewModel.createToken(name, scopes)
                showCreate = false
            }
        )
    }

    state.freshToken?.let { token ->
        AlertDialog(
            onDismissRequest = viewModel::dismissFreshToken,
            title = { Text(stringResource(R.string.agent_access_new_token)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.agent_access_token_once),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    MonoBlock(token)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(token))
                    viewModel.dismissFreshToken()
                }) { Text(stringResource(R.string.agent_access_copy)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissFreshToken) {
                    Text(stringResource(R.string.agent_access_done))
                }
            }
        )
    }
}

@Composable
private fun CreateTokenDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Set<AgentScope>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    // media:read is absent from the default set on purpose — granting an agent the ability
    // to pull the operator's actual photos should be a decision, not a default.
    var scopes by remember { mutableStateOf(AgentScope.DEFAULT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agent_access_new_token)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.agent_access_token_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.agent_access_scopes), style = MaterialTheme.typography.labelLarge)
                AgentScope.entries.forEach { scope ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = scope in scopes,
                            onCheckedChange = { checked ->
                                scopes = if (checked) scopes + scope else scopes - scope
                            }
                        )
                        Text(scope.wire, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && scopes.isNotEmpty(),
                onClick = { onCreate(name.trim(), scopes) }
            ) { Text(stringResource(R.string.agent_access_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.agent_access_cancel)) }
        }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun MonoBlock(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun stringResource(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringResource(id: Int, arg: Any): String = androidx.compose.ui.res.stringResource(id, arg)
