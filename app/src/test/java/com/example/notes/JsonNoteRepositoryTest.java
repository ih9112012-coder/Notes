package com.example.notes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.notes.model.Note;
import com.example.notes.model.StorageType;
import com.example.notes.repository.JsonNoteRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests validating JsonNoteRepository with atomic file persistence,
 * crash resilience, stream reading, and batch operations.
 */
public class JsonNoteRepositoryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File tempFile;
    private JsonNoteRepository repository;

    @Before
    public void setUp() throws IOException {
        tempFile = tempFolder.newFile("test_notes.json");
        repository = new JsonNoteRepository(tempFile);
    }

    @After
    public void tearDown() {
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    public void testEmptyFileReturnsEmptyList() {
        List<Note> notes = repository.getAllNotes();
        assertNotNull(notes);
        assertTrue(notes.isEmpty());
    }

    @Test
    public void testNonExistentFileReturnsEmptyList() {
        File nonExistent = new File(tempFolder.getRoot(), "does_not_exist.json");
        JsonNoteRepository repo = new JsonNoteRepository(nonExistent);
        List<Note> notes = repo.getAllNotes();
        assertNotNull(notes);
        assertTrue(notes.isEmpty());
    }

    @Test
    public void testSaveAndRetrieveNotes() {
        Note note1 = new Note("id-1", "First Note", "Body 1", 1000L, StorageType.JSON_FILE);
        Note note2 = new Note("id-2", "Second Note", "Body 2", 2000L, StorageType.JSON_FILE);

        assertTrue(repository.saveNote(note1));
        assertTrue(repository.saveNote(note2));

        List<Note> allNotes = repository.getAllNotes();
        assertEquals(2, allNotes.size());
        // Sorted newest timestamp first
        assertEquals("id-2", allNotes.get(0).getId());
        assertEquals("id-1", allNotes.get(1).getId());

        Note fetched = repository.getNoteById("id-1");
        assertNotNull(fetched);
        assertEquals("First Note", fetched.getTitle());
        assertEquals("Body 1", fetched.getContent());
    }

    @Test
    public void testBatchSaveNotes() {
        Note note1 = new Note("batch-1", "B1", "Content 1", 1000L, StorageType.JSON_FILE);
        Note note2 = new Note("batch-2", "B2", "Content 2", 2000L, StorageType.JSON_FILE);

        assertTrue(repository.saveNotes(Arrays.asList(note1, note2)));

        List<Note> allNotes = repository.getAllNotes();
        assertEquals(2, allNotes.size());
        assertEquals("batch-2", allNotes.get(0).getId());
        assertEquals("batch-1", allNotes.get(1).getId());
    }

    @Test
    public void testUpdateExistingNote() {
        Note note = new Note("id-up", "Initial Title", "Initial Content", 1000L, StorageType.JSON_FILE);
        repository.saveNote(note);

        Note updated = new Note("id-up", "Updated Title", "Updated Content", 3000L, StorageType.JSON_FILE);
        assertTrue(repository.saveNote(updated));

        Note fetched = repository.getNoteById("id-up");
        assertNotNull(fetched);
        assertEquals("Updated Title", fetched.getTitle());
        assertEquals("Updated Content", fetched.getContent());
        assertEquals(3000L, fetched.getTimestamp());

        // Should still only have 1 note total
        assertEquals(1, repository.getAllNotes().size());
    }

    @Test
    public void testDeleteNote() {
        Note note = new Note("id-del", "To Delete", "Content", 1000L, StorageType.JSON_FILE);
        repository.saveNote(note);
        assertEquals(1, repository.getAllNotes().size());

        assertTrue(repository.deleteNote("id-del"));
        assertNull(repository.getNoteById("id-del"));
        assertTrue(repository.getAllNotes().isEmpty());

        // Deleting non-existent note should return false
        assertFalse(repository.deleteNote("id-non-existent"));
    }

    @Test
    public void testDeleteAllNotes() {
        repository.saveNote(new Note("id-1", "N1", "C1", 1000L, StorageType.JSON_FILE));
        repository.saveNote(new Note("id-2", "N2", "C2", 2000L, StorageType.JSON_FILE));
        assertEquals(2, repository.getAllNotes().size());

        repository.deleteAllNotes();
        assertTrue(repository.getAllNotes().isEmpty());
    }

    @Test
    public void testPersistenceAcrossRepositoryInstances() {
        Note note = new Note("id-persist", "Persisted Title", "Persisted Body", 5000L, StorageType.JSON_FILE);
        assertTrue(repository.saveNote(note));

        // Reopen same file in a brand new repository instance
        JsonNoteRepository freshInstance = new JsonNoteRepository(tempFile);
        List<Note> notes = freshInstance.getAllNotes();
        assertEquals(1, notes.size());
        assertEquals("id-persist", notes.get(0).getId());
        assertEquals("Persisted Title", notes.get(0).getTitle());
    }

    @Test
    public void testCorruptedJsonFileGracefullyHandled() throws IOException {
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("{ this is not a valid JSON array !!");
        }

        List<Note> notes = repository.getAllNotes();
        assertNotNull(notes);
        assertTrue(notes.isEmpty());
    }
}
