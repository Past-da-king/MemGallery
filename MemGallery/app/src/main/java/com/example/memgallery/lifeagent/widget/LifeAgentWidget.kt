package com.example.memgallery.lifeagent.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.memgallery.R
import com.example.memgallery.ui.widget.WidgetPalette
import com.example.memgallery.ui.widget.resolvePalette

/**
 * The Life Agent widget: 2x2, 4x2 and 4x4, all interactive and all read only. Ticking done and snoozing touch the
 * app's own task table; keeping or dropping a proposal decides a proposed memory. Nothing is ever sent to Gmail,
 * Calendar, ClickUp or Slack.
 */
class LifeAgentWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(150.dp, 150.dp),
            DpSize(250.dp, 150.dp),
            DpSize(320.dp, 150.dp),
            DpSize(150.dp, 320.dp),
            DpSize(320.dp, 320.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = LifeAgentWidgetData.load(context)
        updateAppWidgetState(context, id) { prefs -> prefs[SnapshotKey] = snapshot.toJson() }
        provideContent { LifeAgentWidgetContent() }
    }

    private companion object {
        val SnapshotKey = LifeSnapshotKey
    }
}

internal val LifeSnapshotKey = stringPreferencesKey("life_agent_snapshot")

/** The whole widget drawing, separate from provideGlance so a Glance unit test can drive it with seeded state and a size. */
@Composable
internal fun LifeAgentWidgetContent() {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val snap = LifeWidgetSnapshot.fromJson(currentState(LifeSnapshotKey))
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val palette = resolvePalette(
        context = ctx,
        themeMode = snap.themeMode,
        amoledMode = snap.amoled,
        customColor = snap.accent,
        dynamicTheming = snap.dynamic,
        isSystemDark = dark,
    )
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(26.dp)
            .background(palette.background)
            .padding(14.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        when {
            size.width < 220.dp && size.height < 220.dp -> SmallBody(ctx, snap, palette)
            size.height < 220.dp -> WideBody(ctx, snap, palette)
            else -> LargeBody(ctx, snap, palette)
        }
    }
}

internal val LifeDangerLight = androidx.compose.ui.graphics.Color(0xFFB3261E)
internal val LifeDangerDark = androidx.compose.ui.graphics.Color(0xFFFFB4AB)

// ---- shared pieces ----

private fun WidgetPalette.isDark(): Boolean = background.luminance() < 0.45f

private fun WidgetPalette.danger(): androidx.compose.ui.graphics.Color = if (isDark()) LifeDangerDark else LifeDangerLight

private fun WidgetPalette.chip(): androidx.compose.ui.graphics.Color = accent.copy(alpha = if (isDark()) 0.22f else 0.14f)

private fun WidgetPalette.line(): androidx.compose.ui.graphics.Color =
    onSurfaceMuted.copy(alpha = if (isDark()) 0.22f else 0.16f)

