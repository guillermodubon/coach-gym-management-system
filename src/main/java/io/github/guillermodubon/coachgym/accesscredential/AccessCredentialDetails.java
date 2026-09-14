package io.github.guillermodubon.coachgym.accesscredential;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, nonsecret metadata for a client access credential.
 *
 * <p>The contract deliberately contains neither the raw token, its protected
 * representation, the QR payload, nor a storage path.</p>
 */
public record AccessCredentialDetails(
        UUID id,
        UUID clientId,
        String credentialCode,
        AccessCredentialStatus status,
        String payloadVersion,
        Instant issuedAt,
        UUID issuedByUserId,
        Instant revokedAt,
        UUID revokedByUserId,
        UUID replacedByCredentialId,
        long version) {

    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_PAYLOAD_VERSION_LENGTH = 16;

    public AccessCredentialDetails {
        id = requiredId(id, "Credential id");
        clientId = requiredId(clientId, "Client id");
        credentialCode = requiredText(credentialCode, "Credential code", MAX_CODE_LENGTH);
        if (status == null) {
            throw new IllegalArgumentException("Credential status is required.");
        }
        payloadVersion = requiredPayloadVersion(payloadVersion);
        if (issuedAt == null) {
            throw new IllegalArgumentException("Credential issued timestamp is required.");
        }
        issuedByUserId = requiredId(issuedByUserId, "Credential issuer id");
        if (version < 0) {
            throw new IllegalArgumentException("Credential version must not be negative.");
        }
        if (replacedByCredentialId != null && id.equals(replacedByCredentialId)) {
            throw new IllegalArgumentException("Credential cannot replace itself.");
        }

        if (status == AccessCredentialStatus.ACTIVE
                && (revokedAt != null
                        || revokedByUserId != null
                        || replacedByCredentialId != null)) {
            throw new IllegalArgumentException(
                    "An active credential cannot contain revocation metadata.");
        }

        if (status == AccessCredentialStatus.REVOKED) {
            if (revokedAt == null || revokedByUserId == null) {
                throw new IllegalArgumentException(
                        "A revoked credential requires revocation metadata.");
            }
            if (revokedAt.isBefore(issuedAt)) {
                throw new IllegalArgumentException(
                        "Credential revocation cannot precede issuance.");
            }
        }
    }

    private static UUID requiredId(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private static String requiredPayloadVersion(String value) {
        String normalized = requiredText(
                value, "Credential payload version", MAX_PAYLOAD_VERSION_LENGTH);
        if (!normalized.matches("v[0-9]+")) {
            throw new IllegalArgumentException(
                    "Credential payload version must use the vN format.");
        }
        return normalized;
    }
}
