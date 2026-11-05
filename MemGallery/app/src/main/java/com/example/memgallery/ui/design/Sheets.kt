package com.example.memgallery.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one sheet container of the app. On the phone every choice, form and confirmation slides up
 * from the bottom in this container; centred dialogs are not used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.appNavigationBarsPadding(), content = content)
    }
}

/** The content of a sheet: big emphasized title, optional supporting line, then body. */
@Composable
fun SheetTitle(title: String, supporting: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        if (supporting != null) {
            Spacer(Modifier.height(4.dp))
            Text(supporting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Slide-up confirmation: replaces AlertDialog everywhere. */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    MemSheet(onDismiss = onDismiss) {
        SheetTitle(title, body)
        extra()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(dismissLabel, style = MaterialTheme.typography.titleMedium) }
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
