package io.github.guillermodubon.coachgym.user;

import java.util.Objects;

/** Pure lifecycle rules for append-only staff branch assignments. */
public final class StaffBranchAssignmentPolicy {

    private StaffBranchAssignmentPolicy() {
    }

    public static void requireCreationAllowed(boolean branchIsActive, boolean duplicateActiveAssignment) {
        if (!branchIsActive) {
            throw new StaffBranchAssignmentStateConflictException("assignment branch is not active");
        }
        if (duplicateActiveAssignment) {
            throw new StaffBranchAssignmentStateConflictException("an active assignment already exists");
        }
    }

    public static void requireEndAllowed(
            StaffBranchAssignmentDetails current,
            EndStaffBranchAssignmentCommand command) {
        Objects.requireNonNull(current, "current assignment is required");
        Objects.requireNonNull(command, "command is required");
        if (current.status() != StaffBranchAssignmentStatus.ACTIVE) {
            throw new StaffBranchAssignmentStateConflictException("only an active assignment may be ended");
        }
        if (!current.id().equals(command.assignmentId())) {
            throw new StaffBranchAssignmentValidationException("assignment id does not match current assignment");
        }
        if (current.version() != command.expectedVersion()) {
            throw new StaffBranchAssignmentStateConflictException("assignment version is stale");
        }
    }

    public static void requireActiveAssignmentRetained(
            StaffAuthorizationContext target,
            boolean anotherActiveAssignmentExists,
            boolean simultaneousValidScopeChange) {
        Objects.requireNonNull(target, "target is required");
        if (target.scopeType() == StaffScopeType.BRANCH
                && target.accountStatus() == StaffAccountStatus.ACTIVE
                && !anotherActiveAssignmentExists
                && !simultaneousValidScopeChange) {
            throw new StaffBranchAssignmentStateConflictException(
                    "active branch-scoped staff must retain one active branch assignment");
        }
    }
}
