package io.github.guillermodubon.coachgym.membership.application;

/** Period does not belong to the requested membership in its authorized branch. */
public final class MembershipPeriodNotFoundException extends RuntimeException {

    public MembershipPeriodNotFoundException() {
        super("The requested membership period was not found.");
    }
}
