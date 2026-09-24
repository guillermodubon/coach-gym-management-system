package io.github.guillermodubon.coachgym.configuration;

/** Safe denial raised when the authenticated staff actor lacks policy authority. */
public final class AccessPaymentPolicyAuthorizationException extends RuntimeException {

    public AccessPaymentPolicyAuthorizationException() {
        super("The staff member is not authorized for this access-payment policy operation.");
    }
}
