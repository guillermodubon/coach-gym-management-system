package io.github.guillermodubon.coachgym.audit.application;

/** Safe application failure for an audit read operation. */
public final class AuditQueryDataAccessException extends RuntimeException {

    public AuditQueryDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
