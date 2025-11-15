package com.example.memgallery.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.memgallery.navigation.Screen

@Composable
fun ImageCreationScreen(
    navController: NavController
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Immediately navigate to PostCaptureScreen with the selected image URI
            navController.navigate(Screen.PostCapture.createRoute(imageUri = uri.toString())) {
                // Pop this screen off the back stack
                popUpTo(Screen.ImageCreation.route) { inclusive = true }
            }
        } else {
            // If the user cancels the picker, just go back.
            navController.popBackStack()
        }
    }

    // Launch the image picker automatically when the screen is composed
    LaunchedEffect(Unit) {
        imagePickerLauncher.launch("image/*")
    }

    // Show a loading state while the user is selecting an image.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Waiting for image selection...")
        }
    }
}
