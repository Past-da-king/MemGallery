package com.example.memgallery.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class UrlMetadata(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val faviconUrl: String?
)

@Singleton
class UrlMetadataExtractor @Inject constructor() {

    suspend fun extract(url: String): UrlMetadata = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(10000) // 10 seconds timeout
                .get()

            // Extract Title
            var title = doc.select("meta[property=og:title]").attr("content")
            if (title.isEmpty()) {
                title = doc.title()
            }

            // Extract Description
            var description = doc.select("meta[property=og:description]").attr("content")
            if (description.isEmpty()) {
                description = doc.select("meta[name=description]").attr("content")
            }

            // Extract Image
            var imageUrl = doc.select("meta[property=og:image]").attr("content")
            if (imageUrl.isEmpty()) {
                // Try twitter card image
                imageUrl = doc.select("meta[name=twitter:image]").attr("content")
            }
            if (imageUrl.isEmpty()) {
                // Try finding the first significant image
                val firstImg = doc.select("img[src~=(?i)\\.(png|jpe?g)]").first()
                imageUrl = firstImg?.absUrl("src") ?: ""
            }

            // Extract Favicon (simplified)
            var faviconUrl = doc.select("link[rel~=(?i)^(shortcut|icon|apple-touch-icon)]").attr("href")
            if (faviconUrl.isNotEmpty() && !faviconUrl.startsWith("http")) {
                // Handle relative URLs for favicon is tricky without base URI, but Jsoup absUrl helps if used on element
                val faviconEl = doc.select("link[rel~=(?i)^(shortcut|icon|apple-touch-icon)]").first()
                faviconUrl = faviconEl?.absUrl("href") ?: ""
            }
            if (faviconUrl.isEmpty()) {
                // Fallback to default location
                val uri = java.net.URI(url)
                faviconUrl = "${uri.scheme}://${uri.host}/favicon.ico"
            }

            UrlMetadata(
                url = url,
                title = title.takeIf { it.isNotEmpty() },
                description = description.takeIf { it.isNotEmpty() },
                imageUrl = imageUrl.takeIf { it.isNotEmpty() },
                faviconUrl = faviconUrl.takeIf { it.isNotEmpty() }
            )

        } catch (e: IOException) {
            e.printStackTrace()
            // Return basic metadata with just the URL if extraction fails
            UrlMetadata(url, null, null, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            UrlMetadata(url, null, null, null, null)
        }
    }
}
