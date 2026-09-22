package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentDetails;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStatus;
import java.time.Instant;
import java.util.UUID;

/** Safe administrative projection of one assignment lifecycle record. */
record StaffBranchAssignmentResponse(
        UUID id,
        UUID userId,
        UUID branchId,
        StaffBranchAssignmentStatus status,
        Instant assignedAt,
        Instant endedAt,
        String endReason,
        long version) {

    static StaffBranchAssignmentResponse from(StaffBranchAssignmentDetails details) {
        return new StaffBranchAssignmentResponse(
                details.id(), details.userId(), details.branchId(), details.status(),
                details.assignedAt(), details.endedAt(), details.endReason(), details.version());
    }
}
