package edu.gcet.placementhub.database;

/**
 * Checked exception raised when the MongoDB cluster (Atlas or local) cannot be reached
 * or a data-layer operation fails. Services catch this (together with the driver's
 * unchecked {@link com.mongodb.MongoException}) and surface a friendly message to the UI.
 */
public class DatabaseException extends Exception {

    private final String remediationHint;

    public DatabaseException(String message) {
        this(message, null, null);
    }

    public DatabaseException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public DatabaseException(String message, String remediationHint, Throwable cause) {
        super(message, cause);
        this.remediationHint = remediationHint;
    }

    /**
     * Human-readable fix suggestion (e.g. whitelist 0.0.0.0/0 in Atlas) shown in the startup dialog.
     */
    public String getRemediationHint() {
        return remediationHint;
    }
}
