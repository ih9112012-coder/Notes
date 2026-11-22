package com.example.notes.util;

/**
 * Validates note inputs to enforce MADT Lab #5 Requirement 6:
 * "The application must validate the entered data to prevent empty notes from being saved."
 */
public class NoteValidator {

    public static class ValidationResult {
        private final boolean titleValid;
        private final boolean contentValid;

        public ValidationResult(boolean titleValid, boolean contentValid) {
            this.titleValid = titleValid;
            this.contentValid = contentValid;
        }

        public boolean isValid() {
            return titleValid && contentValid;
        }

        public boolean isTitleValid() {
            return titleValid;
        }

        public boolean isContentValid() {
            return contentValid;
        }
    }

    /**
     * Validates note title and content.
     * Both must be non-null and contain at least one non-whitespace character.
     */
    public static ValidationResult validate(String title, String content) {
        boolean titleValid = title != null && !title.trim().isEmpty();
        boolean contentValid = content != null && !content.trim().isEmpty();
        return new ValidationResult(titleValid, contentValid);
    }

    public static boolean isTitleValid(String title) {
        return title != null && !title.trim().isEmpty();
    }

    public static boolean isContentValid(String content) {
        return content != null && !content.trim().isEmpty();
    }
}
