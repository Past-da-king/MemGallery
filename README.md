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
    <img src="https://img.shields.io/badge/Download-Alpha%20v0.0.3-8C25F4?style=for-the-badge&logo=android&logoColor=white" alt="Download Alpha v0.0.3">
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

## 🎯 What is MemGallery?

**MemGallery** is an AI-powered Android application that acts as your personal memory assistant. It automatically captures, organizes, and makes searchable every piece of information you encounter—from screenshots to voice notes to shared content.

Think of it as **your second brain** that never forgets. MemGallery uses advanced AI (powered by **Google's Gemini 2.5 Flash**) to understand the content of your memories, extract actionable insights, and help you recall information instantly through intelligent search.

---

## ✨ Features

### 📷 **Multi-Modal Memory Capture**
- **Screenshot Auto-Indexing**: Automatically detects and processes screenshots
- **Camera Capture**: Take photos directly within the app
- **Audio Recording**: Record voice notes and memos
- **Text Input**: Quickly jot down thoughts
- **Share Integration**: Save content from any app via Android's Share Sheet
- **Text Selection**: Long-press any text → "Save to MemGallery"

### 🤖 **AI-Powered Processing**
- **Automatic Analysis**: Gemini AI analyzes images, audio, and text
- **Smart Descriptions**: Generates detailed, searchable descriptions
- **Action Detection**: Identifies events, to-dos, and reminders
- **Contextual Understanding**: Extracts meaning and context from your memories

### 🔍 **Intelligent Search & Organization**
- **Semantic Search**: Find memories by describing what you're looking for
- **Filter by Type**: Images, Notes, Audio
- **Timeline View**: Browse memories chronologically
- **Selection Mode**: Bulk select and delete memories

### 🔔 **Smart Notifications**
- **Action Alerts**: Get notified about detected events and to-dos
- **Customizable Filters**: Choose notification types (All, Events, To-Dos)
- **Processing Updates**: Real-time feedback when memories are being analyzed

### ⚡ **Quick Access**
- **App Shortcuts**: Long-press the app icon for instant actions:
  - Add Memory
  - Record Audio
  - Create Text Note
- **Background Sharing**: Silent save from other apps
- **Instant Processing**: Expedited background workers ensure fast analysis

### 🎨 **Premium Design**
- **Material Design 3**: Modern, beautiful UI
- **Dark Mode Support**: Easy on the eyes
- **Smooth Animations**: Polished, responsive interactions
- **Edge-to-Edge Display**: Immersive full-screen experience

---

## 🤔 Why MemGallery?

### The Problem
We encounter hundreds of pieces of information daily—screenshots, conversations, ideas, reminders. Traditional photo galleries and note apps treat everything as disconnected files. Finding that screenshot from last week? Good luck scrolling through hundreds of images.

### The Solution
MemGallery transforms your Android device into an intelligent memory system that:

1. **Never Forgets**: Automatically captures every screenshot without manual intervention
2. **Understands Context**: AI reads and comprehends your content
3. **Makes Everything Searchable**: Find anything by describing it in natural language
4. **Extracts Actionable Items**: Automatically detects events, tasks, and important dates
5. **Works in the Background**: No interruption to your workflow

### Perfect For:
- 📚 **Students**: Capture lecture slides, notes, and research
- 💼 **Professionals**: Save important emails, documents, and conversations  
- 🎯 **Project Managers**: Track tasks, deadlines, and meeting notes
- 🧑‍💻 **Developers**: Screenshot code snippets, error messages, and documentation
- 📝 **Content Creators**: Collect inspiration, references, and ideas
- 🌟 **Anyone** who wants to remember everything effortlessly

---

## ⚙️ How It Works

```
1. CAPTURE
   ├─ Take a screenshot (automatic detection)
   ├─ Share content from any app
   ├─ Record audio or take photo
   └─ Type a quick note

2. PROCESS (Automatic)
   ├─ AI analyzes the content
   ├─ Generates rich descriptions
   ├─ Extracts actionable items
   └─ Makes it searchable

3. RECALL
   ├─ Search by describing what you need
   ├─ Filter by type or date
   ├─ Get notifications for actions
   └─ Instantly access any memory
```

---

## 📥 Installation

### Option 1: Download APK (Recommended)
1. Go to the [Releases](../../releases) page
2. Download the latest `MemGallery-v*.*.*.apk` file
3. Open the APK on your Android device
./gradlew installDebug
```

**Minimum Requirements:**
- Android 7.0 (API 24) or higher
- Internet connection for AI processing
- ~50MB storage space

---

## 🔑 Setup

### 1. Get a Google AI Studio API Key

MemGallery requires a **Google Gemini API key** for AI processing. Don't worry—it's **completely free** for most users!

#### Steps to Get Your API Key:

1. Visit [Google AI Studio](https://aistudio.google.com/)
2. Sign in with your Google account
3. Click **"Get API Key"** in the top-right corner
4. Click **"Create API Key"**
5. Select your Google Cloud project (or create a new one)
6. Copy the generated API key

#### Free Tier Limits:
- ✅ 60 requests per minute
- ✅ 1,500 requests per day
- ✅ More than enough for personal use!

### 2. Configure MemGallery

On first launch, you'll go through a quick onboarding:

1. **Welcome**: Learn what Mem Gallery does
2. **API Key Setup**: Paste your Google AI Studio API key
3. **Permissions**: Grant necessary permissions:
   - 📸 Camera (for taking photos)
   - 🎤 Microphone (for audio recording)
   - 🖼️ Media (for screenshot auto-indexing)
   - 🔔 Notifications (for action alerts)
4. **How It Works**: Quick tutorial
5. **Get Started**: Start capturing memories!

### 3. Enable Screenshot Auto-Indexing (Optional)

Go to **Settings** → Toggle **"Auto-Index Screenshots"** ON

Now every screenshot you take will automatically be saved and processed!

---

## 📱 Usage

### Capturing Memories

#### Screenshot (Automatic)
Just take a screenshot normally. MemGallery will detect it automatically!

#### Camera
1. Open MemGallery
2. Tap the **+ button**
3. Select **Camera**
4. Take a photo

#### Share from Other Apps
1. In any app (browser, Twitter, etc.)
2. Tap **Share**
3. Select **MemGallery**
4. Content is saved instantly

#### Text Selection
1. Select any text in any app
2. Long-press the selection
3. Tap **"Save to MemGallery"**

#### Audio/Text Notes
- Long-press the MemGallery app icon
- Select **"Record Audio"** or **"Create Text Note"**

### Searching Memories

**Semantic Search**: Just describe what you're looking for!
- "Screenshot about coffee shop meeting"
- "Notes from yesterday's presentation"
- "Image with Python code"

**Filters**: Use filter chips to narrow down:
- All | Images | Notes | Audio

### Managing Memories

- **View Details**: Tap any memory card
- **Edit**: Tap the edit icon in detail view
- **Delete**: Long-press to enter selection mode, then tap delete
- **Bulk Actions**: Select multiple memories and delete at once

---

## 🛠️ Technology Stack

### Core
- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

### Architecture
- **Pattern**: MVVM (Model-View-ViewModel)
- **DI**: Hilt/Dagger
- **Async**: Coroutines + Flow

### UI
- **Framework**: Jetpack Compose
- **Design**: Material Design 3
- **Navigation**: Jetpack Navigation Compose
- **Theming**: Dynamic Color (Material You)

### Data & Storage
- **Database**: Room
- **Preferences**: DataStore
- **File Management**: MediaStore API

### Background Processing
- **Work Management**: WorkManager
- **Foreground Services**: Expedited workers for screenshots
- **Content Observation**: ContentObserver for screenshot detection

### AI & ML
- **AI Model**: Google Gemini 1.5 Flash
- **API**: Generative AI SDK
- **Capabilities**: Multimodal understanding (text + images)

### Networking
- **HTTP**: Retrofit + OkHttp
- **Serialization**: Kotlinx Serialization

### Utilities
- **Image Loading**: Coil
- **Permissions**: Accompanist Permissions
- **Logging**: Android Logcat

---

## 🔒 Privacy

- **Local Storage**: All memories are stored locally on your device
- **AI Processing**: Only the content you capture is sent to Google's Gemini API for analysis
- **No Third-Party Analytics**: We don't track or collect your data
- **API Key Security**: Your API key is stored securely using Android's DataStore

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 🐛 Bug Reports & Feature Requests

Found a bug or have an idea? Please open an issue on GitHub!

---

##  🙏 Acknowledgments

- **Google Gemini AI**: For powering the intelligent analysis
- **Material Design**: For the beautiful design system
- **Jetpack Compose**: For making Android UI development enjoyable

---

<p align="center">
  Made with ❤️ for people who want to remember everything
</p>

<p align="center">
  <strong>MemGallery - Your external brain, always remembering</strong>
</p>
