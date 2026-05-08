# MemGallery 🎨 — The Augmented Intelligence Vault

[![Version](https://img.shields.io/badge/Version-0.0.8--alpha-blueviolet?style=for-the-badge)](https://github.com/Past-da-king/MemGallery)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](details.html#license)
[![Platform](https://img.shields.io/badge/Platform-Android_12+-black?style=for-the-badge&logo=android)](https://developer.android.com/about/versions/12)

**MemGallery** is an ultra-private, local-first intelligence engine designed to serve as a high-fidelity "second brain." It leverages multi-modal LLMs (**Gemini v2.0** and **Groq Maverick**) to index, reason over, and proactively manage your personal knowledge.

---

## 🏗️ Technical Architecture

MemGallery is built on a modern MVVM stack with a heavy emphasis on **AI Tool-Calling (Function Calling)**. Unlike traditional apps, the AI has a "live" window into your structured data.

### 🧠 Proactive Reasoning Engine
The AI Assistant uses a custom tooling layer (`ChatTools.kt`) to interact with the Room database. It can perform:
- **Semantic Retrieval**: Querying by concept rather than keyword.
- **Relational Filtering**: Accessing collections, tasks, and memory metadata.
- **Interactive Injection**: Programmatically requesting the UI to display memory cards.

#### Example Tool Interaction (JSON):
```json
{
  "call": "queryDatabase",
  "arguments": {
    "table": "memories",
    "filters": "{ \"search\": \"contract\", \"status\": \"ACTIVE\" }",
    "fields": "title, summary, imageUri"
  }
}
```

---

## ✨ Core Feature Set

- **⚡ Dual AI Ingestion**: Configure **Google Gemini** for high-reasoning tasks or **Groq (Llama-3)** for sub-second forensic analysis.
- **🎙️ Whisper Transcription**: Automated processing of `.m4a` and `.wav` voice memos with semantic tagging.
- **📸 Forensic Vision**: Detailed analysis of OCR text, screenshot context, and visual elements.
- **🛡️ The "Zero Cloud" Mandate**: NO telemetry, NO cloud sync, NO external trackers. All persistence is strictly local via SQLite.
- **✅ Intent-to-Task Logic**: Automatic detection of TODOs, EVENTs, and REMINDERs from natural language capture.

---

## 🚀 Professional Setup Guide

### 1. Requirements
- **JDK 17+**
- **Android Studio Koala+**
- **Android 12 (API 31)** or higher.

### 2. Environment Configuration
Create a `local.properties` file in the project root with your signing and token configuration:

```properties
# AI Provider Keys
# Get them from https://aistudio.google.com and https://console.groq.com
GEMINI_API_KEY=your_gemini_key
GROQ_API_KEY=your_groq_key

# CI/CD & Feedback (Optional)
GITHUB_TOKEN=your_github_token # For secure log reporting

# Signing Configuration
storePassword=your_password
keyAlias=your_alias
keyPassword=your_alias_password
```

### 3. Build & Deploy
```bash
./gradlew assembleRelease
```
*Note: For the best experience, ensure "Dynamic Color" is enabled in Android Settings to activate the Material You theme engine.*

---

## 📜 Legal & Attribution

MemGallery is open-sourced under the **MIT License**. We prioritize transparency and user sovereignty over data. 

- **Detailed Features & Architecture**: [Technical Deep-Dive](details.html)
- **Security Protocols**: [Privacy & Security Policy](details.html#privacy)
- **License Details**: [MIT Full Text](details.html#license)

---
*Powered by Artificial Intelligence. Controlled by Human Sovereignty.*
