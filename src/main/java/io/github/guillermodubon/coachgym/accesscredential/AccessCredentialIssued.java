package io.github.guillermodubon.coachgym.accesscredential;

import java.time.Instant;
import java.util.UUID;

/**
 * Privacy-safe lifecycle event emitted after an access credential becomes
 * canonical. Raw token material, QR payloads, digests, and storage metadata are
 * intentionally absent.
 */
public record AccessCredentialIssued(
        UUID credentialId,
        UUID clientId,
        String credentialCode,
        String payloadVersion,
        String tokenSchemeVersion,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt,
        UUID branchId) {

    public AccessCredentialIssued(UUID credentialId, UUID clientId,
            String credentialCode, String payloadVersion, String tokenSchemeVersion,
            UUID actorUserId, String actorIdentifier, Instant occurredAt) {
        this(credentialId, clientId, credentialCode, payloadVersion,
                tokenSchemeVersion, actorUserId, actorIdentifier, occurredAt, null);
    }

    public AccessCredentialIssued {
        credentialId = requiredId(credentialId, "Credential event id");
        clientId = requiredId(clientId, "Client event id");
        credentialCode = requiredText(credentialCode, "Credential event code");
        payloadVersion = requiredText(payloadVersion, "Credential event payload version");
        tokenSchemeVersion = requiredText(
                tokenSchemeVersion, "Credential event token scheme version");
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
