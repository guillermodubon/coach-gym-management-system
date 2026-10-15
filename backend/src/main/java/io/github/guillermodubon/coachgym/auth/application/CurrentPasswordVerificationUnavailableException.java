package io.github.guillermodubon.coachgym.auth.application;

/** Safe failure raised when current-password verification cannot be completed. */
public final class CurrentPasswordVerificationUnavailableException extends RuntimeException {

    public CurrentPasswordVerificationUnavailableException() {
        super("Current-password verification is temporarily unavailable.");
    }
}
