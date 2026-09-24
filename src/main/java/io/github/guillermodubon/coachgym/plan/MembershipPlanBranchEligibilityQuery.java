package io.github.guillermodubon.coachgym.plan;

import java.util.UUID;

/** Read boundary for checking current plan eligibility at one branch. */
@FunctionalInterface
public interface MembershipPlanBranchEligibilityQuery {

    /** Returns whether an active plan is currently valid at an active branch. */
    boolean isValidAtBranch(UUID planId, UUID branchId);
}
