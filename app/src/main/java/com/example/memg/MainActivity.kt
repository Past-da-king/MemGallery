package com.example.memg

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.memg.ui.theme.MemgTheme
import com.example.memg.presentation.MemGalleryApp
import com.example.memg.util.ScreenCaptureService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    fun startScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemgTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MemGalleryApp(
                        modifier = Modifier.padding(innerPadding),
                        startScreenCapture = ::startScreenCapture
                    )
                }
            }
        }
    }
}

@Composable
fun MemGalleryApp(modifier: Modifier = Modifier, startScreenCapture: () -> Unit = {}) {
    MemGalleryApp(startScreenCapture = startScreenCapture)
}

@Preview(showBackground = true)
@Composable
fun MemGalleryAppPreview() {
    MemgTheme {
        MemGalleryApp(startScreenCapture = {})
    }
}