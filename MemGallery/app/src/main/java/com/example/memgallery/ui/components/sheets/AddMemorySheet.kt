package com.example.memgallery.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.memgallery.R
import com.example.memgallery.ui.design.Tone
import com.example.memgallery.ui.design.colors

/** The five ways to add a memory, as tonal rows. Content of a slide-up sheet. */
@Composable
fun AddMemorySheet(
    onTextNote: () -> Unit,
    onUploadImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRecordAudio: () -> Unit,
    onSaveBookmark: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            stringResource(R.string.sheet_create_new_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 16.dp)
        )
        val items = listOf(
            Triple(stringResource(R.string.sheet_create_new_text), Icons.Rounded.EditNote, onTextNote) to Tone.Secondary,
            Triple(stringResource(R.string.sheet_create_new_image), Icons.Rounded.AddPhotoAlternate, onUploadImage) to Tone.Primary,
            Triple(stringResource(R.string.sheet_create_new_photo), Icons.Rounded.PhotoCamera, onTakePhoto) to Tone.Tertiary,
            Triple(stringResource(R.string.sheet_create_new_audio), Icons.Rounded.Mic, onRecordAudio) to Tone.Sky,
            Triple(stringResource(R.string.sheet_create_new_bookmark), Icons.Rounded.Link, onSaveBookmark) to Tone.Success,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { index, (item, tone) ->
                val (label, icon, action) = item
                val (bg, fg) = tone.colors()
                val outer = 28.dp
                val inner = 6.dp
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) outer else inner, topEnd = if (index == 0) outer else inner,
                    bottomStart = if (index == items.lastIndex) outer else inner, bottomEnd = if (index == items.lastIndex) outer else inner
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(onClick = action)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
