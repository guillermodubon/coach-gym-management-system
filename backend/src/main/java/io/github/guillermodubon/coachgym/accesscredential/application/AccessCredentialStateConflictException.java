package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import java.util.UUID;

/** Indicates that a credential lifecycle transition is not currently allowed. */
public class AccessCredentialStateConflictException extends RuntimeException {

    private final UUID credentialId;
    private final AccessCredentialStatus currentStatus;
    private final AccessCredentialStatus requestedStatus;

    public AccessCredentialStateConflictException(
            UUID credentialId,
            AccessCredentialStatus currentStatus,
            AccessCredentialStatus requestedStatus) {
        super("Access credential transition is not allowed from the current state.");
        this.credentialId = credentialId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public UUID credentialId() {
        return credentialId;
    }

    public AccessCredentialStatus currentStatus() {
        return currentStatus;
    }

    public AccessCredentialStatus requestedStatus() {
        return requestedStatus;
    }
}
