package com.example.memgallery.lifeagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memgallery.R
import com.example.memgallery.ui.design.BlobHeader
import com.example.memgallery.ui.design.EmptyState
import com.example.memgallery.ui.design.FilterButtonGroup
import com.example.memgallery.ui.design.FilterOption
import com.example.memgallery.ui.design.HeaderBackButton
import com.example.memgallery.ui.design.MemTextField
import com.example.memgallery.ui.design.RoundIconButton
import com.example.memgallery.ui.design.overlapUp
import com.example.memgallery.ui.theme.memColors

class PeopleActions(
    val onBack: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onSort: (PeopleSort) -> Unit = {},
    val onOpenPerson: (String) -> Unit = {},
    val onOpenConnections: () -> Unit = {},
)

/** People list: built from the user's memories. Search, three orderings, one row per person. */
@Composable
fun PeopleContent(state: PeopleUiState, actions: PeopleActions, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val visible = state.visible
    Column(modifier.fillMaxSize().background(scheme.surface)) {
        BlobHeader {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 36.dp), verticalAlignment = Alignment.Top) {
                HeaderBackButton(actions.onBack, Modifier.padding(top = 6.dp))
                Column(Modifier.weight(1f).padding(start = 4.dp, top = 6.dp)) {
                    Text(
                        pluralStringResource(R.plurals.la_people_eyebrow, state.people.size, state.people.size).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(stringResource(R.string.la_people_title), style = MaterialTheme.typography.displayMedium, color = scheme.onPrimaryContainer)
                }
                RoundIconButton(
                    icon = if (state.sort == PeopleSort.AtoZ) Icons.Rounded.Schedule else Icons.Rounded.SortByAlpha,
                    contentDescription = stringResource(R.string.la_people_sort_cd),
                    onClick = { actions.onSort(if (state.sort == PeopleSort.AtoZ) PeopleSort.Recent else PeopleSort.AtoZ) },
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        // Search pill sits on the seam of the header
        Box(Modifier.overlapUp(28.dp).padding(horizontal = 16.dp).shadow(6.dp, RoundedCornerShape(28.dp), clip = false)) {
            MemTextField(
                value = state.query, onValueChange = actions.onQuery,
                placeholder = stringResource(R.string.la_people_search),
                leadingIcon = Icons.Rounded.Search,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))

        if (state.people.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Groups,
                    title = stringResource(R.string.la_people_empty_title),
                    body = stringResource(R.string.la_people_empty_body),
                    primaryLabel = stringResource(R.string.la_collect_open_connections),
                    onPrimary = actions.onOpenConnections,
                    artScale = 0.8f,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = RowGap
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.tertiaryContainer).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.onTertiaryContainer)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.la_people_note), style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer)
                    }
                    Spacer(Modifier.height(12.dp))
                    FilterButtonGroup(
                        options = listOf(
                            FilterOption(PeopleSort.Recent.name, stringResource(R.string.la_people_recent), Icons.Rounded.Schedule),
                            FilterOption(PeopleSort.AtoZ.name, stringResource(R.string.la_people_atoz), Icons.Rounded.SortByAlpha),
                            FilterOption(PeopleSort.Open.name, stringResource(R.string.la_people_open), Icons.Rounded.Flag),
                        ),
                        selectedKey = state.sort.name,
                        onSelect = { actions.onSort(PeopleSort.valueOf(it)) },
                        horizontalPadding = 0.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (visible.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.la_people_none_match),
                            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                items(visible, key = { it.id }) { p -> PersonRow(p) { actions.onOpenPerson(p.name) } }
            }
        }
    }
}

@Composable
private fun PersonRow(p: PersonRowUi, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RowCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
            PersonAvatar(p.name, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text(p.lastLabel, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                if (p.role.isNotBlank()) Text(p.role, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.commitmentKind != null && p.commitmentText != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(commitmentLabel(p.commitmentKind), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = commitmentColor(p.commitmentKind))
                        Spacer(Modifier.width(8.dp))
                        Text(p.commitmentText, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun commitmentLabel(kind: CommitmentKind): String = stringResource(
    when (kind) {
        CommitmentKind.YouOwe -> R.string.la_kind_you_owe
        CommitmentKind.OwesYou -> R.string.la_kind_owes_you
        CommitmentKind.Meeting -> R.string.la_kind_meeting
        CommitmentKind.Waiting -> R.string.la_kind_waiting
    }
).uppercase()

@Composable
fun commitmentColor(kind: CommitmentKind): androidx.compose.ui.graphics.Color = when (kind) {
    CommitmentKind.YouOwe -> MaterialTheme.colorScheme.tertiary
    CommitmentKind.OwesYou -> MaterialTheme.memColors.success
    CommitmentKind.Meeting, CommitmentKind.Waiting -> MaterialTheme.colorScheme.primary
}
