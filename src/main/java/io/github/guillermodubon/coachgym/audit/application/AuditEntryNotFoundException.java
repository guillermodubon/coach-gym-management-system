package io.github.guillermodubon.coachgym.audit.application;

import java.util.Objects;
import java.util.UUID;

/** Indicates that an administrative audit entry lookup found no entry. */
public final class AuditEntryNotFoundException extends RuntimeException {

    private final UUID auditEntryId;

    public AuditEntryNotFoundException(UUID auditEntryId) {
        super("Audit entry was not found.");
        this.auditEntryId = Objects.requireNonNull(
                auditEntryId,
                "Audit entry id must be provided.");
    }

    /** Returns the requested identifier for internal error mapping only. */
    public UUID auditEntryId() {
        return auditEntryId;
    }
}
