package com.example.memg.util

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var recordFile: String = ""
    private var isRecording = false

    fun startRecording(): String {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            recordFile = "${context.getExternalFilesDir(null)}/audio_$timestamp.mp4"

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordFile)
                prepare()
            }

            recorder?.start()
            isRecording = true
            Log.d("AudioRecorder", "Started recording to: $recordFile")
            return recordFile
        } catch (e: IOException) {
            Log.e("AudioRecorder", "prepare() failed: ${e.message}")
            return ""
        }
    }

    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            Log.d("AudioRecorder", "Stopped recording")
        } catch (e: RuntimeException) {
            Log.e("AudioRecorder", "stop() failed: ${e.message}")
            // Reset recorder if stop fails
            recorder?.release()
            recorder = null
            isRecording = false
        }
    }

    fun isRecording(): Boolean {
        return isRecording
    }
    
    fun getCurrentRecordFile(): String {
        return recordFile
    }
}