package com.example.memgallery.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkInputScreen(
    navController: NavController
) {
    var url by remember { mutableStateOf("") }
    var userNote by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Save Bookmark") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val encodedNote = java.net.URLEncoder.encode(userNote, "UTF-8")
                            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                            navController.navigate(
                                com.example.memgallery.navigation.Screen.PostCapture.createRoute(
                                    userText = encodedNote,
                                    bookmarkUrl = encodedUrl
                                )
                            ) {
                                popUpTo(com.example.memgallery.navigation.Screen.Gallery.route) { inclusive = false }
                            }
                        },
                        enabled = url.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Next")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("https://example.com") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = "Link") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = userNote,
                onValueChange = { userNote = it },
                label = { Text("Note (Optional)") },
                placeholder = { Text("Add a note about this link...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Take up remaining space
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }
    }
}
