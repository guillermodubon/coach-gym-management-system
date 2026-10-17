package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import java.time.Instant;
import java.util.UUID;

/** Internal persistence input for one append-only lifecycle history row. */
public final class AccessCredentialHistoryPersistenceCommand {

    private final UUID credentialId;
    private final UUID clientId;
    private final AccessCredentialStatus previousStatus;
    private final AccessCredentialStatus newStatus;
    private final String reason;
    private final Instant occurredAt;
    private final UUID changedByUserId;
    private final UUID replacementCredentialId;

    public AccessCredentialHistoryPersistenceCommand(
            UUID credentialId,
            UUID clientId,
            AccessCredentialStatus previousStatus,
            AccessCredentialStatus newStatus,
            String reason,
            Instant occurredAt,
            UUID changedByUserId,
            UUID replacementCredentialId) {

        AccessCredentialHistoryDetails normalized = new AccessCredentialHistoryDetails(
                UUID.randomUUID(),
                credentialId,
                clientId,
                previousStatus,
                newStatus,
                reason,
                occurredAt,
                changedByUserId,
                replacementCredentialId);
        this.credentialId = normalized.credentialId();
        this.clientId = normalized.clientId();
        this.previousStatus = normalized.previousStatus();
        this.newStatus = normalized.newStatus();
        this.reason = normalized.reason();
        this.occurredAt = normalized.occurredAt();
        this.changedByUserId = normalized.changedByUserId();
        this.replacementCredentialId = normalized.replacementCredentialId();
    }

    public UUID credentialId() {
        return credentialId;
    }

    public UUID clientId() {
        return clientId;
    }

    public AccessCredentialStatus previousStatus() {
        return previousStatus;
    }

    public AccessCredentialStatus newStatus() {
        return newStatus;
    }

    public String reason() {
        return reason;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public UUID changedByUserId() {
        return changedByUserId;
    }

    public UUID replacementCredentialId() {
        return replacementCredentialId;
    }

    @Override
    public String toString() {
        return "AccessCredentialHistoryPersistenceCommand["
                + "credentialId=" + credentialId
                + ", clientId=" + clientId
                + ", previousStatus=" + previousStatus
                + ", newStatus=" + newStatus
                + ", reasonPresent=" + (reason != null)
                + ", occurredAt=" + occurredAt
                + ", changedByUserId=" + changedByUserId
                + ", replacementCredentialId=" + replacementCredentialId
                + ']';
    }
}
