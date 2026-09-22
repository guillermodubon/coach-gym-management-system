package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event emitted after an active branch assignment is ended. */
public record StaffBranchAssignmentEnded(
        UUID assignmentId,
        UUID targetUserId,
        UUID branchId,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt,
        boolean reasonPresent) {

    public StaffBranchAssignmentEnded {
        assignmentId = StaffAssignmentValuePolicy.requireId(assignmentId, "assignmentId");
        targetUserId = StaffAssignmentValuePolicy.requireId(targetUserId, "targetUserId");
        branchId = StaffAssignmentValuePolicy.requireId(branchId, "branchId");
        actorUserId = StaffAssignmentValuePolicy.requireId(actorUserId, "actorUserId");
        actorIdentifier = StaffAssignmentValuePolicy.requireText(
                actorIdentifier, "actorIdentifier", 255);
        occurredAt = StaffAssignmentValuePolicy.requireInstant(occurredAt, "occurredAt");
        if (actorUserId.equals(targetUserId)) {
            throw new StaffBranchAssignmentValidationException(
                    "a staff member cannot end their own assignment");
        }
    }
}
