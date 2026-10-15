package io.github.guillermodubon.coachgym.configuration.application;

/** Wraps an unexpected failure while reading or persisting policy settings. */
public class AccessPaymentPolicyDataAccessException extends RuntimeException {

    public AccessPaymentPolicyDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
