package com.example.memgallery.lifeagent.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memgallery.R
import com.example.memgallery.ui.design.ConfirmSheet
import com.example.memgallery.ui.design.EmptyArt
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.MemSheet
import com.example.memgallery.ui.design.SheetTitle
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.design.appStatusBarsPadding
import com.example.memgallery.ui.theme.memColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class ReviewActions(
    val onClose: () -> Unit = {},
    val onKeep: (String) -> Unit = {},
    val onDrop: (String) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onApproveAll: () -> Unit = {},
    val onOpenSource: (String) -> Unit = {},
    val onOpenConnections: () -> Unit = {},
)

/** Proposed memories as a swipe deck: right keeps, left drops, undo, Approve all as a slide-up sheet, then the caught-up state. */
@Composable
fun ReviewContent(
    state: ReviewUiState,
    actions: ReviewActions,
    modifier: Modifier = Modifier,
    initialApproveSheet: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    var approveSheet by remember { mutableStateOf(initialApproveSheet) }
    val caughtUp = state.remaining.isEmpty()

    Column(modifier.fillMaxSize().background(scheme.surface).appStatusBarsPadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.la_close), tint = scheme.onSurface)
            }
            Text(stringResource(R.string.la_review_title), style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (!caughtUp) {
                FilledTonalButton(onClick = { approveSheet = true }, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Rounded.DoneAll, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.la_review_approve_all), style = MaterialTheme.typography.titleMedium)
                }
            } else if (state.total > 0) {
                Text(stringResource(R.string.la_review_counter_done, state.total, state.total), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }

        if (caughtUp) CaughtUp(state, actions) else Deck(state, actions)
    }

    if (approveSheet && !caughtUp) {
        ApproveAllSheet(
            remaining = state.remaining,
            onApprove = { approveSheet = false; actions.onApproveAll() },
            onDismiss = { approveSheet = false },
        )
    }
}

