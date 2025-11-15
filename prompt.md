### **Master Prompt v2.0: "MemGallery" - Complete Android Implementation**

**[BEGIN DIRECTIVE]**

**Your Role:** You are hereby designated as the Lead Android Architect and Developer for Project "MemGallery". Your assigned task is the ground-up, production-quality implementation of this application using an expert-level understanding of Kotlin, Jetpack Compose, Material 3, and modern Android architecture.

**Your Mission:** To write the **complete, final, and fully functional** source code for the "MemGallery" application based on the provided documentation. Failure to adhere to any directive in this prompt will result in mission failure.

---

### **Directive Zero: The Prime Directive - Full Knowledge Ingestion**

Before any code is written, you must first perform a complete and thorough analysis of the entire project dossier. You are to ingest, parse, and commit to memory the following three artifacts:

1.  **`Project_Specification.md`:** This is the **sacred text**. It is your single source of truth for all functional requirements, the exact schema for the `AiAnalysisDto` and `MemoryEntity` data models, and the comprehensive UI/UX flows. Every feature described within must be implemented.
2.  **`Implementation_Guide.md`:** This document contains the canonical, non-negotiable implementation pattern for the core AI logic within `GeminiService.kt`. The unified `processMemory` function, its type-safe `Result`-based signature, and its dynamic prompt-building logic are to be replicated with perfect fidelity.
3.  **UI Mockup Assets (`/ui_mockups` directory):** This folder contains the visual contract. You will analyze the PNG images and the static `index.html` to understand the precise layout, spacing, typography, and component states. The final UI must be a pixel-perfect, fully interactive implementation of these mockups.

**Acknowledge completion of this directive before proceeding.**

---

### **The Core Mandates: Unbreakable Development Rules**

Your entire development process will be governed by the following five mandates. These are not suggestions; they are absolute requirements.

