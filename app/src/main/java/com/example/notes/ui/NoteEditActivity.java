package com.example.notes.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.notes.R;
import com.example.notes.databinding.ActivityNoteEditBinding;
import com.example.notes.model.Note;
import com.example.notes.model.StorageType;
import com.example.notes.repository.StorageManager;
import com.example.notes.util.NoteValidator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Activity for creating or editing an individual note.
 * Handles input validation (Requirement 6), saving into selected storage (Requirement 4),
 * asynchronous disk operations, and resilient state restoration across orientation changes.
 */
public class NoteEditActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE_ID = "com.example.notes.EXTRA_NOTE_ID";
    public static final String EXTRA_STORAGE_TYPE = "com.example.notes.EXTRA_STORAGE_TYPE";

    public static Intent newIntent(Context context, String noteId, StorageType storageType) {
        Intent intent = new Intent(context, NoteEditActivity.class);
        if (noteId != null) {
            intent.putExtra(EXTRA_NOTE_ID, noteId);
        }
        intent.putExtra(EXTRA_STORAGE_TYPE, storageType.name());
        return intent;
    }

    private ActivityNoteEditBinding binding;
    private StorageManager storageManager;
    private StorageType targetStorageType;
    private String existingNoteId;
    private Note existingNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNoteEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        storageManager = StorageManager.getInstance(this);

        // Parse intent parameters
        String storageTypeName = getIntent().getStringExtra(EXTRA_STORAGE_TYPE);
        if (storageTypeName != null) {
            try {
                targetStorageType = StorageType.valueOf(storageTypeName);
            } catch (IllegalArgumentException e) {
                targetStorageType = storageManager.getActiveStorageType();
            }
        } else {
            targetStorageType = storageManager.getActiveStorageType();
        }

        existingNoteId = getIntent().getStringExtra(EXTRA_NOTE_ID);

        setupToolbar();
        setupStorageChip();

        // Only load from disk if fresh launch; on rotation, Android view hierarchy restores typed text
        if (savedInstanceState == null) {
            loadExistingNoteIfNeeded();
        } else if (isEditMode()) {
            // Restore existingNote reference in background without overwriting typed fields
            storageManager.getNoteByIdAsync(existingNoteId, targetStorageType, note -> {
                if (!isFinishing() && !isDestroyed()) {
                    existingNote = note;
                }
            });
        }

        setupListeners();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            if (isEditMode()) {
                getSupportActionBar().setTitle(R.string.title_edit_note);
            } else {
                getSupportActionBar().setTitle(R.string.title_create_note);
            }
        }
    }

    private void setupStorageChip() {
        String storageName = targetStorageType == StorageType.JSON_FILE
                ? getString(R.string.storage_json)
                : getString(R.string.storage_sqlite);
        binding.chipStorageTarget.setText(getString(R.string.storage_saving_to, storageName));
    }

    private boolean isEditMode() {
        return existingNoteId != null && !existingNoteId.isEmpty();
    }

    private void loadExistingNoteIfNeeded() {
        if (isEditMode()) {
            storageManager.getNoteByIdAsync(existingNoteId, targetStorageType, note -> {
                if (isFinishing() || isDestroyed()) return;
                existingNote = note;
                if (existingNote != null) {
                    binding.etNoteTitle.setText(existingNote.getTitle());
                    binding.etNoteContent.setText(existingNote.getContent());
                }
            });
        }
    }

    private void setupListeners() {
        binding.btnSave.setOnClickListener(v -> saveNote());
        binding.btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_note_edit, menu);
        MenuItem deleteItem = menu.findItem(R.id.action_delete_note);
        if (deleteItem != null) {
            deleteItem.setVisible(isEditMode());
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        } else if (id == R.id.action_save_note) {
            saveNote();
            return true;
        } else if (id == R.id.action_delete_note) {
            confirmDeleteNote();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Enforces Requirement 6:
     * "The application must validate the entered data to prevent empty notes from being saved."
     * Saves asynchronously off the UI thread to prevent ANR.
     */
    private void saveNote() {
        String title = binding.etNoteTitle.getText() != null
                ? binding.etNoteTitle.getText().toString()
                : "";
        String content = binding.etNoteContent.getText() != null
                ? binding.etNoteContent.getText().toString()
                : "";

        // Reset error messages
        binding.tilNoteTitle.setError(null);
        binding.tilNoteContent.setError(null);

        NoteValidator.ValidationResult result = NoteValidator.validate(title, content);

        if (!result.isValid()) {
            if (!result.isTitleValid()) {
                binding.tilNoteTitle.setError(getString(R.string.error_title_empty));
            }
            if (!result.isContentValid()) {
                binding.tilNoteContent.setError(getString(R.string.error_content_empty));
            }
            Toast.makeText(this, R.string.error_validation_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        // Data is valid; proceed with asynchronous saving
        String storageDisplayName = targetStorageType == StorageType.JSON_FILE
                ? getString(R.string.storage_json)
                : getString(R.string.storage_sqlite);

        final Note noteToSave;
        if (isEditMode() && existingNote != null) {
            existingNote.setTitle(title.trim());
            existingNote.setContent(content.trim());
            existingNote.setTimestamp(System.currentTimeMillis());
            noteToSave = existingNote;
        } else if (isEditMode() && existingNoteId != null) {
            noteToSave = new Note(existingNoteId, title.trim(), content.trim(), System.currentTimeMillis(), targetStorageType);
        } else {
            noteToSave = new Note(title.trim(), content.trim(), targetStorageType);
        }

        // Prevent double submission while asynchronous write is executing
        binding.btnSave.setEnabled(false);

        storageManager.saveNoteAsync(noteToSave, targetStorageType, success -> {
            if (isFinishing() || isDestroyed()) return;
            binding.btnSave.setEnabled(true);
            if (success) {
                int toastRes = isEditMode() ? R.string.toast_note_updated : R.string.toast_note_saved;
                Toast.makeText(this, getString(toastRes, storageDisplayName), Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, R.string.error_saving_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteNote() {
        if (!isEditMode()) return;

        String noteTitle = existingNote != null ? existingNote.getTitle() : "";
        String noteId = existingNote != null ? existingNote.getId() : existingNoteId;
        if (noteId == null) return;

        String storageDisplayName = targetStorageType == StorageType.JSON_FILE
                ? getString(R.string.storage_json)
                : getString(R.string.storage_sqlite);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_note_title)
                .setMessage(getString(R.string.dialog_delete_note_message, noteTitle, storageDisplayName))
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    storageManager.deleteNoteAsync(noteId, targetStorageType, success -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, getString(R.string.toast_note_deleted, storageDisplayName), Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                })
                .setNegativeButton(R.string.dialog_dismiss, null)
                .show();
    }
}
