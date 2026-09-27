package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/** Safe aggregate fact describing assignment history closed by account deactivation. */
public record StaffIdentityAssignmentsClosed(
        UUID targetUserId,
        UUID actorUserId,
        int assignmentCount,
        Instant occurredAt,
        boolean reasonPresent) {

    public StaffIdentityAssignmentsClosed {
        targetUserId = StaffAssignmentValuePolicy.requireId(targetUserId, "targetUserId");
        actorUserId = StaffAssignmentValuePolicy.requireId(actorUserId, "actorUserId");
        occurredAt = StaffAssignmentValuePolicy.requireInstant(occurredAt, "occurredAt");
        if (targetUserId.equals(actorUserId) || assignmentCount < 0) {
            throw new StaffIdentityValidationException("Closed assignment event data is invalid.");
        }
    }
}
