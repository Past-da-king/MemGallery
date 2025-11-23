# How to Publish Release v0.0.3

The "Releases" page on GitHub is empty because **you need to manually create the release entry**. I have prepared all the files, but I cannot log in to your GitHub account to click the button for you.

Follow these steps to fill that page:

## 1. Locate the APK
I have already built the signed APK. It is located on your computer at:
`c:\Users\past9\OneDrive\Desktop\project\FFF\release\MemGallery-v0.0.3.apk`

## 2. Create the Release on GitHub
1.  Go to your Releases page: [https://github.com/Past-da-king/MemGallery/releases](https://github.com/Past-da-king/MemGallery/releases)
2.  Click the green **"Create a new release"** button (as seen in your screenshot).
3.  **Choose a tag**: Click the dropdown, type `v0.0.3`, and click "Create new tag".
4.  **Release title**: Enter `Alpha v0.0.3`.
5.  **Describe this release**: Copy and paste the text below:

    ```markdown
    ## 🚀 Alpha v0.0.3 Release

    First public alpha release of MemGallery!

    ### ✨ Features
    *   **Smart Capture**: Auto-detects screenshots and processes them.
    *   **AI Powered**: Uses Gemini 2.5 Flash for analysis.
    *   **Semantic Search**: Search by description.
    *   **Privacy**: Local storage with secure AI processing.

    ### 📥 Installation
    Download the `MemGallery-v0.0.3.apk` file below and install it on your Android device.
    ```

6.  **Attach binaries**: Drag and drop the `MemGallery-v0.0.3.apk` file (from step 1) into the "Attach binaries by dropping them here..." box.
7.  Click **"Publish release"**.

## 3. Done!
Once you click publish, the link in the README and index.html will work perfectly.
