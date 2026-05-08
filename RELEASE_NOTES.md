# MemGallery v0.3.0 — Chat reliability + UI polish

## ✨ Highlights

- 🤖 **Chat actually works.** Fixed the silent "1 chunk, 0 chars" failures across every Gemini model. Routes through non-streaming `generateContent` so AutomaticFunctionCalling reliably executes the agent's tools.
- 🎛️ **Live Gemini model picker** — dropdown fetches the current list of models from Google's API. Default `gemini-3.1-flash-lite`, swap freely without rebuilding.
- 🌐 **OpenAI-compatible provider** — connect OpenRouter, OpenAI, Together AI, or any compatible endpoint via the new third option in onboarding (base URL + model + key).
- 🎨 **Themed launcher icon** (Material You). Toggle "Themed icons" in your launcher to retint the app to your wallpaper colour.

## 💬 Chat & agent fixes

- Tool registration: `getDatabaseSchema` now wired for the Gemini provider (was only registered for Groq/OpenAI-compatible).
- `webSearch` lazy-initialises its client on first use, so it works on cold start with a saved API key instead of returning "Web search unavailable".
- Streaming flows now run on `Dispatchers.IO` — fixes `NetworkOnMainThreadException` that was surfacing as "Error: null".
- Empty model responses surface as visible errors with a "try a different model" nudge instead of silent stalls.
- **Save chat as memory** finally produces useful summaries — the AI worker was previously processing chat memories with all-null inputs.
- `maxToolCalls` setting now actually flows through to both the system prompt and the function-calling config (was hardcoded to 3 in four places).

## 🪄 Onboarding

- Animated welcome page: eyebrow caption, soft halo behind the hero, staggered fade-in.
- Expanding-pill page indicator (replaces the old dots).
- **Skip** button on every page — no longer forces you to validate an API key before continuing.
- New OpenAI-compatible flow with full base-URL/model/key form.

## 📅 Tasks

- "Show all upcoming" pill appears under the calendar strip when a date is selected, so you're not trapped behind the date filter.

## 💎 Chat history sheet

- Letter-avatar rows, "Active" pill badge for the current chat, soft selection mode (no more red bar), relative timestamps, empty state.

## 🐛 Fixes

- `gemini-2.5-flash` literal hardcode dropped — model is fully user-configurable from settings.
- Onboarding API-key page no longer uses the `Check` icon as a stand-in for `Key`.

## 📦 Install

Download `MemGallery-v0.3.0.apk` from the [release page](https://github.com/Past-da-king/MemGallery/releases/tag/v0.3.0).

> ⚠️ **Heads up:** this release is signed with a fresh signing key. Existing v0.2.0 installs cannot upgrade in-place — uninstall the previous version first, then install this APK. Future releases will be signature-stable from this version forward.

## 🔁 Upgrading from v0.2.0 — keep your memories

Because v0.3.0 is signed with a new key, Android won't replace the old app for you, and uninstalling normally wipes its data. Use the built-in **Backup** feature to carry your memories across:

1. **On v0.2.0**, open **Settings → Backup and restore your memories → Export Backup**. Pick a location outside the app sandbox (Downloads, Drive, etc.) — that file survives the uninstall.
2. **Uninstall** the v0.2.0 app.
3. **Install** `MemGallery-v0.3.0.apk` from this release.
4. Complete onboarding (tap **Skip** if you don't want to enter the API key right now — you can do it later in Settings).
5. **Settings → Backup and restore your memories → Import Backup**, point at the file from step 1. Your memories and attached images/audio reappear.

> 📌 **What carries over:** memory entries (titles, summaries, tags, AI analyses, transcriptions, bookmarks) and their attached images/audio.
> 📌 **What does not yet carry over:** tasks (events/to-dos) and collections — those live in separate database tables that the current backup format doesn't include. You'll need to re-create them, or AI-generated tasks will reappear as you re-process memories.

## Requirements

- Android 7.0+ (API 24)
- Google Gemini API key (free from [Google AI Studio](https://aistudio.google.com/)), or a Groq / OpenAI-compatible key.
