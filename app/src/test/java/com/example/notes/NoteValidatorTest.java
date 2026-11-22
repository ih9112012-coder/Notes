package com.example.notes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.notes.util.NoteValidator;

import org.junit.Test;

/**
 * Unit tests enforcing MADT Lab #5 Requirement 6:
 * "The application must validate the entered data to prevent empty notes from being saved."
 */
public class NoteValidatorTest {

    @Test
    public void testValidTitleAndContent() {
        NoteValidator.ValidationResult result = NoteValidator.validate("Important Note", "This is the body content.");
        assertTrue(result.isValid());
        assertTrue(result.isTitleValid());
        assertTrue(result.isContentValid());
    }

    @Test
    public void testEmptyTitleIsInvalid() {
        NoteValidator.ValidationResult result = NoteValidator.validate("", "Valid body content.");
        assertFalse(result.isValid());
        assertFalse(result.isTitleValid());
        assertTrue(result.isContentValid());
    }

    @Test
    public void testWhitespaceOnlyTitleIsInvalid() {
        NoteValidator.ValidationResult result = NoteValidator.validate("    ", "Valid body content.");
        assertFalse(result.isValid());
        assertFalse(result.isTitleValid());
        assertTrue(result.isContentValid());
    }

    @Test
    public void testNullTitleIsInvalid() {
        NoteValidator.ValidationResult result = NoteValidator.validate(null, "Valid body content.");
        assertFalse(result.isValid());
        assertFalse(result.isTitleValid());
        assertTrue(result.isContentValid());
    }

    @Test
    public void testEmptyContentIsInvalid() {
        NoteValidator.ValidationResult result = NoteValidator.validate("Valid Title", "");
        assertFalse(result.isValid());
        assertTrue(result.isTitleValid());
        assertFalse(result.isContentValid());
    }

    @Test
    public void testWhitespaceOnlyContentIsInvalid() {
        NoteValidator.ValidationResult result = NoteValidator.validate("Valid Title", "   \t\n  ");
        assertFalse(result.isValid());
        assertTrue(result.isTitleValid());
        assertFalse(result.isContentValid());
    }

    @Test
    public void testNullContentIsInvalid() {
        NoteValidator.ValidationResult result = NoteValidator.validate("Valid Title", null);
        assertFalse(result.isValid());
        assertTrue(result.isTitleValid());
        assertFalse(result.isContentValid());
    }

    @Test
    public void testBothEmptyAreInvalid() {
        NoteValidator.ValidationResult result = NoteValidator.validate("", "");
        assertFalse(result.isValid());
        assertFalse(result.isTitleValid());
        assertFalse(result.isContentValid());
    }

    @Test
    public void testConvenienceHelperMethods() {
        assertTrue(NoteValidator.isTitleValid("Non-empty Title"));
        assertFalse(NoteValidator.isTitleValid(""));
        assertFalse(NoteValidator.isTitleValid(null));

        assertTrue(NoteValidator.isContentValid("Non-empty Content"));
        assertFalse(NoteValidator.isContentValid("   "));
        assertFalse(NoteValidator.isContentValid(null));
    }
}
