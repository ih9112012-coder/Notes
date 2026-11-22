package com.example.notes.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.notes.R;
import com.example.notes.databinding.ActivityMainBinding;
import com.example.notes.model.Note;
import com.example.notes.model.StorageType;
import com.example.notes.repository.StorageManager;
import com.example.notes.sync.NoteSyncManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Main Activity of the Notes application.
 * Manages notes listing, active storage switching (Requirement 4),
 * asynchronous bidirectional synchronization (Requirement 5), and note interactions
 * completely decoupled from the main UI thread.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private StorageManager storageManager;
    private NotesAdapter notesAdapter;

    // Modern Activity Result Launcher for note creation & editing
    private final ActivityResultLauncher<Intent> noteEditLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadNotes();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        storageManager = StorageManager.getInstance(this);

        setupToolbar();
        setupRecyclerView();
        setupStorageToggle();
        setupFab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotes();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_notes_list);
        }
    }

    private void setupRecyclerView() {
        notesAdapter = new NotesAdapter(
                this::openNoteForEdit,
                this::confirmDeleteNote
        );

        binding.rvNotes.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotes.setAdapter(notesAdapter);
    }

    /**
     * Configures the storage switch (MADT Lab #5 Requirement 4).
     */
    private void setupStorageToggle() {
        StorageType currentStorage = storageManager.getActiveStorageType();
        if (currentStorage == StorageType.JSON_FILE) {
            binding.toggleStorageGroup.check(R.id.btnStorageJson);
        } else {
            binding.toggleStorageGroup.check(R.id.btnStorageSqlite);
        }

        binding.toggleStorageGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            StorageType newStorage = (checkedId == R.id.btnStorageJson)
                    ? StorageType.JSON_FILE
                    : StorageType.SQLITE;

            if (newStorage != storageManager.getActiveStorageType()) {
                storageManager.setActiveStorageType(newStorage);

                String storageName = newStorage == StorageType.JSON_FILE
                        ? getString(R.string.storage_json)
                        : getString(R.string.storage_sqlite);

                Toast.makeText(this, getString(R.string.toast_storage_switched, storageName), Toast.LENGTH_SHORT).show();
                loadNotes();
            }
        });
    }

    private void setupFab() {
        binding.fabAddNote.setOnClickListener(v -> {
            StorageType activeStorage = storageManager.getActiveStorageType();
            Intent intent = NoteEditActivity.newIntent(this, null, activeStorage);
            noteEditLauncher.launch(intent);
        });
    }

    private void openNoteForEdit(Note note) {
        if (note == null) return;
        StorageType activeStorage = storageManager.getActiveStorageType();
        Intent intent = NoteEditActivity.newIntent(this, note.getId(), activeStorage);
        noteEditLauncher.launch(intent);
    }

    /**
     * Asynchronously loads notes from the active storage off the UI thread.
     */
    private void loadNotes() {
        StorageType activeType = storageManager.getActiveStorageType();
        String storageName = activeType == StorageType.JSON_FILE
                ? getString(R.string.storage_json)
                : getString(R.string.storage_sqlite);

        binding.tvActiveStorageStatus.setText(getString(R.string.storage_active_label, storageName));

        storageManager.loadNotesAsync(activeType, notes -> {
            if (isFinishing() || isDestroyed()) return;

            binding.tvNotesCount.setText(getString(R.string.notes_count_format, notes.size()));
            notesAdapter.setNotes(notes);

            if (notes.isEmpty()) {
                binding.rvNotes.setVisibility(View.GONE);
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.tvEmptySubtitle.setText(getString(R.string.empty_notes_subtitle, storageName));
            } else {
                binding.rvNotes.setVisibility(View.VISIBLE);
                binding.layoutEmptyState.setVisibility(View.GONE);
            }
        });
    }

    private void confirmDeleteNote(Note note) {
        if (note == null) return;

        StorageType activeType = storageManager.getActiveStorageType();
        String storageName = activeType == StorageType.JSON_FILE
                ? getString(R.string.storage_json)
                : getString(R.string.storage_sqlite);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_note_title)
                .setMessage(getString(R.string.dialog_delete_note_message, note.getTitle(), storageName))
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    storageManager.deleteNoteAsync(note.getId(), activeType, success -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, getString(R.string.toast_note_deleted, storageName), Toast.LENGTH_SHORT).show();
                        loadNotes();
                    });
                })
                .setNegativeButton(R.string.dialog_dismiss, null)
                .show();
    }

    private void confirmDeleteAllNotes() {
        StorageType activeType = storageManager.getActiveStorageType();
        String storageName = activeType == StorageType.JSON_FILE
                ? getString(R.string.storage_json)
                : getString(R.string.storage_sqlite);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_all_title)
                .setMessage(getString(R.string.dialog_delete_all_message, storageName))
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    storageManager.deleteAllNotesAsync(activeType, v -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, getString(R.string.toast_all_deleted, storageName), Toast.LENGTH_SHORT).show();
                        loadNotes();
                    });
                })
                .setNegativeButton(R.string.dialog_dismiss, null)
                .show();
    }

    /**
     * Executes asynchronous synchronization between SQLite and JSON file storage
     * (MADT Lab #5 Requirement 5) off the UI thread to prevent ANR.
     */
    private void performSynchronization() {
        Toast.makeText(this, R.string.sync_in_progress, Toast.LENGTH_SHORT).show();

        storageManager.synchronizeAsync(result -> {
            if (isFinishing() || isDestroyed()) return;

            String message;
            if (result.hasChanges()) {
                message = getString(
                        R.string.sync_success_summary,
                        result.getAddedToPrimary(),
                        result.getAddedToSecondary(),
                        result.getUpdatedInPrimary(),
                        result.getUpdatedInSecondary(),
                        result.getAlreadyInSync()
                );
            } else {
                message = getString(R.string.sync_no_changes);
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.sync_dialog_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.sync_ok_button, (dialog, which) -> loadNotes())
                    .show();

            loadNotes();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sync) {
            performSynchronization();
            return true;
        } else if (id == R.id.action_delete_all) {
            confirmDeleteAllNotes();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
