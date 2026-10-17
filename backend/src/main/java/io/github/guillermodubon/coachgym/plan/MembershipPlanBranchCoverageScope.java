package io.github.guillermodubon.coachgym.plan;

/** Branch-coverage mode configured on a membership plan. */
public enum MembershipPlanBranchCoverageScope {

    /** Exactly one active branch is explicitly associated with the plan. */
    SINGLE_BRANCH,

    /** Two or more active branches are explicitly associated with the plan. */
    SELECTED_BRANCHES,

    /** The plan is available to all active branches in the canonical organization. */
    ALL_BRANCHES
}
