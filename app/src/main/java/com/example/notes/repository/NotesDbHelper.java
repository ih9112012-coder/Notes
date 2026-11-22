package com.example.notes.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLiteOpenHelper managing local database creation and schema versioning
 * for primary SQLite storage.
 */
public class NotesDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "notes_primary.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NOTES = "notes";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_NOTES + " (" +
                    COLUMN_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_TITLE + " TEXT NOT NULL, " +
                    COLUMN_CONTENT + " TEXT NOT NULL, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL);";

    private static final String SQL_DROP_TABLE =
            "DROP TABLE IF EXISTS " + TABLE_NOTES;

    public NotesDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DROP_TABLE);
        onCreate(db);
    }
}
