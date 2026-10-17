package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.StaffBranchContext;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.List;
import java.util.UUID;

/** Server-authoritative active branch context for the authenticated staff user. */
record StaffBranchContextResponse(
        UUID organizationId,
        StaffScopeType scopeType,
        AuthorizedBranchSummary activeBranch,
        List<AuthorizedBranchSummary> availableBranches) {

    static StaffBranchContextResponse from(StaffBranchContext context) {
        AuthorizedBranchSummary active = context.availableBranches().stream()
                .filter(branch -> branch.id().equals(context.activeBranchId()))
                .findFirst()
                .orElse(null);
        return new StaffBranchContextResponse(
                context.organizationId(), context.scopeType(), active, context.availableBranches());
    }
}
