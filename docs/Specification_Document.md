# Complete Feature Specification Document

## 1. Automatic Task Approval System

### Overview

Automatically created tasks should no longer be instantly added to the user's main task list. Instead, users must explicitly approve them. This prevents clutter and gives users full control over which auto-generated tasks actually matter.

### Functionality
(note this   section i  refere to action as taks  but im do not mean  just task i mean  task , event and reminders)
* The AI continues generating tasks in the background as usual.
* Generated tasks remain **unapproved** until the user reviews them.
* When the user opens the **Task Page**, a **bottom‑up sheet** automatically appears if new tasks exist.

### Bottom-Up Approval Sheet

* Displays all unapproved auto-created tasks.
* User can:

  * Approve selected tasks.
  * Approve all tasks.
  * Delete selected tasks.
  * Delete all tasks.
* Only after approval do tasks appear in the main task list.

---

## 2. Full Image Viewing Inside Memory Details

### Overview

Users currently only see small image previews in the memory details sheet. The new interaction allows users to pull down the existing **bottom‑up sheet** to reveal a full-screen image.

### Functionality

* Same bottom-up sheet is used—no new component.
* User drags the **bottom-up sheet downward**.
* As it moves down, the **full image behind it becomes visible**.
* The sheet stops with a small **visible lip** so the user can pull it back up.
* Dragging the sheet back up returns to normal memory details view.

---

## 3. Collections System

### Overview

A complete system for creating, viewing, modifying, and auto-sorting collections inside the gallery.

---

### 3.1 Creating a Collection

* User enters the **Gallery page**.
* They long‑press or select memories (same UX as current delete mode).
* When selection mode is active, a **folder/collection icon** appears next to the delete icon.
* User taps the folder icon.
* A **bottom-up sheet** opens.

#### Bottom-Up Sheet Fields

* **Collection Name**
* **Collection Description**

  * Description is important for AI auto-classification.

#### After Submission

* The selected memories are added to the new collection.
* Collection is created and visible in the Collections category.

---

### 3.2 Collections Filter Chip

* A new **Collections** chip appears in the gallery filter row (next to "Image", "URL", "Audio", etc.).
* Tapping this chip shows all user-created collections.
* User taps a specific collection to view its contents.

---

### 3.3 Viewing a Collection

* Opens a gallery-like view scoped to that collection.
* Top-right corner shows a **plus (+) icon** instead of the settings icon.
* Plus icon = add more memories to this collection.

### 3.4 Adding Memories to an Existing Collection

* User taps the **+** icon.
* App displays **only memories NOT already in the collection**.
* User selects memories.
* Confirms to add them.

---

### 3.5 Removing Memories From a Collection

* Inside a specific collection, user long‑presses a memory.
* Enters selection mode.
* A **remove/delete** icon appears.
* User can select one or multiple memories.
* Removing a memory does **not** delete it from the app—only from that collection.

---

## 4. AI Integration With Collections

### Overview

The AI should be able to automatically sort new memories into collections based on their descriptions.

### Functionality

* When generating a memory, the AI is provided with:

  * List of collection names.
  * List of collection descriptions.
* These are combined into a formatted string and injected into the system prompt.
* The AI analyzes the new memory.
* If a collection description matches the content or context of the memory, the AI automatically assigns it to that collection.

---

# End of Feature Specification
