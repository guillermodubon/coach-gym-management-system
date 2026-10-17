package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchPage;
import java.util.List;

/** Bounded administrative assignment page with stable pagination metadata. */
record StaffBranchAssignmentPageResponse(
        List<StaffBranchAssignmentSearchResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static StaffBranchAssignmentPageResponse from(StaffBranchAssignmentSearchPage page) {
        return new StaffBranchAssignmentPageResponse(
                page.items().stream().map(StaffBranchAssignmentSearchResponse::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
