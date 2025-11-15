

## **Comprehensive Specification: "MemGallery" (v1.0)**

### **1.0 Executive Summary**

**1.1. Project Vision:** MemGallery is a sophisticated, AI-powered personal journaling application for Android. It enables users to create rich, multimodal "memories" by combining images, audio, and text. The application's core is its integration with the Google Gemini LLM, which intelligently analyzes the user's inputs to generate titles, narrative summaries, and descriptive tags, transforming raw data into a cohesive, searchable, and meaningful experience.

**1.2. Core User Experience:** The user journey centers around effortless capture and intelligent enrichment. A user can quickly capture a moment (e.g., a photo), add context with their voice or text, and with a single tap, have the AI process it into a fully-formed memory. The app then serves as a beautiful, searchable gallery of these enriched moments.

**1.3. Monetization & Architecture Model:** The application follows a "Bring Your Own API Key" (BYOAK) model. The core app is free, but users must provide their own Google Gemini API key to enable the AI-powered analysis features. This lowers the operational cost for the developer and gives power users control over their API usage.

### **2.0 System Architecture & Technology Stack**

*   **Platform:** Native Android
*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose
*   **Design System:** Material 3 (M3)
*   **Architecture Pattern:** Model-View-ViewModel (MVVM) is recommended, with ViewModels handling UI state and business logic, and a Repository pattern for data abstraction.
*   **AI Engine:** Google Gemini (Multimodal) via REST API.
*   **Data Persistence:** Room Persistence Library for local storage of memory objects on the device.
*   **Security:** `EncryptedSharedPreferences` for secure storage of the user's Gemini API key.
*   **Asynchronous Operations:** Kotlin Coroutines for managing background tasks like API calls and database operations.

### **3.0 Core Data Models**

#### **3.1. Gemini API Data Transfer Object (DTO)**

This model defines the structured JSON that the application expects back from the Gemini API.

```kotlin
// Data class for parsing the JSON response from the Gemini API
data class AiAnalysisDto(
    val title: String,
    val summary: String,
    val tags: List<String>,
    val image_analysis: String?, // Nullable if no image was provided
    val audio_transcription: String? // Nullable if no audio was provided
)
```

#### **3.2. Room Database `MemoryEntity`**

This model defines the schema for the `memories` table in the local Room database. This is the single source of truth for all saved memories.

```kotlin
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // User-provided inputs
    val userText: String?,
    val imageUri: String?, // Stores the local content URI of the image
    val audioFilePath: String?, // Stores the local file path of the recording

    // AI-generated data
    val aiTitle: String,
    val aiSummary: String,
    val aiTags: List<String>, // Room will use a TypeConverter for this
    val aiImageAnalysis: String?,
    val aiAudioTranscription: String?,

    // Metadata
    val creationTimestamp: Long
)
```

---

### **4.0 Detailed UI/UX Screen-by-Screen Breakdown**

#### **4.1. Screen: Main Gallery (`MemGallery`)**

*   **Purpose:** The central hub of the application. It provides an overview of all memories, enables searching and filtering, and serves as the entry point for memory creation.
*   **Material 3 Components Analysis:**
    *   **`Scaffold`:** The main layout container.
    *   **`TopAppBar` (`small` or `medium`):** Displays the "MemGallery" title. An `IconButton` with a person icon (`Icons.Filled.Person`) will be in the `actions` slot, navigating to a user profile or settings screen.
    *   **`SearchBar`:** A full-width, M3 `SearchBar` composable below the `TopAppBar`. It will have placeholder text "Search memories..."
    *   **`FilterChip`:** A horizontally scrolling `Row` contains a set of `FilterChip` composables ("All", "Images", "Notes", "Audio"). Toggling these chips will filter the `LazyVerticalGrid` content.
    *   **`Card`:** The "Kyoto Trip Memories" highlight is a `Card` with an `AsyncImage` as its background and overlayed text. The grid items are `ElevatedCard`s to give them a slight lift. Small icons (e.g., `Icons.Outlined.PhotoCamera`, `Icons.Outlined.Mic`) are overlaid on the top-right corner of the card's image to indicate the media types contained within.
    *   **`LazyVerticalGrid`:** This composable will be used to efficiently display the main grid of memory cards, adapting to different screen sizes.
    *   **`FloatingActionButton` (FAB):** The large, purple `+` FAB (`Icons.Filled.Add`) is the primary action for creating a new memory.
