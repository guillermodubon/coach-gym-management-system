package io.github.guillermodubon.coachgym.membership.application;

/** Safe boundary for failures while reading membership-period history. */
public final class MembershipPeriodQueryUnavailableException extends RuntimeException {

    public MembershipPeriodQueryUnavailableException(Throwable cause) {
        super("Membership period query is unavailable.", cause);
    }
}
