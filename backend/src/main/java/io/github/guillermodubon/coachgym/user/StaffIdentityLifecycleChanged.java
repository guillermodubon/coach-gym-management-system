package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Privacy-minimized account lifecycle fact published only after commit. */
public record StaffIdentityLifecycleChanged(
        UUID targetUserId,
        StaffIdentityStatus previousStatus,
        StaffIdentityStatus newStatus,
        UUID actorUserId,
        Instant occurredAt,
        boolean reasonPresent) {

    public StaffIdentityLifecycleChanged {
        targetUserId = StaffAssignmentValuePolicy.requireId(targetUserId, "targetUserId");
        previousStatus = Objects.requireNonNull(previousStatus);
        newStatus = Objects.requireNonNull(newStatus);
        actorUserId = StaffAssignmentValuePolicy.requireId(actorUserId, "actorUserId");
        occurredAt = StaffAssignmentValuePolicy.requireInstant(occurredAt, "occurredAt");
        if (targetUserId.equals(actorUserId) || previousStatus == newStatus
                || previousStatus == StaffIdentityStatus.INVITED
                || newStatus == StaffIdentityStatus.INVITED) {
            throw new StaffIdentityValidationException("Lifecycle event data is invalid.");
        }
    }
}
