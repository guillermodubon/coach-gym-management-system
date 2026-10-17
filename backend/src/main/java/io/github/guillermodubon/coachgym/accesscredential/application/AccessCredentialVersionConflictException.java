package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Indicates an optimistic-lock conflict for a credential lifecycle operation. */
public class AccessCredentialVersionConflictException extends RuntimeException {

    private final UUID credentialId;
    private final long expectedVersion;
    private final long currentVersion;

    public AccessCredentialVersionConflictException(
            UUID credentialId,
            long expectedVersion,
            long currentVersion) {
        super("Access credential was modified by another operation.");
        this.credentialId = credentialId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public UUID credentialId() {
        return credentialId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
