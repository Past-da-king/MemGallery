# 🐛 Next Release: Critical Bug Fixes & Feature Implementation Guide

**Target Release**: Monday Build (v0.0.6)  
**Priority**: CRITICAL  
**Status**: Ready for Implementation

---

## 📋 Table of Contents

1. [Critical Bug #1: Crash on Note Editing](#bug-1-crash-on-note-editing)
2. [Critical Bug #2: Duplicate Screenshot Generation](#bug-2-duplicate-screenshot-generation)
3. [Critical Bug #3: Duplicate Voice Note Creation](#bug-3-duplicate-voice-note-creation)
4. [Critical Bug #4: Onboarding Navigation Loop](#bug-4-onboarding-navigation-loop)
5. [Critical Bug #5: Permissions Logic Failure](#bug-5-permissions-logic-failure)
6. [Critical Bug #6: Auto-Indexing Failure](#bug-6-auto-indexing-failure)
7. [Critical Bug #7: Settings Button Hitbox](#bug-7-settings-button-hitbox)
8. [Critical Bug #8: Dark Mode Readability](#bug-8-dark-mode-readability)
9. [Feature #1: Manual Reminder Button](#feature-1-manual-reminder-button)
10. [Feature #2: Disable Auto-Reminders Toggle](#feature-2-disable-auto-reminders-toggle)
11. [Implementation Order](#implementation-order)

---

## 🔴 Bug #1: Crash on Note Editing

### Problem Description
**Symptom**: App crashes when clicking the Edit button on MemoryDetailScreen  
**Affected Files**: `PostCaptureScreen.kt`, `MemoryDetailScreen.kt`, `MemoryUpdateViewModel.kt`

### Root Cause Analysis

**Issue Location**: [PostCaptureScreen.kt:265-272](file:///c:/Users/past9/OneDrive/Desktop/project/FFF/MemGallery/app/src/main/java/com/example/memgallery/ui/screens/PostCaptureScreen.kt#L265-L272)

```kotlin
// PROBLEMATIC CODE
if (isEditMode) {
    val updatedMemory = memory!!.copy(  // ❌ CRASH HERE: memory is null
        userText = draftUserText,
        imageUri = draftImageUri,
        audioFilePath = draftAudioUri,
        bookmarkUrl = draftBookmarkUrl
    )
    updateViewModel.updateMemory(updatedMemory)
    navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
}
```

**Root Cause**: 
1. `memory` StateFlow is observed but may be `null` during composition
2. Navigation to edit mode passes `memoryId`, but memory loading is asynchronous
3. `LaunchedEffect(memory)` at line 96 only updates draft values when memory becomes non-null
4. Save button doesn't wait for memory to load before executing

### Fix Implementation

**File**: `PostCaptureScreen.kt`

#### Step 1: Add null safety check

```kotlin
// BEFORE (Line 263-277)
Button(
    onClick = {
        if (isEditMode) {
            val updatedMemory = memory!!.copy(
                userText = draftUserText,
                imageUri = draftImageUri,
                audioFilePath = draftAudioUri,
                bookmarkUrl = draftBookmarkUrl
            )
            updateViewModel.updateMemory(updatedMemory)
            navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
        } else {
            creationViewModel.createMemory()
        }
    },
    // ...
)

// AFTER (Safe version)
Button(
    onClick = {
        if (isEditMode) {
            memory?.let { existingMemory ->
                val updatedMemory = existingMemory.copy(
                    userText = draftUserText,
                    imageUri = draftImageUri,
                    audioFilePath = draftAudioUri,
                    bookmarkUrl = draftBookmarkUrl
                )
                updateViewModel.updateMemory(updatedMemory)
                navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
            } ?: run {
                // Memory not loaded yet - show error or disable button
                android.util.Log.e("PostCaptureScreen", "Attempted to update null memory")
            }
        } else {
            creationViewModel.createMemory()
        }
    },
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    shape = RoundedCornerShape(12.dp),
    contentPadding = PaddingValues(vertical = 12.dp),
    enabled = (isEditMode && memory != null) || (!isEditMode && uiState !is MemoryCreationUiState.Loading)
)
```

#### Step 2: Add loading indicator for edit mode

```kotlin
// Add after line 282
if (uiState is MemoryCreationUiState.Loading && !isEditMode) {
    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
} else if (isEditMode && memory == null) {
    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
} else {
    Icon(Icons.Default.AddCircle, contentDescription = "Save Now", modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(if (isEditMode) "Update" else "Save Now")
}
```

### Testing Checklist
- [ ] Navigate to memory detail screen
- [ ] Click edit button
- [ ] Verify no crash occurs
- [ ] Make edits and save successfully
- [ ] Verify changes persist

---

## 🔴 Bug #2: Duplicate Screenshot Generation

### Problem Description
**Symptom**: Single screenshot creates 4-7 duplicate entries  
**Affected File**: `ScreenshotObserver.kt`

### Root Cause Analysis

**Issue Location**: [ScreenshotObserver.kt:58-96](file:///c:/Users/past9/OneDrive/Desktop/project/FFF/MemGallery/app/src/main/java/com/example/memgallery/service/ScreenshotObserver.kt#L58-L96)

**Root Causes**:
1. **Multiple `onChange` triggers**: MediaStore fires multiple onChange events for a single screenshot:
   - Thumbnail creation
   - File metadata updates
   - Scanner completion
2. **Insufficient deduplication**: Current 5-second cooldown is per-URI, but same screenshot gets different URIs during processing
3. **Filename-based detection weakness**: `isScreenshot()` only checks filename, doesn't verify file uniqueness

### Fix Implementation

**File**: `ScreenshotObserver.kt`

```kotlin
// Add at class level (after line 55)
private val recentlyProcessedUris = mutableSetOf<Uri>()
private val recentlyProcessedPaths = mutableSetOf<String>() // NEW: Track by file path
private val handler = Handler(Looper.getMainLooper())

override fun onChange(selfChange: Boolean, uri: Uri?) {
    super.onChange(selfChange, uri)
    uri?.let {
        if (recentlyProcessedUris.contains(it)) {
            Log.d(TAG, "Ignoring already processed URI: $it")
            return
        }
        
        scope.launch {
            if (settingsRepository.autoIndexScreenshotsFlow.first()) {
                Log.d(TAG, "New media detected: $it")
                
                // NEW: Get file path for deduplication
                val filePath = getFilePathFromUri(it)
                if (filePath != null && recentlyProcessedPaths.contains(filePath)) {
                    Log.d(TAG, "Ignoring already processed file path: $filePath")
                    return@launch
                }
                
                if (isScreenshot(it)) {
                    recentlyProcessedUris.add(it)
                    
                    // NEW: Add file path to processed set
                    if (filePath != null) {
                        recentlyProcessedPaths.add(filePath)
                        handler.postDelayed({ recentlyProcessedPaths.remove(filePath) }, 10000) // 10-second cooldown
                    }
                    
                    handler.postDelayed({ recentlyProcessedUris.remove(it) }, 5000)

                    Log.d(TAG, "New screenshot detected: $it, enqueuing for processing.")
                    val inputData = Data.Builder()
                        .putString(MemoryProcessingWorker.KEY_IMAGE_URI, it.toString())
                        .build()

                    val workRequest = OneTimeWorkRequestBuilder<MemoryProcessingWorker>()
                        .setInputData(inputData)
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    workManager.enqueue(workRequest)
                    
                    handler.post {
                        android.widget.Toast.makeText(
                            context,
                            "Screenshot saved to MemGallery!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}

// NEW: Add helper function
private fun getFilePathFromUri(uri: Uri): String? {
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    return try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
            } else null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error getting file path from URI", e)
        null
    }
}
```

### Testing Checklist
- [ ] Take a screenshot
- [ ] Verify only ONE entry created
- [ ] Take multiple screenshots in rapid succession
- [ ] Verify correct number of entries
- [ ] Test with scrolling screenshot

---

## 🔴 Bug #3: Duplicate Voice Note Creation

### Problem Description
**Symptom**: Adding voice note to existing image creates new duplicate entry instead of updating original  
**Affected Files**: `AudioCaptureScreen.kt`, navigation logic

### Root Cause Analysis

**Issue**: When navigating to `AudioCapture` with existing `imageUri`, the screen saves as a NEW memory instead of updating the existing one.

**Expected Flow**:  
Image captured → Navigate to PostCapture → Add Audio → Update SAME memory

**Actual Flow**:  
Image captured → Memory saved → Navigate to AudioCapture → Audio saved as NEW memory

### Fix Implementation

**Solution**: Unify all capture flows through `PostCaptureScreen` before saving

#### File: `AudioCaptureScreen.kt` (Lines 150-160)

```kotlin
// BEFORE: Saves directly to repository
onSave = {
    viewModel.saveMemory() // Saves as new memory
}

// AFTER: Navigate to PostCaptureScreen with audio data
onSave = {
    val audioPath = viewModel.recordedFilePath.value
    navController.navigate(
        Screen.PostCapture.createRoute(
            imageUri = existingImageUri,
            audioUri = "file://$audioPath",
            userText = existingUserText,
            bookmarkUrl = existingBookmarkUrl
        )
    ) {
        popUpTo(Screen.Gallery.route) { inclusive = false }
    }
}
```

#### File: `TextInputScreen.kt`, `BookmarkInputScreen.kt`

Apply same pattern - navigate to PostCaptureScreen instead of saving directly.

### Testing Checklist
- [ ] Capture image
- [ ] Add audio from PostCaptureScreen
- [ ] Verify single memory with both image AND audio
- [ ] Test reverse: Audio first, then image
- [ ] Verify no duplicates

---

## 🔴 Bug #4: Onboarding Navigation Loop

### Problem Description
**Symptom**: On "Grant Permissions" screen, FAB button incorrectly jumps back to API validation screen

### Root Cause Analysis

**Issue Location**: [OnboardingScreen.kt:107-120](file:///c:/Users/past9/OneDrive/Desktop/project/FFF/MemGallery/app/src/main/java/com/example/memgallery/ui/screens/OnboardingScreen.kt#L107-L120)

```kotlin
if (pagerState.currentPage < 3) {
    FloatingActionButton(
        onClick = {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        },
        // ...
    )
}
```

**Root Cause**: All pages (0, 1, 2) show the same FAB that advances to next page, BUT:
- Page 1 (API validation) should disable FAB until API key is validated
- Page 2 (Permissions) should advance to page 3 (How It Works)

The issue is that pagerState can programmatically scroll backwards (e.g., from validation failures), causing confusion.

### Fix Implementation

**File**: `OnboardingScreen.kt`

```kotlin
// REPLACE Lines 107-120
val apiKeyUiState by viewModel.apiKeyUiState.collectAsState()

if (pagerState.currentPage < 3) {
    FloatingActionButton(
        onClick = {
            coroutineScope.launch {
                // Check if on API page and key not validated
                if (pagerState.currentPage == 1 && apiKeyUiState !is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Success) {
                    // Show toast: "Please validate your API key first"
                    android.widget.Toast.makeText(
                        context,
                        "Please validate your API key before continuing",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        },
        modifier = Modifier.align(Alignment.CenterEnd),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        // Disable FAB on API page if key not validated
        enabled = pagerState.currentPage != 1 || apiKeyUiState is com.example.memgallery.ui.viewmodels.ApiKeyUiState.Success
    ) {
        Icon(Icons.Default.ArrowForward, contentDescription = "Next")
    }
}
```

### Testing Checklist
- [ ] Complete onboarding from page 0 to 3
- [ ] Try skipping API validation - verify blocked
- [ ] Validate API key, then proceed
- [ ] Verify no backwards navigation

---

## 🔴 Bug #5: Permissions Logic Failure

### Problem Description
**Symptom**: App requires "Full File Access" - folder-only access to Screenshots doesn't work

### Root Cause Analysis

**Issue**: Android 13+ scoped storage requires `READ_MEDIA_IMAGES` permission, which grants access to ALL images, not individual folders. Folder selection via SAF (Storage Access Framework) is NOT the same as runtime permissions.

**Current Code** (OnboardingScreen.kt:316-320):
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
}
```

**The Issue**: `READ_MEDIA_IMAGES` is all-or-nothing. Users cannot grant "screenshots only" access via permissions dialog.

### Fix Implementation

**Solution**: Implement dual-mode permission handling:
1. **Preferred**: Request `READ_MEDIA_IMAGES` (all images)
2. **Fallback**: Use `ContentProvider` observer (doesn't need permission, but less reliable)

**File**: `OnboardingScreen.kt`

```kotlin
// ADD new text explaining permissions
Text(
    text = "Media Access allows auto-detection of screenshots.\\n\\nNote: Android requires access to all photos. Your privacy is protected - the app only processes screenshots.",
    style = MaterialTheme.typography.bodySmall,
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
)
```

**File**: `ScreenshotObserver.kt`

```kotlin
// ADD permission-less fallback observer
fun start() {
    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    
    if (hasPermission) {
        // Full MediaStore observer
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
        Log.d(TAG, "ScreenshotObserver started with full permissions")
    } else {
        // Fallback: Limited observer (may miss some screenshots)
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            false, // Not recursive
            this
        )
        Log.d(TAG, "ScreenshotObserver started in limited mode (no permission)")
    }
}
```

### Testing Checklist
- [ ] Grant full media access - verify works
- [ ] Deny permission - verify fallback mode activates
- [ ] Test screenshot detection in both modes
- [ ] Update onboarding text

---

## 🔴 Bug #6: Auto-Indexing Failure

### Problem Description
**Symptom**: First screenshot works, subsequent ones require app restart

### Root Cause Analysis

**Hypothesis**: ContentObserver gets garbage collected or unregistered after first trigger.

**Investigation needed in** `ScreenshotObserver.kt`:
1. Verify observer lifecycle (should persist across Application lifecycle)
2. Check for memory leaks causing early cleanup
3. Verify WorkManager isn't canceling the observer

### Fix Implementation

**File**: `MemGalleryApplication.kt`

```kotlin
// VERIFY current code (Lines 19-30)
@Inject
lateinit var screenshotObserver: ScreenshotObserver

override fun onCreate() {
    super.onCreate()
    screenshotObserver.start() // ✓ This should keep observer alive
    enqueueMemoryProcessingWorker()
}
```

**ADD lifecycle logging in ScreenshotObserver.kt**:

```kotlin
private var isStarted = false

fun start() {
    if (isStarted) {
        Log.w(TAG, "ScreenshotObserver.start() called when already started")
        return
    }
    
    val contentResolver: ContentResolver = context.contentResolver
    contentResolver.registerContentObserver(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        true,
        this
    )
    isStarted = true
    Log.d(TAG, "ScreenshotObserver started successfully")
}

override fun onChange(selfChange: Boolean, uri: Uri?) {
    super.onChange(selfChange, uri)
    Log.d(TAG, "onChange triggered - isStarted: $isStarted, uri: $uri")
    // ... rest of implementation
}
```

**POTENTIAL FIX**: Ensure observer survives configuration changes

```kotlin
// In ScreenshotObserver constructor
init {
    Log.d(TAG, "ScreenshotObserver instance created: ${System.identityHashCode(this)}")
}
```

### Testing Checklist
- [ ] Enable debug logging
- [ ] Take first screenshot - verify log shows onChange
- [ ] Take second screenshot - verify onChange still logs
- [ ] Check logcat for observer recreation/destruction
- [ ] Test across app restarts

---

## 🔴 Bug #7: Settings Button Hitbox

### Problem Description
**Symptom**: Settings gear icon requires exact top-left corner tap to register

### Root Cause Analysis

**Issue Location**: [GalleryScreen.kt:287-297](file:///c:/Users/past9/OneDrive/Desktop/project/FFF/MemGallery/app/src/main/java/com/example/memgallery/ui/screens/GalleryScreen.kt#L287-L297)

```kotlin
IconButton(
    onClick = { navController.navigate(Screen.Settings.route) },
    modifier = Modifier
        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
) {
    Icon(
        Icons.Default.Settings,
        contentDescription = "Settings",
        tint = MaterialTheme.colorScheme.onPrimaryContainer
    )
}
```

**Root Cause**: `IconButton` default size is 48dp, but background circle makes it LOOK smaller. Users tap the visible circle edge, which is outside the icon's touch bounds.

### Fix Implementation

**File**: `GalleryScreen.kt`

```kotlin
// REPLACE Lines 287-297
IconButton(
    onClick = { navController.navigate(Screen.Settings.route) },
    modifier = Modifier
        .size(48.dp) // Explicitly set size
        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
) {
    Icon(
        Icons.Default.Settings,
        contentDescription = "Settings",
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(24.dp) // Slightly larger icon for clarity
    )
}
```

**Alternative Fix** (Better UX):

```kotlin
Surface(
    onClick = { navController.navigate(Screen.Settings.route) },
    modifier = Modifier.size(48.dp),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.primaryContainer
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
            Icons.Default.Settings,
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

### Testing Checklist
- [ ] Tap settings icon center - verify opens
- [ ] Tap icon edges - verify opens
- [ ] Tap slightly outside icon - verify opens
- [ ] Test on small screen device

---

## 🔴 Bug #8: Dark Mode Readability

### Problem Description
**Symptom**: Black text on dark background in onboarding/recall screens

### Root Cause Analysis

**Likely Issue**: Hardcoded text colors not respecting Material Theme's color scheme

**Files to Check**:
- `OnboardingScreen.kt`
- Any screens with hardcoded `Color.Black` text

### Fix Implementation

**File**: `OnboardingScreen.kt`

**Search for**:
```kotlin
// BAD PATTERNS:
color = Color.Black
textColor = Color.Black
setTextColor(Color.BLACK)
```

**Replace with**:
```kotlin
color = MaterialTheme.colorScheme.onBackground // For primary text
color = MaterialTheme.colorScheme.onSurface // For general text
color = MaterialTheme.colorScheme.onSurfaceVariant // For secondary text
```

**Specific Fix Example** (Lines 145-158):

```kotlin
// VERIFY these use theme colors
Text(
    text = "Welcome to MemGallery",
    style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onBackground // ✓ Should already be correct
)

Text(
    text = "Your external brain for memories.\\nCapture, process, and recall everything.",
    style = MaterialTheme.typography.bodyLarge,
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant // ✓ Should already be correct
)
```

### Testing Checklist
- [ ] Enable dark mode
- [ ] Check onboarding screens 0-3
- [ ] Verify all text is readable
- [ ] Test AMOLED mode
- [ ] Check settings screens

---

## ✨ Feature #1: Manual Reminder Button

### Feature Specification

**Location**: Memory Detail Screen, in the "Suggested Actions" section  
**Behavior**: Add a floating "+" button that opens a reminder creation dialog

###Implementation

**File**: `MemoryDetailScreen.kt`

```kotlin
// ADD after line 305 (after existing actions loop)
if (!memory.aiActions.isNullOrEmpty()) {
    Text("Suggested Actions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    Spacer(modifier = Modifier.height(8.dp))
    val context = LocalContext.current
    memory.aiActions.forEach { action ->
        ActionCard(action = action, onAction = {
            ActionHandler.handleAction(context, action)
        })
        Spacer(modifier = Modifier.height(8.dp))
    }
    
    // NEW: Manual Reminder Button
    OutlinedButton(
        onClick = { showManualReminderDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add Manual Reminder", modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Add Manual Reminder")
    }
    
    Spacer(modifier = Modifier.height(24.dp))
}

// ADD state variable near top (after line 105)
var showManualReminderDialog by remember { mutableStateOf(false) }

// ADD dialog at bottom of composable
if (showManualReminderDialog) {
    ManualReminderDialog(
        memoryTitle = memory.aiTitle ?: "Memory",
        onDismiss = { showManualReminderDialog = false },
        onConfirm = { description, date, time, type ->
            // Create task
            val taskDao = // Need to inject TaskDao via ViewModel
            // Implementation similar to ActionHandler
            showManualReminderDialog = false
        }
    )
}
```

**Add Dialog Composable**:

```kotlin
@Composable
fun ManualReminderDialog(
    memoryTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (description: String, date: String?, time: String?, type: String) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedTime by remember { mutableStateOf("") }
    var reminderType by remember { mutableStateOf("REMINDER") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Reminder for: $memoryTitle") },
        text = {
            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Reminder Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                // Add date/time pickers
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(description, selectedDate?.toString(), selectedTime, reminderType)
                },
                enabled = description.isNotBlank()
            ) {
                Text("Create Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

---

## ✨ Feature #2: Disable Auto-Reminders Toggle

### Feature Specification

**Location**: Settings Screen → Advanced Settings  
**Purpose**: Allow users to disable AI-generated reminders/tasks

### Implementation

**File**: `SettingsRepository.kt`

```kotlin
// ADD preference key (after line 67)
val AUTO_REMINDERS_ENABLED = booleanPreferencesKey("auto_reminders_enabled")

// ADD flow (after line 243)
val autoRemindersEnabledFlow: Flow<Boolean> = context.dataStore.data
    .map { preferences ->
        preferences[PreferencesKeys.AUTO_REMINDERS_ENABLED] ?: true // Default: enabled
    }

// ADD setter (after line 349)
suspend fun setAutoRemindersEnabled(enabled: Boolean) {
    context.dataStore.edit { settings ->
        settings[PreferencesKeys.AUTO_REMINDERS_ENABLED] = enabled
    }
}
```

**File**: `MemoryRepository.kt` (Lines 184-200)

```kotlin
// MODIFY task extraction logic
// Extract and save tasks
aiAnalysis.actions?.let { actions ->
    // NEW: Check if auto-reminders are enabled
    val autoRemindersEnabled = settingsRepository.autoRemindersEnabledFlow.first()
    
    if (autoRemindersEnabled) {
        val tasks = actions.map { action ->
            com.example.memgallery.data.local.entity.TaskEntity(
                memoryId = memoryId,
                title = action.description.take(50),
                description = action.description,
                dueDate = action.date,
                dueTime = action.time,
                priority = "MEDIUM",
                status = "PENDING",
                type = action.type ?: "TODO"
            )
        }
        taskDao.insertTasks(tasks)
        Log.d(TAG, "Inserted ${tasks.size} tasks for memory $memoryId")
    } else {
        Log.d(TAG, "Auto-reminders disabled - skipping task creation")
    }
}
```

**File**: `AdvancedSettingsScreen.kt`

```kotlin
// ADD toggle in settings UI
val autoRemindersEnabled by viewModel.autoRemindersEnabled.collectAsState()

SettingItem(
    title = "Auto-Generate Reminders",
    description = "Allow AI to automatically create tasks and reminders from your memories",
    onClick = { viewModel.setAutoRemindersEnabled(!autoRemindersEnabled) },
    trailing = {
        Switch(
            checked = autoRemindersEnabled,
            onCheckedChange = { viewModel.setAutoRemindersEnabled(it) }
        )
    }
)
```

---

## 📝 Implementation Order

### Day 1: Critical Crashes
1. ✅ Bug #1: Crash on Note Editing (30 min)
2. ✅ Bug #7: Settings Button Hitbox (15 min)
3. ✅ Bug #8: Dark Mode Readability (30 min)

### Day 2: Duplication Issues
4. ✅ Bug #2: Duplicate Screenshots (1 hour)
5. ✅ Bug #3: Duplicate Voice Notes (1 hour)

### Day 3: UX & Permissions
6. ✅ Bug #4: Onboarding Loop (30 min)
7. ✅ Bug #5: Permissions Logic (1 hour)
8. ✅ Bug #6: Auto-Indexing (1 hour - investigation heavy)

### Day 4: Features
9. ✅ Feature #2: Auto-Reminders Toggle (45 min)
10. ✅ Feature #1: Manual Reminder Dialog (1.5 hours)

### Day 5: Testing & Polish
11. Full regression testing
12. User acceptance testing
13. Build release APK

**Total Estimated Time**: 10-12 hours of development

---

## 🧪 Comprehensive Testing Plan

### Test Device Requirements
- Android 13+ device (for permission testing)
- Android 10 device (for backward compatibility)
- Dark mode enabled device
- AMOLED mode testing

### Testing Matrix

| Test Case | Bug #1 | Bug #2 | Bug #3 | Bug #4 | Bug #5 | Bug #6 | Bug #7 | Bug #8 | Feat #1 | Feat #2 |
|-----------|--------|--------|--------|--------|--------|--------|--------|--------|---------|---------|
| Fresh Install | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Upgrade from v0.0.5 | ✓ | - | - | - | - | ✓ | ✓ | ✓ | ✓ | ✓ |
| Dark Mode | ✓ | - | - | ✓ | - | - | ✓ | ✓ | ✓ | - |
| AMOLED Mode | ✓ | - | - | ✓ | - | - | ✓ | ✓ | ✓ | - |
| Permissions Denied | - | - | - | ✓ | ✓ | ✓ | - | - | - | - |

---

**END OF DOCUMENT**  
**Ready for Implementation**: All bugs analyzed, fixes documented, ready to code!
