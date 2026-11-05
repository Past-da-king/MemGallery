package com.example.memgallery.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * Floating nav: a detached pill above the content (vibrant colours) with a separate primary FAB beside it.
 * Selected item is a filled pill with a filled icon and its label; the others are outlined icon buttons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingNav(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fabIcon: ImageVector? = Icons.Filled.Add,
    fabDescription: String? = null,
    onFabClick: () -> Unit = {}
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            if (fabIcon != null) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = onFabClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Icon(fabIcon, fabDescription)
                }
            }
        },
        content = {
            items.forEachIndexed { index, item ->
                NavToggle(item, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
    )
}

@Composable
private fun NavToggle(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) scheme.onPrimaryContainer else Color.Transparent
    val fg = if (selected) scheme.primaryContainer else scheme.onPrimaryContainer
    Row(
        Modifier
            .animateContentSize()
            .height(48.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (selected) 20.dp else 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = fg,
            modifier = Modifier.size(24.dp)
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

data class AddMenuItem(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit,
)

/**
 * The add menu: a scrim over the screen and a FAB menu with its items stacked above a close button.
 * Show it while [expanded]; it draws nothing when collapsed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<AddMenuItem>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut(), modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { onExpandedChange(false) }
                )
        )
    }
    if (expanded) {
        Box(Modifier.fillMaxSize()) {
            FloatingActionButtonMenu(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .appNavigationBarsPadding()
                    .padding(end = 0.dp, bottom = 0.dp),
                button = {
                    ToggleFloatingActionButton(
                        checked = true,
                        onCheckedChange = { onExpandedChange(false) }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            ) {
                items.forEach { item ->
                    FloatingActionButtonMenuItem(
                        onClick = {
                            onExpandedChange(false)
                            item.onClick()
                        },
                        containerColor = item.container,
                        contentColor = item.content,
                        icon = { Icon(item.icon, null) },
                        text = { Text(item.label, style = MaterialTheme.typography.titleMedium) }
                    )
                }
            }
        }
    }
}
