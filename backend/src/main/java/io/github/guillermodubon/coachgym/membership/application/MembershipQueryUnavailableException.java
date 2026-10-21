package io.github.guillermodubon.coachgym.membership.application;

/** Safe boundary for failures while reading a membership page. */
public final class MembershipQueryUnavailableException extends RuntimeException {

    public MembershipQueryUnavailableException(Throwable cause) {
        super("Membership query is unavailable.", cause);
    }
}
