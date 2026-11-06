package com.example.memgallery.lifeagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.memgallery.R
import com.example.memgallery.ui.design.MemSheet
import com.example.memgallery.ui.design.SheetTitle

/** How long to snooze a task. The ViewModel turns the choice into a time. */
enum class SnoozeChoice { OneHour, Tomorrow }

/** Slide-up snooze chooser shared by the daily report and the person page. */
@Composable
fun SnoozeSheet(taskTitle: String, onPick: (SnoozeChoice) -> Unit, onDismiss: () -> Unit) {
    SheetFrame(onDismiss) {
        SheetTitle(stringResource(R.string.la_snooze_title), taskTitle)
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = RowGap) {
            SheetChoice(Icons.Rounded.Alarm, stringResource(R.string.la_snooze_hour), stringResource(R.string.la_snooze_hour_sub)) { onPick(SnoozeChoice.OneHour) }
            SheetChoice(Icons.Rounded.WbSunny, stringResource(R.string.la_snooze_tomorrow), stringResource(R.string.la_snooze_tomorrow_sub)) { onPick(SnoozeChoice.Tomorrow) }
        }
    }
}

@Composable
fun SheetChoice(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainer).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

/** A grouped-list checkbox: rounded square, filled when done, red outline when overdue. */
@Composable
fun TickBox(done: Boolean, overdue: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ring = if (overdue) scheme.error else scheme.outline
    Box(modifier.size(48.dp).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                .background(if (done) scheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                .then(if (done) Modifier else Modifier.border(2.dp, ring, RoundedCornerShape(9.dp))),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Rounded.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}