*   **User Interaction & System Flow:**
    1.  **On Launch:** The screen fetches all `MemoryEntity` objects from the Room database and displays them in the `LazyVerticalGrid`.
    2.  **Highlight Logic:** The "Highlight" card at the top will dynamically feature a memory. The logic will be to select a random memory from the last 14 days that contains both an image and at least two tags.
    3.  **Search:** As the user types in the `SearchBar`, the ViewModel filters the list of memories based on matches in the `aiTitle`, `aiSummary`, or `aiTags` fields.
    4.  **Filtering:** Tapping a `FilterChip` (e.g., "Audio") filters the grid to show only memories where the corresponding field (e.g., `audioFilePath`) is not null.
    5.  **View Detail:** Tapping any memory `Card` navigates the user to the **Memory Detail Screen**, passing the `id` of the selected memory.
    6.  **Create Memory:** Tapping the `+` FAB changes a state variable (`showBottomSheet = true`), which triggers the display of the **`ModalBottomSheet`**.

#### **4.2. UI Element: Add New Memory (`ModalBottomSheet`)**

*   **Purpose:** A quick, non-intrusive way for the user to select the starting point for their new memory. As per the user's description, this is not a new screen but a component that appears over the gallery.
*   **Material 3 Component Analysis:**
    *   **`ModalBottomSheet`:** This is the correct M3 component. It will contain a `Column` with a `dragHandle` at the top. Inside the column are three `ListItem` composables, each with a `leadingContent` icon (`Icons.Filled.Edit`, `Icons.Filled.Image`, `Icons.Filled.Mic`) and the corresponding text. The "Cancel" button is a `Button` with `filledTonal` styling.
*   **User Interaction & System Flow:**
    1.  The sheet animates up from the bottom.
    2.  Tapping "Add Text" navigates to the **Text Entry Screen**.
    3.  Tapping "Add Image" initiates the OS image picker (select from gallery or take a new photo).
    4.  Tapping "Add Audio" navigates to the **Record Audio Screen**.
    5.  Upon successful media selection, the user is navigated to the **Memory Composition Screen**, and the `ModalBottomSheet` is dismissed.

#### **4.3. Screen: Memory Composition (`New Memory Captured`)**

*   **Purpose:** A staging area where the user assembles their memory before submitting it for AI processing.
*   **Material 3 Components Analysis:**
    *   **`Scaffold`:** Provides the basic layout. A `TopAppBar` with a close `IconButton` (`Icons.Filled.Close`) allows the user to discard the new memory.
    *   **`Card`:** The main content area displaying the initially selected image is a large `Card`.
    *   **`Button`:** The "Add Audio" and "Add Text" actions are `FilledTonalButton`s. The primary action, "Save Now," is a `FilledButton`.
    *   **`LinearProgressIndicator`:** A determinate or indeterminate progress bar appears below the "Save Now" button after it's tapped, accompanied by text like "Processing with Gemini..."
