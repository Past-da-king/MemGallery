# MemGallery v0.0.8

## New Features

### Multiple Image Selection
- Select multiple images when creating a memory
- Swipe through all images in Memory Detail view
- Image carousel with page indicators

### AI Chat Improvements
- Full database schema access for Chat AI
- Query memories, tasks, and collections tables
- Web Search tool for external queries
- Current date/time context for task scheduling

## Bug Fixes

- **Fixed: Multiple images saving as duplicates** - All selected images now save with unique filenames
- **Fixed: Chat AI missing system prompt** - AI now receives complete database schema
- Removed crashing camera feature from attachment sheet
- Fixed double attachment sheets bug
- Improved PDF display with proper icon
- AI messages now full-width

## Notes

> **AI Database Access**: Currently READ-ONLY. The AI can query but cannot modify data. Updates & deletes coming soon!
