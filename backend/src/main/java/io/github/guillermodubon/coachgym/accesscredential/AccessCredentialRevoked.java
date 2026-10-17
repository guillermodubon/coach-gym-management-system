package io.github.guillermodubon.coachgym.accesscredential;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event for an irreversible access-credential revocation. */
public record AccessCredentialRevoked(
        UUID credentialId,
        UUID clientId,
        String credentialCode,
        AccessCredentialStatus previousStatus,
        AccessCredentialStatus newStatus,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt,
        boolean reasonPresent,
        UUID branchId) {

    public AccessCredentialRevoked(UUID credentialId, UUID clientId,
            String credentialCode, AccessCredentialStatus previousStatus,
            AccessCredentialStatus newStatus, UUID actorUserId,
            String actorIdentifier, Instant occurredAt, boolean reasonPresent) {
        this(credentialId, clientId, credentialCode, previousStatus, newStatus,
                actorUserId, actorIdentifier, occurredAt, reasonPresent, null);
    }

    public AccessCredentialRevoked {
        credentialId = requiredId(credentialId, "Credential event id");
        clientId = requiredId(clientId, "Client event client id");
        credentialCode = requiredText(credentialCode, "Credential event code");
        if (previousStatus != AccessCredentialStatus.ACTIVE
                || newStatus != AccessCredentialStatus.REVOKED) {
            throw new IllegalArgumentException(
                    "Credential revocation event must represent ACTIVE to REVOKED.");
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
