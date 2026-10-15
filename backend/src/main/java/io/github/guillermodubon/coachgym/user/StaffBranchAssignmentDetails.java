package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only lifecycle facts for one staff-to-branch assignment. */
public record StaffBranchAssignmentDetails(
        UUID id,
        UUID userId,
        UUID branchId,
        StaffBranchAssignmentStatus status,
        Instant assignedAt,
        UUID assignedByUserId,
        Instant endedAt,
        UUID endedByUserId,
        String endReason,
        long version) {

    public StaffBranchAssignmentDetails {
        id = StaffAssignmentValuePolicy.requireId(id, "id");
        userId = StaffAssignmentValuePolicy.requireId(userId, "userId");
        branchId = StaffAssignmentValuePolicy.requireId(branchId, "branchId");
        status = Objects.requireNonNull(status, "status is required");
        assignedAt = StaffAssignmentValuePolicy.requireInstant(assignedAt, "assignedAt");
        if (assignedByUserId != null && assignedByUserId.equals(userId)) {
            throw new StaffBranchAssignmentValidationException("a staff member cannot assign themselves");
        }
        StaffAssignmentValuePolicy.requireVersion(version, "version");
        if (status == StaffBranchAssignmentStatus.ACTIVE) {
            if (endedAt != null || endedByUserId != null || endReason != null) {
                throw new StaffBranchAssignmentValidationException(
                        "an active assignment cannot contain end metadata");
            }
        } else {
            endedAt = StaffAssignmentValuePolicy.requireInstant(endedAt, "endedAt");
            if (endedAt.isBefore(assignedAt)) {
                throw new StaffBranchAssignmentValidationException("endedAt cannot precede assignedAt");
            }
            endedByUserId = StaffAssignmentValuePolicy.requireId(endedByUserId, "endedByUserId");
            if (endedByUserId.equals(userId)) {
                throw new StaffBranchAssignmentValidationException("a staff member cannot end their own assignment");
            }
            endReason = StaffAssignmentValuePolicy.requireReason(endReason);
        }
    }
}
