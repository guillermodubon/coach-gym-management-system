package io.github.guillermodubon.coachgym.membership;

/** Indicates an invalid immutable membership-period branch snapshot. */
public final class MembershipPeriodBranchCoverageValidationException
        extends IllegalArgumentException {

    public MembershipPeriodBranchCoverageValidationException(String message) {
        super(message);
    }
}
