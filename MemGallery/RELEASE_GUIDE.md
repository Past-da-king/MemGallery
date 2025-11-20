# GitHub Release Instructions

## Creating a Release on GitHub

Follow these steps to create a release with a downloadable APK:

### 1. Build the Release APK

First, build a signed release APK:

```bash
# Navigate to project directory
cd MemGallery

# Build the release APK
./gradlew assembleRelease
```

The APK will be located at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

### 2. Sign the APK (If Not Signed)

If the APK is unsigned, you'll need to sign it:

```bash
# Generate a keystore (one-time setup)
keytool -genkey -v -keystore memgallery-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias memgallery

# Sign the APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore memgallery-keystore.jks app/build/outputs/apk/release/app-release-unsigned.apk memgallery
```

**Note**: For production, consider setting up signing in `build.gradle.kts` with proper keystore management.

### 3. Create a Git Tag

```bash
# Tag the current commit
git tag -a v1.0.0 -m "Release version 1.0.0"

# Push the tag to GitHub
git push origin v1.0.0
```

### 4. Create the GitHub Release

1. Go to your repository on GitHub
2. Click on **"Releases"** in the right sidebar
3. Click **"Draft a new release"**
4. **Choose a tag**: Select `v1.0.0` (or create it if not pushed yet)
5. **Release title**: `MemGallery v1.0.0 - Your External Brain`
6. **Description**: Write release notes (see template below)
7. **Upload APK**: Drag and drop your signed APK file
8. **Publish release**

### Release Notes Template

```markdown
# MemGallery v1.0.0 🧠📸

*Your External Brain for Memories*

## ✨ Initial Release Features

### 📷 Multi-Modal Capture
- Screenshot auto-indexing
- Camera capture
- Audio recording
- Text input
- Share from any app
- Text selection integration

### 🤖 AI-Powered Analysis
- Automatic content analysis with Google Gemini
- Smart descriptions and tagging
- Action detection (events, to-dos, reminders)

### 🔍 Intelligent Search
- Semantic search
- Filter by type (Images, Notes, Audio)
- Timeline browse

### 🔔 Smart Notifications
- Action alerts for detected events and to-dos
- Customizable notification filters
- Processing updates

### ⚡ Quick Actions
- App shortcuts for instant access
- Background sharing
- Expedited processing

## 📥 Installation

1. Download `MemGallery-v1.0.0.apk`
2. Allow installation from unknown sources
3. Install and launch
4. Follow the onboarding to set up your Google AI Studio API key

## 🔑 Setup

- **API Key Required**: Get a FREE API key from [Google AI Studio](https://aistudio.google.com/)
- **Permissions**: Grant Camera, Microphone, Media, and Notifications

## 📱 Requirements

- Android 7.0 (API 24) or higher
- Internet connection for AI processing
- ~50MB storage

## 🐛 Known Issues

None at this time

## 🙏 Feedback

Found a bug or have a suggestion? [Open an issue](https://github.com/YOUR_USERNAME/MemGallery/issues)

---

**Full Changelog**: Initial release
```

### 5. Alternative: Use GitHub CLI

If you have GitHub CLI installed:

```bash
# Create release with APK
gh release create v1.0.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "MemGallery v1.0.0 - Your External Brain" \
  --notes-file RELEASE_NOTES.md
```

## Tips

- **Version Naming**: Follow semantic versioning (MAJOR.MINOR.PATCH)
- **Changelog**: Keep detailed release notes for each version
- **APK Naming**: Rename APK to `MemGallery-v1.0.0.apk` for clarity
- **Pre-release**: Check "This is a pre-release" for beta versions
- **Assets**: You can attach multiple files (APK, source code, documentation)

## Updating Future Releases

1. Make changes and commit
2. Update version in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2
   versionName = "1.1.0"
   ```
3. Build new APK
4. Create new tag (`v1.1.0`)
5. Create new release on GitHub

That's it! Users can now download your app from the Releases page.
