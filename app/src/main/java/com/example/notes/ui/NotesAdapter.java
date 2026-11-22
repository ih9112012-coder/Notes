package com.example.notes.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.notes.R;
import com.example.notes.databinding.ItemNoteBinding;
import com.example.notes.model.Note;
import com.example.notes.model.StorageType;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter displaying notes with titles, content previews,
 * formatted timestamps, and storage type badges.
 */
public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    public interface OnNoteDeleteClickListener {
        void onNoteDeleteClick(Note note);
    }

    private final List<Note> notes = new ArrayList<>();
    private final OnNoteClickListener clickListener;
    private final OnNoteDeleteClickListener deleteClickListener;

    public NotesAdapter(OnNoteClickListener clickListener, OnNoteDeleteClickListener deleteClickListener) {
        this.clickListener = clickListener;
        this.deleteClickListener = deleteClickListener;
    }

    public void setNotes(List<Note> newNotes) {
        notes.clear();
        if (newNotes != null) {
            notes.addAll(newNotes);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new NoteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        holder.bind(notes.get(position));
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        private final ItemNoteBinding binding;

        public NoteViewHolder(@NonNull ItemNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Note note) {
            binding.tvNoteTitle.setText(note.getTitle());
            binding.tvNoteContent.setText(note.getContent());
            binding.tvNoteDate.setText(note.getFormattedDate());

            // Storage badge styling
            if (note.getStorageType() == StorageType.JSON_FILE) {
                binding.tvStorageBadge.setText(R.string.storage_json_badge);
                binding.tvStorageBadge.setBackground(
                        ContextCompat.getDrawable(itemView.getContext(), R.drawable.bg_badge_json)
                );
                binding.tvStorageBadge.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.color_json)
                );
            } else {
                binding.tvStorageBadge.setText(R.string.storage_sqlite_badge);
                binding.tvStorageBadge.setBackground(
                        ContextCompat.getDrawable(itemView.getContext(), R.drawable.bg_badge_sqlite)
                );
                binding.tvStorageBadge.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.color_sqlite)
                );
            }

            binding.cardNote.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onNoteClick(note);
                }
            });

            binding.btnDeleteNote.setOnClickListener(v -> {
                if (deleteClickListener != null) {
                    deleteClickListener.onNoteDeleteClick(note);
                }
            });
        }
    }
}