*   **User Interaction & System Flow:**
    1.  The user arrives here after selecting an initial piece of media (e.g., an image).
    2.  The state of this screen's ViewModel holds temporary URIs/data for the image, audio, and text.
    3.  Tapping "Add Audio" or "Add Text" navigates to the respective screens. Upon returning, the ViewModel is updated with the new data.
    4.  When the user taps **"Save Now"**:
        a. The UI enters a loading state (disabling buttons, showing the progress indicator).
        b. The ViewModel packages the user's inputs (image bytes, audio file, text string) into a multimodal request.
        c. It calls the Gemini API (using the key from `EncryptedSharedPreferences`).
        d. On a successful response, it parses the `AiAnalysisDto`.
        e. It constructs a full `MemoryEntity` object.
        f. It saves the entity to the Room database.
        g. It navigates back to the Main Gallery (which will now show the new memory) and displays a confirmation `Snackbar` saying "Memory created."

#### **4.4. Screen: Memory Detail (`Inspiration`)**

*   **Purpose:** To provide a full, immersive view of a single saved memory.
*   **Material 3 Components Analysis:**
    *   **`Scaffold`:** The root container.
    *   **`TopAppBar`:** Displays the `aiTitle` of the memory. Actions include an `IconButton` for "Edit" (`Icons.Filled.Edit`).
    *   **`AsyncImage` (from Coil/Glide):** Displays the image associated with the `imageUri`.
    *   **Custom Audio Player:** This will be a custom composable `Row` containing an `IconButton` (play/pause), a `Slider` for scrubbing, and `Text` for timestamps.
    *   **`Text`:** Used for "Full Note" and the main body of the transcription/note.
    *   **`Card`:** The "AI Summary" section is placed within a `Card` with a slightly different background color (`surfaceVariant`) to visually separate it from the user's raw text.
    *   **`AssistChip`:** The tags are displayed in a `FlowRow` (from the Accompanist library or Compose foundation) containing multiple `AssistChip`s.
    *   **`ExtendedFloatingActionButton`:** The share icon (`Icons.Filled.Share`) is an `ExtendedFAB`.
*   **User Interaction & System Flow:**
    1.  The screen receives a `memoryId` as an argument.
    2.  The ViewModel fetches the corresponding `MemoryEntity` from the Room database.
    3.  The UI is populated with the data from the entity. All sections (Image, Audio, Note, Summary, Tags) are conditionally displayed based on whether their corresponding data is non-null.
    4.  The "Share" button compiles a text summary (Title + AI Summary) and triggers the Android Sharesheet intent.

#### **4.5. Screen: API Key Management**

*   **Purpose:** A secure and user-friendly way for the user to manage their personal Gemini API key.
*   **Material 3 Component Analysis:**
    *   **`Scaffold` with `TopAppBar`:** Provides standard screen structure.
    *   **`OutlinedTextField`:** The field for the API key. It will use a `PasswordVisualTransformation` to obscure the key. The trailing icon will be an `IconButton` that toggles the visibility.
    *   **`Button` (`Filled`):** "Validate Key" is the primary action.
    *   **`TextButton`:** "Clear Key" is a secondary, lower-emphasis action.
    *   **Helper UI:** The "Success! Key is valid." message is a `Row` inside a `Card` with a green container color, containing an icon (`Icons.Filled.CheckCircle`) and `Text`. An equivalent error message will have a red color.
*   **User Interaction & System Flow:**
    1.  The user enters their key and taps "Validate Key."
    2.  The ViewModel makes a lightweight, inexpensive test call to the Gemini API (e.g., listing available models).
    3.  If the call is successful (HTTP 200), the ViewModel saves the key to `EncryptedSharedPreferences` and displays the green "Success" message.
    4.  If the call fails (e.g., HTTP 401/403), it shows a red "Error: Invalid Key" message and does not save the key.
    5.  "Clear Key" removes the key from `EncryptedSharedPreferences`.

### **5.0 Error Handling & Edge Cases**

