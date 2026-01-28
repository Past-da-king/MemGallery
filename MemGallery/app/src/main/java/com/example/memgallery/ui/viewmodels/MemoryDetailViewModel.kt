package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import android.media.MediaPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryDetailViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _memory = MutableStateFlow<MemoryEntity?>(null)
    val memory: StateFlow<MemoryEntity?> = _memory.asStateFlow()

    // Audio Playback State
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _audioProgress = MutableStateFlow(0f)
    val audioProgress = _audioProgress.asStateFlow()

    private val _audioCurrentTime = MutableStateFlow("00:00")
    val audioCurrentTime = _audioCurrentTime.asStateFlow()

    private val _audioTotalTime = MutableStateFlow("00:00")
    val audioTotalTime = _audioTotalTime.asStateFlow()

    fun loadMemory(id: Int) {
        viewModelScope.launch {
            memoryRepository.getMemory(id).collect {
                _memory.value = it
            }
        }
    }

    fun createTask(
        memoryId: Int,
        title: String,
        description: String,
        date: String?,
        time: String?,
        type: String
    ) {
        viewModelScope.launch {
            val task = com.example.memgallery.data.local.entity.TaskEntity(
                memoryId = memoryId,
                title = title,
                description = description,
                dueDate = date,
                dueTime = time,
                type = type,
                status = "PENDING",
                priority = "MEDIUM",
                isApproved = true
            )
            memoryRepository.createTask(task)
        }
    }
    fun playAudio(path: String) {
        if (_isPlaying.value) {
            pauseAudio()
            return
        }
        
        stopAudio() // Stop any previous playback
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    stopAudio()
                }
            }
            _isPlaying.value = true
            
            // Start progress polling
            startProgressPolling()
        } catch (e: Exception) {
            android.util.Log.e("MemoryDetailViewModel", "Failed to play audio: $path", e)
            stopAudio()
        }
    }
    
    fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }
    
    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        _isPlaying.value = false
        _audioProgress.value = 0f
        _audioCurrentTime.value = "00:00"
        playbackJob?.cancel()
    }
    
    private fun startProgressPolling() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_isPlaying.value && mediaPlayer != null) {
                try {
                    val current = mediaPlayer!!.currentPosition
                    val total = mediaPlayer!!.duration
                    if (total > 0) {
                        _audioProgress.value = current.toFloat() / total.toFloat()
                        _audioCurrentTime.value = formatTime(current)
                        _audioTotalTime.value = formatTime(total)
                    }
                } catch (e: Exception) {
                    // Handle illegal state
                }
                delay(100)
            }
        }
    }
    
    fun seekAudio(progress: Float) {
        mediaPlayer?.let { player ->
            if (player.isPlaying || _isPlaying.value) {
                val newPos = (player.duration * progress).toInt()
                player.seekTo(newPos)
                _audioProgress.value = progress
            }
        }
    }
    
    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
