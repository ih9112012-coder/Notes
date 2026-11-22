package com.example.notes.repository;

import android.content.Context;
import android.util.Log;

import com.example.notes.model.Note;
import com.example.notes.model.StorageType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Secondary storage implementation using JSON files in Android's internal app storage.
 * Features crash-resilient atomic writes with temporary staging and atomic replacement
 * to prevent data corruption during power loss or process kill, plus robust stream reading.
 * Fully thread-safe and decoupled from SQLite.
 */
public class JsonNoteRepository implements NoteRepository {

    private static final String TAG = "JsonNoteRepository";
    private static final String FILE_NAME = "notes_secondary.json";

    private final File storageFile;
    private final Object fileLock = new Object();

    public JsonNoteRepository(Context context) {
        this(new File(context.getApplicationContext().getFilesDir(), FILE_NAME));
    }

    // Constructor for testing with specific file
    public JsonNoteRepository(File file) {
        this.storageFile = file;
    }

    @Override
    public List<Note> getAllNotes() {
        synchronized (fileLock) {
            List<Note> notes = readFromFile();
            // Sort newest first
            Collections.sort(notes, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            return notes;
        }
    }

    @Override
    public Note getNoteById(String id) {
        if (id == null) return null;

        synchronized (fileLock) {
            List<Note> notes = readFromFile();
            for (Note note : notes) {
                if (id.equals(note.getId())) {
                    return note;
                }
            }
        }
        return null;
    }

    @Override
    public boolean saveNote(Note note) {
        if (note == null) return false;

        synchronized (fileLock) {
            List<Note> notes = readFromFile();
            boolean found = false;
            for (int i = 0; i < notes.size(); i++) {
                if (notes.get(i).getId().equals(note.getId())) {
                    notes.set(i, note);
                    found = true;
                    break;
                }
            }
            if (!found) {
                notes.add(note);
            }
            return writeToFile(notes);
        }
    }

    @Override
    public boolean saveNotes(List<Note> newNotes) {
        if (newNotes == null) return false;
        if (newNotes.isEmpty()) return true;

        synchronized (fileLock) {
            List<Note> currentNotes = readFromFile();
            Map<String, Note> noteMap = new LinkedHashMap<>();
            for (Note n : currentNotes) {
                noteMap.put(n.getId(), n);
            }
            for (Note n : newNotes) {
                if (n != null) {
                    noteMap.put(n.getId(), n);
                }
            }
            return writeToFile(new ArrayList<>(noteMap.values()));
        }
    }

    @Override
    public boolean insertNote(Note note) {
        if (note == null) return false;

        synchronized (fileLock) {
            List<Note> notes = readFromFile();
            for (Note existing : notes) {
                if (existing.getId().equals(note.getId())) {
                    return false; // already exists
                }
            }
            notes.add(note);
            return writeToFile(notes);
        }
    }

    @Override
    public boolean updateNote(Note note) {
        if (note == null) return false;

        synchronized (fileLock) {
            List<Note> notes = readFromFile();
            for (int i = 0; i < notes.size(); i++) {
                if (notes.get(i).getId().equals(note.getId())) {
                    notes.set(i, note);
                    return writeToFile(notes);
                }
            }
            return false;
        }
    }

    @Override
    public boolean deleteNote(String id) {
        if (id == null) return false;

        synchronized (fileLock) {
            List<Note> notes = readFromFile();
            boolean removed = notes.removeIf(note -> id.equals(note.getId()));
            if (removed) {
                return writeToFile(notes);
            }
            return false;
        }
    }

    @Override
    public void deleteAllNotes() {
        synchronized (fileLock) {
            writeToFile(new ArrayList<>());
        }
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.JSON_FILE;
    }

    private List<Note> readFromFile() {
        List<Note> notes = new ArrayList<>();
        if (!storageFile.exists()) {
            return notes;
        }

        try {
            byte[] data = readFully(storageFile);
            if (data == null || data.length == 0) {
                return notes;
            }

            String jsonString = new String(data, StandardCharsets.UTF_8).trim();
            if (jsonString.isEmpty()) {
                return notes;
            }

            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                notes.add(Note.fromJson(obj, StorageType.JSON_FILE));
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to read notes from JSON file: " + storageFile.getAbsolutePath(), e);
        }

        return notes;
    }

    private static byte[] readFully(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(file.length(), 32))) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        }
    }

    private boolean writeToFile(List<Note> notes) {
        JSONArray jsonArray = new JSONArray();
        for (Note note : notes) {
            try {
                jsonArray.put(note.toJson());
            } catch (JSONException e) {
                Log.e(TAG, "Error serializing note to JSON: " + note.getId(), e);
            }
        }

        File parentDir = storageFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        File tempFile = new File(storageFile.getParentFile(), storageFile.getName() + ".tmp");
        try {
            byte[] bytes = jsonArray.toString(2).getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(bytes);
                fos.flush();
                try {
                    fos.getFD().sync();
                } catch (IOException ignored) {
                }
            }

            // Atomic replace of the target file
            try {
                Files.move(tempFile.toPath(), storageFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), storageFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to write notes atomically to JSON file", e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            return false;
        }
    }
}
