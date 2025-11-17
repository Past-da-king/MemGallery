package com.example.memgallery.ui.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.IOException

@Composable
fun AudioPlayer(
    audioUri: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0) }
    val mediaPlayer = remember { MediaPlayer() }

    // Initialize and manage MediaPlayer lifecycle
    DisposableEffect(audioUri) {
        try {
            mediaPlayer.setDataSource(context, Uri.parse(audioUri))
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { mp ->
                duration = mp.duration
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                progress = 0f
            }
        } catch (e: IOException) {
            e.printStackTrace()
            // Handle error, e.g., show a Toast
        }

        onDispose {
            mediaPlayer.release()
        }
    }

    // Update progress slider
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive && isPlaying) {
                progress = mediaPlayer.currentPosition.toFloat() / duration.toFloat()
                delay(50) // Update every 50ms
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (isPlaying) {
                    mediaPlayer.pause()
                } else {
                    mediaPlayer.start()
                }
                isPlaying = !isPlaying
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Audio Note", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        progress = newProgress
                        mediaPlayer.seekTo((duration * newProgress).toInt())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = duration > 0
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(mediaPlayer.currentPosition), style = MaterialTheme.typography.bodySmall)
                    Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val minutes = (ms / 1000) / 60
    val seconds = (ms / 1000) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
