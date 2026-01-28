package com.example.memgallery.utils

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader

object LogUtils {
    
    fun getLogcatDump(maxChars: Int = 50_000): String {
        return try {
            // -v time: Add timestamps
            // *:E: Show all Errors
            // *:D: Show all Debug (this might be too much, but we'll filter below)
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()
            var line: String?
            
            // Key tags we want to keep even if they aren't Errors
            val importantTags = listOf("ChatViewModel", "AIProvider", "Backup", "MemoryWorker", "Feedback")
            
            while (bufferedReader.readLine().also { line = it } != null) {
                val currentLine = line ?: ""
                // Skip network logs completely for security and noise reduction
                if (currentLine.contains("okhttp", ignoreCase = true)) continue
                
                // Keep if it's an Error OR if it contains one of our important debug tags
                val isError = currentLine.contains(" E/")
                val isImportantDebug = importantTags.any { currentLine.contains("$it:") }
                
                if (isError || isImportantDebug) {
                    lines.add(currentLine)
                }
            }
            
            // Start from the end and work backwards to stay under limit
            val result = StringBuilder()
            for (l in lines.reversed()) {
                if (result.length + l.length + 1 > maxChars) break
                result.insert(0, l + "\n")
            }
            
            if (result.isEmpty() && lines.isNotEmpty()) {
                lines.last().takeLast(maxChars)
            } else {
                result.toString()
            }
        } catch (e: Exception) {
            "Failed to collect logs: ${e.message}"
        }
    }

    fun getDeviceInfo(): String {
        return """
            Model: ${Build.MODEL}
            Manufacturer: ${Build.MANUFACTURER}
            Android Version: ${Build.VERSION.RELEASE}
            SDK: ${Build.VERSION.SDK_INT}
            App Version: ${com.example.memgallery.BuildConfig.VERSION_NAME}
        """.trimIndent()
    }
}
