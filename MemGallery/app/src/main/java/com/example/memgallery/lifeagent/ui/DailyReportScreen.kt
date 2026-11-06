package com.example.memgallery.lifeagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.TaskAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memgallery.R
import com.example.memgallery.ui.design.BlobHeader
import com.example.memgallery.ui.design.EmptyState
import com.example.memgallery.ui.design.HeaderBackButton
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.RoundIconButton
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.theme.memColors

class DailyReportActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onReview: () -> Unit = {},
    val onPeople: () -> Unit = {},
    val onOpenConnections: () -> Unit = {},
    val onTick: (taskId: String, done: Boolean) -> Unit = { _, _ -> },
    val onSnooze: (taskId: String, SnoozeChoice) -> Unit = { _, _ -> },
    val onOpenUrl: (String) -> Unit = {},
)

/** Today's report: aware, due today, overdue, meetings and still open, each row with its source and an action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyReportContent(
    state: DailyReportUiState,
    actions: DailyReportActions,
    modifier: Modifier = Modifier,
    initialSnoozeTaskId: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var snoozeFor by remember { mutableStateOf(initialSnoozeTaskId) }

    Column(modifier.fillMaxSize().background(scheme.surface).verticalScroll(rememberScrollState())) {
        BlobHeader {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    HeaderBackButton(actions.onBack, Modifier.padding(top = 6.dp))
                    Column(Modifier.weight(1f).padding(start = 4.dp, top = 6.dp)) {
                        Text(
                            state.dateLabel.uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
                            color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(stringResource(R.string.la_report_title), style = MaterialTheme.typography.displayMedium, color = scheme.onPrimaryContainer)
                    }
                    RoundIconButton(Icons.Rounded.Groups, stringResource(R.string.la_people_title), actions.onPeople, Modifier.padding(top = 6.dp))
                    RoundIconButton(Icons.Rounded.Refresh, stringResource(R.string.la_report_refresh), actions.onRefresh, Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.collectedLabel != null) HeaderChip(Icons.Rounded.Sync, stringResource(R.string.la_report_collected, state.collectedLabel), scheme.onPrimaryContainer.copy(alpha = 0.12f), scheme.onPrimaryContainer)
                    if (state.dueToday.isNotEmpty()) HeaderChip(null, stringResource(R.string.la_report_due_n, state.dueToday.size), scheme.secondaryContainer, scheme.onSecondaryContainer)
                    if (state.overdue.isNotEmpty()) HeaderChip(null, stringResource(R.string.la_report_overdue_n, state.overdue.size), scheme.errorContainer, scheme.onErrorContainer)
                }
            }
        }

        if (!state.anyConnected) {
            EmptyState(
                icon = Icons.Rounded.Link,
                title = stringResource(R.string.la_report_noapps_title),
                body = stringResource(R.string.la_report_noapps_body),
                primaryLabel = stringResource(R.string.la_collect_open_connections),
                onPrimary = actions.onOpenConnections,
                modifier = Modifier.padding(top = 24.dp),
                artScale = 0.8f,
            )
            Spacer(Modifier.height(32.dp))
            return@Column
        }

        if (state.toReview > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.primaryContainer)
                    .clickable(onClick = actions.onReview).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.DoneAll, null, tint = scheme.onPrimaryContainer)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.la_report_review_n, state.toReview), style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = scheme.onPrimaryContainer)
            }
        }

        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Rounded.TaskAlt,
                title = stringResource(R.string.la_report_empty_title),
                body = stringResource(R.string.la_report_empty_body),
                modifier = Modifier.padding(top = 24.dp),
                artScale = 0.8f,
            )
            Spacer(Modifier.height(32.dp))
            return@Column
        }

        if (state.aware.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GroupLabel(stringResource(R.string.la_report_aware), state.aware.size)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = RowGap) {
                state.aware.forEach { AwareRow(it) { url -> actions.onOpenUrl(url) } }
            }
        }
        if (state.dueToday.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GroupLabel(stringResource(R.string.la_report_due), state.dueToday.size)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = RowGap) {
                state.dueToday.forEach { t -> TaskRow(t, { actions.onTick(t.taskId, !t.done) }, { snoozeFor = t.taskId }) }
            }
        }
        if (state.overdue.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GroupLabel(stringResource(R.string.la_report_overdue), state.overdue.size)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = RowGap) {
                state.overdue.forEach { t -> TaskRow(t, { actions.onTick(t.taskId, !t.done) }, { snoozeFor = t.taskId }) }
            }
        }
        if (state.meetings.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GroupLabel(stringResource(R.string.la_report_meetings), state.meetings.size)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = RowGap) {
                state.meetings.forEach { m -> MeetingRow(m) { url -> actions.onOpenUrl(url) } }
            }
        }
        if (state.open.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GroupLabel(stringResource(R.string.la_report_open), state.open.size)
            Spacer(Modifier.height(8.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.open.forEach { o ->
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, scheme.outline, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(o.title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Spacer(Modifier.appNavigationBarsPadding())
    }

    snoozeFor?.let { id ->
        val title = (state.dueToday + state.overdue).firstOrNull { it.taskId == id }?.title.orEmpty()
        SnoozeSheet(title, onPick = { actions.onSnooze(id, it); snoozeFor = null }, onDismiss = { snoozeFor = null })
    }
}

@Composable
private fun HeaderChip(icon: ImageVector?, text: String, bg: Color, fg: Color) {
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun awareStyle(kind: AwareKind): Triple<ImageVector, Shape, Pair<Color, Color>> {
    val scheme = MaterialTheme.colorScheme
    val mem = MaterialTheme.memColors
    return when (kind) {
        AwareKind.Deadline -> Triple(Icons.Rounded.Schedule, MemShape.softBurst, scheme.tertiaryContainer to scheme.onTertiaryContainer)
        AwareKind.Money -> Triple(Icons.Rounded.AttachMoney, MemShape.sunny, mem.amberContainer to mem.onAmberContainer)
        AwareKind.Meeting -> Triple(Icons.Rounded.Event, MemShape.clover, mem.skyContainer to mem.onSkyContainer)
        AwareKind.Security -> Triple(Icons.Rounded.Shield, MemShape.gem, mem.successContainer to mem.onSuccessContainer)
        AwareKind.Person -> Triple(Icons.Rounded.Person, MemShape.cookie, scheme.primaryContainer to scheme.onPrimaryContainer)
        AwareKind.Awaiting -> Triple(Icons.Rounded.HourglassTop, MemShape.puffy, scheme.secondaryContainer to scheme.onSecondaryContainer)
        AwareKind.Other -> Triple(Icons.Rounded.TaskAlt, MemShape.pill, scheme.secondaryContainer to scheme.onSecondaryContainer)
    }
}

@Composable
private fun AwareRow(r: AwareRowUi, onOpen: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, shape, colors) = awareStyle(r.kind)
    RowCard(onClick = r.url?.let { u -> { onOpen(u) } }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(shape).background(colors.first), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = colors.second, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppChip(LifeAppId.from(r.app?.id), fallbackLabel = r.appLabel.takeIf { r.app == null })
                    if (r.sub.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Text(r.sub, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (r.url != null) Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.la_open), tint = scheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

@Composable
private fun TaskRow(t: ReportTaskUi, onTick: () -> Unit, onSnooze: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RowCard(container = if (t.overdue && !t.done) scheme.errorContainer.copy(alpha = 0.55f) else scheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TickBox(done = t.done, overdue = t.overdue, onToggle = onTick)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (t.done) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppChip(t.app)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        t.whenLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (t.overdue) FontWeight.Bold else FontWeight.Normal),
                        color = if (t.overdue && !t.done) scheme.error else scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!t.done) IconButton(onClick = onSnooze, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Alarm, stringResource(R.string.la_snooze), tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MeetingRow(m: ReportMeetingUi, onOpen: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RowCard(onClick = m.joinUrl?.takeIf { !m.ended }?.let { u -> { onOpen(u) } }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp)) {
                Text(m.timeLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (m.ended) scheme.onSurfaceVariant else scheme.onSurface)
                if (m.ended) Text(stringResource(R.string.la_report_ended), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppChip(m.app)
                    if (m.sub.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Text(m.sub, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (m.ended) {
                Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
