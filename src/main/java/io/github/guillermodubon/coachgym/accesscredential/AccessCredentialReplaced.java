package io.github.guillermodubon.coachgym.accesscredential;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event for one atomic credential replacement. */
public record AccessCredentialReplaced(
        UUID previousCredentialId,
        UUID replacementCredentialId,
        UUID clientId,
        AccessCredentialStatus previousStatus,
        AccessCredentialStatus replacementStatus,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt,
        boolean reasonPresent,
        UUID branchId) {

    public AccessCredentialReplaced(UUID previousCredentialId,
            UUID replacementCredentialId, UUID clientId,
            AccessCredentialStatus previousStatus,
            AccessCredentialStatus replacementStatus, UUID actorUserId,
            String actorIdentifier, Instant occurredAt, boolean reasonPresent) {
        this(previousCredentialId, replacementCredentialId, clientId,
                previousStatus, replacementStatus, actorUserId, actorIdentifier,
                occurredAt, reasonPresent, null);
    }

    public AccessCredentialReplaced {
        previousCredentialId = requiredId(
                previousCredentialId, "Previous credential event id");
        replacementCredentialId = requiredId(
                replacementCredentialId, "Replacement credential event id");
        if (previousCredentialId.equals(replacementCredentialId)) {
            throw new IllegalArgumentException(
                    "Replacement event credentials must be different.");
        }
        clientId = requiredId(clientId, "Credential event client id");
        if (previousStatus != AccessCredentialStatus.REVOKED
                || replacementStatus != AccessCredentialStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Credential replacement event contains an invalid transition.");
        }
        actorUserId = requiredId(actorUserId, "Credential event actor id");
        actorIdentifier = requiredText(actorIdentifier, "Credential event actor identifier");
        if (occurredAt == null) {
            throw new IllegalArgumentException("Credential event timestamp is required.");
        }
    }

    private static UUID requiredId(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.strip();
    }
}
