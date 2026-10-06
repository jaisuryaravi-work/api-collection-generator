package com.hcm.postmangen.curl;

/**
 * One field from a curl --form / -F argument (multipart/form-data).
 * For a file upload (value started with "@"), value holds the local file
 * path and file is true; Postman calls this "src" and type "file".
 * Otherwise it's a plain text field.
 */
public class FormField {

    private final String key;
    private final String value;
    private final boolean file;

    public FormField(String key, String value, boolean file) {
        this.key = key;
        this.value = value;
        this.file = file;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public boolean isFile() {
        return file;
    }
}