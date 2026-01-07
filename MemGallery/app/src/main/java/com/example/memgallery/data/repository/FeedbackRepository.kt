package com.example.memgallery.data.repository

import com.example.memgallery.BuildConfig
import com.example.memgallery.data.remote.github.GitHubIssueRequest
import com.example.memgallery.data.remote.github.GitHubService
import com.example.memgallery.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor(
    private val gitHubService: GitHubService
) {
    suspend fun submitFeedback(userMessage: String, includeLogs: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val title = "Feedback: ${userMessage.take(30)}..."
            
            val logDump = if (includeLogs) LogUtils.getLogcatDump() else "Logs not included."
            val deviceInfo = LogUtils.getDeviceInfo()
            
            val body = """
                ## User Feedback
                $userMessage
                
                ## Device Info
                ```
                $deviceInfo
                ```
                
                ## Logs
                <details>
                <summary>Click to expand Logcat</summary>
                
                ```
                $logDump
                ```
                </details>
            """.trimIndent()
            
            val finalBody = body.take(65000)
            
            val response = gitHubService.createIssue(
                token = "Bearer ${BuildConfig.GITHUB_TOKEN}",
                issue = GitHubIssueRequest(title = title, body = finalBody)
            )
            
            if (response.isSuccessful) {
                Result.success(response.body()?.html_url ?: "Success")
            } else {
                Result.failure(Exception("Failed to submit: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
