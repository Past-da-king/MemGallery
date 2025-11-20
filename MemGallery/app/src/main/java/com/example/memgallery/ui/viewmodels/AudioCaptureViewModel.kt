package com.example.memgallery.ui.viewmodels

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class AudioCaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var outputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingTime = MutableStateFlow(0L)
    val recordingTime: StateFlow<Long> = _recordingTime.asStateFlow()

    // Amplitudes for visualizer (normalized 0..1)
    private val _amplitudes = MutableStateFlow<List<Float>>(List(30) { 0f }) // Keep last 30 samples
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    private val _recordedFilePath = MutableStateFlow<String?>(null)
    val recordedFilePath: StateFlow<String?> = _recordedFilePath.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun startRecording() {
        if (_isRecording.value) return

        val fileName = "AUD_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        outputFile = File(context.externalCacheDir, fileName)

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile?.absolutePath)
            try {
                prepare()
                start()
                _isRecording.value = true
                _error.value = null
                startTimerAndPolling()
            } catch (e: IOException) {
                Log.e("AudioCaptureViewModel", "prepare() failed", e)
                _error.value = "Failed to start recording"
            } catch (e: IllegalStateException) {
                Log.e("AudioCaptureViewModel", "start() failed", e)
                _error.value = "Failed to start recording"
            }
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return

        var success = false
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            success = true
            Log.d("AudioCaptureViewModel", "Recording stopped successfully")
        } catch (e: RuntimeException) {
            // RuntimeException is thrown if stop() is called immediately after start()
            Log.e("AudioCaptureViewModel", "stop() failed, likely recording was too short", e)
            outputFile?.delete()
            _error.value = "Recording failed. Please record for at least 1 second."
        } finally {
            mediaRecorder = null
            recordingJob?.cancel()
            _isRecording.value = false
            
            // Only publish the file path if recording was successful AND file exists
            if (success && outputFile?.exists() == true) {
                _recordedFilePath.value = outputFile?.absolutePath
                Log.d("AudioCaptureViewModel", "Recording saved to: ${outputFile?.absolutePath}")
            } else {
                _recordedFilePath.value = null
                if (!success && _error.value == null) {
                    _error.value = "Recording failed. File was not saved."
                }
                Log.e("AudioCaptureViewModel", "Recording file does not exist or recording failed")
            }
        }
    }

    private fun startTimerAndPolling() {
        recordingJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (_isRecording.value) {
                val elapsedTime = System.currentTimeMillis() - startTime
                _recordingTime.value = elapsedTime / 1000

                // Poll amplitude
                mediaRecorder?.maxAmplitude?.let { maxAmp ->
                    // Normalize amplitude (empirically, max is usually around 32767)
                    val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                    // Update list: remove first, add new
                    val currentList = _amplitudes.value.toMutableList()
                    currentList.removeAt(0)
                    currentList.add(normalized)
                    _amplitudes.value = currentList
                }

                delay(50) // Update every 50ms (20fps)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isRecording.value) {
            stopRecording()
        }
        mediaRecorder?.release()
        mediaRecorder = null
    }
    
    fun clearError() {
        _error.value = null
    }
}
