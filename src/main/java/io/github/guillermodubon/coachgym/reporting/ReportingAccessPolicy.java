package io.github.guillermodubon.coachgym.reporting;

import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;

/** Pure role/scope policy for report families and normalized branch selections. */
public final class ReportingAccessPolicy {

    private ReportingAccessPolicy() {
    }

    /**
     * Tests whether the actor may request the given aggregate family.
     *
     * <p>This policy checks staff scope and assigned branch identifiers only.
     * The application must still verify that each branch belongs to the
     * organization and is eligible for the requested historical/current view.
     * SQL must apply the resolved branch predicate before aggregation.</p>
     */
    public static boolean allows(
            StaffAuthorizationContext actor,
            ReportingMetricGroup metricGroup,
            BranchReportingSelection selection) {
        if (actor == null || metricGroup == null || selection == null
                || actor.accountStatus()
                        != io.github.guillermodubon.coachgym.user.StaffAccountStatus.ACTIVE) {
            return false;
        }

        if (actor.organizationAdmin()) {
            return true;
        }
        if (actor.branchAdmin()) {
            return selection.scope() != ReportingScope.ORGANIZATION
                    && actor.assignedBranchIds().containsAll(selection.branchIds());
        }
        if (actor.receptionist()) {
            boolean approvedMetricGroup = metricGroup == ReportingMetricGroup.MEMBERSHIP_SUMMARY
                    || metricGroup == ReportingMetricGroup.ACCESS_SUMMARY;
            boolean oneBranch = selection.scope() == ReportingScope.SINGLE_BRANCH
                    || selection.scope() == ReportingScope.ACTIVE_BRANCH;
            return approvedMetricGroup
                    && oneBranch
                    && selection.branchIds().size() == 1
                    && actor.assignedTo(selection.branchIds().getFirst());
        }
        return false;
    }
}
