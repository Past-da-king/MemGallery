# 🚀 MemGallery v0.1.0: The Augmented Mind Release

This major update brings MemGallery from its experimental roots into a more stable, production-ready "Second Brain" experience. We've overhauled the AI engine, unified the rendering system, and polished the user experience across the board.

## 🌟 Major Highlights

### 🧠 Unified AI Rendering Engine
We've completely rebuilt how the app understands and displays AI responses. 
- **The Fix**: Resolved the critical "No scheme-handler" crash that prevented images from loading in the chat.
- **The Tech**: A unified `MarkdownText` component now handles `file://`, `content://`, and `network` URIs seamlessly, ensuring your memory snapshots are always visible during AI interactions.

### 🛡️ Smart Permission Flow
Microphone access is now handled proactively and with transparency.
- **Bottom Sheets**: Instead of system-level popups, we now use integrated bottom sheets to explain *why* we need access before you start recording.
- **Coverage**: Applied to both the **Quick Audio Note** and **AI Chat** interfaces.

### ⚡ Performance & Physics
The Gallery now feels faster and more responsive.
- **Smooth Scrolling**: Implemented `derivedStateOf` logic to optimize recomposition. The search bar now toggles with buttery smoothness.
- **Background Stability**: Wrapped foreground service calls in robust error handling to prevent crashes on Android 12+ devices during indexing.

## 🛠️ Complete Bug Fixes & Improvements

### AI & Tools
- **OpenAI Compatibility**: Fixed a loop logic bug in the `OpenAICompatibleProvider` that caused tool-calling failures.
- **Thinking Process**: Improved the `ThinkingProcessAccordion` to handle streaming content without UI flickering.
- **Calendar Sync**: Verified and stabilized the task synchronization with system calendars.

### System & Core
- **Ghost Updates**: Removed hardcoded version strings from logs. The app now dynamically reports its version (`BuildConfig.VERSION_NAME`).
- **Audio Engine**: Standardized recording at `44.1kHz` and `128kbps` for universal device compatibility and better AI transcription results.

### Web & Branding
- **v0.1.0 Official**: Updated the landing page and app metadata to reflect the v0.1.0 milestone.

---

**Download the APK below to experience the next level of personal AI.**
