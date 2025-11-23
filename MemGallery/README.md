# MemGallery 🎨

MemGallery is a smart, private, AI-powered "second brain" for your memories. It allows you to capture, process, and recall everything that's important to you, from photos and voice notes to web links and screenshots.

Harnessing the power of Google's Gemini AI, MemGallery automatically analyzes your content to provide titles, summaries, searchable tags, and even suggests actionable items like creating calendar events or to-do list items.

## ✨ Features

- **Multi-Modal Capture**: Save memories using:
  - **Text Notes**: Quickly jot down thoughts.
  - **Images**: Upload from your gallery or take a photo directly.
  - **Audio**: Record voice notes and memos.
  - **Bookmarks**: Save web links for later.
- **AI-Powered Analysis**: Your content is automatically processed to generate:
  - Concise Titles & Summaries
  - Exhaustive, Searchable Tags
  - Detailed Image and Audio Transcriptions
  - Actionable Suggestions (Events, To-Dos, Reminders)
- **Quick Capture**:
  - **Overlay**: An overlay service lets you create a memory from anywhere in the OS.
  - **Quick Settings Tile**: A dedicated tile to instantly launch the capture overlay.
  - **Screenshot Detection**: Automatically detects and offers to save new screenshots.
- **Integrated Task Manager**: View and manage all AI-generated tasks and events in one place.
- **Advanced Customization**:
  - **Theming**: Supports system-wide dark/light mode, Dynamic Color (Android 12+), custom accent colors, and an AMOLED-friendly pure black mode.
  - **Custom AI Instructions**: Fine-tune the AI's behavior and tone with your own system prompts in the Advanced Settings.
- **Secure & Private**:
  - Your data is stored locally on your device.
  - Sensitive information like API keys are stored securely using Android's `EncryptedSharedPreferences`.

## 🛠️ Setup

1.  **Clone the repository.**
2.  **Open in Android Studio** (latest stable version recommended).
3.  **Get a Gemini API Key**:
    - Visit [aistudio.google.com](https://aistudio.google.com).
    - Sign in and create a new API key.
    - Launch the app for the first time and complete the onboarding process by entering your API key when prompted.
4.  **Build and run** the app.

## ✍️ Signing

The project is configured to use a `release-key.jks` file for release builds. Passwords and key aliases are loaded from your project's `local.properties` file:

```properties
# In local.properties
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```
This ensures your signing keys are kept secure and are not checked into version control.