@Composable
private fun Deck(state: ReviewUiState, actions: ReviewActions) {
    val scheme = MaterialTheme.colorScheme
    val top = state.remaining.first()
    // A fling requested by the buttons: true keeps, false drops. The card animates, then the decision is sent.
    var requested by remember(top.id) { mutableStateOf<Boolean?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.la_review_counter, (state.decided + 1).coerceAtMost(state.total), state.total),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.la_review_collected, state.collectedLabel, state.keptSoFar),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant
            )
        }
        // Segmented progress
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(state.total.coerceAtLeast(1)) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < state.decided) scheme.primary else scheme.surfaceContainerHighest))
            }
        }
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            val behind = state.remaining.drop(1).take(2)
            // Peeking cards, back to front
            behind.asReversed().forEachIndexed { i, _ ->
                val depth = behind.size - i // 2 = furthest
                Box(
                    Modifier.fillMaxSize().padding(bottom = (34 - 14 * depth).dp).padding(horizontal = (8 * depth).dp)
                        .rotate(if (depth == 1) -1.5f else 1.2f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(if (depth == 1) scheme.secondaryContainer else scheme.tertiaryContainer)
                )
            }
            key(top.id) {
                TopCard(
                    card = top,
                    requested = requested,
                    widthPx = with(LocalDensity.current) { maxWidth.toPx() },
                    onDecided = { keep -> if (keep) actions.onKeep(top.id) else actions.onDrop(top.id) },
                    onOpenSource = actions.onOpenSource,
                    modifier = Modifier.padding(bottom = 34.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.la_review_swipe_drop), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.la_review_swipe_keep), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        // Buttons: Drop, Undo, Keep (the same as swiping)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).appNavigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { requested = false },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer)
            ) {
                Icon(Icons.Rounded.Close, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.la_review_drop), style = MaterialTheme.typography.titleMedium)
            }
            IconButton(
                onClick = actions.onUndo, enabled = state.canUndo,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(scheme.surfaceContainerHigh.copy(alpha = if (state.canUndo) 1f else 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.la_review_undo), tint = if (state.canUndo) scheme.onSurface else scheme.outline)
            }
            Button(onClick = { requested = true }, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.la_review_keep), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TopCard(
    card: ReviewCardUi,
    requested: Boolean?,
    widthPx: Float,
    onDecided: (Boolean) -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val mem = MaterialTheme.memColors
    val x = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = widthPx * 0.3f
    var gone by remember { mutableStateOf(false) }

    suspend fun fling(keep: Boolean) {
        if (gone) return
        gone = true
        x.animateTo(if (keep) widthPx * 1.4f else -widthPx * 1.4f, tween(220))
        onDecided(keep)
    }
    LaunchedEffect(requested) { if (requested != null) fling(requested) }

    val progress = (x.value / threshold).coerceIn(-1f, 1f)
    Box(
        modifier
            .fillMaxSize()
            .offset { IntOffset(x.value.roundToInt(), 0) }
            .rotate(x.value / widthPx * 14f)
            .shadow(8.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(if (scheme.surface.luminance() < 0.5f) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest)
            .pointerInput(card.id) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                x.value > threshold -> fling(true)
                                x.value < -threshold -> fling(false)
                                else -> x.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { x.animateTo(0f, spring()) } },
                ) { change, drag ->
                    change.consume()
                    scope.launch { x.snapTo(x.value + drag.x) }
                }
            }
    ) {
        // Decorative blob in the corner, tinted by the suggestion
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-30).dp).size(150.dp).clip(MemShape.cookie)
                .background((if (card.suggestKeep) mem.successContainer else scheme.errorContainer).copy(alpha = 0.7f))
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppChipLarge(card)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.height(36.dp).clip(RoundedCornerShape(14.dp)).background(scheme.surface.copy(alpha = 0.9f)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.onSurface, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (card.suggestKeep) R.string.la_review_suggest_keep else R.string.la_review_suggest_drop),
                        style = MaterialTheme.typography.labelLarge, color = scheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                buildAnnotatedString {
                    append(card.title)
                    append(" ")
                    withStyle(SpanStyle(fontSize = 14.sp, baselineShift = BaselineShift.Superscript, color = scheme.primary, fontWeight = FontWeight.Bold)) { append("[1]") }
                },
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface
            )
            if (card.personName != null) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(card.personName, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(card.personName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
                        if (!card.personRole.isNullOrBlank()) Text(card.personRole, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
            Spacer(Modifier.height(16.dp))
            Overlabel(stringResource(R.string.la_review_why))
            Spacer(Modifier.height(4.dp))
            Text(card.why, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)

            if (card.dateLabel != null) {
                Spacer(Modifier.height(16.dp))
                Overlabel(stringResource(R.string.la_review_date))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Event, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(card.dateLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface, modifier = Modifier.weight(1f))
                    if (card.dateTag != null) {
                        val (bg, fg) = when (card.dateTone) {
                            DateTone.Today -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                            DateTone.Overdue -> scheme.errorContainer to scheme.onErrorContainer
                            DateTone.Upcoming -> scheme.secondaryContainer to scheme.onSecondaryContainer
                        }
                        Box(Modifier.height(28.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(card.dateTag.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = fg)
                        }
                    }
                }
            }
            if (card.matchedTask != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = scheme.onSurface, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.la_review_matches, card.matchedTask), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                }
            }
            if (card.sourceTitle.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHigh)
                        .then(if (card.sourceUrl != null) Modifier.clickable { onOpenSource(card.sourceUrl) } else Modifier)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                        Text("1", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.sourceTitle, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(card.sourceSub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (card.sourceUrl != null) Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.la_open), tint = scheme.onSurfaceVariant)
                }
            }
        }
        // Stamp while dragging
        if (abs(progress) > 0.05f) {
            val keep = progress > 0
            Box(
                Modifier.align(if (keep) Alignment.TopStart else Alignment.TopEnd).padding(20.dp).alpha(abs(progress))
                    .clip(RoundedCornerShape(14.dp)).background(if (keep) scheme.primary else scheme.error).padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(if (keep) R.string.la_review_keep else R.string.la_review_drop).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (keep) scheme.onPrimary else scheme.onError
                )
            }
        }
    }
}