*   **API Key Not Present:** If the user tries to create a memory without a valid API key, the "Save Now" button will be disabled. Tapping it will show a `Snackbar` with the message "Please add a valid API Key in Settings." and an action button to navigate to the API Key screen.
*   **Network Failure:** All API calls will be wrapped in a `try-catch` block. If a network error occurs, a `Snackbar` will be displayed to the user (e.g., "Network error. Please check your connection.").
*   **API Error:** If the Gemini API returns an error (e.g., 500 server error, rate limiting), the app will show a user-friendly error message in a `Snackbar` (e.g., "AI analysis failed. Please try again later.").
*   **Permissions:** The app will use the modern Android permission flow to request Camera, Microphone, and (if necessary for older Android versions) Storage permissions when the user first attempts to use those features. If permissions are denied, the app will gracefully disable the corresponding feature and show an explanation.




### **Implementation Guide: The Unified AI Processor**

This implementation focuses on creating a single, powerful method, `processMemory`, within your `GeminiService`. This method is designed to be the sole entry point for all AI analysis, intelligently adapting its request to the Gemini API based on the inputs it receives.

#### **Step 1: Define a Type-Safe Data Model for the AI Response**

First, instead of parsing the JSON into a generic `Map<String, Any>`, we will define a strict Kotlin data class. This provides type safety, makes the code much easier to work with, and reduces runtime errors. This class should align with the detailed specification we created earlier.

**File: `AiAnalysisDto.kt`**
```kotlin
//PACKAGE  NAME WOULD  GO HERE

import com.google.gson.annotations.SerializedName

/**
 * A type-safe data class to represent the structured JSON response from the Gemini API.
 * This ensures predictable and safe handling of the AI's output.
 */
data class AiAnalysisDto(
    @SerializedName("title")
    val title: String,

    @SerializedName("summary")
    val summary: String,

    @SerializedName("tags")
    val tags: List<String>,

    @SerializedName("image_analysis")
    val imageAnalysis: String?, // Nullable as it's only present for image inputs

    @SerializedName("audio_transcription")
    val audioTranscription: String? // Nullable as it's only present for audio inputs
)
```

---

#### **Step 2: Implement the Unified `GeminiService`**

Next, we refactor your `GeminiService.kt`. We will remove the separate `processImageContent`, `processTextContent`, etc., and replace them with a single `processMemory` function. This function will be responsible for dynamically building the multimodal request.

**File: `GeminiService.kt` (Refactored and Unified)**

