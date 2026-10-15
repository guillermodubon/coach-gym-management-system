package io.github.guillermodubon.coachgym.access.application;

/** Safe technical failure when immutable branch entitlement cannot be read. */
public final class AccessMembershipCoverageEvaluationException
        extends RuntimeException {

    public AccessMembershipCoverageEvaluationException(String message) {
        super(message);
    }

    public AccessMembershipCoverageEvaluationException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}
