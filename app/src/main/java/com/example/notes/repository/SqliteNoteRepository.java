package com.example.notes.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.notes.model.Note;
import com.example.notes.model.StorageType;

import java.util.ArrayList;
import java.util.List;

/**
 * Primary storage implementation using Android's native SQLite database.
 */
public class SqliteNoteRepository implements NoteRepository {

    private final NotesDbHelper dbHelper;

    public SqliteNoteRepository(Context context) {
        this.dbHelper = new NotesDbHelper(context.getApplicationContext());
    }

    // Constructor for testing with mock helper
    public SqliteNoteRepository(NotesDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @Override
    public List<Note> getAllNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String[] projection = {
                NotesDbHelper.COLUMN_ID,
                NotesDbHelper.COLUMN_TITLE,
                NotesDbHelper.COLUMN_CONTENT,
                NotesDbHelper.COLUMN_TIMESTAMP
        };

        String sortOrder = NotesDbHelper.COLUMN_TIMESTAMP + " DESC";

        try (Cursor cursor = db.query(
                NotesDbHelper.TABLE_NOTES,
                projection,
                null,
                null,
                null,
                null,
                sortOrder
        )) {
            while (cursor != null && cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(NotesDbHelper.COLUMN_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(NotesDbHelper.COLUMN_TITLE));
                String content = cursor.getString(cursor.getColumnIndexOrThrow(NotesDbHelper.COLUMN_CONTENT));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(NotesDbHelper.COLUMN_TIMESTAMP));

                notes.add(new Note(id, title, content, timestamp, StorageType.SQLITE));
            }
        }

        return notes;
    }

    @Override
    public Note getNoteById(String id) {
        if (id == null) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {
                NotesDbHelper.COLUMN_ID,
                NotesDbHelper.COLUMN_TITLE,
                NotesDbHelper.COLUMN_CONTENT,
                NotesDbHelper.COLUMN_TIMESTAMP
        };

        String selection = NotesDbHelper.COLUMN_ID + " = ?";
        String[] selectionArgs = {id};

        try (Cursor cursor = db.query(
                NotesDbHelper.TABLE_NOTES,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                String noteId = cursor.getString(cursor.getColumnIndexOrThrow(NotesDbHelper.COLUMN_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(NotesDbHelper.COLUMN_TITLE));
                String content = cursor.getString(cursor.getColumnIndexOrThrow(NotesDbHelper.COLUMN_CONTENT));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(NotesDbHelper.COLUMN_TIMESTAMP));

                return new Note(noteId, title, content, timestamp, StorageType.SQLITE);
            }
        }

        return null;
    }

    @Override
    public boolean saveNote(Note note) {
        if (note == null) return false;
        return insertNote(note);
    }

    @Override
    public boolean saveNotes(List<Note> notes) {
        if (notes == null || notes.isEmpty()) return true;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Note note : notes) {
                if (note != null) {
                    ContentValues values = new ContentValues();
                    values.put(NotesDbHelper.COLUMN_ID, note.getId());
                    values.put(NotesDbHelper.COLUMN_TITLE, note.getTitle());
                    values.put(NotesDbHelper.COLUMN_CONTENT, note.getContent());
                    values.put(NotesDbHelper.COLUMN_TIMESTAMP, note.getTimestamp());

                    db.insertWithOnConflict(
                            NotesDbHelper.TABLE_NOTES,
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_REPLACE
                    );
                }
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public boolean insertNote(Note note) {
        if (note == null) return false;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(NotesDbHelper.COLUMN_ID, note.getId());
        values.put(NotesDbHelper.COLUMN_TITLE, note.getTitle());
        values.put(NotesDbHelper.COLUMN_CONTENT, note.getContent());
        values.put(NotesDbHelper.COLUMN_TIMESTAMP, note.getTimestamp());

        long result = db.insertWithOnConflict(
                NotesDbHelper.TABLE_NOTES,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
        return result != -1;
    }

    @Override
    public boolean updateNote(Note note) {
        if (note == null) return false;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(NotesDbHelper.COLUMN_TITLE, note.getTitle());
        values.put(NotesDbHelper.COLUMN_CONTENT, note.getContent());
        values.put(NotesDbHelper.COLUMN_TIMESTAMP, note.getTimestamp());

        String selection = NotesDbHelper.COLUMN_ID + " = ?";
        String[] selectionArgs = {note.getId()};

        int rows = db.update(NotesDbHelper.TABLE_NOTES, values, selection, selectionArgs);
        return rows > 0;
    }

    @Override
    public boolean deleteNote(String id) {
        if (id == null) return false;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String selection = NotesDbHelper.COLUMN_ID + " = ?";
        String[] selectionArgs = {id};

        int rows = db.delete(NotesDbHelper.TABLE_NOTES, selection, selectionArgs);
        return rows > 0;
    }

    @Override
    public void deleteAllNotes() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(NotesDbHelper.TABLE_NOTES, null, null);
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.SQLITE;
    }
}
