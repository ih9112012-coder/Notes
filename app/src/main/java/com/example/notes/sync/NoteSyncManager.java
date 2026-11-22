package com.example.notes.sync;

import com.example.notes.model.Note;
import com.example.notes.model.StorageType;
import com.example.notes.repository.NoteRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles bidirectional synchronization between primary (SQLite)
 * and secondary (JSON File) storage sources (MADT Lab #5 Requirement 5).
 */
public class NoteSyncManager {

    public static class SyncResult {
        private final int addedToPrimary;
        private final int addedToSecondary;
        private final int updatedInPrimary;
        private final int updatedInSecondary;
        private final int alreadyInSync;

        public SyncResult(int addedToPrimary, int addedToSecondary,
                          int updatedInPrimary, int updatedInSecondary,
                          int alreadyInSync) {
            this.addedToPrimary = addedToPrimary;
            this.addedToSecondary = addedToSecondary;
            this.updatedInPrimary = updatedInPrimary;
            this.updatedInSecondary = updatedInSecondary;
            this.alreadyInSync = alreadyInSync;
        }

        public int getAddedToPrimary() {
            return addedToPrimary;
        }

        public int getAddedToSecondary() {
            return addedToSecondary;
        }

        public int getUpdatedInPrimary() {
            return updatedInPrimary;
        }

        public int getUpdatedInSecondary() {
            return updatedInSecondary;
        }

        public int getAlreadyInSync() {
            return alreadyInSync;
        }

        public int getTotalChanges() {
            return addedToPrimary + addedToSecondary + updatedInPrimary + updatedInSecondary;
        }

        public boolean hasChanges() {
            return getTotalChanges() > 0;
        }
    }

    private final NoteRepository primaryRepo;
    private final NoteRepository secondaryRepo;

    public NoteSyncManager(NoteRepository primaryRepo, NoteRepository secondaryRepo) {
        this.primaryRepo = primaryRepo;
        this.secondaryRepo = secondaryRepo;
    }

    /**
     * Executes bidirectional synchronization between primary and secondary storage.
     * Uses timestamp-based conflict resolution (newest timestamp wins).
     */
    public SyncResult synchronize() {
        List<Note> primaryNotes = primaryRepo.getAllNotes();
        List<Note> secondaryNotes = secondaryRepo.getAllNotes();

        Map<String, Note> primaryMap = new HashMap<>();
        for (Note note : primaryNotes) {
            primaryMap.put(note.getId(), note);
        }

        Map<String, Note> secondaryMap = new HashMap<>();
        for (Note note : secondaryNotes) {
            secondaryMap.put(note.getId(), note);
        }

        Set<String> allIds = new HashSet<>();
        allIds.addAll(primaryMap.keySet());
        allIds.addAll(secondaryMap.keySet());

        int addedToPrimary = 0;
        int addedToSecondary = 0;
        int updatedInPrimary = 0;
        int updatedInSecondary = 0;
        int alreadyInSync = 0;

        List<Note> primaryBatch = new java.util.ArrayList<>();
        List<Note> secondaryBatch = new java.util.ArrayList<>();

        for (String id : allIds) {
            boolean inPrimary = primaryMap.containsKey(id);
            boolean inSecondary = secondaryMap.containsKey(id);

            if (inPrimary && !inSecondary) {
                // Exists in SQLite primary only -> propagate to JSON secondary
                Note pNote = primaryMap.get(id);
                Note copyForSecondary = new Note(
                        pNote.getId(),
                        pNote.getTitle(),
                        pNote.getContent(),
                        pNote.getTimestamp(),
                        StorageType.JSON_FILE
                );
                secondaryBatch.add(copyForSecondary);
                addedToSecondary++;
            } else if (!inPrimary && inSecondary) {
                // Exists in JSON secondary only -> propagate to SQLite primary
                Note sNote = secondaryMap.get(id);
                Note copyForPrimary = new Note(
                        sNote.getId(),
                        sNote.getTitle(),
                        sNote.getContent(),
                        sNote.getTimestamp(),
                        StorageType.SQLITE
                );
                primaryBatch.add(copyForPrimary);
                addedToPrimary++;
            } else {
                // Exists in both -> compare timestamps
                Note pNote = primaryMap.get(id);
                Note sNote = secondaryMap.get(id);

                if (pNote.getTimestamp() > sNote.getTimestamp()) {
                    // Primary has newer modification -> update secondary
                    Note copyForSecondary = new Note(
                            pNote.getId(),
                            pNote.getTitle(),
                            pNote.getContent(),
                            pNote.getTimestamp(),
                            StorageType.JSON_FILE
                    );
                    secondaryBatch.add(copyForSecondary);
                    updatedInSecondary++;
                } else if (sNote.getTimestamp() > pNote.getTimestamp()) {
                    // Secondary has newer modification -> update primary
                    Note copyForPrimary = new Note(
                            sNote.getId(),
                            sNote.getTitle(),
                            sNote.getContent(),
                            sNote.getTimestamp(),
                            StorageType.SQLITE
                    );
                    primaryBatch.add(copyForPrimary);
                    updatedInPrimary++;
                } else {
                    alreadyInSync++;
                }
            }
        }

        if (!primaryBatch.isEmpty()) {
            primaryRepo.saveNotes(primaryBatch);
        }
        if (!secondaryBatch.isEmpty()) {
            secondaryRepo.saveNotes(secondaryBatch);
        }

        return new SyncResult(
                addedToPrimary,
                addedToSecondary,
                updatedInPrimary,
                updatedInSecondary,
                alreadyInSync
        );
    }
}
