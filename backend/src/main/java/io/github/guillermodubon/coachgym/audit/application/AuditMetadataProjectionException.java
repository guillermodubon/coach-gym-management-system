package io.github.guillermodubon.coachgym.audit.application;

/** Safe failure raised when an audit detail cannot be projected privately. */
public final class AuditMetadataProjectionException extends RuntimeException {

    public AuditMetadataProjectionException(String message) {
        super(message);
    }

    public AuditMetadataProjectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
