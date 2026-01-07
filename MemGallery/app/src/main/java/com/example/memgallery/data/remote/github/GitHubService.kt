package com.example.memgallery.data.remote.github

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface GitHubService {
    @Headers(
        "Accept: application/vnd.github+json",
        "X-GitHub-Api-Version: 2022-11-28",
        "User-Agent: MemGallery-AndroidApp"
    )
    @POST("repos/Past-da-king/MemGallery/issues")
    suspend fun createIssue(
        @Header("Authorization") token: String,
        @Body issue: GitHubIssueRequest
    ): Response<GitHubIssueResponse>
}

data class GitHubIssueRequest(
    val title: String,
    val body: String,
    val labels: List<String> = listOf("feedback")
)

data class GitHubIssueResponse(
    val html_url: String,
    val number: Int
)
