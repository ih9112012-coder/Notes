package com.example.notes.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.notes.model.Note;
import com.example.notes.model.StorageType;
import com.example.notes.sync.NoteSyncManager;
import com.example.notes.util.AppExecutors;

import java.util.List;

/**
 * Coordinates primary (SQLite) and secondary (JSON) storage repositories,
 * persists user's active storage choice, and manages asynchronous execution
 * and bidirectional synchronization.
 */
public class StorageManager {

    private static final String PREFS_NAME = "notes_storage_prefs";
    private static final String KEY_ACTIVE_STORAGE = "active_storage_type";

    private static StorageManager instance;

    private final Context context;
    private final SharedPreferences preferences;
    private final SqliteNoteRepository sqliteRepository;
    private final JsonNoteRepository jsonRepository;
    private final NoteSyncManager syncManager;
    private final AppExecutors appExecutors;

    /**
     * Functional callback interface for receiving asynchronous query results on the main thread.
     */
    public interface Callback<T> {
        void onResult(T result);
    }

    private StorageManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.sqliteRepository = new SqliteNoteRepository(this.context);
        this.jsonRepository = new JsonNoteRepository(this.context);
        this.syncManager = new NoteSyncManager(sqliteRepository, jsonRepository);
        this.appExecutors = AppExecutors.getInstance();
    }

    public static synchronized StorageManager getInstance(Context context) {
        if (instance == null) {
            instance = new StorageManager(context);
        }
        return instance;
    }

    public StorageType getActiveStorageType() {
        String typeName = preferences.getString(KEY_ACTIVE_STORAGE, StorageType.SQLITE.name());
        try {
            return StorageType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return StorageType.SQLITE;
        }
    }

    public void setActiveStorageType(StorageType storageType) {
        preferences.edit()
                .putString(KEY_ACTIVE_STORAGE, storageType.name())
                .apply();
    }

    public NoteRepository getActiveRepository() {
        return getRepository(getActiveStorageType());
    }

    public NoteRepository getRepository(StorageType type) {
        if (type == StorageType.JSON_FILE) {
            return jsonRepository;
        }
        return sqliteRepository;
    }

    public SqliteNoteRepository getSqliteRepository() {
        return sqliteRepository;
    }

    public JsonNoteRepository getJsonRepository() {
        return jsonRepository;
    }

    public NoteSyncManager.SyncResult synchronize() {
        return syncManager.synchronize();
    }

    // =========================================================================
    // Asynchronous Storage APIs (Offloads Disk I/O from Main UI Thread)
    // =========================================================================

    public void loadNotesAsync(Callback<List<Note>> callback) {
        loadNotesAsync(getActiveStorageType(), callback);
    }

    public void loadNotesAsync(StorageType storageType, Callback<List<Note>> callback) {
        appExecutors.diskIO().execute(() -> {
            List<Note> notes = getRepository(storageType).getAllNotes();
            appExecutors.mainThread().execute(() -> {
                if (callback != null) {
                    callback.onResult(notes);
                }
            });
        });
    }

    public void getNoteByIdAsync(String id, StorageType storageType, Callback<Note> callback) {
        appExecutors.diskIO().execute(() -> {
            Note note = getRepository(storageType).getNoteById(id);
            appExecutors.mainThread().execute(() -> {
                if (callback != null) {
                    callback.onResult(note);
                }
            });
        });
    }

    public void saveNoteAsync(Note note, StorageType storageType, Callback<Boolean> callback) {
        appExecutors.diskIO().execute(() -> {
            boolean success = getRepository(storageType).saveNote(note);
            appExecutors.mainThread().execute(() -> {
                if (callback != null) {
                    callback.onResult(success);
                }
            });
        });
    }

    public void deleteNoteAsync(String id, StorageType storageType, Callback<Boolean> callback) {
        appExecutors.diskIO().execute(() -> {
            boolean success = getRepository(storageType).deleteNote(id);
            appExecutors.mainThread().execute(() -> {
                if (callback != null) {
                    callback.onResult(success);
                }
            });
        });
    }

    public void deleteAllNotesAsync(StorageType storageType, Callback<Void> callback) {
        appExecutors.diskIO().execute(() -> {
            getRepository(storageType).deleteAllNotes();
            appExecutors.mainThread().execute(() -> {
                if (callback != null) {
                    callback.onResult(null);
                }
            });
        });
    }

    public void synchronizeAsync(Callback<NoteSyncManager.SyncResult> callback) {
        appExecutors.diskIO().execute(() -> {
            NoteSyncManager.SyncResult result = syncManager.synchronize();
            appExecutors.mainThread().execute(() -> {
                if (callback != null) {
                    callback.onResult(result);
                }
            });
        });
    }
}
