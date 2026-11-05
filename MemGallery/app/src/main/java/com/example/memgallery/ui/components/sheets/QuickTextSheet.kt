package com.example.memgallery.ui.components.sheets

import com.example.memgallery.ui.design.MemTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.memgallery.R

@Composable
fun QuickTextSheet(
    onDismiss: () -> Unit,
    onSaveText: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.sheet_quick_text_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            FilledIconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSaveText(text)
                    }
                },
                enabled = text.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.common_save))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        MemTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = stringResource(R.string.common_whats_on_your_mind),
            singleLine = false,
            minLines = 5,
            maxLines = 12,
            textStyle = MaterialTheme.typography.bodyLarge,
            fieldModifier = Modifier.focusRequester(focusRequester),
        )
    }
}