```kotlin
//PACKAGE  NAME WOULD  GO HERE

import android.content.Context
import android.net.Uri
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson // Inject Gson for robust JSON parsing
) {
    private var client: Client? = null

    fun initialize(apiKey: String) {
        this.client = Client.builder().apiKey(apiKey).build()
    }

    fun isEnabled(): Boolean = client != null

    fun disable() {
        client = null
    }

    /**
     * The single, unified function for processing any combination of memory inputs.
     * It dynamically builds a multimodal request for the Gemini API.
     *
     * @param imageUri The local URI of the user's image (optional).
     * @param audioUri The local URI of the user's audio recording (optional).
     * @param userText The user's typed text note (optional).
     * @return A Result wrapper containing the parsed AiAnalysisDto on success, or an Exception on failure.
     */
    suspend fun processMemory(
        imageUri: String?,
        audioUri: String?,
        userText: String?
    ): Result<AiAnalysisDto> = withContext(Dispatchers.IO) {
        if (!isEnabled()) {
            return@withContext Result.failure(IllegalStateException("Gemini client is not initialized."))
        }

        try {
            // 1. Dynamically build the list of "Parts" for the multimodal request.
            val parts = mutableListOf<Part>()

            // 2. Construct a dynamic, context-aware prompt for the AI.
            val promptBuilder = StringBuilder(
                "You are an expert AI assistant for the MemGallery app. Your task is to analyze user-provided content and generate a structured JSON response. " +
                "The JSON object must contain the following fields: 'title' (a short, evocative title for the memory), 'summary' (a single paragraph narrative synthesizing all inputs), " +
                "'tags' (an array of 3-5 relevant string tags), 'image_analysis' (a detailed description of the image, if present), and 'audio_transcription' (a verbatim transcription of the audio, if present).\n\n"
            )

            // --- Add Image Part and update prompt ---
            if (imageUri != null) {
                val imageBytes = context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { it.readBytes() }
                    ?: throw IOException("Could not read image file from URI: $imageUri")
                val mimeType = context.contentResolver.getType(Uri.parse(imageUri)) ?: "image/jpeg"
                parts.add(Part.fromBytes(imageBytes, mimeType))
                promptBuilder.append("Analyze the provided image in detail. ")
            }

            // --- Add Audio Part and update prompt ---
            if (audioUri != null) {
                val audioBytes = context.contentResolver.openInputStream(Uri.parse(audioUri))?.use { it.readBytes() }
                    ?: throw IOException("Could not read audio file from URI: $audioUri")
                // Common audio formats. Adjust if you use a different format like .wav or .ogg.
                val mimeType = context.contentResolver.getType(Uri.parse(audioUri)) ?: "audio/m4a"
                parts.add(Part.fromBytes(audioBytes, mimeType))
                promptBuilder.append("Transcribe the provided audio recording verbatim and analyze its content. ")
            }

            // --- Add Text Part and update prompt ---
            if (!userText.isNullOrBlank()) {
                // User text is added directly to the prompt, not as a separate part, for better context.
                promptBuilder.append("Incorporate the following user's note into your analysis: \"$userText\". ")
            }

            promptBuilder.append("\n\nBased on all the provided content, generate the final JSON response.")

            // The final text prompt must be the first part in the list.
            parts.add(0, Part.fromText(promptBuilder.toString()))

            // 3. Build the final Content object
            val multimodalContent = Content.fromParts(*parts.toTypedArray())

            // 4. Set generation config to enforce JSON output
            val config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .build()

            // 5. Call the API
            val response = client!!.models.generateContent("gemini-1.5-flash", multimodalContent, config)
            val textResponse = response.text() ?: throw Exception("Received an empty response from the API.")

            // 6. Parse the response and return success
            Result.success(parseJsonResponse(textResponse))

        } catch (e: Exception) {
            // 7. Catch any exception and return failure
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * A robust parser for the JSON response from the Gemini API.
     * It cleans the string and deserializes it into our type-safe DTO.
     *
     * @param responseText The raw text response from the API.
     * @return The parsed AiAnalysisDto.
     * @throws JsonSyntaxException if the JSON is malformed.
     */
    private fun parseJsonResponse(responseText: String): AiAnalysisDto {
        // Clean the response text by removing markdown code block delimiters (```json ... ```)
        val cleanedResponseText = responseText
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()

        return gson.fromJson(cleanedResponseText, AiAnalysisDto::class.java)
    }
}
```

---

### **Step 3: Detailed Explanation of the Implementation**

This unified approach is superior because it allows the AI to find connections between the different media types, leading to a much richer and more contextual summary.

#### **3.1. The Function Signature**
```kotlin
suspend fun processMemory(
    imageUri: String?,
    audioUri: String?,
    userText: String?
): Result<AiAnalysisDto>
```
*   **`suspend`**: The function is a coroutine, allowing it to perform network and disk operations without blocking the main UI thread.
*   **Nullable Parameters (`String?`)**: All three inputs are optional. This is the key to the unified approach. The function is designed to work whether it receives one, two, or all three inputs.
*   **`Result<AiAnalysisDto>`**: The return type is a `Result` wrapper. This is a standard and robust Kotlin pattern for handling operations that can either succeed or fail.
    *   **On Success**: It will contain `Result.success(AiAnalysisDto(...))`.
    *   **On Failure**: It will contain `Result.failure(Exception(...))`, providing the calling code (the ViewModel) with the specific error that occurred (e.g., `IOException`, `JsonSyntaxException`, `IllegalStateException`).

