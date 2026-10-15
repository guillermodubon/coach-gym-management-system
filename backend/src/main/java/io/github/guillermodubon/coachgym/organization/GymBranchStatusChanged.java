package io.github.guillermodubon.coachgym.organization;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event emitted after a branch lifecycle transition. */
public record GymBranchStatusChanged(
        UUID branchId,
        UUID organizationId,
        String branchCode,
        GymBranchStatus previousStatus,
        GymBranchStatus newStatus,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public GymBranchStatusChanged {
        if (branchId == null || organizationId == null
                || actorUserId == null || occurredAt == null) {
            throw new GymBranchValidationException(
                    "Branch event identifiers and timestamp are required.");
        }
        branchCode = required(branchCode, "Branch event code is required.");
        actorIdentifier = required(actorIdentifier, "Branch event actor is required.");
        if (previousStatus == null || newStatus == null) {
            throw new GymBranchValidationException(
                    "Branch event statuses are required.");
        }
        if (previousStatus == newStatus) {
            throw new GymBranchValidationException(
                    "Branch event statuses must represent a transition.");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new GymBranchValidationException(message);
        }
        return value.strip();
    }
}
