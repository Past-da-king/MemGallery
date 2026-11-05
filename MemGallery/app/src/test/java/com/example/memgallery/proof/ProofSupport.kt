package com.example.memgallery.proof

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity
import com.example.memgallery.ui.design.ArtPainter
import com.example.memgallery.ui.design.FixedInsets
import com.example.memgallery.ui.design.LocalFixedInsets
import com.example.memgallery.ui.design.LocalMemoryPainter
import com.example.memgallery.ui.theme.MemGalleryTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Where the proof PNGs go (his Downloads, plain names). */
val ProofDir = File("C:/Users/past9/Downloads/MemGallery/build-proof")

/** The fixed "today" for every proof, so screenshots are stable. */
val ProofNow: LocalDateTime = LocalDateTime.of(2026, 11, 5, 9, 41)
val ProofToday: LocalDate = ProofNow.toLocalDate()

fun at(daysAgo: Int, hour: Int, minute: Int = 0): Long =
    ProofNow.toLocalDate().minusDays(daysAgo.toLong()).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Renders [content] in the app theme at phone size, light or dark, and saves ProofDir/[name]-light|dark.png. */
fun ComposeContentTestRule.proof(name: String, dark: Boolean, content: @Composable () -> Unit) {
    setContent {
        val art: @Composable (Any?) -> Painter = { model -> androidx.compose.runtime.remember(model) { ArtPainter(model.toString().removePrefix("art://")) } }
        CompositionLocalProvider(
            LocalMemoryPainter provides art,
            LocalFixedInsets provides FixedInsets(top = 40.dp, bottom = 24.dp)
        ) {
            MemGalleryTheme(darkTheme = dark, dynamicColor = false) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
    ProofDir.mkdirs()
    onRoot().captureRoboImage(File(ProofDir, "$name-${if (dark) "dark" else "light"}.png").path)
}

fun memory(
    id: Int, title: String?, daysAgo: Int = 0, hour: Int = 9,
    image: String? = null, text: String? = null, summary: String? = null, audio: Boolean = false,
    bookmark: String? = null, tags: List<String>? = null, analysis: String? = null, transcript: String? = null,
    status: String = "COMPLETED"
) = MemoryEntity(
    id = id, userText = text, imageUri = image?.let { "art://$it" }, audioFilePath = if (audio) "/x/$id.m4a" else null,
    bookmarkUrl = bookmark, bookmarkTitle = if (bookmark != null) title else null,
    aiTitle = title, aiSummary = summary, aiTags = tags, aiImageAnalysis = analysis, aiAudioTranscription = transcript,
    aiActions = null, creationTimestamp = at(daysAgo, hour), status = status
)

fun sampleMemories(): List<MemoryEntity> = listOf(
    memory(3, "Evening sky", 0, 9, image = "sunset"),
    memory(9, "Alpine trail", 0, 9, image = "mountains"),
    memory(10, "Flat white", 0, 9, image = "latte"),
    memory(1, "Cedar Guest Wi-Fi", 0, 8, image = "wifi", tags = listOf("wifi", "password", "guest"), analysis = "Guest network details card (sample art, no real credentials)"),
    memory(2, "Gift ideas", 0, 8, text = "Ceramic mug set, linen napkins, a good pepper grinder", summary = "Ceramic mug set, linen napkins", tags = listOf("gift")),
    memory(4, "Voice: guest network", 0, 7, audio = true, transcript = "the wifi passcode for the guest room is on the fridge", tags = listOf("wifi", "guest")),
    memory(5, "Night train to Porto", 1, 18, image = "ticket", tags = listOf("travel")),
    memory(6, "Lemon ricotta soup", 1, 13, image = "soup", tags = listOf("recipe")),
    memory(7, "How to share your Wi-Fi with a QR code", 1, 10, bookmark = "https://support.example.com/wifi-qr", summary = "Create a wifi QR and pass it to guests", tags = listOf("wifi", "router")),
    memory(8, "Router reset steps", 3, 9, text = "Hold reset 10 s, then the wifi light turns white. Default password is on the sticker", tags = listOf("router", "wifi")),
)

fun sampleHeat(): Map<LocalDate, Int> {
    val rnd = java.util.Random(21)
    return (0 until 168).associate { ProofToday.minusDays(it.toLong()) to (if (rnd.nextFloat() < 0.55f) 1 + rnd.nextInt(5) else 0) }
}

fun sampleCollections(): List<CollectionEntity> = listOf(
    CollectionEntity(1, "Travel", "Tickets, maps and places for trips"),
    CollectionEntity(2, "Recipes", ""),
    CollectionEntity(3, "Reading list", ""),
    CollectionEntity(4, "Home ideas", ""),
)

fun sampleTasks(): List<TaskEntity> = listOf(
    TaskEntity(1, 5, "Train to Lisbon", "Porto Campanha · platform 4", ProofToday.toString(), "12:20", type = "EVENT", isApproved = true),
    TaskEntity(2, null, "Lunch at Blue Door Café", "8 min walk", ProofToday.toString(), "13:00", type = "EVENT", isApproved = true),
    TaskEntity(3, 5, "Print the boarding pass", "", ProofToday.toString(), "10:00", type = "TODO", isApproved = true),
    TaskEntity(4, 2, "Buy gift for Saturday", "", ProofToday.toString(), null, type = "TODO", isRecurring = true, recurrenceRule = "WEEKLY", isApproved = true),
    TaskEntity(5, null, "Water the plants", "", ProofToday.toString(), "08:10", type = "TODO", isCompleted = true, isApproved = true, updatedAt = at(0, 8, 10)),
)