@Composable
private fun AppChipLarge(card: ReviewCardUi) {
    val s = card.app.style()
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(s.container).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(s.icon, null, tint = s.onContainer, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(appLabel(card.app, card.appLabel), style = MaterialTheme.typography.titleMedium, color = s.onContainer)
    }
}

@Composable
private fun Overlabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- Approve all (slide-up) ----

@Composable
private fun ApproveAllSheet(remaining: List<ReviewCardUi>, onApprove: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val mem = MaterialTheme.memColors
    val keep = remaining.filter { it.suggestKeep }
    val drop = remaining.filter { !it.suggestKeep }
    SheetFrame(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle(
                stringResource(R.string.la_approve_title, remaining.size),
                pluralStringResource(R.plurals.la_approve_body, remaining.size, remaining.size)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ApproveTile(Modifier.weight(1f), stringResource(R.string.la_approve_keep), keep.map { it.title }, mem.successContainer, mem.onSuccessContainer, Icons.Rounded.Check)
                ApproveTile(Modifier.weight(1f), stringResource(R.string.la_approve_drop), drop.map { it.title }, scheme.surfaceContainerHigh, scheme.onSurface, Icons.Rounded.FilterAlt)
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Visibility, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.la_approve_note), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Button(onClick = onApprove, modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.DoneAll, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.la_approve_cta, remaining.size), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.TouchApp, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.la_approve_first), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ApproveTile(modifier: Modifier, label: String, titles: List<String>, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, icon: ImageVector) {
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp), color = fg, modifier = Modifier.weight(1f))
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        }
        Text(titles.size.toString(), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold), color = fg)
        Spacer(Modifier.height(8.dp))
        titles.take(4).forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 3.dp))
        }
        if (titles.size > 4) Text(stringResource(R.string.la_approve_more, titles.size - 4), style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

// ---- Caught up ----

private fun learnedIcon(kind: String): ImageVector = when (kind) {
    "newsletter" -> Icons.Rounded.Newspaper
    "promotion" -> Icons.Rounded.LocalOffer
    "receipt" -> Icons.Rounded.Receipt
    "social" -> Icons.Rounded.Notifications
    else -> Icons.Rounded.FilterAlt
}

@Composable
private fun CaughtUp(state: ReviewUiState, actions: ReviewActions) {
    val scheme = MaterialTheme.colorScheme
    val nothing = state.total == 0
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyArt(if (nothing) Icons.Rounded.Schedule else Icons.Rounded.DoneAll, scale = 0.85f)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (nothing) R.string.la_review_nothing_title else R.string.la_review_caught_title),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (nothing) stringResource(R.string.la_review_nothing_body)
                else pluralStringResource(R.plurals.la_review_caught_body, state.keptSoFar, state.keptSoFar),
                style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            if (!nothing && state.learned.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.la_review_learned), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer).padding(16.dp)) {
                    Text(
                        pluralStringResource(R.plurals.la_review_learned_body, state.droppedTotal, state.droppedTotal),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
                    state.learned.forEach { l ->
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(MemShape.softBurst).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                Icon(learnedIcon(l.kind), null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(l.label, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                            Text(l.count.toString(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).appNavigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainerHigh).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, tint = scheme.onSurface, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.la_review_next).uppercase(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    Text(state.nextCollectionLabel.ifBlank { stringResource(R.string.la_review_next_unset) }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(onClick = actions.onClose, modifier = Modifier.height(56.dp)) { Text(stringResource(R.string.la_done), style = MaterialTheme.typography.titleMedium) }
        }
    }
}
