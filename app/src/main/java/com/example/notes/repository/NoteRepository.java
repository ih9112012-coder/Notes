package com.example.notes.repository;

import com.example.notes.model.Note;
import com.example.notes.model.StorageType;

import java.util.List;

/**
 * Common CRUD operations interface for both primary (SQLite)
 * and secondary (JSON File) storage implementations.
 */
public interface NoteRepository {

    /**
     * Retrieve all saved notes, typically sorted newest first.
     */
    List<Note> getAllNotes();

    /**
     * Retrieve single note by unique ID.
     */
    Note getNoteById(String id);

    /**
     * Insert or update note depending on whether ID already exists.
     */
    boolean saveNote(Note note);

    /**
     * Insert new note.
     */
    boolean insertNote(Note note);

    /**
     * Update existing note.
     */
    boolean updateNote(Note note);

    /**
     * Delete note by unique ID.
     */
    boolean deleteNote(String id);

    /**
     * Delete all notes from this storage.
     */
    void deleteAllNotes();

    /**
     * Batch save multiple notes in a single operation.
     * Default implementation iterates and saves each note.
     */
    default boolean saveNotes(List<Note> notes) {
        if (notes == null) return false;
        boolean allOk = true;
        for (Note note : notes) {
            if (!saveNote(note)) {
                allOk = false;
            }
        }
        return allOk;
    }

    /**
     * Identify the storage type of this repository.
     */
    StorageType getStorageType();
}
