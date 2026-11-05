package com.example.memgallery.ui.design

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Large rounded corner shared by every field and its focus ring. */
val FieldShape: Shape = RoundedCornerShape(20.dp)

/**
 * The one text field of the app: a filled tonal container (surfaceContainerHigh) with a large radius,
 * a primary focus ring, label, optional leading icon, helper and error text, and a show/hide toggle for secrets.
 * Use this everywhere instead of OutlinedTextField or a raw TextField.
 */
@Composable
fun MemTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    supportingText: String? = null,
    errorText: String? = null,
    isSecret: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    shape: Shape = FieldShape,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    fieldModifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val scheme = MaterialTheme.colorScheme
    var revealed by remember { mutableStateOf(false) }
    val focused by interactionSource.collectIsFocusedAsState()
    val isError = errorText != null
    val ring = when {
        isError -> scheme.error
        focused -> scheme.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Column(modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier
                .fillMaxWidth()
                .border(width = if (focused || isError) 2.dp else 0.dp, color = ring, shape = shape),
            textStyle = textStyle,
            enabled = enabled,
            readOnly = readOnly,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = leadingIcon?.let { { Icon(it, null, Modifier.size(22.dp)) } },
            trailingIcon = when {
                isSecret -> ({
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (revealed) "Hide" else "Show"
                        )
                    }
                })
                trailing != null -> trailing
                else -> null
            },
            isError = isError,
            visualTransformation = if (isSecret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            interactionSource = interactionSource,
            shape = shape,
            colors = memFieldColors(),
        )
        val below = errorText ?: supportingText
        if (below != null) {
            Text(
                below,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) scheme.error else scheme.onSurfaceVariant
            )
        }
    }
}

/** Colours of [MemTextField], reusable for fields that need their own layout (chat input, search). */
@Composable
fun memFieldColors() = MaterialTheme.colorScheme.let { s ->
    TextFieldDefaults.colors(
        focusedContainerColor = s.surfaceContainerHigh,
        unfocusedContainerColor = s.surfaceContainerHigh,
        disabledContainerColor = s.surfaceContainer,
        errorContainerColor = s.surfaceContainerHigh,
        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        errorIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        focusedTextColor = s.onSurface,
        unfocusedTextColor = s.onSurface,
        disabledTextColor = s.onSurface.copy(alpha = 0.5f),
        errorTextColor = s.onSurface,
        cursorColor = s.primary,
        errorCursorColor = s.error,
        focusedLabelColor = s.primary,
        unfocusedLabelColor = s.onSurfaceVariant,
        errorLabelColor = s.error,
        focusedPlaceholderColor = s.onSurfaceVariant,
        unfocusedPlaceholderColor = s.onSurfaceVariant,
        focusedLeadingIconColor = s.primary,
        unfocusedLeadingIconColor = s.onSurfaceVariant,
        errorLeadingIconColor = s.error,
        focusedTrailingIconColor = s.onSurfaceVariant,
        unfocusedTrailingIconColor = s.onSurfaceVariant,
        errorTrailingIconColor = s.error,
    )
}
