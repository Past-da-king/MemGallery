package com.example.memgallery.ui.screens

import com.example.memgallery.ui.design.appStatusBarsPadding
import com.example.memgallery.ui.design.appNavigationBarsPadding
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.memgallery.R
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.ui.design.FilterButtonGroup
import com.example.memgallery.ui.design.FilterOption
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.MemoryKind
import com.example.memgallery.ui.design.Tone
import com.example.memgallery.ui.design.colors
import com.example.memgallery.ui.design.displayBody
import com.example.memgallery.ui.design.displayTitle
import com.example.memgallery.ui.design.kind
import com.example.memgallery.ui.design.memoryPainter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun String.fold(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()

private fun queryTerms(q: String): List<String> = q.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

/** Marks every occurrence of a query term (accent and case insensitive) with the tertiary container colour. */
@Composable
private fun highlight(text: String, query: String): AnnotatedString {
    val bg = MaterialTheme.colorScheme.tertiaryContainer
    val fg = MaterialTheme.colorScheme.onTertiaryContainer
    val terms = queryTerms(query).map { it.fold() }
    val folded = text.fold()
    // Folding can change the length of a string; only highlight when positions still line up.
    if (terms.isEmpty() || folded.length != text.length) return AnnotatedString(text)
    val marks = BooleanArray(text.length)
    terms.forEach { t ->
        var i = folded.indexOf(t)
        while (i >= 0) {
            for (k in i until (i + t.length)) marks[k] = true
            i = folded.indexOf(t, i + t.length)
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            var j = i
            while (j < text.length && marks[j] == marks[i]) j++
            if (marks[i]) withStyle(SpanStyle(background = bg, color = fg)) { append(text.substring(i, j)) }
            else append(text.substring(i, j))
            i = j
        }
    }
}

private fun snippet(text: String, query: String, max: Int = 120): String {
    val flat = text.replace('\n', ' ')
    if (flat.length <= max) return flat
    val first = queryTerms(query).map { flat.fold().indexOf(it.fold()) }.filter { it >= 0 }.minOrNull() ?: 0
    val start = (first - 24).coerceAtLeast(0)
    val end = (start + max).coerceAtMost(flat.length)
    return (if (start > 0) "…" else "") + flat.substring(start, end).trim() + (if (end < flat.length) "…" else "")
}

private fun relevance(m: MemoryEntity, q: String): Int {
    val terms = queryTerms(q).map { it.fold() }
    var s = 0
    terms.forEach { t ->
        if (m.aiTitle?.fold()?.contains(t) == true) s += 6
        if (m.aiTags?.any { it.fold().contains(t) } == true) s += 4
        if (m.aiSummary?.fold()?.contains(t) == true) s += 2
        if (m.userText?.fold()?.contains(t) == true) s += 2
        if (m.aiImageAnalysis?.fold()?.contains(t) == true) s += 1
        if (m.aiAudioTranscription?.fold()?.contains(t) == true) s += 1
    }
    return s
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMM")

private fun MemoryEntity.day(): LocalDate =
    Instant.ofEpochMilli(creationTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()

/** The full-screen search view: field, filters, "Ask about" hand-over, highlighted results and related tags. */
@Composable
fun SearchView(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    results: List<MemoryEntity>,
    onOpen: (MemoryEntity) -> Unit,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    requestFocus: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    var newestFirst by remember { mutableStateOf(false) }
    val sorted = remember(results, query, newestFirst) {
        if (newestFirst || query.isBlank()) results.sortedByDescending { it.creationTimestamp }
        else results.sortedByDescending { relevance(it, query) }
    }
    val focus = remember { FocusRequester() }
    if (requestFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val options = listOf(
        FilterOption("All", stringResource(R.string.gallery_filter_all), Icons.Outlined.Apps, Icons.Filled.Apps),
        FilterOption("Images", stringResource(R.string.gallery_filter_images), Icons.Outlined.Image, Icons.Filled.Image),
        FilterOption("Notes", stringResource(R.string.gallery_filter_notes), Icons.Outlined.StickyNote2, Icons.Filled.StickyNote2),
        FilterOption("Audio", stringResource(R.string.gallery_filter_audio), Icons.Outlined.Mic, Icons.Filled.Mic),
        FilterOption("Bookmarks", stringResource(R.string.gallery_filter_bookmarks), Icons.Outlined.Bookmark, Icons.Filled.Bookmark),
    )

    Column(modifier.fillMaxSize().background(scheme.surface).appStatusBarsPadding()) {
        // Search field
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = scheme.onSurface)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_clear), tint = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        FilterButtonGroup(options, filter, onFilter)

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 32.dp)) {
            if (query.isNotBlank()) {
                item {
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(scheme.tertiaryContainer)
                            .clickable { onAsk(query) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(scheme.onTertiaryContainer.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.onTertiaryContainer) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.search_ask_about, query), style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.search_ask_sub), style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer.copy(alpha = 0.8f))
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = scheme.onTertiaryContainer)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        (if (sorted.size == 1) stringResource(R.string.search_result_one) else stringResource(R.string.search_results, sorted.size)).uppercase(),
                        style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant
                    )
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { newestFirst = !newestFirst }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (newestFirst || query.isBlank()) stringResource(R.string.search_newest) else stringResource(R.string.search_best_match),
                            style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant
                        )
                        Icon(Icons.Rounded.ExpandMore, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            items(sorted.size) { i ->
                val m = sorted[i]
                val shape = RoundedCornerShape(
                    topStart = if (i == 0) 28.dp else 6.dp, topEnd = if (i == 0) 28.dp else 6.dp,
                    bottomStart = if (i == sorted.lastIndex) 28.dp else 6.dp, bottomEnd = if (i == sorted.lastIndex) 28.dp else 6.dp
                )
                ResultRow(m, query, today, shape, onClick = { onOpen(m) },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp))
            }
            val tags = sorted.flatMap { it.aiTags.orEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key }
            if (tags.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.search_related_tags).uppercase(), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(scheme.surface)
                                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .clickable { onQueryChange(tag) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.LocalOffer, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tag, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ResultRow(
    m: MemoryEntity,
    query: String,
    today: LocalDate,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val kind = m.kind()
    val title = m.displayTitle(stringResource(R.string.memory_card_untitled))
    val body = m.displayBody()?.let { snippet(it, query) }
    val day = m.day()
    val dayLabel = when (day) {
        today -> stringResource(R.string.home_today)
        else -> day.format(dayFmt)
    }.uppercase()
    val imageHit = m.aiImageAnalysis?.fold()?.let { a -> queryTerms(query).any { a.contains(it.fold()) } } == true
    val kindLabel = when (kind) {
        MemoryKind.Image -> stringResource(R.string.search_kind_image)
        MemoryKind.Note -> stringResource(R.string.search_kind_note)
        MemoryKind.Audio -> stringResource(R.string.search_kind_voice)
        MemoryKind.Link -> stringResource(R.string.search_kind_bookmark)
    }.uppercase()
    val meta = listOfNotNull(kindLabel, if (kind == MemoryKind.Image && imageHit) stringResource(R.string.search_text_found_in_image).uppercase() else null, dayLabel).joinToString(" · ")
    val kindIcon: ImageVector = when (kind) {
        MemoryKind.Image -> Icons.Rounded.Image
        MemoryKind.Note -> Icons.Rounded.StickyNote2
        MemoryKind.Audio -> Icons.Rounded.Mic
        MemoryKind.Link -> Icons.Rounded.Link
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Thumbnail: the image itself, or a shaped tonal chip carrying the kind icon.
        when (kind) {
            MemoryKind.Image -> Image(
                memoryPainter(m.imageUri), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHighest)
            )
            else -> {
                val (tone, shapeOf) = when (kind) {
                    MemoryKind.Audio -> Tone.Sky to MemShape.arch
                    MemoryKind.Link -> Tone.Success to MemShape.softBurst
                    else -> Tone.Tertiary to MemShape.softBurst
                }
                val (bg, fg) = tone.colors()
                Box(Modifier.size(56.dp).clip(shapeOf).background(bg), contentAlignment = Alignment.Center) {
                    Icon(kindIcon, null, tint = fg, modifier = Modifier.size(26.dp))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(highlight(title, query), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (body != null) {
                Spacer(Modifier.height(2.dp))
                Text(highlight(body, query), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(meta, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
