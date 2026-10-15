package io.github.guillermodubon.coachgym.organization;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event emitted after a branch is created. */
public record GymBranchCreated(
        UUID branchId,
        UUID organizationId,
        String branchCode,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public GymBranchCreated {
        if (branchId == null || organizationId == null
                || actorUserId == null || occurredAt == null) {
            throw new GymBranchValidationException(
                    "Branch event identifiers and timestamp are required.");
        }
        branchCode = required(branchCode, "Branch event code is required.");
        actorIdentifier = required(actorIdentifier, "Branch event actor is required.");
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new GymBranchValidationException(message);
        }
        return value.strip();
    }
}
