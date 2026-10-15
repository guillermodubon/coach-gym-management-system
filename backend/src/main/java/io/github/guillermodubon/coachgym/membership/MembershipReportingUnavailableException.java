package io.github.guillermodubon.coachgym.membership;

/** Safe failure for an unavailable membership reporting read. */
public final class MembershipReportingUnavailableException extends RuntimeException {

    public MembershipReportingUnavailableException(Throwable cause) {
        super("Membership reporting data could not be read.", cause);
    }
}
