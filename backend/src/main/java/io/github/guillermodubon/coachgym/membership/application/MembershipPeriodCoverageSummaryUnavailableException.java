package io.github.guillermodubon.coachgym.membership.application;

/** Raised when a persisted commercial period has no valid coverage snapshot. */
public class MembershipPeriodCoverageSummaryUnavailableException
        extends RuntimeException {

    public MembershipPeriodCoverageSummaryUnavailableException() {
        super("Membership period coverage summary is unavailable.");
    }
}
