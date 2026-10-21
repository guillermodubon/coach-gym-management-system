package io.github.guillermodubon.coachgym.membership.application;

/** Raised when a bounded membership query contains invalid values. */
public final class MembershipQueryValidationException extends IllegalArgumentException {

    public MembershipQueryValidationException(String message) {
        super(message);
    }
}
