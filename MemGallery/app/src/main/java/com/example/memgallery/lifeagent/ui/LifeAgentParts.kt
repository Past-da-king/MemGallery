package com.example.memgallery.lifeagent.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memgallery.R
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.theme.memColors

/** Colours, shape, icon and names of one source app, from the app's own tokens (no new colours). */
data class LifeAppStyle(
    val container: Color,
    val onContainer: Color,
    val shape: Shape,
    val icon: ImageVector,
    @StringRes val label: Int,
    @StringRes val shortLabel: Int,
)

@Composable
fun LifeAppId?.style(): LifeAppStyle {
    val scheme = MaterialTheme.colorScheme
    val mem = MaterialTheme.memColors
    return when (this) {
        LifeAppId.GMAIL -> LifeAppStyle(scheme.tertiaryContainer, scheme.onTertiaryContainer, MemShape.softBurst, Icons.Rounded.Mail, R.string.la_app_gmail, R.string.la_app_gmail)
        LifeAppId.GCAL -> LifeAppStyle(mem.skyContainer, mem.onSkyContainer, MemShape.clover, Icons.Rounded.CalendarMonth, R.string.la_app_gcal, R.string.la_app_gcal_short)
        LifeAppId.CLICKUP -> LifeAppStyle(scheme.primaryContainer, scheme.onPrimaryContainer, MemShape.cookie, Icons.Rounded.TaskAlt, R.string.la_app_clickup, R.string.la_app_clickup)
        LifeAppId.SLACK -> LifeAppStyle(mem.successContainer, mem.onSuccessContainer, MemShape.flower, Icons.Rounded.Forum, R.string.la_app_slack, R.string.la_app_slack)
        null -> LifeAppStyle(scheme.secondaryContainer, scheme.onSecondaryContainer, MemShape.pill, Icons.Rounded.TaskAlt, R.string.la_app_other, R.string.la_app_other)
    }
}

/** The shaped tile that carries an app's icon (Gmail soft burst, Calendar clover, ClickUp cookie, Slack flower). */
@Composable
fun AppBadge(app: LifeAppId?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val s = app.style()
    Box(modifier.size(size).clip(s.shape).background(s.container), contentAlignment = Alignment.Center) {
        Icon(s.icon, null, tint = s.onContainer, modifier = Modifier.size(size * 0.5f))
    }
}

/** Small source chip: app icon and short name on the app's tonal colour. */
@Composable
fun AppChip(app: LifeAppId?, modifier: Modifier = Modifier, fallbackLabel: String? = null) {
    val s = app.style()
    Row(
        modifier.height(28.dp).clip(RoundedCornerShape(10.dp)).background(s.container).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(s.icon, null, tint = s.onContainer, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            fallbackLabel ?: stringResource(s.shortLabel),
            style = MaterialTheme.typography.labelLarge, color = s.onContainer, maxLines = 1
        )
    }
}

@Composable
fun appLabel(app: LifeAppId?, fallback: String): String =
    if (app == null) fallback.ifBlank { stringResource(R.string.la_app_other) } else stringResource(app.style().label)

/** The one-line banner every connection surface carries: this agent only reads. */
@Composable
fun ReadsOnlyBanner(modifier: Modifier = Modifier, title: String = stringResource(R.string.la_readonly_title), body: String = stringResource(R.string.la_readonly_body)) {
    val mem = MaterialTheme.memColors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(mem.successContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(MemShape.softBurst).background(mem.success), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Visibility, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = mem.onSuccessContainer)
            if (body.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = mem.onSuccessContainer)
            }
        }
    }
}

/** Section label with a count pill: "DUE TODAY  3". */
@Composable
fun GroupLabel(title: String, count: Int?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
            color = scheme.onSurfaceVariant
        )
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(scheme.secondaryContainer).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer) }
        }
    }
}

/** A grouped-list row surface: tonal container with the app's 20dp radius, used with 4dp gaps. */
@Composable
fun RowCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(container)
    Box(if (onClick != null) base.clickable(onClick = onClick) else base) { content() }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun OneLine(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Vertical gap between grouped rows. */
val RowGap: Arrangement.HorizontalOrVertical = Arrangement.spacedBy(4.dp)