#### **3.2. Dynamic Prompt Construction**
The magic of this function lies in how it builds the prompt for Gemini.
*   **`StringBuilder`**: We use a `StringBuilder` for efficient string concatenation.
*   **Base Instructions**: The prompt starts by giving the AI its persona ("expert AI assistant for MemGallery") and defining the *exact* JSON structure required. This is crucial for getting reliable, structured output.
*   **Conditional Appending**: Inside each `if (input != null)` block, we don't just add the media `Part`, we also append a specific instruction to the prompt (e.g., `"Analyze the provided image in detail."`). This tells the AI exactly what to do with each piece of content, guiding it to create a cohesive analysis.

#### **3.3. Assembling Multimodal Parts**
The Gemini SDK requires inputs to be assembled into a list of `Part` objects.
*   `mutableListOf<Part>()`: We create a list to hold all our media parts.
*   `Part.fromBytes()`: For images and audio, we read their content into a `ByteArray` and create a `Part` from it, specifying the correct MIME type (e.g., `image/jpeg`, `audio/m4a`). The `.use{}` block ensures the `InputStream` is automatically closed, preventing memory leaks.
*   `parts.add(0, Part.fromText(...))`: The complete text prompt *must* be the first element in the list of parts. We add it at index 0 after assembling all other media parts.

#### **3.4. Enforcing JSON Output**
```kotlin
val config = GenerateContentConfig.builder()
    .responseMimeType("application/json")
    .build()
```This is a powerful feature of the Gemini API. By setting `responseMimeType` to `"application/json"`, we are instructing the model to guarantee its output is valid JSON, which significantly improves the reliability of our JSON parser.

#### **3.5. Robust Parsing and Error Handling**
*   **`try-catch (e: Exception)`**: The entire logic is wrapped in a `try-catch` block. Any failure—from reading a file to a network error to a malformed API response—will be caught.
*   **`parseJsonResponse()`**: This private helper function:
    1.  **Cleans the String**: It removes the markdown fences (` ```json` and ```` `) that the model sometimes wraps around its JSON output.
    2.  **Uses Gson**: It uses a proper JSON library to deserialize the cleaned string directly into our `AiAnalysisDto` data class. If the JSON is invalid, Gson will throw a `JsonSyntaxException`, which is caught by the main `try-catch` block and returned as a `Result.failure`.

---

#### **Step 4: How to Use this Service in your ViewModel**

Here is an example of how you would call this unified function from your `MemoryViewModel`.

```kotlin
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val geminiService: GeminiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<MemoryUiState>(MemoryUiState.Idle)
    val uiState: StateFlow<MemoryUiState> = _uiState

    fun createMemory(imageUri: String?, audioUri: String?, userText: String?) {
        // Guard clause: ensure at least one input is provided
        if (imageUri == null && audioUri == null && userText.isNullOrBlank()) {
            _uiState.value = MemoryUiState.Error("At least one input is required.")
            return
        }

        viewModelScope.launch {
            _uiState.value = MemoryUiState.Loading

            val result = geminiService.processMemory(
                imageUri = imageUri,
                audioUri = audioUri,
                userText = userText
            )

            result.onSuccess { aiAnalysis ->
                // TODO: Save the aiAnalysis and user inputs to the Room database here
                _uiState.value = MemoryUiState.Success(aiAnalysis)
            }.onFailure { exception ->
                _uiState.value = MemoryUiState.Error(exception.message ?: "An unknown error occurred.")
            }
        }
    }
}

// Sealed interface to represent the UI State
sealed interface MemoryUiState {
    object Idle : MemoryUiState
    object Loading : MemoryUiState
    data class Success(val analysis: AiAnalysisDto) : MemoryUiState
    data class Error(val message: String) : MemoryUiState
}
```
This example demonstrates a complete, robust, and clean implementation pattern. The UI observes the `uiState` and can show a loading indicator, the success message, or a detailed error message, all driven by the `Result` from your unified `GeminiService`.