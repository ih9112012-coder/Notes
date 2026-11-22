package com.example.notes.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Data model representing a note with user-visible title, content,
 * unique ID, modification timestamp, and source storage type.
 */
public class Note implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String JSON_KEY_ID = "id";
    public static final String JSON_KEY_TITLE = "title";
    public static final String JSON_KEY_CONTENT = "content";
    public static final String JSON_KEY_TIMESTAMP = "timestamp";

    private String id;
    private String title;
    private String content;
    private long timestamp;
    private StorageType storageType;

    /**
     * Default constructor for creating a brand new note.
     */
    public Note(String title, String content, StorageType storageType) {
        this(UUID.randomUUID().toString(), title, content, System.currentTimeMillis(), storageType);
    }

    /**
     * Full constructor for existing notes.
     */
    public Note(String id, String title, String content, long timestamp, StorageType storageType) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.title = title != null ? title : "";
        this.content = content != null ? content : "";
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.storageType = storageType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
    }

    /**
     * Format timestamp to human-readable date/time string.
     */
    public String getFormattedDate() {
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());
        return dateFormat.format(new Date(timestamp));
    }

    /**
     * Serialize note to JSON object for secondary storage.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(JSON_KEY_ID, id);
        jsonObject.put(JSON_KEY_TITLE, title);
        jsonObject.put(JSON_KEY_CONTENT, content);
        jsonObject.put(JSON_KEY_TIMESTAMP, timestamp);
        return jsonObject;
    }

    /**
     * Deserialize note from JSON object.
     */
    public static Note fromJson(JSONObject jsonObject, StorageType storageType) throws JSONException {
        String id = jsonObject.optString(JSON_KEY_ID, UUID.randomUUID().toString());
        String title = jsonObject.optString(JSON_KEY_TITLE, "");
        String content = jsonObject.optString(JSON_KEY_CONTENT, "");
        long timestamp = jsonObject.optLong(JSON_KEY_TIMESTAMP, System.currentTimeMillis());
        return new Note(id, title, content, timestamp, storageType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Note note = (Note) o;
        return Objects.equals(id, note.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Note{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", timestamp=" + timestamp +
                ", storageType=" + storageType +
                '}';
    }
}
