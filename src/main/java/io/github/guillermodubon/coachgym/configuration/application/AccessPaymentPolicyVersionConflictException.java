package io.github.guillermodubon.coachgym.configuration.application;

/** Raised when a policy update uses a stale optimistic-lock version. */
public class AccessPaymentPolicyVersionConflictException extends RuntimeException {

    public AccessPaymentPolicyVersionConflictException() {
        super("Access payment policy was changed by another operation.");
    }
}
