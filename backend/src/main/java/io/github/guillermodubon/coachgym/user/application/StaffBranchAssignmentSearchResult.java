package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentDetails;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.Objects;

/** Safe administrative assignment projection without credentials or profile data. */
public record StaffBranchAssignmentSearchResult(
        StaffBranchAssignmentDetails assignment,
        String staffIdentifier,
        RoleCode role,
        StaffScopeType scopeType,
        AuthorizedBranchSummary branch) {

    public StaffBranchAssignmentSearchResult {
        assignment = Objects.requireNonNull(assignment, "assignment is required");
        staffIdentifier = requireStaffIdentifier(staffIdentifier);
        role = Objects.requireNonNull(role, "role is required");
        scopeType = Objects.requireNonNull(scopeType, "scopeType is required");
        branch = Objects.requireNonNull(branch, "branch is required");
    }

    private static String requireStaffIdentifier(String value) {
        Objects.requireNonNull(value, "staffIdentifier is required");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 100
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("staffIdentifier is invalid");
        }
        return normalized;
    }
}
