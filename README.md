# MemGallery 🧠📸

<p align="center">
  <img src="images/icon.png" alt="MemGallery Icon" width="150"/>
</p>

<p align="center">
  <strong>Your External Brain for Memories</strong><br>
  Capture, Process, and Recall Everything with AI
</p>

<p align="center">
  <a href="https://github.com/Past-da-king/MemGallery/releases">
    <img src="https://img.shields.io/badge/Download-Alpha%20v0.0.6-8C25F4?style=for-the-badge&logo=android&logoColor=white" alt="Download Alpha v0.0.6">
  </a>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#why-memgallery">Why MemGallery?</a> •
  <a href="#how-it-works">How It Works</a> •
  <a href="#installation">Installation</a> •
  <a href="#setup">Setup</a> •
  <a href="#usage">Usage</a> •
  <a href="#technology-stack">Tech Stack</a>
</p>

---
## 📱 App Showcase

<p align="center">
  <img src="images/banner1 (1).png" alt="MemGallery UI - Organize & Actionable" width="100%"/>
</p>

<p align="center">
  <img src="images/banner1 (2).png" alt="MemGallery UI - Seamless Workflow" width="100%"/>
</p>

---


## 🎯 What is MemGallery?

**MemGallery** is a smart memory bank that allows you to store all your memories from audio, images, text, and URLs for you to remember later. It uses AI to index it, and the AI can be either local or provided. Most of everything is on-device, no cloud required, and no sign-up required.

---

## ✨ Features

### 📷 **Multi-Modal Capture**
- **Screenshot Auto-Indexing**: Automatically detects and processes new screenshots
- **Web Bookmarks**: Save and analyze web links with smart summaries
- **Camera Capture**: Take photos directly within the app
- **Audio Recording**: Record voice notes with AI transcription
- **Text Input**: Quickly jot down thoughts or save copied text
- **Share Integration**: Save content from any app via Android's Share Sheet

### 🤖 **AI-Powered Intelligence**
- **Contextual Analysis**: Gemini AI reads images, transcribes audio, and summarizes web pages
- **Smart Tagging**: Automatically generates relevant tags for organization
- **Action Detection**: Identifies events, to-dos, and reminders from your content
- **Custom AI Persona**: Configure how the AI talks and analyzes your data

### 🗓️ **Task Management**
- **Integrated Task Manager**: View all AI-detected to-dos and events in one place
- **Calendar View**: Browse your tasks and memories by date
- **Smart Reminders**: Get notified about upcoming deadlines and events found in your memories

### 🔍 **Search & Organization**
- **Semantic Search**: Find memories by describing them (e.g., "receipt for coffee", "notes from meeting")
- **Smart Filters**: Quickly sort by Images, Notes, Audio, or Bookmarks
- **Highlights**: Rediscover past memories with random highlights

### 🎨 **Premium Customization**
- **Material Design 3**: Beautiful, modern UI that adapts to your device
- **Dynamic Theming**: Matches your system wallpaper colors (Android 12+)
- **AMOLED Mode**: True black theme for battery saving on OLED screens
- **Custom Accents**: Choose your preferred app color

---

## 🤔 Why MemGallery?

### The Problem
We encounter hundreds of pieces of information daily—screenshots, conversations, articles, reminders. Traditional galleries and note apps treat these as disconnected files. Finding that one specific screenshot or link from last week is often a hassle.

### The Solution
MemGallery transforms your Android device into an intelligent memory system that:

1. **Never Forgets**: Automatically captures screenshots and organizes web links
2. **Understands Context**: AI reads and comprehends the content of your images and audio
3. **Makes Everything Searchable**: Find anything using natural language
4. **Extracts Actionable Items**: Automatically detects events and adds them to your task list
5. **Works Offline-First**: Your data stays on your device until you choose to process it

---

## ⚙️ How It Works

```
1. CAPTURE
   ├─ Take a screenshot (auto-detected)
   ├─ Share a link from Chrome
   ├─ Record a voice note
   └─ Snap a photo

2. PROCESS (AI)
   ├─ Gemini analyzes content
   ├─ Generates title, summary & tags
   ├─ Extracts events & to-dos
   └─ Indexes for search

3. RECALL & ACT
   ├─ Search using natural language
   ├─ View detected tasks in Calendar
   └─ Receive smart notifications
```

---

## 📥 Installation

### Option 1: Download APK (Recommended)
1. Go to the [Releases](../../releases) page
2. Download the latest `MemGallery-v*.apk` file
3. Open the APK on your Android device

**Minimum Requirements:**
- Android 7.0 (API 24) or higher
- Internet connection for AI processing
- ~50MB storage space

---

## 🔑 Setup

### 1. Get a Google AI Studio API Key

MemGallery requires a **Google Gemini API key** for AI processing. It's free for most personal use cases.

1. Visit [Google AI Studio](https://aistudio.google.com/)
2. Sign in with your Google account
3. Click **"Get API Key"** -> **"Create API Key"**
4. Copy the key

### 2. Configure MemGallery

1. Launch MemGallery and complete the onboarding
2. Paste your **API Key** when prompted
3. Grant necessary permissions (Camera, Microphone, Notifications)
4. (Optional) Enable **"Auto-Index Screenshots"** in Settings

---

## 📱 Usage

### Capturing Memories

- **Screenshots**: Take a screenshot as usual. MemGallery will detect and process it automatically.
- **Web Bookmarks**: In your browser, tap **Share** -> **MemGallery** to save and analyze the link.
- **Quick Capture**: Use the **+** button or long-press the app icon to add specific types (Audio, Text, Photo).

### Task Management

- Navigate to the **Tasks** tab (if enabled in Settings).
- View AI-detected tasks and events from your memories.
- Use the **Calendar** view to see your schedule at a glance.

### Customization

- Go to **Settings** -> **Appearance** to toggle **AMOLED Mode** or change the **App Theme**.
- Use **Advanced Settings** to provide a custom system prompt for the AI (e.g., "Be concise", "Focus on technical details").

---

## 🛠️ Technology Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **AI**: Google Gemini 2.5 Flash via Google Gen AI SDK
- **Database**: Room
- **Background**: WorkManager & Foreground Services
- **Web Parsing**: Jsoup

---

## 🔒 Privacy

- **Local Storage**: All memories and tasks are stored locally on your device.
- **Secure AI**: Content is sent to Google's Gemini API only for analysis and is not used to train public models (enterprise-grade privacy).
- **Encrypted Keys**: Your API key is stored securely using Android's EncryptedSharedPreferences.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Made with ❤️ for people who want to remember everything
</p>
