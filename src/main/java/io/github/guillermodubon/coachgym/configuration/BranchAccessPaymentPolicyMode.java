package io.github.guillermodubon.coachgym.configuration;

/** Branch-level behavior relative to the organization access-payment default. */
public enum BranchAccessPaymentPolicyMode {

    /** Use the current organization default. */
    INHERIT,

    /** Require a confirmed payment for access at this branch. */
    REQUIRED,

    /** Do not require a confirmed payment for access at this branch. */
    NOT_REQUIRED
}
