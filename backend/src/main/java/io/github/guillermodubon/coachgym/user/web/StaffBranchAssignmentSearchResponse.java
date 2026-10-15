package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchResult;

/** Safe list projection without credentials, profile data, or permissions maps. */
record StaffBranchAssignmentSearchResponse(
        StaffBranchAssignmentResponse assignment,
        String staffIdentifier,
        RoleCode role,
        StaffScopeType scopeType,
        AuthorizedBranchSummary branch) {

    static StaffBranchAssignmentSearchResponse from(StaffBranchAssignmentSearchResult result) {
        return new StaffBranchAssignmentSearchResponse(
                StaffBranchAssignmentResponse.from(result.assignment()),
                result.staffIdentifier(), result.role(), result.scopeType(), result.branch());
    }
}
