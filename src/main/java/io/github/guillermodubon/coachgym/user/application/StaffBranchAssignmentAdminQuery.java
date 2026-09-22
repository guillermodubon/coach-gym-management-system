package io.github.guillermodubon.coachgym.user.application;

/** Read port for bounded administrative assignment history projections. */
public interface StaffBranchAssignmentAdminQuery {

    StaffBranchAssignmentSearchPage findPage(StaffBranchAssignmentSearchQuery query);
}
