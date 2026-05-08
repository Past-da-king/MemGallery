# MemGallery v0.3.1 — Lossless backup/restore

## ✨ Highlights

- 📦 **Backup format v2.** Export now includes everything: memories, tasks, collections, and the memory↔collection links. Restoring on a fresh install brings back your full state.
- 🔁 **Tasks recovered from old backups.** When importing a v1 backup (any export from v0.2.0 or v0.3.0), the importer reconstitutes AI-detected tasks from each memory's stored `aiActions`. So even users coming from older versions get their events/to-dos back.
- 🛡️ **ID-safe imports.** Foreign keys (`task.memoryId`, cross-ref pairs) are remapped to the new auto-generated IDs after insert. Orphaned rows whose references can't resolve are skipped and counted in logcat instead of corrupting the database.
- 🪪 **Manifest at the root of the zip.** Future format bumps can branch off `manifest.json`'s `format` field without breaking older importers.

## 💾 Backup file layout (v2)

```
MemGallery-backup.zip
├── manifest.json              ← { format: 2, exportedAt, appVersion }
├── memories.json              ← MemoryEntity[]
├── tasks.json                 ← TaskEntity[]
├── collections.json           ← CollectionEntity[]
├── cross_refs.json            ← MemoryCollectionCrossRef[]
└── assets/
    ├── images/
    └── audio/
```

## 🔁 Upgrading from v0.2.0 / v0.3.0

Same flow as before — and now collections + tasks come along too (with the caveats below).

1. **On the old version**, Settings → **Backup and restore your memories** → **Export Backup**. Save outside the app sandbox (Downloads, Drive, etc.).
2. **Uninstall** the old app.
3. **Install** `MemGallery-v0.3.1.apk` from this release.
4. Onboarding (Skip is fine).
5. Settings → **Import Backup** → pick the file.

> 📌 **From v0.3.1+ exports:** memories, tasks (approved + unapproved), collections, and links all carry over.
> 📌 **From v0.3.0 / v0.2.0 exports:** memories carry over normally. **AI-detected tasks** (events / to-dos / reminders) are reconstituted from each memory's stored `aiActions` and reappear approved. **Manually-created standalone tasks and collections were never in the old format and cannot be recovered** — recreate them by hand.

## 📦 Install

Download `MemGallery-v0.3.1.apk` from the [release page](https://github.com/Past-da-king/MemGallery/releases/tag/v0.3.1).

> Same signing key as v0.3.0 — if you're already on v0.3.0, this is a normal in-place update. v0.2.0 users still need the uninstall-then-install dance per the upgrade guide above.

## Requirements

- Android 7.0+ (API 24)
- Google Gemini API key (free from [Google AI Studio](https://aistudio.google.com/)), or a Groq / OpenAI-compatible key.
