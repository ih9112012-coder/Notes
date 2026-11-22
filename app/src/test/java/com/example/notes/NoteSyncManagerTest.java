package com.example.notes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.notes.model.Note;
import com.example.notes.model.StorageType;
import com.example.notes.repository.NoteRepository;
import com.example.notes.sync.NoteSyncManager;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests enforcing MADT Lab #5 Requirement 5:
 * "The user must be able to synchronize notes from the primary source with the secondary source."
 */
public class NoteSyncManagerTest {

    /**
     * In-memory test fake implementation of NoteRepository.
     */
    private static class InMemoryNoteRepository implements NoteRepository {
        private final StorageType storageType;
        private final Map<String, Note> storage = new HashMap<>();

        public InMemoryNoteRepository(StorageType storageType) {
            this.storageType = storageType;
        }

        @Override
        public List<Note> getAllNotes() {
            return new ArrayList<>(storage.values());
        }

        @Override
        public Note getNoteById(String id) {
            return storage.get(id);
        }

        @Override
        public boolean saveNote(Note note) {
            storage.put(note.getId(), note);
            return true;
        }

        @Override
        public boolean insertNote(Note note) {
            storage.put(note.getId(), note);
            return true;
        }

        @Override
        public boolean updateNote(Note note) {
            storage.put(note.getId(), note);
            return true;
        }

        @Override
        public boolean deleteNote(String id) {
            return storage.remove(id) != null;
        }

        @Override
        public void deleteAllNotes() {
            storage.clear();
        }

        @Override
        public StorageType getStorageType() {
            return storageType;
        }
    }

    private InMemoryNoteRepository primaryRepo;
    private InMemoryNoteRepository secondaryRepo;
    private NoteSyncManager syncManager;

    @Before
    public void setUp() {
        primaryRepo = new InMemoryNoteRepository(StorageType.SQLITE);
        secondaryRepo = new InMemoryNoteRepository(StorageType.JSON_FILE);
        syncManager = new NoteSyncManager(primaryRepo, secondaryRepo);
    }

    @Test
    public void testSyncFromPrimaryToSecondary() {
        // SQLite has 2 notes, Secondary is empty
        Note n1 = new Note("id-1", "Note 1", "Content 1", 1000L, StorageType.SQLITE);
        Note n2 = new Note("id-2", "Note 2", "Content 2", 2000L, StorageType.SQLITE);
        primaryRepo.saveNote(n1);
        primaryRepo.saveNote(n2);

        NoteSyncManager.SyncResult result = syncManager.synchronize();

        assertEquals(0, result.getAddedToPrimary());
        assertEquals(2, result.getAddedToSecondary());
        assertEquals(0, result.getUpdatedInPrimary());
        assertEquals(0, result.getUpdatedInSecondary());
        assertEquals(0, result.getAlreadyInSync());
        assertTrue(result.hasChanges());
        assertEquals(2, result.getTotalChanges());

        // Verify secondary now contains both notes
        assertNotNull(secondaryRepo.getNoteById("id-1"));
        assertNotNull(secondaryRepo.getNoteById("id-2"));
        assertEquals("Note 1", secondaryRepo.getNoteById("id-1").getTitle());
    }

    @Test
    public void testSyncFromSecondaryToPrimary() {
        // JSON has 2 notes, SQLite is empty
        Note n1 = new Note("id-a", "JSON Note A", "Content A", 1000L, StorageType.JSON_FILE);
        Note n2 = new Note("id-b", "JSON Note B", "Content B", 2000L, StorageType.JSON_FILE);
        secondaryRepo.saveNote(n1);
        secondaryRepo.saveNote(n2);

        NoteSyncManager.SyncResult result = syncManager.synchronize();

        assertEquals(2, result.getAddedToPrimary());
        assertEquals(0, result.getAddedToSecondary());
        assertEquals(2, result.getTotalChanges());
        assertTrue(result.hasChanges());

        assertNotNull(primaryRepo.getNoteById("id-a"));
        assertNotNull(primaryRepo.getNoteById("id-b"));
        assertEquals("JSON Note A", primaryRepo.getNoteById("id-a").getTitle());
    }

    @Test
    public void testAlreadyInSyncProducesNoChanges() {
        Note n1 = new Note("id-1", "Title 1", "Content 1", 5000L, StorageType.SQLITE);
        primaryRepo.saveNote(n1);
        secondaryRepo.saveNote(new Note("id-1", "Title 1", "Content 1", 5000L, StorageType.JSON_FILE));

        NoteSyncManager.SyncResult result = syncManager.synchronize();

        assertEquals(0, result.getAddedToPrimary());
        assertEquals(0, result.getAddedToSecondary());
        assertEquals(0, result.getUpdatedInPrimary());
        assertEquals(0, result.getUpdatedInSecondary());
        assertEquals(1, result.getAlreadyInSync());
        assertFalse(result.hasChanges());
        assertEquals(0, result.getTotalChanges());
    }

    @Test
    public void testConflictResolutionPrimaryNewer() {
        // Same ID, but Primary was modified later
        Note primaryNote = new Note("id-sync", "Updated in SQLite", "Newer Content", 9000L, StorageType.SQLITE);
        Note secondaryNote = new Note("id-sync", "Old in JSON", "Older Content", 5000L, StorageType.JSON_FILE);
        primaryRepo.saveNote(primaryNote);
        secondaryRepo.saveNote(secondaryNote);

        NoteSyncManager.SyncResult result = syncManager.synchronize();

        assertEquals(0, result.getAddedToPrimary());
        assertEquals(0, result.getAddedToSecondary());
        assertEquals(0, result.getUpdatedInPrimary());
        assertEquals(1, result.getUpdatedInSecondary());
        assertTrue(result.hasChanges());

        // Secondary should now reflect primary's newer title
        assertEquals("Updated in SQLite", secondaryRepo.getNoteById("id-sync").getTitle());
        assertEquals("Newer Content", secondaryRepo.getNoteById("id-sync").getContent());
        assertEquals(9000L, secondaryRepo.getNoteById("id-sync").getTimestamp());
    }

    @Test
    public void testConflictResolutionSecondaryNewer() {
        // Same ID, but Secondary was modified later
        Note primaryNote = new Note("id-sync", "Old in SQLite", "Older Content", 3000L, StorageType.SQLITE);
        Note secondaryNote = new Note("id-sync", "Updated in JSON", "Newer Content", 7000L, StorageType.JSON_FILE);
        primaryRepo.saveNote(primaryNote);
        secondaryRepo.saveNote(secondaryNote);

        NoteSyncManager.SyncResult result = syncManager.synchronize();

        assertEquals(0, result.getAddedToPrimary());
        assertEquals(0, result.getAddedToSecondary());
        assertEquals(1, result.getUpdatedInPrimary());
        assertEquals(0, result.getUpdatedInSecondary());
        assertTrue(result.hasChanges());

        // Primary should now reflect secondary's newer title
        assertEquals("Updated in JSON", primaryRepo.getNoteById("id-sync").getTitle());
        assertEquals("Newer Content", primaryRepo.getNoteById("id-sync").getContent());
        assertEquals(7000L, primaryRepo.getNoteById("id-sync").getTimestamp());
    }
}