/** "3 due today · 1 late" in the accent colour, or the calm line when nothing is due. */
@Composable
private fun CountsLine(ctx: Context, snap: LifeWidgetSnapshot, palette: WidgetPalette, small: Boolean = false) {
    val text = when {
        snap.overdue > 0 && snap.dueToday > 0 ->
            ctx.getString(R.string.la_widget_counts_both, snap.dueToday, snap.overdue)
        snap.overdue > 0 -> ctx.getString(R.string.la_widget_counts_late, snap.overdue)
        snap.dueToday > 0 -> ctx.getString(R.string.la_widget_counts_due, snap.dueToday)
        snap.toReview > 0 -> ctx.getString(R.string.la_widget_counts_review, snap.toReview)
        else -> ctx.getString(R.string.la_widget_counts_clear)
    }
    val tint = if (snap.overdue > 0) palette.danger() else palette.accent
    Text(
        text = text,
        style = TextStyle(color = ColorProvider(tint), fontSize = if (small) 11.sp else 12.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

/** A round tap target sized for a home screen. */
@Composable
private fun Chit(
    icon: Int,
    action: Action,
    palette: WidgetPalette,
    description: String,
    diameter: Dp = 32.dp,
    filled: Boolean = true,
) {
    Box(
        GlanceModifier
            .size(diameter)
            .cornerRadius(diameter * 0.5f)
            .background(if (filled) palette.chip() else palette.background.copy(alpha = 0f))
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            modifier = GlanceModifier.size(diameter * 0.5f),
            colorFilter = ColorFilter.tint(ColorProvider(if (filled) palette.accent else palette.onSurfaceMuted)),
        )
    }
}

/** Ask: the one action on every size. Opens the chat with this item as the question. */
@Composable
private fun AskPill(ctx: Context, palette: WidgetPalette, item: LifeWidgetItem?, compact: Boolean = false) {
    val params = actionParametersOf(
        LifeWidgetKeys.itemTitle to (item?.title ?: ""),
        LifeWidgetKeys.itemId to (item?.id ?: ""),
    )
    if (compact) {
        Chit(R.drawable.ic_la_ask, actionRunCallback<LifeWidgetAskAction>(params), palette, ctx.getString(R.string.la_widget_ask))
    } else {
        Row(
            GlanceModifier
                .cornerRadius(16.dp)
                .background(palette.chip())
                .clickable(actionRunCallback<LifeWidgetAskAction>(params))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_la_ask),
                contentDescription = ctx.getString(R.string.la_widget_ask),
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(ColorProvider(palette.accent)),
            )
            Spacer(GlanceModifier.width(5.dp))
            Text(
                ctx.getString(R.string.la_widget_ask),
                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** One task or meeting row: icon, title, when, and the tick when it is a task we may close locally. */
@Composable
private fun ItemRow(
    ctx: Context,
    item: LifeWidgetItem,
    palette: WidgetPalette,
    showTick: Boolean,
    height: Dp? = null,
    showWhen: Boolean = true,
) {
    val openParams = actionParametersOf(
        LifeWidgetKeys.route to LifeRouteForItem(item),
        LifeWidgetKeys.itemId to item.id,
    )
    val tint = if (item.isOverdue) palette.danger() else palette.onSurfaceMuted
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .then(if (height != null) GlanceModifier.height(height) else GlanceModifier)
            .cornerRadius(12.dp)
            .clickable(actionRunCallback<LifeWidgetOpenAction>(openParams))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconFor(item, palette, tint)
        Spacer(GlanceModifier.width(9.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                item.title,
                style = TextStyle(color = ColorProvider(palette.onSurface), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            if (showWhen && item.whenLabel.isNotBlank()) {
                Text(
                    item.whenLabel,
                    style = TextStyle(color = ColorProvider(tint), fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
        if (showTick && item.isTask) {
            Spacer(GlanceModifier.width(6.dp))
            Chit(
                icon = R.drawable.ic_la_check,
                action = actionRunCallback<LifeWidgetDoneAction>(actionParametersOf(LifeWidgetKeys.taskId to item.id)),
                palette = palette,
                description = ctx.getString(R.string.la_widget_done),
                diameter = 28.dp,
            )
        }
    }
}

private fun LifeRouteForItem(item: LifeWidgetItem): String =
    if (item.kind == "proposal") "life_review" else "life_report"

@Composable
private fun IconFor(item: LifeWidgetItem, palette: WidgetPalette, tint: androidx.compose.ui.graphics.Color) {
    val icon = when (item.icon) {
        "video" -> R.drawable.ic_la_video
        "mail" -> R.drawable.ic_la_mail
        "money" -> R.drawable.ic_la_money
        "person" -> R.drawable.ic_la_person
        "security" -> R.drawable.ic_la_security
        "document" -> R.drawable.ic_la_document
        else -> R.drawable.ic_la_clock
    }
    Image(
        provider = ImageProvider(icon),
        contentDescription = null,
        modifier = GlanceModifier.size(15.dp),
        colorFilter = ColorFilter.tint(ColorProvider(tint)),
    )
}

@Composable
private fun SectionHeader(ctx: Context, label: String, count: Int, palette: WidgetPalette) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            style = TextStyle(color = ColorProvider(palette.onSurfaceMuted), fontSize = 10.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.width(5.dp))
        Text(
            count.toString(),
            style = TextStyle(color = ColorProvider(palette.onSurfaceMuted.copy(alpha = 0.7f)), fontSize = 10.sp),
        )
        Spacer(GlanceModifier.defaultWeight())
    }
}

/** A small labelled pill: Keep, Drop, Snooze. */
@Composable
private fun Pill(label: String, action: Action, palette: WidgetPalette, accent: Boolean = true, icon: Int? = null) {
    val fg = if (accent) palette.accent else palette.onSurfaceMuted
    Row(
        GlanceModifier
            .cornerRadius(14.dp)
            .background(if (accent) palette.chip() else palette.line().copy(alpha = 0.5f))
            .clickable(action)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                modifier = GlanceModifier.size(12.dp),
                colorFilter = ColorFilter.tint(ColorProvider(fg)),
            )
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(label, style = TextStyle(color = ColorProvider(fg), fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
}

/** 2x2: the one thing that needs him now, with the tick and Ask. */
@Composable
private fun SmallBody(ctx: Context, snap: LifeWidgetSnapshot, palette: WidgetPalette) {
    val top = snap.rows.firstOrNull()
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CountsLine(ctx, snap, palette, small = true)
            Spacer(GlanceModifier.defaultWeight())
            AskPill(ctx, palette, top, compact = true)
        }
        Spacer(GlanceModifier.defaultWeight())
        if (top == null) {
            Text(
                ctx.getString(R.string.la_widget_nothing),
                style = TextStyle(color = ColorProvider(palette.onSurface), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                ctx.getString(R.string.la_widget_nothing_sub),
                style = TextStyle(color = ColorProvider(palette.onSurfaceMuted), fontSize = 11.sp),
                maxLines = 2,
            )
        } else {
            Text(
                top.title,
                style = TextStyle(color = ColorProvider(palette.onSurface), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    top.whenLabel,
                    style = TextStyle(
                        color = ColorProvider(if (top.isOverdue) palette.danger() else palette.onSurfaceMuted),
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                if (top.isTask) {
                    Chit(
                        icon = R.drawable.ic_la_check,
                        action = actionRunCallback<LifeWidgetDoneAction>(actionParametersOf(LifeWidgetKeys.taskId to top.id)),
                        palette = palette,
                        description = ctx.getString(R.string.la_widget_done),
                        diameter = 30.dp,
                    )
                }
            }
        }
    }
}

/** 4x2: a left rail (date, counts, next meeting, refresh, Ask) and three tap rows. */
@Composable
private fun WideBody(ctx: Context, snap: LifeWidgetSnapshot, palette: WidgetPalette) {
    val rows = snap.rows.take(3)
    Row(GlanceModifier.fillMaxSize()) {
        Column(GlanceModifier.width(94.dp).fillMaxHeight()) {
            Text(
                snap.dateLabel,
                style = TextStyle(color = ColorProvider(palette.onSurface), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            CountsLine(ctx, snap, palette, small = true)
            Spacer(GlanceModifier.defaultWeight())
            snap.nextMeeting?.let { meeting ->
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_la_video),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(palette.onSurfaceMuted)),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        meeting.whenLabel,
                        style = TextStyle(color = ColorProvider(palette.onSurfaceMuted), fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chit(
                    icon = R.drawable.ic_la_refresh,
                    action = actionRunCallback<LifeWidgetRefreshAction>(),
                    palette = palette,
                    description = ctx.getString(R.string.la_widget_refresh),
                    diameter = 26.dp,
                )
                Spacer(GlanceModifier.width(6.dp))
                AskPill(ctx, palette, rows.firstOrNull(), compact = true)
            }
        }
        Spacer(GlanceModifier.width(1.dp).fillMaxHeight().background(palette.line()))
        Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 10.dp)) {
            if (rows.isEmpty()) {
                Text(
                    ctx.getString(R.string.la_widget_nothing),
                    style = TextStyle(color = ColorProvider(palette.onSurfaceMuted), fontSize = 12.sp),
                    maxLines = 2,
                )
            }
            rows.forEach { item ->
                ItemRow(ctx, item, palette, showTick = true, height = 40.dp)
            }
        }
    }
}

/** 4x4: sections (late, today, meetings, open) and the proposed memory with Keep and Drop. */
@Composable
private fun LargeBody(ctx: Context, snap: LifeWidgetSnapshot, palette: WidgetPalette) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    snap.dateLabel,
                    style = TextStyle(color = ColorProvider(palette.onSurface), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                CountsLine(ctx, snap, palette, small = true)
            }
            Chit(
                icon = R.drawable.ic_la_refresh,
                action = actionRunCallback<LifeWidgetRefreshAction>(),
                palette = palette,
                description = ctx.getString(R.string.la_widget_refresh),
                diameter = 26.dp,
            )
            Spacer(GlanceModifier.width(6.dp))
            AskPill(ctx, palette, snap.rows.firstOrNull(), compact = true)
        }
        Spacer(GlanceModifier.height(4.dp))
        LazyColumn(GlanceModifier.fillMaxSize()) {
            if (snap.overdueItems.isNotEmpty()) {
                item { SectionHeader(ctx, ctx.getString(R.string.la_widget_section_late), snap.overdue, palette) }
                items(snap.overdueItems) { item -> ItemRow(ctx, item, palette, showTick = true, height = 40.dp) }
            }
            if (snap.dueItems.isNotEmpty()) {
                item { SectionHeader(ctx, ctx.getString(R.string.la_widget_section_today), snap.dueToday, palette) }
                items(snap.dueItems) { item -> ItemRow(ctx, item, palette, showTick = true, height = 40.dp) }
            }
            if (snap.meetingItems.isNotEmpty()) {
                item { SectionHeader(ctx, ctx.getString(R.string.la_widget_section_meetings), snap.meetingItems.size, palette) }
                items(snap.meetingItems) { item -> ItemRow(ctx, item, palette, showTick = false, height = 40.dp) }
            }
            if (snap.openItems.isNotEmpty()) {
                item { SectionHeader(ctx, ctx.getString(R.string.la_widget_section_open), snap.openItems.size, palette) }
                items(snap.openItems) { item -> ItemRow(ctx, item, palette, showTick = true, height = 40.dp) }
            }
            snap.proposal?.let { proposal ->
                item { ProposalRow(ctx, proposal, palette) }
            }
            if (snap.empty) {
                item {
                    Text(
                        ctx.getString(R.string.la_widget_nothing_sub),
                        style = TextStyle(color = ColorProvider(palette.onSurfaceMuted), fontSize = 12.sp),
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

/** The proposed memory row: what it is, where it came from, and Keep or Drop. */
@Composable
private fun ProposalRow(ctx: Context, proposal: LifeWidgetItem, palette: WidgetPalette) {
    Column(
        GlanceModifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .cornerRadius(14.dp)
            .background(palette.chip().copy(alpha = 0.55f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_la_mail),
                contentDescription = null,
                modifier = GlanceModifier.size(12.dp),
                colorFilter = ColorFilter.tint(ColorProvider(palette.accent)),
            )
            Spacer(GlanceModifier.width(5.dp))
            Text(
                ctx.getString(R.string.la_widget_proposed, proposal.source ?: ""),
                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        Text(
            proposal.title,
            style = TextStyle(color = ColorProvider(palette.onSurface), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 2,
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(
                ctx.getString(R.string.la_widget_keep),
                actionRunCallback<LifeWidgetKeepAction>(actionParametersOf(LifeWidgetKeys.proposalId to proposal.id)),
                palette,
                accent = true,
                icon = R.drawable.ic_la_check,
            )
            Spacer(GlanceModifier.width(8.dp))
            Pill(
                ctx.getString(R.string.la_widget_drop),
                actionRunCallback<LifeWidgetDropAction>(actionParametersOf(LifeWidgetKeys.proposalId to proposal.id)),
                palette,
                accent = false,
                icon = R.drawable.ic_la_close,
            )
        }
    }
}
