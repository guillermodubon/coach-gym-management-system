package io.github.guillermodubon.coachgym.configuration;

/** Raised when an access-payment policy contract is structurally invalid. */
public class AccessPaymentPolicyValidationException extends RuntimeException {

    public AccessPaymentPolicyValidationException(String message) {
        super(message);
    }
}
