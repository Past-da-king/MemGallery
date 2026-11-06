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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memgallery.R
import com.example.memgallery.ui.design.BlobHeader
import com.example.memgallery.ui.design.EmptyState
import com.example.memgallery.ui.design.HeaderBackButton
import com.example.memgallery.ui.design.appNavigationBarsPadding

class PersonActions(
    val onBack: () -> Unit = {},
    val onAsk: (String) -> Unit = {},
    val onTick: (taskId: String, done: Boolean) -> Unit = { _, _ -> },
    val onSnooze: (taskId: String, SnoozeChoice) -> Unit = { _, _ -> },
    val onOpenMemory: (String) -> Unit = {},
)

/** One person: header with a shaped avatar, open commitments both ways, recent memories, details, Ask about. */
@Composable
fun PersonContent(
    state: PersonUiState,
    actions: PersonActions,
    modifier: Modifier = Modifier,
    initialSnoozeTaskId: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var snoozeFor by remember { mutableStateOf(initialSnoozeTaskId) }
    val clipboard = LocalClipboardManager.current

    if (!state.found) {
        Column(modifier.fillMaxSize().background(scheme.surface)) {
            Row(Modifier.padding(start = 8.dp, top = 8.dp)) { HeaderBackButton(actions.onBack) }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.la_person_missing_title),
                    body = stringResource(R.string.la_person_missing_body),
                    artScale = 0.8f,
                )
            }
        }
        return
    }

    Box(modifier.fillMaxSize().background(scheme.surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BlobHeader {
                Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeaderBackButton(actions.onBack)
                        Text(
                            stringResource(R.string.la_person_eyebrow).uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
                            color = scheme.onPrimaryContainer.copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    Row(Modifier.padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(state.name, size = 112.dp, fontSize = 40.sp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.name, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onPrimaryContainer)
                            if (state.role.isNotBlank()) Text(state.role, style = MaterialTheme.typography.titleLarge, color = scheme.onPrimaryContainer)
                            Text(stringResource(R.string.la_person_last, state.lastLabel), style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.8f))
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.youOwe > 0) CountChip(Icons.Rounded.Flag, stringResource(R.string.la_person_you_owe_n, state.youOwe), scheme.tertiaryContainer, scheme.onTertiaryContainer)
                                if (state.waiting > 0) CountChip(Icons.Rounded.HourglassTop, stringResource(R.string.la_person_waiting_n, state.waiting), scheme.secondaryContainer, scheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            GroupLabel(stringResource(R.string.la_person_commitments), state.commitments.size)
            Spacer(Modifier.height(8.dp))
            if (state.commitments.isEmpty()) {
                Text(stringResource(R.string.la_person_no_commitments), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = RowGap) {
                state.commitments.forEach { c ->
                    CommitmentRow(c, onTick = { c.taskId?.let { id -> actions.onTick(id, !c.done) } }, onSnooze = { snoozeFor = c.taskId })
                }
            }

            Spacer(Modifier.height(16.dp))
            GroupLabel(stringResource(R.string.la_person_memories), state.memories.size)
            Spacer(Modifier.height(8.dp))
            if (state.memories.isEmpty()) {
                Text(stringResource(R.string.la_person_no_memories), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = RowGap) {
                state.memories.forEach { m ->
                    RowCard(onClick = { actions.onOpenMemory(m.key) }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(m.title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppChip(m.app, fallbackLabel = m.appLabel.takeIf { m.app == null })
                                Spacer(Modifier.width(10.dp))
                                Text(m.dateLabel, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (state.email != null || state.phone != null) {
                Spacer(Modifier.height(16.dp))
                GroupLabel(stringResource(R.string.la_person_details), null)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = RowGap) {
                    state.email?.let { DetailRow(Icons.Rounded.Email, it, stringResource(R.string.la_person_email_sub)) { clipboard.setText(AnnotatedString(it)) } }
                    state.phone?.let { DetailRow(Icons.Rounded.Phone, it, stringResource(R.string.la_person_phone_sub)) { clipboard.setText(AnnotatedString(it)) } }
                }
            }
            Spacer(Modifier.height(120.dp))
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).appNavigationBarsPadding()) {
            Button(
                onClick = { actions.onAsk(state.name) },
                modifier = Modifier.fillMaxWidth().height(64.dp).shadow(6.dp, RoundedCornerShape(32.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer)
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.la_person_ask, state.name.substringBefore(' ')), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }

    snoozeFor?.let { id ->
        val title = state.commitments.firstOrNull { it.taskId == id }?.title.orEmpty()
        SnoozeSheet(title, onPick = { actions.onSnooze(id, it); snoozeFor = null }, onDismiss = { snoozeFor = null })
    }
}

@Composable
private fun CountChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun CommitmentRow(c: CommitmentUi, onTick: () -> Unit, onSnooze: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RowCard {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (c.kind == CommitmentKind.YouOwe && c.taskId != null) {
                TickBox(done = c.done, overdue = false, onToggle = onTick)
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.HourglassTop, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(commitmentLabel(c.kind), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp), color = commitmentColor(c.kind))
                Text(
                    c.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface,
                    textDecoration = if (c.done) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppChip(c.app)
                    if (c.dateLabel != null) {
                        Spacer(Modifier.width(10.dp))
                        Text(c.dateLabel, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (c.taskId != null && !c.done) {
                IconButton(onClick = onSnooze, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Alarm, stringResource(R.string.la_snooze), tint = scheme.onSurfaceVariant)
                }
            } else if (c.kind == CommitmentKind.Waiting) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, sub: String, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RowCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = scheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ContentCopy, stringResource(R.string.la_copy), tint = scheme.onSurfaceVariant)
            }
        }
    }
}
