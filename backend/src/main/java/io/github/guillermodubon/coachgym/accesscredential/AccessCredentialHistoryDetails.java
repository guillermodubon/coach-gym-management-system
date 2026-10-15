package io.github.guillermodubon.coachgym.accesscredential;

import java.time.Instant;
import java.util.UUID;

/** Immutable append-only lifecycle history for one access credential. */
public record AccessCredentialHistoryDetails(
        UUID id,
        UUID credentialId,
        UUID clientId,
        AccessCredentialStatus previousStatus,
        AccessCredentialStatus newStatus,
        String reason,
        Instant occurredAt,
        UUID changedByUserId,
        UUID replacementCredentialId) {

    private static final int MIN_REASON_LENGTH = 3;
    private static final int MAX_REASON_LENGTH = 2000;

    public AccessCredentialHistoryDetails {
        id = requiredId(id, "History id");
        credentialId = requiredId(credentialId, "Credential id");
        clientId = requiredId(clientId, "Client id");
        if (newStatus == null) {
            throw new IllegalArgumentException("New credential status is required.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Credential history timestamp is required.");
        }
        changedByUserId = requiredId(changedByUserId, "History actor id");
        if (replacementCredentialId != null
                && credentialId.equals(replacementCredentialId)) {
            throw new IllegalArgumentException("Credential cannot replace itself.");
        }

        reason = normalizeReason(reason, previousStatus != null);
        validateTransition(previousStatus, newStatus, replacementCredentialId);
    }

    private static void validateTransition(
            AccessCredentialStatus previousStatus,
            AccessCredentialStatus newStatus,
            UUID replacementCredentialId) {
        if (previousStatus == null) {
            if (newStatus != AccessCredentialStatus.ACTIVE
                    || replacementCredentialId != null) {
                throw new IllegalArgumentException(
                        "Initial credential history must issue an active credential.");
            }
            return;
        }

        if (previousStatus != AccessCredentialStatus.ACTIVE
                || newStatus != AccessCredentialStatus.REVOKED) {
            throw new IllegalArgumentException(
                    "Credential history contains an unsupported status transition.");
        }
    }

    private static String normalizeReason(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new IllegalArgumentException(
                        "Credential lifecycle reason is required.");
            }
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() < MIN_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Credential lifecycle reason must contain at least "
                            + MIN_REASON_LENGTH + " characters.");
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Credential lifecycle reason must not exceed "
                            + MAX_REASON_LENGTH + " characters.");
        }
        return normalized;
    }

    private static UUID requiredId(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }
}
