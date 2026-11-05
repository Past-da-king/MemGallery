package com.example.memgallery.ui.components.sheets

import com.example.memgallery.ui.design.MemTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.memgallery.R

@Composable
fun CreateCollectionSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Icon
        Icon(
            imageVector = Icons.Default.CreateNewFolder,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                .padding(12.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.sheet_collection_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.sheet_collection_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))
        
        // Stylish Name Field
        MemTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.sheet_collection_name_label),
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Stylish Description Field
        MemTextField(
            value = description,
            onValueChange = { description = it },
            label = stringResource(R.string.sheet_collection_description_label),
            singleLine = false,
            minLines = 3,
            maxLines = 5,
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { onCreate(name, description) },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.sheet_collection_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
