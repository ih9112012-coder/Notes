package com.example.notes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.notes.model.Note;
import com.example.notes.model.StorageType;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Unit tests for Note model and JSON serialization.
 */
public class NoteTest {

    @Test
    public void testNoteCreationWithDefaults() {
        Note note = new Note("Shopping List", "Milk, Bread, Coffee", StorageType.SQLITE);

        assertNotNull(note.getId());
        assertFalse(note.getId().isEmpty());
        assertEquals("Shopping List", note.getTitle());
        assertEquals("Milk, Bread, Coffee", note.getContent());
        assertEquals(StorageType.SQLITE, note.getStorageType());
        assertTrue(note.getTimestamp() > 0);
        assertNotNull(note.getFormattedDate());
    }

    @Test
    public void testNoteFullConstructor() {
        long timestamp = 1700000000000L;
        Note note = new Note("custom-id-123", "Meeting", "Discuss architecture", timestamp, StorageType.JSON_FILE);

        assertEquals("custom-id-123", note.getId());
        assertEquals("Meeting", note.getTitle());
        assertEquals("Discuss architecture", note.getContent());
        assertEquals(timestamp, note.getTimestamp());
        assertEquals(StorageType.JSON_FILE, note.getStorageType());
    }

    @Test
    public void testJsonSerializationRoundTrip() throws JSONException {
        long timestamp = 1700000000000L;
        Note originalNote = new Note("uuid-abc", "Project Plan", "Step 1: Planning\nStep 2: Coding", timestamp, StorageType.JSON_FILE);

        JSONObject json = originalNote.toJson();
        assertNotNull(json);
        assertEquals("uuid-abc", json.getString(Note.JSON_KEY_ID));
        assertEquals("Project Plan", json.getString(Note.JSON_KEY_TITLE));
        assertEquals("Step 1: Planning\nStep 2: Coding", json.getString(Note.JSON_KEY_CONTENT));
        assertEquals(timestamp, json.getLong(Note.JSON_KEY_TIMESTAMP));

        Note deserialized = Note.fromJson(json, StorageType.JSON_FILE);
        assertEquals(originalNote.getId(), deserialized.getId());
        assertEquals(originalNote.getTitle(), deserialized.getTitle());
        assertEquals(originalNote.getContent(), deserialized.getContent());
        assertEquals(originalNote.getTimestamp(), deserialized.getTimestamp());
        assertEquals(StorageType.JSON_FILE, deserialized.getStorageType());
    }

    @Test
    public void testNoteEqualsAndHashCode() {
        Note note1 = new Note("id-same", "Title 1", "Content 1", 1000L, StorageType.SQLITE);
        Note note2 = new Note("id-same", "Title 2", "Content 2", 2000L, StorageType.JSON_FILE);
        Note note3 = new Note("id-different", "Title 1", "Content 1", 1000L, StorageType.SQLITE);

        assertEquals(note1, note2);
        assertEquals(note1.hashCode(), note2.hashCode());
        assertFalse(note1.equals(note3));
    }
}
