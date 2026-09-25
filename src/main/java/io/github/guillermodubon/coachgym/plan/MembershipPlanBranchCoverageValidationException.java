package io.github.guillermodubon.coachgym.plan;

/** Indicates invalid plan coverage or membership-period coverage snapshot data. */
public final class MembershipPlanBranchCoverageValidationException
        extends IllegalArgumentException {

    public MembershipPlanBranchCoverageValidationException(String message) {
        super(message);
    }
}
