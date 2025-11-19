# Implementation Plan

This document outlines the plan to implement the following features:
1.  **Auto-indexing of new screenshots:** A feature to automatically detect and process new screenshots.
2.  **Edit functionality in Memory Detail:** Allow users to edit memories.

## 1. Auto-indexing of New Screenshots

This feature will allow the app to automatically detect and process new screenshots taken on the device.

### 1.1. Settings UI

-   **Create a new Settings Screen:** - **COMPLETED**
-   **Add a toggle for "Auto-index new screenshots":** - **COMPLETED**
-   **Add a button for "Index screenshots now":** - **PENDING**

### 1.2. Background Service/Receiver

-   **Create a `BroadcastReceiver` to listen for new screenshots:** - **COMPLETED** (using `ContentObserver`)
-   **Create a `ContentObserver` to monitor the `MediaStore`:** - **COMPLETED**
-   **Create a `ForegroundService` to process new screenshots:** - **COMPLETED** (Refactored to use `WorkManager` and integrated into `MemoryProcessingWorker`)

### 1.3. Data Flow

-   **Implemented** (Refactored to use `WorkManager` for processing)

## 2. Edit Functionality in Memory Detail

This feature will allow users to edit existing memories.

### 2.1. UI Changes

-   **Enable the "Edit" button in `MemoryDetailScreen`:** - **COMPLETED**
-   **Navigate to `PostCaptureScreen` in "edit mode":** - **COMPLETED**
-   **Modify `PostCaptureScreen` to support "edit mode": - **COMPLETED**
-   **Change the "Save" button to "Update":** - **COMPLETED**

### 2.2. Business Logic

-   **Create a new `MemoryUpdateViewModel`:** - **COMPLETED**
-   **Modify `MemoryRepository` to support updating a memory:** - **COMPLETED**
-   **Modify `MemoryProcessingService` to support re-indexing:** - **COMPLETED** (Refactored to use `WorkManager`)

### 2.3. Data Flow

-   **Partially Implemented**
