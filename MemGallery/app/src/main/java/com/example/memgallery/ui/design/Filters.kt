package com.example.memgallery.ui.design

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class FilterOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/**
 * Connected button group used for filters. The selected item morphs to a filled tonal pill
 * with its icon and label; the others stay icon buttons with small inner corners.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterButtonGroup(
    options: List<FilterOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            val selected = option.key == selectedKey
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = selected,
                onCheckedChange = { onSelect(option.key) },
                shapes = shapes,
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurfaceVariant,
                    checkedContainerColor = scheme.secondaryContainer,
                    checkedContentColor = scheme.onSecondaryContainer,
                ),
                modifier = Modifier.animateContentSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (selected) 16.dp else 12.dp)
            ) {
                Icon(
                    if (selected) option.selectedIcon else option.icon,
                    contentDescription = option.label,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                )
                if (selected) {
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    Text(option.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Connected single-select group of equal-width buttons (theme mode, AI provider, My Day / Tomorrow / Upcoming).
 * The selected button is the dark secondary role and can carry a check.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    icons: Map<String, ImageVector> = emptyMap(),
    selectedCheck: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (key, label) ->
            val checked = key == selectedKey
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(key) },
                modifier = Modifier.weight(1f),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    checkedContainerColor = scheme.secondary,
                    checkedContentColor = scheme.onSecondary,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                val icon = if (checked && selectedCheck) androidx.compose.material.icons.Icons.Filled.Done else icons[key]
                if (icon != null) {
                    Icon(icon, null, Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
    }
}
