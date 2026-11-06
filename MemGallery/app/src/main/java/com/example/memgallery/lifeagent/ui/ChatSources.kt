package com.example.memgallery.lifeagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memgallery.R

/** The sources an answer cites with [1] [2], in numeric order. Only the numbers that really appear in the answer. */
fun citedSources(answer: String, sources: List<LifeSourceUi>): List<LifeSourceUi> =
    sources.filter { answer.contains("[${it.number}]") }.sortedBy { it.number }

/** Tappable numbered source rows under a chat answer that came from the user's own apps. Draws nothing when none are cited. */
@Composable
fun LifeSourceRows(answer: String, sources: List<LifeSourceUi>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val cited = citedSources(answer, sources)
    if (cited.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            stringResource(R.string.la_chat_sources).uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp), color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = RowGap) {
            cited.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainer)
                        .then(if (s.url != null) Modifier.clickable { onOpen(s.url) } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                        Text(s.number.toString(), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    AppBadge(s.app, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(appLabel(s.app, s.appLabel), s.who).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (s.url != null) Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.la_open), tint = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
