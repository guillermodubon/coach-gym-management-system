package io.github.guillermodubon.coachgym.organization;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Privacy-safe event emitted after a meaningful branch update. */
public record GymBranchUpdated(
        UUID branchId,
        UUID organizationId,
        String branchCode,
        Set<String> changedFields,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public GymBranchUpdated {
        if (branchId == null || organizationId == null
                || actorUserId == null || occurredAt == null) {
            throw new GymBranchValidationException(
                    "Branch event identifiers and timestamp are required.");
        }
        branchCode = required(branchCode, "Branch event code is required.");
        actorIdentifier = required(actorIdentifier, "Branch event actor is required.");
        if (changedFields == null || changedFields.isEmpty()) {
            throw new GymBranchValidationException(
                    "Branch event changed fields are required.");
        }
        changedFields = Set.copyOf(changedFields);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new GymBranchValidationException(message);
        }
        return value.strip();
    }
}
