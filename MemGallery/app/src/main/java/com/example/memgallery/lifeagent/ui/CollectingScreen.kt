package com.example.memgallery.lifeagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memgallery.R
import com.example.memgallery.ui.design.BlobHeader
import com.example.memgallery.ui.design.ConfirmSheet
import com.example.memgallery.ui.design.EmptyState
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.theme.memColors

class CollectingActions(
    val onBack: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onBackground: () -> Unit = {},
    val onCollectNow: () -> Unit = {},
    val onReview: () -> Unit = {},
    val onOpenConnections: () -> Unit = {},
)

/** The daily collection in progress: per-app found and dropped, a step counter, cancel and keep running in background. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectingContent(
    state: CollectingUiState,
    actions: CollectingActions,
    modifier: Modifier = Modifier,
    initialConfirmCancel: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    var confirmCancel by remember { mutableStateOf(initialConfirmCancel) }
    val idle = !state.running && !state.finished

    Column(modifier.fillMaxSize().background(scheme.surface)) {
        BlobHeader {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 20.dp)) {
                Text(
                    stringResource(R.string.la_collect_eyebrow).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    stringResource(if (state.finished) R.string.la_collect_title_done else R.string.la_collect_title),
                    style = MaterialTheme.typography.displayMedium, color = scheme.onPrimaryContainer
                )
                if (!idle) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.finished) stringResource(R.string.la_collect_finished_line) else stringResource(R.string.la_collect_step, state.step, state.steps),
                        style = MaterialTheme.typography.headlineMedium, color = scheme.onPrimaryContainer
                    )
                    val running = state.apps.firstOrNull { it.state == StepState.Running }
                    val line = when {
                        state.finished -> stringResource(R.string.la_collect_finished_sub, state.proposalsCreated)
                        running != null -> stringResource(readingLine(running.app), state.startedLabel)
                        else -> stringResource(R.string.la_collect_starting, state.startedLabel)
                    }
                    Text(line, style = MaterialTheme.typography.bodyLarge, color = scheme.onPrimaryContainer)
                    Spacer(Modifier.height(12.dp))
                    val fraction = if (state.steps == 0) 0f else (if (state.finished) 1f else ((state.step - 1).coerceAtLeast(0) + 0.5f) / state.steps).coerceIn(0f, 1f)
                    if (state.finished) {
                        LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth().height(8.dp), color = scheme.primary, trackColor = scheme.primary.copy(alpha = 0.2f))
                    } else {
                        LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.primary.copy(alpha = 0.2f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TotalChip(Icons.Rounded.Inbox, stringResource(R.string.la_collect_found, state.found), scheme.secondaryContainer, scheme.onSecondaryContainer)
                        TotalChip(Icons.Rounded.FilterAlt, stringResource(R.string.la_collect_noise, state.dropped), scheme.tertiaryContainer, scheme.onTertiaryContainer)
                    }
                }
            }
        }

        if (idle) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.apps.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Sync,
                        title = stringResource(R.string.la_collect_idle_noapps_title),
                        body = stringResource(R.string.la_collect_idle_noapps_body),
                        primaryLabel = stringResource(R.string.la_collect_open_connections),
                        onPrimary = actions.onOpenConnections,
                        artScale = 0.8f,
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.Sync,
                        title = stringResource(R.string.la_collect_idle_title),
                        body = stringResource(R.string.la_collect_idle_body),
                        primaryLabel = stringResource(R.string.la_collect_now),
                        onPrimary = actions.onCollectNow,
                        artScale = 0.8f,
                    )
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.apps.forEach { AppStepCard(it) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).appNavigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.finished) {
                    OutlinedButton(onClick = actions.onBack, modifier = Modifier.height(56.dp)) {
                        Text(stringResource(R.string.la_close), style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = actions.onReview, enabled = state.proposalsCreated > 0, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Rounded.DoneAll, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.la_collect_review, state.proposalsCreated), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.height(56.dp)) {
                        Text(stringResource(R.string.la_cancel), style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = actions.onBackground, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(stringResource(R.string.la_collect_background), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        ConfirmSheet(
            title = stringResource(R.string.la_collect_cancel_title),
            body = stringResource(R.string.la_collect_cancel_body),
            confirmLabel = stringResource(R.string.la_collect_cancel_yes),
            dismissLabel = stringResource(R.string.la_collect_cancel_no),
            onConfirm = { confirmCancel = false; actions.onCancel() },
            onDismiss = { confirmCancel = false },
            destructive = true,
        )
    }
}

private fun readingLine(app: LifeAppId): Int = when (app) {
    LifeAppId.GMAIL -> R.string.la_reading_gmail
    LifeAppId.GCAL -> R.string.la_reading_gcal
    LifeAppId.CLICKUP -> R.string.la_reading_clickup
    LifeAppId.SLACK -> R.string.la_reading_slack
}

@Composable
private fun TotalChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(container).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

@Composable
private fun StepMark(state: StepState) {
    val scheme = MaterialTheme.colorScheme
    val mem = MaterialTheme.memColors
    val (bg, fg, icon) = when (state) {
        StepState.Done -> Triple(mem.successContainer, mem.onSuccessContainer, Icons.Rounded.Check)
        StepState.Running -> Triple(scheme.primary, scheme.onPrimary, Icons.Rounded.Sync)
        StepState.Failed -> Triple(scheme.errorContainer, scheme.onErrorContainer, Icons.Rounded.ErrorOutline)
        StepState.Skipped -> Triple(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, Icons.Rounded.Remove)
        StepState.Queued -> Triple(scheme.surface, scheme.outline, null)
    }
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(bg).then(if (state == StepState.Queued) Modifier.border(2.dp, scheme.outline, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) { if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun AppStepCard(a: CollectAppUi) {
    val scheme = MaterialTheme.colorScheme
    val mem = MaterialTheme.memColors
    val running = a.state == StepState.Running
    val container = when (a.state) {
        StepState.Done -> scheme.surfaceContainer
        StepState.Running -> scheme.primaryContainer
        StepState.Failed -> scheme.errorContainer.copy(alpha = 0.5f)
        else -> scheme.surface
    }
    val outlined = a.state == StepState.Queued || a.state == StepState.Skipped
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(container)
            .then(if (outlined) Modifier.border(BorderStroke(1.dp, scheme.outlineVariant), RoundedCornerShape(24.dp)) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        StepMark(a.state)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBadge(a.app, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(appLabel(a.app, ""), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface, modifier = Modifier.weight(1f))
                val (tag, tagColor) = when (a.state) {
                    StepState.Done -> stringResource(R.string.la_collect_found_new, a.found) to mem.success
                    StepState.Running -> stringResource(R.string.la_collect_pulling) to scheme.primary
                    StepState.Queued -> stringResource(R.string.la_collect_upnext) to scheme.onSurfaceVariant
                    StepState.Skipped -> stringResource(R.string.la_collect_skipped) to scheme.onSurfaceVariant
                    StepState.Failed -> stringResource(R.string.la_collect_failed) to scheme.error
                }
                Text(tag, style = MaterialTheme.typography.titleSmall, color = tagColor)
            }
            when (a.state) {
                StepState.Done -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.la_collect_dropped_n, a.dropped), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant
                    )
                    a.reasons.forEach { r ->
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.RemoveCircleOutline, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(r, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                StepState.Running -> {
                    Spacer(Modifier.height(8.dp))
                    if (a.readTotal > 0) {
                        Text(stringResource(R.string.la_collect_read_of, a.readSoFar, a.readTotal), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { a.readSoFar.toFloat() / a.readTotal }, modifier = Modifier.fillMaxWidth().height(6.dp), color = scheme.primary, trackColor = scheme.primary.copy(alpha = 0.2f))
                    }
                    if (!a.note.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(a.note, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                StepState.Failed -> if (!a.note.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(a.note, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                }
                else -> Unit
            }
        }
    }
}
