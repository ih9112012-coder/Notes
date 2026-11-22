# Notes Storage — Native Android Application

[![Android Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java%2017-ED8B00.svg?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Architecture](https://img.shields.io/badge/Architecture-Repository%20Pattern%20%2B%20ViewBinding-007ACC.svg)](https://developer.android.com/topic/architecture)
[![Build System](https://img.shields.io/badge/Build%20Tool-Gradle%208.2-02303A.svg?logo=gradle&logoColor=white)](https://gradle.org)
[![Storage Engines](https://img.shields.io/badge/Storage-SQLite%20%7C%20JSON%20File-4E6B99.svg)](https://sqlite.org)
[![Testing](https://img.shields.io/badge/Unit%20Tests-100%25%20Passing-success.svg)](app/src/test/java/com/example/notes)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A robust native Android application featuring dual persistent storage engines (**SQLite Database** and **JSON File Storage**), real-time runtime storage switching, background multi-threaded concurrency, robust input validation, and an automated bidirectional synchronization engine with timestamp-based conflict resolution.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [System Architecture](#system-architecture)
  - [Design Pattern: Repository Pattern](#design-pattern-repository-pattern)
  - [Threading & Concurrency (`AppExecutors`)](#threading--concurrency-appexecutors)
  - [Crash-Resilient JSON Atomic Writes](#crash-resilient-json-atomic-writes)
- [Data Storage Engines](#data-storage-engines)
  - [Primary Storage: SQLite](#primary-storage-sqlite)
  - [Secondary Storage: JSON File](#secondary-storage-json-file)
- [Synchronization Algorithm](#synchronization-algorithm)
- [User Interface & Workflows](#user-interface--workflows)
  - [Main Dashboard](#main-dashboard)
  - [Note Creation & Editing](#note-creation--editing)
  - [Storage Switching](#storage-switching)
  - [Synchronization Workflow](#synchronization-workflow)
- [Project Directory Structure](#project-directory-structure)
- [Component Reference](#component-reference)
- [Unit Testing Suite](#unit-testing-suite)
- [Getting Started & Installation](#getting-started--installation)
  - [Prerequisites](#prerequisites)
  - [Import into Android Studio](#import-into-android-studio)
  - [Command Line Build](#command-line-build)
- [License](#license)

---

## Overview

The **Notes Storage Application** demonstrates production-grade local persistence engineering on modern Android systems. Rather than coupling business logic to a single database framework, the application abstracts data operations through a unified repository contract (`NoteRepository`), allowing notes to be saved, modified, queried, and purged across both a relational SQL database and flat structured JSON documents.

Users can toggle their active working storage at any time with instantaneous UI updates, and seamlessly reconcile divergence between the storage media via background synchronization.

---

## Key Features

- **Dual Independent Persistence Backends**:
  - **SQLite**: Local relational database with indexed primary keys and transactional query capabilities.
  - **JSON File**: Flat human-readable serialization stored in internal private app sandbox storage (`getFilesDir()`).
- **Dynamic Active Storage Switching**:
  - Real-time toggle on the main dashboard to swap active repository targets without restarting the app.
  - Persistent storage selection remembered across sessions via Android `SharedPreferences`.
- **Bidirectional Timestamp Synchronization**:
  - Compares note instances across both engines by UUID.
  - Identifies unique notes missing from either store and propagates copies.
  - Resolves conflicts for shared records using "last-write-wins" millisecond timestamp comparison.
  - Presents a detailed modal summary indicating notes added, updated, and verified in sync.
- **Strict Data Validation**:
  - Prevents accidental creation of blank or whitespace-only notes.
  - Highlights input errors dynamically with Material Design `TextInputLayout` helpers.
- **ANR-Proof Asynchronous Architecture**:
  - All file I/O and SQL database operations are processed on dedicated background threads via `AppExecutors`.
  - Dispatches UI updates safely back to the Android main thread (Looper) via callback interfaces.
- **Crash-Proof File Writing**:
  - Uses atomic file staging (`.tmp` write, filesystem sync flush, and atomic file replace) to guarantee zero file corruption if the process is terminated mid-write.
- **Modern Material Design UI**:
  - Material 3 cards, dynamic storage color badges (Navy Blue for SQLite, Amber Gold for JSON), floating action button (FAB), swipe-to-scroll, and confirmation dialogs for destructive actions.

---

## System Architecture

```
                                  +-----------------------+
                                  |     MainActivity      |
                                  |   NoteEditActivity    |
                                  +-----------+-----------+
                                              |
                                              | (Dispatches Asynchronous Tasks)
                                              v
                                  +-----------------------+
                                  |    StorageManager     |
                                  +-----+-----------+-----+
                                        |           |
            +---------------------------+           +---------------------------+
            |                                                                   |
            v                                                                   v
+-----------------------+                                           +-----------------------+
| SqliteNoteRepository  |                                           |  JsonNoteRepository   |
| (Primary Engine)      |                                           |  (Secondary Engine)   |
+-----------+-----------+                                           +-----------+-----------+
            |                                                                   |
            v                                                                   v
   [ notes_primary.db ]                                                [ notes_secondary.json ]
   SQLite Database                                                     Internal Filesystem
```

### Design Pattern: Repository Pattern

Data operations conform to the clean `NoteRepository` contract:

```java
public interface NoteRepository {
    List<Note> getAllNotes();
    Note getNoteById(String id);
    boolean saveNote(Note note);
    boolean insertNote(Note note);
    boolean updateNote(Note note);
    boolean deleteNote(String id);
    void deleteAllNotes();
    boolean saveNotes(List<Note> notes);
    StorageType getStorageType();
}
```

This guarantees complete loose coupling: the presentation layer never interacts directly with low-level SQLite cursors or file output streams.

### Threading & Concurrency (`AppExecutors`)

To satisfy Android performance best practices and guarantee a 60fps/120fps smooth interface:
- **`diskIO()`**: Backed by a single-threaded executor queue ensuring serialized, race-condition-free writes.
- **`mainThread()`**: Backed by an Android `Handler(Looper.getMainLooper())` that routes completed data payloads directly back to the UI thread.
- Includes a test fallback allowing unit tests in pure JVM environments to run synchronously without Android Looper dependencies.

### Crash-Resilient JSON Atomic Writes

Writing JSON directly to a target file risks truncation if battery dies or the OS terminates the app. The JSON repository writes to a temporary sibling file (`notes_secondary.json.tmp`), calls `FileOutputStream.getFD().sync()` to flush OS disk caches, and then invokes `java.nio.file.Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.

---

## Data Storage Engines

### Primary Storage: SQLite

- **Database Name**: `notes_primary.db`
- **Helper Class**: `NotesDbHelper` extending `SQLiteOpenHelper`
- **Table Schema**:
  ```sql
  CREATE TABLE notes (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      content TEXT NOT NULL,
      timestamp INTEGER NOT NULL
  );
  ```

### Secondary Storage: JSON File

- **File Path**: `<context.getFilesDir()>/notes_secondary.json`
- **Encoding**: UTF-8
- **JSON Structure**:
  ```json
  [
    {
      "id": "e4f8d22c-8064-4e2b-b99b-325f1870a25f",
      "title": "Sprint Planning",
      "content": "Discuss architecture and storage synchronization.",
      "timestamp": 1700000000000
    }
  ]
  ```

---

## Synchronization Algorithm

The `NoteSyncManager` executes a four-stage synchronization reconciliation:

```
[Fetch Primary Notes]       [Fetch Secondary Notes]
          \                          /
           \                        /
      [Aggregate Unique IDs in Memory Map]
                     |
         +-----------+-----------+
         |                       |
    (Missing in S)         (Missing in P)
         |                       |
  [Copy P -> S]            [Copy S -> P]
         |                       |
         +-----------+-----------+
                     |
           (Present in Both)
                     |
     [Compare Timestamps (p.ts vs s.ts)]
         |                       |
   p.ts > s.ts              s.ts > p.ts
         |                       |
  [Update S with P]        [Update P with S]
```

1. **ID Aggregation**: Fetches all notes from SQLite and JSON, creating index maps by `note.id`.
2. **Missing Record Resolution**:
   - Notes existing exclusively in SQLite are cloned to JSON with `StorageType.JSON_FILE`.
   - Notes existing exclusively in JSON are inserted into SQLite with `StorageType.SQLITE`.
3. **Timestamp Conflict Resolution**:
   - For notes present in both stores, timestamps are evaluated.
   - If `primary.timestamp > secondary.timestamp`, the primary version overwrites secondary.
   - If `secondary.timestamp > primary.timestamp`, the secondary version overwrites primary.
   - If timestamps match, records are flagged as already synchronized.
4. **Batch Persistence**: Writes all accumulated additions and updates using atomic batch transactions.

---

## User Interface & Workflows

### Main Dashboard

```
+-------------------------------------------------------------+
|  Notes Storage                                          [🔄] |
+-------------------------------------------------------------+
|  +-------------------------------------------------------+  |
|  | [ (•) SQLite (Primary) ]   [ ( ) JSON File (Secondary)|  |
|  | Active Storage: SQLite (Primary)          3 note(s)   |  |
|  +-------------------------------------------------------+  |
|                                                             |
|  +-------------------------------------------------------+  |
|  | Sprint Architecture Review                       [🗑️] |  |
|  | Finalize dual storage and atomic json sync write...   |  |
|  | Nov 22, 2026, 13:40                     [ SQLite ]    |  |
|  +-------------------------------------------------------+  |
|                                                             |
|  +-------------------------------------------------------+  |
|  | Research References                              [🗑️] |  |
|  | Review Android SQLiteOpenHelper & internal files.     |  |
|  | Nov 22, 2026, 13:15                     [ SQLite ]    |  |
|  +-------------------------------------------------------+  |
|                                                             |
|                                                     [ + ]   | <-- FAB Add Note
+-------------------------------------------------------------+
```

### Note Creation & Editing

- Tap the floating action button (**`+`**) to open `NoteEditActivity`.
- A top badge highlights the target destination (e.g., `Saving into: SQLite (Primary)`).
- Input fields enforce non-empty titles and contents. Tapping **Save** validates input and writes asynchronously.

### Storage Switching

- Select either **SQLite (Primary)** or **JSON File (Secondary)** on the toggle group.
- The list immediately reloads with the items stored in that repository.
- Each item features a distinct color badge reflecting its origin.

### Synchronization Workflow

- Tap the top toolbar's **Synchronize** icon (`ic_sync`).
- A progress indicator indicates background synchronization.
- Upon completion, a `MaterialAlertDialog` summarizes the operation:
  - Added to SQLite count
  - Added to JSON count
  - Updated in SQLite count
  - Updated in JSON count
  - Already in sync count

---

## Project Directory Structure

```
NotesApp/
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/notes/
│       │   │   ├── model/
│       │   │   │   ├── Note.java               # Domain entity & JSON serialization
│       │   │   │   └── StorageType.java        # Enum (SQLITE, JSON_FILE)
│       │   │   ├── repository/
│       │   │   │   ├── NoteRepository.java     # Unified CRUD repository contract
│       │   │   │   ├── NotesDbHelper.java      # SQLite database schema helper
│       │   │   │   ├── SqliteNoteRepository.java # SQLite storage implementation
│       │   │   │   ├── JsonNoteRepository.java # Crash-resilient JSON storage
│       │   │   │   └── StorageManager.java     # Repository orchestrator & preferences
│       │   │   ├── sync/
│       │   │   │   └── NoteSyncManager.java    # Bidirectional sync engine
│       │   │   ├── ui/
│       │   │   │   ├── MainActivity.java       # Listing, toggling, sync triggers
│       │   │   │   ├── NoteEditActivity.java   # Create/Edit form with validation
│       │   │   │   └── NotesAdapter.java       # RecyclerView adapter with badges
│       │   │   └── util/
│       │   │       ├── AppExecutors.java       # Background I/O and UI thread pools
│       │   │       └── NoteValidator.java      # Input validation logic
│       │   └── res/
│       │       ├── drawable/                   # Vector assets and custom badge shapes
│       │       ├── layout/                     # activity_main, activity_note_edit, item_note
│       │       ├── menu/                       # Main menu (sync, delete all), edit menu
│       │       └── values/                     # colors, strings, themes
│       └── test/java/com/example/notes/
│           ├── AppExecutorsTest.java           # Concurrency & executor test
│           ├── JsonNoteRepositoryTest.java     # Atomic JSON file I/O tests
│           ├── NoteSyncManagerTest.java        # Bidirectional sync & conflict tests
│           ├── NoteTest.java                   # Model & serialization tests
│           └── NoteValidatorTest.java          # Form validation tests
├── build.gradle
├── gradle/wrapper/
├── gradlew
├── gradlew.bat
├── settings.gradle
└── README.md
```

---

## Component Reference

| Class | Package | Responsibility |
| :--- | :--- | :--- |
| `Note` | `model` | Represents a note with `id` (UUID), `title`, `content`, `timestamp`, and `StorageType`. |
| `StorageType` | `model` | Enum specifying persistence backend (`SQLITE`, `JSON_FILE`). |
| `NoteRepository` | `repository` | Interface defining standard asynchronous/synchronous CRUD contracts. |
| `SqliteNoteRepository` | `repository` | Concrete SQLite repository using `SQLiteDatabase` and transactions. |
| `JsonNoteRepository` | `repository` | Concrete file repository writing UTF-8 JSON via temporary atomic staging. |
| `StorageManager` | `repository` | Singleton facade managing active storage selection and thread dispatching. |
| `NoteSyncManager` | `sync` | Logic engine comparing records across stores and reconciling conflicts. |
| `NoteValidator` | `util` | Validates titles and body texts for non-null, non-whitespace integrity. |
| `AppExecutors` | `util` | Dispatches file/DB operations away from the main thread to avoid ANR. |
| `MainActivity` | `ui` | Main screen showing note lists, storage toggles, and sync actions. |
| `NoteEditActivity` | `ui` | Form activity for creating or modifying notes with input error display. |
| `NotesAdapter` | `ui` | Binds note items to card layouts with custom storage indicator badges. |

---

## Unit Testing Suite

The project includes unit tests executed on the local JVM:

| Test Class | Focus Area | Scenarios Covered |
| :--- | :--- | :--- |
| `NoteSyncManagerTest` | Bidirectional Sync | Primary-to-secondary sync, secondary-to-primary sync, timestamp conflict resolution (latest update wins), already synchronized states, empty repo sync. |
| `JsonNoteRepositoryTest` | File Persistence | Missing/empty file handling, record saving/retrieval, batch save, update by ID, single deletion, full wipe, and sorted order verification. |
| `NoteValidatorTest` | Input Validation | Valid inputs, empty strings, whitespace-only strings, null values, independent title/content validation, helper method checks. |
| `NoteTest` | Domain Model | Constructor defaults, UUID generation, formatted timestamp rendering, JSON round-trip serialization/deserialization, equality & hashcode. |
| `AppExecutorsTest` | Threading | Background executor invocation, latch awaiting, and fallback execution in JVM test environments. |

---

## Getting Started & Installation

### Prerequisites

- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: Java Development Kit 17
- **Android SDK**:
  - Minimum SDK: API 24 (Android 7.0 Nougat)
  - Target / Compile SDK: API 34 (Android 14)
- **Gradle**: 8.2+

### Import into Android Studio

1. Clone or download the repository to your local system:
   ```bash
   git clone https://github.com/your-username/notes-storage-android.git
   ```
2. Open **Android Studio**.
3. Select **File > Open...** and navigate to the `NotesApp` directory.
4. Allow Gradle to sync dependencies and index the project.
5. Select an Android Virtual Device (AVD) or physical device running Android 7.0+ with USB debugging enabled.
6. Click **Run > Run 'app'** (`Shift + F10`).

### Command Line Build

To compile and assemble the debug APK via terminal:

**Windows (PowerShell/CMD):**
```powershell
.\gradlew assembleDebug
```

**Linux / macOS:**
```bash
./gradlew assembleDebug
```

The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

To execute the unit test suite:
```bash
.\gradlew test
```

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
