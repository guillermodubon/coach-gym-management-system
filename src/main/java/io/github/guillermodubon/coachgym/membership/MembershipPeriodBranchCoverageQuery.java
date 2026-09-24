package io.github.guillermodubon.coachgym.membership;

import java.util.UUID;

/** Minimal public query for checking a period's immutable branch entitlement. */
@FunctionalInterface
public interface MembershipPeriodBranchCoverageQuery {

    /** Returns whether the exact period snapshot includes the requested branch. */
    boolean coversBranch(UUID membershipPeriodId, UUID branchId);
}
