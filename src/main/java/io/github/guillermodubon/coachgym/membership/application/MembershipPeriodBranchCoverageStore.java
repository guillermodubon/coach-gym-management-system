package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodBranchCoverageDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodBranchCoverageQuery;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummaryQuery;

/** Transactional persistence port for immutable period coverage snapshots. */
public interface MembershipPeriodBranchCoverageStore
        extends MembershipPeriodBranchCoverageQuery,
                MembershipPeriodCoverageSummaryQuery {

    /** Persists the complete snapshot atomically with period creation. */
    void capture(MembershipPeriodBranchCoverageDetails snapshot);
}
