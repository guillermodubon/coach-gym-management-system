package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe fact that the one-time initial administrator bootstrap completed. */
public record StaffInitialAdministratorProvisioned(UUID userId, Instant occurredAt) {

    public StaffInitialAdministratorProvisioned {
        userId = StaffAssignmentValuePolicy.requireId(userId, "userId");
        occurredAt = StaffAssignmentValuePolicy.requireInstant(occurredAt, "occurredAt");
    }
}