*   **Mandate I: The Principle of Bottom-Up Construction.** You will build this application from the lowest level of abstraction upwards. The dependency graph must be respected at all times. The build order is strictly: **Data Layer -> Service/Repository Layer -> Dependency Injection Layer -> Business Logic (ViewModel) Layer -> Presentation (UI) Layer -> Final Assembly**. No component may be written until its underlying dependencies have been fully implemented.
*   **Mandate II: The Principle of Absolute Completeness (Zero Tolerance for TODOs).** Every line of code you write must be final, functional, and production-ready. The use of `// TODO`, placeholder comments, empty function bodies, hardcoded/mock data (outside of specified error fallbacks), or any form of incomplete logic is strictly forbidden and will be considered a critical failure.
*   **Mandate III: The Principle of Logical Inference & Gap Filling.** The provided specification is comprehensive, but if you identify a logical gap in the user flow (e.g., a missing settings screen implied by the "API Key Management" page, or a user profile screen implied by the gallery's profile icon), you are authorized and **required** to implement it. The inferred screen/feature must seamlessly integrate with the existing application and strictly adhere to the established Material 3 design language and MVVM architecture.
*   **Mandate IV: The Principle of Architectural Purity.** The application architecture is non-negotiable:
    *   **Language:** Kotlin
    *   **UI:** Jetpack Compose with Material 3
    *   **Architecture:** MVVM
    *   **Dependency Injection:** Hilt
    *   **Database:** Room
    *   **Concurrency:** Kotlin Coroutines and `Flow`
*   **Mandate V: The Principle of UI Fidelity & Dynamism.** The UI must be a pixel-perfect match for the mockups. It must also fully implement the Material You dynamic theming engine. Colors for key components (buttons, highlights, etc.) must adapt to the user's system wallpaper. The default font for the platform must be used as specified.

---

### **The Implementation Blueprint: A Granular, Step-by-Step Construction Plan**

You will now commence writing the code, adhering to the following file-by-file, bottom-up sequence.

**Module 1: Project & Gradle Setup**
1.  **File:** `build.gradle.kts` (Project Level) - Configure repositories and plugins for Kotlin, Hilt, and Compose.
2.  **File:** `build.gradle.kts` (App Level) - Declare the complete list of dependencies: Compose BOM, UI, Material 3; Coroutines; ViewModel KTX; Hilt; Room (runtime, ktx, compiler); Google Gemini SDK; Gson; Coil (for Compose).

**Module 2: Data Persistence Layer (`/data/local`)**
1.  **File:** `entity/MemoryEntity.kt` - Define the Room `@Entity` with perfect fidelity to the specification.
2.  **File:** `converters/TagListConverter.kt` - Implement the Room `TypeConverter` to handle `List<String>` persistence for the `aiTags` field.
3.  **File:** `dao/MemoryDao.kt` - Define the DAO interface with `@Insert`, `@Update`, `@Delete`, and `@Query` functions for all required database operations. `getAllMemories()` must return `Flow<List<MemoryEntity>>`.
4.  **File:** `database/AppDatabase.kt` - Define the abstract Room Database class, linking the entity and the converter.

**Module 3: Data Access & Service Layer (`/data`)**
1.  **File:** `remote/dto/AiAnalysisDto.kt` - Define the type-safe data class for the Gemini API response.
2.  **File:** `remote/GeminiService.kt` - Implement the Hilt `@Singleton`. This class must contain the unified `processMemory` function as detailed in the `Implementation_Guide.md`.
3.  **File:** `repository/MemoryRepository.kt` - Implement the repository as a Hilt `@Singleton`. It will depend on `MemoryDao` and `GeminiService`. It will abstract all data operations, providing clean functions for the ViewModels to call (e.g., `fun createNewMemory(...)`, `fun getMemories(): Flow<...>`).

**Module 4: Dependency Injection Layer (`/di`)**
1.  **File:** `MemGalleryApplication.kt` - The main application class annotated with `@HiltAndroidApp`.
2.  **File:** `AppModule.kt` - The Hilt module to provide singleton instances of the `AppDatabase`, `MemoryDao`, and `MemoryRepository`.

**Module 5: Business Logic Layer (`/ui/viewmodels`)**
1.  **File:** `GalleryViewModel.kt` - Annotated with `@HiltViewModel`. It will inject the `MemoryRepository`. It must expose a `StateFlow` for the list of memories and handle the logic for search and filtering by updating its state.
2.  **File:** `MemoryCreationViewModel.kt` - Manages the state for creating a new memory. It will hold temporary URIs and text, and will call `memoryRepository.createNewMemory(...)`, exposing the loading/success/error state via a `StateFlow`.
3.  *(As per Mandate III)* **File:** `ApiKeyViewModel.kt` - This ViewModel will handle the logic for the "API Key Management" screen. It will have functions to `validateKey(key: String)` and `saveKey(key: String)`, interacting with a secure storage mechanism (e.g., EncryptedSharedPreferences, provided in a new repository).

**Module 6: Presentation Layer (`/ui`)**
1.  **File:** `theme/Theme.kt`, `Color.kt`, `Type.kt` - Implement the Material 3 theme. `Theme.kt` must enable and correctly configure `dynamicDarkColorScheme` and `dynamicLightColorScheme`.
2.  **File:** `components/MemoryCard.kt`, `components/AudioPlayer.kt`, etc. - Create all small, reusable Composables identified from the mockups.
3.  **File:** `screens/GalleryScreen.kt` - Implement the main gallery UI. It will collect the state from `GalleryViewModel`. It must contain the `SearchBar`, `FilterChip` row, and `LazyVerticalGrid`. The `+` FAB's `onClick` will trigger the `ModalBottomSheet`.
4.  **File:** `screens/MemoryCreationScreen.kt` - Implements the multi-step memory creation flow, collecting state from the `MemoryCreationViewModel`. This screen will be responsible for launching the image picker and navigating to the audio recorder.
5.  **File:** `screens/MemoryDetailScreen.kt` - Implements the detailed view of a single memory.
6.  **File:** `screens/ApiKeyManagementScreen.kt` - Implements the UI for API key entry and validation.

**Module 7: Final Assembly (`/`)**
1.  **File:** `navigation/AppNavigation.kt` - Define a sealed class for navigation routes and create the `NavHost` Composable with all screen destinations.
2.  **File:** `MainActivity.kt` - The main entry point. It will set the theme and host the `AppNavigation` Composable.

---

**Final Output Protocol**

*   You will present the code on a file-by-file basis.
*   For each file, you will state its full, absolute path.
*   You will then provide the complete, final source code for that file.
*   You will proceed sequentially through the modules as laid out in this blueprint.

Your directive is clear. Acknowledge and begin execution.

**[END DIRECTIVE]**